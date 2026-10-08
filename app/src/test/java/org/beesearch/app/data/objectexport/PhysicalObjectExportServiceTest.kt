package org.beesearch.app.data.objectexport

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The service boundary of one object package: which source read is used, which bytes are packed, and
 * which failures refuse the export instead of producing a package that looks successful.
 */
class PhysicalObjectExportServiceTest {
    @Test
    fun `hollow and log hive export their own object owned data and media`() = withStore { store ->
        listOf(PhysicalObjectType.HOLLOW, PhysicalObjectType.LOG_HIVE).forEach { type ->
            val graph = graph(type).copy(fixationDate = LocalDate.of(2026, 10, 6))
            val mediaFile = storeFile(store, graph)
            val calls = mutableListOf<UUID>()
            val service = PhysicalObjectExportService(
                source = PhysicalObjectExportSource { id -> calls += id; graph },
                mediaStore = store,
            )
            val output = ByteArrayOutputStream()

            runBlocking { service.export(graph.id, output) }

            assertEquals(listOf(graph.id), calls)
            val decoded = PhysicalObjectExportCodec.decode(output.toByteArray().inputStream())
            assertEquals(graph, decoded.graph)
            assertArrayEquals(mediaFile.readBytes(), decoded.mediaBytes.getValue(graph.media.single().id))
        }
    }

    @Test
    fun `package carries the minimal territory and observer snapshot and never a foreign entity`() =
        withStore { store ->
            val graph = graph(PhysicalObjectType.HOLLOW)
            storeFile(store, graph)
            val service = PhysicalObjectExportService(
                source = PhysicalObjectExportSource { graph },
                mediaStore = store,
            )
            val output = ByteArrayOutputStream()

            runBlocking { service.export(graph.id, output) }

            val decoded = PhysicalObjectExportCodec.decode(output.toByteArray().inputStream()).graph
            assertEquals(graph.territory, decoded.territory)
            assertEquals(graph.observer, decoded.observer)
            assertEquals(graph.creatorObserverId, decoded.creatorObserverId)
            assertEquals(
                setOf("object", "properties", "media", "territory", "observer"),
                objectJsonKeys(output.toByteArray()),
            )
        }

    @Test
    fun `null creator observer exports a valid package without an observer snapshot`() = withStore { store ->
        val graph = graph(PhysicalObjectType.HOLLOW).copy(creatorObserverId = null, observer = null)
        storeFile(store, graph)
        val service = PhysicalObjectExportService(
            source = PhysicalObjectExportSource { graph },
            mediaStore = store,
        )
        val output = ByteArrayOutputStream()

        runBlocking { service.export(graph.id, output) }

        val decoded = PhysicalObjectExportCodec.decode(output.toByteArray().inputStream()).graph
        assertNull(decoded.observer)
        assertNull(decoded.creatorObserverId)
    }

    @Test
    fun `missing damaged and foreign media refuse the export`() = withStore { store ->
        val graph = graph(PhysicalObjectType.HOLLOW)

        val missing = PhysicalObjectExportService(
            source = PhysicalObjectExportSource { graph },
            mediaStore = store,
        )
        assertFails<PhysicalObjectExportSourceMissing> { runBlocking { missing.export(graph.id, ByteArrayOutputStream()) } }

        val managed = storeFile(store, graph)
        managed.writeText("different-length")
        assertFails<PhysicalObjectExportIntegrityError> {
            runBlocking { missing.export(graph.id, ByteArrayOutputStream()) }
        }

        managed.writeText("wrong-hash")
        assertFails<PhysicalObjectExportIntegrityError> {
            runBlocking { missing.export(graph.id, ByteArrayOutputStream()) }
        }

        managed.delete()
        assertFails<PhysicalObjectExportSourceMissing> { runBlocking { missing.export(graph.id, ByteArrayOutputStream()) } }
    }

    @Test
    fun `unknown object and written destination do not mutate the source`() = withStore { store ->
        val graph = graph(PhysicalObjectType.HOLLOW)
        storeFile(store, graph)
        val service = PhysicalObjectExportService(PhysicalObjectExportSource { null }, store)

        assertFails<PhysicalObjectExportSourceMissing> { runBlocking { service.export(UUID.randomUUID(), ByteArrayOutputStream()) } }

        val failing = PhysicalObjectExportService(PhysicalObjectExportSource { graph }, store)
        assertFails<IOException> {
            runBlocking {
                failing.export(
                    graph.id,
                    object : OutputStream() {
                        override fun write(value: Int) = throw IOException("destination failed")
                    },
                )
            }
        }
        assertEquals(graph, graph)
    }

    @Test
    fun `collection service exports every source object and only owned media`() = withStore { store ->
        val first = graph(PhysicalObjectType.HOLLOW)
        val second = graph(PhysicalObjectType.HOLLOW).withIdentity(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            5,
        )
        listOf(first, second).forEach { storeFile(store, it) }
        val collection = PhysicalObjectCollectionExportGraph(first.territory, PhysicalObjectType.HOLLOW, listOf(first, second))
        val calls = mutableListOf<Pair<UUID, PhysicalObjectType>>()
        val service = PhysicalObjectCollectionExportService(
            source = PhysicalObjectCollectionExportSource { territory, type ->
                calls += territory to type
                collection
            },
            mediaStore = store,
        )
        val output = ByteArrayOutputStream()

        runBlocking { service.export(first.territoryId, PhysicalObjectType.HOLLOW, output) }

        val decoded = PhysicalObjectCollectionExportCodec.decode(output.toByteArray().inputStream())
        assertEquals(listOf(first.territoryId to PhysicalObjectType.HOLLOW), calls)
        assertEquals(setOf(first.id, second.id), decoded.graph.objects.map { it.id }.toSet())
        assertEquals(setOf(first.media.single().id, second.media.single().id), decoded.mediaBytes.keys)
        assertEquals(collection, collection)
    }

