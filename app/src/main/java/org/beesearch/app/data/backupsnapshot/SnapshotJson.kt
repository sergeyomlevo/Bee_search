package org.beesearch.app.data.backupsnapshot

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal object SnapshotJson {
    private const val SAFE = 9007199254740991L

    fun encode(value: JsonElement): ByteArray = canonical(value).toByteArray(Charsets.UTF_8)

    fun parse(bytes: ByteArray, integerOnly: Boolean = true, limits: SnapshotLimits = SnapshotLimits()): JsonElement {
        if (bytes.size > limits.totalBytes) fail("bytes", bytes.size.toLong(), limits.totalBytes)
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) { throw SnapshotException(SnapshotError.INVALID_FORMAT, "MALFORMED_UTF8", cause = e) }
        return Parser(text, integerOnly, limits).parse()
    }

    fun sha(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it) }

    fun sha(input: InputStream, bufferSize: Int = 8192): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(bufferSize)
        while (true) { val n = input.read(buffer); if (n < 0) break; if (n > 0) digest.update(buffer, 0, n) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun canonical(v: JsonElement): String = when (v) {
        is JsonObject -> v.entries.sortedWith(compareBy { it.key }).joinToString(",", "{", "}") { quote(it.key) + ":" + canonical(it.value) }
        is JsonArray -> v.joinToString(",", "[", "]", transform = ::canonical)
        JsonNull -> "null"
        is JsonPrimitive -> when {
            v.isString -> quote(v.content)
            v.content == "true" || v.content == "false" -> v.content
            else -> number(v.content)
        }
        else -> error("unknown JSON element")
    }

    private fun number(raw: String): String {
        if (raw.matches(Regex("-?(0|[1-9][0-9]*)"))) {
            val n = raw.toLongOrNull() ?: throw SnapshotException(SnapshotError.INVALID_FORMAT, "INTEGER")
            if (n !in -SAFE..SAFE) throw SnapshotException(SnapshotError.INVALID_FORMAT, "SAFE_INTEGER")
            return if (n == 0L) "0" else n.toString()
        }
        throw SnapshotException(SnapshotError.INVALID_FORMAT, "INTEGER_ONLY_CANONICAL")
    }

    private fun quote(text: String): String = buildString {
        validateString(text, SnapshotLimits())
        append('"')
        text.forEach { c -> when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\t' -> append("\\t")
            '\n' -> append("\\n"); '\u000c' -> append("\\f"); '\r' -> append("\\r")
            else -> if (c.code < 32) append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
        } }
        append('"')
    }

    private fun validateString(s: String, limits: SnapshotLimits) {
        if (s.length > limits.stringUnits) fail("string", s.length.toLong(), limits.stringUnits.toLong())
        var i = 0
        while (i < s.length) { val c = s[i++]; if (c.isHighSurrogate()) { if (i == s.length || !s[i].isLowSurrogate()) bad("SURROGATE"); i++ } else if (c.isLowSurrogate()) bad("SURROGATE") }
    }
    private fun bad(category: String): Nothing = throw SnapshotException(SnapshotError.INVALID_FORMAT, category)
    private fun fail(category: String, observed: Long, limit: Long): Nothing = throw SnapshotException(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, category, observed, limit)

    private class Parser(private val s: String, private val integerOnly: Boolean, private val limits: SnapshotLimits) {
        private var i = 0
        fun parse(): JsonElement { val v = value(0); ws(); if (i != s.length) bad("TRAILING"); return v }
        private fun value(depth: Int): JsonElement {
            if (depth > limits.depth) fail("depth", depth.toLong(), limits.depth.toLong()); ws()
            if (i == s.length) bad("TOKEN")
            return when (s[i]) {
                '{' -> obj(depth); '[' -> array(depth); '"' -> JsonPrimitive(string())
                't' -> literal("true", JsonPrimitive(true)); 'f' -> literal("false", JsonPrimitive(false)); 'n' -> literal("null", JsonNull)
                '-', in '0'..'9' -> number()
                else -> bad("TOKEN")
            }
        }
        private fun obj(depth: Int): JsonObject { i++; ws(); val map = linkedMapOf<String, JsonElement>(); if (take('}')) return JsonObject(map)
            while (true) { if (map.size >= limits.objectMembers) fail("objectMembers", (map.size + 1).toLong(), limits.objectMembers.toLong()); if (i >= s.length || s[i] != '"') bad("OBJECT_KEY")
                val key = string(); if (map.containsKey(key)) bad("DUPLICATE_JSON:$key"); ws(); if (!take(':')) bad("COLON"); map[key] = value(depth + 1); ws(); if (take('}')) return JsonObject(map); if (!take(',')) bad("COMMA") }
        }
        private fun array(depth: Int): JsonArray { i++; ws(); val list = ArrayList<JsonElement>(); if (take(']')) return JsonArray(list)
            while (true) { if (list.size >= limits.arrayEntries) fail("arrayEntries", (list.size + 1).toLong(), limits.arrayEntries.toLong()); list += value(depth + 1); ws(); if (take(']')) return JsonArray(list); if (!take(',')) bad("COMMA") }
        }
        private fun string(): String { if (!take('"')) bad("STRING"); val out = StringBuilder(); while (i < s.length) { val c = s[i++]; when (c) {
                '"' -> { validateString(out.toString(), limits); return out.toString() }
                '\\' -> { if (i >= s.length) bad("ESCAPE"); when (val e = s[i++]) { '"' -> out.append('"'); '\\' -> out.append('\\'); '/' -> out.append('/'); 'b' -> out.append('\b'); 'f' -> out.append('\u000c'); 'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t'); 'u' -> out.append(unicode()) ; else -> bad("ESCAPE") } }
                in '\u0000'..'\u001f' -> bad("CONTROL")
                else -> out.append(c)
            }; if (out.length > limits.stringUnits) fail("string", out.length.toLong(), limits.stringUnits.toLong()) }; bad("UNTERMINATED_STRING")
        }
        private fun unicode(): Char { if (i + 4 > s.length) bad("UNICODE_ESCAPE"); val h = s.substring(i, i + 4); if (!h.matches(Regex("[0-9a-fA-F]{4}"))) bad("UNICODE_ESCAPE"); i += 4; return h.toInt(16).toChar() }
        private fun number(): JsonPrimitive { val start = i; if (s[i] == '-') i++; if (i >= s.length) bad("NUMBER"); if (s[i] == '0') { i++; if (i < s.length && s[i] in '0'..'9') bad("NUMBER") } else { if (s[i] !in '0'..'9' || s[i] == '0') bad("NUMBER"); while (i < s.length && s[i] in '0'..'9') i++ }
            if (i < s.length && s[i] == '.') { if (++i >= s.length || s[i] !in '0'..'9') bad("NUMBER"); while (i < s.length && s[i] in '0'..'9') i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) { i++; if (i < s.length && (s[i] == '+' || s[i] == '-')) i++; if (i >= s.length || s[i] !in '0'..'9') bad("NUMBER"); while (i < s.length && s[i] in '0'..'9') i++ }
            val raw = s.substring(start, i); if (integerOnly && !raw.matches(Regex("-?(0|[1-9][0-9]*)"))) bad("INTEGER_ONLY")
            if (integerOnly) { val n = raw.toLongOrNull() ?: bad("INTEGER"); if (n !in -SAFE..SAFE) bad("SAFE_INTEGER") } else if (!raw.toDouble().isFinite()) bad("NONFINITE_NUMBER")
            return kotlinx.serialization.json.JsonUnquotedLiteral(raw)
        }
        private fun literal(expected: String, value: JsonElement): JsonElement { if (!s.startsWith(expected, i)) bad("LITERAL"); i += expected.length; return value }
        private fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }
        private fun take(c: Char): Boolean { if (i < s.length && s[i] == c) { i++; return true }; return false }
        private fun bad(category: String): Nothing = throw SnapshotException(SnapshotError.INVALID_FORMAT, category)
        private fun fail(category: String, observed: Long, limit: Long): Nothing = throw SnapshotException(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, category, observed, limit)
    }
}
