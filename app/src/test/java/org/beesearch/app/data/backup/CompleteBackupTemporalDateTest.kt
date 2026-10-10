package org.beesearch.app.data.backup

import org.beesearch.app.domain.backup.MalformedBackup
import org.beesearch.app.data.local.room.*
import org.beesearch.app.data.zip.ArchivePayload
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.WeatherStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.security.MessageDigest
import java.time.Instant
import java.util.TimeZone
import java.util.UUID

    /** Characterizes the V8 canonical research date and instant temporal fields at the private graph parser. */
class CompleteBackupTemporalDateTest {
    @Test fun actualV8WriterBlobsRoundTripThroughParser() {
        val oldZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        try {
        val t = UUID.fromString("00000000-0000-0000-0000-000000000101")
        val o = UUID.fromString("00000000-0000-0000-0000-000000000102")
        val h = UUID.fromString("00000000-0000-0000-0000-000000000103")
        val l = UUID.fromString("00000000-0000-0000-0000-000000000104")
        val a = UUID.fromString("00000000-0000-0000-0000-000000000105")
        val p = UUID.fromString("00000000-0000-0000-0000-000000000106")
        val created = Instant.parse("2026-08-20T12:00:00Z")
        val graph = Graph(
            territories = listOf(TerritoryEntity(t, "T", "Territory", "R", "D", created, created)),
            observers = listOf(ObserverEntity(o, "O", "Last", "First", null, null, created, created)),
            physicalObjects = listOf(
                PhysicalObjectEntity(h, t, PhysicalObjectType.HOLLOW, 1, 56.0, 42.0, created, o, java.time.LocalDate.of(2026, 8, 21),
                    fixationAt = Instant.parse("2026-08-21T08:00:00Z"), updatedAt = Instant.parse("2026-08-22T08:00:00Z")),
                PhysicalObjectEntity(l, t, PhysicalObjectType.LOG_HIVE, 1, 56.0, 42.0, created, o, java.time.LocalDate.of(2026, 8, 22)),
                PhysicalObjectEntity(a, t, PhysicalObjectType.APIARY, 1, 56.0, 42.0, created, o, null),
            ),
            apiaries = listOf(ApiaryEntity(a, null)),
            hollows = listOf(HollowEntity(h, null, null, null, null, null, null)),
            logHives = listOf(LogHiveEntity(l, null, null, null, null, null, null, null, null)),
            points = listOf(ObservationPointEntity(java.time.LocalDate.of(2026, 8, 25), p, t, o, 2026, 1, null, "P", 56.1, 42.7, null, null, null, created, null, null, null)),
            bees = emptyList(), cycles = emptyList(),
            weather = listOf(ObservationPointWeatherEntity(p, WeatherStatus.PENDING, null, null, null, null, null, null)),
        )
        val settings = PortableSettingsSnapshot(null, null, emptyMap())
        val writer = declaredMethod("blobsV7", Graph::class.java, PortableSettingsSnapshot::class.java, Class.forName("org.beesearch.app.data.media.ObservationAttachmentFileStore"), Class.forName("org.beesearch.app.data.media.PhysicalObjectMediaFileStore"), Int::class.javaPrimitiveType!!)
        @Suppress("UNCHECKED_CAST")
        val blobs = writer.invoke(null, graph, settings, null, null, 8) as List<Any>
        val manifestMethod = declaredMethod("manifest", UUID::class.java, Instant::class.java, String::class.java, List::class.java, Int::class.javaPrimitiveType!!)
        val manifest = manifestMethod.invoke(null, UUID.randomUUID(), created, "test", blobs, 8) as ByteArray
        val entries = linkedMapOf<String, ArchivePayload>(MANIFEST to ArchivePayload.metadata(manifest))
        blobs.forEach { blob ->
            val type = blob.javaClass
            entries[type.getDeclaredMethod("getPath").also { it.isAccessible = true }.invoke(blob) as String] =
                type.getDeclaredMethod("getPayload").also { it.isAccessible = true }.invoke(blob) as ArchivePayload
        }
        val parsed = parsePayloadObject(entries)
        val parsedGraph = parsed.javaClass.getDeclaredMethod("getGraph").also { it.isAccessible = true }.invoke(parsed) as Graph
        assertEquals(java.time.LocalDate.of(2026, 8, 25), parsedGraph.points.single().observationDate)
        assertEquals(java.time.LocalDate.of(2026, 8, 21), parsedGraph.physicalObjects.single { it.id == h }.fixationDate)
        assertEquals(Instant.parse("2026-08-21T08:00:00Z"), parsedGraph.physicalObjects.single { it.id == h }.fixationAt)
        assertEquals(Instant.parse("2026-08-22T08:00:00Z"), parsedGraph.physicalObjects.single { it.id == h }.updatedAt)
        assertEquals(java.time.LocalDate.of(2026, 8, 22), parsedGraph.physicalObjects.single { it.id == l }.fixationDate)
        assertEquals(null, parsedGraph.physicalObjects.single { it.id == a }.fixationDate)
        assertEquals("14", String(manifest).substringAfter("\"roomSchemaVersion\":").substringBefore(','))
        val pointPayload = entries.getValue("research/observation-points.json").readMetadata(1024).toString(Charsets.UTF_8)
        val physicalPayload = entries.getValue("research/physical-objects.json").readMetadata(1024).toString(Charsets.UTF_8)
        assertTrue(pointPayload.contains("\"observationDate\":\"2026-08-25\""))
        assertTrue(physicalPayload.contains("\"fixationDate\":\"2026-08-21\""))
        assertTrue(physicalPayload.contains("\"fixationAt\":1787299200000"))
        assertTrue(physicalPayload.contains("\"updatedAt\":1787385600000"))
        assertTrue(physicalPayload.contains("\"fixationDate\":\"2026-08-22\""))
        assertTrue(physicalPayload.contains("\"fixationDate\":null"))
        val v7Graph = graph.copy(physicalObjects = graph.physicalObjects.map {
            it.copy(fixationAt = null, updatedAt = null)
        })
        @Suppress("UNCHECKED_CAST")
        val v7Blobs = writer.invoke(null, v7Graph, settings, null, null, 7) as List<Any>
        assertTrue(v7Blobs.isNotEmpty())
        } finally {
            TimeZone.setDefault(oldZone)
        }
    }

