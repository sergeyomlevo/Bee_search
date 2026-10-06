package org.beesearch.app.data.backupsnapshot

import org.beesearch.app.data.backuprepository.CommittedSnapshot
import org.beesearch.app.data.backuprepository.RepositoryResult
import org.beesearch.app.data.backuprepository.SnapshotDiscovery

/**
 * The narrow seam the backup screen uses for snapshots.
 *
 * It exposes exactly the two accepted service operations and nothing else, so screen logic can be
 * driven and tested on the host without a repository, and no caller can reach Repository V1 or
 * write a snapshot archive itself.
 */
internal interface BackupSnapshotOperations {
    /**
     * Creates, publishes and verifies one new snapshot.
     *
     * A successful result means the snapshot was committed and then read back and validated by the
     * accepted service; nothing weaker may be reported as a created backup.
     */
    suspend fun create(): RepositoryResult<CommittedSnapshot>

    /** Reads the current snapshot state from the repository itself. Never cached by this seam. */
    suspend fun discover(): RepositoryResult<SnapshotDiscovery>
}

/** Production implementation: a pass-through to the accepted [RepositorySnapshotService]. */
internal class RepositoryBackupSnapshotOperations(
    private val service: RepositorySnapshotService,
) : BackupSnapshotOperations {
    override suspend fun create(): RepositoryResult<CommittedSnapshot> = service.create()

    override suspend fun discover(): RepositoryResult<SnapshotDiscovery> = service.discover()
}
