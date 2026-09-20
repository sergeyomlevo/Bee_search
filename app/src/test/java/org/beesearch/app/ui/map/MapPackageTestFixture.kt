package org.beesearch.app.ui.map

import java.io.File
import java.nio.file.Files

/**
 * Test fixtures for a D065 map package: a real PMTiles v3 header plus the manifest that describes it.
 *
 * The header is written byte by byte at the offsets the validator reads, so a package built here goes
 * through exactly the same parsing, size, hash, zoom and coverage checks as a delivered one.
 */
internal fun testRectangle(north: Double, east: Double, south: Double, west: Double): MapCoverageFragment =
    MapCoverageFragment(MapGeoBounds(north = north, east = east, south = south, west = west))

internal fun testPmtilesFile(
    fileName: String = "fixture.pmtiles",
    minZoom: Int = 8,
    maxZoom: Int = 15,
    north: Double = 56.4,
    east: Double = 42.7,
    south: Double = 56.1,
    west: Double = 42.2,
): File {
    val directory = Files.createTempDirectory("map-package-").toFile()
    val file = File(directory, fileName)
    val bytes = ByteArray(127)
    "PMTiles".encodeToByteArray().copyInto(bytes, 0)
    bytes[7] = 3
    bytes[100] = minZoom.toByte()
    bytes[101] = maxZoom.toByte()
    putE7(bytes, 102, west)
    putE7(bytes, 106, south)
    putE7(bytes, 110, east)
    putE7(bytes, 114, north)
    file.writeBytes(bytes)
    file.deleteOnExit()
    directory.deleteOnExit()
    return file
}

internal fun testManifestText(
    file: File,
    coverage: MapCoverageFragment = testRectangle(56.4, 42.7, 56.1, 42.2),
    pmtilesFileName: String = file.name,
    packageId: String = "fixture-v1",
): String {
    val bounds = coverage.bounds
    return """
        {
          "schemaVersion": 1,
          "packageId": "$packageId",
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
          "pmtilesFile": "$pmtilesFileName",
          "pmtilesByteLength": ${file.length()},
          "pmtilesSha256": "${MapPackageValidator.sha256(file)}"
        }
    """.trimIndent()
}

internal fun testManifest(
    file: File,
    coverage: MapCoverageFragment = testRectangle(56.4, 42.7, 56.1, 42.2),
    pmtilesFileName: String = file.name,
): MapPackageManifest = MapPackageManifestParser.parse(
    testManifestText(file = file, coverage = coverage, pmtilesFileName = pmtilesFileName),
)

private fun putE7(target: ByteArray, start: Int, value: Double) {
    val number = (value * 10_000_000).toInt()
    target[start] = number.toByte()
    target[start + 1] = (number ushr 8).toByte()
    target[start + 2] = (number ushr 16).toByte()
    target[start + 3] = (number ushr 24).toByte()
}
