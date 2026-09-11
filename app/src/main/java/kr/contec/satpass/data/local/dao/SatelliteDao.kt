package kr.contec.satpass.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kr.contec.satpass.data.local.entity.SatelliteEntity

@Dao
interface SatelliteDao {

    @Query("SELECT * FROM satellite ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SatelliteEntity>>

    @Query("SELECT * FROM satellite WHERE selected = 1 ORDER BY name COLLATE NOCASE ASC")
    fun observeSelected(): Flow<List<SatelliteEntity>>

    @Query("SELECT * FROM satellite WHERE selected = 1")
    suspend fun getSelected(): List<SatelliteEntity>

    @Query("SELECT COUNT(*) FROM satellite")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(satellite: SatelliteEntity)

    @Query("UPDATE satellite SET selected = :selected WHERE norad_id = :noradId")
    suspend fun updateSelected(noradId: Int, selected: Boolean)

    @Query("UPDATE satellite SET selected = :selected")
    suspend fun updateAllSelected(selected: Boolean)

    @Query("DELETE FROM satellite WHERE norad_id = :noradId")
    suspend fun delete(noradId: Int)
}
