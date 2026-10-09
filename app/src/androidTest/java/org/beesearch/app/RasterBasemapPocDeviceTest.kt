package org.beesearch.app

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapObjectMarker
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.ResearchMarkerCatalog
import org.beesearch.app.ui.map.ResearchMarkerType
import org.beesearch.app.ui.map.coverageFragments
import org.beesearch.app.ui.map.observationPointMarkers
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
import org.maplibre.android.maps.Style
import org.maplibre.android.net.ConnectivityReceiver
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer

/**
 * Opt-in technical PoC: can the current Bee Search map stack use a fully offline **raster**
 * basemap, with working pan/zoom and with Bee Search user data drawn correctly above it?
 *
 * Control A — local XYZ raster tiles served from the app's private storage (`file://`).
 * Control B — the same imagery packed into a raster PMTiles v3 archive (`pmtiles://file://`), i.e.
 *              the archive integration the offline vector packages already use.
 *
 * This class is a preserved research/device harness. It never runs in a normal instrumentation run:
 * every experiment requires its own explicit flag (`beeRasterPoc`, `beeImageryPoc`,
 * `beeSharpnessPoc`, or `beeSentinelPoc`). It targets the DEV package only, adds no runtime
 * dependency and creates no production code path: input is injected through `UiAutomation` and the
 * accessibility tree is read through `UiAutomation.rootInActiveWindow`.
 *
 * After installing the debug app and androidTest APK, run one experiment with a scoped command such
 * as:
 *
 * `adb shell am instrument -w -e class org.beesearch.app.RasterBasemapPocDeviceTest#sentinelZoomReviewOnDevice -e beeSentinelPoc true org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner`
 *
 * The large fixtures deliberately stay outside Git. Push only the fixture needed by the selected
 * experiment below `<external-files>/`, which is normally
 * `/sdcard/Android/data/org.beesearch.app.dev/files/` on the test device. Results are written to
 * `<external-files>/map-poc/raster-poc/`.
 *
 * Base-raster fixtures: `poc-input/xyz-z15`, `poc-input/xyz-z16`,
 * `poc-input/cyclosm-raster-z15-v1.pmtiles`, and
 * `poc-input/cyclosm-raster-z15-v1-png.pmtiles`.
 *
 * Second experiment (`-e beeImageryPoc true`): the same proven raster path is used to show REAL
 * airborne imagery (USGS NAIP, public domain, native 0.30 m/px, Oregon 2022-07-11) at several
 * HONEST information levels — native 0.3 m and downsampled 0.5 / 1 / 2 m — from one source image
 * over one area, so the owner can compare what each level actually resolves. Display zoom and
 * source ground resolution are deliberately kept apart: above a variant's own zoom level the map
 * only overzooms the same pixels and shows no new detail. Its tile trees live under
 * `poc-imagery/<variant>`; sharpness diagnostics use `poc-sharp/<variant>`.
 *
 * Third experiment (`-e beeSentinelPoc true`): one real Copernicus Sentinel-2 L2A true-colour
 * source (B04/B03/B02, native 10 m/px) is shown at display z13..z17 over one fixed centre. The
 * fixture physically contains source z13..z18 so the target Samsung can request z+1 without
 * turning a missing source level into accidental overzoom. Its lossless PNG XYZ tree lives at
 * `poc-sentinel/true-color/{z}/{x}/{y}.png`.
 */
