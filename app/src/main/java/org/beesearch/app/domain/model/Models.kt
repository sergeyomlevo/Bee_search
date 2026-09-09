package org.beesearch.app.domain.model

import java.time.Instant
import java.util.UUID

data class AppSettings(
    val currentTerritoryId: UUID?,
    val currentObserverId: UUID?,
)

data class Territory(
    val id: UUID,
    val code: String,
    val name: String,
    val region: String,
    val district: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Observer(
    val id: UUID,
    val code: String,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val contact: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val displayName: String
        get() = listOfNotNull(lastName, firstName, middleName).joinToString(" ")
}

data class ObservationPoint(
    val id: UUID,
    val territoryId: UUID,
    val observerId: UUID,
    val observationYear: Int,
    val pointNumber: Int,
    val beePresenceResult: BeePresenceResult?,
    val code: String?,
    val latitude: Double,
    val longitude: Double,
    val gpsLatitude: Double?,
    val gpsLongitude: Double?,
    val gpsAccuracyM: Double?,
    val createdAt: Instant,
    /**
     * Timestamp of the atomically recorded initial group release, if it has
     * happened. This is workflow provenance, not a separate GroupRelease
     * entity or a stored Bee state.
     */
    val initialGroupReleaseAt: Instant? = null,
    val completedAt: Instant?,
)

enum class BeePresenceResult {
    BEES_FOUND,
    NO_BEES_FOUND,
}

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
    val azimuthCaptureConsumed: Boolean,
    /** True only when this cycle was created by the initial group-release transaction. */
    val isInitialGroupLaunch: Boolean = false,
    /**
     * The bounded initial-launch correction is safe only before a real return
     * has ever been recorded for this cycle. Clearing an erroneous return does
     * not reopen this historical-deletion permission.
     */
    val isInitialGroupLaunchCorrectionEligible: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class BeeUndoAction {
    AZIMUTH,
    RETURN,
    NEXT_FLIGHT,
    INITIAL_GROUP_LAUNCH,
}

data class NewObservationPoint(
    val territoryId: UUID,
    val observerId: UUID,
    val code: String? = null,
    val latitude: Double,
    val longitude: Double,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsAccuracyM: Double? = null,
)
