package org.beesearch.app.data.gpx

import java.io.Reader

/** XML 1.0 lexical primitives only; no tree, namespace bindings or GPX rules. */
internal class GpxXmlLexical(
    private val input: Reader,
    private val cancel: () -> Unit,
    private val metrics: GpxXmlMetrics,
) {
    private var cached = -2
    private var rawCached = -2

    private fun raw(): Int {
        cancel()
        val first = if (rawCached != -2) rawCached.also { rawCached = -2 } else input.read()
        if (first < 0) return -1
        if (first == 13) {
            val next = input.read()
            if (next != 10) rawCached = next
            return 10
        }
        if (first in 0xD800..0xDBFF) {
            val second = input.read()
            if (second !in 0xDC00..0xDFFF) gpxFail("MALFORMED_XML", "SURROGATE")
            return Character.toCodePoint(first.toChar(), second.toChar())
        }
        if (!xmlChar(first)) gpxFail("MALFORMED_XML", "XML_SCALAR")
        return first
    }

    fun peek(): Int {
        if (cached == -2) cached = raw()
        return cached
    }

    fun take(): Int = peek().also { cached = -2 }
    fun expect(c: Int) { if (take() != c) gpxFail("MALFORMED_XML", "LEXICAL_SYNTAX") }
    fun space(): Boolean {
        var seen = false
        while (peek() == 32 || peek() == 9 || peek() == 10) { take(); seen = true }
        return seen
    }

    fun name(maximum: Int): String {
        val result = StringBuilder()
        var count = 0
        if (!nameStart(peek())) gpxFail("MALFORMED_XML", "NAME")
        while (nameChar(peek())) {
            if (++count > maximum) gpxFail("LIMIT_EXCEEDED", "G09")
            result.appendCodePoint(take())
        }
        metrics.maxTokenScalars = maxOf(metrics.maxTokenScalars, count)
        return result.toString()
    }

    /** Called AFTER '&'. Never retains the numeric digit spelling. */
    fun reference(): Pair<Int, String> {
        if (peek() == 35) {
            take()
            val base = if (peek() == 120) { take(); 16 } else 10
            var value = 0
            var digits = 0L
            while (peek() != 59) {
                cancel()
                val c = take()
                val d = when (c) {
                    in 48..57 -> c - 48
                    in 97..102 -> if (base == 16) c - 87 else -1
                    in 65..70 -> if (base == 16) c - 55 else -1
                    else -> -1
                }
                if (d < 0 || d >= base) gpxFail("MALFORMED_XML", "REFERENCE_DIGIT")
                if (value > (0x10FFFF - d) / base) gpxFail("MALFORMED_XML", "REFERENCE_RANGE")
                value = value * base + d
                digits++
                metrics.referenceDigits++
            }
            take()
            if (digits == 0L || !xmlChar(value)) gpxFail("MALFORMED_XML", "REFERENCE_SCALAR")
            // Finite XML scalar domain: at most six hex digits. Numeric input stores no digits.
            return value to ("&#x" + value.toString(16) + ";")
        }
        val names = mapOf("amp" to 38, "lt" to 60, "gt" to 62, "apos" to 39, "quot" to 34)
        val name = StringBuilder()
        while (peek() != 59) {
            val c = take()
            if (c < 0) gpxFail("MALFORMED_XML", "REFERENCE_END")
            name.appendCodePoint(c)
            metrics.maxReferenceState = maxOf(metrics.maxReferenceState, name.length)
            if (names.keys.none { it.startsWith(name.toString()) })
                gpxFail("MALFORMED_XML", "CUSTOM_REFERENCE")
        }
        take()
        val value = names[name.toString()] ?: gpxFail("MALFORMED_XML", "CUSTOM_REFERENCE")
        return value to ("&" + name + ";")
    }

    companion object {
        fun xmlChar(c: Int): Boolean = c == 9 || c == 10 || c == 13 ||
            c in 0x20..0xD7FF || c in 0xE000..0xFFFD || c in 0x10000..0x10FFFF

        fun nameStart(c: Int): Boolean = c == 58 || c == 95 || c in 65..90 || c in 97..122 ||
            c in 0xC0..0xD6 || c in 0xD8..0xF6 || c in 0xF8..0x2FF ||
            c in 0x370..0x37D || c in 0x37F..0x1FFF || c in 0x200C..0x200D ||
            c in 0x2070..0x218F || c in 0x2C00..0x2FEF || c in 0x3001..0xD7FF ||
            c in 0xF900..0xFDCF || c in 0xFDF0..0xFFFD || c in 0x10000..0xEFFFF

        fun nameChar(c: Int): Boolean = nameStart(c) || c == 45 || c == 46 ||
            c in 48..57 || c == 0xB7 || c in 0x300..0x36F || c in 0x203F..0x2040
    }
}
