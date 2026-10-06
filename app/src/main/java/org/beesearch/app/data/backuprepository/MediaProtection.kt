package org.beesearch.app.data.backuprepository

import androidx.room.withTransaction
import java.io.File
import java.util.UUID
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.snapshot
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore

/** Which managed store owns one captured media relative path. */
internal enum class MediaSourceKind {
    PHYSICAL_OBJECT_MEDIA,
    OBSERVATION_POINT_ATTACHMENT,
}

/**
 * One captured media metadata record.
 *
 * It is the only way protection may reach private bytes: a source exists because a captured record
 * names it, never because a file happens to sit under `filesDir`.
 */
internal data class CapturedMediaRow(
    val kind: MediaSourceKind,
    val recordId: UUID,
    val ownerId: UUID,
    val relativePath: String,
    val sha256: String?,
    val byteSize: Long?,
    val mimeType: String?,
)

/** One coherent capture of the media metadata the current research state holds. */
internal data class CapturedMediaState(val rows: List<CapturedMediaRow>) {
    fun candidates(): List<MediaIdentityCandidate> =
        rows.map { MediaIdentityCandidate(it.sha256, it.byteSize, it.mimeType) }
}

/** The privacy-safe source of captured media metadata: one Room read transaction, like the snapshot. */
internal class MediaStateCapture(
    private val database: BeeSearchDatabase,
) {
    suspend fun capture(): CapturedMediaState {
        val graph: Graph = database.withTransaction { database.backupDao().snapshot() }
        return CapturedMediaState(
            rows = buildList {
                graph.objectMedia.forEach {
                    add(
                        CapturedMediaRow(
                            kind = MediaSourceKind.PHYSICAL_OBJECT_MEDIA,
                            recordId = it.id,
                            ownerId = it.physicalObjectId,
                            relativePath = it.relativePath,
                            sha256 = it.sha256,
                            byteSize = it.byteSize,
                            mimeType = it.mimeType,
                        ),
                    )
                }
                graph.attachments.forEach {
                    add(
                        CapturedMediaRow(
                            kind = MediaSourceKind.OBSERVATION_POINT_ATTACHMENT,
                            recordId = it.id,
                            ownerId = it.observationPointId,
                            relativePath = it.relativePath,
                            sha256 = it.sha256,
                            byteSize = it.byteSize,
                            mimeType = it.mimeType,
                        ),
                    )
                }
            },
        )
    }
}

/** Resolves a captured row to its private file, or null when that source cannot be used safely. */
internal fun interface MediaSourceResolver {
    fun resolve(row: CapturedMediaRow): File?
}

/** The production resolver: the managed stores keep their own path-escape protections. */
internal class FileStoreMediaSourceResolver(
    private val objectMedia: PhysicalObjectMediaFileStore,
    private val attachments: ObservationAttachmentFileStore,
) : MediaSourceResolver {
    override fun resolve(row: CapturedMediaRow): File? = try {
        val file = when (row.kind) {
            MediaSourceKind.PHYSICAL_OBJECT_MEDIA -> objectMedia.resolve(row.relativePath)
            MediaSourceKind.OBSERVATION_POINT_ATTACHMENT -> attachments.resolve(row.relativePath)
        }
        file.takeIf { it.isFile }
    } catch (_: Exception) {
        null
    }
}

/** One required blob with the deterministic captured sources that may supply its bytes. */
internal data class MediaProtectionPlanItem(
    val blob: RequiredMediaBlob,
    val sources: List<CapturedMediaRow>,
)

internal data class MediaProtectionPlan(val items: List<MediaProtectionPlanItem>)

/**
 * Turns one capture into a protection plan.
 *
 * The required set comes from [RequiredMediaSet], and the sources of a blob are exactly the captured
 * rows that contributed to its SHA, in a deterministic order. A second definition of "which media are
 * required" or "which file supplies it" must never appear here.
 */
internal object MediaProtectionPlanner {
    private val sourceOrder = compareBy<CapturedMediaRow>({ it.kind.ordinal }, { it.recordId.toString() })

    fun plan(captured: CapturedMediaState): MediaProtectionPlan {
        val blobs = RequiredMediaSet.resolve(captured.candidates())
        return MediaProtectionPlan(
            blobs.map { blob ->
                MediaProtectionPlanItem(
                    blob = blob,
                    sources = blob.participantIndexes.map(captured.rows::get).sortedWith(sourceOrder),
                )
            },
        )
    }
}

/** What happened to one required blob. */
internal enum class MediaProtectionOutcome {
    /** The blob was published into the repository by this run. */
    INGESTED,

    /** The repository already held this exact blob and verified it again. */
    ALREADY_PRESENT,

    /** No usable captured source could supply these bytes. */
    SOURCE_MISSING,

    /** A captured source no longer matches the metadata identity it claims. */
    SOURCE_CHANGED,

    /** The repository refused growth for space reasons; fail closed. */
    CAPACITY_BLOCKED,