    @Test fun parsesV7PointAndNullablePhysicalFixationDate() {
        val parsed = parseObject(validEntries())
        val graph = parsed.javaClass.getDeclaredMethod("getGraph").also { it.isAccessible = true }.invoke(parsed) as Graph
        assertEquals(java.time.LocalDate.of(2026, 8, 20), graph.points.single().observationDate)
        assertTrue(graph.physicalObjects.all { it.fixationDate == null })
    }

    @Test fun parsesLegacyV6RowsWithDerivedDateMaterialization() {
        val oldZone = TimeZone.getDefault()
        try {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val entries = validEntries().toMutableMap()
        val pointPath = CONTRACTS.getValue("observation-points")
        val objectPath = CONTRACTS.getValue("physical-objects")
        entries[pointPath] = entries.getValue(pointPath).toString(Charsets.UTF_8).replace(",\"observationDate\":\"2026-08-20\"", "").toByteArray()
        entries[objectPath] = entries.getValue(objectPath).toString(Charsets.UTF_8).replace(Regex(",\"fixationDate\":null"), "").toByteArray()
        entries[MANIFEST] = legacyManifest(entries)
        val parsed = parseObject(entries)
        val graph = parsed.javaClass.getDeclaredMethod("getGraph").also { it.isAccessible = true }.invoke(parsed) as Graph
        assertEquals(java.time.LocalDate.of(2026, 8, 20), graph.points.single().observationDate)
        assertEquals(3, graph.physicalObjects.size)
        assertTrue(graph.physicalObjects.all { it.fixationDate == null })
        } finally { TimeZone.setDefault(oldZone) }
    }

    @Test fun missingPointObservationDateIsRejectedAfterManifestHashesAreUpdated() {
        val entries = validEntries()
        val point = entries.getValue("research/observation-points.json").toString(Charsets.UTF_8)
            .replace(Regex(",\"observationDate\":\"[^\"]+\""), "")
        assertMalformed(withCollection(entries, "observation-points", point), "missing field observationDate")
    }

    @Test fun malformedPointObservationDateIsRejected() {
        val entries = validEntries()
        val point = entries.getValue("research/observation-points.json").toString(Charsets.UTF_8)
            .replace("\"observationDate\":\"2026-08-20\"", "\"observationDate\":\"2026-02-30\"")
        assertMalformed(withCollection(entries, "observation-points", point), "invalid research date")
    }

    @Test fun wrongTypePointObservationDateIsRejected() {
        val entries = validEntries()
        val point = entries.getValue("research/observation-points.json").toString(Charsets.UTF_8)
            .replace("\"observationDate\":\"2026-08-20\"", "\"observationDate\":123")
        assertMalformed(withCollection(entries, "observation-points", point), "invalid date observationDate")
    }

