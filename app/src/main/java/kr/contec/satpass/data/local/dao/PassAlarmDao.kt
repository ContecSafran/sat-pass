package kr.contec.satpass.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kr.contec.satpass.data.local.entity.PassAlarmEntity

@Dao
interface PassAlarmDao {

    @Query("SELECT * FROM pass_alarm ORDER BY aos_millis ASC")
    fun observeAll(): Flow<List<PassAlarmEntity>>

    @Query("SELECT * FROM pass_alarm ORDER BY aos_millis ASC")
    suspend fun getAll(): List<PassAlarmEntity>

    @Query("SELECT * FROM pass_alarm WHERE norad_id = :noradId AND aos_millis = :aosMillis")
    suspend fun find(noradId: Int, aosMillis: Long): PassAlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: PassAlarmEntity): Long

    @Query("DELETE FROM pass_alarm WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pass_alarm")
    suspend fun deleteAll()

    /** 이미 지나간 알림 정리 */
    @Query("DELETE FROM pass_alarm WHERE los_millis < :now")
    suspend fun deleteExpired(now: Long)
}
