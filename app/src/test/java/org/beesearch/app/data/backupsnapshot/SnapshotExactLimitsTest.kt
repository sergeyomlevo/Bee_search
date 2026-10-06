package org.beesearch.app.data.backupsnapshot

import java.io.OutputStream
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SnapshotExactLimitsTest {
    private val limits = SnapshotLimits()

    @Test
    fun `default ZIP stream accepts 64 MiB and rejects the first byte after`() {
        val sink = SnapshotArchive.BoundedOutput(NullOutputStream(), limits.zipBytes, "zipBytes") {}
        writeChunks(sink, limits.zipBytes)
        val error = assertThrows(SnapshotException::class.java) { sink.write(0) }

        assertLimit(error, "zipBytes", limits.zipBytes + 1, limits.zipBytes)
    }

    @Test
    fun `default stream counters accept exact limits and reject the first byte after`() {
        val cases = listOf(
            "totalBytes" to limits.totalBytes,
            "manifest.json" to limits.manifestBytes,
            "settings/portable.json" to limits.portableBytes,
            "references/media-blobs.jsonl" to limits.referencesBytes,
            "data/territories.jsonl:recordBytes" to limits.recordBytes,
        )

        cases.forEach { (category, maximum) ->
            var count = 0L
            while (count < maximum) {
                val chunk = minOf(CHUNK_SIZE.toLong(), maximum - count)
                count = SnapshotArchive.addBound(count, chunk, maximum, category)
            }
            assertEquals("exact boundary for $category", maximum, count)
            val error = assertThrows(SnapshotException::class.java) {
                SnapshotArchive.addBound(count, 1, maximum, category)
            }
            assertLimit(error, category, maximum + 1, maximum)
        }
    }

    @Test
    fun `parser default depth boundary starts at root zero`() {
        SnapshotJson.parse(nestedArrays(limits.depth).toByteArray())
        val error = assertThrows(SnapshotException::class.java) {
            SnapshotJson.parse(nestedArrays(limits.depth + 1).toByteArray())
        }

        assertLimit(error, "depth", limits.depth + 1L, limits.depth.toLong())
    }

    @Test
    fun `parser default string and key boundaries are exact`() {
        SnapshotJson.parse(("\"${"x".repeat(limits.stringUnits)}\"").toByteArray())
        SnapshotJson.parse(("{\"${"x".repeat(limits.stringUnits)}\":0}").toByteArray())

        val stringError = assertThrows(SnapshotException::class.java) {
            SnapshotJson.parse(("\"${"x".repeat(limits.stringUnits + 1)}\"").toByteArray())
        }
        val keyError = assertThrows(SnapshotException::class.java) {
            SnapshotJson.parse(("{\"${"x".repeat(limits.stringUnits + 1)}\":0}").toByteArray())
        }

        assertLimit(stringError, "string", limits.stringUnits + 1L, limits.stringUnits.toLong())
        assertLimit(keyError, "string", limits.stringUnits + 1L, limits.stringUnits.toLong())
    }

    @Test
    fun `parser default object and array boundaries are exact`() {
        val exactObject = buildString {
            append('{')
            repeat(limits.objectMembers) { index ->
                if (index > 0) append(',')
                append("\"k$index\":0")
            }
            append('}')
        }
        val excessObject = buildString {
            append(exactObject.dropLast(1))
            append(",\"k${limits.objectMembers}\":0}")
        }
        val exactArray = "[${List(limits.arrayEntries) { "0" }.joinToString(",")}]"
        val excessArray = "[${List(limits.arrayEntries + 1) { "0" }.joinToString(",")}]"

        val exactObjectValue = SnapshotJson.parse(exactObject.toByteArray())
        val exactArrayValue = SnapshotJson.parse(exactArray.toByteArray())
        assertEquals(limits.objectMembers, (exactObjectValue as JsonObject).size)
        assertEquals(limits.arrayEntries, (exactArrayValue as JsonArray).size)

        val objectError = assertThrows(SnapshotException::class.java) {
            SnapshotJson.parse(excessObject.toByteArray())
        }
        val arrayError = assertThrows(SnapshotException::class.java) {
            SnapshotJson.parse(excessArray.toByteArray())
        }

        assertLimit(objectError, "objectMembers", limits.objectMembers + 1L, limits.objectMembers.toLong())
        assertLimit(arrayError, "arrayEntries", limits.arrayEntries + 1L, limits.arrayEntries.toLong())
    }

    private fun assertLimit(error: SnapshotException, category: String, observed: Long, limit: Long) {
        assertSame(SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, error.error)
        assertEquals(category, error.category)
        assertEquals(observed, error.observed ?: Long.MIN_VALUE)
        assertEquals(limit, error.limit ?: Long.MIN_VALUE)
    }

    private fun writeChunks(output: OutputStream, size: Long) {
        val chunk = ByteArray(CHUNK_SIZE)
        var written = 0L
        while (written < size) {
            val length = minOf(chunk.size.toLong(), size - written).toInt()
            output.write(chunk, 0, length)
            written += length
        }
    }

    private fun nestedArrays(depth: Int): String = "[".repeat(depth) + "0" + "]".repeat(depth)

    private class NullOutputStream : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private companion object {
        const val CHUNK_SIZE = 64 * 1024
    }
}
