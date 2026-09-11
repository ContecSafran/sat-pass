package kr.contec.satpass.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kr.contec.satpass.data.settings.SatPassSettings
import kr.contec.satpass.ui.format.PassFormat

/**
 * 설정 화면.
 *
 * - TLE 를 받아올 주소(기본 / 대체)를 직접 지정한다.
 * - 최소 갱신 간격을 정한다. 스케줄 목록을 당겨 새로고침해도 이 간격 안에서는 요청하지 않는다.
 * - 예측 기간과 최소 고각으로 목록에 보일 패스 범위를 조절한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onTleUrlChange: (String) -> Unit,
    onFallbackTleUrlChange: (String) -> Unit,
    onMinSyncIntervalChange: (Int) -> Unit,
    onPredictionDaysChange: (Int) -> Unit,
    onMinElevationChange: (Double) -> Unit,
    onResetUrls: () -> Unit,
    onSyncNow: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // 저장은 편집이 끝났을 때(포커스 이동/완료)만 하도록 로컬 상태로 들고 있는다.
    var tleUrl by remember(state.settings.tleSourceUrl) {
        mutableStateOf(state.settings.tleSourceUrl)
    }
    var fallbackUrl by remember(state.settings.fallbackTleSourceUrl) {
        mutableStateOf(state.settings.fallbackTleSourceUrl)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "tle-source") {
            SettingsCard(
                title = "TLE 가져오기",
                description = "궤도 데이터를 받아올 주소입니다. 3줄 형식(TLE)의 텍스트를 반환해야 합니다.",
            ) {
                OutlinedTextField(
                    value = tleUrl,
                    onValueChange = { tleUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("기본 주소") },
                    singleLine = false,
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                )

                OutlinedTextField(
                    value = fallbackUrl,
                    onValueChange = { fallbackUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("대체 주소 (기본 주소 실패 시)") },
                    singleLine = false,
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    supportingText = {
                        Text(
                            "비워 두면 대체 주소를 시도하지 않습니다.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            onTleUrlChange(tleUrl)
                            onFallbackTleUrlChange(fallbackUrl)
                            onSyncNow()
                        },
                        enabled = !state.isSyncing && tleUrl.isNotBlank(),
                    ) {
                        if (state.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("저장하고 지금 가져오기")
                    }
                    TextButton(onClick = onResetUrls) { Text("기본값") }
                }

                Text(
                    text = buildString {
                        append("캐시 ")
                        append(state.tleCachedCount)
                        append("건")
                        state.lastTleFetchedAt?.let {
                            append(" · 마지막 갱신 ")
                            append(PassFormat.dateTime(it))
                        } ?: append(" · 아직 받지 않음")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "sync-interval") {
            SettingsCard(
                title = "최소 갱신 간격",
                description = "스케줄 목록을 아래로 당겨 새로고침할 때, 마지막 갱신 후 이 시간이 " +
                    "지나지 않았으면 서버에 다시 요청하지 않습니다.",
            ) {
                ChipRow(
                    options = SatPassSettings.SYNC_INTERVAL_OPTIONS,
                    selected = state.settings.minSyncIntervalHours,
                    label = { "${it}시간" },
                    onSelect = onMinSyncIntervalChange,
                )
            }
        }

        item(key = "prediction-days") {
            SettingsCard(
                title = "예측 기간",
                description = "앞으로 며칠분의 통과를 계산할지 정합니다. 기간이 길면 계산 시간이 늘어납니다.",
            ) {
                ChipRow(
                    options = SatPassSettings.PREDICTION_DAY_OPTIONS,
                    selected = state.settings.predictionDays,
                    label = { "${it}일" },
                    onSelect = onPredictionDaysChange,
                )
            }
        }

        item(key = "min-elevation") {
            SettingsCard(
                title = "최소 고각",
                description = "최대 고각이 이 값보다 낮은 통과는 목록에서 숨깁니다.",
            ) {
                ChipRow(
                    options = SatPassSettings.MIN_ELEVATION_OPTIONS,
                    selected = state.settings.minElevationDeg,
                    label = { if (it == 0.0) "전체" else "${it.toInt()}° 이상" },
                    onSelect = onMinElevationChange,
                )
            }
        }

        item(key = "about") {
            SettingsCard(
                title = "궤도 계산",
                description = "SGP4 기반 predict4java 를 사용합니다. AOS/LOS·최대 고각·방위각 계산 " +
                    "방식은 LCAM 서버와 동일합니다.",
            ) {}
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        text = label(option),
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}
