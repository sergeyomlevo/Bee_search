package org.beesearch.app.domain.model

/** Inclusive record-count bounds. Null means an open bound; zero is a real value. */
data class CountRange(val min: Int? = null, val max: Int? = null) {
    init {
        require(min == null || min >= 0)
        require(max == null || max >= 0)
        require(min == null || max == null || min <= max)
    }
    val isActive: Boolean get() = min != null || max != null
}

/** Inclusive centimetre bounds over existing measured properties. */
data class MeasurementRange(val min: Double? = null, val max: Double? = null) {
    init {
        require(min == null || (min.isFinite() && min >= 0))
        require(max == null || (max.isFinite() && max >= 0))
        require(min == null || max == null || min <= max)
    }
    val isActive: Boolean get() = min != null || max != null
}

sealed interface ResearchObjectFilterSet {
    val dateInterval: ResearchDateInterval?
}

data class ObservationPointFilterSet(
    override val dateInterval: ResearchDateInterval? = null,
    val beeCount: CountRange = CountRange(),
    /** All FlightCycle records of all owned Bee records, including open cycles. */
    val flightCycleCount: CountRange = CountRange(),
) : ResearchObjectFilterSet

/** The same existing measurable properties are supported by Hollow and LogHive. */
data class PhysicalObjectFilterSet(
    override val dateInterval: ResearchDateInterval? = null,
    val entranceHeightCm: MeasurementRange = MeasurementRange(),
    val outerDiameterCm: MeasurementRange = MeasurementRange(),
) : ResearchObjectFilterSet
