package org.beesearch.app

import org.beesearch.app.domain.model.BeePresenceResultRequiredException
import org.beesearch.app.domain.model.BeesAlreadyFoundException
import org.beesearch.app.domain.model.AzimuthCaptureAlreadyConsumedException
import org.beesearch.app.domain.model.AzimuthCaptureRequiresOpenFlightCycleException
import org.beesearch.app.domain.model.ObservationPointNotActiveException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DomainErrorMessagesTest {
    @Test
    fun `expected observation errors use Russian user messages`() {
        val required = userMessageFor(
            BeePresenceResultRequiredException(),
            fallback = "fallback",
        )
        val beesFound = userMessageFor(BeesAlreadyFoundException(), fallback = "fallback")
        val inactive = userMessageFor(
            ObservationPointNotActiveException(),
            fallback = "fallback",
        )

        assertEquals(
            "Сначала добавьте пчёл или отметьте, что пчёлы отсутствуют",
            required,
        )
        assertEquals(
            "Пчёлы уже добавлены. Нельзя отметить, что они отсутствуют",
            beesFound,
        )
        assertEquals(
            "Эта точка наблюдения уже завершена или больше не активна",
            inactive,
        )
        assertFalse(required.contains("Bee presence"))
        assertFalse(beesFound.contains("No bees"))
        assertFalse(inactive.contains("not active"))
    }

    @Test
    fun `unexpected error uses neutral operation fallback`() {
        assertEquals(
            "Не удалось сохранить отсутствие пчёл",
            userMessageFor(
                IllegalStateException("raw technical details"),
                fallback = "Не удалось сохранить отсутствие пчёл",
            ),
        )
    }

    @Test
    fun `field azimuth capture errors use Russian messages`() {
        assertEquals(
            "Возможность зафиксировать азимут этого вылета уже использована",
            userMessageFor(AzimuthCaptureAlreadyConsumedException(), fallback = "fallback"),
        )
        assertEquals(
            "Азимут можно зафиксировать только во время текущего вылета",
            userMessageFor(AzimuthCaptureRequiresOpenFlightCycleException(), fallback = "fallback"),
        )
    }
}
