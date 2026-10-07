package org.beesearch.app.data.backupsnapshot

import java.io.File
import org.beesearch.app.data.backuprepository.BoundRepository
import org.beesearch.app.data.backuprepository.CommittedSnapshot
import org.beesearch.app.data.backuprepository.RepositoryResult
import org.beesearch.app.data.backuprepository.SnapshotDiscovery
import org.beesearch.app.data.backuprepository.PublishedBackupSummary

/** Snapshot publication/read APIs; protection and user-operation orchestration live separately. */
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
     * present and verified fails the creation. The user operation supplies captured entries instead.
     */
    suspend fun createFull(cancelled: () -> Boolean = { false }): RepositoryResult<CommittedSnapshot> =
        repository.createFullSnapshot(workspace, capture::capture, cancelled = cancelled)

    suspend fun discover(): RepositoryResult<SnapshotDiscovery> = repository.discoverSnapshots(workspace)

    suspend fun readPublishedSummary(): RepositoryResult<PublishedBackupSummary> =
        repository.readPublishedSummary(workspace)

    suspend fun createFullFromCaptured(entries: SnapshotDomainEntries,
        cancelled: () -> Boolean = { false }): RepositoryResult<CommittedSnapshot> =
        repository.createFullSnapshotFromCaptured(workspace, entries, cancelled)
}
