package org.beesearch.app.data.gpx

import java.io.InputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest

private const val GPX_ORIGINAL_MAX_BYTES = 16L * 1024 * 1024
private const val GPX_COPY_BUFFER_SIZE = 16 * 1024

/** Copies exact source bytes to staging while calculating their identity. */
internal fun copyGpxOriginal(
    input: InputStream,
    output: OutputStream,
    checkCancellation: () -> Unit = {},
): GpxOriginalIdentity {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(GPX_COPY_BUFFER_SIZE)
    var length = 0L

    checkCancellation()
    while (true) {
        checkCancellation()
        val remaining = GPX_ORIGINAL_MAX_BYTES - length
        if (remaining == 0L) {
            if (readSingleByte(input) >= 0) {
                throw GpxOriginalStoreException(
                    code = GpxOriginalStoreError.INPUT_LIMIT_EXCEEDED,
                    stage = GpxOriginalStage.COPY,
                )
            }
            break
        }
        var read = readChunk(input, buffer, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) break
        if (read == 0) {
            val single = readSingleByte(input)
            if (single < 0) break
            buffer[0] = single.toByte()
            read = 1
        }

        if (remaining == 0L || read.toLong() > remaining) {
            throw GpxOriginalStoreException(
                code = GpxOriginalStoreError.INPUT_LIMIT_EXCEEDED,
                stage = GpxOriginalStage.COPY,
            )
        }

        checkCancellation()
        writeChunk(output, buffer, read)
        digest.update(buffer, 0, read)
        length = checkedGpxCopyLength(length, read)
        checkCancellation()
    }

    if (length == 0L) {
        throw GpxOriginalStoreException(
            code = GpxOriginalStoreError.INVALID_EMPTY_SOURCE,
            stage = GpxOriginalStage.COPY,
        )
    }
    return GpxOriginalIdentity(length = length, sha256 = digest.digest().toHexString())
}

internal fun checkedGpxCopyLength(current: Long, read: Int): Long =
    Math.addExact(current, read.toLong())

private fun ByteArray.toHexString(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun readSingleByte(input: InputStream): Int = try {
    input.read()
} catch (error: IOException) {
    if (error is GpxOriginalStoreException) throw error
    throw GpxOriginalStoreException(
        code = GpxOriginalStoreError.IO_FAILURE,
        stage = GpxOriginalStage.READ,
        cause = error,
    )
}

private fun readChunk(input: InputStream, buffer: ByteArray, length: Int): Int = try {
    input.read(buffer, 0, length)
} catch (error: IOException) {
    if (error is GpxOriginalStoreException) throw error
    throw GpxOriginalStoreException(
        code = GpxOriginalStoreError.IO_FAILURE,
        stage = GpxOriginalStage.READ,
        cause = error,
    )
}

private fun writeChunk(output: OutputStream, buffer: ByteArray, length: Int) {
    try {
        output.write(buffer, 0, length)
    } catch (error: IOException) {
        if (error is GpxOriginalStoreException) throw error
        throw GpxOriginalStoreException(
            code = GpxOriginalStoreError.IO_FAILURE,
            stage = GpxOriginalStage.WRITE,
            cause = error,
        )
    }
}
