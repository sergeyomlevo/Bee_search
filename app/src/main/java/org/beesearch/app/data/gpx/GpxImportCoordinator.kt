package org.beesearch.app.data.gpx

import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.beesearch.app.data.local.room.*

/** Captured before source selection. The caller owns request consumption and current-context checks. */
internal data class GpxImportRequest(
    val requestUuid: UUID, val territoryId: UUID, val observerId: UUID?,
    val displayName: String?, val importedAtEpochMs: Long,
)

internal sealed interface GpxPreparationResult {
    /** Prepared only: PENDING, invisible, no admission or successful import correlation. */
    data class Prepared(val requestUuid: UUID, val importId: UUID, val originalId: UUID) : GpxPreparationResult
    /** Committed result. Cleanup debt cannot undo this outcome. */
    data class Ready(val requestUuid: UUID, val importId: UUID, val originalId: UUID,
                     val cleanupPending: Boolean = false, val hasWarnings: Boolean = false) : GpxPreparationResult
    data class Aborted(val requestUuid: UUID) : GpxPreparationResult
    data class Failed(val requestUuid: UUID, val kind: Kind,
        val parse: GpxParseResult.Rejected? = null, val original: GpxOriginalStoreError? = null,
        val cleanupPending: Boolean = false, val admission: GpxAdmissionRejection? = null) : GpxPreparationResult
    enum class Kind { REQUEST, PARSE, ORIGINAL, PERSISTENCE, ADMISSION }
}

/**
 * Sole supported orchestration writer in this Slice; all instances and recovery share a mutex.
 * READY/admission/ABORT and recovery share this serialization boundary. No runtime
 * entry point is wired. Blocking file calls run on IO; no coroutine suspends under the Store lock.
 * Preparation retains staging/witness; completion cleans them without deleting final.
 */
