package org.beesearch.app.data.media

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for file-backed ingest beyond the former 16 MiB limit. */
class LargeMediaIngestTest {
    @Test
    fun `observation photo larger than former limit is staged and activated`() = runBlocking {
        withStores { root, pointStore, _ ->
            val source = Payload(20 * MiB, seed = 11)
            val sessionId = UUID.randomUUID()
            val attachmentId = UUID.randomUUID()
            val pointId = UUID.randomUUID()

            val staged = pointStore.stageDraftPhoto(
                sessionId,
                attachmentId,
                "large-photo.jpg",
                "image/jpeg",
                Instant.EPOCH,
            ) { source.open() }
            val activation = pointStore.prepareDraftActivation(sessionId, pointId, listOf(staged))
            activation.commit()

            assertStored(pointStore.resolve(activation.attachments.single().relativePath), source)
            assertEquals(source.size, activation.attachments.single().byteSize)
            assertEquals(source.sha256(), activation.attachments.single().sha256)
            assertTrue(root.walkTopDown().none { it.name.endsWith(".part") })
        }
    }

    @Test
    fun `physical object video larger than former limit is staged and activated`() = runBlocking {
        withStores { _, _, objectStore ->
            val source = Payload(20 * MiB, seed = 23)
            val sessionId = UUID.randomUUID()
            val mediaId = UUID.randomUUID()
            val objectId = UUID.randomUUID()

            val staged = objectStore.stageDraftMedia(
                sessionId,
                mediaId,
                PhysicalObjectMediaType.VIDEO,
                "large-video.mp4",
                "video/mp4",
                Instant.EPOCH,
            ) { source.open() }
            val activation = objectStore.prepareDraftActivation(sessionId, objectId, listOf(staged))
            activation.commit()

            val media = activation.media.single()
            assertEquals(PhysicalObjectMediaType.VIDEO, media.type)
            assertEquals("large-video.mp4", media.originalFileName)
            assertEquals("video/mp4", media.mimeType)
            assertStored(objectStore.resolve(media.relativePath), source)
            assertEquals(source.size, media.byteSize)
            assertEquals(source.sha256(), media.sha256)
        }
    }

    @Test
    fun `aggregate media above former 64 MiB budget remains file backed`() = runBlocking {
        withStores { _, _, objectStore ->
            val sources = listOf(Payload(20 * MiB, 31), Payload(52 * MiB, 37))
            val sessionId = UUID.randomUUID()
            val objectId = UUID.randomUUID()
            val staged = sources.mapIndexed { index, source ->
                objectStore.stageDraftMedia(
                    sessionId,
                    UUID.randomUUID(),
                    PhysicalObjectMediaType.IMAGE,
                    "large-$index.jpg",
                    "image/jpeg",
                    Instant.EPOCH,
                ) { source.open() }
            }

            assertTrue(sources.sumOf { it.size } > 64 * MiB)
            val activation = objectStore.prepareDraftActivation(sessionId, objectId, staged)
            activation.commit()
            activation.media.zip(sources).forEach { (media, source) ->
                assertStored(objectStore.resolve(media.relativePath), source)
                assertEquals(source.size, media.byteSize)
                assertEquals(source.sha256(), media.sha256)
            }
        }
    }

    @Test
    fun `direct imports preserve size and digest for large photo and video`() = runBlocking {
        withStores { _, pointStore, objectStore ->
            val photo = Payload(20 * MiB, 41)
            val video = Payload(20 * MiB, 43)
            val pointStored = pointStore.importPhoto(UUID.randomUUID(), UUID.randomUUID()) { photo.open() }
            val objectStored = objectStore.importMedia(UUID.randomUUID(), UUID.randomUUID()) { video.open() }

            assertEquals(photo.size, pointStored.byteSize)
            assertEquals(photo.sha256(), pointStored.sha256)
            assertStored(pointStore.resolve(pointStored.relativePath), photo)
            assertEquals(video.size, objectStored.byteSize)
            assertEquals(video.sha256(), objectStored.sha256)
            assertStored(objectStore.resolve(objectStored.relativePath), video)
        }
    }

