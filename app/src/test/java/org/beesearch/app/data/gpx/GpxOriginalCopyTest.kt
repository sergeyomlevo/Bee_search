package org.beesearch.app.data.gpx

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class GpxOriginalCopyTest {
    @Test
    fun copiesChunkedBinaryBytesIncludingBomAndUtf16WithoutTransformation() {
        val expected = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0xFF.toByte(), 0x00, 0x41, 0x00, 0x42)
        val output = ByteArrayOutputStream()

        val identity = copyGpxOriginal(ChunkedInputStream(expected, 2), output)

        assertArrayEquals(expected, output.toByteArray())
        assertEquals(expected.size.toLong(), identity.length)
        assertEquals(sha256(expected), identity.sha256)
    }

    @Test
    fun acceptsBelowAndExactCeilingButRejectsFirstByteOverCeiling() {
        val ceiling = 16 * 1024 * 1024
        val below = ByteArray(ceiling - 1) { (it and 0xFF).toByte() }
        val exact = ByteArray(ceiling) { (it and 0xFF).toByte() }
        val over = ByteArray(ceiling + 1) { (it and 0xFF).toByte() }

        assertEquals(below.size.toLong(), copyGpxOriginal(ByteArrayInputStream(below), ByteArrayOutputStream()).length)
        assertEquals(exact.size.toLong(), copyGpxOriginal(ByteArrayInputStream(exact), ByteArrayOutputStream()).length)
        val output = ByteArrayOutputStream()
        val error = assertFailsWith<GpxOriginalStoreException> {
            copyGpxOriginal(ByteArrayInputStream(over), output)
        }
        assertEquals(GpxOriginalStoreError.INPUT_LIMIT_EXCEEDED, error.code)
        assertEquals(GpxOriginalStage.COPY, error.stage)
        assertEquals(ceiling, output.size())
    }

    @Test
    fun rejectsEmptySource() {
        val error = assertFailsWith<GpxOriginalStoreException> {
            copyGpxOriginal(ByteArrayInputStream(ByteArray(0)), ByteArrayOutputStream())
        }
        assertEquals(GpxOriginalStoreError.INVALID_EMPTY_SOURCE, error.code)
    }

    @Test
    fun toleratesZeroReadsAndChecksCancellationDuringCopy() {
        val expected = ByteArray(32) { it.toByte() }
        var checks = 0
        val output = ByteArrayOutputStream()

        copyGpxOriginal(ZeroThenDataInputStream(expected), output) { checks++ }

        assertArrayEquals(expected, output.toByteArray())
        assertEquals(true, checks > 2)
    }

    @Test
    fun propagatesCancellationAndReadWriteFailures() {
        val cancellation = CancellationException("cancelled")
        assertSame(cancellation, assertFailsWith<CancellationException> {
            copyGpxOriginal(ByteArrayInputStream(byteArrayOf(1)), ByteArrayOutputStream()) { throw cancellation }
        })

        val readFailure = IOException("read")
        val readError = assertFailsWith<GpxOriginalStoreException> {
            copyGpxOriginal(FailingInputStream(readFailure), ByteArrayOutputStream())
        }
        assertEquals(GpxOriginalStoreError.IO_FAILURE, readError.code)
        assertEquals(GpxOriginalStage.READ, readError.stage)
        assertSame(readFailure, readError.cause)

        val writeFailure = IOException("write")
        val writeError = assertFailsWith<GpxOriginalStoreException> {
            copyGpxOriginal(ByteArrayInputStream(byteArrayOf(1)), FailingOutputStream(writeFailure))
        }
        assertEquals(GpxOriginalStoreError.IO_FAILURE, writeError.code)
        assertEquals(GpxOriginalStage.WRITE, writeError.stage)
        assertSame(writeFailure, writeError.cause)

        val midCopyCancellation = CancellationException("mid-copy")
        var checks = 0
        assertSame(midCopyCancellation, assertFailsWith<CancellationException> {
            copyGpxOriginal(ByteArrayInputStream(ByteArray(32)), ByteArrayOutputStream()) {
                checks++
                if (checks > 3) throw midCopyCancellation
            }
        })
    }

    @Test
    fun checkedLengthPreservesArithmeticOverflow() {
        assertFailsWith<ArithmeticException> { checkedGpxCopyLength(Long.MAX_VALUE, 1) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class ChunkedInputStream(private val bytes: ByteArray, private val chunk: Int) : InputStream() {
        private var position = 0
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position == bytes.size) return -1
            val count = minOf(chunk, length, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun read(): Int = if (position == bytes.size) -1 else bytes[position++].toInt() and 0xFF
    }

    private class ZeroThenDataInputStream(private val bytes: ByteArray) : InputStream() {
        private var zero = true
        private var position = 0
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (zero) { zero = false; return 0 }
            if (position == bytes.size) return -1
            val count = minOf(length, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
        override fun read(): Int = if (position == bytes.size) -1 else bytes[position++].toInt() and 0xFF
    }

    private class FailingInputStream(private val failure: IOException) : InputStream() {
        override fun read(): Int = throw failure
    }

    private class FailingOutputStream(private val failure: IOException) : OutputStream() {
        override fun write(byte: Int) = throw failure
    }

    private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            fail("Expected ${T::class.java.name}, got ${error::class.java.name}")
        }
        throw AssertionError("Expected ${T::class.java.name}")
    }
}
