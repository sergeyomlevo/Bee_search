package org.beesearch.app.ui.userpoints

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.data.repository.RoomUserPointRepository
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.repository.UserPointRepository
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.*
import org.junit.Assert.*

class UserPointCardMapActionTest {
    @get:Rule val rule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val territory = UUID.randomUUID()
    private val database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).build()
    private val actual = RoomUserPointRepository(database)
    private val media = UserPointMediaStore(context.filesDir, context.cacheDir)
    private var failWrite = false
    private var writes = 0
    private val repository = object : UserPointRepository by actual {
        override suspend fun setVisible(territoryId: UUID, id: UUID, visible: Boolean) {
            writes++
            if (failWrite) throw IOException("test write failure")
            actual.setVisible(territoryId, id, visible)
        }
    }
    @Before fun prepare() = runBlocking {
        database.backupDao().insertTerritories(listOf(TerritoryEntity(territory, "Card test", "Card test", "R", "D", Instant.EPOCH, Instant.EPOCH)))
    }
    @After fun close() = database.close()
    private fun point() = runBlocking { actual.create(UserPoint(UUID.randomUUID(), territory,
        "Тропа к лесной поляне", "Заметка остаётся", 56.8, 60.6, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)) }

    @Test fun offFailureKeepsCardAndNoCameraThenRetryPersistsAndClosesOnce() {
        val original = point()
        runBlocking { actual.setVisible(territory, original.id, false) }
        failWrite = true
        var requests = 0
        var card by mutableStateOf(true)
        rule.setContent { Bee_searchTheme {
            if (card) UserPointDetailRoute(territory, original.id, repository, media, onBack = { card = false }, onShowOnMap = {
                assertTrue(it.isVisible); requests++; card = false
            }) else Text("Карта")
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("user-point-show-on-map").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("user-point-show-on-map").performScrollTo().assertIsEnabled().performClick()
        rule.onNodeWithText("Не удалось сохранить видимость").assertIsDisplayed()
        assertEquals(0, requests)
        assertFalse(runBlocking { actual.getPoint(territory, original.id) }!!.isVisible)
        failWrite = false
        rule.onNodeWithText("Повторить").performScrollTo().performClick()
        rule.waitUntil(10_000) { requests == 1 }
        rule.onNodeWithText("Карта").assertIsDisplayed()
        val saved = runBlocking { actual.getPoint(territory, original.id) }!!
        assertEquals(original.copy(isVisible = true), saved)
        rule.runOnIdle { card = true }
        rule.waitUntil(10_000) { rule.onAllNodesWithText(original.title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(original.title).assertIsDisplayed()
        assertEquals(1, requests)
    }

    @Test fun alreadyOnDoesNotWriteAndBackDoesNotMoveCamera() {
        val original = point()
        var requests = 0
        var closed = false
        rule.setContent { Bee_searchTheme {
            UserPointDetailRoute(territory, original.id, repository, media, onBack = { closed = true }, onShowOnMap = { requests++ })
        } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("user-point-show-on-map").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("user-point-show-on-map").performScrollTo().assertIsEnabled().performClick()
        rule.waitUntil(10_000) { requests == 1 }
        assertEquals(0, writes)
        rule.onNodeWithContentDescription("Назад").performClick()
        assertTrue(closed)
        assertEquals(1, requests)
    }

    @Test fun cardShowsCreationDateFromTheStoredTimestamp() {
        val createdAt = Instant.parse("2026-10-21T10:15:00Z")
        val original = runBlocking {
            actual.create(UserPoint(UUID.randomUUID(), territory, "Тропа к лесной поляне", null,
                56.8, 60.6, createdAt = createdAt, updatedAt = createdAt))
        }
        rule.setContent { Bee_searchTheme {
            UserPointDetailRoute(territory, original.id, repository, media, onBack = {}, onShowOnMap = {})
        } }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("user-point-created-at").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("user-point-created-at")
            .assertTextEquals(userPointCreatedDateText(createdAt))
    }
}
