package org.beesearch.app.domain.model

import java.time.Instant
import java.util.UUID

/** Read models for the saved ObservationPoint browser. */
data class ObservationPointSummary(
    val id: UUID,
    val territoryId: UUID,
    val observationYear: Int,
    val pointNumber: Int,
    val code: String?,
    val beePresenceResult: BeePresenceResult?,
    val latitude: Double,
    val longitude: Double,
    val gpsAccuracyM: Double?,
    val createdAt: Instant,
    val completedAt: Instant?,
    val beeCount: Int,
    val completedFlightCycleCount: Int,
)

data class ObservationPointDetail(
    val point: ObservationPoint,
    val territory: Territory,
    val observer: Observer,
    val beeHistories: List<BeeObservationHistory>,
)

data class BeeObservationHistory(
    val bee: Bee,
    val flightCycles: List<FlightCycle>,
)
