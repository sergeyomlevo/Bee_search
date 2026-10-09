package org.beesearch.app.data.backup

import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.beesearch.app.data.local.room.ObservationPointAttachmentEntity
import org.beesearch.app.data.local.room.PhysicalObjectEntity
import org.beesearch.app.data.local.room.PhysicalObjectMediaEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.data.zip.ArchivePayload
import org.beesearch.app.data.zip.StagedZipArchive
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompleteBackupStreamingMediaTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun downstreamV7AcceptsMediaAboveOldEntryAndTotalCapsWithWriterReaderSymmetry() {
        val fixture = mediaFixture()
        val size = 65L * 1024 * 1024 + 17
        fixture.objectFile.outputStream().buffered().use { output ->
            var left = size
            val buffer = ByteArray(8192) { 7 }
            while (left > 0) { val count = minOf(left, buffer.size.toLong()).toInt(); output.write(buffer, 0, count); left -= count }
        }
        val payload = ArchivePayload.fromFile(fixture.objectFile)
        val media = fixture.graph.objectMedia.single().copy(byteSize = payload.size, sha256 = payload.sha256)
        val graph = fixture.graph.copy(objectMedia = listOf(media))
        val blobs = blobsV7(graph, PortableSettingsSnapshot(null, null, emptyMap()), fixture.attachments, fixture.objectMedia)
        val manifestBytes = manifest(blobs)
        invoke<Any>("validateBackupWriter", blobs, manifestBytes)
        val file = temporary.newFile("large-v7.zip")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            // Reordering remains valid: media may precede manifest/metadata.
            blobs.reversed().forEach { put(zip, blobPath(it), blobPayload(it)) }
            put(zip, MANIFEST, ArchivePayload.metadata(manifestBytes))
        }
        readArchive(file).use { archive ->
            parse(archive.entries)
            val actual = archive.entries.getValue(blobs.first { blobPath(it).startsWith("physical-object-media/") }.let(::blobPath))
            assertEquals(size, actual.size)
            assertEquals(payload.sha256, actual.sha256)
        }
    }

    @Test
    fun v7WriterStreamsPhotoAndPhysicalMediaThroughReaderAndParser() {
        val fixture = mediaFixture()
        val blobs = blobsV7(fixture.graph, PortableSettingsSnapshot(null, null, emptyMap()), fixture.attachments, fixture.objectMedia)
        val manifest = manifest(blobs)
        val archiveFile = temporary.newFile("backup-v7.zip")
        ZipOutputStream(archiveFile.outputStream()).use { zip ->
            put(zip, MANIFEST, ArchivePayload.metadata(manifest))
            blobs.forEach { blob -> put(zip, blobPath(blob), blobPayload(blob)) }
        }

        readArchive(archiveFile).use { archive ->
            val parsed = parse(archive.entries)
            val graph = parsed.javaClass.getDeclaredMethod("getGraph").also { it.isAccessible = true }.invoke(parsed) as Graph
            assertEquals(fixture.attachmentBytes.size.toLong(), graph.attachments.single().byteSize)
            assertEquals(fixture.objectBytes.size.toLong(), graph.objectMedia.single().byteSize)
            assertEquals(sha(fixture.attachmentBytes), graph.attachments.single().sha256)
            assertEquals(sha(fixture.objectBytes), graph.objectMedia.single().sha256)
            assertEquals(LocalDate.of(2026, 8, 25), graph.points.single().observationDate)
            assertEquals(LocalDate.of(2026, 8, 21), graph.physicalObjects.single().fixationDate)
        }
    }

    @Test
    fun v7ReaderRejectsMediaHashAndSizeMismatchBeforeActivation() {
        val fixture = mediaFixture()
        val blobs = blobsV7(fixture.graph, PortableSettingsSnapshot(null, null, emptyMap()), fixture.attachments, fixture.objectMedia)
        val original = String(manifest(blobs), Charsets.UTF_8)
        val invalidManifests = listOf(
            original.replace("\"sha256\":\"${sha(fixture.attachmentBytes)}\"", "\"sha256\":\"${"0".repeat(64)}\""),
            original.replace("\"byteLength\":${fixture.attachmentBytes.size}", "\"byteLength\":${fixture.attachmentBytes.size + 1}"),
        )
        invalidManifests.forEachIndexed { index, invalid ->
            assertTrue(original != invalid)
            val file = temporary.newFile("mismatch-$index.zip")
            ZipOutputStream(file.outputStream()).use { zip ->
                put(zip, MANIFEST, ArchivePayload.metadata(invalid.toByteArray()))
                blobs.forEach { put(zip, blobPath(it), blobPayload(it)) }
            }
            assertThrows(org.beesearch.app.domain.backup.BackupIntegrityMismatch::class.java) {
                readArchive(file).use { parse(it.entries) }
            }
        }
    }

    @Test
    fun v7WriterPayloadRejectsChangedSourceBeforeArchiveCompletion() {
        val fixture = mediaFixture()
        val blobs = blobsV7(fixture.graph, PortableSettingsSnapshot(null, null, emptyMap()), fixture.attachments, fixture.objectMedia)
        val payload = blobs.first { blobPath(it).startsWith("physical-object-media/") }.let(::blobPayload)
        fixture.objectFile.appendBytes(byteArrayOf(1))

        assertThrows(org.beesearch.app.data.zip.ZipSafetyException::class.java) { payload.copyTo(ByteArrayOutputStream()) }
    }

    private fun mediaFixture(): Fixture {
        val root = temporary.newFolder("media-files")
        val cache = temporary.newFolder("media-cache")
        val attachments = ObservationAttachmentFileStore(root, cache)
        val objectMedia = PhysicalObjectMediaFileStore(root, cache)
        val created = Instant.parse("2026-08-20T12:00:00Z")
        val territoryId = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val observerId = UUID.fromString("00000000-0000-0000-0000-000000000102")
        val pointId = UUID.fromString("00000000-0000-0000-0000-000000000103")
        val objectId = UUID.fromString("00000000-0000-0000-0000-000000000104")
        val attachmentId = UUID.fromString("00000000-0000-0000-0000-000000000105")
        val mediaId = UUID.fromString("00000000-0000-0000-0000-000000000106")
        val attachmentBytes = generatedBytes(3 * 8192 + 17, 0x11)
        val objectBytes = generatedBytes(4 * 8192 + 29, 0x22)
        val attachmentPath = ObservationAttachmentFileStore.relativePath(pointId, attachmentId)
        val objectPath = PhysicalObjectMediaFileStore.relativePath(objectId, mediaId)
        val attachmentFile = attachments.resolve(attachmentPath).also { requireNotNull(it.parentFile).mkdirs(); it.writeBytes(attachmentBytes) }
        val objectFile = objectMedia.resolve(objectPath).also { requireNotNull(it.parentFile).mkdirs(); it.writeBytes(objectBytes) }
        val graph = Graph(
            territories = listOf(TerritoryEntity(territoryId, "T", "Territory", "R", "D", created, created)),
            observers = listOf(ObserverEntity(observerId, "O", "Last", "First", null, null, created, created)),
            physicalObjects = listOf(PhysicalObjectEntity(objectId, territoryId, PhysicalObjectType.HOLLOW, 1, 56.0, 42.0, created, observerId, LocalDate.of(2026, 8, 21))),
            hollows = listOf(org.beesearch.app.data.local.room.HollowEntity(objectId, null, null, null, null, null, null)),
            points = listOf(ObservationPointEntity(LocalDate.of(2026, 8, 25), pointId, territoryId, observerId, 2026, 1, null, "P", 56.1, 42.7, null, null, null, created, null, null, null)),
            objectMedia = listOf(PhysicalObjectMediaEntity(mediaId, objectId, PhysicalObjectMediaType.VIDEO, objectPath, "video.bin", "video/mp4", objectBytes.size.toLong(), sha(objectBytes), created)),
            attachments = listOf(ObservationPointAttachmentEntity(attachmentId, pointId, AttachmentType.PHOTO, attachmentPath, "photo.jpg", "image/jpeg", attachmentBytes.size.toLong(), sha(attachmentBytes), created)),
            bees = emptyList(), cycles = emptyList(), weather = emptyList(), apiaries = emptyList(), logHives = emptyList(), sequences = emptyList(),
        )
        return Fixture(graph, attachments, objectMedia, attachmentBytes, objectBytes, objectFile)
    }

    private data class Fixture(
        val graph: Graph,
        val attachments: ObservationAttachmentFileStore,
        val objectMedia: PhysicalObjectMediaFileStore,
        val attachmentBytes: ByteArray,
        val objectBytes: ByteArray,
        val objectFile: File,
    )

    private fun blobsV7(graph: Graph, settings: PortableSettingsSnapshot, attachments: ObservationAttachmentFileStore, objectMedia: PhysicalObjectMediaFileStore): List<Any> =
        invoke("blobsV7", graph, settings, attachments, objectMedia, 7)

    private fun manifest(blobs: List<Any>): ByteArray = invoke("manifest", UUID.randomUUID(), Instant.parse("2026-08-20T12:00:00Z"), "test", blobs, 7)

    private fun blobPath(blob: Any): String = blob.javaClass.getDeclaredMethod("getPath").also { it.isAccessible = true }.invoke(blob) as String
    private fun blobPayload(blob: Any): ArchivePayload = blob.javaClass.getDeclaredMethod("getPayload").also { it.isAccessible = true }.invoke(blob) as ArchivePayload
    private fun put(zip: ZipOutputStream, name: String, payload: ArchivePayload) {
        val method = Class.forName("org.beesearch.app.data.backup.BackupCoreKt").declaredMethods.single {
            it.name == "put" && it.parameterTypes.last() == ArchivePayload::class.java
        }.also { it.isAccessible = true }
        try { method.invoke(null, zip, name, payload) } catch (error: InvocationTargetException) { throw error.targetException }
    }
    private fun readArchive(file: File): StagedZipArchive = invoke("readArchive", file)
    private fun parse(entries: Map<String, ArchivePayload>): Any = invoke("parse", entries)

    private inline fun <reified T> invoke(name: String, vararg args: Any): T {
        val method = Class.forName("org.beesearch.app.data.backup.BackupCoreKt").declaredMethods.single { it.name == name && it.parameterCount == args.size }.also { it.isAccessible = true }
        return try { method.invoke(null, *args) as T } catch (error: InvocationTargetException) { throw error.targetException }
    }

    private fun generatedBytes(size: Int, seed: Int) = ByteArray(size) { ((it + seed) and 0xff).toByte() }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private companion object { const val MANIFEST = "manifest.json" }
}
