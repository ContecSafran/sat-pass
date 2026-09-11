package kr.contec.satpass.ui.passes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.ui.format.PassFormat
import kr.contec.satpass.ui.theme.PassActive
import java.time.Instant
import java.time.LocalDate

/**
 * 통과 스케줄 화면.
 *
 * - 선택된 위성들의 패스를 AOS 시간순으로 보여준다.
 * - 목록을 아래로 당기면 TLE 를 갱신한다. (최소 간격 안이면 안내만 표시)
 * - 진행 중인 패스는 카드 안에서 진행도가 채워진다.
 * - 카드를 누르면 상세 시트가 열린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassListScreen(
    state: PassListUiState,
    now: Instant,
    onRefresh: () -> Unit,
    onPassClick: (SatellitePass) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onGoToSatellites: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "status") {
                StatusStrip(
                    state = state,
                    onRequestLocationPermission = onRequestLocationPermission,
                )
            }

            if (state.problems.isNotEmpty()) {
                item(key = "problems") { ProblemStrip(state) }
            }

            when {
                state.registeredSatelliteCount == 0 -> item(key = "empty-registered") {
                    EmptyState(
                        icon = Icons.Outlined.SatelliteAlt,
                        title = "등록된 위성이 없습니다",
                        description = "위성 탭에서 관심 위성을 추가하면 통과 스케줄이 여기에 표시됩니다.",
                        actionLabel = "위성 추가하러 가기",
                        onAction = onGoToSatellites,
                    )
                }

                state.selectedSatelliteCount == 0 -> item(key = "empty-selected") {
                    EmptyState(
                        icon = Icons.Outlined.SatelliteAlt,
                        title = "선택된 위성이 없습니다",
                        description = "위성 탭에서 보고 싶은 위성을 켜 주세요.",
                        actionLabel = "위성 목록 열기",
                        onAction = onGoToSatellites,
                    )
                }

                state.isLoading -> item(key = "loading") {
                    EmptyState(
                        icon = Icons.Outlined.SatelliteAlt,
                        title = "패스를 계산하고 있습니다",
                        description = "잠시만 기다려 주세요.",
                    )
                }

                state.passes.isEmpty() -> item(key = "empty-passes") {
                    EmptyState(
                        icon = Icons.Outlined.SatelliteAlt,
                        title = "앞으로 ${state.predictionDays}일간 통과가 없습니다",
                        description = "설정에서 예측 기간을 늘리거나 최소 고각을 낮춰 보세요.",
                    )
                }

                else -> passListItems(
                    passes = state.passes,
                    now = now,
                    onPassClick = onPassClick,
                )
            }
        }
    }
}

/** 날짜별로 묶어 헤더를 끼워 넣는다. */
private fun LazyListScope.passListItems(
    passes: List<SatellitePass>,
    now: Instant,
    onPassClick: (SatellitePass) -> Unit,
) {
    var lastDay: LocalDate? = null

    passes.forEach { pass ->
        val day = PassFormat.dayKey(pass.aosTime)
        if (day != lastDay) {
            lastDay = day
            item(key = "header-$day") {
                DayHeader(text = PassFormat.dayHeader(pass.aosTime, now))
            }
        }
        item(key = "pass-${pass.noradId}-${pass.aosTime.toEpochMilli()}") {
            PassCard(pass = pass, now = now, onClick = { onPassClick(pass) })
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

/** 관측 위치 / TLE 상태 요약 */
@Composable
private fun StatusStrip(
    state: PassListUiState,
    onRequestLocationPermission: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.usingDefaultLocation) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.width(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.usingDefaultLocation) "기본 위치로 계산 중" else "현재 위치 기준",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.usingDefaultLocation) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            PassActive
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${PassFormat.degrees(state.observer.latitude, 4)}, " +
                            "${PassFormat.degrees(state.observer.longitude, 4)} · " +
                            "${state.observer.altitudeMeters.toInt()}m",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!state.locationPermissionGranted) {
                    TextButton(onClick = onRequestLocationPermission) { Text("위치 허용") }
                }
            }

            Text(
                text = buildString {
                    append("TLE ")
                    append(state.tleCachedCount)
                    append("건")
                    state.lastTleFetchedAt?.let {
                        append(" · 갱신 ")
                        append(PassFormat.dateTime(it))
                    } ?: append(" · 아직 받지 않음 (아래로 당겨 새로고침)")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** TLE 누락 등으로 계산하지 못한 위성 안내 */
@Composable
private fun ProblemStrip(state: PassListUiState) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.width(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "계산하지 못한 위성 ${state.problems.size}기",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            state.problems.take(5).forEach { problem ->
                Text(
                    text = "· ${problem.satelliteName} — ${problem.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
