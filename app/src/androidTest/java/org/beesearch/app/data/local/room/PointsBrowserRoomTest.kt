package org.beesearch.app.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.MarkPosition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PointsBrowserRoomTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var repository: RoomObservationRepository
    private val clock = Clock.fixed(Instant.parse("2026-09-17T08:00:00Z"), ZoneOffset.UTC)
    private val territory = UUID.randomUUID()
    private val otherTerritory = UUID.randomUUID()
    private val observer = UUID.randomUUID()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = RoomObservationRepository(
            database, database.territoryDao(), database.observationPointDao(),
            database.observerDao(), database.beeDao(), database.flightCycleDao(), clock,
            observationZoneIdProvider = { ZoneOffset.UTC },
        )
    }

    @After fun tearDown() = database.close()

    @Test
    fun summariesFilterTerritoryAndYearAndKeepAllResults() = runBlocking {
        val t = territoryEntity(territory, "T1")
        database.backupDao().insertTerritories(listOf(t, territoryEntity(otherTerritory, "T2")))
        database.backupDao().insertObservers(listOf(observerEntity()))
        val found = point(territory, 2026, 1, BeePresenceResult.BEES_FOUND)
        val empty = point(territory, 2025, 2, BeePresenceResult.NO_BEES_FOUND)
        val unresolved = point(territory, 2026, 3, null).copy(
            createdAt = Instant.parse("2026-01-03T00:00:00Z"),
            completedAt = null,
        )
        val foreign = point(otherTerritory, 2026, 4, BeePresenceResult.BEES_FOUND)
        database.backupDao().insertObservationPoints(listOf(found, empty, unresolved, foreign))
        val bee = bee(found.id)
        database.backupDao().insertBees(listOf(bee))
        database.backupDao().insertFlightCycles(listOf(cycle(bee.id, 1, true), cycle(bee.id, 2, false)))

        val current = repository.observeObservationPointSummaries(territory, 2026).first()
        assertEquals(listOf(unresolved.id, found.id), current.map { it.id })
        assertEquals(1, current.last().beeCount)
        assertEquals(1, current.last().completedFlightCycleCount)
        assertEquals(BeePresenceResult.BEES_FOUND, current.last().beePresenceResult)
        assertNull(current.first().beePresenceResult)
        assertEquals(listOf(unresolved.id, found.id, empty.id), repository
            .observeObservationPointSummaries(territory, null).first().map { it.id })
        val noBees = repository.observeObservationPointSummaries(territory, 2025).first().single()
        assertEquals(BeePresenceResult.NO_BEES_FOUND, noBees.beePresenceResult)
        assertEquals(0, noBees.beeCount)
        assertEquals(0, noBees.completedFlightCycleCount)
        assertEquals(0, repository.observeObservationPointSummaries(territory, 2030).first().size)
    }

    @Test
    fun detailPreservesBeeCycleOrderOpenReturnAndNullableAzimuth() = runBlocking {
        database.backupDao().insertTerritories(listOf(territoryEntity(territory, "T1")))
        database.backupDao().insertObservers(listOf(observerEntity()))
        val point = point(territory, 2026, 1, BeePresenceResult.BEES_FOUND)
        database.backupDao().insertObservationPoints(listOf(point))
        val first = bee(point.id, "red", Instant.EPOCH)
        val second = bee(point.id, "blue", Instant.EPOCH.plusSeconds(1))
        database.backupDao().insertBees(listOf(first, second))
        database.backupDao().insertFlightCycles(listOf(cycle(first.id, 2, false), cycle(first.id, 1, true), cycle(second.id, 1, false, 90.0)))

        val detail = repository.getObservationPointDetail(point.id)!!
        assertEquals(2, detail.beeHistories.size)
        assertEquals(listOf(1, 2), detail.beeHistories.first().flightCycles.map { it.sequenceNumber })
        assertEquals(Instant.parse("2026-09-17T07:00:01Z"), detail.beeHistories.first().flightCycles.first().returnTime)
        assertNull(detail.beeHistories.first().flightCycles.last().returnTime)
        assertNull(detail.beeHistories.first().flightCycles.last().azimuthDeg)
        assertEquals(90.0, detail.beeHistories.last().flightCycles.single().azimuthDeg!!, 0.0)
    }

    private fun territoryEntity(id: UUID, code: String) = TerritoryEntity(id, code, code, "R", "D", Instant.EPOCH, Instant.EPOCH)
    private fun observerEntity() = ObserverEntity(observer, "O1", "Doe", "Jane", null, null, Instant.EPOCH, Instant.EPOCH)
    private fun point(t: UUID, year: Int, number: Int, result: BeePresenceResult?) = ObservationPointEntity(UUID.randomUUID(), t, observer, year, number, result, null, 56.0, 43.0, null, null, null, Instant.parse("$year-01-01T00:00:00Z"), null, Instant.parse("$year-01-01T01:00:00Z"))
    private fun bee(point: UUID, color: String = "red", createdAt: Instant = Instant.EPOCH) = BeeEntity(UUID.randomUUID(), point, color, MarkPosition.ABDOMEN, createdAt)
    private fun cycle(bee: UUID, sequence: Int, returned: Boolean, azimuth: Double? = null): FlightCycleEntity {
        val departure = Instant.parse("2026-09-17T06:00:00Z").plusSeconds(sequence.toLong())
        return FlightCycleEntity(UUID.randomUUID(), bee, sequence, departure, if (returned) departure.plusSeconds(3600) else null, azimuth, false, false, false, departure, departure)
    }
}
