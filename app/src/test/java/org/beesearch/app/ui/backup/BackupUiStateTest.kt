package org.beesearch.app.ui.backup

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import org.beesearch.app.data.backuprepository.BackupAccessAction
import org.beesearch.app.data.backuprepository.BackupAccessOutcome
import org.beesearch.app.data.backuprepository.BackupAccessProblem
import org.beesearch.app.data.backuprepository.BackupAccessStatus
import org.beesearch.app.data.backuprepository.BackupCreateOutcome
import org.beesearch.app.data.backuprepository.BackupSnapshotStatus
import org.beesearch.app.data.backuprepository.RepositoryError
import org.beesearch.app.data.backuprepository.toBackupAccessProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen-facing translation of repository access results.
 *
 * The point of these tests is that no storage-level name can reach a user: every repository error
 * has a grouped problem with a readable message, and the screen offers exactly one action per state.
 */
class BackupUiStateTest {
    private val path = "Загрузки/BeeSearch/Dev/Backup"

    /** A fixed zone keeps the displayed time deterministic, independent of the test machine. */
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    @Test
    fun everyRepositoryFailureHasAReadableMessage() {
        RepositoryError.entries.forEach { error ->
            val message = accessProblemMessage(error.toBackupAccessProblem())
            assertTrue("$error produced no message", message.isNotBlank())
            assertFalse("$error leaked its enum name", message.contains(error.name))
            assertFalse("'$message' looks like a raw identifier", message.contains('_'))
        }
    }

    @Test
    fun failuresAreGroupedAndOnlyAccessProblemsOfferGrantingAgain() {
        assertEquals(BackupAccessProblem.PERMISSION, RepositoryError.UNBOUND.toBackupAccessProblem())
        assertEquals(BackupAccessProblem.PERMISSION, RepositoryError.PERMISSION_LOST.toBackupAccessProblem())
        assertEquals(BackupAccessProblem.PERMISSION, RepositoryError.BOUND_ROOT_UNAVAILABLE.toBackupAccessProblem())
        assertEquals(
            BackupAccessProblem.REPOSITORY_MISSING,
            RepositoryError.BOUND_REPOSITORY_MISSING.toBackupAccessProblem(),
        )
        assertEquals(
            BackupAccessProblem.IDENTITY_MISMATCH,
            RepositoryError.ROOT_IDENTITY_MISMATCH.toBackupAccessProblem(),
        )
        assertEquals(
            BackupAccessProblem.REPOSITORY_NOT_RECOGNIZED,
            RepositoryError.INVALID_HEADER.toBackupAccessProblem(),
        )
        assertEquals(
            BackupAccessProblem.REPOSITORY_NOT_RECOGNIZED,
            RepositoryError.UNSUPPORTED_FORMAT.toBackupAccessProblem(),
        )
        assertEquals(
            BackupAccessProblem.AMBIGUOUS_OR_INVALID,
            RepositoryError.AMBIGUOUS_REPOSITORY.toBackupAccessProblem(),
        )
        assertEquals(BackupAccessProblem.AMBIGUOUS_OR_INVALID, RepositoryError.DIRECTORY_CONFLICT.toBackupAccessProblem())
        assertEquals(
            BackupAccessProblem.UNSUPPORTED_LOCATION,
            RepositoryError.UNSUPPORTED_PUBLICATION_PATH.toBackupAccessProblem(),
        )
        assertEquals(BackupAccessProblem.BINDING, RepositoryError.BINDING_INVALID.toBackupAccessProblem())
        assertEquals(BackupAccessProblem.BINDING, RepositoryError.BINDING_CHANGED.toBackupAccessProblem())
        assertEquals(BackupAccessProblem.BINDING, RepositoryError.BINDING_PERSISTENCE_FAILED.toBackupAccessProblem())
        assertEquals(BackupAccessProblem.PROVIDER, RepositoryError.PROVIDER_FAILURE.toBackupAccessProblem())
        assertEquals(BackupAccessProblem.CAPACITY, RepositoryError.CAPACITY_INSUFFICIENT.toBackupAccessProblem())

        // Snapshot and operation failures keep their own meaning instead of becoming generic
        // storage failures, so the screen can say what actually happened.
        assertEquals(
            BackupAccessProblem.LOGICAL_DATA,
            RepositoryError.LOGICAL_STATE_INCONSISTENT.toBackupAccessProblem(),
        )
        assertEquals(BackupAccessProblem.SNAPSHOT, RepositoryError.INVALID_SNAPSHOT.toBackupAccessProblem())
        assertEquals(BackupAccessProblem.SNAPSHOT, RepositoryError.SNAPSHOT_LIMIT_EXCEEDED.toBackupAccessProblem())
        assertEquals(BackupAccessProblem.SNAPSHOT, RepositoryError.SNAPSHOT_ID_CONFLICT.toBackupAccessProblem())
        assertEquals(BackupAccessProblem.CANCELLED, RepositoryError.CANCELLED.toBackupAccessProblem())

        assertTrue(BackupAccessProblem.PERMISSION.recoverableByGrant)
        assertTrue(BackupAccessProblem.UNSUPPORTED_LOCATION.recoverableByGrant)
        assertTrue(BackupAccessProblem.PROVIDER.recoverableByGrant)
        listOf(
            BackupAccessProblem.IDENTITY_MISMATCH,
            BackupAccessProblem.REPOSITORY_MISSING,
            BackupAccessProblem.REPOSITORY_NOT_RECOGNIZED,
            BackupAccessProblem.AMBIGUOUS_OR_INVALID,
            BackupAccessProblem.BINDING,
            BackupAccessProblem.CAPACITY,
            BackupAccessProblem.LOGICAL_DATA,
            BackupAccessProblem.SNAPSHOT,
            BackupAccessProblem.CANCELLED,
        ).forEach { problem ->
            assertFalse("$problem must not offer granting access again", problem.recoverableByGrant)
        }
    }

