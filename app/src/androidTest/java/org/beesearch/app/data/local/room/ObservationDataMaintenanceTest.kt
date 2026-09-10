package org.beesearch.app.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomObserverRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.usecase.StartupDestination
import org.beesearch.app.domain.usecase.StartupRouter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

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
}
