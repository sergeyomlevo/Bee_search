package org.beesearch.app.domain.model

import java.time.Instant
import java.util.UUID

data class AppSettings(
    val currentTerritoryId: UUID?,
    val observerCode: String?,
)

data class Territory(
    val id: UUID,
    val code: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ObservationPoint(
    val id: UUID,
    val territoryId: UUID,
    val observerCode: String,
    val code: String?,
    val latitude: Double,
    val longitude: Double,
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val gpsAccuracyM: Double?,
    val createdAt: Instant,
    val completedAt: Instant?,
)

enum class MarkPosition {
    NONE,
    RIGHT_WING,
    LEFT_WING,
}

data class Bee(
    val id: UUID,
    val observationPointId: UUID,
    val markColor: String,
    val markPosition: MarkPosition,
    val createdAt: Instant,
)

data class FlightCycle(
    val id: UUID,
    val beeId: UUID,
    val sequenceNumber: Int,
    val departureTime: Instant,
    val returnTime: Instant?,
    val azimuthDeg: Double?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class NewObservationPoint(
    val territoryId: UUID,
    val code: String? = null,
    val latitude: Double,
    val longitude: Double,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsAccuracyM: Double? = null,
)
