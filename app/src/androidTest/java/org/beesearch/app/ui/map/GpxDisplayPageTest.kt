package org.beesearch.app.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.click
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GpxDisplayPageTest {
    @get:Rule
    val rule = createComposeRule()

    private var contentSet = false
    private val pageState = androidx.compose.runtime.mutableStateOf(GpxDisplayUiState())
    private val font = androidx.compose.runtime.mutableStateOf(1f)
    private var toggle: (UUID, Boolean) -> Unit = { _, _ -> }
    private var retryCallback: (UUID?) -> Unit = {}
    private var deleteCallback: (UUID) -> Unit = {}
    private var retryDeleteCallback: (UUID) -> Unit = {}
    private val firstId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val secondId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun emptyAndLoadingAreDistinctStates() {
        setPage(GpxDisplayUiState(loading = true))
        rule.onNodeWithTag("gpx-display-loading").assertIsDisplayed()
        rule.onNodeWithText("Нет сохранённых GPX").assertDoesNotExist()

        setPage(GpxDisplayUiState(loading = false))
        rule.onNodeWithText("Нет сохранённых GPX").assertIsDisplayed()
        rule.onNodeWithTag("gpx-display-loading").assertDoesNotExist()
    }

    @Test
    fun togglingRowReportsIdAndNewValue() {
        var changed: Pair<UUID, Boolean>? = null
        setPage(
            GpxDisplayUiState(
                loading = false,
                rows = listOf(GpxDisplayRow(firstId, "Лесная дорога.gpx", selected = true)),
            ),
            onToggle = { id, visible -> changed = id to visible },
        )

        rule.onNodeWithTag("gpx-display-row-$firstId").assertIsOn().performClick()
        assertEquals(firstId to false, changed)
    }

    @Test
    fun rowErrorOffersRetryAndPageErrorUsesCatalogRetry() {
        var retry: UUID? = secondId
        setPage(
            GpxDisplayUiState(
                loading = false,
                rows = listOf(GpxDisplayRow(secondId, "К реке.gpx", error = "Не удалось показать трек")),
            ),
            onRetry = { id -> retry = id },
        )
        rule.onNodeWithText("Не удалось показать трек").assertIsDisplayed()
        rule.onNodeWithText("Повторить").performClick()
        assertEquals(secondId, retry)

        setPage(GpxDisplayUiState(loading = false, error = "Не удалось загрузить GPX"), onRetry = { id -> retry = id })
        rule.onNodeWithText("Не удалось загрузить GPX").assertIsDisplayed()
        rule.onNodeWithText("Повторить").performClick()
        assertEquals(null, retry)
    }

    @Test
    fun longPressOpensDeleteMenuAndCancelPreservesRow() {
        var deleted: UUID? = null
        setPage(
            GpxDisplayUiState(loading = false, rows = listOf(GpxDisplayRow(firstId, "Лесная дорога.gpx"))),
            onDelete = { deleted = it },
            fontScale = 1.7f,
        )
        rule.onNodeWithText("Лесная дорога.gpx").performTouchInput { longClick() }
        screenshot("delete-menu-font17.png")
        rule.onNodeWithText("Удалить").performClick()
        rule.onNodeWithText("Удалить трек?").assertIsDisplayed()
        rule.onNodeWithText("Отмена").performClick()
        rule.onNodeWithText("Удалить трек?").assertDoesNotExist()
        rule.onNodeWithTag("gpx-display-row-$firstId").assertIsDisplayed()
        assertEquals(null, deleted)
    }

    @Test
    fun accessibilityDeleteActionUsesSameConfirmationAndConfirmedId() {
        var deleted: UUID? = null
        setPage(
            GpxDisplayUiState(loading = false, rows = listOf(GpxDisplayRow(firstId, "Маршрут с длинным названием.gpx"))),
            onDelete = { deleted = it },
            fontScale = 1.7f,
        )
        val deleteAction = rule.onNodeWithTag("gpx-display-row-$firstId").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first { it.label == "Удалить трек" }
        rule.runOnIdle { deleteAction.action() }
        rule.onNodeWithText("Удалить трек?").assertIsDisplayed()
        rule.onNodeWithText("Удалить").performClick()
        assertEquals(firstId, deleted)
    }

    @Test
    fun changingTerritoryClosesPendingDeleteWithoutCallingOldId() {
        var deleted: UUID? = null
        setPage(
            GpxDisplayUiState(
                territoryId = firstId,
                loading = false,
                rows = listOf(GpxDisplayRow(secondId, "Лесная дорога.gpx")),
            ),
            onDelete = { deleted = it },
        )
        val deleteAction = rule.onNodeWithTag("gpx-display-row-$secondId").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first { it.label == "Удалить трек" }
        rule.runOnIdle { deleteAction.action() }
        rule.onNodeWithText("Удалить трек?").assertIsDisplayed()
        setPage(
            GpxDisplayUiState(
                territoryId = UUID(0, 99),
                loading = false,
                rows = listOf(GpxDisplayRow(secondId, "Другой маршрут.gpx")),
            ),
        )
        rule.onNodeWithText("Удалить трек?").assertDoesNotExist()
        assertEquals(null, deleted)
    }

    @Test
    fun busyDeleteRowRejectsToggleAndSecondLongPress() {
        var toggled = false
        setPage(
            GpxDisplayUiState(
                loading = false,
                rows = listOf(GpxDisplayRow(firstId, "Лесная дорога.gpx", selected = true, deleting = true)),
            ),
            onToggle = { _, _ -> toggled = true },
        )
        rule.onNodeWithTag("gpx-display-row-$firstId").performTouchInput { click() }
        rule.onNodeWithTag("gpx-display-row-$firstId").performTouchInput { longClick() }
        rule.onNodeWithText("Удалить").assertDoesNotExist()
        assertEquals(false, toggled)
    }

    @Test
    fun deleteErrorRetryReopensConfirmationForSameId() {
        var retried: UUID? = null
        setPage(
            GpxDisplayUiState(
                loading = false,
                rows = listOf(GpxDisplayRow(firstId, "Лесная дорога.gpx", deleteError = "Не удалось удалить трек")),
            ),
            onRetryDelete = { retried = it },
            fontScale = 1.7f,
        )
        screenshot("delete-error-font17.png")
        rule.onNodeWithText("Повторить").performClick()
        rule.onNodeWithText("Удалить трек?").assertIsDisplayed()
        screenshot("delete-dialog-font17.png")
        rule.onNodeWithText("Удалить").performClick()
        assertEquals(firstId, retried)
    }

    @Test
    fun dialogBackDismissesWithoutDeleting() {
        var deleted: UUID? = null
        setPage(
            GpxDisplayUiState(loading = false, rows = listOf(GpxDisplayRow(firstId, "Лесная дорога.gpx"))),
            onDelete = { deleted = it },
            fontScale = 1.7f,
        )
        val deleteAction = rule.onNodeWithTag("gpx-display-row-$firstId").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first { it.label == "Удалить трек" }
        rule.runOnIdle { deleteAction.action() }
        rule.onNodeWithText("Удалить трек?").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText("Удалить трек?").assertDoesNotExist()
        rule.onNodeWithTag("gpx-display-row-$firstId").assertIsDisplayed()
        assertEquals(null, deleted)
    }

    @Test
    fun longListRemainsReachableAtFontScale17() {
        setPage(
            GpxDisplayUiState(
                loading = false,
                rows = listOf(
                    GpxDisplayRow(firstId, "Маршрут вдоль лесного участка с длинным именем.gpx"),
                ) + (3..30).map { GpxDisplayRow(UUID(0,it.toLong()), "Сохранённый маршрут $it.gpx") } +
                    GpxDisplayRow(secondId, "Лесная дорога.gpx", selected = true),
            ),
            fontScale = 1.7f,
        )
        rule.onNodeWithTag("gpx-display-list").assertIsDisplayed()
        rule.onNodeWithText("Маршрут вдоль лесного участка с длинным именем.gpx").assertTextContains("лесного", substring = true)
        rule.onNodeWithTag("gpx-display-list").performScrollToNode(hasTestTag("gpx-display-row-$secondId"))
        rule.onNodeWithTag("gpx-display-row-$secondId").assertIsDisplayed()
    }

    @Test
    fun approvedSheetAtSystemFontScale17ScreenshotsAndSystemBack() {
        val rows = androidx.compose.runtime.mutableStateOf(listOf(
            GpxDisplayRow(firstId,"Маршрут вдоль лесного участка.gpx",selected=true),
            GpxDisplayRow(secondId,"К реке.gpx"),
            GpxDisplayRow(UUID(0,3),"Лесная дорога.gpx",selected=true),
        ))
        rule.setContent {
            org.beesearch.app.ui.theme.Bee_searchTheme {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    MapDataPanel(
                        state=MapDataUiState(today=java.time.LocalDate.of(2026,10,19),gpxOpen=true),
                        onVisibilityChange={_,_->},onPeriodChange={_,_->},onResetPeriod={},
                        onResetFilters={},onOpenType={},onCloseType={},onDismiss={},
                        gpxDisplayState=GpxDisplayUiState(loading=false,rows=rows.value),
                    )
                }
            }
        }
        rule.onNodeWithTag("gpx-show-track-action").performClick()
        rule.onNodeWithTag("gpx-display-row-${UUID(0,3)}").assertIsDisplayed()
        screenshot("panel-font17.png")
        rule.onNodeWithText("Лесная дорога.gpx").performTouchInput { longClick() }
        screenshot("sheet-delete-menu-font17.png")
        rule.onNodeWithText("Удалить").performClick()
        rule.onNodeWithText("Удалить трек?").assertIsDisplayed()
        screenshot("sheet-delete-dialog-font17.png")
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText("Удалить трек?").assertDoesNotExist()
        rule.runOnIdle {
            rows.value = rows.value.map { if (it.id == UUID(0,3)) it.copy(deleteError = "Не удалось удалить трек") else it }
        }
        rule.onNodeWithText("Не удалось удалить трек").assertIsDisplayed()
        screenshot("sheet-delete-error-font17.png")
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithTag("gpx-tracks-page").assertIsDisplayed()
        rule.runOnIdle { rows.value=emptyList() }
        rule.onNodeWithTag("gpx-show-track-action").performClick()
        rule.onNodeWithText("Нет сохранённых GPX").assertIsDisplayed()
        screenshot("empty-font17.png")
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        // UiAutomation observes the rendered window, after popup/dialog animations and draw.
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        android.os.SystemClock.sleep(400)
        val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val folder=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"d106-gpx-display").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(folder,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }

    private fun setPage(
        state: GpxDisplayUiState,
        onToggle: (UUID, Boolean) -> Unit = { _, _ -> },
        onRetry: (UUID?) -> Unit = {},
        onDelete: (UUID) -> Unit = {},
        onRetryDelete: (UUID) -> Unit = {},
        fontScale: Float = 1f,
    ) {
        if (contentSet) {
            rule.runOnIdle {
                pageState.value = state; font.value = fontScale; toggle = onToggle; retryCallback = onRetry
                deleteCallback = onDelete; retryDeleteCallback = onRetryDelete
            }
            return
        }
        contentSet = true
        pageState.value = state; font.value = fontScale; toggle = onToggle; retryCallback = onRetry
        deleteCallback = onDelete; retryDeleteCallback = onRetryDelete
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, font.value)) {
                GpxDisplayPage(
                    state = pageState.value,
                    onBack = {},
                    onToggle = { id, visible -> toggle(id, visible) },
                    onRetry = { retryCallback(it) },
                    onDeleteConfirmed = { deleteCallback(it) },
                    onRetryDelete = { retryDeleteCallback(it) },
                    modifier = Modifier.fillMaxSize().padding(bottom = 16.dp),
                )
            }
        }
    }
}
