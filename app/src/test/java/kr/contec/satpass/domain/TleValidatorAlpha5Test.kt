package kr.contec.satpass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alpha-5 형식(카탈로그 번호 첫 자리를 알파벳으로 쓰는 표기) 지원 확인.
 *
 * 앞자리 숫자가 알파벳으로 바뀌면 그 자리는 체크섬 합에서 0 으로 세므로
 * 체크섬 값도 함께 바뀐다. 그 계산까지 맞는지 확인한다.
 */
class TleValidatorAlpha5Test {

    private val name = "CONTECSAT-1"

    // 일반(숫자) 카탈로그 번호 59117
    private val numericLine1 =
        "1 59117U 24043V   25016.91041735  .00012931  00000+0  53778-3 0  9998"
    private val numericLine2 =
        "2 59117  97.5006 145.4981 0012470 262.8453  97.1366 15.23844556 48285"

    // 앞자리 '5'(=5) 를 'A'(=0) 로 바꾼 Alpha-5 표기.
    // 체크섬 합이 5 줄어들므로 1번 줄은 8→3, 2번 줄은 5→0 이 된다.
    private val alpha5Line1 =
        "1 A9117U 24043V   25016.91041735  .00012931  00000+0  53778-3 0  9993"
    private val alpha5Line2 =
        "2 A9117  97.5006 145.4981 0012470 262.8453  97.1366 15.23844556 48280"

    @Test
    fun `Alpha-5 표기의 체크섬을 알파벳을 0으로 세어 계산한다`() {
        assertEquals(3, TleValidator.computeChecksum(alpha5Line1))
        assertEquals(0, TleValidator.computeChecksum(alpha5Line2))
    }

    @Test
    fun `Alpha-5 TLE 는 검사를 통과한다`() {
        val result = TleValidator.validate("$name\n$alpha5Line1\n$alpha5Line2")

        assertTrue("검사를 통과해야 한다: $result", result is TleValidator.Result.Valid)
    }

    @Test
    fun `Alpha-5 카탈로그 번호는 10만대 숫자로 해석된다`() {
        val result = TleValidator.validate("$name\n$alpha5Line1\n$alpha5Line2")
        result as TleValidator.Result.Valid

        // A = 10 → 100000 + 9117
        assertEquals(109117, result.noradId)
    }

    @Test
    fun `숫자 표기를 그대로 Alpha-5 로 바꾸면 다른 위성이 된다`() {
        val numeric = TleValidator.validate("$name\n$numericLine1\n$numericLine2")
        val alpha5 = TleValidator.validate("$name\n$alpha5Line1\n$alpha5Line2")

        numeric as TleValidator.Result.Valid
        alpha5 as TleValidator.Result.Valid

        assertEquals(59117, numeric.noradId)
        assertEquals(109117, alpha5.noradId)
    }

    @Test
    fun `Alpha-5 로 바꾸고 체크섬을 안 고치면 오류로 잡는다`() {
        // 카탈로그 번호만 바꾸고 원래 체크섬(8, 5)을 그대로 둔 경우
        val staleChecksum1 = alpha5Line1.dropLast(1) + "8"
        val staleChecksum2 = alpha5Line2.dropLast(1) + "5"

        val result = TleValidator.validate("$name\n$staleChecksum1\n$staleChecksum2")

        assertTrue(result is TleValidator.Result.Invalid)
        result as TleValidator.Result.Invalid
        assertEquals(2, result.errors.count { it.contains("체크섬") })
    }

    @Test
    fun `체크섬 자동 보정은 Alpha-5 에도 적용된다`() {
        val staleChecksum = alpha5Line1.dropLast(1) + "8"
        assertEquals(alpha5Line1, TleValidator.withCorrectedChecksum(staleChecksum))
    }

    @Test
    fun `Alpha-5 에서 제외되는 글자 I 와 O 는 건너뛴다`() {
        // A=10 … H=17, J=18 (I 제외), … N=22, P=23 (O 제외)
        assertEquals(100000 + 9117, catnumOf("A9117"))
        assertEquals(170000 + 9117, catnumOf("H9117"))
        assertEquals(180000 + 9117, catnumOf("J9117"))
        assertEquals(220000 + 9117, catnumOf("N9117"))
        assertEquals(230000 + 9117, catnumOf("P9117"))
        assertEquals(330000 + 9117, catnumOf("Z9117"))
    }

    /** 카탈로그 번호만 바꿔 넣고 체크섬을 맞춘 뒤 파싱된 NORAD ID 를 돌려준다. */
    private fun catnumOf(catalog: String): Int {
        val line1 = TleValidator.withCorrectedChecksum("1 $catalog" + numericLine1.substring(7))
        val line2 = TleValidator.withCorrectedChecksum("2 $catalog" + numericLine2.substring(7))
        val result = TleValidator.validate("$name\n$line1\n$line2")
        assertTrue("$catalog 는 통과해야 한다: $result", result is TleValidator.Result.Valid)
        return (result as TleValidator.Result.Valid).noradId
    }
}
