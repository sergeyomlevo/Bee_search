package org.beesearch.app.data.gpx

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
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

/** Runtime reconciliation and first-access READY gate coverage. */
@RunWith(AndroidJUnit4::class)
class GpxRuntimeDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BeeSearchDatabase
    private lateinit var root: File
    private val territory = UUID(0, 9800)
    private val otherTerritory = UUID(0, 9801)
    private val observer = UUID(0, 9802)

    @Before
    fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .addCallback(GpxPersistenceSchema.callback).build()
        val now = Instant.parse("2026-10-17T00:00:00Z")
        db.territoryDao().insert(TerritoryEntity(territory, "RUNTIME", "Runtime", "R", "D", now, now))
        db.territoryDao().insert(TerritoryEntity(otherTerritory, "OTHER", "Other", "R", "D", now, now))
        db.observerDao().insert(ObserverEntity(observer, "RUNTIME", "Observer", "Test", null, null, now, now))
        root = File(context.filesDir, "gpx-runtime-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun emptyReconciliationCompletesAndIsIdempotent(): Unit = runBlocking {
        val runtime = runtime()
        assertTrue(runtime.reconcile().completed)
        assertEquals(runtime.reconcile(), runtime.reconcile())
        assertTrue(db.gpxDao().jobs().isEmpty())
    }

    @Test
    fun preparedNonRepeatIsRecoveredWithStableIdsAndConcurrentCallsSharePass(): Unit = runBlocking {
        val request = request(1)
        val prepared = interruptedAtFilePublished(request)
        val runtime = runtime()
        val results = listOf(async { runtime.reconcile() }, async { runtime.reconcile() }).awaitAll()
        assertEquals(results[0], results[1])
        val ready = results[0].operations.single() as GpxPreparationResult.Ready
        assertEquals(prepared.importId, ready.importId)
        assertEquals(prepared.originalId, ready.originalId)
        assertNotNull(GpxPersistenceStore(db).readReady(prepared.importId))
        assertTrue(db.gpxDao().jobs().isEmpty())
    }

    @Test
    fun committedReadyRemainsReadableAndIsNotRemovedByReconciliation(): Unit = runBlocking {
        val request = request(2)
        val prepared = coordinator().prepare(request, source(), { true }, { true }) as GpxPreparationResult.Prepared
        val ready = coordinator().complete(request, isCapturedRequestCurrent = { true }) as GpxPreparationResult.Ready
        val runtime = runtime()
        assertTrue(runtime.reconcile().completed)
        assertNotNull(runtime.readReady(territory, ready.importId))
        assertNotNull(GpxOriginalStore(root).listFinalCandidates().single { it == prepared.originalId })
    }

    @Test
    fun duplicateRecoveryWithoutConfirmationIsRejectedWithoutReadyResult(): Unit = runBlocking {
        val first = request(3)
        coordinator().prepare(first, source(), { true }, { true })
        coordinator().complete(first, isCapturedRequestCurrent = { true })
        val duplicate = request(4)
        val prepared = coordinator().prepare(duplicate, source(), { true }, { true }) as GpxPreparationResult.Prepared
        val result = runtime().reconcile()
        assertFalse(result.completed)
        assertTrue(result.operations.single() is GpxPreparationResult.Failed)
        assertNull(GpxPersistenceStore(db).readReady(prepared.importId))
        assertTrue(db.gpxDao().jobs().isEmpty())
        assertFalse(GpxOriginalStore(root).listFinalCandidates().contains(prepared.originalId))
    }

    @Test
    fun pendingGraphStaysInvisibleAfterFirstAccessGateIsSettled(): Unit = runBlocking {
        val runtime = runtime()
        assertTrue(runtime.reconcile().completed)
        val prepared = coordinator().prepare(request(5), source(), { true }, { true }) as GpxPreparationResult.Prepared
        assertNull(runtime.readReady(territory, prepared.importId))
        assertNotNull(GpxPersistenceStore(db).readPending(prepared.importId))
    }

    @Test
    fun readyAccessIsRestrictedToCapturedTerritory(): Unit = runBlocking {
        val request = request(6)
        val prepared = coordinator().prepare(request, source(), { true }, { true }) as GpxPreparationResult.Prepared
        coordinator().complete(request, isCapturedRequestCurrent = { true })
        val runtime = runtime()
        assertNull(runtime.readReady(otherTerritory, prepared.importId))
        assertNotNull(runtime.readReady(territory, prepared.importId))
    }

    @Test
    fun failedReconciliationDoesNotDeleteJournalAndCanBeRetried(): Unit = runBlocking {
        val prepared = interruptedAtFilePublished(request(7))
        val trigger = GpxPersistenceSchema.triggers.keys.first()
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER $trigger")
        val runtime = runtime()
        val failed = runtime.reconcile()
        assertFalse(failed.completed)
        assertNotNull(db.gpxDao().jobByRequest(prepared.requestUuid))
        assertTrue(GpxOriginalStore(root).listFinalCandidates().contains(prepared.originalId))
        db.openHelper.writableDatabase.execSQL(requireNotNull(GpxPersistenceSchema.triggers[trigger]))
        val retried = runtime.reconcile()
        assertTrue(retried.completed)
        assertNotNull(GpxPersistenceStore(db).readReady(prepared.importId))
    }

    @Test
    fun firstAccessFromMainDefersConstructionAndRoomWorkToIo(): Unit = runBlocking {
        val persistence = GpxPersistenceStore(db)
        var constructed = false
        val runtime = GpxRuntime(persistence) {
            assertFalse(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
            constructed = true
            coordinator(persistence)
        }
        assertFalse(constructed)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            assertNull(runtime.readReady(territory, UUID.randomUUID()))
        }
        assertTrue(constructed)
    }

    @Test
    fun applicationContainerStartupCanBeJoinedAndReadyAccessIsAvailable(): Unit = runBlocking {
        val application = context.applicationContext as org.beesearch.app.BeeSearchApplication
        val job = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            application.container.startGpxReconciliation()
        }
        job.join()
        assertNull(application.container.gpxRuntime.readReady(UUID.randomUUID(), UUID.randomUUID()))
    }

    private fun runtime(): GpxRuntime {
        val persistence = GpxPersistenceStore(db)
        return GpxRuntime(coordinator(persistence), persistence)
    }

    private fun coordinator(persistence: GpxPersistenceStore = GpxPersistenceStore(db), checkpoint: (String) -> Unit = {}) =
        GpxImportCoordinator(persistence, GpxOriginalStore(root), checkpoint = checkpoint)

    private suspend fun interruptedAtFilePublished(request: GpxImportRequest): GpxPreparationResult.Prepared {
        return try {
            coordinator(checkpoint = { if (it == "FILE_PUBLISHED") throw Interruption() })
                .prepare(request, source(), { true }, { true })
            error("interruption must escape")
        } catch (_: Interruption) {
            val job = requireNotNull(db.gpxDao().jobByRequest(request.requestUuid))
            GpxPreparationResult.Prepared(request.requestUuid, requireNotNull(job.importId), requireNotNull(job.originalObjectId))
        }
    }

    private fun request(seed: Int) = GpxImportRequest(
        UUID(0, seed.toLong()), territory, observer, "runtime-$seed.gpx", 1_760_000_000_000L + seed,
    )

    private fun source(): () -> ByteArrayInputStream = { ByteArrayInputStream(validGpx()) }

    private fun validGpx() =
        "<?xml version=\"1.0\"?><gpx version=\"1.1\" creator=\"fixture\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg><trkpt lat=\"0\" lon=\"0\"/></trkseg></trk></gpx>".toByteArray()

    private class Interruption : Error()
}
