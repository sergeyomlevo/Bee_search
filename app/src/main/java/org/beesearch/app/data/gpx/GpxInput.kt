package org.beesearch.app.data.gpx

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * The raw-byte and encoding boundary for Slice 1.
 *
 * The supplied stream is read once. Raw bytes are counted and digested before
 * BOM removal or any decoding/normalisation. [identity] becomes available only
 * after the decoder has observed real EOF.
 */
internal class GpxInput(
    source: InputStream,
    private val limits: GpxXmlLimits = GpxXmlLimits(),
    private val metrics: GpxXmlMetrics = GpxXmlMetrics(),
    private val checkCancellation: () -> Unit = {},
) : Closeable {
    private val raw = GpxRawInput(source, limits, metrics, checkCancellation)
    private val prefix: ByteArray
    private val bomLength: Int
    private val charset: Charset
    private val decodedReader: Reader
    private var complete = false
    private var closed = false
    private var finalIdentity: GpxOriginalIdentity? = null

    val reader: Reader

    init {
        try {
            require(limits.bytes > 0) { "bytes limit must be positive" }
            val sniffed = sniff(raw, limits, checkCancellation)
            prefix = sniffed.bytes
            bomLength = sniffed.bomLength
            charset = sniffed.charset
            validateXmlDeclaration(sniffed.decodedWithoutBom, sniffed.charset, sniffed.bomLength, limits.token)

            val replay = SequenceInputStream(
                ByteArrayInputStream(prefix, bomLength, prefix.size - bomLength),
                raw,
            )
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decodedReader = object : InputStreamReader(replay, decoder) {
                private val single = CharArray(1)

                override fun read(): Int {
                    val count = read(single, 0, 1)
                    return if (count < 0) -1 else single[0].code
                }

                override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                    checkCancellation()
                    return try {
                        val count = super.read(cbuf, off, len)
                        if (count < 0) {
                            complete = true
                        } else {
                            metrics.decodedChars = metrics.decodedChars.checkedAdd(count.toLong())
                        }
                        checkCancellation()
                        count
                    } catch (failure: GpxXmlFailure) {
                        throw failure
                    } catch (failure: CharacterCodingException) {
                        gpxFail("MALFORMED_XML", "invalid encoding")
                    }
                }

                override fun close() {
                    super.close()
                    closed = true
                }
            }
            reader = decodedReader
        } catch (failure: Throwable) {
            runCatching { raw.close() }
            throw failure
        }
    }

    fun identity(): GpxOriginalIdentity {
        checkCancellation()
        if (!complete) {
            gpxFail("INPUT_IO_ERROR", "identity requested before EOF")
        }
        return finalIdentity ?: GpxOriginalIdentity(raw.count, raw.digestHex()).also {
            finalIdentity = it
        }
    }

    override fun close() {
        if (!closed) {
            decodedReader.close()
            closed = true
        }
    }
}

private data class SniffedInput(
    val bytes: ByteArray,
    val bomLength: Int,
    val charset: Charset,
    val decodedWithoutBom: String,
)

