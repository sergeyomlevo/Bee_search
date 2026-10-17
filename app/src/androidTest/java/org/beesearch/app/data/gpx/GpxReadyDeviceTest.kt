package org.beesearch.app.data.gpx

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** End-to-end READY transition coverage. READY fixtures are only test setup. */
@RunWith(AndroidJUnit4::class)
class GpxReadyDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BeeSearchDatabase
    private lateinit var root: File
    private val territory = UUID(0, 9700)
    private val observer = UUID(0, 9701)

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .addCallback(GpxPersistenceSchema.callback).build()
        val now = Instant.parse("2026-10-16T00:00:00Z")
        db.territoryDao().insert(TerritoryEntity(territory, "READY", "Ready", "R", "D", now, now))
        db.observerDao().insert(ObserverEntity(observer, "READY", "Ready", "Test", null, null, now, now))
        root = File(context.filesDir, "gpx-ready-${UUID.randomUUID()}").apply { mkdirs() }
    }

    @After fun tearDown() { db.close(); root.deleteRecursively() }

    @Test fun completePublishesReadyWithoutChangingPersistentIds() = runBlocking {
        val request = request(1)
        val prepared = prepare(request)
        val ready = coordinator().complete(request, isCapturedRequestCurrent = { true })
            as GpxPreparationResult.Ready

        assertEquals(prepared.importId, ready.importId)
        assertEquals(prepared.originalId, ready.originalId)
        assertNotNull(GpxPersistenceStore(db).readReady(ready.importId))
        assertNull(GpxPersistenceStore(db).readPending(ready.importId))
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
        assertEquals(request.requestUuid, ready.requestUuid)
    }

    @Test fun pendingGraphIsInvisibleUntilReadyCommit() = runBlocking {
        val request = request(2)
        val prepared = prepare(request)
        val store = GpxPersistenceStore(db)
        assertNotNull(store.readPending(prepared.importId))
        assertNull(store.readReady(prepared.importId))
        assertEquals("FILE_PUBLISHED", requireNotNull(db.gpxDao().jobByRequest(request.requestUuid)).phase)
        coordinator().complete(request, isCapturedRequestCurrent = { true })
        assertNotNull(store.readReady(prepared.importId))
    }

    @Test fun completedRequestLookupIsIdempotentAndDoesNotReopenSource() = runBlocking {
        val request = request(3)
        var opens = 0
        val first = coordinator().prepare(request, { opens++; source().invoke() }, { true }, { true })
        val ready = coordinator().complete(request, isCapturedRequestCurrent = { true }) as GpxPreparationResult.Ready
        val lookup = coordinator().lookup(request.requestUuid)
        val retry = coordinator().complete(request, isCapturedRequestCurrent = { true })
        assertEquals(1, opens)
        assertEquals(ready, lookup)
        assertEquals(ready, retry)
        assertTrue(first is GpxPreparationResult.Prepared)
    }

    @Test fun duplicateNeedsConfirmationButConfirmedRepeatGetsFreshIds() = runBlocking {
        val firstRequest = request(4)
        val first = coordinator().prepare(firstRequest, source(), { true }, { true })
        val firstReady = coordinator().complete(firstRequest, isCapturedRequestCurrent = { true })
            as GpxPreparationResult.Ready
        val repeatRequest = request(5)
        coordinator().prepare(repeatRequest, source(), { true }, { true })
        val rejected = coordinator().complete(repeatRequest, isCapturedRequestCurrent = { true })
        assertTrue(rejected is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.ADMISSION, (rejected as GpxPreparationResult.Failed).kind)
        assertEquals(GpxAdmissionRejection.DUPLICATE_CONFIRMATION_REQUIRED, rejected.admission)
        val confirmedRequest = request(55)
        coordinator().prepare(confirmedRequest, source(), { true }, { true })
        val confirmed = coordinator().complete(confirmedRequest, confirmRepeatedOriginal = true,
            isCapturedRequestCurrent = { true }) as GpxPreparationResult.Ready
        assertTrue(first is GpxPreparationResult.Prepared)
        assertFalse(firstReady.importId == confirmed.importId)
        assertFalse(firstReady.originalId == confirmed.originalId)
    }

    @Test fun sameBytesInAnotherTerritoryDoNotRequireConfirmation() = runBlocking {
        val otherTerritory = UUID(0, 9800)
        val otherObserver = UUID(0, 9801)
        val now = Instant.parse("2026-10-16T00:00:00Z")
        db.territoryDao().insert(TerritoryEntity(otherTerritory, "READY2", "Ready", "R", "D", now, now))
        db.observerDao().insert(ObserverEntity(otherObserver, "READY2", "Ready", "Test", null, null, now, now))
        val firstRequest = request(6)
        coordinator().prepare(firstRequest, source(), { true }, { true })
        coordinator().complete(firstRequest, isCapturedRequestCurrent = { true })
        val secondRequest = GpxImportRequest(UUID(0, 7), otherTerritory, otherObserver, "other.gpx", 1_760_000_000_007L)
        coordinator().prepare(secondRequest, source(), { true }, { true })
        assertTrue(coordinator().complete(secondRequest, isCapturedRequestCurrent = { true }) is GpxPreparationResult.Ready)
    }

    @Test fun cancellationBeforeReadyPreservesIdentityWithoutPublishingReady(): Unit = runBlocking {
        val marker = CancellationException("before ready")
        val request = request(8)
        prepare(request)
        val cancelling = coordinator(checkpoint = { if (it == "BEFORE_READY") throw marker })
        try {
            cancelling.complete(request, isCapturedRequestCurrent = { true })
            fail("cancellation must escape")
        } catch (actual: CancellationException) {
            assertSame(marker, actual)
        }
        val job = db.gpxDao().jobByRequest(request.requestUuid)
        assertTrue(job == null || job.phase != "READY")
        job?.importId?.let { assertNull(GpxPersistenceStore(db).readReady(it)) }
    }

    @Test fun cancellationAfterReadyDoesNotUndoCommittedResult() = runBlocking {
        val request = request(9)
        val prepared = prepare(request)
        val marker = CancellationException("after ready")
        try {
            coordinator(checkpoint = { if (it == "READY_COMMITTED") throw marker })
                .complete(request, isCapturedRequestCurrent = { true })
            fail("cancellation must escape")
        } catch (actual: CancellationException) {
            assertSame(marker, actual)
        }
        assertNotNull(GpxPersistenceStore(db).readReady(prepared.importId))
        assertEquals(prepared.importId, requireNotNull(coordinator().lookup(request.requestUuid)).importId)
    }

    @Test fun actualCoroutineCancellationAfterCommitKeepsDurableResult() = runBlocking {
        val request = request(42)
        val prepared = prepare(request)
        lateinit var operation: Job
        operation = launch {
            coordinator(checkpoint = { if (it == "READY_COMMITTED") operation.cancel() })
                .complete(request, isCapturedRequestCurrent = { true })
        }
        operation.join()
        assertTrue(operation.isCancelled)
        assertEquals(prepared.importId, requireNotNull(coordinator().lookup(request.requestUuid)).importId)
        assertNotNull(GpxPersistenceStore(db).readReady(prepared.importId))
        assertTrue(GpxOriginalStore(root).listFinalCandidates().contains(prepared.originalId))
    }

    @Test fun repeatedSameCleanupCancellationDoesNotSuppressItselfOrUndoReady() = runBlocking {
        val request = request(43)
        val prepared = prepare(request)
        val marker = CancellationException("same cleanup cancellation")
        try {
            coordinator(checkpoint = { if (it == "READY_STAGING_REMOVED") throw marker })
                .complete(request, isCapturedRequestCurrent = { true })
            fail("cancellation must escape")
        } catch (actual: CancellationException) {
            assertSame(marker, actual)
        }
        assertNotNull(GpxPersistenceStore(db).readReady(prepared.importId))
        assertEquals(prepared.importId, (coordinator().recover().single() as GpxPreparationResult.Ready).importId)
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
    }

    @Test fun readyTransactionFailureRollsBackGraphCorrelationAndJournal() = runBlocking {
        val request = request(10)
        val prepared = prepare(request)
        val job = requireNotNull(db.gpxDao().jobByRequest(request.requestUuid))
        try {
            GpxPersistenceStore(db).publishReady(job, false, {},
                checkpoint = { if (it == "READY_IMPORT_UPDATED") error("injected") })
            fail("injected transaction failure must escape")
        } catch (_: IllegalStateException) { }
        assertNull(GpxPersistenceStore(db).readReady(prepared.importId))
        assertNotNull(GpxPersistenceStore(db).readPending(prepared.importId))
        assertEquals("FILE_PUBLISHED", requireNotNull(db.gpxDao().jobByRequest(request.requestUuid)).phase)
    }

    @Test fun abortAndReadyRaceCannotDeleteCommittedReady() = runBlocking {
        val request = request(11)
        val prepared = prepare(request)
        val ready = coordinator().complete(request, isCapturedRequestCurrent = { true })
        assertTrue(ready is GpxPreparationResult.Ready)
        val aborted = coordinator().abort(request.requestUuid)
        assertTrue(aborted is GpxPreparationResult.Ready)
        assertNotNull(GpxPersistenceStore(db).readReady(prepared.importId))
        assertTrue(GpxOriginalStore(root).listFinalCandidates().contains(prepared.originalId))
    }

    @Test fun recoveryCompletesNonRepeatedFilePublishedOperation() = runBlocking {
        val request = request(12)
        try {
            coordinator(checkpoint = { if (it == "FILE_PUBLISHED") throw Interruption() })
                .prepare(request, source(), { true }, { true })
            fail("injected interruption must escape")
        } catch (_: Error) { }
        val recovered = coordinator().recover().single()
        assertTrue(recovered is GpxPreparationResult.Ready)
        assertNotNull(coordinator().lookup(request.requestUuid))
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
    }

    @Test fun recoveryRejectsUnconfirmedRepeatedFilePublishedOperation() = runBlocking {
        val first = request(13)
        coordinator().prepare(first, source(), { true }, { true })
        coordinator().complete(first, isCapturedRequestCurrent = { true })
        val repeated = request(14)
        try {
            coordinator(checkpoint = { if (it == "FILE_PUBLISHED") throw Interruption() })
                .prepare(repeated, source(), { true }, { true })
            fail("injected interruption must escape")
        } catch (_: Error) { }
        val recovered = coordinator().recover().single()
        assertTrue(recovered is GpxPreparationResult.Failed)
        val failed = recovered as GpxPreparationResult.Failed
        assertEquals(GpxPreparationResult.Kind.ADMISSION, failed.kind)
        assertEquals(GpxAdmissionRejection.DUPLICATE_CONFIRMATION_REQUIRED, failed.admission)
        assertNull(coordinator().lookup(repeated.requestUuid))
    }

    @Test fun recoveryOfCommittedReadyKeepsIdsAndOriginalWithoutReadmission() = runBlocking {
        val request = request(21)
        val prepared = prepare(request)
        try {
            coordinator(checkpoint = { if (it == "READY_COMMITTED") throw Interruption() })
                .complete(request, isCapturedRequestCurrent = { true })
            fail("injected caller-side interruption must escape")
        } catch (_: Error) { }
        val recovered = GpxImportCoordinator(GpxPersistenceStore(db), GpxOriginalStore(root)).recover().single()
            as GpxPreparationResult.Ready
        assertEquals(prepared.importId, recovered.importId)
        assertEquals(prepared.originalId, recovered.originalId)
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
        assertTrue(GpxOriginalStore(root).listFinalCandidates().contains(prepared.originalId))
    }

    @Test fun duplicateAppearingBetweenPrepareAndCompleteIsRejectedFromCurrentDatabase() = runBlocking {
        val first = request(22)
        val second = request(23)
        coordinator().prepare(first, source(), { true }, { true })
        coordinator().prepare(second, source(), { true }, { true })
        assertTrue(coordinator().complete(second, isCapturedRequestCurrent = { true }) is GpxPreparationResult.Ready)
        val result = coordinator().complete(first, isCapturedRequestCurrent = { true })
        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxAdmissionRejection.DUPLICATE_CONFIRMATION_REQUIRED,
            (result as GpxPreparationResult.Failed).admission)
        assertNull(coordinator().lookup(first.requestUuid))
    }

    @Test fun requestContextConflictDoesNotAbortForeignJob() = runBlocking {
        val foreignTerritory = UUID(0, 9900)
        val foreignObserver = UUID(0, 9901)
        insertContext(foreignTerritory, foreignObserver)
        val requestId = UUID(0, 24)
        val foreign = org.beesearch.app.data.local.room.GpxImportJobEntity(
            requestId, foreignTerritory, foreignObserver, "SELECTED", "CONTINUE",
        )
        GpxPersistenceStore(db).begin(foreign)
        val result = coordinator().prepare(
            GpxImportRequest(requestId, territory, observer, "conflict.gpx", 1_760_000_000_024L),
            source(), { true }, { true },
        )
        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.PERSISTENCE, (result as GpxPreparationResult.Failed).kind)
        assertEquals(foreign, db.gpxDao().jobByRequest(requestId))
    }

    @Test fun postReadyCleanupFailureReturnsReadyAndRecoveryFinishesCleanup() = runBlocking {
        val request = request(25)
        val prepared = prepare(request)
        val first = coordinator(checkpoint = { if (it == "READY_STAGING_REMOVED") throw IllegalStateException("cleanup") })
            .complete(request, isCapturedRequestCurrent = { true }) as GpxPreparationResult.Ready
        assertTrue(first.cleanupPending)
        assertNotNull(db.gpxDao().jobByRequest(request.requestUuid))
        val recovered = coordinator().recover().single() as GpxPreparationResult.Ready
        assertFalse(recovered.cleanupPending)
        assertEquals(prepared.importId, recovered.importId)
        assertNull(db.gpxDao().jobByRequest(request.requestUuid))
    }

    @Test fun concurrentCompleteAndAbortHasOnlyCommittedOrCompensatedOutcome() = runBlocking {
        val request = request(26)
        val prepared = prepare(request)
        val results = listOf(
            async { coordinator().complete(request, isCapturedRequestCurrent = { true }) },
            async { coordinator().abort(request.requestUuid) },
        ).awaitAll()
        val ready = results.filterIsInstance<GpxPreparationResult.Ready>().firstOrNull()
        if (ready != null) {
            assertEquals(prepared.importId, ready.importId)
            assertTrue(results.all { it is GpxPreparationResult.Ready })
            assertNotNull(GpxPersistenceStore(db).readReady(prepared.importId))
            assertTrue(GpxOriginalStore(root).listFinalCandidates().contains(prepared.originalId))
        } else {
            assertTrue(results.any { it is GpxPreparationResult.Aborted ||
                it is GpxPreparationResult.Failed && it.kind == GpxPreparationResult.Kind.REQUEST })
            assertNull(GpxPersistenceStore(db).readReady(prepared.importId))
            assertFalse(GpxOriginalStore(root).listFinalCandidates().contains(prepared.originalId))
        }
    }

    @Test fun capturedRequestChangeAtFinalCheckPreventsReady() = runBlocking {
        val request = request(27)
        val prepared = prepare(request)
        var current = true
        val result = coordinator(checkpoint = { if (it == "READY_JOB_UPDATED") current = false })
            .complete(request, isCapturedRequestCurrent = { current })
        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.REQUEST, (result as GpxPreparationResult.Failed).kind)
        assertNull(GpxPersistenceStore(db).readReady(prepared.importId))
    }

    @Test fun g11UsesPersistedReadyRowsAtTheBoundary() = runBlocking {
        seedReadyImports(1022)
        val allowed = request(15)
        coordinator().prepare(allowed, source(), { true }, { true })
        assertTrue(coordinator().complete(allowed, isCapturedRequestCurrent = { true }) is GpxPreparationResult.Ready)

        seedReadyImports(1, offset = 20_000)
        val rejected = request(16)
        coordinator().prepare(rejected, source(), { true }, { true })
        val result = coordinator().complete(rejected, isCapturedRequestCurrent = { true })
        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxAdmissionRejection.G11, (result as GpxPreparationResult.Failed).admission)
    }

    @Test fun concurrentCandidatesShareTheLastG11Slot() = runBlocking {
        seedReadyImports(1023)
        val first = request(17)
        val second = request(18)
        coordinator().prepare(first, source(), { true }, { true })
        coordinator().prepare(second, source(), { true }, { true })
        val results = listOf(
            async { coordinator().complete(first, isCapturedRequestCurrent = { true }) },
            async { coordinator().complete(second, isCapturedRequestCurrent = { true }) },
        ).awaitAll()
        assertEquals(1, results.count { it is GpxPreparationResult.Ready })
        assertEquals(1, results.count { it is GpxPreparationResult.Failed && it.admission == GpxAdmissionRejection.G11 })
        assertEquals(1024L, GpxPersistenceStore(db).counts(territory, "READY").imports)
    }

    @Test fun otherPendingCandidatesDoNotReserveG11Capacity() = runBlocking {
        seedReadyImports(1023)
        val first = request(28)
        val second = request(29)
        coordinator().prepare(first, source(), { true }, { true })
        coordinator().prepare(second, source(), { true }, { true })
        assertTrue(coordinator().complete(first, isCapturedRequestCurrent = { true }) is GpxPreparationResult.Ready)
        val result = coordinator().complete(second, isCapturedRequestCurrent = { true })
        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxAdmissionRejection.G11, (result as GpxPreparationResult.Failed).admission)
    }

    @Test fun g12CountsPersistedSamplesWithoutMaterializingTheExistingGraph() = runBlocking {
        seedReadySamples(199_999)
        val exact = request(19)
        coordinator().prepare(exact, source(), { true }, { true })
        assertTrue(coordinator().complete(exact, isCapturedRequestCurrent = { true }) is GpxPreparationResult.Ready)

        val overflow = request(20)
        coordinator().prepare(overflow, source(), { true }, { true })
        val result = coordinator().complete(overflow, isCapturedRequestCurrent = { true })
        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxAdmissionRejection.G12, (result as GpxPreparationResult.Failed).admission)
    }

    @Test fun concurrentCandidatesShareTheLastG12SampleWithoutPendingReservations() = runBlocking {
        seedReadySamples(199_999)
        val first = request(40)
        val second = request(41)
        prepare(first)
        prepare(second)
        val results = listOf(
            async { coordinator().complete(first, isCapturedRequestCurrent = { true }) },
            async { coordinator().complete(second, isCapturedRequestCurrent = { true }) },
        ).awaitAll()
        assertEquals(1, results.count { it is GpxPreparationResult.Ready })
        assertEquals(1, results.count { it is GpxPreparationResult.Failed && it.admission == GpxAdmissionRejection.G12 })
        assertEquals(200_000L, GpxPersistenceStore(db).counts(territory, "READY").samples)
    }

    private class Interruption : Error()

    private suspend fun prepare(request: GpxImportRequest): GpxPreparationResult.Prepared =
        coordinator().prepare(request, source(), { true }, { true }) as GpxPreparationResult.Prepared

    private fun coordinator(checkpoint: (String) -> Unit = {}) =
        GpxImportCoordinator(GpxPersistenceStore(db), GpxOriginalStore(root), checkpoint = checkpoint)

    private fun request(seed: Int) = GpxImportRequest(
        UUID(0, seed.toLong()), territory, observer, "ready-$seed.gpx", 1_760_000_000_000L + seed,
    )

    private suspend fun insertContext(territoryId: UUID, observerId: UUID) {
        val now = Instant.parse("2026-10-16T00:00:00Z")
        db.territoryDao().insert(TerritoryEntity(territoryId, "EXTRA", "Extra", "R", "D", now, now))
        db.observerDao().insert(ObserverEntity(observerId, "EXTRA", "Extra", "Test", null, null, now, now))
    }

    private fun source(samples: Int = 1): () -> ByteArrayInputStream = { ByteArrayInputStream(validGpx(samples)) }

    private fun validGpx(samples: Int = 1) = buildString {
        append("<?xml version=\"1.0\"?><gpx version=\"1.1\" creator=\"ready\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg>")
        repeat(samples) { append("<trkpt lat=\"0\" lon=\"0\"/>") }
        append("</trkseg></trk></gpx>")
    }.toByteArray()

    private suspend fun seedReadyImports(count: Int, offset: Int = 10_000) {
        repeat(count) { index ->
            val id = UUID(0, (offset + index).toLong())
            db.gpxDao().insertImport(
                org.beesearch.app.data.local.room.GpxImportEntity(
                    id, territory, observer, UUID(1, (offset + index).toLong()),
                    "c".repeat(64), 1, "seed.gpx", "1.1", "GPX_1_1_STAGE_1", 1_760_000_000_000L,
                    "seed",
                ),
            )
            db.openHelper.writableDatabase.execSQL(
                "UPDATE gpx_imports SET publication_state='READY' WHERE id=?",
                arrayOf(id.toString()),
            )
        }
    }

    private suspend fun seedReadySamples(count: Int) {
        val importId = UUID(0, 30_000)
        val trackId = UUID(0, 30_001)
        val segmentId = UUID(0, 30_002)
        db.gpxDao().insertImport(
            org.beesearch.app.data.local.room.GpxImportEntity(
                importId, territory, observer, UUID(1, 30_000), "d".repeat(64), 1,
                "seed-samples.gpx", "1.1", "GPX_1_1_STAGE_1", 1_760_000_000_000L, "seed",
            ),
        )
        db.gpxDao().insertTracks(listOf(org.beesearch.app.data.local.room.GpxSourceTrackEntity(trackId, importId, 0)))
        db.gpxDao().insertSegments(listOf(org.beesearch.app.data.local.room.GpxTrackSegmentEntity(segmentId, trackId, 0)))
        val sql = db.openHelper.writableDatabase
        sql.beginTransaction()
        try {
            val statement = sql.compileStatement(
                "INSERT INTO gpx_track_samples(segment_id,ordinal,latitude,longitude,elevation,time_raw,time_status,time_epoch_seconds,time_nanos) VALUES(?,?,?,?,NULL,NULL,'MISSING',NULL,NULL)",
            )
            repeat(count) { ordinal ->
                statement.clearBindings()
                statement.bindString(1, segmentId.toString())
                statement.bindLong(2, ordinal.toLong())
                statement.bindString(3, "0")
                statement.bindString(4, "0")
                statement.executeInsert()
            }
            statement.close()
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        sql.execSQL("UPDATE gpx_imports SET publication_state='READY' WHERE id=?", arrayOf(importId.toString()))
    }
}
