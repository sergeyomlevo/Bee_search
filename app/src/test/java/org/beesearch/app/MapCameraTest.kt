package org.beesearch.app

import org.beesearch.app.ui.map.formatMapZoom
import org.beesearch.app.ui.map.shouldShowGpsTargetGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCameraTest {
    @Test
    fun `zoom indicator uses a compact rounded integer`() {
        assertEquals("z 16", formatMapZoom(16.44))
        assertEquals("z 17", formatMapZoom(16.5))
    }

    @Test
    fun `main field map shows GPS target guide whenever measurement is visible`() {
        assertTrue(
            shouldShowGpsTargetGuide(
                isFieldMap = true,
                coverageSelectionActive = false,
                locationSelectionActive = false,
                measurementAvailable = true,
            ),
        )
        assertFalse(
            shouldShowGpsTargetGuide(
                isFieldMap = true,
                coverageSelectionActive = false,
                locationSelectionActive = false,
                measurementAvailable = false,
            ),
        )
    }
}
