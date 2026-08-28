package org.beesearch.app

import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

class BeeObservationUiTest {
    private val startedAt = Instant.parse("2026-08-28T12:00:00Z")

    @Test
    fun elapsedTimerUsesFieldFormatBoundaries() {
        assertEquals("00:00", formatElapsedTime(startedAt, startedAt))
        assertEquals("00:59", formatElapsedTime(startedAt, startedAt.plusSeconds(59)))
        assertEquals("01:00", formatElapsedTime(startedAt, startedAt.plusSeconds(60)))
        assertEquals("59:59", formatElapsedTime(startedAt, startedAt.plusSeconds(3_599)))
        assertEquals("1:00:00", formatElapsedTime(startedAt, startedAt.plusSeconds(3_600)))
    }

    @Test
    fun elapsedTimerIsDerivedFromSuppliedInstantsAndClampsClockRollback() {
        assertEquals("02:03", formatElapsedTime(startedAt, startedAt.plusSeconds(123)))
        assertEquals("00:00", formatElapsedTime(startedAt, startedAt.minusSeconds(1)))
    }

    @Test
    fun openAndClosedLatestCyclesDeriveIndependentBeeStates() {
        val flyingBee = bee("WHITE")
        val atPointBee = bee("BLUE")
        val preparedBee = bee("RED")
        val returnTime = startedAt.plusSeconds(40)

        val cards = buildBeeObservationCards(
            bees = listOf(flyingBee, atPointBee, preparedBee),
            flightCycles = listOf(
                cycle(flyingBee, 1, startedAt, null),
                cycle(atPointBee, 1, startedAt, returnTime),
            ),
        )

        assertEquals(BeeFieldState.IN_FLIGHT, cards[0].fieldState)
        assertEquals(startedAt, cards[0].stateStartedAt)
        assertEquals(BeeFieldState.AT_POINT, cards[1].fieldState)
        assertEquals(returnTime, cards[1].stateStartedAt)
        assertNull(cards[2].fieldState)
        assertNull(cards[2].stateStartedAt)
    }

    @Test
    fun latestCycleDeterminesStateAndTimerOrigin() {
        val bee = bee("YELLOW")
        val firstReturn = startedAt.plusSeconds(20)
        val nextDeparture = startedAt.plusSeconds(147)
        val card = buildBeeObservationCards(
            bees = listOf(bee),
            flightCycles = listOf(
                cycle(bee, 2, nextDeparture, null),
                cycle(bee, 1, startedAt, firstReturn),
            ),
        ).single()

        assertEquals(listOf(1, 2), card.cycles.map { it.sequenceNumber })
        assertEquals(BeeFieldState.IN_FLIGHT, card.fieldState)
        assertEquals(nextDeparture, card.stateStartedAt)
    }

    @Test
    fun persistedCyclePresenceSelectsObservationWorkflow() {
        assertEquals(
            ActivePointWorkflowPhase.PREPARATION,
            activePointWorkflowPhase(hasFlightCycles = false),
        )
        assertEquals(
            ActivePointWorkflowPhase.OBSERVATION,
            activePointWorkflowPhase(hasFlightCycles = true),
        )
    }

    private fun bee(color: String) = Bee(
        id = UUID.randomUUID(),
        observationPointId = UUID.randomUUID(),
        markColor = color,
        markPosition = MarkPosition.NONE,
        createdAt = startedAt,
    )

    private fun cycle(
        bee: Bee,
        sequenceNumber: Int,
        departureTime: Instant,
        returnTime: Instant?,
    ) = FlightCycle(
        id = UUID.randomUUID(),
        beeId = bee.id,
        sequenceNumber = sequenceNumber,
        departureTime = departureTime,
        returnTime = returnTime,
        azimuthDeg = null,
        createdAt = departureTime,
        updatedAt = returnTime ?: departureTime,
    )
}
