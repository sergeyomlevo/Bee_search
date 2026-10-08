package org.beesearch.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class PhysicalObjectType(val designationPrefix: String) {
    HOLLOW("Дупло"),
    LOG_HIVE("Колода"),
    APIARY("Пасека"),
    ;

    fun designation(sequenceNumber: Int): String {
        require(sequenceNumber > 0) { "Physical object sequence number must be positive" }
        return "$designationPrefix $sequenceNumber"
    }
}

enum class PhysicalObjectMediaType { IMAGE, VIDEO }

data class PhysicalObjectMedia(
    val id: UUID,
    val physicalObjectId: UUID,
    val type: PhysicalObjectMediaType,
    val relativePath: String,
    val originalFileName: String?,
    val mimeType: String?,
    val byteSize: Long,
    val sha256: String,
    val createdAt: Instant,
)

data class HollowProperties(
    val tree: String,
    val entranceHeightCm: Double,
    val entranceAzimuthDeg: Int,
    val outerDiameterCm: Double,
    val internalDiameterCm: Double?,
    val notes: String?,
) {
    init {
        require(tree.isNotBlank() && tree == tree.trim()) { "Hollow tree is required and must be trimmed" }
        requirePositive(entranceHeightCm, "Hollow entrance height")
        require(entranceAzimuthDeg in 0..359) { "Hollow entrance azimuth must be in 0..359" }
        requirePositive(outerDiameterCm, "Hollow outer diameter")
        internalDiameterCm?.let { requirePositive(it, "Hollow internal diameter") }
        require(notes == notes?.trim()?.ifEmpty { null }) { "Hollow notes must be trimmed" }
    }
}

data class LogHiveProperties(
    val tree: String,
    val entranceHeightCm: Double,
    val entranceAzimuthDeg: Int,
    val outerDiameterCm: Double,
    val material: String,
    val internalDiameterCm: Double,
    val internalHeightCm: Double,
    val notes: String?,
) {
    init {
        require(tree.isNotBlank() && tree == tree.trim()) { "Log hive tree is required and must be trimmed" }
        requirePositive(entranceHeightCm, "Log hive entrance height")
        require(entranceAzimuthDeg in 0..359) { "Log hive entrance azimuth must be in 0..359" }
        requirePositive(outerDiameterCm, "Log hive outer diameter")
        require(material.isNotBlank() && material == material.trim()) { "Log hive material is required and must be trimmed" }
        requirePositive(internalDiameterCm, "Log hive internal diameter")
        requirePositive(internalHeightCm, "Log hive internal height")
        require(notes == notes?.trim()?.ifEmpty { null }) { "Log hive notes must be trimmed" }
    }
}

data class NewHollow(
    val id: UUID,
    val territoryId: UUID,
    val creatorObserverId: UUID,
    val latitude: Double,
    val longitude: Double,
    val properties: HollowProperties,
    val media: List<PhysicalObjectMedia> = emptyList(),
    val name: String? = null,
)

data class NewLogHive(
    val id: UUID,
    val territoryId: UUID,
    val creatorObserverId: UUID,
    val latitude: Double,
    val longitude: Double,
    val properties: LogHiveProperties,
    val media: List<PhysicalObjectMedia> = emptyList(),
    val name: String? = null,
)

data class Hollow(
    val id: UUID,
    val territoryId: UUID,
    val sequenceNumber: Int,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Instant,
    val creatorObserverId: UUID?,
    val properties: HollowProperties?,
    val media: List<PhysicalObjectMedia> = emptyList(),
    val name: String? = null,
    val fixationDate: LocalDate? = null,
) {
    val designation: String get() = PhysicalObjectType.HOLLOW.designation(sequenceNumber)
}

data class LogHive(
    val id: UUID,
    val territoryId: UUID,
    val sequenceNumber: Int,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Instant,
    val creatorObserverId: UUID?,
    val properties: LogHiveProperties?,
    val media: List<PhysicalObjectMedia> = emptyList(),
    val name: String? = null,
    val fixationDate: LocalDate? = null,
) {
    val designation: String get() = PhysicalObjectType.LOG_HIVE.designation(sequenceNumber)
}

data class Apiary(
    val id: UUID,
    val territoryId: UUID,
    val sequenceNumber: Int,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Instant,
    val name: String?,
    val creatorObserverId: UUID? = null,
    val fixationDate: LocalDate? = null,
) {
    val designation: String get() = PhysicalObjectType.APIARY.designation(sequenceNumber)
}

/** Concrete physical types grouped for a Territory without introducing a generic domain Object. */
data class TerritoryPhysicalObjects(
    val hollows: List<Hollow>,
    val logHives: List<LogHive>,
    val apiaries: List<Apiary>,
)

/**
 * One kind of persisted reference that can block the physical deletion of an object.
 *
 * Only kinds that really exist are listed: speculative future references are not modelled in advance
 * (AGENTS.md §16), and a new kind is added together with the reference that creates it. The kind is a
 * user-facing fact, not a table name, so the UI can name the blocking data without learning how the
 * reference is stored. [label] is the plural noun used in the blocked-deletion message.
 */
enum class PhysicalObjectReferenceKind(val label: String) {
    /** A Bee that explicitly states it belongs to this physical object. */
    BEE("Пчёлы"),
}

/**
 * How many rows of one [PhysicalObjectReferenceKind] point at one Physical Object.
 *
 * The count is the user-facing quantity of the blocking data; it is deliberately not a Boolean, so
 * the message can say how much data is involved. A reference is only reported when it exists.
 */
data class PhysicalObjectReference(
    val kind: PhysicalObjectReferenceKind,
    val count: Int,
) {
    init {
        require(count > 0) { "A blocking reference is reported only when at least one row exists" }
    }
}

private fun requirePositive(value: Double, field: String) {
    require(value.isFinite() && value > 0.0) { "$field must be positive" }
}
