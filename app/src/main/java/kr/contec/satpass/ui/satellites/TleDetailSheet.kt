package kr.contec.satpass.ui.satellites

import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.contec.satpass.data.local.entity.TleEntity
import kr.contec.satpass.ui.export.copyToClipboard
import kr.contec.satpass.ui.format.PassFormat
import java.time.Instant

/**
 * 등록된 위성 이름을 눌렀을 때 뜨는 TLE 상세 시트.
 *
 * 캐시에 저장된 3줄 TLE 를 그대로 보여 주고, 복사할 수 있게 한다.
 * TLE 는 열 위치가 의미를 갖는 고정폭 형식이라 줄바꿈 대신 가로 스크롤로 보여 준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TleDetailSheet(
    detail: TleDetailState,
    onCopied: () -> Unit,
    onSaveTle: (input: String, fallbackName: String) -> Unit,
    onDeleteManual: (noradId: Int, satelliteName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }

    // 수정할 때는 현재 TLE 를 채운 채로, 캐시에 없을 때는 빈 칸으로 연다.
    val loaded = detail as? TleDetailState.Loaded
    if (showEditor) {
        ManualTleDialog(
            onSave = onSaveTle,
            onDismiss = { showEditor = false },
            initialInput = loaded?.tle?.toTleLines()?.joinToString("\n").orEmpty(),
            initialName = detail.satelliteName,
        )
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = detail.satelliteName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "NORAD ${detail.noradId}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (detail) {
                is TleDetailState.Loading -> LoadingBlock()

                is TleDetailState.Missing -> {
                    MessageBlock(
                        "이 위성의 TLE 가 캐시에 없습니다.\n" +
                            "스케줄 탭에서 목록을 아래로 당겨 받거나, 직접 입력할 수 있습니다."
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showEditor = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.width(18.dp),
                            )
                            Text("TLE 직접 입력", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }

                is TleDetailState.Loaded -> LoadedBlock(
                    tle = detail.tle,
                    onCopy = {
                        copyToClipboard(context, detail.satelliteName, it)
                        // Android 13 부터는 시스템이 복사 확인 UI 를 띄우므로 중복 안내를 하지 않는다.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onCopied()
                    },
                    onEdit = { showEditor = true },
                    onDeleteManual = {
                        onDeleteManual(detail.tle.noradId, detail.tle.satelliteName)
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.width(28.dp))
    }
}

@Composable
private fun MessageBlock(text: String) {
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
                .padding(horizontal = 16.dp, vertical = 20.dp),
        )
    }
}

@Composable
private fun LoadedBlock(
    tle: TleEntity,
    onCopy: (String) -> Unit,
    onEdit: () -> Unit,
    onDeleteManual: () -> Unit,
) {
    val tleText = tle.toTleLines().joinToString("\n")

    if (tle.isManual) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "직접 입력한 TLE · 카탈로그 갱신 시 유지됩니다",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // TLE 는 열 위치가 의미를 가지므로 줄바꿈 없이 가로 스크롤로 보여 준다.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TleLine(index = 0, text = tle.satelliteName)
                TleLine(index = 1, text = tle.line1)
                TleLine(index = 2, text = tle.line2)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tle.isManual) {
            TextButton(onClick = onDeleteManual) {
                Text(
                    text = "삭제",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        TextButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                modifier = Modifier.width(18.dp),
            )
            Text(
                text = "수정",
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        TextButton(onClick = { onCopy(tleText) }) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                modifier = Modifier.width(18.dp),
            )
            Text(
                text = "복사",
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "궤도 정보",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoRow("TLE epoch", PassFormat.utcDateTime(Instant.ofEpochMilli(tle.epochMillis)))
        InfoRow("받아온 시각", PassFormat.dateTime(Instant.ofEpochMilli(tle.fetchedAt)))
        InfoRow("카탈로그 이름", tle.satelliteName)
    }
}

/** TLE 한 줄. 줄 번호를 앞에 붙여 어느 줄인지 알 수 있게 한다. */
@Composable
private fun TleLine(index: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            maxLines = 1,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
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
