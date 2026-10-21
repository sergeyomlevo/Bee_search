package org.beesearch.app.ui.userpoints

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import org.beesearch.app.ui.theme.Bee_searchTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.data.repository.RoomUserPointRepository
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.ui.map.CreateRecordTypeChooserDialog
import java.time.Instant
import java.util.UUID
import org.junit.*
import org.junit.Assert.*

class UserPointCreationUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var database: BeeSearchDatabase
    private lateinit var repository: RoomUserPointRepository
    private val territory = UUID.randomUUID()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root: java.io.File
    private lateinit var media: UserPointMediaStore
    @Before fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).build()
        database.backupDao().insertTerritories(listOf(TerritoryEntity(territory, "A", "A", "R", "D", Instant.EPOCH, Instant.EPOCH)))
        repository = RoomUserPointRepository(database)
        root = java.io.File(context.cacheDir, "user-point-ui-${UUID.randomUUID()}")
        media = UserPointMediaStore(root.resolve("files"), root.resolve("cache"))
    }
    @After fun cleanup() { database.close(); root.deleteRecursively() }

    private fun capture(name: String, node: SemanticsNodeInteraction = rule.onRoot()) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val folder = java.io.File(context.getExternalFilesDir(null), "my-points-stage-b").apply { mkdirs() }
        java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun chooserPreservesResearchActionsAndSeparatesUserAction() {
        val actions = mutableListOf<String>()
        rule.setContent { Bee_searchTheme { CreateRecordTypeChooserDialog(
            onDismiss = { actions += "cancel" }, onCreateObservationPoint = { actions += "observation" },
            onCreateHollow = { actions += "hollow" }, onCreateLogHive = { actions += "log" },
            onCreateUserPoint = { actions += "user" },
        ) } }
        rule.onNodeWithTag("create-observation-point-type").performScrollTo().performClick()
        rule.onNodeWithTag("create-hollow-type").performScrollTo().performClick()
        rule.onNodeWithTag("create-log-hive-type").performScrollTo().performClick()
        rule.onNodeWithText("Ловушка — пока недоступно").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Пасека — пока недоступно").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("create-user-point-type").performScrollTo().assertIsDisplayed()
        capture("chooser-user-section", rule.onNodeWithTag("create-type-chooser"))
        rule.onNodeWithTag("create-user-point-type").performClick()
        rule.onNodeWithTag("dismiss-create-type-chooser").assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(listOf("observation", "hollow", "log", "user", "cancel"), actions) }
    }

    @Test fun saveValidatesAndCreatesVisiblePointOnlyAfterCommit() {
        var saved: UserPoint? = null
        val initial = UserPointEditorTarget(UUID.randomUUID(), territory, latitude = 56.8, longitude = 60.6)
        rule.setContent { Bee_searchTheme { UserPointEditorRoute(
            target = initial,
            repository = repository, mediaStore = media, onCancel = {}, onSaved = { saved = it }, onChangeLocation = { _, _ -> },
        ) } }
        rule.onNodeWithTag("user-point-save").performScrollTo().performClick()
        rule.onNodeWithTag("user-point-title").performScrollTo().assertIsDisplayed()
        rule.runOnIdle { assertNull(saved) }
        rule.onNodeWithTag("user-point-title").performTextInput("Тропа к лесной поляне с длинным названием")
        rule.onNodeWithTag("user-point-note").performTextInput("Заметка")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("user-point-title").performScrollTo()
        capture("create-title-note")
        rule.onNodeWithTag("user-point-save").performScrollTo().assertIsDisplayed()
        capture("create-bottom-actions")
        rule.onNodeWithTag("user-point-save").performClick()
        rule.waitUntil(10000) { saved != null }
        val actual = runBlocking { repository.getPoint(territory, saved!!.id) }
        assertTrue(actual!!.isVisible)
        assertEquals("Заметка", actual.note)
        assertEquals(56.8, actual.latitude, 0.0)
    }

    @Test fun coordinateSelectionReturnKeepsDraftAndCancelNeverCreatesPoint() {
        val initial = UserPointEditorTarget(UUID.randomUUID(), territory, latitude = 56.8, longitude = 60.6)
        var target by mutableStateOf(initial)
        var selecting by mutableStateOf(false)
        var cancelled = false
        rule.setContent { Bee_searchTheme {
            if (selecting) Button(onClick = {
                target = target.copy(latitude = 57.1, longitude = 61.2, coordinateRevision = UUID.randomUUID())
                selecting = false
            }) { Text("OK") }
            else UserPointEditorRoute(target, repository, media, onCancel = { cancelled = true }, onSaved = {}, onChangeLocation = { _, _ -> selecting = true })
        } }
        rule.onNodeWithTag("user-point-title").performTextInput("Черновик")
        rule.onNodeWithTag("user-point-note").performTextInput("Не потерять")
        rule.onNodeWithText("Изменить место").performScrollTo().performClick()
        rule.onNodeWithText("OK").performClick()
        rule.onNodeWithTag("user-point-title").performScrollTo().assertTextContains("Черновик")
        rule.onNodeWithTag("user-point-note").assertTextContains("Не потерять")
        rule.onNodeWithTag("user-point-coordinates").performScrollTo().assertTextEquals("57.100000, 61.200000")
        rule.onNodeWithTag("user-point-cancel").performScrollTo().performClick()
        rule.waitUntil(10000) { cancelled }
        val count = runBlocking { database.userPointDao().countForTerritory(territory) }
        assertEquals(0, count)
    }
}
