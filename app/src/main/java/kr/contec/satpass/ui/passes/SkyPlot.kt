package kr.contec.satpass.ui.passes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contec.satpass.domain.usecase.GetPassTrackUseCase
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 스카이 플롯(극좌표 궤적).
 *
 * 원의 중심이 천정(고각 90°), 바깥 원이 지평선(고각 0°) 이고 위쪽이 북(N)이다.
 * 위성이 하늘을 가로지르는 경로를 그려서 어느 방향을 봐야 하는지 바로 알 수 있게 한다.
 */
@Composable
fun SkyPlot(
    track: List<GetPassTrackUseCase.TrackPoint>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
        ) {
            val radius = min(size.width, size.height) / 2f - 14.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)

            drawSkyGrid(center, radius, gridColor)
            drawCompassLabels(center, radius, labelColor, textMeasurer)

            if (track.size >= 2) {
                drawTrack(track, center, radius, accent)
            }
        }
    }
}

/** 고각 0/30/60° 원과 십자 눈금 */
private fun DrawScope.drawSkyGrid(center: Offset, radius: Float, gridColor: Color) {
    listOf(0, 30, 60).forEach { elevation ->
        val r = radius * (90 - elevation) / 90f
        drawCircle(
            color = gridColor.copy(alpha = if (elevation == 0) 0.9f else 0.45f),
            radius = r,
            center = center,
            style = Stroke(width = if (elevation == 0) 2f else 1f),
        )
    }
    // 천정
    drawCircle(color = gridColor.copy(alpha = 0.6f), radius = 2.5f, center = center)

    // N-S / E-W 축
    drawLine(
        color = gridColor.copy(alpha = 0.35f),
        start = Offset(center.x, center.y - radius),
        end = Offset(center.x, center.y + radius),
        strokeWidth = 1f,
    )
    drawLine(
        color = gridColor.copy(alpha = 0.35f),
        start = Offset(center.x - radius, center.y),
        end = Offset(center.x + radius, center.y),
        strokeWidth = 1f,
    )
}

private fun DrawScope.drawCompassLabels(
    center: Offset,
    radius: Float,
    labelColor: Color,
    textMeasurer: TextMeasurer,
) {
    val style = TextStyle(color = labelColor, fontSize = 11.sp)
    val labels = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)

    labels.forEach { (text, azimuth) ->
        val point = polarToOffset(center, radius, azimuth, elevationDeg = -6.0)
        val measured = textMeasurer.measure(text, style)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                point.x - measured.size.width / 2f,
                point.y - measured.size.height / 2f,
            ),
        )
    }
}

private fun DrawScope.drawTrack(
    track: List<GetPassTrackUseCase.TrackPoint>,
    center: Offset,
    radius: Float,
    accent: Color,
) {
    val points = track.map { polarToOffset(center, radius, it.azimuthDeg, it.elevationDeg) }

    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color = accent, style = Stroke(width = 3.5f))

    // AOS(빈 원) / LOS(채운 원) / 최고 고각(큰 점)
    drawCircle(color = accent, radius = 5f, center = points.first(), style = Stroke(width = 2.5f))
    drawCircle(color = accent, radius = 5f, center = points.last())

    val maxIndex = track.indices.maxByOrNull { track[it].elevationDeg } ?: return
    drawCircle(color = accent.copy(alpha = 0.35f), radius = 9f, center = points[maxIndex])
    drawCircle(color = accent, radius = 4f, center = points[maxIndex])
}

/**
 * 방위각/고각을 캔버스 좌표로 바꾼다.
 * 위쪽이 북(0°)이고 시계 방향으로 방위각이 증가한다.
 */
private fun polarToOffset(
    center: Offset,
    radius: Float,
    azimuthDeg: Double,
    elevationDeg: Double,
): Offset {
    val r = radius * ((90.0 - elevationDeg) / 90.0).toFloat()
    val azRad = Math.toRadians(azimuthDeg)
    return Offset(
        x = center.x + r * sin(azRad).toFloat(),
        y = center.y - r * cos(azRad).toFloat(),
    )
}
