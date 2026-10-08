package org.beesearch.app.data.backupsnapshot

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipFile
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertThrows

/** Exercises canonical ordering with every snapshot collection populated. */
class SnapshotAllCollectionsDeterminismTest {
    private val at = Instant.parse("2026-01-01T00:00:00Z")

    private fun id(number: Long): UUID = UUID.fromString("00000000-0000-4000-8000-%012d".format(number))

    private fun graph(): Graph {
        val territories = listOf(
            TerritoryEntity(id(1), "T1", "One", "R", "D", at, at),
            TerritoryEntity(id(2), "T2", "Two", "R", "D", at, at),
        )
        val observers = listOf(
            ObserverEntity(id(11), "O1", "One", "A", null, null, at, at),
            ObserverEntity(id(12), "O2", "Two", "B", null, null, at, at),
        )
        val objects = listOf(
            PhysicalObjectEntity(id(21), id(1), PhysicalObjectType.APIARY, 1, 55.70, 37.60, at, id(11)),
            PhysicalObjectEntity(id(22), id(1), PhysicalObjectType.HOLLOW, 1, 55.71, 37.61, at, id(11)),
            PhysicalObjectEntity(id(23), id(1), PhysicalObjectType.LOG_HIVE, 1, 55.72, 37.62, at, id(11)),
            PhysicalObjectEntity(id(24), id(2), PhysicalObjectType.APIARY, 1, 55.73, 37.63, at, id(12)),
            PhysicalObjectEntity(id(25), id(2), PhysicalObjectType.HOLLOW, 1, 55.74, 37.64, at, id(12)),
            PhysicalObjectEntity(id(26), id(2), PhysicalObjectType.LOG_HIVE, 1, 55.75, 37.65, at, id(12)),
        )
        val points = listOf(
            ObservationPointEntity(java.time.LocalDate.of(2026, 1, 1), id(31), id(1), id(11), 2026, 1, BeePresenceResult.BEES_FOUND, "P1", 55.80, 37.70, null, null, null, at, at, at.plusSeconds(60), "one"),
            ObservationPointEntity(java.time.LocalDate.of(2026, 1, 1), id(32), id(2), id(12), 2026, 1, BeePresenceResult.BEES_FOUND, "P2", 55.81, 37.71, null, null, null, at, null, null, "two"),
        )
        val bees = listOf(
            BeeEntity(id(41), id(31), "red", MarkPosition.THORAX, at, id(21)),
            BeeEntity(id(42), id(32), "blue", MarkPosition.ABDOMEN, at, id(24)),
        )
        return Graph(
            territories = territories,
            observers = observers,
            physicalObjects = objects,
            apiaries = listOf(ApiaryEntity(id(21), "A1"), ApiaryEntity(id(24), "A2")),
            hollows = listOf(
                HollowEntity(id(22), "oak", 120.0, 90, 40.0, 30.0, "h1", "H1"),
                HollowEntity(id(25), "pine", 130.0, 180, 45.0, 35.0, "h2", "H2"),
            ),
            logHives = listOf(
                LogHiveEntity(id(23), "birch", 100.0, 0, 50.0, "wood", 40.0, 80.0, "l1", "L1"),
                LogHiveEntity(id(26), "spruce", 110.0, 270, 55.0, "wood", 42.0, 85.0, "l2", "L2"),
            ),
            sequences = objects.map { PhysicalObjectSequenceEntity(it.territoryId, it.objectType, 1) },
            objectMedia = listOf(
                media(51, id(21), "a".repeat(64), "a.jpg", "image/jpeg"),
                media(52, id(24), "b".repeat(64), "b.jpg", "image/jpeg"),
            ),
            points = points,
            bees = bees,
            cycles = listOf(
                FlightCycleEntity(id(61), id(41), 1, at, at.plusSeconds(60), 90.0, true, true, false, at, at),
                FlightCycleEntity(id(62), id(42), 1, at, null, null, false, false, false, at, at),
            ),
            weather = listOf(
                ObservationPointWeatherEntity(id(31), WeatherStatus.LOADED, 20.0, 2.0, 180.0, at, at, "field"),
                ObservationPointWeatherEntity(id(32), WeatherStatus.UNAVAILABLE, null, null, null, null, null, null),
            ),
            attachments = listOf(
                attachment(71, id(31), "c".repeat(64), "c.jpg"),
                attachment(72, id(32), "d".repeat(64), "d.jpg"),
            ),
        )
    }

