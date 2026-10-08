package pcsnapshot

import com.google.gson.JsonObject
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** Raw JSONL framing, normative schemas and metadata-only reachability. */
internal object WireRecords {
    fun validate(files: Map<String, Path>, report: VerificationReport, version: Long) {
        val graph = mutableMapOf<String, List<JsonObject>>()
        for (path in Contract.paths.filter { it.startsWith("data/") }) {
            val found = mutableListOf<JsonObject>()
            orderedRecords(files.getValue(path), path, version = version) { record ->
                found += record
            }
            graph[path] = found
        }
        GraphRules.validate(graph)
        val ids = graph.mapValues { (_,rows) -> rows.filter { it.has("id") }.map { string(it,"id","") }.toSet() }
        val portablePath = "settings/portable.json"
        val portable = objectValue(Files.readAllBytes(files.getValue(portablePath)), portablePath)
        closed(portable,setOf("currentTerritoryId","currentObserverId"),portablePath)
        for ((field, collection) in listOf("currentTerritoryId" to "data/territories.jsonl",
            "currentObserverId" to "data/observers.jsonl")) {
            val value = portable.get(field) ?: reject("PORTABLE_SETTINGS_INVALID", portablePath)
            if (!value.isJsonNull) {
                val id = string(portable, field, portablePath); RecordSchema.uuid(id, portablePath)
                if (id !in ids.getValue(collection)) reject("LOGICAL_STATE_INCONSISTENT", portablePath)
            }
        }
        val coveragePath = "settings/map-coverage.jsonl"
        orderedRecords(files.getValue(coveragePath), coveragePath) { record ->
            closed(record,setOf("territoryId","encoded"),coveragePath)
            if (string(record, "territoryId", coveragePath) !in ids.getValue("data/territories.jsonl"))
                reject("LOGICAL_STATE_INCONSISTENT", coveragePath)
            Coverage.validate(string(record,"encoded",coveragePath),coveragePath)
        }

        val refsPath = "references/media-blobs.jsonl"
        var count = 0L
        val actual = mutableMapOf<String, Blob>()
        orderedRecords(files.getValue(refsPath), refsPath, canonical = true) { record ->
            closed(record,setOf("sha256","byteSize","canonicalExtension"),refsPath)
            val sha = string(record, "sha256", refsPath)
            RecordSchema.sha(sha,refsPath)
            val size = record.get("byteSize")
            if (size == null || !size.isJsonPrimitive || !size.asJsonPrimitive.isNumber ||
                (size.asString.toLongOrNull() ?: -1) <= 0) reject("MEDIA_REFERENCE_SET_MISMATCH", refsPath)
            if (string(record, "canonicalExtension", refsPath) !in setOf("jpg", "mp4", "bin"))
                reject("MEDIA_REFERENCE_INVALID", refsPath)
            actual[sha] = Blob(size.asLong,string(record,"canonicalExtension",refsPath))
            count++
        }
        val manifest = objectValue(Files.readAllBytes(files.getValue("manifest.json")), "manifest.json", true)
        val expected = manifest.getAsJsonObject("mediaReferences").get("recordCount").asLong
        if (count != expected) reject("MEDIA_REFERENCE_COUNT_MISMATCH", refsPath, observed = count, expected = expected)
        if (actual != derive(graph))
            reject("MEDIA_REFERENCE_SET_MISMATCH", refsPath)
    }

