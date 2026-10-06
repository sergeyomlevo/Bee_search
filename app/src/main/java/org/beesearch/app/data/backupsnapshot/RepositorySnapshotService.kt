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
    suspend fun create(cancelled: () -> Boolean = { false }): RepositoryResult<CommittedSnapshot> =
        repository.createMetadataSnapshot(workspace, capture::capture, cancelled)

    suspend fun discover(): RepositoryResult<SnapshotDiscovery> = repository.discoverSnapshots(workspace)
}
