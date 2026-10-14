package org.beesearch.app.data.gpx

import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM boundary triplets; fixed-buffer drains avoid provider or XML-parser claims. */
class GpxXmlBoundaryTest {
    private fun drain(guard: GpxXmlGuard): Long {
        val buffer = CharArray(4096)
        var total = 0L
        while (true) {
            val count = guard.read(buffer, 0, buffer.size)
            if (count < 0) return total
            total += count
        }
    }

    private fun guarded(xml: Reader, limits: GpxXmlLimits = GpxXmlLimits(), metrics: GpxXmlMetrics = GpxXmlMetrics()): GpxXmlMetrics {
        GpxXmlGuard(xml, limits = limits, metrics = metrics, collectStarts = false).use(::drain)
        return metrics
    }

    @Test
    fun g01RawInputAcceptsExactSixteenMiBAndRejectsOneByteOverWithStableSha() {
        val limit = 16L * 1024 * 1024
        val below = GpxRawInput(RepeatingInputStream(limit - 1), GpxXmlLimits(bytes = limit), GpxXmlMetrics()) {}
        val belowBuffer = ByteArray(64 * 1024)
        while (below.read(belowBuffer) >= 0) Unit
        assertEquals(limit - 1, below.count)
        assertEquals(repeatingSha(limit - 1), below.digestHex())

        val exact = GpxRawInput(RepeatingInputStream(limit), GpxXmlLimits(bytes = limit), GpxXmlMetrics()) {}
        val buffer = ByteArray(64 * 1024)
        while (exact.read(buffer) >= 0) Unit
        assertEquals(limit, exact.count)
        assertEquals(repeatingSha(limit), exact.digestHex())

        val over = GpxRawInput(RepeatingInputStream(limit + 1), GpxXmlLimits(bytes = limit), GpxXmlMetrics()) {}
        val error = assertThrows(GpxXmlFailure::class.java) {
            while (over.read(buffer) >= 0) Unit
        }
        assertEquals("LIMIT_EXCEEDED", error.category)
        assertEquals(limit, over.count)
    }

    @Test
    fun g05DepthIncludesRootAndIgnoredNestedElements() {
        guarded(StringReader("<e>".repeat(31) + "</e>".repeat(31)))
        guarded(StringReader("<e>".repeat(32) + "</e>".repeat(32)))
        val error = assertThrows(GpxXmlFailure::class.java) {
            guarded(StringReader("<e>".repeat(33) + "</e>".repeat(33)))
        }
        assertEquals("LIMIT_EXCEEDED", error.category)
        assertEquals("G05", error.reason)
    }

    @Test
    fun g06AttributesIncludeNamespaceDeclarations() {
        fun xml(count: Int): String = buildString {
            append("<e xmlns='urn:x'")
            repeat(count - 1) { append(" a").append(it).append("='v'") }
            append("/>")
        }
        guarded(StringReader(xml(63)))
        guarded(StringReader(xml(64)))
        val error = assertThrows(GpxXmlFailure::class.java) { guarded(StringReader(xml(65))) }
        assertEquals("LIMIT_EXCEEDED", error.category)
        assertEquals("G06", error.reason)
    }

    @Test
    fun g07LogicalEventTripletUsesIndependentFormulaAndNoXmlDeclaration() {
        for (target in listOf(1_999_999L, 2_000_000L)) {
            val metrics = guarded(EventFormulaReader(target))
            assertEquals(target, metrics.events)
        }
        val error = assertThrows(GpxXmlFailure::class.java) { guarded(EventFormulaReader(2_000_001L)) }
        assertEquals("LIMIT_EXCEEDED", error.category)
        assertEquals("G07", error.reason)
    }

    @Test
    fun g09TokenTripletsCoverTextSupplementaryCdataReferencesAndAttributes() {
        for (count in listOf(8191, 8192)) guarded(StringReader("<e>" + "😀".repeat(count) + "</e>"))
        var error = assertThrows(GpxXmlFailure::class.java) {
            guarded(StringReader("<e>" + "😀".repeat(8193) + "</e>"))
        }
        assertEquals("G09", error.reason)

        guarded(StringReader("<e>" + "a".repeat(8191) + "&#65;</e>"))
        guarded(StringReader("<e>" + "a".repeat(8191) + "<![CDATA[x]]></e>"))
        error = assertThrows(GpxXmlFailure::class.java) {
            guarded(StringReader("<e>" + "a".repeat(8192) + "&#65;</e>"))
        }
        assertEquals("G09", error.reason)
        error = assertThrows(GpxXmlFailure::class.java) {
            guarded(StringReader("<e>" + "a".repeat(8192) + "<![CDATA[x]]></e>"))
        }
        assertEquals("G09", error.reason)

        fun attribute(count: Int): String = "<e a='" + "a".repeat(count) + "'/>"
        guarded(StringReader(attribute(8191)))
        guarded(StringReader(attribute(8192)))
        error = assertThrows(GpxXmlFailure::class.java) { guarded(StringReader(attribute(8193))) }
        assertEquals("G09", error.reason)
        assertTrue(error.category == "LIMIT_EXCEEDED")
    }

    private fun repeatingSha(count: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val block = ByteArray(64 * 1024) { 'a'.code.toByte() }
        var remaining = count
        while (remaining > 0) {
            val amount = minOf(remaining, block.size.toLong()).toInt()
            digest.update(block, 0, amount)
            remaining -= amount
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private class RepeatingInputStream(private val length: Long) : InputStream() {
        private var position = 0L
        override fun read(buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= length) return -1
            val amount = minOf(size.toLong(), length - position).toInt()
            buffer.fill('a'.code.toByte(), offset, offset + amount)
            position += amount
            return amount
        }
        override fun read(): Int = if (position++ < length) 'a'.code else -1
    }

    private class EventFormulaReader(target: Long) : Reader() {
        private var phase = 0
        private var remainingEmpty = (target - 2L) / 2L
        private val odd = (target - 2L) % 2L == 1L
        private var oneChar = ""
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (oneChar.isEmpty()) {
                oneChar = when {
                    phase == 0 -> { phase++; "<e>" }
                    remainingEmpty > 0 -> { remainingEmpty--; "<x/>" }
                    odd && phase == 1 -> { phase++; "<!--c-->" }
                    phase <= 1 -> { phase = 4; "</e>" }
                    phase == 2 -> { phase = 4; "</e>" }
                    else -> return -1
                }
            }
            val amount = minOf(len, oneChar.length)
            oneChar.toCharArray().copyInto(cbuf, off, 0, amount)
            oneChar = oneChar.substring(amount)
            return amount
        }
        override fun read(): Int {
            val one = CharArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].code
        }
        override fun close() = Unit
    }
}
