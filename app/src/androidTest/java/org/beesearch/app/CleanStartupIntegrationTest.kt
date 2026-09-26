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
import java.time.Instant
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
import org.beesearch.app.data.local.room.BeeEntity
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.settings.DataStoreMapAreaStore
import org.beesearch.app.data.local.settings.DataStoreMapPackageStore
import org.beesearch.app.data.local.settings.DataStoreSettingsRepository
import org.beesearch.app.data.location.AndroidLocationProvider
import org.beesearch.app.data.media.FileAwarePhysicalObjectDeletion
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomObserverRepository
import org.beesearch.app.data.repository.RoomPhysicalObjectRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.usecase.CreateObservationPoint
import org.beesearch.app.domain.weather.WeatherSyncScheduler
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.MapPackageAvailability
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private val settingsFile = File(context.cacheDir, "clean-startup-settings-${UUID.randomUUID()}.preferences_pb")
    private val installStateFile = File(context.cacheDir, "clean-startup-install-${UUID.randomUUID()}.preferences_pb")
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = storeScope,
        produceFile = { settingsFile },
    )
    private val installStateDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = storeScope,
        produceFile = { installStateFile },
    )
    private val settingsRepository = DataStoreSettingsRepository(dataStore, installStateDataStore)
    private val territoryRepository = RoomTerritoryRepository(database, database.territoryDao(), Clock.systemUTC())
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
    private val physicalObjectMediaStore = PhysicalObjectMediaFileStore(context.filesDir, context.cacheDir)
    private val physicalObjectRepository = RoomPhysicalObjectRepository(
        database = database,
        objectDao = database.physicalObjectDao(),
        sequenceDao = database.physicalObjectSequenceDao(),
        territoryDao = database.territoryDao(),
        observerDao = database.observerDao(),
        beeDao = database.beeDao(),
        clock = Clock.systemUTC(),
    )
    private val renderedRoutes = CopyOnWriteArrayList<AppRoute>()

    @After
    fun tearDown() {
        database.close()
        storeScope.cancel()
        settingsFile.delete()
        installStateFile.delete()
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
        physicalObjectRepository = physicalObjectRepository,
        physicalObjectDeletion = FileAwarePhysicalObjectDeletion(physicalObjectRepository, physicalObjectMediaStore),
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
    fun physicalObjectCoordinateEditUsesMapCenterAndReturnsOneShotUpdateForSameIdentity() = runBlocking {
        val viewModel = newViewModel()
        val objectId = UUID.randomUUID()

        viewModel.openPhysicalObjectDetail(objectId, PhysicalObjectType.HOLLOW)
        viewModel.editPhysicalObjectCoordinates(objectId, "Дупло 7", 56.1, 42.7)

        assertEquals(objectId, (viewModel.physicalObjectLocationSelection.value as PhysicalObjectLocationSelection.Edit).objectId)
        assertEquals(MapTarget(56.1, 42.7), viewModel.mapCenterRequest.value?.target)
        awaitRoute(viewModel, AppRoute.CurrentTerritory())

        viewModel.confirmPhysicalObjectLocation(56.2, 42.8)
        val detail = withTimeout(ROUTE_TIMEOUT_MILLIS) {
            viewModel.route.first {
                it is AppRoute.PhysicalObjectDetail && it.coordinateUpdate != null
            }
        } as AppRoute.PhysicalObjectDetail
        assertEquals(objectId, detail.objectId)
        assertEquals(56.2, requireNotNull(detail.coordinateUpdate).latitude, 0.0)
        assertEquals(42.8, requireNotNull(detail.coordinateUpdate).longitude, 0.0)

        viewModel.consumePhysicalObjectCoordinateUpdate(requireNotNull(detail.coordinateUpdate).requestId)
        awaitRoute(viewModel, AppRoute.PhysicalObjectDetail(objectId, PhysicalObjectType.HOLLOW))
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
            awaitRoute(viewModel, AppRoute.CurrentTerritory())
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
            assertEquals(AppRoute.CurrentTerritory(), firstUserRoute(relaunched))
        } finally {
            nextActivity.cancel()
        }
    }

    @Test
    fun physicalObjectCardKeepsItsReturnPathAcrossTheMap() = runBlocking {
        val viewModel = newViewModel()
        val hollowId = UUID.randomUUID()
        val logHiveId = UUID.randomUUID()

        // Card → map → Back → the same card → Back → its list → Back → Объекты.
        viewModel.openPhysicalObjectDetail(hollowId, PhysicalObjectType.HOLLOW)
        viewModel.showPhysicalObjectOnMap(56.19, 42.74)
        awaitRoute(
            viewModel,
            AppRoute.CurrentTerritory(PhysicalObjectCardReturn(hollowId, PhysicalObjectType.HOLLOW)),
        )
        viewModel.returnFromPhysicalObjectMap()
        awaitRoute(viewModel, AppRoute.PhysicalObjectDetail(hollowId, PhysicalObjectType.HOLLOW))
        viewModel.closePhysicalObjectDetail()
        awaitRoute(viewModel, AppRoute.PhysicalObjectList(PhysicalObjectType.HOLLOW))
        viewModel.closePhysicalObjectList()
        awaitRoute(viewModel, AppRoute.Objects)

        // A second card of another type returns to its own card and then to Колоды.
        viewModel.openPhysicalObjectDetail(logHiveId, PhysicalObjectType.LOG_HIVE)
        viewModel.showPhysicalObjectOnMap(56.20, 42.75)
        viewModel.returnFromPhysicalObjectMap()
        awaitRoute(viewModel, AppRoute.PhysicalObjectDetail(logHiveId, PhysicalObjectType.LOG_HIVE))

        // Repeating the round trip leaves no stale return target.
        viewModel.showPhysicalObjectOnMap(56.21, 42.76)
        viewModel.returnFromPhysicalObjectMap()
        awaitRoute(viewModel, AppRoute.PhysicalObjectDetail(logHiveId, PhysicalObjectType.LOG_HIVE))
        viewModel.closePhysicalObjectDetail()
        awaitRoute(viewModel, AppRoute.PhysicalObjectList(PhysicalObjectType.LOG_HIVE))

        // An ordinary map opening never carries an object return context.
        viewModel.openCurrentTerritory()
        val ordinary = withTimeout(ROUTE_TIMEOUT_MILLIS) {
            viewModel.route.first { it is AppRoute.CurrentTerritory }
        } as AppRoute.CurrentTerritory
        assertEquals("an ordinary map opening must not return to an old object card", null, ordinary.returnToObject)

        // A restart does not restore the transient navigation context.
        assertEquals(AppRoute.InitialSetup, firstUserRoute(newViewModel()))
    }

    @Test
    fun deletingAnObjectFromItsCardReturnsToItsListAndDoesNotReuseTheNumber() = runBlocking {
        val territory = territoryRepository.createTerritory("DEL", "Территория", "Область", "Район")
        val observer = observerRepository.createObserver("DELOBS", "Иванов", "Иван", null, null)
        settingsRepository.setCurrentTerritoryId(territory.id)
        settingsRepository.setCurrentObserverId(observer.id)
        val viewModel = newViewModel()
        val first = physicalObjectRepository.createHollow(
            NewHollow(UUID.randomUUID(), territory.id, observer.id, 56.1, 42.7, HollowProperties("дуб", 180.0, 123, 40.0, null, null)),
        )
        val second = physicalObjectRepository.createHollow(
            NewHollow(UUID.randomUUID(), territory.id, observer.id, 56.2, 42.8, HollowProperties("дуб", 180.0, 123, 40.0, null, null)),
        )

        viewModel.openPhysicalObjectList(PhysicalObjectType.HOLLOW)
        awaitRoute(viewModel, AppRoute.PhysicalObjectList(PhysicalObjectType.HOLLOW))
        viewModel.openPhysicalObjectDetail(second.id, PhysicalObjectType.HOLLOW)
        awaitRoute(viewModel, AppRoute.PhysicalObjectDetail(second.id, PhysicalObjectType.HOLLOW))

        viewModel.deletePhysicalObject(second.id, PhysicalObjectType.HOLLOW)

        awaitRoute(viewModel, AppRoute.PhysicalObjectList(PhysicalObjectType.HOLLOW))
        assertNull(physicalObjectRepository.getHollow(second.id))
        assertEquals("Объект удалён", viewModel.feedback.value?.message)
        val third = physicalObjectRepository.createHollow(
            NewHollow(UUID.randomUUID(), territory.id, observer.id, 56.3, 42.9, HollowProperties("дуб", 180.0, 123, 40.0, null, null)),
        )
        assertEquals(1, first.sequenceNumber)
        assertEquals(3, third.sequenceNumber)
    }

    @Test
    fun deletingAnObjectUsedByObservationDataIsRefusedAndKeepsTheCardOpen() = runBlocking {
        val territory = territoryRepository.createTerritory("KEEP", "Территория", "Область", "Район")
        val observer = observerRepository.createObserver("KEEPOBS", "Иванов", "Иван", null, null)
        settingsRepository.setCurrentTerritoryId(territory.id)
        settingsRepository.setCurrentObserverId(observer.id)
        val hollow = physicalObjectRepository.createHollow(
            NewHollow(UUID.randomUUID(), territory.id, observer.id, 56.1, 42.7, HollowProperties("дуб", 180.0, 123, 40.0, null, null)),
        )
        val pointId = UUID.randomUUID()
        val beeId = UUID.randomUUID()
        database.backupDao().insertObservationPoints(
            listOf(
                ObservationPointEntity(
                    pointId, territory.id, observer.id, 2026, 1, BeePresenceResult.BEES_FOUND,
                    null, 56.0, 42.0, null, null, null, Instant.parse("2026-09-24T08:00:00Z"), null, null,
                ),
            ),
        )
        database.backupDao().insertBees(
            listOf(BeeEntity(beeId, pointId, "WHITE", MarkPosition.THORAX, Instant.parse("2026-09-24T08:00:00Z"), null)),
        )
        physicalObjectRepository.setBeeSourceObject(beeId, hollow.id)
        val viewModel = newViewModel()
        viewModel.openPhysicalObjectDetail(hollow.id, PhysicalObjectType.HOLLOW)
        awaitRoute(viewModel, AppRoute.PhysicalObjectDetail(hollow.id, PhysicalObjectType.HOLLOW))

        viewModel.deletePhysicalObject(hollow.id, PhysicalObjectType.HOLLOW)

        withTimeout(ROUTE_TIMEOUT_MILLIS) { viewModel.feedback.first { it != null } }
        assertEquals(
            "Объект используется в данных наблюдений и не может быть удалён",
            viewModel.feedback.value?.message,
        )
        assertEquals(AppRoute.PhysicalObjectDetail(hollow.id, PhysicalObjectType.HOLLOW), viewModel.route.value)
        assertNotNull(physicalObjectRepository.getHollow(hollow.id))
        assertEquals(hollow.id, physicalObjectRepository.getBeeSourceObjectId(beeId))
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

    @Test
    fun restoredStateWithoutTheOfflineMapStillAsksForTheMap() = runBlocking {
        // A restore can bring Observer, Territory and the Ареал while the PMTiles package stays
        // device-local, which is exactly the state the checklist has to report honestly.
        val territory = territoryRepository.createTerritory(
            "LPO", "Лухское полесье", "Владимирская область", "Лухский район",
        )
        val observer = observerRepository.createObserver("OBS", "Иванов", "Иван", null, null)
        settingsRepository.setCurrentTerritoryId(territory.id)
        settingsRepository.setCurrentObserverId(observer.id)
        areaStore.create(territory.id, territory.name, listOf(MapGeoBounds(57.0, 39.0, 56.0, 38.0)))

        val viewModel = newViewModel()
        val activity = mirrorActivityStartup(viewModel)
        try {
            assertEquals(AppRoute.InitialSetup, firstUserRoute(viewModel))
            val state = withTimeout(ROUTE_TIMEOUT_MILLIS) {
                viewModel.visibleInitialSetup.first { it is InitialSetupState.Ready }
            } as InitialSetupState.Ready

            assertFalse("the offer must not be treated as handled on a fresh installation", state.offerHandled)
            assertNotNull("restored Observer", state.observer)
            assertNotNull("restored Territory", state.territory)
            assertTrue("restored Ареал", state.area is MapAreaReadResult.Present)
            assertFalse("the offline map is not restored, so the checklist stays incomplete", state.complete)
            assertTrue(state.map == null || state.map !is MapPackageAvailability.Ready)
        } finally {
            activity.cancel()
        }
    }

    private companion object {
        const val ROUTE_TIMEOUT_MILLIS = 10_000L
        const val SETTLE_MILLIS = 300L
    }
}
