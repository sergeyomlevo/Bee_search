package org.beesearch.app.data.backuprepository

import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

internal enum class RepositoryError {
    NOT_FOUND, PERMISSION_LOST, PROVIDER_FAILURE, ROOT_IDENTITY_MISMATCH,
    AMBIGUOUS_REPOSITORY, INVALID_HEADER, UNSUPPORTED_FORMAT, DIRECTORY_CONFLICT,
    CAPACITY_UNKNOWN, CAPACITY_INSUFFICIENT, WRITE_FAILED, SYNC_FAILED, VERIFY_FAILED,
    PUBLISH_FAILED, UNSUPPORTED_PUBLICATION_PATH, METADATA_INCONSISTENCY,
    SOURCE_CHANGED, CANCELLED, DELETE_FAILED,
    UNBOUND, BOUND_ROOT_UNAVAILABLE, BOUND_REPOSITORY_MISSING,
    BINDING_INVALID, BINDING_CHANGED, BINDING_PERSISTENCE_FAILED,
    INVALID_SNAPSHOT, LOGICAL_STATE_INCONSISTENT, SNAPSHOT_LIMIT_EXCEEDED, SNAPSHOT_ID_CONFLICT,
    MEDIA_EVIDENCE_MISSING, MEDIA_EVIDENCE_MISMATCH, MEDIA_EVIDENCE_INCONSISTENT,
}

internal data class RepositoryFailureDetail(val category: String, val observed: Long?, val limit: Long?)

internal class RepositoryException(val error: RepositoryError, cause: Throwable? = null,
    val detail: RepositoryFailureDetail? = null) :
    Exception(error.name, cause)

internal sealed interface RepositoryResult<out T> {
    data class Success<T>(val value: T) : RepositoryResult<T>
    data class Failure(val error: RepositoryError, val detail: RepositoryFailureDetail? = null) : RepositoryResult<Nothing>
}

internal data class RepositoryEntry(val path: String, val isDirectory: Boolean, val byteSize: Long)

/** Root-relative paths only. Only successful listings prove absence, never provider exceptions.
 * One service owns this adapter; callers must not bypass its maintenance lock.
 */
internal interface RepositoryStorage {
    fun ensureDirectory(path: String)
    fun inspect(path: String): RepositoryEntry?
    fun list(path: String): List<RepositoryEntry>
    fun createOwnedStage(operationId: UUID): String
    fun openTruncatedWriter(path: String): OutputStream
    fun openReader(path: String): InputStream
    fun sync(path: String)
    fun requirePublicationCapability()
    /** No replacement or copy fallback. Exact requested target only. */
    fun moveOwnedStage(stage: String, target: String)
    /** Only entries created by this adapter/operation; never recursive unknown data. */
    fun deleteOwnedStage(operationId: UUID)
    /** Fresh available bytes; null includes unconfirmed mapping. Never allocatable bytes. */
    fun availableBytes(): Long?
}

internal data class CommittedBlob(
    val sha256: String,
    val byteSize: Long,
    val extension: String,
    val alreadyPresent: Boolean,
) {
    val path: String get() = "Media/$sha256.$extension"
}
