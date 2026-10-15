package org.beesearch.app.data.local.room

import androidx.room.withTransaction
import java.util.UUID

/** Foundation only. No READY writer, UUID allocation, URI/file access or runtime wiring. */
internal class GpxPersistenceStore(private val database: BeeSearchDatabase) {
    private val dao get() = database.gpxDao()
    private suspend fun <T> checked(block: suspend () -> T): T = database.withTransaction {
        GpxPersistenceSchema.verify(database.openHelper.writableDatabase)
        block()
    }
    suspend fun savePending(graph: GpxStoredGraph, job: GpxImportJobEntity) {
        val owned = graph.snapshot()
        owned.validate()
        require(owned.importRow.publicationState == "PENDING" && owned.importRow.requestUuid == null)
        require(job.phase == "PARSED" && job.intent == "CONTINUE" && job.importId == owned.importRow.id)
        validateLink(job, owned.importRow)
        checked {
            val previous = dao.jobByRequest(job.requestUuid)
            if (previous != null) {
                require(previous.phase in setOf("COPIED", "PARSING") && previous.intent == "CONTINUE" && previous.importId == null)
                require(previous.copy(phase = job.phase, importId = job.importId) == job)
            }
            dao.insertImport(owned.importRow)
            dao.insertTracks(owned.tracks)
            dao.insertSegments(owned.segments)
            dao.insertSamples(owned.samples)
            if (previous == null) dao.insertJob(job) else check(dao.updateJob(job) == 1)
        }
    }
    suspend fun readReady(id: UUID): GpxStoredGraph? = checked { dao.readyImportById(id)?.let { read(it) } }
    suspend fun readPending(id: UUID): GpxStoredGraph? = checked {
        dao.importById(id)?.takeIf { it.publicationState == "PENDING" }?.let { read(it) }
    }
    private suspend fun read(row: GpxImportEntity): GpxStoredGraph {
        val tracks = dao.tracks(row.id)
        val segments = tracks.flatMap { dao.segments(it.id) }
        val samples = segments.flatMap { dao.samples(it.id) }
        return GpxStoredGraph(row, tracks, segments, samples).also { it.validate() }
    }
    suspend fun cleanupPending(requestUuid: UUID) = checked {
        val job = dao.jobByRequest(requestUuid) ?: return@checked
        require(job.intent == "ABORT" && job.phase != "READY")
        val id = job.importId ?: return@checked
        val row = dao.importById(id) ?: throw GpxPersistenceIntegrityException("MISSING_PENDING")
        require(row.publicationState == "PENDING")
        validateLink(job, row)
        check(dao.updateJob(job.copy(importId = null)) == 1)
        dao.deleteSamples(id); dao.deleteSegments(id); dao.deleteTracks(id)
        check(dao.deletePendingImport(id) == 1)
    }
    suspend fun counts(territoryId: UUID, state: String): GpxPersistenceCounts = checked {
        require(state in setOf("PENDING", "READY"))
        GpxPersistenceCounts(dao.countImports(territoryId, state), dao.countSamples(territoryId, state))
    }
    suspend fun sampleCount(importId: UUID): Long = checked { dao.countImportSamples(importId) }

    private fun validateLink(job: GpxImportJobEntity, row: GpxImportEntity) {
        require(job.territoryId == row.territoryId && job.importingObserverId == row.importingObserverId)
        require(job.originalObjectId == row.originalObjectId && job.originalSha256 == row.originalSha256 && job.originalByteLength == row.originalByteLength)
    }
}

/** Facts, not reservations or concurrency-safe admission. */
internal data class GpxPersistenceCounts(val imports: Long, val samples: Long)
