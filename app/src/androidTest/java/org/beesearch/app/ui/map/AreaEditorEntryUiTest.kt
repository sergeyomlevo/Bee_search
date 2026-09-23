package org.beesearch.app.ui.map

import android.net.Uri
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The участки editor panel on the map.
 *
 * The panel may appear only because the user asked for it. This test drives the real map composable,
 * because the defect was exactly that the *screen* showed the panel on a plain visit: a unit test of
 * an internal flag would not have caught it.
 */
@RunWith(AndroidJUnit4::class)
class AreaEditorEntryUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val territoryId = UUID.fromString("48ef6a6c-59d4-4405-838a-b9a40bbe32c0")
    private val area = MapArea(
        id = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d"),
        name = "Лух",
        bounds = listOf(MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0)),
    )

    /** The pending request, owned here exactly as the view model owns it. */
    private val requestToken = mutableIntStateOf(AreaEditorRequest.NO_REQUEST)

    /** Counts how often the map reported that it handled the request. */
    private val handledRequests = mutableIntStateOf(0)

    /** Stands for a map instance: a navigation round-trip produces a new one. */
    private val mapInstance = mutableIntStateOf(0)
    private val recordRequests = mutableIntStateOf(0)

    private fun showMap(store: MapAreaStore = FakeAreaStore(area)) {
        composeRule.setContent {
            Bee_searchTheme {
                key(mapInstance.intValue) {
                    BeeMap(
                        territoryId = territoryId,
                        territoryName = "DEV Territory",
                        areaStore = store,
                        packageStore = NoMapPackageStore,
                        locationState = LocationUiState.PermissionRequired,
                        locationPermissionGranted = false,
                        onRequestLocationPermission = {},
                        onRequestCreateRecord = { _, _ -> recordRequests.intValue += 1 },
                        areaEditorRequest = requestToken.intValue,
                        // The view model finishes the request; the map only reports that it handled it.
                        onAreaEditorRequestHandled = {
                            handledRequests.intValue += 1
                            requestToken.intValue = AreaEditorRequest.NO_REQUEST
                        },
                    )
                }
            }
        }
    }

    @Test
    fun aPlainVisitToTheMapShowsNoEditorPanel() {
        showMap()
        composeRule.waitForIdle()

        assertNoEditorPanel()
        assertEquals(0, handledRequests.intValue)
    }

    @Test
    fun anExplicitRequestOpensTheEditorPanelExactlyOnce() {
        showMap()
        composeRule.waitForIdle()
        assertNoEditorPanel()

        // «Создать ареал» / «Изменить участки»: the only kind of action that may open the editor.
        composeRule.runOnIdle { requestToken.intValue = 1 }
        composeRule.waitForIdle()

        assertEditorPanelShown()
        assertEquals("The handled request must be finished", AreaEditorRequest.NO_REQUEST, requestToken.intValue)
        assertEquals("The request must be handled exactly once", 1, handledRequests.intValue)
        // Finishing the request does not close the session the user is working in.
        assertEditorPanelShown()
    }

    @Test
    fun aNewAreaRequestStartsOnTheFreeMapAndOpensTheFragmentEditorExplicitly() {
        showMap(FakeAreaStore(null))
        composeRule.waitForIdle()

        composeRule.runOnIdle { requestToken.intValue = 1 }
        composeRule.waitForIdle()

        assertNoEditorPanel(allowCompactControls = true)
        composeRule.onNodeWithTag(AREA_CREATION_CONTROLS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(CREATE_COVERAGE_FRAGMENT_LABEL).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(CREATE_RECORD_DESCRIPTION).assertDoesNotExist()
        assertEquals(0, recordRequests.intValue)

        composeRule.onNodeWithText(CREATE_COVERAGE_FRAGMENT_LABEL).performClick()
        assertEditorPanelShown(isNewArea = true)

        composeRule.onNodeWithText(CANCEL_LABEL).performClick()
        assertNoEditorPanel(allowCompactControls = true)
        composeRule.onNodeWithText(CREATE_COVERAGE_FRAGMENT_LABEL).assertIsDisplayed()
    }

    @Test
    fun addingAFragmentReturnsToTheFreeMapAndCancelingTheNextKeepsIt() {
        showMap(FakeAreaStore(null))
        composeRule.waitForIdle()
        composeRule.runOnIdle { requestToken.intValue = 1 }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(CREATE_COVERAGE_FRAGMENT_LABEL).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).assertIsEnabled()
            }.isSuccess
        }
        composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).performClick()
        composeRule.waitForIdle()

        assertNoEditorPanel(allowCompactControls = true)
        composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).assertIsDisplayed()

        composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).performClick()
        assertEditorPanelShown(isNewArea = true)
        composeRule.onNodeWithText(CANCEL_LABEL).performClick()

        assertNoEditorPanel(allowCompactControls = true)
        composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).assertIsDisplayed()
    }

    @Test
    fun doneAfterTheFirstFragmentUsesTheExistingAreaNameFlow() {
        showMap(FakeAreaStore(null))
        composeRule.waitForIdle()
        composeRule.runOnIdle { requestToken.intValue = 1 }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(CREATE_COVERAGE_FRAGMENT_LABEL).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).assertIsEnabled()
            }.isSuccess
        }
        composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).performClick()
        composeRule.onNodeWithText(DONE_COVERAGE_SELECTION_LABEL).performClick()

        composeRule.onNodeWithTag(AREA_NAME_DIALOG_TAG).assertIsDisplayed()
    }

    // Regression guard:
    // a consumed editor request must never resurrect after map recreation.
    @Test
    fun aFinishedEditorSessionNeverReopensOnALaterVisit() {
        showMap()
        composeRule.waitForIdle()

        composeRule.runOnIdle { requestToken.intValue = 1 }
        composeRule.waitForIdle()
        assertEditorPanelShown()

        // The user finishes the session with «Готово» and leaves the editor.
        composeRule.onNodeWithText(DONE_COVERAGE_SELECTION_LABEL).performClick()
        composeRule.waitForIdle()
        assertNoEditorPanel()

        // Ordinary navigation from here on: every new map instance must show the normal map only.
        repeat(4) {
            composeRule.runOnIdle { mapInstance.intValue += 1 }
            composeRule.waitForIdle()
            assertNoEditorPanel()
        }
        assertEquals(1, handledRequests.intValue)
    }

    private fun assertEditorPanelShown(isNewArea: Boolean = false) {
        composeRule.onNodeWithTag(MAP_COVERAGE_SELECTION_CONTROLS_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(CLEAR_COVERAGE_LABEL).assertIsDisplayed()
        if (isNewArea) {
            composeRule.onNodeWithText(CANCEL_LABEL).assertIsDisplayed()
            composeRule.onNodeWithText(DONE_COVERAGE_SELECTION_LABEL).assertDoesNotExist()
        } else {
            composeRule.onNodeWithText(DONE_COVERAGE_SELECTION_LABEL).assertIsDisplayed()
        }
    }

    private fun assertNoEditorPanel(allowCompactControls: Boolean = false) {
        composeRule.onNodeWithTag(MAP_COVERAGE_SELECTION_CONTROLS_TAG).assertDoesNotExist()
        if (!allowCompactControls) {
            composeRule.onNodeWithText(ADD_COVERAGE_FRAGMENT_LABEL).assertDoesNotExist()
            composeRule.onNodeWithText(DONE_COVERAGE_SELECTION_LABEL).assertDoesNotExist()
        }
        composeRule.onNodeWithText(UNDO_COVERAGE_FRAGMENT_LABEL).assertDoesNotExist()
        composeRule.onNodeWithText(CLEAR_COVERAGE_LABEL).assertDoesNotExist()
    }

    /** A canonical store that answers reads and accepts a save, so the editor can be finished. */
    private class FakeAreaStore(area: MapArea?) : MapAreaStore {
        private var current: MapArea? = area

        override suspend fun load(territoryId: UUID, territoryName: String?): MapAreaReadResult =
            current?.let(MapAreaReadResult::Present) ?: MapAreaReadResult.Absent

        override suspend fun create(
            territoryId: UUID,
            name: String,
            bounds: List<MapGeoBounds>,
        ): MapAreaChangeResult = MapAreaChangeResult.Refused("тест не создаёт ареал")

        override suspend fun updateBounds(
            territoryId: UUID,
            bounds: List<MapGeoBounds>,
        ): MapAreaChangeResult {
            val updated = requireNotNull(current).copy(bounds = bounds)
            current = updated
            return MapAreaChangeResult.Saved(updated)
        }

        override suspend fun rename(territoryId: UUID, name: String): MapAreaChangeResult =
            MapAreaChangeResult.Refused("тест не переименовывает ареал")

        override suspend fun delete(territoryId: UUID): MapAreaChangeResult =
            MapAreaChangeResult.Refused("тест не удаляет ареал")

        override suspend fun clear(territoryId: UUID) {
            current = null
        }

        override suspend fun snapshot(territoryId: UUID): String? = null

        override suspend fun restore(territoryId: UUID, value: String?) = Unit
    }

    private object NoMapPackageStore : MapPackageStore {
        override suspend fun loadActive(
            territoryId: UUID,
            desiredCoverage: List<MapCoverageFragment>,
        ): MapPackageAvailability = MapPackageAvailability.Missing

        override suspend fun import(
            territoryId: UUID,
            desiredCoverage: List<MapCoverageFragment>,
            manifestUri: Uri,
            pmtilesUri: Uri,
        ): MapPackageImportResult = MapPackageImportResult.Rejected("тест не импортирует карту")

        override suspend fun clear(territoryId: UUID) = Unit
    }
}
