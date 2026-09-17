package org.beesearch.app

import org.beesearch.app.domain.location.LocationReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ObservationPointCreationDraftTest {
    @Test
    fun `map center becomes confirmed coordinates while original GPS measurement is preserved`() {
        val territoryId = UUID.randomUUID()
        val observerId = UUID.randomUUID()
        val originalGps = LocationReading(
            latitude = 56.1959000,
            longitude = 42.7477000,
            accuracyMeters = 3.8,
            timestamp = Instant.parse("2026-08-27T08:31:00Z"),
        )
        val draft = ObservationPointCreationDraft(
            territoryId = territoryId,
            observerId = observerId,
            originalGps = originalGps,
        ).withSelectedMapCenter(MapTarget(56.1959786, 42.7477116))

        val point = draft.toNewObservationPoint()

        assertEquals(territoryId, point.territoryId)
        assertEquals(observerId, point.observerId)
        assertEquals(56.1959786, point.latitude, 0.0)
        assertEquals(42.7477116, point.longitude, 0.0)
        assertEquals(originalGps.latitude, point.gpsLatitude!!, 0.0)
        assertEquals(originalGps.longitude, point.gpsLongitude!!, 0.0)
        assertEquals(originalGps.accuracyMeters, point.gpsAccuracyM!!, 0.0)
    }

    @Test
    fun `a new placement draft does not reuse confirmed coordinates from an aborted draft`() {
        val originalGps = LocationReading(
            latitude = 56.1959000,
            longitude = 42.7477000,
            accuracyMeters = 3.8,
            timestamp = Instant.parse("2026-08-27T08:31:00Z"),
        )
        val abortedDraft = ObservationPointCreationDraft(
            territoryId = UUID.randomUUID(),
            observerId = UUID.randomUUID(),
            originalGps = originalGps,
        ).withSelectedMapCenter(MapTarget(56.1965000, 42.7485000))
        val newDraft = ObservationPointCreationDraft(
            territoryId = UUID.randomUUID(),
            observerId = UUID.randomUUID(),
            originalGps = originalGps,
        )

        assertEquals(56.1965000, abortedDraft.selectedLatitude, 0.0)
        assertEquals(originalGps.latitude, newDraft.selectedLatitude, 0.0)
        assertEquals(originalGps.longitude, newDraft.selectedLongitude, 0.0)
    }

    @Test
    fun `later GPS reading cannot change coordinates captured by create action`() {
        val capturedDraft = ObservationPointCreationDraft(
            territoryId = UUID.randomUUID(),
            observerId = UUID.randomUUID(),
            originalGps = LocationReading(
                latitude = 56.1959000,
                longitude = 42.7477000,
                accuracyMeters = 3.8,
                timestamp = Instant.parse("2026-08-27T08:31:00Z"),
            ),
        ).withSelectedMapCenter(MapTarget(56.1965000, 42.7485000))

        val laterGpsReading = LocationReading(
            latitude = 56.2100000,
            longitude = 42.7600000,
            accuracyMeters = 2.0,
            timestamp = Instant.parse("2026-08-27T08:32:00Z"),
        )
        val point = capturedDraft.toNewObservationPoint()

        assertEquals(56.1965000, point.latitude, 0.0)
        assertEquals(42.7485000, point.longitude, 0.0)
        assertEquals(56.1959000, point.gpsLatitude!!, 0.0)
        assertEquals(42.7477000, point.gpsLongitude!!, 0.0)
        assertTrue(laterGpsReading.latitude != point.latitude)
        assertTrue(laterGpsReading.longitude != point.longitude)
    }
}
