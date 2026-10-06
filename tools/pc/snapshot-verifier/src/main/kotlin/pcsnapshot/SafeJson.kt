package pcsnapshot

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.Strictness
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Independent, bounded JSON reader and restricted canonical encoder for the PC verifier. */
object SafeJson {
    fun parse(bytes: ByteArray, canonical: Boolean = false): JsonElement {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            reject("JSON_BOM")
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { reject("JSON_UTF8") }
        try { JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            val value = read(reader, 0, canonical)
            if (reader.peek() != JsonToken.END_DOCUMENT) reject("JSON_TRAILING")
            return value
        } } catch (e: CheckFailure) { throw e }
        catch (_: Exception) { reject("JSON_SYNTAX") }
    }

    fun encode(value: JsonElement): ByteArray = buildString { write(value) }.toByteArray(Charsets.UTF_8)

    private fun read(reader: JsonReader, depth: Int, canonical: Boolean): JsonElement {
        if (depth > Contract.DEPTH) reject("JSON_DEPTH", observed = depth, expected = Contract.DEPTH)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().also { obj ->
                reader.beginObject()
                while (reader.hasNext()) {
                    if (obj.size() >= Contract.MEMBERS) reject("JSON_OBJECT_LIMIT", observed = obj.size() + 1, expected = Contract.MEMBERS)
                    val key = valid(reader.nextName())
                    if (key.length > Contract.STRING) reject("JSON_STRING_LIMIT", observed = key.length, expected = Contract.STRING)
                    if (obj.has(key)) reject("JSON_DUPLICATE_KEY")
                    obj.add(key, read(reader, depth + 1, canonical))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().also { array ->
                reader.beginArray()
                while (reader.hasNext()) {
                    if (array.size() >= Contract.ARRAY) reject("JSON_ARRAY_LIMIT", observed = array.size() + 1, expected = Contract.ARRAY)
                    array.add(read(reader, depth + 1, canonical))
                }
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(valid(reader.nextString()).also {
                if (it.length > Contract.STRING) reject("JSON_STRING_LIMIT", observed = it.length, expected = Contract.STRING)
            })
            JsonToken.NUMBER -> number(reader.nextString(), canonical)
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
            else -> reject("JSON_TOKEN")
        }
    }

    private fun number(raw: String, canonical: Boolean): JsonPrimitive {
        if (canonical) return JsonPrimitive(parseInteger(raw))
        val parsed = raw.toDoubleOrNull() ?: reject("JSON_NUMBER")
        if (!parsed.isFinite()) reject("JSON_NUMBER_NONFINITE")
        return JsonPrimitive(RawNumber(raw, parsed))
    }

    private fun parseInteger(raw: String): Long {
        if (!raw.matches(Regex("-?(0|[1-9][0-9]*)"))) reject("JSON_INTEGER")
        val value = raw.toLongOrNull() ?: reject("JSON_SAFE_INTEGER")
        if (value !in -Contract.SAFE..Contract.SAFE) reject("JSON_SAFE_INTEGER")
        return value
    }

    private fun valid(value: String): String {
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c.isHighSurrogate()) {
                if (i == value.length || !value[i].isLowSurrogate()) reject("JSON_UNPAIRED_SURROGATE")
                i++
            } else if (c.isLowSurrogate()) reject("JSON_UNPAIRED_SURROGATE")
        }
        return value
    }

    private fun StringBuilder.write(value: JsonElement) {
        when {
            value.isJsonObject -> {
                append('{')
                value.asJsonObject.entrySet().map { it.key to it.value }
                    .sortedBy { it.first }.forEachIndexed { index, (key, child) ->
                        if (index != 0) append(',')
                        quote(key); append(':'); write(child)
                    }
                append('}')
            }
            value.isJsonArray -> {
                append('[')
                value.asJsonArray.forEachIndexed { index, child -> if (index != 0) append(','); write(child) }
                append(']')
            }
            value.isJsonNull -> append("null")
            value.asJsonPrimitive.isBoolean -> append(if (value.asBoolean) "true" else "false")
            value.asJsonPrimitive.isString -> quote(value.asString)
            value.asJsonPrimitive.isNumber -> append(parseInteger(value.asString))
            else -> reject("JSON_VALUE")
        }
    }

    private fun StringBuilder.quote(value: String) {
        valid(value); append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\")
                '\b' -> append("\\b"); '\t' -> append("\\t"); '\n' -> append("\\n")
                '\u000C' -> append("\\f"); '\r' -> append("\\r")
                else -> if (c.code < 0x20) append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
            }
        }
        append('"')
    }

    private class RawNumber(private val raw: String, private val value: Double) : Number() {
        override fun toByte(): Byte = value.toInt().toByte()
        override fun toShort(): Short = value.toInt().toShort()
        override fun toInt(): Int = value.toInt()
        override fun toLong(): Long = value.toLong()
        override fun toFloat(): Float = value.toFloat()
        override fun toDouble(): Double = value
        override fun toString(): String = raw
    }
}
