package kr.contec.satpass.data.remote

import android.util.Log
import kr.contec.satpass.data.local.entity.TleEntity
import kr.contec.satpass.domain.OrbitTime
import kr.contec.satpass.orbit.TLE

/**
 * 3줄(TLE) 형식 텍스트를 [TleEntity] 목록으로 바꾼다.
 *
 * 3줄씩 묶어 predict4java `TLE` 로 파싱해 보고 실패한 위성만 건너뛴다.
 * (형식이 깨진 한 건 때문에 전체 갱신이 실패하지 않게 하는 것이 목적)
 */
object TleTextParser {

    private const val TAG = "TleTextParser"

    /**
     * @param body 3줄 단위 TLE 텍스트
     * @param fetchedAt 수신 시각 (epoch millis)
     * @return 파싱에 성공한 TLE 목록. 실패 건은 제외된다.
     */
    fun parse(body: String, fetchedAt: Long): List<TleEntity> {
        val lines = body.lineSequence()
            .map { it.trimEnd('\r', ' ') }
            .filter { it.isNotBlank() }
            .toList()

        val result = ArrayList<TleEntity>(lines.size / 3)
        var skipped = 0

        for (i in 0 until lines.size / 3) {
            val group = arrayOf(lines[3 * i], lines[3 * i + 1], lines[3 * i + 2])
            try {
                val tle = TLE(group)
                result += TleEntity(
                    noradId = tle.catnum,
                    satelliteName = tle.name,
                    line1 = group[1],
                    line2 = group[2],
                    epochMillis = OrbitTime.tleEpochToInstant(tle.epoch).toEpochMilli(),
                    fetchedAt = fetchedAt,
                )
            } catch (e: Exception) {
                skipped++
                Log.w(TAG, "잘못된 TLE 건너뜀: name='${group[0]}' reason=$e")
            }
        }

        if (skipped > 0) {
            Log.i(TAG, "TLE 파싱 완료: 성공 ${result.size}건, 건너뜀 ${skipped}건")
        }
        return result
    }
}
