package org.beesearch.app.data.media

import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalObjectMediaFileStoreTest {
    @Test
    fun `multiple image and video items activate as separate owned files`() = runBlocking {
        withStore { store ->
            val session = UUID.randomUUID()
            val objectId = UUID.randomUUID()
            val image = store.stageDraftMedia(
                session, UUID.randomUUID(), PhysicalObjectMediaType.IMAGE,
                "tree.jpg", "image/jpeg", Instant.EPOCH,
            ) { ByteArrayInputStream(IMAGE) }
            val video = store.stageDraftMedia(
                session, UUID.randomUUID(), PhysicalObjectMediaType.VIDEO,
                "flight.mp4", "video/mp4", Instant.EPOCH,
            ) { ByteArrayInputStream(VIDEO) }

            val activation = store.prepareDraftActivation(session, objectId, listOf(image, video))

            assertEquals(listOf(PhysicalObjectMediaType.IMAGE, PhysicalObjectMediaType.VIDEO), activation.media.map { it.type })
            assertEquals(2, activation.media.map { it.relativePath }.distinct().size)
            activation.media.forEach {
                assertEquals(objectId, it.physicalObjectId)
                assertTrue(store.resolve(it.relativePath).isFile)
            }
            activation.commit()
        }
    }

    @Test
    fun `activation rollback restores draft bytes`() = runBlocking {
        withStore { store ->
            val session = UUID.randomUUID()
            val staged = store.stageDraftMedia(
                session, UUID.randomUUID(), PhysicalObjectMediaType.IMAGE,
                null, "image/jpeg", Instant.EPOCH,
            ) { ByteArrayInputStream(IMAGE) }
            val activation = store.prepareDraftActivation(session, UUID.randomUUID(), listOf(staged))
            val finalPath = activation.media.single().relativePath

            activation.rollback()

            assertTrue(store.resolveDraft(staged.relativePath).isFile)
            assertFalse(store.resolve(finalPath).exists())
        }
    }

    private suspend fun withStore(block: suspend (PhysicalObjectMediaFileStore) -> Unit) {
        val root = createTempDirectory("physical-object-media-").toFile()
        try {
            block(PhysicalObjectMediaFileStore(root.resolve("files"), root.resolve("cache")))
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        val IMAGE = "image-bytes".encodeToByteArray()
        val VIDEO = "video-bytes".encodeToByteArray()
    }
}
