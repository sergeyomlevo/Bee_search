package org.beesearch.app.ui.backup

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assert
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID

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
        composeRule.onNodeWithText("Доступно").assertIsDisplayed()
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

        composeRule.onNodeWithContentDescription("Назад").performClick()

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
    fun readyWithoutBackupsShowsApprovedContentsAndPromise() {
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(state = ready(BackupSnapshotsUi.None), onBack = {}, onRequestAccess = {})
            }
        }

        composeRule.onNodeWithTag("backup-ready").assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_SECTION_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag("backup-promise").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_PROMISE).assertExists()
        composeRule.onNodeWithTag("backup-none").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_NO_SNAPSHOTS).assertExists()
        composeRule.onNodeWithTag("backup-create").assertIsDisplayed().assertIsEnabled()
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
        composeRule.onNodeWithTag("backup-create").assertIsNotEnabled()
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
                        BackupOperationUi.Created(4),
                    ),
                    onBack = {},
                    onRequestAccess = {},
                )
            }
        }

        composeRule.onNodeWithTag("backup-created").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_CREATED_TITLE).assertExists()
        composeRule.onNodeWithText(BACKUP_CREATED_BODY).assertExists()
        composeRule.onNodeWithText("Проверено: 4 из 4 файлов").assertExists()
        // A further backup stays possible.
        composeRule.onNodeWithTag("backup-create").assertIsEnabled()
    }

    @Test
    fun anEmptyPublishedSummaryAfterSuccessMakesNoLastBackupClaim() {
        val outcome = org.beesearch.app.data.backuprepository.BackupCreateOutcome.Created(
            snapshots = org.beesearch.app.data.backuprepository.BackupSnapshotStatus.None,
            committedAtEpochMs = 2_000_000_000_000,
            verifiedMediaCount = 4,
        )
        val screen = outcome.toScreenState(
            path, java.time.ZoneId.of("UTC"), java.util.Locale.forLanguageTag("ru-RU"), BackupSnapshotsUi.None,
        ) as BackupScreenState.Ready
        assertEquals(BackupSnapshotsUi.None, screen.snapshots)
        assertEquals(BackupOperationUi.Created(4), screen.operation)
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(screen, onBack = {}, onRequestAccess = {})
            }
        }

        composeRule.onNodeWithTag("backup-created").assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_CREATED_TITLE).assertExists()
        composeRule.onNodeWithText(BACKUP_NO_SNAPSHOTS).assertDoesNotExist()
        composeRule.onNodeWithTag("backup-latest").assertDoesNotExist()
        composeRule.onNodeWithText(BACKUP_LAST_PREFIX).assertDoesNotExist()
    }

    @Test
    fun successKeepsPublishedSummaryProblemsVisibleWithoutALatestDate() {
        val snapshots = mutableStateOf<BackupSnapshotsUi>(BackupSnapshotsUi.Problem(BACKUP_UNUSABLE_MESSAGE))
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(ready(snapshots.value, BackupOperationUi.Created(4)), onBack = {}, onRequestAccess = {})
            }
        }

        for (message in listOf(BACKUP_UNUSABLE_MESSAGE, "Не удалось прочитать папку резервных копий.")) {
            composeRule.runOnIdle { snapshots.value = BackupSnapshotsUi.Problem(message) }
            composeRule.onNodeWithText(BACKUP_CREATED_TITLE).assertExists()
            composeRule.onNodeWithTag("backup-snapshot-warning").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText(message).assertExists()
            composeRule.onNodeWithTag("backup-latest").assertDoesNotExist()
        }
    }

    @Test
    fun mediaFailureShowsCountsAndRetryWithoutSuccess() {
        var retried = false
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(
                        BackupSnapshotsUi.None,
                        BackupOperationUi.MediaFailed(failedCount = 1, totalCount = 4),
                    ),
                    onBack = {},
                    onRequestAccess = {},
                    onCreateBackup = { retried = true },
                )
            }
        }

        composeRule.onNodeWithTag("backup-media-error").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Не удалось сохранить 1 из 4 файлов фото или видео.").assertExists()
        composeRule.onNodeWithText(BACKUP_MEDIA_ERROR_RETRY).assertExists()
        composeRule.onNodeWithTag("backup-created").assertDoesNotExist()
        composeRule.onNodeWithTag("backup-create").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun zeroMediaSuccessHasNoMediaClaimOrCounter() {
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(BackupSnapshotsUi.None, BackupOperationUi.Created(0)),
                    onBack = {},
                    onRequestAccess = {},
                )
            }
        }

        composeRule.onNodeWithTag("backup-created").assertIsDisplayed()
        composeRule.onNodeWithText(BACKUP_CREATED_NO_MEDIA_BODY).assertExists()
        composeRule.onNodeWithTag("backup-verified-count").assertDoesNotExist()
        composeRule.onNodeWithText("0 из 0", substring = true).assertDoesNotExist()
    }

    @Test
    fun disclosureExpandsToApprovedUserFacingList() {
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(state = ready(BackupSnapshotsUi.None), onBack = {}, onRequestAccess = {})
            }
        }

        composeRule.onNodeWithTag("backup-disclosure").performClick()
        composeRule.onNodeWithText("Территории и ареалы", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Фото и видео, относящиеся к этим данным", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Резервное копирование данных исследований").assertDoesNotExist()
    }

    @Test
    fun readyPathOpensOnlyTheDirectoryActionOnEveryTap() {
        var opens = 0
        var otherActions = 0
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(
                    state = ready(BackupSnapshotsUi.Latest("31 декабря 2026, 23:59", null)),
                    onBack = {},
                    onRequestAccess = { otherActions++ },
                    onCreateBackup = { otherActions++ },
                    onRetry = { otherActions++ },
                    onOpenDirectory = { opens++ },
                )
            }
        }
        composeRule.onNodeWithTag("backup-path").assert(hasClickAction())
            .assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
            .performClick().performClick()
        composeRule.runOnIdle {
            assertEquals(2, opens)
            assertEquals(0, otherActions)
        }
    }

    @Test
    fun longPathLinkRemainsUsableAtNormalAndLargeFontScale() {
        val fontScale = mutableStateOf(1f)
        val longPath = "Загрузки/BeeSearch/Длинное название варианта/Backup"
        var opens = 0
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                Bee_searchTheme {
                    BackupScreen(BackupScreenState.Ready(longPath), onBack = {}, onRequestAccess = {},
                        onOpenDirectory = { opens++ })
                }
            }
        }
        for (scale in listOf(1f, 1.7f)) {
            composeRule.runOnIdle { fontScale.value = scale }
            composeRule.onNodeWithTag("backup-path").performScrollTo().assertIsDisplayed().performClick()
            val bounds = composeRule.onNodeWithTag("backup-path").getBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top >= 48.dp)
            composeRule.onNodeWithTag("backup-create").assertIsDisplayed()
        }
        composeRule.runOnIdle { assertEquals(2, opens) }
    }

    @Test
    fun lostAccessPathCannotOpenDirectoryAndRestoreAccessKeepsItsOwnAction() {
        var opens = 0
        var restores = 0
        composeRule.setContent {
            Bee_searchTheme {
                BackupScreen(BackupScreenState.AccessLost(path, "Доступ потерян"),
                    onBack = {}, onRequestAccess = { restores++ }, onOpenDirectory = { opens++ })
            }
        }
        composeRule.onNodeWithTag("backup-path").assert(hasClickAction().not())
        composeRule.onNodeWithTag("backup-restore-access").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(0, opens); assertEquals(1, restores) }
    }

    @Test
    fun v2StatesKeepBottomActionStableAtLargeFontScaleAndProduceReviewScreenshots() {
        val state = mutableStateOf(ready(BackupSnapshotsUi.None))
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.7f)) {
                Bee_searchTheme {
                    // Match MainActivity: its outer Scaffold owns the system-bar safe area.
                    Scaffold { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            BackupScreen(state.value, onBack = {}, onRequestAccess = {})
                        }
                    }
                }
            }
        }
        val reviewDir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "backup-v2-review-${UUID.randomUUID()}",
        ).apply { mkdirs() }
        fun capture(name: String) {
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("Назад").assertIsDisplayed()
            val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
            File(reviewDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun buttonBounds() = composeRule.onNodeWithTag("backup-create").getBoundsInRoot()

        composeRule.waitForIdle(); capture("ready"); val readyBounds = buttonBounds()
        composeRule.onNodeWithTag("backup-promise").performScrollTo().assertIsDisplayed()
        capture("ready-scrolled")
        composeRule.onNodeWithTag("backup-disclosure").performScrollTo()
        composeRule.onNodeWithTag("backup-disclosure").performClick()
        composeRule.waitForIdle(); capture("ready-expanded")
        composeRule.onNodeWithText("Фото и видео, относящиеся к этим данным", substring = true, useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        capture("expanded-scrolled")
        state.value = ready(BackupSnapshotsUi.None, BackupOperationUi.Creating)
        composeRule.waitForIdle(); capture("working"); val workingBounds = buttonBounds()
        state.value = ready(BackupSnapshotsUi.Latest("31 декабря 2026, 23:59", null), BackupOperationUi.Created(4))
        composeRule.waitForIdle(); capture("success"); val successBounds = buttonBounds()
        composeRule.onNodeWithTag("backup-latest").performScrollTo().assertIsDisplayed()
        capture("success-scrolled")
        state.value = ready(BackupSnapshotsUi.Latest("31 декабря 2026, 23:59", null), BackupOperationUi.Created(0))
        composeRule.waitForIdle(); capture("success-no-media")
        state.value = ready(BackupSnapshotsUi.None, BackupOperationUi.MediaFailed(1, 4))
        composeRule.waitForIdle(); capture("error"); val errorBounds = buttonBounds()
        assertEquals(readyBounds.top.value, workingBounds.top.value, 1f)
        assertEquals(readyBounds.top.value, successBounds.top.value, 1f)
        assertEquals(readyBounds.top.value, errorBounds.top.value, 1f)
        assertTrue(reviewDir.listFiles()?.map(File::getName)?.toSet()?.containsAll(
            setOf("ready.png", "ready-expanded.png", "working.png", "success.png", "success-no-media.png", "error.png"),
        ) == true)
        println("Backup v2 review screenshots: ${reviewDir.absolutePath}")
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
        composeRule.onNodeWithTag("backup-create").performClick()

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
        composeRule.onNodeWithTag("backup-create").assertExists()
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

        composeRule.onNodeWithTag("backup-create").performClick()

        composeRule.runOnIdle { assertEquals(1, createCalls) }
    }
}
