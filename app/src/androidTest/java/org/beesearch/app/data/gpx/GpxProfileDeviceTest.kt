package org.beesearch.app.data.gpx

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import java.io.IOException
import java.util.concurrent.CancellationException
import org.beesearch.app.domain.gpx.GpxTimeStatus
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.beesearch.app.domain.gpx.GpxCanonicalDraft
import org.beesearch.app.domain.gpx.GpxCanonicalValues
import org.beesearch.app.domain.gpx.GpxMetadata
import org.beesearch.app.domain.gpx.GpxProfileFailure
import org.beesearch.app.domain.gpx.GpxSample
import org.beesearch.app.domain.gpx.GpxSegment
import org.beesearch.app.domain.gpx.GpxTrack
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Slice3's actual Android XML-to-profile corpus. Fixtures and expectations are independent. */
@RunWith(AndroidJUnit4::class)
class GpxProfileDeviceTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun everySlice3FamilyHasAnIndependentFixtureAndExpectedOutcome() {
        val rows = manifest()
        assertEquals(52, rows.size)
        assertEquals((rows.map { it.family }).toSet().size, rows.size)
        rows.forEach { row ->
            val result = runCatching { parse(row.fixture) }
            if (row.expect == "ACCEPTED") {
                val read = result.getOrNull() ?: throw AssertionError(
                    "${row.family} should parse: ${result.exceptionOrNull()}",
                )
                assertProfileVerification(read)
                assertEquals(expectedDraft(row.family), read.profile.draft)
                assertWarnings(row, read)
                Log.i("D106_GPX_PROFILE", "family=${row.family} result=ACCEPTED api=${read.verification.apiLevel} provider=${read.verification.provider} configuration=${read.verification.configuration}")
            } else {
                val failure = result.exceptionOrNull() ?: throw AssertionError("${row.family} should fail")
                assertEquals(row.expect, failureCategory(failure))
            }
        }
    }

    @Test
    fun actualXmlPairsHaveIndependentCanonicalAndCm1Relationships() {
        val equivalent = listOf(listOf("B01", "B02"), listOf("B01", "B03"), listOf("B01", "B04"),
            listOf("B01", "B05"), listOf("B01", "B06"), listOf("B01", "B07"), listOf("B01", "B08"),
            listOf("B01", "B09"), listOf("C01", "C07"), listOf("E01", "E02", "E03", "E04"))
        equivalent.forEach { pair ->
            val encoded = pair.map { cm1(parse("$it.xml").profile.draft) }
            encoded.drop(1).forEach { bytes -> assertArrayEquals(encoded.first(), bytes) }
        }
        for ((base, different) in listOf("B01" to "C01", "A02" to "C02", "A02" to "C03", "A02" to "C04",
            "A04" to "C05", "B01" to "C06", "B01" to "C08", "B01" to "C09")) {
            assertFalse("$base/$different", cm1(parse("$base.xml").profile.draft).contentEquals(cm1(parse("$different.xml").profile.draft)))
        }
    }

    @Test
    fun a01XmlUsesTheIndependent205ByteCm1Golden() {
        val literal = """
            424545475058434d0001
            00010100000003312e31
            0002010000000766697874757265
            00030000000000 00040000000000 00050000000000
            0006030000008f 00000001 00010200000084
            0001010000000130 00020000000000 00030000000000
            00040300000067 00000001 0001020000005c
            0001010000000130 0002030000004d 00000001 00010200000042
            0001010000000130 0002010000000130 0003010000000130
            00040000000000 00050000000000
            000601000000074d495353494e47
            00070000000000 00080000000000
        """.filterNot { it.isWhitespace() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertEquals(205, literal.size)
        assertArrayEquals(literal, cm1(parse("A01.xml").profile.draft))
    }

    @Test
    fun acceptedProfilesPreserveIndependentOrderSegmentsMetadataAndTimes() {
        val a01 = parse("A01.xml").profile.draft
        assertEquals("fixture", a01.metadata.creator)
        assertEquals(1, a01.tracks.size)
        assertEquals(1, a01.tracks[0].segments.size)
        assertEquals("0", a01.tracks[0].segments[0].samples[0].lat.canonicalPlain)

        val a02 = parse("A02.xml").profile.draft
        assertEquals(listOf("1", "3", "5"), a02.tracks[0].segments[0].samples.map { it.lat.canonicalPlain })

        val a03 = parse("A03.xml").profile.draft
        assertEquals(2, a03.tracks[0].segments.size)
        val a04 = parse("A04.xml").profile.draft
        assertEquals(2, a04.tracks.size)

        val c05 = parse("C05.xml").profile.draft
        assertEquals(listOf("3", "1"), c05.tracks.map { it.segments[0].samples[0].lat.canonicalPlain })
        val c09 = parse("C09.xml").profile.draft.tracks[0].segments[0].samples[0]
        assertEquals("2020-01-02T04:04:05+01:00", c09.time.raw)
        assertEquals(0, c09.time.nanos)
        assertEquals(1577934245L, c09.time.epochSeconds)
    }

    @Test
    fun warningsExposeCategoryCountAndAddressWithoutChangingAcceptedGraph() {
        val b07 = parse("B07.xml")
        assertEquals(1, b07.profile.draft.tracks.size)
        assertTrue(b07.profile.warnings.any { it.category.name == "UNSUPPORTED_CONTENT" && it.count > 0 })

        val f09 = parse("F09.xml")
        assertTrue(f09.profile.warnings.any { it.category.name == "UNSUPPORTED_CONTENT" })
        assertFalse(f09.profile.warnings.any { it.category.name == "UNSUPPORTED_TIME" })

        val a08 = parse("A08.xml")
        assertTrue(a08.profile.warnings.isEmpty())
        val times = a08.profile.draft.tracks[0].segments[0].samples.map { it.time.epochSeconds }
        assertEquals(times[0], times[1]); assertEquals(null, times[2]); assertTrue(times[3]!! < times[0]!!)
    }

    @Test
    fun isolatedStructuralVariantsKeepTheirFamilyCategories() {
        assertFailure("D05-empty.xml", "MALFORMED_XML")
        assertFailure("D05-wrong-root.xml", "UNSUPPORTED_PROFILE")
        assertFailure("D05-second-root.xml", "MALFORMED_XML")
        assertFailure("D06-missing-namespace.xml", "UNSUPPORTED_PROFILE")
        assertFailure("D06-version10.xml", "UNSUPPORTED_PROFILE")
        assertFailure("D07-missing-version.xml", "INVALID_STRUCTURE")
        assertFailure("D07-missing-creator.xml", "INVALID_STRUCTURE")
        assertFailure("D07-duplicate-creator.xml", "INVALID_STRUCTURE")
        assertFailure("D11-nested-scalar.xml", "INVALID_STRUCTURE")
        assertFailure("D11-misplaced.xml", "INVALID_STRUCTURE")
        assertFailure("D11-order.xml", "INVALID_STRUCTURE")
        assertFailure("D11-core-text.xml", "INVALID_STRUCTURE")
        assertFailure("D09-longitude.xml", "INVALID_COORDINATE")
        assertFailure("D12-routes-only.xml", "NO_IMPORTABLE_TRACK")
        val foreignMixed = parse("D13-foreign-smuggle.xml")
        assertEquals(listOf("1"), foreignMixed.profile.draft.tracks[0].segments[0].samples.map { it.lat.canonicalPlain })
        assertTrue(foreignMixed.profile.warnings.any { it.category.name == "UNSUPPORTED_CONTENT" })
    }

    @Test
    fun b08CdataAndReferenceValuesShareCanonicalTextButAttributeOriginsRemainDistinct() {
        val cdata = parse("B08-cdata.xml").profile.draft
        val reference = parse("B08-ref.xml").profile.draft
        assertEquals("source", cdata.metadata.sourceName)
        assertEquals(cdata, reference)
        val literal = parse("B08-tab-literal.xml").profile.draft
        val referenced = parse("B08-tab-reference.xml").profile.draft
        assertEquals("a b", literal.metadata.creator)
        assertEquals("a\tb", referenced.metadata.creator)
        assertFalse(cm1(literal).contentEquals(cm1(referenced)))
    }

    @Test fun contextSpecificIgnoredScalarsDoNotBecomeCanonicalFields() {
        val base = String(encodedFixture("B01.xml").readBytes(), Charsets.UTF_8)
        val variant = base.replace("<trk>", "<metadata><time>not-supported</time></metadata><trk>")
            .replace("</time></trkpt>", "</time><name>ignored name</name><desc>ignored desc</desc></trkpt>")
        val read = parseText(variant)
        assertEquals(expectedDraft("B01"), read.profile.draft)
        assertEquals(3L, read.profile.warnings.single().count)
        assertEquals("UNSUPPORTED_CONTENT", read.profile.warnings.single().category.name)
    }

    @Test fun namespaceAttributesAndMalformedIgnoredContentRemainStructurallyChecked() {
        val base = String(encodedFixture("B01.xml").readBytes(), Charsets.UTF_8)
        fun ignored(attributes: String) = base.replace("</gpx>", "<extensions><x $attributes/></extensions></gpx>")
        for (xml in listOf(ignored("xmlns:a=\"urn:u\" xmlns:b=\"urn:u\" a:x=\"1\" b:x=\"2\""),
            ignored("u:x=\"1\""), base.replace("</gpx>", "<extensions><x></extensions></gpx>"))) {
            assertTextFailure(xml, "MALFORMED_XML")
        }
        val legal = parseText(ignored("xmlns:a=\"urn:u1\" xmlns:b=\"urn:u2\" a:x=\"1\" b:x=\"2\""))
        assertEquals(expectedDraft("B01"), legal.profile.draft)
        assertEquals(1L, legal.profile.warnings.single().count)
        assertTextFailure(base.replace("lat=\"1\"", "xmlns:f=\"urn:foreign\" f:lat=\"1\""), "INVALID_COORDINATE")
    }

    @Test fun timeStatusNullElevationAndCanonicalDifferencesAreIndependent() {
        val base = String(encodedFixture("B01.xml").readBytes(), Charsets.UTF_8)
        for ((fixture, status) in listOf("D14-no-zone.xml" to GpxTimeStatus.NO_ZONE,
            "D14-leap-second.xml" to GpxTimeStatus.UNSUPPORTED, "D14-bad-time.xml" to GpxTimeStatus.UNSUPPORTED)) {
            val read = parse(fixture)
            val time = read.profile.draft.tracks[0].segments[0].samples[0].time
            assertEquals(status, time.status); assertNull(time.epochSeconds); assertNull(time.nanos)
            assertEquals("UNSUPPORTED_TIME", read.profile.warnings.single().category.name)
            assertEquals(1L, read.profile.warnings.single().count)
        }
        val noElevation = parseText(base.replace("<ele>3</ele>", ""))
        assertNull(noElevation.profile.draft.tracks[0].segments[0].samples[0].elevation)
        assertFalse(cm1(noElevation.profile.draft).contentEquals(cm1(parse("C08.xml").profile.draft)))
        val zeroVariant = String(encodedFixture("A01.xml").readBytes(), Charsets.UTF_8)
            .replace("lat=\"0\"", "lat=\"+000.000\"").replace("lon=\"0\"", "lon=\"-0.0\"")
        assertArrayEquals(cm1(parse("A01.xml").profile.draft), cm1(parseText(zeroVariant).profile.draft))
        val offset = parse("C09.xml").profile.draft.tracks[0].segments[0].samples[0].time
        assertEquals(1577934245L, offset.epochSeconds)
        val nanos = parse("A09.xml").profile.draft.tracks[0].segments[0].samples[0].time
        assertEquals(1577923445L, nanos.epochSeconds); assertEquals(123456789, nanos.nanos)
        val segmentSwap = String(encodedFixture("A03.xml").readBytes(), Charsets.UTF_8)
            .replace("lat=\"1\" lon=\"2\"", "lat=\"3\" lon=\"4\"")
            .replace("lat=\"3\" lon=\"4\"/></trkseg></trk>", "lat=\"1\" lon=\"2\"/></trkseg></trk>")
        assertFalse(cm1(parse("A03.xml").profile.draft).contentEquals(cm1(parseText(segmentSwap).profile.draft)))
    }

    @Test fun cancellationAndLateSourceFailureNeverReturnPartialCanonicalResult() {
        val bytes = encodedFixture("B01.xml").readBytes()
        var opens = 0
        var checks = 0
        var accepted: GpxProfileRead? = null
        assertThrows(CancellationException::class.java) {
            accepted = AndroidGpxProfileReader.parse(open = { opens++; ByteArrayInputStream(bytes) }, checkCancellation = {
                if (opens == 2 && ++checks > 500) throw CancellationException()
            })
        }
        assertNull(accepted); assertEquals(2, opens); assertTrue(checks > 500)
        opens = 0
        val failure = runCatching {
            accepted = AndroidGpxProfileReader.parse(open = {
                val pass = ++opens
                object : ByteArrayInputStream(bytes) { override fun close() { super.close(); if (pass == 2) throw IOException("test-close-fault") } }
            })
        }.exceptionOrNull() ?: throw AssertionError("late close must fail")
        assertEquals("INPUT_IO_ERROR", failureCategory(failure)); assertNull(accepted); assertEquals(2, opens)
    }

    private fun parseText(xml: String): GpxProfileRead = AndroidGpxProfileReader.parse({ ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)) })
    private fun assertTextFailure(xml: String, category: String) {
        val failure = runCatching { parseText(xml) }.exceptionOrNull() ?: throw AssertionError("expected $category")
        assertEquals(category, failureCategory(failure))
    }
    @Test fun requiredLexicalProfileAndIsolatedEncodingFailuresKeepTheirCategories() {
        val base = String(encodedFixture("B01.xml").readBytes(), Charsets.UTF_8)
        assertTextFailure(base.replace("version=\"1.1\"", "version=\" 1.1 \""), "UNSUPPORTED_PROFILE")
        assertTextFailure(base.replace("<?xml version=\"1.0\"", "<?xml version=\"1.1\""), "UNSUPPORTED_PROFILE")
        assertTextFailure(base.replace("t-base", "bad\u0001"), "MALFORMED_XML")
        val emptyCreator = parseText(base.replace("creator=\"slice3\"", "creator=\"\""))
        assertEquals("", emptyCreator.profile.draft.metadata.creator)
    }
    private fun parse(fixture: String) =
        AndroidGpxProfileReader.parse(
            open = { encodedFixture(fixture) },
        )

    private fun encodedFixture(fixture: String): ByteArrayInputStream {
        val source = assets.open("gpx/slice3/$fixture").use { it.readBytes() }
        return when (fixture.substringBefore('.')) {
            "E02", "B02" -> ByteArrayInputStream(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + source)
            "E03" -> ByteArrayInputStream(byteArrayOf(0xff.toByte(), 0xfe.toByte()) +
                String(source, StandardCharsets.UTF_8).replace("UTF-8", "UTF-16").toByteArray(Charsets.UTF_16LE))
            "E04", "B09" -> ByteArrayInputStream(byteArrayOf(0xfe.toByte(), 0xff.toByte()) +
                String(source, StandardCharsets.UTF_8).replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "")
                    .toByteArray(Charsets.UTF_16BE))
            else -> ByteArrayInputStream(source)
        }
    }

    private fun assertProfileVerification(read: GpxProfileRead) {
        assertTrue(read.verification.identity.length > 0)
        assertTrue(read.verification.identity.sha256.isNotEmpty())
        assertTrue(read.verification.provider.isNotEmpty())
        assertTrue(read.verification.configuration.isNotEmpty())
    }

    private fun assertWarnings(row: Row, read: GpxProfileRead) {
        val expected = row.warnings.split(',').filter { it.isNotEmpty() }
        assertEquals(expected, read.profile.warnings.map { it.category.name })
        read.profile.warnings.forEach { warning ->
            assertTrue(warning.count > 0)
            assertNotNullAddress(warning.firstAddress)
        }
    }

    private fun assertNotNullAddress(address: Any?) {
        assertTrue(address != null)
    }

    private fun failureCategory(error: Throwable): String {
        return when (error) {
            is GpxProfileFailure -> error.category
            is GpxXmlFailure -> error.category
            else -> error.javaClass.simpleName
        }
    }

    private fun assertFailure(fixture: String, category: String) {
        val failure = runCatching { parse(fixture) }.exceptionOrNull()
            ?: throw AssertionError("$fixture should fail")
        assertEquals(category, failureCategory(failure))
    }

    private fun cm1(draft: GpxCanonicalDraft): ByteArray = ByteArrayOutputStream().also {
        GpxCm1Encoder.write(draft, it)
    }.toByteArray()

    private fun expectedDraft(family: String): GpxCanonicalDraft {
        fun sample(index: Int, lat: String, lon: String, ele: String? = null, time: String? = null) =
            GpxSample(index, GpxCanonicalValues.latitude(lat), GpxCanonicalValues.longitude(lon),
                GpxCanonicalValues.elevation(ele), GpxCanonicalValues.time(time))
        fun segment(vararg points: GpxSample) = GpxSegment(0, points.toList())
        fun track(vararg segments: GpxSegment, name: String? = null, desc: String? = null) =
            GpxTrack(0, segments.toList(), name, desc)
        fun baseline(name: String = "t-base") = GpxCanonicalDraft(GpxMetadata("slice3"), listOf(
            track(segment(sample(0, "1", "2", "3", "2020-01-02T03:04:05Z")), name = name),
        ))
        return when (family) {
            "A01" -> GpxCanonicalDraft(GpxMetadata("fixture"), listOf(track(segment(sample(0, "0", "0")))))
            "A02" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"1","2"), sample(1,"3","4"), sample(2,"5","6")))))
            "A03" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"1","2")), GpxSegment(1, listOf(sample(0,"3","4"))))))
            "A04" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"1","2"))), GpxTrack(1, listOf(segment(sample(0,"3","4"))))))
            "A05" -> GpxCanonicalDraft(GpxMetadata("slice3", "source", "desc", "author"), listOf(
                track(segment(sample(0,"1.250","-2.500","3.750","2020-01-02T03:04:05.000000001Z")), name = "track", desc = "tdesc")))
            "A06" -> GpxCanonicalDraft(GpxMetadata("slice3", "é😀"), listOf(track(segment(sample(0,"1","2")))))
            "A07" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"-90","-180"), sample(1,"90","179.999999999999999999")))))
            "A08" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(
                sample(0,"1","2",time="2020-01-02T00:00:02Z"), sample(1,"3","4",time="2020-01-02T00:00:02Z"),
                sample(2,"5","6"), sample(3,"7","8",time="2020-01-02T00:00:01Z")))))
            "A09" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"1","2",time="2020-01-02T03:04:05.123456789+03:00")))))
            "C02" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"3","4"), sample(1,"1","2"), sample(2,"5","6")))))
            "C03" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"5","6"), sample(1,"3","4"), sample(2,"1","2")))))
            "C04" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"1","2")), GpxSegment(1, listOf(sample(0,"3","4"), sample(1,"5","6"))))))
            "C05" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"3","4"))), GpxTrack(1, listOf(segment(sample(0,"1","2"))))))
            "C06" -> GpxCanonicalDraft(GpxMetadata("slice3", "changed", "supported", "a"), listOf(track(segment(sample(0,"1","2","3","2020-01-02T03:04:05Z")), name = "t-base")))
            "C08" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"1","2","0","2020-01-02T03:04:05Z")), name = "t-base")))
            "C09" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"1","2","3","2020-01-02T04:04:05+01:00")), name = "t-base")))
            "D04" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(GpxTrack(0, emptyList()), GpxTrack(1, listOf(GpxSegment(0, emptyList()), GpxSegment(1, listOf(sample(0,"1","2")))))))
            "F09" -> GpxCanonicalDraft(GpxMetadata("slice3", "supported"), listOf(track(segment(sample(0,"1","2","3","2020-01-02T03:04:05Z")), name = "t-F09")))
            "G02" -> GpxCanonicalDraft(GpxMetadata("slice3"), (1..4).mapIndexed { i, lat -> GpxTrack(i, listOf(GpxSegment(0, listOf(sample(0, lat.toString(), "2"))))) })
            "G03" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(GpxTrack(0, (1..4).mapIndexed { i, lat -> GpxSegment(i, listOf(sample(0, lat.toString(), "2"))) })))
            "G04" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"1","2"), sample(1,"2","2"), sample(2,"3","2"), sample(3,"4","2")))))
            "G08" -> GpxCanonicalDraft(GpxMetadata("slice3", "small supported text", "counter"), listOf(track(segment(sample(0,"1","2","3","2020-01-02T03:04:05Z")), name = "t-G08")))
            "C01", "C07" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"1.000000000000000000001","2","3","2020-01-02T03:04:05Z")), name = "t-base")))
            "D12" -> GpxCanonicalDraft(GpxMetadata("slice3"), listOf(track(segment(sample(0,"1","2")))))
            "B01", "B02", "B03", "B04", "B05", "B06", "B07", "B08", "B09" -> baseline()
            "E01", "E02", "E03", "E04" -> baseline("t-E01")
            else -> throw AssertionError("No independent canonical expectation for $family")
        }
    }

    private fun manifest(): List<Row> = assets.open("gpx/slice3/manifest.tsv").bufferedReader().useLines { lines ->
        lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val fields = line.split('\t')
            Row(fields[0], fields[1], fields[2], fields.getOrElse(3) { "" })
        }.toList()
    }

    private data class Row(val family: String, val fixture: String, val expect: String, val warnings: String)
}
