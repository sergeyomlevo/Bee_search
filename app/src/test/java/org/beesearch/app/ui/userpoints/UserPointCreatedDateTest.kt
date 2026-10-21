package org.beesearch.app.ui.userpoints

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPointCreatedDateTest {
    @Test
    fun formatsTheStoredCreationMomentAsALocalCalendarDate() {
        assertEquals(
            "Создана: 21.10.2026",
            userPointCreatedDateText(
                Instant.parse("2026-10-21T10:15:00Z"),
                ZoneId.of("Europe/Moscow"),
            ),
        )
    }

    @Test
    fun localZoneControlsTheCalendarDayAndTimeOfDayIsNotShown() {
        val lateEvening = Instant.parse("2026-10-21T23:30:00Z")

        assertEquals(
            "Создана: 22.10.2026",
            userPointCreatedDateText(lateEvening, ZoneId.of("Europe/Moscow")),
        )
        assertEquals(
            "Создана: 21.10.2026",
            userPointCreatedDateText(lateEvening, ZoneId.of("UTC")),
        )
    }
}
