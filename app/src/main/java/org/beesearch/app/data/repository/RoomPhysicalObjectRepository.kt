package org.beesearch.app.data.repository

import androidx.room.withTransaction
import org.beesearch.app.data.local.room.ApiaryEntity
import org.beesearch.app.data.local.room.BeeDao
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.HollowEntity
import org.beesearch.app.data.local.room.LogHiveEntity
import org.beesearch.app.data.local.room.ObserverDao
import org.beesearch.app.data.local.room.PhysicalObjectDao
import org.beesearch.app.data.local.room.PhysicalObjectEntity
import org.beesearch.app.data.local.room.PhysicalObjectMediaEntity
import org.beesearch.app.data.local.room.TerritoryDao
import org.beesearch.app.data.local.room.toDomain
import org.beesearch.app.domain.model.Apiary
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.NewLogHive
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.TerritoryPhysicalObjects
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import java.time.Clock
import java.util.UUID

internal class RoomPhysicalObjectRepository(
    private val database: BeeSearchDatabase,
    private val objectDao: PhysicalObjectDao,
    private val territoryDao: TerritoryDao,
    private val observerDao: ObserverDao,
    private val beeDao: BeeDao,
    private val clock: Clock,
) : PhysicalObjectRepository {

    override suspend fun createHollow(value: NewHollow): Hollow = database.withTransaction {
        validateMedia(value.id, value.media)
        val identity = newIdentity(value.id, value.territoryId, value.creatorObserverId, PhysicalObjectType.HOLLOW, value.latitude, value.longitude)
        val subtype = value.properties.toEntity(value.id)
        objectDao.insertObject(identity)
        objectDao.insertHollow(subtype)
        objectDao.insertMedia(value.media.map(PhysicalObjectMedia::toEntity))
        identity.toHollow(subtype, value.media)
    }

    override suspend fun createLogHive(value: NewLogHive): LogHive = database.withTransaction {
        validateMedia(value.id, value.media)
        val identity = newIdentity(value.id, value.territoryId, value.creatorObserverId, PhysicalObjectType.LOG_HIVE, value.latitude, value.longitude)
        val subtype = value.properties.toEntity(value.id)
        objectDao.insertObject(identity)
        objectDao.insertLogHive(subtype)
        objectDao.insertMedia(value.media.map(PhysicalObjectMedia::toEntity))
        identity.toLogHive(subtype, value.media)
    }

    override suspend fun createApiary(
        territoryId: UUID,
        latitude: Double,
        longitude: Double,
        name: String?,
    ): Apiary = database.withTransaction {
        val identity = newIdentity(UUID.randomUUID(), territoryId, null, PhysicalObjectType.APIARY, latitude, longitude)
        objectDao.insertObject(identity)
        val subtype = ApiaryEntity(identity.id, name)
        objectDao.insertApiary(subtype)
        identity.toApiary(subtype)
    }

    override suspend fun getHollow(id: UUID): Hollow? = database.withTransaction {
        val identity = objectDao.getById(id)?.takeIf { it.objectType == PhysicalObjectType.HOLLOW }
            ?: return@withTransaction null
        identity.toHollow(
            objectDao.getHollow(id) ?: error("Hollow subtype is missing for $id"),
            objectDao.getMedia(id).map(PhysicalObjectMediaEntity::toDomain),
        )
    }

    override suspend fun getLogHive(id: UUID): LogHive? = database.withTransaction {
        val identity = objectDao.getById(id)?.takeIf { it.objectType == PhysicalObjectType.LOG_HIVE }
            ?: return@withTransaction null
        identity.toLogHive(
            objectDao.getLogHive(id) ?: error("LogHive subtype is missing for $id"),
            objectDao.getMedia(id).map(PhysicalObjectMediaEntity::toDomain),
        )
    }

    override suspend fun getApiary(id: UUID): Apiary? = database.withTransaction {
        val identity = objectDao.getById(id)?.takeIf { it.objectType == PhysicalObjectType.APIARY }
            ?: return@withTransaction null
        val subtype = objectDao.getApiary(id)
            ?: error("Apiary subtype is missing for $id")
        identity.toApiary(subtype)
    }

    override suspend fun listForTerritory(territoryId: UUID): TerritoryPhysicalObjects = database.withTransaction {
        val identities = objectDao.getForTerritory(territoryId)
        val hollowIds = identities.filter { it.objectType == PhysicalObjectType.HOLLOW }.map { it.id }
        val logHiveIds = identities.filter { it.objectType == PhysicalObjectType.LOG_HIVE }.map { it.id }
        val apiaryIds = identities.filter { it.objectType == PhysicalObjectType.APIARY }.map { it.id }
        val hollows = if (hollowIds.isEmpty()) emptyMap() else objectDao.getHollows(hollowIds).associateBy { it.physicalObjectId }
        val logHives = if (logHiveIds.isEmpty()) emptyMap() else objectDao.getLogHives(logHiveIds).associateBy { it.physicalObjectId }
        val apiaries = if (apiaryIds.isEmpty()) emptyMap() else objectDao.getApiaries(apiaryIds).associateBy { it.physicalObjectId }
        val media = if (identities.isEmpty()) emptyMap() else objectDao.getMediaForObjects(identities.map { it.id })
            .groupBy { it.physicalObjectId }
            .mapValues { (_, values) -> values.map(PhysicalObjectMediaEntity::toDomain) }
        TerritoryPhysicalObjects(
            hollows = identities.filter { it.objectType == PhysicalObjectType.HOLLOW }.map { identity ->
                identity.toHollow(
                    hollows[identity.id] ?: error("Hollow subtype is missing for ${identity.id}"),
                    media[identity.id].orEmpty(),
                )
            },
            logHives = identities.filter { it.objectType == PhysicalObjectType.LOG_HIVE }.map { identity ->
                identity.toLogHive(
                    logHives[identity.id] ?: error("LogHive subtype is missing for ${identity.id}"),
                    media[identity.id].orEmpty(),
                )
            },
            apiaries = identities.filter { it.objectType == PhysicalObjectType.APIARY }.map { identity ->
                identity.toApiary(apiaries[identity.id] ?: error("Apiary subtype is missing for ${identity.id}"))
            },
        )
    }

    override suspend fun updateHollow(id: UUID, properties: HollowProperties): Hollow = database.withTransaction {
        val identity = objectDao.getById(id)?.takeIf { it.objectType == PhysicalObjectType.HOLLOW }
            ?: throw EntityNotFoundException("Hollow")
        val changed = objectDao.updateHollow(
            id = id,
            tree = properties.tree,
            entranceHeightCm = properties.entranceHeightCm,
            entranceAzimuthDeg = properties.entranceAzimuthDeg,
            outerDiameterCm = properties.outerDiameterCm,
            internalDiameterCm = properties.internalDiameterCm,
            notes = properties.notes,
        )
        if (changed != 1) throw EntityNotFoundException("Hollow")
        identity.toHollow(properties.toEntity(id), objectDao.getMedia(id).map(PhysicalObjectMediaEntity::toDomain))
    }

    override suspend fun updateLogHive(id: UUID, properties: LogHiveProperties): LogHive = database.withTransaction {
        val identity = objectDao.getById(id)?.takeIf { it.objectType == PhysicalObjectType.LOG_HIVE }
            ?: throw EntityNotFoundException("LogHive")
        val changed = objectDao.updateLogHive(
            id = id,
            tree = properties.tree,
            entranceHeightCm = properties.entranceHeightCm,
            entranceAzimuthDeg = properties.entranceAzimuthDeg,
            outerDiameterCm = properties.outerDiameterCm,
            material = properties.material,
            internalDiameterCm = properties.internalDiameterCm,
            internalHeightCm = properties.internalHeightCm,
            notes = properties.notes,
        )
        if (changed != 1) throw EntityNotFoundException("LogHive")
        identity.toLogHive(properties.toEntity(id), objectDao.getMedia(id).map(PhysicalObjectMediaEntity::toDomain))
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

    private suspend fun newIdentity(
        id: UUID,
        territoryId: UUID,
        creatorObserverId: UUID?,
        type: PhysicalObjectType,
        latitude: Double,
        longitude: Double,
    ): PhysicalObjectEntity {
        if (territoryDao.getById(territoryId) == null) throw EntityNotFoundException("Territory")
        if (creatorObserverId != null && observerDao.getById(creatorObserverId) == null) {
            throw EntityNotFoundException("Observer")
        }
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude is out of range" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude is out of range" }
        return PhysicalObjectEntity(
            id = id,
            territoryId = territoryId,
            objectType = type,
            sequenceNumber = objectDao.getNextSequenceNumber(territoryId, type),
            latitude = latitude,
            longitude = longitude,
            createdAt = clock.instant(),
            creatorObserverId = creatorObserverId,
        )
    }
}

