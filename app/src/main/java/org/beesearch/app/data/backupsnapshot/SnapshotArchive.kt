package org.beesearch.app.data.backupsnapshot

import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.beesearch.app.data.zip.ZipEntryNames
import org.beesearch.app.data.zip.validateZipRelativePath

/** Fixed private ZIP, bounded streaming spools, integrity before semantics. */
internal class SnapshotArchive(private val limits: SnapshotLimits = SnapshotLimits()) {
    fun build(target: File, identity: SnapshotIdentity, domainEntries: SnapshotDomainEntries,
        check: () -> Unit = {}): ValidatedSnapshot {
        val expected = SnapshotContract.paths.drop(1)
        if (domainEntries.records.keys != expected.filter { it.endsWith("jsonl") && !it.startsWith("references/") }.toSet()) invalid("ENTRY_SET")
        target.parentFile?.mkdirs()
        val scratch = Files.createTempDirectory(target.absoluteFile.parentFile!!.toPath(), "snapshot-build-").toFile()
        try {
            var total = 0L
            val files = linkedMapOf<String, File>()
            for ((index, path) in expected.withIndex()) {
                check()
                val file = File(scratch, "entry-$index")
                var written = 0L
                file.outputStream().buffered().use { output ->
                    fun write(bytes: ByteArray) {
                        check()
                        written = addBound(written, bytes.size.toLong(), SnapshotContract.entryLimit(path, limits), path)
                        total = addBound(total, bytes.size.toLong(), limits.totalBytes, "totalBytes")
                        output.write(bytes)
                    }
                    if (path == "settings/portable.json") write(domainEntries.portable)
                    else if (path.startsWith("references/")) {
                        for (reference in domainEntries.references) write(line(SnapshotJson.encode(buildJsonObject {
                            put("sha256", reference.sha256); put("byteSize", reference.byteSize)
                            put("canonicalExtension", reference.canonicalExtension)
                        }), path))
                    } else for (record in domainEntries.records.getValue(path)) write(line(record.toByteArray(Charsets.UTF_8), path))
                }
                files[path] = file
            }
            val descriptors = files.map { (path, file) -> SnapshotEntryDescriptor(path, file.length(), hash(file, check)) }
            val manifest = SnapshotManifest.encode(identity, descriptors, domainEntries.references.size.toLong())
            addBound(0, manifest.size.toLong(), limits.manifestBytes, "manifest.json")
            addBound(total, manifest.size.toLong(), limits.totalBytes, "totalBytes")
            ZipOutputStream(BoundedOutput(target.outputStream(), limits.zipBytes, "zipBytes", check)).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
                for (path in expected) {
                    check(); zip.putNextEntry(ZipEntry(path))
                    files.getValue(path).inputStream().use { input -> transfer(input, zip, check) }
                    zip.closeEntry()
                }
            }
            return validate(target, identity.repositoryId, identity.variant, identity.snapshotId, check = check)
        } finally { scratch.deleteRecursively() }
    }

    fun validate(file: File, expectedRepositoryId: UUID, variant: String,
        expectedSnapshotId: UUID? = null, expectedWholeSha: String? = null,
        check: () -> Unit = {}): ValidatedSnapshot {
        if (!file.isFile) throw SnapshotException(SnapshotError.READ_FAILED, "FILE")
        addBound(0, file.length(), limits.zipBytes, "zipBytes")
        val whole = hash(file, check)
        if (expectedWholeSha != null && whole != expectedWholeSha) throw SnapshotException(SnapshotError.WHOLE_DIGEST_MISMATCH)
        val scratch = Files.createTempDirectory(file.absoluteFile.parentFile!!.toPath(), "snapshot-read-").toFile()
        try {
            ZipFile(file).use { zip ->
                val names = ZipEntryNames()
                val entries = linkedMapOf<String, ZipEntry>()
                val iterator = zip.entries()
                while (iterator.hasMoreElements()) {
                    check()
                    val entry = iterator.nextElement()
                    if (entries.size >= 17) limit("entryCount", entries.size + 1L, 17)
                    try { validateZipRelativePath(entry.name); names.accept(entry.name) }
                    catch (_: Exception) { invalid("ZIP_PATH:${entry.name}") }
                    if (entry.isDirectory || entry.name !in SnapshotContract.paths) invalid("UNEXPECTED_ENTRY:${entry.name}")
                    entries[entry.name] = entry
                }
                if (entries.keys != SnapshotContract.paths.toSet()) invalid("MISSING_ENTRY")
                var total = 0L
                val verifiedFiles = linkedMapOf<String, File>()
                val bytes = linkedMapOf<String, Long>()
                val counts = linkedMapOf<String, Long>()
                val maxima = linkedMapOf<String, Long>()
                fun spool(path: String, index: Int): File {
                    val entry = entries.getValue(path)
                    val target = File(scratch, "entry-$index")
                    val crc = CRC32()
                    var count = 0L
                    var record = 0L
                    var maxRecord = 0L
                    var records = 0L
                    zip.getInputStream(entry).use { input -> target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            check(); val n = input.read(buffer)
                            if (n < 0) break
                            if (n == 0) continue
                            count = addBound(count, n.toLong(), SnapshotContract.entryLimit(path, limits), path)
                            total = addBound(total, n.toLong(), limits.totalBytes, "totalBytes")
                            if (path.endsWith("jsonl")) for (i in 0 until n) {
                                record = addBound(record, 1, limits.recordBytes, "$path:recordBytes")
                                if (buffer[i] == 10.toByte()) {
                                    if (record == 1L) invalid("EMPTY_RECORD:$path")
                                    records++; maxRecord = maxOf(maxRecord, record); record = 0
                                }
                            }
                            crc.update(buffer, 0, n); output.write(buffer, 0, n)
                        }
                    } }
                    if (record != 0L) invalid("MISSING_FINAL_LF:$path")
                    if (entry.size != count || entry.crc != crc.value) invalid("ZIP_SIZE_CRC:$path")
                    bytes[path] = count; counts[path] = records; maxima[path] = maxRecord
                    return target
                }
                val manifestFile = spool("manifest.json", 0)
                val manifestBytes = manifestFile.readBytes() // bounded to 1 MiB
                val (identity, descriptors, referenceCount) = SnapshotManifest.decode(manifestBytes, limits)
                if (identity.repositoryId != expectedRepositoryId || identity.variant != variant ||
                    (expectedSnapshotId != null && identity.snapshotId != expectedSnapshotId)) {
                    throw SnapshotException(SnapshotError.SNAPSHOT_ID_CONFLICT, "IDENTITY")
                }
                if (!SnapshotManifest.encode(identity, descriptors, referenceCount).contentEquals(manifestBytes)) invalid("NONCANONICAL_MANIFEST")
                val descriptorMap = descriptors.associateBy { it.path }
                if (descriptorMap.keys != SnapshotContract.paths.drop(1).toSet()) invalid("MANIFEST_ENTRIES")
                for ((index, path) in SnapshotContract.paths.drop(1).withIndex()) {
                    val target = spool(path, index + 1)
                    val expected = descriptorMap.getValue(path)
                    if (target.length() != expected.byteSize || hash(target, check) != expected.sha256) {
                        throw SnapshotException(SnapshotError.ENTRY_DIGEST_MISMATCH, path)
                    }
                    verifiedFiles[path] = target
                }
                if (counts["references/media-blobs.jsonl"] != referenceCount) invalid("REFERENCE_COUNT")
                SnapshotDomainCodec.validate(verifiedFiles, check, limits)
                if (hash(file, check) != whole) throw SnapshotException(SnapshotError.WHOLE_DIGEST_MISMATCH, "SOURCE_CHANGED")
                return ValidatedSnapshot(identity, whole, file.length(), SnapshotMetrics(counts, bytes,
                    maxima.values.maxOrNull() ?: 0, file.length(), total, maxima))
            }
        } catch (e: CancellationException) { throw e }
        catch (e: SnapshotException) { throw e }
        catch (e: java.util.zip.ZipException) { throw SnapshotException(SnapshotError.INVALID_ZIP, "ZIP", cause = e) }
        catch (e: IOException) { throw SnapshotException(SnapshotError.READ_FAILED, "READ", cause = e) }
        catch (e: Exception) { throw SnapshotException(SnapshotError.INVALID_ZIP, "ZIP", cause = e) }
        finally { scratch.deleteRecursively() }
    }

    private fun line(record: ByteArray, path: String): ByteArray {
        if (record.isEmpty() || record.any { it == 10.toByte() || it == 13.toByte() }) invalid("JSONL_FRAMING:$path")
        addBound(0, record.size + 1L, limits.recordBytes, "$path:recordBytes")
        return record + 10.toByte()
    }
    private fun hash(file: File, check: () -> Unit): String {
        val d = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> transfer(input, object : OutputStream() {
            override fun write(b: Int) { d.update(b.toByte()) }
            override fun write(b: ByteArray, off: Int, len: Int) { d.update(b, off, len) }
        }, check) }
        return d.digest().joinToString("") { "%02x".format(it) }
    }
    private fun transfer(input: InputStream, output: OutputStream, check: () -> Unit) {
        val b = ByteArray(64 * 1024)
        while (true) { check(); val n = input.read(b); if (n < 0) break; if (n > 0) output.write(b, 0, n) }
    }
    internal class BoundedOutput(private val output: OutputStream, private val maximum: Long,
        private val category: String, private val check: () -> Unit) : OutputStream() {
        private var count = 0L
        override fun write(b: Int) { check(); count = addBound(count, 1, maximum, category); output.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { check(); count = addBound(count, len.toLong(), maximum, category); output.write(b, off, len) }
        override fun close() = output.close()
        override fun flush() = output.flush()
    }
    companion object {
        internal fun addBound(old: Long, n: Long, maximum: Long, category: String): Long {
            if (old < 0 || n < 0 || n > maximum - old) limit(category, if (n > Long.MAX_VALUE - old) Long.MAX_VALUE else old + n, maximum)
            return old + n
        }
        private fun limit(category: String, observed: Long, maximum: Long): Nothing =
            throw SnapshotException(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, category, observed, maximum)
        private fun invalid(category: String): Nothing = throw SnapshotException(SnapshotError.INVALID_ZIP, category)
    }
}
