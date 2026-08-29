package org.beesearch.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class BeeObservationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val pointId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val releaseTime = Instant.parse("2026-08-28T12:00:00Z")
    private val returnTime = releaseTime.plusSeconds(45)
    private val now = releaseTime.plusSeconds(75)
    private val flyingBee = bee(
        id = UUID.fromString("00000000-0000-0000-0000-000000000201"),
        color = "WHITE",
        position = MarkPosition.NONE,
    )
    private val atPointBee = bee(
        id = UUID.fromString("00000000-0000-0000-0000-000000000202"),
        color = "BLUE",
        position = MarkPosition.RIGHT_WING,
    )
    @Test
    fun cardsShowIndependentPersistedStatesTimersAndNaturalActions() {
        var returnedBeeId: UUID? = null
        var departedBeeId: UUID? = null

        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee, atPointBee),
                    flightCycles = listOf(
                        cycle(flyingBee, 1, releaseTime, null),
                        cycle(atPointBee, 1, releaseTime, returnTime),
                    ),
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = { returnedBeeId = it },
                    onStartNextFlight = { departedBeeId = it },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithText("Белая").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-state-${flyingBee.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-timer-${flyingBee.id}").assertIsDisplayed()
        composeRule.onNodeWithText("01:15").assertIsDisplayed()
        composeRule.onNodeWithText("00:30").assertIsDisplayed()

        composeRule.onNodeWithTag("bee-action-${flyingBee.id}")
            .assertHeightIsAtLeast(48.dp)
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithTag("bee-action-${atPointBee.id}").assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals(flyingBee.id, returnedBeeId)
            assertEquals(atPointBee.id, departedBeeId)
        }
    }

    @Test
    fun compactLayoutKeepsFiveBeeActionsVisibleWithoutScrolling() {
        val visibleBees = listOf(
            flyingBee,
            atPointBee,
            bee(UUID.fromString("00000000-0000-0000-0000-000000000203"), "YELLOW", MarkPosition.NONE),
            bee(UUID.fromString("00000000-0000-0000-0000-000000000204"), "RED", MarkPosition.LEFT_WING),
            bee(UUID.fromString("00000000-0000-0000-0000-000000000205"), "GREEN", MarkPosition.RIGHT_WING),
        )

        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = visibleBees,
                    flightCycles = visibleBees.map { cycle(it, 1, releaseTime, null) },
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-action-${visibleBees.last().id}")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun eventButtonDisablesImmediatelyWhileItsPersistenceOperationIsPending() {
        var returnRequests = 0
        composeRule.setContent {
            val pendingIds = remember { mutableStateOf(emptySet<UUID>()) }
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = listOf(cycle(flyingBee, 1, releaseTime, null)),
                    beeEventInProgressIds = pendingIds.value,
                    isCompleting = false,
                    onRegisterReturn = {
                        returnRequests += 1
                        pendingIds.value = pendingIds.value + it
                    },
                    onStartNextFlight = {},
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-action-${flyingBee.id}").performClick()
        composeRule.onNodeWithTag("bee-action-${flyingBee.id}").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, returnRequests) }
    }

    @Test
    fun validAzimuthIsSavedForSelectedLatestCycleAndShownInCard() {
        val previousCycle = cycle(flyingBee, 1, releaseTime, returnTime, azimuthDeg = 45.0)
        val selectedCycle = cycle(
            flyingBee,
            2,
            returnTime.plusSeconds(5),
            null,
            azimuthDeg = 90.0,
        )
        var savedCycleId: UUID? = null
        var savedAzimuth: Double? = null

        composeRule.setContent {
            val cycles = remember { mutableStateOf(listOf(previousCycle, selectedCycle)) }
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { cycleId, value, onSuccess ->
                        savedCycleId = cycleId
                        savedAzimuth = value
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == cycleId) cycle.copy(azimuthDeg = value) else cycle
                        }
                        onSuccess()
                    },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.onNodeWithText("Азимут").assertIsDisplayed()
        composeRule.onNodeWithTag("azimuth-input").assertTextContains("90")
        composeRule.onNodeWithTag("azimuth-input").performTextClearance()
        composeRule.onNodeWithTag("azimuth-input").performTextInput("247")
        composeRule.onNodeWithTag("save-azimuth").performClick()

        composeRule.onNodeWithText("Азимут 247°").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(selectedCycle.id, savedCycleId)
            assertEquals(247.0, savedAzimuth)
        }
    }

    @Test
    fun azimuth360ShowsValidationAndDoesNotInvokeSave() {
        var saveRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = listOf(cycle(flyingBee, 1, releaseTime, null)),
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { _, _, _ -> saveRequests += 1 },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}").performClick()
        composeRule.onNodeWithTag("azimuth-input").performTextInput("360")
        composeRule.onNodeWithTag("save-azimuth").performClick()

        composeRule.onNodeWithText("Азимут должен быть от 0° до 359°").assertIsDisplayed()
        composeRule.onNodeWithTag("azimuth-input").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, saveRequests) }
    }

    @Test
    fun existingAzimuthCanBeRemovedFromItsCycle() {
        val selectedCycle = cycle(flyingBee, 1, releaseTime, null, azimuthDeg = 90.0)
        var removeRequests = 0
        composeRule.setContent {
            val cycles = remember { mutableStateOf(listOf(selectedCycle)) }
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { cycleId, value, onSuccess ->
                        if (value == null) removeRequests += 1
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == cycleId) cycle.copy(azimuthDeg = value) else cycle
                        }
                        onSuccess()
                    },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithText("Азимут 90°").performClick()
        composeRule.onNodeWithTag("remove-azimuth").performClick()

        composeRule.onNodeWithText("Азимут —").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, removeRequests) }
    }

    @Test
    fun completionRequiresConfirmationAndCancelDoesNotComplete() {
        var completionRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = listOf(cycle(flyingBee, 1, releaseTime, null)),
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onComplete = { completionRequests += 1 },
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("complete-field-observation").performClick()
        composeRule.onNodeWithText("Незаконченные полёты сохранятся как есть.", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("cancel-field-observation-completion").performClick()
        composeRule.runOnIdle { assertEquals(0, completionRequests) }

        composeRule.onNodeWithTag("complete-field-observation").performClick()
        composeRule.onNodeWithTag("confirm-field-observation-completion").performClick()
        composeRule.runOnIdle { assertEquals(1, completionRequests) }
    }

    private fun point() = ObservationPoint(
        id = pointId,
        territoryId = UUID.randomUUID(),
        observerCode = "SV",
        observationYear = 2026,
        pointNumber = 7,
        beePresenceResult = BeePresenceResult.BEES_FOUND,
        code = null,
        latitude = 56.1,
        longitude = 42.7,
        gpsLatitude = 56.1,
        gpsLongitude = 42.7,
        gpsAccuracyM = 3.8,
        createdAt = releaseTime.minusSeconds(60),
        completedAt = null,
    )

    private fun bee(id: UUID, color: String, position: MarkPosition) = Bee(
        id = id,
        observationPointId = pointId,
        markColor = color,
        markPosition = position,
        createdAt = releaseTime.minusSeconds(30),
    )

    private fun cycle(
        bee: Bee,
        sequenceNumber: Int,
        departureTime: Instant,
        returnTime: Instant?,
        azimuthDeg: Double? = null,
    ) = FlightCycle(
        id = UUID.randomUUID(),
        beeId = bee.id,
        sequenceNumber = sequenceNumber,
        departureTime = departureTime,
        returnTime = returnTime,
        azimuthDeg = azimuthDeg,
        createdAt = departureTime,
        updatedAt = returnTime ?: departureTime,
    )
}
