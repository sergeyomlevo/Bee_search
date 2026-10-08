package org.beesearch.app.data.backupsnapshot

import java.io.File
import java.time.Instant
import java.util.UUID
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backup.validateGraph
import org.beesearch.app.domain.backup.DuplicateBackupIdentity
import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Reader-facing V1 vectors built through the production archive path. */
class SnapshotReaderAlignmentTest {
    @Rule @JvmField val temp = TemporaryFolder()
    private val at = Instant.parse("2026-01-01T00:00:00Z")
    private val t = UUID.fromString("70000000-0000-4000-8000-00000000000a")
    private val o = UUID.fromString("70000000-0000-4000-8000-000000000002")
    private val objectId = UUID.fromString("70000000-0000-4000-8000-000000000003")
    private val point = UUID.fromString("70000000-0000-4000-8000-000000000004")
    private val repo = UUID.fromString("70000000-0000-4000-8000-000000000005")
    private val snapshot = UUID.fromString("70000000-0000-4000-8000-000000000006")

    private fun graph(count: Int, completed: Boolean = false): Graph {
        val points = listOf(ObservationPointEntity(java.time.LocalDate.of(2026, 1, 1), point, t, o, 2026, 1,
            if (count == 0) BeePresenceResult.NO_BEES_FOUND else BeePresenceResult.BEES_FOUND,
            "P", 55.0, 37.0, null, null, null, at, null, if (completed) at else null, null))
        val bees = (1..count).map { n -> BeeEntity(UUID.fromString("70000000-0000-4000-8000-%012d".format(10L + n)), point, "c$n", MarkPosition.THORAX, at, objectId) }
        return Graph(
            territories = listOf(TerritoryEntity(t, "T", "Territory", "R", "D", at, at)),
            observers = listOf(ObserverEntity(o, "O", "Last", "First", null, null, at, at)),
            physicalObjects = listOf(PhysicalObjectEntity(objectId, t, PhysicalObjectType.APIARY, 1, 55.0, 37.0, at, o)),
            apiaries = listOf(ApiaryEntity(objectId, "Apiary")),
            sequences = listOf(PhysicalObjectSequenceEntity(t, PhysicalObjectType.APIARY, 1)),
            points = points, bees = bees, cycles = emptyList(),
            weather = listOf(ObservationPointWeatherEntity(point, WeatherStatus.UNAVAILABLE, null, null, null, null, null, null)),
        )
    }

    private fun archive(entries: SnapshotDomainEntries, mutate: (MutableMap<String, List<String>>) -> Unit = {}): File {
        val records = entries.records.toMutableMap()
        mutate(records)
        val changed = entries.copy(records = records)
        val root = temp.newFolder()
        val target = File(root, "snapshot.zip")
        try {
            SnapshotArchive().build(target, SnapshotIdentity(snapshot, repo, "Dev", 0), changed)
            return target
        } catch (error: Throwable) {
            throw error
        }
    }

    private fun mediaEntries(): SnapshotDomainEntries {
        val encoded = SnapshotDomainCodec.encode(graph(1), PortableSettingsSnapshot(null, null, emptyMap()))
        val media = "{\"id\":\"70000000-0000-4000-8000-000000000050\",\"physicalObjectId\":\"$objectId\",\"type\":\"IMAGE\",\"relativePath\":\"a\",\"originalFileName\":null,\"mimeType\":\"image/jpeg\",\"byteSize\":10,\"sha256\":\"${"a".repeat(64)}\",\"createdAt\":0}"
        return encoded.copy(records = encoded.records + ("data/physical-object-media.jsonl" to listOf(media)), references = listOf(SnapshotMediaReference("a".repeat(64), 10, "jpg")))
    }

    private fun expect(error: SnapshotError, block: () -> Unit) {
        try { block(); error("expected $error") }
        catch (actual: SnapshotException) { assertEquals(error, actual.error) }
    }

