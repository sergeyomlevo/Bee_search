package org.beesearch.app

import org.beesearch.app.ui.map.formatMapZoom
import org.junit.Assert.assertEquals
import org.junit.Test

class MapCameraTest {
    @Test
    fun `zoom indicator uses one decimal with stable separator`() {
        assertEquals("z 16.4", formatMapZoom(16.44))
        assertEquals("z 16.5", formatMapZoom(16.45))
    }
}
