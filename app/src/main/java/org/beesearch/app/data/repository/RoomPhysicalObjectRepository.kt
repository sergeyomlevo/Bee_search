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
import org.beesearch.app.data.local.room.PhysicalObjectSequenceDao
import org.beesearch.app.data.local.room.PhysicalObjectSequenceEntity
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
import org.beesearch.app.domain.model.PhysicalObjectInUseException
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectReference
import org.beesearch.app.domain.model.PhysicalObjectReferenceKind
import org.beesearch.app.domain.model.PhysicalObjectSequenceResetBlockedException
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.ResearchDateInterval
import org.beesearch.app.domain.model.TerritoryPhysicalObjects
import org.beesearch.app.domain.repository.PhysicalObjectDeletion
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

internal class RoomPhysicalObjectRepository(
    private val database: BeeSearchDatabase,
    private val objectDao: PhysicalObjectDao,
    private val sequenceDao: PhysicalObjectSequenceDao,
    private val territoryDao: TerritoryDao,
    private val observerDao: ObserverDao,
    private val beeDao: BeeDao,
    private val clock: Clock,
    private val fixationZoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : PhysicalObjectRepository {

    override suspend fun createHollow(value: NewHollow): Hollow = database.withTransaction {
        validateMedia(value.id, value.media)
        val identity = newIdentity(value.id, value.territoryId, value.creatorObserverId, PhysicalObjectType.HOLLOW, value.latitude, value.longitude)
        val subtype = value.properties.toEntity(value.id, normalizeName(value.name))
        objectDao.insertObject(identity)
        objectDao.insertHollow(subtype)
        objectDao.insertMedia(value.media.map(PhysicalObjectMedia::toEntity))
        identity.toHollow(subtype, value.media)
    }

    override suspend fun createLogHive(value: NewLogHive): LogHive = database.withTransaction {
        validateMedia(value.id, value.media)
        val identity = newIdentity(value.id, value.territoryId, value.creatorObserverId, PhysicalObjectType.LOG_HIVE, value.latitude, value.longitude)
        val subtype = value.properties.toEntity(value.id, normalizeName(value.name))
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

    override suspend fun listForTerritory(
        territoryId: UUID,
        hollowDateInterval: ResearchDateInterval?,
        logHiveDateInterval: ResearchDateInterval?,
        hollowFilters: org.beesearch.app.domain.model.PhysicalObjectFilterSet,
        logHiveFilters: org.beesearch.app.domain.model.PhysicalObjectFilterSet,
    ): TerritoryPhysicalObjects = database.withTransaction {
        val identities = objectDao.getForTerritoryByType(territoryId, PhysicalObjectType.APIARY) +
            identitiesForFilters(territoryId, PhysicalObjectType.HOLLOW, hollowDateInterval, hollowFilters) +
            identitiesForFilters(territoryId, PhysicalObjectType.LOG_HIVE, logHiveDateInterval, logHiveFilters)
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

    private suspend fun identitiesForFilters(
        territoryId: UUID, type: PhysicalObjectType, interval: ResearchDateInterval?,
        filters: org.beesearch.app.domain.model.PhysicalObjectFilterSet,
    ): List<PhysicalObjectEntity> = objectDao.getFilteredForTerritoryByType(
        territoryId, type, (filters.dateInterval ?: interval)?.fromDate, (filters.dateInterval ?: interval)?.toDate,
        filters.entranceHeightCm.min, filters.entranceHeightCm.max,
        filters.outerDiameterCm.min, filters.outerDiameterCm.max,
    )

    override suspend fun updateHollow(id: UUID, properties: HollowProperties, name: String?): Hollow = database.withTransaction {
        val identity = objectDao.getById(id)?.takeIf { it.objectType == PhysicalObjectType.HOLLOW }
            ?: throw EntityNotFoundException("Hollow")
        val normalizedName = normalizeName(name)
        val subtype = properties.toEntity(id, normalizedName)
        if (objectDao.getHollow(id) == subtype) {
            return@withTransaction identity.toHollow(subtype, objectDao.getMedia(id).map(PhysicalObjectMediaEntity::toDomain))
        }
        val changed = objectDao.updateHollow(
            id = id,
            tree = properties.tree,
            entranceHeightCm = properties.entranceHeightCm,
            entranceAzimuthDeg = properties.entranceAzimuthDeg,
            outerDiameterCm = properties.outerDiameterCm,
            internalDiameterCm = properties.internalDiameterCm,
            notes = properties.notes,
            name = normalizedName,
        )
        if (changed != 1) throw EntityNotFoundException("Hollow")
        val updated = markModified(identity)
        updated.toHollow(subtype, objectDao.getMedia(id).map(PhysicalObjectMediaEntity::toDomain))
    }

    override suspend fun updateLogHive(id: UUID, properties: LogHiveProperties, name: String?): LogHive = database.withTransaction {
        val identity = objectDao.getById(id)?.takeIf { it.objectType == PhysicalObjectType.LOG_HIVE }
            ?: throw EntityNotFoundException("LogHive")
        val normalizedName = normalizeName(name)
        val subtype = properties.toEntity(id, normalizedName)
        if (objectDao.getLogHive(id) == subtype) {
            return@withTransaction identity.toLogHive(subtype, objectDao.getMedia(id).map(PhysicalObjectMediaEntity::toDomain))
        }
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
            name = normalizedName,
        )
        if (changed != 1) throw EntityNotFoundException("LogHive")
        val updated = markModified(identity)
        updated.toLogHive(subtype, objectDao.getMedia(id).map(PhysicalObjectMediaEntity::toDomain))
    }

    override suspend fun updateCoordinates(id: UUID, latitude: Double, longitude: Double) =
        database.withTransaction {
            require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude is out of range" }
            require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude is out of range" }
            val identity = objectDao.getById(id) ?: throw EntityNotFoundException("PhysicalObject")
            require(identity.objectType == PhysicalObjectType.HOLLOW || identity.objectType == PhysicalObjectType.LOG_HIVE) {
                "Coordinates can only be edited for Hollow or LogHive"
            }
            if (identity.latitude == latitude && identity.longitude == longitude) return@withTransaction
            if (objectDao.updateCoordinates(id, latitude, longitude) != 1) {
                throw EntityNotFoundException("PhysicalObject")
            }
            markModified(identity)
        }

    override suspend fun addObjectMedia(id: UUID, media: List<PhysicalObjectMedia>) = database.withTransaction {
        val identity = editableIdentity(id)
        validateMedia(id, media)
        if (media.isNotEmpty()) {
            objectDao.insertMedia(media.map(PhysicalObjectMedia::toEntity))
            markModified(identity)
        }
        Unit
    }

    override suspend fun removeObjectMedia(id: UUID, mediaId: UUID): String? = database.withTransaction {
        val identity = editableIdentity(id)
        val media = objectDao.getMedia(id).find { it.id == mediaId } ?: return@withTransaction null
        check(objectDao.deleteObjectMedia(id, mediaId) == 1)
        markModified(identity)
        media.relativePath
    }

    private suspend fun editableIdentity(id: UUID): PhysicalObjectEntity {
        val identity = objectDao.getById(id) ?: throw EntityNotFoundException("PhysicalObject")
        require(identity.objectType == PhysicalObjectType.HOLLOW || identity.objectType == PhysicalObjectType.LOG_HIVE)
        return identity
    }

    private suspend fun markModified(identity: PhysicalObjectEntity): PhysicalObjectEntity {
        val at = java.time.Instant.ofEpochMilli(clock.instant().toEpochMilli())
        check(objectDao.updateModificationTime(identity.id, at) == 1)
        return identity.copy(updatedAt = at)
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

    override suspend fun deleteHollow(id: UUID): PhysicalObjectDeletion = deleteObject(
        id = id,
        type = PhysicalObjectType.HOLLOW,
        deleteSubtype = objectDao::deleteHollow,
    )

    override suspend fun deleteLogHive(id: UUID): PhysicalObjectDeletion = deleteObject(
        id = id,
        type = PhysicalObjectType.LOG_HIVE,
        deleteSubtype = objectDao::deleteLogHive,
    )

    override suspend fun resetSequence(territoryId: UUID, objectType: PhysicalObjectType) =
        database.withTransaction {
            if (territoryDao.getById(territoryId) == null) {
                throw PhysicalObjectSequenceResetBlockedException("Territory does not exist")
            }
            if (objectDao.countInScope(territoryId, objectType) != 0) {
                throw PhysicalObjectSequenceResetBlockedException("Scope still contains objects")
            }
            if (objectDao.countReferencesInScope(territoryId, objectType) != 0) {
                throw PhysicalObjectSequenceResetBlockedException("Scope still has references")
            }
            if (objectDao.countDependentRowsInScope(territoryId, objectType) != 0) {
                throw PhysicalObjectSequenceResetBlockedException("Scope still has dependent rows")
            }
            val lastIssued = sequenceDao.getLastIssued(territoryId, objectType) ?: return@withTransaction
            val liveMax = objectDao.getMaxSequenceNumber(territoryId, objectType)
            if (lastIssued < 0 || lastIssued < liveMax) {
                throw PhysicalObjectSequenceResetBlockedException("Sequence state contradicts stored data")
            }
            if (sequenceDao.setLastIssued(territoryId, objectType, 0) != 1) {
                throw PhysicalObjectSequenceResetBlockedException("Sequence scope disappeared")
            }
        }

    /**
     * Deletes one object and its owned rows inside a single transaction.
     *
     * Working or historical references are collected first and block the deletion, so a Bee link is
     * never destroyed silently and the user learns which data is involved; the `RESTRICT` foreign keys
     * remain the second, structural line of defence. While the deletion is blocked nothing is
     * removed: no media row, no subtype row, no identity row and no file. The numbering high-water
     * mark is deliberately not touched: an ordinary deletion never frees a number.
     */
    private suspend fun deleteObject(
        id: UUID,
        type: PhysicalObjectType,
        deleteSubtype: suspend (UUID) -> Int,
    ): PhysicalObjectDeletion = database.withTransaction {
        val identity = objectDao.getById(id)?.takeIf { it.objectType == type }
            ?: throw EntityNotFoundException(type.entityLabel())
        val blockers = deletionBlockers(id)
        if (blockers.isNotEmpty()) throw PhysicalObjectInUseException(blockers)
        val mediaPaths = objectDao.getMedia(id).map { it.relativePath }
        objectDao.deleteMediaForObject(id)
        if (deleteSubtype(id) != 1) throw EntityNotFoundException(type.entityLabel())
        if (objectDao.deleteIdentity(id, type) != 1) throw EntityNotFoundException(type.entityLabel())
        PhysicalObjectDeletion(id, mediaPaths)
    }

    /**
     * Every persisted reference that makes this object undeletable, in kind declaration order.
     *
     * The query of each kind is chosen exhaustively, so adding a
     * [PhysicalObjectReferenceKind] without its reference count is a compile error rather than a
     * silently ignored blocker.
     */
    private suspend fun deletionBlockers(objectId: UUID): List<PhysicalObjectReference> =
        PhysicalObjectReferenceKind.entries.mapNotNull { kind ->
            val count = countReferences(objectId, kind)
            if (count == 0) null else PhysicalObjectReference(kind, count)
        }

    private suspend fun countReferences(objectId: UUID, kind: PhysicalObjectReferenceKind): Int = when (kind) {
        PhysicalObjectReferenceKind.BEE -> objectDao.countBeeReferences(objectId)
    }

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
        val createdAt = java.time.Instant.ofEpochMilli(clock.instant().toEpochMilli())
        return PhysicalObjectEntity(
            id = id,
            territoryId = territoryId,
            objectType = type,
            sequenceNumber = allocateSequenceNumber(territoryId, type),
            latitude = latitude,
            longitude = longitude,
            createdAt = createdAt,
            fixationDate = if (type == PhysicalObjectType.APIARY) null else createdAt.atZone(fixationZoneIdProvider()).toLocalDate(),
            fixationAt = if (type == PhysicalObjectType.APIARY) null else createdAt,
            updatedAt = createdAt,
            creatorObserverId = creatorObserverId,
        )
    }

    /**
     * Allocates the next number of one `Territory + object_type` scope.
     *
     * Runs inside the caller's creation transaction, so the counter update and the object insert
     * commit or roll back together: a failed creation does not consume a number, and two concurrent
     * creations cannot read the same value and produce the same designation.
     *
     * The next number is `max(last issued, highest live number) + 1`. The high-water mark is what
     * guarantees that an ordinary deletion never frees a number; the live maximum is kept as a floor
     * for data written before the high-water mark existed (an archive restored from format ≤4, or a
     * database where the counter was never created).
     */
    private suspend fun allocateSequenceNumber(territoryId: UUID, type: PhysicalObjectType): Int {
        sequenceDao.insertScope(PhysicalObjectSequenceEntity(territoryId, type, 0))
        val lastIssued = sequenceDao.getLastIssued(territoryId, type)
            ?: error("Physical object sequence scope is missing")
        val liveMax = objectDao.getMaxSequenceNumber(territoryId, type)
        require(lastIssued >= 0 && liveMax >= 0) { "Physical object sequence must not be negative" }
        val next = maxOf(lastIssued, liveMax) + 1
        if (sequenceDao.setLastIssued(territoryId, type, next) != 1) {
            error("Physical object sequence scope is missing")
        }
        return next
    }
}

private fun PhysicalObjectType.entityLabel(): String = when (this) {
    PhysicalObjectType.HOLLOW -> "Hollow"
    PhysicalObjectType.LOG_HIVE -> "LogHive"
    PhysicalObjectType.APIARY -> "Apiary"
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

private fun HollowProperties.toEntity(id: UUID, name: String?) = HollowEntity(
    id, tree, entranceHeightCm, entranceAzimuthDeg, outerDiameterCm, internalDiameterCm, notes, name,
)

private fun LogHiveProperties.toEntity(id: UUID, name: String?) = LogHiveEntity(
    id, tree, entranceHeightCm, entranceAzimuthDeg, outerDiameterCm, material,
    internalDiameterCm, internalHeightCm, notes, name,
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
    Hollow(id, territoryId, sequenceNumber, latitude, longitude, createdAt, creatorObserverId, subtype.toProperties(), media, subtype.name, fixationDate, fixationAt, updatedAt)

private fun PhysicalObjectEntity.toLogHive(subtype: LogHiveEntity, media: List<PhysicalObjectMedia>) =
    LogHive(id, territoryId, sequenceNumber, latitude, longitude, createdAt, creatorObserverId, subtype.toProperties(), media, subtype.name, fixationDate, fixationAt, updatedAt)

private fun PhysicalObjectEntity.toApiary(subtype: ApiaryEntity) =
    Apiary(id, territoryId, sequenceNumber, latitude, longitude, createdAt, subtype.name, creatorObserverId, fixationDate, fixationAt, updatedAt)

private fun normalizeName(value: String?): String? = value?.trim()?.ifEmpty { null }
