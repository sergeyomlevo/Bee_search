package org.beesearch.app.data.repository

import androidx.room.withTransaction
import org.beesearch.app.data.local.room.ApiaryEntity
import org.beesearch.app.data.local.room.BeeDao
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.PhysicalObjectDao
import org.beesearch.app.data.local.room.PhysicalObjectEntity
import org.beesearch.app.data.local.room.TerritoryDao
import org.beesearch.app.data.local.room.toDomain
import org.beesearch.app.domain.model.Apiary
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.TerritoryPhysicalObjects
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import java.time.Clock
import java.util.UUID

internal class RoomPhysicalObjectRepository(
    private val database: BeeSearchDatabase,
    private val objectDao: PhysicalObjectDao,
    private val territoryDao: TerritoryDao,
    private val beeDao: BeeDao,
    private val clock: Clock,
) : PhysicalObjectRepository {

    override suspend fun createHollow(territoryId: UUID, latitude: Double, longitude: Double): Hollow =
        createIdentity(territoryId, PhysicalObjectType.HOLLOW, latitude, longitude).toHollow()

    override suspend fun createLogHive(territoryId: UUID, latitude: Double, longitude: Double): LogHive =
        createIdentity(territoryId, PhysicalObjectType.LOG_HIVE, latitude, longitude).toLogHive()

    override suspend fun createApiary(
        territoryId: UUID,
        latitude: Double,
        longitude: Double,
        name: String?,
    ): Apiary = database.withTransaction {
        val identity = newIdentity(territoryId, PhysicalObjectType.APIARY, latitude, longitude)
        objectDao.insertObject(identity)
        val subtype = ApiaryEntity(identity.id, name)
        objectDao.insertApiary(subtype)
        identity.toApiary(subtype)
    }

    override suspend fun getHollow(id: UUID): Hollow? = objectDao.getById(id)
        ?.takeIf { it.objectType == PhysicalObjectType.HOLLOW }
        ?.toHollow()

    override suspend fun getLogHive(id: UUID): LogHive? = objectDao.getById(id)
        ?.takeIf { it.objectType == PhysicalObjectType.LOG_HIVE }
        ?.toLogHive()

    override suspend fun getApiary(id: UUID): Apiary? = database.withTransaction {
        val identity = objectDao.getById(id)?.takeIf { it.objectType == PhysicalObjectType.APIARY }
            ?: return@withTransaction null
        val subtype = objectDao.getApiary(id)
            ?: error("Apiary subtype is missing for $id")
        identity.toApiary(subtype)
    }

    override suspend fun listForTerritory(territoryId: UUID): TerritoryPhysicalObjects = database.withTransaction {
        val identities = objectDao.getForTerritory(territoryId)
        val apiaryIds = identities.filter { it.objectType == PhysicalObjectType.APIARY }.map { it.id }
        val apiaries = if (apiaryIds.isEmpty()) emptyMap() else objectDao.getApiaries(apiaryIds).associateBy { it.physicalObjectId }
        TerritoryPhysicalObjects(
            hollows = identities.filter { it.objectType == PhysicalObjectType.HOLLOW }.map(PhysicalObjectEntity::toHollow),
            logHives = identities.filter { it.objectType == PhysicalObjectType.LOG_HIVE }.map(PhysicalObjectEntity::toLogHive),
            apiaries = identities.filter { it.objectType == PhysicalObjectType.APIARY }.map { identity ->
                identity.toApiary(apiaries[identity.id] ?: error("Apiary subtype is missing for ${identity.id}"))
            },
        )
    }

    override suspend fun setBeeSourceObject(beeId: UUID, sourceObjectId: UUID?) = database.withTransaction {
        val bee = beeDao.getById(beeId) ?: throw EntityNotFoundException("Bee")
        if (sourceObjectId != null && objectDao.getById(sourceObjectId) == null) {
            throw EntityNotFoundException("Physical object")
        }
        if (beeDao.setSourceObject(beeId, sourceObjectId) != 1) throw EntityNotFoundException("Bee")
        bee.copy(sourceObjectId = sourceObjectId).toDomain()
    }

    override suspend fun getBeeSourceObjectId(beeId: UUID): UUID? =
        (beeDao.getById(beeId) ?: throw EntityNotFoundException("Bee")).sourceObjectId

    private suspend fun createIdentity(
        territoryId: UUID,
        type: PhysicalObjectType,
        latitude: Double,
        longitude: Double,
    ): PhysicalObjectEntity = database.withTransaction {
        val identity = newIdentity(territoryId, type, latitude, longitude)
        objectDao.insertObject(identity)
        identity
    }

    private suspend fun newIdentity(
        territoryId: UUID,
        type: PhysicalObjectType,
        latitude: Double,
        longitude: Double,
    ): PhysicalObjectEntity {
        if (territoryDao.getById(territoryId) == null) throw EntityNotFoundException("Territory")
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude is out of range" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude is out of range" }
        return PhysicalObjectEntity(
            id = UUID.randomUUID(),
            territoryId = territoryId,
            objectType = type,
            sequenceNumber = objectDao.getNextSequenceNumber(territoryId, type),
            latitude = latitude,
            longitude = longitude,
            createdAt = clock.instant(),
        )
    }
}

private fun PhysicalObjectEntity.toHollow() = Hollow(id, territoryId, sequenceNumber, latitude, longitude, createdAt)
private fun PhysicalObjectEntity.toLogHive() = LogHive(id, territoryId, sequenceNumber, latitude, longitude, createdAt)
private fun PhysicalObjectEntity.toApiary(subtype: ApiaryEntity) =
    Apiary(id, territoryId, sequenceNumber, latitude, longitude, createdAt, subtype.name)
