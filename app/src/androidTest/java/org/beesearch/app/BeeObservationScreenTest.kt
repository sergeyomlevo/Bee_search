package org.beesearch.app

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.beesearch.app.domain.heading.HeadingAccuracy
import org.beesearch.app.domain.heading.HeadingProvider
import org.beesearch.app.domain.heading.HeadingState
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeeMarkCombination
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.ui.observation.BeeObservationScreen
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs

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
    fun emptyObservationShowsDerivedMarkChoicesAndStartsSelectedMark() {
        var selected: Pair<String, MarkPosition>? = null
        var propertiesOpened = false
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point().copy(beePresenceResult = null),
                    bees = emptyList(),
                    flightCycles = emptyList(),
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartFirstFlight = { color, position -> selected = color to position },
                    onStartNextFlight = {},
                    onComplete = {},
                    onOpenPointProperties = { propertiesOpened = true },
                )
            }
        }

        assertEquals(15, BeeMarkCatalog.supportedCombinations.size)
        composeRule.onNodeWithTag("open-active-point-properties").performClick()
        composeRule.runOnIdle { assertTrue(propertiesOpened) }
        composeRule.onNodeWithTag("available-mark-WHITE-NONE").assertIsDisplayed()
        composeRule.onNodeWithTag("available-mark-action-WHITE-NONE")
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle { assertEquals("WHITE" to MarkPosition.NONE, selected) }
    }

    @Test
    fun persistedBeeIsAboveChoicesAndItsMarkIsNoLongerAvailable() {
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
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("available-mark-WHITE-NONE").assertDoesNotExist()
        val beeBounds = composeRule.onNodeWithTag("bee-card-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot
        val choiceBounds = composeRule.onNodeWithTag("available-mark-WHITE-RIGHT_WING")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(beeBounds.top < choiceBounds.top)
    }

    @Test
    fun choiceCardMirrorsBeeCardLayoutWithoutASeparatePositionLine() {
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(atPointBee),
                    flightCycles = listOf(cycle(atPointBee, 1, releaseTime, returnTime)),
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        // The mark itself carries the position, exactly like on a Bee card;
        // the retired second text line must not come back.
        composeRule.onNodeWithTag("bee-mark-WHITE-RIGHT_WING").assertExists()
        composeRule.onNodeWithTag("bee-mark-WHITE-LEFT_WING").assertExists()
        composeRule.onNodeWithTag("available-mark-position-WHITE-RIGHT_WING").assertDoesNotExist()

        val beeCardHeight = composeRule.onNodeWithTag("bee-card-${atPointBee.id}")
            .fetchSemanticsNode().boundsInRoot.height
        val choiceCardHeight = composeRule.onNodeWithTag("available-mark-WHITE-RIGHT_WING")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "Choice card must not be taller than a Bee card: " +
                "choice=$choiceCardHeight, bee=$beeCardHeight",
            choiceCardHeight <= beeCardHeight,
        )
    }

    @Test
    fun firstFlightFromAChoiceScrollsTheNewBeeIntoView() {
        val existingBees = (0 until 9).map { index ->
            bee(
                UUID.fromString("00000000-0000-0000-0000-${(400 + index).toString().padStart(12, '0')}"),
                listOf("WHITE", "YELLOW", "BLUE", "RED", "GREEN")[index % 5],
                listOf(MarkPosition.NONE, MarkPosition.RIGHT_WING, MarkPosition.LEFT_WING)[index % 3],
            )
        }
        val bees = mutableStateOf(existingBees)
        val cycles = mutableStateOf(
            existingBees.mapIndexed { index, bee ->
                cycle(bee, 1, releaseTime.plusSeconds(index.toLong()), returnTime)
            },
        )
        val remaining = BeeMarkCatalog.availableCombinations(
            existingBees.map { BeeMarkCombination(it.markColor, it.markPosition) },
        ).first()
        var startedBee: Bee? = null

        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = bees.value,
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onStartFirstFlight = { color, position ->
                        val started = bee(UUID.randomUUID(), color, position)
                        startedBee = started
                        bees.value = bees.value + started
                        cycles.value = cycles.value + cycle(started, 1, now, null)
                    },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        val choiceActionTag = "available-mark-action-" +
            "${remaining.markColor}-${remaining.markPosition.name}"
        composeRule.onNodeWithTag("bee-observation-list")
            .performScrollToNode(hasTestTag(choiceActionTag))
        composeRule.onNodeWithTag(choiceActionTag).performClick()
        composeRule.waitForIdle()

        val started = requireNotNull(startedBee)
        // The new Bee card is inserted above the choices, so the viewport must
        // follow it instead of leaving the observer at the former scroll offset.
        composeRule.onNodeWithTag("bee-card-${started.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-state-${started.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("available-mark-action-" +
            "${remaining.markColor}-${remaining.markPosition.name}").assertDoesNotExist()
    }

    @Test
    fun noBeesFoundResultIsRecordableOnlyWhileNoBeeExistsAndNeedsConfirmation() {
        var noBeesRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point().copy(beePresenceResult = null),
                    bees = emptyList(),
                    flightCycles = emptyList(),
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onRecordNoBeesFound = { noBeesRequests += 1 },
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithTag("bee-observation-list")
            .performScrollToNode(hasTestTag("record-no-bees-from-observation"))
        composeRule.onNodeWithTag("record-no-bees-from-observation").performClick()
        composeRule.onNodeWithText("Пчёлы отсутствуют?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, noBeesRequests) }

        // Cancelling the confirmation must not record the negative research result.
        composeRule.onNodeWithTag("cancel-no-bees-from-observation").performClick()
        composeRule.onNodeWithText("Пчёлы отсутствуют?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, noBeesRequests) }

        composeRule.onNodeWithTag("record-no-bees-from-observation").performClick()
        composeRule.onNodeWithTag("confirm-no-bees-from-observation").performClick()
        composeRule.runOnIdle { assertEquals(1, noBeesRequests) }
    }

    @Test
    fun noBeesFoundResultIsUnavailableWhenABeeWasAlreadyObserved() {
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
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithTag("record-no-bees-from-observation").assertDoesNotExist()
    }

    @Test
    fun tenthRealBeeDisablesRemainingMarkChoices() {
        val tenBees = BeeMarkCatalog.supportedCombinations.take(10).mapIndexed { index, mark ->
            bee(
                id = UUID.fromString("00000000-0000-0000-0000-0000000003%02d".format(index)),
                color = mark.markColor,
                position = mark.markPosition,
            )
        }

        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = tenBees,
                    flightCycles = tenBees.map { cycle(it, 1, releaseTime, null) },
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onComplete = {},
                )
            }
        }

        val remaining = BeeMarkCatalog.availableCombinations(
            tenBees.map { BeeMarkCombination(it.markColor, it.markPosition) },
        )
        assertEquals(5, remaining.size)
        remaining.forEach { mark ->
            val tag = "available-mark-action-${mark.markColor}-${mark.markPosition.name}"
            composeRule.onNodeWithTag("bee-observation-list").performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsNotEnabled()
        }
    }

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
    fun actionRowKeepsUndoAtTheSameXForFlightAndAtPointCards() {
        val flightBee = flyingBee
        val pointBee = atPointBee
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flightBee, pointBee),
                    flightCycles = listOf(
                        cycle(flightBee, 2, releaseTime, null, azimuthDeg = 82.0),
                        cycle(pointBee, 2, releaseTime, returnTime, azimuthDeg = 82.0),
                    ),
                    beeEventInProgressIds = emptySet(),
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        val flightUndoBounds = composeRule.onNodeWithTag("bee-undo-${flightBee.id}")
            .fetchSemanticsNode().boundsInRoot
        val pointUndoBounds = composeRule.onNodeWithTag("bee-undo-${pointBee.id}")
            .fetchSemanticsNode().boundsInRoot
        listOf(flightBee, pointBee).forEach { bee ->
            val azimuthBounds = composeRule.onNodeWithTag("bee-azimuth-${bee.id}")
                .fetchSemanticsNode().boundsInRoot
            val undoBounds = composeRule.onNodeWithTag("bee-undo-${bee.id}")
                .fetchSemanticsNode().boundsInRoot
            val actionBounds = composeRule.onNodeWithTag("bee-action-${bee.id}")
                .fetchSemanticsNode().boundsInRoot
            assertEquals(
                "Undo must be centered between neighboring control edges",
                (azimuthBounds.right + actionBounds.left) / 2f,
                undoBounds.center.x,
                1f,
            )
        }
        assertTrue(
            "Undo must have the same horizontal position in both card states",
            abs(flightUndoBounds.center.x - pointUndoBounds.center.x) <= 1f,
        )
        composeRule.onNodeWithTag("bee-undo-${flightBee.id}")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
        composeRule.onNodeWithTag("bee-undo-${pointBee.id}")
            .assertWidthIsEqualTo(48.dp)
            .assertHeightIsEqualTo(48.dp)
        captureActionRowScreenshot("flight-and-at-point")
    }

    @Test
    fun actionSlotsStayStableWhenAzimuthOrUndoDisappears() {
        val flight = cycle(flyingBee, 2, releaseTime, null, azimuthDeg = 82.0)
        val cycles = mutableStateOf(listOf(flight))
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee),
                    flightCycles = cycles.value,
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { flowOf(availableHeading(132)) },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        fun primaryRight() = composeRule.onNodeWithTag("bee-action-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot.right

        val initialRight = primaryRight()
        val initialUndo = composeRule.onNodeWithTag("bee-undo-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot
        val initialCard = composeRule.onNodeWithTag("bee-card-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot
        val cardInset = with(composeRule.density) { 10.dp.toPx() }
        assertEquals(initialCard.right - cardInset, initialRight, 1f)

        // Render reachable persisted-state snapshots without emulating domain mutations.
        val snapshots = listOf(
            "flight-82" to flight,
            "flight-empty" to flight.copy(azimuthDeg = null),
            "at-point-empty" to flight.copy(azimuthDeg = null, returnTime = returnTime),
            "at-point-82" to flight.copy(returnTime = returnTime),
            "at-point-no-undo" to null,
            "flight-no-undo" to cycle(flyingBee, 1, releaseTime, null),
        )
        snapshots.forEach { (name, snapshot) ->
            composeRule.runOnIdle { cycles.value = listOfNotNull(snapshot) }
            val action = composeRule.onNodeWithTag("bee-action-${flyingBee.id}")
                .assertIsDisplayed().assertIsEnabled()
            assertEquals("Primary must stay at the right edge: $name", initialRight, primaryRight(), 1f)
            val undo = composeRule.onNodeWithTag("bee-undo-${flyingBee.id}")
            if (name.endsWith("no-undo")) {
                undo.assertDoesNotExist()
            } else {
                undo.assertIsDisplayed().assertIsEnabled()
                    .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
                assertEquals("Undo must not move: $name", initialUndo.left,
                    undo.fetchSemanticsNode().boundsInRoot.left, 1f)
            }
            val azimuth = composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            if (name == "at-point-empty" || name == "at-point-no-undo") {
                azimuth.assertDoesNotExist()
            } else {
                val azimuthBounds = azimuth.assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("Azimuth stays left of the correction slot: $name",
                    azimuthBounds.right < initialUndo.left)
            }
            assertTrue("Correction slot stays left of primary: $name",
                initialUndo.right < action.fetchSemanticsNode().boundsInRoot.left)
            val cardBounds = composeRule.onNodeWithTag("bee-card-${flyingBee.id}")
                .fetchSemanticsNode().boundsInRoot
            assertEquals("Slots must not change card height: $name", initialCard.height, cardBounds.height, 1f)
            captureActionRowScreenshot(name)
        }
    }

    @Test
    fun primaryLabelsRemainFullyVisibleAtLargeFontScale() {
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale = 1.7f),
            ) {
                Bee_searchTheme {
                    BeeObservationScreen(
                        point = point(),
                        bees = listOf(flyingBee, atPointBee),
                        flightCycles = listOf(cycle(flyingBee, 1, releaseTime, null)),
                        beeEventInProgressIds = emptySet(),
                        isCompleting = false,
                        onRegisterReturn = {},
                        onStartNextFlight = {},
                        onComplete = {},
                        nowProvider = { now },
                    )
                }
            }
        }

        listOf(
            "ПРИЛЕТЕЛА" to flyingBee,
            "УЛЕТЕЛА" to atPointBee,
        ).forEach { (label, bee) ->
            val action = composeRule.onNodeWithTag("bee-action-${bee.id}")
            val actionBounds = action.fetchSemanticsNode().boundsInRoot
            val textNode = composeRule.onNode(
                hasText(label) and hasTestTag("bee-action-${bee.id}"),
            )
                .assertIsDisplayed()
            val textSemantics = textNode.fetchSemanticsNode()
            val layoutResults = mutableListOf<TextLayoutResult>()
            val layoutAction = textSemantics.config
                .getOrNull(SemanticsActions.GetTextLayoutResult)?.action
            check(layoutAction != null) { "Text layout result is unavailable for $label" }
            check(layoutAction.invoke(layoutResults)) { "Text layout result was not returned for $label" }
            assertEquals(1, layoutResults.size)
            val layout = layoutResults.single()
            assertTrue(
                "$label must not overflow its layout: size=${layout.size}, " +
                    "lineCount=${layout.lineCount}, width=${textSemantics.boundsInRoot.width}, " +
                    "actionWidth=${actionBounds.width}, overflowWidth=${layout.didOverflowWidth}, " +
                    "overflowHeight=${layout.didOverflowHeight}",
                !layout.hasVisualOverflow,
            )
            val textBounds = textSemantics.boundsInRoot
            assertTrue("$label must fit inside its primary action", textBounds.left >= actionBounds.left)
            assertTrue("$label must fit inside its primary action", textBounds.right <= actionBounds.right)
            assertTrue("$label must fit inside its primary action", textBounds.top >= actionBounds.top)
            assertTrue("$label must fit inside its primary action", textBounds.bottom <= actionBounds.bottom)
        }
        composeRule.onNodeWithTag("bee-undo-${atPointBee.id}").assertDoesNotExist()
    }

    @Test
    fun returnAndDepartureMoveOnlyTheChangedBeeAcrossTheStateBoundary() {
        val longFlyingBee = bee(
            UUID.fromString("00000000-0000-0000-0000-000000000211"),
            "WHITE",
            MarkPosition.NONE,
        )
        val newerFlyingBee = bee(
            UUID.fromString("00000000-0000-0000-0000-000000000212"),
            "YELLOW",
            MarkPosition.RIGHT_WING,
        )
        val longAtPointBee = bee(
            UUID.fromString("00000000-0000-0000-0000-000000000213"),
            "BLUE",
            MarkPosition.LEFT_WING,
        )
        val longFlyingCycle = cycle(longFlyingBee, 1, releaseTime, null)
        val newerFlyingCycle = cycle(newerFlyingBee, 1, releaseTime.plusSeconds(20), null)
        val longAtPointCycle = cycle(
            longAtPointBee,
            1,
            releaseTime,
            releaseTime.plusSeconds(15),
        )

        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale = 1.7f),
            ) {
                val cycles = remember {
                    mutableStateOf(listOf(longAtPointCycle, longFlyingCycle, newerFlyingCycle))
                }
                Bee_searchTheme {
                    BeeObservationScreen(
                        point = point(),
                        bees = listOf(longAtPointBee, longFlyingBee, newerFlyingBee),
                        flightCycles = cycles.value,
                        beeEventInProgressIds = emptySet(),
                        isCompleting = false,
                        onRegisterReturn = { beeId ->
                            cycles.value = cycles.value.map { cycle ->
                                if (cycle.beeId == beeId && cycle.returnTime == null) {
                                    cycle.copy(returnTime = now)
                                } else {
                                    cycle
                                }
                            }
                        },
                        onStartNextFlight = { beeId ->
                            cycles.value = cycles.value + cycle(
                                bee = longAtPointBee.takeIf { it.id == beeId } ?: error("Unexpected Bee"),
                                sequenceNumber = 2,
                                departureTime = now,
                                returnTime = null,
                            )
                        },
                        onComplete = {},
                        nowProvider = { now },
                    )
                }
            }
        }

        fun cardTop(bee: Bee) = composeRule.onNodeWithTag("bee-card-${bee.id}")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(cardTop(newerFlyingBee) < cardTop(longFlyingBee))
        assertTrue(cardTop(longFlyingBee) < cardTop(longAtPointBee))

        composeRule.onNodeWithTag("bee-action-${longFlyingBee.id}").performClick()

        assertTrue(cardTop(newerFlyingBee) < cardTop(longAtPointBee))
        assertTrue(cardTop(longAtPointBee) < cardTop(longFlyingBee))

        composeRule.onNodeWithTag("bee-action-${longAtPointBee.id}").performClick()

        assertTrue(cardTop(longAtPointBee) < cardTop(newerFlyingBee))
        assertTrue(cardTop(newerFlyingBee) < cardTop(longFlyingBee))
        composeRule.onNodeWithTag("bee-state-${longAtPointBee.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-state-${longFlyingBee.id}").assertIsDisplayed()
    }

    @Test
    fun departureScrollsTheMovedBeeIntoViewForImmediateAzimuthCapture() {
        val longListBees = (0 until 10).map { index ->
            bee(
                UUID.fromString("00000000-0000-0000-0000-${(300 + index).toString().padStart(12, '0')}"),
                listOf("WHITE", "YELLOW", "BLUE", "RED", "GREEN")[index % 5],
                listOf(MarkPosition.NONE, MarkPosition.RIGHT_WING, MarkPosition.LEFT_WING)[index % 3],
            )
        }
        val departingBee = longListBees.last()
        val cycles = mutableStateOf(
            longListBees.mapIndexed { index, bee ->
                cycle(
                    bee = bee,
                    sequenceNumber = 1,
                    departureTime = releaseTime.plusSeconds(index.toLong()),
                    returnTime = returnTime,
                )
            },
        )

        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale = 1.7f),
            ) {
                Bee_searchTheme {
                    BeeObservationScreen(
                        point = point(),
                        bees = longListBees,
                        flightCycles = cycles.value,
                        beeEventInProgressIds = emptySet(),
                        headingProvider = HeadingProvider { flowOf(availableHeading(247)) },
                        isCompleting = false,
                        onRegisterReturn = {},
                        onStartNextFlight = { beeId ->
                            cycles.value = cycles.value + cycle(
                                bee = longListBees.single { it.id == beeId },
                                sequenceNumber = 2,
                                departureTime = now,
                                returnTime = null,
                            )
                        },
                        onComplete = {},
                        nowProvider = { now },
                    )
                }
            }
        }

        repeat(6) {
            composeRule.onNodeWithTag("bee-observation-list")
                .performTouchInput { swipeUp() }
        }
        composeRule.onNodeWithTag("bee-action-${departingBee.id}").performClick()

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("bee-card-${departingBee.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-azimuth-${departingBee.id}")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun largeTextHeaderKeepsTheObservationTitleAndCompletionActionFullyVisible() {
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale = 1.7f),
            ) {
                Bee_searchTheme {
                    BeeObservationScreen(
                        point = point(),
                        bees = listOf(flyingBee),
                        flightCycles = listOf(cycle(flyingBee, 1, releaseTime, null)),
                        beeEventInProgressIds = emptySet(),
                        isCompleting = false,
                        onRegisterReturn = {},
                        onStartNextFlight = {},
                        onComplete = {},
                        nowProvider = { now },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("observation-header-title")
            .assertIsDisplayed()
            .assertTextContains("Наблюдение")
        composeRule.onNodeWithTag("complete-field-observation").assertIsDisplayed()

        val titleBounds = composeRule.onNodeWithTag("observation-header-title")
            .fetchSemanticsNode().boundsInRoot
        val completeBounds = composeRule.onNodeWithTag("complete-field-observation")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Заголовок не должен перекрывать действие завершения", titleBounds.right <= completeBounds.left)
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
                    onUndoLastBeeAction = { beeId ->
                        assertEquals(flyingBee.id, beeId)
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == selectedCycle.id) cycle.copy(azimuthDeg = null) else cycle
                        }
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
        composeRule.onNodeWithTag("azimuth-undo-banner").assertDoesNotExist()
        composeRule.onNodeWithTag("bee-undo-${flyingBee.id}")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
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

        composeRule.onNodeWithTag("bee-undo-${flyingBee.id}").performClick()
        composeRule.onNodeWithTag("bee-azimuth-${flyingBee.id}")
            .assertTextContains("—°")
            .assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
            .performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(247.0, savedAzimuth)
            assertEquals(1, saveRequests)
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
    fun atPointWithoutAzimuthUsesLocalReturnUndoInsteadOfAHeadingControl() {
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
            .assertDoesNotExist()
        composeRule.onNodeWithTag("bee-undo-${atPointBee.id}")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsEqualTo(48.dp)
            .assertIsEnabled()
        composeRule.onNodeWithText("Отменить", substring = true).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, saveRequests) }
    }

    @Test
    fun beeWhoseInitialLaunchWasCorrectedStaysAtPointWithoutAFakeFlightCycle() {
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point().copy(initialGroupReleaseAt = releaseTime),
                    bees = listOf(atPointBee),
                    flightCycles = emptyList(),
                    beeEventInProgressIds = emptySet(),
                    headingProvider = HeadingProvider { flowOf(availableHeading(269)) },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-state-${atPointBee.id}").assertTextContains("На точке")
        composeRule.onNodeWithTag("bee-timer-${atPointBee.id}").assertTextContains("--:--")
        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("bee-action-${atPointBee.id}").assertIsEnabled()
            .assertTextContains("УЛЕТЕЛА")
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

        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}").assertDoesNotExist()
        composeRule.onNodeWithTag("bee-undo-${atPointBee.id}").assertIsDisplayed()
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
            .assertDoesNotExist()
        composeRule.onNodeWithTag("bee-undo-${flyingBee.id}")
            .assertIsEnabled()
        composeRule.runOnIdle { assertEquals(0, saveRequests) }
    }

    @Test
    fun longTransientFeedbackIsFullyVisibleBelowTheHeaderWithoutCoveringCards() {
        val feedback = mutableStateOf<UiFeedback?>(null)
        val message = "Вылет сохранён. Зафиксируйте азимут, пока пчела в полёте."
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale = 1.7f),
            ) {
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
        }

        composeRule.mainClock.advanceTimeByFrame()
        val firstCardTopBefore = composeRule.onNodeWithTag("bee-card-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot.top

        composeRule.runOnIdle {
            feedback.value = autoFeedback(1, message)
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText(message).assertIsDisplayed()
        val headerBounds = composeRule.onNodeWithTag("observation-header")
            .fetchSemanticsNode().boundsInRoot
        val bannerBounds = composeRule.onNodeWithTag("observation-transient-banner")
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("complete-field-observation")
            .assertIsDisplayed()
            .assertIsEnabled()
        val firstCardTopWithFeedback = composeRule.onNodeWithTag("bee-card-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue("Feedback должен начинаться ниже стабильного header", bannerBounds.top >= headerBounds.bottom)
        assertTrue("Feedback не должен перекрывать первую карточку", bannerBounds.bottom <= firstCardTopWithFeedback)
        assertTrue("Feedback может сдвинуть, но не перекрыть список", firstCardTopWithFeedback > firstCardTopBefore)

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
    fun longPersistentFeedbackIsFullyVisibleBelowTheHeaderAndCanBeDismissed() {
        val message = "Не удалось сохранить азимут. Повторите действие после восстановления доступа к данным."
        val feedback = mutableStateOf<UiFeedback?>(
            UiFeedback(1, message, FeedbackDisplayMode.PERSISTENT),
        )
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale = 1.7f),
            ) {
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
        }

        composeRule.onNodeWithText(message).assertIsDisplayed()
        val headerBounds = composeRule.onNodeWithTag("observation-header")
            .fetchSemanticsNode().boundsInRoot
        val feedbackBounds = composeRule.onNodeWithTag("observation-persistent-feedback")
            .fetchSemanticsNode().boundsInRoot
        val firstCardTop = composeRule.onNodeWithTag("bee-card-${flyingBee.id}")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Persistent feedback должен находиться под header", feedbackBounds.top >= headerBounds.bottom)
        assertTrue("Persistent feedback не должен перекрывать карточку", feedbackBounds.bottom <= firstCardTop)

        composeRule.onNodeWithTag("feedback-dismiss").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("observation-persistent-feedback").assertDoesNotExist()
    }

    @Test
    fun localUndoUnwindsDepartureThenAzimuthOneStepAtATimeInTheSameBeeCard() {
        val firstCycle = cycle(atPointBee, 1, releaseTime, returnTime)
        val heading = MutableStateFlow(availableHeading(269))
        val cycles = mutableStateOf(listOf(firstCycle))
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
                            now,
                            null,
                        )
                    },
                    onCaptureFlightAzimuth = { cycleId, value, onSuccess ->
                        cycles.value = cycles.value.map { cycle ->
                            if (cycle.id == cycleId) {
                                cycle.copy(azimuthDeg = value, azimuthCaptureConsumed = true)
                            } else cycle
                        }
                        onSuccess()
                    },
                    onUndoLastBeeAction = { beeId ->
                        assertEquals(atPointBee.id, beeId)
                        val latest = cycles.value.maxBy { it.sequenceNumber }
                        cycles.value = when {
                            latest.returnTime != null -> cycles.value.map { cycle ->
                                if (cycle.id == latest.id) cycle.copy(returnTime = null) else cycle
                            }
                            latest.azimuthDeg != null -> cycles.value.map { cycle ->
                                if (cycle.id == latest.id) cycle.copy(azimuthDeg = null) else cycle
                            }
                            else -> cycles.value.filterNot { it.id == latest.id }
                        }
                    },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithTag("bee-action-${atPointBee.id}").performClick()
        composeRule.onNodeWithText("В полёте").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-undo-${atPointBee.id}").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}").performClick()
        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}").assertTextContains("269°")

        composeRule.onNodeWithTag("bee-undo-${atPointBee.id}").performClick()
        composeRule.onNodeWithTag("bee-azimuth-${atPointBee.id}").assertTextContains("—°")
        composeRule.onNodeWithTag("bee-undo-${atPointBee.id}").assertIsDisplayed()

        composeRule.onNodeWithTag("bee-undo-${atPointBee.id}").performClick()
        composeRule.onNodeWithText("На точке").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-undo-${atPointBee.id}").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(1), cycles.value.map { it.sequenceNumber })
            assertEquals(returnTime, cycles.value.single().returnTime)
        }
    }

    @Test
    fun localUndoBelongsToTheBeeCardAndDoesNotReplaceOrdinaryFeedback() {
        val flyingCycle = cycle(flyingBee, 1, releaseTime, null, azimuthDeg = 269.0)
        val atPointCycle = cycle(atPointBee, 1, releaseTime, returnTime)
        val feedback = mutableStateOf<UiFeedback?>(autoFeedback(1, "Вылет сохранён"))
        var undoneBeeId: UUID? = null
        composeRule.setContent {
            Bee_searchTheme {
                BeeObservationScreen(
                    point = point(),
                    bees = listOf(flyingBee, atPointBee),
                    flightCycles = listOf(flyingCycle, atPointCycle),
                    beeEventInProgressIds = emptySet(),
                    feedback = feedback.value,
                    onDismissFeedback = { id -> if (feedback.value?.id == id) feedback.value = null },
                    isCompleting = false,
                    onRegisterReturn = {},
                    onStartNextFlight = {},
                    onUndoLastBeeAction = { undoneBeeId = it },
                    onComplete = {},
                    nowProvider = { now },
                )
            }
        }

        composeRule.onNodeWithText("Вылет сохранён").assertIsDisplayed()
        composeRule.onNodeWithTag("bee-undo-${flyingBee.id}")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.runOnIdle { assertEquals(flyingBee.id, undoneBeeId) }
        composeRule.onNodeWithText("Вылет сохранён").assertIsDisplayed()
        composeRule.onNodeWithTag("azimuth-undo-banner").assertDoesNotExist()
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
        observerId = UUID.randomUUID(),
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

    private fun captureActionRowScreenshot(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureActionRow") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "action-row")
        check(directory.mkdirs() || directory.isDirectory)
        FileOutputStream(File(directory, "$name.png")).use { output ->
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
