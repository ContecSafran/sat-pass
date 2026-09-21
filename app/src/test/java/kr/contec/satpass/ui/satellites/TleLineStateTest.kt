package kr.contec.satpass.ui.satellites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 칸 단위 TLE 입력 상태와 체크섬 자동 계산 동작 확인.
 */
class TleLineStateTest {

    private val line1 =
        "1 59117U 24043V   25016.91041735  .00012931  00000+0  53778-3 0  9998"
    private val line2 =
        "2 59117  97.5006 145.4981 0012470 262.8453  97.1366 15.23844556 48285"

    @Test
    fun `초기 상태는 줄 번호와 고정 공백만 채워져 있다`() {
        val state = TleLineState(lineNumber = 1)

        assertEquals("1", state.cells[0])
        assertEquals(" ", state.cells[1])
        assertEquals(" ", state.cells[8])
        assertEquals("", state.cells[2])
        assertFalse("아직 빈 칸이 있다", state.isBodyComplete)
    }

    @Test
    fun `빈 칸이 있으면 체크섬을 계산하지 않는다`() {
        val state = TleLineState(lineNumber = 1, initial = line1)

        // 한 칸을 비운다.
        state.set(20, "")

        assertFalse(state.isBodyComplete)
        assertNull("빈 칸이 있으면 체크섬은 null 이어야 한다", state.autoChecksum)
        assertEquals("체크섬 칸도 비워져야 한다", "", state.cells[68])
    }

    @Test
    fun `마지막 빈 칸을 채우면 체크섬이 자동으로 들어간다`() {
        val state = TleLineState(lineNumber = 1, initial = line1)
        val original = line1[20].toString()
        state.set(20, "")
        assertNull(state.autoChecksum)

        state.set(20, original)

        assertTrue(state.isBodyComplete)
        assertEquals(8, state.autoChecksum)
        assertEquals("8", state.cells[68])
    }

    @Test
    fun `자동 계산된 체크섬은 원본 TLE 의 값과 같다`() {
        val state1 = TleLineState(lineNumber = 1, initial = line1)
        val state2 = TleLineState(lineNumber = 2, initial = line2)

        assertEquals(line1.last().digitToInt(), state1.autoChecksum)
        assertEquals(line2.last().digitToInt(), state2.autoChecksum)
    }

    @Test
    fun `text 는 69자를 유지한다`() {
        val state = TleLineState(lineNumber = 1, initial = line1)
        assertEquals(69, state.text().length)
        assertEquals(line1, state.text())
    }

    @Test
    fun `덜 채워진 줄도 69자로 만들되 빈 칸은 공백이 된다`() {
        val state = TleLineState(lineNumber = 1)
        assertEquals(69, state.text().length)
    }

    @Test
    fun `읽기 전용 칸은 줄 번호와 고정 공백과 체크섬이다`() {
        val state = TleLineState(lineNumber = 1)

        assertTrue(state.isReadOnly(0))   // 줄 번호
        assertTrue(state.isReadOnly(1))   // 고정 공백
        assertTrue(state.isReadOnly(63))  // 고정 공백
        assertTrue(state.isReadOnly(68))  // 체크섬
        assertFalse(state.isReadOnly(2))
    }

    @Test
    fun `다음 입력 칸은 고정 공백을 건너뛴다`() {
        val state = TleLineState(lineNumber = 1)

        // index 0 다음은 고정 공백(1)을 건너뛴 2
        assertEquals(2, state.nextEditable(0))
        // index 7 다음은 고정 공백(8)을 건너뛴 9
        assertEquals(9, state.nextEditable(7))
    }

    @Test
    fun `이전 입력 칸도 고정 공백을 건너뛴다`() {
        val state = TleLineState(lineNumber = 1)

        // index 9 이전은 고정 공백(8)을 건너뛴 7
        assertEquals(7, state.previousEditable(9))
    }

    @Test
    fun `마지막 입력 칸 다음은 없다`() {
        val state = TleLineState(lineNumber = 1)
        // 67 이 마지막 입력 칸 (68 은 체크섬)
        assertNull(state.nextEditable(67))
    }

    @Test
    fun `2번 줄의 고정 공백 위치는 1번 줄과 다르다`() {
        val state = TleLineState(lineNumber = 2)

        assertTrue(state.isReadOnly(7))    // line2 고정 공백
        assertFalse(state.isReadOnly(8))   // line1 에서는 고정 공백이지만 line2 에서는 아님
        assertTrue(state.isReadOnly(51))
    }
}
