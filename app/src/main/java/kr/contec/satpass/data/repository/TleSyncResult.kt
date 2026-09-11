package kr.contec.satpass.data.repository

import java.time.Instant

/** TLE 갱신 시도 결과. */
sealed interface TleSyncResult {

    /**
     * 갱신 성공.
     *
     * @property count 저장된 TLE 건수
     * @property sourceUrl 실제로 데이터를 받아온 주소
     * @property usedFallback 기본 주소가 실패해서 폴백 주소를 썼는지
     */
    data class Updated(
        val count: Int,
        val sourceUrl: String,
        val usedFallback: Boolean,
    ) : TleSyncResult

    /**
     * 최소 갱신 간격이 지나지 않아 요청하지 않음.
     *
     * @property lastFetchedAt 마지막 갱신 시각
     * @property nextAvailableAt 다음 갱신이 가능해지는 시각
     */
    data class Skipped(
        val lastFetchedAt: Instant,
        val nextAvailableAt: Instant,
    ) : TleSyncResult

    /**
     * 모든 주소에서 실패.
     *
     * @property message 사용자에게 보여줄 메시지
     * @property cachedCount 그대로 사용할 기존 캐시 건수
     */
    data class Failed(
        val message: String,
        val cachedCount: Int,
    ) : TleSyncResult
}
