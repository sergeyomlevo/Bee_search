package pcsnapshot

/** Constants transcribed from approved text, not Android classes. */
object Contract {
    const val ZIP = 64L * 1024 * 1024
    const val TOTAL = 128L * 1024 * 1024
    const val ONE = 1024L * 1024
    const val REFERENCES = 32L * 1024 * 1024
    const val DEPTH = 32
    const val STRING = 65536
    const val MEMBERS = 256
    const val ARRAY = 100000
    const val SAFE = 9007199254740991L
    val paths = listOf("manifest.json", "data/territories.jsonl", "data/observers.jsonl",
        "data/physical-objects.jsonl", "data/apiaries.jsonl", "data/hollows.jsonl",
        "data/log-hives.jsonl", "data/physical-object-sequences.jsonl", "data/physical-object-media.jsonl",
        "data/observation-points.jsonl", "data/bees.jsonl", "data/flight-cycles.jsonl",
        "data/observation-point-weather.jsonl", "data/observation-point-attachments.jsonl",
        "settings/portable.json", "settings/map-coverage.jsonl", "references/media-blobs.jsonl")
    val keys = mapOf("data/apiaries.jsonl" to listOf("physicalObjectId"),
        "data/hollows.jsonl" to listOf("physicalObjectId"), "data/log-hives.jsonl" to listOf("physicalObjectId"),
        "data/physical-object-sequences.jsonl" to listOf("territoryId", "objectType"),
        "data/observation-point-weather.jsonl" to listOf("observationPointId"),
        "settings/map-coverage.jsonl" to listOf("territoryId"), "references/media-blobs.jsonl" to listOf("sha256"))
    fun entryLimit(path: String): Long = when (path) {
        "manifest.json", "settings/portable.json" -> ONE
        "references/media-blobs.jsonl" -> REFERENCES
        else -> TOTAL
    }
}

data class Issue(val code: String, val scope: String = "", val message: String = code,
    val observed: String? = null, val expected: String? = null)
class CheckFailure(val issue: Issue) : Exception(issue.code)
fun reject(code: String, scope: String = "", message: String = code,
    observed: Any? = null, expected: Any? = null): Nothing =
    throw CheckFailure(Issue(code, scope, message, observed?.toString(), expected?.toString()))

/** Used by actual decompressed streams as well as bounded boundary adapters. */
class Budget(private val maximum: Long, private val scope: String) {
    var count = 0L; private set
    fun add(bytes: Long) {
        if (bytes < 0 || bytes > maximum - count)
            reject("SNAPSHOT_LIMIT_EXCEEDED", scope, observed =
                if (bytes > Long.MAX_VALUE - count) Long.MAX_VALUE else count + bytes, expected = maximum)
        count += bytes
    }
}

/**
 * The two supported Snapshot V1 evidence tuples (wire schema §7.1).
 *
 * Transcribed from the contract text only; no production Kotlin is imported or copied.
 */
enum class EvidenceProfile(val token: String, val profile: String, val repositoryEvidenceRequired: Boolean) {
    METADATA_ONLY("METADATA_ONLY/NO_MEDIA_EVIDENCE/COMPLETE", "METADATA_ONLY", false),
    FULL_LOCAL_VERIFIED("FULL/LOCAL_VERIFIED/COMPLETE", "FULL", true),
    ;

    companion object {
        /** Null for every unknown token and for every unsupported cross-combination. */
        fun parse(profile: String?, evidencePolicy: String?, creationResult: String?): EvidenceProfile? =
            entries.firstOrNull { it.token == "$profile/$evidencePolicy/$creationResult" }
    }
}

data class VerificationReport(val inputPath: String, var fileSize: Long? = null,
    var actualWholeSha256: String? = null, var filenameSnapshotId: String? = null,
    var filenameWholeSha256: String? = null, var verifiedEntries: Int = 0,
    val issues: MutableList<Issue> = mutableListOf()) {
    /** The declared, immutable manifest evidence of this snapshot; never inferred from the ZIP only. */
    var evidenceProfile: EvidenceProfile? = null
    var snapshotProfile: String? = null
    var evidencePolicy: String? = null
    var creationResult: String? = null
    var snapshotFormatVersion: Long? = null

    /**
     * True when the snapshot declares the local full-evidence profile: standalone inspection can prove
     * the structure, but only a repository context can prove the declared media evidence.
     */
    val repositoryEvidenceRequired: Boolean get() = evidenceProfile?.repositoryEvidenceRequired == true

    val verdict: String get() = when {
        issues.isNotEmpty() -> "FAIL"
        repositoryEvidenceRequired -> "REPOSITORY_EVIDENCE_REQUIRED"
        else -> "PASS"
    }
}
