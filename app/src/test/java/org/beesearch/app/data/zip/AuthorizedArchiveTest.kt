package org.beesearch.app.data.zip

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuthorizedArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private val policy = ZipSafetyPolicy(8, 256, 512)

    @Test fun metadataBoundariesRemainFiniteAndInclusive() {
        val exact = archive(listOf("manifest" to 256L, "other" to 256L))
        read(exact).use { assertEquals(512L, it.entries.values.sumOf { payload -> payload.size }) }
        assertFailsCleanly(archive(listOf("manifest" to 257L)))
        assertFailsCleanly(archive(listOf("manifest" to 256L, "other" to 256L, "extra" to 1L)))
        assertFailsCleanly(archive((0..8).map { "e$it" to 0L }))
    }

    @Test fun mediaBeforeMetadataCanExceedBothOldEntryAndAggregateBudgets() {
        val size = 33L * 1024 * 1024
        val expected = mapOf("media/first" to expectation(size), "media/second" to expectation(size))
        val zip = archive(listOf("media/second" to size, "media/first" to size, "manifest" to 1L))
        read(zip, expected).use { staged ->
            expected.forEach { (name, value) ->
                assertEquals(value.size, staged.entries.getValue(name).size)
                assertEquals(value.sha256, staged.entries.getValue(name).sha256)
            }
        }
    }

    @Test fun exactActualSizeAndHashAreRequiredAndFailureCleansStaging() {
        val size = 8193L
        val zip = archive(listOf("manifest" to 1L, "media/a" to size))
        listOf(expectation(size - 1), expectation(size + 1), MediaExpectation(size, "0".repeat(64))).forEach {
            assertFailsCleanly(zip, mapOf("media/a" to it))
        }
    }

    @Test fun unexpectedMediaIsRejectedBeforeExtractionAndMalformedMetadataCannotAuthorizeIt() {
        val zip = archive(listOf("media/unexpected" to 17L * 1024 * 1024, "manifest" to 1L))
        assertFailsCleanly(zip)
        val parent = temporary.newFolder()
        assertThrows(IOException::class.java) {
            zip.inputStream().use { input ->
                StagedZipArchive.readAuthorized(input, policy, { it.startsWith("media/") }, { _, _ ->
                    // Only compressed source + one bounded metadata spool exist at authorization.
                    assertEquals(2, parent.listFiles()!!.single().listFiles()!!.size)
                    throw IOException("malformed metadata")
                }, parent)
            }
        }
        assertTrue(parent.listFiles()!!.isEmpty())
    }

    @Test fun overflowIsRejectedBeforeAnyMediaExtraction() {
        val overflow = listOf(MediaExpectation(Long.MAX_VALUE, "0".repeat(64)), MediaExpectation(1, "0".repeat(64)))
        assertEquals(ZipSafetyFailure.OVERFLOW, assertThrows(ZipSafetyException::class.java) { checkedMediaTotal(overflow) }.failure)
        val zip = archive(listOf("manifest" to 0L, "media/a" to 0L, "media/b" to 0L))
        assertFailsCleanly(zip, mapOf("media/a" to overflow[0], "media/b" to overflow[1]))
    }

    @Test fun unsafeDuplicateAndTruncatedArchivesAreRejected() {
        assertFailsCleanly(archive(listOf("../unsafe" to 1L)))
        val original = archive(listOf("media/a" to 1L, "media/b" to 1L))
        val bytes = original.readBytes()
        val name = "media/b".toByteArray()
        for (i in 0..bytes.size - name.size) {
            if (name.indices.all { bytes[i + it] == name[it] }) bytes[i + name.lastIndex] = 'a'.code.toByte()
        }
        val duplicate = temporary.newFile().also { it.writeBytes(bytes) }
        assertFailsCleanly(duplicate)
        val truncated = temporary.newFile().also { it.writeBytes(original.readBytes().dropLast(10).toByteArray()) }
        assertFailsCleanly(truncated)
    }

    private fun read(file: File, expected: Map<String, MediaExpectation> = emptyMap(), parent: File? = null) =
        file.inputStream().use { StagedZipArchive.readAuthorized(it, policy, { name -> name.startsWith("media/") },
            { _, _ -> expected }, parent) }

    private fun assertFailsCleanly(file: File, expected: Map<String, MediaExpectation> = emptyMap()) {
        val parent = temporary.newFolder()
        assertThrows(Exception::class.java) { read(file, expected, parent).use { fail("unexpected success") } }
        assertTrue(parent.listFiles()!!.isEmpty())
    }

    private fun archive(entries: List<Pair<String, Long>>): File = temporary.newFile().also { file ->
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { (name, size) ->
                zip.putNextEntry(ZipEntry(name))
                var left = size
                val buffer = ByteArray(8192)
                while (left > 0) { val count = minOf(left, buffer.size.toLong()).toInt(); zip.write(buffer, 0, count); left -= count }
                zip.closeEntry()
            }
        }
    }

    private fun expectation(size: Long): MediaExpectation {
        val digest = MessageDigest.getInstance("SHA-256")
        var left = size
        val buffer = ByteArray(8192)
        while (left > 0) { val count = minOf(left, buffer.size.toLong()).toInt(); digest.update(buffer, 0, count); left -= count }
        return MediaExpectation(size, digest.digest().joinToString("") { "%02x".format(it) })
    }
}
