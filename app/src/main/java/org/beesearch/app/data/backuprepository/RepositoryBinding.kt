package org.beesearch.app.data.backuprepository

import java.util.UUID
import java.io.File
import org.beesearch.app.data.backupsnapshot.SnapshotDomainEntries
import org.beesearch.app.data.backupsnapshot.SnapshotException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class RepositoryBinding(val expectedRepositoryId: UUID, val rootLocator: String)

internal interface RepositoryBindingStore {
    suspend fun read(): RepositoryBinding?
    /** Atomic compare-and-replace; malformed/unreadable persistence is never treated as unbound. */
    suspend fun replace(expected: RepositoryBinding?, next: RepositoryBinding?)
}

internal fun interface RepositoryRootResolver {
    fun resolve(locator: String): RepositoryFoundation
}

internal sealed interface RepositoryConnection {
    data object Unbound : RepositoryConnection
    data class Bound(val binding: RepositoryBinding) : RepositoryConnection
    data class Failed(val error: RepositoryError) : RepositoryConnection
}

/** The application-facing gate. No long-lived write-capable session/identity cache. */
internal class BoundRepository(
    private val store: RepositoryBindingStore,
    private val roots: RepositoryRootResolver,
    private val gate: Mutex = Mutex(),
) {
    suspend fun probe(): RepositoryConnection = gate.withLock {
        try {
            val binding = store.read() ?: return@withLock RepositoryConnection.Unbound
            requireConnected(binding)
            RepositoryConnection.Bound(binding)
        } catch (e: RepositoryException) { RepositoryConnection.Failed(e.error) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { RepositoryConnection.Failed(RepositoryError.PROVIDER_FAILURE) }
    }

    /** New repository only when unbound. Existing valid repository requires explicit adopt. */
    suspend fun initializeNew(locator: String): RepositoryResult<RepositoryBinding> = operation {
        if (store.read() != null) throw RepositoryException(RepositoryError.BINDING_CHANGED)
        val foundation = roots.resolve(locator)
        val header = foundation.initializeNew().valueOrThrow()
        foundation.open(header.repositoryId).valueOrThrow()
        val binding = RepositoryBinding(header.repositoryId, locator)
        store.replace(null, binding)
        binding
    }

    /** Explicit adoption after reinstall/unbound. Never automatically replaces an existing UUID. */
    suspend fun adoptExisting(locator: String): RepositoryResult<RepositoryBinding> = operation {
        if (store.read() != null) throw RepositoryException(RepositoryError.BINDING_CHANGED)
        val foundation = roots.resolve(locator)
        val header = foundation.inspectHeader().valueOrThrow()
        foundation.open(header.repositoryId).valueOrThrow()
        val binding = RepositoryBinding(header.repositoryId, locator)
        store.replace(null, binding)
        binding
    }

    /** New location for the SAME identity only. A failed probe leaves the old locator unchanged. */
    suspend fun reconnect(locator: String): RepositoryResult<RepositoryBinding> = operation {
        val previous = store.read() ?: throw RepositoryException(RepositoryError.UNBOUND)
        val candidate = previous.copy(rootLocator = locator)
        requireConnected(candidate)
        store.replace(previous, candidate)
        candidate
    }

    /** Intentional switch; requires caller's exact prior binding and explicit target identity. */
    suspend fun rebind(expected: RepositoryBinding, locator: String, targetId: UUID): RepositoryResult<RepositoryBinding> = operation {
        if (store.read() != expected) throw RepositoryException(RepositoryError.BINDING_CHANGED)
        val next = RepositoryBinding(targetId, locator)
        requireConnected(next)
        store.replace(expected, next)
        next
    }

    /** Removes device-local binding only; never touches repository files or grants. */
    suspend fun clear(expected: RepositoryBinding): RepositoryResult<Unit> = operation {
        store.replace(expected, null)
    }

    suspend fun ingest(source: BlobSource, cancelled: () -> Boolean = { false }): RepositoryResult<CommittedBlob> = operation {
        val binding = store.read() ?: throw RepositoryException(RepositoryError.UNBOUND)
        val foundation = requireConnected(binding)
        foundation.ingest(binding.expectedRepositoryId, source, cancelled).valueOrThrow()
    }

    suspend fun createMetadataSnapshot(workspace: File, capture: suspend () -> SnapshotDomainEntries,
        cancelled: () -> Boolean = { false }): RepositoryResult<CommittedSnapshot> = operation {
        val binding = store.read() ?: throw RepositoryException(RepositoryError.UNBOUND)
        requireConnected(binding).createMetadataSnapshot(binding.expectedRepositoryId, workspace, capture,
            cancelled = cancelled).valueOrThrow()
    }

    /** Tuple B: the same capture with its required media set verified in this same bound repository. */
    suspend fun createFullSnapshot(workspace: File, capture: suspend () -> SnapshotDomainEntries,
        cancelled: () -> Boolean = { false }): RepositoryResult<CommittedSnapshot> = operation {
        val binding = store.read() ?: throw RepositoryException(RepositoryError.UNBOUND)
        requireConnected(binding).createFullSnapshot(binding.expectedRepositoryId, workspace, capture,
            cancelled = cancelled).valueOrThrow()
    }

    suspend fun discoverSnapshots(workspace: File): RepositoryResult<SnapshotDiscovery> = operation {
        val binding = store.read() ?: throw RepositoryException(RepositoryError.UNBOUND)
        requireConnected(binding).discoverSnapshots(binding.expectedRepositoryId, workspace).valueOrThrow()
    }

    suspend fun readPublishedSummary(workspace: File): RepositoryResult<PublishedBackupSummary> = operation {
        val binding = store.read() ?: throw RepositoryException(RepositoryError.UNBOUND)
        requireConnected(binding).readPublishedSummary(binding.expectedRepositoryId, workspace).valueOrThrow()
    }

    suspend fun createFullSnapshotFromCaptured(workspace: File, entries: SnapshotDomainEntries,
        cancelled: () -> Boolean = { false }): RepositoryResult<CommittedSnapshot> = operation {
        val binding = store.read() ?: throw RepositoryException(RepositoryError.UNBOUND)
        requireConnected(binding).createFullSnapshotFromCaptured(binding.expectedRepositoryId, workspace,
            entries, cancelled = cancelled).valueOrThrow()
    }

    private suspend fun requireConnected(binding: RepositoryBinding): RepositoryFoundation {
        val foundation = try { roots.resolve(binding.rootLocator) } catch (e: RepositoryException) {
            if (e.error == RepositoryError.NOT_FOUND) throw RepositoryException(RepositoryError.BOUND_ROOT_UNAVAILABLE, e)
            throw e
        }
        val root = foundation.inspectRoot()
        if (root is RepositoryResult.Failure && root.error == RepositoryError.NOT_FOUND) {
            throw RepositoryException(RepositoryError.BOUND_ROOT_UNAVAILABLE)
        }
        root.valueOrThrow()
        val result = foundation.open(binding.expectedRepositoryId)
        if (result is RepositoryResult.Failure && result.error == RepositoryError.NOT_FOUND) {
            throw RepositoryException(RepositoryError.BOUND_REPOSITORY_MISSING)
        }
        result.valueOrThrow()
        return foundation
    }

    private suspend fun <T> operation(block: suspend () -> T): RepositoryResult<T> = gate.withLock {
        try { RepositoryResult.Success(block()) }
        catch (e: RepositoryException) { RepositoryResult.Failure(e.error, e.detail) }
        catch (e: SnapshotException) { RepositoryResult.Failure(e.repositoryError(), e.repositoryDetail()) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { RepositoryResult.Failure(RepositoryError.PROVIDER_FAILURE) }
    }
}

internal fun <T> RepositoryResult<T>.valueOrThrow(): T = when (this) {
    is RepositoryResult.Success -> value
    is RepositoryResult.Failure -> throw RepositoryException(error, detail = detail)
}
