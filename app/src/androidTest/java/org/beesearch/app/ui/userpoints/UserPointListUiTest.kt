package org.beesearch.app.ui.userpoints

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID

class UserPointListUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val territoryId = UUID.randomUUID()

    private fun point(title: String, visible: Boolean = true): UserPoint = UserPoint(
        id = UUID.randomUUID(),
        territoryId = territoryId,
        title = title,
        note = null,
        latitude = 56.8,
        longitude = 60.6,
        isVisible = visible,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private class PageHarness(initialState: UserPointDisplayState) {
        val state = mutableStateOf(initialState)
        var onBack: () -> Unit = {}
        var onOpen: (UUID) -> Unit = {}
        var onToggle: (UUID, Boolean) -> Unit = { _, _ -> }
        var onRetryRead: () -> Unit = {}
        var onRetryVisibility: (UUID) -> Unit = {}
    }

    private fun setPage(
        state: UserPointDisplayState,
        onBack: () -> Unit = {},
        onOpen: (UUID) -> Unit = {},
        onToggle: (UUID, Boolean) -> Unit = { _, _ -> },
        onRetryRead: () -> Unit = {},
        onRetryVisibility: (UUID) -> Unit = {},
    ): PageHarness {
        val page = PageHarness(state).apply {
            this.onBack = onBack
            this.onOpen = onOpen
            this.onToggle = onToggle
            this.onRetryRead = onRetryRead
            this.onRetryVisibility = onRetryVisibility
        }
        rule.setContent {
            Bee_searchTheme {
                UserPointListPage(
                    state = page.state.value,
                    onBack = page.onBack,
                    onOpen = page.onOpen,
                    onToggle = page.onToggle,
                    onRetryRead = page.onRetryRead,
                    onRetryVisibility = page.onRetryVisibility,
                )
            }
        }
        return page
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.onNodeWithTag("user-point-list-header").assertIsDisplayed()
        val bitmap = requireNotNull(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val folder = File(context.getExternalFilesDir(null), "my-points-stage-c").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test
    fun titleAndSwitchAreIndependentTargets() {
        val item = point("Лесная поляна")
        val opened = mutableListOf<UUID>()
        val toggled = mutableListOf<Pair<UUID, Boolean>>()
        setPage(
            UserPointDisplayState(territoryId = territoryId, loading = false, points = listOf(item)),
            onOpen = { opened += it },
            onToggle = { id, visible -> toggled += id to visible },
        )

        rule.onNodeWithTag("user-point-title-${item.id}").performClick()
        rule.onNodeWithTag("user-point-visibility-${item.id}").performClick()

        rule.runOnIdle {
            assertEquals(listOf(item.id), opened)
            assertEquals(listOf(item.id to false), toggled)
        }
    }

    @Test
    fun loadingReadErrorAndEmptyHaveDistinctStates() {
        val page = setPage(UserPointDisplayState(territoryId = territoryId, loading = true))
        rule.onNodeWithTag("user-point-list-loading").assertIsDisplayed()
        rule.onNodeWithText("Нет сохранённых точек").assertDoesNotExist()

        var retries = 0
        page.onRetryRead = { retries++ }
        page.state.value = UserPointDisplayState(
            territoryId = territoryId,
            loading = false,
            error = "read failed",
        )
        rule.onNodeWithText("Не удалось загрузить Мои точки").assertIsDisplayed()
        rule.onNodeWithText("Повторить").performClick()
        rule.runOnIdle { assertEquals(1, retries) }

        page.state.value = UserPointDisplayState(territoryId = territoryId, loading = false)
        rule.onNodeWithText("Нет сохранённых точек").assertIsDisplayed()
        rule.onNodeWithText("Создать").assertDoesNotExist()
    }

    @Test
    fun longNamesScrollAndMixedVisibilityRowsReachTheBottom() {
        val points = List(8) { index ->
            point(
                if (index == 7) "Очень длинное название пользовательской точки для проверки переноса и прокрутки"
                else "Точка ${index + 1}",
                visible = index % 3 != 0,
            )
        }
        val second = points.last()
        setPage(
            UserPointDisplayState(
                territoryId = territoryId,
                loading = false,
                points = points,
            ),
        )

        capture("d-list-long-names")
        rule.onNodeWithTag("user-point-list").performScrollToNode(hasText(second.title))
        capture("d-list-bottom")
        rule.onNodeWithText(second.title).assertIsDisplayed()
    }

    @Test
    fun writingDisablesOnlyTheAffectedSwitch() {
        val first = point("Первая точка")
        val second = point("Вторая точка")
        setPage(
            UserPointDisplayState(
                territoryId = territoryId,
                loading = false,
                points = listOf(first, second),
                writing = setOf(first.id),
            ),
        )

        rule.onNodeWithTag("user-point-visibility-${first.id}").assertIsDisplayed()
            .assertIsNotEnabled()
        rule.onNodeWithTag("user-point-visibility-${second.id}").assertIsEnabled()
    }

    @Test
    fun listStateCanBeCapturedWithExistingMechanism() {
        val points = List(7) { index -> point("Снимок списка ${index + 1}", visible = index % 2 == 0) }
        setPage(UserPointDisplayState(territoryId = territoryId, loading = false, points = points))
        capture("d-list")
    }

    @Test
    fun emptyAndVisibilityErrorStatesCanBeCaptured() {
        val page = setPage(UserPointDisplayState(territoryId = territoryId, loading = false))
        capture("d-empty")

        val item = point("Точка с ошибкой", visible = false)
        var retried: UUID? = null
        page.onRetryVisibility = { retried = it }
        page.state.value = UserPointDisplayState(
            territoryId = territoryId,
            loading = false,
            points = listOf(item),
            writeErrors = mapOf(item.id to true),
        )
        rule.onNodeWithText("Не удалось сохранить видимость").assertIsDisplayed()
        rule.onNodeWithTag("user-point-visibility-${item.id}").assertIsOff().assertIsEnabled()
        rule.onNodeWithTag("user-point-retry-visibility-${item.id}").performClick()
        rule.runOnIdle { assertEquals(item.id, retried) }
        capture("d-visibility-error")
    }
}
