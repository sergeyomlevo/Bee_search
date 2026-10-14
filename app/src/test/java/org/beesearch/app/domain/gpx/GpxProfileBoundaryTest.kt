package org.beesearch.app.domain.gpx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

/** JVM-only boundary coverage over hand-normalized events; no XML parser or provider claim. */
class GpxProfileBoundaryTest {
    private val ns = GpxProfileSchema.NAMESPACE

    private fun start(name: String, attrs: List<GpxProfileAttribute> = emptyList()) =
        GpxProfileEvent.Start(name, ns, attrs)

    private fun end(name: String) = GpxProfileEvent.End(name, ns)

    private fun root(creator: String = "fixture") = sequence {
        yield(start("gpx", listOf(attr("version", "1.1"), attr("creator", creator))))
    }

    private fun attr(name: String, value: String) = GpxProfileAttribute(name, "", value)

    private fun point(lat: String = "0", lon: String = "0", body: Sequence<GpxProfileEvent> = emptySequence()) = sequence {
        yield(start("trkpt", listOf(attr("lat", lat), attr("lon", lon))))
        yieldAll(body)
        yield(end("trkpt"))
    }

    private fun sampleSegment(sampleCount: Int, lat: String = "0") = sequence {
        yield(start("trkseg"))
        repeat(sampleCount) { yieldAll(point(lat = if (it == 0) lat else "0")) }
        yield(end("trkseg"))
    }

    private fun parse(events: Sequence<GpxProfileEvent>): GpxProfileResult {
        val parser = GpxProfileParser()
        events.forEach(parser::on)
        return parser.finish()
    }

    private fun assertFailure(
        events: Sequence<GpxProfileEvent>,
        category: String,
        reason: String,
        assertInactive: Boolean = true,
    ) {
        val parser = GpxProfileParser()
        val error = assertThrows(GpxProfileFailure::class.java) { events.forEach(parser::on) }
        assertEquals(category, error.category)
        assertEquals(reason, error.reason)
        if (assertInactive) {
            val inactive = assertThrows(GpxProfileFailure::class.java) { parser.on(GpxProfileEvent.ProcessingInstruction) }
            assertEquals("INVALID_STRUCTURE", inactive.category)
            assertEquals("PROFILE_NOT_ACTIVE", inactive.reason)
            val incomplete = assertThrows(GpxProfileFailure::class.java) { parser.finish() }
            assertEquals("INVALID_STRUCTURE", incomplete.category)
            assertEquals("INCOMPLETE_PROFILE", incomplete.reason)
        }
    }

    private fun tracks(count: Int) = sequence {
        yieldAll(root())
        repeat(count) { track ->
            yield(start("trk"))
            if (track == 0) yieldAll(sampleSegment(1))
            yield(end("trk"))
        }
        yield(end("gpx"))
    }

    private fun segments(total: Int) = sequence {
        yieldAll(root())
        val firstTrack = total / 2
        val secondTrack = total - firstTrack
        yield(start("trk"))
        repeat(firstTrack) { segment ->
            yieldAll(sampleSegment(if (segment == 0) 1 else 0))
        }
        yield(end("trk"))
        yield(start("trk"))
        repeat(secondTrack) { yieldAll(sampleSegment(0)) }
        yield(end("trk"))
        yield(end("gpx"))
    }

    private fun samples(count: Int) = sequence {
        yieldAll(root())
        yield(start("trk"))
        yieldAll(sampleSegment(count))
        yield(end("trk"))
        yield(end("gpx"))
    }

    @Test
    fun g02TrackBoundariesIncludeEmptyTracksAndPreserveOrdinals() {
        for (count in listOf(127, 128)) {
            val result = parse(tracks(count))
            assertEquals(count, result.draft.tracks.size)
            assertEquals((0 until count).toList(), result.draft.tracks.map { it.ordinal })
            assertEquals(1, result.draft.tracks.first().segments.single().samples.size)
            assertTrue(result.draft.tracks.drop(1).all { it.segments.isEmpty() })
        }
        assertFailure(tracks(129), "LIMIT_EXCEEDED", "G02")
    }

    @Test
    fun g03SegmentBoundariesSpanTwoTracksAndIncludeEmptySegments() {
        for (total in listOf(4095, 4096)) {
            val result = parse(segments(total))
            assertEquals(2, result.draft.tracks.size)
            assertEquals(total, result.draft.tracks.sumOf { it.segments.size })
            assertEquals((0 until total / 2).toList(), result.draft.tracks[0].segments.map { it.ordinal })
            assertEquals((0 until total - total / 2).toList(), result.draft.tracks[1].segments.map { it.ordinal })
            assertEquals(1, result.draft.tracks.first().segments.first().samples.size)
            assertTrue(result.draft.tracks.flatMap { it.segments }.drop(1).all { it.samples.isEmpty() })
        }
        assertFailure(segments(4097), "LIMIT_EXCEEDED", "G03")
    }

