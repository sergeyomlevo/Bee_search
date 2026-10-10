package org.beesearch.app.data.backupsnapshot

import org.beesearch.app.data.backup.*
import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.backup.BackupDomainInvariantViolation
import org.beesearch.app.domain.model.PhysicalObjectType
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PhysicalObjectLegacyDateGuardTest {
    private val at = Instant.parse("2026-08-20T12:00:00Z")
    private val date = LocalDate.of(2026, 8, 20)
    private val settings = PortableSettingsSnapshot(null, null, emptyMap())

    @Test fun legacyNullObjectsRemainRepresentableAndSnapshotReadersMaterializeNull() {
        val graph = graph(null)
        validateLegacyPhysicalObjectDates(graph)
        val entries = SnapshotDomainCodec.encode(graph, settings, version = 1)
        val rows = entries.records.filterKeys { it != "settings/map-coverage.jsonl" }
            .mapValues { (_, lines) -> lines.map { SnapshotJson.parse(it.toByteArray(), false).jsonObject } }
        assertTrue(snapshotGraphFromRows(rows, version = 1).physicalObjects.all { it.fixationDate == null })
        val directory = Files.createTempDirectory("physical-legacy-date").toFile()
        try {
            val output = File(directory, "snapshot.zip")
            SnapshotArchive().build(output, identity(), entries)
            assertTrue(output.isFile)
        } finally { directory.deleteRecursively() }
    }

    @Test fun everyPhysicalTypeWithDateFailsBackupRepresentability() {
        PhysicalObjectType.entries.forEach { type ->
            val original = graph(null)
            val graph = original.copy(physicalObjects = original.physicalObjects.map { if (it.objectType == type) it.copy(fixationDate = date) else it })
            val error = assertThrows(BackupDomainInvariantViolation::class.java) { validateLegacyPhysicalObjectDates(graph) }
            assertTrue(error.message!!.contains("fixationDate"))
        }
    }

    @Test fun everyPhysicalTypeWithDateFailsSnapshotBeforeCreatingOrReplacingOutput() {
        val directory = Files.createTempDirectory("physical-rejected-date").toFile()
        try {
            PhysicalObjectType.entries.forEach { type ->
                val original = graph(null)
                val graph = original.copy(physicalObjects = original.physicalObjects.map { if (it.objectType == type) it.copy(fixationDate = date) else it })
                val output = File(directory, type.name + ".zip")
                val error = assertThrows(SnapshotException::class.java) {
                    SnapshotArchive().build(output, identity(), SnapshotDomainCodec.encode(graph, settings, version = 1))
                }
                assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error)
                assertTrue(error.category.contains("fixationDate"))
                assertFalse(output.exists())
                val previous = "existing snapshot".toByteArray()
                output.writeBytes(previous)
                assertThrows(SnapshotException::class.java) {
                    SnapshotArchive().build(output, identity(), SnapshotDomainCodec.encode(graph, settings, version = 1))
                }
                assertTrue(previous.contentEquals(output.readBytes()))
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun v2PreservesNonNullFixationDateThroughProductionBridge() {
        val source = graph(date)
        val entries = SnapshotDomainCodec.encode(source, settings, version = 2)
        val rows = entries.records
            .filterKeys { it != "settings/map-coverage.jsonl" }
            .mapValues { (_, lines) -> lines.map { SnapshotJson.parse(it.toByteArray(), false).jsonObject } }
        val restored = snapshotGraphFromRows(rows, version = 2)
        assertEquals(listOf(date), restored.physicalObjects.map { it.fixationDate }.distinct())
        assertEquals(source.physicalObjects.map { it.fixationDate }, restored.physicalObjects.map { it.fixationDate })
    }

    @Test fun v2RefusesNewInstantFieldsInsteadOfDroppingThem() {
        val instant = Instant.parse("2026-08-19T12:34:56Z")
        val source = graph(date).let { value -> value.copy(physicalObjects = value.physicalObjects.map {
            it.copy(fixationAt = instant, updatedAt = instant)
        }) }
        assertThrows(SnapshotException::class.java) {
            SnapshotDomainCodec.encode(source, settings, version = 2)
        }
        assertThrows(BackupDomainInvariantViolation::class.java) {
            validateLegacyPhysicalObjectInstants(source)
        }
    }

    @Test fun v3CarriesNullableInstantFieldsAndOldSnapshotRowsMaterializeNull() {
        val fixationAt = Instant.parse("2026-08-19T12:34:56Z")
        val updatedAt = Instant.parse("2026-09-01T00:00:00Z")
        val source = graph(date).let { value -> value.copy(physicalObjects = value.physicalObjects.map {
            it.copy(fixationAt = if (it.objectType == PhysicalObjectType.HOLLOW) fixationAt else null,
                updatedAt = if (it.objectType == PhysicalObjectType.HOLLOW) updatedAt else null)
        }) }
        val entries = SnapshotDomainCodec.encode(source, settings, version = 3)
        val rows = entries.records.filterKeys { it != "settings/map-coverage.jsonl" }
            .mapValues { (_, lines) -> lines.map { SnapshotJson.parse(it.toByteArray(), false).jsonObject } }
        val restored = snapshotGraphFromRows(rows, version = 3).physicalObjects.associateBy { it.id }
        val restoredHollow = restored.getValue(source.physicalObjects.first().id)
        assertEquals(fixationAt, restoredHollow.fixationAt)
        assertEquals(updatedAt, restoredHollow.updatedAt)

        val oldRows = SnapshotDomainCodec.encode(graph(null), settings, version = 2).records
            .filterKeys { it != "settings/map-coverage.jsonl" }
            .mapValues { (_, lines) -> lines.map { SnapshotJson.parse(it.toByteArray(), false).jsonObject } }
        assertTrue(snapshotGraphFromRows(oldRows, version = 2).physicalObjects.all {
            it.fixationAt == null && it.updatedAt == null
        })
    }

    @Test fun fixationInstantWithoutCalendarDateIsRejected() {
        val source = graph(null).let { value -> value.copy(physicalObjects = value.physicalObjects.map {
            it.copy(fixationAt = Instant.parse("2026-08-19T12:34:56Z"))
        }) }
        assertThrows(BackupDomainInvariantViolation::class.java) { validateGraph(source) }
    }


    @Test fun v1AndV2RefuseUpdatedAtEvenWhenFixationIsEntirelyUnknown() {
        val source = graph(null).let { value -> value.copy(physicalObjects = value.physicalObjects.map {
            it.copy(updatedAt = at.plusSeconds(60))
        }) }
        for (version in listOf(1, 2)) {
            val failure = assertThrows(SnapshotException::class.java) {
                SnapshotDomainCodec.encode(source, settings, version = version)
            }
            assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, failure.error)
        }
    }

    private fun identity() = SnapshotIdentity(UUID.randomUUID(), UUID.randomUUID(), "Dev", at.toEpochMilli())
    private fun graph(date: LocalDate?): Graph {
        val t = UUID.randomUUID(); val o = UUID.randomUUID()
        val h = UUID.randomUUID(); val l = UUID.randomUUID(); val a = UUID.randomUUID()
        return Graph(
            territories = listOf(TerritoryEntity(t, "T", "Territory", "R", "D", at, at)),
            observers = listOf(ObserverEntity(o, "O", "Last", "First", null, null, at, at)),
            points = emptyList(), bees = emptyList(), cycles = emptyList(),
            physicalObjects = listOf(PhysicalObjectEntity(h, t, PhysicalObjectType.HOLLOW, 1, 56.0, 42.0, at, o, date),
                PhysicalObjectEntity(l, t, PhysicalObjectType.LOG_HIVE, 1, 56.0, 42.0, at, o, date),
                PhysicalObjectEntity(a, t, PhysicalObjectType.APIARY, 1, 56.0, 42.0, at, o, date)),
            hollows = listOf(HollowEntity(h, null, null, null, null, null, null)),
            logHives = listOf(LogHiveEntity(l, null, null, null, null, null, null, null, null)),
            apiaries = listOf(ApiaryEntity(a, "Apiary")),
        )
    }
}
