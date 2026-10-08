package org.beesearch.app.data.zip

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchivePayloadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun fileBackedPayloadCopiesGeneratedMultiBufferContentAndPreservesDigest() {
        val source = temporary.newFile("media.bin")
        writePattern(source, 3 * 8192 + 137)
        val payload = ArchivePayload.fromFile(source)
        val output = ByteArrayOutputStream()

        payload.copyTo(output)

        assertEquals(source.length(), payload.size)
        assertEquals(sha256(source), payload.sha256)
        assertEquals(source.readBytes().toList(), output.toByteArray().toList())
    }

    @Test
    fun fileBackedPayloadRejectsSourceMutationDuringCopy() {
        val source = temporary.newFile("media.bin")
        writePattern(source, 8192 + 31)
        val payload = ArchivePayload.fromFile(source)
        RandomAccessFile(source, "rw").use { file ->
            val offset = file.length() - 1
            file.seek(offset)
            val value = file.readByte().toInt() xor 1
            file.seek(offset)
            file.writeByte(value)
        }

        assertThrows(java.io.IOException::class.java) { payload.copyTo(ByteArrayOutputStream()) }
    }

    @Test
    fun fileBackedPayloadRejectsSourceLengthChangeDuringCopy() {
        val source = temporary.newFile("media.bin")
        writePattern(source, 8192 + 31)
        val payload = ArchivePayload.fromFile(source)
        source.appendBytes(byteArrayOf(1))

        assertThrows(ZipSafetyException::class.java) { payload.copyTo(ByteArrayOutputStream()) }
    }

    @Test
    fun stagedArchiveSpoolsEntriesAndCleansItsDirectoryOnClose() {
        val archiveFile = temporary.newFile("archive.zip")
        archiveFile.writeBytes(zipOf("media.bin", generatedBytes(8192 + 17)))
        val parent = temporary.newFolder("staging-parent")

        val archive = archiveFile.inputStream().use {
            StagedZipArchive.read(it, ZipSafetyPolicy(4, 1L shl 20, 1L shl 20), parent)
        }
        val staging = parent.listFiles()!!.single()
        assertTrue(staging.isDirectory)
        assertEquals(8192L + 17, archive.entries.getValue("media.bin").size)
        assertArrayEquals(generatedBytes(8192 + 17), archive.entries.getValue("media.bin").readMetadata(1L shl 20))

        archive.close()

        assertFalse(staging.exists())
    }

    @Test
    fun truncatedArchiveCleansStagingDirectory() {
        val archiveFile = temporary.newFile("truncated.zip")
        val complete = zipOf("media.bin", generatedBytes(8192 + 17))
        archiveFile.writeBytes(complete.copyOf(complete.indexOfSignature(0x02014b50) - 1))
        val parent = temporary.newFolder("staging-parent")

        assertThrows(Exception::class.java) {
            archiveFile.inputStream().use {
                StagedZipArchive.read(it, ZipSafetyPolicy(4, 1L shl 20, 1L shl 20), parent)
            }
        }

        assertTrue(parent.listFiles().orEmpty().isEmpty())
    }

    private fun writePattern(file: File, size: Int) = FileOutputStream(file).use { output ->
        var remaining = size
        var value = 0
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val count = minOf(remaining, buffer.size)
            repeat(count) { buffer[it] = (value++ and 0xff).toByte() }
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun generatedBytes(size: Int): ByteArray = ByteArray(size) { (it and 0xff).toByte() }

    private fun zipOf(name: String, bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }.toByteArray()

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ByteArray.indexOfSignature(value: Int): Int {
        for (index in 0..size - 4) {
            if ((this[index].toInt() and 0xff) == (value and 0xff) &&
                (this[index + 1].toInt() and 0xff) == ((value ushr 8) and 0xff) &&
                (this[index + 2].toInt() and 0xff) == ((value ushr 16) and 0xff) &&
                (this[index + 3].toInt() and 0xff) == ((value ushr 24) and 0xff)
            ) return index
        }
        error("signature not found")
    }
}
