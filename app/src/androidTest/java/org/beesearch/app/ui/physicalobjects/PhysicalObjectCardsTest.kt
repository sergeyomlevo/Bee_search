package org.beesearch.app.ui.physicalobjects

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.beesearch.app.domain.model.Hollow
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.ui.theme.Bee_searchTheme
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
