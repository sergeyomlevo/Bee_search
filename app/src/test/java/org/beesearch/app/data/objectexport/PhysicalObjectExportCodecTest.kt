package org.beesearch.app.data.objectexport

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.beesearch.app.data.media.PhysicalObjectMediaFileStore
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `SINGLE_PHYSICAL_OBJECT` v1 container itself: what it carries, what it refuses and how strict
 * the reader is about a file that claims this profile.
 */
class PhysicalObjectExportCodecTest {
    @Test
    fun `hollow round trip preserves the object its properties its media and its context`() {
        val fixture = fixture(PhysicalObjectType.HOLLOW)
        val archive = encode(fixture.graph, fixture.blobs)
        val decoded = PhysicalObjectExportCodec.decode(archive.inputStream())

        assertEquals(fixture.graph, decoded.graph)
        fixture.blobs.forEach { (id, bytes) -> assertArrayEquals(bytes, decoded.mediaBytes.getValue(id)) }
    }

    @Test
    fun `log hive round trip preserves the object its properties its media and its context`() {
        val fixture = fixture(PhysicalObjectType.LOG_HIVE)
        val archive = encode(fixture.graph, fixture.blobs)
        val decoded = PhysicalObjectExportCodec.decode(archive.inputStream())

        assertEquals(fixture.graph, decoded.graph)
        fixture.blobs.forEach { (id, bytes) -> assertArrayEquals(bytes, decoded.mediaBytes.getValue(id)) }
    }

    @Test
    fun `foundation object without subtype properties stays valid and keeps its nulls`() {
        val base = fixture(PhysicalObjectType.HOLLOW).graph
        val graph = base.copy(
            properties = null,
            name = null,
            creatorObserverId = null,
            observer = null,
            media = emptyList(),
        )

        val decoded = PhysicalObjectExportCodec.decode(encode(graph, emptyMap()).inputStream()).graph

        assertNull(decoded.properties)
        assertNull(decoded.name)
        assertNull(decoded.creatorObserverId)
        assertNull(decoded.observer)
        assertTrue(decoded.media.isEmpty())
        assertEquals(graph.id, decoded.id)
    }

    @Test
    fun `manifest declares the profile version object identity and every binary entry`() {
        val fixture = fixture(PhysicalObjectType.HOLLOW)
        val entries = entries(encode(fixture.graph, fixture.blobs))
        val manifest = entries.getValue("manifest.json").toString(Charsets.UTF_8)

        assertTrue(manifest.contains("\"profile\":\"SINGLE_PHYSICAL_OBJECT\""))
        assertTrue(manifest.contains("\"formatVersion\":1"))
        assertTrue(manifest.contains("\"physicalObjectType\":\"HOLLOW\""))
        assertTrue(manifest.contains(fixture.graph.id.toString()))
        assertTrue(manifest.contains("\"objectEntry\":\"object.json\""))
        assertTrue(manifest.contains("\"objectSha256\""))
        fixture.graph.media.forEach { media ->
            assertTrue(manifest.contains("media/${media.id}"))
            assertTrue(manifest.contains(media.sha256))
        }
        assertEquals(setOf("manifest.json", "object.json") + fixture.blobs.keys.map { "media/$it" }, entries.keys)
    }

    @Test
    fun `object json carries owned data and the labelled context only`() {
        val fixture = fixture(PhysicalObjectType.HOLLOW)
        val objectJson = entries(encode(fixture.graph, fixture.blobs))
            .getValue("object.json").toString(Charsets.UTF_8)

        assertTrue(objectJson.contains(fixture.graph.territory.code))
        assertTrue(objectJson.contains(fixture.graph.observer!!.lastName))
        assertTrue(objectJson.contains("\"entranceAzimuthDeg\":127"))
        // Nothing from the observation graph may appear, even in a package of a referenced object.
        listOf("bee", "flightCycle", "observationPoint", "weather", "attachment").forEach { foreign ->
            assertFalse("object.json must not mention $foreign", objectJson.contains(foreign, ignoreCase = true))
        }
    }

    @Test
    fun `media ordering is canonical so identical graphs produce identical bytes`() {
        val fixture = fixture(PhysicalObjectType.LOG_HIVE, twoMedia = true)
        val reversed = fixture.graph.copy(
            media = fixture.graph.media.sortedByDescending { it.createdAt.toString() },
        )

        assertArrayEquals(encode(fixture.graph, fixture.blobs), encode(reversed, fixture.blobs))
        assertArrayEquals(encode(fixture.graph, fixture.blobs), encode(fixture.graph, fixture.blobs))
    }

