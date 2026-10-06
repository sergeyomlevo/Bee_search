package pcsnapshot

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Independent wire fixture builder. It deliberately does not use production JSON code. */
object Fixture {
    const val snapshotId = "11111111-1111-4111-8111-111111111111"
    const val repositoryId = "22222222-2222-4222-8222-222222222222"
    private val paths = Contract.paths
    private val emptyCollections = paths.filter { it.startsWith("data/") || it == "settings/map-coverage.jsonl" || it == "references/media-blobs.jsonl" }

    fun validBytes(extra: Map<String, ByteArray> = emptyMap()): ByteArray {
        val files = linkedMapOf<String, ByteArray>()
        emptyCollections.forEach { files[it] = extra[it] ?: ByteArray(0) }
        files["settings/portable.json"] = extra["settings/portable.json"] ?: "{\"currentObserverId\":null,\"currentTerritoryId\":null}".toByteArray()
        files["manifest.json"] = manifest(files)
        return zip(files)
    }

    fun write(dir: Path, name: String = "snapshot.zip", bytes: ByteArray = validBytes()): Path {
        Files.createDirectories(dir)
        val out = dir.resolve(name)
        Files.write(out, bytes)
        return out
    }

    fun canonicalCopy(dir: Path, bytes: ByteArray = validBytes()): Path {
        val sha = sha256(bytes)
        return write(dir, "snapshot-$snapshotId-$sha.zip", bytes)
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun zip(entries: Map<String, ByteArray>, comment: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            if (comment != null) zip.setComment(comment)
            entries.toSortedMap().forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** A genuinely compressed archive whose expansion exceeds the total budget. */
    fun zipBombBytes(expandedBytes: Int = (Contract.TOTAL + 1).toInt()): ByteArray {
        val block = "{}\n".toByteArray()
        val files = Contract.paths.associateWith { ByteArray(0) }.toMutableMap()
        val manifest = repeatedManifest(expandedBytes, block)
        files["manifest.json"] = manifest
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setLevel(9)
            files.toSortedMap().forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                if (name == "data/territories.jsonl") {
                    var remaining = expandedBytes
                    while (remaining > 0) {
                        val count = minOf(remaining, block.size)
                        zip.write(block, 0, count)
                        remaining -= count
                    }
                } else {
                    zip.write(bytes)
                }
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun repeatedManifest(size: Int, block: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        var remaining = size
        while (remaining > 0) {
            val count = minOf(remaining, block.size)
            digest.update(block, 0, count)
            remaining -= count
        }
        val descriptors = Contract.paths.filter { it != "manifest.json" }.sorted().joinToString(",") { path ->
            val bytes = if (path == "data/territories.jsonl") size.toLong() to digest.digest().joinToString("") { "%02x".format(it) } else 0L to sha256(ByteArray(0))
            "{\"byteSize\":${bytes.first},\"path\":\"$path\",\"sha256\":\"${bytes.second}\"}"
        }
        return ("{\"createdAtEpochMs\":0,\"creationIssues\":[],\"creationResult\":\"COMPLETE\",\"entries\":[" + descriptors +
            "],\"evidencePolicy\":\"NO_MEDIA_EVIDENCE\",\"mediaReferences\":{\"path\":\"references/media-blobs.jsonl\",\"recordCount\":0}," +
            "\"repositoryId\":\"$repositoryId\",\"snapshotFormat\":\"beesearch-snapshot\",\"snapshotFormatVersion\":1,\"snapshotId\":\"$snapshotId\",\"snapshotProfile\":\"METADATA_ONLY\",\"variant\":\"Dev\"}").toByteArray()
    }

    fun replaceAscii(bytes: ByteArray, old: String, new: String): ByteArray {
        require(old.length == new.length)
        val out = bytes.copyOf()
        val from = old.toByteArray(); val to = new.toByteArray()
        var i = 0
        while (i <= out.size - from.size) {
            if (out.copyOfRange(i, i + from.size).contentEquals(from)) {
                to.copyInto(out, i); i += to.size
            } else i++
        }
        return out
    }

    fun entries(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(bytes.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                result[entry.name] = input.readBytes()
            }
        }
        return result
    }

    /** Changes only a local-header name, preserving its fixed length and central directory. */
    fun mutateFirstLocalName(bytes: ByteArray, oldName: String, newName: String): ByteArray {
        require(oldName.length == newName.length)
        val out = bytes.copyOf()
        val signature = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
        val oldBytes = oldName.toByteArray()
        val newBytes = newName.toByteArray()
        var at = -1
        for (i in 0..out.size - 34 - oldBytes.size) {
            if (out.copyOfRange(i, i + 4).contentEquals(signature) &&
                out.copyOfRange(i + 30, i + 30 + oldBytes.size).contentEquals(oldBytes)) {
                at = i + 30
                break
            }
        }
        require(at >= 0)
        newBytes.copyInto(out, at)
        return out
    }

    fun manifest(files: Map<String, ByteArray>, referenceCount: Long? = null): ByteArray {
        val refs = files["references/media-blobs.jsonl"] ?: ByteArray(0)
        val refsCount = referenceCount ?: refs.count { it == '\n'.code.toByte() }.toLong()
        val descriptors = files.filterKeys { it != "manifest.json" }.toSortedMap().entries.joinToString(",") {
            "{\"byteSize\":${it.value.size},\"path\":\"${it.key}\",\"sha256\":\"${sha256(it.value)}\"}"
        }
        return ("{" +
            "\"createdAtEpochMs\":0," +
            "\"creationIssues\":[]," +
            "\"creationResult\":\"COMPLETE\"," +
            "\"entries\":[" + descriptors + "]," +
            "\"evidencePolicy\":\"NO_MEDIA_EVIDENCE\"," +
            "\"mediaReferences\":{\"path\":\"references/media-blobs.jsonl\",\"recordCount\":" + refsCount + "}," +
            "\"repositoryId\":\"$repositoryId\"," +
            "\"snapshotFormat\":\"beesearch-snapshot\"," +
            "\"snapshotFormatVersion\":1," +
            "\"snapshotId\":\"$snapshotId\"," +
            "\"snapshotProfile\":\"METADATA_ONLY\"," +
            "\"variant\":\"Dev\"}").toByteArray()
    }
}
