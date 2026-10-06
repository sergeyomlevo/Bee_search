package org.beesearch.app.data.backuprepository

/**
 * One media identity as captured research metadata presents it.
 *
 * The same record shape covers both paths that need the required media set: Snapshot V1 reads it from
 * the raw wire rows (where the identity fields are optional and may be absent), and media protection
 * reads it from the captured Room rows. Values are never repaired here: an absent or unusable
 * identity stays unusable.
 */
internal data class MediaIdentityCandidate(
    val sha256: String?,
    val byteSize: Long?,
    val mimeType: String?,
)

/**
 * One blob the current research state requires in the repository.
 *
 * [mimeHints] are the aggregated hints of exactly the eligible candidates that form this blob, in
 * capture order. Passing them to Repository ingest reproduces [canonicalExtension], so protection and
 * snapshot references cannot disagree about the canonical path.
 */
internal data class RequiredMediaBlob(
    val sha256: String,
    val byteSize: Long,
    val canonicalExtension: String,
    val mimeHints: List<String?>,
    /** Indexes into the resolver input of the eligible candidates that formed this blob. */
    val participantIndexes: List<Int>,
)

/** The captured metadata cannot define a coherent required media set. Nothing may be attempted. */
internal class RequiredMediaSetException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The single definition of which media blobs the current research state requires.
 *
 * Snapshot V1's `references/media-blobs.jsonl` and media protection both resolve their work through
 * this object, so there is exactly one eligibility, deduplication and canonical-extension rule set.
 * Duplicating this algorithm anywhere else would let the two views of "required media" drift apart.
 *
 * Semantics (unchanged and Snapshot V1 wire compatible):
 *
 * - a candidate is eligible only with a present SHA and a size in `1..9007199254740991`;
 * - ineligible candidates produce no blob and supply neither size nor MIME evidence;
 * - candidates group by exact SHA, so one SHA yields exactly one blob;
 * - all eligible sizes in a group must agree, otherwise the set is rejected;
 * - MIME hints of the whole group are aggregated through [CanonicalExtension] (`jpg`, `mp4`, `bin`);
 * - the result is sorted by SHA.
 */
internal object RequiredMediaSet {
    const val CONFLICTING_SIZE = "conflicting media size"
    const val CONFLICTING_TYPE = "conflicting media type"
    private const val MAX_SAFE_SIZE = 9007199254740991L

    fun resolve(candidates: List<MediaIdentityCandidate>): List<RequiredMediaBlob> {
        val eligible = candidates.withIndex().filter { (_, candidate) ->
            val sha = candidate.sha256
            val size = candidate.byteSize
            sha != null && size != null && size >= 1L && size <= MAX_SAFE_SIZE
        }
        val grouped = eligible.groupBy { (_, candidate) -> candidate.sha256!! }
        for (group in grouped.values) {
            if (group.map { (_, candidate) -> candidate.byteSize }.distinct().size > 1) {
                throw RequiredMediaSetException(CONFLICTING_SIZE)
            }
        }
        return grouped.map { (sha, group) ->
            val hints = group.map { (_, candidate) -> candidate.mimeType }
            val extension = try {
                CanonicalExtension.resolve(hints)
            } catch (e: Exception) {
                // Recognized types that disagree are a metadata inconsistency, never a silent pick.
                throw RequiredMediaSetException(CONFLICTING_TYPE, e)
            }
            RequiredMediaBlob(
                sha256 = sha,
                byteSize = group.first().value.byteSize!!,
                canonicalExtension = extension,
                mimeHints = hints,
                participantIndexes = group.map { (index, _) -> index },
            )
        }.sortedBy { it.sha256 }
    }
}
