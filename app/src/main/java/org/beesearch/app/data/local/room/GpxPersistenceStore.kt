package org.beesearch.app.data.local.room

import androidx.room.withTransaction
import java.util.UUID
import org.beesearch.app.data.gpx.GpxAdmissionException
import org.beesearch.app.data.gpx.gpxAdmissionRejection

/** Persistence boundary. No UUID allocation, URI/file access or runtime wiring. */
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
            requireContext(job)
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
    suspend fun listReady(territoryId: UUID): List<GpxReadyMetadata> = checked {
        dao.readyMetadataByTerritory(territoryId)
    }
    /** Durable result lookup is separate from accepting/consuming a new request. */
    suspend fun completed(request: UUID): GpxImportEntity? = checked {
        dao.completedByRequest(request)?.also { row ->
            read(row)
            dao.jobByRequest(request)?.let { job -> validateReadyLink(job, row) }
        }
    }

    /** Recovery has no durable user confirmation: an observed repeat MUST be rejected. */
    suspend fun rejectUnconfirmedRecoveryRepeat(job: GpxImportJobEntity) = checked {
        require(dao.jobByRequest(job.requestUuid) == job && job.intent == "CONTINUE")
        val row = requirePendingLink(job)
        if (dao.countRepeatedOriginal(job.territoryId, row.originalSha256, row.originalByteLength) > 0) {
            throw GpxAdmissionException(org.beesearch.app.data.gpx.GpxAdmissionRejection.DUPLICATE_CONFIRMATION_REQUIRED)
        }
    }

    /** Called only by the serialized Coordinator after current file ownership/integrity proof. */
    suspend fun publishReady(job: GpxImportJobEntity, confirmRepeatedOriginal: Boolean,
                             checkAdmissibility: () -> Unit,
                             checkpoint: (String) -> Unit = {}): GpxImportEntity = checked {
        require(job.phase == "FILE_PUBLISHED" && job.intent == "CONTINUE")
        require(dao.jobByRequest(job.requestUuid) == job)
        requireContext(job)
        val graph = read(requirePendingLink(job))
        checkAdmissibility()
        gpxAdmissionRejection(dao.countImports(job.territoryId, "READY"),
            dao.countSamples(job.territoryId, "READY"), dao.countImportSamples(graph.importRow.id),
            dao.countRepeatedOriginal(job.territoryId, graph.importRow.originalSha256,
                graph.importRow.originalByteLength) > 0, confirmRepeatedOriginal)?.let {
            throw GpxAdmissionException(it)
        }
        check(dao.publishReady(graph.importRow.id, job.requestUuid) == 1)
        checkpoint("READY_IMPORT_UPDATED")
        check(dao.updateJob(job.copy(phase = "READY")) == 1)
        checkpoint("READY_JOB_UPDATED")
        checkAdmissibility()
        graph.importRow.copy(publicationState = "READY", requestUuid = job.requestUuid)
    }

    suspend fun forgetReady(job: GpxImportJobEntity) = checked {
        require(dao.jobByRequest(job.requestUuid) == job)
        val row = requireNotNull(dao.completedByRequest(job.requestUuid))
        validateReadyLink(job, row)
        check(dao.deleteReadyJob(job.requestUuid, row.id) == 1)
    }

    private fun validateReadyLink(job: GpxImportJobEntity, row: GpxImportEntity) {
        require(job.phase == "READY" && job.intent == "CONTINUE" && job.importId == row.id)
        require(row.publicationState == "READY" && row.requestUuid == job.requestUuid)
        validateLink(job, row)
    }

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

    suspend fun journal(request: UUID): GpxImportJobEntity? = checked { dao.jobByRequest(request) }
    suspend fun journals(): List<GpxImportJobEntity> = checked { dao.jobs() }

    suspend fun begin(job: GpxImportJobEntity) = checked {
        require(job.phase == "SELECTED" && job.intent == "CONTINUE" && job.originalObjectId == null &&
            job.originalSha256 == null && job.originalByteLength == null && job.importId == null)
        requireContext(job)
        dao.insertJob(job)
    }

    /** Compare the complete durable snapshot, not just a phase that may have gone stale. */
    suspend fun advance(previous: GpxImportJobEntity, next: GpxImportJobEntity) = checked {
        require(dao.jobByRequest(previous.requestUuid) == previous && previous.intent == "CONTINUE")
        val transition = previous.phase to next.phase
        require(transition in setOf("SELECTED" to "COPYING", "COPYING" to "COPIED",
            "COPIED" to "PARSING", "PARSED" to "FILE_PUBLISHED"))
        val allowed = when (previous.phase) {
            "SELECTED" -> previous.copy(phase = next.phase, originalObjectId = next.originalObjectId)
            "COPYING" -> previous.copy(phase = next.phase, originalSha256 = next.originalSha256,
                originalByteLength = next.originalByteLength)
            else -> previous.copy(phase = next.phase)
        }
        require(next == allowed && next.intent == "CONTINUE")
        requireContext(next)
        if (next.phase == "FILE_PUBLISHED") requirePendingLink(next)
        check(dao.updateJob(next) == 1)
    }

    suspend fun abort(request: UUID): GpxImportJobEntity? = checked {
        val job = dao.jobByRequest(request) ?: return@checked null
        require(job.phase != "READY")
        job.importId?.let { id ->
            val row = dao.importById(id) ?: throw GpxPersistenceIntegrityException("MISSING_PENDING")
            require(row.publicationState == "PENDING") // ABORT must never acquire authority over READY.
            validateLink(job, row)
        }
        job.copy(intent = "ABORT").also { check(dao.updateJob(it) == 1) }
    }

    suspend fun requirePending(job: GpxImportJobEntity): GpxStoredGraph = checked {
        require(dao.jobByRequest(job.requestUuid) == job && job.intent == "CONTINUE")
        requireContext(job)
        read(requirePendingLink(job))
    }

    suspend fun forgetAborted(job: GpxImportJobEntity) = checked {
        require(dao.jobByRequest(job.requestUuid) == job && job.intent == "ABORT" && job.importId == null)
        check(dao.deleteAbortedJob(job.requestUuid) == 1)
    }

    private suspend fun requirePendingLink(job: GpxImportJobEntity): GpxImportEntity {
        val row = job.importId?.let { dao.importById(it) }
            ?: throw GpxPersistenceIntegrityException("MISSING_PENDING")
        require(row.publicationState == "PENDING" && row.requestUuid == null)
        validateLink(job, row)
        return row
    }
    private suspend fun requireContext(job: GpxImportJobEntity) {
        require(database.territoryDao().getById(job.territoryId) != null)
        job.importingObserverId?.let { require(database.observerDao().getById(it) != null) }
    }

    private fun validateLink(job: GpxImportJobEntity, row: GpxImportEntity) {
        require(job.territoryId == row.territoryId && job.importingObserverId == row.importingObserverId)
        require(job.originalObjectId == row.originalObjectId && job.originalSha256 == row.originalSha256 && job.originalByteLength == row.originalByteLength)
    }
}

/** Facts, not reservations or concurrency-safe admission. */
internal data class GpxPersistenceCounts(val imports: Long, val samples: Long)
