package org.beesearch.app.data.media

import org.beesearch.app.domain.repository.PhysicalObjectDeletion
import org.beesearch.app.domain.repository.PhysicalObjectDeletionOutcome
import org.beesearch.app.domain.repository.PhysicalObjectRepository
import java.util.UUID

/**
 * Deletes a Physical Object together with the app-owned bytes it owns.
 *
 * The database is the authoritative state. The DB-only deletion commits first and only then are the
 * media files of that object removed, so the two possible failures are not equivalent: a failed
 * filesystem cleanup leaves orphan bytes (invisible, unexported, harmless), while the reverse order
 * could leave a media row pointing at a missing file. For the same reason nothing here can resurrect
 * a deleted object.
 *
 * Only files inside the managed root of the deleted object are touched. Bytes that the user picked
 * or captured live outside app storage and are never referenced by that path, so they are never
 * deleted. A leftover file is reported through [PhysicalObjectDeletionOutcome.fileCleanupComplete]
 * instead of failing the deletion, and no retry, journal or sweeper is introduced for it.
 */
internal class FileAwarePhysicalObjectDeletion(
    private val repository: PhysicalObjectRepository,
    private val fileStore: PhysicalObjectMediaFileStore,
) {
    suspend fun deleteHollow(id: UUID): PhysicalObjectDeletionOutcome = cleanup { repository.deleteHollow(id) }

    suspend fun deleteLogHive(id: UUID): PhysicalObjectDeletionOutcome = cleanup { repository.deleteLogHive(id) }

    private suspend fun cleanup(remove: suspend () -> PhysicalObjectDeletion): PhysicalObjectDeletionOutcome {
        val deleted = remove()
        val filesRemoved = deleted.mediaRelativePaths
            .fold(true) { complete, path -> fileStore.delete(path) && complete }
        val directoryRemoved = fileStore.deleteObjectDirectory(deleted.id)
        return PhysicalObjectDeletionOutcome(fileCleanupComplete = filesRemoved && directoryRemoved)
    }
}
