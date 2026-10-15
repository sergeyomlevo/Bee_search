package org.beesearch.app.data.gpx

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.beesearch.app.domain.gpx.GpxCanonicalDraft
import org.beesearch.app.domain.gpx.GpxCanonicalValues
import org.beesearch.app.domain.gpx.GpxMetadata
import org.beesearch.app.domain.gpx.GpxSample
import org.beesearch.app.domain.gpx.GpxSegment
import org.beesearch.app.domain.gpx.GpxTrack
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpxProcessorDeviceTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun a01LiteralUsesExactCanonicalGraphIdentityAndIndependentCm1Oracle() {
        val xml = a01Xml().toByteArray(StandardCharsets.UTF_8)
        val expected = a01Draft()
        val result = processor().process({ ByteArrayInputStream(xml) }) as GpxParseResult.Accepted

        assertEquals(expected, result.draft)
        assertEquals(GpxOriginalIdentity(xml.size.toLong(), sha256(xml)), result.originalIdentity)
        assertTrue(result.diagnostics.isEmpty())
        assertArrayEquals(a01Cm1(), cm1(expected))
        assertArrayEquals(a01Cm1(), cm1(result.draft))
    }

    @Test
    fun commentOnlyVariantsKeepCanonicalAndCm1ButChangeIndependentOriginalIdentity() {
        val base = a01Xml()
        val variants = listOf(base, base.replace("</gpx>", "<!--one--></gpx>"), base.replace("</trk>", "<!--two--></trk>"))
            .map { it.toByteArray(StandardCharsets.UTF_8) }
        val results = variants.map { processor().process({ ByteArrayInputStream(it) }) as GpxParseResult.Accepted }

        results.drop(1).forEach { result ->
            assertEquals(results.first().draft, result.draft)
            assertArrayEquals(cm1(results.first().draft), cm1(result.draft))
        }
        variants.zip(results).forEach { (bytes, result) ->
            assertEquals(sha256(bytes), result.originalIdentity.sha256)
            assertEquals(bytes.size.toLong(), result.originalIdentity.length)
        }
    }

    @Test
    fun chunkSizesPreserveTwoPassCanonicalResultAndOriginalIdentity() {
        val bytes = "".toByteArray(StandardCharsets.UTF_8) + GpxBoundaryCorpus.events(100).data
        val expected = processor().process({ ByteArrayInputStream(bytes) }) as GpxParseResult.Accepted
        for (chunk in listOf(1, 2, 7, 32)) {
            val source = CountingSource(bytes, chunk)
            val result = processor().process({ source.open() }) as GpxParseResult.Accepted
            assertEquals(expected.draft, result.draft)
            assertEquals(expected.originalIdentity, result.originalIdentity)
            assertEquals(2, source.streams.size)
            assertTrue(source.streams.all { it.eof && it.bytesRead == bytes.size.toLong() && it.closeCount == 1 })
            assertEquals(source.streams[0].bytesRead, source.streams[1].bytesRead)
        }
        val differing = DifferingChunkSource(bytes, listOf(1, 32))
        val differingResult = processor().process({ differing.open() }) as GpxParseResult.Accepted
        assertEquals(expected.draft, differingResult.draft)
        assertEquals(expected.originalIdentity, differingResult.originalIdentity)
        assertEquals(listOf(1, 32), differing.chunksUsed)
    }

    @Test
    fun warningsPreserveAcceptedGraphForUnsupportedContentAndTime() {
        val content = processor().process({ ByteArrayInputStream(asset("B07.xml")) }) as GpxParseResult.Accepted
        assertTrue(content.diagnostics.any { it.category.name == "UNSUPPORTED_CONTENT" && it.count > 0 })
        val time = processor().process({ ByteArrayInputStream(asset("D14-bad-time.xml")) }) as GpxParseResult.Accepted
        assertTrue(time.diagnostics.any { it.category.name == "UNSUPPORTED_TIME" && it.count > 0 })
        assertEquals(1, time.draft.tracks.single().segments.single().samples.size)
    }

    @Test
    fun repeatedEqualInputProducesSeparateAcceptedResultsWithoutMerging() {
        val bytes = a01Xml().toByteArray(StandardCharsets.UTF_8)
        val source = CountingSource(bytes, 32)
        val first = processor().process({ source.open() }) as GpxParseResult.Accepted
        val second = processor().process({ source.open() }) as GpxParseResult.Accepted
        assertEquals(first.draft, second.draft)
        assertEquals(first.originalIdentity, second.originalIdentity)
        assertEquals(4, source.streams.size)
        assertTrue(source.streams.all { it.closeCount == 1 })
    }

    @Test
    fun expectedIdentityMismatchRejectsWithSourceChangedAndNoAcceptedResult() {
        val bytes = a01Xml().toByteArray(StandardCharsets.UTF_8)
        val expected = GpxOriginalIdentity(bytes.size.toLong(), "0".repeat(64))
        val result = processor().process({ ByteArrayInputStream(bytes) }, expected)
        assertRejected(result, GpxFailureCategory.INPUT_IO_ERROR, GpxFailureReason.SOURCE_CHANGED)
    }

    @Test
    fun secondOpenMutationRejectsSourceChanged() {
        val first = a01Xml().toByteArray(StandardCharsets.UTF_8)
        val second = a01Xml().replace("lat='0'", "lat='1'").toByteArray(StandardCharsets.UTF_8)
        val source = SequenceSource(listOf(first, second))
        val result = processor().process({ source.open() })
        assertRejected(result, GpxFailureCategory.INPUT_IO_ERROR, GpxFailureReason.SOURCE_CHANGED)
        assertEquals(2, source.opens)
    }

    @Test
    fun secondPassTruncationRejectsAtFirstObservedMalformedFailure() {
        val valid = a01Xml().toByteArray(StandardCharsets.UTF_8)
        val malformed = a01Xml().removeSuffix("</gpx>").toByteArray(StandardCharsets.UTF_8)
        val source = SequenceSource(listOf(valid, malformed))
        val result = processor().process({ source.open() })
        assertRejected(result, GpxFailureCategory.MALFORMED_XML, GpxFailureReason.XML_REJECTED)
        assertEquals(2, source.opens)
    }

    @Test
    fun sourceOpenReadAndCloseFailuresMapToBoundedReasons() {
        val bytes = a01Xml().toByteArray(StandardCharsets.UTF_8)
        val open = OpeningFailureSource(bytes, failOpen = 2)
        assertRejected(processor().process({ open.open() }), GpxFailureCategory.INPUT_IO_ERROR, GpxFailureReason.SOURCE_OPEN)
        for (failPass in 1..2) {
            val read = FailureSource(bytes, failReadPass = failPass)
            assertRejected(processor().process({ read.open() }), GpxFailureCategory.INPUT_IO_ERROR, GpxFailureReason.SOURCE_READ)
        }
        for (failPass in 1..2) {
            val close = FailureSource(bytes, failClosePass = failPass)
            assertRejected(processor().process({ close.open() }), GpxFailureCategory.INPUT_IO_ERROR, GpxFailureReason.SOURCE_CLOSE)
            assertTrue(close.streams.last().closeCount >= 1)
        }
    }

    @Test
    fun malformedSecurityProfileAndLimitCategoriesRemainTyped() {
        val cases = listOf(
            Triple("malformed", "<gpx", GpxFailureCategory.MALFORMED_XML),
            Triple("security", "<!DOCTYPE gpx [<!ENTITY x 'x'>]><gpx xmlns='http://www.topografix.com/GPX/1/1' version='1.1' creator='x'></gpx>", GpxFailureCategory.SECURITY_REJECTED),
            Triple("profile", "<root xmlns='http://www.topografix.com/GPX/1/1' version='1.1' creator='x'></root>", GpxFailureCategory.UNSUPPORTED_PROFILE),
        )
        cases.forEach { (_, xml, category) ->
            val result = processor().process({ ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)) })
            assertTrue(result is GpxParseResult.Rejected)
            assertEquals(category, (result as GpxParseResult.Rejected).category)
        }
        val limit = processor().process({ ByteArrayInputStream(GpxBoundaryCorpus.token(8193).data) })
        assertRejected(limit, GpxFailureCategory.LIMIT_EXCEEDED, GpxFailureReason.G09)
        val invalidCoordinate = processor().process({ ByteArrayInputStream(GpxBoundaryCorpus.lexical(129).data) })
        assertRejected(invalidCoordinate, GpxFailureCategory.LIMIT_EXCEEDED, GpxFailureReason.G10)
    }

    @Test
    fun cancellationBeforeOpenDuringPassesBetweenPassAndTerminalNeverReturnsRejected() {
        val bytes = GpxBoundaryCorpus.events(100).data
        val before = CancellationException("before")
        val noOpen = CancellationSource(bytes)
        assertSame(before, assertThrowsCancellation { processor().process({ noOpen.open() }, checkCancellation = { throw before }) })
        assertEquals(0, noOpen.opens)

        for (pass in 1..2) {
            for (closeFailure in listOf(false, true)) {
                val source = CountingSource(bytes, 32, failClosePass = if (closeFailure) pass else null)
                val cancellation = CancellationException("pass-$pass-close-$closeFailure")
                val thrown = assertThrowsCancellation {
                    processor().process({ source.open() }, checkCancellation = {
                        if (source.streams.size == pass && source.streams.last().bytesRead >= 64) throw cancellation
                    })
                }
                assertSame(cancellation, thrown)
                assertTrue(source.streams.all { it.closeCount >= 1 })
                if (closeFailure) assertTrue(cancellation.suppressed.any { it.message == "INPUT_IO_ERROR:source-close" })
            }
        }

        val betweenCancellation = CancellationException("between")
        var attempts = 0
        val betweenStreams = ArrayList<TrackedStream>()
        val betweenThrown = assertThrowsCancellation {
            processor().process({
                attempts++
                if (attempts == 2) throw betweenCancellation
                TrackedStream(bytes, 32).also { betweenStreams += it }
            })
        }
        assertSame(betweenCancellation, betweenThrown)
        assertEquals(2, attempts)
        assertEquals(1, betweenStreams.size)
        assertTrue(betweenStreams.single().eof)
        assertEquals(1, betweenStreams.single().closeCount)

        val terminal = CountingSource(bytes, 32)
        val terminalCancellation = CancellationException("terminal")
        val terminalThrown = assertThrowsCancellation {
            processor().process({ terminal.open() }, checkCancellation = {
                if (terminal.opens == 2 && terminal.streams.all { it.closeCount == 1 }) throw terminalCancellation
            })
        }
        assertSame(terminalCancellation, terminalThrown)
        assertTrue(terminal.streams.all { it.closeCount == 1 })
    }

    @Test
    fun deterministicTwoPassMeasurementRecordsProcessorOnlyTiming() {
        val samples = 300
        val bytes = measurementXml(samples).toByteArray(StandardCharsets.UTF_8)
        val source = CountingSource(bytes, 32)
        val start = System.nanoTime()
        val result = processor().process({ source.open() })
        val elapsed = System.nanoTime() - start
        val accepted = result as GpxParseResult.Accepted
        val graphSamples = accepted.draft.tracks.sumOf { track -> track.segments.sumOf { it.samples.size } }
        assertEquals(samples, graphSamples)
        assertEquals(2, source.streams.size)
        assertTrue(source.streams.all { it.eof && it.bytesRead == bytes.size.toLong() })
        assertTrue(source.streams.all { it.closeCount == 1 })
        Log.i("D106_GPX_PROCESSOR", "RECORD scenario=S5-two-pass-measurement api=${Build.VERSION.SDK_INT} " +
            "build=${Build.FINGERPRINT} inputBytes=${bytes.size} opens=${source.opens} passes=${source.streams.size} " +
            "bytesRead=${source.streams.joinToString(",") { it.bytesRead.toString() }} samples=$samples oracleEvents=606 " +
            "elapsedNs=$elapsed heapPeak=UNKNOWN allocation=UNKNOWN actualEvents=UNKNOWN")
    }

    private fun processor() = GpxProcessor()

    private fun assertRejected(result: GpxParseResult, category: GpxFailureCategory, reason: GpxFailureReason) {
        assertTrue("expected rejected, got $result", result is GpxParseResult.Rejected)
        result as GpxParseResult.Rejected
        assertEquals(category, result.category)
        assertEquals(reason, result.boundedContext.reason)
        assertNull(result.boundedContext.address.takeIf { category == GpxFailureCategory.INPUT_IO_ERROR })
    }

    private fun assertThrowsCancellation(block: () -> Unit): CancellationException {
        val outcome = runCatching(block)
        assertNull(outcome.getOrNull())
        val thrown = outcome.exceptionOrNull()
        assertTrue("expected cancellation, got $thrown", thrown is CancellationException)
        return thrown as CancellationException
    }

    private fun asset(name: String): ByteArray = assets.open("gpx/slice3/$name").use { it.readBytes() }

    private fun cm1(draft: GpxCanonicalDraft): ByteArray = java.io.ByteArrayOutputStream().also {
        GpxCm1Encoder.write(draft, it)
    }.toByteArray()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun a01Draft() = GpxCanonicalDraft(
        GpxMetadata("fixture"),
        listOf(
            GpxTrack(
                0,
                listOf(
                    GpxSegment(
                        0,
                        listOf(
                            GpxSample(
                                0,
                                GpxCanonicalValues.latitude("0"),
                                GpxCanonicalValues.longitude("0"),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun a01Xml() = "<gpx xmlns='http://www.topografix.com/GPX/1/1' version='1.1' creator='fixture'><trk><trkseg><trkpt lat='0' lon='0'/></trkseg></trk></gpx>"

    private fun a01Cm1(): ByteArray = """
        424545475058434d0001
        00010100000003312e31
        0002010000000766697874757265
        00030000000000 00040000000000 00050000000000
        0006030000008f 00000001 00010200000084
        0001010000000130 00020000000000 00030000000000
        00040300000067 00000001 0001020000005c
        0001010000000130 0002030000004d 00000001 00010200000042
        0001010000000130 0002010000000130 0003010000000130
        00040000000000 00050000000000
        000601000000074d495353494e47
        00070000000000 00080000000000
    """.filterNot { it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun measurementXml(samples: Int): String = buildString {
        append("<gpx xmlns='http://www.topografix.com/GPX/1/1' version='1.1' creator='measurement'><trk><trkseg>")
        repeat(samples) { append("<trkpt lat='1' lon='2'></trkpt>") }
        append("</trkseg></trk></gpx>")
    }

    private open class CountingSource(
        private val bytes: ByteArray,
        private val chunk: Int,
        private val failClosePass: Int? = null,
    ) {
        val streams = ArrayList<TrackedStream>()
        var opens = 0
            private set
        open fun open(): InputStream = TrackedStream(bytes, chunk, ++opens == failClosePass).also { streams += it }
    }

    private class SequenceSource(private val values: List<ByteArray>) {
        var opens = 0
            private set
        fun open(): InputStream = ByteArrayInputStream(values[opens++])
    }

    private class DifferingChunkSource(private val bytes: ByteArray, private val chunks: List<Int>) {
        val chunksUsed = ArrayList<Int>()
        private var opens = 0
        fun open(): InputStream {
            val chunk = chunks[opens++]
            chunksUsed += chunk
            return TrackedStream(bytes, chunk)
        }
    }

    private class OpeningFailureSource(private val bytes: ByteArray, private val failOpen: Int) {
        var opens = 0
            private set
        fun open(): InputStream {
            opens++
            if (opens == failOpen) throw IOException("open")
            return ByteArrayInputStream(bytes)
        }
    }

    private class FailureSource(
        private val bytes: ByteArray,
        private val failReadPass: Int? = null,
        private val failClosePass: Int? = null,
    ) {
        val streams = ArrayList<TrackedStream>()
        var opens = 0
            private set
        fun open(): InputStream {
            val pass = ++opens
            return TrackedStream(bytes, 32, pass == failClosePass, pass == failReadPass).also { streams += it }
        }
    }

    private class CancellationSource(private val bytes: ByteArray) {
        var opens = 0
            private set
        fun open(): InputStream { opens++; return ByteArrayInputStream(bytes) }
    }

    private class TrackedStream(
        bytes: ByteArray,
        private val chunk: Int,
        private val failClose: Boolean = false,
        private val failRead: Boolean = false,
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)
        var bytesRead = 0L
            private set
        var eof = false
            private set
        var closeCount = 0
            private set
        override fun read(): Int {
            if (failRead) throw IOException("read")
            val value = delegate.read()
            if (value < 0) eof = true else bytesRead++
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (failRead) throw IOException("read")
            val result = delegate.read(buffer, offset, minOf(length, chunk))
            if (result < 0) eof = true else bytesRead += result
            return result
        }
        override fun close() {
            closeCount++
            if (failClose) throw IOException("close")
            delegate.close()
        }
    }
}
