package kr.contec.satpass.ui.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.domain.usecase.GetPassTrackUseCase
import kr.contec.satpass.ui.format.PassFormat
import java.util.Locale

/**
 * 클립보드로 내보낼 형식.
 *
 * 나중에 JSON 등을 추가할 수 있도록 enum 으로 둔다.
 */
enum class ExportFormat(val label: String) {
    /** 탭으로 구분한 표. 메모장이나 엑셀에 그대로 붙여 넣을 수 있다. */
    TABLE("표로 복사"),

    /** 쉼표로 구분한 CSV */
    CSV("CSV로 복사"),
    ;

    /** 이 형식의 열 구분자 */
    val delimiter: String get() = if (this == CSV) "," else "\t"
}

/**
 * 패스 목록과 궤적을 텍스트로 바꾸는 함수 모음.
 *
 * 숫자는 로캘과 무관하게 소수점이 '.' 이 되도록 [Locale.US] 로 포맷한다.
 */
object PassExport {

    private val PASS_HEADERS = listOf(
        "위성", "NORAD", "AOS", "LOS", "최대고각시각",
        "최대고각(deg)", "AOS방위각(deg)", "중간방위각(deg)", "LOS방위각(deg)", "지속시간(초)",
    )

    private val TRACK_HEADERS = listOf("시각", "Azimuth(deg)", "Elevation(deg)")

    /** 패스 스케줄 전체를 표/CSV 로 */
    fun passes(passes: List<SatellitePass>, format: ExportFormat): String {
        val rows = passes.map { pass ->
            listOf(
                pass.satelliteName,
                pass.noradId.toString(),
                PassFormat.dateTime(pass.aosTime),
                PassFormat.dateTime(pass.losTime),
                PassFormat.dateTime(pass.maxElevationTime),
                decimal(pass.maxElevationDeg),
                decimal(pass.aosAzimuthDeg),
                decimal(pass.centerAzimuthDeg),
                decimal(pass.losAzimuthDeg),
                pass.duration.seconds.toString(),
            )
        }
        return render(PASS_HEADERS, rows, format)
    }

    /** 패스 1건의 초 단위 궤적을 표/CSV 로 */
    fun track(
        pass: SatellitePass,
        points: List<GetPassTrackUseCase.TrackPoint>,
        format: ExportFormat,
    ): String {
        val rows = points.map { point ->
            listOf(
                PassFormat.dateTime(point.time),
                decimal(point.azimuthDeg),
                decimal(point.elevationDeg),
            )
        }
        // 어떤 패스의 궤적인지 알 수 있도록 머리말을 붙인다.
        val title = "# ${pass.satelliteName} (NORAD ${pass.noradId}) " +
            "${PassFormat.dateTime(pass.aosTime)} ~ ${PassFormat.dateTime(pass.losTime)}"
        return title + "\n" + render(TRACK_HEADERS, rows, format)
    }

    private fun render(
        headers: List<String>,
        rows: List<List<String>>,
        format: ExportFormat,
    ): String = buildString {
        val delimiter = format.delimiter
        append(headers.joinToString(delimiter) { escape(it, format) })
        rows.forEach { row ->
            append('\n')
            append(row.joinToString(delimiter) { escape(it, format) })
        }
    }

    /** CSV 는 구분자·따옴표·줄바꿈이 든 값을 따옴표로 감싼다. */
    private fun escape(value: String, format: ExportFormat): String {
        if (format != ExportFormat.CSV) return value
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needsQuote) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun decimal(value: Double): String = String.format(Locale.US, "%.2f", value)
}

/** 텍스트를 클립보드에 넣는다. */
fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
