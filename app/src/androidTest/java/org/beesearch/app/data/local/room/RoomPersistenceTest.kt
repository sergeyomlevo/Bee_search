package org.beesearch.app.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.domain.model.InvalidAzimuthException
import org.beesearch.app.domain.model.InitialReleaseAlreadyStartedException
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.ObservationPointAlreadyActiveException
import org.beesearch.app.domain.model.OpenFlightCycleExistsException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class RoomPersistenceTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var clock: MutableClock
    private lateinit var territoryRepository: RoomTerritoryRepository
    private lateinit var observationRepository: RoomObservationRepository
    private lateinit var territoryId: UUID

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableClock(Instant.parse("2026-08-26T06:34:12Z"))
        territoryRepository = RoomTerritoryRepository(database.territoryDao(), clock)
        observationRepository = RoomObservationRepository(
            database = database,
            territoryDao = database.territoryDao(),
            pointDao = database.observationPointDao(),
            beeDao = database.beeDao(),
            cycleDao = database.flightCycleDao(),
            clock = clock,
        )
        territoryId = territoryRepository.createTerritory("KLYAZMA-01", "Клязьма").id
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun onlyOneObservationPointCanBeActive() = runBlocking {
        val first = createPoint()

        assertThrows(ObservationPointAlreadyActiveException::class.java) {
            runBlocking { createPoint() }
        }

        clock.advanceSeconds(10)
        observationRepository.completeObservationPoint(first.id)
        val second = createPoint()
        assertNull(second.completedAt)
    }

    @Test
    fun initialReleaseIsAtomicAndOneOpenCycleIsEnforced() = runBlocking {
        val point = createPoint()
        val firstBee = observationRepository.addBee(point.id, "WHITE", MarkPosition.NONE)
        val secondBee = observationRepository.addBee(point.id, "BLUE", MarkPosition.RIGHT_WING)

        val initialCycles = observationRepository.startInitialGroupRelease(point.id)
        assertEquals(2, initialCycles.size)
        assertEquals(setOf(1), initialCycles.map { it.sequenceNumber }.toSet())
        assertEquals(1, initialCycles.map { it.departureTime }.toSet().size)
        initialCycles.forEach { cycle ->
            assertEquals(cycle.departureTime, cycle.createdAt)
            assertEquals(cycle.departureTime, cycle.updatedAt)
        }

        assertThrows(InitialReleaseAlreadyStartedException::class.java) {
            runBlocking { observationRepository.startInitialGroupRelease(point.id) }
        }

        assertThrows(OpenFlightCycleExistsException::class.java) {
            runBlocking { observationRepository.startNextFlight(firstBee.id) }
        }

        clock.advanceSeconds(20)
        val returned = observationRepository.registerBeeReturn(firstBee.id)
        assertEquals(clock.instant(), returned.returnTime)
        assertEquals(clock.instant(), returned.updatedAt)
        clock.advanceSeconds(5)
        val next = observationRepository.startNextFlight(firstBee.id)
        assertEquals(2, next.sequenceNumber)
        assertEquals(next.departureTime, next.createdAt)
        assertEquals(next.departureTime, next.updatedAt)

        val firstBeeCycles = observationRepository.observeFlightCycles(firstBee.id).first()
        val secondBeeCycles = observationRepository.observeFlightCycles(secondBee.id).first()
        assertEquals(2, firstBeeCycles.size)
        assertEquals(1, secondBeeCycles.size)
    }

    @Test
    fun databaseAndRepositoryProtectCoreConstraints() = runBlocking {
        val point = createPoint()
        observationRepository.addBee(point.id, "WHITE", MarkPosition.LEFT_WING)

        assertThrows(Exception::class.java) {
            runBlocking {
                observationRepository.addBee(point.id, "WHITE", MarkPosition.LEFT_WING)
            }
        }

        val orphan = BeeEntity(
            id = UUID.randomUUID(),
            observationPointId = UUID.randomUUID(),
            markColor = "BLUE",
            markPosition = MarkPosition.NONE,
            createdAt = clock.instant(),
        )
        assertThrows(Exception::class.java) {
            runBlocking { database.beeDao().insert(orphan) }
        }
        Unit
    }

    @Test
    fun azimuthAllowsZeroRejects360AndCanBeRemoved() = runBlocking {
        val point = createPoint()
        val bee = observationRepository.addBee(point.id, "YELLOW", MarkPosition.NONE)
        val cycle = observationRepository.startInitialGroupRelease(point.id).single()

        assertEquals(0.0, observationRepository.setFlightAzimuth(cycle.id, 0.0).azimuthDeg)
        assertThrows(InvalidAzimuthException::class.java) {
            runBlocking { observationRepository.setFlightAzimuth(cycle.id, 360.0) }
        }
        assertNull(observationRepository.setFlightAzimuth(cycle.id, null).azimuthDeg)
        assertEquals(bee.id, cycle.beeId)
    }

    private suspend fun createPoint() = observationRepository.createObservationPoint(
        point = NewObservationPoint(
            territoryId = territoryId,
            latitude = 56.1959786,
            longitude = 42.7477116,
            gpsLatitude = 56.1959000,
            gpsLongitude = 42.7477000,
            gpsAccuracyM = 4.5,
        ),
        observerCode = " SV ",
    ).also { point -> assertEquals("SV", point.observerCode) }

    private class MutableClock(
        private var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advanceSeconds(seconds: Long) {
            current = current.plusSeconds(seconds)
        }
    }
}
