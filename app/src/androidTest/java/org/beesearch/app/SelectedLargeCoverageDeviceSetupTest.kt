package org.beesearch.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in DEV-only recovery of the exact bbox already selected and exported by the user.
 * It does not import or activate a map package; that remains a real UI flow.
 */
@RunWith(AndroidJUnit4::class)
class SelectedLargeCoverageDeviceSetupTest {
    @Test
    fun persistExportedUserSelection() = runBlocking {
        assumeTrue(
            "Large coverage setup is opt-in",
            InstrumentationRegistry.getArguments().getString(SETUP_ARGUMENT) == "true",
        )
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(DEVELOPMENT_PACKAGE, appContext.packageName)
        assertTrue("Coverage setup must run only in a debug build", BuildConfig.DEBUG)

        val container = (appContext.applicationContext as BeeSearchApplication).container
        val territoryId = requireNotNull(container.settingsRepository.getSettings().currentTerritoryId) {
            "Bee Search DEV has no current Territory"
        }
        val coverage = listOf(
            MapCoverageFragment(
                MapGeoBounds(
                    west = 42.2055994,
                    south = 55.7058541,
                    east = 43.0389210,
                    north = 56.4944621,
                ),
            ),
        )

        val territory = requireNotNull(container.territoryRepository.getTerritory(territoryId)) {
            "Bee Search DEV has no current Territory"
        }

        container.mapAreaStore.saveBounds(territoryId, coverage.map { it.bounds }, territory.name)

        val stored = container.mapAreaStore.load(territoryId, territory.name) as MapAreaReadResult.Present
        assertEquals(coverage.map { it.bounds }, stored.area.bounds)
    }

    private companion object {
        const val SETUP_ARGUMENT = "beeSelectedLargeCoverageSetup"
        const val DEVELOPMENT_PACKAGE = "org.beesearch.app.dev"
    }
}
