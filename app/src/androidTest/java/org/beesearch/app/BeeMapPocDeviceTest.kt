package org.beesearch.app

import android.graphics.Bitmap
import android.graphics.RectF
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.MapPackageImportResult
import org.beesearch.app.ui.map.coverageFragments

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
    fun reimportingActiveLargePackageIsIdempotent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeLargeActiveMapPackage") == "true")
        assertEquals("org.beesearch.app.dev", instrumentation.targetContext.packageName)
        assertTrue("Large-package device check must run only in a debug build", BuildConfig.DEBUG)

        val container = (instrumentation.targetContext.applicationContext as BeeSearchApplication).container
        val (territoryId, coverage, before) = runBlocking {
            val territoryId = requireNotNull(container.settingsRepository.getSettings().currentTerritoryId)
            val coverage = storedCoverage(container, territoryId)
            val ready = container.mapPackageStore.loadActive(territoryId, coverage) as? MapPackageAvailability.Ready
            Triple(territoryId, coverage, requireNotNull(ready).activePackage)
        }
        assertEquals("user-large-coverage-20260909-v2", before.manifest.packageId)
        val packagesDirectory = requireNotNull(before.pmtilesFile.parentFile?.parentFile)
        val beforeDirectories = packagesDirectory.listFiles().orEmpty().filter(File::isDirectory).map(File::getName).toSet()

        val result = runBlocking {
            container.mapPackageStore.import(
                territoryId = territoryId,
                desiredCoverage = coverage,
                manifestUri = Uri.fromFile(File(requireNotNull(before.pmtilesFile.parentFile), "package.manifest.json")),
                pmtilesUri = Uri.fromFile(before.pmtilesFile),
            )
        }
        assertTrue("Byte-identical active package was not an idempotent success: $result", result is MapPackageImportResult.Activated)
        val after = (result as MapPackageImportResult.Activated).activePackage
        assertEquals(before.pmtilesFile.absolutePath, after.pmtilesFile.absolutePath)
        assertEquals(
            beforeDirectories,
            packagesDirectory.listFiles().orEmpty().filter(File::isDirectory).map(File::getName).toSet(),
        )
    }

    @Test
    fun activeLargePackageRendersRemoteCoverageAndOverscalesOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeLargeActiveMapPackage") == "true")
        assertEquals("org.beesearch.app.dev", instrumentation.targetContext.packageName)
        assertTrue("Large-package device check must run only in a debug build", BuildConfig.DEBUG)

        val activePackage = runBlocking {
            val container = (instrumentation.targetContext.applicationContext as BeeSearchApplication).container
            val territoryId = requireNotNull(container.settingsRepository.getSettings().currentTerritoryId)
            val coverage = storedCoverage(container, territoryId)
            val ready = container.mapPackageStore.loadActive(territoryId, coverage) as? MapPackageAvailability.Ready
            requireNotNull(ready) { "The selected large package is not active and compatible" }
            ready.activePackage
        }
        assertEquals("user-large-coverage-20260909-v2", activePackage.manifest.packageId)
        val profile = beeSearchActivePmtilesMapProfile(activePackage)
        val timings = mutableListOf<String>()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val mapView = scenario.findMapView()
            val map = mapView.awaitMap()
            map.setStyleAndAwait(profile.styleJson)
            assertEquals(20.0, onMain { map.maxZoomLevel }, 0.0)
            assertNotNull(onMain { map.style?.getSource("bee-field") })

            val points = listOf(
                Triple("north-west", 56.43, 42.30),
                Triple("north-east", 56.43, 42.95),
                Triple("south-west", 55.78, 42.30),
                Triple("south-east", 55.78, 42.95),
            )
            points.forEach { (name, latitude, longitude) ->
                val started = System.nanoTime()
                map.moveAndAwait(latitude = latitude, longitude = longitude, zoom = 14.0)
                assertAnyRendered(
                    mapView,
                    map,
                    "forest",
                    "water",
                    "waterways",
                    "roads",
                    "tracks",
                    "railway",
                    "power-lines",
                    "cutlines",
                    "buildings",
                    "place-labels",
                )
                timings += "$name-z14=${"%.3f".format((System.nanoTime() - started) / 1_000_000_000.0)}s"
                captureScreenshot("large-active-$name-z14")
            }

            val overviewStarted = System.nanoTime()
            map.moveAndAwait(latitude = 56.1001581, longitude = 42.6222602, zoom = 9.0)
            assertAnyRendered(mapView, map, "forest", "water", "roads", "place-labels")
            timings += "overview-z9=${"%.3f".format((System.nanoTime() - overviewStarted) / 1_000_000_000.0)}s"
            captureScreenshot("large-active-overview-z9")

            val overscaleStarted = System.nanoTime()
            map.moveAndAwait(latitude = 56.0715664, longitude = 42.7508162, zoom = 20.0)
            assertEquals(20.0, onMain { map.cameraPosition.zoom }, 0.01)
            assertRendered(mapView, map, "cutlines") { it.getStringProperty("class") == "cutline" }
            timings += "center-z20=${"%.3f".format((System.nanoTime() - overscaleStarted) / 1_000_000_000.0)}s"
            captureScreenshot("large-active-center-z20")
        }

        val evidenceDirectory = File(instrumentation.targetContext.getExternalFilesDir(null), "map-poc")
        check(evidenceDirectory.mkdirs() || evidenceDirectory.isDirectory)
        File(evidenceDirectory, "large-active-performance.txt").writeText(timings.joinToString("\n") + "\n")
    }

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

    /**
     * The temporary DEV Sentinel basemap, exercised exactly as the map selector installs it:
     * same profile factory, same style JSON, same archive path. This check pins the real raster
     * levels z10..z13 and mirrors the owner-approved UI cap at the package maxzoom.
     */
    @Test
    fun devSentinelSelectorProfileRendersWholeAreaLevels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeSentinelDevProfile") == "true")
        assertEquals("org.beesearch.app.dev", instrumentation.targetContext.packageName)
        assertTrue("The DEV Sentinel basemap must exist only in a debug build", BuildConfig.DEBUG)
        val archive = devSentinelArchive(instrumentation.targetContext)
        assumeTrue("The DEV Sentinel archive is not staged on this device", archive != null)
        val profile = beeSearchDevSentinelMapProfile(requireNotNull(archive))
        val evidence = mutableListOf<String>()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val mapView = scenario.findMapView()
            val map = mapView.awaitMap()
            // The product map recenters to z15 over the device position when the first GPS fix
            // arrives. Wait that out, otherwise the recenter would move the camera during the
            // measurements below; with location disabled the wait simply expires.
            eventually { map.cameraPosition.zoom >= 14.9 }
            map.setStyleAndAwait(profile.styleJson)
            assertEquals(13.0, profile.uiMaxZoom, 0.0)
            onMain { map.setMaxZoomPreference(profile.uiMaxZoom) }
            assertEquals(13.0, onMain { map.maxZoomLevel }, 0.0)
            assertNotNull("The Sentinel raster source was not installed", onMain { map.style?.getSource("sentinel-area") })
            assertNotNull("The Sentinel raster layer was not installed", onMain { map.style?.getLayer("sentinel-area-raster") })

            for (zoom in listOf(10.0, 11.0, 12.0, 13.0)) {
                map.moveAndAwait(latitude = 56.1002, longitude = 42.6223, zoom = zoom)
                val covered = rasterCoveredFraction(mapView)
                // A product-side recenter arriving mid-measurement would invalidate the reading.
                assertTrue("The camera left z$zoom during the measurement", onMain { abs(map.cameraPosition.zoom - zoom) < 0.05 })
                evidence += "z$zoom covered=$covered"
                assertTrue(
                    "The DEV Sentinel profile did not paint the package at z$zoom ($covered)",
                    covered >= SENTINEL_DEVELOPER_COVERED_MIN,
                )
                captureScreenshot("dev-sentinel-z${zoom.toInt()}")
            }
        }

        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "map-poc")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "dev-sentinel-profile.txt").writeText(evidence.joinToString("\n") + "\n")
    }

    /**
     * Temporary DEV-only hybrid: the accepted Sentinel raster package with the existing offline
     * vector line and label layers on top, using the very profile the map selector installs.
     *
     * Reports per overlay class (roads, tracks, water lines, place/road labels) which layers
     * actually render at several characteristic places, so the owner review has facts instead of
     * a single pass/fail. Raster coverage is asserted; the overlay inventory is recorded because
     * it depends on what the vector package contains at each place.
     */
    @Test
    fun devHybridProfileRendersSentinelWithVectorOverlay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeHybridDevProfile") == "true")
        assertEquals("org.beesearch.app.dev", instrumentation.targetContext.packageName)
        assertTrue("The DEV hybrid must exist only in a debug build", BuildConfig.DEBUG)
        val sentinel = devSentinelArchive(instrumentation.targetContext)
        assumeTrue("The DEV Sentinel archive is not staged on this device", sentinel != null)
        val activePackage = runBlocking {
            val container = (instrumentation.targetContext.applicationContext as BeeSearchApplication).container
            val territoryId = requireNotNull(container.settingsRepository.getSettings().currentTerritoryId)
            val coverage = storedCoverage(container, territoryId)
            val ready = container.mapPackageStore.loadActive(territoryId, coverage) as? MapPackageAvailability.Ready
            requireNotNull(ready) { "No active compatible offline vector package" }.activePackage
        }
        val profile = beeSearchDevHybridMapProfile(requireNotNull(sentinel), activePackage.pmtilesFile)
        val evidence = mutableListOf<String>()
        evidence += "vectorPackage=${activePackage.manifest.packageId} path=${activePackage.pmtilesFile.name}"
        evidence += "hybridProfile=${profile.profileId} uiMaxZoom=${profile.uiMaxZoom} sourceMaxZoom=${profile.sourceMaxZoom}"
        evidence += "offline=true sources=raster+vector glyphs=asset"

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val mapView = scenario.findMapView()
            val map = mapView.awaitMap()
            eventually { map.cameraPosition.zoom >= 14.9 }
            map.setStyleAndAwait(profile.styleJson)
            // The map screen applies the profile zoom cap in its own style effect; mirror that
            // here. The user-visible clamp (deep zoom -> 13) is verified on the real UI.
            assertEquals(13.0, profile.uiMaxZoom, 0.0)
            onMain { map.setMaxZoomPreference(profile.uiMaxZoom) }
            assertEquals(13.0, onMain { map.maxZoomLevel }, 0.0)
            assertNotNull("The Sentinel raster source was not installed", onMain { map.style?.getSource("sentinel-area") })
            assertNotNull("The vector source was not installed", onMain { map.style?.getSource("bee-field") })
            assertNotNull("The raster layer was not installed", onMain { map.style?.getLayer("sentinel-area-raster") })
            assertNull("The hybrid must not carry the vector background", onMain { map.style?.getLayer("forest") })

            val overlayClasses = listOf(
                "roads", "tracks", "waterways",
                "place-labels", "water-labels", "waterway-labels", "road-labels",
            )
            val places = listOf(
                Triple("dev-territory", 56.1969, 42.7477),
                Triple("west-settlement", 56.1960, 42.6900),
                Triple("river-north", 56.1850, 42.6600),
                Triple("package-centre", 56.1002, 42.6223),
                Triple("river-south", 56.0200, 42.7000),
            )
            val problems = mutableListOf<String>()
            for ((name, latitude, longitude) in places) {
                map.moveAndAwait(latitude = latitude, longitude = longitude, zoom = 13.0)
                val covered = settledRasterCoveredFraction(mapView)
                captureScreenshot("dev-hybrid-$name")
                if (covered < 0.5) problems += "$name raster=$covered"
                val counts = overlayClasses.joinToString(" ") { layer ->
                    "$layer=${onMain { renderedFeatureCount(mapView, map, layer) }}"
                }
                evidence += "$name lat=$latitude lon=$longitude raster=$covered $counts"
            }

            // The composite remains usable at the current raster ceiling.
            map.moveAndAwait(latitude = 56.1969, longitude = 42.7477, zoom = 13.0)
            evidence += "zoomClamp=${onMain { map.cameraPosition.zoom }} maxZoom=${onMain { map.maxZoomLevel }}"

            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "map-poc")
            check(directory.mkdirs() || directory.isDirectory)
            File(directory, "dev-hybrid-profile.txt").writeText(evidence.joinToString("\n") + "\n")
            assertTrue("The hybrid raster did not paint at $problems", problems.isEmpty())
        }
    }

    private fun renderedFeatureCount(mapView: MapView, map: MapLibreMap, layer: String): Int =
        map.queryRenderedFeatures(
            RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()),
            layer,
        ).size

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

    /**
     * Coverage sampled only once the frame has settled: after a camera move MapLibre still has to
     * fetch and decode the new tiles, and a single capture would report the empty background as a
     * missing raster. Repeats until two consecutive readings agree.
     */
    private fun settledRasterCoveredFraction(mapView: MapView): Double {
        var previous = rasterCoveredFraction(mapView)
        repeat(8) {
            Thread.sleep(400)
            val current = rasterCoveredFraction(mapView)
            if (abs(current - previous) < 0.01) return current
            previous = current
        }
        return previous
    }

    /**
     * Fraction of the map viewport that differs from the Sentinel profile's light background,
     * sampled sparsely. A painted raster package reads near 1.0, an empty background near 0.0.
     */
    private fun rasterCoveredFraction(mapView: MapView): Double {
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val location = IntArray(2)
        onMain { mapView.getLocationOnScreen(location) }
        val left = location[0] + mapView.width / 10
        val right = location[0] + mapView.width - mapView.width / 10
        val top = location[1] + mapView.height / 5
        val bottom = location[1] + mapView.height - mapView.height / 5
        var covered = 0
        var total = 0
        var y = top
        while (y < bottom && y < screenshot.height) {
            var x = left
            while (x < right && x < screenshot.width) {
                val pixel = screenshot.getPixel(x, y)
                val differs = abs((pixel and 0xFF) - 0xF0) > 8 ||
                    abs(((pixel shr 8) and 0xFF) - 0xF0) > 8 ||
                    abs(((pixel shr 16) and 0xFF) - 0xF0) > 8
                if (differs) covered++
                total++
                x += 6
            }
            y += 6
        }
        return if (total == 0) 0.0 else covered.toDouble() / total
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

/** Minimum share of the sampled viewport the DEV Sentinel raster profile must paint. */
private const val SENTINEL_DEVELOPER_COVERED_MIN = 0.5

private fun View.findMapView(): MapView? {
    if (this is MapView) return this
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
        getChildAt(index).findMapView()?.let { return it }
    }
    return null
}

/** The участки of the territory's Ареал, as the map package validator expects them. */
private suspend fun storedCoverage(container: AppContainer, territoryId: java.util.UUID) =
    container.mapAreaStore
        .load(territoryId, container.territoryRepository.getTerritory(territoryId)?.name)
        .let { (it as? MapAreaReadResult.Present)?.area?.coverageFragments().orEmpty() }
