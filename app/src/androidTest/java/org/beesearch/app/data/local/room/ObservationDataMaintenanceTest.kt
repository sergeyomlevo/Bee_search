package org.beesearch.app.data.local.room

import org.beesearch.app.addBee
import org.beesearch.app.startInitialGroupRelease

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.settings.DataStoreMapCoverageStore
import org.beesearch.app.data.local.settings.DataStoreSettingsRepository
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomObserverRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.EntityNotFoundException
import org.beesearch.app.domain.model.ObservationDataCounts
import org.beesearch.app.domain.model.ObservationPointNotCompletedException
import org.beesearch.app.domain.usecase.StartupDestination
import org.beesearch.app.domain.usecase.StartupRouter
import org.beesearch.app.ui.map.MapCoverageFragment
import org.beesearch.app.ui.map.MapGeoBounds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.io.File
import java.util.UUID

class ObservationDataMaintenanceTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var territoryRepository: RoomTerritoryRepository
    private lateinit var observerRepository: RoomObserverRepository
    private lateinit var observationRepository: RoomObservationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-09-10T08:00:00Z"), ZoneOffset.UTC)
        territoryRepository = RoomTerritoryRepository(database.territoryDao(), clock)
        observerRepository = RoomObserverRepository(database.observerDao(), clock)
        observationRepository = RoomObservationRepository(
            database = database,
            territoryDao = database.territoryDao(),
            pointDao = database.observationPointDao(),
            observerDao = database.observerDao(),
            beeDao = database.beeDao(),
            cycleDao = database.flightCycleDao(),
            clock = clock,
            observationZoneIdProvider = { ZoneOffset.UTC },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun clearDeletesResearchChildrenButPreservesTerritoryObserverAndStartupSafety() = runBlocking {
        val territory = territoryRepository.createTerritory("T01", "Территория", "Регион", "Район")
        val observer = observerRepository.createObserver("O01", "Иванов", "Иван", null, null)
        val point = observationRepository.createObservationPoint(
            NewObservationPoint(
                territoryId = territory.id,
                observerId = observer.id,
                latitude = 56.2,
                longitude = 42.7,
            ),
        )
        observationRepository.addBee(point.id, "Красная", MarkPosition.RIGHT_WING)
        observationRepository.addBee(point.id, "Синяя", MarkPosition.LEFT_WING)
        observationRepository.startInitialGroupRelease(point.id)

        assertEquals(1, observationRepository.getObservationDataCounts().observationPoints)
        assertEquals(2, observationRepository.getObservationDataCounts().bees)
        assertEquals(2, observationRepository.getObservationDataCounts().flightCycles)

        val deleted = observationRepository.clearObservationData()

        assertEquals(1, deleted.observationPoints)
        assertEquals(2, deleted.bees)
        assertEquals(2, deleted.flightCycles)
        assertEquals(0, database.backupDao().observationPointCount())
        assertEquals(0, database.backupDao().beeCount())
        assertEquals(0, database.backupDao().flightCycleCount())
        assertEquals(1, database.backupDao().territoryCount())
        assertEquals(1, database.backupDao().observerCount())
        assertNull(observationRepository.observeActivePoint().first())
        assertEquals(
            StartupDestination.ReadyForMap,
            StartupRouter.decide(
                activePoint = observationRepository.observeActivePoint().first(),
                currentTerritoryId = territory.id,
                territories = territoryRepository.observeTerritories().first(),
                currentObserverId = observer.id,
                observers = observerRepository.observeObservers().first(),
            ),
        )
    }

    @Test
    fun selectiveDeleteRemovesOnlyTheCompletedPointGraphAndPreservesPortableAndMapState() = runBlocking {
        val territory = territoryRepository.createTerritory("T01", "Территория", "Регион", "Район")
        val observer = observerRepository.createObserver("O01", "Иванов", "Иван", null, null)
        val deletedPoint = observationRepository.createObservationPoint(
            NewObservationPoint(territory.id, observer.id, latitude = 56.2, longitude = 42.7),
        )
        val deletedBee = observationRepository.addBee(
            deletedPoint.id,
            "Красная",
            MarkPosition.RIGHT_WING,
        )
        val deletedCycle = observationRepository.startInitialGroupRelease(deletedPoint.id).single()
        observationRepository.completeObservationPoint(deletedPoint.id)

        val retainedPoint = observationRepository.createObservationPoint(
            NewObservationPoint(territory.id, observer.id, latitude = 56.3, longitude = 42.8),
        )
        val retainedBee = observationRepository.addBee(
            retainedPoint.id,
            "Синяя",
            MarkPosition.LEFT_WING,
        )
        val retainedCycle = observationRepository.startInitialGroupRelease(retainedPoint.id).single()
        observationRepository.completeObservationPoint(retainedPoint.id)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferenceFile = File(context.cacheDir, "selective-delete-${UUID.randomUUID()}.preferences_pb")
        val mapFile = File(context.cacheDir, "selective-delete-map-${UUID.randomUUID()}.pmtiles")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { preferenceFile })
            val settings = DataStoreSettingsRepository(dataStore)
            val coverageStore = DataStoreMapCoverageStore(dataStore)
            val coverage = listOf(MapCoverageFragment(MapGeoBounds(57.0, 43.0, 56.0, 42.0)))
            val activeMapKey = stringPreferencesKey("map_package_active_${territory.id}")
            settings.setCurrentTerritoryId(territory.id)
            settings.setCurrentObserverId(observer.id)
            coverageStore.replace(territory.id, coverage)
            dataStore.edit { it[activeMapKey] = mapFile.absolutePath }
            mapFile.writeText("device-local map marker")

            val summaries = observationRepository.getCompletedObservationPoints()
            assertEquals(setOf(deletedPoint.id, retainedPoint.id), summaries.map { it.id }.toSet())
            assertEquals(1, summaries.single { it.id == deletedPoint.id }.beeCount)
            val retainedPointEntity = database.backupDao().observationPoints().single {
                it.id == retainedPoint.id
            }
            val retainedBeeEntity = database.backupDao().bees().single { it.id == retainedBee.id }
            val retainedCycleEntity = database.backupDao().flightCycles().single {
                it.id == retainedCycle.id
            }

            val remainingCounts = observationRepository.deleteCompletedObservationPoint(deletedPoint.id)

            assertEquals(ObservationDataCounts(1, 1, 1), remainingCounts)
            assertEquals(listOf(retainedPointEntity), database.backupDao().observationPoints())
            assertEquals(listOf(retainedBeeEntity), database.backupDao().bees())
            assertEquals(listOf(retainedCycleEntity), database.backupDao().flightCycles())
            assertFalse(database.backupDao().bees().any { it.id == deletedBee.id })
            assertFalse(database.backupDao().flightCycles().any { it.id == deletedCycle.id })
            assertEquals(1, database.backupDao().territoryCount())
            assertEquals(1, database.backupDao().observerCount())
            assertEquals(territory.id, settings.getSettings().currentTerritoryId)
            assertEquals(observer.id, settings.getSettings().currentObserverId)
            assertEquals(coverage, coverageStore.load(territory.id))
            assertEquals(mapFile.absolutePath, dataStore.data.first()[activeMapKey])
            assertTrue(mapFile.exists())
        } finally {
            scope.cancel()
            preferenceFile.delete()
            mapFile.delete()
        }
    }

    @Test
    fun activePointIsNotListedAndCannotBeSelectivelyDeleted() = runBlocking {
        val territory = territoryRepository.createTerritory("T01", "Территория", "Регион", "Район")
        val observer = observerRepository.createObserver("O01", "Иванов", "Иван", null, null)
        val completedPoint = observationRepository.createObservationPointWithNoBeesFound(
            NewObservationPoint(territory.id, observer.id, latitude = 56.2, longitude = 42.7),
        )
        val activePoint = observationRepository.createObservationPoint(
            NewObservationPoint(territory.id, observer.id, latitude = 56.3, longitude = 42.8),
        )
        val activeBee = observationRepository.addBee(activePoint.id, "Красная", MarkPosition.NONE)
        val before = database.backupDao().let { dao ->
            Triple(dao.observationPoints(), dao.bees(), dao.flightCycles())
        }

        assertEquals(listOf(completedPoint.id), observationRepository.getCompletedObservationPoints().map { it.id })
        try {
            observationRepository.deleteCompletedObservationPoint(activePoint.id)
            throw AssertionError("Expected active point deletion to be rejected")
        } catch (_: ObservationPointNotCompletedException) {
            // Expected controlled refusal.
        }

        val after = database.backupDao().let { dao ->
            Triple(dao.observationPoints(), dao.bees(), dao.flightCycles())
        }
        assertEquals(before, after)
        assertEquals(activeBee.id, database.backupDao().bees().single().id)
        assertEquals(activePoint.id, observationRepository.observeActivePoint().first()?.id)
    }

    @Test
    fun selectiveDeleteRollsBackChildrenWhenPointDeleteFails() = runBlocking {
        val territory = territoryRepository.createTerritory("T01", "Территория", "Регион", "Район")
        val observer = observerRepository.createObserver("O01", "Иванов", "Иван", null, null)
        val point = observationRepository.createObservationPoint(
            NewObservationPoint(territory.id, observer.id, latitude = 56.2, longitude = 42.7),
        )
        val bee = observationRepository.addBee(point.id, "Красная", MarkPosition.RIGHT_WING)
        val cycle = observationRepository.startInitialGroupRelease(point.id).single()
        observationRepository.completeObservationPoint(point.id)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_observation_point_delete
            BEFORE DELETE ON observation_points
            BEGIN
                SELECT RAISE(ABORT, 'forced point delete failure');
            END
            """.trimIndent(),
        )

        try {
            observationRepository.deleteCompletedObservationPoint(point.id)
            throw AssertionError("Expected point delete failure")
        } catch (_: Exception) {
            // The trigger rejects the final delete; withTransaction must restore the child rows.
        }

        assertEquals(listOf(point.id), database.backupDao().observationPoints().map { it.id })
        assertEquals(listOf(bee.id), database.backupDao().bees().map { it.id })
        assertEquals(listOf(cycle.id), database.backupDao().flightCycles().map { it.id })
    }

    @Test
    fun missingPointIsRejectedWithoutChangingExistingData() = runBlocking {
        val before = observationRepository.getObservationDataCounts()
        try {
            observationRepository.deleteCompletedObservationPoint(UUID.randomUUID())
            throw AssertionError("Expected missing point deletion to be rejected")
        } catch (_: EntityNotFoundException) {
            // Expected controlled refusal.
        }
        assertEquals(before, observationRepository.getObservationDataCounts())
    }
}
