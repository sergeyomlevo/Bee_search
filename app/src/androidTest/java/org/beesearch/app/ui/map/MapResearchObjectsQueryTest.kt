package org.beesearch.app.ui.map

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomPhysicalObjectRepository
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.NewLogHive
import org.beesearch.app.domain.model.ResearchDateInterval
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Данные на карте» over real records of one Territory.
 *
 * This is the integration proof of the approved map result: three types, three independent temporal
 * filters and one visibility state, all resolved by the I5 temporal query layer and assembled into
 * the existing D102 marker list.
 */
@RunWith(AndroidJUnit4::class)
class MapResearchObjectsQueryTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var observations: RoomObservationRepository
    private lateinit var objects: RoomPhysicalObjectRepository
    private val territory = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val observer = UUID.fromString("00000000-0000-0000-0000-000000000110")
    private val now = Instant.parse("2026-06-15T12:00:00Z")
    private var nextPointNumber = 0

    private val may2026 = ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))
    private val year2025 = ResearchDateInterval(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), BeeSearchDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.backupDao().insertTerritories(listOf(TerritoryEntity(territory, "T", "T", "R", "D", now, now)))
        database.backupDao().insertObservers(
            listOf(ObserverEntity(observer, "O", "Last", "First", null, null, now, now)),
        )
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        observations = RoomObservationRepository(
            database, database.territoryDao(), database.observationPointDao(), database.observerDao(),
            database.beeDao(), database.flightCycleDao(), clock,
            observationZoneIdProvider = { ZoneOffset.UTC },
        )
        objects = RoomPhysicalObjectRepository(
            database, database.physicalObjectDao(), database.physicalObjectSequenceDao(),
            database.territoryDao(), database.observerDao(), database.beeDao(), clock,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun eachTypeFollowsOnlyItsOwnFilterAndVisibilityOnlyHides() = runBlocking {
        val pointOutside = observation(LocalDate.of(2026, 4, 30))
        val pointInside = observation(LocalDate.of(2026, 5, 17))
        database.backupDao().insertObservationPoints(listOf(pointOutside, pointInside))
        val hollow = hollow(LocalDate.of(2026, 5, 5))
        val hollowUnknownDate = hollowWithUnknownDate()
        val logHive = logHive(LocalDate.of(2025, 4, 1))

        // Approved mixed state: points bounded to one month, hollows unbounded, log hives to 2025.
        val display = DEFAULT_MAP_DATA_DISPLAY
            .withPeriod(MapDataType.OBSERVATION_POINT, may2026)
            .withPeriod(MapDataType.LOG_HIVE, year2025)

        val markers = markersOf(display)
        assertEquals(
            listOf(pointInside.id, hollow.id, hollowUnknownDate.id, logHive.id),
            markers.map { it.id },
        )
        assertEquals(
            listOf(
                MapObjectType.OBSERVATION_POINT,
                MapObjectType.HOLLOW,
                MapObjectType.HOLLOW,
                MapObjectType.LOG_HIVE,
            ),
            markers.map { it.type },
        )
        assertEquals("Точка 2, пчёлы не найдены", markers.first().label)

        // Hiding hollows removes their markers and keeps their (absent) filter as it was.
        val hidden = display.withVisibility(MapDataType.HOLLOW, false)
        assertEquals(
            listOf(pointInside.id, logHive.id),
            markersOf(hidden).map { it.id },
        )
        assertEquals(display.period(MapDataType.HOLLOW), hidden.period(MapDataType.HOLLOW))

        // Bounding hollows excludes the record whose canonical fixation date is unknown; the
        // all-time state above kept it visible.
        val boundedHollows = hidden
            .withVisibility(MapDataType.HOLLOW, true)
            .withPeriod(MapDataType.HOLLOW, may2026)
        assertEquals(listOf(pointInside.id, hollow.id, logHive.id), markersOf(boundedHollows).map { it.id })
    }

    @Test
    fun everyTypeCanCarryADifferentFilterAtTheSameTime() = runBlocking {
        val point = observation(LocalDate.of(2026, 5, 17))
        val otherPoint = observation(LocalDate.of(2024, 5, 17))
        database.backupDao().insertObservationPoints(listOf(point, otherPoint))
        val hollow = hollow(LocalDate.of(2025, 8, 5))
        val logHive = logHive(LocalDate.of(2026, 5, 1))

        val display = DEFAULT_MAP_DATA_DISPLAY
            .withPeriod(MapDataType.OBSERVATION_POINT, may2026)
            .withPeriod(MapDataType.HOLLOW, year2025)
            .withPeriod(MapDataType.LOG_HIVE, may2026)

        assertEquals(
            listOf(point.id, hollow.id, logHive.id),
            markersOf(display).map { it.id },
        )
        // Clearing one type's filter does not restore or remove anything for the other types.
        val pointUnbounded = display.withResetPeriod(MapDataType.OBSERVATION_POINT)
        assertEquals(
            setOf(point.id, otherPoint.id, hollow.id, logHive.id),
            markersOf(pointUnbounded).map { it.id }.toSet(),
        )
        assertEquals(year2025, pointUnbounded.period(MapDataType.HOLLOW))
        assertEquals(may2026, pointUnbounded.period(MapDataType.LOG_HIVE))
    }

    @Test
    fun measuredFiltersAreInclusiveIndependentAndComposeWithPeriod() = runBlocking {
        val h = hollow(LocalDate.of(2026, 5, 5))
        val unknownDate = hollow(null)
        val l = logHive(LocalDate.of(2025, 4, 1))
        val filters = org.beesearch.app.domain.model.PhysicalObjectFilterSet(
            may2026,
            org.beesearch.app.domain.model.MeasurementRange(1.0, 1.0),
            org.beesearch.app.domain.model.MeasurementRange(min = 1.0),
        )
        val state = DEFAULT_MAP_DATA_DISPLAY.withFilters(MapDataType.HOLLOW, filters)
        assertEquals(listOf(h.id, l.id), markersOf(state).map { it.id })
        val noMatch = state.withFilters(MapDataType.HOLLOW, filters.copy(
            entranceHeightCm = org.beesearch.app.domain.model.MeasurementRange(min = 1.1),
        ))
        assertEquals(listOf(l.id), markersOf(noMatch).map { it.id })
        assertEquals(listOf(h.id, unknownDate.id, l.id), markersOf(state.withResetPeriod(MapDataType.HOLLOW)).map { it.id })
        assertEquals(filters, state.withVisibility(MapDataType.HOLLOW, false).display(MapDataType.HOLLOW).filters)
        assertEquals(listOf(h.id, l.id), markersOf(state.withVisibility(MapDataType.HOLLOW, false).withVisibility(MapDataType.HOLLOW, true)).map { it.id })
        val logFilter = noMatch.withFilters(MapDataType.LOG_HIVE, org.beesearch.app.domain.model.PhysicalObjectFilterSet(
            outerDiameterCm = org.beesearch.app.domain.model.MeasurementRange(max = 0.9),
        ))
        assertEquals(emptyList<UUID>(), markersOf(logFilter).map { it.id })
    }

    private suspend fun markersOf(display: MapDataDisplayState): List<MapObjectMarker> {
        val sets = mapResearchObjectFlow(
            observationRepository = observations,
            physicalObjectRepository = objects,
            territoryId = territory,
            filters = display.objectFilters(),
        ).first()
        return researchObjectMarkers(display, sets.points, sets.hollows, sets.logHives)
    }

    private fun observation(date: LocalDate): ObservationPointEntity = ObservationPointEntity(
        date, UUID.randomUUID(), territory, observer, date.year, ++nextPointNumber, null, null,
        56.0, 43.0, null, null, null, now, null, null,
    )

    private suspend fun hollow(fixationDate: LocalDate?): Hollow {
        val created = objects.createHollow(
            NewHollow(UUID.randomUUID(), territory, observer, 56.4, 43.4, HollowProperties("oak", 1.0, 0, 1.0, null, null)),
        )
        setFixationDate(created.id, fixationDate)
        return created
    }

    private suspend fun hollowWithUnknownDate(): Hollow = hollow(null)

    private suspend fun logHive(fixationDate: LocalDate?): LogHive {
        val created = objects.createLogHive(
            NewLogHive(
                UUID.randomUUID(), territory, observer, 56.6, 43.6,
                LogHiveProperties("pine", 1.0, 0, 1.0, "wood", 1.0, 1.0, null),
            ),
        )
        setFixationDate(created.id, fixationDate)
        return created
    }

    private fun setFixationDate(id: UUID, date: LocalDate?) {
        database.openHelper.writableDatabase.execSQL(
            "UPDATE physical_objects SET fixation_date = ? WHERE id = ?",
            arrayOf(date?.toString(), id.toString()),
        )
    }
}
