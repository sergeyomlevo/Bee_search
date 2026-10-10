package org.beesearch.app.ui.map

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeEntity
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.FlightCycleEntity
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.CountRange
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPointFilterSet
import org.beesearch.app.domain.model.ResearchDateInterval
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Query-level proof for inclusive Bee and all-cycle count filters, including open cycles. */
@RunWith(AndroidJUnit4::class)
class MapCountFiltersQueryTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var observations: RoomObservationRepository

    private val territory = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val observer = UUID.fromString("00000000-0000-0000-0000-000000000210")
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val points = mapOf(
        "A" to PointFixture(1, LocalDate.of(2026, 5, 1), bees = 2, cycles = 5, openCycles = 1),
        "B" to PointFixture(2, LocalDate.of(2026, 6, 1), bees = 5, cycles = 12, openCycles = 2),
        "C" to PointFixture(3, LocalDate.of(2026, 7, 1), bees = 8, cycles = 20, openCycles = 3),
        "Z" to PointFixture(4, LocalDate.of(2026, 8, 1), bees = 0, cycles = 0, openCycles = 0),
    )

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), BeeSearchDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.backupDao().insertTerritories(
            listOf(TerritoryEntity(territory, "T", "T", "R", "D", now, now)),
        )
        database.backupDao().insertObservers(
            listOf(ObserverEntity(observer, "O", "Last", "First", null, null, now, now)),
        )
        val entities = points.values.map { fixture -> fixture.toPointEntity(now) }
        database.backupDao().insertObservationPoints(entities)
        val bees = buildList {
            points.values.forEach { fixture ->
                repeat(fixture.bees) { index ->
                    add(
                        BeeEntity(
                            id = fixture.beeId(index),
                            observationPointId = fixture.pointId,
                            markColor = "C$index",
                            markPosition = if (index % 2 == 0) MarkPosition.THORAX else MarkPosition.ABDOMEN,
                            createdAt = now.plusSeconds(index.toLong()),
                        ),
                    )
                }
            }
        }
        database.backupDao().insertBees(bees)
        database.backupDao().insertFlightCycles(
            points.values.flatMap { fixture ->
                (0 until fixture.cycles).map { cycleIndex ->
                    val beeIndex = cycleIndex % fixture.bees
                    val departure = now.plusSeconds((fixture.pointNumber * 1_000L) + cycleIndex)
                    FlightCycleEntity(
                        id = fixture.cycleId(cycleIndex),
                        beeId = fixture.beeId(beeIndex),
                        sequenceNumber = cycleIndex / fixture.bees + 1,
                        departureTime = departure,
                        returnTime = if (cycleIndex < fixture.cycles - fixture.openCycles) {
                            departure.plusSeconds(60)
                        } else {
                            null
                        },
                        azimuthDeg = null,
                        azimuthCaptureConsumed = false,
                        isInitialGroupLaunch = false,
                        isFirstDepartureCancellationEligible = false,
                        createdAt = departure,
                        updatedAt = departure,
                    )
                }
            },
        )
        observations = RoomObservationRepository(
            database,
            database.territoryDao(),
            database.observationPointDao(),
            database.observerDao(),
            database.beeDao(),
            database.flightCycleDao(),
            Clock.fixed(now, ZoneOffset.UTC),
            observationZoneIdProvider = { ZoneOffset.UTC },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun inclusiveBeeAndCycleBoundsIncludeZeroAndOpenBounds() = runBlocking {
        assertEquals(setOf(1, 2, 3, 4), pointNumbers())
        assertEquals(setOf(4), pointNumbers(ObservationPointFilterSet(beeCount = CountRange(0, 0))))
        assertEquals(setOf(1, 2), pointNumbers(ObservationPointFilterSet(beeCount = CountRange(2, 5))))
        assertEquals(setOf(4, 1), pointNumbers(ObservationPointFilterSet(flightCycleCount = CountRange(0, 5))))
        assertEquals(setOf(2, 3), pointNumbers(ObservationPointFilterSet(flightCycleCount = CountRange(min = 12))))
        assertEquals(setOf(4, 1, 2), pointNumbers(ObservationPointFilterSet(flightCycleCount = CountRange(max = 12))))
    }

    @Test
    fun dateBeeAndCycleFiltersAreIntersectedInclusively() = runBlocking {
        val filters = ObservationPointFilterSet(
            dateInterval = ResearchDateInterval(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1)),
            beeCount = CountRange(min = 5, max = 8),
            flightCycleCount = CountRange(min = 12, max = 20),
        )
        assertEquals(setOf(2, 3), pointNumbers(filters))

        val exactBoundary = filters.copy(
            dateInterval = ResearchDateInterval(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)),
            beeCount = CountRange(5, 5),
            flightCycleCount = CountRange(12, 12),
        )
        assertEquals(setOf(2), pointNumbers(exactBoundary))

        val disjoint = filters.copy(flightCycleCount = CountRange(min = 13, max = 19))
        assertEquals(emptySet<Int>(), pointNumbers(disjoint))
    }

    @Test
    fun independentMinMaxRangesAndEveryCombination() = runBlocking {
        assertEquals(setOf(2, 3), pointNumbers(ObservationPointFilterSet(beeCount = CountRange(min = 5))))
        assertEquals(setOf(4, 1, 2), pointNumbers(ObservationPointFilterSet(beeCount = CountRange(max = 5))))
        assertEquals(setOf(2), pointNumbers(ObservationPointFilterSet(flightCycleCount = CountRange(10, 15))))
        val period = ResearchDateInterval(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1))
        assertEquals(setOf(2), pointNumbers(ObservationPointFilterSet(period, beeCount = CountRange(min = 5))))
        assertEquals(setOf(2), pointNumbers(ObservationPointFilterSet(period, flightCycleCount = CountRange(min = 10))))
        assertEquals(setOf(2), pointNumbers(ObservationPointFilterSet(beeCount = CountRange(5, 8), flightCycleCount = CountRange(max = 12))))
    }

    @Test
    fun previewCountsAreTheSameAggregatesAsCountFiltersIncludingOpenCycles() = runBlocking {
        val summaries = observations.observeObservationPointSummaries(territory).first()
        points.values.forEach { fixture ->
            val summary = summaries.single { it.id == fixture.pointId }
            assertEquals(fixture.bees, summary.beeCount)
            assertEquals(fixture.cycles, summary.totalFlightCycleCount)
            assertEquals(fixture.cycles - fixture.openCycles, summary.completedFlightCycleCount)
            val exact = observations.observeObservationPointSummaries(territory, countFilters = ObservationPointFilterSet(
                beeCount = CountRange(fixture.bees, fixture.bees),
                flightCycleCount = CountRange(fixture.cycles, fixture.cycles),
            )).first().single()
            assertEquals(summary.id, exact.id)
            assertEquals(observationPointMarkers(listOf(summary)).single(), observationPointMarkers(listOf(exact)).single())
        }
    }

    private suspend fun pointNumbers(filters: ObservationPointFilterSet = ObservationPointFilterSet()): Set<Int> =
        observations.observeObservationPointSummaries(
            territoryId = territory,
            dateInterval = filters.dateInterval,
            countFilters = filters,
        ).first().map { it.pointNumber }.toSet()

    private data class PointFixture(
        val pointNumber: Int,
        val date: LocalDate,
        val bees: Int,
        val cycles: Int,
        val openCycles: Int,
    ) {
        val pointId: UUID get() = id("point-$pointNumber")

        fun beeId(index: Int): UUID = id("point-$pointNumber-bee-$index")

        fun cycleId(index: Int): UUID = id("point-$pointNumber-cycle-$index")

        fun toPointEntity(createdAtBase: Instant) = ObservationPointEntity(
            observationDate = date,
            id = pointId,
            territoryId = UUID.fromString("00000000-0000-0000-0000-000000000201"),
            observerId = UUID.fromString("00000000-0000-0000-0000-000000000210"),
            observationYear = date.year,
            pointNumber = pointNumber,
            beePresenceResult = if (bees == 0) BeePresenceResult.NO_BEES_FOUND else BeePresenceResult.BEES_FOUND,
            code = null,
            latitude = 56.0 + pointNumber,
            longitude = 43.0 + pointNumber,
            gpsLatitude = null,
            gpsLongitude = null,
            gpsAccuracyM = null,
            createdAt = createdAtBase.plusSeconds(pointNumber.toLong()),
            initialGroupReleaseAt = null,
            completedAt = null,
        )
    }

    private companion object {
        fun id(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))
    }
}
