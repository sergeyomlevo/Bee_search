package pcsnapshot

import com.google.gson.JsonObject
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeJsonTest {
    @Test fun canonicalVectorsMatchBytesAndDigest() {
        val corpus = SafeJson.parse(javaClass.getResourceAsStream("/canonical-vectors.json")!!.readBytes(), canonical = true)
        corpus.asJsonObject.getAsJsonArray("vectors").forEach { row ->
            val item = row.asJsonObject
            val bytes = SafeJson.encode(SafeJson.parse(item["input"].asString.toByteArray(), canonical = true))
            assertEquals(item["canonical"].asString, bytes.toString(Charsets.UTF_8))
            assertEquals(item["utf8Hex"].asString, bytes.joinToString("") { "%02x".format(it) })
            assertEquals(item["sha256"].asString, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        }
    }

    @Test fun strictParserRejectsUnsafeInputs() {
        listOf("{\"a\":1,\"a\":2}", "1 2", "\uFEFF{}", "{\"x\":1e309}", "{\"x\":1.1}").forEach { input ->
            try { SafeJson.parse(input.toByteArray(), canonical = true); throw AssertionError(input) }
            catch (_: CheckFailure) { }
        }
    }

    @Test fun nonCanonicalPreservesRawFiniteNumberAndSignedZero() {
        val value = SafeJson.parse("[-0.0,5e-324]".toByteArray())
        assertEquals("-0.0", value.asJsonArray[0].asJsonPrimitive.asString)
        assertEquals("5e-324", value.asJsonArray[1].asJsonPrimitive.asString)
        assertTrue(java.lang.Double.doubleToRawLongBits(value.asJsonArray[0].asDouble) < 0)
    }

    @Test fun semanticSetPermutationIsCanonical() {
        fun descriptors(input: String): ByteArray {
            val array = SafeJson.parse(input.toByteArray(), true).asJsonArray
            val sorted = com.google.gson.JsonArray()
            array.sortedBy { it.asJsonObject["path"].asString }.forEach(sorted::add)
            return SafeJson.encode(sorted)
        }
        // Schema sorting is explicit. The canonical encoder must NOT sort arrays itself.
        assertArrayEquals(descriptors("[{\"path\":\"a\"},{\"path\":\"b\"}]"),
            descriptors("[{\"path\":\"b\"},{\"path\":\"a\"}]"))
        org.junit.Assert.assertFalse(SafeJson.encode(SafeJson.parse("[1,2]".toByteArray(), true))
            .contentEquals(SafeJson.encode(SafeJson.parse("[2,1]".toByteArray(), true))))
    }

    @Test fun textualBinary64VectorsMatchExactBits() {
        val vectors = listOf("0.1" to "3FB999999999999A", "-0.0" to "8000000000000000",
            "5e-324" to "0000000000000001", "1.7976931348623157e308" to "7FEFFFFFFFFFFFFF",
            "55.7558" to "404BE0BE0DED288D", "37.6173" to "4042CF03AFB7E910",
            "12.5" to "4029000000000000", "3.6" to "400CCCCCCCCCCCCD", "23.75" to "4037C00000000000")
        vectors.forEach { (raw, bits) ->
            assertEquals(java.lang.Long.parseUnsignedLong(bits, 16),
                SafeJson.parse(raw.toByteArray()).asDouble.toRawBits())
        }
        listOf("NaN", "Infinity", "+Infinity", "-Infinity", "1e309").forEach { raw ->
            org.junit.Assert.assertThrows(CheckFailure::class.java) { SafeJson.parse(raw.toByteArray()) }
        }
    }

    @Test fun malformedUtf8AndUnicodeReject() {
        org.junit.Assert.assertThrows(CheckFailure::class.java) { SafeJson.parse(byteArrayOf(0xc3.toByte(), 0x28)) }
        for (raw in listOf("\"\\ud800\"", "\"\\udc00\"", "{\"x\":1.0}", "{\"x\":1e0}", "{\"x\":9007199254740992}"))
            org.junit.Assert.assertThrows(CheckFailure::class.java) { SafeJson.parse(raw.toByteArray(), true) }
    }

    @Test fun exactDefaultParserBounds() {
        fun fails(raw: String, code: String) = assertEquals(code,
            org.junit.Assert.assertThrows(CheckFailure::class.java) { SafeJson.parse(raw.toByteArray()) }.issue.code)
        SafeJson.parse(("[".repeat(32) + "0" + "]".repeat(32)).toByteArray())
        fails("[".repeat(33) + "0" + "]".repeat(33), "JSON_DEPTH")
        for (key in listOf(false, true)) {
            fun raw(n: Int) = if (key) "{\"${"x".repeat(n)}\":0}" else "\"${"x".repeat(n)}\""
            SafeJson.parse(raw(65536).toByteArray()); fails(raw(65537), "JSON_STRING_LIMIT")
        }
        fun obj(n: Int) = (0 until n).joinToString(",", "{", "}") { "\"k$it\":0" }
        fun arr(n: Int) = (0 until n).joinToString(",", "[", "]") { "0" }
        SafeJson.parse(obj(256).toByteArray()); fails(obj(257), "JSON_OBJECT_LIMIT")
        SafeJson.parse(arr(100000).toByteArray()); fails(arr(100001), "JSON_ARRAY_LIMIT")
    }
}
