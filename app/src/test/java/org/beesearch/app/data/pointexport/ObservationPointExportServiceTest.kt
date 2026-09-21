package org.beesearch.app.data.pointexport

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationPointExportServiceTest {
    @Test
    fun `service asks for only selected point and creates read-only package`() = withService { store ->
        val selected = detail(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val other = detail(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
        val calls = mutableListOf<UUID>()
        val available = mapOf(selected.point.id to selected, other.point.id to other)
        val service = ObservationPointExportService(
            source = ObservationPointExportSource { id -> calls += id; available[id] },
            attachmentStore = store,
        )
        val output = ByteArrayOutputStream()

        runBlocking { service.export(selected.point.id, output) }

        assertEquals(listOf(selected.point.id), calls)
        val decoded = ObservationPointExportCodec.decode(output.toByteArray().inputStream()).graph
        assertEquals(selected.point, decoded.point)
        assertTrue(decoded.point.id != other.point.id)
        assertEquals("Описание", selected.point.description)
    }

    @Test
    fun `missing point and destination write failure do not call any mutation path`() = withService { store ->
        var reads = 0
        val selected = detail(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        val service = ObservationPointExportService(
            source = ObservationPointExportSource { reads += 1; selected },
            attachmentStore = store,
        )
        var thrown: Throwable? = null
        try {
            runBlocking {
                service.export(selected.point.id, object : OutputStream() {
                    override fun write(value: Int) = throw IOException("destination failed")
                })
            }
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue(thrown != null)
        assertEquals(1, reads)
        assertEquals("Описание", selected.point.description)

        val missing = ObservationPointExportService(ObservationPointExportSource { null }, store)
        thrown = null
        try { runBlocking { missing.export(UUID.randomUUID(), ByteArrayOutputStream()) } } catch (error: Throwable) { thrown = error }
        assertTrue(thrown is ObservationPointExportSourceMissing)
    }

    private fun detail(pointId: UUID): ObservationPointDetail {
        val territoryId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val observerId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val timestamp = Instant.parse("2026-09-20T10:00:00Z")
        return ObservationPointDetail(
            point = ObservationPoint(pointId, territoryId, observerId, 2026, 16, null, null, 56.1, 43.2, null, null, null, timestamp, null, null, "Описание"),
            territory = Territory(territoryId, "DEV", "Территория", "Регион", "Район", timestamp, timestamp),
            observer = Observer(observerId, "O1", "Иванов", "Иван", null, null, timestamp, timestamp),
            beeHistories = emptyList(),
            weather = null,
            attachments = emptyList(),
        )
    }

    private fun withService(block: (ObservationAttachmentFileStore) -> Unit) {
        val root = Files.createTempDirectory("point-export-service").toFile()
        try {
            block(ObservationAttachmentFileStore(File(root, "files").apply { mkdirs() }, File(root, "cache").apply { mkdirs() }))
        } finally {
            root.deleteRecursively()
        }
    }
}
