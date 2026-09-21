package kr.contec.satpass.domain.usecase

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kr.contec.satpass.data.local.entity.SatelliteEntity
import kr.contec.satpass.data.local.entity.TleEntity
import kr.contec.satpass.data.repository.TleRepository
import kr.contec.satpass.domain.OrbitTime.toDate
import kr.contec.satpass.domain.OrbitTime.toInstant
import kr.contec.satpass.domain.model.ObserverLocation
import kr.contec.satpass.domain.model.SatellitePass
import kr.contec.satpass.orbit.GroundStationPosition
import kr.contec.satpass.orbit.PassPredictor
import kr.contec.satpass.orbit.SatPassTime
import kr.contec.satpass.orbit.SatPos
import kr.contec.satpass.orbit.TLE
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * 등록·선택된 위성들의 통과 스케줄을 계산한다.
 *
 * 계산 방식은 다음과 같다.
 *  - `PassPredictor.getPasses(오늘 00:00 UTC, 24 * 기간, false)` 로 패스 목록을 얻는다.
 *  - 최대 고각 시각은 AOS~LOS 의 중간 시점으로 잡는다.
 *  - 최대 고각은 그 시점 ±1분을 1초 간격으로 훑어 최댓값을 쓴다.
 *  - 중간 방위각은 최대 고각 시점의 방위각을 쓴다.
 *  - AOS 가 (오늘 + 기간) 00:00 이후인 패스는 버린다.
 */
class PredictPassesUseCase(
    private val tleRepository: TleRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    companion object {
        private const val TAG = "PredictPassesUseCase"

        /**
         * 위성 1기당 계산 제한 시간.
         * 기간이 길면 계산이 오래 걸릴 수 있어 넉넉히 준다.
         */
        private const val PER_SATELLITE_TIMEOUT_MS = 15_000L
    }

    /**
     * @param satellites 계산 대상 위성 (위성 목록 화면에서 선택된 것들)
     * @param observer 관측자 위치
     * @param days 앞으로 며칠분을 계산할지
     * @param minElevationDeg 이 고각 미만의 패스는 제외
     * @param now 현재 시각. LOS 가 이미 지난 패스는 제외한다.
     * @return AOS 시간순으로 정렬된 패스 목록
     */
    suspend operator fun invoke(
        satellites: List<SatelliteEntity>,
        observer: ObserverLocation,
        days: Int,
        minElevationDeg: Double,
        now: Instant = Instant.now(),
    ): Result = withContext(computeDispatcher) {
        if (satellites.isEmpty()) return@withContext Result(emptyList(), emptyList())

        val tleByNoradId = tleRepository.getByNoradIds(satellites.map { it.noradId })
            .associateBy { it.noradId }

        val passes = ArrayList<SatellitePass>()
        val problems = ArrayList<SatelliteProblem>()

        for (satellite in satellites) {
            val tle = tleByNoradId[satellite.noradId]
            if (tle == null) {
                problems += SatelliteProblem(
                    satellite.noradId,
                    satellite.name,
                    "TLE 캐시에 없습니다. 목록을 당겨 새로고침하세요.",
                )
                continue
            }

            val computed = withTimeoutOrNull(PER_SATELLITE_TIMEOUT_MS) {
                runCatching { predictOne(tle, observer, days) }
            }

            when {
                computed == null -> problems += SatelliteProblem(
                    satellite.noradId,
                    satellite.name,
                    "계산 시간이 초과되었습니다.",
                )

                computed.isFailure -> {
                    val e = computed.exceptionOrNull()
                    Log.w(TAG, "패스 계산 실패: ${satellite.name}", e)
                    problems += SatelliteProblem(
                        satellite.noradId,
                        satellite.name,
                        e?.localizedMessage ?: "패스를 계산할 수 없습니다.",
                    )
                }

                else -> passes += computed.getOrDefault(emptyList())
            }
        }

        val visible = passes
            .filter { it.losTime.isAfter(now) }
            .filter { it.maxElevationDeg >= minElevationDeg }
            .sortedBy { it.aosTime }

        Result(passes = visible, problems = problems)
    }

    private fun predictOne(
        tle: TleEntity,
        observer: ObserverLocation,
        days: Int,
    ): List<SatellitePass> {
        val predictor = PassPredictor(
            TLE(tle.toTleLines()),
            GroundStationPosition(observer.latitude, observer.longitude, observer.altitudeMeters),
        )

        // "오늘 00:00 UTC" 부터 24 * 기간 시간을 훑는다.
        val startDate = LocalDate.now(ZoneOffset.UTC)
        val startInstant = startDate.atTime(LocalTime.MIDNIGHT).toInstant(ZoneOffset.UTC)
        val endInstant = startDate.plusDays(days.toLong()).atTime(LocalTime.MIDNIGHT)
            .toInstant(ZoneOffset.UTC)

        val satPassTimes: List<SatPassTime> =
            predictor.getPasses(startInstant.toDate(), 24 * days, false)

        val tleEpoch = Instant.ofEpochMilli(tle.epochMillis)

        return satPassTimes.mapNotNull { satPassTime ->
            val aos = satPassTime.startTime.toInstant()
            val los = satPassTime.endTime.toInstant()
            if (!aos.isBefore(endInstant)) return@mapNotNull null

            val maxElevationTime = aos.plusSeconds(Duration.between(aos, los).seconds / 2)

            SatellitePass(
                noradId = tle.noradId,
                satelliteName = tle.satelliteName,
                aosTime = aos,
                losTime = los,
                maxElevationTime = maxElevationTime,
                maxElevationDeg = maxElevationAt(predictor, maxElevationTime),
                aosAzimuthDeg = satPassTime.aosAzimuth.toDouble(),
                losAzimuthDeg = satPassTime.losAzimuth.toDouble(),
                centerAzimuthDeg = centerAzimuthAt(predictor, maxElevationTime),
                tleEpoch = tleEpoch,
            )
        }
    }

    /**
     * 최대 고각. 지정 시각 ±1분을 1초 간격으로 훑어 최댓값을 쓴다.
     */
    private fun maxElevationAt(predictor: PassPredictor, time: Instant): Double {
        val degrees = Math.toDegrees(
            predictor.getPositions(time.toDate(), 1, 1, 1)
                .maxOfOrNull(SatPos::getElevation) ?: 0.0
        )
        return roundTo2(degrees)
    }

    /** 최대 고각 시점의 방위각. */
    private fun centerAzimuthAt(predictor: PassPredictor, time: Instant): Double =
        roundTo2(Math.toDegrees(predictor.getSatPos(time.toDate()).azimuth))

    private fun roundTo2(value: Double): Double = Math.round(value * 100) / 100.0

    /**
     * 계산 결과.
     *
     * @property passes 표시할 패스 목록
     * @property problems 계산하지 못한 위성과 이유
     */
    data class Result(
        val passes: List<SatellitePass>,
        val problems: List<SatelliteProblem>,
    )

    data class SatelliteProblem(
        val noradId: Int,
        val satelliteName: String,
        val reason: String,
    )
}
