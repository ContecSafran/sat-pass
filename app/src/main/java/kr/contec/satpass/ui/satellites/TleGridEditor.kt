package kr.contec.satpass.ui.satellites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.contec.satpass.domain.TleValidator
import kr.contec.satpass.ui.theme.PassActive

/** TLE 한 줄의 문자 수 */
const val TLE_LINE_LENGTH = 69

/** 체크섬이 들어가는 칸 (0-based) */
private const val CHECKSUM_INDEX = 68

/**
 * TLE 형식상 항상 공백인 자리.
 * 입력을 받지 않고 미리 공백으로 채워 둔다.
 */
private val LINE1_FIXED_SPACES = setOf(1, 8, 17, 32, 43, 52, 61, 63)
private val LINE2_FIXED_SPACES = setOf(1, 7, 16, 25, 33, 42, 51)

/**
 * 셀 하나의 기본 크기. 69칸이 화면을 넘으므로 가로 스크롤로 본다.
 *
 * 기기 글자 크기를 키운 사용자에게는 글자가 잘리지 않도록 칸도 같이 키운다.
 * (칸이 커져도 가로 스크롤이라 화면 폭에는 영향이 없다)
 */
private val BASE_CELL_WIDTH = 20.dp
private val BASE_CELL_HEIGHT = 32.dp

/** 칸이 지나치게 커지지 않도록 배율에 상한을 둔다. */
private const val MAX_CELL_SCALE = 1.6f

/** 글자 크기 설정을 반영한 칸 크기 */
private data class CellSize(val width: Dp, val height: Dp)

@Composable
private fun rememberCellSize(): CellSize {
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, MAX_CELL_SCALE)
    return remember(fontScale) {
        CellSize(
            width = BASE_CELL_WIDTH * fontScale,
            height = BASE_CELL_HEIGHT * fontScale,
        )
    }
}

/**
 * TLE 한 줄의 입력 상태.
 *
 * 칸마다 한 글자씩 들고 있어서 "아직 입력하지 않은 칸"(빈 문자열)과
 * "의도적인 공백"(" ")을 구분할 수 있다. 체크섬 자동 계산은 이 구분에 기댄다.
 */
class TleLineState(
    val lineNumber: Int,
    initial: String = "",
) {
    private val fixedSpaces = if (lineNumber == 1) LINE1_FIXED_SPACES else LINE2_FIXED_SPACES

    val cells = androidx.compose.runtime.mutableStateListOf<String>().apply {
        repeat(TLE_LINE_LENGTH) { add("") }
    }

    init {
        reset(initial)
    }

    /** 입력을 받지 않는 칸인지 (줄 번호·고정 공백·체크섬) */
    fun isReadOnly(index: Int): Boolean =
        index == 0 || index in fixedSpaces || index == CHECKSUM_INDEX

    /** 체크섬을 뺀 나머지 칸이 모두 채워졌는지 */
    val isBodyComplete: Boolean
        get() = (0 until CHECKSUM_INDEX).all { cells[it].isNotEmpty() }

    /**
     * 자동 계산된 체크섬. 빈 칸이 하나라도 있으면 null.
     * (덜 입력된 상태에서 잘못된 체크섬이 박히는 것을 막는다)
     */
    val autoChecksum: Int?
        get() = if (isBodyComplete) TleValidator.computeChecksum(text(includeChecksum = false)) else null

    fun text(includeChecksum: Boolean = true): String = buildString {
        val last = if (includeChecksum) TLE_LINE_LENGTH else CHECKSUM_INDEX
        for (i in 0 until last) {
            // 아직 입력하지 않은 칸은 공백으로 취급해 길이를 69 로 맞춘다.
            append(cells[i].ifEmpty { " " })
        }
    }

    /** 체크섬 칸을 자동 계산 값으로 맞춘다. */
    fun syncChecksum() {
        cells[CHECKSUM_INDEX] = autoChecksum?.toString() ?: ""
    }

    fun set(index: Int, value: String) {
        cells[index] = value
        syncChecksum()
    }

    /** 줄 전체를 [line] 으로 채운다. 빈 문자열이면 초기 상태로 되돌린다. */
    fun reset(line: String) {
        repeat(TLE_LINE_LENGTH) { i ->
            cells[i] = when {
                line.length == TLE_LINE_LENGTH -> line[i].toString()
                i == 0 -> lineNumber.toString()
                i in fixedSpaces -> " "
                else -> ""
            }
        }
        if (line.length == TLE_LINE_LENGTH) syncChecksum()
    }

    /** 다음으로 입력할 칸. 없으면 null */
    fun nextEditable(from: Int): Int? =
        (from + 1 until CHECKSUM_INDEX).firstOrNull { !isReadOnly(it) }

    /** 이전 입력 칸. 없으면 null */
    fun previousEditable(from: Int): Int? =
        (from - 1 downTo 0).firstOrNull { !isReadOnly(it) }
}

