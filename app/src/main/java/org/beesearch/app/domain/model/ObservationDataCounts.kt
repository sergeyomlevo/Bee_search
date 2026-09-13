package org.beesearch.app.domain.model

import java.time.Instant
import java.util.UUID

data class ObservationDataCounts(
    val observationPoints: Int,
    val bees: Int,
    val flightCycles: Int,
) {
    val isEmpty: Boolean
        get() = observationPoints == 0 && bees == 0 && flightCycles == 0
}

data class CompletedObservationPointSummary(
    val id: UUID,
    val createdAt: Instant,
    val observationYear: Int,
    val pointNumber: Int,
    val territoryCode: String,
    val territoryName: String,
    val beeCount: Int,
)
