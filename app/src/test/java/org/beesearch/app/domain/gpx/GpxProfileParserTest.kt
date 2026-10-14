package org.beesearch.app.domain.gpx

import org.beesearch.app.data.gpx.GpxCm1Encoder
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException

/** Hand-authored normalized events: no host XML parser, no Android/provider claim. */
class GpxProfileParserTest {
    private class Events {
        val values = ArrayList<GpxProfileEvent>()
        fun node(name: String, attrs: List<GpxProfileAttribute> = emptyList(), ns: String = GpxProfileSchema.NAMESPACE, body: Events.() -> Unit = {}) {
            values += GpxProfileEvent.Start(name, ns, attrs); body(); values += GpxProfileEvent.End(name, ns)
        }
        fun text(vararg pieces: String) { pieces.forEach { values += GpxProfileEvent.Text(it) } }
        fun pi() { values += GpxProfileEvent.ProcessingInstruction }
        fun point(lat: String = "0", lon: String = "0", ele: String? = null, time: String? = null) {
            node("trkpt", listOf(a("lat", lat), a("lon", lon))) {
                ele?.let { node("ele") { text(it) } }; time?.let { node("time") { text(it) } }
            }
        }
        fun usable() { node("trk") { node("trkseg") { point() } } }
    }
    private fun root(creator: String = "fixture", body: Events.() -> Unit): List<GpxProfileEvent> = Events().apply {
        node("gpx", listOf(a("version", "1.1"), a("creator", creator)), body = body)
    }.values
    private fun parse(events: List<GpxProfileEvent>): GpxProfileResult = GpxProfileParser().let { p -> events.forEach(p::on); p.finish() }
    private fun failure(category: String, events: List<GpxProfileEvent>): GpxProfileFailure =
        assertThrows(GpxProfileFailure::class.java) { parse(events) }.also { assertEquals(category, it.category) }
    private fun cm1(result: GpxProfileResult): ByteArray = ByteArrayOutputStream().also { GpxCm1Encoder.write(result.draft, it) }.toByteArray()
    private fun same(a: List<GpxProfileEvent>, b: List<GpxProfileEvent>) = assertArrayEquals(cm1(parse(a)), cm1(parse(b)))
    private fun different(a: List<GpxProfileEvent>, b: List<GpxProfileEvent>) = assertFalse(cm1(parse(a)).contentEquals(cm1(parse(b))))
    private fun ordered(vararg values: String) = root { node("trk") { node("trkseg") { values.forEach { point(it) } } } }
    private companion object { fun a(name: String, value: String, ns: String = "") = GpxProfileAttribute(name, ns, value) }