    @Test
    fun `unsupported profile and format version are rejected`() {
        val archive = encode(fixture(PhysicalObjectType.HOLLOW).graph, fixture(PhysicalObjectType.HOLLOW).blobs)
        val original = entries(archive)

        assertInvalid {
            decode(replace(original, "manifest.json") { it.replace("SINGLE_PHYSICAL_OBJECT", "SINGLE_OBSERVATION_POINT") })
        }
        assertInvalid {
            decode(replace(original, "manifest.json") { it.replace("\"formatVersion\":1", "\"formatVersion\":2") })
        }
    }

    @Test
    fun `unsupported object type is refused instead of producing a half shaped package`() {
        val fixture = fixture(PhysicalObjectType.HOLLOW)
        assertUnsupportedType {
            encode(fixture.graph.copy(type = PhysicalObjectType.APIARY), fixture.blobs)
        }
        val original = entries(encode(fixture.graph, fixture.blobs))
        assertUnsupportedType {
            decode(replace(original, "manifest.json") { it.replace("\"physicalObjectType\":\"HOLLOW\"", "\"physicalObjectType\":\"APIARY\"") })
        }
    }

    @Test
    fun `malformed JSON missing entries and unexpected entries are rejected`() {
        val original = entries(encode(fixture(PhysicalObjectType.HOLLOW).graph, fixture(PhysicalObjectType.HOLLOW).blobs))

        // The descriptor is patched too, so the case tests malformed JSON and not a broken hash.
        assertInvalid { decode(replaceObject(original, "{".toByteArray())) }
        assertInvalid { decode(writeEntries(original - "object.json")) }
        assertInvalid { decode(writeEntries(original - "manifest.json")) }
        assertInvalid { decode(writeEntries(original + ("extra.json" to byteArrayOf(1)))) }
        assertInvalid { decode(writeEntries(original + ("media/${UUID.randomUUID()}" to byteArrayOf(1)))) }
    }

    @Test
    fun `undeclared JSON field is rejected because the schema is the whole schema`() {
        val original = entries(encode(fixture(PhysicalObjectType.HOLLOW).graph, fixture(PhysicalObjectType.HOLLOW).blobs))
        val patched = replaceObjectDescriptor(original, "\"sequenceNumber\":7", "\"sequenceNumber\":7,\"beeId\":\"x\"")

        assertInvalid { decode(patched) }
    }

    @Test
    fun `unsafe absolute and traversal ZIP entries are rejected`() {
        assertInvalid { decode(writeEntries(linkedMapOf("../object.json" to byteArrayOf(1)))) }
        assertInvalid { decode(writeEntries(linkedMapOf("/object.json" to byteArrayOf(1)))) }
        assertInvalid { decode(writeEntries(linkedMapOf("media\\1" to byteArrayOf(1)))) }
        assertInvalid { decode(writeEntries(linkedMapOf("C:object.json" to byteArrayOf(1)))) }
    }

    @Test
    fun `duplicate ZIP entry is rejected`() {
        val duplicate = rawStoredZip(listOf("manifest.json" to byteArrayOf(1), "manifest.json" to byteArrayOf(2)))

        assertInvalid { decode(duplicate) }
    }

    @Test
    fun `size and SHA-256 mismatches of the object or of a media entry are refused`() {
        val fixture = fixture(PhysicalObjectType.HOLLOW)
        val original = entries(encode(fixture.graph, fixture.blobs))
        val mediaEntry = "media/${fixture.graph.media.single().id}"

        assertIntegrity { decode(replaceBytes(original, mediaEntry, byteArrayOf(1, 2, 3))) }
        assertIntegrity {
            decode(replace(original, "manifest.json") { it.replace(fixture.graph.media.single().sha256, "0".repeat(64)) })
        }
        assertIntegrity {
            decode(replace(original, "object.json") { "$it " })
        }
    }

