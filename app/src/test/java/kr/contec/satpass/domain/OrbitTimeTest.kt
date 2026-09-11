package kr.contec.satpass.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class OrbitTimeTest {

    /**
     * TLE epoch `25016.91041735` (2025년 16일째 0.91041735일) 가
     * 2025-01-16T21:51:00Z 부근으로 변환되어야 한다.
     * (LCAM docs 의 CONTECSAT-1 예시 TLE 값)
     */
    @Test
    fun `TLE epoch를 UTC 시각으로 변환한다`() {
        val actual = OrbitTime.tleEpochToInstant(25016.91041735)

        // 2025-01-16 + 0.91041735일 = 21시간 51분 0.06초
        val expected = Instant.parse("2025-01-16T21:51:00Z")
        val diffSeconds = Math.abs(actual.epochSecond - expected.epochSecond)

        assertEquals("변환 오차가 1초 이내여야 한다", 0L, diffSeconds)
    }

    @Test
    fun `2자리 연도 57 미만은 2000년대로 해석한다`() {
        val y2025 = OrbitTime.tleEpochToInstant(25001.0)
        assertEquals(2025, y2025.atZone(java.time.ZoneOffset.UTC).year)

        val y1998 = OrbitTime.tleEpochToInstant(98001.0)
        assertEquals(1998, y1998.atZone(java.time.ZoneOffset.UTC).year)
    }

    @Test
    fun `연중 1일째는 1월 1일이다`() {
        val instant = OrbitTime.tleEpochToInstant(25001.0)
        val date = instant.atZone(java.time.ZoneOffset.UTC).toLocalDate()
        assertEquals(1, date.monthValue)
        assertEquals(1, date.dayOfMonth)
    }
}
