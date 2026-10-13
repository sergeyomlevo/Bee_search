package org.beesearch.app.data.gpx

import java.io.Reader
import java.util.ArrayDeque

/**
 * Bounded lexical transducer. Complete bounded lexical pieces are classified before
 * delivery, one UTF16 code unit per read. No tree/namespace/profile/canonical ownership.
 */
internal class GpxXmlGuard(
    input: Reader,
    private val limits: GpxXmlLimits = GpxXmlLimits(),
    private val metrics: GpxXmlMetrics = GpxXmlMetrics(),
    private val checkCancellation: () -> Unit = {},
    private val collectStarts: Boolean = true,
) : Reader() {
    private val lexical = GpxXmlLexical(input, checkCancellation, metrics)
    private val underlying = input
    private val starts = ArrayDeque<GpxStartFacts>()
    val semantics = GpxXmlSemantics(collectStarts)
    private var piece = ""
    private var offset = 0
    private var depth = 0
    private var textCount = 0
    private var textActive = false
    var failure: GpxXmlFailure? = null
        private set
    val pendingStartCount: Int get() = starts.size

    fun takeStartFacts(): GpxStartFacts = starts.pollFirst()
        ?: gpxFail("SECURITY_CONFIGURATION_UNAVAILABLE", "GUARD_SDK_START_FACTS")

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        require(off >= 0 && len >= 0 && off <= cbuf.size - len)
        if (len == 0) return 0
        checkCancellation()
        failure?.let { throw it }
        try {
            while (offset == piece.length) {
                if (lexical.peek() < 0) return -1
                piece = nextPiece()
                offset = 0
                metrics.maxBufferedChars = maxOf(metrics.maxBufferedChars, piece.length)
            }
            cbuf[off] = piece[offset++]
            metrics.deliveredChars++
            return 1
        } catch (e: GpxXmlFailure) {
            failure = e
            throw e
        }
    }

    override fun close() = underlying.close()

    private fun event(amount: Int = 1) {
        if (metrics.events > limits.events - amount) gpxFail("LIMIT_EXCEEDED", "G07")
        metrics.events += amount
    }

    private fun token(count: Int) {
        if (count > limits.token) gpxFail("LIMIT_EXCEEDED", "G09")
        metrics.maxTokenScalars = maxOf(metrics.maxTokenScalars, count)
    }

    private fun textScalar() {
        if (!textActive) { event(); textActive = true }
        token(++textCount)
    }

    private fun boundary() { textActive = false; textCount = 0 }

    private fun nextPiece(): String {
        if (lexical.peek() != 60) return text()
        lexical.take()
        return when (lexical.peek()) {
            33 -> declaration()
            63 -> instruction()
            47 -> endTag()
            else -> startTag()
        }
    }

    private fun text(): String {
        val out = StringBuilder()
        var literalTail = ""
        val decoded = StringBuilder()
        while (lexical.peek() >= 0 && lexical.peek() != 60) {
            checkCancellation()
            if (lexical.peek() == 38) {
                lexical.take()
                val ref = lexical.reference()
                textScalar()
                out.append(ref.second)
                decoded.appendCodePoint(ref.first)
                literalTail = ""
            } else {
                val c = lexical.take()
                textScalar()
                out.appendCodePoint(c)
                decoded.appendCodePoint(c)
                literalTail = (literalTail + String(Character.toChars(c))).takeLast(3)
                if (literalTail == "]]>") gpxFail("MALFORMED_XML", "CDATA_END_IN_TEXT")
            }
        }
        semantics.expected('T', decoded.toString())
        return out.toString()
    }

    private fun declaration(): String {
        lexical.expect(33)
        return when (lexical.take()) {
            45 -> {
                lexical.expect(45)
                val body = until("-->")
                if (body.contains("--") || body.endsWith("-"))
                    gpxFail("MALFORMED_XML", "COMMENT_SYNTAX")
                boundary(); event()
                semantics.expected('C', body)
                "<!--" + body + "-->"
            }
            91 -> {
                for (c in "CDATA[") lexical.expect(c.code)
                val body = until("]]>")
                body.codePoints().forEach { textScalar() }
                semantics.expected('T', body)
                "<![CDATA[" + body + "]]>"
            }
            else -> gpxFail("SECURITY_REJECTED", "DECLARATION_BARRIER")
        }
    }

    /** Only bounded body plus fixed delimiter lookahead is retained. */
    private fun until(delimiter: String): String {
        val out = StringBuilder()
        var scalars = 0
        while (true) {
            checkCancellation()
            val c = lexical.take()
            if (c < 0) gpxFail("MALFORMED_XML", "UNTERMINATED_TOKEN")
            out.appendCodePoint(c)
            scalars++
            if (out.endsWith(delimiter)) {
                out.setLength(out.length - delimiter.length)
                token(scalars - delimiter.length)
                return out.toString()
            }
            if (scalars > limits.token + delimiter.length)
                gpxFail("LIMIT_EXCEEDED", "G09")
        }
    }

    private fun instruction(): String {
        lexical.expect(63)
        val name = lexical.name(limits.token)
        val separator = lexical.peek() == 32 || lexical.peek() == 9 || lexical.peek() == 10
        if (!separator && lexical.peek() != 63) gpxFail("MALFORMED_XML", "PI_SEPARATOR")
        val firstSpace = if (separator) String(Character.toChars(lexical.take())) else ""
        val body = firstSpace + until("?>")
        token(name.codePointCount(0, name.length) + body.codePointCount(0, body.length))
        if (name.equals("xml", ignoreCase = true) && name != "xml")
            gpxFail("MALFORMED_XML", "RESERVED_PI_TARGET")
        boundary(); event()
        if (name == "xml") metrics.declarationEvents++
        else semantics.expected('P', name + body)
        return "<?" + name + body + "?>"
    }

    private fun endTag(): String {
        lexical.expect(47)
        val name = lexical.name(limits.token)
        lexical.space()
        lexical.expect(62)
        boundary(); event()
        if (--depth < 0) gpxFail("MALFORMED_XML", "NEGATIVE_DEPTH")
        semantics.expected('E', name)
        return "</" + name + ">"
    }

    private fun startTag(): String {
        val name = lexical.name(limits.token)
        if (++depth > limits.depth) gpxFail("LIMIT_EXCEEDED", "G05")
        metrics.maxDepth = maxOf(metrics.maxDepth, depth)
        val attributes = ArrayList<GpxLexicalAttribute>()
        val seen = HashSet<String>()
        val out = StringBuilder("<").append(name)
        while (true) {
            val spaced = lexical.space()
            if (lexical.peek() == 47 || lexical.peek() == 62) break
            if (!spaced) gpxFail("MALFORMED_XML", "ATTRIBUTE_SEPARATOR")
            if (attributes.size >= limits.attributes) gpxFail("LIMIT_EXCEEDED", "G06")
            val attr = lexical.name(limits.token)
            if (!seen.add(attr)) gpxFail("MALFORMED_XML", "DUPLICATE_LEXICAL_ATTRIBUTE")
            lexical.space(); lexical.expect(61); lexical.space()
            val quote = lexical.take()
            if (quote != 34 && quote != 39) gpxFail("MALFORMED_XML", "ATTRIBUTE_QUOTE")
            val value = StringBuilder()
            val encoded = StringBuilder()
            var count = 0
            while (lexical.peek() != quote) {
                checkCancellation()
                val c = lexical.take()
                if (c < 0 || c == 60) gpxFail("MALFORMED_XML", "ATTRIBUTE_VALUE")
                token(++count)
                if (c == 38) {
                    val ref = lexical.reference()
                    value.appendCodePoint(ref.first)
                    encoded.append(ref.second)
                } else {
                    val normalized = if (c == 9 || c == 10) 32 else c
                    value.appendCodePoint(normalized)
                    encoded.appendCodePoint(normalized)
                }
            }
            lexical.take()
            attributes.add(GpxLexicalAttribute(attr, value.toString()))
            out.append(' ').append(attr).append('=').append(quote.toChar())
                .append(encoded).append(quote.toChar())
        }
        val empty = lexical.peek() == 47
        if (empty) lexical.take()
        lexical.expect(62)
        boundary(); event(if (empty) 2 else 1)
        if (empty) depth--
        out.append(if (empty) "/>" else ">")
        semantics.expected('S', name)
        if (empty) semantics.expected('E', name)
        if (collectStarts) {
            // Capability guard on SDK read-ahead, not a GPX acceptance ceiling.
            // With one-unit Reader delivery KXml requires at most fixed lexical lookahead.
            if (starts.size >= 4)
                gpxFail("SECURITY_CONFIGURATION_UNAVAILABLE", "UNQUALIFIED_PROVIDER_READ_AHEAD")
            starts.addLast(GpxStartFacts(name, attributes.toList()))
            metrics.maxPendingStarts = maxOf(metrics.maxPendingStarts, starts.size)
        }
        return out.toString()
    }
}
