package org.beesearch.app.ui.userpoints

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.beesearch.app.BeeMapProfile
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.ui.map.MapOverlay
import org.beesearch.app.ui.map.MapOverlayRegistry
import org.beesearch.app.ui.map.MapLibreOverlaySession
import org.beesearch.app.ui.map.OverlayResult
import org.beesearch.app.ui.map.OVERLAY_REGISTRY_FIXTURE_STYLE_A
import org.beesearch.app.ui.map.OVERLAY_REGISTRY_FIXTURE_STYLE_B
import org.beesearch.app.ui.map.overlayRegistryFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.Source

@RunWith(AndroidJUnit4::class)
class UserPointMapOverlayNativeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Volatile private var renderedStyle: Style? = null

    private val territoryA = UUID.randomUUID()
    private val territoryB = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val foreign = UUID.randomUUID()

    @Test
    fun visibleCurrentTerritoryPointsAndSelectedHaloReachNativeSources() {
        val session = createSession()
        val state = state(territoryA, 1, point(first, territoryA, true), point(second, territoryA, true), point(foreign, territoryB, true), selected = first)
        val overlay = requireNotNull(userPointOverlay(state))
        onMain {
            session.sync(territoryA, listOf(overlayRegistryFixture(), overlay))
            session.requestStyle(profile(OVERLAY_REGISTRY_FIXTURE_STYLE_A))
        }
        awaitStyle(session)
        val features = onMainValue { source(session, overlay).querySourceFeatures(Expression.all()) }
        assertEquals(setOf(first.toString(), second.toString()), features.map { it.getStringProperty("id") }.toSet())
        assertEquals(setOf(true, false), features.map { it.getBooleanProperty("selected") }.toSet())
        onMain {
            assertTrue(session.currentStyle!!.getLayer(MapOverlayRegistry.layerId(overlay.key, "selection")) != null)
            assertTrue(session.currentStyle!!.getLayer(MapOverlayRegistry.layerId(overlay.key, "markers")) != null)
            session.release()
        }
    }

    @Test
    fun offRemovesOverlayAndOnRestoresItWithNewSnapshot() {
        val session = createSession()
        val on = requireNotNull(userPointOverlay(state(territoryA, 1, point(first, territoryA, true))))
        val offState = state(territoryA, 2, point(first, territoryA, false))
        val onAgain = requireNotNull(userPointOverlay(state(territoryA, 3, point(first, territoryA, true))))
        onMain {
            session.sync(territoryA, listOf(on))
            session.requestStyle(profile(OVERLAY_REGISTRY_FIXTURE_STYLE_A))
        }
        awaitStyle(session)
        onMain {
            assertTrue(session.currentStyle!!.getSource(MapOverlayRegistry.sourceId(on.key, "points")) != null)
            session.sync(territoryA, listOfNotNull(userPointOverlay(offState)))
            assertNull(session.currentStyle!!.getSource(MapOverlayRegistry.sourceId(on.key, "points")))
            assertNull(session.currentStyle!!.getLayer(MapOverlayRegistry.layerId(on.key, "markers")))
            session.sync(territoryA, listOf(onAgain))
            assertTrue(session.currentStyle!!.getSource(MapOverlayRegistry.sourceId(on.key, "points")) != null)
            assertEquals(OverlayResult.APPLIED, session.registry.presentation()[on.key]?.result)
            session.release()
        }
    }

    @Test
    fun styleReloadRestoresSourcesLayersAndSelectedFeature() {
        val session = createSession()
        val overlay = requireNotNull(userPointOverlay(state(territoryA, 1, point(first, territoryA, true), selected = first)))
        onMain {
            session.sync(territoryA, listOf(overlay))
            session.requestStyle(profile(OVERLAY_REGISTRY_FIXTURE_STYLE_A))
        }
        awaitStyle(session)
        val oldSource = onMainValue { source(session, overlay) }
        val oldStyle = onMainValue { session.currentStyle }
        onMain { session.requestStyle(profile(OVERLAY_REGISTRY_FIXTURE_STYLE_B)) }
        awaitStyle(session, oldStyle)
        val newSource = onMainValue { source(session, overlay) }
        assertNotSame(oldSource, newSource)
        assertEquals(first.toString(), onMainValue { source(session, overlay).querySourceFeatures(Expression.all()).single().getStringProperty("id") })
        onMain {
            assertTrue(session.currentStyle!!.getLayer(MapOverlayRegistry.layerId(overlay.key, "selection")) != null)
            assertTrue(session.currentStyle!!.getLayer(MapOverlayRegistry.layerId(overlay.key, "markers")) != null)
            session.release()
        }
    }

    @Test
    fun scopeAtoBtoARemovesForeignPointFromNativeSource() {
        val session = createSession()
        val a = requireNotNull(userPointOverlay(state(territoryA, 1, point(first, territoryA, true))))
        val b = requireNotNull(userPointOverlay(state(territoryB, 2, point(foreign, territoryB, true))))
        onMain {
            session.sync(territoryA, listOf(a))
            session.requestStyle(profile(OVERLAY_REGISTRY_FIXTURE_STYLE_A))
        }
        awaitStyle(session)
        onMain { session.sync(territoryB, listOf(b)) }
        awaitPointIds(session, b, listOf(foreign.toString()))
        onMain { session.sync(territoryA, listOf(a)) }
        awaitPointIds(session, a, listOf(first.toString()))
        onMain { session.release() }
    }

    // Source updates are parsed asynchronously by the native renderer after sync returns.
    private fun awaitPointIds(session: MapLibreOverlaySession, overlay: MapOverlay<Source, Layer>, expected: List<String>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var actual = emptyList<String>()
        while (System.nanoTime() < deadline) {
            actual = onMainValue { source(session, overlay).querySourceFeatures(Expression.all()).map { it.getStringProperty("id") } }
            if (actual == expected) return
            Thread.sleep(50)
        }
        assertEquals(expected, actual)
    }

    private fun state(territory: UUID, revision: Long, vararg points: UserPoint, selected: UUID? = null) =
        UserPointDisplayState(territoryId = territory, loading = false, points = points.toList(), selectedId = selected, revision = revision)

    private fun point(id: UUID, territory: UUID, visible: Boolean) = UserPoint(
        id, territory, id.toString(), null, 56.8, 60.6, visible, Instant.EPOCH, Instant.EPOCH,
    )

    private fun source(session: MapLibreOverlaySession, overlay: MapOverlay<Source, Layer>) =
        session.currentStyle!!.getSource(MapOverlayRegistry.sourceId(overlay.key, "points")) as GeoJsonSource

    private fun createSession(): MapLibreOverlaySession {
        val ready = CountDownLatch(1)
        var session: MapLibreOverlaySession? = null
        composeRule.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                MapLibre.getInstance(context)
                MapView(context).also { view ->
                    view.addOnDidFinishRenderingFrameListener { fully, _, _ ->
                        if (fully) renderedStyle = session?.currentStyle
                    }
                    view.onCreate(null); view.onStart(); view.onResume()
                    view.getMapAsync { map -> session = MapLibreOverlaySession(view, map); ready.countDown() }
                }
            }, onRelease = { view ->
                session?.release(); view.onPause(); view.onStop(); view.onDestroy()
            })
        }
        check(ready.await(15, TimeUnit.SECONDS)) { "MapLibre map did not become ready" }
        return requireNotNull(session)
    }

    private fun awaitStyle(session: MapLibreOverlaySession, previous: Style? = null) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            if (onMainValue { session.currentStyle?.let { it.isFullyLoaded && it === renderedStyle && (previous == null || it !== previous) } == true }) return
            Thread.sleep(50)
        }
        error("MapLibre style did not become fully loaded")
    }

    private fun profile(json: String) = BeeMapProfile("user-point-test", "test", "test", "test", json, 20.0, 20.0)

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun <T> onMainValue(block: () -> T): T {
        var result: T? = null
        onMain { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
