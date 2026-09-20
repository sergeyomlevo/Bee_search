package org.beesearch.app.ui.map

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A file name never replaces validation.
 *
 * Automatic discovery only saves the user a search: whatever it offers is still checked for the
 * manifest contract, integrity and D065 geographic coverage before it can be activated. These tests
 * pin the two directions of that rule - a perfect name with a bad package is rejected, and a package
 * with an unrelated name is accepted when it really fits.
 */
class AreaMapValidationInvariantTest {
    private val desired = listOf(testRectangle(56.3, 42.6, 56.2, 42.3))

    /**
     * A package whose declared coverage sits inside its own PMTiles header but does not reach the
     * desired участок: this is the D065 geographic comparison, not the header check.
     */
    private val tooSmallCoverage = testRectangle(56.35, 42.60, 56.30, 42.50)

    private fun assertRejected(operation: () -> Unit) {
        try {
            operation()
        } catch (_: MapPackageValidationException) {
            return
        }
        assertTrue("Expected D065 validation to reject the package", false)
    }

    @Test
    fun `an exactly named package with compatible coverage may proceed`() {
        val file = testPmtilesFile(fileName = "Лух--7e82a310--map-v1.pmtiles")
        val manifest = testManifest(file)

        val validated = MapPackageValidator.validate(manifest, file, desired)

        assertEquals("Лух--7e82a310--map-v1.pmtiles", validated.pmtilesFile.name)
    }

    @Test
    fun `an exactly named package with incompatible coverage is rejected`() {
        // Perfectly named, entirely valid package, but it does not cover the current Ареал.
        val file = testPmtilesFile(fileName = "Лух--7e82a310--map-v1.pmtiles")
        val manifest = testManifest(file, coverage = tooSmallCoverage)

        assertRejected { MapPackageValidator.validate(manifest, file, desired) }
    }

    @Test
    fun `an exactly named package never bypasses the manifest contract`() {
        val file = testPmtilesFile(fileName = "Лух--7e82a310--map-v12.pmtiles")
        // A name cannot make an unsupported profile acceptable.
        val manifest = testManifest(file).copy(profileVersion = "v2")

        assertRejected { MapPackageValidator.validate(manifest, file, desired) }
    }

    @Test
    fun `an exactly named package never bypasses the integrity checks`() {
        val file = testPmtilesFile(fileName = "Лух--7e82a310--map-v12.pmtiles")

        assertRejected {
            MapPackageValidator.validate(testManifest(file).copy(pmtilesSha256 = "0".repeat(64)), file, desired)
        }
        assertRejected {
            MapPackageValidator.validate(testManifest(file).copy(pmtilesByteLength = 4096), file, desired)
        }
        assertRejected {
            MapPackageValidator.validate(testManifest(file).copy(pmtilesFile = "another.pmtiles"), file, desired)
        }
    }

    @Test
    fun `a differently named package is accepted when it fits`() {
        // The user picked it by hand: no Ареал stem appears anywhere in this pair.
        val file = testPmtilesFile(fileName = "forest-map.pmtiles")
        val manifest = testManifest(file)

        val validated = MapPackageValidator.validate(manifest, file, desired)

        assertEquals("forest-map.pmtiles", validated.pmtilesFile.name)
    }

    @Test
    fun `a manually selected package of another area is accepted when it fits`() {
        val file = testPmtilesFile(fileName = "ДругаяКарта--abcdef12--map-v3.pmtiles")
        val manifest = testManifest(file)

        val validated = MapPackageValidator.validate(manifest, file, desired)

        assertEquals("ДругаяКарта--abcdef12--map-v3.pmtiles", validated.pmtilesFile.name)
    }

    @Test
    fun `a differently named package is still rejected when it does not fit`() {
        val file = testPmtilesFile(fileName = "forest-map.pmtiles")
        val manifest = testManifest(file, coverage = tooSmallCoverage)

        assertRejected { MapPackageValidator.validate(manifest, file, desired) }
    }

    @Test
    fun `an empty Ареал cannot be satisfied by any map`() {
        val file = testPmtilesFile(fileName = "Лух--7e82a310--map-v1.pmtiles")

        assertRejected { MapPackageValidator.validate(testManifest(file), file, emptyList()) }
    }

    @Test
    fun `the coverage rejection keeps its documented wording`() {
        val file = testPmtilesFile(fileName = "Лух--7e82a310--map-v1.pmtiles")
        val manifest = testManifest(file, coverage = tooSmallCoverage)

        val message = try {
            MapPackageValidator.validate(manifest, file, desired)
            null
        } catch (error: MapPackageValidationException) {
            error.message
        }

        // The Ареал screen recognises exactly this message to explain the situation in its own words.
        assertEquals(MAP_PACKAGE_COVERAGE_MISMATCH_MESSAGE, message)
    }

    @Test
    fun `a package is validated from its files and never from its name`() {
        // The validator takes no Ареал stem at all: this asserts the signature stays that way, because
        // a name-based compatibility check would break manually delivered packages.
        val file = testPmtilesFile(fileName = "anything-at-all.pmtiles")
        val manifest = testManifest(file)

        val validated = MapPackageValidator.validate(manifest, file, desired)

        assertTrue(validated.pmtilesFile.isFile)
        assertTrue(File(validated.pmtilesFile.name).name == "anything-at-all.pmtiles")
    }
}
