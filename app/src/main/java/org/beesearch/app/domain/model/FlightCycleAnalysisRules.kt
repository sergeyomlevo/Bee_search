package org.beesearch.app.domain.model

import java.time.Duration

private val minimumMeaningfulInitialFlightDuration: Duration = Duration.ofSeconds(60)

/**
 * Whether this raw observation must be omitted from calculations based on a
 * meaningful flight duration.
 *
 * The cycle remains valid historical data. This derived property neither
 * changes nor replaces its persisted timestamps.
 */
val FlightCycle.isExcludedFromFlightDurationAnalysis: Boolean
    get() {
        val returnedAt = returnTime ?: return false
        return sequenceNumber == 1 &&
            Duration.between(departureTime, returnedAt) < minimumMeaningfulInitialFlightDuration
    }
