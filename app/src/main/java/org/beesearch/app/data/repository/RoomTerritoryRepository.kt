package org.beesearch.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.beesearch.app.data.local.room.TerritoryDao
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.local.room.toDomain
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.repository.TerritoryRepository
import java.time.Clock
import java.util.UUID

internal class RoomTerritoryRepository(
    private val territoryDao: TerritoryDao,
    private val clock: Clock,
) : TerritoryRepository {
    override fun observeTerritories(): Flow<List<Territory>> = territoryDao.observeAll()
        .map { territories -> territories.map(TerritoryEntity::toDomain) }

    override suspend fun getTerritory(id: UUID): Territory? = territoryDao.getById(id)?.toDomain()

    override suspend fun createTerritory(code: String, name: String): Territory {
        val now = clock.instant()
        val territory = TerritoryEntity(
            id = UUID.randomUUID(),
            code = code,
            name = name,
            createdAt = now,
            updatedAt = now,
        )
        territoryDao.insert(territory)
        return territory.toDomain()
    }

    override suspend fun updateTerritory(id: UUID, code: String, name: String): Territory {
        val existing = territoryDao.getById(id) ?: throw EntityNotFoundException("Territory")
        val updated = existing.copy(
            code = code,
            name = name,
            updatedAt = clock.instant(),
        )
        territoryDao.update(updated)
        return updated.toDomain()
    }
}
