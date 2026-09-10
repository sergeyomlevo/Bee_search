package org.beesearch.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.unit.Density
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
                BeePreparationScreen(
                    point = point(BeePresenceResult.BEES_FOUND),
                    preparation = BeePreparationUiState(
                        pointId = pointId,
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = {},
                    onRecordNoBeesFound = {},
                    onComplete = { completionRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Завершить").performClick()
        composeRule.onNodeWithText("Завершить наблюдение?").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, completionRequests) }

        composeRule.onNodeWithTag("confirm-complete-observation").performClick()
        composeRule.runOnIdle { assertEquals(1, completionRequests) }
    }

    @Test
    fun preparedBeesEnableInitialGroupReleaseAction() {
        var releaseRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(),
                    preparation = BeePreparationUiState(
                        pointId = pointId,
                        bees = listOf(bee("WHITE", MarkPosition.RIGHT_WING)),
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = {},
                    onRecordNoBeesFound = {},
                    onComplete = {},
                    onStartInitialGroupRelease = { releaseRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Подготовка точки").assertIsDisplayed()
        composeRule.onNodeWithText("Добавить пчелу").assertIsDisplayed()
        composeRule.onNodeWithText("Белая").assertIsDisplayed()
        composeRule.onAllNodesWithText("КП").assertCountEquals(2)
        composeRule.onNodeWithText("Активная точка наблюдения").assertDoesNotExist()
        composeRule.onNodeWithText("Готово к выпуску: 1").assertDoesNotExist()
        composeRule.onNodeWithText("Добавляйте только фактически подготовленных пчёл.")
            .assertDoesNotExist()
        composeRule.onNodeWithText("Все подготовленные пчёлы получат одно общее время первого выпуска.")
            .assertDoesNotExist()
        composeRule.onNodeWithTag("initial-group-release").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, releaseRequests) }
    }

    @Test
    fun preparedBeeShowsColorAndTypeAndCanBeRemovedBeforeFirstLaunch() {
        val preparedBee = bee("GREEN", MarkPosition.NONE)
        var removedBeeId: UUID? = null
        composeRule.setContent {
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(),
                    preparation = BeePreparationUiState(
                        pointId = pointId,
                        bees = listOf(preparedBee),
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = { removedBeeId = it },
                    onRecordNoBeesFound = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithText("Зелёная").assertIsDisplayed()
        composeRule.onAllNodesWithText("Грудь").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Цвет метки: Зелёная")
            .assertCountEquals(1)
        composeRule.onNodeWithText("Обычная").assertDoesNotExist()
        composeRule.onNodeWithText("Удалить").performClick()
        composeRule.runOnIdle { assertEquals(preparedBee.id, removedBeeId) }
    }

    @Test
    fun preparedBeeListAndEditorShareOneScrollContainer() {
        val newestBee = bee("BLUE", MarkPosition.LEFT_WING)
        composeRule.setContent {
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(),
                    preparation = BeePreparationUiState(
                        pointId = pointId,
                        bees = listOf(bee("WHITE", MarkPosition.NONE), newestBee),
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = {},
                    onRecordNoBeesFound = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithTag("prepared-bee-list").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation-editor").assertIsDisplayed()
        BeeMarkCatalog.colors.forEach { color ->
            composeRule.onNodeWithTag("mark-color-${color.value}").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("add-bee").assertIsDisplayed()
        composeRule.onNodeWithText("Синяя").assertIsDisplayed()
        composeRule.onAllNodesWithText("КЛ").assertCountEquals(2)
        composeRule.onNodeWithText("Цвет метки").assertIsDisplayed()
        composeRule.onNodeWithText("Расположение метки").assertIsDisplayed()
        composeRule.onNodeWithText("Тип метки").assertDoesNotExist()
        composeRule.onNodeWithText("Последняя добавленная").assertDoesNotExist()
        composeRule.onNodeWithText("ВАЖНО! Первый выпуск").assertIsDisplayed()
        composeRule.onNodeWithText("После открытия клеточки пчёлы обычно вылетают почти одновременно, " +
            "с разницей в несколько секунд. В этот момент нажмите «Выпустить всех» — " +
            "для всех подготовленных пчёл будет зафиксировано одинаковое время вылета.")
            .assertDoesNotExist()
        composeRule.onNodeWithTag("launch-instruction-toggle").performClick()
        composeRule.onNodeWithTag("preparation-reading-list").assertDoesNotExist()
        composeRule.onNodeWithText("Свернуть").assertIsDisplayed()
        composeRule.onNodeWithText("После открытия клеточки пчёлы обычно вылетают почти одновременно, " +
            "с разницей в несколько секунд. В этот момент нажмите «Выпустить всех» — " +
            "для всех подготовленных пчёл будет зафиксировано одинаковое время вылета.")
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "В первом цикле полёт длительностью менее одной минуты при анализе не учитывается.",
        ).assertDoesNotExist()
        composeRule.onNodeWithTag("launch-instruction-toggle").performClick()
        composeRule.onNodeWithTag("preparation-editor").assertIsDisplayed()
        composeRule.onNodeWithText("Выпустить всех").assertIsDisplayed()
    }

    @Test
    fun manualScrollGivesPreparedBeeListTheViewportWithoutASeparateReviewMode() {
        val preparedBees = listOf(
            bee("WHITE", MarkPosition.NONE),
            bee("WHITE", MarkPosition.RIGHT_WING),
            bee("WHITE", MarkPosition.LEFT_WING),
            bee("YELLOW", MarkPosition.RIGHT_WING),
            bee("YELLOW", MarkPosition.NONE),
            bee("YELLOW", MarkPosition.LEFT_WING),
            bee("BLUE", MarkPosition.LEFT_WING),
            bee("BLUE", MarkPosition.NONE),
        )
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale = 1.7f),
            ) {
                Bee_searchTheme {
                    BeePreparationScreen(
                        point = point(),
                        preparation = BeePreparationUiState(
                            pointId = pointId,
                            bees = preparedBees,
                            beePresenceResult = BeePresenceResult.BEES_FOUND,
                            isLoading = false,
                        ),
                        isMutating = false,
                        isCompleting = false,
                        onAddBee = { _, _ -> },
                        onRemoveBee = {},
                        onRecordNoBeesFound = {},
                        onComplete = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag("preparation-editor").assertIsDisplayed()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("prepared-bee-list").performTouchInput { swipeDown() }

        composeRule.onNodeWithTag("remove-prepared-bee-${preparedBees.first().id}")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("remove-prepared-bee-${preparedBees[3].id}")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("preparation-editor").assertIsNotDisplayed()
    }

    @Test
    fun removingPreparedBeeLeavesTheRemainingListRowAccessible() {
        val firstBee = bee("WHITE", MarkPosition.NONE)
        val latestBee = bee("YELLOW", MarkPosition.RIGHT_WING)
        composeRule.setContent {
            val preparation = remember {
                mutableStateOf(
                    BeePreparationUiState(
                        pointId = pointId,
                        bees = listOf(firstBee, latestBee),
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                )
            }
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(),
                    preparation = preparation.value,
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = { id ->
                        preparation.value = preparation.value.copy(
                            bees = preparation.value.bees.filterNot { it.id == id },
                        )
                    },
                    onRecordNoBeesFound = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithTag("remove-prepared-bee-${latestBee.id}").performClick()
        composeRule.onNodeWithText("Белая").assertIsDisplayed()
        composeRule.onAllNodesWithText("Грудь").assertCountEquals(2)
    }

    @Test
    fun successfulAddShowsTheNewBeeWhileTheEditorStaysVisible() {
        val addedBeeId = UUID.fromString("00000000-0000-0000-0000-000000000222")
        composeRule.setContent {
            val preparation = remember {
                mutableStateOf(
                    BeePreparationUiState(
                        pointId = pointId,
                        bees = listOf(
                            bee("YELLOW", MarkPosition.NONE),
                            bee("YELLOW", MarkPosition.RIGHT_WING),
                            bee("YELLOW", MarkPosition.LEFT_WING),
                            bee("BLUE", MarkPosition.NONE),
                            bee("BLUE", MarkPosition.RIGHT_WING),
                            bee("BLUE", MarkPosition.LEFT_WING),
                        ),
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                )
            }
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(),
                    preparation = preparation.value,
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { color, position ->
                        preparation.value = preparation.value.copy(
                            bees = preparation.value.bees + bee(color, position).copy(id = addedBeeId),
                        )
                    },
                    onRemoveBee = {},
                    onRecordNoBeesFound = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithTag("add-bee").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("remove-prepared-bee-$addedBeeId").assertIsDisplayed()
        composeRule.onNodeWithTag("preparation-editor").assertIsDisplayed()
    }

    @Test
    fun noBeesResultRequiresConfirmationAndCancelDoesNotInvokePersistenceAction() {
        var noBeesRequests = 0
        composeRule.setContent {
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(),
                    preparation = BeePreparationUiState(pointId = pointId, isLoading = false),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = {},
                    onRecordNoBeesFound = { noBeesRequests += 1 },
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithTag("preparation-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("record-no-bees").performClick()
        composeRule.onNodeWithText("Пчёлы отсутствуют?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Точка наблюдения будет сохранена с результатом «пчёлы отсутствуют» и завершена.",
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, noBeesRequests) }

        composeRule.onNodeWithText("Отмена").performClick()
        composeRule.onNodeWithText("Пчёлы отсутствуют?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, noBeesRequests) }

        composeRule.onNodeWithTag("record-no-bees").performClick()
        composeRule.onNodeWithTag("confirm-no-bees").performClick()
        composeRule.runOnIdle { assertEquals(1, noBeesRequests) }
    }

    @Test
    fun noBeesActionIsUnavailableAfterBeeWasFound() {
        composeRule.setContent {
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(BeePresenceResult.BEES_FOUND),
                    preparation = BeePreparationUiState(
                        pointId = pointId,
                        bees = listOf(bee("BLUE", MarkPosition.NONE)),
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = {},
                    onRecordNoBeesFound = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithText("Пчёлы отсутствуют").assertDoesNotExist()
    }

    @Test
    fun directPositionSelectionAddsChosenCombination() {
        var added: Pair<String, MarkPosition>? = null
        composeRule.setContent {
            Bee_searchTheme {
                BeeSelector(
                    bees = emptyList(),
                    enabled = true,
                    onAddBee = { color, position -> added = color to position },
                )
            }
        }

        composeRule.onNodeWithTag("mark-color-WHITE").performClick()
        composeRule.onNodeWithTag("mark-position-RIGHT_WING").performClick()
        composeRule.onNodeWithTag("add-bee").performClick()
        composeRule.runOnIdle { assertEquals("WHITE" to MarkPosition.RIGHT_WING, added) }
    }

    @Test
    fun successfulAddsAdvanceThroughColorBeforeNextCatalogColor() {
        composeRule.setContent {
            val bees = remember { mutableStateOf(emptyList<Bee>()) }
            Bee_searchTheme {
                BeeSelector(
                    bees = bees.value,
                    enabled = true,
                    onAddBee = { color, position -> bees.value = bees.value + bee(color, position) },
                )
            }
        }

        composeRule.onNodeWithTag("mark-color-WHITE").performClick()
        composeRule.onNodeWithTag("mark-position-NONE").assertIsSelected()
        composeRule.onNodeWithTag("add-bee").performClick()
        composeRule.onNodeWithTag("mark-color-WHITE").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-NONE").assertIsNotEnabled()
        composeRule.onNodeWithTag("mark-position-RIGHT_WING").assertIsSelected()

        composeRule.onNodeWithTag("add-bee").performClick()
        composeRule.onNodeWithTag("mark-color-WHITE").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-LEFT_WING").assertIsSelected()

        composeRule.onNodeWithTag("add-bee").performClick()
        composeRule.onNodeWithTag("mark-color-WHITE").assertIsNotEnabled()
        composeRule.onNodeWithTag("mark-color-YELLOW").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-NONE").assertIsSelected()
        composeRule.onNodeWithTag("add-bee").assertIsEnabled()
    }

    @Test
    fun preparationScreenPreservesSelectorSequenceAsBeeRowsAreInserted() {
        composeRule.setContent {
            val preparation = remember {
                mutableStateOf(BeePreparationUiState(pointId = pointId, isLoading = false))
            }
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(),
                    preparation = preparation.value,
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { color, position ->
                        preparation.value = preparation.value.copy(
                            bees = preparation.value.bees + bee(color, position),
                            beePresenceResult = BeePresenceResult.BEES_FOUND,
                        )
                    },
                    onRemoveBee = {},
                    onRecordNoBeesFound = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithTag("preparation-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("mark-color-WHITE").performClick()
        composeRule.onNodeWithTag("mark-position-NONE").assertIsSelected()
        composeRule.onNodeWithTag("add-bee").performClick()

        composeRule.onNodeWithTag("preparation-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("mark-color-WHITE").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-RIGHT_WING").assertIsSelected()
        composeRule.onNodeWithTag("add-bee").performClick()

        composeRule.onNodeWithTag("preparation-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("mark-color-WHITE").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-LEFT_WING").assertIsSelected()
        composeRule.onNodeWithTag("add-bee").performClick()

        composeRule.onNodeWithTag("preparation-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("mark-color-YELLOW").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-NONE").assertIsSelected()
    }

    @Test
    fun persistentPreparationStartsWithNextAvailableCombinationAfterFirstBee() {
        composeRule.setContent {
            Bee_searchTheme {
                BeePreparationScreen(
                    point = point(),
                    preparation = BeePreparationUiState(
                        pointId = pointId,
                        bees = listOf(bee("WHITE", MarkPosition.NONE)),
                        beePresenceResult = BeePresenceResult.BEES_FOUND,
                        isLoading = false,
                    ),
                    isMutating = false,
                    isCompleting = false,
                    onAddBee = { _, _ -> },
                    onRemoveBee = {},
                    onRecordNoBeesFound = {},
                    onComplete = {},
                )
            }
        }

        composeRule.onNodeWithTag("preparation-editor").assertIsDisplayed()
        composeRule.onNodeWithTag("mark-color-WHITE").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-RIGHT_WING").assertIsSelected()
    }

    @Test
    fun manualPositionIsAddedAndThenSequenceContinuesFromIt() {
        composeRule.setContent {
            val bees = remember {
                mutableStateOf(listOf(bee("WHITE", MarkPosition.NONE)))
            }
            Bee_searchTheme {
                BeeSelector(
                    bees = bees.value,
                    enabled = true,
                    onAddBee = { color, position -> bees.value = bees.value + bee(color, position) },
                )
            }
        }

        composeRule.onNodeWithTag("mark-color-WHITE").performClick()
        composeRule.onNodeWithTag("mark-position-RIGHT_WING").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-LEFT_WING").performClick()
        composeRule.onNodeWithTag("mark-position-LEFT_WING").assertIsSelected()
        composeRule.onNodeWithTag("add-bee").performClick()

        composeRule.onNodeWithTag("mark-color-WHITE").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-RIGHT_WING").assertIsSelected()
    }

    @Test
    fun removalRecalculatesAvailabilityWithoutLosingValidSelection() {
        lateinit var beesState: MutableState<List<Bee>>
        composeRule.setContent {
            val bees = remember {
                mutableStateOf(
                    listOf(
                        bee("WHITE", MarkPosition.NONE),
                        bee("GREEN", MarkPosition.NONE),
                    ),
                )
            }
            beesState = bees
            Bee_searchTheme {
                BeeSelector(
                    bees = bees.value,
                    enabled = true,
                    onAddBee = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("mark-color-GREEN").performClick()
        composeRule.onNodeWithTag("mark-position-RIGHT_WING").assertIsSelected()
        composeRule.runOnIdle {
            beesState.value = listOf(bee("GREEN", MarkPosition.NONE))
        }

        composeRule.onNodeWithTag("mark-color-GREEN").assertIsSelected()
        composeRule.onNodeWithTag("mark-position-RIGHT_WING").assertIsSelected()
        composeRule.onNodeWithTag("add-bee").assertIsEnabled()
        composeRule.onNodeWithTag("mark-color-WHITE").performClick()
        composeRule.onNodeWithTag("mark-position-NONE").assertIsSelected()
    }

    @Test
    fun fullCatalogKeepsAddDisabledWithoutSelection() {
        composeRule.setContent {
            Bee_searchTheme {
                BeeSelector(
                    bees = BeeMarkCatalog.supportedCombinations.map { combination ->
                        bee(combination.markColor, combination.markPosition)
                    },
                    enabled = true,
                    onAddBee = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Все поддерживаемые сочетания меток уже добавлены.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("add-bee").assertIsNotEnabled()
    }

    private val pointId = UUID.fromString("00000000-0000-0000-0000-000000000111")

    private fun bee(markColor: String, markPosition: MarkPosition) = Bee(
        id = UUID.randomUUID(),
        observationPointId = pointId,
        markColor = markColor,
        markPosition = markPosition,
        createdAt = Instant.parse("2026-08-27T08:32:00Z"),
    )

    private fun point(beePresenceResult: BeePresenceResult? = null) = ObservationPoint(
        id = pointId,
        territoryId = UUID.randomUUID(),
        observerId = UUID.randomUUID(),
        observationYear = 2026,
        pointNumber = 1,
        beePresenceResult = beePresenceResult,
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
