package org.beesearch.app.data.gpx

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.GpxDeletionStore
import org.beesearch.app.data.local.room.GpxPersistenceSchema
import org.beesearch.app.data.local.room.GpxPersistenceStore
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated deletion qualification; no owner-device database or files are used. */
@RunWith(AndroidJUnit4::class)
class GpxDeletionDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BeeSearchDatabase
    private lateinit var root: File
    private val territory = UUID(0, 9910)
    private val otherTerritory = UUID(0, 9911)
    private val observer = UUID(0, 9912)
    private val researchPoint = UUID(0, 9913)
    private val researchBee = UUID(0, 9914)
    private val researchFlight = UUID(0, 9915)
    private val hidden = mutableListOf<Pair<UUID, UUID>>()

    @Before
    fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .addCallback(GpxPersistenceSchema.callback).build()
        val now = Instant.parse("2026-10-19T00:00:00Z")
        db.territoryDao().insert(TerritoryEntity(territory, "DEL", "Delete", "R", "D", now, now))
        db.territoryDao().insert(TerritoryEntity(otherTerritory, "DEL2", "Delete 2", "R", "D", now, now))
        db.observerDao().insert(ObserverEntity(observer, "DEL-O", "Delete", "Test", null, null, now, now))
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO observation_points VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf("2026-10-19", researchPoint.toString(), territory.toString(), observer.toString(), 2026, 1, "BEES_FOUND", "P1", 56.1, 42.7, 56.1, 42.7, 4.0, 1_760_000_000_000L, null, null, "protected research"),
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO bees VALUES(?,?,?,?,?,?)",
            arrayOf(researchBee.toString(), researchPoint.toString(), "WHITE", "THORAX", 1_760_000_000_000L, null),
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO flight_cycles VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(researchFlight.toString(), researchBee.toString(), 1, 1_760_000_000_000L, null, null, 0, 1, 1, 1_760_000_000_000L, 1_760_000_000_000L),
        )
        root = File(context.cacheDir, "gpx-delete-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun deletesVisibleAndHiddenImportsByExactUuidAndLeavesOtherImportAndResearchTerritory() = runBlocking {
        val visible = ready(1, territory)
        val hiddenImport = ready(2, territory)
        val other = ready(3, territory)
        val before = GpxPersistenceStore(db).counts(territory, "READY")
        val researchBefore = researchRows()

        val deletion = deletion()
        assertFalse(deletion.delete(territory, visible.importId).cleanupPending)
        assertFalse(deletion.delete(territory, hiddenImport.importId).cleanupPending)

        assertNull(GpxPersistenceStore(db).readReady(visible.importId))
        assertNull(GpxPersistenceStore(db).readReady(hiddenImport.importId))
        assertNotNull(GpxPersistenceStore(db).readReady(other.importId))
        assertEquals(before.imports - 2, GpxPersistenceStore(db).counts(territory, "READY").imports)
        assertEquals(researchBefore, researchRows())
        assertEquals(listOf(visible.importId, hiddenImport.importId).toSet(), hidden.map { it.second }.toSet())
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test
    fun duplicateBytesRemainIndependentAndNewRequestCanReimportAfterDeletion() = runBlocking {
        val first = ready(10, territory)
        val second = ready(11, territory, confirmRepeat = true)
        val originalFirst = first.originalId
        val originalSecond = second.originalId
        assertFalse(deletion().delete(territory, first.importId).cleanupPending)
        assertNotNull(GpxPersistenceStore(db).readReady(second.importId))
        assertTrue(GpxOriginalStore(root).listFinalCandidates().contains(originalSecond))
        assertFalse(GpxOriginalStore(root).listFinalCandidates().contains(originalFirst))

        val replay = request(10, territory)
        val replayResult = coordinator().prepare(replay, source(), { true }, { true })
        assertTrue(replayResult is GpxPreparationResult.Failed)
        val fresh = request(12, territory)
        coordinator().prepare(fresh, source(), { true }, { true })
        val result = coordinator().complete(fresh, confirmRepeatedOriginal = true, isCapturedRequestCurrent = { true })
        assertTrue(result is GpxPreparationResult.Ready)
    }

    @Test
    fun wrongTerritoryAndRepeatedDeleteAreBoundAndIdempotent() = runBlocking {
        val item = ready(20, territory)
        val deletion = deletion()
        var rejected = false
        try { deletion.delete(otherTerritory, item.importId) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertNotNull(GpxPersistenceStore(db).readReady(item.importId))
        assertFalse(deletion.delete(territory, item.importId).cleanupPending)
        assertFalse(deletion.delete(territory, item.importId).cleanupPending)
        assertNull(GpxPersistenceStore(db).readReady(item.importId))
    }

    @Test
    fun beforeRoomFailureLeavesReadyAndAfterCommitFailureLeavesRetryableTombstone() = runBlocking {
        val before = ready(30, territory)
        var beforeThrown = false
        try {
            GpxDeletion(GpxDeletionStore(db), GpxOriginalStore(root), ::hide) { point ->
                if (point == "BEFORE_ROOM") error("before room")
            }.delete(territory, before.importId)
        } catch (_: IllegalStateException) { beforeThrown = true }
        assertTrue(beforeThrown)
        assertNotNull(GpxPersistenceStore(db).readReady(before.importId))
        assertNull(db.gpxDao().deletion(before.importId))

        val beforeCommit = ready(32, territory)
        var beforeCommitThrown = false
        try {
            GpxDeletion(
                GpxDeletionStore(db) { point -> if (point == "BEFORE_COMMIT") error("before commit") },
                GpxOriginalStore(root), ::hide,
            ).delete(territory, beforeCommit.importId)
        } catch (_: IllegalStateException) { beforeCommitThrown = true }
        assertTrue(beforeCommitThrown)
        assertNotNull(GpxPersistenceStore(db).readReady(beforeCommit.importId))
        assertNull(db.gpxDao().deletion(beforeCommit.importId))

        val after = ready(31, territory)
        val result = GpxDeletion(GpxDeletionStore(db), GpxOriginalStore(root), ::hide) { point ->
            if (point == "AFTER_COMMIT") error("after commit")
        }.delete(territory, after.importId)
        assertTrue(result.cleanupPending)
        assertNull(GpxPersistenceStore(db).readReady(after.importId))
        assertNotNull(db.gpxDao().deletion(after.importId))
        deletion().reconcile()
        assertNotNull(db.gpxDao().deletion(after.importId))
        assertTrue(requireNotNull(db.gpxDao().deletion(after.importId)).filesCleaned)
        assertTrue(requireNotNull(db.gpxDao().deletion(after.importId)).settingsCleaned)
    }

    @Test
    fun missingOriginalIsCleanedAsPendingDebtAndForeignOwnerFailsClosed() = runBlocking {
        val missing = ready(40, territory)
        val missingOriginal = missing.originalId
        File(root, "gpx-originals/${missing.originalId}/original.gpx").delete()
        val missingResult = deletion().delete(territory, missing.importId)
        assertFalse(missingResult.cleanupPending)
        assertNotNull(db.gpxDao().deletion(missing.importId))

        val unsafe = ready(41, territory)
        val owner = File(root, "gpx-originals/${unsafe.originalId}/owner")
        owner.writeText("foreign")
        var rejected = false
        try { deletion().delete(territory, unsafe.importId) } catch (_: Exception) { rejected = true }
        assertTrue(rejected)
        assertNotNull(GpxPersistenceStore(db).readReady(unsafe.importId))
        assertNull(db.gpxDao().deletion(unsafe.importId))
        assertTrue(owner.exists())
    }

    @Test
    fun unlinkCheckpointAndSettingsFailureRemainRetryableWithoutBlockingOtherImport() = runBlocking {
        val fileFault = ready(42, territory)
        val faultyOriginals = GpxOriginalStore(root, checkpoint = {}, deletionCheckpoint = { point ->
            if (point == "UNLINK_original.gpx") error("unlink fault")
        })
        val pending = GpxDeletion(GpxDeletionStore(db), faultyOriginals, ::hide).delete(territory, fileFault.importId)
        assertTrue(pending.cleanupPending)
        assertNull(GpxPersistenceStore(db).readReady(fileFault.importId))
        deletion().reconcile()
        assertNotNull(db.gpxDao().deletion(fileFault.importId))
        assertTrue(requireNotNull(db.gpxDao().deletion(fileFault.importId)).filesCleaned)
        assertTrue(requireNotNull(db.gpxDao().deletion(fileFault.importId)).settingsCleaned)

        val settingsFault = ready(43, territory)
        var failSettings = true
        val result = GpxDeletion(GpxDeletionStore(db), GpxOriginalStore(root), { _, _ ->
            if (failSettings) error("settings fault")
        }).delete(territory, settingsFault.importId)
        assertTrue(result.cleanupPending)
        assertNotNull(db.gpxDao().deletion(settingsFault.importId))
        failSettings = false
        deletion().reconcile()
        assertNotNull(db.gpxDao().deletion(settingsFault.importId))
        assertTrue(requireNotNull(db.gpxDao().deletion(settingsFault.importId)).filesCleaned)
        assertTrue(requireNotNull(db.gpxDao().deletion(settingsFault.importId)).settingsCleaned)

        val sentinel = File(root, "external-sentinel.gpx").apply { writeText("keep") }
        listOf("UNLINK_original.gpx", "UNLINK_verified", "UNLINK_owner", "DIRECTORY_REMOVED", "ORIGINALS_SYNCED", "BEFORE_FILES_ACK", "BEFORE_SETTINGS_ACK")
            .forEachIndexed { index, stage ->
                val item = ready(60 + index, territory)
                val other = ready(70 + index, territory)
                val faulty = GpxOriginalStore(root, checkpoint = {}, deletionCheckpoint = { point ->
                    if (point == stage && (point.startsWith("UNLINK_") || point == "DIRECTORY_REMOVED" || point == "ORIGINALS_SYNCED")) error("filesystem checkpoint $stage")
                })
                val faultyDeletion = GpxDeletion(GpxDeletionStore(db), faulty, ::hide) { point ->
                    if (point == stage) error("deletion checkpoint $stage")
                }
                assertTrue(faultyDeletion.delete(territory, item.importId).cleanupPending)
                val persistence = GpxPersistenceStore(db)
                val runtime = GpxRuntime(
                    persistence,
                    createCoordinator = { GpxImportCoordinator(persistence, GpxOriginalStore(root)) },
                    createDeletion = { faultyDeletion },
                )
                assertTrue(runtime.listReady(territory).any { it.id == other.importId })
                assertNull(runtime.readReady(territory, item.importId))
                deletion().reconcile()
                val tombstone = requireNotNull(db.gpxDao().deletion(item.importId))
                assertTrue(tombstone.filesCleaned)
                assertTrue(tombstone.settingsCleaned)
            }
        assertTrue(sentinel.exists())
    }

    @Test
    fun residualReadyJobFailsBeforeCommitWithoutDeletingImport() = runBlocking {
        val item = ready(50, territory)
        val row = requireNotNull(db.gpxDao().importById(item.importId))
        db.gpxDao().insertJob(
            org.beesearch.app.data.local.room.GpxImportJobEntity(
                item.requestUuid, territory, observer, "READY", "CONTINUE",
                row.originalObjectId, row.originalSha256, row.originalByteLength, item.importId,
            ),
        )
        var rejected = false
        try { deletion().delete(territory, item.importId) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertNotNull(GpxPersistenceStore(db).readReady(item.importId))
        assertNotNull(db.gpxDao().jobByRequest(item.requestUuid))
    }

    @Test
    fun afterCommitDebtIsRecoveredAfterClosingAndReopeningDiskDatabase() = runBlocking {
        val name = "gpx-delete-restart-${UUID.randomUUID()}"
        val diskRoot = File(context.cacheDir, name).apply { mkdirs() }
        var diskDb: BeeSearchDatabase? = null
        try {
            diskDb = Room.databaseBuilder(context, BeeSearchDatabase::class.java, "$name.db")
                .addCallback(GpxPersistenceSchema.callback).build()
            val now = Instant.parse("2026-10-19T00:00:00Z")
            diskDb.territoryDao().insert(TerritoryEntity(territory, "RDEL", "Restart", "R", "D", now, now))
            diskDb.observerDao().insert(ObserverEntity(observer, "RDEL-O", "Restart", "Test", null, null, now, now))
            val persistence = GpxPersistenceStore(diskDb)
            val request = request(90, territory)
            val coordinator = GpxImportCoordinator(persistence, GpxOriginalStore(diskRoot))
            coordinator.prepare(request, source(), { true }, { true })
            val ready = coordinator.complete(request, confirmRepeatedOriginal = true, isCapturedRequestCurrent = { true }) as GpxPreparationResult.Ready
            val faulty = GpxDeletion(GpxDeletionStore(diskDb), GpxOriginalStore(diskRoot), ::hide) { point ->
                if (point == "AFTER_COMMIT") error("restart fault")
            }
            assertTrue(faulty.delete(territory, ready.importId).cleanupPending)
            diskDb.close(); diskDb = null

            val reopened = Room.databaseBuilder(context, BeeSearchDatabase::class.java, "$name.db")
                .addCallback(GpxPersistenceSchema.callback).build()
            try {
                val reopenedPersistence = GpxPersistenceStore(reopened)
                val runtime = GpxRuntime(
                    reopenedPersistence,
                    createCoordinator = { GpxImportCoordinator(reopenedPersistence, GpxOriginalStore(diskRoot)) },
                    createDeletion = { GpxDeletion(GpxDeletionStore(reopened), GpxOriginalStore(diskRoot), ::hide) },
                )
                assertTrue(runtime.reconcile().completed)
                assertNull(reopenedPersistence.readReady(ready.importId))
                val tombstone = requireNotNull(reopened.gpxDao().deletion(ready.importId))
                assertTrue(tombstone.filesCleaned)
                assertTrue(tombstone.settingsCleaned)
            } finally {
                reopened.close()
            }
        } finally {
            diskDb?.close()
            context.deleteDatabase("$name.db")
            diskRoot.deleteRecursively()
        }
    }

    private fun deletion() = GpxDeletion(
        GpxDeletionStore(db), GpxOriginalStore(root), ::hide,
    )

    private fun hide(territoryId: UUID, importId: UUID) {
        hidden += territoryId to importId
    }

    private fun researchRows(): List<String> = buildList {
        db.openHelper.writableDatabase.query(
            "SELECT bees.id, bees.observation_point_id, flight_cycles.sequence_number FROM bees JOIN flight_cycles ON flight_cycles.bee_id = bees.id WHERE bees.id = ?",
            arrayOf(researchBee.toString()),
        ).use { while (it.moveToNext()) add((0 until it.columnCount).joinToString("|") { n -> it.getString(n) }) }
    }

    private suspend fun ready(seed: Int, territoryId: UUID, confirmRepeat: Boolean = true): ReadyIds {
        val request = request(seed, territoryId)
        coordinator().prepare(request, source(), { true }, { true })
        val result = coordinator().complete(request, confirmRepeatedOriginal = confirmRepeat, isCapturedRequestCurrent = { true })
        val ready = result as GpxPreparationResult.Ready
        val row = requireNotNull(db.gpxDao().importById(ready.importId))
        return ReadyIds(ready.importId, ready.originalId, ready.requestUuid)
    }

    private fun coordinator() = GpxImportCoordinator(GpxPersistenceStore(db), GpxOriginalStore(root))

    private fun request(seed: Int, territoryId: UUID) = GpxImportRequest(
        UUID(0, seed.toLong()), territoryId, observer, "delete-$seed.gpx", 1_760_000_000_000L + seed,
    )

    private fun source(): () -> ByteArrayInputStream = { ByteArrayInputStream(validGpx()) }

    private fun validGpx() = "<?xml version=\"1.0\"?><gpx version=\"1.1\" creator=\"fixture\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg><trkpt lat=\"0\" lon=\"0\"/><trkpt lat=\"1\" lon=\"1\"/></trkseg></trk></gpx>".toByteArray()

    private data class ReadyIds(val importId: UUID, val originalId: UUID, val requestUuid: UUID)
}
