package org.beesearch.app.ui.area

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.UUID
import org.beesearch.app.ui.map.AreaNameDialog
import org.beesearch.app.ui.map.CANCEL_LABEL
import org.beesearch.app.ui.map.CREATE_AREA_LABEL
import org.beesearch.app.ui.map.DELETE_AREA_LABEL
import org.beesearch.app.ui.map.DELETE_AREA_CONFIRM_LABEL
import org.beesearch.app.ui.map.DeleteAreaDialog
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapGeoBounds
import org.beesearch.app.ui.map.SEND_AREA_LABEL
import org.beesearch.app.ui.map.VIEW_AREA_ON_MAP_LABEL
import org.beesearch.app.ui.map.areaUnionKm2
import org.beesearch.app.ui.map.formatSquareKilometers
import org.beesearch.app.ui.objects.ObjectsScreen
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Ареал screen is a card for the one Ареал of the current Territory: what it is, how to look at
 * it on the map and how to send its file. Editing участки is a separate screen, and the name is set
 * once, so the card must not offer renaming. A damaged value must never be presented as "not
 * created".
 */
class AreaScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val area = MapArea(
        id = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d"),
        name = "Лух",
        bounds = listOf(
            MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0),
            MapGeoBounds(north = 56.9, east = 38.9, south = 56.8, west = 38.8),
        ),
    )

    private fun show(
        read: MapAreaReadResult,
        territoryCode: String? = "DEV-BENCH2",
        message: String? = null,
        sending: Boolean = false,
        onCreate: () -> Unit = {},
        onViewOnMap: () -> Unit = {},
        onSend: () -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        composeRule.setContent {
            Bee_searchTheme {
                AreaScreen(
                    territoryCode = territoryCode,
                    read = read,
                    message = message,
                    sending = sending,
                    onCreate = onCreate,
                    onViewOnMap = onViewOnMap,
                    onSend = onSend,
                    onDelete = onDelete,
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun objectsScreenOffersTheAreaObject() {
        var opened = false
        composeRule.setContent {
            Bee_searchTheme {
                ObjectsScreen(onBack = {}, onOpenArea = { opened = true }, onOpenObservationPoints = {})
            }
        }

        composeRule.onNodeWithTag("objects-area").assertIsDisplayed()
        composeRule.onNodeWithText("Ареал").assertIsDisplayed()
        composeRule.onNodeWithTag("objects-area").performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun anAbsentAreaIsShownAsNotCreatedAndCreateOpensTheMapEditor() {
        var created = false
        show(MapAreaReadResult.Absent, onCreate = { created = true })

        composeRule.onNodeWithTag(AREA_NOT_CREATED_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Ареал не создан").assertIsDisplayed()
        composeRule.onNodeWithTag(CREATE_AREA_TAG).performClick()
        composeRule.runOnIdle { assertTrue(created) }
    }

    @Test
    fun anAbsentAreaOffersNothingToViewOrSend() {
        show(MapAreaReadResult.Absent)

        composeRule.onNodeWithTag(VIEW_AREA_ON_MAP_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SEND_AREA_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(DELETE_AREA_TAG).assertDoesNotExist()
        composeRule.onNodeWithText(VIEW_AREA_ON_MAP_LABEL).assertDoesNotExist()
        composeRule.onNodeWithText(SEND_AREA_LABEL).assertDoesNotExist()
    }

    @Test
    fun withoutATerritoryTheScreenDoesNotOfferCreation() {
        show(MapAreaReadResult.Absent, territoryCode = null)

        composeRule.onNodeWithText("Текущая территория не выбрана").assertIsDisplayed()
        composeRule.onNodeWithTag(CREATE_AREA_TAG).assertDoesNotExist()
    }

    @Test
    fun anExistingAreaShowsItsNameSectionCountAndTotalArea() {
        show(MapAreaReadResult.Present(area))

        composeRule.onNodeWithTag(AREA_CARD_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(AREA_NAME_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Лух").assertIsDisplayed()
        composeRule.onNodeWithText("Участков: 2").assertIsDisplayed()
        // The same helper the editor uses for one участок, so the two screens cannot disagree.
        val expectedArea = "$AREA_TOTAL_AREA_LABEL: ${formatSquareKilometers(areaUnionKm2(area.bounds))} км²"
        composeRule.onNodeWithTag(AREA_TOTAL_AREA_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(expectedArea).assertIsDisplayed()
    }

    @Test
    fun theCardOffersViewingSendingAndDeletingOnly() {
        show(MapAreaReadResult.Present(area))

        composeRule.onNodeWithTag(VIEW_AREA_ON_MAP_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SEND_AREA_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DELETE_AREA_TAG).assertIsDisplayed()
        // Renaming was retired: the name is set once when the Ареал is created.
        composeRule.onNodeWithText("Переименовать").assertDoesNotExist()
        composeRule.onNodeWithTag("rename-area").assertDoesNotExist()
        // Raw coordinates, the technical id and file details are not user-facing.
        composeRule.onNodeWithText(area.id.toString()).assertDoesNotExist()
        composeRule.onNodeWithText("56.0").assertDoesNotExist()
    }

    @Test
    fun viewingOnTheMapOpensTheAreaView() {
        var viewed = false
        show(MapAreaReadResult.Present(area), onViewOnMap = { viewed = true })

        composeRule.onNodeWithTag(VIEW_AREA_ON_MAP_TAG).performClick()

        composeRule.runOnIdle { assertTrue(viewed) }
    }

    @Test
    fun sendingTheAreaUsesTheSendAction() {
        var sent = false
        show(MapAreaReadResult.Present(area), onSend = { sent = true })

        composeRule.onNodeWithTag(SEND_AREA_TAG).performClick()

        composeRule.runOnIdle { assertTrue(sent) }
    }

    @Test
    fun theSendActionIsDisabledWhileTheFileIsBeingPrepared() {
        show(MapAreaReadResult.Present(area), sending = true)

        composeRule.onNodeWithTag(SEND_AREA_TAG).assertIsNotEnabled()
    }

    @Test
    fun aDamagedAreaIsNotShownAsAbsentAndOffersNoLifecycleActions() {
        show(MapAreaReadResult.Corrupt("bad json"))

        composeRule.onNodeWithTag(AREA_CORRUPT_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Данные ареала повреждены").assertIsDisplayed()
        composeRule.onNodeWithTag(AREA_NOT_CREATED_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(CREATE_AREA_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(VIEW_AREA_ON_MAP_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SEND_AREA_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(DELETE_AREA_TAG).assertDoesNotExist()
    }

    @Test
    fun deleteAsksForConfirmationAndNamesTheArea() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            Bee_searchTheme {
                DeleteAreaDialog(
                    areaName = area.name,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithTag("delete-area-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Удалить ареал «Лух»?").assertIsDisplayed()
        composeRule.onNodeWithText("Территория и точки наблюдения останутся.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(DELETE_AREA_CONFIRM_LABEL).assertIsDisplayed()

        // Nothing happens until the destructive action is chosen explicitly.
        composeRule.onNodeWithText(DELETE_AREA_CONFIRM_LABEL).performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }
        assertFalse(dismissed)
    }

    @Test
    fun cancellingTheDeleteConfirmationChangesNothing() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            Bee_searchTheme {
                DeleteAreaDialog(
                    areaName = area.name,
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText(CANCEL_LABEL).performClick()

        composeRule.runOnIdle { assertTrue(dismissed) }
        assertFalse(confirmed)
    }

    @Test
    fun theNameDialogShowsTheCurrentNameAndAcceptsAChange() {
        var typed = ""
        var confirmed = false
        composeRule.setContent {
            Bee_searchTheme {
                AreaNameDialog(
                    name = "Лух",
                    blankName = false,
                    onNameChange = { typed = it },
                    onConfirm = { confirmed = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Название ареала").assertIsDisplayed()
        composeRule.onNodeWithText("Лух").assertIsDisplayed()
        composeRule.onNodeWithTag("area-name-field").performClick()

        composeRule.onNodeWithText("Сохранить").performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }
        assertTrue(typed.isEmpty())
    }

    @Test
    fun aBlankNameShowsAValidationErrorAndKeepsTheDialogOpen() {
        composeRule.setContent {
            Bee_searchTheme {
                AreaNameDialog(
                    name = "   ",
                    blankName = true,
                    onNameChange = {},
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("area-name-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("area-name-error").assertIsDisplayed()
        composeRule.onNodeWithText("Введите название ареала").assertIsDisplayed()
        composeRule.onNodeWithText(CREATE_AREA_LABEL).assertDoesNotExist()
    }

    @Test
    fun anErrorMessageOnTheScreenIsVisible() {
        show(MapAreaReadResult.Present(area), message = "Не удалось подготовить файл ареала")

        composeRule.onNodeWithText("Не удалось подготовить файл ареала").assertIsDisplayed()
    }
}
