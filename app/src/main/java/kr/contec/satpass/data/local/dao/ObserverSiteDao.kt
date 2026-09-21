package kr.contec.satpass.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kr.contec.satpass.data.local.entity.ObserverSiteEntity

@Dao
interface ObserverSiteDao {

    @Query("SELECT * FROM observer_site ORDER BY created_at ASC")
    fun observeAll(): Flow<List<ObserverSiteEntity>>

    @Query("SELECT * FROM observer_site WHERE id = :id")
    suspend fun getById(id: Long): ObserverSiteEntity?

    /** @return 새로 만들어진 행의 id */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(site: ObserverSiteEntity): Long

    @Update
    suspend fun update(site: ObserverSiteEntity)

    @Delete
    suspend fun delete(site: ObserverSiteEntity)

    @Query("DELETE FROM observer_site WHERE id = :id")
    suspend fun deleteById(id: Long)
}
