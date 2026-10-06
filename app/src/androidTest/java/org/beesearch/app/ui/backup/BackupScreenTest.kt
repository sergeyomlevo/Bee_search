package org.beesearch.app.ui.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
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

    // --- S2: the manual metadata backup ---
    //
    // The owner's device runs at font scale 1.7, so content lower on the screen is below the fold.
    // Assertions therefore scroll to the node first instead of assuming it is on screen.

    private fun ready(
        snapshots: BackupSnapshotsUi,
        operation: BackupOperationUi = BackupOperationUi.Idle,
    ) = BackupScreenState.Ready(path, snapshots, operation)

    @Test
    fun readyWithoutBackupsExplainsWhatIsSavedAndExcludesMedia() {
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(state = ready(BackupSnapshotsUi.None), onBack = {}, onRequestAccess = {})
            }
        }

        composeRule.onNodeWithTag("backup-ready").assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_SECTION_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag("backup-media-note").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_MEDIA_NOTE).assertExists()
        composeRule.onNodeWithTag("backup-none").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_NO_SNAPSHOTS).assertExists()
        composeRule.onNodeWithTag("backup-create").performScrollTo().assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(BACKUP_CREATE_ACTION).assertExists()
    }

    @Test
    fun readyWithALatestBackupShowsItsTime() {
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(BackupSnapshotsUi.Latest("6 октября 2026, 20:15", warning = null)),
                    onBack = {},
                    onRequestAccess = {},
                )
            }
        }

        composeRule.onNodeWithText(BACKUP_LAST_PREFIX).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("backup-latest").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("6 октября 2026, 20:15").assertExists()
        composeRule.onNodeWithTag("backup-snapshot-warning").assertDoesNotExist()
        composeRule.onNodeWithTag("backup-none").assertDoesNotExist()
    }

    @Test
    fun anUnverifiableOlderCopyIsShownAsAWarningNextToTheValidBackup() {
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(
                        BackupSnapshotsUi.Latest("6 октября 2026, 20:15", warning = BACKUP_SNAPSHOT_WARNING),
                    ),
                    onBack = {},
                    onRequestAccess = {},
                )
            }
        }

        composeRule.onNodeWithTag("backup-latest").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("6 октября 2026, 20:15").assertExists()
        composeRule.onNodeWithTag("backup-snapshot-warning").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_SNAPSHOT_WARNING).assertExists()
    }

    @Test
    fun unreadableBackupsMakeNoLastBackupClaim() {
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(BackupSnapshotsUi.Problem(BACKUP_UNUSABLE_MESSAGE)),
                    onBack = {},
                    onRequestAccess = {},
                )
            }
        }

        composeRule.onNodeWithTag("backup-snapshot-warning").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_UNUSABLE_MESSAGE).assertExists()
        composeRule.onNodeWithTag("backup-latest").assertDoesNotExist()
        composeRule.onNodeWithText(BACKUP_LAST_PREFIX).assertDoesNotExist()
    }

    @Test
    fun creatingShowsProgressWithoutPercentageAndBlocksASecondStart() {
        var createCalls = 0
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(BackupSnapshotsUi.None, BackupOperationUi.Creating),
                    onBack = {},
                    onRequestAccess = {},
                    onCreateBackup = { createCalls++ },
                )
            }
        }

        composeRule.onNodeWithTag("backup-creating").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_CREATING).assertExists()
        composeRule.onNodeWithTag("backup-create").performScrollTo().assertIsNotEnabled()
        // No invented progress and no explicit cancel action.
        composeRule.onNodeWithText("%", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Отмена").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, createCalls) }
    }

    @Test
    fun successfulCreationStatesTheApprovedMeaning() {
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(
                        BackupSnapshotsUi.Latest("6 октября 2026, 20:15", warning = null),
                        BackupOperationUi.Created,
                    ),
                    onBack = {},
                    onRequestAccess = {},
                )
            }
        }

        composeRule.onNodeWithTag("backup-created").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_CREATED_TITLE).assertExists()
        composeRule.onNodeWithText(BACKUP_CREATED_BODY).assertExists()
        composeRule.onNodeWithText(BACKUP_CREATED_MEDIA_NOTE).assertExists()
        // A further backup stays possible.
        composeRule.onNodeWithTag("backup-create").performScrollTo().assertIsEnabled()
    }

    @Test
    fun failedCreationExplainsTheProblemAndOffersRetry() {
        var retried = false
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(
                        BackupSnapshotsUi.None,
                        BackupOperationUi.Problem(
                            "На устройстве недостаточно свободного места для резервных копий Bee Search.",
                        ),
                    ),
                    onBack = {},
                    onRequestAccess = {},
                    onCreateBackup = { retried = true },
                )
            }
        }

        composeRule.onNodeWithTag("backup-message").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Повторить").assertExists()
        composeRule.onNodeWithTag("backup-created").assertDoesNotExist()
        composeRule.onNodeWithTag("backup-create-retry").performScrollTo().performClick()

        composeRule.runOnIdle { assertTrue(retried) }
    }

    /**
     * A storage failure while access still works keeps its own message and a retry on this screen: it
     * must never be routed into the access states, which would show a healthy-looking screen after a
     * failed backup.
     */
    @Test
    fun storageFailureStaysOnThisScreenWithItsMessage() {
        val message = "Не удалось получить доступ к папке резервных копий."
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(BackupSnapshotsUi.None, BackupOperationUi.Problem(message)),
                    onBack = {},
                    onRequestAccess = {},
                )
            }
        }

        composeRule.onNodeWithTag("backup-ready").assertIsDisplayed()
        composeRule.onNodeWithTag("backup-message").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(message).assertExists()
        composeRule.onNodeWithTag("backup-create-retry").assertExists()
        composeRule.onNodeWithTag("backup-restore-access").assertDoesNotExist()
    }

    @Test
    fun createButtonStartsTheBackupOncePerTap() {
        var createCalls = 0
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(BackupSnapshotsUi.None),
                    onBack = {},
                    onRequestAccess = {},
                    onCreateBackup = { createCalls++ },
                )
            }
        }

        composeRule.onNodeWithTag("backup-create").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(1, createCalls) }
    }
}
