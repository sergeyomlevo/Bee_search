package org.beesearch.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Clock
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.settings.DataStoreMapAreaStore
import org.beesearch.app.data.local.settings.DataStoreMapPackageStore
import org.beesearch.app.data.local.settings.DataStoreSettingsRepository
import org.beesearch.app.data.location.AndroidLocationProvider
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomObserverRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.usecase.CreateObservationPoint
import org.beesearch.app.domain.weather.WeatherSyncScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup contract proof on the real integration path.
 *
 * The tested unit is [MainViewModel] exactly as the Activity uses it: the production startup inputs
 * (`settingsRepository`, territories, observers, `observeActivePoint`), the production readiness and
 * routing wiring, and the Activity-level effect that reacts to the rendered route. Only the storage
 * locations are isolated — an in-memory Room database and a private DataStore file — so a genuinely
 * clean installation is observable without touching any installed package's data.
 *
 * This is deliberately not a test of `StartupRouter.decide` alone: a correct routing function already
 * proved insufficient for the clean Beta first run.
 */
@RunWith(AndroidJUnit4::class)
class CleanStartupIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database: BeeSearchDatabase =
        Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = storeScope,
        produceFile = { File(context.cacheDir, "clean-startup-${UUID.randomUUID()}.preferences_pb") },
    )
    private val settingsRepository = DataStoreSettingsRepository(dataStore)
    private val territoryRepository = RoomTerritoryRepository(database.territoryDao(), Clock.systemUTC())
    private val observerRepository = RoomObserverRepository(database.observerDao(), Clock.systemUTC())
    private val observationRepository = RoomObservationRepository(
        database = database,
        territoryDao = database.territoryDao(),
        pointDao = database.observationPointDao(),
        observerDao = database.observerDao(),
        beeDao = database.beeDao(),
        cycleDao = database.flightCycleDao(),
        attachmentDao = database.observationPointAttachmentDao(),
        weatherDao = database.observationPointWeatherDao(),
        clock = Clock.systemUTC(),
    )
    private val areaStore = DataStoreMapAreaStore(dataStore)
    private val renderedRoutes = CopyOnWriteArrayList<AppRoute>()

    @After
    fun tearDown() {
        database.close()
        storeScope.cancel()
    }

    private fun newViewModel(): MainViewModel = MainViewModel(
        settingsRepository = settingsRepository,
        territoryRepository = territoryRepository,
        observerRepository = observerRepository,
        observationRepository = observationRepository,
        createObservationPoint = CreateObservationPoint(
            settingsRepository = settingsRepository,
            pointCreator = observationRepository,
            weatherSyncScheduler = WeatherSyncScheduler {},
        ),
        attachmentFileStore = ObservationAttachmentFileStore(context.filesDir, context.cacheDir),
        locationProvider = AndroidLocationProvider(context),
        territoryCoverageDeletion = TerritoryCoverageDeletion(territoryRepository, areaStore),
        mapAreaStore = areaStore,
        mapPackageStore = DataStoreMapPackageStore(context.contentResolver, context.filesDir, dataStore),
    )

    /**
     * Mirrors MainActivity: the running Activity records the route it rendered and reacts to the
     * Initial Setup route it is showing. Anything that replaced Initial Setup after that reaction is
     * part of the observed sequence and fails the assertions below.
     */
    private fun CoroutineScope.mirrorActivityStartup(viewModel: MainViewModel): Job =
        launch(Dispatchers.Main) {
            viewModel.route.collect { route ->
                renderedRoutes += route
                if (route == AppRoute.InitialSetup) viewModel.onAutomaticSetupShown()
            }
        }

    private suspend fun firstUserRoute(viewModel: MainViewModel): AppRoute =
        withTimeout(ROUTE_TIMEOUT_MILLIS) { viewModel.route.first { it != AppRoute.Loading } }

    private suspend fun awaitRoute(viewModel: MainViewModel, expected: AppRoute) {
        withTimeout(ROUTE_TIMEOUT_MILLIS) { viewModel.route.first { it == expected } }
    }

    @Test
    fun cleanInstallationStartsInInitialSetupAndKeepsIt() = runBlocking {
        val viewModel = newViewModel()
        val activity = mirrorActivityStartup(viewModel)
        try {
            val first = firstUserRoute(viewModel)
            val settings = settingsRepository.getSettings()
            assertEquals(
                "clean installation (no observer, no territory, no area, no map, offer open) " +
                    "must start in Initial Setup, sequence was $renderedRoutes; " +
                    "offerHandled=${settings.initialSetupOfferHandled}",
                AppRoute.InitialSetup,
                first,
            )
            withTimeout(ROUTE_TIMEOUT_MILLIS) {
                viewModel.visibleInitialSetup.first { it is InitialSetupState.Ready }
            }
            delay(SETTLE_MILLIS)
            assertEquals(
                "no later effect may replace the checklist with the map, sequence was $renderedRoutes",
                AppRoute.InitialSetup,
                viewModel.route.value,
            )
            assertFalse(
                "showing the checklist is not handling the offer",
                settingsRepository.getSettings().initialSetupOfferHandled,
            )
        } finally {
            activity.cancel()
        }
    }

    @Test
    fun checklistObserverStepReturnsToTheChecklistAfterSaving() = runBlocking {
        val viewModel = newViewModel()
        val activity = mirrorActivityStartup(viewModel)
        try {
            awaitRoute(viewModel, AppRoute.InitialSetup)
            viewModel.openSetupSettings(SetupSettingsSection.OBSERVER)
            awaitRoute(viewModel, AppRoute.Settings)
            assertEquals(SetupSettingsSection.OBSERVER, viewModel.setupSettingsSection.value)

            viewModel.createObserver("OBS", "Иванов", "Иван", "", "")
            awaitRoute(viewModel, AppRoute.InitialSetup)
        } finally {
            activity.cancel()
        }
    }

    @Test
    fun checklistTerritoryStepReturnsToTheChecklistAfterSaving() = runBlocking {
        val viewModel = newViewModel()
        val activity = mirrorActivityStartup(viewModel)
        try {
            awaitRoute(viewModel, AppRoute.InitialSetup)
            viewModel.openSetupSettings(SetupSettingsSection.TERRITORY)
            assertEquals(SetupSettingsSection.TERRITORY, viewModel.setupSettingsSection.value)

            viewModel.createTerritory("LPO", "Лухское полесье", "Владимирская область", "Лухский район")
            awaitRoute(viewModel, AppRoute.InitialSetup)
        } finally {
            activity.cancel()
        }
    }

    @Test
    fun explicitExitAllowsTheOrdinaryMapFlowOnTheNextLaunch() = runBlocking {
        val viewModel = newViewModel()
        val activity = mirrorActivityStartup(viewModel)
        try {
            awaitRoute(viewModel, AppRoute.InitialSetup)
            viewModel.leaveInitialSetup()
            awaitRoute(viewModel, AppRoute.CurrentTerritory)
            assertTrue(
                "the labelled exit handles the offer",
                settingsRepository.getSettings().initialSetupOfferHandled,
            )
        } finally {
            activity.cancel()
        }

        renderedRoutes.clear()
        val relaunched = newViewModel()
        val nextActivity = mirrorActivityStartup(relaunched)
        try {
            assertEquals(AppRoute.CurrentTerritory, firstUserRoute(relaunched))
        } finally {
            nextActivity.cancel()
        }
    }

    @Test
    fun activeObservationRecoveryOutranksTheChecklist() = runBlocking {
        val territory = territoryRepository.createTerritory("T01", "Территория", "Область", "Район")
        val observer = observerRepository.createObserver("O01", "Иванов", "Иван", null, null)
        settingsRepository.setCurrentTerritoryId(territory.id)
        settingsRepository.setCurrentObserverId(observer.id)
        observationRepository.createObservationPoint(
            NewObservationPoint(
                territoryId = territory.id,
                observerId = observer.id,
                code = "P01",
                latitude = 56.30,
                longitude = 42.50,
                gpsLatitude = null,
                gpsLongitude = null,
                gpsAccuracyM = null,
            ),
        )

        val viewModel = newViewModel()
        val activity = mirrorActivityStartup(viewModel)
        try {
            val first = firstUserRoute(viewModel)
            assertTrue(
                "active ObservationPoint recovery must win over the checklist, was $first",
                first is AppRoute.ResumeObservation,
            )
        } finally {
            activity.cancel()
        }
    }

    private companion object {
        const val ROUTE_TIMEOUT_MILLIS = 10_000L
        const val SETTLE_MILLIS = 300L
    }
}
