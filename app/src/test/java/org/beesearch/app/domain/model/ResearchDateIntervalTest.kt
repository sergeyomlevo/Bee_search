package org.beesearch.app.domain.model

import java.time.LocalDate
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ResearchDateIntervalTest {
    @Test
    fun acceptsEqualAndOrderedDates() {
        val date = LocalDate.of(2026, 5, 10)

        assertEquals(ResearchDateInterval(date, date), ResearchDateInterval(date, date))
        assertEquals(
            ResearchDateInterval(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)).fromDate,
            LocalDate.of(2026, 5, 1),
        )
    }

    @Test
    fun rejectsReversedDates() {
        assertThrows(IllegalArgumentException::class.java) {
            ResearchDateInterval(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 10))
        }
    }

    @Test
    fun intervalUsesCanonicalResearchDateParser() {
        assertThrows(IllegalArgumentException::class.java) {
            ResearchDateInterval(LocalDate.of(10000, 1, 1), LocalDate.of(10000, 1, 2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResearchDateInterval(LocalDate.of(-1, 1, 1), LocalDate.of(-1, 1, 2))
        }
    }

    @Test
    fun dateIntervalIsIndependentOfDefaultTimezone() {
        val oldZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            val honolulu = ResearchDateInterval(
                parseResearchDate("2026-05-10"),
                parseResearchDate("2026-05-12"),
            )
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val tokyo = ResearchDateInterval(
                parseResearchDate("2026-05-10"),
                parseResearchDate("2026-05-12"),
            )
            assertEquals(honolulu, tokyo)
        } finally {
            TimeZone.setDefault(oldZone)
        }
    }
}
