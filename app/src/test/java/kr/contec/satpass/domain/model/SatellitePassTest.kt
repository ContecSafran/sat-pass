package kr.contec.satpass.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SatellitePassTest {

    private val aos = Instant.parse("2026-09-11T10:00:00Z")
    private val los = Instant.parse("2026-09-11T10:10:00Z")

    private val pass = SatellitePass(
        noradId = 25544,
        satelliteName = "ISS (ZARYA)",
        aosTime = aos,
        losTime = los,
        maxElevationTime = Instant.parse("2026-09-11T10:05:00Z"),
        maxElevationDeg = 62.5,
        aosAzimuthDeg = 20.0,
        losAzimuthDeg = 200.0,
        centerAzimuthDeg = 110.0,
        tleEpoch = null,
    )

    @Test
    fun `지속 시간은 AOS부터 LOS까지다`() {
        assertEquals(600L, pass.duration.seconds)
    }

    @Test
    fun `AOS 이전에는 진행 중이 아니다`() {
        val before = aos.minusSeconds(1)
        assertFalse(pass.isInProgress(before))
        assertNull(pass.progress(before))
    }

    @Test
    fun `LOS 시점은 진행 중이 아니다`() {
        assertFalse(pass.isInProgress(los))
        assertNull(pass.progress(los))
    }

    @Test
    fun `AOS 시점의 진행도는 0이다`() {
        assertTrue(pass.isInProgress(aos))
        assertEquals(0f, pass.progress(aos)!!, 0.0001f)
    }

    @Test
    fun `중간 시점의 진행도는 0_5다`() {
        val middle = aos.plusSeconds(300)
        assertEquals(0.5f, pass.progress(middle)!!, 0.0001f)
    }

    @Test
    fun `LOS 직전의 진행도는 1에 가깝다`() {
        val almostEnd = los.minusSeconds(1)
        assertEquals(0.9983f, pass.progress(almostEnd)!!, 0.001f)
    }
}
