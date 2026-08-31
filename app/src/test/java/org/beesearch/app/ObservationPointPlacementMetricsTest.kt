package org.beesearch.app

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationPointPlacementMetricsTest {
    @Test
    fun `distance is zero when crosshair remains at original GPS fix`() {
        val distance = geodesicDistanceMeters(
            fromLatitude = 56.195946,
            fromLongitude = 42.747704,
            toLatitude = 56.195946,
            toLongitude = 42.747704,
        )

        assertEquals(0.0, distance, 0.001)
    }

    @Test
    fun `distance is geodesic between original fix and moved crosshair`() {
        val distance = geodesicDistanceMeters(
            fromLatitude = 56.195946,
            fromLongitude = 42.747704,
            toLatitude = 56.195912,
            toLongitude = 42.748041,
        )

        assertEquals(21.2, distance, 0.2)
    }

    @Test
    fun `initial geographic bearing covers cardinal and diagonal directions`() {
        assertEquals(0.0, initialGeographicBearingDegrees(0.0, 0.0, 1.0, 0.0), 0.01)
        assertEquals(90.0, initialGeographicBearingDegrees(0.0, 0.0, 0.0, 1.0), 0.01)
        assertEquals(180.0, initialGeographicBearingDegrees(0.0, 0.0, -1.0, 0.0), 0.01)
        assertEquals(270.0, initialGeographicBearingDegrees(0.0, 0.0, 0.0, -1.0), 0.01)
        assertEquals(45.0, initialGeographicBearingDegrees(0.0, 0.0, 1.0, 1.0), 0.02)
        assertEquals(225.0, initialGeographicBearingDegrees(0.0, 0.0, -1.0, -1.0), 0.02)
    }

    @Test
    fun `bearing normalization crosses zero without producing 360`() {
        assertEquals(0.0, normalizeDegrees(360.0), 0.0)
        assertEquals(359.9, normalizeDegrees(-0.1), 0.0001)
        assertEquals(1.0, normalizeDegrees(721.0), 0.0)
        assertEquals(90.0, initialGeographicBearingDegrees(0.0, 179.9, 0.0, -179.9), 0.01)
    }

    @Test
    fun `eight-direction sectors use deterministic clockwise boundaries`() {
        assertEquals("С", mapDirectionAbbreviation(0.0))
        assertEquals("С", mapDirectionAbbreviation(22.4999))
        assertEquals("СВ", mapDirectionAbbreviation(22.5))
        assertEquals("В", mapDirectionAbbreviation(90.0))
        assertEquals("Ю", mapDirectionAbbreviation(180.0))
        assertEquals("З", mapDirectionAbbreviation(270.0))
        assertEquals("С", mapDirectionAbbreviation(359.9999))
    }

    @Test
    fun `short field measurement combines distance true bearing and direction`() {
        val measurement = mapMeasurement(
            gpsPosition = MapTarget(56.195946, 42.747704),
            mapCenter = MapTarget(56.195912, 42.748041),
        )

        assertEquals(21.2, measurement.distanceMeters, 0.2)
        assertTrue(measurement.bearingDegrees in 95.0..105.0)
        assertEquals("В", measurement.directionAbbreviation)
        assertEquals("21 м · 100° В", formatMapMeasurement(measurement, Locale.forLanguageTag("ru-RU")))
    }

    @Test
    fun `sub-meter jitter is hidden only from map presentation`() {
        val gps = MapTarget(56.195946, 42.747704)

        assertNull(visibleMapMeasurement(gps, gps))
        assertNull(visibleMapMeasurement(gps, MapTarget(56.195949, 42.747704)))
    }

    @Test
    fun `new GPS fix updates measurement without changing selected map center`() {
        val selectedCenter = MapTarget(56.1965, 42.7490)
        val first = mapMeasurement(MapTarget(56.1959, 42.7477), selectedCenter)
        val updated = mapMeasurement(MapTarget(56.1961, 42.7482), selectedCenter)

        assertTrue(updated.distanceMeters < first.distanceMeters)
    }

    @Test
    fun `offset below ten meters uses one decimal without false extra precision`() {
        val russian = Locale.forLanguageTag("ru-RU")

        assertEquals("3,8 м", formatManualOffsetMeters(3.84, russian))
        assertEquals("9,9 м", formatManualOffsetMeters(9.94, russian))
    }

    @Test
    fun `offset at ten meters and above uses whole meters`() {
        val russian = Locale.forLanguageTag("ru-RU")

        assertEquals("10 м", formatManualOffsetMeters(9.96, russian))
        assertEquals("13 м", formatManualOffsetMeters(12.6, russian))
    }
}
