package org.beesearch.app.data.gpx

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpxXmlCapabilityTest {
    @Test
    fun providerConfigurationAndMinimalChainAreQualified() {
        gateRuntime()
        val result = verify(xml("<name>probe</name>"))
        assertEquals(2L, result.observedStarts)
        assertTrue(result.identity.length > 0)
        evidence("minimal", result)
    }

    @Test
    fun strictDecodingRejectsMalformedUtf8() {
        gateRuntime()
        val valid = xml("<name>x</name>")
        val malformed = valid.copyOf().also { bytes ->
            val index = bytes.indexOfFirst { it == '<'.code.toByte() }
            bytes[index] = 0xC3.toByte()
            bytes[index + 1] = 0x28
        }
        expectFailure("MALFORMED_XML") { verify(malformed) }
    }

    @Test
    fun dtdAndExternalEntityAreRejectedBeforeProviderUse() {
        gateRuntime()
        val xml = """<?xml version="1.0"?><!DOCTYPE gpx [<!ENTITY x SYSTEM "file:///d106-sentinel">]><gpx xmlns="$GPX_NS" version="1.1"><name>&x;</name></gpx>"""
        expectFailure("SECURITY_REJECTED") { verify(xml.toByteArray()) }
    }

    @Test
    fun numericReferencesRemainBoundedAndPreserveM3Origin() {
        gateRuntime()
        val longDecimal = "&#${"0".repeat(512)}65;"
        val longHex = "&#x${"0".repeat(512)}1F600;"
        val xml = gpx("<name>a$longDecimal$longHex</name><desc literal=\"a\tb\" referenced=\"a&#9;b\" />")
        val observations = mutableListOf<GpxXmlObservation>()
        val result = verify(xml, observations)
        assertTrue(result.metrics.maxReferenceState <= 64)
        val desc = observations.first { it.kind == GpxXmlObservation.Kind.START && it.name == "desc" }
        assertEquals("a b", desc.attributes.first { it.name == "literal" }.value)
        assertEquals("a\tb", desc.attributes.first { it.name == "referenced" }.value)
        evidence("references", result)
    }

    @Test
    fun utf16AndDeclarationMismatchAreHandledStrictly() {
        gateRuntime()
        val body = "<?xml version=\"1.0\" encoding=\"UTF-16\"?>" +
            "<gpx xmlns=\"$GPX_NS\" version=\"1.1\"><name>λ😀</name></gpx>"
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + body.toByteArray(Charsets.UTF_16LE)
        assertEquals(2L, verify(utf16).observedStarts)
        val mismatch = body.replace("UTF-16", "UTF-8").toByteArray(Charsets.UTF_16LE)
        expectFailure("UNSUPPORTED_ENCODING") { verify(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + mismatch) }
    }

    @Test
    fun guardedTokenBoundsRejectTextCommentCdataPiAndAttributeOverflow() {
        gateRuntime()
        val over = "a".repeat(8193)
        expectFailure("LIMIT_EXCEEDED") { verify(gpx("<name>$over</name>")) }
        expectFailure("LIMIT_EXCEEDED") { verify(gpx("<!--$over-->")) }
        expectFailure("LIMIT_EXCEEDED") { verify(gpx("<![CDATA[$over]]>")) }
        expectFailure("LIMIT_EXCEEDED") { verify(gpx("<?probe $over?>")) }
        expectFailure("LIMIT_EXCEEDED") { verify(gpx("<name value=\"$over\" />")) }
    }

    @Test
    fun chunkedInputProducesSameSemanticObservations() {
        gateRuntime()
        val bytes = gpx("<!-- c --><trk xmlns:a=\"urn:u\"><name>a&#x41;</name></trk>")
        val direct = mutableListOf<GpxXmlObservation>()
        val chunked = mutableListOf<GpxXmlObservation>()
        val directResult = verify(bytes, direct)
        val chunkedResult = AndroidGpxXmlReader.verify(
            open = { ChunkedInputStream(bytes, 1) },
            observe = { chunked += it },
        )
        assertEquals(directResult.identity, chunkedResult.identity)
        assertEquals(direct, chunked)
    }

    @Test
    fun duplicateExpandedAttributesAreRejectedButDifferentUrisRemainDistinct() {
        gateRuntime()
        val duplicate = gpx("<trk xmlns:a=\"urn:u\" xmlns:b=\"urn:u\" a:x=\"1\" b:x=\"2\" />")
        expectFailure("MALFORMED_XML") { verify(duplicate) }
        val distinct = gpx("<trk xmlns:a=\"urn:u1\" xmlns:b=\"urn:u2\" a:x=\"1\" b:x=\"2\" />")
        assertTrue(verify(distinct).observedStarts >= 2)
    }

    @Test
    fun chunkAndCancellationBoundariesDoNotPublishPartialResult() {
        gateRuntime()
        val cancelled = AtomicBoolean(false)
        var checks = 0
        val result = runCatching {
            AndroidGpxXmlReader.verify(
                open = { ByteArrayInputStream(gpx("<trk><trkseg><trkpt lat=\"1\" lon=\"2\" /></trkseg></trk>")) },
                checkCancellation = {
                    checks++
                    if (checks > 8) {
                        cancelled.set(true)
                        throw GpxXmlFailure("CANCELLED", "probe")
                    }
                },
            )
        }
        assertTrue("cancellation must terminate", result.isFailure)
        assertTrue("cancellation was not observed", cancelled.get())
        assertEquals("CANCELLED", (result.exceptionOrNull() as GpxXmlFailure).category)
    }

    @Test
    fun repeatableSourceShaAndLengthMustMatchAcrossPasses() {
        gateRuntime()
        val first = gpx("<name>first</name>")
        val second = gpx("<name>second</name>")
        var calls = 0
        expectFailure("INPUT_IO_ERROR") {
            AndroidGpxXmlReader.verify(open = {
                calls++
                ByteArrayInputStream(if (calls == 1) first else second)
            })
        }
    }

    private fun verify(bytes: ByteArray, observations: MutableList<GpxXmlObservation> = mutableListOf()): GpxXmlVerification =
        AndroidGpxXmlReader.verify(
            open = { ByteArrayInputStream(bytes) },
            observe = { observations += it },
        )

    private fun expectFailure(category: String, block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue("expected GpxXmlFailure, got $failure", failure is GpxXmlFailure)
        assertEquals(category, (failure as GpxXmlFailure).category)
    }

    private fun gateRuntime() {
        assertTrue("Slice 1 endpoint requires API 29 or API 37", Build.VERSION.SDK_INT == 29 || Build.VERSION.SDK_INT == 37)
        Log.i(TAG, "D106_XML runtime api=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.firstOrNull()}")
    }

    private fun evidence(name: String, result: GpxXmlVerification) {
        Log.i(TAG, "D106_XML probe=$name api=${result.apiLevel} provider=${result.provider} " +
            "bytes=${result.identity.length} sha=${result.identity.sha256.take(12)} " +
            "events=${result.metrics.events} maxDepth=${result.metrics.maxDepth} " +
            "maxToken=${result.metrics.maxTokenScalars} maxBuffer=${result.metrics.maxBufferedChars}")
    }

    private fun xml(content: String): ByteArray = gpx(content)

    private fun gpx(content: String): ByteArray =
        ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx xmlns=\"$GPX_NS\" version=\"1.1\" creator=\"d106\">$content</gpx>")
            .toByteArray(StandardCharsets.UTF_8)

    private companion object {
        const val TAG = "D106_XML"
        const val GPX_NS = "http://www.topografix.com/GPX/1/1"
    }

    private class ChunkedInputStream(
        private val bytes: ByteArray,
        private val chunk: Int,
    ) : InputStream() {
        private var offset = 0
        override fun read(): Int = if (offset >= bytes.size) -1 else bytes[offset++].toInt() and 0xff
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (this.offset >= bytes.size) return -1
            val count = minOf(length, chunk, bytes.size - this.offset)
            bytes.copyInto(buffer, offset, this.offset, this.offset + count)
            this.offset += count
            return count
        }
    }
}
