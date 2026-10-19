package org.beesearch.app.data.gpx

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.GpxPersistenceSchema
import org.beesearch.app.data.local.room.GpxPersistenceStore
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** READY catalog reads project metadata only and never load the stored graph. */
@RunWith(AndroidJUnit4::class)
class GpxReadyCatalogDeviceTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var originalRoot: File
    private val territory = UUID(0, 11_001)
    private val otherTerritory = UUID(0, 11_002)

    @Before
    fun setUp(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), BeeSearchDatabase::class.java,
        ).addCallback(GpxPersistenceSchema.callback).build()
        val now = Instant.parse("2026-10-19T00:00:00Z")
        database.territoryDao().insert(TerritoryEntity(territory, "CATALOG", "Catalog", "R", "D", now, now))
        database.territoryDao().insert(TerritoryEntity(otherTerritory, "OTHER", "Other", "R", "D", now, now))
        database.observerDao().insert(ObserverEntity(UUID(0, 11_003), "CATALOG", "Observer", "Test", null, null, now, now))
        originalRoot = File(
            ApplicationProvider.getApplicationContext<Context>().filesDir,
            "gpx-catalog-${UUID.randomUUID()}",
        ).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        originalRoot.deleteRecursively()
    }

    @Test
    fun storeCatalogIsReadyOnlyTerritoryScopedAndDeterministicallyOrdered(): Unit = runBlocking {
        val newest = UUID(0, 1)
        val tieById = UUID(0, 2)
        val older = UUID(0, 3)
        insertReady(newest, territory, "Newest", 300)
        insertReady(tieById, territory, "Tie", 200)
        insertReady(older, territory, "Older", 100)
        insertReady(UUID(0, 4), otherTerritory, "Other", 400)
        insertPending(UUID(0, 5), territory, "Pending", 500)

        assertEquals(
            listOf(newest, tieById, older),
            GpxPersistenceStore(database).listReady(territory).map { it.id },
        )
    }

    @Test
    fun catalogReturnsMetadataForReadyRowWithNoReadableGeometry(): Unit = runBlocking {
        val id = UUID(0, 20)
        insertReady(id, territory, null, 700)
        insertCorruptGeometry(id)

        val metadata = GpxPersistenceStore(database).listReady(territory).single()

        assertEquals(id, metadata.id)
        assertEquals(null, metadata.displayName)
        assertEquals(700L, metadata.importedAtEpochMs)
    }

    @Test
    fun runtimeCatalogUsesReconciliationGateAndSameTerritoryProjection(): Unit = runBlocking {
        val id = UUID(0, 30)
        insertReady(id, territory, "Visible", 800)
        insertReady(UUID(0, 31), otherTerritory, "Foreign", 900)
        val store = GpxPersistenceStore(database)
        val runtime = GpxRuntime(
            GpxImportCoordinator(store, GpxOriginalStore(originalRoot)), store,
        )

        assertEquals(listOf(id), runtime.listReady(territory).map { it.id })
    }

    private fun insertPending(id: UUID, territoryId: UUID, name: String, importedAt: Long) {
        insert(id, territoryId, name, importedAt, "PENDING", null)
    }

    private fun insertReady(id: UUID, territoryId: UUID, name: String?, importedAt: Long) {
        insert(id, territoryId, name, importedAt, "READY", id)
    }

    private fun insert(id: UUID, territoryId: UUID, name: String?, importedAt: Long, state: String, request: UUID?) {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO gpx_imports(
                id, territory_id, original_object_id, original_sha256, original_byte_length,
                display_name, gpx_version, parser_profile_version, imported_at_epoch_ms, creator,
                publication_state, request_uuid, unsupported_content_count, unsupported_time_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
            """.trimIndent(),
            arrayOf(
                id.toString(), territoryId.toString(), UUID(0, id.leastSignificantBits + 100).toString(),
                "a".repeat(64), 1L, name, "1.1", "GPX_1_1_STAGE_1", importedAt, "catalog-test",
                state, request?.toString(),
            ),
        )
    }

    private fun insertCorruptGeometry(importId: UUID) {
        val trackId = UUID(0, 21)
        val segmentId = UUID(0, 22)
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO gpx_source_tracks(id, import_id, ordinal) VALUES (?, ?, 0)",
            arrayOf(trackId.toString(), importId.toString()),
        )
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO gpx_track_segments(id, track_id, ordinal) VALUES (?, ?, 0)",
            arrayOf(segmentId.toString(), trackId.toString()),
        )
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO gpx_track_samples(segment_id, ordinal, latitude, longitude, time_status) VALUES (?, 0, ?, ?, 'MISSING')",
            arrayOf(segmentId.toString(), "not-a-decimal", "0"),
        )
    }
}