private fun validateMedia(objectId: UUID, media: List<PhysicalObjectMedia>) {
    require(media.map { it.id }.distinct().size == media.size) { "Duplicate physical object media id" }
    media.forEach {
        require(it.physicalObjectId == objectId) { "Physical object media belongs to another object" }
        require(it.relativePath.isNotBlank() && it.byteSize > 0L && it.sha256.isNotBlank()) {
            "Invalid physical object media metadata"
        }
    }
}

private fun HollowProperties.toEntity(id: UUID) = HollowEntity(
    id, tree, entranceHeightCm, entranceAzimuthDeg, outerDiameterCm, internalDiameterCm, notes,
)

private fun LogHiveProperties.toEntity(id: UUID) = LogHiveEntity(
    id, tree, entranceHeightCm, entranceAzimuthDeg, outerDiameterCm, material,
    internalDiameterCm, internalHeightCm, notes,
)

private fun PhysicalObjectMedia.toEntity() = PhysicalObjectMediaEntity(
    id, physicalObjectId, type, relativePath, originalFileName, mimeType, byteSize, sha256, createdAt,
)

private fun PhysicalObjectMediaEntity.toDomain() = PhysicalObjectMedia(
    id, physicalObjectId, type, relativePath, originalFileName, mimeType, byteSize, sha256, createdAt,
)

