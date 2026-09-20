package org.beesearch.app

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.MapPackageImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Explicit development-only bootstrap for manual device review after a test deployment.
 *
 * It is inert during the ordinary connected test suite. tools/dev-bootstrap.ps1 is the
 * only supported caller: it clears and stages files for org.beesearch.app.dev, then
 * invokes this method with beeSearchDevBootstrap=true.
 */
@RunWith(AndroidJUnit4::class)
class DevBootstrapInstrumentedTest {
    @Test
    fun bootstrapDevelopmentState() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Development bootstrap is opt-in",
            InstrumentationRegistry.getArguments().getString(BOOTSTRAP_ARGUMENT) == "true",
        )

        val appContext = instrumentation.targetContext
        assertEquals(DEVELOPMENT_PACKAGE, appContext.packageName)
        assertTrue("Bootstrap must run only in a debug build", BuildConfig.DEBUG)

        val fixtureDirectory = File(appContext.filesDir, FIXTURE_DIRECTORY)
        val manifest = File(fixtureDirectory, MANIFEST_FILE_NAME)
        val pmtiles = File(fixtureDirectory, PMTILES_FILE_NAME)
        assertTrue("Missing staged D065 manifest", manifest.isFile && manifest.length() > 0)
        assertTrue("Missing staged PMTiles fixture", pmtiles.isFile && pmtiles.length() > 0)

        val container = (appContext.applicationContext as BeeSearchApplication).container
        val observer = container.observerRepository.createObserver(
            code = "DEV-OBS",
            lastName = "Тестов",
            firstName = "Наблюдатель",
            middleName = null,
            contact = "Только для Bee Search DEV",
        )
        val territory = container.territoryRepository.createTerritory(
            code = "DEV-BENCH",
            name = "Тестовая территория DEV",
            region = "Тестовый регион",
            district = "Тестовый район",
        )
        container.settingsRepository.setCurrentObserverId(observer.id)
        container.settingsRepository.setCurrentTerritoryId(territory.id)

        val coverage = listOf(
            MapCoverageFragment(
                MapGeoBounds(
                    north = 56.40,
                    east = 42.65,
                    south = 56.20,
                    west = 42.35,
                ),
            ),
        )
        container.mapAreaStore.create(territory.id, territory.name, coverage.map { it.bounds })
        val import = container.mapPackageStore.import(
            territoryId = territory.id,
            desiredCoverage = coverage,
            manifestUri = Uri.fromFile(manifest),
            pmtilesUri = Uri.fromFile(pmtiles),
        )
        assertTrue("D065 fixture was not activated: $import", import is MapPackageImportResult.Activated)
        assertTrue(
            "Activated package is not Ready",
            container.mapPackageStore.loadActive(territory.id, coverage) is MapPackageAvailability.Ready,
        )

        val point = container.observationRepository.createObservationPointWithFirstBee(
            NewObservationPoint(
                territoryId = territory.id,
                observerId = observer.id,
                code = "DEV-POINT",
                latitude = 56.30,
                longitude = 42.50,
                gpsLatitude = 56.30,
                gpsLongitude = 42.50,
                gpsAccuracyM = 5.0,
            ),
            markColor = "WHITE",
            markPosition = MarkPosition.THORAX,
        )
        // One Bee per real mark, so the prepared DEV Point shows all ten new
        // marking variants in a mixed-state list.
        org.beesearch.app.domain.model.BeeMarkCatalog.supportedCombinations
            .drop(1)
            .forEach { (color, position) ->
                container.observationRepository.addBee(point.id, color, position)
            }
        val preparedBees = container.observationRepository.observeBees(point.id).first()
        assertEquals(10, preparedBees.size)
        container.observationRepository.startInitialGroupRelease(point.id)
        val returnedBees = preparedBees.take(5)
        returnedBees.forEach { bee ->
            container.observationRepository.registerBeeReturn(bee.id)
        }
        // Keep a long mixed-state list for real device review: five first-cycle
        // flyers, three bees at the point, and two repeat flights.
        returnedBees.take(2).forEach { bee ->
            container.observationRepository.startNextFlight(bee.id)
        }

        val settings = container.settingsRepository.getSettings()
        assertEquals(territory.id, settings.currentTerritoryId)
        assertEquals(observer.id, settings.currentObserverId)
        val storedArea = container.mapAreaStore.load(territory.id, territory.name) as MapAreaReadResult.Present
        assertEquals(coverage.map { it.bounds }, storedArea.area.bounds)
        assertNotNull(container.observationRepository.observeActivePoint().first())
        assertEquals(10, container.observationRepository.observeBees(point.id).first().size)
        val flightCycles = container.observationRepository.observeFlightCyclesForPoint(point.id).first()
        assertEquals(12, flightCycles.size)
        assertEquals(7, flightCycles.count { it.returnTime == null })
        assertEquals(5, flightCycles.count { it.returnTime != null })
        assertEquals(2, flightCycles.maxOf { it.sequenceNumber })
    }

    private companion object {
        const val BOOTSTRAP_ARGUMENT = "beeSearchDevBootstrap"
        const val DEVELOPMENT_PACKAGE = "org.beesearch.app.dev"
        const val FIXTURE_DIRECTORY = "dev-bootstrap"
        const val MANIFEST_FILE_NAME = "package.manifest.json"
        const val PMTILES_FILE_NAME = "territory-benchmark-v1.pmtiles"
    }
}
