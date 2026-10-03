package org.beesearch.app.data.zip

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Mechanical budgets supplied by a format adapter; no schema or storage ownership. */
internal data class ZipSafetyPolicy(val maxEntries: Int, val maxEntryBytes: Long, val maxTotalBytes: Long) {
    init { require(maxEntries >= 0 && maxEntryBytes >= 0 && maxTotalBytes >= 0) }
}
internal enum class ZipSafetyFailure { ENTRY_COUNT, UNSAFE_PATH, DUPLICATE, ENTRY_BYTES, TOTAL_BYTES, OVERFLOW, SIZE_MISMATCH }
internal class ZipSafetyException(val failure: ZipSafetyFailure) : Exception(failure.name)

/** Exact, case-sensitive names. Path validation is deliberately separate. */
internal class ZipEntryNames {
    private val names = hashSetOf<String>()
    fun accept(name: String) {
        if (!names.add(name)) throw ZipSafetyException(ZipSafetyFailure.DUPLICATE)
    }
}

/** Existing relative-segment rules; no normalization or filesystem access. */
internal fun validateZipRelativePath(name: String) {
    val parts = name.split('/')
    if (name.isBlank() || name.startsWith('/') || name.contains('\\') || name.contains(':') ||
        parts.any { it.isBlank() || it == "." || it == ".." }
    ) throw ZipSafetyException(ZipSafetyFailure.UNSAFE_PATH)
}

internal class ZipByteBudget(private val maximum: Long, private val exceeded: ZipSafetyFailure) {
    init { require(maximum >= 0) }
    var bytes: Long = 0
        private set
    fun add(amount: Long) {
        require(amount >= 0)
        if (amount > Long.MAX_VALUE - bytes) throw ZipSafetyException(ZipSafetyFailure.OVERFLOW)
        val next = bytes + amount
        if (next > maximum) throw ZipSafetyException(exceeded)
        bytes = next
    }
}

/** Aggregate accounting AFTER entry read preserves legacy stream error precedence. */
internal class ZipReadGuard(private val policy: ZipSafetyPolicy) {
    private val names = ZipEntryNames()
    private val aggregate = ZipByteBudget(policy.maxTotalBytes, ZipSafetyFailure.TOTAL_BYTES)
    private var entries = 0
    fun acceptEntry(name: String, isDirectory: Boolean) {
        if (entries >= policy.maxEntries) throw ZipSafetyException(ZipSafetyFailure.ENTRY_COUNT)
        if (isDirectory) throw ZipSafetyException(ZipSafetyFailure.UNSAFE_PATH)
        validateZipRelativePath(name)
        names.accept(name)
        entries++
    }
    fun readEntry(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        copyZipBytes(input, output, policy.maxEntryBytes)
        val bytes = output.toByteArray()
        aggregate.add(bytes.size.toLong())
        return bytes
    }
}

internal data class ZipCopyResult(val byteCount: Long, val sha256: String)

/**
 * Does not close/flush streams or remove a partial sink: caller owns cleanup.
 * Callback provides cancellation (e.g. ensureActive); its exception propagates unchanged.
 * Checked before each read/write, including before the first read. No directory assumptions.
 * Optional exact length rejects short EOF and excess data; safety cap is checked first.
 */
internal fun copyZipBytes(
    input: InputStream,
    output: OutputStream,
    maximumBytes: Long,
    expectedBytes: Long? = null,
    checkCancellation: () -> Unit = {},
    digest: MessageDigest? = null,
): Long {
    require(expectedBytes == null || expectedBytes >= 0)
    val budget = ZipByteBudget(maximumBytes, ZipSafetyFailure.ENTRY_BYTES)
    val buffer = ByteArray(8192)
    while (true) {
        checkCancellation()
        var read = input.read(buffer)
        if (read < 0) break
        // A zero-length provider read must not cause an unbounded spin.
        if (read == 0) {
            checkCancellation()
            val single = input.read()
            if (single < 0) break
            buffer[0] = single.toByte()
            read = 1
        }
        budget.add(read.toLong())
        if (expectedBytes != null && budget.bytes > expectedBytes) throw ZipSafetyException(ZipSafetyFailure.SIZE_MISMATCH)
        checkCancellation()
        output.write(buffer, 0, read)
        digest?.update(buffer, 0, read)
    }
    if (expectedBytes != null && budget.bytes != expectedBytes) throw EOFException("source shorter than expected")
    return budget.bytes
}

internal fun copyZipBytesWithSha256(
    input: InputStream,
    output: OutputStream,
    maximumBytes: Long,
    expectedBytes: Long? = null,
    checkCancellation: () -> Unit = {},
): ZipCopyResult {
    val digest = MessageDigest.getInstance("SHA-256")
    val count = copyZipBytes(input, output, maximumBytes, expectedBytes, checkCancellation, digest)
    return ZipCopyResult(count, digest.digest().joinToString("") { "%02x".format(it) })
}
