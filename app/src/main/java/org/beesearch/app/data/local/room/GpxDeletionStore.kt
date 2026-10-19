package org.beesearch.app.data.local.room

import androidx.room.withTransaction
import java.util.UUID

/** Exact-ID Room boundary. The permanent tombstone outlives its removed parent. */
internal class GpxDeletionStore(
    private val database: BeeSearchDatabase,
    private val checkpoint: (String) -> Unit = {},
) {
    private val dao get() = database.gpxDao()

    private suspend fun <T> checked(block: suspend () -> T): T = database.withTransaction {
        GpxPersistenceSchema.verify(database.openHelper.writableDatabase)
        block()
    }

    suspend fun existing(id: UUID) = checked { dao.deletion(id) }
    suspend fun envelope(territory: UUID, id: UUID) = checked {
        dao.readyImportById(id)?.also { require(it.territoryId == territory) }
    }
    suspend fun pending() = checked { dao.pendingDeletions() }
    suspend fun unreferenced(original: UUID) = checked {
        dao.originalReferences(original) == 0 && dao.originalJobReferences(original) == 0
    }

    suspend fun commit(row: GpxImportEntity, proof: String?): GpxDeletionEntity = checked {
        require(dao.readyImportById(row.id) == row)
        val request = requireNotNull(row.requestUuid)
        // Existing import reconciliation must first retire staging and its READY job.
        require(dao.jobByRequest(request) == null)
        val tombstone = GpxDeletionEntity(
            row.id, row.territoryId, request, row.originalObjectId,
            row.originalSha256, row.originalByteLength, proof,
        )
        dao.insertDeletion(tombstone)
        dao.deleteSamples(row.id)
        dao.deleteSegments(row.id)
        dao.deleteTracks(row.id)
        check(dao.deleteReadyImport(row.id, row.territoryId) == 1)
        checkpoint("BEFORE_COMMIT")
        tombstone
    }

    suspend fun filesDone(row: GpxDeletionEntity) = checked {
        check(dao.markDeletionFiles(row.importId) == 1)
    }
    suspend fun settingsDone(row: GpxDeletionEntity) = checked {
        check(dao.markDeletionSettings(row.importId) == 1)
    }
}
