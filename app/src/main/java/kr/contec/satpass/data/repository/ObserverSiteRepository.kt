package kr.contec.satpass.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kr.contec.satpass.data.local.dao.ObserverSiteDao
import kr.contec.satpass.data.local.entity.ObserverSiteEntity
import java.time.Instant

/**
 * 저장해 둔 관측 지점 관리.
 */
class ObserverSiteRepository(
    private val observerSiteDao: ObserverSiteDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    val all: Flow<List<ObserverSiteEntity>> = observerSiteDao.observeAll()

    suspend fun getById(id: Long): ObserverSiteEntity? = withContext(ioDispatcher) {
        if (id == ObserverSiteEntity.CURRENT_LOCATION_ID) null else observerSiteDao.getById(id)
    }

    /** @return 새로 만들어진 지점의 id */
    suspend fun add(
        name: String,
        latitude: Double,
        longitude: Double,
        altitudeMeters: Double,
    ): Long = withContext(ioDispatcher) {
        observerSiteDao.insert(
            ObserverSiteEntity(
                name = name.trim(),
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                createdAt = Instant.now().toEpochMilli(),
            )
        )
    }

    suspend fun update(site: ObserverSiteEntity) = withContext(ioDispatcher) {
        observerSiteDao.update(site.copy(name = site.name.trim()))
    }

    suspend fun remove(id: Long) = withContext(ioDispatcher) {
        observerSiteDao.deleteById(id)
    }
}
