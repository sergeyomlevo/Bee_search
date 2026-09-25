package org.beesearch.app.domain.model

import java.time.Instant
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
)

data class NewLogHive(
    val id: UUID,
    val territoryId: UUID,
    val creatorObserverId: UUID,
    val latitude: Double,
    val longitude: Double,
    val properties: LogHiveProperties,
    val media: List<PhysicalObjectMedia> = emptyList(),
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
) {
    val designation: String get() = PhysicalObjectType.APIARY.designation(sequenceNumber)
}

/** Concrete physical types grouped for a Territory without introducing a generic domain Object. */
data class TerritoryPhysicalObjects(
    val hollows: List<Hollow>,
    val logHives: List<LogHive>,
    val apiaries: List<Apiary>,
)

private fun requirePositive(value: Double, field: String) {
    require(value.isFinite() && value > 0.0) { "$field must be positive" }
}
