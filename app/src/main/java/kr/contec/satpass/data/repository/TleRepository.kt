package kr.contec.satpass.data.repository

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kr.contec.satpass.data.local.dao.TleDao
import kr.contec.satpass.data.local.entity.TleEntity
import kr.contec.satpass.data.remote.TleApi
import kr.contec.satpass.data.remote.TleTextParser
import kr.contec.satpass.data.settings.SettingsRepository
import kr.contec.satpass.domain.TleValidator
import java.time.Duration
import java.time.Instant

/**
 * TLE 캐시 관리.
 *
 * 갱신 정책은 다음과 같다.
 *  - 마지막 갱신 후 최소 간격(설정값, 기본 6시간)이 지나지 않으면 요청하지 않는다.
 *  - 받아온 카탈로그로 테이블 전체를 교체한다.
 *  - 요청이 실패하면 테이블을 건드리지 않고 기존 캐시를 계속 쓴다.
 *
 * 모바일에서는 자동 주기 갱신 대신 목록을 당겨서 새로고침할 때 [sync] 를 호출한다.
 */
class TleRepository(
    private val tleDao: TleDao,
    private val tleApi: TleApi,
    private val settingsRepository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    companion object {
        private const val TAG = "TleRepository"
    }

    val lastFetchedAt: Flow<Long?> = tleDao.observeLastFetchedAt()

    val cachedCount: Flow<Int> = tleDao.observeCount()

    suspend fun getByNoradIds(noradIds: List<Int>): List<TleEntity> = withContext(ioDispatcher) {
        if (noradIds.isEmpty()) emptyList() else tleDao.getByNoradIds(noradIds)
    }

    suspend fun getByNoradId(noradId: Int): TleEntity? = withContext(ioDispatcher) {
        tleDao.getByNoradId(noradId)
    }

    suspend fun search(query: String): List<TleEntity> = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) emptyList() else tleDao.search(trimmed)
    }

    /**
     * 사용자가 직접 입력한 TLE 를 저장한다.
     *
     * 체크섬을 포함한 형식 검사를 통과해야만 저장하며, 저장된 값은 `is_manual` 로 표시되어
     * CelesTrak 카탈로그를 새로 받아도 덮어쓰이지 않는다.
     *
     * @param input 붙여 넣은 TLE 텍스트 (2줄 또는 3줄)
     * @param fallbackName 이름 줄이 없을 때 쓸 이름
     * @return 검사 결과. [TleValidator.Result.Valid] 면 저장까지 완료된 상태다.
     */
    suspend fun saveManualTle(
        input: String,
        fallbackName: String = "",
        now: Instant = Instant.now(),
    ): TleValidator.Result = withContext(ioDispatcher) {
        when (val result = TleValidator.validate(input, fallbackName)) {
            is TleValidator.Result.Invalid -> result

            is TleValidator.Result.Valid -> {
                tleDao.insert(
                    TleEntity(
                        noradId = result.noradId,
                        satelliteName = result.satelliteName,
                        line1 = result.line1,
                        line2 = result.line2,
                        epochMillis = result.epochMillis,
                        fetchedAt = now.toEpochMilli(),
                        isManual = true,
                    )
                )
                Log.i(TAG, "수동 TLE 저장: ${result.satelliteName} (NORAD ${result.noradId})")
                result
            }
        }
    }

    /**
     * 수동 입력 TLE 를 지운다.
     * 이후 CelesTrak 을 갱신하면 카탈로그 값이 다시 채워진다.
     */
    suspend fun deleteManualTle(noradId: Int) = withContext(ioDispatcher) {
        tleDao.deleteManual(noradId)
    }

    /**
     * TLE 갱신.
     *
     * @param force true 면 최소 갱신 간격을 무시하고 바로 요청한다.
     *   (설정 화면에서 TLE 주소를 바꾼 직후처럼 즉시 받아와야 할 때 쓴다.)
     */
    suspend fun sync(force: Boolean = false, now: Instant = Instant.now()): TleSyncResult =
        withContext(ioDispatcher) {
            val settings = settingsRepository.current()
            val lastFetchedAt = tleDao.getLastFetchedAt()

            // 최소 갱신 간격 검사
            if (!force && lastFetchedAt != null) {
                val last = Instant.ofEpochMilli(lastFetchedAt)
                val nextAvailableAt = last.plus(Duration.ofHours(settings.minSyncIntervalHours.toLong()))
                if (now.isBefore(nextAvailableAt)) {
                    return@withContext TleSyncResult.Skipped(
                        lastFetchedAt = last,
                        nextAvailableAt = nextAvailableAt,
                    )
                }
            }

            // 기본 주소 → (실패 시) 폴백 주소 순으로 시도
            val candidates = buildList {
                settings.tleSourceUrl.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                settings.fallbackTleSourceUrl.trim()
                    .takeIf { it.isNotEmpty() && it != settings.tleSourceUrl.trim() }
                    ?.let { add(it) }
            }

            if (candidates.isEmpty()) {
                return@withContext TleSyncResult.Failed(
                    message = "TLE 주소가 설정되어 있지 않습니다.",
                    cachedCount = tleDao.count(),
                )
            }

            var lastError: Exception? = null

            candidates.forEachIndexed { index, url ->
                try {
                    Log.i(TAG, "TLE 요청: $url")
                    val body = tleApi.fetchTle(url)
                    if (body.isBlank()) throw IllegalStateException("응답 본문이 비어 있습니다.")

                    val parsed = TleTextParser.parse(body, now.toEpochMilli())
                    if (parsed.isEmpty()) throw IllegalStateException("유효한 TLE 가 없습니다.")

                    tleDao.replaceAutomatic(parsed)
                    Log.i(TAG, "TLE 갱신 완료: ${parsed.size}건 (from $url)")

                    return@withContext TleSyncResult.Updated(
                        count = parsed.size,
                        sourceUrl = url,
                        usedFallback = index > 0,
                    )
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "TLE 요청 실패: $url", e)
                }
            }

            // 전부 실패 — 기존 캐시를 그대로 쓴다.
            TleSyncResult.Failed(
                message = lastError?.localizedMessage ?: "TLE 를 받아오지 못했습니다.",
                cachedCount = tleDao.count(),
            )
        }
}
