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

data class Hollow(
    val id: UUID,
    val territoryId: UUID,
    val sequenceNumber: Int,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Instant,
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
) {
    val designation: String get() = PhysicalObjectType.APIARY.designation(sequenceNumber)
}

/** Concrete physical types grouped for a Territory without introducing a generic domain Object. */
data class TerritoryPhysicalObjects(
    val hollows: List<Hollow>,
    val logHives: List<LogHive>,
    val apiaries: List<Apiary>,
)
