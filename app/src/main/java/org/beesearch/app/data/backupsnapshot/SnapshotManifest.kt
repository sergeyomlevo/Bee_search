package org.beesearch.app.data.backupsnapshot

import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal data class SnapshotIdentity(
    val snapshotId: UUID,
    val repositoryId: UUID,
    val variant: String,
    val createdAtEpochMs: Long,
)

internal data class SnapshotEntryDescriptor(val path: String, val byteSize: Long, val sha256: String)

internal data class SnapshotMetrics(
    val recordCounts: Map<String, Long>,
    val entryBytes: Map<String, Long>,
    val maxRecordBytes: Long,
    val zipBytes: Long,
    val totalBytes: Long,
    val maxRecordBytesByEntry: Map<String, Long> = emptyMap(),
)

internal data class ValidatedSnapshot(
    val identity: SnapshotIdentity,
    val wholeSha256: String,
    val byteSize: Long,
    val metrics: SnapshotMetrics,
)

internal object SnapshotManifest {
    private const val PROFILE = "METADATA_ONLY"
    private const val RESULT = "COMPLETE"
    private const val EVIDENCE = "NO_MEDIA_EVIDENCE"

    fun encode(identity: SnapshotIdentity, descriptors: List<SnapshotEntryDescriptor>, mediaReferenceCount: Long = 0): ByteArray {
        validateIdentity(identity)
        if (mediaReferenceCount < 0) invalid()
        val sorted = descriptors.sortedBy { it.path }
        val json = buildJsonObject {
            put("snapshotFormat", SnapshotContract.FORMAT)
            put("snapshotFormatVersion", SnapshotContract.VERSION)
            put("snapshotId", identity.snapshotId.toString())
            put("repositoryId", identity.repositoryId.toString())
            put("variant", identity.variant)
            put("createdAtEpochMs", identity.createdAtEpochMs)
            put("snapshotProfile", PROFILE)
            put("creationResult", RESULT)
            put("evidencePolicy", EVIDENCE)
            put("entries", buildJsonArray {
                sorted.forEach { descriptor -> add(buildJsonObject {
                    put("path", descriptor.path)
                    put("byteSize", descriptor.byteSize)
                    put("sha256", descriptor.sha256)
                }) }
            })
            put("creationIssues", buildJsonArray {})
            put("mediaReferences", buildJsonObject {
                put("path", "references/media-blobs.jsonl")
                put("recordCount", mediaReferenceCount)
            })
        }
        return SnapshotJson.encode(json)
    }

    fun decode(bytes: ByteArray, limits: SnapshotLimits = SnapshotLimits()): Triple<SnapshotIdentity, List<SnapshotEntryDescriptor>, Long> {
        val root = SnapshotJson.parse(bytes, integerOnly = true, limits = limits) as? JsonObject ?: invalid()
        val required = setOf("snapshotFormat", "snapshotFormatVersion", "snapshotId", "repositoryId", "variant", "createdAtEpochMs", "snapshotProfile", "creationResult", "evidencePolicy", "entries", "creationIssues", "mediaReferences")
        if (root.keys != required) invalid()
        if (root.string("snapshotFormat") != SnapshotContract.FORMAT || root.long("snapshotFormatVersion") != SnapshotContract.VERSION.toLong()) invalid()
        if (root.string("snapshotProfile") != PROFILE || root.string("creationResult") != RESULT || root.string("evidencePolicy") != EVIDENCE) invalid()
        val identity = SnapshotIdentity(uuid(root.string("snapshotId")), uuid(root.string("repositoryId")), root.string("variant"), root.long("createdAtEpochMs"))
        validateIdentity(identity)
        if (root["creationIssues"] !is JsonArray || root["creationIssues"]!!.jsonArray.isNotEmpty()) invalid()
        val descriptors = root["entries"]?.jsonArray?.map { element ->
            val obj = element.jsonObject
            if (obj.keys != setOf("path", "byteSize", "sha256")) invalid()
            SnapshotEntryDescriptor(obj.string("path"), obj.long("byteSize"), obj.string("sha256")).also { validateDescriptor(it) }
        } ?: invalid()
        if (descriptors.map { it.path }.toSet().size != descriptors.size) invalid()
        if (descriptors.map { it.path } != descriptors.map { it.path }.sorted()) invalid()
        val references = root["mediaReferences"] as? JsonObject ?: invalid()
        if (references.keys != setOf("path", "recordCount") || references.string("path") != "references/media-blobs.jsonl") invalid()
        val count = references.long("recordCount")
        if (count < 0) invalid()
        return Triple(identity, descriptors.sortedBy { it.path }, count)
    }

    private fun validateIdentity(identity: SnapshotIdentity) {
        if (identity.variant !in setOf("Stable", "Beta", "Dev") || identity.createdAtEpochMs < 0L) invalid()
    }
    private fun validateDescriptor(descriptor: SnapshotEntryDescriptor) {
        try { org.beesearch.app.data.zip.validateZipRelativePath(descriptor.path) } catch (_: Exception) { invalid() }
        if (descriptor.byteSize < 0L || !descriptor.sha256.matches(Regex("[0-9a-f]{64}"))) invalid()
    }
    private fun uuid(value: String): UUID = try { UUID.fromString(value).also { if (it.toString() != value) invalid() } } catch (_: IllegalArgumentException) { invalid() }
    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.takeUnless { !it.isString }?.content ?: invalid()
    private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.takeIf { !it.isString }?.longOrNull ?: invalid()
    private fun invalid(): Nothing = throw SnapshotException(SnapshotError.INVALID_FORMAT, "MANIFEST")
}
