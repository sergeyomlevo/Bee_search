package org.beesearch.app.data.gpx

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxInputTest {
    @Test fun rawShaMatchesIndependentKnownVector() {
        val raw = GpxRawInput(ByteArrayInputStream("abc".toByteArray()), GpxXmlLimits(), GpxXmlMetrics()) {}
        while (raw.read() >= 0) Unit
        assertEquals(3L, raw.count)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", raw.digestHex())
    }

    @Test fun sourceIoFailureDoesNotLeakUntrustedExceptionText() {
        val raw = GpxRawInput(object : InputStream() {
            override fun read(): Int = throw java.io.IOException("secret-uri-and-attacker-text")
        }, GpxXmlLimits(), GpxXmlMetrics()) {}
        val error = assertThrows(GpxXmlFailure::class.java) { raw.read() }
        assertEquals("INPUT_IO_ERROR", error.category)
        assertEquals("source-read", error.reason)
    }

    @Test
    fun utf8BomIsStrippedReaderAndHashIncludesBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + xml("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx/>")
        val input = GpxInput(ByteArrayInputStream(bytes))
        val text = input.reader.readText()
        val identity = input.identity()
        assertTrue(text.startsWith("<?xml"))
        assertEquals(bytes.size.toLong(), identity.length)
        assertEquals(sha(bytes), identity.sha256)
    }

    @Test
    fun utf16LeAndBeAreAcceptedAndBomIsNotReaderContent() {
        listOf(
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "<?xml version=\"1.0\" encoding=\"UTF-16\"?><gpx/>".toByteArray(Charsets.UTF_16LE),
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + "<?xml version=\"1.0\" encoding=\"UTF-16\"?><gpx/>".toByteArray(Charsets.UTF_16BE),
        ).forEach { bytes ->
            val input = GpxInput(ByteArrayInputStream(bytes))
            assertTrue(input.reader.readText().startsWith("<?xml"))
            assertEquals(sha(bytes), input.identity().sha256)
        }
    }

    @Test
    fun malformedUtf8FailsWithoutReplacement() {
        val bytes = byteArrayOf('<'.code.toByte(), 0xC3.toByte(), 0x28)
        assertThrows(GpxXmlFailure::class.java) { GpxInput(ByteArrayInputStream(bytes)) }
    }

    @Test
    fun utf16WithoutBomAndDeclarationMismatchAreRejected() {
        val noBom = "<?xml version=\"1.0\"?><gpx/>".toByteArray(Charsets.UTF_16LE)
        val mismatch = xml("<?xml version=\"1.0\" encoding=\"UTF-16\"?><gpx/>")
        assertEquals("UNSUPPORTED_ENCODING", assertThrows(GpxXmlFailure::class.java) {
            GpxInput(ByteArrayInputStream(noBom))
        }.category)
        assertEquals("UNSUPPORTED_ENCODING", assertThrows(GpxXmlFailure::class.java) {
            GpxInput(ByteArrayInputStream(mismatch))
        }.category)
    }

    @Test
    fun exactLimitIsAcceptedAndOneByteOverIsRejected() {
        val bytes = xml("<gpx/>")
        GpxInput(ByteArrayInputStream(bytes), GpxXmlLimits(bytes = bytes.size.toLong())).use {
            it.reader.readText()
            it.identity()
        }
        assertEquals("LIMIT_EXCEEDED", assertThrows(GpxXmlFailure::class.java) {
            val input = GpxInput(ByteArrayInputStream(bytes + byteArrayOf(1)), GpxXmlLimits(bytes = bytes.size.toLong()))
            input.reader.readText()
        }.category)
    }

    @Test
    fun identityRequiresRealEof() {
        val input = GpxInput(ByteArrayInputStream(xml("<gpx/>")))
        assertThrows(GpxXmlFailure::class.java) { input.identity() }
    }

    @Test
    fun cancellationPropagates() {
        val cancellation = CancellationException("stop")
        assertThrows(CancellationException::class.java) {
            GpxInput(ByteArrayInputStream(xml("<gpx/>"))) {
                throw cancellation
            }
        }
    }

    @Test
    fun shortAndZeroReadStreamStillDecodes() {
        val bytes = xml("<?xml version=\"1.0\"?><gpx/>")
        val input = GpxInput(ChunkedInputStream(bytes))
        assertEquals("<?xml version=\"1.0\"?><gpx/>", input.reader.readText())
        assertEquals(sha(bytes), input.identity().sha256)
    }

    private fun xml(value: String): ByteArray = value.toByteArray(StandardCharsets.UTF_8)

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class ChunkedInputStream(private val bytes: ByteArray) : InputStream() {
        private var position = 0
        private var zero = true

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            if (zero) {
                zero = false
                return 0
            }
            zero = true
            val count = minOf(2, length, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }

        override fun read(): Int = if (position < bytes.size) bytes[position++].toInt() and 0xFF else -1
    }
}
