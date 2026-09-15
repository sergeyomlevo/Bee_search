package org.beesearch.app.domain.model

/**
 * Individual FlightCycle records all follow the same duration-evidence path.
 * A first cycle is not excluded merely because its sequence number is 1.
 */
val FlightCycle.isExcludedFromFlightDurationAnalysis: Boolean
    get() = false
