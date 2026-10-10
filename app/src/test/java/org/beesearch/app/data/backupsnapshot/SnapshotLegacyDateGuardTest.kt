package org.beesearch.app.data.backupsnapshot

import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObservationPointWeatherEntity
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.domain.model.WeatherStatus
import org.beesearch.app.domain.model.legacyObservationDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import java.util.UUID

class SnapshotLegacyDateGuardTest {
    private val at = Instant.parse("2026-08-20T23:30:00Z")
    private val settings = PortableSettingsSnapshot(null, null, emptyMap())

    @Test fun representableDateProducesValidSnapshot() = inUtc { output ->
        SnapshotArchive().build(output, identity(), SnapshotDomainCodec.encode(graph(legacyObservationDate(at)), settings, version = 1))
        assertTrue(output.isFile)
    }

    @Test fun v2PreservesCorrectedObservationDate() = inUtc { output ->
        val corrected = LocalDate.of(2026, 8, 19)
        val entries = SnapshotDomainCodec.encode(graph(corrected), settings, version = 2)
        SnapshotArchive().build(output, identity(), entries)
        assertTrue(output.isFile)
    }

    @Test fun divergentDateRejectsSnapshotBeforeArtifactCreation() = inUtc { output ->
        val error = assertThrows(SnapshotException::class.java) {
            SnapshotArchive().build(output, identity(), SnapshotDomainCodec.encode(graph(LocalDate.of(2026, 8, 19)), settings, version = 1))
        }
        assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error)
        assertTrue(error.category.contains("not representable"))
        assertFalse(output.exists())
    }

    @Test fun timezoneChangeRejectsSnapshotUsingCurrentLegacyConvention() = inUtc { output ->
        val source = graph(legacyObservationDate(at))
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
        assertEquals(LocalDate.of(2026, 8, 21), legacyObservationDate(at))
        val error = assertThrows(SnapshotException::class.java) {
            SnapshotArchive().build(output, identity(), SnapshotDomainCodec.encode(source, settings, version = 1))
        }
        assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, error.error)
        assertFalse(output.exists())
    }

    private fun inUtc(block: (File) -> Unit) {
        val oldZone = TimeZone.getDefault()
        val directory = Files.createTempDirectory("snapshot-legacy-date").toFile()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            block(File(directory, "snapshot.zip"))
        } finally {
            TimeZone.setDefault(oldZone)
            directory.deleteRecursively()
        }
    }

    private fun identity() = SnapshotIdentity(UUID.randomUUID(), UUID.randomUUID(), "Dev", at.toEpochMilli())

    private fun graph(date: LocalDate): Graph {
        val territory = UUID.randomUUID()
        val observer = UUID.randomUUID()
        val point = UUID.randomUUID()
        return Graph(
            territories = listOf(TerritoryEntity(territory, "T", "Territory", "R", "D", at, at)),
            observers = listOf(ObserverEntity(observer, "O", "Last", "First", null, null, at, at)),
            points = listOf(ObservationPointEntity(date, point, territory, observer, date.year, 1, null, null,
                56.1, 42.7, null, null, null, at, null, null)),
            bees = emptyList(), cycles = emptyList(),
            weather = listOf(ObservationPointWeatherEntity(point, WeatherStatus.PENDING, null, null, null, null, null, null)),
        )
    }
}
