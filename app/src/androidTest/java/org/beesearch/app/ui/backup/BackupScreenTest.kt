package org.beesearch.app.ui.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The S1 backup screen states.
 *
 * The system picker is deliberately not automated here: the grant itself is a physical-device step.
 * What is checked is that each state shows exactly one meaningful action and one path.
 */
class BackupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val path = "Загрузки/BeeSearch/Dev/Backup"

    @Test
    fun needsGrantShowsTheFolderAndOffersAccess() {
        var requested = false
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = BackupScreenState.NeedsGrant(path),
                    onBack = {},
                    onRequestAccess = { requested = true },
                )
            }
        }

        composeRule.onNodeWithText("Резервное копирование").assertIsDisplayed()
        composeRule.onNodeWithTag("backup-path").assertIsDisplayed()
        composeRule.onNodeWithText(path).assertIsDisplayed()
        composeRule.onNodeWithTag("backup-grant").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertTrue(requested) }
    }

    @Test
    fun readyShowsAvailableWithoutAnAccessButton() {
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(state = BackupScreenState.Ready(path), onBack = {}, onRequestAccess = {})
            }
        }

        composeRule.onNodeWithTag("backup-ready").assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_READY_LABEL).assertIsDisplayed()
        composeRule.onNodeWithTag("backup-path").assertIsDisplayed()
        composeRule.onNodeWithTag("backup-grant").assertDoesNotExist()
    }

    @Test
    fun accessLostOffersRestoringAccess() {
        var requested = false
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = BackupScreenState.AccessLost(path, "Нет доступа к папке резервных копий."),
                    onBack = {},
                    onRequestAccess = { requested = true },
                )
            }
        }

        composeRule.onNodeWithTag("backup-message").assertIsDisplayed()
        composeRule.onNodeWithTag("backup-restore-access").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertTrue(requested) }
    }

    @Test
    fun failedStateExplainsTheProblemAndOnlyOffersRechecking() {
        var retried = false
        val identityMessage = "В этой папке находится другая резервная копия Bee Search. Подключение не изменено."
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = BackupScreenState.Failed(path, identityMessage),
                    onBack = {},
                    onRequestAccess = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText(identityMessage).assertIsDisplayed()
        composeRule.onNodeWithTag("backup-grant").assertDoesNotExist()
        composeRule.onNodeWithTag("backup-retry").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun rejectedSelectionOffersThePickerAgain() {
        var requested = false
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = BackupScreenState.Failed(path, WRONG_FOLDER_MESSAGE, offerAccess = true),
                    onBack = {},
                    onRequestAccess = { requested = true },
                )
            }
        }

        composeRule.onNodeWithText(WRONG_FOLDER_MESSAGE).assertIsDisplayed()
        composeRule.onNodeWithTag("backup-retry").assertDoesNotExist()
        composeRule.onNodeWithTag("backup-grant").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertTrue(requested) }
    }

    @Test
    fun backLeavesTheBackupScreen() {
        var backed = false
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(state = BackupScreenState.Ready(path), onBack = { backed = true }, onRequestAccess = {})
            }
        }

        composeRule.onNodeWithText("Назад").performClick()

        composeRule.runOnIdle { assertTrue(backed) }
    }
}
