package org.beesearch.app.data.gpx

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.GpxPersistenceSchema
import org.beesearch.app.data.local.room.GpxPersistenceStore
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end Room + managed-original coverage through the FILE_PUBLISHED boundary. */
@RunWith(AndroidJUnit4::class)
class GpxCoordinatorDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BeeSearchDatabase
    private lateinit var root: File
    private val territory = UUID(0, 9100)
    private val observer = UUID(0, 9101)

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .addCallback(GpxPersistenceSchema.callback)
            .build()
        val now = Instant.parse("2026-10-16T00:00:00Z")
        db.territoryDao().insert(TerritoryEntity(territory, "COORD", "Coordinator", "R", "D", now, now))
        db.observerDao().insert(ObserverEntity(observer, "COORD", "Observer", "Test", null, null, now, now))
        root = File(context.filesDir, "gpx-coordinator-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun preparePublishesVerifiedOriginalButLeavesGraphPendingAndInvisible() = runBlocking {
        val request = request(1)
        val result = coordinator().prepare(request, source(), { true }, { true })
        val prepared = result as GpxPreparationResult.Prepared

        assertEquals(request.requestUuid, prepared.requestUuid)
        val store = GpxPersistenceStore(db)
        assertNotNull(store.readPending(prepared.importId))
        assertNull(store.readReady(prepared.importId))
        assertEquals("FILE_PUBLISHED", requireNotNull(db.gpxDao().jobByRequest(request.requestUuid)).phase)
        assertTrue(GpxOriginalStore(root).listFinalCandidates().contains(prepared.originalId))
    }

    @Test
    fun sameRequestRetryReturnsSameIdsWithoutReopeningSource() = runBlocking {
        val request = request(2)
        var opens = 0
        val first = coordinator().prepare(request, {
            opens++
            ByteArrayInputStream(validGpx())
        }, { true }, { true }) as GpxPreparationResult.Prepared

        val second = coordinator().prepare(request, {
            opens++
            error("a completed request must not reopen its provider")
        }, { true }, { true }) as GpxPreparationResult.Prepared

        assertEquals(1, opens)
        assertEquals(first, second)
    }

    @Test
    fun parserRejectionAndInputIoLeaveNoPendingGraphOrOriginalFiles() = runBlocking {
        val rejected = coordinator().prepare(request(3),
            { ByteArrayInputStream("<gpx".toByteArray()) }, { true }, { true })
        assertTrue(rejected is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.PARSE, (rejected as GpxPreparationResult.Failed).kind)
        assertNoFilesOrJobs()

        val ioFailure = coordinator().prepare(request(4), { throw IOException("source") }, { true }, { true })
        assertTrue(ioFailure is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.ORIGINAL, (ioFailure as GpxPreparationResult.Failed).kind)
        assertNoFilesOrJobs()
    }

    @Test
    fun cancellationAtEveryCoordinatorPhasePreservesIdentityAndCleansOwnedState() = runBlocking {
        listOf("SELECTED", "COPYING", "COPIED", "PARSING", "PARSED", "FINAL_VERIFIED", "FILE_PUBLISHED").forEachIndexed { n, phase ->
            val request = request(100 + n)
            val marker = CancellationException("cancel at $phase")
            try {
                coordinator(checkpoint = { actual -> if (actual == phase) throw marker })
                    .prepare(request, source(), { true }, { true })
                fail("cancellation at $phase escaped as success")
            } catch (actual: CancellationException) {
                assertSame(marker, actual)
            }
            assertNull(db.gpxDao().jobByRequest(request.requestUuid))
            assertNoFilesOrJobs()
        }
    }

    @Test
    fun injectedInterruptionAtSelectedIsRecoveredAsAbort() = runBlocking {
        val request = request(200)
        try {
            coordinator(checkpoint = { if (it == "SELECTED") throw Interruption() })
                .prepare(request, source(), { true }, { true })
            fail("interruption must escape without normal result")
        } catch (_: Interruption) {
            // Durable SELECTED journal is the recovery input.
        }
        val recovered = coordinator().recover()
        assertEquals(listOf(GpxPreparationResult.Aborted(request.requestUuid)), recovered)
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
        assertNoFilesOrJobs()
    }

    @Test
    fun injectedInterruptionAtParsedRecoversToFilePublishedWithoutReparseOrNewIds() = runBlocking {
        val request = request(201)
        var opens = 0
        try {
            coordinator(checkpoint = { if (it == "PARSED") throw Interruption() })
                .prepare(request, { opens++; ByteArrayInputStream(validGpx()) }, { true }, { true })
            fail("interruption must escape without normal result")
        } catch (_: Interruption) {
        }
        val saved = requireNotNull(db.gpxDao().jobByRequest(request.requestUuid))
        val originalId = requireNotNull(saved.originalObjectId)
        val recovered = coordinator().recover().single() as GpxPreparationResult.Ready
        assertEquals(saved.importId, recovered.importId)
        assertEquals(originalId, recovered.originalId)
        assertEquals(1, opens)
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
        assertNotNull(GpxPersistenceStore(db).readReady(recovered.importId))
    }

    @Test
    fun retryWithNoLongerCurrentCapturedRequestDoesNotPublishParsedWork() = runBlocking {
        val request = request(203)
        try {
            coordinator(checkpoint = { if (it == "PARSED") throw Interruption() })
                .prepare(request, source(), { true }, { true })
            fail("interruption must escape without normal result")
        } catch (_: Interruption) {
        }

        val retry = coordinator().prepare(request, source(), { true }, { false })
        assertTrue(retry is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.REQUEST, (retry as GpxPreparationResult.Failed).kind)
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
        assertNoFilesOrJobs()
    }

    @Test
    fun injectedInterruptionAtFilePublishedIsIdempotentlyRecovered() = runBlocking {
        val request = request(202)
        try {
            coordinator(checkpoint = { if (it == "FILE_PUBLISHED") throw Interruption() })
                .prepare(request, source(), { true }, { true })
            fail("interruption must escape without normal result")
        } catch (_: Interruption) {
        }
        assertEquals("FILE_PUBLISHED", requireNotNull(db.gpxDao().jobByRequest(request.requestUuid)).phase)
        val first = coordinator().recover().single() as GpxPreparationResult.Ready
        val second = coordinator().lookup(request.requestUuid)
        assertEquals(first, second)
        assertTrue(coordinator().recover().isEmpty())
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
    }

    @Test
    fun twoOperationsWithEqualBytesKeepIndependentGraphsAndOriginals() = runBlocking {
        val first = coordinator().prepare(request(300), source(), { true }, { true }) as GpxPreparationResult.Prepared
        val second = coordinator().prepare(request(301), source(), { true }, { true }) as GpxPreparationResult.Prepared

        assertFalse(first.importId == second.importId)
        assertFalse(first.originalId == second.originalId)
        assertNotNull(GpxPersistenceStore(db).readPending(first.importId))
        assertNotNull(GpxPersistenceStore(db).readPending(second.importId))
        assertTrue(GpxOriginalStore(root).listFinalCandidates().containsAll(listOf(first.originalId, second.originalId)))
    }

    @Test
    fun unavailableCapturedContextFailsBeforeCreatingOperation() = runBlocking {
        val request = request(400)
        val result = coordinator().prepare(request, source(), { false }, { true })
        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.REQUEST, (result as GpxPreparationResult.Failed).kind)
        assertNoFilesOrJobs()

        val changedContext = coordinator().prepare(request(401), source(), { true }, { false })
        assertTrue(changedContext is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.REQUEST, (changedContext as GpxPreparationResult.Failed).kind)
        assertNoFilesOrJobs()
    }

    @Test
    fun abortNeverPublishesOrDeletesAnotherOperation() = runBlocking {
        val retained = coordinator().prepare(request(500), source(), { true }, { true }) as GpxPreparationResult.Prepared
        val aborted = coordinator().prepare(request(501), source(), { true }, { true }) as GpxPreparationResult.Prepared
        val result = coordinator().abort(aborted.requestUuid)

        assertEquals(GpxPreparationResult.Aborted(aborted.requestUuid), result)
        assertNull(GpxPersistenceStore(db).readReady(aborted.importId))
        assertNotNull(GpxPersistenceStore(db).readPending(retained.importId))
        assertTrue(GpxOriginalStore(root).listFinalCandidates().contains(retained.originalId))
        assertFalse(GpxOriginalStore(root).listFinalCandidates().contains(aborted.originalId))
    }

    @Test
    fun persistenceInsertCollisionRollsBackGraphAndLeavesNoFiles() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER coordinator_abort_segment BEFORE INSERT ON gpx_track_segments " +
                "BEGIN SELECT RAISE(ABORT, 'coordinator test'); END",
        )
        val result = coordinator().prepare(request(600), source(), { true }, { true })

        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.PERSISTENCE, (result as GpxPreparationResult.Failed).kind)
        assertNoFilesOrJobs()
        assertEquals(0L, GpxPersistenceStore(db).counts(territory, "PENDING").imports)
    }

    @Test
    fun equalBytesForeignFinalIsConflictAndIsNeverAdopted() = runBlocking {
        val foreignRequest = UUID(0, 6100)
        val original = UUID(0, 6101)
        val foreignStore = GpxOriginalStore(root)
        val identity = foreignStore.copyToStaging(foreignRequest, original, source())
        foreignStore.publish(foreignRequest, original, identity, { true })
        val ids = ArrayDeque(listOf(original, UUID(0, 6102), UUID(0, 6103), UUID(0, 6104)))

        val result = GpxImportCoordinator(
            GpxPersistenceStore(db), GpxOriginalStore(root), newId = { ids.removeFirst() },
        ).prepare(request(610), source(), { true }, { true })

        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.ORIGINAL, (result as GpxPreparationResult.Failed).kind)
        assertTrue(result.cleanupPending)
        assertArrayEquals(validGpx(), GpxOriginalStore(root).openFinal(original, identity).use { it.readBytes() })
        assertEquals("ABORT", requireNotNull(db.gpxDao().jobByRequest(request(610).requestUuid)).intent)
    }

    @Test
    fun abortCannotAcquireAuthorityOverReadyGraphOrOriginal() = runBlocking {
        val request = request(620)
        val prepared = coordinator().prepare(request, source(), { true }, { true }) as GpxPreparationResult.Prepared
        db.openHelper.writableDatabase.execSQL(
            "UPDATE gpx_imports SET publication_state='READY', request_uuid=? WHERE id=?",
            arrayOf(request.requestUuid.toString(), prepared.importId.toString()),
        )

        val aborted = coordinator().abort(request.requestUuid)
        assertTrue(aborted is GpxPreparationResult.Failed)
        assertEquals("CONTINUE", requireNotNull(db.gpxDao().jobByRequest(request.requestUuid)).intent)
        assertNotNull(GpxPersistenceStore(db).readReady(prepared.importId))
        assertTrue(GpxOriginalStore(root).listFinalCandidates().contains(prepared.originalId))

        val recovery = coordinator().recover().single()
        assertTrue(recovery is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.PERSISTENCE, (recovery as GpxPreparationResult.Failed).kind)
        assertEquals("CONTINUE", requireNotNull(db.gpxDao().jobByRequest(request.requestUuid)).intent)
        assertNotNull(GpxPersistenceStore(db).readReady(prepared.importId))
    }

    @Test
    fun cancellationDuringCopyPreservesSameCancellationAndCloseFailure() = runBlocking {
        val marker = CancellationException("copy cancellation")
        val input = object : InputStream() {
            override fun read(): Int = throw marker
            override fun close() { throw IOException("close after cancellation") }
        }
        try {
            coordinator().prepare(request(630), { input }, { true }, { true })
            fail("cancellation must escape")
        } catch (actual: CancellationException) {
            assertSame(marker, actual)
            assertTrue(actual.suppressed.any { it is GpxOriginalStoreException })
        }
        assertNoFilesOrJobs()
    }

    @Test
    fun actualCoroutineCancellationAtParsedCleansJournalGraphAndFiles() = runBlocking {
        val request = request(635)
        lateinit var operation: Job
        val cancelled = CompletableDeferred<CancellationException>()
        operation = launch {
            try {
                coordinator(checkpoint = { phase ->
                    if (phase == "PARSED") operation.cancel(CancellationException("job cancelled"))
                }).prepare(request, source(), { true }, { true })
                fail("cancelled operation must not complete")
            } catch (error: CancellationException) {
                cancelled.complete(error)
                throw error
            }
        }
        operation.join()
        assertTrue(operation.isCancelled)
        assertTrue(cancelled.isCompleted)
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
        assertNoFilesOrJobs()
    }

    @Test
    fun concurrentSameRequestIsSerializedAndOpensSourceOnce() = runBlocking {
        val request = request(636)
        val opens = AtomicInteger(0)
        val results = listOf(
            async { coordinator().prepare(request, { opens.incrementAndGet(); ByteArrayInputStream(validGpx()) }, { true }, { true }) },
            async { coordinator().prepare(request, { opens.incrementAndGet(); ByteArrayInputStream(validGpx()) }, { true }, { true }) },
        ).awaitAll()
        assertEquals(1, opens.get())
        assertEquals(results[0], results[1])
        assertTrue(results.all { it is GpxPreparationResult.Prepared })
    }

    @Test
    fun completeFinalBeforeVerificationIsReconciledToReady() = runBlocking {
        val request = request(640)
        val interruptedStore = GpxOriginalStore(root) { stage ->
            if (stage == GpxOriginalStage.FINAL_COPY) throw Interruption()
        }
        try {
            GpxImportCoordinator(GpxPersistenceStore(db), interruptedStore)
                .prepare(request, source(), { true }, { true })
            fail("final-copy interruption must escape")
        } catch (_: Interruption) {
        }

        val recovered = coordinator().recover().single() as GpxPreparationResult.Ready
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
        assertNotNull(GpxPersistenceStore(db).readReady(recovered.importId))
    }

    @Test
    fun partialFinalAfterInterruptionIsCompensatedWithoutLeakingPendingState() = runBlocking {
        val request = request(641)
        val interruptedStore = GpxOriginalStore(root) { stage ->
            if (stage == GpxOriginalStage.FINAL_COPY) throw Interruption()
        }
        try {
            GpxImportCoordinator(GpxPersistenceStore(db), interruptedStore)
                .prepare(request, { ByteArrayInputStream(largeValidGpx()) }, { true }, { true })
            fail("final-copy interruption must escape")
        } catch (_: Interruption) {
        }

        val recovery = coordinator().recover().single()
        assertTrue(recovery is GpxPreparationResult.Failed)
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
        assertEquals(0L, GpxPersistenceStore(db).counts(territory, "PENDING").imports)
        assertNoFilesOrJobs()
    }

    private fun coordinator(checkpoint: (String) -> Unit = {}): GpxImportCoordinator =
        GpxImportCoordinator(GpxPersistenceStore(db), GpxOriginalStore(root), checkpoint = checkpoint)

    private fun request(seed: Int) = GpxImportRequest(
        UUID(0, seed.toLong()), territory, observer, "fixture-$seed.gpx", 1_760_000_000_000L + seed,
    )

    private fun source(): () -> ByteArrayInputStream = { ByteArrayInputStream(validGpx()) }

    private fun validGpx() =
        "<?xml version=\"1.0\"?><gpx version=\"1.1\" creator=\"fixture\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg><trkpt lat=\"0\" lon=\"0\"/></trkseg></trk></gpx>".toByteArray()

    private fun largeValidGpx(): ByteArray = buildString {
        append("<?xml version=\"1.0\"?><gpx version=\"1.1\" creator=\"fixture\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>")
        repeat(3000) { append("<trkpt lat=\"0\" lon=\"0\"/>") }
        append("</trkseg></trk></gpx>")
    }.toByteArray()

    private suspend fun assertNoFilesOrJobs() {
        assertTrue(db.gpxDao().jobs().isEmpty())
        assertTrue(root.walkTopDown().drop(1).none { it.isFile })
    }

    private class Interruption : Error()
}