    @Test
    fun `damaged media metadata is refused before a package can look successful`() {
        val fixture = fixture(PhysicalObjectType.HOLLOW)
        val media = fixture.graph.media.single()

        assertInvalid {
            encode(fixture.graph.copy(media = listOf(media.copy(sha256 = "not-a-hash"))), fixture.blobs)
        }
        assertInvalid {
            encode(fixture.graph.copy(media = listOf(media.copy(byteSize = 0L))), fixture.blobs)
        }
        assertInvalid { encode(fixture.graph, emptyMap()) }
        // A well-formed hash or size that does not describe the stored bytes is an integrity failure.
        assertIntegrity {
            encode(fixture.graph.copy(media = listOf(media.copy(sha256 = "0".repeat(64)))), fixture.blobs)
        }
        assertIntegrity {
            encode(fixture.graph.copy(media = listOf(media.copy(byteSize = media.byteSize + 1))), fixture.blobs)
        }
    }

    @Test
    fun `media of another object and foreign context are refused`() {
        val fixture = fixture(PhysicalObjectType.HOLLOW)
        val media = fixture.graph.media.single()

        assertInvalid {
            encode(fixture.graph.copy(media = listOf(media.copy(physicalObjectId = UUID.randomUUID()))), fixture.blobs)
        }
        assertInvalid {
            val foreign = "physical-object-media/${UUID.randomUUID()}/${media.id}"
            encode(fixture.graph.copy(media = listOf(media.copy(relativePath = foreign))), fixture.blobs)
        }
        assertInvalid { encode(fixture.graph.copy(territoryId = UUID.randomUUID()), fixture.blobs) }
        assertInvalid { encode(fixture.graph.copy(observer = fixture.graph.observer!!.copy(id = UUID.randomUUID())), fixture.blobs) }
        assertInvalid { encode(fixture.graph.copy(creatorObserverId = null), fixture.blobs) }
    }

    @Test
    fun `archive and entry limits are enforced`() {
        val fixture = fixture(PhysicalObjectType.HOLLOW)
        val mediaId = fixture.graph.media.single().id
        val oversized = ByteArray((PhysicalObjectExportContract.MAX_ENTRY_BYTES + 1).toInt())
        val original = entries(encode(fixture.graph, fixture.blobs))

        assertInvalid { decode(writeEntries(original + ("media/$mediaId" to oversized))) }
        val manyEntries = linkedMapOf<String, ByteArray>()
        repeat(PhysicalObjectExportContract.MAX_ENTRIES + 1) { index -> manyEntries["media/$index"] = byteArrayOf(1) }
        assertInvalid { decode(writeEntries(manyEntries)) }
        assertInvalid { decode(ByteArray(0)) }
    }

    private fun fixture(type: PhysicalObjectType, twoMedia: Boolean = false): Fixture {
        val objectId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val territoryId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val observerId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val mediaIds = listOf(UUID.fromString("44444444-4444-4444-4444-444444444444")) +
            if (twoMedia) listOf(UUID.fromString("55555555-5555-5555-5555-555555555555")) else emptyList()
        val blobs = mediaIds.mapIndexed { index, id -> id to "media-$index-bytes".toByteArray() }.toMap()
        val media = blobs.entries.mapIndexed { index, (id, bytes) ->
            PhysicalObjectMedia(
                id = id,
                physicalObjectId = objectId,
                type = if (index == 0) PhysicalObjectMediaType.IMAGE else PhysicalObjectMediaType.VIDEO,
                relativePath = PhysicalObjectMediaFileStore.relativePath(objectId, id),
                originalFileName = if (index == 0) "дупло.jpg" else null,
                mimeType = if (index == 0) "image/jpeg" else "video/mp4",
                byteSize = bytes.size.toLong(),
                sha256 = sha(bytes),
                createdAt = Instant.parse("2026-09-20T10:0${index + 1}:00Z"),
            )
        }
        return Fixture(
            PhysicalObjectExportGraph(
                id = objectId,
                type = type,
                territoryId = territoryId,
                sequenceNumber = 7,
                latitude = 56.1961784,
                longitude = 42.7480444,
                createdAt = Instant.parse("2026-09-20T10:00:00Z"),
                creatorObserverId = observerId,
                name = "Старое дупло",
                properties = when (type) {
                    PhysicalObjectType.HOLLOW -> PhysicalObjectExportProperties.Hollow(
                        HollowProperties("дуб", 180.0, 127, 40.0, 25.0, "рядом с тропой"),
                    )

                    PhysicalObjectType.LOG_HIVE -> PhysicalObjectExportProperties.LogHive(
                        LogHiveProperties("сосна", 250.0, 90, 45.0, "сосна", 30.0, 120.0, null),
                    )

                    PhysicalObjectType.APIARY -> null
                },
                media = media,
                territory = TerritoryExportSnapshot(territoryId, "DEV-BENCH2", "Тестовая территория"),
                observer = ObserverExportSnapshot(observerId, "DEV-OBS1", "Иванов", "Иван", "Иванович"),
            ),
            blobs,
        )
    }

