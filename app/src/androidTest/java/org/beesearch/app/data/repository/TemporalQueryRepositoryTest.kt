package org.beesearch.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.BeeEntity
import org.beesearch.app.data.local.room.FlightCycleEntity
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.NewLogHive
import org.beesearch.app.domain.model.ResearchDateInterval
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemporalQueryRepositoryTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var observations: RoomObservationRepository
    private lateinit var objects: RoomPhysicalObjectRepository
    private val territory = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherTerritory = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val observer = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val now = Instant.parse("2026-06-15T12:00:00Z")
    private var nextPointNumber = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), BeeSearchDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.backupDao().insertTerritories(
            listOf(
                territoryEntity(territory, "T1"),
                territoryEntity(otherTerritory, "T2"),
            ),
        )
        database.backupDao().insertObservers(listOf(ObserverEntity(observer, "O", "Last", "First", null, null, now, now)))
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        observations = RoomObservationRepository(
            database, database.territoryDao(), database.observationPointDao(), database.observerDao(),
            database.beeDao(), database.flightCycleDao(), clock,
            observationZoneIdProvider = { ZoneOffset.UTC },
        )
        objects = RoomPhysicalObjectRepository(
            database, database.physicalObjectDao(), database.physicalObjectSequenceDao(), database.territoryDao(),
            database.observerDao(), database.beeDao(), clock,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun observationSummariesFilterInclusiveDateAndPreserveYearTerritoryAndAggregate() = runBlocking {
        val before = observation(2026, LocalDate.of(2026, 4, 30), now.minusSeconds(1), territory)
        val lower = observation(2026, LocalDate.of(2026, 5, 1), now.minusSeconds(4), territory)
        val inside = observation(2026, LocalDate.of(2026, 5, 15), now.minusSeconds(3), territory)
        val upper = observation(2026, LocalDate.of(2026, 5, 31), now.minusSeconds(2), territory)
        val after = observation(2026, LocalDate.of(2026, 6, 1), now, territory)
        val otherYear = observation(2025, LocalDate.of(2025, 5, 15), now.plusSeconds(1), territory)
        val otherTerritoryPoint = observation(2026, LocalDate.of(2026, 5, 15), now.plusSeconds(2), otherTerritory)
        database.backupDao().insertObservationPoints(listOf(before, lower, inside, upper, after, otherYear, otherTerritoryPoint))
        val beeId = UUID.randomUUID()
        database.backupDao().insertBees(listOf(BeeEntity(beeId, inside.id, "red", MarkPosition.ABDOMEN, now)))
        database.backupDao().insertFlightCycles(
            listOf(FlightCycleEntity(UUID.randomUUID(), beeId, 1, now, now.plusSeconds(60), null, false, false, false, now, now)),
        )

        val interval = ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))
        val filtered = observations.observeObservationPointSummaries(territory, observationYear = 2026, dateInterval = interval).first()
        assertEquals(listOf(upper.id, inside.id, lower.id), filtered.map { it.id })
        assertEquals(1, filtered[1].beeCount)
        assertEquals(1, filtered[1].completedFlightCycleCount)
        assertEquals(2026, filtered.first().observationYear)

        val singleDay = ResearchDateInterval(LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 15))
        assertEquals(listOf(inside.id), observations
            .observeObservationPointSummaries(territory, observationYear = 2026, dateInterval = singleDay)
            .first().map { it.id })

        val unbounded = observations.observeObservationPointSummaries(territory, observationYear = 2026).first()
        assertEquals(listOf(after.id, before.id, upper.id, inside.id, lower.id), unbounded.map { it.id })
    }

    @Test
    fun observationSummariesTieOnCreatedAtThenOrderById() = runBlocking {
        val first = ObservationPointEntity(
            LocalDate.of(2026, 5, 10), UUID.fromString("00000000-0000-0000-0000-00000000000a"),
            territory, observer, 2026, 1, null, null, 56.0, 43.0, null, null, null, now, null, null,
        )
        val second = ObservationPointEntity(
            LocalDate.of(2026, 5, 11), UUID.fromString("00000000-0000-0000-0000-00000000000b"),
            territory, observer, 2026, 2, null, null, 56.0, 43.0, null, null, null, now, null, null,
        )
        database.backupDao().insertObservationPoints(listOf(second, first))

        val expected = listOf(first.id, second.id)
        assertEquals(expected, observations
            .observeObservationPointSummaries(territory, observationYear = 2026).first().map { it.id })
        assertEquals(expected, observations
            .observeObservationPointSummaries(
                territory,
                observationYear = 2026,
                dateInterval = ResearchDateInterval(LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 11)),
            ).first().map { it.id })
    }

    @Test
    fun observationSummariesFlowEmitsWhenNewPointEntersDateWindow() = runBlocking {
        val interval = ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))
        val initial = observation(2026, LocalDate.of(2026, 4, 30), now, territory)
        database.backupDao().insertObservationPoints(listOf(initial))
        val initialEmission = CompletableDeferred<Unit>()
        val updates = async(start = CoroutineStart.UNDISPATCHED) {
            observations.observeObservationPointSummaries(territory, dateInterval = interval)
                .onEach { if (!initialEmission.isCompleted) {
                    assertTrue(it.isEmpty())
                    initialEmission.complete(Unit)
                } }
                .drop(1).first()
        }
        withTimeout(TimeUnit.SECONDS.toMillis(5)) { initialEmission.await() }
        val entered = observation(2026, LocalDate.of(2026, 5, 10), now.plusSeconds(1), territory)
        database.backupDao().insertObservationPoints(listOf(entered))
        val emission = withTimeout(TimeUnit.SECONDS.toMillis(5)) { updates.await() }
        assertEquals(listOf(entered.id), emission.map { it.id })
    }

    @Test
    fun physicalObjectIntervalsAreIndependentPerTypeAndExcludeNullFixation() = runBlocking {
        val hollowBefore = objects.createHollow(NewHollow(UUID.randomUUID(), territory, observer, 56.1, 42.7, hollowProperties()))
        val hollowInside = objects.createHollow(NewHollow(UUID.randomUUID(), territory, observer, 56.2, 42.8, hollowProperties()))
        val hollowAfter = objects.createHollow(NewHollow(UUID.randomUUID(), territory, observer, 56.3, 42.9, hollowProperties()))
        val logBefore = objects.createLogHive(NewLogHive(UUID.randomUUID(), territory, observer, 56.4, 43.0, logHiveProperties()))
        val logInside = objects.createLogHive(NewLogHive(UUID.randomUUID(), territory, observer, 56.5, 43.1, logHiveProperties()))
        val logUpper = objects.createLogHive(NewLogHive(UUID.randomUUID(), territory, observer, 56.6, 43.2, logHiveProperties()))
        val logAfter = objects.createLogHive(NewLogHive(UUID.randomUUID(), territory, observer, 56.7, 43.3, logHiveProperties()))
        val otherTerritory = objects.createHollow(NewHollow(UUID.randomUUID(), otherTerritory, observer, 56.6, 43.2, hollowProperties()))
        val apiary = objects.createApiary(territory, 56.8, 43.4, "apiary")
        val hollowLower = objects.createHollow(NewHollow(UUID.randomUUID(), territory, observer, 57.0, 43.0, hollowProperties()))
        val hollowUpper = objects.createHollow(NewHollow(UUID.randomUUID(), territory, observer, 57.0, 43.0, hollowProperties()))
        val logLower = objects.createLogHive(NewLogHive(UUID.randomUUID(), territory, observer, 57.0, 43.0, logHiveProperties()))
        listOf(hollowBefore.id to "2026-04-30", hollowInside.id to "2026-05-15", hollowAfter.id to "2026-06-01",
            logBefore.id to "2026-04-30", logInside.id to "2026-05-15", logUpper.id to "2026-05-31", logAfter.id to "2026-06-01",
            hollowLower.id to "2026-05-01", hollowUpper.id to "2026-05-31", logLower.id to "2026-05-01",
            otherTerritory.id to "2026-05-15").forEach { (id, date) ->
            database.openHelper.writableDatabase.execSQL("UPDATE physical_objects SET fixation_date = ? WHERE id = ?", arrayOf(date, id.toString()))
        }
        val legacyHollow = objects.createHollow(NewHollow(UUID.randomUUID(), territory, observer, 56.9, 43.5, hollowProperties()))
        val legacyLogHive = objects.createLogHive(NewLogHive(UUID.randomUUID(), territory, observer, 57.0, 43.6, logHiveProperties()))
        listOf(legacyHollow.id, legacyLogHive.id).forEach { id ->
            database.openHelper.writableDatabase.execSQL("UPDATE physical_objects SET fixation_date = NULL WHERE id = ?", arrayOf(id.toString()))
        }
        val interval = ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))

        val hollowOnly = objects.listForTerritory(territory, hollowDateInterval = interval)
        assertEquals(listOf(hollowInside.id, hollowLower.id, hollowUpper.id), hollowOnly.hollows.map { it.id })
        assertEquals(listOf(logBefore.id, logInside.id, logUpper.id, logAfter.id, logLower.id, legacyLogHive.id), hollowOnly.logHives.map { it.id })
        assertEquals(listOf(apiary.id), hollowOnly.apiaries.map { it.id })
        assertTrue(hollowOnly.hollows.none { it.id == legacyHollow.id })

        val logOnly = objects.listForTerritory(territory, logHiveDateInterval = interval)
        assertEquals(listOf(hollowBefore.id, hollowInside.id, hollowAfter.id, hollowLower.id, hollowUpper.id, legacyHollow.id), logOnly.hollows.map { it.id })
        assertEquals(listOf(logInside.id, logUpper.id, logLower.id), logOnly.logHives.map { it.id })
        assertTrue(logOnly.hollows.none { it.id == otherTerritory.id })
        assertEquals(listOf(apiary.id), logOnly.apiaries.map { it.id })

        val independentWindows = objects.listForTerritory(
            territory,
            hollowDateInterval = ResearchDateInterval(LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 15)),
            logHiveDateInterval = ResearchDateInterval(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)),
        )
        assertEquals(listOf(hollowInside.id), independentWindows.hollows.map { it.id })
        assertEquals(listOf(logAfter.id), independentWindows.logHives.map { it.id })
        assertEquals(listOf(apiary.id), independentWindows.apiaries.map { it.id })

        val unbounded = objects.listForTerritory(territory)
        assertEquals(listOf(hollowBefore.id, hollowInside.id, hollowAfter.id, hollowLower.id, hollowUpper.id, legacyHollow.id), unbounded.hollows.map { it.id })
        assertEquals(listOf(logBefore.id, logInside.id, logUpper.id, logAfter.id, logLower.id, legacyLogHive.id), unbounded.logHives.map { it.id })
        assertEquals(listOf(apiary.id), unbounded.apiaries.map { it.id })
    }

    private fun territoryEntity(id: UUID, code: String) = TerritoryEntity(id, code, code, "R", "D", now, now)
    private fun observation(year: Int, date: LocalDate, createdAt: Instant, territoryId: UUID) = ObservationPointEntity(
        date, UUID.randomUUID(), territoryId, observer, year, ++nextPointNumber, null, null, 56.0, 43.0,
        null, null, null, createdAt, null, null,
    )
    private fun hollowProperties() = HollowProperties("oak", 1.0, 0, 1.0, null, null)
    private fun logHiveProperties() = LogHiveProperties("pine", 1.0, 0, 1.0, "wood", 1.0, 1.0, null)
}
