package org.beesearch.app.ui.map

import android.net.Uri
import android.util.Log
import org.json.JSONObject
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.beesearch.app.BeeMapProfile
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.beesearch.app.domain.location.LocationUiState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.sources.Source

/**
 * Native MapLibre lifecycle proof for D106 Stage 0.
 *
 * These tests are opt-in because the native renderer is environment-sensitive. Run with
 * `-Pandroid.testInstrumentationRunnerArguments.beeOverlayRegistry=true` on an emulator.
 */
@RunWith(AndroidJUnit4::class)
class MapOverlayRegistryLifecycleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun optInBeforeNativeMapCreation() {
        assumeTrue(
            "Pass beeOverlayRegistry=true to run native MapLibre lifecycle tests",
            InstrumentationRegistry.getArguments().getString("beeOverlayRegistry") == "true",
        )
    }

    @Test
    fun gpxLinesSingletonsHideAndStyleReloadUseTheSameSession() {
        val session = createSession()
        val id = java.util.UUID.randomUUID()
        val prepared = GpxPreparedOverlay(id, 1, """{"type":"FeatureCollection","features":[
          {"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[[39,57],[39.01,57.01]]}},
          {"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[39.02,57.02]}}
        ]}""")
        val overlay = prepared.declaration()
        val research = overlayRegistryFixture()
        val a = java.util.UUID.randomUUID()
        val b = java.util.UUID.randomUUID()
        val statuses = mutableListOf<Map<String, OverlayPresentation>>()
        onMain {
            session.onPresentationChanged = { _, status -> statuses.add(status) }
            session.sync(a, listOf(research, overlay))
            session.requestStyle(testProfile(OVERLAY_REGISTRY_FIXTURE_STYLE_A))
        }
        awaitStyle(session)
        onMain {
            assertEquals(OverlayResult.APPLIED,session.registry.presentation()[overlay.key]?.result)
            assertEquals(1,countLayers(session,research))
            assertEquals(true,session.currentStyle!!.getLayer(MapOverlayRegistry.layerId(overlay.key,"lines")) != null)
            assertEquals(true,session.currentStyle!!.getLayer(MapOverlayRegistry.layerId(overlay.key,"singletons")) != null)
            session.sync(a,listOf(research))
            assertEquals(null,session.currentStyle!!.getSource(MapOverlayRegistry.sourceId(overlay.key,"geometry")))
            assertEquals(1,countLayers(session,research))
            session.sync(a,listOf(research,prepared.copy(revision=2).declaration()))
        }
        val old = onMainValue { session.currentStyle }
        onMain { session.requestStyle(testProfile(OVERLAY_REGISTRY_FIXTURE_STYLE_B)) }
        awaitStyle(session, old)
        onMain {
            assertEquals(OverlayResult.APPLIED,session.registry.presentation()[overlay.key]?.result)
            assertEquals(2L,session.registry.presentation()[overlay.key]?.revision)
            session.sync(b,listOf(research))
            assertEquals(null,session.currentStyle!!.getSource(MapOverlayRegistry.sourceId(overlay.key,"geometry")))
            session.sync(a,listOf(research,prepared.copy(revision=3).declaration()))
            assertEquals(OverlayResult.APPLIED,session.registry.presentation()[overlay.key]?.result)
            assertEquals(true,statuses.any { it[overlay.key]?.result == OverlayResult.DEFERRED })
            val good=prepared.copy(revision=4).declaration()
            val broken=good.copy(layers=good.layers.mapIndexed { index, factory ->
                if(index==1) factory.copy(create={_,_->error("test-owned factory failure")}) else factory
            })
            session.sync(a,listOf(research,broken))
            assertEquals(OverlayResult.FACTORY_FAILED,session.registry.presentation()[overlay.key]?.result)
            assertEquals(null,session.currentStyle!!.getSource(MapOverlayRegistry.sourceId(overlay.key,"geometry")))
            assertEquals(null,session.currentStyle!!.getLayer(MapOverlayRegistry.layerId(overlay.key,"lines")))
            assertEquals(1,countLayers(session,research))
            session.sync(a,listOf(research,prepared.copy(revision=5).declaration()))
            assertEquals(OverlayResult.APPLIED,session.registry.presentation()[overlay.key]?.result)
            session.release()
        }
    }

    @Test
    fun sdkSessionRestoresFixtureAcrossReloadAndScopeChange() {
        val session = createSession()
        val overlay = overlayRegistryFixture(visible = false)

        onMain {
            session.sync("scope-a", listOf(overlay))
            session.requestStyle(testProfile(OVERLAY_REGISTRY_FIXTURE_STYLE_A))
        }
        awaitStyle(session)
        onMain {
            assertEquals(1, countSources(session, overlay))
            assertEquals(1, countLayers(session, overlay))
            assertEquals("none", layerVisibility(session, overlay))
        }
        val firstSource = onMainValue {
            session.currentStyle!!.getSource(MapOverlayRegistry.sourceId(overlay.key, "points"))
        }
        assertEquals(true, onMainValue { session.currentStyle!!.json.contains("Overlay Registry Fixture A") })

        val firstStyle = onMainValue { session.currentStyle }
        onMain { session.requestStyle(testProfile(OVERLAY_REGISTRY_FIXTURE_STYLE_B)) }
        awaitStyle(session, firstStyle)
        onMain {
            assertEquals(1, countSources(session, overlay))
            assertEquals(1, countLayers(session, overlay))
            session.sync("scope-b", listOf(overlay.copy(visible = true)))
            assertEquals(1, countSources(session, overlay))
            assertEquals(1, countLayers(session, overlay))
            assertEquals("visible", layerVisibility(session, overlay))
        }
        val secondSource = onMainValue {
            session.currentStyle!!.getSource(MapOverlayRegistry.sourceId(overlay.key, "points"))
        }
        assertEquals(true, onMainValue { session.currentStyle!!.json.contains("Overlay Registry Fixture B") })
        check(firstSource !== secondSource) { "Style reload must create fresh Source objects" }

        val secondStyle = onMainValue { session.currentStyle }
        onMain { session.requestStyle(testProfile(OVERLAY_REGISTRY_FIXTURE_STYLE_A)) }
        awaitStyle(session, secondStyle)
        onMain {
            assertEquals(1, countSources(session, overlay))
            assertEquals(1, countLayers(session, overlay))
            assertEquals(true, session.currentStyle!!.json.contains("Overlay Registry Fixture A"))
        }
        val thirdSource = onMainValue {
            session.currentStyle!!.getSource(MapOverlayRegistry.sourceId(overlay.key, "points"))
        }
        check(secondSource !== thirdSource) { "Every style attachment must create fresh Source objects" }
        onMain {
            session.release()
            assertEquals(true, session.isReleased)
        }
    }

    @Test
    fun foreignStyleIdsAreNotAdoptedOrRemoved() {
        val session = createSession()
        val foreignSource = MapOverlayRegistry.sourceId(OVERLAY_REGISTRY_FIXTURE_KEY, "points")
        val foreignLayer = MapOverlayRegistry.layerId(OVERLAY_REGISTRY_FIXTURE_KEY, "circles")
        onMain {
            session.requestStyle(testProfile("""
                {"version":8,"name":"foreign","sources":{"$foreignSource":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}},"layers":[{"id":"$foreignLayer","type":"circle","source":"$foreignSource"}]}
            """))
        }
        awaitStyle(session)
        val heldStyle = onMainValue { session.currentStyle!! }
        onMain {
            session.sync("scope-a", listOf(overlayRegistryFixture()))
            assertEquals(OverlayResult.ID_COLLISION, session.registry.failures[OVERLAY_REGISTRY_FIXTURE_KEY])
            assertEquals(1, countSources(session, overlayRegistryFixture()))
            assertEquals(1, countLayers(session, overlayRegistryFixture()))
        }
        onMain { session.release() }
        assertEquals(true, onMainValue { heldStyle.getSource(foreignSource) != null })
        assertEquals(true, onMainValue { heldStyle.getLayer(foreignLayer) != null })
    }

    @Test
    fun actualBeeMapHostProvidesAndReleasesOverlaySession() {
        val captured = AtomicReference<MapLibreOverlaySession?>()
        val startupSignals = mutableListOf<String>()
        val listeners = mutableListOf<Pair<MapView, MapView.OnDidFinishLoadingStyleListener>>()
        val visible = mutableStateOf(true)
        val round = mutableIntStateOf(0)
        val mode = mutableStateOf(BeeMapMode.FIELD)
        composeRule.setContent {
            Bee_searchTheme {
                if (visible.value) {
                    key(round.intValue) {
                        BeeMap(
                            territoryId = null,
                            territoryName = null,
                            areaStore = NoOpAreaStore,
                            packageStore = NoOpPackageStore,
                            locationState = LocationUiState.PermissionRequired,
                            locationPermissionGranted = false,
                            onRequestLocationPermission = {},
                            onRequestCreateRecord = { _, _ -> },
                            runtimeOverlays = listOf(overlayRegistryFixture()),
                            overlayScope = "fixture-scope",
                            mode = mode.value,
                            onOverlaySessionReady = { readySession ->
                                captured.set(readySession)
                                // Test-side observation before BeeMap starts its initial request.
                                val field = MapLibreOverlaySession::class.java.getDeclaredField("hostView")
                                    .apply { isAccessible = true }
                                val view = field.get(readySession) as MapView
                                val listener = MapView.OnDidFinishLoadingStyleListener {
                                    val style = readySession.currentStyle
                                    val marker = style?.let { JSONObject(it.json)
                                        .optJSONObject("metadata")?.optString(MapLibreOverlaySession.REQUEST_METADATA) }
                                    startupSignals += "generation=${readySession.currentGeneration},marker=$marker,ready=${style?.isFullyLoaded}"
                                }
                                view.addOnDidFinishLoadingStyleListener(listener)
                                listeners += view to listener
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(15_000) { onMainValue { captured.get()?.currentStyle?.isFullyLoaded == true } }
        val session = requireNotNull(captured.get())
        composeRule.waitUntil(15_000) { onMainValue { session.currentStyle?.getLayer(MapOverlayRegistry.layerId(OVERLAY_REGISTRY_FIXTURE_KEY, "circles")) != null } }
        assertEquals(false, onMainValue { session.isReleased })
        composeRule.waitForIdle()
        onMain {
            val style = requireNotNull(session.currentStyle)
            val marker = JSONObject(style.json).getJSONObject("metadata")
                .getString(MapLibreOverlaySession.REQUEST_METADATA)
            check(marker.endsWith(":${session.currentGeneration}"))
            check(session.currentGeneration > 0)
            assertEquals(1, countSources(session, overlayRegistryFixture()))
            assertEquals(1, countLayers(session, overlayRegistryFixture()))
            assertEquals(emptyMap<String, OverlayResult>(), session.registry.failures)
            Log.i("OverlayRegistryStartup", "initial/effect requests=${session.currentGeneration}; signals=$startupSignals; finalMarker=$marker")
            listeners.forEach { (view, listener) -> view.removeOnDidFinishLoadingStyleListener(listener) }
            listeners.clear()
        }
        val initialStyle = onMainValue { session.currentStyle }
        onMain { session.requestStyle(testProfile(OVERLAY_REGISTRY_FIXTURE_STYLE_A)) }
        awaitStyle(session, initialStyle)
        onMain {
            session.sync("fixture-scope-b", listOf(overlayRegistryFixture(revision = 2L, visible = false)))
            assertEquals(1, countSources(session, overlayRegistryFixture()))
            assertEquals(1, countLayers(session, overlayRegistryFixture()))
            assertEquals("none", layerVisibility(session, overlayRegistryFixture()))
            session.restoreIfCurrent()
        }
        val first = session
        composeRule.runOnIdle { visible.value = false }
        composeRule.waitForIdle()
        assertEquals(true, onMainValue { session.isReleased })
        composeRule.runOnIdle { visible.value = true; mode.value = BeeMapMode.AREA_VIEW; round.intValue++ }
        composeRule.waitUntil(15_000) { captured.get() != null && captured.get() !== first }
        val recreated = requireNotNull(captured.get())
        composeRule.waitUntil(15_000) { onMainValue { recreated.currentStyle?.getLayer(MapOverlayRegistry.layerId(OVERLAY_REGISTRY_FIXTURE_KEY, "circles")) != null } }
        onMain {
            val marker = JSONObject(requireNotNull(recreated.currentStyle).json)
                .getJSONObject("metadata").getString(MapLibreOverlaySession.REQUEST_METADATA)
            check(marker.endsWith(":${recreated.currentGeneration}"))
            assertEquals(1, countSources(recreated, overlayRegistryFixture()))
            assertEquals(1, countLayers(recreated, overlayRegistryFixture()))
            Log.i("OverlayRegistryStartup", "recreated AREA_VIEW requests=${recreated.currentGeneration}; finalMarker=$marker")
            listeners.forEach { (view, listener) -> view.removeOnDidFinishLoadingStyleListener(listener) }
            listeners.clear()
            recreated.release()
        }
    }

    private fun createSession(): MapLibreOverlaySession {
        val latch = CountDownLatch(1)
        val session = AtomicReference<MapLibreOverlaySession?>()
        composeRule.setContent {
            AndroidView(
                    modifier = Modifier.fillMaxSize(),
                factory = { view ->
                    MapLibre.getInstance(view)
                    MapView(view).also { mapView ->
                        mapView.onCreate(null)
                        mapView.onStart()
                        mapView.onResume()
                        mapView.getMapAsync { map ->
                            session.set(MapLibreOverlaySession(mapView, map))
                            latch.countDown()
                        }
                    }
                },
                onRelease = { view ->
                    session.get()?.release()
                    view.onPause()
                    view.onStop()
                    view.onDestroy()
                },
            )
        }
        check(latch.await(15, TimeUnit.SECONDS)) { "MapLibre map did not become ready" }
        return requireNotNull(session.get())
    }

    private fun awaitStyle(session: MapLibreOverlaySession, previous: Style? = null) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync {
                val style = session.currentStyle
                ready = style?.isFullyLoaded == true && (previous == null || style !== previous)
            }
            if (ready) return
            Thread.sleep(50)
        }
        error("MapLibre style did not become fully loaded")
    }

    private fun countSources(session: MapLibreOverlaySession, overlay: MapOverlay<Source, Layer>): Int =
        session.currentStyle?.sources?.count { it.id == MapOverlayRegistry.sourceId(overlay.key, "points") } ?: 0

    private fun countLayers(session: MapLibreOverlaySession, overlay: MapOverlay<Source, Layer>): Int =
        session.currentStyle?.layers?.count { it.id == MapOverlayRegistry.layerId(overlay.key, "circles") } ?: 0

    private fun layerVisibility(session: MapLibreOverlaySession, overlay: MapOverlay<Source, Layer>): String? =
        session.currentStyle?.getLayer(MapOverlayRegistry.layerId(overlay.key, "circles"))?.visibility?.value

    private fun testProfile(json: String) = BeeMapProfile(
        profileId = "overlay-registry-fixture",
        profileVersion = "test",
        datasetVersion = "test",
        styleVersion = "test",
        styleJson = json.trimIndent(),
        sourceMaxZoom = 20.0,
        uiMaxZoom = 20.0,
    )

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun <T> onMainValue(block: () -> T): T {
        var value: T? = null
        onMain { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private object NoOpAreaStore : MapAreaStore {
        override suspend fun load(territoryId: java.util.UUID, territoryName: String?) = MapAreaReadResult.Absent
        override suspend fun create(territoryId: java.util.UUID, name: String, bounds: List<MapGeoBounds>) = MapAreaChangeResult.Refused("fixture")
        override suspend fun updateBounds(territoryId: java.util.UUID, bounds: List<MapGeoBounds>) = MapAreaChangeResult.Refused("fixture")
        override suspend fun rename(territoryId: java.util.UUID, name: String) = MapAreaChangeResult.Refused("fixture")
        override suspend fun delete(territoryId: java.util.UUID) = MapAreaChangeResult.Refused("fixture")
        override suspend fun clear(territoryId: java.util.UUID) = Unit
        override suspend fun snapshot(territoryId: java.util.UUID): String? = null
        override suspend fun restore(territoryId: java.util.UUID, value: String?) = Unit
    }

    private object NoOpPackageStore : MapPackageStore {
        override suspend fun loadActive(territoryId: java.util.UUID, desiredCoverage: List<MapCoverageFragment>) = MapPackageAvailability.Missing
        override suspend fun import(territoryId: java.util.UUID, desiredCoverage: List<MapCoverageFragment>, manifestUri: Uri, pmtilesUri: Uri): MapPackageImportResult = MapPackageImportResult.Rejected("fixture")
        override suspend fun clear(territoryId: java.util.UUID) = Unit
    }
}
