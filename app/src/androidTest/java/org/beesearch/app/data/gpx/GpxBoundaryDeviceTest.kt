package org.beesearch.app.data.gpx

import android.os.StrictMode
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.Executor
import org.beesearch.app.domain.gpx.GpxWarningCategory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual qualified XML -> canonical path, deterministic inherited-ceiling probes only. */
@RunWith(AndroidJUnit4::class)
class GpxBoundaryDeviceTest {
    private fun run(case: GpxBoundaryCorpus.Case, chunk: Int = 4096) = GpxBoundaryMeasurement(case, chunk).run()
    private fun cm1(result: GpxProfileRead): ByteArray = ByteArrayOutputStream().also {
        GpxCm1Encoder.write(result.profile.draft, it)
    }.toByteArray()

    @Test fun g02TracksIncludingEmptyBoundaryTriplet() {
        for (n in 127..129) run(GpxBoundaryCorpus.tracks(n))
    }

    @Test fun g03TotalSegmentsAcrossTracksIncludingEmptyBoundaryTriplet() {
        for (n in 4095..4097) run(GpxBoundaryCorpus.segments(n))
    }

    @Test fun g04TotalSamplesBoundaryTripletAndCanonicalMemory() {
        for (n in 99999..100001) {
            val result = run(GpxBoundaryCorpus.samples(n))
            if (n <= 100000) {
                val graph = checkNotNull(result).profile.draft
                val points = graph.tracks.flatMap { it.segments }.flatMap { it.samples }
                assertEquals(n, points.size)
                assertTrue(points.all { it.lat.canonicalPlain == "1" && it.lon.canonicalPlain == "2" })
            }
        }
    }

    @Test fun g08SupplementaryUnicodeWholeValueBoundaryTriplet() {
        for (n in 1023..1025) run(GpxBoundaryCorpus.text(n), 1)
    }

    @Test fun g05G06IgnoredDepthAndLexicalAttributesFullTriplets() {
        for (n in 31..33) run(GpxBoundaryCorpus.depth(n), 1)
        for (n in 63..65) run(GpxBoundaryCorpus.attributes(n), 1)
    }

    @Test fun b05G07IndependentLogicalEventTripletAndChunkInvariance() {
        val baseline = checkNotNull(run(GpxBoundaryCorpus.events(8)))
        for (n in listOf(9, 10)) { assertArrayEquals(cm1(baseline), cm1(checkNotNull(run(GpxBoundaryCorpus.events(n))))) }
        for (n in 1999999..2000001) {
            val result = run(GpxBoundaryCorpus.events(n))
            if (n <= 2000000) assertArrayEquals(cm1(baseline), cm1(checkNotNull(result)))
        }
        // One-byte source fragmentation must not change normalized event accounting.
        val fragmented = checkNotNull(run(GpxBoundaryCorpus.events(2000000), 1))
        assertArrayEquals(cm1(baseline), cm1(fragmented))
        assertNotEquals(baseline.verification.identity, fragmented.verification.identity)
    }

    @Test fun g09G10DecodedTokenAndBeforeTrimLexicalTriplets() {
        for (n in 8191..8193) run(GpxBoundaryCorpus.token(n), 3)
        for (n in 127..129) run(GpxBoundaryCorpus.lexical(n), 1)
        val reference = checkNotNull(run(GpxBoundaryCorpus.reference(1000000), 7))
        assertEquals("1", reference.profile.draft.tracks[0].segments[0].samples[0].lat.canonicalPlain)
        assertTrue(reference.verification.metrics.maxReferenceState <= 4)
        assertTrue(reference.verification.metrics.maxBufferedChars < 1024)
    }

    @Test fun g01RawOriginalByteBoundaryTripletIncludingBoundedComments() {
        for (n in 16777215..16777217) run(GpxBoundaryCorpus.rawBytes(n))
    }

    @Test fun nearCeilingCancellationEachPassAndLateEofCloseFailurePublishNothing() {
        val case = GpxBoundaryCorpus.samples(100000)
        for (pass in 1..2) {
            val measurement = GpxBoundaryMeasurement(case.copy(id = "G04-cancel-pass$pass"))
            val result = measurement.run(cancel = {
                it.streams.size == pass && it.streams.last().bytes >= case.data.size * 9L / 10
            }, expected = "CANCELLED")
            assertNull(result)
            assertEquals(pass, measurement.streams.size)
        }
        // Cancellation after complete verified input/close, at profile finish: no public graph.
        GpxBoundaryMeasurement(case.copy(id = "G04-cancel-after-close")).run(cancel = {
            it.streams.size == 2 && it.streams.all { s -> s.eof && s.closes > 0 }
        }, expected = "CANCELLED")
        for (close in listOf(false, true)) {
            GpxBoundaryMeasurement(case.copy(id = "G04-late-" + if (close) "close" else "EOF")).run(setup = { source, pass ->
                if (pass == 2) { source.failAtEof = !close; source.failClose = close }
            }, expected = "INPUT_IO_ERROR")
        }
    }

