package org.beesearch.app.data.media

import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationAttachmentDraftFileStoreTest {
    @Test
    fun `draft photo stays outside final storage and cancel removes its session`() = runBlocking {
        withStore { root, store ->
            val sessionId = UUID.randomUUID()
            val photo = store.stageDraftPhoto(
                draftSessionId = sessionId,
                attachmentId = UUID.randomUUID(),
                originalFileName = "field photo.jpg",
                mimeType = "image/jpeg",
                createdAt = Instant.EPOCH,
                source = { ByteArrayInputStream(PHOTO) },
            )

            assertTrue(store.resolveDraftPhoto(photo.relativePath).isFile)
            assertFalse(store.attachmentsRoot.exists())
            assertTrue(store.discardDraft(sessionId))
            assertFalse(store.resolveDraftPhoto(photo.relativePath).exists())
            assertTrue(root.resolve("files/observation-attachments-staging").listFiles().isNullOrEmpty())
        }
    }

    @Test
    fun `activation uses technical final path and preserves display metadata`() = runBlocking {
        withStore { _, store ->
            val sessionId = UUID.randomUUID()
            val attachmentId = UUID.randomUUID()
            val pointId = UUID.randomUUID()
            val photo = store.stageDraftPhoto(
                sessionId,
                attachmentId,
                originalFileName = "пчела на кормушке.jpg",
                mimeType = "image/jpeg",
                createdAt = Instant.EPOCH,
                source = { ByteArrayInputStream(PHOTO) },
            )

            val activation = store.prepareDraftActivation(sessionId, pointId, listOf(photo))
            val attachment = activation.attachments.single()
            assertEquals("observation-attachments/$pointId/$attachmentId", attachment.relativePath)
            assertEquals("пчела на кормушке.jpg", attachment.originalFileName)
            assertEquals(PHOTO.size.toLong(), attachment.byteSize)
            assertTrue(store.resolve(attachment.relativePath).isFile)
            assertFalse(store.resolveDraftPhoto(photo.relativePath).exists())

            activation.commit()
            assertFalse(store.draftStagingRoot.resolve(sessionId.toString()).exists())
        }
    }

    @Test
    fun `activation rollback restores draft and removes final file`() = runBlocking {
        withStore { _, store ->
            val sessionId = UUID.randomUUID()
            val photo = store.stageDraftPhoto(
                sessionId,
                UUID.randomUUID(),
                originalFileName = null,
                mimeType = "image/jpeg",
                createdAt = Instant.EPOCH,
                source = { ByteArrayInputStream(PHOTO) },
            )
            val activation = store.prepareDraftActivation(sessionId, UUID.randomUUID(), listOf(photo))
            val finalPath = activation.attachments.single().relativePath

            activation.rollback()

            assertTrue(store.resolveDraftPhoto(photo.relativePath).isFile)
            assertFalse(store.resolve(finalPath).exists())
        }
    }

    @Test
    fun `removing one draft photo removes bytes without creating metadata`() = runBlocking {
        withStore { _, store ->
            val photo = store.stageDraftPhoto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                originalFileName = null,
                mimeType = "image/jpeg",
                createdAt = Instant.EPOCH,
                source = { ByteArrayInputStream(PHOTO) },
            )
            assertTrue(store.deleteDraftPhoto(photo))
            assertFalse(store.resolveDraftPhoto(photo.relativePath).exists())
        }
    }

    private suspend fun withStore(block: suspend (File, ObservationAttachmentFileStore) -> Unit) {
        val root = createTempDirectory("observation-draft-").toFile()
        try {
            block(root, ObservationAttachmentFileStore(root.resolve("files"), root.resolve("cache")))
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        val PHOTO = "small-photo".encodeToByteArray()
    }
}