    @Test
    fun onlyARealAccessLossSendsTheScreenBackToTheS1States() {
        // The folder is unreachable: S1 owns that and offers access recovery.
        assertTrue(BackupAccessProblem.PERMISSION.belongsToAccessState)
        assertTrue(BackupAccessProblem.UNSUPPORTED_LOCATION.belongsToAccessState)

        // A storage or data failure while access works keeps its own message and a retry here:
        // re-granting access would neither explain nor fix it, and routing it through a second access
        // probe would drop the message and show a healthy-looking screen after a failed backup.
        listOf(
            BackupAccessProblem.PROVIDER,
            BackupAccessProblem.SNAPSHOT,
            BackupAccessProblem.LOGICAL_DATA,
            BackupAccessProblem.CAPACITY,
            BackupAccessProblem.IDENTITY_MISMATCH,
            BackupAccessProblem.BINDING,
        ).forEach { problem ->
            assertFalse("$problem must keep its own message", problem.belongsToAccessState)
        }
    }

    @Test
    fun aStorageFailureKeepsItsMessageAndRetryOnThisScreen() {
        val screen = BackupCreateOutcome.Failed(BackupAccessProblem.PROVIDER)
            .toScreenState(path, zone, BACKUP_DATE_LOCALE, BackupSnapshotsUi.None)

        assertEquals(
            BackupScreenState.Ready(
                path = path,
                snapshots = BackupSnapshotsUi.None,
                operation = BackupOperationUi.Problem(accessProblemMessage(BackupAccessProblem.PROVIDER)),
            ),
            screen,
        )
    }

    /**
     * An access-flavoured failure that a fresh probe cannot confirm must not turn into a silent
     * healthy screen. Publication capability, for example, is only exercised while publishing, so the
     * probe can report ready while every create fails; the user has to see why and be able to retry.
     */
    @Test
    fun anUnconfirmedAccessProblemAfterAFailedCreationKeepsItsMessageAndTheLastCopy() {
        val screen = accessClassFailureScreenState(
            path = path,
            failure = BackupCreateOutcome.Failed(BackupAccessProblem.UNSUPPORTED_LOCATION),
            accessAfterFailure = BackupAccessStatus.Ready(UUID.randomUUID()),
            previousSnapshots = BackupSnapshotsUi.Latest("6 октября 2026, 20:15", warning = null),
            zone = zone,
            locale = BACKUP_DATE_LOCALE,
        )

        assertEquals(
            BackupScreenState.Ready(
                path = path,
                snapshots = BackupSnapshotsUi.Latest("6 октября 2026, 20:15", warning = null),
                operation = BackupOperationUi.Problem(
                    accessProblemMessage(BackupAccessProblem.UNSUPPORTED_LOCATION),
                ),
            ),
            screen,
        )
    }

