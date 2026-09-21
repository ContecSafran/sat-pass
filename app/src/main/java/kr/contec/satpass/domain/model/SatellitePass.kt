package kr.contec.satpass.domain.model

import java.time.Duration
import java.time.Instant

/**
 * 계산된 위성 통과(pass) 1건.
 *
 * @property noradId NORAD 카탈로그 번호
 * @property satelliteName 위성 이름
 * @property aosTime AOS(Acquisition of Signal) 시각
 * @property losTime LOS(Loss of Signal) 시각
 * @property maxElevationTime 최대 고각 시각 (AOS~LOS 중간 시점)
 * @property maxElevationDeg 최대 고각(도)
 * @property aosAzimuthDeg AOS 방위각(도)
 * @property losAzimuthDeg LOS 방위각(도)
 * @property centerAzimuthDeg 최대 고각 시점의 방위각(도)
 * @property tleEpoch 계산에 사용한 TLE 의 epoch
 */
data class SatellitePass(
    val noradId: Int,
    val satelliteName: String,
    val aosTime: Instant,
    val losTime: Instant,
    val maxElevationTime: Instant,
    val maxElevationDeg: Double,
    val aosAzimuthDeg: Double,
    val losAzimuthDeg: Double,
    val centerAzimuthDeg: Double,
    val tleEpoch: Instant?,
) {
    /** 통과 지속 시간 */
    val duration: Duration get() = Duration.between(aosTime, losTime)

    /** [now] 가 AOS~LOS 구간 안에 있는지 */
    fun isInProgress(now: Instant): Boolean = !now.isBefore(aosTime) && now.isBefore(losTime)

    /**
     * 진행 중인 패스의 진행도(0f~1f). 진행 중이 아니면 null.
     * 리스트 아이템 안에 좌→우 진행 바를 그리는 데 쓴다.
     */
    fun progress(now: Instant): Float? {
        if (!isInProgress(now)) return null
        val total = duration.toMillis()
        if (total <= 0L) return null
        val elapsed = Duration.between(aosTime, now).toMillis()
        return (elapsed.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }
}
