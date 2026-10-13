package org.beesearch.app.data.gpx

import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.security.MessageDigest

/** Counts and digests the exact source bytes before any XML transformation. */
internal class GpxRawInput(
    input: InputStream,
    private val limits: GpxXmlLimits,
    private val metrics: GpxXmlMetrics,
    private val checkCancellation: () -> Unit,
) : FilterInputStream(input) {
    private val digest = MessageDigest.getInstance("SHA-256")
    var count: Long = 0
        private set

    override fun read(): Int {
        checkCancellation()
        if (count >= limits.bytes) {
            val witness = sourceRead { super.read() }
            if (witness >= 0) gpxFail("LIMIT_EXCEEDED", "raw input exceeds byte limit")
            return -1
        }
        val value = sourceRead { super.read() }
        if (value >= 0) record(byteArrayOf(value.toByte()), 0, 1)
        checkCancellation()
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkCancellation()
        if (length == 0) return 0
        if (count >= limits.bytes) {
            val witness = sourceRead { super.read() }
            if (witness >= 0) gpxFail("LIMIT_EXCEEDED", "raw input exceeds byte limit")
            return -1
        }
        val allowed = minOf(length.toLong(), limits.bytes - count).toInt()
        var zeroReads = 0
        var read: Int
        do {
            read = sourceRead { super.read(buffer, offset, allowed) }
            if (read == 0) {
                zeroReads++
                if (zeroReads > 8) gpxFail("INPUT_IO_ERROR", "source returned repeated zero-length reads")
                checkCancellation()
            }
        } while (read == 0)
        if (read > 0) record(buffer, offset, read)
        checkCancellation()
        return read
    }

    private inline fun sourceRead(block: () -> Int): Int = try {
        block()
    } catch (error: IOException) {
        if (error is GpxXmlFailure) throw error
        gpxFail("INPUT_IO_ERROR", "source-read")
    }

    override fun close() {
        try { super.close() } catch (error: IOException) {
            if (error is GpxXmlFailure) throw error
            gpxFail("INPUT_IO_ERROR", "source-close")
        }
    }

    private fun record(buffer: ByteArray, offset: Int, length: Int) {
        count = Math.addExact(count, length.toLong())
        digest.update(buffer, offset, length)
        metrics.bytesRead = count
    }

    fun digestHex(): String = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
