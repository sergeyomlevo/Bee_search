package org.beesearch.app.data.exchange

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The map package file-name grammar.
 *
 * Matching is exact against the stem of the *current* Ареал, because this name is what lets Bee
 * Search offer the right package without the user searching. It is only a convenience: the name never
 * proves coverage, integrity or compatibility, and a manually picked file needs no such name at all.
 */
class MapPackageFileNameTest {
    private val stem = "Лух--7e82a310"

    private fun match(fileName: String) = matchMapPackageFileName(stem, fileName)

    @Test
    fun `the current area is recognised for each version`() {
        assertEquals(MapPackageFileNameMatch.CurrentArea(1), match("Лух--7e82a310--map-v1.pmtiles"))
        assertEquals(MapPackageFileNameMatch.CurrentArea(2), match("Лух--7e82a310--map-v2.pmtiles"))
        assertEquals(MapPackageFileNameMatch.CurrentArea(12), match("Лух--7e82a310--map-v12.pmtiles"))
    }

    @Test
    fun `version zero is malformed`() {
        assertEquals(MapPackageFileNameMatch.Malformed, match("Лух--7e82a310--map-v0.pmtiles"))
    }

    @Test
    fun `a leading zero is malformed`() {
        assertEquals(MapPackageFileNameMatch.Malformed, match("Лух--7e82a310--map-v01.pmtiles"))
        assertEquals(MapPackageFileNameMatch.Malformed, match("Лух--7e82a310--map-v001.pmtiles"))
    }

    @Test
    fun `a negative version is malformed`() {
        assertEquals(MapPackageFileNameMatch.Malformed, match("Лух--7e82a310--map-v-1.pmtiles"))
    }

    @Test
    fun `a non-numeric version is malformed`() {
        listOf("final", "Final", "latest", "1.0", "1a", "").forEach { version ->
            assertEquals(
                "version «$version» must not be a candidate",
                MapPackageFileNameMatch.Malformed,
                match("Лух--7e82a310--map-v$version.pmtiles"),
            )
        }
        // A year without the version marker is not a version either.
        assertEquals(MapPackageFileNameMatch.Malformed, match("Лух--7e82a310--map-2026.pmtiles"))
    }

    @Test
    fun `any positive integer is a version`() {
        // The contract is `[1-9][0-9]*`, so a large number such as a year is a valid version and is
        // ordered numerically rather than by its digits or by the file's timestamp.
        assertEquals(MapPackageFileNameMatch.CurrentArea(2026), match("Лух--7e82a310--map-v2026.pmtiles"))
        assertTrue(
            (match("Лух--7e82a310--map-v2026.pmtiles") as MapPackageFileNameMatch.CurrentArea).version >
                (match("Лух--7e82a310--map-v12.pmtiles") as MapPackageFileNameMatch.CurrentArea).version,
        )
    }

    @Test
    fun `a name without a version is malformed`() {
        assertEquals(MapPackageFileNameMatch.Malformed, match("Лух--7e82a310.pmtiles"))
        assertEquals(MapPackageFileNameMatch.Malformed, match("Лух--7e82a310--map.pmtiles"))
        assertEquals(MapPackageFileNameMatch.Malformed, match("forest-map.pmtiles"))
    }

    @Test
    fun `a wrong extension is reported as such`() {
        assertEquals(MapPackageFileNameMatch.WrongExtension, match("Лух--7e82a310--map-v1.txt"))
        assertEquals(MapPackageFileNameMatch.WrongExtension, match("Лух--7e82a310--map-v1.pmtiles.zip"))
    }

    @Test
    fun `another area id is not a candidate`() {
        assertEquals(MapPackageFileNameMatch.OtherArea, match("Лух--12345678--map-v1.pmtiles"))
    }

    @Test
    fun `the same short id under another name is not a candidate`() {
        // The whole stem must match: a renamed Ареал keeps its id but must not claim this Ареал's map.
        assertEquals(MapPackageFileNameMatch.OtherArea, match("СтароеИмя--7e82a310--map-v1.pmtiles"))
        assertTrue(match("Лух--7e82a310-extra--map-v1.pmtiles") !is MapPackageFileNameMatch.CurrentArea)
    }

    @Test
    fun `a similar name containing the stem is not a candidate`() {
        assertEquals(MapPackageFileNameMatch.OtherArea, match("XЛух--7e82a310--map-v1.pmtiles"))
        assertEquals(MapPackageFileNameMatch.Malformed, match("Лух--7e82a310X--map-v1.pmtiles"))
        assertEquals(MapPackageFileNameMatch.Malformed, match("Лух--7e82a310--map-v1-extra.pmtiles"))
    }

    @Test
    fun `a name of another area with a bad version is not a candidate either`() {
        // Whatever the reason, it must never become a candidate of the current Ареал.
        assertTrue(match("Лух--12345678--map-v0.pmtiles") !is MapPackageFileNameMatch.CurrentArea)
        assertTrue(match("Лух--12345678--map-final.pmtiles") !is MapPackageFileNameMatch.CurrentArea)
    }

    @Test
    fun `large versions are parsed numerically`() {
        assertEquals(MapPackageFileNameMatch.CurrentArea(123456), match("Лух--7e82a310--map-v123456.pmtiles"))
        // A number that cannot be a version is malformed rather than a wrapped negative value.
        assertEquals(MapPackageFileNameMatch.Malformed, match("Лух--7e82a310--map-v99999999999999999999.pmtiles"))
    }

    @Test
    fun `versions are compared numerically and not lexicographically`() {
        val low = match("Лух--7e82a310--map-v2.pmtiles") as MapPackageFileNameMatch.CurrentArea
        val high = match("Лух--7e82a310--map-v12.pmtiles") as MapPackageFileNameMatch.CurrentArea

        assertTrue(high.version > low.version)
        // Lexicographic ordering would put "v12" before "v2"; the parser must not.
        assertTrue("v12".compareTo("v2") < 0)
    }

    @Test
    fun `the canonical name is matched by its own parser`() {
        listOf(1, 2, 12, 999).forEach { version ->
            val name = canonicalMapPackageFileName(stem, version)
            assertEquals(MapPackageFileNameMatch.CurrentArea(version), match(name))
        }
    }
}
