package org.beesearch.app.data.zip

import java.io.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ZipSafetyTest {
    @Test fun safeRelativeNamesAndExactCaseSensitiveDuplicates() {
        listOf("manifest.json", "media/id/file", "A", "a").forEach(::validateZipRelativePath)
        val names = ZipEntryNames()
        names.accept("A"); names.accept("a")
        failure(ZipSafetyFailure.DUPLICATE) { names.accept("A") }
    }

    @Test fun unsafeRelativeNames() {
        listOf("", " ", "../x", "/x", "a\\b", "C:x", "a//b", "a/./b", "a/../b", "x/").forEach {
            failure(ZipSafetyFailure.UNSAFE_PATH) { validateZipRelativePath(it) }
        }
    }

    @Test fun directoryEntryRejected() {
        failure(ZipSafetyFailure.UNSAFE_PATH) { guard().acceptEntry("x", true) }
    }

    @Test fun entryCountExactBoundaryAndPlusOnePrecedesPathAndDuplicate() {
        val guard = guard(entries = 2)
        guard.acceptEntry("a", false); guard.acceptEntry("b", false)
        failure(ZipSafetyFailure.ENTRY_COUNT) { guard.acceptEntry("../x", false) }
        failure(ZipSafetyFailure.ENTRY_COUNT) { guard.acceptEntry("a", false) }
    }

    @Test fun duplicateEntryIsTyped() {
        val guard = guard(); guard.acceptEntry("a", false)
        failure(ZipSafetyFailure.DUPLICATE) { guard.acceptEntry("a", false) }
    }

    @Test fun exactEntrySizeAndPlusOne() {
        assertArrayEquals(byteArrayOf(1, 2, 3), guard(entry = 3).readEntry(byteArrayOf(1, 2, 3).inputStream()))
        failure(ZipSafetyFailure.ENTRY_BYTES) { guard(entry = 3).readEntry(ByteArray(4).inputStream()) }
    }

    @Test fun exactAggregateAndPlusOne() {
        val guard = guard(entry = 3, total = 4)
        guard.acceptEntry("a", false); guard.readEntry(ByteArray(3).inputStream())
        guard.acceptEntry("b", false); guard.readEntry(ByteArray(1).inputStream())
        guard.acceptEntry("c", false)
        failure(ZipSafetyFailure.TOTAL_BYTES) { guard.readEntry(ByteArray(1).inputStream()) }
    }

    @Test fun perEntryAndSourceFailurePrecedeAggregateFailure() {
        val guard = guard(entry = 3, total = 0)
        failure(ZipSafetyFailure.ENTRY_BYTES) { guard.readEntry(ByteArray(4).inputStream()) }
        val error = IOException("source")
        assertSame(error, assertThrows(IOException::class.java) { guard.readEntry(failingInput(error)) })
    }

    @Test fun longBudgetOverflowBeforeCapComparisonAndNoWrap() {
        val budget = ZipByteBudget(Long.MAX_VALUE, ZipSafetyFailure.TOTAL_BYTES)
        budget.add(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, budget.bytes)
        failure(ZipSafetyFailure.OVERFLOW) { budget.add(1) }
        assertEquals(Long.MAX_VALUE, budget.bytes)
    }

    @Test fun negativePoliciesRejectedZeroPoliciesIntentional() {
        assertThrows(IllegalArgumentException::class.java) { ZipSafetyPolicy(-1, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { ZipSafetyPolicy(1, -1, 0) }
        assertThrows(IllegalArgumentException::class.java) { ZipSafetyPolicy(1, 0, -1) }
        failure(ZipSafetyFailure.ENTRY_COUNT) { ZipReadGuard(ZipSafetyPolicy(0, 0, 0)).acceptEntry("a", false) }
        assertEquals(0, ZipReadGuard(ZipSafetyPolicy(1, 0, 0)).readEntry(byteArrayOf().inputStream()).size)
    }

    @Test fun copyCountAndKnownSha256() {
        val sink = ByteArrayOutputStream()
        val result = copyZipBytesWithSha256("abc".byteInputStream(), sink, 3, 3)
        assertEquals(3L, result.byteCount)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256)
        assertEquals("abc", sink.toString("UTF-8"))
    }

    @Test fun emptyInputHasEmptyDigest() {
        val result = copyZipBytesWithSha256(byteArrayOf().inputStream(), ByteArrayOutputStream(), 0, 0)
        assertEquals(0L, result.byteCount)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", result.sha256)
    }

    @Test fun truncatedSourceFailsExactLength() {
        assertThrows(EOFException::class.java) { copyZipBytes(ByteArray(2).inputStream(), ByteArrayOutputStream(), 4, 3) }
    }

    @Test fun excessExpectedLengthAndSafetyLimitAreDistinct() {
        failure(ZipSafetyFailure.SIZE_MISMATCH) { copyZipBytes(ByteArray(4).inputStream(), ByteArrayOutputStream(), 5, 3) }
        failure(ZipSafetyFailure.ENTRY_BYTES) { copyZipBytes(ByteArray(4).inputStream(), ByteArrayOutputStream(), 3, 3) }
    }

    @Test fun sourceExceptionPropagatesUnchanged() {
        val error = IOException("read failed")
        assertSame(error, assertThrows(IOException::class.java) { copyZipBytes(failingInput(error), ByteArrayOutputStream(), 10) })
    }

    @Test fun sinkExceptionPropagatesUnchanged() {
        val error = IOException("write failed")
        val sink = object : OutputStream() { override fun write(b: Int) { throw error } }
        assertSame(error, assertThrows(IOException::class.java) { copyZipBytes(ByteArray(1).inputStream(), sink, 10) })
    }

    @Test fun cancellationBeforeFirstReadAndDuringCopyLeavesCleanupToCaller() {
        val error = CancellationException("cancel")
        var read = false
        val source = object : InputStream() { override fun read(): Int { read = true; return -1 } }
        assertSame(error, assertThrows(CancellationException::class.java) {
            copyZipBytes(source, ByteArrayOutputStream(), 10, checkCancellation = { throw error })
        })
        assertFalse(read)
        val sink = ByteArrayOutputStream()
        var checks = 0
        assertSame(error, assertThrows(CancellationException::class.java) {
            copyZipBytes(ByteArray(16384).inputStream(), sink, 16384, checkCancellation = { if (++checks == 3) throw error })
        })
        assertEquals(8192, sink.size())
    }

    @Test fun streamsAreNotClosedOrFlushedOnSuccessOrFailure() {
        var sourceClosed = false; var sinkClosed = false; var flushed = false
        val source = object : ByteArrayInputStream(byteArrayOf(1)) { override fun close() { sourceClosed = true } }
        val sink = object : ByteArrayOutputStream() {
            override fun close() { sinkClosed = true }
            override fun flush() { flushed = true }
        }
        copyZipBytes(source, sink, 1)
        assertFalse(sourceClosed); assertFalse(sinkClosed); assertFalse(flushed)
        failure(ZipSafetyFailure.ENTRY_BYTES) { copyZipBytes(byteArrayOf(2).inputStream(), sink, 0) }
        assertFalse(sinkClosed); assertFalse(flushed)
    }

    @Test fun zeroReturningSourceMakesProgress() {
        var first = true
        val source = object : ByteArrayInputStream(byteArrayOf(7)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = if (first) { first = false; 0 } else super.read(b, off, len)
        }
        val sink = ByteArrayOutputStream()
        assertEquals(1L, copyZipBytes(source, sink, 1))
        assertArrayEquals(byteArrayOf(7), sink.toByteArray())
    }

    private fun guard(entries: Int = 4, entry: Long = 10, total: Long = 20) = ZipReadGuard(ZipSafetyPolicy(entries, entry, total))
    private fun failingInput(error: IOException) = object : InputStream() { override fun read(): Int = throw error }
    private fun failure(reason: ZipSafetyFailure, block: () -> Unit) {
        assertEquals(reason, assertThrows(ZipSafetyException::class.java, block).failure)
    }
}