/**
 * TLE 한 줄을 칸 단위로 입력받는 편집기.
 *
 * - 69칸을 가로로 늘어놓고 좌우로 스크롤한다. (세로로 긴 화면에서 줄바꿈되지 않도록)
 * - 한 글자를 넣으면 자동으로 다음 칸으로 넘어가고, 빈 칸에서 백스페이스를 누르면 앞 칸으로 간다.
 * - 체크섬 칸은 직접 입력할 수 없고, 나머지가 모두 채워졌을 때만 자동으로 채워진다.
 *
 * @param onMoveToNextLine 마지막 칸을 채웠을 때 호출 (다음 줄로 넘어가기 위함)
 */
@Composable
fun TleLineEditor(
    label: String,
    state: TleLineState,
    onMoveToNextLine: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val focusRequesters = remember(state) { List(TLE_LINE_LENGTH) { FocusRequester() } }
    var focusedIndex by remember(state) { mutableIntStateOf(-1) }

    val cellSize = rememberCellSize()
    val density = LocalDensity.current
    val cellWidthPx = with(density) { cellSize.width.toPx() }

    // 포커스가 화면 밖으로 나가지 않도록 따라 스크롤한다.
    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) {
            val target = (focusedIndex * cellWidthPx - cellWidthPx * 3).toInt()
            scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ChecksumBadge(state.autoChecksum)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        ) {
            // 열 번호 눈금 (5칸마다 표시)
            Row {
                repeat(TLE_LINE_LENGTH) { index ->
                    val column = index + 1
                    Box(
                        modifier = Modifier.width(cellSize.width),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (column == 1 || column % 5 == 0) {
                            Text(
                                text = column.toString(),
                                style = TextStyle(fontSize = 8.sp, fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Row {
                repeat(TLE_LINE_LENGTH) { index ->
                    TleCell(
                        cellSize = cellSize,
                        state = state,
                        index = index,
                        focusRequester = focusRequesters[index],
                        isFocused = focusedIndex == index,
                        onFocusChanged = { if (it) focusedIndex = index },
                        onFilled = {
                            val next = state.nextEditable(index)
                            if (next != null) {
                                focusRequesters[next].requestFocus()
                            } else {
                                onMoveToNextLine?.invoke()
                            }
                        },
                        onBackspaceOnEmpty = {
                            state.previousEditable(index)?.let { previous ->
                                state.set(previous, "")
                                focusRequesters[previous].requestFocus()
                            }
                        },
                    )
                }
            }
        }
    }
}

/** 칸 하나 */
@Composable
private fun TleCell(
    cellSize: CellSize,
    state: TleLineState,
    index: Int,
    focusRequester: FocusRequester,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onFilled: () -> Unit,
    onBackspaceOnEmpty: () -> Unit,
) {
    val readOnly = state.isReadOnly(index)
    val isChecksum = index == CHECKSUM_INDEX
    val value = state.cells[index]

    val background = when {
        isChecksum -> MaterialTheme.colorScheme.primaryContainer
        readOnly -> MaterialTheme.colorScheme.surfaceContainerHigh
        value.isEmpty() -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    Box(
        modifier = Modifier
            .size(width = cellSize.width, height = cellSize.height)
            .padding(horizontal = 1.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (readOnly) {
            Text(
                text = value.ifEmpty { if (isChecksum) "?" else "" },
                style = cellTextStyle(),
                color = if (isChecksum) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        } else {
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    // 붙여넣기 등으로 여러 글자가 들어오면 마지막 글자만 쓴다.
                    val char = raw.lastOrNull()
                    if (char == null) {
                        state.set(index, "")
                    } else {
                        state.set(index, char.uppercaseChar().toString())
                        onFilled()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .onPreviewKeyEvent { event ->
                        val isBackspace = event.type == KeyEventType.KeyDown &&
                            event.key == Key.Backspace
                        if (isBackspace && value.isEmpty()) {
                            onBackspaceOnEmpty()
                            true
                        } else {
                            false
                        }
                    },
                textStyle = cellTextStyle().copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            )
        }
    }
}

@Composable
private fun cellTextStyle(): TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    textAlign = TextAlign.Center,
)

/** 체크섬 자동 계산 상태 표시 */
@Composable
private fun ChecksumBadge(checksum: Int?) {
    val filled = checksum != null
    val color = if (filled) PassActive else MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = if (filled) "체크섬 $checksum 자동 입력" else "빈 칸이 있어 체크섬 대기",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal,
    )
}
