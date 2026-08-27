package org.beesearch.app

import org.beesearch.app.domain.location.LocationReading
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ObservationPointCreationDraftTest {
    @Test
    fun `moving crosshair changes confirmed coordinates but preserves original GPS measurement`() {
        val territoryId = UUID.randomUUID()
        val originalGps = LocationReading(
            latitude = 56.1959000,
            longitude = 42.7477000,
            accuracyMeters = 3.8,
            timestamp = Instant.parse("2026-08-27T08:31:00Z"),
        )
        val draft = ObservationPointCreationDraft(
            territoryId = territoryId,
            originalGps = originalGps,
        ).withSelectedCoordinates(
            latitude = 56.1959786,
            longitude = 42.7477116,
        )

        val point = draft.toNewObservationPoint()

        assertEquals(territoryId, point.territoryId)
        assertEquals(56.1959786, point.latitude, 0.0)
        assertEquals(42.7477116, point.longitude, 0.0)
        assertEquals(originalGps.latitude, point.gpsLatitude!!, 0.0)
        assertEquals(originalGps.longitude, point.gpsLongitude!!, 0.0)
        assertEquals(originalGps.accuracyMeters, point.gpsAccuracyM!!, 0.0)
    }

    @Test
    fun `GPS recenter request preserves original fix and current zoom is left to map camera`() {
        val originalGps = LocationReading(
            latitude = 56.1959000,
            longitude = 42.7477000,
            accuracyMeters = 3.8,
            timestamp = Instant.parse("2026-08-27T08:31:00Z"),
        )
        val movedDraft = ObservationPointCreationDraft(
            territoryId = UUID.randomUUID(),
            originalGps = originalGps,
        ).withSelectedCoordinates(56.2, 42.8)

        val requested = movedDraft.requestGpsRecenter()

        assertEquals(1L, requested.gpsRecenterRequestId)
        assertEquals(originalGps, requested.originalGps)
        assertEquals(movedDraft.selectedLatitude, requested.selectedLatitude, 0.0)
        assertEquals(movedDraft.selectedLongitude, requested.selectedLongitude, 0.0)
    }
}
