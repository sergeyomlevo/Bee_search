package org.beesearch.app

import android.graphics.Bitmap
import android.graphics.RectF
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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
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

    private fun ActivityScenario<MainActivity>.findMapView(): MapView {
        lateinit var result: MapView
        onActivity { activity ->
            result = checkNotNull(activity.window.decorView.findMapView()) {
                "The current persisted startup route did not contain BeeMap"
            }
        }
        return result
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
