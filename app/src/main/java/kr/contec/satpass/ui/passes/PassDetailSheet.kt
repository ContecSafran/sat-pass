package kr.contec.satpass.ui.passes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.contec.satpass.domain.model.ObserverLocation
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.domain.usecase.GetPassTrackUseCase
import kr.contec.satpass.ui.format.PassFormat
import java.time.Instant

/**
 * 리스트 아이템을 눌렀을 때 뜨는 상세 정보 시트.
 *
 * 카드에서 생략했던 절대 시각·방위각·TLE epoch 를 모두 보여주고,
 * 위성이 지나가는 경로를 스카이 플롯으로 그린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassDetailSheet(
    pass: SatellitePass,
    observer: ObserverLocation,
    now: Instant,
    loadTrack: suspend (SatellitePass, ObserverLocation) -> List<GetPassTrackUseCase.TrackPoint>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var track by remember(pass.noradId, pass.aosTime) {
        mutableStateOf<List<GetPassTrackUseCase.TrackPoint>?>(null)
    }

    LaunchedEffect(pass.noradId, pass.aosTime) {
        track = loadTrack(pass, observer)
    }

    val accent = elevationColor(pass.maxElevationDeg)
    val progress = pass.progress(now)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 헤더
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = pass.satelliteName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "NORAD ${pass.noradId}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when {
                            progress != null -> "진행 중 ${(progress * 100).toInt()}%"
                            pass.aosTime.isAfter(now) -> PassFormat.relative(pass.aosTime, now)
                            else -> "종료됨"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // 스카이 플롯
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val current = track) {
                        null -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.width(28.dp))
                            Text(
                                text = "궤적 계산 중…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> if (current.isEmpty()) {
                            Text(
                                text = "궤적을 계산할 수 없습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            SkyPlot(track = current, accent = accent)
                        }
                    }
                }
            }

            DetailSection(title = "시각") {
                DetailRow("AOS", PassFormat.dateTime(pass.aosTime))
                DetailRow("최대 고각", PassFormat.dateTime(pass.maxElevationTime))
                DetailRow("LOS", PassFormat.dateTime(pass.losTime))
                DetailRow("지속 시간", PassFormat.duration(pass.duration))
            }

            DetailSection(title = "방위 / 고각") {
                DetailRow("AOS 방위각", PassFormat.azimuthWithCompass(pass.aosAzimuthDeg))
                DetailRow("최대 고각 방위각", PassFormat.azimuthWithCompass(pass.centerAzimuthDeg))
                DetailRow("LOS 방위각", PassFormat.azimuthWithCompass(pass.losAzimuthDeg))
                DetailRow("최대 고각", PassFormat.degrees(pass.maxElevationDeg, decimals = 2))
            }

            DetailSection(title = "계산 기준") {
                DetailRow(
                    "관측 위치",
                    "${PassFormat.degrees(observer.latitude, 4)}, " +
                        PassFormat.degrees(observer.longitude, 4),
                )
                DetailRow("관측 고도", "${observer.altitudeMeters.toInt()} m")
                pass.tleEpoch?.let { DetailRow("TLE epoch", PassFormat.utcDateTime(it)) }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
