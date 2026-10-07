package org.beesearch.app.data.backupsnapshot

import org.beesearch.app.data.backuprepository.CommittedSnapshot
import org.beesearch.app.data.backuprepository.RepositoryResult
import org.beesearch.app.data.backuprepository.SnapshotDiscovery
import org.beesearch.app.data.backuprepository.PublishedBackupSummary

/**
 * The narrow seam the backup screen uses for snapshots.
 *
 * Keeps standalone metadata creation and strong discovery separate from the ordinary screen's
 * published summary. The single-button operation is supplied by CreateBackupOperation.
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

    /** Published container/metadata only; never current media evidence. */
    suspend fun readPublishedSummary(): RepositoryResult<PublishedBackupSummary>
}

/** Production implementation: a pass-through to the accepted [RepositorySnapshotService]. */
internal class RepositoryBackupSnapshotOperations(
    private val service: RepositorySnapshotService,
) : BackupSnapshotOperations {
    override suspend fun create(): RepositoryResult<CommittedSnapshot> = service.create()

    override suspend fun discover(): RepositoryResult<SnapshotDiscovery> = service.discover()

    override suspend fun readPublishedSummary(): RepositoryResult<PublishedBackupSummary> =
        service.readPublishedSummary()
}
