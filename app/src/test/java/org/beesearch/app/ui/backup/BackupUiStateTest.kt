package org.beesearch.app.ui.backup

import java.util.UUID
import org.beesearch.app.data.backuprepository.BackupAccessAction
import org.beesearch.app.data.backuprepository.BackupAccessOutcome
import org.beesearch.app.data.backuprepository.BackupAccessProblem
import org.beesearch.app.data.backuprepository.BackupAccessStatus
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
        ).forEach { problem ->
            assertFalse("$problem must not offer granting access again", problem.recoverableByGrant)
        }
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
}