    @Test
    fun `source failure during staged and direct import removes partial files`() = runBlocking {
        withStores { root, pointStore, objectStore ->
            val sessionId = UUID.randomUUID()
            assertThrows(IOException::class.java) {
                runBlocking {
                    pointStore.stageDraftPhoto(
                        sessionId,
                        UUID.randomUUID(),
                        null,
                        "image/jpeg",
                        Instant.EPOCH,
                    ) { FailingInputStream(20 * MiB, 5 * MiB) }
                }
            }
            assertThrows(IOException::class.java) {
                runBlocking {
                    objectStore.importMedia(UUID.randomUUID(), UUID.randomUUID()) {
                        FailingInputStream(20 * MiB, 5 * MiB)
                    }
                }
            }
            assertThrows(IOException::class.java) {
                runBlocking {
                    pointStore.importPhoto(UUID.randomUUID(), UUID.randomUUID()) {
                        FailingInputStream(20 * MiB, 5 * MiB)
                    }
                }
            }
            assertThrows(IOException::class.java) {
                runBlocking {
                    objectStore.stageDraftMedia(
                        sessionId,
                        UUID.randomUUID(),
                        PhysicalObjectMediaType.IMAGE,
                        null,
                        "image/jpeg",
                        Instant.EPOCH,
                    ) { FailingInputStream(20 * MiB, 5 * MiB) }
                }
            }
            assertTrue(root.walkTopDown().none { it.isFile })
        }
    }

    @Test
    fun `cancellation and empty input leave no final or partial media`() = runBlocking {
        withStores { root, pointStore, objectStore ->
            val pointJob = Job()
            assertThrows(CancellationException::class.java) {
                runBlocking(pointJob) {
                    pointStore.importPhoto(UUID.randomUUID(), UUID.randomUUID()) {
                        CancellingInputStream(20 * MiB, 2 * MiB) { pointJob.cancel() }
                    }
                }
            }
            val objectJob = Job()
            assertThrows(CancellationException::class.java) {
                runBlocking(objectJob) {
                    objectStore.stageDraftMedia(
                        UUID.randomUUID(), UUID.randomUUID(), PhysicalObjectMediaType.VIDEO,
                        null, "video/mp4", Instant.EPOCH,
                    ) { CancellingInputStream(20 * MiB, 2 * MiB) { objectJob.cancel() } }
                }
            }
            assertThrows(EmptyAttachmentException::class.java) {
                runBlocking {
                    pointStore.importPhoto(UUID.randomUUID(), UUID.randomUUID(), EMPTY_STREAM)
                }
            }
            assertThrows(EmptyAttachmentException::class.java) {
                runBlocking {
                    objectStore.importMedia(UUID.randomUUID(), UUID.randomUUID(), EMPTY_STREAM)
                }
            }
            assertTrue(root.walkTopDown().none { it.isFile })
        }
    }

    @Test
    fun `collision preserves existing managed media and removes failed partial`() = runBlocking {
        withStores { root, pointStore, objectStore ->
            val pointId = UUID.randomUUID()
            val attachmentId = UUID.randomUUID()
            val original = Payload(2 * MiB, 61)
            val replacement = Payload(2 * MiB, 67)
            val first = pointStore.importPhoto(pointId, attachmentId) { original.open() }
            assertThrows(AttachmentStorageException::class.java) {
                runBlocking { pointStore.importPhoto(pointId, attachmentId) { replacement.open() } }
            }
            assertStored(pointStore.resolve(first.relativePath), original)

            val objectId = UUID.randomUUID()
            val mediaId = UUID.randomUUID()
            val firstObject = objectStore.importMedia(objectId, mediaId) { original.open() }
            assertThrows(AttachmentStorageException::class.java) {
                runBlocking { objectStore.importMedia(objectId, mediaId) { replacement.open() } }
            }
            assertStored(objectStore.resolve(firstObject.relativePath), original)
            assertTrue(root.walkTopDown().none { it.name.endsWith(".part") })
        }
    }