    @Test
    fun `empty collection fails with a typed result before writing`() = withStore { store ->
        val graph = graph(PhysicalObjectType.HOLLOW)
        val service = PhysicalObjectCollectionExportService(
            PhysicalObjectCollectionExportSource { _, type ->
                PhysicalObjectCollectionExportGraph(graph.territory, type, emptyList())
            },
            store,
        )
        val output = ByteArrayOutputStream()

        assertFails<EmptyPhysicalObjectCollectionExport> {
            runBlocking { service.export(graph.territoryId, PhysicalObjectType.HOLLOW, output) }
        }
        assertEquals(0, output.size())
    }

    @Test
    fun `one corrupt media aborts the whole collection before any package bytes are written`() = withStore { store ->
        val first = graph(PhysicalObjectType.HOLLOW)
        val second = graph(PhysicalObjectType.HOLLOW).withIdentity(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            5,
        )
        storeFile(store, first)
        val corrupt = storeFile(store, second).apply { writeText("corrupt") }
        assertTrue(corrupt.isFile)
        val service = PhysicalObjectCollectionExportService(
            PhysicalObjectCollectionExportSource { _, _ ->
                PhysicalObjectCollectionExportGraph(first.territory, PhysicalObjectType.HOLLOW, listOf(first, second))
            },
            store,
        )
        val output = ByteArrayOutputStream()
        assertFails<PhysicalObjectExportIntegrityError> {
            runBlocking { service.export(first.territoryId, PhysicalObjectType.HOLLOW, output) }
        }
        assertEquals(0, output.size())
    }

    /** The declared top-level schema of `object.json`: nothing foreign, nothing missing. */
    private fun objectJsonKeys(archive: ByteArray): Set<String> =
        kotlinx.serialization.json.Json.parseToJsonElement(entryText(archive, "object.json"))
            .jsonObject.keys

    private fun entryText(archive: ByteArray, name: String): String =
        java.util.zip.ZipInputStream(archive.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return@use zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
            error("entry $name is missing")
        }

    private fun storeFile(store: PhysicalObjectMediaFileStore, graph: PhysicalObjectExportGraph): File {
        val media = graph.media.single()
        val file = store.resolve(media.relativePath)
        file.parentFile?.mkdirs()
        file.writeText("media-bytes")
        return file
    }

    private fun graph(type: PhysicalObjectType): PhysicalObjectExportGraph {
        val objectId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val territoryId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val observerId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val mediaId = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val content = "media-bytes".toByteArray()
        return PhysicalObjectExportGraph(
            id = objectId,
            type = type,
            territoryId = territoryId,
            sequenceNumber = 4,
            latitude = 56.1961784,
            longitude = 42.7480444,
            createdAt = Instant.parse("2026-09-20T10:00:00Z"),
            creatorObserverId = observerId,
            name = null,
            properties = when (type) {
                PhysicalObjectType.HOLLOW -> PhysicalObjectExportProperties.Hollow(
                    HollowProperties("ель", 450.0, 127, 32.0, null, null),
                )

                PhysicalObjectType.LOG_HIVE -> PhysicalObjectExportProperties.LogHive(
                    LogHiveProperties("сосна", 250.0, 90, 45.0, "сосна", 30.0, 120.0, null),
                )

                PhysicalObjectType.APIARY -> null
            },
            media = listOf(
                PhysicalObjectMedia(
                    mediaId, objectId, PhysicalObjectMediaType.IMAGE,
                    PhysicalObjectMediaFileStore.relativePath(objectId, mediaId),
                    "дупло.jpg", "image/jpeg", content.size.toLong(), sha(content),
                    Instant.parse("2026-09-20T10:01:00Z"),
                ),
            ),
            territory = TerritoryExportSnapshot(territoryId, "DEV", "Территория"),
            observer = ObserverExportSnapshot(observerId, "O1", "Иванов", "Иван", null),
        )
    }

    private fun PhysicalObjectExportGraph.withIdentity(id: UUID, mediaId: UUID, sequence: Int): PhysicalObjectExportGraph =
        copy(
            id = id,
            sequenceNumber = sequence,
            media = media.map { value ->
                value.copy(
                    id = mediaId,
                    physicalObjectId = id,
                    relativePath = PhysicalObjectMediaFileStore.relativePath(id, mediaId),
                )
            },
        )

    private fun sha(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue("expected ${T::class.simpleName}, got $thrown", thrown is T)
    }

    private fun withStore(block: (PhysicalObjectMediaFileStore) -> Unit) {
        val root = Files.createTempDirectory("object-export-service").toFile()
        try {
            block(
                PhysicalObjectMediaFileStore(
                    File(root, "files").apply { mkdirs() },
                    File(root, "cache").apply { mkdirs() },
                ),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
