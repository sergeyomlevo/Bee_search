package org.beesearch.app.data.backupsnapshot

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotConformanceDeviceTest {
    @Test fun approvedCanonicalCorpusMatchesHostBytesAndSha() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val raw = context.assets.open("backupsnapshot/canonical-vectors.json").use { it.readBytes() }
        val vectors = (SnapshotJson.parse(raw) as JsonObject)["vectors"] as JsonArray
        vectors.forEach { element ->
            val row = element as JsonObject
            fun field(name: String) = (row[name] as JsonPrimitive).content
            val encoded = SnapshotJson.encode(SnapshotJson.parse(field("input").toByteArray(Charsets.UTF_8)))
            assertArrayEquals(field("canonical").toByteArray(Charsets.UTF_8), encoded)
            assertEquals(field("utf8Hex"), encoded.joinToString("") { "%02x".format(it) })
            assertEquals(field("sha256"), SnapshotJson.sha(encoded))
        }
    }
    @Test fun binary64RepresentativeTokens() {
        listOf("0.1" to 0x3fb999999999999aL, "-0.0" to Long.MIN_VALUE, "5e-324" to 1L,
            "1.7976931348623157e308" to 0x7fefffffffffffffL,
            "55.751244" to 0x404be028c36da87aL, "37.618423" to 0x4042cf287c200c0fL,
            "12.5" to 0x4029000000000000L).forEach { (token, bits) ->
            val value = SnapshotJson.parse(token.toByteArray(), false) as JsonPrimitive
            assertEquals(bits, value.content.toDouble().toRawBits())
        }
    }
}
