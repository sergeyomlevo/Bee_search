package org.beesearch.app.data.zip

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Only validated format metadata may authorize media; ZIP sizes are not authorization. */
internal data class MediaExpectation(val size: Long, val sha256: String) {
    init {
        if (size < 0) throw ZipSafetyException(ZipSafetyFailure.SIZE_MISMATCH)
        if (!sha256.matches(Regex("[0-9a-f]{64}"))) throw IOException("invalid media SHA-256")
    }
}

internal fun checkedMediaTotal(values: Collection<MediaExpectation>): Long {
    val budget = ZipByteBudget(Long.MAX_VALUE, ZipSafetyFailure.TOTAL_BYTES)
    values.forEach { budget.add(it.size) }
    return budget.bytes
}

/** Finite limits apply to metadata only, even when the archive contains large media. */
internal fun validateMetadataBudget(values: Collection<ArchivePayload>, policy: ZipSafetyPolicy) {
    val budget = ZipByteBudget(policy.maxTotalBytes, ZipSafetyFailure.TOTAL_BYTES)
    values.forEach {
        if (it.size > policy.maxEntryBytes) throw ZipSafetyException(ZipSafetyFailure.ENTRY_BYTES)
        budget.add(it.size)
    }
}

/** Advisory filesystem check; unavailable capacity information never invents a file-size cap. */
internal fun requireArchiveSpace(directory: File, bytes: Long) {
    require(bytes >= 0)
    val available = directory.usableSpace
    if (available > 0 && bytes > available) throw IOException("insufficient archive staging space")
}

/**
 * Stream profiles spool compressed bytes for random access. No media is decompressed until all
 * bounded metadata and the format domain/inventory validator authorize it. Raw spool writes are
 * constrained by filesystem capacity/ENOSPC rather than an application media-size cap.
 */
internal fun readAuthorizedArchive(
    input: InputStream,
    policy: ZipSafetyPolicy,
    isMediaPath: (String) -> Boolean,
    authorize: (Map<String, ArchivePayload>, Set<String>) -> Map<String, MediaExpectation>,
    parent: File?,
): StagedZipArchive {
    val directory = if (parent == null) Files.createTempDirectory("bee-archive-").toFile()
    else Files.createTempDirectory(parent.toPath(), "bee-archive-").toFile()
    try {
        val compressed = File(directory, "source.zip")
        input.use { source -> compressed.outputStream().buffered().use { copyZipBytes(source, it, Long.MAX_VALUE) } }
        checkCentralDirectoryBudget(compressed, policy)
        val payloads = linkedMapOf<String, ArchivePayload>()
        ZipFile(compressed).use { zip ->
            val entries = linkedMapOf<String, ZipEntry>()
            val guard = ZipReadGuard(policy)
            val enumeration = zip.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                guard.acceptEntry(entry.name, entry.isDirectory)
                entries[entry.name] = entry
            }
            val metadataBudget = ZipByteBudget(policy.maxTotalBytes, ZipSafetyFailure.TOTAL_BYTES)
            entries.values.filterNot { isMediaPath(it.name) }.forEach { entry ->
                val target = File(directory, "entry-${payloads.size}")
                val remaining = policy.maxTotalBytes - metadataBudget.bytes
                val result = try {
                    extract(zip, entry, target, minOf(policy.maxEntryBytes, remaining), null)
                } catch (error: ZipSafetyException) {
                    if (error.failure == ZipSafetyFailure.ENTRY_BYTES && remaining < policy.maxEntryBytes) {
                        throw ZipSafetyException(ZipSafetyFailure.TOTAL_BYTES)
                    }
                    throw error
                }
                metadataBudget.add(result.byteCount)
                payloads[entry.name] = ArchivePayload.staged(target, result)
            }
            val mediaNames = entries.keys.filterTo(linkedSetOf(), isMediaPath)
            val expected = authorize(payloads.toMap(), entries.keys.toSet())
            if (expected.keys != mediaNames) throw IOException("unexpected or missing media ZIP entry")
            requireArchiveSpace(directory, checkedMediaTotal(expected.values))
            // Validate local records too: ZipFile alone may ignore orphan/duplicate local entries.
            val localGuard = ZipReadGuard(policy)
            val seen = linkedSetOf<String>()
            ZipInputStream(compressed.inputStream().buffered()).use { local ->
                while (true) {
                    val entry = local.nextEntry ?: break
                    localGuard.acceptEntry(entry.name, entry.isDirectory)
                    val central = entries[entry.name] ?: throw IOException("unexpected local ZIP entry")
                    val crc = CRC32()
                    val checked = CheckedInputStream(local, crc)
                    val media = expected[entry.name]
                    if (media == null) {
                        val prior = payloads.getValue(entry.name)
                        val result = copyZipBytesWithSha256(checked, DiscardBytes, prior.size, prior.size)
                        if (result.sha256 != prior.sha256) throw IOException("local metadata differs from directory")
                    } else {
                        val target = File(directory, "entry-${payloads.size}")
                        val result = target.outputStream().buffered().use {
                            copyZipBytesWithSha256(checked, it, media.size, media.size)
                        }
                        if (result.sha256 != media.sha256) throw IOException("media SHA-256 mismatch")
                        payloads[entry.name] = ArchivePayload.staged(target, result)
                    }
                    local.closeEntry() // checks local CRC/data descriptor/truncation too.
                    if (crc.value != central.crc || entry.size != central.size) throw IOException("local ZIP metadata mismatch")
                    seen += entry.name
                }
            }
            if (seen != entries.keys) throw IOException("missing local ZIP entry")
        }
        if (!compressed.delete()) throw IOException("compressed archive staging cleanup failed")
        return StagedZipArchive.owned(payloads, directory)
    } catch (error: Throwable) {
        if (!directory.deleteRecursively()) error.addSuppressed(IOException("archive staging cleanup failed"))
        throw error
    }
}

