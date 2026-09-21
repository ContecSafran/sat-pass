package kr.contec.satpass.domain.usecase

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.contec.satpass.data.repository.TleRepository
import kr.contec.satpass.domain.OrbitTime.toDate
import kr.contec.satpass.domain.model.ObserverLocation
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.orbit.GroundStationPosition
import kr.contec.satpass.orbit.PassPredictor
import kr.contec.satpass.orbit.TLE
import java.time.Duration
import java.time.Instant

/**
 * 한 패스 구간의 위성 궤적(방위각/고각)을 계산한다.
 * 상세 화면의 스카이 플롯에 쓴다.
 *
 * 화면에 그리는 용도일 때는 표본 수를 제한해 간격을 자동으로 넓힌다.
 */
class GetPassTrackUseCase(
    private val tleRepository: TleRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    companion object {
        private const val TAG = "GetPassTrackUseCase"

        /** 그래프용 최대 표본 수 */
        private const val MAX_SAMPLES = 120
    }

    /**
     * @property time 표본 시각
     * @property azimuthDeg 방위각(도)
     * @property elevationDeg 고각(도)
     */
    data class TrackPoint(
        val time: Instant,
        val azimuthDeg: Double,
        val elevationDeg: Double,
    )

    /**
     * @param stepSeconds 표본 간격(초)
     * @param limitSamples true 면 그래프용으로 표본 수를 [MAX_SAMPLES] 이하로 줄인다.
     *   표(초 단위 목록)를 만들 때는 false 로 두어 [stepSeconds] 를 그대로 지킨다.
     */
    suspend operator fun invoke(
        pass: SatellitePass,
        observer: ObserverLocation,
        stepSeconds: Int = 1,
        limitSamples: Boolean = true,
    ): List<TrackPoint> = withContext(computeDispatcher) {
        val tle = tleRepository.getByNoradIds(listOf(pass.noradId)).firstOrNull()
            ?: return@withContext emptyList()

        try {
            val predictor = PassPredictor(
                TLE(tle.toTleLines()),
                GroundStationPosition(
                    observer.latitude,
                    observer.longitude,
                    observer.altitudeMeters,
                ),
            )

            val totalSeconds = Duration.between(pass.aosTime, pass.losTime).seconds
            if (totalSeconds <= 0) return@withContext emptyList()

            val step = if (limitSamples) {
                maxOf(stepSeconds.toLong(), totalSeconds / MAX_SAMPLES)
            } else {
                maxOf(1L, stepSeconds.toLong())
            }

            generateSequence(0L) { it + step }
                .takeWhile { it <= totalSeconds }
                .map { offset ->
                    val time = pass.aosTime.plusSeconds(offset)
                    val satPos = predictor.getSatPos(time.toDate())
                    TrackPoint(
                        time = time,
                        azimuthDeg = Math.toDegrees(satPos.azimuth),
                        elevationDeg = Math.toDegrees(satPos.elevation),
                    )
                }
                .toList()
        } catch (e: Exception) {
            Log.w(TAG, "궤적 계산 실패: ${pass.satelliteName}", e)
            emptyList()
        }
    }
}
