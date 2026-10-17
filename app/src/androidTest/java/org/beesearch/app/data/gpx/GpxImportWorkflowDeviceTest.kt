package org.beesearch.app.data.gpx

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.GpxPersistenceSchema
import org.beesearch.app.data.local.room.GpxPersistenceStore
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.local.settings.SettingsSelectionGuard
import org.beesearch.app.domain.model.AppSettings
import org.beesearch.app.domain.repository.SettingsRepository
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

@RunWith(AndroidJUnit4::class)
class GpxImportWorkflowDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: BeeSearchDatabase
    private lateinit var root: File
    private val territory = UUID(0, 11000)
    private val otherTerritory = UUID(0, 11001)
    private val observer = UUID(0, 11002)
    private val instant = Instant.parse("2026-10-17T00:00:00Z")
    private lateinit var settings: FakeSettings
    private lateinit var selection: SettingsSelectionGuard
    private lateinit var ids: ArrayDeque<UUID>

    @Before
    fun setUp(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .addCallback(GpxPersistenceSchema.callback).build()
        db.territoryDao().insert(TerritoryEntity(territory, "WORKFLOW", "Workflow", "R", "D", instant, instant))
        db.territoryDao().insert(TerritoryEntity(otherTerritory, "OTHER", "Other", "R", "D", instant, instant))
        db.observerDao().insert(ObserverEntity(observer, "WORKFLOW", "Observer", "Test", null, null, instant, instant))
        root = File(context.filesDir, "gpx-workflow-${UUID.randomUUID()}").apply { mkdirs() }
        settings = FakeSettings(AppSettings(territory, observer))
        selection = settings.selection
        ids = ArrayDeque((1..40).map { UUID(0, 11000L + it) })
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun capturedRequestExecutesToReadyAndKeepsCapturedContextAndIds(): Unit = runBlocking {
        val workflow = workflow()
        val captured = workflow.capture("workflow.gpx")

        val result = workflow.execute(captured, source()) as GpxPreparationResult.Ready

        assertEquals(territory, result.territoryId(db))
        assertNotNull(GpxPersistenceStore(db).readReady(result.importId))
        assertEquals(captured.request.requestUuid, result.requestUuid)
    }

    @Test
    fun changedCapturedContextRejectsBeforeOpeningSource(): Unit = runBlocking {
        val workflow = workflow()
        val captured = workflow.capture()
        settings.setCurrentTerritoryId(otherTerritory)
        settings.setCurrentTerritoryId(territory)
        var opens = 0

        val result = workflow.execute(captured, { opens++; ByteArrayInputStream(validGpx()) })

        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(0, opens)
        assertTrue(db.gpxDao().jobs().isEmpty())
    }

    @Test
    fun sameCapturedRequestRetriesByDurableLookupWithoutReopeningSource(): Unit = runBlocking {
        val workflow = workflow()
        val captured = workflow.capture()
        var opens = 0
        val first = workflow.execute(captured, { opens++; ByteArrayInputStream(validGpx()) })
        val second = workflow.execute(captured, { opens++; error("consumed request reopened source") })

        assertEquals(1, opens)
        assertEquals(first, second)
    }

    @Test
    fun duplicateNeedsConfirmationAndConfirmedNewCaptureGetsFreshIds(): Unit = runBlocking {
        val workflow = workflow()
        val first = workflow.execute(workflow.capture(), source()) as GpxPreparationResult.Ready
        val duplicateRequest = workflow.capture()
        val rejected = workflow.execute(duplicateRequest, source())

        assertTrue(rejected is GpxPreparationResult.Failed)
        assertNotNull(GpxPersistenceStore(db).readReady(first.importId))

        val confirmed = workflow.execute(workflow.capture(), source(), confirmRepeatedOriginal = true)
            as GpxPreparationResult.Ready
        assertFalse(first.importId == confirmed.importId)
        assertFalse(first.originalId == confirmed.originalId)
        assertEquals(2L, db.gpxDao().countImports(territory, "READY"))
    }

    @Test
    fun handleFromAnotherWorkflowIssuerIsRejected(): Unit = runBlocking {
        val first = workflow()
        val foreign = workflow()
        val captured = first.capture()

        val result = foreign.execute(captured, source())

        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.REQUEST, (result as GpxPreparationResult.Failed).kind)
        assertTrue(db.gpxDao().jobs().isEmpty())
    }

    @Test
    fun sourceFailureConsumesCapturedHandleAndDoesNotCreateOperation(): Unit = runBlocking {
        val workflow = workflow()
        val captured = workflow.capture()
        val first = workflow.execute(captured, { throw IOException("provider revoked") })
        val second = workflow.execute(captured, source())

        assertTrue(first is GpxPreparationResult.Failed)
        assertTrue(second is GpxPreparationResult.Failed)
        assertTrue(db.gpxDao().jobs().isEmpty())
    }

    @Test
    fun cancellationBeforeReadyPreservesCancellationAndLeavesNoReadyResult(): Unit = runBlocking {
        val marker = CancellationException("workflow cancelled")
        val workflow = workflow(checkpoint = { if (it == "PARSED") throw marker })
        val captured = workflow.capture()

        try {
            workflow.execute(captured, source())
            fail("cancellation must escape")
        } catch (actual: CancellationException) {
            assertSame(marker, actual)
        }
        assertTrue(db.gpxDao().jobs().isEmpty())
    }

    @Test
    fun cancellationDuringPostReadyCleanupPreservesCommittedReady(): Unit = runBlocking {
        val marker = CancellationException("cleanup cancelled")
        var cancelCleanup = true
        val workflow = workflow(checkpoint = { if (cancelCleanup && it == "READY_STAGING_REMOVED") throw marker })
        val captured = workflow.capture()

        try {
            workflow.execute(captured, source())
            fail("post-READY cancellation must escape")
        } catch (actual: CancellationException) {
            assertSame(marker, actual)
        }
        // Inspect durable success while the recovery fault is still armed, then retry after it clears.
        assertNotNull(GpxPersistenceStore(db).completed(captured.request.requestUuid))
        cancelCleanup = false
        val ready = workflow.lookup(captured.request.requestUuid)
        assertNotNull(ready)
        assertNotNull(GpxPersistenceStore(db).readReady(requireNotNull(ready).importId))
    }

    @Test fun observerChangeRejectsWithoutRetargetingCapturedObserver(): Unit = runBlocking {
        val workflow = workflow()
        val captured = workflow.capture()
        settings.setCurrentObserverId(null)
        val result = workflow.execute(captured, { error("stale context must not open source") })
        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(observer, captured.request.observerId)
        assertEquals(0L, db.gpxDao().countImports(territory, "READY"))
    }

    @Test fun revokedProviderAfterCopyIsNotReopenedByParsing(): Unit = runBlocking {
        var revoked = false
        var opens = 0
        val workflow = workflow(checkpoint = { if (it == "COPIED") revoked = true })
        val result = workflow.execute(workflow.capture(), {
            check(!revoked) { "provider no longer accessible" }
            opens++
            ByteArrayInputStream(validGpx())
        })
        assertTrue(revoked)
        assertEquals(1, opens)
        assertTrue(result is GpxPreparationResult.Ready)
    }

    @Test fun reconciliationCannotAbortAnActiveWorkflowBetweenSteps(): Unit = runBlocking {
        kotlinx.coroutines.withTimeout(15_000) {
            val persistence = GpxPersistenceStore(db)
            val runtime = GpxRuntime(GpxImportCoordinator(persistence, GpxOriginalStore(root)), persistence)
            val workflow = GpxImportWorkflow(runtime, settings, selection)
            val captured = workflow.capture()
            val entered = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            try {
                val importing = async(kotlinx.coroutines.Dispatchers.IO) {
                    workflow.execute(captured, {
                        entered.countDown()
                        check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                        ByteArrayInputStream(validGpx())
                    })
                }
                assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
                val started = kotlinx.coroutines.CompletableDeferred<Unit>()
                val recovery = async(kotlinx.coroutines.Dispatchers.IO) {
                    started.complete(Unit)
                    runtime.reconcile()
                }
                started.await()
                assertFalse(recovery.isCompleted)
                release.countDown()
                val ready = importing.await() as GpxPreparationResult.Ready
                assertTrue(recovery.await().completed)
                assertNotNull(runtime.readReady(territory, ready.importId))
            } finally { release.countDown() }
        }
    }

    @Test fun selectionChangeAtReadyBoundaryLinearizesAfterCommitAndCannotUndoIt(): Unit = runBlocking {
        kotlinx.coroutines.withTimeout(15_000) {
            val entered = java.util.concurrent.CountDownLatch(1)
            val release = java.util.concurrent.CountDownLatch(1)
            val workflow = workflow(checkpoint = {
                if (it == "READY_IMPORT_UPDATED") {
                    entered.countDown()
                    check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                }
            })
            val captured = workflow.capture()
            try {
                val importing = async(kotlinx.coroutines.Dispatchers.IO) {
                    workflow.execute(captured, source())
                }
                assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
                val started = kotlinx.coroutines.CompletableDeferred<Unit>()
                val change = async(kotlinx.coroutines.Dispatchers.Main) {
                    started.complete(Unit)
                    settings.setCurrentTerritoryId(otherTerritory)
                }
                started.await()
                assertFalse(change.isCompleted)
                release.countDown()
                val ready = importing.await() as GpxPreparationResult.Ready
                change.await()
                assertEquals(otherTerritory, settings.getSettings().currentTerritoryId)
                assertEquals(territory, ready.territoryId(db))
                assertEquals(ready, workflow.execute(captured, { error("READY cannot be replayed") }))
            } finally { release.countDown() }
        }
    }

    @Test fun contextChangeDuringSourceOpenCancelsEvenAfterSwitchingBack(): Unit = runBlocking {
        val workflow = workflow()
        val captured = workflow.capture()
        val result = workflow.execute(captured, {
            runBlocking {
                settings.setCurrentTerritoryId(otherTerritory)
                settings.setCurrentTerritoryId(territory)
            }
            ByteArrayInputStream(validGpx())
        })
        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(territory, captured.request.territoryId)
        assertEquals(0L, db.gpxDao().countImports(territory, "READY"))
        assertEquals(0L, db.gpxDao().countImports(otherTerritory, "READY"))
        assertTrue(db.gpxDao().jobs().isEmpty())
    }

    @Test fun cancellationBetweenPreparationAndCompleteCompensatesPendingGraph(): Unit = runBlocking {
        val marker = CancellationException("publication context read cancelled")
        val workflow = workflow(checkpoint = { if (it == "FILE_PUBLISHED") settings.nextReadFailure = marker })
        val captured = workflow.capture()
        try {
            workflow.execute(captured, source())
            fail("cancellation must escape")
        } catch (actual: CancellationException) { assertSame(marker, actual) }
        assertTrue(db.gpxDao().jobs().isEmpty())
        assertEquals(0L, db.gpxDao().countImports(territory, "PENDING"))
        assertEquals(0L, db.gpxDao().countImports(territory, "READY"))
        assertNull(workflow.lookup(captured.request.requestUuid))
    }

    @Test fun contextReadFailureBetweenPreparationAndCompleteFailsClosed(): Unit = runBlocking {
        val workflow = workflow(checkpoint = {
            if (it == "FILE_PUBLISHED") settings.nextReadFailure = IOException("settings read failed")
        })
        val result = workflow.execute(workflow.capture(), source())
        assertTrue(result is GpxPreparationResult.Failed)
        assertEquals(GpxPreparationResult.Kind.PERSISTENCE, (result as GpxPreparationResult.Failed).kind)
        assertFalse(result.cleanupPending)
        assertTrue(db.gpxDao().jobs().isEmpty())
        assertEquals(0L, db.gpxDao().countImports(territory, "PENDING"))
        assertEquals(0L, db.gpxDao().countImports(territory, "READY"))
    }

    private fun workflow(checkpoint: (String) -> Unit = {}): GpxImportWorkflow {
        val persistence = GpxPersistenceStore(db)
        val coordinator = GpxImportCoordinator(persistence, GpxOriginalStore(root), newId = { ids.removeFirst() }, checkpoint = checkpoint)
        return GpxImportWorkflow(
            GpxRuntime(coordinator, persistence), settings, selection, Clock.fixed(instant, ZoneOffset.UTC),
            newRequestId = { ids.removeFirst() },
        )
    }

    private fun source(): () -> ByteArrayInputStream = { ByteArrayInputStream(validGpx()) }

    private fun validGpx() = "<?xml version=\"1.0\"?><gpx version=\"1.1\" creator=\"fixture\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><trkseg><trkpt lat=\"0\" lon=\"0\"/></trkseg></trk></gpx>".toByteArray()

    private suspend fun GpxPreparationResult.Ready.territoryId(db: BeeSearchDatabase): UUID =
        requireNotNull(db.gpxDao().importById(importId)).territoryId

    private class FakeSettings(initial: AppSettings) : SettingsRepository {
        val selection = SettingsSelectionGuard()
        val value = MutableStateFlow(initial)
        override val settings: Flow<AppSettings> = value
        var nextReadFailure: Exception? = null
        override suspend fun getSettings(): AppSettings {
            nextReadFailure?.let { nextReadFailure = null; throw it }
            return value.value
        }
        override suspend fun setCurrentTerritoryId(territoryId: UUID?) {
            selection.withSelection {
                if (value.value.currentTerritoryId != territoryId) selection.invalidate()
                value.value = value.value.copy(currentTerritoryId = territoryId)
            }
        }
        override suspend fun setCurrentObserverId(observerId: UUID?) {
            selection.withSelection {
                if (value.value.currentObserverId != observerId) selection.invalidate()
                value.value = value.value.copy(currentObserverId = observerId)
            }
        }
        override suspend fun setInitialSetupOfferHandled(handled: Boolean) {
            value.value = value.value.copy(initialSetupOfferHandled = handled)
        }
    }
}
