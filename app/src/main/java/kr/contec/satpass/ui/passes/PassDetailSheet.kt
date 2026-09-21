package kr.contec.satpass.ui.passes

import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.contec.satpass.domain.model.ObserverLocation
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.domain.usecase.GetPassTrackUseCase
import kr.contec.satpass.ui.export.ExportFormat
import kr.contec.satpass.ui.export.PassExport
import kr.contec.satpass.ui.export.copyToClipboard
import kr.contec.satpass.ui.format.PassFormat
import kr.contec.satpass.ui.theme.PassImminent
import java.time.Instant

/** 궤적 표에서 고를 수 있는 표본 간격(초) */
private val TRACK_STEP_OPTIONS = listOf(1, 5, 10, 30)

/**
 * 리스트 아이템을 눌렀을 때 뜨는 상세 정보 시트.
 *
 * 절대 시각·방위각·TLE epoch, 스카이 플롯, 그리고 초 단위 Azimuth/Elevation 표를 보여 준다.
 * 표는 행이 많을 수 있어 [LazyColumn] 으로 그린다.
 *
 * @param loadTrack (패스, 관측 위치, 표본 간격(초)) → 궤적
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassDetailSheet(
    pass: SatellitePass,
    observer: ObserverLocation,
    now: Instant,
    loadTrack: suspend (SatellitePass, ObserverLocation, Int) -> List<GetPassTrackUseCase.TrackPoint>,
    onCopied: () -> Unit,
    alarmEnabled: Boolean,
    onToggleAlarm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var stepSeconds by remember(pass.noradId, pass.aosTime) { mutableIntStateOf(TRACK_STEP_OPTIONS.first()) }
    var track by remember(pass.noradId, pass.aosTime) {
        mutableStateOf<List<GetPassTrackUseCase.TrackPoint>?>(null)
    }

    LaunchedEffect(pass.noradId, pass.aosTime, stepSeconds) {
        track = null
        track = loadTrack(pass, observer, stepSeconds)
    }

    val accent = elevationColor(pass.maxElevationDeg)
    val progress = pass.progress(now)
    val points = track

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
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
                        Spacer(Modifier.weight(1f))
                        // 이 패스의 알림 켜고 끄기
                        IconButton(onClick = onToggleAlarm) {
                            Icon(
                                imageVector = if (alarmEnabled) {
                                    Icons.Filled.NotificationsActive
                                } else {
                                    Icons.Outlined.NotificationsNone
                                },
                                contentDescription = if (alarmEnabled) "알림 해제" else "알림 설정",
                                tint = if (alarmEnabled) {
                                    PassImminent
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            item(key = "skyplot") {
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
                        when {
                            points == null -> LoadingIndicator("궤적 계산 중…")
                            points.isEmpty() -> Text(
                                text = "궤적을 계산할 수 없습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            else -> SkyPlot(track = points, accent = accent)
                        }
                    }
                }
            }

            item(key = "times") {
                DetailSection(title = "시각") {
                    DetailRow("AOS", PassFormat.dateTime(pass.aosTime))
                    DetailRow("최대 고각", PassFormat.dateTime(pass.maxElevationTime))
                    DetailRow("LOS", PassFormat.dateTime(pass.losTime))
                    DetailRow("지속 시간", PassFormat.duration(pass.duration))
                }
            }

            item(key = "angles") {
                DetailSection(title = "방위 / 고각") {
                    DetailRow("AOS 방위각", PassFormat.azimuthWithCompass(pass.aosAzimuthDeg))
                    DetailRow("최대 고각 방위각", PassFormat.azimuthWithCompass(pass.centerAzimuthDeg))
                    DetailRow("LOS 방위각", PassFormat.azimuthWithCompass(pass.losAzimuthDeg))
                    DetailRow("최대 고각", PassFormat.degrees(pass.maxElevationDeg, decimals = 2))
                }
            }

            item(key = "basis") {
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

            // 궤적 표
            item(key = "track-header") {
                TrackTableHeader(
                    stepSeconds = stepSeconds,
                    onStepChange = { stepSeconds = it },
                    rowCount = points?.size,
                    onCopy = { format ->
                        val current = points ?: return@TrackTableHeader
                        copyToClipboard(
                            context = context,
                            label = "${pass.satelliteName} 궤적",
                            text = PassExport.track(pass, current, format),
                        )
                        // Android 13 부터는 시스템이 복사 확인 UI 를 띄운다.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onCopied()
                    },
                )
            }

            if (points == null) {
                item(key = "track-loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator("표 만드는 중…")
                    }
                }
            } else {
                item(key = "track-columns") { TrackRow("시각", "Azimuth", "Elevation", isHeader = true) }
                items(
                    items = points,
                    key = { it.time.toEpochMilli() },
                ) { point ->
                    TrackRow(
                        time = PassFormat.time(point.time),
                        azimuth = PassFormat.degrees(point.azimuthDeg, 2),
                        elevation = PassFormat.degrees(point.elevationDeg, 2),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator(label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.width(28.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 궤적 표의 제목 · 간격 선택 · 복사 버튼 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TrackTableHeader(
    stepSeconds: Int,
    onStepChange: (Int) -> Unit,
    rowCount: Int?,
    onCopy: (ExportFormat) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "궤적 표",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                rowCount?.let {
                    Text(
                        text = "${it}행",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box {
                TextButton(
                    onClick = { menuExpanded = true },
                    enabled = rowCount != null && rowCount > 0,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.width(18.dp),
                    )
                    Text("복사", modifier = Modifier.padding(start = 6.dp))
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    ExportFormat.entries.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format.label) },
                            onClick = {
                                onCopy(format)
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TRACK_STEP_OPTIONS.forEach { step ->
                FilterChip(
                    selected = step == stepSeconds,
                    onClick = { onStepChange(step) },
                    label = { Text("${step}초") },
                )
            }
        }
    }
}

/** 궤적 표 한 줄. 열 너비를 고정해 숫자가 흔들리지 않게 한다. */
@Composable
private fun TrackRow(
    time: String,
    azimuth: String,
    elevation: String,
    isHeader: Boolean = false,
) {
    val color = if (isHeader) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val weight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = weight,
            color = color,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = azimuth,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = weight,
            color = color,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = elevation,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = weight,
            color = color,
            modifier = Modifier.weight(1f),
        )
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
