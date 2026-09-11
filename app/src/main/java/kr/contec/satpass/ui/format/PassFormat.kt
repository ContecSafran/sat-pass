package kr.contec.satpass.ui.format

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 화면 표시용 포맷 모음.
 * 계산은 UTC 로 하고 표시는 기기 시간대로 바꾼다.
 */
object PassFormat {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREA)
    private val shortTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA)
    private val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREA)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
    private val utcFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.KOREA)

    /** 기기 시간대 기준 `HH:mm:ss` */
    fun time(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        timeFormatter.format(instant.atZone(zone))

    /** 기기 시간대 기준 `HH:mm` */
    fun shortTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        shortTimeFormatter.format(instant.atZone(zone))

    /** 기기 시간대 기준 `yyyy-MM-dd HH:mm:ss` */
    fun dateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormatter.format(instant.atZone(zone))

    /** UTC 기준 표시 (TLE epoch 등 궤도 데이터용) */
    fun utcDateTime(instant: Instant): String =
        utcFormatter.format(instant.atZone(ZoneId.of("UTC")))

    /** 날짜 구분 헤더용 — `오늘`, `내일`, 그 밖에는 `M월 d일 (E)` */
    fun dayHeader(instant: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = instant.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        return when (date) {
            today -> "오늘"
            today.plusDays(1) -> "내일"
            today.minusDays(1) -> "어제"
            else -> dateFormatter.format(date)
        }
    }

    /** 날짜 구분 기준값 (헤더 그룹핑용) */
    fun dayKey(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        instant.atZone(zone).toLocalDate()

    /** 지속 시간 — `8분 42초`, 1시간 이상이면 `1시간 3분` */
    fun duration(duration: Duration): String {
        val totalSeconds = duration.seconds.coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}시간 ${minutes}분"
            minutes > 0 -> "${minutes}분 ${seconds}초"
            else -> "${seconds}초"
        }
    }

    /** 남은/경과 시간 — `3분 후`, `2시간 12분 후`, `진행 중` */
    fun relative(target: Instant, now: Instant): String {
        val seconds = Duration.between(now, target).seconds
        if (seconds <= 0) return "지금"
        val days = seconds / 86_400
        val hours = (seconds % 86_400) / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            days > 0 -> "${days}일 ${hours}시간 후"
            hours > 0 -> "${hours}시간 ${minutes}분 후"
            minutes > 0 -> "${minutes}분 후"
            else -> "${seconds}초 후"
        }
    }

    /** `123.4°` */
    fun degrees(value: Double, decimals: Int = 1): String =
        String.format(Locale.US, "%.${decimals}f°", value)

    /** 방위각을 16방위 약어로 — `N`, `NNE`, ... */
    fun compass(azimuthDeg: Double): String {
        val points = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )
        val normalized = ((azimuthDeg % 360) + 360) % 360
        val index = Math.round(normalized / 22.5).toInt() % 16
        return points[index]
    }

    /** `205.3° (SSW)` */
    fun azimuthWithCompass(azimuthDeg: Double): String =
        "${degrees(azimuthDeg)} (${compass(azimuthDeg)})"
}
