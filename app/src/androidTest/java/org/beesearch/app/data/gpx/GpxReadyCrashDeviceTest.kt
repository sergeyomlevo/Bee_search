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
class GpxReadyCrashDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val name get() = (arguments.getString("readySandbox") ?: "gpx-ready-normal").also {
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
            val coordinator = GpxImportCoordinator(persistence, GpxOriginalStore(root), checkpoint = ::checkpoint)
            val bytes = "<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"crash\"><trk><trkseg><trkpt lat=\"1\" lon=\"2\"/></trkseg></trk></gpx>".toByteArray()
            val prepared = coordinator.prepare(request, { bytes.inputStream() }, { true }, { true }) as GpxPreparationResult.Prepared
            val graph = requireNotNull(persistence.readPending(prepared.importId))
            // Test-owned witness survives app process death; it is not production recovery evidence.
            File(root, "expected").writeText(listOf(prepared.importId, prepared.originalId,
                graph.tracks.single().id, graph.segments.single().id).joinToString("\n"))
            assertTrue(coordinator.complete(request, isCapturedRequestCurrent = { true }) is GpxPreparationResult.Ready)
        } finally { db.close() }
    }

    @Test fun b_recoverWithoutSourceOrNewIds(): Unit = runBlocking {
        val expected = File(root, "expected").readLines().map(UUID::fromString)
        val db = database()
        try {
            val persistence = GpxPersistenceStore(db)
            val coordinator = GpxImportCoordinator(persistence, GpxOriginalStore(root),
                newId = { error("Recovery must preserve identities") })
            val result = coordinator.recover().singleOrNull() as GpxPreparationResult.Ready?
                ?: requireNotNull(coordinator.lookup(request.requestUuid))
            assertEquals(expected[0], result.importId)
            assertEquals(expected[1], result.originalId)
            val graph = requireNotNull(persistence.readReady(result.importId))
            assertEquals(expected[2], graph.tracks.single().id)
            assertEquals(expected[3], graph.segments.single().id)
            assertEquals(0, graph.samples.single().ordinal)
            assertEquals(request.requestUuid, graph.importRow.requestUuid)
            assertEquals(1L, persistence.counts(request.territoryId, "READY").imports)
            assertEquals(0L, persistence.counts(request.territoryId, "PENDING").imports)
            assertNull(persistence.journal(request.requestUuid))
            assertTrue(coordinator.recover().isEmpty())
            assertEquals(result, coordinator.abort(request.requestUuid))
            assertEquals(GpxOriginalPresence.PRESENT_VERIFIED, GpxOriginalStore(root).inspectFinal(result.originalId,
                GpxOriginalIdentity(graph.importRow.originalByteLength, graph.importRow.originalSha256)))
        } finally { db.close() }
        assertTrue(context.deleteDatabase("$name.db"))
        assertTrue(root.deleteRecursively())
    }
}
