package org.beesearch.app.data.gpx

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.beesearch.app.data.local.room.GpxPersistenceStore
import org.beesearch.app.data.local.room.GpxStoredGraph

internal data class GpxReconciliationResult(
    val operations: List<GpxPreparationResult> = emptyList(),
    val failure: Exception? = null,
) {
    // A committed READY remains readable even when post-commit cleanup needs a retry.
    val completed: Boolean get() = failure == null && operations.all {
        it is GpxPreparationResult.Ready || it is GpxPreparationResult.Aborted
    }
}

internal class GpxRuntimeUnavailableException(val reconciliation: GpxReconciliationResult) :
    IllegalStateException("GPX reconciliation incomplete", reconciliation.failure)

/**
 * Application-owned first-access gate. No UI lifetime or provider URI ownership.
 * The Coordinator is private: runtime reconciliation precedes internal import workflow;
 * its existing process-wide writer mutex remains the authority for journal/file mutations.
 * Failed passes are retryable, never permission for extra cleanup at this boundary.
 */
internal class GpxRuntime(
    private val persistence: GpxPersistenceStore,
    createCoordinator: () -> GpxImportCoordinator,
) {
    private val coordinator by lazy(createCoordinator)
    constructor(coordinator: GpxImportCoordinator, persistence: GpxPersistenceStore) :
        this(persistence, { coordinator })

    private val gate = Mutex()
    private var settled: GpxReconciliationResult? = null

    /** Concurrent startup/first readers share one successful pass; failed passes can be retried. */
    suspend fun reconcile(): GpxReconciliationResult = withContext(Dispatchers.IO) {
        gate.withLock { reconcileLocked() }
    }

    /** Keep live orchestration and reconciliation mutually exclusive, including between passes. */
    internal suspend fun <T> withImportCoordinator(block: suspend (GpxImportCoordinator) -> T): T =
        withContext(Dispatchers.IO) {
            gate.withLock {
                val result = reconcileLocked()
                if (!result.completed) throw GpxRuntimeUnavailableException(result)
                try { block(coordinator) } finally {
                    // A failed/cancelled operation may leave journal/cleanup debt for the next pass.
                    settled = null
                }
            }
        }

    /** READY is stored research data, not membership in the map's visible set. */
    suspend fun readReady(territoryId: UUID, importId: UUID): GpxStoredGraph? = withContext(Dispatchers.IO) {
        gate.withLock {
            val result = reconcileLocked()
            if (!result.completed) throw GpxRuntimeUnavailableException(result)
            persistence.readReady(importId)?.takeIf { it.importRow.territoryId == territoryId }
        }
    }

    private suspend fun reconcileLocked(): GpxReconciliationResult {
        settled?.let { return it }
        val result = try {
            GpxReconciliationResult(coordinator.recover())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            GpxReconciliationResult(failure = failure)
        }
        if (result.completed && result.operations.none {
                it is GpxPreparationResult.Ready && it.cleanupPending
            }) settled = result
        return result
    }
}
