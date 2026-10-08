package org.beesearch.app.data.backupsnapshot

import java.time.Instant
import java.time.LocalDate
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.TimeZone
import java.util.zip.ZipFile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backup.snapshotRows
import org.beesearch.app.data.backup.snapshotGraphFromRows
import org.beesearch.app.data.local.room.ApiaryEntity
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObservationPointWeatherEntity
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.PhysicalObjectEntity
import org.beesearch.app.data.local.room.PhysicalObjectSequenceEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.PhysicalObjectType
import org.beesearch.app.domain.model.WeatherStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotV2DateConformanceTest {
    private val at = Instant.parse("2026-08-20T12:00:00Z")
    private val observationDate = LocalDate.of(2026, 8, 19)
    private val fixationDate = LocalDate.of(2026, 7, 31)
    private val territory = UUID.fromString("71000000-0000-4000-8000-000000000001")
    private val observer = UUID.fromString("71000000-0000-4000-8000-000000000002")
    private val physicalObject = UUID.fromString("71000000-0000-4000-8000-000000000003")
    private val point = UUID.fromString("71000000-0000-4000-8000-000000000004")

    private fun graph(): Graph = Graph(
        territories = listOf(TerritoryEntity(territory, "T", "Territory", "R", "D", at, at)),
        observers = listOf(ObserverEntity(observer, "O", "Last", "First", null, null, at, at)),
        physicalObjects = listOf(PhysicalObjectEntity(
            physicalObject, territory, PhysicalObjectType.APIARY, 1, 56.0, 42.0, at, observer, fixationDate,
        )),
        apiaries = listOf(ApiaryEntity(physicalObject, "Apiary")),
        sequences = listOf(PhysicalObjectSequenceEntity(territory, PhysicalObjectType.APIARY, 1)),
        points = listOf(ObservationPointEntity(
            observationDate, point, territory, observer, observationDate.year, 1,
            BeePresenceResult.NO_BEES_FOUND, "P", 56.0, 42.0, null, null, null, at, null, at, null,
        )),
        bees = emptyList(), cycles = emptyList(),
        weather = listOf(ObservationPointWeatherEntity(point, WeatherStatus.UNAVAILABLE, null, null, null, null, null, null)),
    )

    private fun v2Rows(): Map<String, List<JsonObject>> = SnapshotDomainCodec.encode(
        graph(), PortableSettingsSnapshot(null, null, emptyMap()),
    ).records.filterKeys { it != "settings/map-coverage.jsonl" }.mapValues { (_, lines) ->
        lines.map { SnapshotJson.parse(it.toByteArray(), false).jsonObject }
    }

    private fun v2Entries() = SnapshotDomainCodec.encode(graph(), PortableSettingsSnapshot(null, null, emptyMap()))

    private fun identity() = SnapshotIdentity(
        UUID.fromString("71000000-0000-4000-8000-000000000010"),
        UUID.fromString("71000000-0000-4000-8000-000000000011"),
        "Dev", at.toEpochMilli(),
    )

    @Test fun v2RoundTripMaterializesBothPersistedDates() {
        val restored = snapshotGraphFromRows(v2Rows(), version = 2)
        assertEquals(listOf(observationDate), restored.points.map { it.observationDate })
        assertEquals(listOf(fixationDate), restored.physicalObjects.map { it.fixationDate })
    }

    @Test fun v2ArchiveBuildValidatesAndCarriesSeventeenEntries() {
        val root = Files.createTempDirectory("snapshot-v2-archive").toFile()
        try {
            val output = File(root, "snapshot.zip")
            val built = SnapshotArchive().build(output, identity(), v2Entries())
            val validated = SnapshotArchive().validate(output, identity().repositoryId, identity().variant,
                identity().snapshotId, built.wholeSha256)
            assertEquals(2, validated.formatVersion)
            ZipFile(output).use { zip -> assertEquals(17, zip.entries().asSequence().count()) }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun malformedV2DateCannotOverwriteExistingArchive() {
        val root = Files.createTempDirectory("snapshot-v2-invalid").toFile()
        try {
            val output = File(root, "snapshot.zip")
            val prior = "prior archive".toByteArray()
            output.writeBytes(prior)
            val records = v2Entries().records.toMutableMap()
            records["data/observation-points.jsonl"] = records.getValue("data/observation-points.jsonl")
                .map { it.replace("\"observationDate\":\"$observationDate\"", "\"observationDate\":\"2026-02-30\"") }
            val invalid = v2Entries().copy(records = records)
            expectInvalid { SnapshotArchive().build(output, identity(), invalid) }
            assertEquals(prior.toList(), output.readBytes().toList())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun v2PersistedDatesSurviveTimezoneChangeAndNullableFixation() {
        val oldZone = TimeZone.getDefault()
        val root = Files.createTempDirectory("snapshot-v2-timezone").toFile()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val source = graph()
            val nullableFixationSource = source.copy(
                physicalObjects = source.physicalObjects.map { it.copy(fixationDate = null) },
            )
            val entries = SnapshotDomainCodec.encode(nullableFixationSource, PortableSettingsSnapshot(null, null, emptyMap()))
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
            val output = File(root, "snapshot.zip")
            SnapshotArchive().build(output, identity(), entries)
            ZipFile(output).use { zip ->
                val rows = SnapshotContract.paths.filter { it.startsWith("data/") }.associateWith { path ->
                    zip.getInputStream(zip.getEntry(path)).bufferedReader().use { reader ->
                        reader.readLines().map { SnapshotJson.parse(it.toByteArray(), false).jsonObject }
                    }
                }
                val restored = snapshotGraphFromRows(rows, version = 2)
                assertEquals(listOf(observationDate), restored.points.map { it.observationDate })
                assertNull(restored.physicalObjects.single().fixationDate)
            }
        } finally {
            TimeZone.setDefault(oldZone)
            root.deleteRecursively()
        }
    }

    @Test fun v2RequiresCanonicalObservationAndFixationDateFields() {
        val rows = v2Rows()
        for ((path, field) in listOf(
            "data/observation-points.jsonl" to "observationDate",
            "data/physical-objects.jsonl" to "fixationDate",
        )) {
            val row = rows.getValue(path).single()
            val missing = JsonObject(row - field)
            expectInvalid { SnapshotRecordSchema.validateRows(path, listOf(missing), version = 2) }
        }
    }

    @Test fun v2RejectsMalformedAndWrongTypeDates() {
        val rows = v2Rows()
        val observation = rows.getValue("data/observation-points.jsonl").single()
        val physical = rows.getValue("data/physical-objects.jsonl").single()
        for (candidate in listOf(
            JsonObject(observation + ("observationDate" to SnapshotJson.parse("\"2026-02-30\"".toByteArray(), false))),
            JsonObject(observation + ("observationDate" to SnapshotJson.parse("\"2026-8-19\"".toByteArray(), false))),
            JsonObject(observation + ("observationDate" to SnapshotJson.parse("123".toByteArray(), false))),
        )) {
            expectInvalid { SnapshotRecordSchema.validateRows("data/observation-points.jsonl", listOf(candidate), version = 2) }
        }
        for (candidate in listOf(
            JsonObject(physical + ("fixationDate" to SnapshotJson.parse("\"2026-02-30\"".toByteArray(), false))),
            JsonObject(physical + ("fixationDate" to SnapshotJson.parse("123".toByteArray(), false))),
        )) {
            expectInvalid { SnapshotRecordSchema.validateRows("data/physical-objects.jsonl", listOf(candidate), version = 2) }
        }
    }

    @Test fun v1SchemaRemainsClosedAndReadable() {
        val source = graph()
        val rows = snapshotRows(source, version = 1).filterKeys { it != "settings/map-coverage.jsonl" }
            .mapValues { (_, lines) -> lines.map { SnapshotJson.parse(it.toByteArray(), false).jsonObject } }
        rows.forEach { (path, values) -> SnapshotRecordSchema.validateRows(path, values, version = 1) }
        assertFalse(rows.getValue("data/observation-points.jsonl").single().containsKey("observationDate"))
        assertFalse(rows.getValue("data/physical-objects.jsonl").single().containsKey("fixationDate"))
        val pointWithV2Date = JsonObject(rows.getValue("data/observation-points.jsonl").single() +
            ("observationDate" to SnapshotJson.parse("\"2026-08-19\"".toByteArray(), false)))
        expectInvalid {
            SnapshotRecordSchema.validateRows("data/observation-points.jsonl", listOf(pointWithV2Date), version = 1)
        }
        val objectWithV2Date = JsonObject(rows.getValue("data/physical-objects.jsonl").single() +
            ("fixationDate" to SnapshotJson.parse("null".toByteArray(), false)))
        expectInvalid {
            SnapshotRecordSchema.validateRows("data/physical-objects.jsonl", listOf(objectWithV2Date), version = 1)
        }
        val restored = snapshotGraphFromRows(rows, version = 1)
        assertEquals(LocalDate.of(2026, 8, 20), restored.points.single().observationDate)
        assertNull(restored.physicalObjects.single().fixationDate)
    }

    private fun expectInvalid(block: () -> Unit) {
        val error = org.junit.Assert.assertThrows(SnapshotException::class.java, block)
        assertEquals(SnapshotError.INVALID_FORMAT, error.error)
    }
}
