package org.beesearch.app.data.gpx

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpxCancellationCloseDeviceTest {
    @Test
    fun completeCanonicalParseClosesBothProviderStreamsOnce() {
        val source = SourceSet(GpxBoundaryCorpus.events(100).data)

        val result = AndroidGpxProfileReader.parse({ source.open() })

        assertTrue(result.profile.draft.tracks.isNotEmpty())
        assertEquals(2, source.streams.size)
        assertTrue(source.streams.all { it.closeCount == 1 })
    }

    @Test
    fun standaloneCloseFailureEachPassIsExactSourceCloseFailure() {
        for (pass in 1..2) {
            val source = SourceSet(GpxBoundaryCorpus.events(100).data, failClosePass = pass)

            val failure = runCatching {
                AndroidGpxProfileReader.parse({ source.open() })
            }.exceptionOrNull()

            assertEquals(pass, source.streams.size)
            assertEquals("INPUT_IO_ERROR:source-close", failure?.message)
            assertTrue(failure is GpxXmlFailure)
            assertTrue(source.streams.dropLast(1).all { it.closeCount == 1 })
            assertEquals(2, source.streams.last().closeCount)
        }
    }

    @Test
    fun cancellationWithSuccessfulClosePreservesSameCancellationEachPass() {
        for (pass in 1..2) {
            val source = SourceSet(GpxBoundaryCorpus.events(100).data)
            val cancellation = CancellationException("cancel-pass-$pass")

            val outcome = runCatching {
                AndroidGpxProfileReader.parse(
                    open = { source.open() },
                    checkCancellation = {
                        if (source.streams.size == pass && source.streams.last().bytesRead >= 64) {
                            throw cancellation
                        }
                    },
                )
            }
            val thrown = outcome.exceptionOrNull()

            assertSame(cancellation, thrown)
            assertNull(outcome.getOrNull())
            assertEquals(pass, source.streams.size)
            assertTrue(source.streams.all { it.closeCount == 1 })
        }
    }

    @Test
    fun cancellationWithCloseFailurePreservesSameCancellationAndBoundedSuppressedFailureEachPass() {
        for (pass in 1..2) {
            val source = SourceSet(GpxBoundaryCorpus.events(100).data, failClosePass = pass)
            val cancellation = CancellationException("cancel-pass-$pass")

            val outcome = runCatching {
                AndroidGpxProfileReader.parse(
                    open = { source.open() },
                    checkCancellation = {
                        if (source.streams.size == pass && source.streams.last().bytesRead >= 64) {
                            throw cancellation
                        }
                    },
                )
            }
            val thrown = outcome.exceptionOrNull()

            assertSame(cancellation, thrown)
            assertNull(outcome.getOrNull())
            assertEquals(1, cancellation.suppressed.size)
            val suppressed = cancellation.suppressed.single()
            assertEquals("INPUT_IO_ERROR:source-close", suppressed.message)
            assertEquals(pass, source.streams.size)
            assertTrue(source.streams.all { it.closeCount == 1 })
        }
    }

    private class SourceSet(
        private val bytes: ByteArray,
        private val failClosePass: Int? = null,
    ) {
        val streams = ArrayList<TrackedInputStream>()

        fun open(): InputStream = TrackedInputStream(bytes, streams.size + 1 == failClosePass).also {
            streams += it
        }
    }

    private class TrackedInputStream(
        bytes: ByteArray,
        private val failClose: Boolean,
    ) : InputStream() {
        private val delegate = bytes.inputStream()
        var bytesRead = 0L
            private set
        var closeCount = 0
            private set

        override fun read(): Int = delegate.read().also { if (it >= 0) bytesRead++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, minOf(length, 32)).also { if (it > 0) bytesRead += it }

        override fun close() {
            closeCount++
            if (failClose) throw java.io.IOException("controlled close failure")
            delegate.close()
        }
    }
}
