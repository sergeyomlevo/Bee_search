package org.beesearch.app.ui.map

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPackageContractTest {
    @Test
    fun `valid v1 manifest and PMTiles validate selected coverage`() {
        val packageFile = pmtilesFile()
        val manifest = parseManifest(packageFile, coverage = rectangle(56.4, 42.7, 56.1, 42.2))

        val validated = MapPackageValidator.validate(
            manifest = manifest,
            pmtilesFile = packageFile,
            desiredCoverage = listOf(rectangle(56.3, 42.6, 56.2, 42.3)),
        )

        assertEquals(8, validated.header.minZoom)
        assertEquals(15, validated.header.maxZoom)
        assertEquals("fixture.pmtiles", validated.pmtilesFile.name)
    }

    @Test
    fun `hash mismatch rejects package before activation`() {
        val packageFile = pmtilesFile()
        val manifest = parseManifest(packageFile).copy(pmtilesSha256 = "0".repeat(64))

        assertRejected { MapPackageValidator.validate(manifest, packageFile, listOf(rectangle(56.3, 42.6, 56.2, 42.3))) }
    }

    @Test
    fun `incomplete declared coverage is not ready for desired rectangle`() {
        val packageFile = pmtilesFile()
        val manifest = parseManifest(packageFile, coverage = rectangle(56.25, 42.5, 56.1, 42.2))

        assertRejected { MapPackageValidator.validate(manifest, packageFile, listOf(rectangle(56.3, 42.6, 56.2, 42.3))) }
    }

    @Test
    fun `unsupported profile rejects package`() {
        val packageFile = pmtilesFile()
        val manifest = parseManifest(packageFile).copy(profileVersion = "v2")

        assertRejected { MapPackageValidator.validate(manifest, packageFile, listOf(rectangle(56.3, 42.6, 56.2, 42.3))) }
    }

    @Test
    fun `parser rejects manifest which embeds territory identity`() {
        val packageFile = pmtilesFile()
        val text = manifestText(packageFile).replace(
            "\"policy\": \"coverage_fragments_only\"",
            "\"policy\": \"coverage_fragments_only\", \"territoryId\": \"forbidden\"",
        )

        assertRejected { MapPackageManifestParser.parse(text) }
    }

    private fun parseManifest(file: File, coverage: MapCoverageFragment = rectangle(56.4, 42.7, 56.1, 42.2)): MapPackageManifest =
        MapPackageManifestParser.parse(manifestText(file, coverage))

    private fun manifestText(file: File, coverage: MapCoverageFragment = rectangle(56.4, 42.7, 56.1, 42.2)): String {
        val bounds = coverage.bounds
        return """
            {
              "schemaVersion": 1,
              "packageId": "fixture-v1",
              "territoryCompatibility": { "policy": "coverage_fragments_only" },
              "datasetVersion": "fixture-dataset",
              "profileId": "bee-search-field",
              "profileVersion": "v1",
              "styleVersion": "vector-pmtiles-v1",
              "coverageFragments": [{
                "west": ${bounds.west}, "south": ${bounds.south},
                "east": ${bounds.east}, "north": ${bounds.north}
              }],
              "minZoom": 8,
              "maxZoom": 15,
              "pmtilesFile": "fixture.pmtiles",
              "pmtilesByteLength": ${file.length()},
              "pmtilesSha256": "${MapPackageValidator.sha256(file)}"
            }
        """.trimIndent()
    }

    private fun pmtilesFile(): File {
        val directory = Files.createTempDirectory("map-package-").toFile()
        val file = File(directory, "fixture.pmtiles")
        val bytes = ByteArray(127)
        "PMTiles".encodeToByteArray().copyInto(bytes, 0)
        bytes[7] = 3
        bytes[100] = 8
        bytes[101] = 15
        putE7(bytes, 102, 42.2)
        putE7(bytes, 106, 56.1)
        putE7(bytes, 110, 42.7)
        putE7(bytes, 114, 56.4)
        file.writeBytes(bytes)
        file.deleteOnExit()
        directory.deleteOnExit()
        return file
    }

    private fun rectangle(north: Double, east: Double, south: Double, west: Double) =
        MapCoverageFragment(MapGeoBounds(north = north, east = east, south = south, west = west))

    private fun putE7(target: ByteArray, start: Int, value: Double) {
        val number = (value * 10_000_000).toInt()
        target[start] = number.toByte()
        target[start + 1] = (number ushr 8).toByte()
        target[start + 2] = (number ushr 16).toByte()
        target[start + 3] = (number ushr 24).toByte()
    }

    private fun assertRejected(operation: () -> Unit) {
        try {
            operation()
        } catch (_: MapPackageValidationException) {
            return
        }
        assertTrue("Expected D065 validation to reject the package", false)
    }
}
