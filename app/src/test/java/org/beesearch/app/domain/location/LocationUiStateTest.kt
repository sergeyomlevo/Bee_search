package org.beesearch.app.domain.location

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationUiStateTest {
    @Test
    fun availableState_preserves_structured_reading() {
        val reading = LocationReading(55.75, 37.61, 8.5, Instant.ofEpochMilli(1234))

        assertEquals(reading, (LocationUiState.Available(reading)).reading)
    }

    @Test
    fun permission_required_is_distinct_from_waiting_for_fix() {
        assertEquals(LocationUiState.PermissionRequired, LocationUiState.PermissionRequired)
        assert(LocationUiState.PermissionRequired != LocationUiState.WaitingForFix)
    }

    @Test
    fun available_fix_remains_visible_while_foreground_tracking_restarts() {
        val available = LocationUiState.Available(
            LocationReading(55.75, 37.61, 8.5, Instant.ofEpochMilli(1234)),
        )

        assertEquals(available, available.awaitingNextFix(preserveAvailable = true))
        assertEquals(
            LocationUiState.WaitingForFix,
            available.awaitingNextFix(preserveAvailable = false),
        )
    }

    @Test
    fun state_without_a_fix_waits_for_the_next_location_callback() {
        assertEquals(
            LocationUiState.WaitingForFix,
            LocationUiState.PermissionRequired.awaitingNextFix(preserveAvailable = true),
        )
        assertEquals(
            LocationUiState.WaitingForFix,
            LocationUiState.Unavailable("GPS выключен").awaitingNextFix(preserveAvailable = true),
        )
    }
}
