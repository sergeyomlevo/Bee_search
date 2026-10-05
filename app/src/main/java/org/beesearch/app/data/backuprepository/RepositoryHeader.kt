package org.beesearch.app.data.backuprepository

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class RepositoryHeader(val repositoryId: UUID, val variant: String)

internal object RepositoryHeaderCodec {
    private const val FORMAT = "beesearch-repository"
    private const val VERSION = 1

    fun encode(header: RepositoryHeader): ByteArray {
        requireVariant(header.variant)
        return "{\"repositoryFormat\":\"$FORMAT\",\"repositoryFormatVersion\":$VERSION,\"repositoryId\":\"${header.repositoryId}\",\"variant\":\"${header.variant}\"}".toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(bytes: ByteArray): RepositoryHeader {
        if (bytes.size > MAX_BYTES) invalid()
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (error: CharacterCodingException) {
            invalid(error)
        }
        val parser = Parser(text)
        val values = parser.objectValues()
        if (values.keys != EXPECTED_KEYS) invalid()
        if (values["repositoryFormat"] !is String || values["repositoryFormat"] != FORMAT) {
            throw RepositoryException(RepositoryError.UNSUPPORTED_FORMAT)
        }
        if (values["repositoryFormatVersion"] != 1L) {
            throw RepositoryException(RepositoryError.UNSUPPORTED_FORMAT)
        }
        val idText = values["repositoryId"] as? String ?: invalid()
        val id = try { UUID.fromString(idText) } catch (error: IllegalArgumentException) { invalid(error) }
        if (id.toString() != idText) invalid()
        val variant = values["variant"] as? String ?: invalid()
        requireVariant(variant)
        return RepositoryHeader(id, variant)
    }

    private fun requireVariant(variant: String) {
        if (variant !in setOf("Stable", "Beta", "Dev")) throw RepositoryException(RepositoryError.INVALID_HEADER)
    }

    private fun invalid(cause: Throwable? = null): Nothing = throw RepositoryException(RepositoryError.INVALID_HEADER, cause)

    private const val MAX_BYTES = 4096
    private val EXPECTED_KEYS = setOf("repositoryFormat", "repositoryFormatVersion", "repositoryId", "variant")

    private class Parser(private val source: String) {
        private var index = 0
        fun objectValues(): Map<String, Any> {
            ws(); expect('{'); ws()
            val result = linkedMapOf<String, Any>()
            if (take('}')) { ws(); if (index != source.length) invalid(); return result }
            while (true) {
                val key = string(); ws(); expect(':'); ws()
                if (!result.containsKey(key)) result[key] = value() else invalid()
                ws()
                if (take('}')) break
                expect(','); ws()
            }
            ws(); if (index != source.length) invalid()
            return result
        }
        private fun value(): Any = when {
            peek('"') -> string()
            peek('-') || (index < source.length && source[index] in '0'..'9') -> integer()
            else -> invalid()
        }
        private fun integer(): Long {
            val start = index
            take('-')
            if (index >= source.length || source[index] !in '0'..'9') invalid()
            if (take('0')) {
                if (index < source.length && source[index] in '0'..'9') invalid()
            } else {
                while (index < source.length && source[index] in '0'..'9') index++
            }
            return source.substring(start, index).toLongOrNull() ?: invalid()
        }
        private fun string(): String {
            expect('"'); val out = StringBuilder()
            while (index < source.length) {
                val c = source[index++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> escape(out)
                    else -> if (c.code < 0x20) invalid() else out.append(c)
                }
            }
            invalid()
        }
        private fun escape(out: StringBuilder) {
            if (index >= source.length) invalid()
            when (val escaped = source[index++]) {
                '"', '\\', '/' -> out.append(escaped)
                'b' -> out.append('\b')
                'f' -> out.append('\u000c')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'u' -> {
                    if (index + 4 > source.length) invalid()
                    val hex = source.substring(index, index + 4)
                    if (!hex.all { it in "0123456789abcdefABCDEF" }) invalid()
                    out.append(hex.toInt(16).toChar())
                    index += 4
                }
                else -> invalid()
            }
        }
        private fun ws() { while (index < source.length && source[index] in " \t\r\n") index++ }
        private fun peek(c: Char) = index < source.length && source[index] == c
        private fun take(c: Char): Boolean = if (peek(c)) { index++; true } else false
        private fun expect(c: Char) { if (!take(c)) invalid() }
    }
}
