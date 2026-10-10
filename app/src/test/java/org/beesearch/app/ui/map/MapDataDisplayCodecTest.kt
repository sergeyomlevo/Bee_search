package org.beesearch.app.ui.map

import java.time.LocalDate
import org.beesearch.app.domain.model.ResearchDateInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persisted «Данные на карте» value.
 *
 * A damaged value must never be reported as a partial or invented state: display state is
 * presentation only, so the safe reading is the approved default, never a lost research record.
 */
class MapDataDisplayCodecTest {
    private val may2026 = ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))

    @Test
    fun roundTripKeepsVisibilityAndEveryOwnPeriod() {
        val state = DEFAULT_MAP_DATA_DISPLAY
            .withVisibility(MapDataType.HOLLOW, false)
            .withPeriod(MapDataType.OBSERVATION_POINT, may2026)
            .withPeriod(MapDataType.LOG_HIVE, ResearchDateInterval(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))

        val decoded = MapDataDisplayCodec.decode(MapDataDisplayCodec.encode(state))
        assertEquals(state, decoded)
        assertFalse(decoded!!.isVisible(MapDataType.HOLLOW))
        assertEquals(may2026, decoded.period(MapDataType.OBSERVATION_POINT))
    }

    @Test
    fun defaultStateRoundTripsAndCarriesNoPeriod() {
        val encoded = MapDataDisplayCodec.encode(DEFAULT_MAP_DATA_DISPLAY)
        val decoded = MapDataDisplayCodec.decode(encoded)
        assertEquals(DEFAULT_MAP_DATA_DISPLAY, decoded)
        assertTrue(encoded.startsWith(MapDataDisplayCodec.VERSION))
        MapDataType.entries.forEach { type -> assertNull(decoded!!.period(type)) }
    }

    @Test
    fun absentVersionMismatchAndDamageAreUnreadableInsteadOfPartlyRead() {
        assertNull(MapDataDisplayCodec.decode(null))
        assertNull(MapDataDisplayCodec.decode(""))
        assertNull(MapDataDisplayCodec.decode("v2|{}"))
        assertNull(MapDataDisplayCodec.decode("v1|not json"))
        assertNull(MapDataDisplayCodec.decode("""v1|{"types":"not an object"}"""))
        assertNull(MapDataDisplayCodec.decode("""v1|{"unexpected":{"x":1}}"""))
        // Damage inside one type makes the whole value unreadable rather than silently dropping it.
        assertNull(
            MapDataDisplayCodec.decode(
                """v1|{"types":{"HOLLOW":{"visible":true,"from":"2026-05-01"}}}""",
            ),
        )
        assertNull(
            MapDataDisplayCodec.decode(
                """v1|{"types":{"HOLLOW":{"visible":true,"from":"2026-05-01","to":"01.05.2026"}}}""",
            ),
        )
        assertNull(
            MapDataDisplayCodec.decode(
                """v1|{"types":{"HOLLOW":{"visible":true,"from":"2026-05-31","to":"2026-05-01"}}}""",
            ),
        )
    }

    @Test
    fun aTypeThisBuildDoesNotKnowIsIgnoredAndOtherTypesStillDecode() {
        val decoded = MapDataDisplayCodec.decode(
            """v1|{"types":{"TRAP":{"visible":false,"from":"","to":""},""" +
                """"HOLLOW":{"visible":false,"from":"2026-05-01","to":"2026-05-31"}}}""",
        )
        assertFalse(decoded!!.isVisible(MapDataType.HOLLOW))
        assertEquals(may2026, decoded.period(MapDataType.HOLLOW))
        // Missing entries fall back to the approved default instead of being invented.
        assertTrue(decoded.isVisible(MapDataType.OBSERVATION_POINT))
        assertNull(decoded.period(MapDataType.LOG_HIVE))
    }
}
