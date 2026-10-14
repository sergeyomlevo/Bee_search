package org.beesearch.app.domain.gpx

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class GpxCanonicalValuesTest {
    @Test fun b06ZeroAndScaleEquivalence() {
        for (text in listOf("0", "+0", "-0", "+0.0", "000.000", "-000.000")) {
            assertEquals("0", GpxCanonicalValues.latitude(text).canonicalPlain)
            assertEquals(GpxCanonicalValues.latitude("0"), GpxCanonicalValues.latitude(text))
        }
        for (text in listOf("+056.1200", "56.12", "00056.120"))
            assertEquals("56.12", GpxCanonicalValues.latitude(text).canonicalPlain)
        assertEquals("0.5", GpxCanonicalValues.latitude(".5").canonicalPlain)
        assertEquals("1", GpxCanonicalValues.latitude("1.").canonicalPlain)
    }

    @Test fun a07D09ExactCoordinateEndpoints() {
        for (text in listOf("-90", "90")) assertEquals(text, GpxCanonicalValues.latitude(text).canonicalPlain)
        assertEquals("-180", GpxCanonicalValues.longitude("-180").canonicalPlain)
        assertEquals("179.999999999999999999999999", GpxCanonicalValues.longitude("179.999999999999999999999999").canonicalPlain)
        for (text in listOf("-90.0000000000000000001", "90.0000000000000000001"))
            failure("INVALID_COORDINATE") { GpxCanonicalValues.latitude(text) }
        for (text in listOf("180", "180.0000", "-180.00000000000001"))
            failure("INVALID_COORDINATE") { GpxCanonicalValues.longitude(text) }
    }

    @Test fun c01PrecisionIsNotSpatiallyRounded() {
        val a = GpxCanonicalValues.latitude("1.0000000000000000000000000000000001")
        val b = GpxCanonicalValues.latitude("1.0000000000000000000000000000000002")
        assertNotEquals(a, b)
        assertTrue(a.canonicalPlain.endsWith("1"))
        assertEquals("-0.125", GpxCanonicalValues.elevation(" -000.12500\r\n")!!.canonicalPlain)
    }

    @Test fun d10F12InvalidAsciiDecimalGrammar() {
        for (text in listOf("", " ", ".", "+", "-", "1e2", "1E-2", "1,2", "NaN", "Infinity",
            "-Infinity", "0x1", "١", "１", "1 2", "1\t.2", "--1", "1..0", "\u00a01")) {
            failure("INVALID_COORDINATE") { GpxCanonicalValues.latitude(text) }
        }
    }

    @Test fun localeDoesNotChangeDecimalSemantics() {
        val previous = Locale.getDefault()
        try {
            for (locale in listOf(Locale.GERMANY, Locale.forLanguageTag("ar-EG"))) {
                Locale.setDefault(locale)
                assertEquals("1.25", GpxCanonicalValues.latitude(" +001.250 ").canonicalPlain)
                failure("INVALID_COORDINATE") { GpxCanonicalValues.latitude("1,25") }
            }
        } finally { Locale.setDefault(previous) }
    }

    @Test fun g10CountsBeforeTrim127128129() {
        for (length in listOf(127, 128)) {
            assertEquals("1", GpxCanonicalValues.latitude(" ".repeat(length - 1) + "1").canonicalPlain)
            assertEquals(GpxTimeStatus.UTC, GpxCanonicalValues.time(" ".repeat(length - 20) + "2000-01-01T00:00:00Z").status)
        }
        failure("LIMIT_EXCEEDED", "G10") { GpxCanonicalValues.latitude(" ".repeat(128) + "1") }
        failure("LIMIT_EXCEEDED", "G10") { GpxCanonicalValues.time(" ".repeat(109) + "2000-01-01T00:00:00Z") }
        // All digits still preserve exact value at the inherited boundary, not a new precision cap.
        assertEquals("0." + "1".repeat(126), GpxCanonicalValues.latitude("." + "1".repeat(126)).canonicalPlain)
        assertEquals("0." + "1".repeat(127), GpxCanonicalValues.latitude("." + "1".repeat(127)).canonicalPlain)
        failure("LIMIT_EXCEEDED", "G10") { GpxCanonicalValues.latitude("." + "1".repeat(128)) }
    }

    @Test fun scalarCountIsNotUtf16CodeUnitCount() {
        assertEquals("😀".repeat(128), GpxValueText.check("😀".repeat(128), 128, "G10"))
        failure("LIMIT_EXCEEDED", "G10") { GpxValueText.check("😀".repeat(129), 128, "G10") }
        for (text in listOf("\ud800", "\udc00", "\ud800x"))
            failure("MALFORMED_XML") { GpxValueText.check(text, 128, "G10") }
    }

    @Test fun d14ElevationUsesG09NotG10AndNoInventedRange() {
        assertNull(GpxCanonicalValues.elevation(null))
        assertEquals("1".repeat(8192), GpxCanonicalValues.elevation("1".repeat(8192))!!.canonicalPlain)
        assertEquals("0." + "0".repeat(200) + "1", GpxCanonicalValues.elevation("0." + "0".repeat(200) + "1")!!.canonicalPlain)
        failure("LIMIT_EXCEEDED", "G09") { GpxCanonicalValues.elevation("0".repeat(8193)) }
        for (text in listOf("", "NaN", "1E10", "1,2")) failure("INVALID_OPTIONAL_VALUE") { GpxCanonicalValues.elevation(text) }
    }

    @Test fun a09OffsetAndNanosPreserveRawText() {
        val raw = " \t1970-01-01T01:30:00.123456789+01:30\r\n"
        val time = GpxCanonicalValues.time(raw)
        assertEquals(raw, time.raw)
        assertEquals(GpxTimeStatus.UTC, time.status)
        assertEquals(0L, time.epochSeconds)
        assertEquals(123456789, time.nanos)
        assertEquals(-1L, GpxCanonicalValues.time("1969-12-31T23:59:59.999999999Z").epochSeconds)
        assertEquals(999999999, GpxCanonicalValues.time("1969-12-31T23:59:59.999999999Z").nanos)
    }

    @Test fun c09SameInstantDifferentRawIsDifferentTimeValue() {
        val a = GpxCanonicalValues.time("1970-01-01T00:00:00Z")
        val b = GpxCanonicalValues.time("1970-01-01T01:00:00+01:00")
        assertEquals(a.epochSeconds, b.epochSeconds)
        assertEquals(a.nanos, b.nanos)
        assertNotEquals(a, b)
    }

    @Test fun d14MissingAndValidNoZoneNeverUseDeviceTimezone() {
        assertEquals(GpxSampleTime(null, GpxTimeStatus.MISSING, null, null), GpxCanonicalValues.time(null))
        val previous = TimeZone.getDefault()
        try {
            for (zone in listOf("Pacific/Honolulu", "Asia/Tokyo")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val t = GpxCanonicalValues.time("2000-02-29T23:59:59.1")
                assertEquals(GpxTimeStatus.NO_ZONE, t.status)
                assertNull(t.epochSeconds)
                assertNull(t.nanos)
            }
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun d14UnsupportedFormsArePreservedNotRounded() {
        for (raw in listOf("", "2000-01-01", "2000-01-01t00:00:00z", "2001-02-29T00:00:00Z",
            "2000-13-01T00:00:00Z", "2000-01-01T23:59:60Z", "2000-01-01T00:00:00.1234567890Z",
            "0000-01-01T00:00:00Z", "10000-01-01T00:00:00Z", "2000-01-01T00:00:00+14:01",
            "2000-01-01T00:00:00-14:01", "2000-01-01T00:00:00+13:60", "2000-01-01T00:00:00+15:00",
            "2000-01-01T24:00:01Z", "2000-01-01T24:01:00Z", "2000-01-01T24:00:00.1Z",
            "2000-01-01T25:00:00Z", "9999-12-31T24:00:00Z", "0001-01-01T00:00:00+00:01",
            "9999-12-31T23:59:59-00:01")) {
            val t = GpxCanonicalValues.time(raw)
            assertEquals(raw, t.raw)
            assertEquals(raw, GpxTimeStatus.UNSUPPORTED, t.status)
            assertNull(t.epochSeconds)
            assertNull(t.nanos)
        }
    }

    @Test fun supportedCalendarBoundariesAnd24HourRollover() {
        assertEquals(-62135596800L, GpxCanonicalValues.time("0001-01-01T00:00:00Z").epochSeconds)
        assertEquals(253402300799L, GpxCanonicalValues.time("9999-12-31T23:59:59.999999999Z").epochSeconds)
        assertEquals(GpxCanonicalValues.time("2000-03-01T00:00:00Z").epochSeconds,
            GpxCanonicalValues.time("2000-02-29T24:00:00.000Z").epochSeconds)
        assertEquals(GpxTimeStatus.NO_ZONE, GpxCanonicalValues.time("2000-02-29T24:00:00").status)
        for (offset in listOf("+14:00", "-14:00"))
            assertEquals(GpxTimeStatus.UTC, GpxCanonicalValues.time("2000-01-01T00:00:00$offset").status)
    }

    @Test fun timeModelRejectsIncoherentNullStatusAndNanos() {
        assertThrows(IllegalArgumentException::class.java) { GpxSampleTime(null, GpxTimeStatus.UTC, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { GpxSampleTime("x", GpxTimeStatus.MISSING, null, null) }
        assertThrows(IllegalArgumentException::class.java) { GpxSampleTime("x", GpxTimeStatus.NO_ZONE, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { GpxSampleTime("x", GpxTimeStatus.UTC, 0, 1000000000) }
    }

    private fun failure(category: String, reason: String? = null, block: () -> Unit) {
        val error = assertThrows(GpxValueFailure::class.java, block)
        assertEquals(category, error.category)
        if (reason != null) assertEquals(reason, error.reason)
    }
}