@RunWith(AndroidJUnit4::class)
class RasterBasemapPocDeviceTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    /** Camera anchor inside the PoC raster extent and near the DEV territory points. */
    private val territoryCenter = 56.1914 to 42.7423

    /** ~18 km east of the raster's eastern edge: the raster must draw nothing there. */
    private val outsideRaster = 56.1900 to 43.0500

    /** Centre of the real-imagery comparison area (Oregon, USGS NAIP, 1400 x 1400 m ground). */
    private val imageryCenter = 45.7420 to -123.6150

    /** Centre of the 250 x 250 m sharpness-diagnostic window inside the same NAIP area. */
    private val sharpnessCenter = 45.747032 to -123.615002

    /** Fixed centre of the Sentinel-2 overview comparison near the working Bee Search territory. */
    private val sentinelCenter = 56.1914 to 42.7423

    /** Centre of the whole-area Sentinel-2 overview package (the current area coverage rectangle). */
    private val sentinelAreaCenter = 56.1002 to 42.6223

    @Test
    fun sentinelZoomReviewOnDevice() {
        assumeSentinelPoc()
        val root = prepareSentinelFixtures()
        val connectivity = ConnectivityReceiver.instance(instrumentation.targetContext)
        connectivity.setConnected(false)
        val evidence = mutableListOf<String>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                applyRasterStyle(map, sentinelRasterStyle(root), RASTER_SOURCE)
                // The product map applies its one-time z15 recenter when the first GPS reading
                // arrives. Wait for that actual state instead of guessing a delay; otherwise only
                // the z13 owner-review frame can be silently replaced by the first-fix camera.
                assertTrue(
                    "GPS reading did not become available before the Sentinel review",
                    eventually(20_000) {
                        findAccessibilityNode { it.text?.toString()?.startsWith("Точность ") == true } != null
                    },
                )
                SystemClock.sleep(500)
                evidence += "product=S2B_T38VLH_20260718T082107_L2A acquisition=2026-07-18T08:24:24.15Z " +
                    "bands=B04,B03,B02 nativeGsdM=10 sourceLevels=13..18"
                for (zoom in SENTINEL_DISPLAY_ZOOMS) {
                    val label = "sentinel-z${zoom.toInt()}"
                    settleSentinelCamera(mapView, map, zoom, label)
                    val drawn = measureRasterCoverage(mapView, map, sentinelCenter, zoom, evidence, label)
                    val actualZoom = onMain { map.cameraPosition.zoom }
                    assertEquals("$label camera zoom was overridden", zoom, actualZoom, 0.05)
                    captureScreenshot(label)
                    assertTrue(
                        "Sentinel-2 was not drawn at display z$zoom (rasterPixels $drawn)",
                        drawn >= IMAGERY_COVERED_MIN,
                    )
                    evidence += "$label actualZoom=${"%.3f".format(actualZoom)} " +
                        "groundMetersPerScreenWidth=${groundMetersPerScreenWidth(mapView, map)} drawn=$drawn offline=true"
                }
            }
        } finally {
            connectivity.setConnected(null)
            writeEvidence("sentinel-evidence.txt", evidence)
        }
    }

    /**
     * Whole-area Sentinel-2 overview: one offline raster PMTiles archive covering the entire
     * existing Bee Search area (west 42.2056, south 55.7059, east 43.0389, north 56.4945, about
     * 4512 km2), with real pyramid levels z10, z11, z12 and z13 built from the same 10 m source.
     *
     * Beyond the four zoom screenshots this checks the whole rectangle rather than its centre:
     * the four inset corners, a reference place with forest, fields and a river, the package
     * boundary, and a second independent open of the same offline archive.
     */
    @Test
    fun sentinelAreaOverviewOnDevice() {
        assumeSentinelAreaPoc()
        val archive = prepareSentinelAreaFixture()
        val connectivity = ConnectivityReceiver.instance(instrumentation.targetContext)
        connectivity.setConnected(false)
        val evidence = mutableListOf<String>()
        try {
            val firstOpenStarted = System.nanoTime()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                // The product map applies its one-time z15 recenter and reinstalls its own style when
                // the first GPS reading arrives; wait for that state before installing the PoC style.
                assertTrue(
                    "GPS reading did not become available before the Sentinel area review",
                    eventually(20_000) {
                        findAccessibilityNode { it.text?.toString()?.startsWith("Точность ") == true } != null
                    },
                )
                SystemClock.sleep(500)
                applyRasterStyle(map, sentinelAreaRasterStyle(archive), RASTER_SOURCE)
                evidence += "firstOpen styleSeconds=${seconds(firstOpenStarted)} archiveBytes=${archive.length()} " +
                    "levels=10,11,12,13 tileType=png transport=pmtiles"
                for (zoom in SENTINEL_AREA_ZOOMS) {
                    val label = "sentinel-area-z${zoom.toInt()}"
                    val drawn = measureSentinelArea(mapView, map, archive, sentinelAreaCenter, zoom, evidence, label)
                    captureScreenshot(label)
                    assertTrue("Sentinel overview not drawn at z$zoom (rasterPixels $drawn)", drawn >= SENTINEL_AREA_MIN)
                }
                for ((name, position) in SENTINEL_AREA_PROBES) {
                    val label = "sentinel-area-$name"
                    val drawn = measureSentinelArea(mapView, map, archive, position, 13.0, evidence, label)
                    captureScreenshot(label)
                    assertTrue("Sentinel overview missing at the $name corner (rasterPixels $drawn)", drawn >= SENTINEL_AREA_MIN)
                }
                val reference = measureSentinelArea(
                    mapView, map, archive, SENTINEL_AREA_REFERENCE, 13.0, evidence, "sentinel-area-reference",
                )
                captureScreenshot("sentinel-area-reference")
                assertTrue("Sentinel overview missing at the reference place ($reference)", reference >= SENTINEL_AREA_MIN)
                val edge = measureSentinelArea(
                    mapView, map, archive, SENTINEL_AREA_EDGE, 11.0, evidence, "sentinel-area-edge",
                )
                captureScreenshot("sentinel-area-edge")
                evidence += "sentinel-area-edge drawn=$edge (the remaining part of this frame is the package boundary)"
                val outside = measureSentinelArea(
                    mapView, map, archive, SENTINEL_AREA_OUTSIDE, 13.0, evidence, "sentinel-area-outside",
                )
                captureScreenshot("sentinel-area-outside")
                assertTrue("Sentinel imagery painted outside its own coverage ($outside)", outside <= RASTER_OUTSIDE_MAX)
            }
            val reopenStarted = System.nanoTime()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                val drawn = measureSentinelArea(
                    mapView, map, archive, sentinelAreaCenter, 13.0, evidence, "sentinel-area-reopen-z13",
                )
                captureScreenshot("sentinel-area-reopen-z13")
                evidence += "reopen styleSeconds=${seconds(reopenStarted)} rasterPixels=$drawn"
                assertTrue("The Sentinel overview did not come back after a restart ($drawn)", drawn >= SENTINEL_AREA_MIN)
            }
        } finally {
            connectivity.setConnected(null)
            writeEvidence("sentinel-area-evidence.txt", evidence)
        }
    }

    /**
     * Sharpness diagnostic for the 250 x 250 m window: one camera, one display zoom per screenshot,
     * only the tile pipeline changes. The corrected variants are built with a single Lanczos resample
     * and lossless PNG; the control reproduces the previous build (bilinear placement + JPEG q90).
     * The corrected z19 variant is captured twice, with `raster-resampling` linear and nearest, to
     * measure MapLibre's own texture filtering separately from the tile content.
     */
    @Test
    fun imagerySharpnessDiagnosticOnDevice() {
        assumeSharpnessPoc()
        val roots = prepareSharpnessFixtures()
        val connectivity = ConnectivityReceiver.instance(instrumentation.targetContext)
        connectivity.setConnected(false)
        val evidence = mutableListOf<String>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                for (variant in SHARPNESS_VARIANTS) {
                    val root = roots.getValue(variant.directory)
                    val tileCount = File(root).walkTopDown().count { it.isFile }
                    for (zoom in variant.displayZooms) {
                        for (resampling in variant.resampling) {
                            applyRasterStyle(
                                map,
                                sharpnessRasterStyle(root, variant.minZoom, variant.maxZoom, variant.encoding, resampling, variant.tileSize),
                                RASTER_SOURCE,
                            )
                            val suffix = if (resampling == "nearest") "-nearest" else ""
                            val label = "sharp-${variant.directory}-z${zoom.toInt()}$suffix"
                            val drawn = measureRasterCoverage(mapView, map, sharpnessCenter, zoom, evidence, label)
                            captureScreenshot(label)
                            evidence += "$label note=${variant.note} tiles=$tileCount drawn=$drawn " +
                                "groundMetersPerScreenWidth=${groundMetersPerScreenWidth(mapView, map)} resampling=$resampling " +
                                "density=${instrumentation.targetContext.resources.displayMetrics.density} " +
                                "viewportPx=${onMain { mapView.width }}x${onMain { mapView.height }}"
                        }
                    }
                }
            }
        } finally {
            connectivity.setConnected(null)
            writeEvidence("sharpness-evidence.txt", evidence)
        }
    }

    /**
     * Owner-review material: one real airborne-imagery area, one camera, one display zoom per
     * screenshot, only the raster dataset changes between variants.
     */
    @Test
    fun imageryResolutionComparisonOnDevice() {
        assumeImageryPoc()
        val roots = prepareImageryFixtures()
        val connectivity = ConnectivityReceiver.instance(instrumentation.targetContext)
        connectivity.setConnected(false)
        val evidence = mutableListOf<String>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                for (variant in IMAGERY_VARIANTS) {
                    val root = roots.getValue(variant.directory)
                    val tileCount = File(root).walkTopDown().count { it.isFile }
                    applyRasterStyle(
                        map,
                        imageryRasterStyle(root, variant.minZoom, variant.maxZoom),
                        RASTER_SOURCE,
                    )
                    evidence += "variant=${variant.directory} information=${variant.information} " +
                        "zmin=${variant.minZoom} zmax=${variant.maxZoom} tiles=$tileCount"
                    for (zoom in IMAGERY_DISPLAY_ZOOMS) {
                        val label = "imagery-${variant.directory}-z${zoom.toInt()}"
                        val drawn = measureRasterCoverage(mapView, map, imageryCenter, zoom, evidence, label)
                        captureScreenshot(label)
                        assertTrue(
                            "Imagery variant ${variant.directory} was not drawn at display z$zoom (rasterPixels $drawn)",
                            drawn >= IMAGERY_COVERED_MIN,
                        )
                    }
                }
            }
        } finally {
            connectivity.setConnected(null)
            writeEvidence("imagery-evidence.txt", evidence)
        }
    }

    @Test
    fun controlALocalXyzRasterRendersOfflineAcrossPanAndZoom() {
        assumePoc()
        val fixtures = prepareFixtures()
        val connectivity = ConnectivityReceiver.instance(instrumentation.targetContext)
        connectivity.setConnected(false)
        val evidence = mutableListOf<String>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                val styleStarted = System.nanoTime()
                applyRasterStyle(map, localXyzRasterStyle(fixtures.xyzZ15), RASTER_SOURCE)
                evidence += "style+first-frame=${seconds(styleStarted)}s"
                assertNotNull("raster source missing", onMain { map.style?.getSource(RASTER_SOURCE) })
                assertNotNull("raster layer missing", onMain { map.style?.getLayer(RASTER_LAYER) })
                evidence += "timeToFirstRasterFrame z15=${secondsToFirstRasterFrame(mapView, map, territoryCenter, 15.0)}s"
                evidence += "timeToFirstRasterFrame z20=${secondsToFirstRasterFrame(mapView, map, territoryCenter, 20.0)}s"

                for (zoom in listOf(15.0, 17.0, 18.0, 20.0)) {
                    val drawn = measureRasterCoverage(mapView, map, territoryCenter, zoom, evidence, "control-a-in-z${zoom.toInt()}")
                    captureScreenshot("control-a-in-z${zoom.toInt()}")
                    assertTrue("Control A: raster not drawn at z$zoom (rasterPixels $drawn)", drawn >= RASTER_COVERED_MIN)
                }

                for ((name, position) in PANS) {
                    val drawn = measureRasterCoverage(mapView, map, position, 15.0, evidence, "control-a-pan-$name")
                    captureScreenshot("control-a-pan-$name")
                    // The pan corners sit near the dataset edges, so part of the sampled band is
                    // legitimately off the raster; what must hold is that the raster moved with it.
                    assertTrue("Control A: raster did not follow the pan to $name (rasterPixels $drawn)", drawn >= RASTER_PAN_MIN)
                }

                val outside = measureRasterCoverage(mapView, map, outsideRaster, 15.0, evidence, "control-a-outside-z15")
                captureScreenshot("control-a-outside-z15")
                assertTrue("Control A: raster painted $outside outside its own extent", outside <= RASTER_OUTSIDE_MAX)

                // Recorded behaviour, not a failure: the dataset carries only z15, and MapLibre
                // Native 13.5.1 paints nothing below the source minzoom instead of underzooming.
                val belowMinzoom = measureRasterCoverage(mapView, map, territoryCenter, 13.0, evidence, "control-a-below-minzoom-z13")
                captureScreenshot("control-a-below-minzoom-z13")
                evidence += "control-a-below-minzoom-z13: dataset minzoom is 15, nothing painted below it"
                assertTrue("Control A: below the dataset minzoom the raster suddenly painted $belowMinzoom", belowMinzoom <= RASTER_OUTSIDE_MAX)

                val far = measureRasterCoverage(mapView, map, territoryCenter, 19.0, evidence, "control-a-in-z19")
                assertTrue("Control A: raster lost after returning to the extent ($far)", far >= RASTER_COVERED_MIN)
            }
        } finally {
            connectivity.setConnected(null)
            writeEvidence("control-a-evidence.txt", evidence)
        }
    }

    @Test
    fun controlBRasterPmtilesWithWriterTileTypeRendersOffline() {
        assumePoc()
        val fixtures = prepareFixtures()
        assertRasterPmtilesBasemap(
            fixtures.pmtilesWriterHeader,
            "control-b-writer-header",
            pmtilesRasterStyle("pmtiles://file://${fixtures.pmtilesWriterHeader.absolutePath.replace('\\', '/')}"),
        )
    }

    @Test
    fun controlBRasterPmtilesWithPngTileTypeRendersOffline() {
        assumePoc()
        val fixtures = prepareFixtures()
        assertRasterPmtilesBasemap(
            fixtures.pmtilesPngHeader,
            "control-b-png-header",
            pmtilesRasterStyle("pmtiles://file://${fixtures.pmtilesPngHeader.absolutePath.replace('\\', '/')}"),
        )
    }

    /**
     * Documented negative result of the same archive in a second integration form: MapLibre Native
     * 13.5.1 serves a raster PMTiles archive through `url: pmtiles://…`, not through a `pmtiles://`
     * z/x/y template inside `tiles`.
     */
    @Test
    fun rasterPmtilesTileTemplateFormPaintsNothing() {
        assumePoc()
        val fixtures = prepareFixtures()
        val archive = fixtures.pmtilesPngHeader
        val connectivity = ConnectivityReceiver.instance(instrumentation.targetContext)
        connectivity.setConnected(false)
        val evidence = mutableListOf<String>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                applyRasterStyle(map, pmtilesRasterTileTemplateStyle(archive.absolutePath), RASTER_SOURCE)
                val drawn = measureRasterCoverage(mapView, map, territoryCenter, 15.0, evidence, "control-b-tile-template-z15")
                captureScreenshot("control-b-tile-template-z15")
                evidence += "control-b-tile-template: unsupported form painted rasterPixels=$drawn"
                assertTrue(
                    "The pmtiles:// tile-template form unexpectedly painted the basemap ($drawn); update the recommendation",
                    drawn <= RASTER_OUTSIDE_MAX,
                )
            }
        } finally {
            connectivity.setConnected(null)
            writeEvidence("control-b-tile-template-evidence.txt", evidence)
        }
    }

    /**
     * Control B body. The header variants differ only in the PMTiles `tileType` byte, which the
     * standard PMTiles writer available to this project stamps as MVT even for a raster payload.
     */
    private fun assertRasterPmtilesBasemap(archive: File, evidenceName: String, styleJson: String) {
        assertTrue("Raster PMTiles archive is missing: $archive", archive.isFile && archive.length() > 0)
        val connectivity = ConnectivityReceiver.instance(instrumentation.targetContext)
        connectivity.setConnected(false)
        val evidence = mutableListOf<String>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                val styleStarted = System.nanoTime()
                applyRasterStyle(map, styleJson, RASTER_SOURCE)
                evidence += "style+first-frame=${seconds(styleStarted)}s archive=${archive.name} bytes=${archive.length()}"
                assertNotNull("raster source missing ($evidenceName)", onMain { map.style?.getSource(RASTER_SOURCE) })
                evidence += "timeToFirstRasterFrame z15=${secondsToFirstRasterFrame(mapView, map, territoryCenter, 15.0)}s"

                val drawn = measureRasterCoverage(mapView, map, territoryCenter, 15.0, evidence, "$evidenceName-z15")
                captureScreenshot("$evidenceName-z15")
                assertTrue("Control B ($evidenceName): raster PMTiles rasterPixels $drawn", drawn >= RASTER_COVERED_MIN)

                for (zoom in listOf(17.0, 20.0)) {
                    val zoomDrawn = measureRasterCoverage(mapView, map, territoryCenter, zoom, evidence, "$evidenceName-z${zoom.toInt()}")
                    captureScreenshot("$evidenceName-z${zoom.toInt()}")
                    assertTrue("Control B ($evidenceName): raster PMTiles lost at z$zoom ($zoomDrawn)", zoomDrawn >= RASTER_COVERED_MIN)
                }

                val outside = measureRasterCoverage(mapView, map, outsideRaster, 15.0, evidence, "$evidenceName-outside-z15")
                assertTrue("Control B ($evidenceName): raster painted $outside outside its own extent", outside <= RASTER_OUTSIDE_MAX)
            }
        } finally {
            connectivity.setConnected(null)
            writeEvidence("$evidenceName-evidence.txt", evidence)
        }
    }

    /**
     * Overlay check over the offline raster basemap with the product's own user data: the saved
     * observation points on «Объекты» → «Точки наблюдения» (mode POINT_BROWSER), i.e. the real Bee
     * Search marker overlay, not a new overlay kind.
     *
     * For every visible marker the screen bounds are compared with the map projection of that
     * point's own coordinates, and the coordinate derived back from the marker bounds must keep
     * pointing inside the raster extent across zoom and pan.
     */
    @Test
    fun beeSearchMarkersStayGeoreferencedOverOfflineRasterBasemap() {
        assumePoc()
        val fixtures = prepareFixtures()
        val container = (instrumentation.targetContext.applicationContext as BeeSearchApplication).container
        val markers = runBlocking {
            val territoryId = requireNotNull(container.settingsRepository.getSettings().currentTerritoryId)
            observationPointMarkers(
                container.observationRepository
                    .observeObservationPointSummaries(territoryId)
                    .first(),
            )
        }
        assertTrue("The DEV package has no saved observation points to overlay", markers.isNotEmpty())
        // Several DEV points lie a few metres apart and their tone-coloured discs merge on screen,
        // so only markers with no neighbour closer than ISOLATED_MARKER_M are measured.
        val verifiable = isolatedMarkers(markers)
        val anchor = verifiable.firstOrNull() ?: markers.first()
        val connectivity = ConnectivityReceiver.instance(instrumentation.targetContext)
        connectivity.setConnected(false)
        val evidence = mutableListOf<String>()
        var verifiedMarkers = 0
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTrue("Could not open the saved-points map («Объекты» → «Точки наблюдения»)", openPointsBrowserMap())
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                applyRasterStyle(map, localXyzRasterStyle(fixtures.xyzZ15), RASTER_SOURCE)

                val anchorPosition = anchor.latitude to anchor.longitude
                for (zoom in listOf(15.0, 16.0, 17.0, 18.0, 20.0)) {
                    moveTo(mapView, map, anchorPosition, zoom)
                    SystemClock.sleep(1_200)
                    verifiedMarkers += verifyVisibleMarkers(mapView, map, verifiable, evidence, "overlay-z${zoom.toInt()}")
                    captureScreenshot("overlay-z${zoom.toInt()}")
                }

                for ((name, position) in overlayPans(anchor.latitude, anchor.longitude)) {
                    moveTo(mapView, map, position, 16.0)
                    SystemClock.sleep(1_200)
                    verifiedMarkers += verifyVisibleMarkers(mapView, map, verifiable, evidence, "overlay-pan-$name")
                    captureScreenshot("overlay-pan-$name")
                }
                assertTrue("No Bee Search marker could be verified above the raster basemap", verifiedMarkers >= 4)

                val basemap = measureRasterCoverage(mapView, map, anchorPosition, 15.0, evidence, "overlay-basemap-z15")
                captureScreenshot("overlay-basemap-z15")
                assertTrue("The raster basemap was not behind the markers (rasterPixels $basemap)", basemap >= RASTER_COVERED_MIN)
            }
        } finally {
            connectivity.setConnected(null)
            writeEvidence("overlay-evidence.txt", evidence)
        }
    }

    /** Regression: the offline vector PMTiles package of the DEV app still renders with no network. */
    @Test
    fun activeVectorPmtilesBasemapStillRendersOffline() {
        assumePoc()
        val container = (instrumentation.targetContext.applicationContext as BeeSearchApplication).container
        val profile = runBlocking {
            val territoryId = requireNotNull(container.settingsRepository.getSettings().currentTerritoryId)
            val coverage = container.mapAreaStore
                .load(territoryId, container.territoryRepository.getTerritory(territoryId)?.name)
                .let { (it as? MapAreaReadResult.Present)?.area?.coverageFragments() }
                .orEmpty()
            val ready = container.mapPackageStore.loadActive(territoryId, coverage) as? MapPackageAvailability.Ready
            beeSearchActivePmtilesMapProfile(requireNotNull(ready) { "No active vector package" }.activePackage)
        }
        val connectivity = ConnectivityReceiver.instance(instrumentation.targetContext)
        connectivity.setConnected(false)
        val evidence = mutableListOf<String>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val mapView = scenario.findMapView()
                val map = mapView.awaitMap()
                map.setStyleAndAwait(profile.styleJson)
                assertNotNull(onMain { map.style?.getSource("bee-field") })
                var started = System.nanoTime()
                map.moveAndAwait(56.43, 42.30, 14.0)
                evidence += "vector cameraIdle z14=${seconds(started)}s"
                assertAnyRendered(mapView, map, "forest", "water", "waterways", "roads", "tracks", "buildings", "place-labels")
                captureScreenshot("regression-vector-z14")
                started = System.nanoTime()
                map.moveAndAwait(56.0715664, 42.7508162, 20.0)
                evidence += "vector cameraIdle z20=${seconds(started)}s"
                assertAnyRendered(mapView, map, "forest", "roads", "tracks", "cutlines")
                captureScreenshot("regression-vector-z20")
            }
        } finally {
            connectivity.setConnected(null)
            writeEvidence("regression-vector-evidence.txt", evidence)
        }
    }

    // ------------------------------------------------------------------ overlay verification

    /** Markers whose nearest neighbour is at least [ISOLATED_MARKER_M] away, so discs do not merge. */
    private fun isolatedMarkers(markers: List<MapObjectMarker>): List<MapObjectMarker> =
        markers.filter { candidate ->
            markers.filter { it !== candidate }.all { metersBetween(candidate, it) >= ISOLATED_MARKER_M }
        }

    private fun metersBetween(first: MapObjectMarker, second: MapObjectMarker): Double {
        val dLat = (first.latitude - second.latitude) * METERS_PER_DEGREE_LAT
        val dLon = (first.longitude - second.longitude) * METERS_PER_DEGREE_LAT * cos(Math.toRadians(first.latitude))
        return hypot(dLat, dLon)
    }

    private fun verifyVisibleMarkers(
        mapView: MapView,
        map: MapLibreMap,
        markers: List<MapObjectMarker>,
        evidence: MutableList<String>,
        label: String,
    ): Int {
        val mapLocation = IntArray(2)
        onMain { mapView.getLocationOnScreen(mapLocation) }
        val camera = onMain { map.cameraPosition }
        evidence += "$label camera=${"%.5f".format(camera.target!!.latitude)},${"%.5f".format(camera.target!!.longitude)}" +
            " z=${"%.2f".format(camera.zoom)} mapView=(${mapLocation[0]},${mapLocation[1]})"
        val region = sampleRegion(mapView)
        val screenshot = captureStable(region)
        var verified = 0
        for (marker in markers) {
            val projected = projectToScreen(mapView, map, marker.latitude, marker.longitude)
            val node = findAccessibilityNode { descriptionOf(it) == marker.label }
            val nodeCenter = node?.let {
                val bounds = Rect()
                it.getBoundsInScreen(bounds)
                bounds
            }
            val centroid = markerCentroid(screenshot, projected)
            val horizontalDelta = centroid?.let { abs(it.first - projected.first) }
            val verticalDelta = centroid?.let { projected.second - it.second }
            evidence += "$label marker=${marker.label} projected=(${"%.0f".format(projected.first)},${"%.0f".format(projected.second)}) " +
                "nodeBounds=$nodeCenter markerCentroid=$centroid horizontalDeltaPx=${horizontalDelta?.let { "%.1f".format(it) } ?: "not-found"}"
            if (centroid == null || horizontalDelta == null || verticalDelta == null) continue
            // The approved production pin anchors its tip at the recorded position, so its painted
            // glyph sits above the projection inside the marker box; the horizontal centre does not move.
            assertTrue(
                "$label: marker ${marker.label} was drawn ${"%.0f".format(horizontalDelta)}px sideways from its projected position over the raster",
                horizontalDelta <= MARKER_TOLERANCE_PX,
            )
            assertTrue(
                "$label: marker ${marker.label} was drawn ${"%.0f".format(verticalDelta)}px outside the pin box of its projected position over the raster",
                verticalDelta >= -MARKER_TOLERANCE_PX && verticalDelta <= markerBoxPx,
            )
            val derived = deriveCoordinate(map, mapView, PointF(centroid.first - mapLocation[0], centroid.second - mapLocation[1]))
            evidence += "$label marker=${marker.label} derived=${"%.6f".format(derived.first)},${"%.6f".format(derived.second)}" +
                " expected=${"%.6f".format(marker.latitude)},${"%.6f".format(marker.longitude)}"
            assertTrue(
                "$label: marker ${marker.label} sits outside the raster extent",
                insideRasterExtent(derived.first, derived.second),
            )
            verified++
        }
        if (verified == 0) evidence += "$label: no marker drawn near its projected position"
        return verified
    }

    /**
     * Approved D102 production marker appearance, read from the single presentation catalogue so the
     * pixel search and the production marker cannot drift apart.
     */
    private val markerFamilyColor = ResearchMarkerType.OBSERVATION_POINT.familyColor.toArgb()
    private val markerBoxPx = ResearchMarkerCatalog.TOUCH_TARGET_SIZE_DP *
        instrumentation.targetContext.resources.displayMetrics.density

    /**
     * Centroid of the pixels painted in the marker's approved family colour inside a window around
     * the projected position of the marker. This reads the marker off the screen itself, so it is
     * independent of how the accessibility tree reports Compose bounds.
     */
    private fun markerCentroid(bitmap: Bitmap, projected: Pair<Float, Float>): Pair<Float, Float>? {
        val markerColor = markerFamilyColor
        val left = (projected.first - TONE_WINDOW_PX).toInt().coerceAtLeast(0)
        val right = (projected.first + TONE_WINDOW_PX).toInt().coerceAtMost(bitmap.width - 1)
        val top = (projected.second - TONE_WINDOW_PX).toInt().coerceAtLeast(0)
        val bottom = (projected.second + TONE_WINDOW_PX).toInt().coerceAtMost(bitmap.height - 1)
        var sumX = 0L
        var sumY = 0L
        var count = 0
        for (y in top..bottom) {
            for (x in left..right) {
                val color = bitmap.getPixel(x, y)
                if (
                    abs((color shr 16 and 0xFF) - (markerColor shr 16 and 0xFF)) <= 12 &&
                    abs((color shr 8 and 0xFF) - (markerColor shr 8 and 0xFF)) <= 12 &&
                    abs((color and 0xFF) - (markerColor and 0xFF)) <= 12
                ) {
                    sumX += x
                    sumY += y
                    count++
                }
            }
        }
        if (count < 60) return null
        return (sumX.toFloat() / count) to (sumY.toFloat() / count)
    }

    private fun deriveCoordinate(map: MapLibreMap, mapView: MapView, mapLocal: PointF): Pair<Double, Double> {
        val latLng = onMain { map.projection.fromScreenLocation(mapLocal) }
        return latLng.latitude to latLng.longitude
    }

    /** Camera positions that keep the clustered DEV points on screen at z16. */
    private fun overlayPans(latitude: Double, longitude: Double): List<Pair<String, Pair<Double, Double>>> = listOf(
        "offset-nw" to ((latitude + 0.0020) to (longitude - 0.0040)),
        "offset-ne" to ((latitude + 0.0020) to (longitude + 0.0040)),
        "offset-sw" to ((latitude - 0.0020) to (longitude - 0.0040)),
        "offset-se" to ((latitude - 0.0020) to (longitude + 0.0040)),
    )

    private fun projectToScreen(mapView: MapView, map: MapLibreMap, latitude: Double, longitude: Double): Pair<Float, Float> {
        val location = IntArray(2)
        onMain { mapView.getLocationOnScreen(location) }
        val projected = onMain { map.projection.toScreenLocation(LatLng(latitude, longitude)) }
        return (projected.x + location[0]) to (projected.y + location[1])
    }

    private fun insideRasterExtent(latitude: Double, longitude: Double): Boolean =
        longitude in RASTER_WEST..RASTER_EAST && latitude in RASTER_SOUTH..RASTER_NORTH

    /** «Объекты» → «Точки наблюдения» → «Карта»: the product screen that draws saved points. */
    private fun openPointsBrowserMap(): Boolean {
        if (!tap("Объекты")) return false
        if (!tap("Точки наблюдения")) return false
        if (eventually(3_000) { findAccessibilityNode { it.text?.toString() == "Карта" } != null }) {
            tap("Карта")
        }
        return eventually(15_000) { findAccessibilityNode { node -> descriptionOf(node).startsWith("Точка ") } != null }
    }

    // ------------------------------------------------------------------ fixtures

    private data class Fixtures(val xyzZ15: String, val pmtilesWriterHeader: File, val pmtilesPngHeader: File)

    /**
     * Copies the adb-pushed PoC input from the app's external files directory into the app-private
     * `files/map-poc` tree used by the debug-only local profiles.
     */
    private fun prepareFixtures(): Fixtures {
        val input = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "poc-input")
        assertTrue("PoC input directory is missing: $input", input.isDirectory)
        val target = File(instrumentation.targetContext.filesDir, "map-poc")
        val xyzZ15 = File(target, "xyz-z15")
        val xyzZ16 = File(target, "xyz-z16")
        copyTree(File(input, "xyz-z15"), xyzZ15)
        copyTree(File(input, "xyz-z16"), xyzZ16)
        val writerHeader = File(target, "raster-z15-writer-header.pmtiles")
        val pngHeader = File(target, "raster-z15-png-header.pmtiles")
        File(input, "cyclosm-raster-z15-v1.pmtiles").copyTo(writerHeader, overwrite = true)
        File(input, "cyclosm-raster-z15-v1-png.pmtiles").copyTo(pngHeader, overwrite = true)
        return Fixtures(xyzZ15.absolutePath, writerHeader, pngHeader)
    }

    private fun copyTree(from: File, to: File) {
        assertTrue("PoC fixture directory is missing: $from", from.isDirectory)
        from.walkTopDown().filter(File::isFile).forEach { file ->
            val destination = File(to, file.relativeTo(from).path)
            check(destination.parentFile?.mkdirs() != false || destination.parentFile!!.isDirectory)
            file.copyTo(destination, overwrite = true)
        }
    }

    // ------------------------------------------------------------------ real imagery fixtures

    private data class ImageryVariant(
        val directory: String,
        val information: String,
        val minZoom: Int,
        val maxZoom: Int,
    )

    /**
     * Copies the adb-pushed imagery pyramids into app-private storage. Every variant is derived from
     * the same native 0.30 m NAIP source over the same area; only the information level differs.
     */
    private fun prepareImageryFixtures(): Map<String, String> {
        val input = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "poc-imagery")
        assertTrue("PoC imagery input is missing: $input", input.isDirectory)
        val target = File(instrumentation.targetContext.filesDir, "map-poc/imagery")
        val roots = mutableMapOf<String, String>()
        for (variant in IMAGERY_VARIANTS) {
            val destination = File(target, variant.directory)
            // Start from a clean tree: a stale tile set from an earlier build must not linger and
            // inflate the counted fixtures.
            if (destination.exists()) destination.deleteRecursively()
            copyTree(File(input, variant.directory), destination)
            roots[variant.directory] = destination.absolutePath
        }
        return roots
    }

    private fun imageryRasterStyle(tileRoot: String, minZoom: Int, maxZoom: Int): String = """    {
      "version": 8,
      "name": "$POC_STYLE_NAME",
      "sources": {
        "$RASTER_SOURCE": {
          "type": "raster",
          "tiles": ["file://${tileRoot.replace('\\', '/')}/{z}/{x}/{y}.jpg"],
          "tileSize": 256,
          "scheme": "xyz",
          "minzoom": $minZoom,
          "maxzoom": $maxZoom,
          "attribution": "USGS NAIP (USDA FSA APFO) 2022, public domain"
        }
      },
      "layers": [
        { "id": "$BACKGROUND_LAYER", "type": "background", "paint": { "background-color": "$BACKGROUND_COLOR_HEX" } },
        { "id": "$RASTER_LAYER", "type": "raster", "source": "$RASTER_SOURCE" }
      ]
    }
    """.trimIndent()

    // ------------------------------------------------------------------ Sentinel-2 fixtures

    private fun prepareSentinelFixtures(): String {
        val input = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "poc-sentinel")
        val source = File(input, "true-color")
        assertTrue("Sentinel-2 PoC input is missing: $source", source.isDirectory)
        val destination = File(instrumentation.targetContext.filesDir, "map-poc/sentinel/true-color")
        if (destination.exists()) destination.deleteRecursively()
        copyTree(source, destination)
        for (zoom in 13..18) {
            assertTrue(
                "Sentinel-2 source level z$zoom is missing",
                File(destination, zoom.toString()).walkTopDown().any(File::isFile),
            )
        }
        return destination.absolutePath
    }

    private fun sentinelRasterStyle(tileRoot: String): String = """
    {
      "version": 8,
      "name": "$POC_STYLE_NAME",
      "sources": {
        "$RASTER_SOURCE": {
          "type": "raster",
          "tiles": ["file://${tileRoot.replace('\\', '/')}/{z}/{x}/{y}.png"],
          "tileSize": 256,
          "scheme": "xyz",
          "minzoom": 13,
          "maxzoom": 18,
          "attribution": "Copernicus Sentinel-2 L2A 2026-07-18 via Earth Search"
        }
      },
      "layers": [
        { "id": "$BACKGROUND_LAYER", "type": "background", "paint": { "background-color": "$BACKGROUND_COLOR_HEX" } },
        { "id": "$RASTER_LAYER", "type": "raster", "source": "$RASTER_SOURCE", "paint": { "raster-resampling": "linear" } }
      ]
    }
    """.trimIndent()

    /** Consumes the product map's one-shot first-fix recenter without weakening the final assertion. */
    private fun settleSentinelCamera(mapView: MapView, map: MapLibreMap, zoom: Double, label: String) {
        settleCameraAt(mapView, map, sentinelCenter, zoom, label)
    }

    /** Moves the camera and retries while the product map's one-shot first-fix recenter is pending. */
    private fun settleCameraAt(
        mapView: MapView,
        map: MapLibreMap,
        position: Pair<Double, Double>,
        zoom: Double,
        label: String = "camera",
    ) {
        repeat(3) {
            moveTo(mapView, map, position, zoom)
            SystemClock.sleep(1_200)
            val settled = onMain {
                val camera = map.cameraPosition
                val target = camera.target
                abs(camera.zoom - zoom) <= 0.05 && target != null &&
                    abs(target.latitude - position.first) < 0.00001 && abs(target.longitude - position.second) < 0.00001
            }
            if (settled) return
        }
        assertEquals("$label camera did not settle after first-fix recenter", zoom, onMain { map.cameraPosition.zoom }, 0.05)
    }

    /**
     * Settles the camera at [position]/[zoom] and reinstalls the PoC style right before measuring:
     * the product screen may reinstall its own style while the camera is settling, and the
     * measurement must observe the PoC style rather than a half-installed product one.
     */
    private fun measureSentinelArea(
        mapView: MapView,
        map: MapLibreMap,
        archive: File,
        position: Pair<Double, Double>,
        zoom: Double,
        evidence: MutableList<String>,
        label: String,
    ): Double {
        settleCameraAt(mapView, map, position, zoom, label)
        applyRasterStyle(map, sentinelAreaRasterStyle(archive), RASTER_SOURCE)
        return measureRasterCoverage(mapView, map, position, zoom, evidence, label, SENTINEL_AREA_BACKGROUND)
    }

    // ------------------------------------------------------------------ Sentinel area fixture

    /** Copies the whole-area Sentinel-2 raster PMTiles archive into the app-private PoC tree. */
    private fun prepareSentinelAreaFixture(): File {
        val input = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "poc-sentinel")
        val source = File(input, "sentinel-area-z10-13.pmtiles")
        assertTrue("Sentinel area PoC input is missing: $source", source.isFile)
        val destination = File(instrumentation.targetContext.filesDir, "map-poc/sentinel-area-z10-13.pmtiles")
        check(destination.parentFile?.mkdirs() != false || destination.parentFile!!.isDirectory)
        source.copyTo(destination, overwrite = true)
        return destination
    }

    private fun sentinelAreaRasterStyle(archive: File): String = """
    {
      "version": 8,
      "name": "$POC_STYLE_NAME",
      "sources": {
        "$RASTER_SOURCE": {
          "type": "raster",
          "url": "pmtiles://file://${archive.absolutePath.replace('\\', '/')}",
          "tileSize": 256,
          "minzoom": 10,
          "maxzoom": 13,
          "attribution": "Contains modified Copernicus Sentinel data 2026"
        }
      },
      "layers": [
        { "id": "$BACKGROUND_LAYER", "type": "background", "paint": { "background-color": "$SENTINEL_AREA_BACKGROUND_HEX" } },
        { "id": "$RASTER_LAYER", "type": "raster", "source": "$RASTER_SOURCE", "paint": { "raster-resampling": "linear" } }
      ]
    }
    """.trimIndent()

    // ------------------------------------------------------------------ sharpness fixtures

    private data class SharpnessVariant(
        val directory: String,
        val note: String,
        val minZoom: Int,
        val maxZoom: Int,
        val encoding: String,
        val displayZooms: List<Double>,
        val resampling: List<String>,
        val tileSize: Int = 256,
    )

    private fun prepareSharpnessFixtures(): Map<String, String> {
        val input = File(requireNotNull(instrumentation.targetContext.getExternalFilesDir(null)), "poc-sharp")
        assertTrue("PoC sharpness input is missing: $input", input.isDirectory)
        val target = File(instrumentation.targetContext.filesDir, "map-poc/sharp")
        val roots = mutableMapOf<String, String>()
        for (variant in SHARPNESS_VARIANTS) {
            val destination = File(target, variant.directory)
            if (destination.exists()) destination.deleteRecursively()
            copyTree(File(input, variant.directory), destination)
            roots[variant.directory] = destination.absolutePath
        }
        return roots
    }

    private fun sharpnessRasterStyle(
        tileRoot: String,
        minZoom: Int,
        maxZoom: Int,
        encoding: String,
        resampling: String,
        tileSize: Int,
    ): String = """
    {
      "version": 8,
      "name": "$POC_STYLE_NAME",
      "sources": {
        "$RASTER_SOURCE": {
          "type": "raster",
          "tiles": ["file://${tileRoot.replace('\\', '/')}/{z}/{x}/{y}.$encoding"],
          "tileSize": $tileSize,
          "scheme": "xyz",
          "minzoom": $minZoom,
          "maxzoom": $maxZoom,
          "attribution": "USGS NAIP (USDA FSA APFO) 2022, public domain"
        }
      },
      "layers": [
        { "id": "$BACKGROUND_LAYER", "type": "background", "paint": { "background-color": "$BACKGROUND_COLOR_HEX" } },
        { "id": "$RASTER_LAYER", "type": "raster", "source": "$RASTER_SOURCE", "paint": { "raster-resampling": "$resampling" } }
      ]
    }
    """.trimIndent()

    /** Ground metres across the map viewport: identical for every variant at the same zoom. */
    private fun groundMetersPerScreenWidth(mapView: MapView, map: MapLibreMap): String {
        val camera = onMain { map.cameraPosition }
        val width = onMain { mapView.width }
        val target = camera.target ?: return "unknown"
        val ground = 156543.03392804097 * cos(Math.toRadians(target.latitude)) / Math.pow(2.0, camera.zoom)
        return "%.1f".format(ground * width)
    }

    // ------------------------------------------------------------------ styles

    private fun localXyzRasterStyle(tileRoot: String): String = """
    {
      "version": 8,
      "name": "$POC_STYLE_NAME",
      "sources": {
        "$RASTER_SOURCE": {
          "type": "raster",
          "tiles": ["file://${tileRoot.replace('\\', '/')}/{z}/{x}/{y}.png"],
          "tileSize": 256,
          "scheme": "xyz",
          "minzoom": 15,
          "maxzoom": 15,
          "attribution": "CyclOSM / OpenStreetMap contributors"
        }
      },
      "layers": [
        { "id": "$BACKGROUND_LAYER", "type": "background", "paint": { "background-color": "$BACKGROUND_COLOR_HEX" } },
        { "id": "$RASTER_LAYER", "type": "raster", "source": "$RASTER_SOURCE" }
      ]
    }
    """.trimIndent()

    private fun pmtilesRasterStyle(archiveUrl: String): String = """
    {
      "version": 8,
      "name": "$POC_STYLE_NAME",
      "sources": {
        "$RASTER_SOURCE": {
          "type": "raster",
          "url": "$archiveUrl",
          "tileSize": 256,
          "minzoom": 15,
          "maxzoom": 15,
          "attribution": "CyclOSM / OpenStreetMap contributors"
        }
      },
      "layers": [
        { "id": "$BACKGROUND_LAYER", "type": "background", "paint": { "background-color": "$BACKGROUND_COLOR_HEX" } },
        { "id": "$RASTER_LAYER", "type": "raster", "source": "$RASTER_SOURCE" }
      ]
    }
    """.trimIndent()

    /** Second form of the same archive integration: an explicit z/x/y template on `pmtiles://`. */    private fun pmtilesRasterTileTemplateStyle(archivePath: String): String = """
    {
      "version": 8,
      "name": "$POC_STYLE_NAME",
      "sources": {
        "$RASTER_SOURCE": {
          "type": "raster",
          "tiles": ["pmtiles://file://${archivePath.replace('\\', '/')}/{z}/{x}/{y}.png"],
          "tileSize": 256,
          "scheme": "xyz",
          "minzoom": 15,
          "maxzoom": 15,
          "attribution": "CyclOSM / OpenStreetMap contributors"
        }
      },
      "layers": [
        { "id": "$BACKGROUND_LAYER", "type": "background", "paint": { "background-color": "$BACKGROUND_COLOR_HEX" } },
        { "id": "$RASTER_LAYER", "type": "raster", "source": "$RASTER_SOURCE" }
      ]
    }
    """.trimIndent()

    /**
     * Applies the PoC style and waits until the raster source exists. The product screen installs
     * its own style when its state changes, so the application is retried and retries are reported
     * in the evidence instead of being hidden.
     */
    private fun applyRasterStyle(map: MapLibreMap, styleJson: String, sourceId: String) {
        for (attempt in 1..3) {
            map.setStyleAndAwait(styleJson)
            if (eventuallyOnMain(5_000) { map.style?.getSource(sourceId) != null }) {
                if (attempt > 1) writeEvidence("style-application.txt", listOf("raster style needed $attempt attempts"))
                return
            }
        }
        throw AssertionError("The raster style did not stay installed in the running map")
    }

    // ------------------------------------------------------------------ raster measurement

    /**
     * Returns the fraction of the sampled map area that is NOT the PoC style's background colour,
     * i.e. the pixels that only the offline raster can have painted: the PoC style contains exactly
     * two layers — that background and the raster — so the number is unambiguous as long as the PoC
     * style is still the one installed in the map, which is asserted here.
     *
     * A second capture after hiding the raster layer is recorded as a cross-check. The camera is
     * given time to load tiles and the capture is repeated until two consecutive screenshots agree,
     * so a stale frame cannot be mistaken for a rendered basemap.
     */
    private fun measureRasterCoverage(
        mapView: MapView,
        map: MapLibreMap,
        position: Pair<Double, Double>,
        zoom: Double,
        evidence: MutableList<String>,
        label: String,
        background: Int = BACKGROUND_COLOR,
    ): Double {
        val started = System.nanoTime()
        val cameraStarted = System.nanoTime()
        map.moveAndAwait(position.first, position.second, zoom)
        awaitFrames(mapView, 2)
        val cameraSeconds = seconds(cameraStarted)
        SystemClock.sleep(1_500)
        val region = sampleRegion(mapView)
        val stable = captureStable(region)
        // Style identity: the PoC style is the only style in this session that has exactly these two
        // layers, so their presence rules out the product style having replaced it mid-measurement.
        val layerIds = onMain { map.style?.layers?.map { it.id }?.filterNot { it.startsWith("org.maplibre.annotations") }?.sorted() }
        assertEquals(
            "The product style replaced the PoC style before measuring ($label)",
            listOf(BACKGROUND_LAYER, RASTER_LAYER).sorted(),
            layerIds,
        )
        val rasterPixels = backgroundDifferingFraction(stable, region, background)
        setRasterVisibility(map, false)
        SystemClock.sleep(900)
        val hidden = captureStable(region)
        setRasterVisibility(map, true)
        SystemClock.sleep(400)
        val toggleDelta = differingFraction(stable, hidden, region)
        evidence += "$label cameraIdle=${cameraSeconds}s measured=${seconds(started)}s rasterPixels=${"%.3f".format(rasterPixels)} " +
            "hiddenLayerDelta=${"%.3f".format(toggleDelta)} layers=${layerIds?.joinToString()}"
        return rasterPixels
    }

    /**
     * Seconds from camera-idle until the raster owns the sampled area — the practical "how long does
     * the offline basemap take to appear after a zoom/pan" observation of this PoC.
     */
    private fun secondsToFirstRasterFrame(
        mapView: MapView,
        map: MapLibreMap,
        position: Pair<Double, Double>,
        zoom: Double,
    ): String {
        map.moveAndAwait(position.first, position.second, zoom)
        val started = System.nanoTime()
        while (System.nanoTime() - started < TimeUnit.SECONDS.toNanos(15)) {
            val region = sampleRegion(mapView)
            if (backgroundDifferingFraction(captureScreen(), region) >= RASTER_COVERED_MIN) {
                return seconds(started)
            }
            SystemClock.sleep(200)
        }
        return ">15.000"
    }

    /** Repeated captures until the screen stops changing, so tile loading cannot be missed. */
    private fun captureStable(region: Rect): Bitmap {
        var previous = captureScreen()
        repeat(8) {
            SystemClock.sleep(400)
            val next = captureScreen()
            if (differingFraction(previous, next, region) < 0.005) return next
            previous = next
        }
        return previous
    }

    /** Pixels of the sampled region that are not the PoC background colour. */
    private fun backgroundDifferingFraction(bitmap: Bitmap, region: Rect, background: Int = BACKGROUND_COLOR): Double {
        var sampled = 0
        var painted = 0
        val right = region.right.coerceAtMost(bitmap.width)
        val bottom = region.bottom.coerceAtMost(bitmap.height)
        var y = region.top.coerceIn(0, maxOf(0, bottom - 1))
        while (y < bottom - 1) {
            var x = region.left.coerceIn(0, maxOf(0, right - 1))
            while (x < right - 1) {
                val color = bitmap.getPixel(x, y)
                if (
                    abs((color and 0xFF) - (background and 0xFF)) > 24 ||
                    abs((color shr 8 and 0xFF) - (background shr 8 and 0xFF)) > 24 ||
                    abs((color shr 16 and 0xFF) - (background shr 16 and 0xFF)) > 24
                ) {
                    painted++
                }
                sampled++
                x += 6
            }
            y += 6
        }
        return if (sampled == 0) 0.0 else painted.toDouble() / sampled.toDouble()
    }

    private fun setRasterVisibility(map: MapLibreMap, visible: Boolean) {
        onMain {
            (map.style?.getLayer(RASTER_LAYER) as? RasterLayer)?.setProperties(
                PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE),
            )
        }
    }

    /** The part of the screen that belongs to the map and to neither the panel nor the crosshair. */
    private fun sampleRegion(mapView: MapView): Rect {
        val location = IntArray(2)
        onMain { mapView.getLocationOnScreen(location) }
        val width = onMain { mapView.width }
        val height = onMain { mapView.height }
        val screen = captureScreen()
        return Rect(
            (location[0] + (width * 0.10f).toInt()).coerceIn(0, screen.width - 1),
            (location[1] + (height * 0.22f).toInt()).coerceIn(0, screen.height - 1),
            (location[0] + (width * 0.90f).toInt()).coerceIn(1, screen.width),
            (location[1] + (height * 0.62f).toInt()).coerceIn(1, screen.height),
        )
    }

    private fun differingFraction(first: Bitmap, second: Bitmap, region: Rect): Double {
        var sampled = 0
        var different = 0
        // Screenshots taken in different device states can differ in size; never read outside either one.
        val right = minOf(region.right, first.width, second.width)
        val bottom = minOf(region.bottom, first.height, second.height)
        val left0 = region.left.coerceIn(0, maxOf(0, right - 1))
        val top0 = region.top.coerceIn(0, maxOf(0, bottom - 1))
        var y = top0
        while (y < bottom - 1) {
            var x = left0
            while (x < right - 1) {
                val left = first.getPixel(x, y)
                val right1 = second.getPixel(x, y)
                if (
                    abs((left and 0xFF) - (right1 and 0xFF)) > 24 ||
                    abs((left shr 8 and 0xFF) - (right1 shr 8 and 0xFF)) > 24 ||
                    abs((left shr 16 and 0xFF) - (right1 shr 16 and 0xFF)) > 24
                ) {
                    different++
                }
                sampled++
                x += 6
            }
            y += 6
        }
        return if (sampled == 0) 0.0 else different.toDouble() / sampled.toDouble()
    }

    // ------------------------------------------------------------------ UiAutomation (no new dependency)

    private fun descriptionOf(node: AccessibilityNodeInfo): String = node.contentDescription?.toString().orEmpty()

    private fun findAccessibilityNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        return search(root, predicate, 0)
    }

    private fun search(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
        depth: Int,
    ): AccessibilityNodeInfo? {
        if (depth > 24) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            search(child, predicate, depth + 1)?.let { return it }
        }
        return null
    }

    private fun tap(text: String): Boolean {
        val found = eventually(6_000) {
            findAccessibilityNode { descriptionOf(it) == text || it.text?.toString() == text } != null
        }
        if (!found) return false
        val node = findAccessibilityNode { descriptionOf(it) == text || it.text?.toString() == text } ?: return false
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 60, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.uiAutomation.injectInputEvent(down, true)
        SystemClock.sleep(60)
        instrumentation.uiAutomation.injectInputEvent(up, true)
        down.recycle()
        up.recycle()
        SystemClock.sleep(1_200)
        return true
    }

    // ------------------------------------------------------------------ map plumbing

    private fun ActivityScenario<MainActivity>.findMapView(): MapView {
        var result: MapView? = null
        // The «Точки» screen can keep the field map attached behind its own map, so the visible
        // map is the one that must receive the PoC style.
        assertTrue("The current screen did not contain a visible MapView", eventually(25_000) {
            onActivity { activity -> result = activity.window.decorView.findShownMapView() }
            result != null
        })
        return checkNotNull(result)
    }

    private fun MapView.awaitMap(): MapLibreMap {
        val latch = CountDownLatch(1)
        lateinit var result: MapLibreMap
        onMain { getMapAsync { map -> result = map; latch.countDown() } }
        assertTrue("MapLibreMap was not ready", latch.await(20, TimeUnit.SECONDS))
        return result
    }

    private fun MapLibreMap.setStyleAndAwait(styleJson: String) {
        val latch = CountDownLatch(1)
        onMain { setStyle(Style.Builder().fromJson(styleJson)) { latch.countDown() } }
        assertTrue("The raster style was not loaded", latch.await(20, TimeUnit.SECONDS))
    }

    private fun MapLibreMap.moveAndAwait(latitude: Double, longitude: Double, zoom: Double) {
        val latch = CountDownLatch(1)
        val listener = MapLibreMap.OnCameraIdleListener {
            val camera = cameraPosition
            val target = camera.target
            if (target != null && abs(camera.zoom - zoom) < 0.05 &&
                abs(target.latitude - latitude) < 0.00001 && abs(target.longitude - longitude) < 0.00001
            ) {
                latch.countDown()
            }
        }
        onMain {
            addOnCameraIdleListener(listener)
            moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom))
        }
        assertTrue("The map did not become idle at z$zoom", latch.await(20, TimeUnit.SECONDS))
        onMain { removeOnCameraIdleListener(listener) }
    }

    private fun moveTo(mapView: MapView, map: MapLibreMap, position: Pair<Double, Double>, zoom: Double) {
        map.moveAndAwait(position.first, position.second, zoom)
        awaitFrames(mapView, 2)
    }

    private fun awaitFrames(mapView: MapView, frames: Int) {
        var remaining = frames
        val latch = CountDownLatch(1)
        val listener = MapView.OnDidFinishRenderingFrameListener { _, _, _ ->
            remaining--
            if (remaining <= 0) latch.countDown()
        }
        onMain { mapView.addOnDidFinishRenderingFrameListener(listener) }
        latch.await(10, TimeUnit.SECONDS)
        onMain { mapView.removeOnDidFinishRenderingFrameListener(listener) }
    }

    private fun assertAnyRendered(mapView: MapView, map: MapLibreMap, vararg layers: String) {
        fun hasFeatures(): Boolean = map.queryRenderedFeatures(
            RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()),
            *layers,
        ).isNotEmpty()
        assertTrue("No feature rendered in ${layers.joinToString()}", eventuallyOnMain(20_000) { hasFeatures() })
    }

    private fun captureScreen(): Bitmap = instrumentation.uiAutomation.takeScreenshot()

    private fun captureScreenshot(name: String) {
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "map-poc/raster-poc")
        check(directory.mkdirs() || directory.isDirectory)
        FileOutputStream(File(directory, "$name.png")).use { output ->
            captureScreen().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun writeEvidence(name: String, lines: List<String>) {
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "map-poc/raster-poc")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, name).writeText(lines.joinToString("\n") + "\n")
    }

    /** Polls on the instrumentation thread; use [eventuallyOnMain] for map state that needs main. */
    private fun eventually(timeoutMillis: Long, predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (System.nanoTime() < deadline) {
            if (predicate()) return true
            SystemClock.sleep(150)
        }
        return false
    }

    private fun eventuallyOnMain(timeoutMillis: Long, predicate: () -> Boolean): Boolean =
        eventually(timeoutMillis) { onMain { predicate() } }

    private fun seconds(started: Long): String = "%.3f".format((System.nanoTime() - started) / 1_000_000_000.0)

    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask(block)
        instrumentation.runOnMainSync(task)
        return task.get()
    }

    private fun assumePoc() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeRasterPoc") == "true")
        assertEquals("org.beesearch.app.dev", instrumentation.targetContext.packageName)
        assertTrue("Raster basemap PoC must run only in a debug build", BuildConfig.DEBUG)
    }

    private fun assumeImageryPoc() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeImageryPoc") == "true")
        assertEquals("org.beesearch.app.dev", instrumentation.targetContext.packageName)
        assertTrue("Imagery PoC must run only in a debug build", BuildConfig.DEBUG)
    }

    private fun assumeSharpnessPoc() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeSharpnessPoc") == "true")
        assertEquals("org.beesearch.app.dev", instrumentation.targetContext.packageName)
        assertTrue("Sharpness PoC must run only in a debug build", BuildConfig.DEBUG)
    }

    private fun assumeSentinelPoc() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeSentinelPoc") == "true")
        assertEquals("org.beesearch.app.dev", instrumentation.targetContext.packageName)
        assertTrue("Sentinel-2 PoC must run only in a debug build", BuildConfig.DEBUG)
    }

    private fun assumeSentinelAreaPoc() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("beeSentinelAreaPoc") == "true")
        assertEquals("org.beesearch.app.dev", instrumentation.targetContext.packageName)
        assertTrue("Sentinel area PoC must run only in a debug build", BuildConfig.DEBUG)
    }

    private companion object {
        const val RASTER_SOURCE = "poc-offline-raster"
        const val RASTER_LAYER = "poc-offline-raster-layer"
        const val BACKGROUND_LAYER = "poc-offline-background"
        const val POC_STYLE_NAME = "Offline raster PoC (file basemap)"

        /** Deliberately unmistakable background: any other colour can only come from the raster. */
        const val BACKGROUND_COLOR_HEX = "#101014"
        const val BACKGROUND_COLOR = 0xFF101014.toInt()

        /** A raster that covers the sampled area; the app overlays alone stay far below this. */
        const val RASTER_COVERED_MIN = 0.50

        /** Airborne imagery is dark forest here, so a lower bound with the same meaning is used. */
        const val IMAGERY_COVERED_MIN = 0.30

        /**
         * Real imagery variants, all derived from one native 0.30 m NAIP source image over one area.
         * `maxZoom` is the variant's own level: above it the map only overzooms the same pixels.
         */
        val IMAGERY_VARIANTS = listOf(
            ImageryVariant("native-030", "native 0.30 m/px", 15, 19),
            ImageryVariant("res-050", "0.50 m/px downsampled", 15, 18),
            ImageryVariant("res-100", "1.00 m/px downsampled", 15, 17),
            ImageryVariant("res-200", "2.00 m/px downsampled", 15, 16),
        )

        /** Same camera and same display zooms for every variant. */
        val IMAGERY_DISPLAY_ZOOMS = listOf(16.0, 17.0, 18.0, 19.0)

        /** Owner-review zooms; z18 exists only as a source level for the Samsung z+1 request. */
        val SENTINEL_DISPLAY_ZOOMS = listOf(13.0, 14.0, 15.0, 16.0, 17.0)

        /** Whole-area Sentinel overview: real levels of the area package, no overzoom in this range. */
        val SENTINEL_AREA_ZOOMS = listOf(10.0, 11.0, 12.0, 13.0)

        /** A drawn basemap covers most of the sampled map area; the package has no transparency. */
        const val SENTINEL_AREA_MIN = 0.50

        /** Light background so the dark Sentinel imagery is unambiguous in the drawn-pixel metric. */
        const val SENTINEL_AREA_BACKGROUND_HEX = "#f0f0f0"
        const val SENTINEL_AREA_BACKGROUND = 0xFFF0F0F0.toInt()

        /** Inset corners of the area rectangle: west 42.2056, south 55.7059, east 43.0389, north 56.4945. */
        val SENTINEL_AREA_PROBES = listOf(
            "nw" to (56.4645 to 42.2356),
            "ne" to (56.4645 to 43.0089),
            "sw" to (55.7359 to 42.2356),
            "se" to (55.7359 to 43.0089),
        )

        /** Working field place with forest, fields, a river and a settlement, inside the area. */
        val SENTINEL_AREA_REFERENCE = 56.1914 to 42.7423

        /** Just inside the package boundary: the frame shows both imagery and the package edge. */
        val SENTINEL_AREA_EDGE = 56.5200 to 42.6223

        /** Well beyond the package boundary: no imagery may be painted here. */
        val SENTINEL_AREA_OUTSIDE = 56.6600 to 42.6223

        /**
         * Sharpness diagnostic variants, all cut from the same 250 m window of the same NAIP source.
         * `encoding` selects the tile file extension; `resampling` sets MapLibre's raster-resampling.
         */
        val SHARPNESS_VARIANTS = listOf(
            SharpnessVariant(
                "corr-native-z18-lanczos-png",
                "native 0.30 single Lanczos on the z18 grid, lossless PNG",
                15, 18, "png", listOf(18.0, 19.0), listOf("linear"),
            ),
            SharpnessVariant(
                "corr-native-z19-lanczos-png",
                "native 0.30 single Lanczos on the z19 grid, lossless PNG",
                15, 19, "png", listOf(18.0, 19.0), listOf("linear", "nearest"),
            ),
            SharpnessVariant(
                "corr-native-z19-bilinear-jpg90",
                "previous build: bilinear placement + JPEG q90 on the z19 grid",
                18, 19, "jpg", listOf(18.0, 19.0), listOf("linear"),
            ),
            SharpnessVariant(
                "corr-res050-z18-lanczos-png",
                "0.50 m on the z18 grid, Lanczos, lossless PNG",
                15, 18, "png", listOf(18.0, 19.0), listOf("linear"),
            ),
            SharpnessVariant(
                "syn-pattern-z19",
                "synthetic 16 px bar pattern: measures the real display scale and contrast",
                19, 19, "png", listOf(19.0), listOf("linear", "nearest"),
            ),
            SharpnessVariant(
                "syn-pattern-z18",
                "synthetic 16 px bar pattern on the z18 grid",
                18, 18, "png", listOf(18.0), listOf("linear"),
            ),
            SharpnessVariant(
                "syn-band-20m-z19",
                "synthetic 20 m wide band: measures ground metres per screen pixel at z19",
                19, 19, "png", listOf(19.0), listOf("linear"),
            ),
            SharpnessVariant(
                "syn-band-20m-z18",
                "synthetic 20 m wide band: measures ground metres per screen pixel at z18",
                18, 18, "png", listOf(18.0), listOf("linear"),
            ),
            SharpnessVariant(
                "rev-native-z20-lanczos-png",
                "review: 0.30 m information shipped up to z20 so the renderer finds the level it asks for",
                18, 20, "png", listOf(18.0, 19.0), listOf("linear"),
            ),
            SharpnessVariant(
                "rev-res050-z19-lanczos-png",
                "review: 0.50 m information on the z19 grid",
                18, 19, "png", listOf(18.0), listOf("linear"),
            ),
            SharpnessVariant(
                "syn-band-20m-z20",
                "same 20 m band shipped at z20: does the finer package remove the 2x display magnification",
                20, 20, "png", listOf(19.0), listOf("linear"),
            ),
            SharpnessVariant(
                "syn-band-20m-z21",
                "same 20 m band shipped at z21",
                21, 21, "png", listOf(19.0), listOf("linear"),
            ),
            SharpnessVariant(
                "syn-band-20m-z19-ts512",
                "same band pixels declared with tileSize 512: isolates the tileSize effect",
                19, 19, "png", listOf(19.0), listOf("linear"), tileSize = 512,
            ),
            SharpnessVariant(
                "syn-band-20m-z19-ts128",
                "same band pixels declared with tileSize 128: isolates the tileSize effect",
                19, 19, "png", listOf(19.0), listOf("linear"), tileSize = 128,
            ),
        )

        /** Pans towards the dataset edges only have to show that the raster followed the camera. */
        const val RASTER_PAN_MIN = 0.15

        /** Outside the dataset only the app overlays and the background remain. */
        const val RASTER_OUTSIDE_MAX = 0.10

        /** Tolerance for a 48 dp marker whose centre is placed on the projected coordinate. */
        const val MARKER_TOLERANCE_PX = 24f

        /** Search window (screen pixels) around the projected marker position for its own tone. */
        const val TONE_WINDOW_PX = 120f

        /** Markers closer than this to another marker are skipped: their discs merge on screen. */
        const val ISOLATED_MARKER_M = 30.0

        const val METERS_PER_DEGREE_LAT = 111_320.0

        val PANS = listOf(
            "north-west" to (56.2040 to 42.7300),
            "north-east" to (56.2040 to 42.7550),
            "south-west" to (56.1810 to 42.7300),
            "south-east" to (56.1810 to 42.7550),
        )

        /** Extent of the PoC raster dataset (z15 mosaic covering the DEV territory). */
        const val RASTER_WEST = 42.725830
        const val RASTER_SOUTH = 56.176139
        const val RASTER_EAST = 42.758789
        const val RASTER_NORTH = 56.206704
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

/** The MapView the user can actually see: a background screen may keep its own map attached. */
private fun View.findShownMapView(): MapView? {
    if (this is MapView) return if (isShown && width > 0 && height > 0) this else null
    if (this !is ViewGroup) return null
    for (index in 0 until childCount) {
        getChildAt(index).findShownMapView()?.let { return it }
    }
    return null
}
