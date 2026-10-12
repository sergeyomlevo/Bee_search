package org.beesearch.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exact automatic infrastructure in MapLibre Native android-v13.5.1 AnnotationManager::updateStyle.
 * Shape layers and annotation images require annotation API calls; this fixture makes none.
 * https://github.com/maplibre/maplibre-native/blob/android-v13.5.1/src/mbgl/annotation/annotation_manager.cpp
 */
internal object MapOverlayNativeResourceParity {
    private val sdkSources = setOf("org.maplibre.annotations")
    private val sdkLayers = setOf("org.maplibre.annotations.points")

    fun assertSources(expectedApplicationIds: Set<String>, actualIds: List<String>) =
        assertParity(expectedApplicationIds, actualIds, sdkSources)

    fun assertLayers(expectedApplicationIds: Set<String>, actualIds: List<String>) =
        assertParity(expectedApplicationIds, actualIds, sdkLayers)

    private fun assertParity(expected: Set<String>, actual: List<String>, sdkIds: Set<String>) {
        assertTrue("Application declaration collides with SDK infrastructure", expected.intersect(sdkIds).isEmpty())
        assertEquals("Duplicate native resource IDs", actual.size, actual.toSet().size)
        val actualSdk = actual.toSet().intersect(sdkIds)
        assertEquals("Exact SDK infrastructure set", sdkIds, actualSdk)
        assertEquals("Exact application resource parity", expected, actual.toSet() - actualSdk)
    }
}

class MapOverlayResourceParityTest {
    private val expected = setOf("openstreetmap-standard", "test-areas", "beesearch-overlay-fixture")
    private val sdkSource = "org.maplibre.annotations"

    @Test fun exactSdkInfrastructureAndApplicationIdsPass() {
        MapOverlayNativeResourceParity.assertSources(expected, expected.toList() + sdkSource)
        MapOverlayNativeResourceParity.assertLayers(setOf("application-layer"), listOf("application-layer", "org.maplibre.annotations.points"))
    }

    @Test fun unexpectedApplicationSourceFails() {
        assertThrows(AssertionError::class.java) {
            MapOverlayNativeResourceParity.assertSources(expected, expected.toList() + sdkSource + "beesearch-unexpected")
        }
    }

    @Test fun missingRequiredApplicationSourceFails() {
        assertThrows(AssertionError::class.java) {
            MapOverlayNativeResourceParity.assertSources(expected, expected.filterNot { it == "test-areas" } + sdkSource)
        }
    }

    @Test fun wrongExpectedApplicationSourceIdFails() {
        assertThrows(AssertionError::class.java) {
            MapOverlayNativeResourceParity.assertSources(expected - "test-areas" + "wrong-id", expected.toList() + sdkSource)
        }
    }

    @Test fun sdkLookingExtraSourcesAreNotTolerated() {
        for (extra in listOf("org.maplibre.annotations.extra", "org.maplibre.unknown", "org.unknown")) {
            assertThrows(AssertionError::class.java) {
                MapOverlayNativeResourceParity.assertSources(expected, expected.toList() + sdkSource + extra)
            }
        }
    }

    @Test fun sdkResourceCannotHideAnApplicationDeclarationOrDuplicate() {
        assertThrows(AssertionError::class.java) {
            MapOverlayNativeResourceParity.assertSources(expected + sdkSource, expected.toList() + sdkSource)
        }
        assertThrows(AssertionError::class.java) {
            MapOverlayNativeResourceParity.assertSources(expected, expected.toList() + sdkSource + sdkSource)
        }
        assertThrows(AssertionError::class.java) {
            MapOverlayNativeResourceParity.assertSources(expected, expected.toList())
        }
    }

    @Test fun onlyExactAutomaticSdkLayerIsTolerated() {
        for (extra in listOf("org.maplibre.annotations.shape.0", "org.maplibre.annotations.points.extra", "beesearch-unexpected-layer")) {
            assertThrows(AssertionError::class.java) {
                MapOverlayNativeResourceParity.assertLayers(setOf("application-layer"), listOf("application-layer", "org.maplibre.annotations.points", extra))
            }
        }
    }
}