    @Test fun a01SingletonMatchesHandAuthoredCanonicalTree() {
        val result = parse(root { usable() })
        val expected = GpxCanonicalDraft(GpxMetadata("fixture"), listOf(GpxTrack(0, listOf(GpxSegment(0,
            listOf(GpxSample(0, GpxCanonicalValues.latitude("0"), GpxCanonicalValues.longitude("0"))))))))
        assertEquals(expected, result.draft)
        assertTrue(result.warnings.isEmpty())
    }
    @Test fun a02A03A04D04G02G03G04PreserveOrderEmptyNodesAndBasicCounts() {
        val result = parse(root {
            node("trk") {}
            node("trk") { node("trkseg") {}; node("trkseg") { point("3"); point("1"); point("2") } }
        })
        assertEquals(listOf(0, 1), result.draft.tracks.map { it.ordinal })
        assertTrue(result.draft.tracks[0].segments.isEmpty())
        assertEquals(listOf(0, 1), result.draft.tracks[1].segments.map { it.ordinal })
        assertTrue(result.draft.tracks[1].segments[0].samples.isEmpty())
        val points = result.draft.tracks[1].segments[1].samples
        assertEquals(listOf("3", "1", "2"), points.map { it.lat.canonicalPlain })
        assertEquals(listOf(0, 1, 2), points.map { it.ordinal })
        assertEquals(2, result.draft.tracks.sumOf { it.segments.size })
        assertEquals(3, result.draft.tracks.sumOf { t -> t.segments.sumOf { it.samples.size } })
    }
    @Test fun a05A06MetadataNullEmptyWhitespaceUnicodeAndEmptyCreator() {
        val result = parse(root("") {
            node("metadata") { node("name") { text("e\u0301😀") }; node("desc") { text(" \t\n") }; node("author") { node("name") { text("author") } } }
            node("trk") { node("name") {}; node("desc") { text("track") }; node("trkseg") { point(ele = "-3.75") } }
        })
        assertEquals(GpxMetadata("", "e\u0301😀", " \t\n", "author"), result.draft.metadata)
        assertEquals("", result.draft.tracks[0].name)
        assertEquals("track", result.draft.tracks[0].description)
        assertEquals("-3.75", result.draft.tracks[0].segments[0].samples[0].elevation?.canonicalPlain)
        different(root { usable() }, root { node("metadata") { node("name") {} }; usable() })
    }
    @Test fun a07B06C01D09D10ExactCoordinateRulesAreReused() {
        val result = parse(root { node("trk") { node("trkseg") { point("-90", "-180"); point("90", "179.999999999999999999") } } })
        assertEquals(listOf("-90", "90"), result.draft.tracks[0].segments[0].samples.map { it.lat.canonicalPlain })
        same(ordered("-0"), ordered("+000.000"))
        same(ordered("+001.2500"), ordered("1.25"))
        different(ordered("1.00000000000000000001"), ordered("1.00000000000000000002"))
        for (lat in listOf("91", "-90.00000001", "1e0", "1,2", "NaN", "Infinity")) failure("INVALID_COORDINATE", ordered(lat))
        failure("INVALID_COORDINATE", root { node("trk") { node("trkseg") { point(lon = "180") } } })
    }
    @Test fun a08A09C09D14TimeRawStatusOffsetNanosAndOrder() {
        val early = "1970-01-01T00:00:00.123456789Z"
        val offset = "1970-01-01T01:00:00.123456789+01:00"
        val result = parse(root { node("trk") { node("trkseg") {
            point(time = "1970-01-01T00:00:01Z"); point(time = early); point(time = offset); point()
        } } })
        val points = result.draft.tracks[0].segments[0].samples
        assertEquals(listOf(0, 1, 2, 3), points.map { it.ordinal })
        assertEquals(listOf(1L, 0L, 0L, null), points.map { it.time.epochSeconds })
        assertEquals(123456789, points[1].time.nanos)
        assertEquals(offset, points[2].time.raw)
        assertEquals(GpxTimeStatus.MISSING, points[3].time.status)
        different(root { node("trk") { node("trkseg") { point(time = early) } } }, root { node("trk") { node("trkseg") { point(time = offset) } } })
        val unsupported = parse(root { node("trk") { node("trkseg") {
            point(time = "2000-01-01T00:00:00"); point(time = "2000-01-01T23:59:60Z"); point(time = "bad")
        } } })
        assertEquals(listOf(GpxTimeStatus.NO_ZONE, GpxTimeStatus.UNSUPPORTED, GpxTimeStatus.UNSUPPORTED),
            unsupported.draft.tracks[0].segments[0].samples.map { it.time.status })
        assertEquals(3L, unsupported.warnings.single().count)
        assertEquals(GpxWarningCategory.UNSUPPORTED_TIME, unsupported.warnings.single().category)
    }
    @Test fun b01B08G08WhitespaceAndTextFragmentationPreserveExactValues() {
        same(root { usable() }, root { text("\n\t"); usable(); text("\r ") })
        fun named(vararg pieces: String) = root { node("metadata") { node("name") { text(*pieces) } }; usable() }
        same(named("ab😀"), named("a", "b", "\ud83d", "\ude00"))
        assertEquals("ab😀", parse(named("a", "b", "\ud83d", "\ude00")).draft.metadata.sourceName)
        different(named("\t"), named(" "))
        // Cumulative G08 scope, not a new ceiling or a resource/capacity qualification.
        failure("LIMIT_EXCEEDED", named("x".repeat(600), "x".repeat(425)))
    }
    @Test fun b07C07D12D13ForeignOpaqueAndContextSpecificIgnoredContent() {
        val base = root { usable() }
        val ignored = root {
            node("metadata") { node("time") { text("bad ignored time") } }
            node("rte") { node("trk") { node("trkseg") { point("89") } } }
            node("trk", ns = "urn:foreign") { node("trkseg") { point("88") } }
            node("trk") { node("type") { text("ignored") }; node("trkseg") {
                node("trkpt", listOf(a("lat", "0"), a("lon", "0"))) {
                    node("name") { text("ignored point name") }; node("desc") { text("ignored point desc") }
                    node("extensions") { node("trkpt") {} }
                }
            } }
        }
        same(base, ignored)
        val warnings = parse(ignored).warnings
        assertEquals(1, warnings.size)
        assertEquals(7L, warnings.single().count)
        assertEquals(GpxProfileScope.METADATA, warnings.single().firstAddress.scope)
    }
    @Test fun b04D06NormalizedNamespaceUriControlsIdentity() {
        for (ns in listOf("", "http://www.topografix.com/GPX/1/0", "urn:foreign")) {
            failure("UNSUPPORTED_PROFILE", Events().apply { node("gpx", listOf(a("version", "1.1"), a("creator", "x")), ns) { usable() } }.values)
        }
        failure("UNSUPPORTED_PROFILE", Events().apply { node("gpx", listOf(a("version", " 1.1 "), a("creator", "x"))) { usable() } }.values)
    }
    @Test fun c02C03PermutationAndReversalDifferWithoutCanonicalSorting() {
        different(ordered("1", "2", "3"), ordered("2", "1", "3"))
        different(ordered("1", "2", "3"), ordered("3", "2", "1"))
    }
    @Test fun c04C05SegmentBoundariesAndTrackSegmentPermutationMatter() {
        fun segments(a: String, b: String) = root { node("trk") { node("trkseg") { point(a) }; node("trkseg") { point(b) } } }
        fun tracks(a: String, b: String) = root { node("trk") { node("trkseg") { point(a) } }; node("trk") { node("trkseg") { point(b) } } }
        different(ordered("1", "2"), segments("1", "2"))
        different(segments("1", "2"), segments("2", "1"))
        different(tracks("1", "2"), tracks("2", "1"))
    }
    @Test fun c06SupportedMetadataChangesMatterWhileSourceContextHasNoSlot() {
        different(root { usable() }, root("different") { usable() })
        different(root { usable() }, root { node("metadata") { node("name") { text("changed") } }; usable() })
        different(root { usable() }, root { node("trk") { node("name") { text("changed") }; node("trkseg") { point() } } })
    }
    @Test fun c08D14ElevationNullZeroAndMalformedAreDistinct() {
        different(root { usable() }, root { node("trk") { node("trkseg") { point(ele = "0") } } })
        failure("INVALID_OPTIONAL_VALUE", root { node("trk") { node("trkseg") { point(ele = "NaN") } } })
    }
    @Test fun d01D02D03D12ZeroSupportedSamplesRejectAfterStructureValidation() {
        for (events in listOf(root {}, root { node("trk") {} }, root { node("trk") { node("trkseg") {} } },
            root { node("rte") { node("rtept") {} }; node("wpt") {} })) failure("NO_IMPORTABLE_TRACK", events)
    }
    @Test fun d07RequiredRootFieldsAndDuplicateSupportedValuesReject() {
        for (attrs in listOf(listOf(a("version", "1.1")), listOf(a("creator", "x"))))
            failure("INVALID_STRUCTURE", Events().apply { node("gpx", attrs) { usable() } }.values)
        failure("INVALID_STRUCTURE", root { node("metadata") {}; node("metadata") {}; usable() })
        failure("INVALID_STRUCTURE", root { node("metadata") { node("name") {}; node("name") {} }; usable() })
        failure("INVALID_STRUCTURE", root { node("metadata") { node("author") {}; node("author") {} }; usable() })
        failure("INVALID_STRUCTURE", root { node("trk") { node("trkseg") { node("trkpt", listOf(a("lat", "0"), a("lon", "0"))) { node("ele") { text("0") }; node("ele") { text("1") } } } } })
    }
    @Test fun d08OnlyUnqualifiedRequiredCoordinatesCount() {
        for (attrs in listOf(listOf(a("lat", "0")), listOf(a("lon", "0")), listOf(a("lat", "0", "urn:foreign"), a("lon", "0")))) {
            val error = failure("INVALID_COORDINATE", root { node("trk") { node("trkseg") { node("trkpt", attrs) {} } } })
            assertEquals(GpxProfileAddress(GpxProfileScope.SAMPLE, 0, 0, 0), error.address)
        }
    }
    @Test fun d11SupportedOrderingMisplacementNestedMarkupAndCoreTextAreFatal() {
        val invalid = listOf(root { node("trkseg") {} }, root { node("metadata") { node("desc") {}; node("name") {} }; usable() },
            root { node("trk") { node("trkseg") {}; node("name") {} } }, root { usable(); node("metadata") {} },
            root { text("non-whitespace"); usable() }, root { node("metadata") { node("name") { node("x", ns = "urn:foreign") {} } }; usable() },
            root { node("trk") { node("trkseg") { node("trkpt", listOf(a("lat", "0"), a("lon", "0"))) { node("time") { text("bad") }; node("ele") { text("0") } } } } })
        invalid.forEach { failure("INVALID_STRUCTURE", it) }
    }
    @Test fun f09UnsupportedSummaryIsBoundedCountedAndDoesNotRetainAttackerText() {
        val result = parse(Events().apply {
            pi(); node("gpx", listOf(a("version", "1.1"), a("creator", "fixture"), a("schemaLocation", "not-a-path", "urn:xsi"))) {
                node("metadata") { node("name") { text("a"); pi(); text("b") }; node("xinclude") {} }; usable()
            }
        }.values)
        assertEquals("ab", result.draft.metadata.sourceName)
        assertEquals(4L, result.warnings.single().count)
        assertEquals(GpxProfileAddress(GpxProfileScope.ROOT), result.warnings.single().firstAddress)
    }
    @Test fun malformedPointPoisonsWholeTentativeDocument() {
        val parser = GpxProfileParser()
        val events = root { node("trk") { node("trkseg") { point(); point("91") } } }
        assertThrows(GpxProfileFailure::class.java) { events.forEach(parser::on) }
        assertThrows(GpxProfileFailure::class.java) { parser.finish() }
    }
    @Test fun cancellationAndExplicitAbortExposeNoPartialAcceptedGraph() {
        var requested = false
        val parser = GpxProfileParser { if (requested) throw CancellationException() }
        root { usable() }.forEach(parser::on)
        requested = true
        assertThrows(CancellationException::class.java) { parser.finish() }
        assertThrows(GpxProfileFailure::class.java) { parser.finish() }
        val aborted = GpxProfileParser(); aborted.abort()
        assertThrows(GpxProfileFailure::class.java) { aborted.finish() }
    }
}