    @Test fun f09IgnoredSchemaStylesheetXIncludeObservedWithoutExternalIo() {
        // Warm the same production path before enabling parser-thread I/O observation.
        val baseline = checkNotNull(run(GpxBoundaryCorpus.events(8)))
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 250
            val target = "http://127.0.0.1:${server.localPort}/never-resolve"
            val xml = "<?xml-stylesheet href='$target'?>" +
                "<gpx xmlns='http://www.topografix.com/GPX/1/1' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'" +
                " version='1.1' creator='boundary' xsi:schemaLocation='$target'>" +
                "<trk><trkseg><trkpt lat='1' lon='2'/></trkseg></trk>" +
                "<xi:include xmlns:xi='http://www.w3.org/2001/XInclude' href='$target'/></gpx>"
            val data = xml.toByteArray()
            val violations = ArrayList<String>()
            val previous = StrictMode.getThreadPolicy()
            val policy = StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyListener(Executor { it.run() }) { violations.add(it.javaClass.simpleName) }.build()
            // Observe parser work only; resource logging/fixture construction are outside this interval.
            val parsed = try {
                StrictMode.setThreadPolicy(policy)
                AndroidGpxProfileReader.parse({ data.inputStream() })
            } finally { StrictMode.setThreadPolicy(previous) }
            assertArrayEquals(cm1(baseline), cm1(parsed))
            assertTrue(parsed.profile.warnings.any { it.category == GpxWarningCategory.UNSUPPORTED_CONTENT })
            assertTrue("Parser-thread StrictMode signals: $violations", violations.isEmpty())
            try { server.accept().use { fail("XML-controlled connection") } }
            catch (_: SocketTimeoutException) { /* finite local observer, structural prevention remains primary */ }
            Log.i(GpxBoundaryMeasurement.TAG, "F09 api=${parsed.verification.apiLevel} provider=${parsed.verification.provider}" +
                " config=${parsed.verification.configuration} inputBytes=${data.size} passes=2" +
                " bytes1=${parsed.verification.preflightMetrics.bytesRead} bytes2=${parsed.verification.metrics.bytesRead}" +
                " externalConnections=0 strictModeSignals=$violations universalSyscallProof=UNKNOWN canonicalEqual=true warnings=UNSUPPORTED_CONTENT outcome=ACCEPTED")
        }
    }

    @Test fun f03TestOwnedFileCanaryIsBlockedBeforeProviderInput() {
        // Fixture creation, expected file I/O and class warmup are outside parser-thread observation.
        run(GpxBoundaryCorpus.events(8))
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val canary = java.io.File(context.cacheDir, "d106-s4-file-canary")
        assertTrue("Do not overwrite a pre-existing canary", canary.createNewFile())
        val marker = "D106_S4_FILE_MUST_NOT_RESOLVE".toByteArray()
        try {
            canary.writeBytes(marker)
            assertArrayEquals(marker, canary.readBytes())
            val target = canary.toURI().toASCIIString()
            val xml = "<!DOCTYPE gpx [<!ENTITY c SYSTEM '$target'>]>" +
                "<gpx xmlns='http://www.topografix.com/GPX/1/1' version='1.1' creator='boundary'>" +
                "<metadata><name>&c;</name></metadata>" +
                "<trk><trkseg><trkpt lat='1' lon='2'/></trkseg></trk></gpx>"
            val data = xml.toByteArray()
            var opens = 0
            var closes = 0
            val violations = ArrayList<String>()
            val previous = StrictMode.getThreadPolicy()
            val policy = StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyListener(Executor { it.run() }) { violations.add(it.javaClass.simpleName) }.build()
            val start = System.nanoTime()
            val failure = try {
                StrictMode.setThreadPolicy(policy)
                assertThrows(GpxXmlFailure::class.java) {
                    AndroidGpxProfileReader.parse({
                        opens++
                        object : java.io.ByteArrayInputStream(data) {
                            override fun close() { closes++; super.close() }
                        }
                    })
                }
            } finally { StrictMode.setThreadPolicy(previous) }
            assertEquals("SECURITY_REJECTED", failure.category)
            assertEquals("DECLARATION_BARRIER", failure.reason)
            assertEquals(1, opens) // Preflight rejects before provider-facing second pass opens.
            assertTrue(closes >= 1)
            assertTrue("XML-controlled file/network I/O signals: $violations", violations.isEmpty())
            assertArrayEquals(marker, canary.readBytes())
            Log.i(GpxBoundaryMeasurement.TAG, "F03 api=${android.os.Build.VERSION.SDK_INT}" +
                " inputBytes=${data.size} passes=$opens closes=$closes elapsedNs=${System.nanoTime() - start}" +
                " testOwnedCanary=true contentsUnchanged=true strictModeSignals=$violations" +
                " providerInputConsumed=false partialAccepted=false outcome=SECURITY_REJECTED:DECLARATION_BARRIER" +
                " universalSyscallProof=UNKNOWN")
        } finally { assertTrue("Remove only the operation-owned canary", canary.delete()) }
    }
}