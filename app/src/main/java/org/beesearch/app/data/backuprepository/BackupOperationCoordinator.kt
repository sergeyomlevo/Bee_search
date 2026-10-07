package org.beesearch.app.data.backuprepository

import kotlinx.coroutines.sync.Mutex
import org.beesearch.app.data.backupsnapshot.BackupSnapshotOperations
import org.beesearch.app.data.backupoperation.CreateBackupResult

/**
 * What the repository currently holds, as far as the backup screen needs to know.
 *
 * [Latest] follows published summary semantics: the newest recognized artifact by
 * creation time and then by snapshot id, never by file name or listing order.
 */
internal sealed interface BackupSnapshotStatus {
    /** Discovery has not run yet. */
    data object Unknown : BackupSnapshotStatus

    /** No snapshot exists at all. */
    data object None : BackupSnapshotStatus

    /** The latest valid snapshot, plus whether unusable candidates were visible next to it. */
    data class Latest(val createdAtEpochMs: Long, val hasUnusable: Boolean) : BackupSnapshotStatus

    /** Snapshots are present but none of them could be validated: no last-backup claim is made. */
    data object Unusable : BackupSnapshotStatus

    /** Snapshots could not be read at all. */
    data class Failed(val problem: BackupAccessProblem) : BackupSnapshotStatus
}

/** Outcome of one manual snapshot creation attempt. */
internal sealed interface BackupCreateOutcome {
    /** A committed and verified snapshot exists; [snapshots] is the re-read repository state. */
    data class Created(val snapshots: BackupSnapshotStatus, val committedAtEpochMs: Long,
        val verifiedMediaCount: Int = 0) : BackupCreateOutcome

    data class MediaFailed(val failedCount: Int, val totalCount: Int) : BackupCreateOutcome

    /** Another creation is already running, so this request did not start a second one. */
    data object AlreadyRunning : BackupCreateOutcome

    data class Failed(val problem: BackupAccessProblem) : BackupCreateOutcome

    /** The operation stopped before finishing. Never reported as success. */
    data object Cancelled : BackupCreateOutcome
}

/**
 * Snapshot operations behind the manual backup button.
 *
 * Two rules live here and are deliberately testable without Android:
 *
 * - **one creation at a time.** A second request while one runs is refused instead of publishing a
 *   second snapshot, so a double tap cannot create duplicates;
 * - **the repository is the source of truth.** Nothing is remembered between calls: every
 *   [inspect] re-reads the repository, and a successful creation immediately re-reads it again, so
 *   no second timestamp store exists anywhere.
 */
internal class BackupOperationCoordinator(
    private val operations: BackupSnapshotOperations,
    private val createBackup: suspend () -> CreateBackupResult,
) {
    private val gate = Mutex()

    /** Reads the current snapshot state. Never modifies the repository. */
    suspend fun inspect(): BackupSnapshotStatus = when (val result = operations.readPublishedSummary()) {
        is RepositoryResult.Success -> result.value.toSnapshotStatus()
        is RepositoryResult.Failure -> BackupSnapshotStatus.Failed(result.error.toBackupAccessProblem())
    }

    /**
     * Creates one snapshot and re-reads the repository afterwards.
     *
     * The gate is released in every outcome except [AlreadyRunning], where the running operation
     * still owns it, so a failed or cancelled attempt leaves the button usable again.
     */
    suspend fun create(): BackupCreateOutcome {
        if (!gate.tryLock()) return BackupCreateOutcome.AlreadyRunning
        return try {
            when (val result = createBackup()) {
                is CreateBackupResult.Created -> {
                    val committedAt = result.committed.snapshot.identity.createdAtEpochMs
                    BackupCreateOutcome.Created(
                        snapshots = inspect(),
                        committedAtEpochMs = committedAt,
                        verifiedMediaCount = result.verifiedMediaCount,
                    )
                }

                is CreateBackupResult.Failed -> if (result.error == RepositoryError.CANCELLED) {
                    BackupCreateOutcome.Cancelled
                } else {
                    BackupCreateOutcome.Failed(result.error.toBackupAccessProblem())
                }
                is CreateBackupResult.MediaFailed -> BackupCreateOutcome.MediaFailed(result.failedCount, result.totalCount)
                CreateBackupResult.AlreadyRunning -> BackupCreateOutcome.AlreadyRunning
            }
        } finally {
            gate.unlock()
        }
    }

}

private fun PublishedBackupSummary.toSnapshotStatus(): BackupSnapshotStatus {
    val unusable = hasUnrecognizedCandidates
    val valid = latest
    return when {
        valid != null -> BackupSnapshotStatus.Latest(valid.createdAtEpochMs, unusable)
        unusable -> BackupSnapshotStatus.Unusable
        else -> BackupSnapshotStatus.None
    }
}
