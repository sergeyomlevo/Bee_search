package org.beesearch.app.data.gpx

import java.util.UUID
import org.beesearch.app.data.local.room.GpxImportJobEntity
import org.beesearch.app.domain.gpx.*
import org.junit.Assert.*
import org.junit.Test

class GpxPendingGraphTest {
    private val time = GpxCanonicalValues.time("2000-01-01T00:00:00.123456789Z")
    private val draft = GpxCanonicalDraft(GpxMetadata("source", "name", "description", "author"), listOf(
        GpxTrack(0, emptyList()),
        GpxTrack(1, listOf(GpxSegment(0, emptyList()), GpxSegment(1, listOf(
            GpxSample(0, GpxCanonicalValues.latitude(".1234567890123456789012345"),
                GpxCanonicalValues.longitude("-180"), GpxCanonicalValues.elevation("123.456789"), time),
        ))), "track", "desc"),
    ))
    private val warnings = GpxFrozenList(listOf(
        GpxProfileWarning(GpxWarningCategory.UNSUPPORTED_CONTENT, 3, GpxProfileAddress(GpxProfileScope.TRACK, 1)),
        GpxProfileWarning(GpxWarningCategory.UNSUPPORTED_TIME, 1, GpxProfileAddress(GpxProfileScope.SAMPLE, 1, 1, 0)),
    ))
    private val accepted = GpxParseResult.Accepted(draft, GpxOriginalIdentity(101, "ab".repeat(32)), warnings)
    private val job = GpxImportJobEntity(UUID(0, 1), UUID(0, 2), UUID(0, 3), "PARSING", "CONTINUE",
        UUID(0, 4), accepted.originalIdentity.sha256, accepted.originalIdentity.length)

    @Test fun exactCanonicalWarningsProvenanceAndEmptyStructuresRoundTrip() {
        var next = 10L
        val graph = pendingGpxGraph(accepted, job, "source.gpx", 12345) { UUID(0, next++) }
        assertEquals(draft, graph.canonical())
        assertEquals(warnings, graph.warnings())
        assertEquals(job.territoryId, graph.importRow.territoryId)
        assertEquals(job.importingObserverId, graph.importRow.importingObserverId)
        assertEquals(job.originalObjectId, graph.importRow.originalObjectId)
        assertEquals(12345L, graph.importRow.importedAtEpochMs)
        assertEquals("source.gpx", graph.importRow.displayName)
        assertEquals("PENDING", graph.importRow.publicationState)
        assertNull(graph.importRow.requestUuid)
        assertEquals(listOf(0, 1), graph.tracks.map { it.ordinal })
        assertEquals(listOf(0, 1), graph.segments.map { it.ordinal })
    }

    @Test fun equalCanonicalBytesDoNotDeduplicatePersistentIdentities() {
        var next = 100L
        val first = pendingGpxGraph(accepted, job, null, 0) { UUID(0, next++) }
        val second = pendingGpxGraph(accepted, job.copy(originalObjectId = UUID(0, 99)), null, 0) { UUID(0, next++) }
        assertEquals(first.canonical(), second.canonical())
        assertEquals(first.importRow.originalSha256, second.importRow.originalSha256)
        assertNotEquals(first.importRow.id, second.importRow.id)
        assertNotEquals(first.importRow.originalObjectId, second.importRow.originalObjectId)
        assertTrue(first.tracks.map { it.id }.toSet().intersect(second.tracks.map { it.id }.toSet()).isEmpty())
        assertTrue(first.segments.map { it.id }.toSet().intersect(second.segments.map { it.id }.toSet()).isEmpty())
    }
}
