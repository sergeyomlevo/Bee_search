package org.beesearch.app.data.media

import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.beesearch.app.domain.model.UserPointPhoto
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPointMediaStoreTest {
    @Test
    fun `user point namespace is isolated and activation commits multiple photos`() = runBlocking {
        withStore { root, store ->
            val physical = PhysicalObjectMediaFileStore(root.resolve("files"), root.resolve("cache"))
            val physicalSession = UUID.randomUUID()
            val physicalItem = physical.stageDraftMedia(
                physicalSession, UUID.randomUUID(), PhysicalObjectMediaType.IMAGE,
                "research.jpg", "image/jpeg", Instant.EPOCH,
            ) { ByteArrayInputStream(BYTES_A) }
            val physicalActivation = physical.prepareDraftActivation(
                physicalSession, UUID.randomUUID(), listOf(physicalItem),
            )
            physicalActivation.commit()
            val physicalPath = physicalActivation.media.single().relativePath
            val physicalBytes = physical.resolve(physicalPath).readBytes()
            val session = UUID.randomUUID()
            val point = UUID.randomUUID()
            val first = store.stageDraftMedia(session, UUID.randomUUID(), "a.jpg", "image/jpeg", Instant.EPOCH) {
                ByteArrayInputStream(BYTES_A)
            }
            val second = store.stageDraftMedia(session, UUID.randomUUID(), "b.jpg", "image/jpeg", Instant.EPOCH) {
                ByteArrayInputStream(BYTES_B)
            }
            val activation = store.prepareDraftActivation(session, point, listOf(first, second))
            activation.commit()

            assertEquals(2, activation.photos.size)
            assertTrue(activation.photos.all { it.relativePath.startsWith("user-point-media/$point/") })
            assertTrue(activation.photos.all { store.resolve(it.relativePath).isFile })
            assertTrue(root.resolve("files/physical-object-media").exists())
            assertTrue(physical.resolve(physicalPath).isFile)
            assertEquals(BYTES_A.toList(), physicalBytes.toList())
            assertEquals(BYTES_A.toList(), physical.resolve(physicalPath).readBytes().toList())
        }
    }

    @Test
    fun `rollback restores staged bytes and external source remains untouched`() = runBlocking {
        withStore { root, store ->
            val source = root.resolve("original.jpg")
            source.writeBytes(BYTES_A)
            val session = UUID.randomUUID()
            val staged = store.stageDraftMedia(session, UUID.randomUUID(), source.name, "image/jpeg", Instant.EPOCH) {
                source.inputStream()
            }
            val activation = store.prepareDraftActivation(session, UUID.randomUUID(), listOf(staged))
            activation.rollback()

            assertEquals(BYTES_A.toList(), source.readBytes().toList())
            assertEquals(BYTES_A.toList(), store.resolveDraft(staged.relativePath).readBytes().toList())
        }
    }

    @Test
    fun `corrupt staged bytes fail closed`() = runBlocking {
        withStore { _, store ->
            val session = UUID.randomUUID()
            val staged = store.stageDraftMedia(session, UUID.randomUUID(), null, "image/jpeg", Instant.EPOCH) {
                ByteArrayInputStream(BYTES_A)
            }
            store.resolveDraft(staged.relativePath).writeBytes("corrupt".encodeToByteArray())
            assertThrows(AttachmentStorageException::class.java) {
                runBlocking { store.prepareDraftActivation(session, UUID.randomUUID(), listOf(staged)) }
            }
        }
    }

    @Test
    fun `requireAvailable rejects a foreign owner tuple and malformed path`() = runBlocking {
        withStore { _, store ->
            val point = UUID.randomUUID()
            val photoId = UUID.randomUUID()
            val photo = UserPointPhoto(
                photoId, UUID.randomUUID(), "user-point-media/$point/$photoId", "x.jpg", "image/jpeg",
                BYTES_A.size.toLong(), "a".repeat(64), Instant.EPOCH,
            )
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.requireAvailable(point, listOf(photo)) }
            }
            val malformed = photo.copy(userPointId = point, relativePath = "user-point-media/$point/other")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.requireAvailable(point, listOf(malformed)) }
            }
        }
    }

    @Test
    fun `cleanup is idempotent and a nonempty owner directory fails closed then recovers`() = runBlocking {
        withStore { root, store ->
            val point = UUID.randomUUID()
            val session = UUID.randomUUID()
            val staged = store.stageDraftMedia(session, UUID.randomUUID(), "a.jpg", "image/jpeg", Instant.EPOCH) {
                ByteArrayInputStream(BYTES_A)
            }
            val activation = store.prepareDraftActivation(session, point, listOf(staged))
            activation.commit()
            val photo = activation.photos.single()
            val managed = store.resolve(photo.relativePath)
            assertTrue(managed.isFile)
            assertTrue(store.cleanupOwned(point, listOf(photo)))
            assertTrue(store.cleanupOwned(point, listOf(photo)))

            val session2 = UUID.randomUUID()
            val staged2 = store.stageDraftMedia(session2, UUID.randomUUID(), "b.jpg", "image/jpeg", Instant.EPOCH) {
                ByteArrayInputStream(BYTES_B)
            }
            val activation2 = store.prepareDraftActivation(session2, point, listOf(staged2))
            activation2.commit()
            val photo2 = activation2.photos.single()
            val blockingDirectory = store.resolve(photo2.relativePath)
            assertTrue(blockingDirectory.delete())
            assertTrue(blockingDirectory.mkdirs())
            val child = File(blockingDirectory, "child").apply { writeBytes(BYTES_A) }
            assertFalse(store.cleanupOwned(point, listOf(photo2)))
            assertTrue(child.delete())
            assertTrue(blockingDirectory.delete())
            assertTrue(store.cleanupOwned(point, listOf(photo2)))
        }
    }

    @Test
    fun `cleanup refuses another point photo even with forged owner metadata`() = runBlocking {
        withStore { _, store ->
            val owner = UUID.randomUUID()
            val other = UUID.randomUUID()
            val session = UUID.randomUUID()
            val staged = store.stageDraftMedia(session, UUID.randomUUID(), "keep.jpg", "image/jpeg", Instant.EPOCH) {
                ByteArrayInputStream(BYTES_A)
            }
            val activation = store.prepareDraftActivation(session, owner, listOf(staged))
            activation.commit()
            val photo = activation.photos.single()
            assertFalse(store.cleanupOwned(other, listOf(photo)))
            assertFalse(store.cleanupOwned(other, listOf(photo.copy(userPointId = other))))
            assertEquals(BYTES_A.toList(), store.resolve(photo.relativePath).readBytes().toList())
            assertTrue(store.cleanupOwned(owner, listOf(photo), removeDirectory = true))
            assertFalse(store.resolve(photo.relativePath).exists())
        }
    }

    private suspend fun withStore(block: suspend (java.io.File, UserPointMediaStore) -> Unit) {
        val root = createTempDirectory("user-point-media-").toFile()
        try {
            block(root, UserPointMediaStore(root.resolve("files"), root.resolve("cache")))
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        val BYTES_A = "photo-a".encodeToByteArray()
        val BYTES_B = "photo-b".encodeToByteArray()
    }
}
