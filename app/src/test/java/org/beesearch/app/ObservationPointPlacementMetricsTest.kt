package org.beesearch.app

import java.util.Locale
import org.junit.Assert.assertEquals
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
