package org.beesearch.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.beesearch.app.data.backupsnapshot.SnapshotDomainCodec
import org.beesearch.app.data.backupsnapshot.SnapshotJson
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.ObservationPointEntity
import org.beesearch.app.data.local.room.ObservationPointWeatherEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.domain.model.NewObservationPoint
import org.beesearch.app.domain.model.WeatherStatus
import org.beesearch.app.domain.model.legacyObservationDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.beesearch.app.domain.backup.BackupDomainInvariantViolation
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class TemporalLegacyCompatibilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BeeSearchDatabase::class.java,
    )

    @Test
    fun legacyDateRuleAgreesAcrossBackupAndSnapshotMaterialization() = runBlocking {
        val oldZone = TimeZone.getDefault()
        val fixedZone = TimeZone.getTimeZone("Pacific/Kiritimati")
        TimeZone.setDefault(fixedZone)
        val createdAt = Instant.parse("2026-08-20T10:00:00Z")
        val expected = legacyObservationDate(createdAt, fixedZone.toZoneId())
        val source = database()
        val target = database()
        val archive = File(context.cacheDir, "${UUID.randomUUID()}-temporal-v6.zip")
        try {
            val fixture = fixture(createdAt, expected)
            source.backupDao().insertTerritories(listOf(fixture.territory))
            source.backupDao().insertObservers(listOf(fixture.observer))
            val repository = RoomObservationRepository(
                source,
                source.territoryDao(), source.observationPointDao(), source.observerDao(),
                source.beeDao(), source.flightCycleDao(), java.time.Clock.fixed(createdAt, fixedZone.toZoneId()),
                observationZoneIdProvider = { fixedZone.toZoneId() },
            )
            val created = repository.createObservationPoint(
                NewObservationPoint(
                    territoryId = fixture.territory.id,
                    observerId = fixture.observer.id,
                    code = "P1",
                    latitude = 56.1,
                    longitude = 42.7,
                    id = fixture.point.id,
                ),
            )
            val persisted = source.backupDao().observationPoints().single()
            assertEquals(expected, created.observationDate)
            assertEquals(expected, persisted.observationDate)

            BackupService(source, EmptySettings, ClockForTest, "test").export(archive)
            val pointRecord = zipLines(archive).getValue("research/observation-points.json").joinToString("\n")
            assertFalse(pointRecord.contains("observationDate"))
            BackupService(target, EmptySettings, ClockForTest, "test").restore(archive)
            assertEquals(expected, target.backupDao().observationPoints().single().observationDate)

            val graph = Graph(
                territories = listOf(fixture.territory),
                observers = listOf(fixture.observer),
                points = listOf(persisted),
                bees = emptyList(),
                cycles = emptyList(),
                weather = listOf(fixture.weather),
            )
            val entries = SnapshotDomainCodec.encode(graph, PortableSettingsSnapshot(null, null, emptyMap()))
            val snapshotPointRecord = entries.records.getValue("data/observation-points.jsonl").single()
            assertFalse(snapshotPointRecord.contains("observationDate"))
            val parsedRows = entries.records
                .filterKeys { it != "settings/map-coverage.jsonl" }
                .mapValues { (_, rows) -> rows.map { SnapshotJson.parse(it.toByteArray(), false).jsonObject } }
            val materialized = snapshotGraphFromRows(parsedRows).points.single()
            assertEquals(expected, materialized.observationDate)
            assertEquals(expected, legacyObservationDate(createdAt, fixedZone.toZoneId()))

            val migrationDbName = "temporal-legacy-${UUID.randomUUID()}"
            migrationHelper.createDatabase(migrationDbName, 11).apply {
                execSQL("INSERT INTO territories VALUES (?, 'T', 'Territory', 'R', 'D', ?, ?)", arrayOf<Any>(fixture.territory.id.toString(), createdAt.toEpochMilli(), createdAt.toEpochMilli()))
                execSQL("INSERT INTO observers VALUES (?, 'O', 'Last', 'First', NULL, NULL, ?, ?)", arrayOf<Any>(fixture.observer.id.toString(), createdAt.toEpochMilli(), createdAt.toEpochMilli()))
                execSQL(
                    "INSERT INTO observation_points (id, territory_id, observer_id, observation_year, point_number, bee_presence_result, code, latitude, longitude, gps_latitude, gps_longitude, gps_accuracy_m, created_at, initial_group_release_at, completed_at, description) VALUES (?, ?, ?, 2026, 1, NULL, 'P1', 56.1, 42.7, NULL, NULL, NULL, ?, NULL, NULL, NULL)",
                    arrayOf<Any>(fixture.point.id.toString(), fixture.territory.id.toString(), fixture.observer.id.toString(), createdAt.toEpochMilli()),
                )
                close()
            }
            val migrated = migrationHelper.runMigrationsAndValidate(migrationDbName, 12, true, org.beesearch.app.data.local.room.MIGRATION_11_12)
            migrated.query("SELECT observation_date FROM observation_points WHERE id = ?", arrayOf(fixture.point.id.toString())).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(expected.toString(), cursor.getString(0))
            }
            migrated.close()
        } finally {
            source.close()
            target.close()
            archive.delete()
            TimeZone.setDefault(oldZone)
        }
    }

    @Test
    fun snapshotRowsKeepV1ObservationPointWireInventoryClosed() {
        val createdAt = Instant.parse("2026-02-28T12:00:00Z")
        val fixture = fixture(createdAt, legacyObservationDate(createdAt))
        val entries = SnapshotDomainCodec.encode(
            Graph(
                territories = listOf(fixture.territory),
                observers = listOf(fixture.observer),
                points = listOf(fixture.point),
                bees = emptyList(),
                cycles = emptyList(),
                weather = listOf(fixture.weather),
            ),
            PortableSettingsSnapshot(null, null, emptyMap()),
        )
        val point = entries.records.getValue("data/observation-points.jsonl").single()
        assertTrue(point.contains("createdAt"))
        assertFalse(point.contains("observationDate"))
    }

    @Test
    fun divergentDateRejectsBackupBeforeCreatingOrReplacingOutput() = runBlocking {
        val oldZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val source = database()
        val output = File(context.cacheDir, "${UUID.randomUUID()}-rejected-v6.zip")
        try {
            val createdAt = Instant.parse("2026-08-20T12:00:00Z")
            val fixture = fixture(createdAt, LocalDate.of(2026, 8, 19))
            source.backupDao().insertTerritories(listOf(fixture.territory))
            source.backupDao().insertObservers(listOf(fixture.observer))
            source.backupDao().insertObservationPoints(listOf(fixture.point))
            val service = BackupService(source, EmptySettings, ClockForTest, "test")
            assertThrows(BackupDomainInvariantViolation::class.java) { runBlocking { service.export(output) } }
            assertFalse(output.exists())
            val existing = "existing output must stay untouched".toByteArray()
            output.writeBytes(existing)
            assertThrows(BackupDomainInvariantViolation::class.java) { runBlocking { service.export(output) } }
            assertTrue(existing.contentEquals(output.readBytes()))
        } finally {
            source.close()
            output.delete()
            TimeZone.setDefault(oldZone)
        }
    }

    @Test
    fun timezoneChangeRejectsBackupNotRepresentableInCurrentLegacyConvention() = runBlocking {
        val oldZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val source = database()
        val output = File(context.cacheDir, "${UUID.randomUUID()}-timezone-v6.zip")
        try {
            val createdAt = Instant.parse("2026-08-20T23:30:00Z")
            val fixture = fixture(createdAt, legacyObservationDate(createdAt))
            source.backupDao().insertTerritories(listOf(fixture.territory))
            source.backupDao().insertObservers(listOf(fixture.observer))
            source.backupDao().insertObservationPoints(listOf(fixture.point))
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
            assertEquals(LocalDate.of(2026, 8, 21), legacyObservationDate(createdAt))
            assertThrows(BackupDomainInvariantViolation::class.java) {
                runBlocking { BackupService(source, EmptySettings, ClockForTest, "test").export(output) }
            }
            assertFalse(output.exists())
        } finally {
            source.close()
            output.delete()
            TimeZone.setDefault(oldZone)
        }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun zipLines(file: File): Map<String, List<String>> = buildMap {
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8).lineSequence().filter(String::isNotBlank).toList())
            }
        }
    }

    private fun fixture(createdAt: Instant, date: LocalDate): Fixture {
        val territoryId = UUID.randomUUID()
        val observerId = UUID.randomUUID()
        val pointId = UUID.randomUUID()
        return Fixture(
            territory = TerritoryEntity(territoryId, "T-${territoryId.toString().take(8)}", "Territory", "R", "D", createdAt, createdAt),
            observer = ObserverEntity(observerId, "O-${observerId.toString().take(8)}", "Last", "First", null, null, createdAt, createdAt),
            point = ObservationPointEntity(date, pointId, territoryId, observerId, date.year, 1, null, "P1", 56.1, 42.7, null, null, null, createdAt, null, null, null),
            weather = ObservationPointWeatherEntity(pointId, WeatherStatus.PENDING, null, null, null, null, null, null),
        )
    }

    private data class Fixture(
        val territory: TerritoryEntity,
        val observer: ObserverEntity,
        val point: ObservationPointEntity,
        val weather: ObservationPointWeatherEntity,
    )

    private object EmptySettings : PortableSettingsStore {
        override suspend fun snapshot() = PortableSettingsSnapshot(null, null, emptyMap())
        override suspend fun replace(snapshot: PortableSettingsSnapshot) = Unit
    }

    private companion object {
        val ClockForTest = java.time.Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC)
    }
}
