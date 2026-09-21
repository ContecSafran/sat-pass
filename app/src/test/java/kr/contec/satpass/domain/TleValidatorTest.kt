package kr.contec.satpass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TleValidatorTest {

    // LCAM docs 의 CONTECSAT-1 예시 TLE
    private val name = "CONTECSAT-1"
    private val line1 = "1 59117U 24043V   25016.91041735  .00012931  00000+0  53778-3 0  9998"
    private val line2 = "2 59117  97.5006 145.4981 0012470 262.8453  97.1366 15.23844556 48285"

    private val threeLine = "$name\n$line1\n$line2"

    @Test
    fun `정상 TLE는 검사를 통과한다`() {
        val result = TleValidator.validate(threeLine)

        assertTrue("검사를 통과해야 한다: $result", result is TleValidator.Result.Valid)
        result as TleValidator.Result.Valid
        assertEquals(59117, result.noradId)
        assertEquals(name, result.satelliteName)
    }

    @Test
    fun `이름 줄이 없으면 대체 이름을 쓴다`() {
        val result = TleValidator.validate("$line1\n$line2", fallbackName = "내 위성")

        assertTrue(result is TleValidator.Result.Valid)
        result as TleValidator.Result.Valid
        assertEquals("내 위성", result.satelliteName)
        assertTrue("이름이 없었다는 안내가 있어야 한다", result.warnings.isNotEmpty())
    }

    @Test
    fun `이름과 대체 이름이 모두 없으면 NORAD 번호로 채운다`() {
        val result = TleValidator.validate("$line1\n$line2")

        assertTrue(result is TleValidator.Result.Valid)
        result as TleValidator.Result.Valid
        assertEquals("NORAD 59117", result.satelliteName)
    }

    @Test
    fun `체크섬이 틀리면 오류로 잡는다`() {
        // 1번 줄의 마지막 체크섬 8 을 7 로 바꾼다.
        val broken = line1.dropLast(1) + "7"
        val result = TleValidator.validate("$name\n$broken\n$line2")

        assertTrue(result is TleValidator.Result.Invalid)
        result as TleValidator.Result.Invalid
        assertTrue(
            "체크섬 오류가 보고돼야 한다: ${result.errors}",
            result.errors.any { it.contains("체크섬") && it.contains("1번 줄") },
        )
    }

    @Test
    fun `줄 수가 맞지 않으면 오류로 잡는다`() {
        val result = TleValidator.validate(line1)
        assertTrue(result is TleValidator.Result.Invalid)
    }

    @Test
    fun `줄 길이가 다르면 오류로 잡는다`() {
        val short = line1.dropLast(5)
        val result = TleValidator.validate("$name\n$short\n$line2")

        assertTrue(result is TleValidator.Result.Invalid)
        result as TleValidator.Result.Invalid
        assertTrue(result.errors.any { it.contains("길이") })
    }

    @Test
    fun `두 줄의 카탈로그 번호가 다르면 오류로 잡는다`() {
        // 2번 줄의 카탈로그 번호를 바꾸고 체크섬을 다시 맞춘다.
        val mismatched = TleValidator.withCorrectedChecksum(
            "2 59118" + line2.substring(7)
        )
        val result = TleValidator.validate("$name\n$line1\n$mismatched")

        assertTrue(result is TleValidator.Result.Invalid)
        result as TleValidator.Result.Invalid
        assertTrue(result.errors.any { it.contains("카탈로그 번호") })
    }

    @Test
    fun `체크섬 계산은 실제 TLE 값과 일치한다`() {
        assertEquals(line1.last().digitToInt(), TleValidator.computeChecksum(line1))
        assertEquals(line2.last().digitToInt(), TleValidator.computeChecksum(line2))
    }

    @Test
    fun `체크섬 보정은 올바른 값을 채워 넣는다`() {
        val broken = line1.dropLast(1) + "0"
        assertEquals(line1, TleValidator.withCorrectedChecksum(broken))
    }

    @Test
    fun `빼기 기호는 체크섬에서 1로 센다`() {
        // 숫자 없이 빼기 기호 3개 + 나머지는 공백인 68자 → 합 3
        val line = "-".repeat(3) + " ".repeat(65) + "3"
        assertEquals(3, TleValidator.computeChecksum(line))
    }
}