internal class GpxImportCoordinator(
    private val persistence: GpxPersistenceStore,
    private val originals: GpxOriginalStore,
    private val newId: () -> UUID = UUID::randomUUID,
    private val processor: GpxProcessor = GpxProcessor(),
    private val checkpoint: (String) -> Unit = {},
) {
    suspend fun prepare(request: GpxImportRequest, open: () -> InputStream,
                        authorizeNewRequest: () -> Boolean,
                        isCapturedRequestCurrent: () -> Boolean): GpxPreparationResult {
        var owned = false
        return guarded(request.requestUuid, { owned }) {
            val context = currentCoroutineContext()
            val check = { context.ensureActive(); if (!isCapturedRequestCurrent()) throw RequestUnavailable() }
            persistence.completed(request.requestUuid)?.let { row ->
                require(row.territoryId == request.territoryId && row.importingObserverId == request.observerId)
                return@guarded completedResult(row)
            }
            val existing = persistence.journal(request.requestUuid)
            if (existing != null) {
                require(existing.territoryId == request.territoryId && existing.importingObserverId == request.observerId)
                owned = true
                check()
                return@guarded recoverJob(existing, check)
            }
            if (!authorizeNewRequest() || !isCapturedRequestCurrent()) throw RequestUnavailable()
            var job = GpxImportJobEntity(request.requestUuid, request.territoryId, request.observerId,
                "SELECTED", "CONTINUE")
            persistence.begin(job)
            owned = true
            checkpoint("SELECTED")
            check()
            job = advance(job, job.copy(phase = "COPYING", originalObjectId = newId()))
            val identity = originals.copyToStaging(job.requestUuid, job.originalObjectId!!, open,
                checkCancellation = check)
            job = advance(job, job.copy(phase = "COPIED", originalSha256 = identity.sha256,
                originalByteLength = identity.length))
            check()
            job = advance(job, job.copy(phase = "PARSING"))
            val result = processor.process({ originals.openStaged(job.requestUuid, job.originalObjectId!!, identity, check) },
                identity, check)
            if (result is GpxParseResult.Rejected) throw ParseRejected(result)
            check()
            val graph = pendingGpxGraph(result as GpxParseResult.Accepted, job,
                request.displayName, request.importedAtEpochMs, newId)
            val parsed = job.copy(phase = "PARSED", importId = graph.importRow.id)
            persistence.savePending(graph, parsed)
            job = parsed
            checkpoint("PARSED")
            check()
            publish(job, check)
        }
    }

    /** Finish a captured operation. Only an explicitly confirmed repeat may bypass the repeat gate. */
    suspend fun complete(request: GpxImportRequest, confirmRepeatedOriginal: Boolean = false,
                         isCapturedRequestCurrent: () -> Boolean): GpxPreparationResult {
        var owned = false
        return guarded(request.requestUuid, { owned }) {
            persistence.completed(request.requestUuid)?.let { row ->
                require(row.territoryId == request.territoryId && row.importingObserverId == request.observerId)
                return@guarded completedResult(row)
            }
            val job = persistence.journal(request.requestUuid) ?: throw RequestUnavailable()
            require(job.territoryId == request.territoryId && job.importingObserverId == request.observerId)
            owned = true
            val context = currentCoroutineContext()
            val check = { context.ensureActive(); if (!isCapturedRequestCurrent()) throw RequestUnavailable() }
            finishReady(job, confirmRepeatedOriginal, check)
        }
    }

    /** Read-only correlation lookup never handles or consumes a request. */
    suspend fun lookup(requestUuid: UUID): GpxPreparationResult.Ready? = withContext(Dispatchers.IO) {
        lock.withLock { persistence.completed(requestUuid)?.let { ready(it) } }
    }

    private suspend fun finishReady(job: GpxImportJobEntity, confirmed: Boolean,
                                    check: () -> Unit): GpxPreparationResult {
        require(job.phase == "FILE_PUBLISHED" && job.intent == "CONTINUE")
        check()
        persistence.requirePending(job)
        originals.openFinal(requireNotNull(job.originalObjectId), descriptor(job), check).use { }
        checkpoint("BEFORE_READY")
        check()
        val row = persistence.publishReady(job, confirmed, check, checkpoint)
        checkpoint("READY_COMMITTED")
        return completedResult(row)
    }

    private fun ready(row: GpxImportEntity, cleanupPending: Boolean = false) = GpxPreparationResult.Ready(
        requireNotNull(row.requestUuid), row.id, row.originalObjectId, cleanupPending,
        hasWarnings = row.unsupportedContentCount > 0 || row.unsupportedTimeCount > 0,
    )

    private suspend fun completedResult(row: GpxImportEntity): GpxPreparationResult.Ready {
        val request = requireNotNull(row.requestUuid)
        return try {
            val job = persistence.journal(request)
            if (job != null) {
                check(persistence.completed(request) == row)
                val files = originals.inspectOperation(request, row.originalObjectId)
                if (files.owned) originals.cleanupStaging(request, row.originalObjectId,
                    GpxOriginalIdentity(row.originalByteLength, row.originalSha256), authorize = { true })
                checkpoint("READY_STAGING_REMOVED")
                persistence.forgetReady(job)
            }
            ready(row)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ready(row, cleanupPending = true)
        }
    }

    /** Startup owner calls before exposing GPX reads; never reopens a provider URI. */
    suspend fun recover(): List<GpxPreparationResult> = withContext(Dispatchers.IO) {
        // The snapshot is not authorization: each item is re-read under the shared mutex.
        persistence.journals().map { saved ->
            guarded(saved.requestUuid, { true }) {
                persistence.completed(saved.requestUuid)?.let { completedResult(it) } ?: run {
                    val current = persistence.journal(saved.requestUuid)
                    if (current == null) GpxPreparationResult.Aborted(saved.requestUuid) else {
                        val prepared = recoverJob(current)
                        if (prepared is GpxPreparationResult.Prepared) {
                            val published = requireNotNull(persistence.journal(saved.requestUuid))
                            // OWNER clarification: non-repeats may complete; unconfirmed repeats must abort.
                            persistence.rejectUnconfirmedRecoveryRepeat(published)
                            val context = currentCoroutineContext()
                            // Recheck current inventory in the READY transaction, never infer consent.
                            finishReady(published, confirmed = false, check = { context.ensureActive() })
                        } else prepared
                    }
                }
            }
        }
    }

    suspend fun abort(requestUuid: UUID): GpxPreparationResult = guarded(requestUuid, { true }) {
        persistence.completed(requestUuid)?.let { completedResult(it) } ?: run {
            compensate(requestUuid)
            GpxPreparationResult.Aborted(requestUuid)
        }
    }

    private suspend fun recoverJob(job: GpxImportJobEntity, currentRequestCheck: (() -> Unit)? = null): GpxPreparationResult {
        if (job.phase == "READY") throw RequestUnavailable() // Never touch READY, even malformed ABORT.
        if (job.intent == "ABORT" || job.phase !in setOf("PARSED", "FILE_PUBLISHED")) {
            compensate(job.requestUuid)
            return GpxPreparationResult.Aborted(job.requestUuid)
        }
        val context = currentCoroutineContext()
        // Startup reconciliation uses persisted ownership plus live FK existence, not the UI's
        // current Territory. A live prepare retry must additionally honor captured-request cancellation.
        val check = { context.ensureActive(); currentRequestCheck?.invoke(); Unit }
        check()
        persistence.requirePending(job)
        return if (job.phase == "PARSED") publish(job, check) else {
            val original = requireNotNull(job.originalObjectId)
            require(originals.inspectFinal(original, descriptor(job), check) == GpxOriginalPresence.PRESENT_VERIFIED)
            // Durable FILE_PUBLISHED was committed only after publish proved request ownership.
            // Verify its bound final, without replaying publish's PARSED-only authorization.
            originals.openFinal(original, descriptor(job), check).use { }
            prepared(job)
        }
    }

    private suspend fun publish(job: GpxImportJobEntity, check: () -> Unit): GpxPreparationResult {
        persistence.requirePending(job) // Current PARSED context and full graph verified while serialized.
        check()
        originals.publish(job.requestUuid, job.originalObjectId!!, descriptor(job),
            { job.phase == "PARSED" && job.intent == "CONTINUE" }, check)
        checkpoint("FINAL_VERIFIED") // Crash here leaves PARSED plus independently verifiable file proof.
        check()
        val published = advance(job, job.copy(phase = "FILE_PUBLISHED"))
        check()
        return prepared(published)
    }

    private fun prepared(job: GpxImportJobEntity) = GpxPreparationResult.Prepared(
        job.requestUuid, requireNotNull(job.importId), requireNotNull(job.originalObjectId))

    private suspend fun advance(previous: GpxImportJobEntity, next: GpxImportJobEntity): GpxImportJobEntity {
        persistence.advance(previous, next)
        checkpoint(next.phase)
        return next
    }

    /** Durable ABORT before any deletion; graph detachment retains the descriptor for file cleanup. */
    private suspend fun compensate(request: UUID) {
        val aborted = persistence.abort(request) ?: return
        persistence.cleanupPending(request)
        val current = requireNotNull(persistence.journal(request))
        check(current.intent == "ABORT" && current.importId == null && current.phase != "READY")
        val original = current.originalObjectId
        if (original != null) {
            val files = originals.inspectOperation(request, original)
            if (files.finalExists) {
                // A descriptor and bound filesystem evidence are mandatory; never guess an owner.
                originals.cleanupAbortedOriginal(request, original, descriptor(current),
                    confirmDurablePublicationOwnership = { aborted.phase == "FILE_PUBLISHED" },
                    authorizeCurrentAbortWithoutReady = { true })
            }
            if (files.owned) originals.cleanupStaging(request, original, authorize = { true })
        }
        persistence.forgetAborted(current)
    }

    private suspend fun guarded(request: UUID, owns: () -> Boolean,
                                block: suspend () -> GpxPreparationResult): GpxPreparationResult = try {
        withContext(Dispatchers.IO) { lock.withLock { block() } }
    } catch (cancelled: CancellationException) {
        if (owns()) try { settleUncancelled(request) } catch (cleanup: Exception) {
            if (cleanup !== cancelled) cancelled.addSuppressed(cleanup)
        }
        throw cancelled // SAME cancellation; committed success remains discoverable through lookup.
    } catch (failure: Exception) {
        var pending = false
        var completed: GpxPreparationResult.Ready? = null
        if (owns()) try { completed = settleUncancelled(request) } catch (cleanup: Exception) {
            if (cleanup !== failure) failure.addSuppressed(cleanup)
            pending = true // Durable journal remains for another recovery attempt.
        }
        completed ?: GpxPreparationResult.Failed(request, when (failure) {
            is RequestUnavailable -> GpxPreparationResult.Kind.REQUEST
            is ParseRejected -> GpxPreparationResult.Kind.PARSE
            is GpxOriginalStoreException -> GpxPreparationResult.Kind.ORIGINAL
            is GpxAdmissionException -> GpxPreparationResult.Kind.ADMISSION
            else -> GpxPreparationResult.Kind.PERSISTENCE
        }, (failure as? ParseRejected)?.result, (failure as? GpxOriginalStoreException)?.code, pending,
            (failure as? GpxAdmissionException)?.rejection)
    }

    private suspend fun settleUncancelled(request: UUID): GpxPreparationResult.Ready? = withContext(NonCancellable) {
        withContext(Dispatchers.IO) { lock.withLock {
            // Read durable state before ANY compensation; a failed lookup is not deletion authority.
            persistence.completed(request)?.let { completedResult(it) } ?: run { compensate(request); null }
        } }
    }

    private fun descriptor(job: GpxImportJobEntity) = GpxOriginalIdentity(
        requireNotNull(job.originalByteLength), requireNotNull(job.originalSha256))
    private class RequestUnavailable : Exception()
    private class ParseRejected(val result: GpxParseResult.Rejected) : Exception()
    companion object {
        private val lock = Mutex()
        internal suspend fun <T> serializedWriter(block: suspend () -> T): T = lock.withLock { block() }
    }
}