private object DiscardBytes : java.io.OutputStream() {
    override fun write(value: Int) = Unit
    override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
}

/** Bound the central index BEFORE ZipFile can allocate its in-memory index, including ZIP64. */
private fun checkCentralDirectoryBudget(file: File, policy: ZipSafetyPolicy) = RandomAccessFile(file, "r").use { source ->
    val length = source.length()
    if (length < 22) throw IOException("missing ZIP end record")
    val tail = ByteArray(minOf(length, 65_557L).toInt())
    val start = length - tail.size
    source.seek(start)
    source.readFully(tail)
    val bytes = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN)
    var end = tail.size - 22
    while (end >= 0 && !(bytes.getInt(end) == 0x06054b50 &&
        end + 22 + (bytes.getShort(end + 20).toInt() and 0xffff) == tail.size)) end--
    if (end < 0) throw IOException("missing ZIP end record")
    var count = (bytes.getShort(end + 10).toInt() and 0xffff).toLong()
    var centralSize = bytes.getInt(end + 12).toLong() and 0xffffffffL
    var centralOffset = bytes.getInt(end + 16).toLong() and 0xffffffffL
    if (count == 0xffffL || centralSize == 0xffffffffL || centralOffset == 0xffffffffL) {
        val locatorPosition = start + end - 20
        if (locatorPosition < 0) throw IOException("missing ZIP64 locator")
        val locator = ByteArray(20)
        source.seek(locatorPosition); source.readFully(locator)
        val loc = ByteBuffer.wrap(locator).order(ByteOrder.LITTLE_ENDIAN)
        if (loc.getInt(0) != 0x07064b50) throw IOException("missing ZIP64 locator")
        val offset = loc.getLong(8)
        if (offset < 0 || offset > locatorPosition - 56) throw IOException("invalid ZIP64 offset")
        val record = ByteArray(56)
        source.seek(offset); source.readFully(record)
        val zip64 = ByteBuffer.wrap(record).order(ByteOrder.LITTLE_ENDIAN)
        if (zip64.getInt(0) != 0x06064b50 || zip64.getLong(4) < 44) throw IOException("invalid ZIP64 end record")
        count = zip64.getLong(32); centralSize = zip64.getLong(40); centralOffset = zip64.getLong(48)
    }
    if (count < 0 || centralSize < 0 || centralOffset < 0 || centralOffset > length || centralSize > length - centralOffset) {
        throw IOException("invalid ZIP directory size")
    }
    if (count > policy.maxEntries) throw ZipSafetyException(ZipSafetyFailure.ENTRY_COUNT)
    if (centralSize > policy.maxTotalBytes) throw ZipSafetyException(ZipSafetyFailure.TOTAL_BYTES)
    val guard = ZipReadGuard(policy)
    val header = ByteArray(46)
    var cursor = centralOffset
    val centralEnd = centralOffset + centralSize // Addition already bounded by source length above.
    while (cursor < centralEnd) {
        if (centralEnd - cursor < header.size) throw IOException("truncated ZIP directory entry")
        source.seek(cursor); source.readFully(header)
        val entry = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        if (entry.getInt(0) != 0x02014b50) throw IOException("invalid ZIP directory entry")
        val nameLength = entry.getShort(28).toInt() and 0xffff
        val recordSize = 46L + nameLength + (entry.getShort(30).toInt() and 0xffff) + (entry.getShort(32).toInt() and 0xffff)
        if (recordSize > centralEnd - cursor) throw IOException("truncated ZIP directory entry")
        val name = ByteArray(nameLength)
        source.readFully(name)
        val path = name.toString(Charsets.UTF_8)
        guard.acceptEntry(path, path.endsWith('/'))
        cursor += recordSize
    }
}

private fun extract(zip: ZipFile, entry: ZipEntry, target: File, maximum: Long, expected: Long?): ZipCopyResult {
    val crc = CRC32()
    val result = zip.getInputStream(entry).use { raw ->
        CheckedInputStream(raw, crc).use { input ->
            target.outputStream().buffered().use { output ->
                copyZipBytesWithSha256(input, output, maximum, expected)
            }
        }
    }
    if (entry.crc < 0 || crc.value != entry.crc) throw IOException("ZIP CRC mismatch")
    // Header size is an additional consistency check, never the security boundary.
    if (entry.size < 0 || entry.size != result.byteCount) throw IOException("ZIP size mismatch")
    return result
}
