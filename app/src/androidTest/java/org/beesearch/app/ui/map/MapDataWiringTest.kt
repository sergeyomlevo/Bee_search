package org.beesearch.app.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomPhysicalObjectRepository
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.ResearchDateInterval
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The panel as the map screen actually mounts it.
 *
 * `MapDataPanelTest` drives the panel's own behaviour with a local harness; this test drives the
 * production wiring (`MapDataSurface` + the real `MapDataViewModel`) instead, so a mis-wiring between
 * the screen and the ViewModel cannot pass unnoticed — exactly the layer the owner-reported defect
 * ("the panel closes after every change") touched.
 */
@RunWith(AndroidJUnit4::class)
class MapDataWiringTest {
    @get:Rule
    val rule = createComposeRule()

    private val viewModelStore = ViewModelStore()
    private lateinit var database: BeeSearchDatabase
    private lateinit var viewModel: MapDataViewModel

    private val territory = UUID.fromString("00000000-0000-0000-0000-000000000301")
    private val observer = UUID.fromString("00000000-0000-0000-0000-000000000302")
    private val now = Instant.parse("2026-10-10T09:00:00Z")
    private val year2026 = ResearchDateInterval(
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 12, 31),
    )

    private class InMemoryDisplayStore : MapDataDisplayStore {
        override suspend fun load(territoryId: UUID): MapDataDisplayState = DEFAULT_MAP_DATA_DISPLAY

        override suspend fun save(territoryId: UUID, state: MapDataDisplayState) = Unit
    }

    /**
     * Builds the real object graph of the map screen: an in-memory Room database, the production
     * repositories, the display store and the session owner the panel is wired to.
     *
     * [seedHollow] mirrors a Territory that already has a Hollow with a canonical fixation date, so the
     * calendar has a year to offer — the calendar is built from real records, never from invented ones.
     */
    private suspend fun startViewModel(seedHollow: Boolean = false, seedAdditionalHollow: Boolean = false) {
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
        if (seedHollow) {
            database.backupDao().insertTerritories(
                listOf(TerritoryEntity(territory, "I6T", "I6 territory", "R", "D", now, now)),
            )
            database.backupDao().insertObservers(
                listOf(ObserverEntity(observer, "I6O", "Иванов", "Иван", null, null, now, now)),
            )
            val hollow = objects.createHollow(
                NewHollow(
                    UUID.randomUUID(), territory, observer, 56.1, 42.7,
                    HollowProperties("дуб", 180.0, 123, 40.0, null, null),
                ),
            )
            database.openHelper.writableDatabase.execSQL(
                "UPDATE physical_objects SET fixation_date = ? WHERE id = ?",
                arrayOf("2026-05-05", hollow.id.toString()),
            )
            if (seedAdditionalHollow) {
                objects.createHollow(NewHollow(UUID.randomUUID(), territory, observer, 56.2, 42.8,
                    HollowProperties("дуб", 200.0, 124, 45.0, null, null)))
            }
        }
        viewModel = ViewModelProvider(
            viewModelStore,
            MapDataViewModel.factory(observations, objects, InMemoryDisplayStore()),
        )[MapDataViewModel::class.java]
        viewModel.setTerritory(territory)
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        if (::database.isInitialized) database.close()
    }

    @Test
    fun aSwitchAndAPeriodKeepThePanelOpenThroughTheProductionWiring() = runBlocking {
        startViewModel(seedHollow = true)
        rule.setContent { WiredScreen() }

        rule.runOnIdle { viewModel.openPanel() }
        node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()

        // A visibility change travels through MapDataSurface to the session owner and back.
        node("map-data-visibility-HOLLOW").performClick()
        node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
        assertFalse(awaitState { !it.display.isVisible(MapDataType.HOLLOW) }.display.isVisible(MapDataType.HOLLOW))

        // So does a period choice, and the editor stays where it is.
        node("map-data-type-HOLLOW").performClick()
        node("period-years-HOLLOW").performScrollTo()
        node("2026").performClick()
        node("period-editor-HOLLOW").assertIsDisplayed()
        assertEquals(year2026, awaitState { it.display.period(MapDataType.HOLLOW) != null }.display.period(MapDataType.HOLLOW))
    }

    @Test
    fun theTypeScreenDoneReturnsToTheListAndThePanelDoneClosesIt() = runBlocking {
        startViewModel()
        rule.setContent { WiredScreen() }
        rule.runOnIdle { viewModel.openPanel() }
        node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()

        node("map-data-type-HOLLOW").performClick()
        node("map-data-type-screen-HOLLOW").assertIsDisplayed()
        node("filter-done-HOLLOW").performClick()

        // The production wiring returns to the list and keeps the sheet mounted.
        node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
        node("map-data-type-screen-HOLLOW").assertDoesNotExist()
        assertTrue(awaitState { it.openedType == null && it.panelOpen }.panelOpen)

        node(MAP_DATA_DONE_TAG).performClick()
        rule.waitForIdle()
        assertFalse(awaitState { !it.panelOpen }.panelOpen)
        node(MAP_DATA_CONTENT_TAG).assertDoesNotExist()
    }

    @Test
    fun thePanelShowsEveryAvailableTypeWithItsOwnSummary() = runBlocking {
        startViewModel()
        rule.setContent { WiredScreen() }
        rule.runOnIdle { viewModel.openPanel() }

        MapDataType.entries.forEach { type ->
            node("map-data-visibility-${type.name}").assertIsDisplayed()
            node("map-data-summary-${type.name}").assertTextEquals("Все данные")
        }
    }

    @Composable
    private fun WiredScreen() {
        val state = viewModel.uiState.collectAsStateWithLifecycle().value
        MapFirstScaffold(
            onOpenObjects = {},
            onOpenSettings = {},
            onOpenMapData = viewModel::openPanel,
            mapDataOpen = state.panelOpen,
            mapDataRestricted = state.display.hasActiveRestriction,
        ) { mapModifier ->
            Box(mapModifier) {
                if (state.panelOpen) {
                    MapDataSurface(state = state, viewModel = viewModel)
                }
            }
        }
    }

    @Test
    fun toolbarVisualizationOpensExistingSessionAndCanReopenAfterDone() = runBlocking {
        startViewModel()
        rule.setContent { WiredScreen() }
        node("open-map-data").performClick()
        node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
        node("map-data-visibility-HOLLOW").performClick()
        assertTrue(awaitState { !it.display.isVisible(MapDataType.HOLLOW) }.panelOpen)
        node("map-data-type-HOLLOW").performClick()
        node("filter-done-HOLLOW").performClick()
        node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
        node(MAP_DATA_DONE_TAG).performClick()
        assertFalse(awaitState { !it.panelOpen }.panelOpen)
        node("open-map-data").performClick()
        node(MAP_DATA_CONTENT_TAG).assertIsDisplayed()
        assertFalse(awaitState { it.panelOpen }.display.isVisible(MapDataType.HOLLOW))
    }

    @Test
    fun recordRoundTripKeepsViewportFiltersAndSelectionButExcludedIdentityCannotReopen() = runBlocking {
        startViewModel(seedHollow = true, seedAdditionalHollow = true)
        rule.setContent { WiredScreen() }
        val markers = awaitState { it.markers.size == 2 }.markers
        val marker = markers.first()
        rule.runOnIdle { viewModel.setPeriod(MapDataType.HOLLOW, year2026) }
        awaitState { it.display.period(MapDataType.HOLLOW) == year2026 && it.markers.isNotEmpty() }
        rule.runOnIdle { viewModel.toggleSelection(marker.id) }
        val before = awaitState { it.selectedMarker != null }
        assertEquals(marker, before.selectedMarker)
        rule.runOnIdle { viewModel.toggleSelection(markers.last().id) }
        assertEquals(markers.last(), awaitState { it.selectedObjectId == markers.last().id }.selectedMarker)
        rule.runOnIdle { viewModel.toggleSelection(marker.id) }
        awaitState { it.selectedObjectId == marker.id }
        val camera = MapCameraContext(56.15, 42.75, 17.25, 23.0, 12.0)
        rule.runOnIdle {
            viewModel.saveCamera(territory, camera)
            // MainMapScreen re-enters with the same Territory after the existing record route.
            viewModel.setTerritory(territory)
        }
        assertEquals(before.display, viewModel.uiState.value.display)
        assertEquals(marker, viewModel.uiState.value.selectedMarker)
        assertEquals(camera, viewModel.cameraFor(territory))
        assertEquals(null, viewModel.cameraFor(UUID.randomUUID()))
        rule.runOnIdle { viewModel.closePreview() }
        val closed = awaitState { it.selectedMarker == null }
        assertEquals(before.display, closed.display)
        rule.runOnIdle {
            viewModel.setVisible(MapDataType.HOLLOW, false)
            viewModel.toggleSelection(marker.id)
        }
        val hidden = awaitState { it.markers.isEmpty() && !it.display.isVisible(MapDataType.HOLLOW) }
        assertEquals(null, hidden.selectedMarker)
        assertEquals(null, hidden.selectedObjectId)
        assertEquals(year2026, hidden.display.period(MapDataType.HOLLOW))
    }

    private suspend fun awaitState(predicate: (MapDataUiState) -> Boolean): MapDataUiState =
        withTimeout(TIMEOUT_MILLIS) { viewModel.uiState.first(predicate) }

    /**
     * Tag lookup in the unmerged tree: a tagged child of a clickable row is merged into its parent in
     * the merged tree, exactly as in `MapDataPanelTest`.
     */
    private fun node(tag: String): SemanticsNodeInteraction =
        rule.onNodeWithTag(tag, useUnmergedTree = true)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
