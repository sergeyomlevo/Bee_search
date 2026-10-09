package org.beesearch.app.data.zip

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.beesearch.app.data.zip.ZipSafetyFailure
import org.beesearch.app.data.zip.ZipSafetyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Characterizes bounded ZIP mechanics with the profiles' metadata/count budgets.
 * Full format authorization and large-media integration are tested by the codec suites.
 */
class PortableZipSafetyCharacterizationTest {
    @Test
    fun `reader accepts exact count entry and aggregate boundaries`() {
        READERS.forEach { reader ->
            assertEquals(reader.count, read(reader, zipOf((0 until reader.count).map { "e$it" to byteArrayOf() })).size)
            failure(reader, zipOf((0..reader.count).map { "e$it" to byteArrayOf() }), "too many ZIP entries")

            val exactEntry = zipOfSizes(listOf("entry" to reader.entryBytes))
            assertEquals(reader.entryBytes, read(reader, exactEntry).getValue("entry"))
            failure(reader, zipOfSizes(listOf("entry" to reader.entryBytes + 1)), "ZIP entry is too large")

            val exactAggregate = zipOfSizes((0 until reader.aggregateBytes / reader.entryBytes).map {
                "part$it" to reader.entryBytes
            })
            assertEquals(reader.aggregateBytes, read(reader, exactAggregate).values.sum())
            val aggregatePlusOne = (0 until reader.aggregateBytes / reader.entryBytes).map {
                "part$it" to reader.entryBytes
            } + ("last" to 1L)
            failure(reader, zipOfSizes(aggregatePlusOne), "archive is too large")
        }
    }

    @Test
    fun `reader checks count before path and path before duplicate`() {
        READERS.forEach { reader ->
            val full = (0 until reader.count).map { "e$it" to byteArrayOf() }
            failure(reader, rawStoredZip(full + ("../late" to byteArrayOf())), "too many ZIP entries")
            failure(reader, rawStoredZip(full + ("e0" to byteArrayOf())), "too many ZIP entries")
            failure(reader, rawStoredZip(full + ("directory/" to byteArrayOf())), "too many ZIP entries")
            failure(reader, rawStoredZip(listOf("safe" to byteArrayOf(), "../bad" to byteArrayOf(), "safe" to byteArrayOf())), "unsafe ZIP entry")
            failure(reader, rawStoredZip(listOf("safe" to byteArrayOf(), "safe" to byteArrayOf())), "duplicate ZIP entry")
        }
    }

