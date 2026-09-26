package org.beesearch.app.data.backup

import org.beesearch.app.addBee
import org.beesearch.app.startInitialGroupRelease

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.repository.RoomObservationRepository
import org.beesearch.app.data.repository.RoomObserverRepository
import org.beesearch.app.data.repository.RoomTerritoryRepository
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.NewObservationPoint
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BackupDocumentExporterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: BeeSearchDatabase
    private lateinit var observationRepository: RoomObservationRepository
    private lateinit var backupService: BackupService

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-09-10T08:00:00Z"), ZoneOffset.UTC)
        val territoryRepository = RoomTerritoryRepository(database, database.territoryDao(), clock)
        val observerRepository = RoomObserverRepository(database.observerDao(), clock)
        observationRepository = RoomObservationRepository(
            database,
            database.territoryDao(),
            database.observationPointDao(),
            database.observerDao(),
            database.beeDao(),
            database.flightCycleDao(),
            clock,
            { ZoneOffset.UTC },
        )
        val territory = territoryRepository.createTerritory("T01", "Территория", "Регион", "Район")
        val observer = observerRepository.createObserver("O01", "Иванов", "Иван", null, null)
        val point = observationRepository.createObservationPoint(
            NewObservationPoint(territory.id, observer.id, latitude = 56.2, longitude = 42.7),
        )
        observationRepository.addBee(point.id, "Красная", MarkPosition.ABDOMEN)
        observationRepository.startInitialGroupRelease(point.id)
        backupService = BackupService(database, EmptyPortableSettingsStore, clock, "test")
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun safAdapterWritesExistingLogicalArchiveWithoutDeletingResearchData() = runBlocking {
        val destination = ByteArrayOutputStream()
        val exporter = SafBackupDocumentExporter(backupService, context.cacheDir) { destination }

        exporter.export(Uri.EMPTY)

        assertTrue(destination.size() > 0)
        val archive = File.createTempFile("exported-", ".zip", context.cacheDir)
        try {
            archive.writeBytes(destination.toByteArray())
            backupService.validate(archive)
        } finally {
            archive.delete()
        }
        assertEquals(1, observationRepository.getObservationDataCounts().observationPoints)
        assertEquals(1, observationRepository.getObservationDataCounts().bees)
        assertEquals(1, observationRepository.getObservationDataCounts().flightCycles)
    }

    @Test
    fun destinationFailureIsReportedAndDoesNotDeleteResearchData() = runBlocking {
        val exporter = SafBackupDocumentExporter(backupService, context.cacheDir) { null }

        assertThrows(IOException::class.java) { runBlocking { exporter.export(Uri.EMPTY) } }

        assertEquals(1, observationRepository.getObservationDataCounts().observationPoints)
        assertEquals(1, observationRepository.getObservationDataCounts().bees)
        assertEquals(1, observationRepository.getObservationDataCounts().flightCycles)
    }

    private object EmptyPortableSettingsStore : PortableSettingsStore {
        override suspend fun snapshot() = PortableSettingsSnapshot(null, null, emptyMap())
        override suspend fun replace(snapshot: PortableSettingsSnapshot) = Unit
    }
}
