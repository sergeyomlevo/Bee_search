package org.beesearch.app.data.media

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID

class ObservationAttachmentFileStoreTest {
    @Test
    fun `import creates an app-owned copy with relative reference and digest`() = withStore { store, root ->
        val pointId = UUID.randomUUID()
        val attachmentId = UUID.randomUUID()
        val sourceBytes = "small-photo".toByteArray()

        val stored = runBlocking {
            store.importPhoto(pointId, attachmentId) { sourceBytes.inputStream() }
        }

        assertEquals("observation-attachments/$pointId/$attachmentId", stored.relativePath)
        assertEquals(sourceBytes.size.toLong(), stored.byteSize)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(sourceBytes).toHex(), stored.sha256)
        assertArrayEquals(sourceBytes, store.resolve(stored.relativePath).readBytes())
        assertTrue(store.resolve(stored.relativePath).canonicalPath.startsWith(root.canonicalPath))
    }

    @Test
    fun `staged deletion can rollback or commit without orphaning bytes`() = withStore { store, _ ->
        val stored = runBlocking {
            store.importPhoto(UUID.randomUUID(), UUID.randomUUID()) { "photo".byteInputStream() }
        }
        val original = store.resolve(stored.relativePath)

        val rollbackBatch = runBlocking { store.stageDeletion(listOf(stored.relativePath)) }
        assertFalse(original.exists())
        runBlocking { rollbackBatch.rollback() }
        assertTrue(original.exists())

        val commitBatch = runBlocking { store.stageDeletion(listOf(stored.relativePath)) }
        runBlocking { commitBatch.commit() }
        assertFalse(original.exists())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `relative reference cannot escape attachment root`() = withStore { store, _ ->
        store.resolve("observation-attachments/../outside")
    }

    private fun withStore(block: (ObservationAttachmentFileStore, File) -> Unit) {
        val base = Files.createTempDirectory("bee-attachments").toFile()
        try {
            val files = File(base, "files").apply(File::mkdirs)
            val cache = File(base, "cache").apply(File::mkdirs)
            block(ObservationAttachmentFileStore(files, cache), File(files, "observation-attachments"))
        } finally {
            base.deleteRecursively()
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