    @Test
    fun aConfirmedAccessLossAfterAFailedCreationShowsTheS1AccessState() {
        val screen = accessClassFailureScreenState(
            path = path,
            failure = BackupCreateOutcome.Failed(BackupAccessProblem.PERMISSION),
            accessAfterFailure = BackupAccessStatus.AccessLost(BackupAccessProblem.PERMISSION),
            previousSnapshots = BackupSnapshotsUi.None,
            zone = zone,
            locale = BACKUP_DATE_LOCALE,
        )

        assertEquals(
            BackupScreenState.AccessLost(path, accessProblemMessage(BackupAccessProblem.PERMISSION)),
            screen,
        )
    }

    @Test
    fun anAccessProblemFoundWhileCreatingIsShownNextToTheCreatedBackup() {
        val screen = BackupCreateOutcome.Created(
            snapshots = BackupSnapshotStatus.Failed(BackupAccessProblem.PERMISSION),
            committedAtEpochMs = time(2026, 10, 6, 20, 15),
        ).toScreenState(path, zone, BACKUP_DATE_LOCALE, BackupSnapshotsUi.None)

        assertEquals(
            BackupScreenState.Ready(
                path = path,
                snapshots = BackupSnapshotsUi.Problem(accessProblemMessage(BackupAccessProblem.PERMISSION)),
                operation = BackupOperationUi.Created(0),
            ),
            screen,
        )
    }

    @Test
    fun groupedMessagesSayWhatActuallyHappened() {
        // Pinned literally: a grouped text must not claim foreign data for Bee Search's own header,
        // and must not claim that nothing was changed where something was.
        assertEquals(
            "Нет доступа к папке резервных копий.",
            accessProblemMessage(BackupAccessProblem.PERMISSION),
        )
        assertEquals(
            "Файл резервной копии в этой папке повреждён или создан другой версией Bee Search. " +
                "Ничего не изменено.",
            accessProblemMessage(BackupAccessProblem.REPOSITORY_NOT_RECOGNIZED),
        )
        assertEquals(
            "Bee Search не может использовать эту папку: в ней есть файлы, которые он не создавал.",
            accessProblemMessage(BackupAccessProblem.AMBIGUOUS_OR_INVALID),
        )
        assertEquals(
            "В этой папке находится другая резервная копия Bee Search. Подключение не изменено.",
            accessProblemMessage(BackupAccessProblem.IDENTITY_MISMATCH),
        )
    }

    @Test
    fun statusMapsToNeedsGrantReadyAccessLostOrFailed() {
        assertEquals(BackupScreenState.NeedsGrant(path), BackupAccessStatus.GrantRequired.toScreenState(path))
        assertEquals(BackupScreenState.Ready(path), BackupAccessStatus.Ready(UUID.randomUUID()).toScreenState(path))
        assertEquals(
            BackupScreenState.AccessLost(path, accessProblemMessage(BackupAccessProblem.PERMISSION)),
            BackupAccessStatus.AccessLost(BackupAccessProblem.PERMISSION).toScreenState(path),
        )
        assertEquals(
            BackupScreenState.Failed(path, accessProblemMessage(BackupAccessProblem.IDENTITY_MISMATCH)),
            BackupAccessStatus.Failed(BackupAccessProblem.IDENTITY_MISMATCH).toScreenState(path),
        )
    }

