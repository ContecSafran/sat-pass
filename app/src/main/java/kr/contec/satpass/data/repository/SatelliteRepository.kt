package kr.contec.satpass.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kr.contec.satpass.data.local.dao.SatelliteDao
import kr.contec.satpass.data.local.entity.SatelliteEntity
import java.time.Instant

/**
 * 사용자가 등록한 관심 위성 관리.
 */
class SatelliteRepository(
    private val satelliteDao: SatelliteDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    val all: Flow<List<SatelliteEntity>> = satelliteDao.observeAll()

    val selected: Flow<List<SatelliteEntity>> = satelliteDao.observeSelected()

    suspend fun add(noradId: Int, name: String) = withContext(ioDispatcher) {
        satelliteDao.upsert(
            SatelliteEntity(
                noradId = noradId,
                name = name,
                selected = true,
                addedAt = Instant.now().toEpochMilli(),
            )
        )
    }

    suspend fun setSelected(noradId: Int, selected: Boolean) = withContext(ioDispatcher) {
        satelliteDao.updateSelected(noradId, selected)
    }

    suspend fun setAllSelected(selected: Boolean) = withContext(ioDispatcher) {
        satelliteDao.updateAllSelected(selected)
    }

    suspend fun remove(noradId: Int) = withContext(ioDispatcher) {
        satelliteDao.delete(noradId)
    }

    suspend fun count(): Int = withContext(ioDispatcher) { satelliteDao.count() }
}
