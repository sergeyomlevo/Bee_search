package org.beesearch.app.data.gpx

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxCancellationCloseTest {
    @Test
    fun normalCloseSucceedsAndClosesSourceOnce() {
        val source = TrackedInputStream(xml())
        val input = GpxInput(source)

        input.closePreservingCancellation(null)

        assertEquals(1, source.closeCount)
    }

    @Test
    fun standaloneCloseFailureIsSourceCloseInputError() {
        val source = TrackedInputStream(xml(), closeFailure = true)
        val input = GpxInput(source)

        val failure = runCatching { input.closePreservingCancellation(null) }.exceptionOrNull()

        assertTrue(failure is GpxXmlFailure)
        assertEquals("INPUT_IO_ERROR", (failure as GpxXmlFailure).category)
        assertEquals("source-close", failure.reason)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun cancellationWithSuccessfulCloseKeepsOriginalCancellation() {
        val source = TrackedInputStream(xml())
        val input = GpxInput(source)
        val cancellation = CancellationException("cancelled")

        val thrown = runCatching {
            try {
                throw cancellation
            } finally {
                input.closePreservingCancellation(cancellation)
            }
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(1, source.closeCount)
    }

    @Test
    fun cancellationWithCloseFailureKeepsCancellationAndBoundsSuppressedFailure() {
        val source = TrackedInputStream(xml(), closeFailure = true)
        val input = GpxInput(source)
        val cancellation = CancellationException("cancelled")

        val thrown = runCatching {
            try {
                throw cancellation
            } finally {
                input.closePreservingCancellation(cancellation)
            }
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(1, source.closeCount)
        assertEquals(1, cancellation.suppressed.size)
        val suppressed = cancellation.suppressed.single()
        assertTrue(suppressed is GpxXmlFailure)
        assertEquals("INPUT_IO_ERROR", (suppressed as GpxXmlFailure).category)
        assertEquals("source-close", suppressed.reason)
    }

    @Test
    fun closeFailureThatIsTheSameCancellationIsNotSelfSuppressed() {
        val source = TrackedInputStream(xml(), closeFailureValue = CancellationException("cancelled"))
        val input = GpxInput(source)
        val cancellation = source.closeFailureValue!!

        input.closePreservingCancellation(cancellation)

        assertEquals(1, source.closeCount)
        assertEquals(0, cancellation.suppressed.size)
    }

    @Test
    fun constructorPreservesOriginalCancellationWhenCleanupRuns() {
        val cancellation = CancellationException("cancelled")

        val thrown = runCatching {
            GpxInput(ByteArrayInputStream(xml())) { throw cancellation }
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
    }

    private fun xml(): ByteArray = "<?xml version=\"1.0\"?><gpx/>".toByteArray()

    private class TrackedInputStream(
        bytes: ByteArray,
        private val closeFailure: Boolean = false,
        val closeFailureValue: CancellationException? = null,
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var closeCount = 0
            private set

        override fun read(): Int = delegate.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length)

        override fun close() {
            closeCount++
            closeFailureValue?.let { throw it }
            if (closeFailure) throw java.io.IOException("controlled close failure")
            delegate.close()
        }
    }
}