    /** The repository could not verify what it wrote. */
    VERIFY_FAILED,

    /** The repository's canonical extension disagreed with the required one; fail closed. */
    EXTENSION_MISMATCH,

    /** Any other repository failure for this blob. */
    REPOSITORY_ERROR,

    /** A repository-wide blocker stopped the run before this blob was attempted. */
    SKIPPED_AFTER_GLOBAL_BLOCKER,
    ;

    val isProtected: Boolean get() = this == INGESTED || this == ALREADY_PRESENT
}

internal data class ProtectedBlobResult(
    val sha256: String,
    val byteSize: Long,
    val canonicalExtension: String,
    val outcome: MediaProtectionOutcome,
    val source: CapturedMediaRow? = null,
    val error: RepositoryError? = null,
)

/** How the whole run ended. A partially protected run must never look like a complete one. */
internal enum class MediaProtectionSummary {
    ALL_PROTECTED,
    PARTIALLY_PROTECTED,
    NOTHING_PROTECTED,
    REPOSITORY_BLOCKED,
    CANCELLED,

    /** The captured metadata could not define a coherent required set; nothing was attempted. */
    METADATA_INCONSISTENT,
}

internal data class MediaProtectionReport(
    val results: List<ProtectedBlobResult>,
    val summary: MediaProtectionSummary,
    /** The repository-wide failure that stopped the run, when there was one. */
    val blocker: RepositoryError? = null,
    /** Present only for [MediaProtectionSummary.METADATA_INCONSISTENT]. */
    val inconsistency: String? = null,
) {
    val requiredCount: Int get() = results.size
    val protectedCount: Int get() = results.count { it.outcome.isProtected }
}

/**
 * Copies every blob the current research state requires into the repository.
 *
 * It is deliberately not a transaction: a blob that cannot be protected never rolls back blobs that
 * already were, and every required blob gets its own typed outcome. Only a repository-wide failure
 * stops the run, and then the untouched blobs are reported as skipped instead of being hidden.
 *
 * This service never deletes or moves a private original, never touches Room or DataStore, and never
 * creates a snapshot: media protection and snapshot evidence stay separate.
 *
 * [MediaProtectionSummary.ALL_PROTECTED] therefore means every required blob is represented in the
 * repository by bytes whose SHA-256, byte size and canonical extension all agree with the required
 * set derived from the captured metadata.
 */
