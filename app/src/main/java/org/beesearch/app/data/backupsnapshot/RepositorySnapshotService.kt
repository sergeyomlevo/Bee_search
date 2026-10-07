package org.beesearch.app.data.backupsnapshot

import java.io.File
import org.beesearch.app.data.backuprepository.BoundRepository
import org.beesearch.app.data.backuprepository.CommittedSnapshot
import org.beesearch.app.data.backuprepository.RepositoryResult
import org.beesearch.app.data.backuprepository.SnapshotDiscovery

/** Explicit metadata export API only: no scheduler/UI, restore, or media protection promise. */
internal class RepositorySnapshotService(
    private val repository: BoundRepository,
    private val capture: SnapshotCapture,
    private val workspace: File,
) {
    /** Tuple A: research metadata only. */
    suspend fun create(cancelled: () -> Boolean = { false }): RepositoryResult<CommittedSnapshot> =
        repository.createMetadataSnapshot(workspace, capture::capture, cancelled = cancelled)

    /**
     * Tuple B: the same single capture, with its required media set strongly verified in this same
     * bound local repository before publication.
     *
     * It never ingests media and never falls back to tuple A: a required blob that is not already
     * present and verified fails the creation. No UI calls this yet.
     */
    suspend fun createFull(cancelled: () -> Boolean = { false }): RepositoryResult<CommittedSnapshot> =
        repository.createFullSnapshot(workspace, capture::capture, cancelled = cancelled)

    suspend fun discover(): RepositoryResult<SnapshotDiscovery> = repository.discoverSnapshots(workspace)
}