    @Test
    fun outcomeMapsToReadyWrongFolderAndRecoverableOrFinalFailure() {
        assertEquals(
            BackupScreenState.Ready(path),
            BackupAccessOutcome.Ready(UUID.randomUUID(), BackupAccessAction.ADOPTED).toScreenState(path),
        )
        assertEquals(
            BackupScreenState.Failed(path, WRONG_FOLDER_MESSAGE, offerAccess = true),
            BackupAccessOutcome.WrongFolder.toScreenState(path),
        )
        assertEquals(
            BackupScreenState.AccessLost(path, accessProblemMessage(BackupAccessProblem.PERMISSION)),
            BackupAccessOutcome.Failed(BackupAccessProblem.PERMISSION).toScreenState(path),
        )
        assertEquals(
            BackupScreenState.Failed(path, accessProblemMessage(BackupAccessProblem.BINDING)),
            BackupAccessOutcome.Failed(BackupAccessProblem.BINDING).toScreenState(path),
        )
    }

    @Test
    fun pathIsShownInEveryResolvedStateAndNotWhileLoading() {
        assertEquals(path, BackupScreenState.Ready(path).pathOrEmpty)
        assertEquals(path, BackupScreenState.NeedsGrant(path).pathOrEmpty)
        assertEquals(path, BackupScreenState.AccessLost(path, "x").pathOrEmpty)
        assertEquals(path, BackupScreenState.Failed(path, "x").pathOrEmpty)
        assertEquals("", BackupScreenState.Loading.pathOrEmpty)
        assertEquals("", BackupScreenState.Working.pathOrEmpty)
    }

    @Test
    fun wrongFolderMessageNamesTheRequiredFolder() {
        assertTrue(WRONG_FOLDER_MESSAGE.contains("папке резервных копий Bee Search"))
    }

