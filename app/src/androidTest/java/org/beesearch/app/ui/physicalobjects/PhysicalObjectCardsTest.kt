package org.beesearch.app.ui.physicalobjects

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHive
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhysicalObjectCardsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savedObjectShowsMediaCharacteristicsAndCoordinateActions() {
        val objectId = UUID.randomUUID()
        val media = listOf(
            media(objectId, PhysicalObjectMediaType.IMAGE, "first.jpg"),
            media(objectId, PhysicalObjectMediaType.VIDEO, "second.mp4"),
        )
        var editCoordinates = false
        var showOnMap = false
        composeRule.setContent {
            Bee_searchTheme {
                HollowCard(
                    value = Hollow(
                        id = objectId,
                        territoryId = UUID.randomUUID(),
                        sequenceNumber = 4,
                        latitude = 56.1961784,
                        longitude = 42.7480444,
                        createdAt = Instant.EPOCH,
                        creatorObserverId = UUID.randomUUID(),
                        properties = HollowProperties("ель", 450.0, 127, 32.0, null, "рядом с тропой"),
                        media = media,
                    ),
                    territoryLabel = "DEV-BENCH2 · DEV Territory",
                    creatorLabel = "DEV-OBS1 · Tester",
                    onEditCoordinates = { editCoordinates = true },
                    onShowOnMap = { showOnMap = true },
                )
            }
        }

        composeRule.onNodeWithTag("physical-object-detail-media-hero").assertIsDisplayed()
        composeRule.onNodeWithTag("physical-object-detail-media-${media[1].id}").assertIsDisplayed()
        composeRule.onNodeWithText("127° · ЮВ").assertIsDisplayed()
        composeRule.onNodeWithTag("physical-object-coordinates").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("physical-object-show-map").performScrollTo().performClick()
        composeRule.onNodeWithTag("physical-object-edit-coordinates").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertTrue(showOnMap)
            assertTrue(editCoordinates)
        }
    }

    @Test
    fun hollowsListShowsOnlyHollowsAndNoRepeatedTypeLine() {
        val hollow = hollow(1)
        val otherHollow = hollow(2)
        val logHive = logHive(1)
        var opened: UUID? = null
        composeRule.setContent { Bee_searchTheme {
            PhysicalObjectsBrowser(
                type = PhysicalObjectType.HOLLOW,
                hollows = listOf(hollow, otherHollow),
                logHives = listOf(logHive),
                onOpen = { opened = it },
            )
        } }

        composeRule.onNodeWithText("Дупло 1").assertIsDisplayed()
        composeRule.onNodeWithText("Дупло 2").assertIsDisplayed()
        composeRule.onNodeWithText("Колода 1").assertDoesNotExist()
        composeRule.onAllNodesWithText("Дупло").assertCountEquals(0)

        composeRule.onNodeWithTag("physical-object-${otherHollow.id}").performClick()
        composeRule.runOnIdle { assertEquals(otherHollow.id, opened) }
    }

    @Test
    fun logHivesListShowsOnlyLogHivesAndNoRepeatedTypeLine() {
        val hollow = hollow(1)
        val logHive = logHive(1)
        composeRule.setContent { Bee_searchTheme {
            PhysicalObjectsBrowser(
                type = PhysicalObjectType.LOG_HIVE,
                hollows = listOf(hollow),
                logHives = listOf(logHive),
                onOpen = {},
            )
        } }

        composeRule.onNodeWithText("Колода 1").assertIsDisplayed()
        composeRule.onNodeWithText("Дупло 1").assertDoesNotExist()
        composeRule.onAllNodesWithText("Колода").assertCountEquals(0)
    }

    @Test
    fun emptyCategoryExplainsWhatIsMissing() {
        composeRule.setContent { Bee_searchTheme {
            PhysicalObjectsBrowser(
                type = PhysicalObjectType.HOLLOW,
                hollows = emptyList(),
                logHives = emptyList(),
                onOpen = {},
            )
        } }

        composeRule.onNodeWithTag("physical-objects-empty").assertIsDisplayed()
        composeRule.onNodeWithText("В этой территории пока нет дупел.").assertIsDisplayed()
        composeRule.onNodeWithTag("physical-objects-list").assertDoesNotExist()
        composeRule.onNodeWithTag("physical-objects-reset").assertDoesNotExist()
    }

    @Test
    fun emptyCategoryOffersTheNumberingResetOnlyWhenTheCallerSupportsIt() {
        var reset = false
        composeRule.setContent { Bee_searchTheme {
            PhysicalObjectsBrowser(
                type = PhysicalObjectType.HOLLOW,
                hollows = emptyList(),
                logHives = emptyList(),
                onOpen = {},
                onResetSequence = { reset = true },
            )
        } }

        composeRule.onNodeWithTag("physical-objects-reset").assertIsDisplayed()
        composeRule.onNodeWithText("Сбросить нумерацию").assertIsDisplayed()
        composeRule.onNodeWithTag("physical-objects-reset").performClick()
        composeRule.runOnIdle { assertTrue(reset) }
    }

    @Test
    fun nonEmptyCategoryDoesNotOfferTheNumberingReset() {
        composeRule.setContent { Bee_searchTheme {
            PhysicalObjectsBrowser(
                type = PhysicalObjectType.HOLLOW,
                hollows = listOf(hollow(1)),
                logHives = emptyList(),
                onOpen = {},
                onResetSequence = {},
            )
        } }

        composeRule.onNodeWithTag("physical-objects-reset").assertDoesNotExist()
        composeRule.onNodeWithTag("physical-objects-list").assertIsDisplayed()
    }

    @Test
    fun deleteActionIsOnTheCardAndRequiresConfirmation() {
        var deleted = false
        composeRule.setContent { Bee_searchTheme {
            HollowCard(
                value = Hollow(
                    id = UUID.randomUUID(),
                    territoryId = UUID.randomUUID(),
                    sequenceNumber = 4,
                    latitude = 56.19,
                    longitude = 42.74,
                    createdAt = Instant.EPOCH,
                    creatorObserverId = null,
                    properties = HollowProperties("ель", 450.0, 127, 32.0, null, null),
                    media = emptyList(),
                ),
                territoryLabel = "DEV-BENCH2 · DEV Territory",
                creatorLabel = "DEV-OBS1 · Tester",
                onDelete = { deleted = true },
            )
        } }

        composeRule.onNodeWithTag("physical-object-delete").performScrollTo().performClick()
        composeRule.onNodeWithText("Удалить Дупло 4?").assertIsDisplayed()
        composeRule.onNodeWithText("Объект будет удалён. Восстановить его нельзя.").assertIsDisplayed()

        composeRule.onNodeWithTag("physical-object-delete-cancel").performClick()
        composeRule.runOnIdle { assertTrue(!deleted) }
        composeRule.onNodeWithText("Удалить Дупло 4?").assertDoesNotExist()

        composeRule.onNodeWithTag("physical-object-delete").performScrollTo().performClick()
        composeRule.onNodeWithTag("physical-object-delete-confirm").performClick()
        composeRule.runOnIdle { assertTrue(deleted) }
    }

    @Test
    fun deleteConfirmationOfAnObjectWithMediaMentionsTheMedia() {
        val objectId = UUID.randomUUID()
        composeRule.setContent { Bee_searchTheme {
            HollowCard(
                value = Hollow(
                    id = objectId,
                    territoryId = UUID.randomUUID(),
                    sequenceNumber = 1,
                    latitude = 56.19,
                    longitude = 42.74,
                    createdAt = Instant.EPOCH,
                    creatorObserverId = null,
                    properties = HollowProperties("ель", 450.0, 127, 32.0, null, null),
                    media = listOf(media(objectId, PhysicalObjectMediaType.IMAGE, "one.jpg")),
                ),
                territoryLabel = "T",
                creatorLabel = "O",
            )
        } }

        composeRule.onNodeWithTag("physical-object-delete").performScrollTo().performClick()

        composeRule.onNodeWithText("Объект и его медиа будут удалены. Восстановить их нельзя.").assertIsDisplayed()
    }

    @Test
    fun logHiveDeleteConfirmationNamesTheLogHive() {
        composeRule.setContent { Bee_searchTheme {
            LogHiveCard(
                value = LogHive(
                    id = UUID.randomUUID(),
                    territoryId = UUID.randomUUID(),
                    sequenceNumber = 2,
                    latitude = 56.19,
                    longitude = 42.74,
                    createdAt = Instant.EPOCH,
                    creatorObserverId = null,
                    properties = LogHiveProperties("сосна", 120.0, 90, 40.0, "сосна", 30.0, 200.0, null),
                ),
                territoryLabel = "T",
                creatorLabel = "O",
            )
        } }

        composeRule.onNodeWithTag("physical-object-delete").performScrollTo().performClick()

        composeRule.onNodeWithText("Удалить Колоду 2?").assertIsDisplayed()
    }

    private fun hollow(sequence: Int) = Hollow(
        id = UUID.randomUUID(),
        territoryId = UUID.randomUUID(),
        sequenceNumber = sequence,
        latitude = 56.19,
        longitude = 42.74,
        createdAt = Instant.EPOCH,
        creatorObserverId = null,
        properties = HollowProperties("ель", 450.0, 127, 32.0, null, null),
        media = emptyList(),
    )

    private fun logHive(sequence: Int) = LogHive(
        id = UUID.randomUUID(),
        territoryId = UUID.randomUUID(),
        sequenceNumber = sequence,
        latitude = 56.19,
        longitude = 42.74,
        createdAt = Instant.EPOCH,
        creatorObserverId = null,
        properties = LogHiveProperties("сосна", 120.0, 90, 40.0, "сосна", 30.0, 200.0, null),
        media = emptyList(),
    )

    private fun media(
        physicalObjectId: UUID,
        type: PhysicalObjectMediaType,
        name: String,
    ) = PhysicalObjectMedia(
        id = UUID.randomUUID(),
        physicalObjectId = physicalObjectId,
        type = type,
        relativePath = "physical-object-media/$physicalObjectId/$name",
        originalFileName = name,
        mimeType = if (type == PhysicalObjectMediaType.VIDEO) "video/mp4" else "image/jpeg",
        byteSize = 1,
        sha256 = "a".repeat(64),
        createdAt = Instant.EPOCH,
    )
}
