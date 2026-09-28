package org.beesearch.app

import java.io.File
import org.beesearch.app.ui.map.BeeSearchMapPackageCompatibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeeMapProfileTest {
    private val sentinelArchive = File("build/tmp/sentinel-area-z10-13-linear-b.pmtiles")
    private val vectorArchive = File("build/tmp/central-volga-260830.pmtiles")

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

    /**
     * The temporary Sentinel basemaps and their selector items are gated by the debug build type.
     * Pinning the gate to BuildConfig.DEBUG keeps that isolation from drifting: the debug build
     * type is the only debuggable variant, so Beta and Stable evaluate it to false.
     */
    @Test
    fun developerBasemapsAreGatedByTheDebugBuildType() {
        assertEquals(BuildConfig.DEBUG, devMapBasemapsEnabled)
    }

    @Test
    fun devSentinelProfileIsALocalRasterPmtilesArchiveForZ10ToZ13() {
        val profile = beeSearchDevSentinelMapProfile(sentinelArchive)
        val expectedUrl = "pmtiles://file://" + sentinelArchive.absolutePath.replace('\\', '/')

        assertEquals("sentinel-area-raster-dev", profile.profileId)
        assertEquals(13.0, profile.sourceMaxZoom, 0.0)
        assertTrue("style must read the archive through the proven pmtiles://file:// form",
            profile.styleJson.contains(expectedUrl))
        assertTrue(profile.styleJson.contains("\"type\": \"raster\""))
        assertTrue(profile.styleJson.contains("\"tileSize\": 256"))
        assertTrue(profile.styleJson.contains("\"minzoom\": 10"))
        assertTrue(profile.styleJson.contains("\"maxzoom\": 13"))
        assertTrue(profile.styleJson.contains("Sentinel"))
        assertFalse("the online OSM tiles must not leak into the Sentinel profile",
            profile.styleJson.contains("tile.openstreetmap.org"))
    }

    /** Zoom is capped at the raster package maxzoom: above z13 the imagery only blurs. */
    @Test
    fun devSentinelProfileStopsAtThePackageMaxZoom() {
        assertEquals(13.0, beeSearchDevSentinelMapProfile(sentinelArchive).uiMaxZoom, 0.0)
    }

    @Test
    fun devHybridProfileCarriesBothPackagesAsIndependentSources() {
        val profile = beeSearchDevHybridMapProfile(sentinelArchive, vectorArchive)
        val sentinelUrl = "pmtiles://file://" + sentinelArchive.absolutePath.replace('\\', '/')
        val vectorUrl = "pmtiles://file://" + vectorArchive.absolutePath.replace('\\', '/')

        assertEquals("sentinel-vector-hybrid-dev", profile.profileId)
        assertEquals(13.0, profile.sourceMaxZoom, 0.0)
        assertEquals(13.0, profile.uiMaxZoom, 0.0)
        assertTrue("hybrid must keep the Sentinel raster source", profile.styleJson.contains(sentinelUrl))
        assertTrue("hybrid must keep the vector source", profile.styleJson.contains(vectorUrl))
        assertTrue(profile.styleJson.contains("\"type\": \"raster\""))
        assertTrue(profile.styleJson.contains("\"type\": \"vector\""))
        assertFalse("hybrid must stay offline", profile.styleJson.contains("http"))
    }

    /** The imagery is the base layer: every vector line and label sits above the raster. */
    @Test
    fun devHybridDrawsVectorOverlaysAboveTheSentinelRaster() {
        val style = beeSearchDevHybridMapProfile(sentinelArchive, vectorArchive).styleJson
        val rasterIndex = style.indexOf("\"id\": \"sentinel-area-raster\"")
        assertTrue("raster layer must exist", rasterIndex >= 0)
        for (layer in listOf(
            "waterways", "roads", "tracks", "railway",
            "place-labels", "water-labels", "waterway-labels", "road-labels",
        )) {
            val index = style.indexOf("\"id\": \"$layer\"")
            assertTrue("$layer must be part of the hybrid overlay", index >= 0)
            assertTrue("$layer must be drawn above the imagery", index > rasterIndex)
        }
    }

    /** No large vector fill may cover the imagery in the hybrid. */
    @Test
    fun devHybridKeepsTheBigVectorFillsOut() {
        val style = beeSearchDevHybridMapProfile(sentinelArchive, vectorArchive).styleJson
        for (layer in listOf("open-land", "forest", "wetland", "buildings")) {
            assertFalse("$layer must not cover the imagery in the hybrid", style.contains("\"id\": \"$layer\""))
        }
        assertFalse(
            "the vector water fill must not tint the imagery in the hybrid",
            style.contains("\"id\": \"water\", \"type\": \"fill\""),
        )
        assertFalse(
            "the vector background must not replace the raster background",
            style.contains("background-color\": \"#f3efdf"),
        )
    }

    /** The overlay is the vector map's own definition, not a copy that can drift from it. */
    @Test
    fun devHybridReusesTheOfflineVectorLayerDefinitions() {
        val vectorStyle = localVectorPmtilesStyle("pmtiles://file:///vector.pmtiles", "vector")
        val hybridStyle = beeSearchDevHybridMapProfile(sentinelArchive, vectorArchive).styleJson

        for (layer in listOf(
            "{ \"id\": \"waterways\", \"type\": \"line\"",
            "{ \"id\": \"roads\", \"type\": \"line\"",
            "{ \"id\": \"tracks\", \"type\": \"line\"",
            "{ \"id\": \"railway\", \"type\": \"line\"",
            "{ \"id\": \"place-labels\", \"type\": \"symbol\"",
            "{ \"id\": \"road-labels\", \"type\": \"symbol\"",
        )) {
            assertTrue("the vector style must still contain $layer", vectorStyle.contains(layer))
            assertTrue("the hybrid must reuse $layer unchanged", hybridStyle.contains(layer))
        }
        assertTrue("the vector map keeps its own background", vectorStyle.contains("#f3efdf"))
        assertTrue("the vector map keeps its forest fill", vectorStyle.contains("\"id\": \"forest\""))
        assertTrue("the vector map keeps its water fill", vectorStyle.contains("\"id\": \"water\", \"type\": \"fill\""))
    }

    /**
     * The temporary DEV profiles are not Map Packages: they must never reuse the production
     * package identity that D065/MapPackageContract validation depends on.
     */
    @Test
    fun devProfilesAreNotPartOfTheProductionPackageContract() {
        val sentinel = beeSearchDevSentinelMapProfile(sentinelArchive)
        val hybrid = beeSearchDevHybridMapProfile(sentinelArchive, vectorArchive)

        for (profile in listOf(sentinel, hybrid)) {
            assertNotEquals(BeeSearchMapPackageCompatibility.PROFILE_ID, profile.profileId)
            assertNotEquals(BeeSearchMapPackageCompatibility.PROFILE_VERSION, profile.profileVersion)
            assertNotEquals(BeeSearchMapPackageCompatibility.STYLE_VERSION, profile.styleVersion)
        }
    }
}