private fun sniff(
    raw: GpxRawInput,
    limits: GpxXmlLimits,
    checkCancellation: () -> Unit,
): SniffedInput {
    val bytes = ArrayList<Byte>(minOf(limits.token * 4 + 16, 64 * 1024))
    val initial = readAtMost(raw, bytes, 16, checkCancellation)
    if (initial == 0) gpxFail("MALFORMED_XML", "empty input")

    // Unsupported UTF32 must be recognized before its LE BOM is mistaken for UTF16.
    if (bytes.startsWith(byteArrayOf(0xFF.toByte(),0xFE.toByte(),0,0)) ||
        bytes.startsWith(byteArrayOf(0,0,0xFE.toByte(),0xFF.toByte())) ||
        bytes.startsWith(byteArrayOf(0x3C,0,0,0)) ||
        bytes.startsWith(byteArrayOf(0,0,0,0x3C))) {
        gpxFail("UNSUPPORTED_ENCODING", "UTF-32")
    }
    val (bomLength, charset) = when {
        bytes.startsWith(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) ->
            3 to Charsets.UTF_8
        bytes.startsWith(byteArrayOf(0xFF.toByte(), 0xFE.toByte())) ->
            2 to Charsets.UTF_16LE
        bytes.startsWith(byteArrayOf(0xFE.toByte(), 0xFF.toByte())) ->
            2 to Charsets.UTF_16BE
        bytes.size >= 2 && ((bytes[0] == 0.toByte() && bytes[1] == 0x3C.toByte()) ||
            (bytes[0] == 0x3C.toByte() && bytes[1] == 0.toByte())) ->
            gpxFail("UNSUPPORTED_ENCODING", "UTF-16 requires a BOM")
        else -> 0 to Charsets.UTF_8
    }

    val maxSniffBytes = try {
        Math.toIntExact(Math.addExact(Math.multiplyExact(limits.token.toLong().checkedAdd(1L), 4L), 4L))
    } catch (_: ArithmeticException) {
        gpxFail("LIMIT_EXCEEDED", "XML declaration sniff bound overflows")
    }
    var decoded = decodeSniffWithRetry(bytes, bomLength, charset, maxSniffBytes, raw, checkCancellation)
    val looksLikeDeclaration = decoded.isXmlDeclarationPrefix()
    while (looksLikeDeclaration && !decoded.contains("?>")) {
        if (decoded.codePointCount(0, decoded.length) - 4 > limits.token) {
            gpxFail("LIMIT_EXCEEDED", "XML declaration exceeds token limit")
        }
        if (bytes.size >= maxSniffBytes) {
            gpxFail("LIMIT_EXCEEDED", "XML declaration exceeds sniff bound")
        }
        if (readAtMost(raw, bytes, minOf(256, maxSniffBytes - bytes.size), checkCancellation) == 0) {
            gpxFail("MALFORMED_XML", "unterminated XML declaration")
        }
        decoded = decodeSniffWithRetry(bytes, bomLength, charset, maxSniffBytes, raw, checkCancellation)
    }
    return SniffedInput(bytes.toByteArray(), bomLength, charset, decoded)
}

private fun readAtMost(
    raw: GpxRawInput,
    target: MutableList<Byte>,
    max: Int,
    checkCancellation: () -> Unit,
): Int {
    if (max <= 0) return 0
    var read = 0
    while (read < max) {
        checkCancellation()
        val value = raw.read()
        checkCancellation()
        if (value < 0) break
        target += value.toByte()
        read++
    }
    return read
}

private fun decodeSniff(bytes: List<Byte>, bomLength: Int, charset: Charset): String {
    val data = ByteArray(bytes.size - bomLength) { index -> bytes[index + bomLength] }
    return try {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(data)).toString()
    } catch (_: CharacterCodingException) {
        gpxFail("MALFORMED_XML", "invalid encoding")
    }
}

private fun decodeSniffWithRetry(
    bytes: MutableList<Byte>,
    bomLength: Int,
    charset: Charset,
    maxSniffBytes: Int,
    raw: GpxRawInput,
    checkCancellation: () -> Unit,
): String {
    while (true) {
        try {
            return decodeSniff(bytes, bomLength, charset)
        } catch (_: GpxXmlFailure) {
            if (bytes.size >= maxSniffBytes || !hasIncompleteTrailingEncoding(bytes, bomLength, charset)) {
                gpxFail("MALFORMED_XML", "invalid encoding")
            }
            if (readAtMost(raw, bytes, 1, checkCancellation) == 0) {
                gpxFail("MALFORMED_XML", "truncated encoding")
            }
        }
    }
}

private fun hasIncompleteTrailingEncoding(bytes: List<Byte>, bomLength: Int, charset: Charset): Boolean {
    val size = bytes.size - bomLength
    if (charset == Charsets.UTF_16LE || charset == Charsets.UTF_16BE) {
        if (size % 2 != 0) return true
        if (size == 0) return false
        val lastCodeUnit = if (charset == Charsets.UTF_16LE) {
            (bytes[bytes.size - 2].toInt() and 0xFF) or
                ((bytes[bytes.size - 1].toInt() and 0xFF) shl 8)
        } else {
            ((bytes[bytes.size - 2].toInt() and 0xFF) shl 8) or
                (bytes[bytes.size - 1].toInt() and 0xFF)
        }
        return lastCodeUnit in 0xD800..0xDBFF
    }
    if (size == 0) return false
    var index = bytes.size - 1
    while (index >= bomLength && (bytes[index].toInt() and 0xC0) == 0x80) index--
    if (index < bomLength) return false
    val lead = bytes[index].toInt() and 0xFF
    val width = when {
        lead in 0xC2..0xDF -> 2
        lead in 0xE0..0xEF -> 3
        lead in 0xF0..0xF4 -> 4
        else -> return false
    }
    return bytes.size - index < width
}

