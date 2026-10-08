package org.beesearch.app.data.objectexport

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.data.zip.ArchivePayload
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class PhysicalObjectCollectionExportCodecTest {
    private val decodedArchives = mutableListOf<java.io.Closeable>()

    @After
    fun closeDecodedArchives() {
        decodedArchives.asReversed().forEach { it.close() }
        decodedArchives.clear()
    }
    @Test
    fun `v2 collection fixation dates are unchanged across timezone changes`() {
        val graph = collection(PhysicalObjectType.LOG_HIVE, listOf(
            objectGraph(PhysicalObjectType.LOG_HIVE, 1).copy(fixationDate = LocalDate.of(2026, 12, 31)),
        ))
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
            val archive = encode(graph, blobs(graph))
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            assertEquals(LocalDate.of(2026, 12, 31), decode(archive).graph.objects.single().fixationDate)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `v2 collection writer refuses noncanonical LocalDate spelling before touching output`() {
        val graph = collection(
            PhysicalObjectType.HOLLOW,
            listOf(objectGraph(PhysicalObjectType.HOLLOW, 1).copy(fixationDate = LocalDate.of(10000, 1, 1))),
        )
        val output = ByteArrayOutputStream().apply { write("existing".toByteArray()) }
        assertLegacyOrInvalid { PhysicalObjectCollectionExportCodec.encode(graph, payloads(blobs(graph)), output) }
        assertEquals("existing", output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `v2 carries nullable and non-null dates for three objects of each same type`() {
        listOf(PhysicalObjectType.HOLLOW, PhysicalObjectType.LOG_HIVE).forEach { type ->
            val graph = collection(type, listOf(
                objectGraph(type, 1),
                objectGraph(type, 2).copy(fixationDate = LocalDate.of(2026, 10, 4)),
                objectGraph(type, 3).copy(fixationDate = LocalDate.of(2026, 10, 5), observer = objectGraph(type, 1).observer),
            ))
            val archive = encode(graph, blobs(graph))
            val entries = zip(archive)
            val manifest = Json.parseToJsonElement(entries.getValue("manifest.json").decodeToString()).jsonObject
            assertEquals("\"PHYSICAL_OBJECT_COLLECTION\"", manifest.getValue("profile").toString())
            assertEquals("2", manifest.getValue("formatVersion").toString())
            graph.objects.forEach { value ->
                val root = Json.parseToJsonElement(entries.getValue(PhysicalObjectCollectionExportContract.objectEntry(value.id)).decodeToString()).jsonObject
                assertEquals(setOf("object", "properties", "media"), root.keys)
                val identity = root.getValue("object").jsonObject
                assertEquals(setOf("id", "territoryId", "type", "sequenceNumber", "latitude", "longitude", "createdAt", "creatorObserverId", "name", "fixationDate"), identity.keys)
                val expectedDate = value.fixationDate?.let { "\"$it\"" } ?: "null"
                assertEquals(expectedDate, identity.getValue("fixationDate").toString())
            }
        val decoded = decode(archive).graph.objects.sortedBy { it.sequenceNumber }
            assertNull(decoded[0].fixationDate)
            assertEquals(LocalDate.of(2026, 10, 4), decoded[1].fixationDate)
            assertEquals(LocalDate.of(2026, 10, 5), decoded[2].fixationDate)
        }
    }

    @Test
    fun `v2 collection rejects missing and wrong fixation date on one record`() {
        val first = objectGraph(PhysicalObjectType.HOLLOW, 1).copy(fixationDate = LocalDate.of(2026, 10, 4))
        val second = objectGraph(PhysicalObjectType.HOLLOW, 2).copy(fixationDate = LocalDate.of(2026, 10, 5))
        val graph = collection(PhysicalObjectType.HOLLOW, listOf(first, second))
        val good = encode(graph, blobs(graph))
        assertInvalid(replaceCollectionObject(good, first.id) { it.replace(",\"fixationDate\":\"2026-10-04\"", "") })
        assertInvalid(replaceCollectionObject(good, second.id) { it.replace("\"fixationDate\":\"2026-10-05\"", "\"fixationDate\":true") })
        listOf("2026-02-30", "2026-01-01T00:00:00Z").forEach { invalidDate ->
            assertInvalid(replaceCollectionObject(good, second.id) { it.replace("\"fixationDate\":\"2026-10-05\"", "\"fixationDate\":\"$invalidDate\"") })
        }
    }

    @Test
    fun `legacy collection export accepts null fixation dates and refuses any non-null date before writing`() {
        val graph = collection(PhysicalObjectType.HOLLOW, listOf(objectGraph(PhysicalObjectType.HOLLOW, 1)))
        val blobs = blobs(graph)
        val output = ByteArrayOutputStream()
        encodeV1(graph, blobs, output)
        assertTrue(output.size() > 0)
        val legacy = output.toByteArray()
        val manifest = Json.parseToJsonElement(zip(legacy).getValue("manifest.json").decodeToString()).jsonObject
        assertEquals("1", manifest.getValue("formatVersion").toString())
        assertInvalid(replaceCollectionObject(legacy, graph.objects.single().id) {
            it.replace("\"name\":null", "\"name\":null,\"fixationDate\":null")
        })
        assertNull(
            decode(output.toByteArray()).graph.objects.single().fixationDate,
        )

        val divergent = collection(
            PhysicalObjectType.HOLLOW,
            listOf(graph.objects.single().copy(fixationDate = LocalDate.parse("2026-10-03"))),
        )
        val untouched = ByteArrayOutputStream().apply { write("existing".toByteArray()) }
        assertLegacyNotRepresentable(divergent, blobs(divergent), untouched)
        assertArrayEquals("existing".toByteArray(), untouched.toByteArray())
    }
    @Test
    fun `multiple hollows and log hives round trip`() {
        listOf(PhysicalObjectType.HOLLOW, PhysicalObjectType.LOG_HIVE).forEach { type ->
            val graph = collection(type, listOf(objectGraph(type, 2), objectGraph(type, 1)))
            val blobs = blobs(graph)
            val decoded = decode(encode(graph, blobs))

            assertEquals(listOf(1, 2), decoded.graph.objects.map { it.sequenceNumber })
            assertEquals(type, decoded.graph.type)
            assertEquals(blobs.keys, decoded.mediaBytes.keys)
            blobs.forEach { (id, bytes) -> assertArrayEquals(bytes, decoded.mediaBytes.getValue(id).open().use { it.readBytes() }) }
        }
    }

    @Test
    fun `one object null creator and null properties are valid`() {
        val value = objectGraph(PhysicalObjectType.HOLLOW, 1, withMedia = false)
            .copy(creatorObserverId = null, observer = null, properties = null)
        val decoded = decode(encode(collection(PhysicalObjectType.HOLLOW, listOf(value)), emptyMap()))
        assertEquals(value, decoded.graph.objects.single())
    }

    @Test
    fun `bytes and canonical object media order are deterministic`() {
        val first = objectGraph(PhysicalObjectType.HOLLOW, 2)
        val second = objectGraph(PhysicalObjectType.HOLLOW, 1)
        val graph = collection(PhysicalObjectType.HOLLOW, listOf(first, second))
        val blobs = blobs(graph)
        val a = encode(graph, blobs)
        val b = encode(graph.copy(objects = graph.objects.reversed()), blobs)

        assertArrayEquals(a, b)
        val names = zip(a).keys.toList()
        assertTrue(names.indexOf(PhysicalObjectCollectionExportContract.objectEntry(second.id)) <
            names.indexOf(PhysicalObjectCollectionExportContract.objectEntry(first.id)))
    }

    @Test
    fun `territory appears once and shared observer is deduplicated`() {
        val shared = ObserverExportSnapshot(OBSERVER, "O1", "Иванов", "Иван", null)
        val graph = collection(
            PhysicalObjectType.HOLLOW,
            listOf(objectGraph(PhysicalObjectType.HOLLOW, 1).copy(observer = shared),
                objectGraph(PhysicalObjectType.HOLLOW, 2).copy(
                    creatorObserverId = OBSERVER,
                    observer = shared,
                )),
        )
        val entries = zip(encode(graph, blobs(graph)))

        assertEquals(1, entries.keys.count { it == PhysicalObjectCollectionExportContract.TERRITORY_ENTRY })
        assertEquals(
            1,
            Json.parseToJsonElement(entries.getValue(PhysicalObjectCollectionExportContract.OBSERVERS_ENTRY).decodeToString())
                .jsonArray.size,
        )
    }

    @Test
    fun `wrong profile version missing unexpected and unsafe entries are rejected`() {
        val graph = collection(PhysicalObjectType.HOLLOW, listOf(objectGraph(PhysicalObjectType.HOLLOW, 1)))
        val good = encode(graph, blobs(graph))
        val manifest = PhysicalObjectCollectionExportContract.MANIFEST_ENTRY
        assertInvalid(mutate(good) { it[manifest] = it.getValue(manifest).decodeToString()
            .replace(PhysicalObjectCollectionExportContract.PROFILE, "WRONG").encodeToByteArray() })
        assertInvalid(mutate(good) { it[manifest] = it.getValue(manifest).decodeToString()
            .replace("\"formatVersion\":2", "\"formatVersion\":3").encodeToByteArray() })
        assertInvalid(mutate(good) { it.remove(PhysicalObjectCollectionExportContract.objectEntry(graph.objects.single().id)) })
        assertInvalid(mutate(good) { it["extra.json"] = "{}".encodeToByteArray() })
        assertInvalid(mutate(good) { it["../unsafe"] = byteArrayOf(1) })
    }

    @Test
    fun `mixed type territory duplicate object observer and media are rejected`() {
        val hollow = objectGraph(PhysicalObjectType.HOLLOW, 1)
        assertEncodeInvalid(collection(PhysicalObjectType.HOLLOW, listOf(hollow, objectGraph(PhysicalObjectType.LOG_HIVE, 2))))
        assertEncodeInvalid(collection(PhysicalObjectType.HOLLOW, listOf(hollow.copy(territoryId = UUID.randomUUID()))))
        assertEncodeInvalid(collection(PhysicalObjectType.HOLLOW, listOf(hollow, hollow)))
        assertEncodeInvalid(collection(PhysicalObjectType.HOLLOW, listOf(
            hollow,
            objectGraph(PhysicalObjectType.HOLLOW, 2).copy(
                creatorObserverId = hollow.creatorObserverId,
                observer = hollow.observer?.copy(code = "OTHER"),
            ),
        )))
        val duplicateMedia = objectGraph(PhysicalObjectType.HOLLOW, 2).let { other ->
            other.copy(media = other.media.map { it.copy(id = hollow.media.single().id) })
        }
        assertEncodeInvalid(collection(PhysicalObjectType.HOLLOW, listOf(hollow, duplicateMedia)))
    }

    @Test
    fun `missing changed-size and changed-hash media reject the entire package`() {
        val graph = collection(PhysicalObjectType.HOLLOW, listOf(objectGraph(PhysicalObjectType.HOLLOW, 1)))
        val good = encode(graph, blobs(graph))
        val media = graph.objects.single().media.single()
        val entry = PhysicalObjectCollectionExportContract.mediaEntry(graph.objects.single().id, media.id)
        assertInvalid(mutate(good) { it.remove(entry) })
        assertInvalid(mutate(good) { it[entry] = it.getValue(entry) + 1 })
        assertInvalid(mutate(good) { bytes ->
            val original = bytes.getValue(entry)
            bytes[entry] = original.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        })
    }

    @Test
    fun `object and entry caps are enforced`() {
        val objects = (1..PhysicalObjectCollectionExportContract.MAX_OBJECTS + 1).map { sequence ->
            objectGraph(PhysicalObjectType.HOLLOW, sequence, withMedia = false)
        }
        assertEncodeInvalid(collection(PhysicalObjectType.HOLLOW, objects))
    }

    @Test
    fun `decoder accepts reordered entries and rejects object descriptor size or hash mismatch`() {
        val graph = collection(PhysicalObjectType.HOLLOW, listOf(objectGraph(PhysicalObjectType.HOLLOW, 1)))
        val good = encode(graph, blobs(graph))
        val original = zip(good)
        val reordered = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                original.entries.reversed().forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        assertEquals(graph.objects.single().id, decode(reordered).graph.objects.single().id)

        val objectEntry = PhysicalObjectCollectionExportContract.objectEntry(graph.objects.single().id)
        val objectBytes = original.getValue(objectEntry)
        assertIntegrityFailure("objects/${graph.objects.single().id}.json size mismatch", mutate(good) { bytes ->
            bytes[PhysicalObjectCollectionExportContract.MANIFEST_ENTRY] = bytes.getValue(PhysicalObjectCollectionExportContract.MANIFEST_ENTRY)
                .decodeToString().replace("\"byteLength\":${objectBytes.size}", "\"byteLength\":${objectBytes.size + 1}").encodeToByteArray()
        })
        assertIntegrityFailure("objects/${graph.objects.single().id}.json SHA-256 mismatch", mutate(good) { bytes ->
            bytes[PhysicalObjectCollectionExportContract.MANIFEST_ENTRY] = bytes.getValue(PhysicalObjectCollectionExportContract.MANIFEST_ENTRY)
                .decodeToString().replace("\"sha256\":\"${sha(objectBytes)}\"", "\"sha256\":\"${"0".repeat(64)}\"").encodeToByteArray()
        })
    }

    private fun collection(type: PhysicalObjectType, objects: List<PhysicalObjectExportGraph>) =
        PhysicalObjectCollectionExportGraph(TERRITORY, type, objects)

    private fun objectGraph(
        type: PhysicalObjectType,
        sequence: Int,
        withMedia: Boolean = true,
    ): PhysicalObjectExportGraph {
        val id = uuid(sequence)
        val observer = if (sequence % 2 == 0) OBSERVER_2 else OBSERVER
        val mediaId = uuid(1_000 + sequence)
        val bytes = mediaBytes(sequence)
        return PhysicalObjectExportGraph(
            id = id,
            type = type,
            territoryId = TERRITORY.id,
            sequenceNumber = sequence,
            latitude = 56.0 + sequence / 100.0,
            longitude = 42.0,
            createdAt = Instant.parse("2026-10-02T10:00:00Z").plusSeconds(sequence.toLong()),
            creatorObserverId = observer,
            name = null,
            properties = when (type) {
                PhysicalObjectType.HOLLOW -> PhysicalObjectExportProperties.Hollow(
                    HollowProperties("дуб", 200.0, 90, 40.0, null, null),
                )
                PhysicalObjectType.LOG_HIVE -> PhysicalObjectExportProperties.LogHive(
                    LogHiveProperties("сосна", 100.0, 180, 50.0, "сосна", 30.0, 120.0, null),
                )
                PhysicalObjectType.APIARY -> null
            },
            media = if (!withMedia) emptyList() else listOf(
                PhysicalObjectMedia(
                    mediaId, id, PhysicalObjectMediaType.IMAGE,
                    PhysicalObjectMediaFileStore.relativePath(id, mediaId),
                    "$sequence.jpg", "image/jpeg", bytes.size.toLong(), sha(bytes),
                    Instant.parse("2026-10-02T10:05:00Z").plusSeconds(sequence.toLong()),
                ),
            ),
            territory = TERRITORY,
            observer = ObserverExportSnapshot(observer, "O$sequence", "Иванов", "Иван", null),
        )
    }

    private fun blobs(graph: PhysicalObjectCollectionExportGraph): Map<UUID, ByteArray> =
        graph.objects.flatMap { it.media }.associate { media ->
            media.id to mediaBytes(graph.objects.first { it.id == media.physicalObjectId }.sequenceNumber)
        }

    private fun encode(graph: PhysicalObjectCollectionExportGraph, blobs: Map<UUID, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { PhysicalObjectCollectionExportCodec.encode(graph, payloads(blobs), it) }.toByteArray()

    private fun encodeV1(graph: PhysicalObjectCollectionExportGraph, blobs: Map<UUID, ByteArray>, output: ByteArrayOutputStream) =
        PhysicalObjectCollectionExportCodec.encodeV1(graph, payloads(blobs), output)

    private fun payloads(blobs: Map<UUID, ByteArray>) = blobs.mapValues { (_, bytes) ->
        File.createTempFile("bee-export-test-", ".media").also {
            it.writeBytes(bytes)
            it.deleteOnExit()
        }.let(ArchivePayload::fromFile)
    }

    private fun decode(bytes: ByteArray) = PhysicalObjectCollectionExportCodec.decode(bytes.inputStream()).also(decodedArchives::add)

    private fun zip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
            }
        }
        return result
    }

    private fun mutate(bytes: ByteArray, change: (LinkedHashMap<String, ByteArray>) -> Unit): ByteArray {
        val entries = zip(bytes)
        change(entries)
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
    }

    private fun replaceCollectionObject(
        bytes: ByteArray,
        objectId: UUID,
        transform: (String) -> String,
    ): ByteArray {
        val entries = zip(bytes)
        val entry = PhysicalObjectCollectionExportContract.objectEntry(objectId)
        val original = entries.getValue(entry)
        val changed = transform(original.decodeToString()).encodeToByteArray()
        val manifest = entries.getValue(PhysicalObjectCollectionExportContract.MANIFEST_ENTRY).decodeToString()
            .replace(
                "\"entry\":\"$entry\",\"byteLength\":${original.size},\"sha256\":\"${sha(original)}\"",
                "\"entry\":\"$entry\",\"byteLength\":${changed.size},\"sha256\":\"${sha(changed)}\"",
            )
        entries[entry] = changed
        entries[PhysicalObjectCollectionExportContract.MANIFEST_ENTRY] = manifest.encodeToByteArray()
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                    zip.write(content)
                    zip.closeEntry()
                }
            }
        }.toByteArray()
    }

    private fun assertEncodeInvalid(graph: PhysicalObjectCollectionExportGraph) {
        var thrown: Throwable? = null
        try { encode(graph, blobs(graph)) } catch (error: Throwable) { thrown = error }
        assertTrue("expected collection validation failure, got $thrown", thrown is PhysicalObjectExportException)
    }

    private fun assertLegacyOrInvalid(block: () -> Unit) {
        var thrown: Throwable? = null
        try { block() } catch (error: Throwable) { thrown = error }
        assertTrue("expected invalid export, got $thrown", thrown is InvalidPhysicalObjectExport)
    }

    private fun assertEncodeInvalid(
        graph: PhysicalObjectCollectionExportGraph,
        mediaBytes: Map<UUID, ByteArray>,
        output: ByteArrayOutputStream,
    ) {
        var thrown: Throwable? = null
        try { PhysicalObjectCollectionExportCodec.encode(graph, payloads(mediaBytes), output) } catch (error: Throwable) { thrown = error }
        assertTrue("expected collection validation failure, got $thrown", thrown is PhysicalObjectExportException)
    }

    private fun assertLegacyNotRepresentable(
        graph: PhysicalObjectCollectionExportGraph,
        mediaBytes: Map<UUID, ByteArray>,
        output: ByteArrayOutputStream,
    ) {
        var thrown: Throwable? = null
        try { PhysicalObjectCollectionExportCodec.encodeV1(graph, payloads(mediaBytes), output) } catch (error: Throwable) { thrown = error }
        assertTrue("expected LegacyPhysicalObjectExportNotRepresentable, got $thrown", thrown is LegacyPhysicalObjectExportNotRepresentable)
    }

    private fun assertInvalid(bytes: ByteArray) {
        var thrown: Throwable? = null
        try { decode(bytes) } catch (error: Throwable) { thrown = error }
        assertTrue("expected invalid package, got $thrown", thrown is PhysicalObjectExportException)
    }

    private fun assertIntegrityFailure(message: String, bytes: ByteArray) {
        var thrown: Throwable? = null
        try { decode(bytes); throw AssertionError("expected integrity failure") } catch (error: Throwable) { thrown = error }
        assertTrue("expected PhysicalObjectExportIntegrityError, got $thrown", thrown is PhysicalObjectExportIntegrityError)
        assertEquals(message, thrown?.message)
    }

    private fun mediaBytes(sequence: Int) = "media-$sequence".encodeToByteArray()
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun uuid(value: Int) = UUID.fromString("00000000-0000-0000-0000-${value.toString().padStart(12, '0')}")

    private companion object {
        val TERRITORY = TerritoryExportSnapshot(
            UUID.fromString("22222222-2222-2222-2222-222222222222"), "DEV", "Территория",
        )
        val OBSERVER: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val OBSERVER_2: UUID = UUID.fromString("55555555-5555-5555-5555-555555555555")
    }
}
