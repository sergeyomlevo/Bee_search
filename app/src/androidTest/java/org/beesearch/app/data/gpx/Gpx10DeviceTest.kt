package org.beesearch.app.data.gpx

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gpx10DeviceTest {
    private fun bytes() = InstrumentationRegistry.getInstrumentation().context.assets.open("gpx/v10/tracks.gpx").use { it.readBytes() }
    private fun parse(bytes: ByteArray) = GpxProcessor().process({ ByteArrayInputStream(bytes) })
    @Test fun tracksMetadataOrderAndUnsupportedContentUseExistingPipeline() {
        val bytes = bytes()
        val result = parse(bytes) as GpxParseResult.Accepted
        val graph = result.draft
        assertEquals("1.0", graph.declaredGpxVersion)
        assertEquals("GPX_1_0_STAGE_1", graph.parserProfileVersion)
        assertEquals("Source", graph.metadata.sourceName)
        assertEquals("Description", graph.metadata.sourceDesc)
        assertEquals("Observer", graph.metadata.authorName)
        assertEquals(listOf("Forest", "Second"), graph.tracks.map { it.name })
        assertEquals(listOf(2,1), graph.tracks[0].segments.map { it.samples.size })
        assertEquals(listOf("55.1","55.101","56","57"), graph.tracks.flatMap { it.segments }.flatMap { it.samples }.map { it.lat.canonicalPlain })
        val first = graph.tracks[0].segments[0].samples[0]
        assertEquals("37.2", first.lon.canonicalPlain)
        assertEquals("123.4", first.elevation!!.canonicalPlain)
        assertEquals("2026-10-14T16:40:11Z", first.time.raw)
        assertTrue(result.diagnostics.any { it.category.name == "UNSUPPORTED_CONTENT" })
        val copied = ByteArrayOutputStream()
        val identity = copyGpxOriginal(ByteArrayInputStream(bytes), copied)
        assertArrayEquals(bytes, copied.toByteArray())
        assertEquals(identity, result.originalIdentity)
    }
    @Test fun profilePairsStructureCoordinatesAndNoTrackPolicy() {
        val xml = bytes().toString(Charsets.UTF_8)
        for (bad in listOf(xml.replace("version=\"1.0\"", "version=\"1.1\""), xml.replace("GPX/1/0", "GPX/2/0"), xml.replace("lat=\"55.100\"", "lat=\"91\""), xml.dropLast(10), xml.replace("<gpx ", "<other ").replace("</gpx>", "</other>"))) {
            assertTrue(parse(bad.toByteArray()) is GpxParseResult.Rejected)
        }
        val empty = "<gpx xmlns=\"http://www.topografix.com/GPX/1/0\" version=\"1.0\" creator=\"test\"><wpt lat=\"1\" lon=\"2\"/></gpx>"
        assertEquals(GpxFailureCategory.NO_IMPORTABLE_TRACK, (parse(empty.toByteArray()) as GpxParseResult.Rejected).category)
    }
    @Test fun securityDepthAndCancellationStillApplyTo10() {
        val xml = bytes().toString(Charsets.UTF_8)
        assertTrue(parse(xml.replace("<gpx ", "<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><gpx ").toByteArray()) is GpxParseResult.Rejected)
        val deep = xml.replace("<speed>1.25</speed>", "<speed>" + "<x>".repeat(100) + "</x>".repeat(100) + "</speed>")
        assertTrue(parse(deep.toByteArray()) is GpxParseResult.Rejected)
        val cancelled = CancellationException("test")
        assertSame(cancelled, assertThrows(CancellationException::class.java) { GpxProcessor().process({ ByteArrayInputStream(bytes()) }, checkCancellation = { throw cancelled }) })
    }
    @Test fun ownerTopokartyOriginal271PointsAndExactBytes() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val bytes = assets.open("gpx/v10/topokarty-original.gpx").use { it.readBytes() }
        val result = parse(bytes) as GpxParseResult.Accepted
        assertEquals(33339L, result.originalIdentity.length)
        assertEquals("dc390810332489769fcea36cc1703e33deac12a8f795648226316f62b9e175d8", result.originalIdentity.sha256)
        assertEquals("1.0", result.draft.declaredGpxVersion)
        assertEquals(1, result.draft.tracks.size)
        assertEquals(1, result.draft.tracks.single().segments.size)
        val points = result.draft.tracks.single().segments.single().samples
        assertEquals(271, points.size)
        // Independent ElementTree transcription of original fields; no production parser oracle.
        val expected = assets.open("gpx/v10/topokarty-expected.tsv").bufferedReader().use { it.readLines() }
        assertEquals(271, expected.size)
        points.zip(expected).forEachIndexed { index, (point, line) ->
            val fields = line.split('\t')
            assertEquals(index, point.ordinal)
            assertEquals(java.math.BigDecimal(fields[0]).stripTrailingZeros(), java.math.BigDecimal(point.lat.canonicalPlain).stripTrailingZeros())
            assertEquals(java.math.BigDecimal(fields[1]).stripTrailingZeros(), java.math.BigDecimal(point.lon.canonicalPlain).stripTrailingZeros())
            assertEquals(java.math.BigDecimal(fields[2]).stripTrailingZeros(), java.math.BigDecimal(point.elevation!!.canonicalPlain).stripTrailingZeros())
            assertEquals(fields[3], point.time.raw)
            assertEquals(java.time.Instant.parse(fields[3]).epochSecond, point.time.epochSeconds)
        }
        assertTrue(result.diagnostics.any { it.category.name == "UNSUPPORTED_CONTENT" && it.count >= 271 })
        val copied = ByteArrayOutputStream()
        assertEquals(result.originalIdentity, copyGpxOriginal(ByteArrayInputStream(bytes), copied))
        assertArrayEquals(bytes, copied.toByteArray())
    }
}