    private fun encode(graph: PhysicalObjectExportGraph, blobs: Map<UUID, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { PhysicalObjectExportCodec.encode(graph, blobs, it) }.toByteArray()

    private fun decode(archive: ByteArray) = PhysicalObjectExportCodec.decode(archive.inputStream())

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
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    /** Rewrites one entry of a valid package, so every negative case differs in exactly one way. */
    private fun replace(
        original: Map<String, ByteArray>,
        name: String,
        transform: (String) -> String,
    ): ByteArray = writeEntries(
        original.mapValues { (entryName, bytes) ->
            if (entryName == name) transform(bytes.toString(Charsets.UTF_8)).toByteArray() else bytes
        },
    )

    private fun replaceBytes(
        original: Map<String, ByteArray>,
        name: String,
        bytes: ByteArray,
    ): ByteArray = writeEntries(original + (name to bytes))

    /** Replaces `object.json` and updates its manifest descriptor, so the package stays self-consistent. */
    private fun replaceObject(original: Map<String, ByteArray>, bytes: ByteArray): ByteArray {
        val manifest = original.getValue("manifest.json").toString(Charsets.UTF_8)
            .replace(Regex("\"objectByteLength\":\\d+"), "\"objectByteLength\":${bytes.size}")
            .replace(Regex("\"objectSha256\":\"[0-9a-f]{64}\""), "\"objectSha256\":\"${sha(bytes)}\"")
        return writeEntries(
            original + ("object.json" to bytes) + ("manifest.json" to manifest.toByteArray()),
        )
    }

    private fun replaceObjectDescriptor(
        original: Map<String, ByteArray>,
        from: String,
        to: String,
    ): ByteArray {
        val patchedObject = original.getValue("object.json").toString(Charsets.UTF_8).replace(from, to)
        val manifest = original.getValue("manifest.json").toString(Charsets.UTF_8)
            .replace(Regex("\"objectByteLength\":\\d+"), "\"objectByteLength\":${patchedObject.toByteArray().size}")
            .replace(Regex("\"objectSha256\":\"[0-9a-f]{64}\""), "\"objectSha256\":\"${sha(patchedObject.toByteArray())}\"")
        return writeEntries(
            original + ("object.json" to patchedObject.toByteArray()) + ("manifest.json" to manifest.toByteArray()),
        )
    }

    /** Minimal STORED local records are enough for ZipInputStream and allow duplicate names. */
    private fun rawStoredZip(entries: List<Pair<String, ByteArray>>): ByteArray =
        ByteArrayOutputStream().also { output ->
            entries.forEach { (name, bytes) ->
                val nameBytes = name.toByteArray()
                val crc = CRC32().apply { update(bytes) }.value
                fun short(value: Int) {
                    output.write(value and 0xff)
                    output.write((value ushr 8) and 0xff)
                }

                fun int(value: Long) {
                    short((value and 0xffff).toInt())
                    short(((value ushr 16) and 0xffff).toInt())
                }

                int(0x04034b50); short(20); short(0); short(0); short(0); short(0)
                int(crc); int(bytes.size.toLong()); int(bytes.size.toLong()); short(nameBytes.size); short(0)
                output.write(nameBytes); output.write(bytes)
            }
        }.toByteArray()

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun assertInvalid(block: () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue("expected InvalidPhysicalObjectExport, got $thrown", thrown is InvalidPhysicalObjectExport)
    }

    private fun assertIntegrity(block: () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue("expected PhysicalObjectExportIntegrityError, got $thrown", thrown is PhysicalObjectExportIntegrityError)
    }

    private fun assertUnsupportedType(block: () -> Unit) {
        var thrown: Throwable? = null
        try {
            block()
        } catch (error: Throwable) {
            thrown = error
        }
        assertTrue("expected UnsupportedPhysicalObjectExportType, got $thrown", thrown is UnsupportedPhysicalObjectExportType)
    }

    private data class Fixture(val graph: PhysicalObjectExportGraph, val blobs: Map<UUID, ByteArray>)
}
