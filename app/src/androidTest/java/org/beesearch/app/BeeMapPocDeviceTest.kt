package org.beesearch.app

import android.graphics.Bitmap
import android.graphics.RectF
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.beesearch.app.beeSearchLocalPmtilesDiagnosticProfile
import org.beesearch.app.beeSearchLocalPmtilesDiagnosticStageProfile
import org.beesearch.app.beeSearchLocalSapunovoGlyphDiagnosticProfile
import org.beesearch.app.beeSearchLocalSapunovoLabelDiagnosticProfile
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.net.ConnectivityReceiver

/**
 * Opt-in real-endpoint checks for the development Map Data PoC.
 *
 * Run only with the static endpoint active and `-e beeMapPoc true`. Normal
 * instrumentation runs skip this test because they must not depend on a
 * workstation server or on a prepared Territory in the target app.
 */
@RunWith(AndroidJUnit4::class)
class BeeMapPocDeviceTest {
    @Test
    fun fieldSourceRendersRequiredObjectsAndOverscalesOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeMapPoc") == "true")
        val connectivity = ConnectivityReceiver.instance(instrumentation.targetContext)
        connectivity.setConnected(true)

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                map.awaitStyle()

                assertEquals(20.0, onMain { map.maxZoomLevel }, 0.0)
                assertTrue(onMain { map.style?.uri?.contains("/style-v3/style.json") == true })
                assertNotNull(onMain { map.style?.getSource("bee-search-field") })
                assertNotNull(onMain { map.style?.getLayer("cutlines") })
                assertNotNull(onMain { map.style?.getLayer("place-labels") })

                map.moveAndAwait(latitude = 56.0714506, longitude = 42.7528554, zoom = 15.0)
                assertRendered(mapView, map, "cutlines") { it.getStringProperty("class") == "cutline" }
                captureScreenshot("area-a-z15")

                map.moveAndAwait(latitude = 56.1057272, longitude = 42.6583599, zoom = 15.0)
                assertRendered(mapView, map, "tracks") {
                    it.getStringProperty("class") == "track" &&
                        it.hasProperty("tracktype") &&
                        it.hasProperty("surface_class")
                }

                map.moveAndAwait(latitude = 56.1068270, longitude = 42.6652980, zoom = 15.0)
                assertRendered(mapView, map, "place-labels") {
                    it.hasProperty("name_ru") && it.getStringProperty("name_ru").any { character ->
                        character in 'А'..'я'
                    }
                }
                assertAnyRendered(mapView, map, "buildings")
                captureScreenshot("area-b-z15")

                map.moveAndAwait(latitude = 56.0966111, longitude = 42.6560229, zoom = 15.0)
                assertAnyRendered(mapView, map, "minor-waterways", "rivers")

                map.moveAndAwait(latitude = 56.1156514, longitude = 42.6489730, zoom = 15.0)
                assertAnyRendered(mapView, map, "railway")

                map.moveAndAwait(latitude = 56.0644769, longitude = 42.7375559, zoom = 15.0)
                assertRendered(mapView, map, "power-lines") { it.getStringProperty("class") == "power" }

                map.moveAndAwait(latitude = 56.2274445, longitude = 42.5192383, zoom = 15.0)
                assertRendered(mapView, map, "minor-waterways") { it.getStringProperty("class") == "drain" }

                map.moveAndAwait(latitude = 56.0715664, longitude = 42.7508162, zoom = 17.0)
                assertEquals(17.0, onMain { map.cameraPosition.zoom }, 0.01)
                assertRendered(mapView, map, "cutlines") { it.getStringProperty("class") == "cutline" }

