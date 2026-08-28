package org.beesearch.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class FlightCycleAnalysisRulesTest {
    @Test
    fun firstCycleLasting20SecondsIsExcluded() {
        assertTrue(cycle(sequenceNumber = 1, durationMillis = 20_000).isExcludedFromFlightDurationAnalysis)
    }

    @Test
    fun firstCycleLasting59999MillisecondsIsExcluded() {
        assertTrue(cycle(sequenceNumber = 1, durationMillis = 59_999).isExcludedFromFlightDurationAnalysis)
    }

    @Test
    fun firstCycleLastingExactly60SecondsIsNotExcluded() {
        assertFalse(cycle(sequenceNumber = 1, durationMillis = 60_000).isExcludedFromFlightDurationAnalysis)
    }

    @Test
    fun firstCycleLongerThan60SecondsIsNotExcluded() {
        assertFalse(cycle(sequenceNumber = 1, durationMillis = 60_001).isExcludedFromFlightDurationAnalysis)
    }

    @Test
    fun laterShortCycleIsNotExcluded() {
        assertFalse(cycle(sequenceNumber = 2, durationMillis = 20_000).isExcludedFromFlightDurationAnalysis)
    }

    private fun cycle(sequenceNumber: Int, durationMillis: Long): FlightCycle {
        val departureTime = Instant.parse("2026-08-28T11:20:00Z")
        return FlightCycle(
            id = UUID.randomUUID(),
            beeId = UUID.randomUUID(),
            sequenceNumber = sequenceNumber,
            departureTime = departureTime,
            returnTime = departureTime.plusMillis(durationMillis),
            azimuthDeg = null,
            createdAt = departureTime,
            updatedAt = departureTime.plusMillis(durationMillis),
        )
    }
}
