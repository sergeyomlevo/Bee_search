package org.beesearch.app.data.gpx

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.*
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Host force-stop probes use only a uniquely named test database and private file sandbox. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GpxImportWorkflowCrashDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val name get() = (arguments.getString("readySandbox") ?: "gpx-ready-workflow-normal").also {
        require(it.matches(Regex("gpx-ready-[a-zA-Z0-9-]{1,70}")))
    }
    private val boundary get() = arguments.getString("readyBoundary") ?: "READY_COMMITTED"
    private val root get() = File(context.filesDir, name)
    private val request = GpxImportRequest(UUID(0, 8200), UUID(0, 8201), null, "ready-crash.gpx", 0)
    private fun database() = Room.databaseBuilder(context, BeeSearchDatabase::class.java, "$name.db")
        .addCallback(GpxPersistenceSchema.callback).build()

    private fun checkpoint(actual: String) {
        if (actual != boundary || arguments.getString("readyKill") != "true") return
        File(root, "boundary").writeText(boundary)
        repeat(120) { Thread.sleep(500) }
        throw AssertionError("Host did not stop the DEV process")
    }

    @Test fun a_completeAndStopAtBoundary(): Unit = runBlocking {
        check(root.mkdir()) { "Never adopt an existing sandbox" }
        check(!context.getDatabasePath("$name.db").exists())
        val db = database()
        try {
            val now = Instant.parse("2026-10-16T00:00:00Z")
            db.territoryDao().insert(TerritoryEntity(request.territoryId, "READYCRASH", "Ready crash", "R", "D", now, now))
            val persistence = GpxPersistenceStore(db)
            val coordinator = GpxImportCoordinator(persistence, GpxOriginalStore(root), checkpoint = { at ->
                if (at == "FILE_PUBLISHED") runBlocking {
                    val job = requireNotNull(persistence.journal(request.requestUuid))
                    val graph = requireNotNull(persistence.readPending(requireNotNull(job.importId)))
                    File(root, "expected").writeText(listOf(job.importId, job.originalObjectId,
                        graph.tracks.single().id, graph.segments.single().id).joinToString("\n"))
                }
                checkpoint(at)
            })
            val runtime = GpxRuntime(coordinator, persistence)
            val workflow = workflow(runtime)
            val captured = workflow.capture()
            val bytes = "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"crash\"><trk><trkseg><trkpt lat=\"1\" lon=\"2\"/></trkseg></trk></gpx>".toByteArray()
            assertTrue(workflow.execute(captured, { bytes.inputStream() },
                displayName = "Полевой маршрут после перезапуска.gpx") is GpxPreparationResult.Ready)
        } finally { db.close() }
    }

    @Test fun b_recoverWithoutSourceOrNewIds(): Unit = runBlocking {
        val expected = File(root, "expected").readLines().map(UUID::fromString)
        val db = database()
        try {
            val persistence = GpxPersistenceStore(db)
            val coordinator = GpxImportCoordinator(persistence, GpxOriginalStore(root),
                newId = { error("Recovery must preserve identities") })
            val runtime = GpxRuntime(coordinator, persistence)
            assertTrue(runtime.reconcile().completed)
            val result = requireNotNull(workflow(runtime, recovering = true).lookup(request.requestUuid))
            assertEquals(expected[0], result.importId)
            assertEquals(expected[1], result.originalId)
            val graph = requireNotNull(runtime.readReady(request.territoryId, result.importId))
            assertEquals(expected[2], graph.tracks.single().id)
            assertEquals(expected[3], graph.segments.single().id)
            assertEquals(0, graph.samples.single().ordinal)
            assertEquals(request.requestUuid, graph.importRow.requestUuid)
            assertEquals("Полевой маршрут после перезапуска.gpx", graph.importRow.displayName)
            assertEquals("Полевой маршрут после перезапуска.gpx",
                runtime.listReady(request.territoryId).single().displayName)
            assertEquals(1L, persistence.counts(request.territoryId, "READY").imports)
            assertEquals(0L, persistence.counts(request.territoryId, "PENDING").imports)
            assertNull(persistence.journal(request.requestUuid))
            assertTrue(runtime.reconcile().completed)
            assertEquals(result, coordinator.abort(request.requestUuid))
            assertEquals(GpxOriginalPresence.PRESENT_VERIFIED, GpxOriginalStore(root).inspectFinal(result.originalId,
                GpxOriginalIdentity(graph.importRow.originalByteLength, graph.importRow.originalSha256)))
        } finally { db.close() }
        assertTrue(context.deleteDatabase("$name.db"))
        assertTrue(root.deleteRecursively())
    }
    private fun workflow(runtime: GpxRuntime, recovering: Boolean = false): GpxImportWorkflow {
        val settings = object : org.beesearch.app.domain.repository.SettingsRepository {
            private val value = org.beesearch.app.domain.model.AppSettings(request.territoryId, request.observerId)
            override val settings = kotlinx.coroutines.flow.flowOf(value)
            override suspend fun getSettings() = value
            override suspend fun setCurrentTerritoryId(territoryId: UUID?) = error("Not used")
            override suspend fun setCurrentObserverId(observerId: UUID?) = error("Not used")
            override suspend fun setInitialSetupOfferHandled(handled: Boolean) = error("Not used")
        }
        return GpxImportWorkflow(runtime, settings,
            org.beesearch.app.data.local.settings.SettingsSelectionGuard(),
            newRequestId = { check(!recovering) { "Recovery never captures another request" }; request.requestUuid })
    }

}
