package org.beesearch.app

import java.time.Instant
import org.beesearch.app.domain.heading.HeadingAccuracy
import org.beesearch.app.domain.heading.HeadingState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The observation screen's azimuth wording is a real flight azimuth of the bee.
 *
 * The physical object cards and forms name the Hollow/LogHive entrance direction instead
 * («Направление летка»), so this test guards the other direction: renaming the object
 * characteristic must not rename the bee/flight azimuth copy.
 */
class HeadingUiTest {

    private fun available(deg: Int) = HeadingState.Available(deg, HeadingAccuracy.HIGH, Instant.EPOCH)

    @Test
    fun savedFlightAzimuthKeepsTheAzimuthWording() {
        assertEquals(
            "Сохранённый азимут 113°",
            headingContentDescription(persistedAzimuth = 113.0, headingState = available(113)),
        )
    }

    @Test
    fun unfixedAndRemovedFlightAzimuthKeepTheAzimuthWording() {
        assertEquals(
            "Азимут не зафиксирован. Пчела находится на точке",
            headingContentDescription(persistedAzimuth = null, headingState = available(0), isInFlight = false),
        )
        assertEquals(
            "Азимут удалён. Повторная фиксация для этого вылета недоступна",
            headingContentDescription(
                persistedAzimuth = null,
                headingState = available(0),
                isInFlight = true,
                captureConsumed = true,
            ),
        )
    }
}