private fun HollowEntity.toProperties(): HollowProperties? {
    val required = listOf(tree, entranceHeightCm, entranceAzimuthDeg, outerDiameterCm)
    if (required.all { it == null }) return null
    check(required.all { it != null }) { "Hollow subtype is partially populated for $physicalObjectId" }
    return HollowProperties(
        requireNotNull(tree), requireNotNull(entranceHeightCm), requireNotNull(entranceAzimuthDeg),
        requireNotNull(outerDiameterCm), internalDiameterCm, notes,
    )
}

private fun LogHiveEntity.toProperties(): LogHiveProperties? {
    val required = listOf(tree, entranceHeightCm, entranceAzimuthDeg, outerDiameterCm, material, internalDiameterCm, internalHeightCm)
    if (required.all { it == null }) return null
    check(required.all { it != null }) { "LogHive subtype is partially populated for $physicalObjectId" }
    return LogHiveProperties(
        requireNotNull(tree), requireNotNull(entranceHeightCm), requireNotNull(entranceAzimuthDeg),
        requireNotNull(outerDiameterCm), requireNotNull(material), requireNotNull(internalDiameterCm),
        requireNotNull(internalHeightCm), notes,
    )
}

private fun PhysicalObjectEntity.toHollow(subtype: HollowEntity, media: List<PhysicalObjectMedia>) =
    Hollow(id, territoryId, sequenceNumber, latitude, longitude, createdAt, creatorObserverId, subtype.toProperties(), media)

private fun PhysicalObjectEntity.toLogHive(subtype: LogHiveEntity, media: List<PhysicalObjectMedia>) =
    LogHive(id, territoryId, sequenceNumber, latitude, longitude, createdAt, creatorObserverId, subtype.toProperties(), media)

private fun PhysicalObjectEntity.toApiary(subtype: ApiaryEntity) =
    Apiary(id, territoryId, sequenceNumber, latitude, longitude, createdAt, subtype.name, creatorObserverId)
