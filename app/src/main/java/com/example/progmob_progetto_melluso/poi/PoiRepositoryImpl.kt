package com.example.progmob_progetto_melluso.poi

import com.example.progmob_progetto_melluso.data.dao.PoiDao
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room-backed [PoiRepository].
 *
 * How it works: it wraps [PoiDao] — reads are reactive DAO flows (every
 * insert/vote/delete re-emits to every screen). The signed-in user id is
 * injected ([userIdProvider]) so user-created POIs carry their owner
 * ([PoiEntity.createdByUserId]): the "my POIs" screen lists them and removal
 * is allowed only on your own POIs. Tests substitute a fixed id.
 *
 * @param poiDao DAO for the POI catalog.
 * @param userIdProvider supplies the signed-in user id (Firebase uid with a
 *   local fallback when anonymous).
 */
class PoiRepositoryImpl(
    private val poiDao: PoiDao,
    private val userIdProvider: () -> String
) : PoiRepository {

    override fun observePois(): Flow<List<PoiEntity>> = poiDao.allPois()

    override fun observeMyPois(): Flow<List<PoiEntity>> =
        poiDao.myPois(userIdProvider())

    override suspend fun addPoi(poi: PoiEntity): Long =
        poiDao.insert(
            poi.copy(
                createdByUserId = poi.createdByUserId ?: userIdProvider(),
                votes = 0
            )
        )

    override suspend fun removeMyPoi(poiId: Long): Boolean {
        val poi = poiDao.byIdOnce(poiId) ?: return false
        if (poi.createdByUserId != userIdProvider()) return false
        poiDao.delete(poiId)
        return true
    }
}
