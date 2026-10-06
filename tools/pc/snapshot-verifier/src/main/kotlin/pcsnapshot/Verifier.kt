package pcsnapshot

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.io.IOException
import java.util.zip.ZipException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Independent, read-only verifier for the Repository Snapshot V1 wire format. */
class Verifier {
    fun verify(input: Path): VerificationReport {
        val report = VerificationReport(input.toString())
        val scratch = Files.createTempDirectory("bee-snapshot-verify-")
        try {
            val fixed = scratch.resolve("fixed.zip")
            copyAndHash(input, fixed, report)
            if (report.issues.isNotEmpty()) return report
            recheckSource(input, report.fileSize!!, report.actualWholeSha256!!, report)
            parseFilename(input.fileName.toString(), report.actualWholeSha256!!, report)
            if (report.issues.isNotEmpty()) return report

            val first = readZipFile(fixed, scratch, report)
            val second = try { readZipStream(fixed, report) } catch (e: CheckFailure) {
                report.issues += Issue("ZIP_VIEW_MISMATCH"); throw e
            } catch (e: ZipException) {
                report.issues += Issue("ZIP_VIEW_MISMATCH"); throw e
            }
            compareViews(first, second, report)
            if (report.issues.isNotEmpty()) return report
            validateManifest(first, report)
            if (report.issues.isNotEmpty()) return report
            validateEntries(first, report)
        } catch (e: CheckFailure) {
            report.issues += e.issue
        } catch (_: ZipException) {
            report.issues += Issue("MALFORMED_ZIP")
        } catch (_: IOException) {
            report.issues += Issue("READ_ERROR")
        } catch (_: SecurityException) {
            report.issues += Issue("PERMISSION_DENIED")
        } catch (_: Exception) {
            report.issues += Issue("INTERNAL_VERIFIER_ERROR")
        } finally {
            scratch.toFile().deleteRecursively()
        }
        return report
    }

    private data class Entry(val name: String, val size: Long, val sha: String, val spool: Path? = null)
    private data class View(val entries: Map<String, Entry>, val total: Long)