    @Test
    fun g04SampleBoundariesPreserveCountAndOrdinalsWithoutPartialFailureResult() {
        for (count in listOf(99999, 100000)) {
            val result = parse(samples(count))
            val actual = result.draft.tracks.single().segments.single().samples
            assertEquals(count, actual.size)
            assertEquals((0 until count).toList(), actual.map { it.ordinal })
        }
        assertFailure(samples(100001), "LIMIT_EXCEEDED", "G04")
    }

    @Test
    fun g08CreatorAndFragmentedNameCountUnicodeScalarsBeforeRetention() {
        for (count in listOf(1023, 1024)) {
            val creator = "c".repeat(count)
            val result = parse(sequence {
                yield(start("gpx", listOf(attr("version", "1.1"), attr("creator", creator))))
                yield(start("metadata")); yield(start("name"))
                yield(GpxProfileEvent.Text("n".repeat(count - 1)))
                yield(GpxProfileEvent.Text("\ud83d")); yield(GpxProfileEvent.Text("\ude00"))
                yield(end("name")); yield(end("metadata"))
                yield(start("trk")); yieldAll(sampleSegment(1)); yield(end("trk"))
                yield(end("gpx"))
            })
            assertEquals(creator, result.draft.metadata.creator)
            assertEquals("n".repeat(count - 1) + "😀", result.draft.metadata.sourceName)
        }
        assertFailure(sequence {
            yield(start("gpx", listOf(attr("version", "1.1"), attr("creator", "c".repeat(1025)))))
            yield(end("gpx"))
        }, "LIMIT_EXCEEDED", "G08")
        assertFailure(sequence {
            yieldAll(root()); yield(start("metadata")); yield(start("name"))
            yield(GpxProfileEvent.Text("n".repeat(1024))); yield(GpxProfileEvent.Text("x"))
        }, "LIMIT_EXCEEDED", "G08")
    }

    @Test
    fun g10NumericCoordinateAndTimeCountBeforeTrim() {
        val validTime = "2000-01-01T00:00:00Z"
        for (count in listOf(127, 128)) {
            val coordinate = " ".repeat(count - 1) + "0"
            val result = parse(sequence {
                yieldAll(root()); yield(start("trk")); yield(start("trkseg"))
                yieldAll(point(lat = coordinate, body = sequenceOf(start("time"), GpxProfileEvent.Text(" ".repeat(count - validTime.length) + validTime), end("time"))))
                yield(end("trkseg")); yield(end("trk")); yield(end("gpx"))
            })
            assertEquals("0", result.draft.tracks.single().segments.single().samples.single().lat.toString())
            assertEquals(GpxTimeStatus.UTC, result.draft.tracks.single().segments.single().samples.single().time.status)
        }
        assertFailure(sequence {
            yieldAll(root()); yield(start("trk")); yield(start("trkseg"))
            yieldAll(point(lat = " ".repeat(128) + "0")); yield(end("trkseg")); yield(end("trk")); yield(end("gpx"))
        }, "LIMIT_EXCEEDED", "G10")
        assertFailure(sequence {
            yieldAll(root()); yield(start("trk")); yield(start("trkseg")); yieldAll(point(body = sequenceOf(
                start("time"), GpxProfileEvent.Text(" ".repeat(109) + validTime), end("time"))))
        }, "LIMIT_EXCEEDED", "G10")
    }

    @Test
    fun cancellationNearLimitAndDuringFinalFinishNeverExposeAcceptedResult() {
        var starts = 0
        val parser = GpxProfileParser {
            if (starts++ >= 199998) throw CancellationException("near limit")
        }
        val nearLimit = assertThrows(CancellationException::class.java) { samples(100000).forEach(parser::on) }
        assertEquals("near limit", nearLimit.message)
        val inactive = assertThrows(GpxProfileFailure::class.java) { parser.finish() }
        assertEquals("INVALID_STRUCTURE", inactive.category)
        assertEquals("INCOMPLETE_PROFILE", inactive.reason)

        var armed = false
        var checks = 0
        val preFreeze = GpxProfileParser {
            if (armed) throw CancellationException("pre-freeze")
        }
        samples(1).forEach(preFreeze::on)
        armed = true
        assertThrows(CancellationException::class.java) { preFreeze.finish() }
        val afterPreFreeze = assertThrows(GpxProfileFailure::class.java) { preFreeze.finish() }
        assertEquals("INVALID_STRUCTURE", afterPreFreeze.category)
        assertEquals("INCOMPLETE_PROFILE", afterPreFreeze.reason)

        armed = false
        val postFreeze = GpxProfileParser {
            if (armed && ++checks == 2) throw CancellationException("post-freeze")
        }
        samples(1).forEach(postFreeze::on)
        armed = true
        assertThrows(CancellationException::class.java) { postFreeze.finish() }
        assertEquals(2, checks)
        val afterPostFreeze = assertThrows(GpxProfileFailure::class.java) { postFreeze.finish() }
        assertEquals("INVALID_STRUCTURE", afterPostFreeze.category)
        assertEquals("INCOMPLETE_PROFILE", afterPostFreeze.reason)
    }
}
