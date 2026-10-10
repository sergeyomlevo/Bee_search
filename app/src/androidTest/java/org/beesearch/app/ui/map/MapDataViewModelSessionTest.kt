package org.beesearch.app.ui.map

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
import kotlinx.coroutines.withTimeout
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomPhysicalObjectRepository
import org.beesearch.app.domain.model.ResearchDateInterval
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The «Данные на карте» session owner.
 *
 * The owner-reported defect was not inside the panel but in where its session lived: the open flag and
 * the current filter screen were remembered inside the map screen, so anything that recreated that
 * screen closed the panel after every change. These tests pin the behaviour on `MapDataViewModel`:
 * a change inside the panel never ends the session, and only an explicit close does.
 */
@RunWith(AndroidJUnit4::class)
class MapDataViewModelSessionTest {
    private lateinit var database: BeeSearchDatabase
    /**
     * Owns the ViewModel so that clearing it cancels its scope before the database is closed: the
     * ViewModel's own flows query Room, and letting them outlive the database would fail the test with
     * a closed connection pool instead of the behaviour under test.
     */
    private val viewModelStore = ViewModelStore()
    private lateinit var viewModel: MapDataViewModel

    private val territory = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val may2026 = ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))
    private val year2026 = ResearchDateInterval(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))

    private class InMemoryDisplayStore : MapDataDisplayStore {
        private val saved = mutableMapOf<UUID, MapDataDisplayState>()

        override suspend fun load(territoryId: UUID): MapDataDisplayState =
            saved[territoryId] ?: DEFAULT_MAP_DATA_DISPLAY

        override suspend fun save(territoryId: UUID, state: MapDataDisplayState) {
            saved[territoryId] = state
        }
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), BeeSearchDatabase::class.java,
        ).allowMainThreadQueries().build()
        val clock = Clock.fixed(Instant.parse("2026-10-10T09:00:00Z"), ZoneOffset.UTC)
        val observations = RoomObservationRepository(
            database, database.territoryDao(), database.observationPointDao(),
            database.observerDao(), database.beeDao(), database.flightCycleDao(), clock,
            observationZoneIdProvider = { ZoneOffset.UTC },
        )
        val objects = RoomPhysicalObjectRepository(
            database, database.physicalObjectDao(), database.physicalObjectSequenceDao(),
            database.territoryDao(), database.observerDao(), database.beeDao(), clock,
        )
        viewModel = ViewModelProvider(
            viewModelStore,
            MapDataViewModel.factory(observations, objects, InMemoryDisplayStore()),
        )[MapDataViewModel::class.java]
        viewModel.setTerritory(territory)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        database.close()
    }

    /**
     * Waits for the state the assertion is about.
     *
     * The UI state is built from flows on the ViewModel scope, so an assertion has to await the value
     * it expects instead of reading whatever the flow happens to hold at that instant.
     */
    private suspend fun awaitState(predicate: (MapDataUiState) -> Boolean): MapDataUiState =
        withTimeout(TIMEOUT_MILLIS) { viewModel.uiState.first(predicate) }

    @Test
    fun theSessionStartsClosedAndOpensAtTheTypeList() = runBlocking {
        assertFalse(awaitState { !it.panelOpen }.panelOpen)

        viewModel.openPanel()

        val opened = awaitState { it.panelOpen }
        assertNull("the panel always opens at its type list", opened.openedType)
    }

    @Test
    fun changesInsideThePanelNeverEndTheSession() = runBlocking {
        viewModel.openPanel()
        viewModel.openTypeFilters(MapDataType.HOLLOW)

        viewModel.setVisible(MapDataType.OBSERVATION_POINT, false)
        val afterVisibility = awaitState { !it.display.isVisible(MapDataType.OBSERVATION_POINT) }
        assertTrue("a visibility switch keeps the panel open", afterVisibility.panelOpen)
        assertEquals(
            "and keeps the filter screen the user is on",
            MapDataType.HOLLOW,
            afterVisibility.openedType,
        )

        viewModel.setPeriod(MapDataType.HOLLOW, may2026)
        val afterPeriod = awaitState { it.display.period(MapDataType.HOLLOW) == may2026 }
        assertTrue("a period change keeps the panel open", afterPeriod.panelOpen)
        assertEquals(MapDataType.HOLLOW, afterPeriod.openedType)

        viewModel.resetPeriod(MapDataType.HOLLOW)
        val afterReset = awaitState { it.display.period(MapDataType.HOLLOW) == null }
        assertTrue("the in-section reset keeps the panel open", afterReset.panelOpen)
        assertFalse(
            "and never changes visibility",
            afterReset.display.isVisible(MapDataType.OBSERVATION_POINT),
        )

        viewModel.setPeriod(MapDataType.HOLLOW, may2026)
        viewModel.resetFilters()
        val afterGlobalReset = awaitState { it.display.period(MapDataType.HOLLOW) == null }
        assertTrue("the global reset keeps the panel open", afterGlobalReset.panelOpen)
        assertEquals(MapDataType.HOLLOW, afterGlobalReset.openedType)
        assertFalse(afterGlobalReset.display.isVisible(MapDataType.OBSERVATION_POINT))
    }

    @Test
    fun aTypeScreenReturnsToTheListAndOnlyAnExplicitCloseEndsTheSession() = runBlocking {
        viewModel.openPanel()
        viewModel.openTypeFilters(MapDataType.LOG_HIVE)
        assertEquals(MapDataType.LOG_HIVE, awaitState { it.openedType == MapDataType.LOG_HIVE }.openedType)

        // The type screen's Back and its «Готово» are the same call: back to the list, panel stays.
        viewModel.closeTypeFilters()
        val backInList = awaitState { it.openedType == null && it.panelOpen }
        assertTrue(backInList.panelOpen)

        viewModel.closePanel()
        val closed = awaitState { !it.panelOpen }
        assertNull(closed.openedType)
    }

    @Test
    fun reopeningAlwaysStartsAtTheListEvenAfterAFilterScreenWasLeftOpen() = runBlocking {
        viewModel.openPanel()
        viewModel.openTypeFilters(MapDataType.HOLLOW)
        awaitState { it.openedType == MapDataType.HOLLOW }
        viewModel.closePanel()
        awaitState { !it.panelOpen }

        viewModel.openPanel()

        val reopened = awaitState { it.panelOpen }
        assertNull("a new opening never resumes the previous type screen", reopened.openedType)
    }

    @Test
    fun visibilityAndPeriodsStayIndependentForEveryType() = runBlocking {
        viewModel.openPanel()
        viewModel.setPeriod(MapDataType.OBSERVATION_POINT, may2026)
        viewModel.setVisible(MapDataType.HOLLOW, false)
        viewModel.setPeriod(MapDataType.LOG_HIVE, year2026)

        val display = awaitState {
            it.display.period(MapDataType.OBSERVATION_POINT) == may2026 &&
                it.display.period(MapDataType.LOG_HIVE) == year2026 &&
                !it.display.isVisible(MapDataType.HOLLOW)
        }.display

        assertEquals(may2026, display.period(MapDataType.OBSERVATION_POINT))
        assertNull(display.period(MapDataType.HOLLOW))
        assertTrue(display.isVisible(MapDataType.OBSERVATION_POINT))
        assertTrue(display.isVisible(MapDataType.LOG_HIVE))
        assertEquals(year2026, display.period(MapDataType.LOG_HIVE))

        // Showing the type again returns its own state and changes nothing else.
        viewModel.setVisible(MapDataType.HOLLOW, true)
        val shown = awaitState { it.display.isVisible(MapDataType.HOLLOW) }.display
        assertEquals(may2026, shown.period(MapDataType.OBSERVATION_POINT))
        assertEquals(year2026, shown.period(MapDataType.LOG_HIVE))
    }

    @Test
    fun losingTheTerritoryClearsTheSessionAndTheDisplayedState() = runBlocking {
        viewModel.openPanel()
        viewModel.openTypeFilters(MapDataType.HOLLOW)
        viewModel.setPeriod(MapDataType.HOLLOW, may2026)
        awaitState { it.display.period(MapDataType.HOLLOW) == may2026 }

        viewModel.setTerritory(null)

        val cleared = awaitState { it.display == DEFAULT_MAP_DATA_DISPLAY && !it.panelOpen }
        assertNull(cleared.openedType)
        assertNull(cleared.selectedObjectId)
        assertTrue(cleared.markers.isEmpty())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
