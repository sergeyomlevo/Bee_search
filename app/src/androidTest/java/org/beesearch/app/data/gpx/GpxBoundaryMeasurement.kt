package org.beesearch.app.data.gpx

import android.os.Build
import android.os.Debug
import android.util.Log
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import org.beesearch.app.domain.gpx.GpxProfileFailure
import org.junit.Assert.*

/** Test-only transport/resource observations; no production thresholds or parser replacement. */
internal class GpxBoundaryMeasurement(private val case: GpxBoundaryCorpus.Case, private val chunk: Int = 4096) {
    val streams = ArrayList<Source>()
    var checks = 0L
    var requestCheck: Long? = null
    var requestBytes: Long? = null
    private var accepted = false

    inner class Source(private val data: ByteArray) : InputStream() {
        var bytes = 0L
        var closes = 0
        var eof = false
        var failAtEof = false
        var failClose = false
        override fun read(): Int {
            if (bytes == data.size.toLong()) { eof = true; if (failAtEof) throw IOException("test-owned EOF"); return -1 }
            return data[bytes++.toInt()].toInt() and 255
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (bytes == data.size.toLong()) return read()
            val count = minOf(length, chunk, data.size - bytes.toInt())
            data.copyInto(buffer, offset, bytes.toInt(), bytes.toInt() + count)
            bytes += count
            return count
        }
        override fun close() { closes++; if (failClose) throw IOException("test-owned close") }
    }

    fun run(cancel: (GpxBoundaryMeasurement) -> Boolean = { false },
        setup: (Source, Int) -> Unit = { _, _ -> }, expected: String? = case.expectedReason): GpxProfileRead? {
        val runtime = Runtime.getRuntime()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val allocationBefore = allocated()
        val start = System.nanoTime()
        var outcome = "NOT_COMPLETED"
        var result: GpxProfileRead? = null
        try {
            result = AndroidGpxProfileReader.parse(open = {
                Source(case.data).also { streams.add(it); setup(it, streams.size) }
            }, checkCancellation = {
                checks++
                if (cancel(this)) {
                    requestCheck = checks; requestBytes = streams.lastOrNull()?.bytes
                    throw CancellationException("S4 controlled cancellation")
                }
            })
            accepted = true
            assertNull("Unexpected accepted ${case.id}", expected)
            val graph = result.profile.draft
            assertEquals(case.tracks, graph.tracks.size)
            assertEquals(case.segments, graph.tracks.sumOf { it.segments.size })
            assertEquals(case.samples, graph.tracks.sumOf { t -> t.segments.sumOf { it.samples.size } })
            graph.tracks.forEachIndexed { ti, t ->
                assertEquals(ti, t.ordinal)
                t.segments.forEachIndexed { si, s ->
                    assertEquals(si, s.ordinal)
                    s.samples.forEachIndexed { pi, p -> assertEquals(pi, p.ordinal) }
                }
            }
            case.semanticText?.let { assertEquals(it, graph.metadata.sourceName) }
            assertEquals(2, streams.size)
            assertTrue(streams.all { it.bytes == case.data.size.toLong() && it.eof })
            val verification = result.verification
            case.events?.let { assertEquals(it, verification.metrics.events); assertEquals(it, verification.preflightMetrics.events) }
            assertEquals(case.data.size.toLong(), verification.identity.length)
            assertTrue(verification.metrics.maxPendingStarts <= 4)
            assertTrue(verification.metrics.maxReferenceState <= 4)
            outcome = "ACCEPTED"
            return result
        } catch (error: Throwable) {
            outcome = when (error) {
                is GpxXmlFailure -> error.category + ":" + error.reason
                is GpxProfileFailure -> error.category + ":" + error.reason
                is CancellationException -> "CANCELLED"
                is OutOfMemoryError -> "OOM"
                else -> error.javaClass.simpleName
            }
            val category = when (error) { is GpxXmlFailure -> error.category; is GpxProfileFailure -> error.category; else -> null }
            if (expected == "CANCELLED") {
                assertTrue(error is CancellationException)
                assertNotNull(requestCheck)
                assertEquals(requestCheck, checks) // No later cancellation checkpoint consumed after termination.
            } else if (expected == "INPUT_IO_ERROR") {
                assertEquals(expected, category)
            } else if (expected != null) {
                assertEquals("LIMIT_EXCEEDED", category)
                if (expected != "G01") assertTrue(outcome, outcome.endsWith(":" + expected))
            } else throw error
            assertFalse("No accepted partial result", accepted)
            assertNull(result)
            return null
        } finally {
            val after = runtime.totalMemory() - runtime.freeMemory()
            val allocationAfter = allocated()
            val delta = if (allocationBefore != null && allocationAfter != null && allocationAfter >= allocationBefore)
                (allocationAfter - allocationBefore).toString() else "UNKNOWN"
            val v = result?.verification
            val m = v?.metrics
            Log.i(TAG, "RECORD scenario=${case.id} chunk=$chunk api=${Build.VERSION.SDK_INT} build=${Build.FINGERPRINT}" +
                " provider=${v?.provider ?: "UNKNOWN"} config=${v?.configuration ?: "UNKNOWN"}" +
                " limits=G01:16777216,G02:128,G03:4096,G04:100000,G05:32,G06:64,G07:2000000,G08:1024,G09:8192,G10:128" +
                " inputBytes=${case.data.size} tracks=${case.tracks} segments=${case.segments} samples=${case.samples} oracleEvents=${case.events ?: "NOT_APPLICABLE"}" +
                " passes=${streams.size} bytesRead=${streams.joinToString(",") { it.bytes.toString() }} eof=${streams.joinToString(",") { it.eof.toString() }}" +
                " closes=${streams.joinToString(",") { it.closes.toString() }} elapsedNs=${System.nanoTime() - start}" +
                " heapBefore=$before heapAfter=$after heapMax=${runtime.maxMemory()} allocatedDelta=$delta heapPeak=UNKNOWN" +
                " deliveredUtf16=${m?.deliveredChars ?: "UNKNOWN"} events=${m?.events ?: "UNKNOWN"}" +
                " maxPiece=${m?.maxBufferedChars ?: "UNKNOWN"} maxPending=${m?.maxPendingStarts ?: "UNKNOWN"}" +
                " maxToken=${m?.maxTokenScalars ?: "UNKNOWN"} maxDepth=${m?.maxDepth ?: "UNKNOWN"} referenceState=${m?.maxReferenceState ?: "UNKNOWN"}" +
                " cancelRequestCheck=${requestCheck ?: "NOT_APPLICABLE"} cancelRequestBytes=${requestBytes ?: "NOT_APPLICABLE"} terminationCheck=$checks" +
                " OOM=${outcome == "OOM"} partialAccepted=${accepted && expected != null} outcome=$outcome")
            assertTrue("Owned sources closed for ${case.id}", streams.all { it.closes >= 1 })
        }
    }

    private fun allocated(): Long? = Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull()
    companion object { const val TAG = "D106_GPX_BOUNDARY" }
}