package kr.contec.satpass.ui.passes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.ui.format.PassFormat
import kr.contec.satpass.ui.theme.ElevationHigh
import kr.contec.satpass.ui.theme.ElevationLow
import kr.contec.satpass.ui.theme.ElevationMid
import kr.contec.satpass.ui.theme.PassActive
import kr.contec.satpass.ui.theme.PassImminent
import java.time.Duration
import java.time.Instant

/** 최대 고각에 따른 강조색. 고각이 높을수록 수신 품질이 좋다. */
fun elevationColor(maxElevationDeg: Double): Color = when {
    maxElevationDeg >= 60 -> ElevationHigh
    maxElevationDeg >= 30 -> ElevationMid
    else -> ElevationLow
}

/** AOS 까지 5분 이내면 임박으로 본다. */
private const val IMMINENT_THRESHOLD_MINUTES = 5L

/**
 * 통과 1건을 나타내는 카드.
 *
 * 현재 시각이 AOS~LOS 안에 있으면 카드 안쪽을 좌→우로 채우는 진행도를 그리고,
 * 그렇지 않으면 AOS 까지 남은 시간을 보여준다. 카드를 누르면 상세 정보가 열린다.
 */
@Composable
fun PassCard(
    pass: SatellitePass,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = pass.progress(now)
    val inProgress = progress != null
    val imminent = !inProgress &&
        Duration.between(now, pass.aosTime).toMinutes() < IMMINENT_THRESHOLD_MINUTES &&
        pass.aosTime.isAfter(now)

    val accent by animateColorAsState(
        targetValue = when {
            inProgress -> PassActive
            imminent -> PassImminent
            else -> elevationColor(pass.maxElevationDeg)
        },
        label = "passAccent",
    )

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (inProgress) 6.dp else 0.dp),
    ) {
        Box {
            // 배경 레이어. 카드 높이는 아래 Column 이 정하므로, 레이아웃에 영향을 주지 않도록
            // matchParentSize + 직접 그리기로 처리한다.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        // 진행 중이면 AOS 쪽부터 진행도만큼 카드 안쪽을 채운다.
                        if (progress != null && progress > 0f) {
                            val fillWidth = size.width * progress
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.26f),
                                        accent.copy(alpha = 0.08f),
                                    ),
                                    startX = 0f,
                                    endX = fillWidth,
                                ),
                                size = Size(fillWidth, size.height),
                            )
                        }
                        // 좌측 상태 바
                        drawRect(color = accent, size = Size(4.dp.toPx(), size.height))
                    }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PassCardHeader(pass = pass, accent = accent, inProgress = inProgress)

                if (progress != null) {
                    PassProgressBar(
                        progress = progress,
                        accent = accent,
                        remaining = Duration.between(now, pass.losTime),
                    )
                }

                PassCardFooter(pass = pass, now = now, inProgress = inProgress, imminent = imminent)
            }
        }
    }
}

@Composable
private fun PassCardHeader(
    pass: SatellitePass,
    accent: Color,
    inProgress: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inProgress) {
            LiveDot(color = accent)
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pass.satelliteName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "NORAD ${pass.noradId}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = PassFormat.degrees(pass.maxElevationDeg),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                color = elevationColor(pass.maxElevationDeg),
            )
            Text(
                text = "최대 고각",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 진행 중임을 알리는 점멸 표시 */
@Composable
private fun LiveDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveDotAlpha",
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

/**
 * 카드 안의 진행 바.
 * AOS 쪽부터 채워지고, 우측에 LOS 까지 남은 시간을 같이 보여준다.
 */
@Composable
private fun PassProgressBar(
    progress: Float,
    accent: Color,
    remaining: Duration,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), accent))
                    )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "진행 중 ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "LOS 까지 ${PassFormat.duration(remaining)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PassCardFooter(
    pass: SatellitePass,
    now: Instant,
    inProgress: Boolean,
    imminent: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeBlock(label = "AOS", value = PassFormat.time(pass.aosTime))
            Text(
                text = "→",
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TimeBlock(label = "LOS", value = PassFormat.time(pass.losTime))

            Spacer(Modifier.weight(1f))

            Text(
                text = PassFormat.duration(pass.duration),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = buildString {
                if (!inProgress) {
                    append(PassFormat.relative(pass.aosTime, now))
                    append(" · ")
                }
                append(PassFormat.compass(pass.aosAzimuthDeg))
                append(" → ")
                append(PassFormat.compass(pass.centerAzimuthDeg))
                append(" → ")
                append(PassFormat.compass(pass.losAzimuthDeg))
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (imminent) PassImminent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (imminent) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TimeBlock(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