    @Test
    fun `reader rejects unsafe names and directories with distinct messages`() {
        READERS.forEach { reader ->
            listOf("", " ", "../x", "/x", "a\\b", "C:x", "a//b", "a/./b", "a/../b").forEach { name ->
                failure(reader, rawStoredZip(listOf(name to byteArrayOf())), "unsafe ZIP entry")
            }
            val directory = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("directory/"))
                    zip.closeEntry()
                }
            }.toByteArray()
            failure(reader, directory, "directory ZIP entry is not allowed")
        }
    }

    @Test
    fun `reader accepts reordered local entries and truncated central directory`() {
        READERS.forEach { reader ->
            val reordered = rawStoredZip(listOf("third" to byteArrayOf(3), "first" to byteArrayOf(1), "second" to byteArrayOf(2)))
            assertEquals(listOf("third", "first", "second"), read(reader, reordered).keys.toList())

            val full = zipOf(listOf("complete" to byteArrayOf(1, 2, 3)))
            val central = signature(full, 0x02014b50)
            assertEquals(listOf("complete"), read(reader, full.copyOf(central)).keys.toList())
        }
    }

    @Test
    fun `reader maps empty truncated payload and CRC damage to malformed archive`() {
        READERS.forEach { reader ->
            failure(reader, ByteArray(0), "empty archive")

            val valid = storedZip("payload", byteArrayOf(1, 2, 3))
            val central = signature(valid, 0x02014b50)
            failure(reader, valid.copyOf(central - 1), "malformed archive")
            val crcDamaged = valid.clone().also { it[14] = (it[14].toInt() xor 1).toByte() }
            failure(reader, crcDamaged, "malformed archive")
        }
    }

    private fun read(reader: Reader, bytes: ByteArray): Map<String, Long> = try {
        if (bytes.isEmpty()) throw IllegalArgumentException("empty archive")
        StagedZipArchive.read(bytes.inputStream(), reader.policy).use { archive ->
            if (archive.entries.isEmpty()) throw IllegalArgumentException("empty archive")
            archive.entries.mapValues { it.value.size }
        }
    } catch (error: ZipSafetyException) {
        throw IllegalArgumentException(zipMessage(error), error)
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: java.io.EOFException) {
        throw IllegalArgumentException("malformed archive", error)
    } catch (error: Exception) {
        throw IllegalArgumentException("malformed archive", error)
    }

    private fun failure(reader: Reader, bytes: ByteArray, message: String) {
        try {
            read(reader, bytes)
            throw AssertionError("expected $message")
        } catch (error: Throwable) {
            assertTrue("${reader.name}: $error", error is IllegalArgumentException)
            assertTrue("${reader.name}: ${error.message}", error.message.orEmpty().contains(message))
        }
    }

    private fun zipOf(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun zipOfSizes(entries: List<Pair<String, Long>>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, size) ->
                zip.putNextEntry(ZipEntry(name))
                writeZeros(zip, size)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun storedZip(name: String, bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                crc = CRC32().apply { update(bytes) }.value
            })
            zip.write(bytes)
            zip.closeEntry()
        }
    }.toByteArray()

    /** Local records without a central directory permit duplicate names and order assertions. */
    private fun rawStoredZip(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { output ->
        entries.forEach { (name, bytes) ->
            val nameBytes = name.toByteArray()
            val crc = CRC32().apply { update(bytes) }.value
            fun short(value: Int) { output.write(value and 0xff); output.write((value ushr 8) and 0xff) }
            fun integer(value: Long) { short(value.toInt()); short((value ushr 16).toInt()) }
            integer(0x04034b50); short(20); short(0); short(0); short(0); short(0)
            integer(crc); integer(bytes.size.toLong()); integer(bytes.size.toLong()); short(nameBytes.size); short(0)
            output.write(nameBytes); output.write(bytes)
        }
    }.toByteArray()

    private fun writeZeros(output: ZipOutputStream, size: Long) {
        // Long overflow is unreachable here: entry and aggregate caps bound every read.
        val chunk = ByteArray(8192)
        var remaining = size
        while (remaining > 0) {
            val count = minOf(remaining, chunk.size.toLong()).toInt()
            output.write(chunk, 0, count)
            remaining -= count
        }
    }

    private fun signature(bytes: ByteArray, value: Int): Int {
        for (index in 0..bytes.size - 4) {
            if ((bytes[index].toInt() and 0xff) == (value and 0xff) &&
                (bytes[index + 1].toInt() and 0xff) == (value ushr 8 and 0xff) &&
                (bytes[index + 2].toInt() and 0xff) == (value ushr 16 and 0xff) &&
                (bytes[index + 3].toInt() and 0xff) == (value ushr 24 and 0xff)
            ) return index
        }
        error("signature not found")
    }

    private data class Reader(
        val name: String,
        val policy: ZipSafetyPolicy,
        val count: Int,
        val entryBytes: Long,
        val aggregateBytes: Long,
    )

    private companion object {
        val READERS = listOf(
            reader("point", 64, 16L * 1024 * 1024, 64L * 1024 * 1024),
            reader("object", 64, 16L * 1024 * 1024, 64L * 1024 * 1024),
            reader("collection", 1_024, 16L * 1024 * 1024, 128L * 1024 * 1024),
        )

        private fun reader(name: String, count: Int, entry: Long, aggregate: Long) =
            Reader(name, ZipSafetyPolicy(count, entry, aggregate), count, entry, aggregate)

        private fun zipMessage(error: ZipSafetyException) = when (error.failure) {
            ZipSafetyFailure.ENTRY_COUNT -> "too many ZIP entries"
            ZipSafetyFailure.UNSAFE_PATH -> if (error.isDirectory) "directory ZIP entry is not allowed" else "unsafe ZIP entry"
            ZipSafetyFailure.DUPLICATE -> "duplicate ZIP entry"
            ZipSafetyFailure.ENTRY_BYTES -> "ZIP entry is too large"
            ZipSafetyFailure.TOTAL_BYTES -> "archive is too large"
            else -> "malformed archive"
        }
    }
}
