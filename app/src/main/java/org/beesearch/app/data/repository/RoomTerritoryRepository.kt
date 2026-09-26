package org.beesearch.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.TerritoryDao
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.local.room.toDomain
import org.beesearch.app.domain.model.DuplicateTerritoryCodeException
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.model.TerritoryInUseException
import org.beesearch.app.domain.repository.TerritoryRepository
import java.time.Clock
import java.util.UUID

internal class RoomTerritoryRepository(
    private val database: BeeSearchDatabase,
    private val territoryDao: TerritoryDao,
    private val clock: Clock,
) : TerritoryRepository {
    override fun observeTerritories(): Flow<List<Territory>> = territoryDao.observeAll()
        .map { territories -> territories.map(TerritoryEntity::toDomain) }

    override suspend fun getTerritory(id: UUID): Territory? = territoryDao.getById(id)?.toDomain()

    override suspend fun createTerritory(
        code: String,
        name: String,
        region: String,
        district: String,
    ): Territory {
        val now = clock.instant()
        val territory = TerritoryEntity(
            id = UUID.randomUUID(),
            code = requiredTrimmed(code, "Territory code"),
            name = requiredTrimmed(name, "Territory name"),
            region = requiredTrimmed(region, "Territory region"),
            district = requiredTrimmed(district, "Territory district"),
            createdAt = now,
            updatedAt = now,
        )
        try {
            territoryDao.insert(territory)
        } catch (_: SQLiteConstraintException) {
            throw DuplicateTerritoryCodeException()
        }
        return territory.toDomain()
    }

    override suspend fun updateTerritory(territory: Territory): Territory {
        val updated = territory.copy(
            code = requiredTrimmed(territory.code, "Territory code"),
            name = requiredTrimmed(territory.name, "Territory name"),
            region = requiredTrimmed(territory.region, "Territory region"),
            district = requiredTrimmed(territory.district, "Territory district"),
            updatedAt = clock.instant(),
        )
        try {
            if (territoryDao.update(updated.id, updated.code, updated.name, updated.region, updated.district, updated.updatedAt) != 1) {
                throw org.beesearch.app.domain.model.EntityNotFoundException("Territory")
            }
        } catch (_: SQLiteConstraintException) {
            throw DuplicateTerritoryCodeException()
        }
        return updated
    }

    override suspend fun ensureTerritoryCanBeDeleted(id: UUID) {
        if (territoryDao.countObservationPoints(id) != 0 || territoryDao.countPhysicalObjects(id) != 0) {
            throw TerritoryInUseException()
        }
        if (territoryDao.getById(id) == null) {
            throw org.beesearch.app.domain.model.EntityNotFoundException("Territory")
        }
    }

    /**
     * Deletes an unused Territory together with the numbering state of its scopes.
     *
     * The sequence rows belong to the Territory by a `RESTRICT` foreign key, so they must be
     * removed in the same transaction; leaving one behind would make an otherwise deletable
     * Territory undeletable. Existing blockers (observation points, physical objects) are unchanged.
     */
    override suspend fun deleteTerritory(id: UUID) = database.withTransaction {
        ensureTerritoryCanBeDeleted(id)
        database.physicalObjectSequenceDao().deleteForTerritory(id)
        if (territoryDao.deleteById(id) != 1) throw org.beesearch.app.domain.model.EntityNotFoundException("Territory")
    }

}
