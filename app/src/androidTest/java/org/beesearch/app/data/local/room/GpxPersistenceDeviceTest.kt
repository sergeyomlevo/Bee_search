package org.beesearch.app.data.local.room

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/** All READY writes here are isolated test fixtures, never a production activation path. */
@RunWith(AndroidJUnit4::class)
class GpxPersistenceDeviceTest {
    private lateinit var db: BeeSearchDatabase
    private fun id(n: Int) = UUID(0, n.toLong())
    private val territory = id(9000)
    private val observer = id(9001)
    private val store get() = GpxPersistenceStore(db)
    private val sql get() = db.openHelper.writableDatabase

    @Before fun prepare(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BeeSearchDatabase::class.java)
            .addCallback(GpxPersistenceSchema.callback).build()
        val now = Instant.parse("2026-10-15T00:00:00Z")
        db.territoryDao().insert(TerritoryEntity(territory, "GPX", "T", "R", "D", now, now))
        db.observerDao().insert(ObserverEntity(observer, "GPX", "L", "F", null, null, now, now))
    }
    @After fun close() { db.close() }

    @Test fun exactGraphRoundTripAndPendingIsolation(): Unit = runBlocking {
        val graph = graph(0)
        store.savePending(graph, job(graph, 50))
        val restored = requireNotNull(store.readPending(graph.importRow.id))
        assertEquals(graph.importRow, restored.importRow)
        assertEquals(graph.canonical(), restored.canonical())
        assertEquals(graph.warnings(), restored.warnings())
        assertNull(store.readReady(graph.importRow.id))
        assertEquals(GpxPersistenceCounts(1, 1), store.counts(territory, "PENDING"))
        assertEquals(GpxPersistenceCounts(0, 0), store.counts(territory, "READY"))
        assertEquals(1, restored.canonical().tracks[0].segments[0].samples.size)
        assertTrue(restored.canonical().tracks[0].segments[1].samples.isEmpty())
        assertTrue(restored.canonical().tracks[1].segments.isEmpty())
        val ready = graph(100).copy(importRow = graph(100).importRow.copy(publicationState = "READY"))
        rawGraph(ready)
        assertEquals(ready.canonical(), requireNotNull(store.readReady(ready.importRow.id)).canonical())
        assertNull(store.readPending(ready.importRow.id))
    }

    @Test fun allTimeStatusesAndExpandedCanonicalDecimalSurviveRoom(): Unit = runBlocking {
        val base = graph(0)
        val values = listOf(null, "1970-01-01T00:00:00.123456789Z", "2000-02-29T00:00:00", "bad")
            .map(org.beesearch.app.domain.gpx.GpxCanonicalValues::time)
        val decimal = org.beesearch.app.domain.gpx.GpxCanonicalValues.latitude("." + "1".repeat(127)).canonicalPlain
        val graph = base.copy(samples = values.mapIndexed { n, t ->
            base.samples[0].copy(ordinal = n, latitude = decimal, timeRaw = t.raw, timeStatus = t.status.name,
                timeEpochSeconds = t.epochSeconds, timeNanos = t.nanos)
        })
        store.savePending(graph, job(graph, 50))
        val restored = requireNotNull(store.readPending(graph.importRow.id))
        assertEquals(graph.canonical(), restored.canonical())
        assertEquals(values, restored.canonical().tracks[0].segments[0].samples.map { it.time })
        assertEquals(129, restored.samples[0].latitude.length)
    }

    @Test fun cleanupRetainsAbortDescriptorAndNeverDeletesReady(): Unit = runBlocking {
        val graph = graph(0); val job = job(graph, 50)
        store.savePending(graph, job)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.cleanupPending(job.requestUuid) } }
        db.gpxDao().updateJob(job.copy(intent = "ABORT"))
        store.cleanupPending(job.requestUuid); store.cleanupPending(job.requestUuid)
        assertNull(db.gpxDao().importById(graph.importRow.id))
        assertEquals(job.copy(intent = "ABORT", importId = null), db.gpxDao().jobByRequest(job.requestUuid))
        assertEquals(0L, store.sampleCount(graph.importRow.id))
        val ready = graph(100).let { it.copy(importRow = it.importRow.copy(publicationState = "READY")) }
        rawGraph(ready)
        val readyJob = job(ready, 150).copy(phase = "READY")
        db.gpxDao().insertJob(readyJob)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { store.cleanupPending(readyJob.requestUuid) } }
        assertNotNull(store.readReady(ready.importRow.id))
    }

    @Test fun sameShaSeparateIdsNullableRequestsAndUniqueConstraints(): Unit = runBlocking {
        val a = graph(0); val b = graph(100)
        store.savePending(a, job(a, 50)); store.savePending(b, job(b, 150))
        assertEquals(a.importRow.originalSha256, b.importRow.originalSha256)
        assertEquals(2L, store.counts(territory, "PENDING").imports)
        val ready = graph(200).let { it.copy(importRow = it.importRow.copy(publicationState = "READY", requestUuid = id(250))) }
        rawGraph(ready)
        val other = graph(300)
        rawGraph(other)
        assertThrows(SQLiteConstraintException::class.java) {
            sql.execSQL("UPDATE gpx_imports SET publication_state='READY',request_uuid=? WHERE id=?", arrayOf(id(250).toString(), other.importRow.id.toString()))
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { db.gpxDao().insertImport(ready.importRow) } }
        assertThrows(SQLiteConstraintException::class.java) { runBlocking { db.gpxDao().insertImport(a.importRow) } }
        assertThrows(SQLiteConstraintException::class.java) {
            sql.execSQL("UPDATE gpx_imports SET request_uuid=? WHERE id=?", arrayOf(id(500).toString(), a.importRow.id.toString()))
        }
    }

    @Test fun fkRestrictAndOwnerGuardsIncludeJobsAndImports(): Unit = runBlocking {
        val graph = graph(0); store.savePending(graph, job(graph, 50))
        listOf("DELETE FROM territories WHERE id='${territory}'", "DELETE FROM observers WHERE id='${observer}'",
            "DELETE FROM gpx_source_tracks WHERE id='${graph.tracks[0].id}'",
            "DELETE FROM gpx_track_segments WHERE id='${graph.segments[0].id}'",
            "DELETE FROM gpx_imports WHERE id='${graph.importRow.id}'").forEach {
            assertThrows(SQLiteConstraintException::class.java) { sql.execSQL(it) }
        }
        assertEquals(2L, db.gpxDao().countTerritoryReferences(territory))
        assertEquals(2L, db.observerDao().countGpxReferences(observer))
        assertThrows(SQLiteConstraintException::class.java) { runBlocking {
            db.gpxDao().insertTracks(listOf(GpxSourceTrackEntity(id(999), id(998), 0)))
        } }
    }

    @Test fun missingChangedAndUnexpectedTriggerFailBeforeStoreAccess() {
        val name = GpxPersistenceSchema.triggers.keys.first()
        sql.execSQL("DROP TRIGGER $name")
        assertThrows(GpxPersistenceIntegrityException::class.java) { runBlocking { store.counts(territory, "READY") } }
        sql.execSQL("CREATE TRIGGER $name BEFORE INSERT ON gpx_imports BEGIN SELECT 1; END")
        assertThrows(GpxPersistenceIntegrityException::class.java) { runBlocking { store.readReady(id(1)) } }
        sql.execSQL("DROP TRIGGER $name"); sql.execSQL(GpxPersistenceSchema.triggers.getValue(name))
        GpxPersistenceSchema.verify(sql)
        sql.execSQL("CREATE TRIGGER gpx_validate_extra BEFORE INSERT ON gpx_imports BEGIN SELECT 1; END")
        assertThrows(GpxPersistenceIntegrityException::class.java) { runBlocking { store.counts(territory, "PENDING") } }
    }

    @Test fun tableReconstructionMustRestoreItsExactTriggers() {
        sql.execSQL("DROP TABLE gpx_track_samples")
        sql.execSQL(GpxPersistenceSchema.tables.single { it.startsWith("CREATE TABLE IF NOT EXISTS gpx_track_samples ") })
        assertThrows(GpxPersistenceIntegrityException::class.java) { GpxPersistenceSchema.verify(sql) }
        GpxPersistenceSchema.triggers.filterKeys { it.contains("gpx_track_samples_") }.values.forEach(sql::execSQL)
        GpxPersistenceSchema.verify(sql)
    }

    @Test fun structuralInsertAndUpdateFailuresAreExplicit(): Unit = runBlocking {
        val graph = graph(0); rawGraph(graph)
        val invalidUpdates = listOf(
            "UPDATE gpx_imports SET publication_state='BROKEN'", "UPDATE gpx_imports SET original_byte_length=0",
            "UPDATE gpx_imports SET unsupported_content_count=-1", "UPDATE gpx_imports SET unsupported_content_count=1",
            "UPDATE gpx_source_tracks SET ordinal=-1", "UPDATE gpx_track_segments SET ordinal=-1",
            "UPDATE gpx_track_samples SET time_status='UTC',time_nanos=0,time_epoch_seconds=NULL",
            "UPDATE gpx_track_samples SET time_status='MISSING'", "UPDATE gpx_track_samples SET ordinal=-1",
        )
        invalidUpdates.forEach { statement -> assertThrows(SQLiteConstraintException::class.java) { sql.execSQL(statement) } }
        rejected { db.gpxDao().insertSamples(listOf(graph.samples[0].copy(ordinal = 1, timeStatus = "UTC", timeEpochSeconds = 0, timeNanos = 1000000000))) }
        rejected { db.gpxDao().insertTracks(listOf(graph.tracks[0].copy(id = id(999)))) }
        rejected { db.gpxDao().insertSamples(listOf(graph.samples[0].copy(ordinal = -1))) }
    }

    @Test fun journalPhaseAndIntentMatrix(): Unit = runBlocking {
        val graph = graph(0); rawGraph(graph)
        val ready = graph(100).let { it.copy(importRow = it.importRow.copy(publicationState = "READY")) }; rawGraph(ready)
        val phases = listOf("SELECTED", "COPYING", "COPIED", "PARSING", "PARSED", "FILE_PUBLISHED", "READY")
        phases.forEachIndexed { index, phase ->
            for (intent in listOf("CONTINUE", "ABORT")) {
                val record = GpxImportJobEntity(id(500 + index), territory, observer, phase, intent,
                    if (phase == "SELECTED") null else id(700 + index),
                    if (index < 2) null else "a".repeat(64), if (index < 2) null else 3,
                    if (index < 4 || intent == "ABORT") null else if (phase == "READY") ready.importRow.id else graph.importRow.id)
                if (phase == "READY" && intent == "ABORT") {
                    rejected { db.gpxDao().insertJob(record) }
                } else {
                    db.gpxDao().insertJob(record)
                    rejected { db.gpxDao().updateJob(record.copy(phase = "INVALID")) }
                    sql.execSQL("DELETE FROM gpx_import_jobs WHERE request_uuid=?", arrayOf(record.requestUuid.toString()))
                }
            }
        }
        rejected { db.gpxDao().insertJob(GpxImportJobEntity(id(800), territory, observer, "COPIED", "CONTINUE", id(801))) }
        rejected { db.gpxDao().insertJob(GpxImportJobEntity(id(802), territory, observer, "PARSED", "CONTINUE", id(803), "a".repeat(64), 3, null)) }
    }

    @Test fun lateJournalConflictRollsBackAlreadyInsertedGraph(): Unit = runBlocking {
        val graph = graph(0)
        // The graph itself is valid; a different operational job already owns O.
        db.gpxDao().insertJob(job(graph, 80).copy(phase = "PARSING", importId = null))
        assertThrows(SQLiteConstraintException::class.java) { runBlocking { store.savePending(graph, job(graph, 81)) } }
        assertNull(db.gpxDao().importById(graph.importRow.id))
        assertTrue(db.gpxDao().tracks(graph.importRow.id).isEmpty())
        assertEquals(0L, store.counts(territory, "PENDING").samples)
        assertNotNull(db.gpxDao().jobByRequest(id(80)))
    }

    @Test fun corruptedSemanticTextFailsExplicitlyWithoutReparse(): Unit = runBlocking {
        val graph = graph(0); rawGraph(graph)
        sql.execSQL("UPDATE gpx_track_samples SET latitude='1.0'")
        assertThrows(GpxPersistenceIntegrityException::class.java) { runBlocking { store.readPending(graph.importRow.id) } }
    }

    private fun rejected(block: suspend () -> Unit) {
        assertThrows(SQLiteConstraintException::class.java) { runBlocking { block() } }
    }
    private suspend fun rawGraph(graph: GpxStoredGraph) {
        db.gpxDao().insertImport(graph.importRow.copy(publicationState = "PENDING", requestUuid = null))
        db.gpxDao().insertTracks(graph.tracks)
        db.gpxDao().insertSegments(graph.segments); db.gpxDao().insertSamples(graph.samples)
        if (graph.importRow.publicationState == "READY") {
            sql.execSQL("UPDATE gpx_imports SET publication_state='READY',request_uuid=? WHERE id=?", arrayOf(graph.importRow.requestUuid?.toString(), graph.importRow.id.toString()))
        }
    }
    private fun graph(offset: Int) = GpxStoredGraph(
        GpxImportEntity(id(1 + offset), territory, observer, id(2 + offset), "a".repeat(64), 3, "x.gpx", "1.1", "GPX_1_1_STAGE_1", 1760000000000, "creator",
            sourceName = "source", sourceDescription = "description", authorName = "author",
            unsupportedTimeCount = 1, unsupportedTimeFirstScope = "SAMPLE", unsupportedTimeFirstTrackOrdinal = 0, unsupportedTimeFirstSegmentOrdinal = 0, unsupportedTimeFirstSampleOrdinal = 0),
        listOf(GpxSourceTrackEntity(id(3 + offset), id(1 + offset), 0, "track", "desc"), GpxSourceTrackEntity(id(5 + offset), id(1 + offset), 1)),
        listOf(GpxTrackSegmentEntity(id(4 + offset), id(3 + offset), 0), GpxTrackSegmentEntity(id(6 + offset), id(3 + offset), 1)),
        listOf(GpxTrackSampleEntity(id(4 + offset), 0, "1.1234567890123456789", "2", "3.25", "bad-time", "UNSUPPORTED")),
    )
    private fun job(graph: GpxStoredGraph, request: Int) = GpxImportJobEntity(id(request), territory, observer, "PARSED", "CONTINUE", graph.importRow.originalObjectId, graph.importRow.originalSha256, graph.importRow.originalByteLength, graph.importRow.id)
}
