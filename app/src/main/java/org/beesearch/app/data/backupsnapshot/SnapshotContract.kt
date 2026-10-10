package org.beesearch.app.data.backupsnapshot

internal enum class SnapshotError {
    INVALID_FORMAT, LOGICAL_STATE_INCONSISTENT, SNAPSHOT_LIMIT_EXCEEDED, INVALID_ZIP,
    ENTRY_DIGEST_MISMATCH, WHOLE_DIGEST_MISMATCH, SNAPSHOT_ID_CONFLICT, READ_FAILED
}

internal class SnapshotException(
    val error: SnapshotError,
    val category: String = "",
    val observed: Long? = null,
    val limit: Long? = null,
    cause: Throwable? = null,
) : Exception(category.ifEmpty { error.name }, cause)

internal data class SnapshotLimits(
    val zipBytes: Long = 64L * 1024 * 1024,
    val totalBytes: Long = 128L * 1024 * 1024,
    val manifestBytes: Long = 1L * 1024 * 1024,
    val portableBytes: Long = 1L * 1024 * 1024,
    val referencesBytes: Long = 32L * 1024 * 1024,
    val recordBytes: Long = 1L * 1024 * 1024,
    val depth: Int = 32,
    val stringUnits: Int = 65536,
    val objectMembers: Int = 256,
    val arrayEntries: Int = 100000,
)

internal object SnapshotContract {
    const val FORMAT = "beesearch-snapshot"
    const val VERSION = 3
    val supportedVersions = setOf(1, 2, 3)
    val paths = listOf(
        "manifest.json", "data/territories.jsonl", "data/observers.jsonl",
        "data/physical-objects.jsonl", "data/apiaries.jsonl", "data/hollows.jsonl",
        "data/log-hives.jsonl", "data/physical-object-sequences.jsonl",
        "data/physical-object-media.jsonl", "data/observation-points.jsonl",
        "data/bees.jsonl", "data/flight-cycles.jsonl",
        "data/observation-point-weather.jsonl", "data/observation-point-attachments.jsonl",
        "settings/portable.json", "settings/map-coverage.jsonl", "references/media-blobs.jsonl",
    )

    fun entryLimit(path: String, limits: SnapshotLimits): Long = when (path) {
        "manifest.json" -> limits.manifestBytes
        "settings/portable.json" -> limits.portableBytes
        "references/media-blobs.jsonl" -> limits.referencesBytes
        else -> limits.totalBytes
    }
}