    private fun time(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    // --- S2: what the repository holds, what one attempt produced, and the exact wording ---

    @Test
    fun repositoryStateBecomesUserTerms() {
        assertEquals(BackupSnapshotsUi.Reading, BackupSnapshotStatus.Unknown.toSnapshotsUi(zone, BACKUP_DATE_LOCALE))
        assertEquals(BackupSnapshotsUi.None, BackupSnapshotStatus.None.toSnapshotsUi(zone, BACKUP_DATE_LOCALE))
        assertEquals(
            BackupSnapshotsUi.Latest("6 октября 2026, 20:15", warning = null),
            BackupSnapshotStatus.Latest(time(2026, 10, 6, 20, 15), hasUnusable = false)
                .toSnapshotsUi(zone, BACKUP_DATE_LOCALE),
        )
        assertEquals(
            BackupSnapshotsUi.Latest("6 октября 2026, 20:15", warning = BACKUP_SNAPSHOT_WARNING),
            BackupSnapshotStatus.Latest(time(2026, 10, 6, 20, 15), hasUnusable = true)
                .toSnapshotsUi(zone, BACKUP_DATE_LOCALE),
        )
        assertEquals(
            BackupSnapshotsUi.Problem(BACKUP_UNUSABLE_MESSAGE),
            BackupSnapshotStatus.Unusable.toSnapshotsUi(zone, BACKUP_DATE_LOCALE),
        )
        assertEquals(
            BackupSnapshotsUi.Problem(accessProblemMessage(BackupAccessProblem.PROVIDER)),
            BackupSnapshotStatus.Failed(BackupAccessProblem.PROVIDER).toSnapshotsUi(zone, BACKUP_DATE_LOCALE),
        )
    }

    @Test
    fun backupTimeIsWrittenTheWayARussianUserReadsIt() {
        assertEquals(
            "6 октября 2026, 20:15",
            formatBackupTime(time(2026, 10, 6, 20, 15), zone, BACKUP_DATE_LOCALE),
        )
        assertEquals(
            "1 января 2026, 09:05",
            formatBackupTime(time(2026, 1, 1, 9, 5), zone, BACKUP_DATE_LOCALE),
        )
    }

    @Test
    fun oneCreateAttemptBecomesOneScreenState() {
        val existing = BackupSnapshotsUi.None
        val created = BackupCreateOutcome.Created(
            snapshots = BackupSnapshotStatus.Latest(time(2026, 10, 6, 20, 15), hasUnusable = false),
            committedAtEpochMs = time(2026, 10, 6, 20, 15),
        ).toScreenState(path, zone, BACKUP_DATE_LOCALE, existing)

        assertEquals(
            BackupScreenState.Ready(
                path = path,
                snapshots = BackupSnapshotsUi.Latest("6 октября 2026, 20:15", warning = null),
                operation = BackupOperationUi.Created(0),
            ),
            created,
        )
        assertEquals(
            BackupScreenState.Ready(path, existing, BackupOperationUi.Cancelled),
            BackupCreateOutcome.Cancelled.toScreenState(path, zone, BACKUP_DATE_LOCALE, existing),
        )
        assertEquals(
            BackupScreenState.Ready(
                path,
                existing,
                BackupOperationUi.Problem(accessProblemMessage(BackupAccessProblem.SNAPSHOT)),
            ),
            BackupCreateOutcome.Failed(BackupAccessProblem.SNAPSHOT)
                .toScreenState(path, zone, BACKUP_DATE_LOCALE, existing),
        )
        assertEquals(
            BackupScreenState.Ready(path, existing, BackupOperationUi.Creating),
            BackupCreateOutcome.AlreadyRunning.toScreenState(path, zone, BACKUP_DATE_LOCALE, existing),
        )
    }

    @Test
    fun mediaFailureIsVisibleAsRetryableErrorWithActualCounts() {
        val screen = BackupCreateOutcome.MediaFailed(failedCount = 1, totalCount = 4)
            .toScreenState(path, zone, BACKUP_DATE_LOCALE, BackupSnapshotsUi.None)

        assertEquals(
            BackupScreenState.Ready(
                path,
                BackupSnapshotsUi.None,
                BackupOperationUi.MediaFailed(failedCount = 1, totalCount = 4),
            ),
            screen,
        )
    }

    @Test
    fun successWordingDescribesTheCapturedMediaResult() {
        assertEquals("Резервная копия создана", BACKUP_CREATED_TITLE)
        assertTrue(BACKUP_CREATED_BODY.contains("фото и видео"))
        assertTrue(BACKUP_CREATED_NO_MEDIA_BODY.contains("Данные исследований и настройки"))
        assertFalse(BACKUP_CREATED_NO_MEDIA_BODY.contains("0 из 0"))
    }

    @Test
    fun userFacingBackupTextsNeverExposeTechnicalTokens() {
        val texts = listOf(
            BACKUP_SECTION_TITLE, BACKUP_EXPLANATION, BACKUP_PROMISE, BACKUP_LAST_PREFIX,
            BACKUP_NO_SNAPSHOTS, BACKUP_SNAPSHOT_WARNING, BACKUP_UNUSABLE_MESSAGE,
            BACKUP_CREATE_ACTION, BACKUP_CREATE_RETRY, BACKUP_CREATING, BACKUP_CREATED_TITLE,
            BACKUP_CREATED_BODY, BACKUP_CREATED_NO_MEDIA_BODY, BACKUP_MEDIA_ERROR_RETRY,
            BACKUP_CANCELLED_MESSAGE,
        )
        val tokens = listOf("METADATA_ONLY", "NO_MEDIA_EVIDENCE", "SHA", "UUID", "SAF", "Snapshot", "snapshot", ".zip")
        texts.forEach { text ->
            tokens.forEach { token ->
                assertFalse("'$text' exposes the implementation term $token", text.contains(token))
            }
        }
    }

    @Test
    fun s1AccessStatesAreUnchangedByS2() {
        assertEquals(BackupScreenState.NeedsGrant(path), BackupAccessStatus.GrantRequired.toScreenState(path))
        assertEquals(
            BackupScreenState.Ready(path, BackupSnapshotsUi.None, BackupOperationUi.Idle),
            BackupAccessStatus.Ready(UUID.randomUUID()).toScreenState(path, BackupSnapshotsUi.None),
        )
        assertEquals(
            BackupScreenState.AccessLost(path, accessProblemMessage(BackupAccessProblem.PERMISSION)),
            BackupAccessStatus.AccessLost(BackupAccessProblem.PERMISSION).toScreenState(path),
        )
        assertEquals(
            BackupScreenState.Failed(path, accessProblemMessage(BackupAccessProblem.IDENTITY_MISMATCH)),
            BackupAccessStatus.Failed(BackupAccessProblem.IDENTITY_MISMATCH).toScreenState(path),
        )
    }
}