    @Test fun beeCardinalityBoundaries() {
        listOf(0, 1, 10).forEach { count -> archive(SnapshotDomainCodec.encode(graph(count, completed = count == 0), PortableSettingsSnapshot(null, null, emptyMap()))) }
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) { SnapshotDomainCodec.encode(graph(11), PortableSettingsSnapshot(null, null, emptyMap())) }
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) {
            archive(SnapshotDomainCodec.encode(graph(10), PortableSettingsSnapshot(null, null, emptyMap()))) { rows ->
                val id = "70000000-0000-4000-8000-000000000021"
                rows["data/bees.jsonl"] = rows.getValue("data/bees.jsonl") +
                    "{\"id\":\"$id\",\"observationPointId\":\"$point\",\"markColor\":\"extra\",\"markPosition\":\"THORAX\",\"createdAt\":0,\"sourceObjectId\":null}"
            }
        }
    }

    @Test fun completedSecondPointWithTenBeesIsAccepted() {
        val first = graph(10, completed = true)
        val secondPoint = UUID.fromString("70000000-0000-4000-8000-000000000030")
        val second = first.copy(points = first.points + ObservationPointEntity(java.time.LocalDate.of(2026, 1, 1), secondPoint, t, o, 2026, 2,
            BeePresenceResult.BEES_FOUND, "P2", 55.1, 37.1, null, null, null, at, null, at, null),
            bees = first.bees + (1..10).map { n -> BeeEntity(UUID.fromString("70000000-0000-4000-8000-%012d".format(40L + n)), secondPoint, "d$n", MarkPosition.ABDOMEN, at, objectId) },
            weather = first.weather + ObservationPointWeatherEntity(secondPoint, WeatherStatus.UNAVAILABLE, null, null, null, null, null, null))
        archive(SnapshotDomainCodec.encode(second, PortableSettingsSnapshot(null, null, emptyMap())))
    }

    @Test fun sharedUuidAcrossTerritoryAndObserverIsAccepted() {
        val source = graph(1)
        val shared = source.copy(
            observers = listOf(source.observers.single().copy(id = t)),
            physicalObjects = source.physicalObjects.map { it.copy(creatorObserverId = t) },
            points = source.points.map { it.copy(observerId = t) },
        )
        archive(SnapshotDomainCodec.encode(shared, PortableSettingsSnapshot(null, t, emptyMap())))
    }

    @Test fun duplicateWithinCollectionFails() {
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) {
            archive(SnapshotDomainCodec.encode(graph(1), PortableSettingsSnapshot(null, null, emptyMap()))) { rows ->
                rows["data/territories.jsonl"] = rows.getValue("data/territories.jsonl") + rows.getValue("data/territories.jsonl")
            }
        }
    }

    @Test fun reversedMultiRecordCollectionFails() {
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) {
            archive(SnapshotDomainCodec.encode(graph(1), PortableSettingsSnapshot(null, null, emptyMap()))) { rows ->
                val first = rows.getValue("data/territories.jsonl").single()
                val second = first.replace(t.toString(), "70000000-0000-4000-8000-00000000000b")
                    .replace("\"code\":\"T\"", "\"code\":\"U\"")
                rows["data/territories.jsonl"] = listOf(second, first)
            }
        }
    }

    @Test fun memberOrderPermutationIsAccepted() {
        archive(SnapshotDomainCodec.encode(graph(1), PortableSettingsSnapshot(null, null, emptyMap()))) { rows ->
            rows["data/territories.jsonl"] = rows.getValue("data/territories.jsonl").map {
                it.replace(Regex("^\\{\\\"id\\\":([^,]+),\\\"code\\\":\\\"T\\\""), "{\"code\":\"T\",\"id\":$1")
            }
        }
    }

    @Test fun invalidSchemaAndUuidShaAreRejected() {
        val base = SnapshotDomainCodec.encode(graph(1), PortableSettingsSnapshot(null, null, emptyMap()))
        expect(SnapshotError.INVALID_FORMAT) { archive(base) { rows -> rows["data/territories.jsonl"] = rows.getValue("data/territories.jsonl").map { it.replace("\"code\":\"T\"", "\"code\":\"T\",\"extra\":1") } } }
        expect(SnapshotError.INVALID_FORMAT) { archive(base) { rows -> rows["data/territories.jsonl"] = rows.getValue("data/territories.jsonl").map { it.replace(",\"code\":\"T\"", "") } } }
        expect(SnapshotError.INVALID_FORMAT) { archive(base) { rows -> rows["data/territories.jsonl"] = rows.getValue("data/territories.jsonl").map { it.replace(t.toString(), t.toString().uppercase()) } } }
        expect(SnapshotError.INVALID_FORMAT) { archive(base) { rows -> rows["data/physical-objects.jsonl"] = rows.getValue("data/physical-objects.jsonl").map { it.replace("\"objectType\":\"APIARY\"", "\"objectType\":\"UNKNOWN\"") } } }
    }

    @Test fun mediaMetadataAndReferenceReachabilityRules() {
        val full = mediaEntries()
        val zero = full.copy(records = full.records.mapValues { (path, rows) ->
            if (path == "data/physical-object-media.jsonl") rows.map { it.replace("\"byteSize\":10", "\"byteSize\":0").replace("\"sha256\":\"${"a".repeat(64)}\"", "\"sha256\":null") } else rows
        }, references = emptyList())
        archive(zero)
        val absent = full.copy(records = full.records.mapValues { (path, rows) ->
            if (path == "data/physical-object-media.jsonl") rows.map { it.replace(",\"byteSize\":10,\"sha256\":\"${"a".repeat(64)}\"", "") } else rows
        }, references = emptyList())
        archive(absent)
        val knownZero = full.copy(records = full.records.mapValues { (path, rows) ->
            if (path == "data/physical-object-media.jsonl") rows.map { it.replace("\"byteSize\":10", "\"byteSize\":0") } else rows
        }, references = emptyList())
        archive(knownZero)
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) { archive(knownZero.copy(references = full.references)) }
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) { archive(full.copy(references = emptyList())) }
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) { archive(full.copy(references = listOf(SnapshotMediaReference("b".repeat(64), 1, "bin")))) }
        expect(SnapshotError.INVALID_FORMAT) { archive(full) { rows -> rows["data/physical-object-media.jsonl"] = rows.getValue("data/physical-object-media.jsonl").map { it.replace("${"a".repeat(64)}", "${"A".repeat(64)}") } } }
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) {
            archive(full) { rows ->
                val second = rows.getValue("data/physical-object-media.jsonl").single().replace("000000000050", "000000000051").replace("\"byteSize\":10", "\"byteSize\":11")
                rows["data/physical-object-media.jsonl"] = rows.getValue("data/physical-object-media.jsonl") + second
            }
        }
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) {
            archive(full) { rows ->
                val second = rows.getValue("data/physical-object-media.jsonl").single().replace("000000000050", "000000000051").replace("image/jpeg", "video/mp4")
                rows["data/physical-object-media.jsonl"] = rows.getValue("data/physical-object-media.jsonl") + second
            }
        }
    }

    @Test fun coverageV1V2AndForeignKeyRules() {
        val v1 = PortableSettingsSnapshot(null, null, mapOf(t to "v1|56.0,38.0,55.0,37.0"))
        archive(SnapshotDomainCodec.encode(graph(1), v1))
        val v2 = PortableSettingsSnapshot(null, null, mapOf(t to "v2|{\"areaId\":\"70000000-0000-4000-8000-000000000099\",\"name\":\"Area\",\"bounds\":[{\"north\":56.0,\"east\":38.0,\"south\":55.0,\"west\":37.0}]}"))
        archive(SnapshotDomainCodec.encode(graph(1), v2))
        val covered = SnapshotDomainCodec.encode(graph(1), v1)
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) {
            archive(covered) { rows -> rows["settings/map-coverage.jsonl"] = rows.getValue("settings/map-coverage.jsonl")
                .map { it.replace(t.toString(), "70000000-0000-4000-8000-000000000099") } }
        }
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) {
            archive(covered) { rows -> rows["settings/map-coverage.jsonl"] = rows.getValue("settings/map-coverage.jsonl")
                .map { it.replace("v1|56.0,38.0,55.0,37.0", "v1|91,38,55,37") } }
        }
        expect(SnapshotError.LOGICAL_STATE_INCONSISTENT) {
            archive(SnapshotDomainCodec.encode(graph(1), PortableSettingsSnapshot(null, null, emptyMap()))) { rows ->
                rows["data/bees.jsonl"] = rows.getValue("data/bees.jsonl").map { it.replace(point.toString(), "70000000-0000-4000-8000-000000000099") }
            }
        }
    }

    @Test fun legacyGlobalIdentityCheckRemainsSeparate() {
        val source = graph(1)
        val shared = source.copy(observers = listOf(source.observers.single().copy(id = t)))
        org.junit.Assert.assertThrows(DuplicateBackupIdentity::class.java) { validateGraph(shared) }
        archive(SnapshotDomainCodec.encode(shared.copy(
            physicalObjects = shared.physicalObjects.map { it.copy(creatorObserverId = t) },
            points = shared.points.map { it.copy(observerId = t) },
        ), PortableSettingsSnapshot(null, t, emptyMap())))
    }

    @Test fun userStringsRemainExact() {
        val exact = "Mélange e\u0301  value"
        archive(SnapshotDomainCodec.encode(graph(1), PortableSettingsSnapshot(null, null, emptyMap()))) { rows ->
            rows["data/observers.jsonl"] = rows.getValue("data/observers.jsonl").map { it.replace("\"contact\":null", "\"contact\":\"$exact\"") }
            assertTrue(rows.getValue("data/observers.jsonl").single().contains(exact))
        }
    }
}
