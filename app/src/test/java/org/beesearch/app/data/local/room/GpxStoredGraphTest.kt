package org.beesearch.app.data.local.room

import java.util.UUID
import org.beesearch.app.domain.gpx.GpxCanonicalDraft
import org.beesearch.app.domain.gpx.GpxCanonicalValues
import org.beesearch.app.domain.gpx.GpxMetadata
import org.beesearch.app.domain.gpx.GpxSample
import org.beesearch.app.domain.gpx.GpxSampleTime
import org.beesearch.app.domain.gpx.GpxSegment
import org.beesearch.app.domain.gpx.GpxTimeStatus
import org.beesearch.app.domain.gpx.GpxTrack
import org.beesearch.app.domain.gpx.GpxProfileWarning
import org.beesearch.app.domain.gpx.GpxWarningCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class GpxStoredGraphTest {
    @Test
    fun canonicalRoundTripPreservesIndependentHighPrecisionDecimal() {
        val source = "." + "1".repeat(127)
        val latitude = GpxCanonicalValues.latitude(source)
        assertEquals(129, latitude.canonicalPlain.length)
        val graph = graph(samples = listOf(sample(latitude = latitude.canonicalPlain)))

        val expected = GpxCanonicalDraft(
            GpxMetadata("creator"),
            listOf(GpxTrack(0, listOf(GpxSegment(0, listOf(
                GpxSample(0, latitude, GpxCanonicalValues.longitude("0")),
            ))))),
        )
        assertEquals(expected, graph.canonical())
        assertEquals(latitude.canonicalPlain, graph.canonical().tracks.single().segments.single().samples.single().lat.canonicalPlain)
    }

    @Test
    fun canonicalRoundTripPreservesAllTimeStatusesAndRawValues() {
        val times = listOf(
            GpxCanonicalValues.time(null),
            GpxCanonicalValues.time("1970-01-01T00:00:00.123456789Z"),
            GpxCanonicalValues.time("2000-02-29T23:59:59.1"),
            GpxCanonicalValues.time("2000-01-01T00:00:00.1234567890Z"),
        )
        val rows = times.mapIndexed { index, time ->
            sample(segmentId = UUID(0, 100L + index), time = time)
        }
        val graph = graph(
            segments = rows.indices.map { GpxTrackSegmentEntity(UUID(0, 100L + it), trackId, it) },
            samples = rows,
        )

        val actual = graph.canonical().tracks.single().segments.flatMap { it.samples }.map { it.time }
        assertEquals(times, actual)
        assertEquals(listOf(GpxTimeStatus.MISSING, GpxTimeStatus.UTC, GpxTimeStatus.NO_ZONE, GpxTimeStatus.UNSUPPORTED), actual.map { it.status })
        assertEquals(123456789, actual[1].nanos)
        assertEquals("2000-01-01T00:00:00.1234567890Z", actual[3].raw)
    }

    @Test
    fun canonicalRoundTripRetainsEmptyTracksSegmentsAndLocalOrdinals() {
        val emptyTrack = GpxSourceTrackEntity(trackId, importId, 0)
        val populatedTrack = GpxSourceTrackEntity(UUID.fromString("00000000-0000-0000-0000-000000000004"), importId, 1)
        val emptySegment = GpxTrackSegmentEntity(UUID.fromString("00000000-0000-0000-0000-000000000005"), populatedTrack.id, 0)
        val populatedSegment = GpxTrackSegmentEntity(UUID.fromString("00000000-0000-0000-0000-000000000006"), populatedTrack.id, 1)
        val graph = graph(
            tracks = listOf(emptyTrack, populatedTrack),
            segments = listOf(emptySegment, populatedSegment),
            samples = listOf(sample(segmentId = populatedSegment.id)),
        )

        val canonical = graph.canonical()
        assertEquals(listOf(0, 1), canonical.tracks.map { it.ordinal })
        assertTrue(canonical.tracks[0].segments.isEmpty())
        assertEquals(listOf(0, 1), canonical.tracks[1].segments.map { it.ordinal })
        assertTrue(canonical.tracks[1].segments[0].samples.isEmpty())
        assertEquals(0, canonical.tracks[1].segments[1].samples.single().ordinal)
    }

    @Test
    fun warningsRoundTripCountsAndOriginalAddresses() {
        val row = baseImport.copy(
            unsupportedContentCount = 2,
            unsupportedContentFirstScope = "SEGMENT",
            unsupportedContentFirstTrackOrdinal = 1,
            unsupportedContentFirstSegmentOrdinal = 2,
            unsupportedTimeCount = 1,
            unsupportedTimeFirstScope = "SAMPLE",
            unsupportedTimeFirstTrackOrdinal = 1,
            unsupportedTimeFirstSegmentOrdinal = 2,
            unsupportedTimeFirstSampleOrdinal = 3,
        )
        val warnings = graph(importRow = row).warnings()

        assertEquals(2, warnings.size)
        assertEquals(GpxWarningCategory.UNSUPPORTED_CONTENT, warnings[0].category)
        assertEquals(2, warnings[0].count)
        assertEquals("SEGMENT", warnings[0].firstAddress.scope.name)
        assertEquals(1, warnings[0].firstAddress.track)
        assertEquals(2, warnings[0].firstAddress.segment)
        assertEquals(3, warnings[1].firstAddress.sample)
        assertEquals(GpxProfileWarning(GpxWarningCategory.UNSUPPORTED_TIME, 1,
            warnings[1].firstAddress), warnings[1])
    }

    @Test
    fun noncanonicalOrMalformedStoredDecimalsFailClosed() {
        listOf("-0", "0.0", "1E0").forEach { value ->
            assertThrows(GpxPersistenceIntegrityException::class.java) {
                graph(samples = listOf(sample(latitude = value))).canonical()
            }
        }
    }

    @Test
    fun outOfRangeStoredCoordinateFailsClosed() {
        assertThrows(GpxPersistenceIntegrityException::class.java) {
            graph(samples = listOf(sample(latitude = "90.0000000000000000000000001"))).canonical()
        }
        assertThrows(GpxPersistenceIntegrityException::class.java) {
            graph(samples = listOf(sample(longitude = "180.0000000000000000000000001"))).canonical()
        }
    }

    @Test
    fun invalidStoredTimeStatusAndNullabilityFailClosed() {
        val invalid = listOf(
            sample(timeStatus = "NOPE"),
            sample(timeRaw = null, timeStatus = "UTC", epochSeconds = 0, nanos = 0),
            sample(timeRaw = "x", timeStatus = "MISSING"),
            sample(timeRaw = "x", timeStatus = "NO_ZONE", epochSeconds = 0, nanos = 0),
            sample(timeRaw = "x", timeStatus = "UTC", epochSeconds = 0, nanos = 1_000_000_000),
        )
        invalid.forEach { row ->
            assertThrows(GpxPersistenceIntegrityException::class.java) {
                graph(samples = listOf(row)).canonical()
            }
        }
    }

    @Test
    fun orphanDuplicateAndNoncontiguousGraphAddressesFailClosed() {
        val orphan = sample(segmentId = UUID.fromString("00000000-0000-0000-0000-000000000099"))
        assertThrows(GpxPersistenceIntegrityException::class.java) {
            graph(samples = listOf(orphan)).canonical()
        }

        val duplicateTrack = GpxSourceTrackEntity(trackId, importId, 0)
        assertThrows(GpxPersistenceIntegrityException::class.java) {
            graph(tracks = listOf(duplicateTrack, duplicateTrack)).canonical()
        }

        val noncontiguousId = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val noncontiguousSegmentId = UUID.fromString("00000000-0000-0000-0000-000000000005")
        val noncontiguous = GpxSourceTrackEntity(noncontiguousId, importId, 2)
        assertThrows(GpxPersistenceIntegrityException::class.java) {
            graph(
                tracks = listOf(noncontiguous),
                segments = listOf(GpxTrackSegmentEntity(noncontiguousSegmentId, noncontiguousId, 0)),
                samples = listOf(sample(segmentId = noncontiguousSegmentId)),
            ).canonical()
        }
    }

    @Test
    fun pendingEnvelopeRejectsRequestCorrelationAndSnapshotCopiesLists() {
        assertThrows(GpxPersistenceIntegrityException::class.java) {
            graph(importRow = baseImport.copy(requestUuid = UUID(0, 99))).validate()
        }

        val source = graph()
        val snapshot = source.snapshot()
        assertNotSame(source.tracks, snapshot.tracks)
        assertNotSame(source.segments, snapshot.segments)
        assertNotSame(source.samples, snapshot.samples)
        assertEquals(source.importRow.id, snapshot.importRow.id)
        assertEquals(source.tracks.map { it.id }, snapshot.tracks.map { it.id })
        assertEquals(source.segments.map { it.id }, snapshot.segments.map { it.id })
        assertEquals(source.samples.map { it.segmentId to it.ordinal }, snapshot.samples.map { it.segmentId to it.ordinal })
        assertFalse(snapshot.tracks === source.tracks)
        assertNull(snapshot.importRow.requestUuid)
    }

    private fun graph(
        importRow: GpxImportEntity = baseImport,
        tracks: List<GpxSourceTrackEntity> = listOf(GpxSourceTrackEntity(trackId, importId, 0)),
        segments: List<GpxTrackSegmentEntity> = listOf(GpxTrackSegmentEntity(segmentId, trackId, 0)),
        samples: List<GpxTrackSampleEntity> = listOf(sample()),
    ) = GpxStoredGraph(importRow, tracks, segments, samples)

    private fun sample(
        segmentId: UUID = GpxStoredGraphTest.segmentId,
        latitude: String = "0",
        longitude: String = "0",
        time: GpxSampleTime = GpxCanonicalValues.time(null),
        timeRaw: String? = time.raw,
        timeStatus: String = time.status.name,
        epochSeconds: Long? = time.epochSeconds,
        nanos: Int? = time.nanos,
    ) = GpxTrackSampleEntity(segmentId, 0, latitude, longitude, null, timeRaw, timeStatus, epochSeconds, nanos)

    private companion object {
        val importId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val trackId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val segmentId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val baseImport = GpxImportEntity(
            id = importId,
            territoryId = UUID.fromString("00000000-0000-0000-0000-000000000010"),
            originalObjectId = UUID.fromString("00000000-0000-0000-0000-000000000011"),
            originalSha256 = "a".repeat(64),
            originalByteLength = 1,
            gpxVersion = "1.1",
            parserProfileVersion = "GPX_1_1_STAGE_1",
            importedAtEpochMs = 1,
            creator = "creator",
        )
    }
}
