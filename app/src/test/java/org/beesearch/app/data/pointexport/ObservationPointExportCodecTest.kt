package org.beesearch.app.data.pointexport

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.nio.file.Files
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.data.zip.ArchivePayload
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeObservationHistory
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.model.WeatherStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationPointExportCodecTest {
    @Test
    fun `point writer rejects attachment count beyond archive entry budget before output`() {
        val base = fixture().graph
        val attachments = (0 until ObservationPointExportContract.MAX_ENTRIES - 1).map { index ->
            val id = UUID.nameUUIDFromBytes("too-many-point-attachments-$index".toByteArray())
            base.attachments.single().copy(
                id = id,
                relativePath = ObservationAttachmentFileStore.relativePath(base.point.id, id),
            )
        }
        val output = ByteArrayOutputStream().apply { write(byteArrayOf(7, 8, 9)) }
        var thrown: Throwable? = null
        try {
            ObservationPointExportCodec.encode(base.copy(attachments = attachments), emptyMap(), output)
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue(thrown is InvalidObservationPointExport)
        assertEquals("too many ZIP entries", thrown?.message)
        assertArrayEquals(byteArrayOf(7, 8, 9), output.toByteArray())
    }

    @Test
    fun `downstream point archive accepts media beyond legacy entry and aggregate caps`() {
        val base = fixture().graph
        val root = Files.createTempDirectory("point-export-large-").toFile()
        try {
            val attachments = (0 until 4).map { index ->
                val id = UUID.nameUUIDFromBytes("large-point-$index".toByteArray())
                ObservationPointAttachment(
                    id, base.point.id, AttachmentType.PHOTO,
                    ObservationAttachmentFileStore.relativePath(base.point.id, id),
                    null, "image/jpeg", 17L * 1024 * 1024, "0".repeat(64),
                    Instant.parse("2026-09-20T10:2${index}:00Z"),
                )
            }
            val payloads = attachments.associate { attachment ->
                val file = File(root, attachment.id.toString())
                FileOutputStream(file).use { output ->
                    val chunk = ByteArray(8192) { (it * 17).toByte() }
                    repeat((attachment.byteSize / chunk.size).toInt()) { output.write(chunk) }
                }
                val payload = ArchivePayload.fromFile(file)
                attachment.copy(byteSize = payload.size, sha256 = payload.sha256) to payload
            }
            val graph = base.copy(attachments = payloads.keys.toList())
            val archive = File(root, "large-point.zip")
            archive.outputStream().use { output ->
                ObservationPointExportCodec.encode(graph, payloads.mapKeys { it.key.id }, output)
            }
            ObservationPointExportCodec.decode(archive.inputStream()).use { decoded ->
                assertEquals(4, decoded.attachmentPayloads.size)
                decoded.attachmentPayloads.values.forEach { assertEquals(17L * 1024 * 1024, it.size) }
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `file backed attachment streams across multiple buffers`() {
        val base = fixture()
        val content = ByteArray(3 * 8192 + 17) { index -> (index * 31).toByte() }
        val attachment = base.graph.attachments.single().copy(byteSize = content.size.toLong(), sha256 = sha(content))
        val graph = base.graph.copy(attachments = listOf(attachment))
        val archive = withPayloads(mapOf(attachment.id to content)) { payloads ->
            ByteArrayOutputStream().also { ObservationPointExportCodec.encode(graph, payloads, it) }.toByteArray()
        }
        ObservationPointExportCodec.decode(archive.inputStream()).use { decoded ->
            assertEquals(content.size.toLong(), decoded.attachmentPayloads.getValue(attachment.id).size)
            assertArrayEquals(content, decoded.attachmentPayloads.getValue(attachment.id).readMetadata(content.size.toLong()))
        }
    }

    @Test
    fun `round trip preserves one complete point graph and excludes anything else`() {
        val fixture = fixture()
        val archive = encode(fixture.graph, fixture.blobs)
        val decoded = ObservationPointExportCodec.decode(archive.inputStream())

        assertEquals(fixture.graph.point, decoded.graph.point)
        assertEquals(fixture.graph.territory, decoded.graph.territory)
        assertEquals(fixture.graph.observer, decoded.graph.observer)
        assertEquals(fixture.graph.weather, decoded.graph.weather)
        assertEquals(
            fixture.graph.beeHistories.sortedBy { it.bee.createdAt }.map { history ->
                history.copy(flightCycles = history.flightCycles.sortedBy { it.sequenceNumber })
            },
            decoded.graph.beeHistories,
        )
        assertEquals(fixture.graph.attachments, decoded.graph.attachments)
        fixture.blobs.forEach { (id, bytes) -> assertArrayEquals(bytes, decoded.attachmentPayloads.getValue(id).readMetadata(ObservationPointExportContract.MAX_METADATA_ENTRY_BYTES)) }
        decoded.close()
        assertFalse(archive.toString(Charsets.ISO_8859_1).contains(UUID.randomUUID().toString()))
    }

    @Test
    fun `manifest identifies format two selected point and binary entries`() {
        val fixture = fixture()
        val entries = entries(encode(fixture.graph, fixture.blobs))
        val manifest = entries.getValue("manifest.json").toString(Charsets.UTF_8)

        assertTrue(manifest.contains("\"profile\":\"SINGLE_OBSERVATION_POINT\""))
        assertTrue(manifest.contains("\"formatVersion\":2"))
        assertTrue(manifest.contains(fixture.graph.point.id.toString()))
        fixture.graph.attachments.forEach { assertTrue(manifest.contains("attachments/${it.id}")) }
        assertTrue(entries.keys.containsAll(listOf("manifest.json", "point.json")))
    }

    @Test
    fun `null description pending weather no photos and open cycle survive`() {
        val base = fixture().graph
        val open = base.beeHistories.first().flightCycles.first().copy(returnTime = null, azimuthDeg = null)
        val graph = base.copy(
            point = base.point.copy(description = null),
            weather = ObservationPointWeather(base.point.id, WeatherStatus.PENDING, null, null, null, null, null, null),
            beeHistories = listOf(base.beeHistories.first().copy(flightCycles = listOf(open))),
            attachments = emptyList(),
        )
        val decoded = decodeGraph(encode(graph, emptyMap()))

        assertNull(decoded.point.description)
        assertEquals(WeatherStatus.PENDING, decoded.weather?.status)
        assertNull(decoded.beeHistories.single().flightCycles.single().returnTime)
        assertNull(decoded.beeHistories.single().flightCycles.single().azimuthDeg)
        assertTrue(decoded.attachments.isEmpty())
    }

    @Test
    fun `canonical ordering is stable for bees cycles and multiple Cyrillic photos`() {
        val fixture = fixture(twoPhotos = true)
        val reversed = fixture.graph.copy(
            beeHistories = fixture.graph.beeHistories.reversed().map { it.copy(flightCycles = it.flightCycles.reversed()) },
            attachments = fixture.graph.attachments.reversed(),
        )
        val first = encode(fixture.graph, fixture.blobs)
        val second = encode(reversed, fixture.blobs)

        assertArrayEquals(first, second)
        val decoded = decodeGraph(first)
        assertEquals(listOf(1, 2), decoded.beeHistories.first().flightCycles.map { it.sequenceNumber })
        assertTrue(decoded.attachments.any { it.originalFileName == "пчёлы у дуба.jpg" })
        assertEquals(setOf(MarkPosition.THORAX, MarkPosition.ABDOMEN), decoded.beeHistories.map { it.bee.markPosition }.toSet())
    }

    @Test
    fun `wrong attachment SHA or size and missing blob are rejected`() {
        val fixture = fixture()
        val attachment = fixture.graph.attachments.single()
        assertInvalid {
            encode(fixture.graph.copy(attachments = listOf(attachment.copy(sha256 = "0".repeat(64)))), fixture.blobs)
        }
        assertInvalid {
            encode(fixture.graph.copy(attachments = listOf(attachment.copy(byteSize = attachment.byteSize + 1))), fixture.blobs)
        }
        assertInvalid { encode(fixture.graph, emptyMap()) }
    }

    @Test
    fun `foreign keys duplicate ids and duplicate cycle sequence are rejected`() {
        val fixture = fixture()
        val history = fixture.graph.beeHistories.first()
        assertInvalid { encode(fixture.graph.copy(point = fixture.graph.point.copy(territoryId = UUID.randomUUID())), fixture.blobs) }
        assertInvalid { encode(fixture.graph.copy(beeHistories = listOf(history.copy(bee = history.bee.copy(observationPointId = UUID.randomUUID())))), fixture.blobs) }
        assertInvalid {
            encode(fixture.graph.copy(beeHistories = listOf(history.copy(flightCycles = history.flightCycles + history.flightCycles.first()))), fixture.blobs)
        }
        assertInvalid {
            val wrong = history.flightCycles.first().copy(beeId = UUID.randomUUID())
            encode(fixture.graph.copy(beeHistories = listOf(history.copy(flightCycles = listOf(wrong)))), fixture.blobs)
        }
    }

    @Test
    fun `unsupported version malformed JSON and missing point are rejected`() {
        val archive = encode(fixture().graph, fixture().blobs)
        val original = entries(archive)
        assertInvalid { ObservationPointExportCodec.decode(writeEntries(original.mapValues { (name, bytes) ->
            if (name == "manifest.json") bytes.toString(Charsets.UTF_8).replace("\"formatVersion\":2", "\"formatVersion\":9").toByteArray() else bytes
        }).inputStream()) }
        assertInvalid { ObservationPointExportCodec.decode(writeEntries(original - "point.json").inputStream()) }
        val malformed = "{".toByteArray()
        val updatedManifest = updatePointDescriptor(original.getValue("manifest.json"), malformed)
        assertInvalid { ObservationPointExportCodec.decode(writeEntries(original + ("manifest.json" to updatedManifest) + ("point.json" to malformed)).inputStream()) }
    }

    @Test
    fun `unexpected and traversal ZIP entries are rejected`() {
        val original = entries(encode(fixture().graph, fixture().blobs))
        assertInvalid { ObservationPointExportCodec.decode(writeEntries(original + ("extra.txt" to byteArrayOf(1))).inputStream()) }
        assertInvalid { ObservationPointExportCodec.decode(writeEntries(linkedMapOf("../point.json" to byteArrayOf(1))).inputStream()) }
        assertInvalid { ObservationPointExportCodec.decode(writeEntries(linkedMapOf("/point.json" to byteArrayOf(1))).inputStream()) }
    }

    @Test
    fun `duplicate ZIP entry is rejected`() {
        val duplicate = rawStoredZip(listOf("manifest.json" to byteArrayOf(1), "manifest.json" to byteArrayOf(2)))
        assertInvalid { ObservationPointExportCodec.decode(duplicate.inputStream()) }
    }

    @Test
    fun `decoder accepts reordered entries and rejects point descriptor size or hash mismatch`() {
        val fixture = fixture()
        val original = entries(encode(fixture.graph, fixture.blobs))
        val reordered = writeEntries(original.entries.reversed().associate { it.toPair() })
        assertEquals(fixture.graph.point, decodeGraph(reordered).point)

        assertIntegrityFailure("point.json size mismatch") {
            ObservationPointExportCodec.decode(writeEntries(original.mapValues { (name, bytes) ->
                if (name == "manifest.json") bytes.toString(Charsets.UTF_8)
                    .replace("\"pointByteLength\":${original.getValue("point.json").size}", "\"pointByteLength\":${original.getValue("point.json").size + 1}")
                    .toByteArray() else bytes
            }).inputStream())
        }
        assertIntegrityFailure("point.json SHA-256 mismatch") {
            ObservationPointExportCodec.decode(writeEntries(original.mapValues { (name, bytes) ->
                if (name == "manifest.json") bytes.toString(Charsets.UTF_8)
                    .replace("\"pointSha256\":\"${sha(original.getValue("point.json"))}\"", "\"pointSha256\":\"${"0".repeat(64)}\"")
                    .toByteArray() else bytes
            }).inputStream())
        }
    }

    @Test
    fun `filename is deterministic readable collision resistant and sanitized`() {
        val fixture = fixture().graph
        val detail = org.beesearch.app.domain.model.ObservationPointDetail(
            fixture.point, fixture.territory.copy(code = "Лес/Юг:*?"), fixture.observer,
            fixture.beeHistories, fixture.weather, fixture.attachments,
        )
        val name = observationPointExportFileName(detail)
        assertEquals(name, observationPointExportFileName(detail))
        assertTrue(name.startsWith("Лес-Юг--point-16--2026-09-20--"))
        assertTrue(name.endsWith("${fixture.point.id.toString().replace("-", "").take(8)}.zip"))
        assertFalse(name.contains('/')); assertFalse(name.contains(':')); assertFalse(name.contains('*'))
    }

    @Test
    fun `V2 corrected date survives a different reader timezone`() {
        val fixture = fixture()
        val point = fixture.graph.point.copy(
            createdAt = Instant.parse("2026-10-01T00:30:00Z"), observationDate = LocalDate.of(2026, 9, 28),
            beePresenceResult = BeePresenceResult.NO_BEES_FOUND, initialGroupReleaseAt = null,
            completedAt = Instant.parse("2026-10-01T01:30:00Z"),
        )
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val archive = encode(fixture.graph.copy(point = point, beeHistories = emptyList(), attachments = emptyList(), weather = null), emptyMap())
            val pointJson = Json.parseToJsonElement(entries(archive).getValue("point.json").toString(Charsets.UTF_8)).jsonObject.getValue("point").jsonObject
            assertEquals(JsonPrimitive("2026-09-28"), pointJson.getValue("observationDate"))
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(point, decodeGraph(archive).point)
        } finally { TimeZone.setDefault(originalZone) }
    }

    @Test
    fun `V2 requires string real canonical date and rejects every invalid spelling and type`() {
        val fixture = fixture()
        val original = entries(encode(fixture.graph, fixture.blobs))
        val invalidValues = listOf<JsonElement?>(null, JsonNull, JsonPrimitive(1), JsonPrimitive(true), JsonObject(emptyMap()), JsonArray(emptyList())) +
            listOf("2026-2-03", "2026-02-3", "2026-13-01", "2026-02-30", "2026-09-20T00:00:00Z", "2026-09-20+03:00").map(::JsonPrimitive)
        invalidValues.forEach { value ->
            val root = Json.parseToJsonElement(original.getValue("point.json").toString(Charsets.UTF_8)).jsonObject
            val fields = root.getValue("point").jsonObject.toMutableMap().apply {
                if (value == null) remove("observationDate") else put("observationDate", value)
            }
            val bytes = JsonObject(root + ("point" to JsonObject(fields))).toString().toByteArray()
            val updated = original + ("point.json" to bytes) + ("manifest.json" to updatePointDescriptor(original.getValue("manifest.json"), bytes))
            var thrown: Throwable? = null
            try { ObservationPointExportCodec.decode(writeEntries(updated).inputStream()) } catch (e: Throwable) { thrown = e }
            assertTrue("value=$value error=$thrown", thrown is InvalidObservationPointExport)
        }
    }

    @Test
    fun `legacy V1 date reconstruction and historical unknown key policy remain readable`() {
        val fixture = fixture()
        val originalZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val archive = encode(fixture.graph, fixture.blobs, formatVersion = 1)
            val original = entries(archive)
            val root = Json.parseToJsonElement(original.getValue("point.json").toString(Charsets.UTF_8)).jsonObject
            val fields = root.getValue("point").jsonObject
            assertFalse(fields.containsKey("observationDate"))
            assertTrue(original.getValue("manifest.json").toString(Charsets.UTF_8).contains("\"formatVersion\":1"))
            val bytes = JsonObject(root + ("point" to JsonObject(fields + mapOf("unknownLegacyField" to JsonPrimitive("accepted"), "observationDate" to JsonPrimitive("2025-01-01"))))).toString().toByteArray()
            val updated = original + ("point.json" to bytes) + ("manifest.json" to updatePointDescriptor(original.getValue("manifest.json"), bytes))
            assertEquals(fixture.graph.point, decodeGraph(writeEntries(updated)).point)
        } finally { TimeZone.setDefault(originalZone) }
    }

    @Test
    fun `legacy V1 refuses corrected date before output while V2 accepts it`() {
        val fixture = fixture()
        val graph = fixture.graph.copy(point = fixture.graph.point.copy(observationDate = LocalDate.of(2026, 9, 18)))
        val output = ByteArrayOutputStream().apply { write(byteArrayOf(7, 8, 9)) }
        var thrown: Throwable? = null
        try { withPayloads(fixture.blobs) { payloads -> ObservationPointExportCodec.encode(graph, payloads, output, formatVersion = 1) } } catch (e: Throwable) { thrown = e }
        assertTrue(thrown is LegacyObservationPointExportNotRepresentable)
        assertArrayEquals(byteArrayOf(7, 8, 9), output.toByteArray())
        assertEquals(graph.point, decodeGraph(encode(graph, fixture.blobs)).point)
    }

    @Test
    fun `V2 rejects a LocalDate outside canonical four digit wire spelling before writing`() {
        val fixture = fixture()
        val graph = fixture.graph.copy(point = fixture.graph.point.copy(observationDate = LocalDate.of(10000, 1, 1)))
        val output = ByteArrayOutputStream().apply { write(byteArrayOf(7, 8, 9)) }
        assertInvalid { withPayloads(fixture.blobs) { payloads -> ObservationPointExportCodec.encode(graph, payloads, output) } }
        assertArrayEquals(byteArrayOf(7, 8, 9), output.toByteArray())
    }

    @Test
    fun `version dispatch rejects future version three for reader and writer`() {
        val fixture = fixture()
        val original = entries(encode(fixture.graph, fixture.blobs))
        val manifest = Json.parseToJsonElement(original.getValue("manifest.json").toString(Charsets.UTF_8)).jsonObject
        val updated = original + ("manifest.json" to JsonObject(manifest + ("formatVersion" to JsonPrimitive(3))).toString().toByteArray())
        assertInvalid { ObservationPointExportCodec.decode(writeEntries(updated).inputStream()) }
        assertInvalid { withPayloads(fixture.blobs) { payloads -> ObservationPointExportCodec.encode(fixture.graph, payloads, ByteArrayOutputStream(), formatVersion = 3) } }
    }

    private fun fixture(twoPhotos: Boolean = false): Fixture {
        val pointId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val territoryId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val observerId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val firstBee = bee(pointId, "44444444-4444-4444-4444-444444444444", MarkPosition.THORAX, "WHITE", "2026-09-20T10:01:00Z")
        val secondBee = bee(pointId, "55555555-5555-5555-5555-555555555555", MarkPosition.ABDOMEN, "RED", "2026-09-20T10:02:00Z")
        val firstCycles = listOf(cycle(firstBee.id, "66666666-6666-6666-6666-666666666666", 1, 247.0), cycle(firstBee.id, "77777777-7777-7777-7777-777777777777", 2, null))
        val secondCycles = listOf(cycle(secondBee.id, "88888888-8888-8888-8888-888888888888", 1, null))
        val photoIds = listOf("99999999-9999-9999-9999-999999999999") + if (twoPhotos) listOf("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa") else emptyList()
        val blobs = photoIds.mapIndexed { index, raw -> UUID.fromString(raw) to "photo-$index-content".toByteArray() }.toMap()
        val attachments = blobs.entries.mapIndexed { index, (id, bytes) ->
            ObservationPointAttachment(
                id, pointId, AttachmentType.PHOTO, ObservationAttachmentFileStore.relativePath(pointId, id),
                if (index == 0) "пчёлы у дуба.jpg" else null, "image/jpeg", bytes.size.toLong(), sha(bytes), Instant.parse("2026-09-20T10:0${index + 3}:00Z"),
            )
        }
        return Fixture(
            ObservationPointExportGraph(
                point = ObservationPoint(java.time.LocalDate.of(2026, 9, 20), pointId, territoryId, observerId, 2026, 16, BeePresenceResult.BEES_FOUND, "P-16", 56.1, 43.2, 56.1001, 43.2001, 3.5, Instant.parse("2026-09-20T10:00:00Z"), Instant.parse("2026-09-20T10:01:00Z"), Instant.parse("2026-09-20T11:00:00Z"), "Описание точки"),
                territory = Territory(territoryId, "DEV-BENCH2", "Тестовая территория", "Нижегородская область", "Бор", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")),
                observer = Observer(observerId, "O1", "Иванов", "Иван", "Иванович", null, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")),
                weather = ObservationPointWeather(pointId, WeatherStatus.LOADED, 18.4, 2.1, 247.0, Instant.parse("2026-09-20T10:00:00Z"), Instant.parse("2026-09-20T10:05:00Z"), "Open-Meteo"),
                beeHistories = listOf(BeeObservationHistory(secondBee, secondCycles), BeeObservationHistory(firstBee, firstCycles)),
                attachments = attachments,
            ),
            blobs,
        )
    }

    private fun bee(pointId: UUID, id: String, position: MarkPosition, color: String, created: String) =
        Bee(UUID.fromString(id), pointId, color, position, Instant.parse(created))

    private fun cycle(beeId: UUID, id: String, sequence: Int, azimuth: Double?) = FlightCycle(
        UUID.fromString(id), beeId, sequence, Instant.parse("2026-09-20T10:10:00Z"),
        Instant.parse("2026-09-20T10:15:12Z"), azimuth, azimuth != null, sequence == 1, false,
        Instant.parse("2026-09-20T10:10:00Z"), Instant.parse("2026-09-20T10:15:12Z"),
    )

    private fun encode(graph: ObservationPointExportGraph, blobs: Map<UUID, ByteArray>, formatVersion: Int = ObservationPointExportContract.FORMAT_VERSION): ByteArray =
        withPayloads(blobs) { payloads -> ByteArrayOutputStream().also { ObservationPointExportCodec.encode(graph, payloads, it, formatVersion) }.toByteArray() }

    private fun decodeGraph(archive: ByteArray): ObservationPointExportGraph =
        ObservationPointExportCodec.decode(archive.inputStream()).use { it.graph }

    private fun <T> withPayloads(blobs: Map<UUID, ByteArray>, block: (Map<UUID, org.beesearch.app.data.zip.ArchivePayload>) -> T): T {
        val directory = Files.createTempDirectory("point-export-test-").toFile()
        return try {
            block(blobs.mapValues { (id, bytes) ->
                java.io.File(directory, id.toString()).also { it.writeBytes(bytes) }
                    .let { org.beesearch.app.data.zip.ArchivePayload.fromFile(it) }
            })
        } finally { directory.deleteRecursively() }
    }

    private fun entries(archive: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
            }
        }
        return result
    }

    private fun writeEntries(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()

    private fun updatePointDescriptor(manifest: ByteArray, point: ByteArray): ByteArray {
        val text = manifest.toString(Charsets.UTF_8)
            .replace(Regex("\"pointByteLength\":\\d+"), "\"pointByteLength\":${point.size}")
            .replace(Regex("\"pointSha256\":\"[0-9a-f]{64}\""), "\"pointSha256\":\"${sha(point)}\"")
        return text.toByteArray()
    }

    /** Minimal STORED local records are enough for ZipInputStream and allow duplicate names. */
    private fun rawStoredZip(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { output ->
        entries.forEach { (name, bytes) ->
            val nameBytes = name.toByteArray()
            val crc = java.util.zip.CRC32().apply { update(bytes) }.value
            fun short(value: Int) { output.write(value and 0xff); output.write((value ushr 8) and 0xff) }
            fun int(value: Long) { short((value and 0xffff).toInt()); short(((value ushr 16) and 0xffff).toInt()) }
            int(0x04034b50); short(20); short(0); short(0); short(0); short(0); int(crc); int(bytes.size.toLong()); int(bytes.size.toLong()); short(nameBytes.size); short(0)
            output.write(nameBytes); output.write(bytes)
        }
    }.toByteArray()

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun assertInvalid(block: () -> Unit) {
        var thrown: Throwable? = null
        try { block() } catch (error: Throwable) { thrown = error }
        assertTrue("Expected ObservationPointExportException, got $thrown", thrown is ObservationPointExportException)
    }

    private fun assertIntegrityFailure(message: String, block: () -> Unit) {
        var thrown: Throwable? = null
        try { block() } catch (error: Throwable) { thrown = error }
        assertTrue("Expected ObservationPointExportIntegrityError, got $thrown", thrown is ObservationPointExportIntegrityError)
        assertEquals(message, thrown?.message)
    }

    private data class Fixture(val graph: ObservationPointExportGraph, val blobs: Map<UUID, ByteArray>)
}
