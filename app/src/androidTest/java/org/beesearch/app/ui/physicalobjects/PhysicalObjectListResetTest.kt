package org.beesearch.app.ui.physicalobjects

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.repository.RoomPhysicalObjectRepository
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.PhysicalObjectSequenceResetBlockedException
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The numbering reset of a typed list.
 *
 * The empty list is what makes the action visible, but it is only convenience: the operation runs
 * against the repository, which re-checks every precondition and refuses when the scope stopped
 * being safe between showing the action and confirming it.
 */
@RunWith(AndroidJUnit4::class)
class PhysicalObjectListResetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: BeeSearchDatabase
    private lateinit var repository: RoomPhysicalObjectRepository
    private val territoryId = UUID.randomUUID()
    private val otherTerritoryId = UUID.randomUUID()
    private val observerId = UUID.randomUUID()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.backupDao().insertTerritories(
            listOf(
                TerritoryEntity(territoryId, "T", "Territory", "R", "D", NOW, NOW),
                TerritoryEntity(otherTerritoryId, "T2", "Other", "R", "D", NOW, NOW),
            ),
        )
        database.backupDao().insertObservers(
            listOf(ObserverEntity(observerId, "O", "Last", "First", null, null, NOW, NOW)),
        )
        repository = RoomPhysicalObjectRepository(
            database,
            database.physicalObjectDao(),
            database.physicalObjectSequenceDao(),
            database.territoryDao(),
            database.observerDao(),
            database.beeDao(),
            Clock.fixed(NOW, ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun emptyListOffersTheResetAndCancelDoesNothing() {
        var confirmed: Pair<UUID, PhysicalObjectType>? = null
        renderList { territory, type -> confirmed = territory to type }

        composeRule.onNodeWithTag("physical-objects-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("physical-objects-reset").performClick()
        composeRule.onNodeWithText("Сбросить нумерацию дупел?").assertIsDisplayed()
        composeRule.onNodeWithText("Следующее созданное дупло получит номер 1.").assertIsDisplayed()

        composeRule.onNodeWithTag("physical-objects-reset-cancel").performClick()

        composeRule.runOnIdle { assertNull(confirmed) }
        composeRule.onNodeWithText("Сбросить нумерацию дупел?").assertDoesNotExist()
    }

    @Test
    fun confirmResetsOnlyTheOpenScope() = runBlocking {
        var confirmed: Pair<UUID, PhysicalObjectType>? = null
        renderList { territory, type ->
            confirmed = territory to type
            runBlocking { repository.resetSequence(territory, type) }
        }
        createHollow(otherTerritoryId)
        createHollow(otherTerritoryId)

        composeRule.onNodeWithTag("physical-objects-reset").performClick()
        composeRule.onNodeWithTag("physical-objects-reset-confirm").performClick()

        composeRule.runOnIdle { assertEquals(territoryId to PhysicalObjectType.HOLLOW, confirmed) }
        // The scope never held an object, so it has no sequence row; "no row" and "0" are the same
        // state, and the next object still gets number 1.
        assertNull(database.physicalObjectSequenceDao().getLastIssued(territoryId, PhysicalObjectType.HOLLOW))
        assertEquals(2, database.physicalObjectSequenceDao().getLastIssued(otherTerritoryId, PhysicalObjectType.HOLLOW))
        assertEquals(1, createHollow(territoryId).sequenceNumber)
        assertEquals(3, createHollow(otherTerritoryId).sequenceNumber)
    }

    /** The scope changed after the action was rendered: the repository refuses and nothing is reset. */
    @Test
    fun confirmedResetIsBlockedWhenTheCategoryIsNoLongerEmpty() = runBlocking {
        var blocked: Throwable? = null
        renderList { territory, type ->
            blocked = runCatching { runBlocking { repository.resetSequence(territory, type) } }.exceptionOrNull()
        }
        createHollow(territoryId)

        composeRule.onNodeWithTag("physical-objects-reset").performClick()
        composeRule.onNodeWithTag("physical-objects-reset-confirm").performClick()

        composeRule.runOnIdle { assertTrue(blocked is PhysicalObjectSequenceResetBlockedException) }
        assertEquals(1, database.physicalObjectSequenceDao().getLastIssued(territoryId, PhysicalObjectType.HOLLOW))
    }

    @Test
    fun logHiveListUsesItsOwnConfirmationText() {
        var confirmed: Pair<UUID, PhysicalObjectType>? = null
        composeRule.setContent { Bee_searchTheme {
            PhysicalObjectListRoute(
                type = PhysicalObjectType.LOG_HIVE,
                territoryId = territoryId,
                repository = repository,
                onOpen = {},
                onBack = {},
                onResetSequence = { territory, type -> confirmed = territory to type },
            )
        } }

        composeRule.onNodeWithTag("physical-objects-reset").performClick()

        composeRule.onNodeWithText("Сбросить нумерацию колод?").assertIsDisplayed()
        composeRule.onNodeWithText("Следующая созданная колода получит номер 1.").assertIsDisplayed()
        composeRule.onNodeWithTag("physical-objects-reset-confirm").performClick()
        composeRule.runOnIdle { assertEquals(territoryId to PhysicalObjectType.LOG_HIVE, confirmed) }
    }

    @Test
    fun nonEmptyListShowsNoResetAction() = runBlocking {
        createHollow(territoryId)

        renderList { _, _ -> }

        composeRule.onNodeWithTag("physical-objects-list").assertIsDisplayed()
        composeRule.onNodeWithTag("physical-objects-reset").assertDoesNotExist()
    }

    private fun renderList(onResetSequence: (UUID, PhysicalObjectType) -> Unit) {
        composeRule.setContent { Bee_searchTheme {
            PhysicalObjectListRoute(
                type = PhysicalObjectType.HOLLOW,
                territoryId = territoryId,
                repository = repository,
                onOpen = {},
                onBack = {},
                onResetSequence = onResetSequence,
            )
        } }
    }

    private suspend fun createHollow(territory: UUID) = repository.createHollow(
        NewHollow(UUID.randomUUID(), territory, observerId, 56.1, 42.7, HollowProperties("дуб", 180.0, 123, 40.0, null, null)),
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-24T10:00:00Z")
    }
}
