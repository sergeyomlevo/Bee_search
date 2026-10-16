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

/** Real host force-stop with durable Room, distinct from throwable fault injection. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GpxCoordinatorCrashDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val name get() = (arguments.getString("coordinatorSandbox") ?: "gpx-coordinator-normal").also {
        require(it.matches(Regex("gpx-coordinator-[a-zA-Z0-9-]{1,70}")))
    }
    private val boundary get() = arguments.getString("coordinatorBoundary") ?: "FILE_PUBLISHED"
    private val root get() = File(context.filesDir, name)
    private val request = UUID(0, 8100)
    private val territory = UUID(0, 8101)
    private fun database() = Room.databaseBuilder(context, BeeSearchDatabase::class.java, "$name.db")
        .addCallback(GpxPersistenceSchema.callback).build()
    private fun checkpoint(actual: String) {
        if (actual != boundary || arguments.getString("coordinatorKill") != "true") return
        File(root, "boundary").writeText(boundary)
        repeat(120) { Thread.sleep(500) }
        throw AssertionError("Host did not stop the DEV process")
    }

    @Test fun a_prepareAndStopAtDurableBoundary(): Unit = runBlocking {
        check(root.mkdir()) { "Do not adopt an existing sandbox" }
        check(!context.getDatabasePath("$name.db").exists())
        val db = database()
        try {
            val now = Instant.parse("2026-10-16T00:00:00Z")
            db.territoryDao().insert(TerritoryEntity(territory, "CRASH", "Crash", "R", "D", now, now))
            val coordinator = GpxImportCoordinator(GpxPersistenceStore(db),
                GpxOriginalStore(root) { checkpoint("STORE_" + it.name) }, checkpoint = ::checkpoint)
            val bytes = buildString {
                append("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"crash\"><trk><trkseg>")
                repeat(2048) { append("<trkpt lat=\"1\" lon=\"2\"/>") }
                append("</trkseg></trk></gpx>")
            }.toByteArray()
            val result = coordinator.prepare(GpxImportRequest(request, territory, null, "crash.gpx", 0),
                { bytes.inputStream() }, { true }, { true })
            assertTrue(result is GpxPreparationResult.Prepared)
        } finally { db.close() }
    }

    @Test fun b_recoverInFreshProcessWithoutSourceOrNewIds(): Unit = runBlocking {
        val db = database()
        try {
            val persistence = GpxPersistenceStore(db)
            val before = requireNotNull(persistence.journal(request))
            val graph = before.importId?.let { persistence.readPending(it) }
            val coordinator = GpxImportCoordinator(persistence, GpxOriginalStore(root),
                newId = { error("Recovery cannot allocate a new identity") })
            val result = coordinator.recover().single()
            val shouldPrepare = boundary in setOf("PARSED", "FINAL_VERIFIED", "FILE_PUBLISHED")
            if (shouldPrepare) {
                val prepared = result as GpxPreparationResult.Prepared
                assertEquals(before.importId, prepared.importId)
                assertEquals(before.originalObjectId, prepared.originalId)
                assertEquals(graph, persistence.readPending(prepared.importId))
                assertNull(persistence.readReady(prepared.importId))
                assertEquals("FILE_PUBLISHED", requireNotNull(persistence.journal(request)).phase)
                assertEquals(listOf(result), coordinator.recover())
                assertEquals(GpxPreparationResult.Aborted(request), coordinator.abort(request))
            } else {
                if (boundary == "STORE_FINAL_COPY") {
                    assertTrue(result is GpxPreparationResult.Failed)
                    assertFalse((result as GpxPreparationResult.Failed).cleanupPending)
                } else assertEquals(GpxPreparationResult.Aborted(request), result)
                assertTrue(coordinator.recover().isEmpty())
            }
            assertNull(persistence.journal(request))
            assertEquals(0L, persistence.counts(territory, "PENDING").imports)
            assertEquals(0L, persistence.counts(territory, "READY").imports)
        } finally { db.close() }
        // Only the explicitly named, validated, unique test DB and sandbox are removed.
        assertTrue(context.deleteDatabase("$name.db"))
        assertTrue(root.deleteRecursively())
    }
}
