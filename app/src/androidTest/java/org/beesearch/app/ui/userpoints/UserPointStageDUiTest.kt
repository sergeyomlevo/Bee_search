package org.beesearch.app.ui.userpoints

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.model.UserPointPhoto
import org.beesearch.app.domain.repository.UserPointRepository
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPointStageDUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun editorEditPrefillsLongTitleAndCancelIsAvailableAfterScroll() {
        val initial = UserPointEditorState(
            title = "Старая точка",
            note = "Заметка",
            latitude = 56.1,
            longitude = 42.7,
            isEditing = true,
            isLoaded = true,
        )
        val draft = mutableStateOf(initial)
        var cancelled = false
        var location: Pair<Double, Double>? = null
        composeRule.setContent {
            Bee_searchTheme {
                UserPointEditor(
                    state = draft.value,
                    media = emptyList(),
                    titleFocusRequester = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() },
                    onTitleChanged = { draft.value = draft.value.copy(title = it) },
                    onNoteChanged = { draft.value = draft.value.copy(note = it) },
                    onChangeLocation = { location = draft.value.latitude to draft.value.longitude },
                    onPickPhoto = {}, onTakePhoto = {}, onRemovePhoto = {}, onOpenPhoto = {},
                    onSave = {}, onCancel = { cancelled = true }, onRetryLoad = {},
                )
            }
        }
        composeRule.onNodeWithText("Редактирование").assertIsDisplayed()
        composeRule.onNodeWithText("Создана:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Изменить место").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(56.1 to 42.7, location) }
        composeRule.onNodeWithTag("user-point-title").performTextInput(
            " — очень длинное русское название точки для проверки переноса текста",
        )
        capture("editor-edit-top")
        closeSoftKeyboard()
        composeRule.onNodeWithTag("user-point-cancel").performScrollTo()
            .assert(hasClickAction()).performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
        capture("editor-edit-bottom")
    }

    @Test
    fun detailDeleteCancelDoesNotMutateAndConfirmedDeleteCallsBackOnce() {
        val territoryId = UUID.randomUUID()
        val point = samplePoint(territoryId)
        val repository = FakeUserPointRepository(point)
        var deletedCallbacks = 0
        val deleted = mutableStateOf(false)
        composeRule.setContent {
            Bee_searchTheme {
                if (deleted.value) {
                    androidx.compose.material3.Text("Точка удалена")
                } else UserPointDetailRoute(
                    territoryId = territoryId, pointId = point.id,
                    repository = repository,
                    mediaStore = mediaStore(),
                    onBack = {}, onDeleted = { deletedCallbacks++; deleted.value = true },
                )
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Удалить точку").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("user-point-delete").performScrollTo().assert(hasClickAction()).performClick()
        composeRule.onNodeWithText("Отмена").performClick()
        assertEquals(0, repository.deleteCount)
        composeRule.onNodeWithTag("user-point-delete").performScrollTo().assert(hasClickAction()).performClick()
        composeRule.onNodeWithText("«Тестовая точка» будет удалена из Bee Search. Восстановить её нельзя. Исходные файлы в другой папке телефона не затрагиваются.").assertIsDisplayed()
        capture("detail-delete-confirmation")
        composeRule.onNodeWithTag("user-point-delete-confirm").performClick()
        composeRule.waitUntil(5_000) { deletedCallbacks == 1 }
        assertEquals(1, repository.deleteCount)
        capture("detail-delete-confirmed")
    }

    @Test
    fun failedDeleteLeavesCardAndRetryReopensConfirmation() {
        val territoryId = UUID.randomUUID()
        val point = samplePoint(territoryId)
        val repository = FakeUserPointRepository(point).apply { failNextDelete = true }
        composeRule.setContent {
            Bee_searchTheme {
                UserPointDetailRoute(
                    territoryId = territoryId, pointId = point.id,
                    repository = repository, mediaStore = mediaStore(), onBack = {},
                )
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Удалить точку").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("user-point-delete").performScrollTo().performClick()
        composeRule.onNodeWithTag("user-point-delete-confirm").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Не удалось удалить точку").fetchSemanticsNodes().isNotEmpty() }
        capture("detail-delete-error")
        composeRule.onNodeWithTag("user-point-delete-retry").assert(hasClickAction()).performClick()
        capture("detail-delete-retry-confirmation")
        composeRule.onNodeWithText("Восстановить её нельзя.", substring = true).assertIsDisplayed()
        repository.failNextDelete = false
        composeRule.onNodeWithTag("user-point-delete-confirm").performClick()
        composeRule.waitUntil(5_000) { repository.deleteCount == 2 }
    }

    @Test
    fun editorRouteCoordinateRevisionRoundTripKeepsDraftAndDoesNotWriteRoom() {
        val territoryId = UUID.randomUUID()
        val point = samplePoint(territoryId)
        val repository = FakeUserPointRepository(point)
        val targetState = mutableStateOf(UserPointEditorTarget(
            requestId = UUID.randomUUID(), territoryId = territoryId, pointId = point.id,
            latitude = point.latitude, longitude = point.longitude,
        ))
        composeRule.setContent {
            Bee_searchTheme {
                UserPointEditorRoute(
                    target = targetState.value, repository = repository, mediaStore = mediaStore(),
                    onCancel = {}, onSaved = {},
                    onChangeLocation = { _, _ ->
                        targetState.value = targetState.value.copy(
                            latitude = 57.0, longitude = 61.0, coordinateRevision = UUID.randomUUID(),
                        )
                    },
                )
            }
        }
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("user-point-title").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag("user-point-title").performTextInput(" — черновик")
        composeRule.onNodeWithText("Изменить место").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("user-point-coordinates").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(57.0, targetState.value.latitude, 0.0)
            assertEquals(61.0, targetState.value.longitude, 0.0)
            assertEquals(0, repository.updateCount)
        }
    }

    @Test
    fun cleanupFailureClosesCardAndRetryNeverDeletesRoomAgain() {
        val territory = UUID.randomUUID()
        val initial = samplePoint(territory)
        val photoId = UUID.randomUUID()
        // A persisted foreign path must be refused; no real file is touched by this fixture.
        val point = initial.copy(photos = listOf(UserPointPhoto(
            photoId, initial.id, "user-point-media/${UUID.randomUUID()}/$photoId", "refused.jpg",
            "image/jpeg", 1, "a".repeat(64), Instant.EPOCH,
        )))
        val repository = FakeUserPointRepository(point)
        val card = mutableStateOf(true)
        composeRule.setContent {
            Bee_searchTheme {
                UserPointCleanupNotice(repository, mediaStore())
                if (card.value) UserPointDetailRoute(territory, point.id, repository, mediaStore(),
                    onBack = {}, onDeleted = { card.value = false })
                else androidx.compose.material3.Text("Исходный список")
            }
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("user-point-delete").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("user-point-delete").performScrollTo().performClick()
        composeRule.onNodeWithText("вместе с фотографиями", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("user-point-delete-confirm").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("user-point-cleanup-notice").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnIdle { assertTrue(!card.value); assertEquals(1, repository.deleteCount) }
        capture("deleted-cleanup-pending")
        composeRule.onNodeWithTag("user-point-cleanup-retry").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, repository.deleteCount) }
        composeRule.onNodeWithTag("user-point-cleanup-close").performClick()
        composeRule.onNodeWithText("Исходный список").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(!card.value) }
    }

    private fun mediaStore() = UserPointMediaStore(
        InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
    )

    private fun capture(name: String) {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "stage-d-ui").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { output ->
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun samplePoint(territoryId: UUID) = UserPoint(
        id = UUID.randomUUID(), territoryId = territoryId, title = "Тестовая точка", note = null,
        latitude = 56.1, longitude = 42.7, createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    private class FakeUserPointRepository(initial: UserPoint) : UserPointRepository {
        private val current = MutableStateFlow<UserPoint?>(initial)
        var deleteCount = 0
        var updateCount = 0
        var failNextDelete = false
        override fun observePoints(territoryId: UUID): Flow<List<UserPoint>> = current.map { it?.let(::listOf).orEmpty() }
        override suspend fun getPoint(territoryId: UUID, id: UUID) = current.value?.takeIf { it.id == id }
        override suspend fun create(point: UserPoint) = point
        override suspend fun update(point: UserPoint) = point.also { updateCount++; current.value = it }
        override suspend fun setVisible(territoryId: UUID, id: UUID, visible: Boolean) { current.value = current.value?.copy(isVisible = visible) }
        override suspend fun delete(territoryId: UUID, id: UUID): UserPoint {
            deleteCount++
            if (failNextDelete) {
                failNextDelete = false
                error("synthetic delete failure")
            }
            return requireNotNull(current.value).also { current.value = null }
        }
    }
}
