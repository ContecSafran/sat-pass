package kr.contec.satpass.ui.satellites

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kr.contec.satpass.domain.TleValidator
import kr.contec.satpass.ui.theme.PassActive

/**
 * TLE 를 칸 단위로 직접 입력·수정하는 화면.
 *
 * LCAM 웹의 TLE 입력 화면과 같은 방식으로 69칸을 늘어놓되,
 * 세로로 긴 휴대폰에서 줄이 접히지 않도록 가로 스크롤로 보여 준다.
 * 체크섬은 직접 넣지 않고 나머지 칸이 모두 채워졌을 때 자동으로 계산해 넣는다.
 *
 * 화면이 좁아 다이얼로그 대신 전체 화면으로 띄운다.
 *
 * @param initialInput 미리 채워 둘 TLE 텍스트 (3줄). 수정할 때 쓴다.
 * @param initialName 미리 채워 둘 위성 이름
 */
@Composable
fun ManualTleDialog(
    onSave: (input: String, fallbackName: String) -> Unit,
    onDismiss: () -> Unit,
    initialInput: String = "",
    initialName: String = "",
) {
    val context = LocalContext.current
    val editing = initialInput.isNotBlank()

    val initialLines = remember(initialInput) {
        initialInput.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
    }

    var satelliteName by remember { mutableStateOf(initialName) }
    val line1 = remember(initialInput) {
        TleLineState(lineNumber = 1, initial = initialLines.getOrNull(1).orEmpty())
    }
    val line2 = remember(initialInput) {
        TleLineState(lineNumber = 2, initial = initialLines.getOrNull(2).orEmpty())
    }

    // 두 줄이 모두 채워졌을 때만 최종 검사를 돌린다.
    val bothComplete = line1.isBodyComplete && line2.isBodyComplete
    val result = remember(bothComplete, line1.cells.toList(), line2.cells.toList(), satelliteName) {
        if (!bothComplete) null
        else TleValidator.validate(
            "${satelliteName.ifBlank { "SATELLITE" }}\n${line1.text()}\n${line2.text()}",
            satelliteName,
        )
    }
    val valid = result as? TleValidator.Result.Valid

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // 상단 바
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (editing) "TLE 수정" else "TLE 직접 입력",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            pasteFromClipboard(context)?.let { pasted ->
                                applyPastedTle(pasted, line1, line2) { name ->
                                    if (name.isNotBlank()) satelliteName = name
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Outlined.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp),
                        )
                        Text("붙여넣기", modifier = Modifier.padding(start = 6.dp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "닫기")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "칸마다 한 글자씩 입력하면 다음 칸으로 자동으로 넘어갑니다. " +
                            "체크섬 칸은 나머지가 모두 채워지면 자동으로 계산됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = satelliteName,
                        onValueChange = { satelliteName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("위성 이름") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )

                    TleLineEditor(label = "Line 1", state = line1)
                    TleLineEditor(label = "Line 2", state = line2)

                    when (result) {
                        null -> StatusBox(
                            ok = false,
                            neutral = true,
                            lines = listOf("두 줄을 모두 채우면 검사 결과가 표시됩니다."),
                        )

                        is TleValidator.Result.Invalid -> StatusBox(
                            ok = false,
                            lines = result.errors,
                        )

                        is TleValidator.Result.Valid -> StatusBox(
                            ok = true,
                            lines = buildList {
                                add("${result.satelliteName} (NORAD ${result.noradId})")
                                addAll(result.warnings)
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(
                        onClick = {
                            onSave(
                                "${satelliteName.ifBlank { "SATELLITE" }}\n${line1.text()}\n${line2.text()}",
                                satelliteName,
                            )
                            onDismiss()
                        },
                        enabled = valid != null,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("저장")
                    }
                }
            }
        }
    }
}

/** 클립보드의 TLE 텍스트를 칸에 채운다. */
private fun applyPastedTle(
    pasted: String,
    line1: TleLineState,
    line2: TleLineState,
    onName: (String) -> Unit,
) {
    val lines = pasted.lineSequence()
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() }
        .toList()

    // 3줄이면 첫 줄이 이름, 2줄이면 본문만 있는 것으로 본다.
    val (name, body1, body2) = when (lines.size) {
        3 -> Triple(lines[0].trim(), lines[1], lines[2])
        2 -> Triple("", lines[0], lines[1])
        else -> return
    }

    if (body1.length != TLE_LINE_LENGTH || body2.length != TLE_LINE_LENGTH) return

    onName(name)
    line1.reset(body1)
    line2.reset(body2)
}

private fun pasteFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

/** 검사 결과 표시 */
@Composable
private fun StatusBox(
    ok: Boolean,
    lines: List<String>,
    neutral: Boolean = false,
) {
    val tint = when {
        neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        ok -> PassActive
        else -> MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!neutral) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        text = if (ok) "확인 완료" else "확인 필요",
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            lines.forEach { line ->
                Text(
                    text = if (neutral) line else "· $line",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
