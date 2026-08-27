package org.beesearch.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ResumeObservationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completionRequiresExplicitConfirmation() {
        var completionRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                ResumeObservationScreen(
                    point = point(),
                    isCompleting = false,
                    onComplete = { completionRequests += 1 },
                    onOpenTerritories = {},
                )
            }
        }

        composeRule.onNodeWithText("Завершить наблюдение").performClick()
        composeRule.onNodeWithText("Завершить наблюдение?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, completionRequests) }

        composeRule.onNodeWithText("Завершить", useUnmergedTree = true).performClick()
        composeRule.runOnIdle { assertEquals(1, completionRequests) }
    }

    private fun point() = ObservationPoint(
        id = UUID.randomUUID(),
        territoryId = UUID.randomUUID(),
        observerCode = "GSE",
        code = null,
        latitude = 56.1959786,
        longitude = 42.7477116,
        gpsLatitude = 56.1959000,
        gpsLongitude = 42.7477000,
        gpsAccuracyM = 3.8,
        createdAt = Instant.parse("2026-08-27T08:31:00Z"),
        completedAt = null,
    )
}
