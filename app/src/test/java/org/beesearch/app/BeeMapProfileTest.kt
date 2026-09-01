package org.beesearch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BeeMapProfileTest {
    @Test
    fun temporaryProfileUsesHttpsOsmStandardWithVisibleAttribution() {
        val profile = beeSearchFieldMapProfile()

        assertEquals("osm-standard-evaluation", profile.profileId)
        assertTrue(profile.styleJson.contains("https://tile.openstreetmap.org/{z}/{x}/{y}.png"))
        assertTrue(profile.styleJson.contains("OpenStreetMap contributors"))
        assertFalse(profile.styleJson.contains("http://"))
        assertEquals(19.0, profile.sourceMaxZoom, 0.0)
        assertEquals(20.0, profile.uiMaxZoom, 0.0)
    }
}
