package org.beesearch.app.data.gpx

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.beesearch.app.data.local.room.GpxDeletionEntity
import org.beesearch.app.data.local.room.GpxDeletionStore

internal data class GpxDeleteResult(val cleanupPending: Boolean)

/** Confirmed operation stays bound to captured IDs, never the screen's later Territory. */
internal class GpxDeletion(
    private val store: GpxDeletionStore,
    private val originals: GpxOriginalStore,
    private val hide: suspend (UUID, UUID) -> Unit,
    private val checkpoint: (String) -> Unit = {},
) {
    suspend fun delete(
        territory: UUID,
        id: UUID,
        onCommitted: suspend (UUID) -> Unit = {},
    ): GpxDeleteResult = withContext(NonCancellable + Dispatchers.IO) {
        GpxImportCoordinator.serializedWriter {
            val prior = store.existing(id)
            val row = if (prior != null) {
                require(prior.territoryId == territory)
                prior
            } else {
                val source = requireNotNull(store.envelope(territory, id))
                val proof = originals.deletionProof(
                    requireNotNull(source.requestUuid), source.originalObjectId,
                    GpxOriginalIdentity(source.originalByteLength, source.originalSha256),
                )
                checkpoint("BEFORE_ROOM")
                store.commit(source, proof)
            }
            try {
                // Publish logical deletion before potentially slow/retryable cleanup.
                onCommitted(row.importId)
                checkpoint("AFTER_COMMIT")
                clean(row)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                GpxDeleteResult(cleanupPending = true)
            }
        }
    }

    suspend fun reconcile() = withContext(Dispatchers.IO) {
        GpxImportCoordinator.serializedWriter {
            // Debt of one deleted file never gates other READY data.
            store.pending().forEach { row ->
                try {
                    clean(row)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Both obligations remain durable until their individual acknowledgements.
                }
            }
        }
    }

    private suspend fun clean(row: GpxDeletionEntity): GpxDeleteResult {
        var pending = false
        if (!row.filesCleaned) try {
            check(store.unreferenced(row.originalObjectId))
            originals.cleanupDeletedOriginal(
                row.requestUuid, row.originalObjectId,
                GpxOriginalIdentity(row.originalByteLength, row.originalSha256), row.proof,
                // The reference check and deletion share the Coordinator writer lock.
                authorize = { true },
            )
            checkpoint("BEFORE_FILES_ACK")
            store.filesDone(row)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            pending = true
        }
        if (!row.settingsCleaned) try {
            hide(row.territoryId, row.importId)
            checkpoint("BEFORE_SETTINGS_ACK")
            store.settingsDone(row)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            pending = true
        }
        return GpxDeleteResult(cleanupPending = pending)
    }
}
