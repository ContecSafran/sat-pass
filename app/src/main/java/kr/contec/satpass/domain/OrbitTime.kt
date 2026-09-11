package kr.contec.satpass.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * 궤도 계산에서 쓰는 시간 변환 모음.
 *
 * predict4java 가 [Date] 기반이라 [Instant] 와 오가는 변환이 필요하고,
 * TLE epoch 해석은 LCAM `TimeUtils.tleEpochToLocalDateTime` 과 같은 방식을 쓴다.
 * (predict4java 는 UTC 기준으로 계산하므로 모든 변환도 UTC 로 맞춘다.)
 */
object OrbitTime {

    fun Instant.toDate(): Date = Date.from(this)

    fun Date.toInstant(): Instant = Instant.ofEpochMilli(this.time)

    /**
     * TLE epoch(`YYDDD.DDDDDDDD` 를 `1000 * YY + DDD.DDDDDDDD` 형태로 합친 값)를
     * UTC 시각으로 변환한다.
     *
     * @param epoch predict4java `TLE.getEpoch()` 값
     */
    fun tleEpochToInstant(epoch: Double): Instant {
        var year = (epoch / 1000).toInt()
        // 2자리 연도 규칙: 57 보다 작으면 2000년대, 아니면 1900년대
        year += if (year < 57) 2000 else 1900

        val dayOfYear = epoch % 1000
        val wholeDays = dayOfYear.toInt()
        val fractionalDay = dayOfYear - wholeDays

        val base = LocalDateTime.of(year, 1, 1, 0, 0, 0).plusDays((wholeDays - 1).toLong())

        val secondsOfDay = fractionalDay * 24 * 60 * 60
        val seconds = secondsOfDay.toLong()
        val millis = ((secondsOfDay - seconds) * 1000).toLong()

        return base
            .plusSeconds(seconds)
            .plus(millis, ChronoUnit.MILLIS)
            .toInstant(ZoneOffset.UTC)
    }
}