private fun validateXmlDeclaration(text: String, charset: Charset, bomLength: Int, tokenLimit: Int) {
    if (!text.isXmlDeclarationPrefix()) return
    val end = text.indexOf("?>")
    if (end < 0) gpxFail("MALFORMED_XML", "unterminated XML declaration")
    if (text.codePointCount(0, end + 2) - 4 > tokenLimit) {
        gpxFail("LIMIT_EXCEEDED", "XML declaration exceeds token limit")
    }
    val body = text.substring(5, end)
    var index = 0
    fun skipWhitespace() {
        while (index < body.length && body[index].isXmlWhitespace()) index++
    }
    fun readName(): String {
        val start = index
        while (index < body.length && body[index].isLetterOrDigit().or(body[index] == '_' || body[index] == ':' || body[index] == '-' || body[index] == '.')) index++
        if (start == index) gpxFail("MALFORMED_XML", "invalid XML declaration")
        return body.substring(start, index)
    }
    fun readQuoted(): String {
        if (index >= body.length || (body[index] != '\'' && body[index] != '"')) {
            gpxFail("MALFORMED_XML", "invalid XML declaration")
        }
        val quote = body[index++]
        val start = index
        while (index < body.length && body[index] != quote) index++
        if (index >= body.length) gpxFail("MALFORMED_XML", "invalid XML declaration")
        return body.substring(start, index++)
    }

    skipWhitespace()
    val seen = HashSet<String>()
    var ordinal = 0
    while (index < body.length) {
        val name = readName()
        if (!seen.add(name)) gpxFail("MALFORMED_XML", "duplicate XML declaration field")
        skipWhitespace()
        if (index >= body.length || body[index++] != '=') gpxFail("MALFORMED_XML", "invalid XML declaration")
        skipWhitespace()
        val value = readQuoted()
        when (name) {
            "version" -> {
                if (ordinal != 0) gpxFail("MALFORMED_XML", "version is not first")
                when (value) {
                    "1.0" -> Unit
                    "1.1" -> gpxFail("UNSUPPORTED_PROFILE", "XML 1.1")
                    else -> gpxFail("MALFORMED_XML", "unsupported XML version")
                }
            }
            "encoding" -> {
                if (ordinal != 1 || seen.contains("standalone")) {
                    gpxFail("MALFORMED_XML", "encoding is out of order")
                }
                val normalized = value.uppercase(Locale.ROOT)
                val expected = if (charset == Charsets.UTF_8) "UTF-8" else "UTF-16"
                if (normalized != expected) gpxFail("UNSUPPORTED_ENCODING", "encoding declaration mismatch")
            }
            "standalone" -> {
                if (ordinal < 1 || ordinal > 2 || value != "yes" && value != "no") {
                    gpxFail("MALFORMED_XML", "invalid standalone declaration")
                }
            }
            else -> gpxFail("MALFORMED_XML", "unknown XML declaration field")
        }
        ordinal++
        skipWhitespace()
    }
    if (ordinal == 0 || !seen.contains("version")) {
        gpxFail("MALFORMED_XML", "XML declaration version is required")
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun List<Byte>.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun Char.isXmlWhitespace(): Boolean = this == ' ' || this == '\t' || this == '\r' || this == '\n'

private fun String.isXmlDeclarationPrefix(): Boolean =
    startsWith("<?xml") && (length == 5 || getOrNull(5)?.isXmlWhitespace() == true)

private fun Long.checkedAdd(value: Long): Long = Math.addExact(this, value)
private fun Long.checkedMultiply(value: Long): Long = Math.multiplyExact(this, value)
