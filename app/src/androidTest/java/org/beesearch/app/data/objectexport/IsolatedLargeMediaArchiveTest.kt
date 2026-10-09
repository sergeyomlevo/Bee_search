package org.beesearch.app.data.objectexport

import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.Random
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.data.zip.ArchivePayload
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit Samsung-only runtime gate for the M2B file-backed export boundary.
 * It never touches Room, the repository, or the normal managed-media store.
 */
@RunWith(AndroidJUnit4::class)
class IsolatedLargeMediaArchiveTest {
    @Test
    fun generatedMediaAboveLegacyCapsRoundTripsThroughProductionCodec() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "pass m2bLargeMedia=true explicitly",
            args.getString("m2bLargeMedia") == "true",
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        assertEquals("org.beesearch.app.dev", target.packageName)
        assertEquals("SM-S938B", Build.MODEL)
        assertEquals(36, Build.VERSION.SDK_INT)

        // Instrumentation runs under the target UID: use only our unique cache child,
        // never the target's database, settings, repository or managed-media directories.
        val storage = target.cacheDir.usableSpace
        assertTrue("at least 512 MiB free storage is required", storage >= MIN_FREE_BYTES)
        val runId = UUID.randomUUID().toString()
        val root = File(target.cacheDir, "m2b-large-media-$runId")
        val archive = File(root, "physical-object-v2.zip")
        val tempRoot = File(System.getProperty("java.io.tmpdir") ?: error("java.io.tmpdir unavailable"))
        val beforeStaging = stagingDirectories(tempRoot)
        val instrumentationStatus = Bundle()
        val sources = linkedMapOf<UUID, GeneratedMedia>()
        try {
            assertTrue(root.mkdirs())
            var graph = fixtureGraph()
            graph.media.forEachIndexed { index, media ->
                val source = File(root, "source-$index.bin")
                val generated = generate(source, media.id, index)
                sources[media.id] = generated
            }
            graph = graph.copy(media = graph.media.map { media ->
                val generated = sources.getValue(media.id)
                media.copy(byteSize = generated.size, sha256 = generated.sha256)
            })
            val payloads = sources.mapValues { (_, value) -> ArchivePayload.fromFile(value.file) }
            FileOutputStream(archive).use { output ->
                PhysicalObjectExportCodec.encode(graph, payloads, BufferedOutputStream(output))
            }
            assertTrue(archive.length() > 0L)

            PhysicalObjectExportCodec.decode(BufferedInputStream(FileInputStream(archive))).use { decoded ->
                assertEquals(graph, decoded.graph)
                graph.media.forEach { media ->
                    val expected = sources.getValue(media.id)
                    val actual = decoded.mediaBytes.getValue(media.id)
                    assertEquals(expected.size, actual.size)
                    assertEquals(expected.sha256, actual.sha256)
                    val actualFile = File(root, "decoded-${media.id}.bin")
                    actual.open().use { input ->
                        FileOutputStream(actualFile).use { output -> input.copyTo(output, BUFFER_SIZE) }
                    }
                    assertEquals(expected.size, actualFile.length())
                    assertEquals(expected.sha256, digest(actualFile))
                    assertEquals(media.id, decoded.graph.media.single { it.id == media.id }.id)
                }
                instrumentationStatus.putString("decoded", "production PhysicalObjectExportCodec")
            }
            assertEquals(beforeStaging, stagingDirectories(tempRoot))
            val oversizedMetadata = File(root, "oversized-metadata.zip")
            ZipOutputStream(oversizedMetadata.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(PhysicalObjectExportContract.MANIFEST_ENTRY))
                val buffer = ByteArray(BUFFER_SIZE)
                var left = PhysicalObjectExportContract.MAX_METADATA_ENTRY_BYTES + 1
                while (left > 0) {
                    val count = minOf(left, buffer.size.toLong()).toInt()
                    zip.write(buffer, 0, count)
                    left -= count
                }
                zip.closeEntry()
            }
            var metadataRejected = false
            try {
                PhysicalObjectExportCodec.decode(oversizedMetadata.inputStream()).use { }
            } catch (error: InvalidPhysicalObjectExport) {
                assertTrue("wrong metadata rejection: ${error.message}", error.message.orEmpty().contains("too large"))
                metadataRejected = true
            }
            assertTrue("metadata budget was not enforced", metadataRejected)
            assertEquals(beforeStaging, stagingDirectories(tempRoot))
            assertTrue("one media entry must exceed 16 MiB", sources.values.any { it.size > OLD_ENTRY_BYTES })
            assertTrue("aggregate media must exceed 64 MiB", sources.values.sumOf { it.size } > OLD_TOTAL_BYTES)
            instrumentationStatus.putLong("aggregateMediaBytes", sources.values.sumOf { it.size })
            instrumentationStatus.putLong("archiveBytes", archive.length())
            instrumentationStatus.putLong("freeBytesBefore", storage)
            instrumentationStatus.putBoolean("metadataOverLimitRejected", metadataRejected)
            instrumentationStatus.putLong("metadataEntryLimit", PhysicalObjectExportContract.MAX_METADATA_ENTRY_BYTES)
            instrumentationStatus.putLong("totalMetadataLimit", PhysicalObjectExportContract.MAX_TOTAL_METADATA_BYTES)
            instrumentationStatus.putString("fixtureRoot", root.absolutePath)
            sources.forEach { (id, value) ->
                instrumentationStatus.putLong("media.$id.bytes", value.size)
                instrumentationStatus.putString("media.$id.sha256", value.sha256)
            }
        } finally {
            sources.clear()
            assertTrue("large-media fixture cleanup failed", !root.exists() || root.deleteRecursively())
            assertFalse("fixture root remains after cleanup", root.exists())
            assertEquals("production archive staging leaked", beforeStaging, stagingDirectories(tempRoot))
        }
        instrumentationStatus.putLong("freeBytesAfterCleanup", target.cacheDir.usableSpace)
        instrumentationStatus.putBoolean("fixtureAndStagingCleanup", true)
        instrumentationStatus.putString("stream", "M2B runtime evidence: $instrumentationStatus\n")
        instrumentation.sendStatus(2, instrumentationStatus)
    }

    private fun fixtureGraph(): PhysicalObjectExportGraph {
        val objectId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val territoryId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val observerId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val media = (0 until MEDIA_COUNT).map { index ->
            val id = UUID.nameUUIDFromBytes("m2b-large-media-$index".toByteArray())
            PhysicalObjectMedia(
                id = id,
                physicalObjectId = objectId,
                type = PhysicalObjectMediaType.VIDEO,
                relativePath = PhysicalObjectMediaFileStore.relativePath(objectId, id),
                originalFileName = "m2b-$index.mp4",
                mimeType = "video/mp4",
                byteSize = MEDIA_BYTES,
                sha256 = "0".repeat(64),
                createdAt = Instant.parse("2026-09-20T10:0${index + 1}:00Z"),
            )
        }
        return PhysicalObjectExportGraph(
            id = objectId,
            type = PhysicalObjectType.HOLLOW,
            territoryId = territoryId,
            sequenceNumber = 7,
            latitude = 56.0,
            longitude = 42.0,
            createdAt = Instant.parse("2026-09-20T10:00:00Z"),
            creatorObserverId = observerId,
            name = "M2B runtime fixture",
            properties = PhysicalObjectExportProperties.Hollow(
                HollowProperties("oak", 180.0, 127, 40.0, 25.0, "runtime"),
            ),
            media = media,
            territory = TerritoryExportSnapshot(territoryId, "M2B", "Runtime fixture"),
            observer = ObserverExportSnapshot(observerId, "M2B-OBS", "Runtime", "Fixture", null),
            fixationDate = LocalDate.of(2026, 8, 19),
        )
    }

    private fun generate(file: File, id: UUID, index: Int): GeneratedMedia {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        val random = Random(id.mostSignificantBits xor id.leastSignificantBits xor index.toLong())
        BufferedOutputStream(FileOutputStream(file)).use { output ->
            while (total < MEDIA_BYTES) {
                random.nextBytes(buffer)
                val count = minOf(buffer.size.toLong(), MEDIA_BYTES - total).toInt()
                digest.update(buffer, 0, count)
                output.write(buffer, 0, count)
                total += count
            }
        }
        return GeneratedMedia(file, total, digest.digest().toHex())
    }

    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        BufferedInputStream(FileInputStream(file)).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun stagingDirectories(tempRoot: File): Set<String> =
        tempRoot.listFiles()?.filter { it.isDirectory && it.name.startsWith("bee-archive-") }
            ?.map { it.name }?.toSet().orEmpty()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private data class GeneratedMedia(val file: File, val size: Long, val sha256: String)

    private companion object {
        const val MEDIA_COUNT = 4
        const val MEDIA_BYTES = 20L * 1024L * 1024L
        const val OLD_ENTRY_BYTES = 16L * 1024L * 1024L
        const val OLD_TOTAL_BYTES = 64L * 1024L * 1024L
        const val MIN_FREE_BYTES = 512L * 1024L * 1024L
        const val BUFFER_SIZE = 64 * 1024
    }
}