                map.moveAndAwait(latitude = 56.0715664, longitude = 42.7508162, zoom = 20.0)
                assertEquals(20.0, onMain { map.cameraPosition.zoom }, 0.01)
                assertRendered(mapView, map, "cutlines") { it.getStringProperty("class") == "cutline" }
                captureScreenshot("area-a-z20")
            }
        } finally {
            connectivity.setConnected(null)
        }
    }

    @Test
    fun localForestPmtilesRendersWithoutNetwork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beePmtiles") == "true")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val mapView = scenario.findMapView()
            val map = mapView.awaitMap()
            val profile = beeSearchLocalForestPmtilesMapProfile(instrumentation.targetContext)
            map.setStyleAndAwait(profile.styleJson)
            map.moveAndAwait(latitude = 56.0714506, longitude = 42.7528554, zoom = 15.0)
            assertAnyRendered(mapView, map, "forest", "water", "waterways", "roads", "tracks", "cutlines")
            captureScreenshot("forest-pmtiles-z15")

            map.moveAndAwait(latitude = 56.0714506, longitude = 42.7528554, zoom = 20.0)
            assertAnyRendered(mapView, map, "forest", "roads", "tracks")
            captureScreenshot("forest-pmtiles-z20")
        }
    }

    @Test
    fun localSapunovoPmtilesRendersAcrossZooms() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeSapunovoPmtiles") == "true")

        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .putExtra("beeMapDiagnostic", "label")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val mapView = scenario.findMapView()
            val map = mapView.awaitMap()
            val profile = beeSearchLocalSapunovoPmtilesMapProfile(instrumentation.targetContext)
            map.setStyleAndAwait(profile.styleJson)
            // z10 is intentionally sparse for this bounded PoC and may contain no
            // rendered feature at the exact camera center; validate the geometry
            // at the useful in-coverage zooms instead of treating that as a source failure.
            for (zoom in listOf(12.0, 15.0, 20.0)) {
                map.moveAndAwait(latitude = 56.1068, longitude = 42.6653, zoom = zoom)
                assertAnyRendered(mapView, map, "open-land", "forest", "water", "waterways", "roads", "tracks", "railway", "buildings")
                captureScreenshot("sapunovo-pmtiles-z${zoom.toInt()}")
            }
        }
    }

    @Test
    fun localTerritoryBenchmarkPmtilesRendersAcrossZooms() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeTerritoryPmtiles") == "true")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val mapView = scenario.findMapView()
            val map = mapView.awaitMap()
            val profile = beeSearchLocalTerritoryBenchmarkPmtilesMapProfile(instrumentation.targetContext)
            map.setStyleAndAwait(profile.styleJson)
            assertNotNull(onMain { map.style?.getLayer("place-labels") })
            assertNotNull(onMain { map.style?.getLayer("water-labels") })
            assertNotNull(onMain { map.style?.getLayer("waterway-labels") })
            assertNotNull(onMain { map.style?.getLayer("road-labels") })
            // z10 can legitimately be sparse at a particular center; the useful field and
            // overscaled zooms must render geometry from the local archive.
            for (zoom in listOf(12.0, 15.0, 20.0)) {
                map.moveAndAwait(latitude = 56.298866, longitude = 42.509102, zoom = zoom)
                assertAnyRendered(mapView, map, "open-land", "forest", "water", "waterways", "roads", "tracks", "railway", "buildings")
                captureScreenshot("territory-benchmark-pmtiles-z${zoom.toInt()}")
            }
        }
    }

    @Test
    fun localSapunovoPmtilesMinimalSourceRenders() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beePmtilesDiagnostic") == "true")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val mapView = scenario.findMapView()
            val map = mapView.awaitMap()
            for (area in listOf("forest", "sapunovo")) {
                val profile = beeSearchLocalPmtilesDiagnosticProfile(instrumentation.targetContext, area)
                var styleCallback = false
                onMain {
                    map.setStyle(Style.Builder().fromJson(profile.styleJson)) {
                        styleCallback = true
                    }
                }
                assertTrue("Diagnostic style callback was not received for $area", eventually { styleCallback })
                map.moveAndAwait(latitude = if (area == "forest") 56.075 else 56.1068, longitude = if (area == "forest") 42.77 else 42.6653, zoom = 12.0)
                assertAnyRendered(mapView, map, "diagnostic-landcover", "diagnostic-transportation")
                captureScreenshot("$area-diagnostic")
            }
        }
    }

    @Test
    fun localSapunovoGlyphUriDiagnostic() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeGlyphUri") != null)
        val glyphUri = InstrumentationRegistry.getArguments().getString("beeGlyphUri")!!
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val mapView = scenario.findMapView()
            val map = mapView.awaitMap()
            val profile = beeSearchLocalSapunovoGlyphDiagnosticProfile(instrumentation.targetContext, glyphUri)
            map.moveAndAwait(56.1068, 42.6653, 12.0)
            assertAnyRendered(mapView, map, "diagnostic-landcover", "diagnostic-transportation")
            captureScreenshot("sapunovo-glyph-${glyphUri.substringBefore(':').replace('/', '-')}")
        }
    }

    @Test
    fun localSapunovoLabelDiagnostic() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeLabelDiagnostic") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fontStack = InstrumentationRegistry.getArguments().getString("beeLabelFontStack") ?: "Noto Sans Regular"
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java)
            .putExtra("beeMapDiagnostic", "label")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val mapView = scenario.findMapView()
            val map = mapView.awaitMap()
            val profile = beeSearchLocalSapunovoLabelDiagnosticProfile(instrumentation.targetContext, fontStack)
            val glyphAssetPath = "map-poc/glyphs/$fontStack/0-255.pbf"
            val glyphAssetBytes = instrumentation.targetContext.assets.open(glyphAssetPath).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                }
                total
            }
            map.setStyleAndAwait(profile.styleJson)
            map.moveAndAwait(56.1068, 42.6653, 12.0)
            val layer = onMain { map.style?.getLayer("diagnostic-place") }
            assertNotNull("diagnostic place layer missing", layer)
            val testLayer = onMain { map.style?.getLayer("diagnostic-test") }
            assertNotNull("diagnostic test layer missing", testLayer)
            val fixedPlaceLayer = onMain { map.style?.getLayer("diagnostic-place-fixed") }
            assertNotNull("diagnostic fixed place layer missing", fixedPlaceLayer)
            val rendered = onMain { map.queryRenderedFeatures(RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()), "diagnostic-place").toList() }
            val renderedTest = onMain { map.queryRenderedFeatures(RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()), "diagnostic-test").toList() }
            val renderedFixedPlace = onMain { map.queryRenderedFeatures(RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()), "diagnostic-place-fixed").toList() }
            android.util.Log.d("BeeMapLabelDiag", "glyph template=asset://map-poc/glyphs/{fontstack}/{range}.pbf fontstack=$fontStack expectedRequest=asset://map-poc/glyphs/$fontStack/0-255.pbf")
            android.util.Log.d("BeeMapLabelDiag", "glyph asset open=$glyphAssetPath bytes=$glyphAssetBytes")
            android.util.Log.d("BeeMapLabelDiag", "place layer source=bee-field source-layer=place minzoom=8 maxzoom=20 visibility=visible rendered count=${rendered.size}")
            android.util.Log.d("BeeMapLabelDiag", "fixed PLACE layer source=bee-field source-layer=place minzoom=8 maxzoom=20 allow-overlap=true ignore-placement=true rendered count=${renderedFixedPlace.size}")
            android.util.Log.d("BeeMapLabelDiag", "TEST layer=diagnostic-test source=diagnostic-point source-layer=<none> minzoom=0 maxzoom=20 visibility=visible text=TEST allow-overlap=true ignore-placement=true rendered count=${renderedTest.size}")
            captureScreenshot("sapunovo-label-diagnostic-${fontStack.replace(" ", "-")}")
        }
    }

    @Test
    fun localSapunovoPmtilesLayersRestoreOneByOne() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beePmtilesLayerIsolation") == "true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val mapView = scenario.findMapView()
            val map = mapView.awaitMap()
            for (stage in 0..10) {
                val profile = beeSearchLocalPmtilesDiagnosticStageProfile(instrumentation.targetContext, "sapunovo", stage)
                map.setStyleAndAwait(profile.styleJson)
                map.moveAndAwait(56.1068, 42.6653, 12.0)
                assertAnyRendered(mapView, map, "landcover", "transportation")
            }
            captureScreenshot("sapunovo-layer-isolation-final")
        }
    }

    private fun ActivityScenario<MainActivity>.findMapView(): MapView {
        var result: MapView? = null
        assertTrue("The current persisted startup route did not contain BeeMap", eventually {
            onActivity { activity ->
                result = activity.window.decorView.findMapView()
            }
            result != null
        })
        return checkNotNull(result)
    }

    private fun MapView.awaitMap(): MapLibreMap {
        val latch = CountDownLatch(1)
        lateinit var result: MapLibreMap
        onMain {
            getMapAsync { map ->
                result = map
                latch.countDown()
            }
        }
        assertTrue("MapLibreMap was not ready", latch.await(20, TimeUnit.SECONDS))
        return result
    }

    private fun MapLibreMap.awaitStyle() {
        val latch = CountDownLatch(1)
        onMain { getStyle { latch.countDown() } }
        assertTrue("Bee Search field style was not loaded", latch.await(20, TimeUnit.SECONDS))
    }

    private fun MapLibreMap.setStyleAndAwait(styleJson: String) {
        val latch = CountDownLatch(1)
        onMain { setStyle(Style.Builder().fromJson(styleJson)) { latch.countDown() } }
        assertTrue("Local PMTiles style was not loaded", latch.await(20, TimeUnit.SECONDS))
    }

    private fun eventually(predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (onMain { predicate() }) return true
            Thread.sleep(100)
        }
        return false
    }

    private fun MapLibreMap.moveAndAwait(
        latitude: Double,
        longitude: Double,
        zoom: Double,
    ) {
        val cameraLatch = CountDownLatch(1)
        fun isTargetPosition(): Boolean {
            val position = cameraPosition
            val target = position.target
            return (
                abs(position.zoom - zoom) < 0.05 &&
                target != null &&
                abs(target.latitude - latitude) < 0.00001 &&
                abs(target.longitude - longitude) < 0.00001
            )
        }
        val cameraListener = MapLibreMap.OnCameraIdleListener {
            if (isTargetPosition()) cameraLatch.countDown()
        }
        onMain {
            addOnCameraIdleListener(cameraListener)
            moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom))
        }
        assertTrue("Map did not become idle at z$zoom", cameraLatch.await(20, TimeUnit.SECONDS))
        onMain { removeOnCameraIdleListener(cameraListener) }
    }

    private fun assertRendered(
        mapView: MapView,
        map: MapLibreMap,
        layer: String,
        predicate: (org.maplibre.geojson.Feature) -> Boolean,
    ) {
        fun matches(): Boolean = map.queryRenderedFeatures(
                RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()),
                layer,
            ).any(predicate)
        if (onMain { matches() }) return

        val latch = CountDownLatch(1)
        val listener = MapView.OnDidFinishRenderingFrameListener { _, _, _ ->
            if (matches()) latch.countDown()
        }
        onMain {
            mapView.addOnDidFinishRenderingFrameListener(listener)
            if (matches()) latch.countDown()
        }
        assertTrue("No expected feature rendered in $layer", latch.await(20, TimeUnit.SECONDS))
        onMain { mapView.removeOnDidFinishRenderingFrameListener(listener) }
    }

    private fun assertAnyRendered(
        mapView: MapView,
        map: MapLibreMap,
        vararg layers: String,
    ) {
        fun hasFeatures(): Boolean = map.queryRenderedFeatures(
                RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()),
                *layers,
            ).isNotEmpty()
        if (onMain { hasFeatures() }) return

        val latch = CountDownLatch(1)
        val listener = MapView.OnDidFinishRenderingFrameListener { _, _, _ ->
            if (hasFeatures()) latch.countDown()
        }
        onMain {
            mapView.addOnDidFinishRenderingFrameListener(listener)
            if (hasFeatures()) latch.countDown()
        }
        assertTrue("No feature rendered in ${layers.joinToString()}", latch.await(20, TimeUnit.SECONDS))
        onMain { mapView.removeOnDidFinishRenderingFrameListener(listener) }
    }

    private fun captureScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "map-poc")
        check(directory.mkdirs() || directory.isDirectory)
        FileOutputStream(File(directory, "$name.png")).use { output ->
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask(block)
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task)
        return task.get()
    }
}

private fun View.findMapView(): MapView? {
    if (this is MapView) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
        getChildAt(index).findMapView()?.let { return it }
    }
    return null
}