internal class MediaProtectionService(
    private val repository: BoundRepository,
    private val sources: MediaSourceResolver,
    private val privateRoot: File,
    /**
     * The publication call for one candidate blob. Production always uses [BoundRepository.ingest];
     * the parameter exists so the defensive agreement checks between the resolved required set and
     * the repository's own answer can be exercised with a repository response that disagrees.
     */
    private val ingest: suspend (BlobSource, () -> Boolean) -> RepositoryResult<CommittedBlob> =
        { source, cancelled -> repository.ingest(source, cancelled) },
) {
    suspend fun protect(
        captured: CapturedMediaState,
        cancelled: () -> Boolean = { false },
    ): MediaProtectionReport {
        val plan = try {
            MediaProtectionPlanner.plan(captured)
        } catch (e: RequiredMediaSetException) {
            return MediaProtectionReport(
                results = emptyList(),
                summary = MediaProtectionSummary.METADATA_INCONSISTENT,
                inconsistency = e.message,
            )
        }

        val results = mutableListOf<ProtectedBlobResult>()
        var blocker: RepositoryError? = null
        var cancelledByCaller = false

        for (item in plan.items) {
            if (blocker != null || cancelledByCaller) {
                results += skipped(item)
                continue
            }
            if (cancelled()) {
                cancelledByCaller = true
                results += skipped(item)
                continue
            }
            val result = protectOne(item, cancelled)
            results += result
            if (result.error == RepositoryError.CANCELLED) {
                // The caller stopped the run: never reported as success, and the rest is untouched.
                cancelledByCaller = true
                continue
            }
            if (result.error?.isRepositoryWide == true) {
                blocker = result.error
            }
        }

        return MediaProtectionReport(
            results = results,
            summary = summarize(results.size, results.count { it.outcome.isProtected }, blocker, cancelledByCaller),
            blocker = blocker,
        )
    }

    /**
     * One blob, trying its captured sources in order.
     *
     * A source that is missing or no longer matches only rules out that source: another captured row
     * with the same SHA may still supply the bytes, so blob-local source problems never stop the run
     * and never stop this blob from being protected by a different source.
     */
    private suspend fun protectOne(item: MediaProtectionPlanItem, cancelled: () -> Boolean): ProtectedBlobResult {
        var sourceProblem: MediaProtectionOutcome = MediaProtectionOutcome.SOURCE_MISSING
        var sourceError: RepositoryError? = null
        for (row in item.sources) {
            val file = sources.resolve(row) ?: continue
            val source = try {
                PrivateBlobSource(privateRoot, file, item.blob.mimeHints, item.blob.sha256)
            } catch (e: RepositoryException) {
                sourceProblem = MediaProtectionOutcome.SOURCE_CHANGED
                sourceError = e.error
                continue
            }
            // The required set declares the size this blob must have, and the repository publishes
            // the bytes it actually read. A candidate whose real size already disagrees with the
            // declaration is a changed candidate: publishing it would report a satisfied size the
            // required set does not describe. Another captured source for the same SHA may still be
            // intact, so this rules out that candidate only, exactly like an unreadable source.
            if (source.byteSize != item.blob.byteSize) {
                sourceProblem = MediaProtectionOutcome.SOURCE_CHANGED
                sourceError = RepositoryError.SOURCE_CHANGED
                continue
            }
            when (val result = ingest(source, cancelled)) {
                is RepositoryResult.Success -> {
                    if (result.value.extension != item.blob.canonicalExtension) {
                        return result(item, MediaProtectionOutcome.EXTENSION_MISMATCH, row, null)
                    }
                    val outcome = if (result.value.alreadyPresent) {
                        MediaProtectionOutcome.ALREADY_PRESENT
                    } else {
                        MediaProtectionOutcome.INGESTED
                    }
                    return result(item, outcome, row, null)
                }

                is RepositoryResult.Failure -> when (result.error) {
                    // Another captured source for the same SHA may still be intact.
                    RepositoryError.SOURCE_CHANGED -> {
                        sourceProblem = MediaProtectionOutcome.SOURCE_CHANGED
                        sourceError = result.error
                        continue
                    }

                    RepositoryError.CAPACITY_INSUFFICIENT, RepositoryError.CAPACITY_UNKNOWN ->
                        return result(item, MediaProtectionOutcome.CAPACITY_BLOCKED, row, result.error)

                    RepositoryError.VERIFY_FAILED ->
                        return result(item, MediaProtectionOutcome.VERIFY_FAILED, row, result.error)

                    // Any other repository error is reported where it happened; whether it stops the
                    // run is decided once, from the outcome mapping, not here.
                    else -> return result(item, repositoryOutcome(result.error), row, result.error)
                }
            }
        }
        return result(item, sourceProblem, null, sourceError)
    }

    private fun result(
        item: MediaProtectionPlanItem,
        outcome: MediaProtectionOutcome,
        source: CapturedMediaRow?,
        error: RepositoryError?,
    ) = ProtectedBlobResult(
        sha256 = item.blob.sha256,
        byteSize = item.blob.byteSize,
        canonicalExtension = item.blob.canonicalExtension,
        outcome = outcome,
        source = source,
        error = error,
    )

    private fun skipped(item: MediaProtectionPlanItem) = result(
        item,
        MediaProtectionOutcome.SKIPPED_AFTER_GLOBAL_BLOCKER,
        null,
        null,
    )

    private fun summarize(
        total: Int,
        protected: Int,
        blocker: RepositoryError?,
        cancelled: Boolean,
    ): MediaProtectionSummary = when {
        cancelled -> MediaProtectionSummary.CANCELLED
        blocker != null -> MediaProtectionSummary.REPOSITORY_BLOCKED
        total == 0 -> MediaProtectionSummary.ALL_PROTECTED
        protected == total -> MediaProtectionSummary.ALL_PROTECTED
        protected == 0 -> MediaProtectionSummary.NOTHING_PROTECTED
        else -> MediaProtectionSummary.PARTIALLY_PROTECTED
    }
}

private fun repositoryOutcome(error: RepositoryError): MediaProtectionOutcome = when (error) {
    RepositoryError.CAPACITY_INSUFFICIENT, RepositoryError.CAPACITY_UNKNOWN -> MediaProtectionOutcome.CAPACITY_BLOCKED
    RepositoryError.VERIFY_FAILED -> MediaProtectionOutcome.VERIFY_FAILED
    RepositoryError.SOURCE_CHANGED -> MediaProtectionOutcome.SOURCE_CHANGED
    else -> MediaProtectionOutcome.REPOSITORY_ERROR
}

/** Repository-wide errors, as opposed to a failure of one blob's bytes. */
internal val RepositoryError.isRepositoryWide: Boolean
    get() = when (this) {
        RepositoryError.UNBOUND,
        RepositoryError.PERMISSION_LOST,
        RepositoryError.BOUND_ROOT_UNAVAILABLE,
        RepositoryError.BOUND_REPOSITORY_MISSING,
        RepositoryError.ROOT_IDENTITY_MISMATCH,
        RepositoryError.BINDING_INVALID,
        RepositoryError.BINDING_CHANGED,
        RepositoryError.BINDING_PERSISTENCE_FAILED,
        RepositoryError.UNSUPPORTED_PUBLICATION_PATH,
        RepositoryError.PROVIDER_FAILURE,
        RepositoryError.CAPACITY_INSUFFICIENT,
        RepositoryError.CAPACITY_UNKNOWN,
        RepositoryError.NOT_FOUND,
        RepositoryError.CANCELLED,
        -> true

        else -> false
    }
