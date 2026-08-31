package org.beesearch.app

import org.beesearch.app.domain.location.LocationReading
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ObservationPointCreationDraftTest {
    @Test
    fun `map center becomes confirmed coordinates while original GPS measurement is preserved`() {
        val territoryId = UUID.randomUUID()
        val originalGps = LocationReading(
            latitude = 56.1959000,
            longitude = 42.7477000,
            accuracyMeters = 3.8,
            timestamp = Instant.parse("2026-08-27T08:31:00Z"),
        )
        val draft = ObservationPointCreationDraft.fromMapCenter(
            territoryId = territoryId,
            originalGps = originalGps,
            mapCenter = MapTarget(56.1959786, 42.7477116),
            observerCodeInput = "GSE",
        )

        val point = draft.toNewObservationPoint()

        assertEquals(territoryId, point.territoryId)
        assertEquals(56.1959786, point.latitude, 0.0)
        assertEquals(42.7477116, point.longitude, 0.0)
        assertEquals(originalGps.latitude, point.gpsLatitude!!, 0.0)
        assertEquals(originalGps.longitude, point.gpsLongitude!!, 0.0)
        assertEquals(originalGps.accuracyMeters, point.gpsAccuracyM!!, 0.0)
    }
}