    @Test fun missingPhysicalFixationDateIsRejectedAfterManifestHashesAreUpdated() {
        val entries = validEntries()
        val physical = entries.getValue("research/physical-objects.json").toString(Charsets.UTF_8)
            .replace(Regex(",\"fixationDate\":(?:\"[^\"]+\"|null)"), "")
        assertMalformed(withCollection(entries, "physical-objects", physical), "missing field fixationDate")
    }

    @Test fun malformedAndWrongTypePhysicalFixationDateAreRejected() {
        val malformed = validEntries().let {
            withCollection(it, "physical-objects", it.getValue("research/physical-objects.json").toString(Charsets.UTF_8).replace("\"fixationDate\":null", "\"fixationDate\":\"2026-02-30\""))
        }
        assertMalformed(malformed, "invalid research date")
        val wrongType = validEntries().let {
            withCollection(it, "physical-objects", it.getValue("research/physical-objects.json").toString(Charsets.UTF_8).replace("\"fixationDate\":null", "\"fixationDate\":123"))
        }
        assertMalformed(wrongType, "invalid date fixationDate")
    }

    private fun assertMalformed(entries: Map<String, ByteArray>, message: String) {
        val error = assertThrows(MalformedBackup::class.java) { parse(entries) }
        assertEquals(true, error.message!!.contains(message))
    }

    private fun parse(entries: Map<String, ByteArray>) {
        parseObject(entries)
    }

    private fun parseObject(entries: Map<String, ByteArray>): Any = parsePayloadObject(entries.mapValues { ArchivePayload.metadata(it.value) })

