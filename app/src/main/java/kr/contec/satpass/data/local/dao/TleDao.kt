package kr.contec.satpass.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kr.contec.satpass.data.local.entity.TleEntity

@Dao
interface TleDao {

    @Query("SELECT * FROM tle WHERE norad_id IN (:noradIds)")
    suspend fun getByNoradIds(noradIds: List<Int>): List<TleEntity>

    @Query("SELECT * FROM tle WHERE norad_id IN (:noradIds)")
    fun observeByNoradIds(noradIds: List<Int>): Flow<List<TleEntity>>

    @Query("SELECT * FROM tle WHERE norad_id = :noradId")
    suspend fun getByNoradId(noradId: Int): TleEntity?

    /**
     * 이름 또는 NORAD ID 로 검색.
     * [query] 가 숫자면 NORAD ID 부분 일치도 함께 본다.
     */
    @Query(
        """
        SELECT * FROM tle
        WHERE satellite_name LIKE '%' || :query || '%' COLLATE NOCASE
           OR CAST(norad_id AS TEXT) LIKE :query || '%'
        ORDER BY
            CASE WHEN satellite_name LIKE :query || '%' COLLATE NOCASE THEN 0 ELSE 1 END,
            satellite_name COLLATE NOCASE ASC
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int = 50): List<TleEntity>

    /** 캐시에 들어 있는 TLE 개수 */
    @Query("SELECT COUNT(*) FROM tle")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tle")
    suspend fun count(): Int

    /**
     * 가장 최근 수신 시각. 최소 갱신 간격(설정값) 판단에 쓴다.
     * LCAM `OrbitService.renew()` 의 `reloadTime` 검사와 같은 역할.
     */
    @Query("SELECT MAX(fetched_at) FROM tle")
    fun observeLastFetchedAt(): Flow<Long?>

    @Query("SELECT MAX(fetched_at) FROM tle")
    suspend fun getLastFetchedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tles: List<TleEntity>)

    @Query("DELETE FROM tle")
    suspend fun deleteAll()

    /**
     * 카탈로그 전체 교체.
     * LCAM 과 동일하게 기존 레코드를 모두 지우고 새로 받은 것을 넣는다.
     */
    @Transaction
    suspend fun replaceAll(tles: List<TleEntity>) {
        deleteAll()
        // 파라미터 바인딩 한도(SQLite 999)를 넘지 않도록 나눠서 넣는다.
        tles.chunked(200).forEach { insertAll(it) }
    }
}
