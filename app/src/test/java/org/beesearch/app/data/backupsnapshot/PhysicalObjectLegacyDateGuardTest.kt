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
        val entries = SnapshotDomainCodec.encode(graph, settings)
        val rows = entries.records.filterKeys { it != "settings/map-coverage.jsonl" }
            .mapValues { (_, lines) -> lines.map { SnapshotJson.parse(it.toByteArray(), false).jsonObject } }
        assertTrue(snapshotGraphFromRows(rows).physicalObjects.all { it.fixationDate == null })
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
                    SnapshotArchive().build(output, identity(), SnapshotDomainCodec.encode(graph, settings))
                }
                assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error)
                assertTrue(error.category.contains("fixationDate"))
                assertFalse(output.exists())
                val previous = "existing snapshot".toByteArray()
                output.writeBytes(previous)
                assertThrows(SnapshotException::class.java) {
                    SnapshotArchive().build(output, identity(), SnapshotDomainCodec.encode(graph, settings))
                }
                assertTrue(previous.contentEquals(output.readBytes()))
            }
        } finally { directory.deleteRecursively() }
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
