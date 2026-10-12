package org.beesearch.app.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.beesearch.app.BeeMapProfile
import org.beesearch.app.beeSearchActivePmtilesMapProfile
import org.beesearch.app.beeSearchDevHybridMapProfile
import org.beesearch.app.beeSearchDevSentinelMapProfile
import org.beesearch.app.beeSearchFieldMapProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.sources.Source

/**
 * Opt-in native verification of the Registry against every production BeeMap style factory.
 * Run with -Pandroid.testInstrumentationRunnerArguments.beeOverlayRegistry=true.
 */
@RunWith(AndroidJUnit4::class)
class MapOverlayProductionProfilesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun requireOptInBeforeCreatingFixtures() {
        assumeTrue(
            "Pass beeOverlayRegistry=true to run native production profile tests",
            InstrumentationRegistry.getArguments().getString("beeOverlayRegistry") == "true",
        )
    }

    @Test
    fun fieldProfileRestoresRegistryOverlayAndPreservesStyleResources() {
        verifyProfile("field", beeSearchFieldMapProfile())
    }

    @Test
    fun activePmtilesProfileRestoresRegistryOverlayAndPreservesStyleResources() {
        withArchives { vector ->
            verifyProfile("active-vector", beeSearchActivePmtilesMapProfile(activePackage(vector)))
        }
    }

    @Test
    fun devSentinelProfileRestoresRegistryOverlayAndPreservesStyleResources() {
        val raster = createPmtiles("sentinel", minZoom = 10, maxZoom = 13, tileType = 2)
        try { verifyProfile("sentinel", beeSearchDevSentinelMapProfile(raster)) } finally { raster.delete() }
    }

    @Test
    fun devHybridProfileRestoresRegistryOverlayAndPreservesStyleResources() {
        val vector = createPmtiles("vector", minZoom = 8, maxZoom = 15, tileType = 1)
        val sentinel = createPmtiles("sentinel", minZoom = 10, maxZoom = 13, tileType = 2)
        try { verifyProfile("hybrid", beeSearchDevHybridMapProfile(sentinel, vector)) }
        finally { sentinel.delete(); vector.delete() }
    }

    private fun verifyProfile(label: String, profile: BeeMapProfile) {
        val session = createSession()
        val overlay = overlayRegistryFixture(visible = false, key = "production-$label")
        val firstRequest = onMainValue {
            session.sync("production-profile", listOf(overlay))
            session.requestStyle(profile)
            OverlayStyleRequest(session.currentGeneration, profile)
        }
        awaitStyle(session)
        val firstStyle = onMainValue { requireNotNull(session.currentStyle) }
        assertLoadedProfile(profile, firstStyle, session.currentGeneration)
        assertOverlay(session, overlay)
        val firstSource = onMainValue {
            requireNotNull(firstStyle.getSource(MapOverlayRegistry.sourceId(overlay.key, "points")))
        }
        val firstLayer = onMainValue {
            requireNotNull(firstStyle.getLayer(MapOverlayRegistry.layerId(overlay.key, "circles")))
        }

        onMain { session.requestStyle(profile) }
        awaitStyle(session, firstStyle)
        val secondStyle = onMainValue { requireNotNull(session.currentStyle) }
        assertLoadedProfile(profile, secondStyle, session.currentGeneration)
        assertOverlay(session, overlay)
        val secondSource = onMainValue {
            requireNotNull(secondStyle.getSource(MapOverlayRegistry.sourceId(overlay.key, "points")))
        }
        val secondLayer = onMainValue {
            requireNotNull(secondStyle.getLayer(MapOverlayRegistry.layerId(overlay.key, "circles")))
        }
        assertNotSame("Style reload must create a fresh Source", firstSource, secondSource)
        assertNotSame("Style reload must create a fresh Layer", firstLayer, secondLayer)
        onMain {
            val before = secondSource
            assertTrue(session.restoreIfCurrent(firstRequest, firstStyle) in setOf(OverlayResult.STALE, OverlayResult.DEFERRED))
            assertTrue(session.currentStyle === secondStyle)
            assertTrue(secondStyle.getSource(MapOverlayRegistry.sourceId(overlay.key, "points")) === before)
        }
        onMain { session.release() }
    }

    private fun assertLoadedProfile(profile: BeeMapProfile, style: Style, generation: Long) {
        val overlaySourceId = MapOverlayRegistry.sourceId("production-${profile.profileId.let { profileKey(it) }}", "points")
        val overlayLayerId = MapOverlayRegistry.layerId("production-${profile.profileId.let { profileKey(it) }}", "circles")
        onMain {
            assertTrue("$profile did not fully load", style.isFullyLoaded)
            val actualJson = JSONObject(style.json)
            val marker = actualJson.optJSONObject("metadata")?.optString(MapLibreOverlaySession.REQUEST_METADATA)
            assertTrue("request marker missing from native Style JSON", marker?.endsWith(":$generation") == true)
            val expectedJson = JSONObject(profile.styleJson)
            val expectedSourceIds = expectedJson.getJSONObject("sources").keys().asSequence().toSet() + overlaySourceId
            val expectedLayerIds = (0 until expectedJson.getJSONArray("layers").length())
                .map { expectedJson.getJSONArray("layers").getJSONObject(it).getString("id") }.toSet() + overlayLayerId
            MapOverlayNativeResourceParity.assertSources(expectedSourceIds, style.sources.map { it.id })
            MapOverlayNativeResourceParity.assertLayers(expectedLayerIds, style.layers.map { it.id })
            assertEquals(canonical(expectedJson.getJSONObject("sources")), canonical(actualJson.getJSONObject("sources")))
            assertEquals(canonical(expectedJson.getJSONArray("layers")), canonical(actualJson.getJSONArray("layers")))
        }
    }

    private fun profileKey(profileId: String): String = when (profileId) {
        "osm-standard-evaluation" -> "field"
        "bee-search-field" -> "active-vector"
        "sentinel-area-raster-dev" -> "sentinel"
        else -> "hybrid"
    }

    private fun assertOverlay(session: MapLibreOverlaySession, overlay: MapOverlay<Source, Layer>) {
        assertEquals(1, onMainValue { session.currentStyle!!.sources.count { it.id == MapOverlayRegistry.sourceId(overlay.key, "points") } })
        assertEquals(1, onMainValue { session.currentStyle!!.layers.count { it.id == MapOverlayRegistry.layerId(overlay.key, "circles") } })
        assertEquals("none", onMainValue { session.currentStyle!!.getLayer(MapOverlayRegistry.layerId(overlay.key, "circles"))!!.visibility.value })
    }

    private fun createSession(): MapLibreOverlaySession {
        val latch = CountDownLatch(1)
        val session = arrayOfNulls<MapLibreOverlaySession>(1)
        composeRule.setContent {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapLibre.getInstance(context)
                    MapView(context).also { view ->
                        view.onCreate(null)
                        view.onStart()
                        view.onResume()
                        view.getMapAsync { map ->
                            session[0] = MapLibreOverlaySession(view, map)
                            latch.countDown()
                        }
                    }
                },
                onRelease = { view ->
                    session[0]?.release()
                    view.onPause()
                    view.onStop()
                    view.onDestroy()
                },
            )
        }
        check(latch.await(15, TimeUnit.SECONDS)) { "MapLibre map did not become ready" }
        return requireNotNull(session[0])
    }

    private fun awaitStyle(session: MapLibreOverlaySession, previous: Style? = null) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            val ready = onMainValue {
                val style = session.currentStyle
                style?.isFullyLoaded == true && (previous == null || style !== previous)
            }
            if (ready) return
            Thread.sleep(50)
        }
        error("MapLibre production profile style did not become fully loaded")
    }

    private fun withArchives(block: (File) -> Unit) {
        val vector = createPmtiles("vector", minZoom = 8, maxZoom = 15, tileType = 1)
        try {
            block(vector)
        } finally {
            vector.delete()
        }
    }

    private fun activePackage(vector: File): ActiveMapPackage {
        val manifest = MapPackageManifest(
            schemaVersion = 1,
            packageId = "stage0-profile-test",
            datasetVersion = "stage0-profile-test",
            profileId = BeeSearchMapPackageCompatibility.PROFILE_ID,
            profileVersion = BeeSearchMapPackageCompatibility.PROFILE_VERSION,
            styleVersion = BeeSearchMapPackageCompatibility.STYLE_VERSION,
            coverageFragments = listOf(MapCoverageFragment(MapGeoBounds(north = 1.0, east = 1.0, south = -1.0, west = -1.0))),
            minZoom = 8,
            maxZoom = 15,
            pmtilesFile = vector.name,
            pmtilesByteLength = vector.length(),
            pmtilesSha256 = MapPackageValidator.sha256(vector),
        )
        MapPackageValidator.validate(manifest, vector, manifest.coverageFragments)
        return ActiveMapPackage(manifest, vector)
    }

    private fun createPmtiles(prefix: String, minZoom: Int, maxZoom: Int, tileType: Int): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "${prefix}-${System.nanoTime()}.pmtiles")
        val metadata = "{\"vector_layers\":[]}".encodeToByteArray()
        val bytes = ByteArray(128 + metadata.size)
        "PMTiles".encodeToByteArray().copyInto(bytes, 0)
        bytes[7] = 3
        putLong(bytes, 8, 127)
        putLong(bytes, 16, 1)
        putLong(bytes, 24, 128)
        putLong(bytes, 32, metadata.size.toLong())
        putLong(bytes, 40, 128 + metadata.size.toLong())
        putLong(bytes, 56, 128 + metadata.size.toLong())
        bytes[96] = 1
        bytes[97] = 1
        bytes[98] = 1
        bytes[99] = tileType.toByte()
        bytes[100] = minZoom.toByte()
        bytes[101] = maxZoom.toByte()
        putInt(bytes, 102, -10_000_000)
        putInt(bytes, 106, -10_000_000)
        putInt(bytes, 110, 10_000_000)
        putInt(bytes, 114, 10_000_000)
        metadata.copyInto(bytes, 128)
        file.writeBytes(bytes)
        file.deleteOnExit()
        return file
    }

    private fun putLong(bytes: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) bytes[offset + i] = (value ushr (8 * i)).toByte()
    }

    private fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) bytes[offset + i] = (value ushr (8 * i)).toByte()
    }

    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { key ->
            "\"$key\":" + canonical(value.get(key))
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonical(value.get(it)) }
        is Number -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        JSONObject.NULL -> "null"
        else -> JSONObject.quote(value.toString())
    }

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun <T> onMainValue(block: () -> T): T {
        var value: T? = null
        onMain { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}