    private fun parsePayloadObject(entries: Map<String, ArchivePayload>): Any {
        val method = declaredMethod("parse", Map::class.java)
        try {
            return method.invoke(null, entries)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private fun withCollection(entries: Map<String, ByteArray>, name: String, row: String): Map<String, ByteArray> {
        val path = CONTRACTS.getValue(name)
        val updated = entries.toMutableMap()
        updated[path] = (if (row.isEmpty()) "" else "$row\n").toByteArray()
        updated[MANIFEST] = manifest(updated)
        return updated
    }

    private fun validEntries(): Map<String, ByteArray> {
        val t = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val o = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val h = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val l = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val a = UUID.fromString("00000000-0000-0000-0000-000000000005")
        val p = UUID.fromString("00000000-0000-0000-0000-000000000006")
        val created = Instant.parse("2026-08-20T12:00:00Z").toEpochMilli()
        val rows = CONTRACTS.mapValues { "" }.toMutableMap()
        rows["territories"] = "{\"id\":\"$t\",\"code\":\"T\",\"name\":\"Territory\",\"region\":\"R\",\"district\":\"D\",\"createdAt\":$created,\"updatedAt\":$created}"
        rows["observers"] = "{\"id\":\"$o\",\"code\":\"O\",\"lastName\":\"Last\",\"firstName\":\"First\",\"middleName\":null,\"contact\":null,\"createdAt\":$created,\"updatedAt\":$created}"
        rows["physical-objects"] = listOf(
            "{\"id\":\"$h\",\"territoryId\":\"$t\",\"objectType\":\"HOLLOW\",\"sequenceNumber\":1,\"latitude\":56.0,\"longitude\":42.0,\"createdAt\":$created,\"creatorObserverId\":\"$o\",\"fixationDate\":null}",
            "{\"id\":\"$l\",\"territoryId\":\"$t\",\"objectType\":\"LOG_HIVE\",\"sequenceNumber\":1,\"latitude\":56.0,\"longitude\":42.0,\"createdAt\":$created,\"creatorObserverId\":\"$o\",\"fixationDate\":null}",
            "{\"id\":\"$a\",\"territoryId\":\"$t\",\"objectType\":\"APIARY\",\"sequenceNumber\":1,\"latitude\":56.0,\"longitude\":42.0,\"createdAt\":$created,\"creatorObserverId\":\"$o\",\"fixationDate\":null}",
        ).joinToString("\n")
        rows["hollows"] = "{\"physicalObjectId\":\"$h\",\"tree\":null,\"entranceHeightCm\":null,\"entranceAzimuthDeg\":null,\"outerDiameterCm\":null,\"internalDiameterCm\":null,\"notes\":null,\"name\":null}"
        rows["log-hives"] = "{\"physicalObjectId\":\"$l\",\"tree\":null,\"entranceHeightCm\":null,\"entranceAzimuthDeg\":null,\"outerDiameterCm\":null,\"material\":null,\"internalDiameterCm\":null,\"internalHeightCm\":null,\"notes\":null,\"name\":null}"
        rows["apiaries"] = "{\"physicalObjectId\":\"$a\",\"name\":null}"
        rows["observation-points"] = "{\"id\":\"$p\",\"territoryId\":\"$t\",\"observerId\":\"$o\",\"observationYear\":2026,\"pointNumber\":1,\"beePresenceResult\":null,\"code\":\"P\",\"latitude\":56.1,\"longitude\":42.7,\"gpsLatitude\":null,\"gpsLongitude\":null,\"gpsAccuracyM\":null,\"createdAt\":$created,\"initialGroupReleaseAt\":null,\"completedAt\":null,\"description\":null,\"observationDate\":\"2026-08-20\"}"
        rows["observation-point-weather"] = "{\"observationPointId\":\"$p\",\"status\":\"PENDING\",\"temperatureC\":null,\"windSpeedMps\":null,\"windDirectionDeg\":null,\"sampleAt\":null,\"fetchedAt\":null,\"source\":null}"
        rows["portable-settings"] = "{\"currentTerritoryId\":null,\"currentObserverId\":null}"
        val wire = CONTRACTS.entries.associate { (name, path) ->
            val value = rows.getValue(name)
            path to (if (value.isEmpty()) ByteArray(0) else "$value\n".toByteArray())
        }
        return wire + (MANIFEST to manifest(wire))
    }

    private fun manifest(entries: Map<String, ByteArray>): ByteArray {
        val descriptors = CONTRACTS.entries.joinToString(",") { (name, path) ->
            val bytes = entries.getValue(path)
            val count = bytes.toString(Charsets.UTF_8).lineSequence().count(String::isNotBlank)
            "{\"name\":\"$name\",\"path\":\"$path\",\"collectionSchemaVersion\":1,\"required\":true,\"recordCount\":$count,\"byteLength\":${bytes.size},\"sha256\":\"${sha(bytes)}\"}"
        }
        return "{\"backupFormatVersion\":7,\"archiveSchemaVersion\":7,\"archiveId\":\"00000000-0000-0000-0000-000000000099\",\"createdAt\":1,\"sourceAppVersion\":\"test\",\"roomSchemaVersion\":13,\"profile\":\"COMPLETE_BACKUP\",\"collections\":[$descriptors]}".toByteArray()
    }

    private fun legacyManifest(entries: Map<String, ByteArray>): ByteArray {
        val descriptors = CONTRACTS.entries.joinToString(",") { (name, path) ->
            val bytes = entries.getValue(path)
            val count = bytes.toString(Charsets.UTF_8).lineSequence().count(String::isNotBlank)
            "{\"name\":\"$name\",\"path\":\"$path\",\"collectionSchemaVersion\":1,\"required\":true,\"recordCount\":$count,\"byteLength\":${bytes.size},\"sha256\":\"${sha(bytes)}\"}"
        }
        return "{\"backupFormatVersion\":6,\"archiveSchemaVersion\":6,\"archiveId\":\"00000000-0000-0000-0000-000000000098\",\"createdAt\":1,\"sourceAppVersion\":\"test\",\"roomSchemaVersion\":11,\"profile\":\"COMPLETE_BACKUP\",\"collections\":[$descriptors]}".toByteArray()
    }

    private fun declaredMethod(name: String, vararg parameters: Class<*>) = Class.forName("org.beesearch.app.data.backup.BackupCoreKt").declaredMethods.single { it.name == name && it.parameterTypes.contentEquals(parameters) }.also { it.isAccessible = true }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MANIFEST = "manifest.json"
        val CONTRACTS = linkedMapOf(
            "territories" to "research/territories.json", "observers" to "research/observers.json",
            "physical-objects" to "research/physical-objects.json", "hollows" to "research/hollows.json",
            "log-hives" to "research/log-hives.json", "physical-object-media" to "research/physical-object-media.json",
            "apiaries" to "research/apiaries.json", "physical-object-sequences" to "research/physical-object-sequences.json",
            "observation-points" to "research/observation-points.json", "bees" to "research/bees.json",
            "flight-cycles" to "research/flight-cycles.json", "observation-point-weather" to "research/observation-point-weather.json",
            "observation-point-attachments" to "research/observation-point-attachments.json", "portable-settings" to "settings/portable-settings.json",
            "map-coverage" to "settings/map-coverage.json",
        )
    }
}
