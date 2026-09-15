package org.beesearch.app

import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

class BeeObservationUiTest {
    private val startedAt = Instant.parse("2026-08-28T12:00:00Z")

    @Test
    fun availableMarksAreDerivedFromCatalogMinusPersistedBees() {
        assertEquals(15, availableBeeMarks(emptyList()).size)

        val used = bee("WHITE")
        val remaining = availableBeeMarks(listOf(used))

        assertEquals(14, remaining.size)
        assertFalse(
            remaining.any {
                it.markColor == used.markColor && it.markPosition == used.markPosition
            },
        )
        assertEquals(remaining, availableBeeMarks(listOf(used)))
    }

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
        assertEquals(BeeFieldState.AT_POINT, cards[2].fieldState)
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
    fun cardsGroupStatesAndKeepLongestDurationsAtTheSharedBoundary() {
        val longestFlying = bee("WHITE")
        val newestFlying = bee("YELLOW")
        val longestAtPoint = bee("BLUE")
        val newestAtPoint = bee("RED")
        val withoutCycle = bee("GREEN")

        val cards = buildBeeObservationCards(
            bees = listOf(longestAtPoint, newestFlying, withoutCycle, longestFlying, newestAtPoint),
            flightCycles = listOf(
                cycle(longestFlying, 1, startedAt, null),
                cycle(newestFlying, 1, startedAt.plusSeconds(30), null),
                cycle(longestAtPoint, 1, startedAt, startedAt.plusSeconds(20)),
                cycle(newestAtPoint, 1, startedAt, startedAt.plusSeconds(45)),
            ),
        )

        assertEquals(
            listOf(newestFlying, longestFlying, longestAtPoint, newestAtPoint, withoutCycle),
            cards.map { it.bee },
        )
    }

    @Test
    fun inFlightCardsPrioritizeCycleNumberThenFlightDurationAtTheBoundary() {
        val firstCycleLongFlight = bee("WHITE")
        val firstCycleNewFlight = bee("YELLOW")
        val secondCycleLongFlight = bee("BLUE")
        val secondCycleNewFlight = bee("RED")
        val atPoint = bee("GREEN")

        val cards = buildBeeObservationCards(
            bees = listOf(secondCycleLongFlight, atPoint, firstCycleLongFlight, secondCycleNewFlight, firstCycleNewFlight),
            flightCycles = listOf(
                cycle(firstCycleLongFlight, 1, startedAt, null),
                cycle(firstCycleNewFlight, 1, startedAt.plusSeconds(30), null),
                cycle(secondCycleLongFlight, 1, startedAt, startedAt.plusSeconds(45)),
                cycle(secondCycleLongFlight, 2, startedAt.plusSeconds(60), null),
                cycle(secondCycleNewFlight, 1, startedAt, startedAt.plusSeconds(45)),
                cycle(secondCycleNewFlight, 2, startedAt.plusSeconds(90), null),
                cycle(atPoint, 1, startedAt, startedAt.plusSeconds(50)),
            ),
        )

        assertEquals(
            listOf(
                firstCycleNewFlight,
                firstCycleLongFlight,
                secondCycleNewFlight,
                secondCycleLongFlight,
                atPoint,
            ),
            cards.map { it.bee },
        )
    }

    @Test
    fun lastReversibleActionIsDerivedOnlyFromTheCurrentLatestCycle() {
        val bee = bee("WHITE")
        val firstReturn = startedAt.plusSeconds(20)
        val secondDeparture = startedAt.plusSeconds(30)
        val secondCycle = cycle(bee, 2, secondDeparture, null).copy(azimuthDeg = 142.0)

        val withAzimuth = BeeObservationCardModel(bee, listOf(cycle(bee, 1, startedAt, firstReturn), secondCycle))
        assertEquals(secondCycle.id, (withAzimuth.lastReversibleAction as BeeLastReversibleAction.Azimuth).flightCycleId)

        val returned = BeeObservationCardModel(bee, listOf(secondCycle.copy(returnTime = startedAt.plusSeconds(40))))
        assertEquals(secondCycle.id, (returned.lastReversibleAction as BeeLastReversibleAction.Return).flightCycleId)

        val nextFlight = BeeObservationCardModel(bee, listOf(secondCycle.copy(azimuthDeg = null)))
        assertEquals(secondCycle.id, (nextFlight.lastReversibleAction as BeeLastReversibleAction.NextFlight).flightCycleId)

        val firstDeparture = BeeObservationCardModel(
            bee,
            listOf(cycle(bee, 1, startedAt, null, cancellationEligible = true)),
        )
        assertEquals(
            firstDeparture.latestCycle?.id,
            (firstDeparture.lastReversibleAction as BeeLastReversibleAction.FirstDeparture).flightCycleId,
        )
    }

    @Test
    fun cardsWithTheSameStateTimestampKeepInputOrder() {
        val firstReleased = bee("WHITE")
        val secondReleased = bee("BLUE")

        val cards = buildBeeObservationCards(
            bees = listOf(secondReleased, firstReleased),
            flightCycles = listOf(
                cycle(firstReleased, 1, startedAt, null),
                cycle(secondReleased, 1, startedAt, null),
            ),
        )

        assertEquals(listOf(secondReleased, firstReleased), cards.map { it.bee })
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
        cancellationEligible: Boolean = false,
    ) = FlightCycle(
        id = UUID.randomUUID(),
        beeId = bee.id,
        sequenceNumber = sequenceNumber,
        departureTime = departureTime,
        returnTime = returnTime,
        azimuthDeg = null,
        azimuthCaptureConsumed = false,
        isFirstDepartureCancellationEligible = cancellationEligible,
        createdAt = departureTime,
        updatedAt = returnTime ?: departureTime,
    )
}
