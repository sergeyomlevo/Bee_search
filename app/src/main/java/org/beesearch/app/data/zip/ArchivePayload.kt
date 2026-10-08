package org.beesearch.app.data.zip

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/** Archive bytes live in a managed source or isolated spool file; only bounded JSON uses metadata(). */
internal class ArchivePayload private constructor(
    private val file: File?,
    private val metadata: ByteArray?,
    val size: Long,
    val sha256: String,
) {
    fun open(): InputStream = file?.inputStream()?.buffered() ?: ByteArrayInputStream(requireNotNull(metadata))

    /** Rechecks bytes while writing, detecting changed sources without whole-file buffering. */
    fun copyTo(output: OutputStream) {
        val actual = open().use { copyZipBytesWithSha256(it, output, size, size) }
        if (actual.sha256 != sha256) throw IOException("archive payload SHA-256 changed")
    }

    /** Only format adapters parsing known, bounded metadata entries may call this. */
    fun readMetadata(maximumBytes: Long): ByteArray {
        if (size > maximumBytes || size > Int.MAX_VALUE) throw ZipSafetyException(ZipSafetyFailure.ENTRY_BYTES)
        return open().use { input ->
            val output = java.io.ByteArrayOutputStream()
            copyZipBytes(input, output, maximumBytes, size)
            output.toByteArray()
        }
    }

    companion object {
        fun fromFile(file: File): ArchivePayload {
            val result = file.inputStream().buffered().use { copyZipBytesWithSha256(it, NullOutput, Long.MAX_VALUE) }
            return staged(file, result)
        }
        fun metadata(bytes: ByteArray): ArchivePayload = ArchivePayload(
            null, bytes, bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).hex(),
        )
        internal fun staged(file: File, result: ZipCopyResult) = ArchivePayload(file, null, result.byteCount, result.sha256)
    }
}

private object NullOutput : OutputStream() {
    override fun write(value: Int) = Unit
    override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
}
private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

/** Owns extracted bytes until a validated decoder result is closed; no archive path becomes a host path. */
internal class StagedZipArchive private constructor(
    val entries: Map<String, ArchivePayload>,
    private val directory: File,
) : Closeable {
    override fun close() {
        if (directory.exists() && !directory.deleteRecursively()) throw IOException("archive staging cleanup failed")
    }

    companion object {
        fun read(input: InputStream, policy: ZipSafetyPolicy, parent: File? = null): StagedZipArchive {
            val directory = if (parent == null) Files.createTempDirectory("bee-archive-").toFile()
            else Files.createTempDirectory(parent.toPath(), "bee-archive-").toFile()
            try {
                val entries = linkedMapOf<String, ArchivePayload>()
                val guard = ZipReadGuard(policy)
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        guard.acceptEntry(entry.name, entry.isDirectory)
                        val file = File(directory, entries.size.toString())
                        val result = file.outputStream().buffered().use { guard.copyEntry(zip, it) }
                        zip.closeEntry()
                        entries[entry.name] = ArchivePayload.staged(file, result)
                    }
                }
                return StagedZipArchive(entries, directory)
            } catch (error: Throwable) {
                if (!directory.deleteRecursively()) error.addSuppressed(IOException("archive staging cleanup failed"))
                throw error
            }
        }
    }
}
