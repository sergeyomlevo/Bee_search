package org.beesearch.app.ui.points

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeObservationHistory
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.ObservationPointSummary
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.SavedObservationPointMarker
import org.beesearch.app.ui.settings.SettingsScreen
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PointsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsEntryOpensPoints() {
        var opened = false
        composeRule.setContent {
            Bee_searchTheme {
                SettingsScreen(
                    observers = listOf(observer),
                    currentObserverId = observer.id,
                    territories = listOf(territory),
                    currentTerritoryId = territory.id,
                    onBack = {},
                    onOpenPoints = { opened = true },
                    onSelectObserver = {},
                    onCreateObserver = { _, _, _, _, _ -> },
                    onSelectTerritory = {},
                    onCreateTerritory = { _, _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag("settings-points").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(true, opened) }
    }

    @Test
    fun tableShowsResultsCountsAndOpensTheCorrectPoint() {
        val found = summary(1, BeePresenceResult.BEES_FOUND, beeCount = 2)
        val noBees = summary(2, BeePresenceResult.NO_BEES_FOUND, beeCount = 0)
        val unresolved = summary(3, null, beeCount = 0)
        var opened: UUID? = null

        composeRule.setContent {
            Bee_searchTheme {
                PointsTable(
                    points = listOf(found, noBees, unresolved),
                    onOpenPoint = { opened = it },
                )
            }
        }

        composeRule.onNodeWithText("Пчёлы найдены").assertIsDisplayed()
        composeRule.onNodeWithText("Пчёлы отсутствуют").assertIsDisplayed()
        composeRule.onNodeWithText("Результат не зафиксирован").assertIsDisplayed()
        composeRule.onAllNodesWithText("Пчёл: 0", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithTag("point-row-${noBees.id}").performClick()
        composeRule.runOnIdle { assertEquals(noBees.id, opened) }
    }

    @Test
    fun pointMarkerOpensTheCorrectPoint() {
        val point = summary(7, BeePresenceResult.NO_BEES_FOUND, beeCount = 0)
        var selected: UUID? = null

        composeRule.setContent {
            Bee_searchTheme {
                SavedObservationPointMarker(point = point, onClick = { selected = point.id })
            }
        }

        composeRule.onNodeWithTag("point-marker-${point.id}").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(point.id, selected) }
    }

    @Test
    fun detailShowsMultipleBeesCyclesOpenStateAndOnlyPersistedAzimuth() {
        val firstBee = bee("WHITE", MarkPosition.NONE)
        val secondBee = bee("BLUE", MarkPosition.RIGHT_WING)
        val firstDeparture = Instant.parse("2026-09-17T06:00:00Z")
        val detail = ObservationPointDetail(
            point = point(),
            territory = territory,
            observer = observer,
            beeHistories = listOf(
                BeeObservationHistory(
                    firstBee,
                    listOf(
                        cycle(firstBee, 1, firstDeparture, firstDeparture.plusSeconds(70), 91.0),
                        cycle(firstBee, 2, firstDeparture.plusSeconds(100), null, null),
                    ),
                ),
                BeeObservationHistory(
                    secondBee,
                    listOf(cycle(secondBee, 1, firstDeparture.plusSeconds(10), firstDeparture.plusSeconds(40), null)),
                ),
            ),
        )

        composeRule.setContent {
            Bee_searchTheme {
                PointDetailScreen(
                    state = PointDetailUiState(detail = detail, isLoading = false),
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Белая").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Цикл 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Открыт").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("00:01:10").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("91°").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Синяя КП").assertExists()
        composeRule.onNodeWithText("—°").assertDoesNotExist()
    }

    @Test
    fun emptyTableIsSafe() {
        composeRule.setContent {
            Bee_searchTheme { PointsTable(points = emptyList(), onOpenPoint = {}) }
        }
        composeRule.onNodeWithText("Точек за выбранный период нет").assertIsDisplayed()
    }

    private fun summary(
        number: Int,
        result: BeePresenceResult?,
        beeCount: Int,
    ) = ObservationPointSummary(
        id = UUID.randomUUID(), territoryId = territory.id, observationYear = 2026,
        pointNumber = number, code = null, beePresenceResult = result,
        latitude = 56.0, longitude = 43.0, gpsAccuracyM = 4.0,
        createdAt = Instant.parse("2026-09-17T08:00:00Z"), completedAt = null,
        beeCount = beeCount, completedFlightCycleCount = 1,
    )

    private fun point() = ObservationPoint(
        id = UUID.randomUUID(), territoryId = territory.id, observerId = observer.id,
        observationYear = 2026, pointNumber = 4, beePresenceResult = BeePresenceResult.BEES_FOUND,
        code = "P4", latitude = 56.1, longitude = 43.2,
        gpsLatitude = 56.0, gpsLongitude = 43.1, gpsAccuracyM = 3.8,
        createdAt = Instant.parse("2026-09-17T05:00:00Z"), completedAt = null,
    )

    private fun bee(color: String, position: MarkPosition) = Bee(
        id = UUID.randomUUID(), observationPointId = UUID.randomUUID(),
        markColor = color, markPosition = position, createdAt = Instant.EPOCH,
    )

    private fun cycle(
        bee: Bee,
        sequence: Int,
        departure: Instant,
        returned: Instant?,
        azimuth: Double?,
    ) = FlightCycle(
        id = UUID.randomUUID(), beeId = bee.id, sequenceNumber = sequence,
        departureTime = departure, returnTime = returned, azimuthDeg = azimuth,
        azimuthCaptureConsumed = azimuth != null, createdAt = departure,
        updatedAt = returned ?: departure,
    )

    private companion object {
        val territory = Territory(
            UUID.randomUUID(), "T1", "Территория", "Регион", "Район", Instant.EPOCH, Instant.EPOCH,
        )
        val observer = Observer(
            UUID.randomUUID(), "O1", "Иванов", "Иван", null, null, Instant.EPOCH, Instant.EPOCH,
        )
    }
}
