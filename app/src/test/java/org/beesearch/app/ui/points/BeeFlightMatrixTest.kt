package org.beesearch.app.ui.points

import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeObservationHistory
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeeFlightMatrixTest {
    @Test
    fun `one Bee and one cycle produce one row and one column`() {
        val bee = bee(index = 1)
        val matrix = buildBeeFlightMatrix(listOf(history(bee, cycle(bee, number = 1, durationSeconds = 312))))

        assertEquals(listOf(1), matrix.columns)
        assertEquals(1, matrix.rows.size)
        assertEquals("Пчела 1", matrix.rows.single().label)
        assertEquals("5:12", matrix.rows.single().cells.single()?.durationText)
    }

    @Test
    fun `multiple cycles use numeric columns and preserve missing numbers`() {
        val bee = bee(index = 1)
        val matrix = buildBeeFlightMatrix(
            listOf(history(bee, cycle(bee, 3, 303), cycle(bee, 1, 288))),
        )

        assertEquals(listOf(1, 2, 3), matrix.columns)
        assertEquals(1, matrix.rows.single().cells[0]?.cycle?.sequenceNumber)
        assertNull(matrix.rows.single().cells[1])
        assertEquals(3, matrix.rows.single().cells[2]?.cycle?.sequenceNumber)
    }

    @Test
    fun `maximum cycle across Bees defines aligned rectangular columns`() {
        val first = bee(index = 1, createdAt = "2026-09-20T10:00:00Z")
        val second = bee(index = 2, createdAt = "2026-09-20T10:01:00Z")
        val matrix = buildBeeFlightMatrix(
            listOf(
                history(first, cycle(first, 1, 120), cycle(first, 2, 180)),
                history(second, cycle(second, 1, 240), cycle(second, 4, 300)),
            ),
        )

        assertEquals(listOf(1, 2, 3, 4), matrix.columns)
        assertEquals(listOf(2, 2), matrix.rows.map { row -> row.cells.count { it != null } })
        assertNull(matrix.rows[0].cells[2])
        assertNull(matrix.rows[0].cells[3])
        assertNull(matrix.rows[1].cells[1])
        assertNull(matrix.rows[1].cells[2])
    }

    @Test
    fun `Bee row order is creation time then id and keeps mark semantics`() {
        val earlier = bee(
            index = 1,
            createdAt = "2026-09-20T10:00:00Z",
            color = "RED",
            position = MarkPosition.ABDOMEN,
        )
        val later = bee(
            index = 2,
            createdAt = "2026-09-20T10:01:00Z",
            color = "WHITE",
            position = MarkPosition.THORAX,
        )

        val matrix = buildBeeFlightMatrix(
            listOf(history(later, cycle(later, 1, 10)), history(earlier, cycle(earlier, 1, 20))),
        )

        assertEquals(listOf(earlier.id, later.id), matrix.rows.map { it.bee.id })
        assertEquals("RED", matrix.rows[0].bee.markColor)
        assertEquals(MarkPosition.ABDOMEN, matrix.rows[0].bee.markPosition)
    }

    @Test
    fun `azimuth is shown only when present and open cycle has no fake duration`() {
        val bee = bee(index = 1)
        val withAzimuth = cycle(bee, 1, 20).copy(azimuthDeg = 248.0)
        val withoutAzimuth = cycle(bee, 2, 30)
        val open = cycle(bee, 3, null)
        val cells = buildBeeFlightMatrix(listOf(history(bee, withAzimuth, withoutAzimuth, open)))
            .rows.single().cells

        assertEquals("248°", cells[0]?.azimuthText)
        assertNull(cells[1]?.azimuthText)
        assertEquals("…", cells[2]?.durationText)
    }

    @Test
    fun `duration formatter stays compact below and above one hour`() {
        val start = Instant.parse("2026-09-20T10:00:00Z")

        assertEquals("5:12", formatMatrixFlightDuration(start, start.plusSeconds(312)))
        assertEquals("1:05:07", formatMatrixFlightDuration(start, start.plusSeconds(3_907)))
        assertEquals("0:00", formatMatrixFlightDuration(start, start.minusSeconds(1)))
    }

    private fun history(bee: Bee, vararg cycles: FlightCycle) =
        BeeObservationHistory(bee = bee, flightCycles = cycles.toList())

    private fun bee(
        index: Int,
        createdAt: String = "2026-09-20T10:00:00Z",
        color: String = "YELLOW",
        position: MarkPosition = MarkPosition.THORAX,
    ) = Bee(
        id = UUID(0, index.toLong()),
        observationPointId = pointId,
        markColor = color,
        markPosition = position,
        createdAt = Instant.parse(createdAt),
    )

    private fun cycle(bee: Bee, number: Int, durationSeconds: Long?): FlightCycle {
        val departure = Instant.parse("2026-09-20T10:00:00Z").plusSeconds(number.toLong() * 600)
        return FlightCycle(
            id = UUID(number.toLong(), bee.id.leastSignificantBits),
            beeId = bee.id,
            sequenceNumber = number,
            departureTime = departure,
            returnTime = durationSeconds?.let(departure::plusSeconds),
            azimuthDeg = null,
            azimuthCaptureConsumed = false,
            createdAt = departure,
            updatedAt = durationSeconds?.let(departure::plusSeconds) ?: departure,
        )
    }

    private companion object {
        val pointId: UUID = UUID(0, 100)
    }
}
