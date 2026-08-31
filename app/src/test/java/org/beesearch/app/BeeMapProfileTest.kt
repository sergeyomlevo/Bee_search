package org.beesearch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BeeMapProfileTest {
    @Test
    fun fieldProfileKeepsAcceptedSourceAndUiZoomContract() {
        val profile = beeSearchFieldMapProfile(
            "https://maps.example/field/v1/dataset/style-v3/style.json",
        )

        assertEquals("bee-search-field", profile.profileId)
        assertEquals("style-v3", profile.styleVersion)
        assertEquals(15.0, profile.sourceMaxZoom, 0.0)
        assertEquals(20.0, profile.uiMaxZoom, 0.0)
    }

    @Test
    fun fieldProfileRequiresHttpStyleJsonUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            beeSearchFieldMapProfile("file:///tmp/style.json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            beeSearchFieldMapProfile("https://maps.example/field/v1/not-a-style.txt")
        }
    }
}