    private fun media(number: Long, objectId: UUID, sha: String, name: String, mime: String) =
        PhysicalObjectMediaEntity(id(number), objectId, PhysicalObjectMediaType.IMAGE, "objects/$name", name, mime, 10, sha, at)

    private fun attachment(number: Long, pointId: UUID, sha: String, name: String) =
        ObservationPointAttachmentEntity(id(number), pointId, AttachmentType.PHOTO, "points/$name", name, "image/jpeg", 10, sha, at)

    private fun settings(reverse: Boolean): PortableSettingsSnapshot {
        val values = listOf(id(1) to "v1|56.0,38.0,55.0,37.0", id(2) to "v1|57.0,39.0,56.0,38.0")
        return PortableSettingsSnapshot(id(1), id(12), (if (reverse) values.asReversed() else values).toMap())
    }

    @Test
    fun `all collections and settings are byte deterministic under input order changes`() {
        val source = graph()
        val reversed = source.copy(
            territories = source.territories.asReversed(), observers = source.observers.asReversed(),
            physicalObjects = source.physicalObjects.asReversed(), apiaries = source.apiaries.asReversed(),
            hollows = source.hollows.asReversed(), logHives = source.logHives.asReversed(),
            sequences = source.sequences.asReversed(), objectMedia = source.objectMedia.asReversed(),
            points = source.points.asReversed(), bees = source.bees.asReversed(), cycles = source.cycles.asReversed(),
            weather = source.weather.asReversed(), attachments = source.attachments.asReversed(),
        )
        val first = SnapshotDomainCodec.encode(source, settings(false))
        val second = SnapshotDomainCodec.encode(reversed, settings(true))
        assertEquals(first.records, second.records)
        assertEquals(first.portable.toList(), second.portable.toList())
        assertEquals(first.references, second.references)
        assertEquals(4, first.references.size)
        assertEquals(2, first.records.getValue("data/territories.jsonl").size)
        assertEquals(6, first.records.getValue("data/physical-object-sequences.jsonl").size)

        val root = Files.createTempDirectory("snapshot-determinism").toFile()
        try {
            val a = File(root, "a.zip")
            val b = File(root, "b.zip")
            SnapshotArchive().build(a, SnapshotIdentity(id(90), id(91), "Dev", 1_000L), first)
            SnapshotArchive().build(b, SnapshotIdentity(id(92), id(91), "Dev", 2_000L), second)
            assertEquals(nonManifestDigests(a), nonManifestDigests(b))
        } finally { root.deleteRecursively() }
    }

    private fun nonManifestDigests(file: File): Map<String, String> = ZipFile(file).use { zip ->
        zip.entries().asSequence().filter { it.name != "manifest.json" }.associate { entry ->
            entry.name to MessageDigest.getInstance("SHA-256").digest(zip.getInputStream(entry).use { it.readBytes() })
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }

    @Test
    fun `every populated wire collection enforces closed schema and stable order`() {
        val encoded = SnapshotDomainCodec.encode(graph(), settings(false))
        encoded.records.forEach { (path, records) ->
            val rows = records.map { SnapshotJson.parse(it.toByteArray(), false).jsonObject }
            SnapshotRecordSchema.validateRows(path, rows)
            val first = rows.first()
            val unknown = JsonObject(first + ("unexpected" to JsonPrimitive("value")))
            assertEquals(SnapshotError.INVALID_FORMAT, assertThrows(SnapshotException::class.java) {
                SnapshotRecordSchema.validateRows(path, listOf(unknown))
            }.error)
            // All emitted non-media fields are required, including nullable fields.
            val required = first.keys.first { it != "sha256" && it != "byteSize" }
            assertEquals(SnapshotError.INVALID_FORMAT, assertThrows(SnapshotException::class.java) {
                SnapshotRecordSchema.validateRows(path, listOf(JsonObject(first - required)))
            }.error)
            assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, assertThrows(SnapshotException::class.java) {
                SnapshotRecordSchema.validateRows(path, listOf(first, first))
            }.error)
            if (rows.size > 1) assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT,
                assertThrows(SnapshotException::class.java) {
                    SnapshotRecordSchema.validateRows(path, rows.asReversed())
                }.error)
        }
    }
}
