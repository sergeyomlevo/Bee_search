package org.beesearch.app.data.local.room

import org.beesearch.app.domain.model.MarkPosition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RoomConvertersTest {
    private val converters = RoomConverters()

    @Test
    fun readsThoraxAndAbdomenTokensWrittenByCompatibleDevBuilds() {
        assertEquals(MarkPosition.THORAX, converters.stringToMarkPosition("THORAX"))
        assertEquals(MarkPosition.ABDOMEN, converters.stringToMarkPosition("ABDOMEN"))
    }

    @Test
    fun currentMarkPositionTokensStillRoundTrip() {
        MarkPosition.entries.forEach { position ->
            assertEquals(
                position,
                converters.stringToMarkPosition(converters.markPositionToString(position)),
            )
        }
    }

    @Test
    fun localDateUsesIsoCalendarRepresentation() {
        val value = LocalDate.of(2026, 1, 9)

        assertEquals("2026-01-09", converters.localDateToString(value))
        assertEquals(value, converters.stringToLocalDate("2026-01-09"))
    }

    @Test
    fun localDateRoundTripsMonthAndYearBoundaries() {
        listOf(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2027, 1, 1),
        ).forEach { value ->
            assertEquals(value, converters.stringToLocalDate(converters.localDateToString(value)))
        }
    }
}
