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
}
