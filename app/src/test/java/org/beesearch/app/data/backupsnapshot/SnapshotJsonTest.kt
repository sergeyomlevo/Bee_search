package org.beesearch.app.data.backupsnapshot

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets

class SnapshotJsonTest {
    @Test fun approvedPocCorpusBytesAndHashes() {
        val raw = javaClass.getResourceAsStream("/backupsnapshot/canonical-vectors.json")!!.use { it.readBytes() }
        val vectors = (SnapshotJson.parse(raw) as kotlinx.serialization.json.JsonObject)["vectors"] as kotlinx.serialization.json.JsonArray
        vectors.forEach { element ->
            val row = element as kotlinx.serialization.json.JsonObject
            fun field(name: String) = (row[name] as JsonPrimitive).content
            val encoded = SnapshotJson.encode(SnapshotJson.parse(field("input").toByteArray(Charsets.UTF_8)))
            assertArrayEquals(field("canonical").toByteArray(Charsets.UTF_8), encoded)
            assertEquals(field("utf8Hex"), encoded.joinToString("") { "%02x".format(it) })
            assertEquals(field("sha256"), SnapshotJson.sha(encoded))
        }
    }
    @Test fun canonicalVectors() {
        val cases = listOf(
            "{\"b\":2,\"a\":1}" to "{\"a\":1,\"b\":2}",
            "{\"o\":{},\"a\":[]}" to "{\"a\":[],\"o\":{}}",
            "{\"z\":[{\"b\":false,\"a\":null}]}" to "{\"z\":[{\"a\":null,\"b\":false}]}",
            "{\"т\":\"Пчела 😀 é\"}" to "{\"т\":\"Пчела 😀 é\"}",
            "{\"s\":\"\\\"\\\\\\n\\t\\u000f<>&=\"}" to "{\"s\":\"\\\"\\\\\\n\\t\\u000f<>&=\"}",
            "{\"z\":-0,\"n\":9007199254740991}" to "{\"n\":9007199254740991,\"z\":0}",
            "{\"a\":[3,1,2]}" to "{\"a\":[3,1,2]}",
            "{\"set\":[\"a\",\"b\"]}" to "{\"set\":[\"a\",\"b\"]}",
            "{\"דּ\":1,\"😀\":2,\"€\":3}" to "{\"€\":3,\"😀\":2,\"דּ\":1}",
        )
        cases.forEach { (input, expected) -> assertArrayEquals(expected.toByteArray(), SnapshotJson.encode(SnapshotJson.parse(input.toByteArray()))) }
    }

    @Test fun strictParserRejectsUnsafeInputsAndLimits() {
        listOf("{\"a\":1,\"a\":2}", "{\"a\":1.0}", "{\"a\":1e3}", "{\"a\":9007199254740992}", "{\"a\":NaN}").forEach {
            assertThrows(SnapshotException::class.java) { SnapshotJson.parse(it.toByteArray()) }
        }
        assertThrows(SnapshotException::class.java) { SnapshotJson.parse(byteArrayOf(0x7b, 0x22, 0x61, 0x22, 0x3a, 0x22, 0xc3.toByte(), 0x28, 0x22, 0x7d)) }
        assertThrows(SnapshotException::class.java) { SnapshotJson.parse("{\"a\":${"[".repeat(33)}0${"]".repeat(33)}}".toByteArray()) }
        val limits = SnapshotLimits(stringUnits = 3)
        assertThrows(SnapshotException::class.java) { SnapshotJson.parse("{\"a\":\"abcd\"}".toByteArray(), limits = limits) }
    }

    @Test fun fractionalVectorsPreserveRawDoubleBits() {
        val vectors = listOf("0.1" to 0x3FB999999999999AL, "-0.0" to Long.MIN_VALUE, "5e-324" to 1L, "1.7976931348623157e308" to 0x7FEFFFFFFFFFFFFFL,
            "55.751244" to 0x404be028c36da87aL, "37.618423" to 0x4042cf287c200c0fL,
            "12.5" to 0x4029000000000000L, "2.75" to 0x4006000000000000L)
        vectors.forEach { (token, bits) ->
            val element = SnapshotJson.parse(token.toByteArray(), integerOnly = false) as JsonPrimitive
            assertEquals(bits, element.content.toDouble().toRawBits())
        }
        assertThrows(SnapshotException::class.java) { SnapshotJson.parse("1e309".toByteArray(), integerOnly = false) }
        listOf("NaN", "Infinity", "-Infinity", "+Infinity").forEach { token ->
            assertThrows(SnapshotException::class.java) { SnapshotJson.parse(token.toByteArray(), integerOnly = false) }
        }
        assertThrows(SnapshotException::class.java) { SnapshotJson.encode(JsonPrimitive(0.1)) }
    }
    @Test fun parserBoundaryEqualityAndFirstExcess() {
        SnapshotJson.parse("[0,1]".toByteArray(), limits = SnapshotLimits(arrayEntries = 2))
        SnapshotJson.parse("{\"a\":0,\"b\":1}".toByteArray(), limits = SnapshotLimits(objectMembers = 2))
        SnapshotJson.parse("\"abc\"".toByteArray(), limits = SnapshotLimits(stringUnits = 3))
        SnapshotJson.parse("[[0]]".toByteArray(), limits = SnapshotLimits(depth = 2))
        for ((raw, limits) in listOf("[0,1,2]" to SnapshotLimits(arrayEntries = 2),
            "{\"a\":0,\"b\":1,\"c\":2}" to SnapshotLimits(objectMembers = 2),
            "\"abcd\"" to SnapshotLimits(stringUnits = 3), "[[[0]]]" to SnapshotLimits(depth = 2))) {
            assertEquals(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED,
                assertThrows(SnapshotException::class.java) { SnapshotJson.parse(raw.toByteArray(), limits = limits) }.error)
        }
    }

    @Test fun contractHasExactPathsAndSpecialLimits() {
        assertEquals(17, SnapshotContract.paths.size)
        assertEquals(1L, SnapshotContract.entryLimit("manifest.json", SnapshotLimits(manifestBytes = 1)))
        assertEquals(2L, SnapshotContract.entryLimit("settings/portable.json", SnapshotLimits(portableBytes = 2)))
        assertEquals(3L, SnapshotContract.entryLimit("references/media-blobs.jsonl", SnapshotLimits(referencesBytes = 3)))
    }

    @Test fun shaIsDeterministic() = assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", SnapshotJson.sha("abc".toByteArray(StandardCharsets.UTF_8)))
}