    private data class Blob(val size: Long,val extension: String)
    private fun derive(graph: Map<String,List<JsonObject>>): Map<String,Blob> {
        val result = mutableMapOf<String,Blob>()
        for (path in listOf("data/physical-object-media.jsonl","data/observation-point-attachments.jsonl")) {
            graph.getValue(path).forEach { row ->
                val sha = if (row.has("sha256")) RecordSchema.nullableString(row,"sha256") else null
                val size = if (row.has("byteSize")) RecordSchema.nullableLong(row,"byteSize") else null
                if (sha != null && size != null && size in 1..9007199254740991L) {
                    val ext = mimeExtension(RecordSchema.nullableString(row,"mimeType"))
                    val previous = result[sha]
                    if (previous != null && (previous.size != size || (previous.extension != "bin" && ext != "bin" && previous.extension != ext)))
                        reject("MEDIA_IDENTITY_CONFLICT",path)
                    result[sha] = Blob(size,if (previous != null && previous.extension != "bin") previous.extension else ext)
                }
            }
        }
        return result
    }
    private fun mimeExtension(hint: String?): String {
        val normalized = hint?.trim { it in MIME_TRIM_CHARS }?.lowercase(Locale.ROOT)
        return when (normalized) {
            "image/jpeg" -> "jpg"
            "video/mp4" -> "mp4"
            else -> "bin"
        }
    }

    private val MIME_TRIM_CHARS = setOf(
        '\u0009', '\u000A', '\u000B', '\u000C', '\u000D',
        '\u001C', '\u001D', '\u001E', '\u001F', '\u0020',
        '\u00A0', '\u1680', '\u2000', '\u2001', '\u2002', '\u2003',
        '\u2004', '\u2005', '\u2006', '\u2007', '\u2008', '\u2009',
        '\u200A', '\u2028', '\u2029', '\u202F', '\u205F', '\u3000'
    )
    private fun closed(row: JsonObject,fields: Set<String>,scope: String) {
        if (row.keySet() != fields) reject("WIRE_SCHEMA_INVALID",scope)
    }

    private fun orderedRecords(file: Path, scope: String, canonical: Boolean = false, version: Long = 1L,
        consume: (JsonObject) -> Unit) {
        var previous: List<String>? = null
        forEachRecord(file, scope) { bytes ->
            val record = objectValue(bytes, scope, canonical)
            if (scope.startsWith("data/")) try { RecordSchema.validate(record,scope,version) }
            catch (e: CheckFailure) { throw CheckFailure(e.issue.copy(scope = scope)) }
            if (canonical && !bytes.contentEquals(SafeJson.encode(record))) reject("JSONL_NOT_CANONICAL", scope)
            val keys = Contract.keys[scope] ?: listOf("id")
            val current = keys.map { key ->
                string(record, key, scope).also { if (key != "sha256" && key != "objectType") RecordSchema.uuid(it, scope) }
            }
            previous?.let { before ->
                val comparison = compareTuple(before, current)
                if (comparison == 0) reject("DUPLICATE_RECORD_KEY", scope)
                if (comparison > 0) reject("RECORD_ORDER_INVALID", scope)
            }
            previous = current; consume(record)
        }
    }

    private fun forEachRecord(file: Path, scope: String, consume: (ByteArray) -> Unit) {
        Files.newInputStream(file).buffered().use { input ->
            val record = ByteArrayOutputStream()
            var budget = Budget(Contract.ONE, "$scope:record")
            while (true) {
                val next = input.read(); if (next < 0) break
                budget.add(1)
                if (next == 10) {
                    if (record.size() == 0) reject("JSONL_EMPTY_RECORD", scope)
                    consume(record.toByteArray()); record.reset()
                    budget = Budget(Contract.ONE, "$scope:record")
                } else record.write(next)
            }
            if (budget.count != 0L) reject("JSONL_MISSING_FINAL_LF", scope)
        }
    }

    private fun objectValue(bytes: ByteArray, scope: String, canonical: Boolean = false): JsonObject {
        val value = try { SafeJson.parse(bytes, canonical) } catch (e: CheckFailure) {
            throw CheckFailure(e.issue.copy(scope = scope))
        }
        if (!value.isJsonObject) reject("JSON_OBJECT_REQUIRED", scope)
        return value.asJsonObject
    }
    private fun string(o: JsonObject, key: String, scope: String): String {
        val value = o.get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString)
            reject("RECORD_KEY_INVALID", scope)
        return value.asString
    }
    private fun compareTuple(a: List<String>, b: List<String>): Int {
        for (index in a.indices) { val result = a[index].compareTo(b[index]); if (result != 0) return result }
        return 0
    }
}