    @Test
    fun `injected destination write failure leaves no published file or partial`() = runBlocking {
        val root = createTempDirectory("large-media-write-failure-").toFile()
        try {
            val partial = root.resolve("staging/media.part")
            val destination = root.resolve("managed/media")
            val source = Payload(2 * MiB, 79)

            assertThrows(IOException::class.java) {
                runBlocking {
                    writeAndPublishMediaFile(
                        partial = partial,
                        destination = destination,
                        source = source::open,
                        publicationError = "publish failed",
                    ) { file ->
                        FailingOutputStream(file.outputStream(), failAfter = 256 * 1024)
                    }
                }
            }

            assertFalse(destination.exists())
            assertFalse(partial.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `discarding a draft does not affect an existing managed file`() = runBlocking {
        withStores { _, pointStore, _ ->
            val pointId = UUID.randomUUID()
            val attachmentId = UUID.randomUUID()
            val source = Payload(2 * MiB, 71)
            val stored = pointStore.importPhoto(pointId, attachmentId) { source.open() }
            val draft = pointStore.stageDraftPhoto(
                UUID.randomUUID(), UUID.randomUUID(), null, "image/jpeg", Instant.EPOCH,
            ) { Payload(2 * MiB, 73).open() }
            assertTrue(pointStore.discardDraft(draft.draftSessionId))
            assertStored(pointStore.resolve(stored.relativePath), source)
        }
    }

    @Test
    fun `prompt cancellation after IO publication removes only the new file`() {
        val root = createTempDirectory("media-prompt-cancel-").toFile()
        val destination = root.resolve("new-media")
        val partial = root.resolve("new-media.part")
        val existing = root.resolve("existing-media").apply { writeText("keep") }
        val callerDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                // IO has published, but the caller has not yet received the completed result.
                if (destination.exists()) context[Job]?.cancel()
                Dispatchers.Default.dispatch(context, block)
            }
        }
        try {
            assertThrows(CancellationException::class.java) {
                runBlocking(callerDispatcher) {
                    writeAndPublishMediaFile(partial, destination, { Payload(MiB, 101).open() }, "publish failed")
                }
            }
            assertFalse(partial.exists())
            assertFalse(destination.exists())
            assertEquals("keep", existing.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `destination close failure cannot publish an otherwise complete file`() = runBlocking {
        val root = createTempDirectory("media-close-failure-").toFile()
        val destination = root.resolve("media")
        val partial = root.resolve("media.part")
        try {
            assertThrows(IOException::class.java) {
                runBlocking {
                    writeAndPublishMediaFile(partial, destination, { Payload(MiB, 103).open() }, "publish failed") { file ->
                        object : java.io.FilterOutputStream(file.outputStream()) {
                            override fun close() {
                                super.close()
                                throw IOException("synthetic close failure")
                            }
                        }
                    }
                }
            }
            assertFalse(partial.exists())
            assertFalse(destination.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private suspend fun withStores(
        block: suspend (File, ObservationAttachmentFileStore, PhysicalObjectMediaFileStore) -> Unit,
    ) {
        val root = createTempDirectory("large-media-ingest-").toFile()
        try {
            block(
                root,
                ObservationAttachmentFileStore(root.resolve("files"), root.resolve("cache")),
                PhysicalObjectMediaFileStore(root.resolve("files"), root.resolve("cache")),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertStored(file: File, payload: Payload) {
        assertEquals(payload.size, file.length())
        assertEquals(payload.sha256(), sha256(file))
    }

    private class Payload(val size: Long, private val seed: Int) {
        fun open(): InputStream = PatternInputStream(size, seed)

        fun sha256(): String = sha256(open()).toHex()
    }

    private open class PatternInputStream(
        private val total: Long,
        private val seed: Int,
    ) : InputStream() {
        protected var position = 0L

        override fun read(): Int {
            if (position >= total) return -1
            return nextByte()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= total) return -1
            val count = minOf(length.toLong(), total - position).toInt()
            repeat(count) { index -> buffer[offset + index] = nextByte().toByte() }
            return count
        }

        private fun nextByte(): Int = ((position++ * 31L + seed) and 0xff).toInt()
    }

    private class FailingInputStream(
        private val total: Long,
        private val failureAt: Long,
    ) : PatternInputStream(total, 89) {
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= failureAt) throw IOException("synthetic source failure")
            return super.read(buffer, offset, minOf(length.toLong(), failureAt - position).toInt())
        }
    }

    private class CancellingInputStream(
        total: Long,
        private val cancelAt: Long,
        private val cancelJob: () -> Unit,
    ) : PatternInputStream(total, 97) {
        private var cancelled = false

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= cancelAt) throw CancellationException("synthetic cancellation")
            val count = super.read(buffer, offset, minOf(length.toLong(), cancelAt - position).toInt())
            if (!cancelled && position >= cancelAt) {
                cancelled = true
                cancelJob()
            }
            return count
        }
    }

    private class EmptyInputStream : InputStream() {
        override fun read(): Int = -1
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
    }

    private class FailingOutputStream(
        private val delegate: OutputStream,
        private val failAfter: Long,
    ) : OutputStream() {
        private var written = 0L

        override fun write(value: Int) {
            if (written >= failAfter) throw IOException("synthetic destination failure")
            delegate.write(value)
            written++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (written + length > failAfter) throw IOException("synthetic destination failure")
            delegate.write(buffer, offset, length)
            written += length
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()
    }

    private companion object {
        const val MiB = 1024L * 1024L
        val EMPTY_STREAM: () -> InputStream = { EmptyInputStream() }

        fun sha256(input: InputStream): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            return digest.digest()
        }

        fun sha256(file: File): String = sha256(file.inputStream()).toHex()

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
