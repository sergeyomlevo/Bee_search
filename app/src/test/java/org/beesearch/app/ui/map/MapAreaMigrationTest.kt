package org.beesearch.app.ui.map

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Migration of an unnamed legacy selection into a named Ареал, and the naming rule it uses.
 *
 * The geometry is carried over untouched: values are not rounded, re-normalised, merged, sorted or
 * de-overlapped, because they are already the user's stored intent.
 */
class MapAreaMigrationTest {
    private val areaId = UUID.fromString("7e82a310-1f4c-4a2b-9c3d-8e5f6a7b8c9d")
    private val legacyBounds = listOf(
        MapGeoBounds(north = 57.111673, east = 39.026918, south = 56.562186, west = 38.470994),
        MapGeoBounds(north = 56.920000, east = 38.910000, south = 56.810000, west = 38.720000),
    )

    @Test
    fun `a legacy selection becomes exactly one area`() {
        val area = migratedMapArea(bounds = legacyBounds, territoryName = "Лух", id = areaId)

        assertEquals(areaId, area.id)
        assertEquals(legacyBounds, area.bounds)
    }

    @Test
    fun `migration takes the name from the territory`() {
        assertEquals("Лух", migratedMapArea(legacyBounds, "Лух", areaId).name)
    }

    @Test
    fun `migration trims the territory name`() {
        assertEquals("Лух", migratedMapArea(legacyBounds, "  Лух  ", areaId).name)
    }

    @Test
    fun `a missing or blank territory name falls back to the neutral area name`() {
        assertEquals(DEFAULT_AREA_NAME, migratedMapArea(legacyBounds, null, areaId).name)
        assertEquals(DEFAULT_AREA_NAME, migratedMapArea(legacyBounds, "", areaId).name)
        assertEquals(DEFAULT_AREA_NAME, migratedMapArea(legacyBounds, "   ", areaId).name)
    }

    @Test
    fun `the initial area name is a pure decision`() {
        assertEquals("Лух", initialAreaName("Лух"))
        assertEquals("Лух", initialAreaName(" Лух "))
        assertEquals(DEFAULT_AREA_NAME, initialAreaName(null))
        assertEquals(DEFAULT_AREA_NAME, initialAreaName("  "))
    }

    @Test
    fun `migrated geometry keeps its exact order overlap and values`() {
        val first = MapGeoBounds(north = 57.0, east = 39.0, south = 56.0, west = 38.0)
        val overlapping = MapGeoBounds(north = 56.9, east = 38.9, south = 56.5, west = 38.5)
        val bounds = listOf(first, overlapping, first)

        val area = migratedMapArea(bounds, "Лух", areaId)

        assertEquals(bounds, area.bounds)
        assertEquals(first, area.bounds.first())
        assertEquals(first, area.bounds.last())
    }

    @Test
    fun `a legacy value that was never migrated can be read and re-encoded unchanged`() {
        val legacy = MapAreaCodec.encodeLegacy(legacyBounds)

        val decoded = MapAreaCodec.decode(legacy)

        assertEquals(MapAreaReadResult.Legacy(legacyBounds), decoded)
        assertEquals(legacy, MapAreaCodec.encodeLegacy((decoded as MapAreaReadResult.Legacy).bounds))
    }

    @Test
    fun `an empty legacy selection produces no area at all`() {
        assertEquals(MapAreaReadResult.Absent, MapAreaCodec.decode(MapAreaCodec.encodeLegacy(emptyList())))
    }

    @Test
    fun `each migration call is given its own identity by the caller`() {
        val first = migratedMapArea(legacyBounds, "Лух", UUID.randomUUID())
        val second = migratedMapArea(legacyBounds, "Лух", UUID.randomUUID())

        assertNotEquals(first.id, second.id)
        assertEquals(first.bounds, second.bounds)
        assertEquals(first.name, second.name)
    }

    @Test
    fun `map package validation receives the area bounds unchanged`() {
        val area = MapArea(id = areaId, name = "Лух", bounds = legacyBounds)

        val desiredCoverage = area.coverageFragments()

        assertEquals(listOf(MapCoverageFragment(legacyBounds[0]), MapCoverageFragment(legacyBounds[1])), desiredCoverage)
        assertEquals(legacyBounds, desiredCoverage.map { it.bounds })
    }
}
