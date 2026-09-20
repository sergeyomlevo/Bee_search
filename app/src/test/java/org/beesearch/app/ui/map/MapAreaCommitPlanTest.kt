package org.beesearch.app.ui.map

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The single commit decision shared by «Готово» and Back → «Сохранить».
 *
 * These are the rules that keep a named Ареал alive: an empty draft never saves, never deletes and
 * never creates anything unnamed.
 */
class MapAreaCommitPlanTest {
    private val areaId = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d")
    private val first = MapCoverageFragment(MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0))
    private val second = MapCoverageFragment(MapGeoBounds(north = 57.1, east = 39.1, south = 56.1, west = 38.1))
    private val existingArea = MapArea(id = areaId, name = "Лух", bounds = listOf(first.bounds))
    private val present = MapAreaReadResult.Present(existingArea)

    @Test
    fun `no area and an empty draft exits without creating anything`() {
        assertEquals(
            AreaCommitPlan.ExitWithoutCreating,
            planAreaCommit(MapAreaReadResult.Absent, emptyList(), "Лух"),
        )
    }

    @Test
    fun `no area and one участок asks for a name prefilled from the territory`() {
        assertEquals(
            AreaCommitPlan.CreateWithName("Лух"),
            planAreaCommit(MapAreaReadResult.Absent, listOf(first), "Лух"),
        )
    }

    @Test
    fun `no area and several участки also ask for a name once`() {
        assertEquals(
            AreaCommitPlan.CreateWithName("Лух"),
            planAreaCommit(MapAreaReadResult.Absent, listOf(first, second), "Лух"),
        )
    }

    @Test
    fun `the prefilled name falls back to the neutral name`() {
        assertEquals(
            AreaCommitPlan.CreateWithName(DEFAULT_AREA_NAME),
            planAreaCommit(MapAreaReadResult.Absent, listOf(first), null),
        )
        assertEquals(
            AreaCommitPlan.CreateWithName(DEFAULT_AREA_NAME),
            planAreaCommit(MapAreaReadResult.Absent, listOf(first), "   "),
        )
    }

    @Test
    fun `the prefilled name is trimmed`() {
        assertEquals(
            AreaCommitPlan.CreateWithName("Лух"),
            planAreaCommit(MapAreaReadResult.Absent, listOf(first), "  Лух  "),
        )
    }

    @Test
    fun `an existing area with участки is saved directly without asking the name again`() {
        assertEquals(AreaCommitPlan.UpdateBounds, planAreaCommit(present, listOf(first, second), "Лух"))
    }

    /** A7/§6: an empty draft must never turn into an area deletion. */
    @Test
    fun `an existing area with an empty draft is refused, not saved and not deleted`() {
        val plan = planAreaCommit(present, emptyList(), "Лух")

        assertEquals(AreaCommitPlan.Refused(EMPTY_AREA_MESSAGE), plan)
    }

    @Test
    fun `a damaged stored value refuses every commit`() {
        val corrupt = MapAreaReadResult.Corrupt("bad json")

        assertEquals(
            AreaCommitPlan.Refused(CORRUPT_AREA_MESSAGE),
            planAreaCommit(corrupt, listOf(first), "Лух"),
        )
        assertEquals(
            AreaCommitPlan.Refused(CORRUPT_AREA_MESSAGE),
            planAreaCommit(corrupt, emptyList(), "Лух"),
        )
    }

    @Test
    fun `an unmigrated legacy value refuses every commit instead of overwriting участки`() {
        val legacy = MapAreaReadResult.Legacy(listOf(first.bounds))

        assertEquals(
            AreaCommitPlan.Refused(UNMIGRATED_AREA_MESSAGE),
            planAreaCommit(legacy, listOf(second), "Лух"),
        )
    }

    @Test
    fun `a blank name is invalid and a blank trimmed name is rejected`() {
        assertEquals(null, normalizedAreaName(""))
        assertEquals(null, normalizedAreaName("   "))
        assertEquals("Лух", normalizedAreaName("Лух"))
        assertEquals("Лух", normalizedAreaName("  Лух  "))
    }

    @Test
    fun `the same plan serves gotovo and the back save path`() {
        // Back → «Сохранить» calls the same function, so both paths must agree for every state.
        val states = listOf(
            MapAreaReadResult.Absent,
            present,
            MapAreaReadResult.Corrupt("bad"),
            MapAreaReadResult.Legacy(listOf(first.bounds)),
        )
        states.forEach { state ->
            listOf(emptyList<MapCoverageFragment>(), listOf(first)).forEach { draft ->
                assertEquals(
                    "state=$state draft=${draft.size}",
                    planAreaCommit(state, draft, "Лух"),
                    planAreaCommit(state, draft, "Лух"),
                )
            }
        }
    }
}
