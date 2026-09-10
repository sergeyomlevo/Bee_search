package org.beesearch.app.domain.model

data class ObservationDataCounts(
    val observationPoints: Int,
    val bees: Int,
    val flightCycles: Int,
) {
    val isEmpty: Boolean
        get() = observationPoints == 0 && bees == 0 && flightCycles == 0
}