    private fun copyAndHash(source: Path, target: Path, report: VerificationReport) {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        val budget = Budget(Contract.ZIP, "zip")
        report.fileSize = Files.size(source)
        Files.newInputStream(source).use { input -> Files.newOutputStream(target).use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                budget.add(n.toLong()); size = budget.count
                digest.update(buffer, 0, n); output.write(buffer, 0, n)
            }
        }}
        report.fileSize = size
        report.actualWholeSha256 = hex(digest.digest())
    }

    private fun recheckSource(source: Path, expectedSize: Long, expectedSha: String, report: VerificationReport) {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        Files.newInputStream(source).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; size += n; if (size > Contract.ZIP) break; digest.update(buffer, 0, n) }
        }
        if (size != expectedSize || hex(digest.digest()) != expectedSha)
            report.issues += Issue("SOURCE_MUTATED_DURING_READ", scope = "input", message = "SOURCE_MUTATED_DURING_READ")
    }

    private fun parseFilename(filename: String, sha: String, report: VerificationReport) {
        val match = Regex("^snapshot-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})-([0-9a-f]{64})\\.zip$").matchEntire(filename)
        if (match == null) { report.issues += Issue("SNAPSHOT_FILENAME_INVALID"); return }
        try { report.filenameSnapshotId = UUID.fromString(match.groupValues[1]).toString() }
        catch (_: IllegalArgumentException) { report.issues += Issue("SNAPSHOT_FILENAME_INVALID") }
        report.filenameWholeSha256 = match.groupValues[2].lowercase()
        if (report.filenameWholeSha256 != sha) report.issues += Issue("SNAPSHOT_FILENAME_SHA_MISMATCH")
    }

    private fun readZipFile(file: Path, scratch: Path, report: VerificationReport): View {
        val result = LinkedHashMap<String, Entry>(); val totalBudget = Budget(Contract.TOTAL, "entries")
        ZipFile(file.toFile()).use { zip ->
            val all = zip.entries()
            while (all.hasMoreElements()) {
                val ze = all.nextElement()
                checkPath(ze, result)
                val name = ze.name
                val spool = scratch.resolve("entry-${result.size}.bin")
                val digest = MessageDigest.getInstance("SHA-256"); val entryBudget = Budget(Contract.entryLimit(name), name)
                zip.getInputStream(ze).use { input -> Files.newOutputStream(spool).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        totalBudget.add(n.toLong()); entryBudget.add(n.toLong())
                        digest.update(buffer, 0, n); output.write(buffer, 0, n)
                    }
                }}
                val size = entryBudget.count
                if (ze.size >= 0 && ze.size != size) report.issues += Issue("ZIP_SIZE_MISMATCH", name)
                if (ze.crc >= 0 && ze.crc != crc32(spool)) report.issues += Issue("ZIP_CRC_MISMATCH", name)
                result[name] = Entry(name, size, hex(digest.digest()), spool)
            }
        }
        return View(result, totalBudget.count)
    }

    private fun readZipStream(file: Path, report: VerificationReport): View {
        val result = LinkedHashMap<String, Entry>(); val totalBudget = Budget(Contract.TOTAL, "entries")
        ZipInputStream(BufferedInputStream(Files.newInputStream(file))).use { zip ->
            while (true) {
                val ze = zip.nextEntry ?: break
                checkPath(ze, result)
                val digest = MessageDigest.getInstance("SHA-256"); val entryBudget = Budget(Contract.entryLimit(ze.name), ze.name)
                val buffer = ByteArray(64 * 1024)
                val crc = java.util.zip.CRC32()
                while (true) { val n = zip.read(buffer); if (n < 0) break; totalBudget.add(n.toLong()); entryBudget.add(n.toLong()); digest.update(buffer, 0, n); crc.update(buffer, 0, n) }
                val size = entryBudget.count
                if (ze.size >= 0 && ze.size != size) report.issues += Issue("ZIP_SIZE_MISMATCH", ze.name)
                if (ze.crc >= 0 && ze.crc != crc.value) report.issues += Issue("ZIP_CRC_MISMATCH", ze.name)
                result[ze.name] = Entry(ze.name, size, hex(digest.digest()))
                zip.closeEntry()
            }
        }
        return View(result, totalBudget.count)
    }

    private fun compareViews(a: View, b: View, report: VerificationReport) {
        if (a.total != b.total || a.entries.size != b.entries.size || a.entries.keys != b.entries.keys)
            report.issues += Issue("ZIP_VIEW_MISMATCH")
        for (name in a.entries.keys.intersect(b.entries.keys)) {
            val x = a.entries.getValue(name); val y = b.entries.getValue(name)
            if (x.size != y.size || x.sha != y.sha) report.issues += Issue("ZIP_VIEW_MISMATCH", name)
        }
    }

    private fun validateManifest(view: View, report: VerificationReport) {
        val entry = view.entries["manifest.json"] ?: return
        val bytes = Files.readAllBytes(entry.spool!!)
        val obj = try { SafeJson.parse(bytes, canonical = true) } catch (_: Exception) { report.issues += Issue("MANIFEST_INVALID"); return }
        if (!bytes.contentEquals(SafeJson.encode(obj))) report.issues += Issue("MANIFEST_NOT_CANONICAL")
        if (!obj.isJsonObject) { report.issues += Issue("MANIFEST_INVALID"); return }
        val o = obj.asJsonObject
        requiredKeys(o, setOf("snapshotFormat", "snapshotFormatVersion", "snapshotId", "repositoryId", "variant", "createdAtEpochMs", "snapshotProfile", "creationResult", "evidencePolicy", "entries", "mediaReferences", "creationIssues"), "manifest", report)
        text(o, "snapshotFormat")?.let { if (it != "beesearch-snapshot") report.issues += Issue("MANIFEST_INVALID") } ?: run { report.issues += Issue("MANIFEST_INVALID") }
        if (number(o, "snapshotFormatVersion") != 1L) report.issues += Issue("UNSUPPORTED_FORMAT_VERSION")
        val id = text(o, "snapshotId"); val repo = text(o, "repositoryId")
        if (id == null || repo == null || !validUuid(id) || !validUuid(repo)) report.issues += Issue("MANIFEST_INVALID")
        if (id != report.filenameSnapshotId) report.issues += Issue("SNAPSHOT_ID_MISMATCH")
        if (text(o, "variant") !in setOf("Dev", "Beta", "Stable")) report.issues += Issue("MANIFEST_INVALID")
        val created = number(o, "createdAtEpochMs"); if (created == null || created < 0) report.issues += Issue("MANIFEST_INVALID")
        if (text(o, "snapshotProfile") != "METADATA_ONLY" || text(o, "evidencePolicy") != "NO_MEDIA_EVIDENCE" || text(o, "creationResult") != "COMPLETE") report.issues += Issue("MANIFEST_UNSUPPORTED_PROFILE")
        val issues = o.get("creationIssues"); if (issues == null || !issues.isJsonArray || issues.asJsonArray.size() != 0) report.issues += Issue("MANIFEST_INVALID")
        val descriptors = o.get("entries"); if (descriptors == null || !descriptors.isJsonArray || descriptors.asJsonArray.size() != 16) report.issues += Issue("MANIFEST_INVALID")
        else validateDescriptors(descriptors.asJsonArray, view, report)
        val refs = o.get("mediaReferences")
        if (refs == null || !refs.isJsonObject) report.issues += Issue("MANIFEST_INVALID") else {
            requiredKeys(refs.asJsonObject, setOf("path", "recordCount"), "manifest.mediaReferences", report)
            if (text(refs.asJsonObject, "path") != "references/media-blobs.jsonl" ||
                (number(refs.asJsonObject, "recordCount") ?: -1L) < 0) report.issues += Issue("MANIFEST_INVALID")
        }
    }

    private fun validateDescriptors(array: com.google.gson.JsonArray, view: View, report: VerificationReport) {
        var prior = ""
        val seen = mutableSetOf<String>()
        for (e in array) {
            if (!e.isJsonObject) { report.issues += Issue("MANIFEST_INVALID"); continue }
            val o = e.asJsonObject; requiredKeys(o, setOf("path", "byteSize", "sha256"), "manifest.entries", report)
            val path = text(o, "path"); val size = number(o, "byteSize"); val sha = text(o, "sha256")
            if (path == null || size == null || sha == null || path == "manifest.json" || !validSha(sha) || size < 0 || path <= prior || !seen.add(path) || path !in Contract.paths) report.issues += Issue("MANIFEST_INVALID")
            if (path != null) { prior = path; val actual = view.entries[path]; if (actual == null || actual.size != size || actual.sha != sha) report.issues += Issue("ENTRY_DIGEST_MISMATCH", path) }
        }
        if (seen != Contract.paths.filter { it != "manifest.json" }.toSet()) report.issues += Issue("MANIFEST_INVALID")
    }

    private fun validateEntries(view: View, report: VerificationReport) {
        for (path in Contract.paths) if (!view.entries.containsKey(path)) report.issues += Issue("MISSING_ENTRY", path)
        for (path in view.entries.keys) if (path !in Contract.paths) report.issues += Issue("UNEXPECTED_ENTRY", path)
        if (report.issues.isNotEmpty()) return
        WireRecords.validate(view.entries.mapValues { it.value.spool!! }, report)
        if (report.issues.isEmpty()) report.verifiedEntries = view.entries.size
    }
    private fun checkPath(e: ZipEntry, current: Map<String, Entry>) { val n = e.name; if (e.isDirectory || n.isEmpty() || n.contains('\\') || n.startsWith('/') || n.contains("../") || n == ".." || n.split('/').any { it == ".." }) reject("ZIP_PATH_INVALID", n); if (n !in Contract.paths) reject("UNEXPECTED_ENTRY", n); if (current.containsKey(n)) reject("DUPLICATE_ENTRY", n) }
    private fun requiredKeys(o: JsonObject, expected: Set<String>, scope: String, report: VerificationReport) { if (o.keySet() != expected) report.issues += Issue("JSON_SCHEMA_INVALID", scope) }
    private fun text(o: JsonObject, k: String): String? = o.get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    private fun number(o: JsonObject, k: String): Long? = o.get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asString?.toLongOrNull()
    private fun validUuid(s: String) = runCatching { UUID.fromString(s).toString() == s }.getOrDefault(false)
    private fun validSha(s: String) = s.length == 64 && s.all { it in "0123456789abcdef" }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun crc32(path: Path): Long { val c = java.util.zip.CRC32(); Files.newInputStream(path).use { i -> val b=ByteArray(65536); while(true){val n=i.read(b);if(n<0)break;c.update(b,0,n)}}; return c.value }
}
