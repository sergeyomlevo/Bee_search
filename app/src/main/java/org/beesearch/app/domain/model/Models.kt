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

/**
 * Position of the colour mark on a Bee.
 *
 * A new Bee is always marked on the thorax or on the abdomen. [LEFT_WING] is a
 * legacy compatibility value: it can only be read from data written before the
 * thorax/abdomen marking system, it is never offered for a new Bee, and it is
 * deliberately not reinterpreted as either real position.
 */
enum class MarkPosition {
    THORAX,
    ABDOMEN,
    LEFT_WING,
    ;

    /** Whether this position may be chosen while marking a new Bee. */
    val isOfferedForNewBee: Boolean
        get() = this != LEFT_WING

    /**
     * Persisted tokens that denote this position. A position may answer to more
     * than one token because earlier releases persisted `NONE` and `RIGHT_WING`,
     * which are confirmed to have meant the thorax and the abdomen in real field
     * data. Persistence and backup code must match on the whole set, otherwise a
     * legacy row would stop participating in duplicate-mark protection.
     */
    val persistedTokens: List<String>
        get() = when (this) {
            THORAX -> listOf("THORAX", "NONE")
            ABDOMEN -> listOf("ABDOMEN", "RIGHT_WING")
            LEFT_WING -> listOf("LEFT_WING")
        }

    companion object {
        /** Positions offered when marking a new Bee, in presentation order. */
        val newBeePositions: List<MarkPosition> = listOf(THORAX, ABDOMEN)

        /**
         * Reads a persisted mark position token, including the legacy tokens of
         * earlier releases. Returns `null` for an unknown token.
         */
        fun fromPersistedToken(token: String): MarkPosition? = when (token) {
            "THORAX", "NONE" -> THORAX
            "ABDOMEN", "RIGHT_WING" -> ABDOMEN
            "LEFT_WING" -> LEFT_WING
            else -> null
        }
    }
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
    /** Legacy backup provenance. New individual departures always store false. */
    val isInitialGroupLaunch: Boolean = false,
    /**
     * The first departure may be cancelled only until its first return is
     * recorded. Clearing an erroneous return does not reopen this permission.
     */
    val isFirstDepartureCancellationEligible: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class StartedBeeFlight(
    val bee: Bee,
    val flightCycle: FlightCycle,
)

enum class BeeUndoAction {
    AZIMUTH,
    RETURN,
    NEXT_FLIGHT,
    FIRST_DEPARTURE,
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
