package org.beesearch.app.ui.map

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the source-set boundary; release build compilation is also checked by the host gate. */
class MarkerPreviewVariantBoundaryTest {
    @Test
    fun productVariantsHaveNoPreviewControlsOrSpecimens() {
        listOf("release", "beta").forEach { variant ->
            val text = File("src/$variant/java/org/beesearch/app/ui/map/MarkerVisualPreview.kt").readText()
            assertTrue(text.contains("= Unit"))
            assertFalse(text.contains("ResearchObjectMarker("))
            assertFalse(text.contains("TextButton("))
            assertFalse(text.contains("ResearchMarkerType"))
        }
        val main = File("src/main/java/org/beesearch/app/ui/map/BeeMap.kt").readText()
        assertTrue(main.contains("MarkerVisualPreview("))
        assertFalse(main.contains("ResearchMarkerType"))
    }
}
