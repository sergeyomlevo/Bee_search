package org.beesearch.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import org.beesearch.app.domain.heading.HeadingAccuracy
import org.beesearch.app.domain.heading.HeadingProvider
import org.beesearch.app.domain.heading.HeadingState
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

        composeRule.onNodeWithText("Белая").assertDoesNotExist()
        composeRule.onNodeWithTag("bee-mark-WHITE-NONE").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-mark-BLUE-RIGHT_WING").assertIsDisplayed()
        composeRule.onNodeWithText("В полёте").assertIsDisplayed()
        composeRule.onNodeWithText("На точке").assertIsDisplayed()
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

        val headingBounds = composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot
        val actionBounds = composeRule.onNodeWithTag("bee-action-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Между heading и основной кнопкой должно оставаться свободное место",
            actionBounds.left > headingBounds.right,
        )
    }

    @Test
    fun compactLayoutKeepsSixBeeActionsVisibleWithoutScrolling() {
        val visibleBees = listOf(
            flyingBee,
            atPointBee,
            bee(UUID.fromString("00000000-0000-0000-0000-000000000203"), "YELLOW", MarkPosition.NONE),
            bee(UUID.fromString("00000000-0000-0000-0000-000000000204"), "RED", MarkPosition.LEFT_WING),
            bee(UUID.fromString("00000000-0000-0000-0000-000000000205"), "GREEN", MarkPosition.RIGHT_WING),
            bee(UUID.fromString("00000000-0000-0000-0000-000000000206"), "BLUE", MarkPosition.LEFT_WING),
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
        composeRule.onNodeWithTag("bee-azimuth-${visibleBees.last().id}")
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
    fun captureConsumesOpportunityAndUndoLeavesControlDisabled() {
        val previousCycle = cycle(flyingBee, 1, releaseTime, returnTime, azimuthDeg = 45.0)
        val selectedCycle = cycle(
            flyingBee,
            2,
            returnTime.plusSeconds(5),
            null,
            azimuthDeg = null,
        )
        val heading = MutableStateFlow(availableHeading(247))
        var savedCycleId: UUID? = null
        var savedAzimuth: Double? = null
        var saveRequests = 0

        composeRule.setContent {
            val cycles = remember { mutableStateOf(listOf(previousCycle, selectedCycle)) }
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { cycleId, value, onSuccess ->
                        saveRequests += 1
                        savedCycleId = cycleId
                        savedAzimuth = value
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == cycleId) {
                                cycle.copy(
                                    azimuthDeg = value,
                                    azimuthCaptureConsumed =
                                        cycle.azimuthCaptureConsumed || value != null,
                                )
                            } else cycle
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
            .assertIsEnabled()
            .assertTextContains("247°")
            .performClick()
        composeRule.onNodeWithTag("azimuth-undo-banner").assertIsDisplayed()
        composeRule.onNodeWithText("247° сохранён").assertIsDisplayed()
        composeRule.runOnIdle { heading.value = availableHeading(250) }
        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .assertTextContains("247°")
            .assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(selectedCycle.id, savedCycleId)
            assertEquals(247.0, savedAzimuth)
            assertEquals(1, saveRequests)
        }

        composeRule.onNodeWithTag("azimuth-undo").performClick()
        composeRule.onNodeWithTag("azimuth-undo-banner").assertDoesNotExist()
        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .assertTextContains("—°")
            .assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(null, savedAzimuth)
            assertEquals(2, saveRequests)
        }
    }

    @Test
    fun unavailableOrUnreliableHeadingCannotCreateAzimuth() {
        val heading = MutableStateFlow<HeadingState>(HeadingState.Unavailable("Нет датчика"))
        var saveRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = listOf(cycle(flyingBee, 1, releaseTime, null)),
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { _, _, _ -> saveRequests += 1 },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .assertTextContains("нет")
            .assertIsNotEnabled()
        composeRule.runOnIdle {
            heading.value = availableHeading(0, HeadingAccuracy.UNRELIABLE)
        }
        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .assertTextContains("! —")
            .assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, saveRequests) }
    }

    @Test
    fun persistedAzimuthWinsAfterRecoveryAndNewCycleReturnsToLiveHeading() {
        val firstCycle = cycle(flyingBee, 1, releaseTime, returnTime, azimuthDeg = 90.0)
        val heading = MutableStateFlow(availableHeading(250))
        val cycles = mutableStateOf(listOf(firstCycle))
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .assertTextContains("90°")
            .assertIsNotEnabled()
        composeRule.runOnIdle {
            cycles.value = listOf(
                firstCycle,
                cycle(flyingBee, 2, returnTime.plusSeconds(5), null),
            )
        }
        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .assertTextContains("250°")
            .assertIsEnabled()
    }

    @Test
    fun consumedOpenCycleWithoutAzimuthStaysDisabledAfterRecovery() {
        val consumedCycle = cycle(
            flyingBee,
            1,
            releaseTime,
            null,
            azimuthDeg = null,
            azimuthCaptureConsumed = true,
        )
        val heading = MutableStateFlow(availableHeading(250))
        var captureRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = listOf(consumedCycle),
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onCaptureFlightAzimuth = { _, _, _ -> captureRequests += 1 },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .assertTextContains("—°")
            .assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(0, captureRequests) }
    }

    @Test
    fun failedCaptureDoesNotConsumeOpportunityOrShowUndo() {
        val heading = MutableStateFlow(availableHeading(250))
        var captureRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = listOf(cycle(flyingBee, 1, releaseTime, null)),
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onCaptureFlightAzimuth = { _, _, _ -> captureRequests += 1 },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        repeat(2) {
            composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
                .assertTextContains("250°")
                .assertIsEnabled()
                .performClick()
        }
        composeRule.onNodeWithTag("azimuth-undo-banner").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(2, captureRequests) }
    }

    @Test
    fun atPointWithoutAzimuthIsDisabledAndTapDoesNotPersist() {
        val heading = MutableStateFlow(availableHeading(269))
        var saveRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(atPointBee),
                    flightCycles = listOf(cycle(atPointBee, 1, releaseTime, returnTime)),
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { _, _, _ -> saveRequests += 1 },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}")
            .assertHeightIsAtLeast(48.dp)
            .assertTextContains("—°")
            .assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(0, saveRequests) }
    }

    @Test
    fun savedAzimuthOnClosedCycleIsVisibleReadOnlyAndDoesNotFollowHeading() {
        val heading = MutableStateFlow(availableHeading(269))
        var saveRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(atPointBee),
                    flightCycles = listOf(
                        cycle(atPointBee, 1, releaseTime, returnTime, azimuthDeg = 132.0),
                    ),
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { _, _, _ -> saveRequests += 1 },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}")
            .assertTextContains("132°")
            .assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.runOnIdle { heading.value = availableHeading(280) }
        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}").assertTextContains("132°")
        composeRule.runOnIdle { assertEquals(0, saveRequests) }
    }

    @Test
    fun departureCreatesOpenCycleAndEnablesLiveAzimuth() {
        val firstCycle = cycle(
            atPointBee,
            1,
            releaseTime,
            returnTime,
            azimuthCaptureConsumed = true,
        )
        val cycles = mutableStateOf(listOf(firstCycle))
        val heading = MutableStateFlow(availableHeading(269))
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(atPointBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {
                        cycles.value = cycles.value + cycle(
                            atPointBee,
                            2,
                            returnTime.plusSeconds(5),
                            null,
                        )
                    },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}").assertIsNotEnabled()
        composeRule.onNodeWithTag("bee-action-${atPointBee.id}").performClick()
        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}")
            .assertTextContains("269°")
            .assertIsEnabled()
    }

    @Test
    fun returnClosesOpenCycleAndDisablesAzimuthImmediately() {
        val openCycle = cycle(flyingBee, 1, releaseTime, null)
        val cycles = mutableStateOf(listOf(openCycle))
        val heading = MutableStateFlow(availableHeading(269))
        var saveRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == openCycle.id) cycle.copy(returnTime = returnTime) else cycle
                        }
                    },
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { _, _, _ -> saveRequests += 1 },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}").assertIsEnabled()
        composeRule.onNodeWithTag("bee-action-${flyingBee.id}").performClick()
        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .assertTextContains("—°")
            .assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(0, saveRequests) }
    }

    @Test
    fun newerCaptureReplacesUndoAndOldTimeoutCannotHideOrUndoIt() {
        val firstCycle = cycle(flyingBee, 1, releaseTime, null)
        val secondCycle = cycle(atPointBee, 1, releaseTime, null)
        val heading = MutableStateFlow(availableHeading(247))
        val cycles = mutableStateOf(listOf(firstCycle, secondCycle))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee, atPointBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { cycleId, _, onSuccess ->
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == cycleId) {
                                cycle.copy(
                                    azimuthDeg = null,
                                )
                            } else cycle
                        }
                        onSuccess()
                    },
                    onCaptureFlightAzimuth = { cycleId, value, onSuccess ->
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == cycleId) {
                                cycle.copy(
                                    azimuthDeg = value,
                                    azimuthCaptureConsumed = true,
                                )
                            } else cycle
                        }
                        onSuccess()
                    },
                    onComplete = {},
                    nowProvider = { now },
                    undoTimeoutMillis = 5_000,
                )
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.runOnIdle { heading.value = availableHeading(132) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("132° сохранён").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(3_100)
        composeRule.onNodeWithText("132° сохранён").assertIsDisplayed()
        composeRule.onNodeWithTag("azimuth-undo").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}").assertTextContains("247°")
        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}")
            .assertTextContains("—°")
            .assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(247.0, cycles.value.single { it.id == firstCycle.id }.azimuthDeg)
            assertEquals(null, cycles.value.single { it.id == secondCycle.id }.azimuthDeg)
        }
    }

    @Test
    fun undoBannerExpiresWithoutClearingPersistedAzimuth() {
        val selectedCycle = cycle(flyingBee, 1, releaseTime, null)
        val heading = MutableStateFlow(availableHeading(247))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val cycles = remember { mutableStateOf(listOf(selectedCycle)) }
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { cycleId, value, onSuccess ->
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == cycleId) {
                                cycle.copy(
                                    azimuthDeg = value,
                                    azimuthCaptureConsumed =
                                        cycle.azimuthCaptureConsumed || value != null,
                                )
                            } else cycle
                        }
                        onSuccess()
                    },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(FEEDBACK_AUTO_DISMISS_MILLIS - 1)
        composeRule.onNodeWithTag("azimuth-undo-banner").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(2)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("azimuth-undo-banner").assertDoesNotExist()
        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}").assertTextContains("247°")
    }

    @Test
    fun transientFeedbackStaysInHeaderWithoutMovingOrCoveringFirstCard() {
        val feedback = mutableStateOf<UiFeedback?>(null)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = listOf(cycle(flyingBee, 1, releaseTime, null)),
                    beeEventInProgressIds = emptySet(),
                    feedback = feedback.value,
                    onDismissFeedback = { id ->
                        if (feedback.value?.id == id) feedback.value = null
                    },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        val firstCardTopBefore = composeRule.onNodeWithTag("bee-card-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot.top

        composeRule.runOnIdle {
            feedback.value = autoFeedback(1, "Прилёт сохранён")
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText("Прилёт сохранён").assertIsDisplayed()
        val headerBounds = composeRule.onNodeWithTag("observation-header")
            .fetchSemanticsNode().boundsInRoot
        val bannerBounds = composeRule.onNodeWithTag("observation-transient-banner")
            .fetchSemanticsNode().boundsInRoot
        val feedbackTextBounds = composeRule.onNodeWithTag("observation-transient-text")
            .fetchSemanticsNode().boundsInRoot
        val completeBounds = composeRule.onNodeWithTag("complete-field-observation")
            .assertIsDisplayed()
            .assertIsEnabled()
            .fetchSemanticsNode().boundsInRoot
        val firstCardTopWithFeedback = composeRule.onNodeWithTag("bee-card-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue("Feedback должен начинаться внутри header", bannerBounds.top >= headerBounds.top)
        assertTrue("Feedback должен заканчиваться внутри header", bannerBounds.bottom <= headerBounds.bottom)
        assertTrue("Feedback не должен перекрывать кнопку завершения", bannerBounds.right <= completeBounds.left)
        assertTrue("Однострочный текст должен помещаться по высоте header", feedbackTextBounds.height <= headerBounds.height)
        assertTrue("Первая карточка должна начинаться ниже header", firstCardTopWithFeedback >= headerBounds.bottom)
        assertEquals(
            "Появление feedback не должно сдвигать список",
            firstCardTopBefore,
            firstCardTopWithFeedback,
            0.5f,
        )

        composeRule.onNodeWithTag("complete-field-observation").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("cancel-field-observation-completion").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(FEEDBACK_AUTO_DISMISS_MILLIS + 1)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("observation-transient-banner").assertDoesNotExist()
        val firstCardTopAfterDismiss = composeRule.onNodeWithTag("bee-card-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot.top
        assertEquals(
            "Исчезновение feedback не должно сдвигать список",
            firstCardTopBefore,
            firstCardTopAfterDismiss,
            0.5f,
        )
    }

    @Test
    fun azimuthUndoImmediatelyReplacesOrdinaryFeedbackAndGetsItsOwnTimeout() {
        val selectedCycle = cycle(atPointBee, 1, releaseTime, null)
        val heading = MutableStateFlow(availableHeading(269))
        val otherCycle = cycle(flyingBee, 1, releaseTime, null)
        val cycles = mutableStateOf(listOf(otherCycle, selectedCycle))
        val feedback = mutableStateOf<UiFeedback?>(autoFeedback(1, "Вылет сохранён"))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee, atPointBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    feedback = feedback.value,
                    onDismissFeedback = { id ->
                        if (feedback.value?.id == id) feedback.value = null
                    },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onSetFlightAzimuth = { cycleId, value, onSuccess ->
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == cycleId) {
                                cycle.copy(
                                    azimuthDeg = value,
                                    azimuthCaptureConsumed =
                                        cycle.azimuthCaptureConsumed || value != null,
                                )
                            } else cycle
                        }
                        onSuccess()
                    },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Вылет сохранён").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText("Вылет сохранён").assertDoesNotExist()
        composeRule.onNodeWithText("269° сохранён").assertIsDisplayed()
        composeRule.onNodeWithTag("azimuth-undo").assertIsDisplayed()

        val headerBounds = composeRule.onNodeWithTag("observation-header")
            .fetchSemanticsNode().boundsInRoot
        val undoBounds = composeRule.onNodeWithTag("azimuth-undo-banner")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Undo должен отображаться в том же header-slot", undoBounds.bottom <= headerBounds.bottom)

        composeRule.mainClock.advanceTimeBy(2_100)
        composeRule.onNodeWithTag("azimuth-undo-banner").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(901)
        composeRule.onNodeWithTag("azimuth-undo-banner").assertDoesNotExist()
    }

    @Test
    fun ordinarySuccessCannotReplaceActiveAzimuthUndoAndIsNotDeferred() {
        val firstCycle = cycle(flyingBee, 1, releaseTime, null)
        val secondCycle = cycle(atPointBee, 1, releaseTime, returnTime)
        val heading = MutableStateFlow(availableHeading(269))
        val cycles = mutableStateOf(listOf(firstCycle, secondCycle))
        val feedback = mutableStateOf<UiFeedback?>(null)
        var departureRequests = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee, atPointBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { heading },
                    feedback = feedback.value,
                    onDismissFeedback = { id ->
                        if (feedback.value?.id == id) feedback.value = null
                    },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {
                        departureRequests += 1
                        feedback.value = autoFeedback(2, "Вылет сохранён")
                    },
                    onSetFlightAzimuth = { cycleId, value, onSuccess ->
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == cycleId) {
                                cycle.copy(
                                    azimuthDeg = value,
                                    azimuthCaptureConsumed =
                                        cycle.azimuthCaptureConsumed || value != null,
                                )
                            } else cycle
                        }
                        onSuccess()
                    },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("azimuth-undo-banner").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-action-${atPointBee.id}").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle { assertEquals(1, departureRequests) }
        composeRule.onNodeWithTag("azimuth-undo-banner").assertIsDisplayed()
        composeRule.onNodeWithText("Вылет сохранён").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(FEEDBACK_AUTO_DISMISS_MILLIS + 1)
        composeRule.onNodeWithTag("azimuth-undo-banner").assertDoesNotExist()
        composeRule.onNodeWithText("Вылет сохранён").assertDoesNotExist()
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
        azimuthCaptureConsumed: Boolean = azimuthDeg != null,
    ) = FlightCycle(
        id = UUID.randomUUID(),
        beeId = bee.id,
        sequenceNumber = sequenceNumber,
        departureTime = departureTime,
        returnTime = returnTime,
        azimuthDeg = azimuthDeg,
        azimuthCaptureConsumed = azimuthCaptureConsumed,
        createdAt = departureTime,
        updatedAt = returnTime ?: departureTime,
    )

    private fun availableHeading(
        degrees: Int,
        accuracy: HeadingAccuracy = HeadingAccuracy.HIGH,
    ) = HeadingState.Available(
        trueHeadingDeg = degrees,
        accuracy = accuracy,
        calculatedAt = now,
    )

    private fun autoFeedback(id: Long, message: String) = UiFeedback(
        id = id,
        message = message,
        displayMode = FeedbackDisplayMode.AUTO_DISMISS,
    )
}
