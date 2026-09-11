package kr.contec.satpass.ui.satellites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.contec.satpass.data.local.entity.SatelliteEntity
import kr.contec.satpass.data.local.entity.TleEntity
import kr.contec.satpass.ui.format.PassFormat
import java.time.Instant

/**
 * 위성 목록 화면.
 *
 * 위쪽에서 TLE 캐시를 검색해 관심 위성을 추가하고,
 * 아래 목록에서 스위치로 스케줄에 포함할 위성을 고른다.
 */
@Composable
fun SatelliteScreen(
    state: SatelliteUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onAdd: (TleEntity) -> Unit,
    onRemove: (SatelliteEntity) -> Unit,
    onSelectedChange: (SatelliteEntity, Boolean) -> Unit,
    onSetAllSelected: (Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("위성 이름 또는 NORAD ID 검색") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = onClearQuery) {
                            Icon(Icons.Outlined.Close, contentDescription = "검색어 지우기")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
            )
        }

        if (state.tleCachedCount == 0) {
            item(key = "no-cache") {
                InfoStrip(
                    "TLE 캐시가 비어 있어 검색할 수 없습니다.\n" +
                        "스케줄 탭에서 목록을 아래로 당겨 TLE 를 먼저 받아 주세요."
                )
            }
        }

        // 검색 결과
        if (state.query.isNotBlank()) {
            item(key = "search-header") {
                SectionHeader(
                    title = "검색 결과",
                    trailing = if (state.isSearching) null else "${state.searchResults.size}건",
                )
            }

            when {
                state.isSearching -> item(key = "searching") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.width(24.dp))
                    }
                }

                state.searchResults.isEmpty() && state.tleCachedCount > 0 ->
                    item(key = "no-result") {
                        InfoStrip("'${state.query}' 에 해당하는 위성이 없습니다.")
                    }

                else -> items(
                    items = state.searchResults,
                    key = { "result-${it.noradId}" },
                ) { tle ->
                    SearchResultRow(
                        tle = tle,
                        alreadyAdded = tle.noradId in state.registeredIds,
                        onAdd = { onAdd(tle) },
                    )
                }
            }
        }

        // 등록된 위성
        item(key = "registered-header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "등록된 위성",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${state.registered.size}기 중 ${state.selectedCount}기 선택",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.registered.isNotEmpty()) {
                    val allSelected = state.selectedCount == state.registered.size
                    TextButton(onClick = { onSetAllSelected(!allSelected) }) {
                        Text(if (allSelected) "전체 해제" else "전체 선택")
                    }
                }
            }
        }

        if (state.registered.isEmpty()) {
            item(key = "registered-empty") {
                InfoStrip("아직 등록된 위성이 없습니다. 위에서 검색해 추가해 주세요.")
            }
        } else {
            items(
                items = state.registered,
                key = { "registered-${it.noradId}" },
            ) { satellite ->
                RegisteredSatelliteRow(
                    satellite = satellite,
                    onSelectedChange = { onSelectedChange(satellite, it) },
                    onRemove = { onRemove(satellite) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    tle: TleEntity,
    alreadyAdded: Boolean,
    onAdd: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tle.satelliteName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "NORAD ${tle.noradId} · epoch " +
                        PassFormat.utcDateTime(Instant.ofEpochMilli(tle.epochMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (alreadyAdded) {
                Text(
                    text = "등록됨",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 10.dp),
                )
            } else {
                IconButton(onClick = onAdd) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "${tle.satelliteName} 추가",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RegisteredSatelliteRow(
    satellite: SatelliteEntity,
    onSelectedChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = satellite.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (satellite.selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (satellite.selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "NORAD ${satellite.noradId}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = satellite.selected,
                onCheckedChange = onSelectedChange,
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "${satellite.name} 삭제",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InfoStrip(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
        )
    }
}
