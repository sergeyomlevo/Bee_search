package org.beesearch.app.data.exchange

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Automatic lookup of the offline map of the current Ареал.
 *
 * The file name decides only whether a package belongs to this Ареал and which version it claims.
 * A package counts as complete only when the manifest and the map it names are both present, because
 * the pairing comes from the manifest itself - the existing D065 contract - and not from a second
 * naming rule invented for this search.
 */
class AreaMapPackageDiscoveryTest {
    private val stem = "Лух--7e82a310"
    private val otherStem = "Другой--12345678"

    private fun mapName(version: Int) = "$stem--map-v$version.pmtiles"
    private fun otherMapName(version: Int) = "$otherStem--map-v$version.pmtiles"

    private fun manifestText(pmtilesFileName: String, packageId: String = "package-$pmtilesFileName"): String = """
        {
          "schemaVersion": 1,
          "packageId": "$packageId",
          "territoryCompatibility": { "policy": "coverage_fragments_only" },
          "datasetVersion": "dataset-v1",
          "profileId": "bee-search-field",
          "profileVersion": "v1",
          "styleVersion": "vector-pmtiles-v1",
          "coverageFragments": [{ "west": 38.0, "south": 56.0, "east": 39.0, "north": 57.0 }],
          "minZoom": 8,
          "maxZoom": 15,
          "pmtilesFile": "$pmtilesFileName",
          "pmtilesByteLength": 127,
          "pmtilesSha256": "${"a".repeat(64)}"
        }
    """.trimIndent()

    private fun discover(
        vararg entries: Pair<String, String?>,
    ): AreaMapDiscoveryResult {
        val manifests = entries.mapNotNull { (name, text) -> text?.let { name to it } }.toMap()
        return discoverAreaMapPackages(
            expectedAreaStem = stem,
            fileNames = entries.map { it.first },
            readManifestText = { name -> manifests[name] },
        )
    }

    // ---- pairing -------------------------------------------------------------------------

    @Test
    fun `one map and its manifest are a complete package`() {
        val result = discover(
            mapName(1) to null,
            "${mapName(1)}.manifest.json" to manifestText(mapName(1)),
        )

        val candidate = (result as AreaMapDiscoveryResult.One).candidate
        assertEquals(1, candidate.version)
        assertEquals(mapName(1), candidate.pmtilesFileName)
        assertEquals("${mapName(1)}.manifest.json", candidate.manifestFileName)
    }

    @Test
    fun `a map without its manifest is not a complete package`() {
        assertEquals(AreaMapDiscoveryResult.None, discover(mapName(1) to null))
    }

    @Test
    fun `a manifest without its map is not a complete package`() {
        assertEquals(
            AreaMapDiscoveryResult.None,
            discover("${mapName(1)}.manifest.json" to manifestText(mapName(1))),
        )
    }

    @Test
    fun `a manifest of another version does not complete an older map`() {
        // The v2 manifest declares the v2 map, which is not here; the v1 map has no manifest.
        assertEquals(
            AreaMapDiscoveryResult.None,
            discover(
                mapName(1) to null,
                "${mapName(2)}.manifest.json" to manifestText(mapName(2)),
            ),
        )
    }

    @Test
    fun `a manifest of another area is ignored`() {
        assertEquals(
            AreaMapDiscoveryResult.None,
            discover(
                mapName(1) to null,
                otherMapName(1) to null,
                "${otherMapName(1)}.manifest.json" to manifestText(otherMapName(1)),
            ),
        )
    }

    @Test
    fun `a manifest naming another map does not pair with this one`() {
        assertEquals(
            AreaMapDiscoveryResult.None,
            discover(
                mapName(1) to null,
                "${mapName(1)}.manifest.json" to manifestText(otherMapName(1)),
            ),
        )
    }

    @Test
    fun `two manifests claiming the same map are ambiguous and never chosen`() {
        val result = discover(
            mapName(2) to null,
            "${mapName(2)}.manifest.json" to manifestText(mapName(2), packageId = "first"),
            "copy-of-manifest.json" to manifestText(mapName(2), packageId = "second"),
        )

        assertTrue(result is AreaMapDiscoveryResult.Ambiguous)
    }

    @Test
    fun `a broken manifest does not complete a package`() {
        assertEquals(
            AreaMapDiscoveryResult.None,
            discover(mapName(1) to null, "${mapName(1)}.manifest.json" to "{ not json"),
        )
    }

    @Test
    fun `a manifest that is not a map description is ignored`() {
        assertEquals(
            AreaMapDiscoveryResult.None,
            discover(mapName(1) to null, "notes.json" to """{"hello":"world"}"""),
        )
    }

    @Test
    fun `a duplicated listing entry does not create ambiguity`() {
        val result = discover(
            mapName(1) to null,
            mapName(1) to null,
            "${mapName(1)}.manifest.json" to manifestText(mapName(1)),
        )

        assertTrue(result is AreaMapDiscoveryResult.One)
    }

    @Test
    fun `a malformed map name never becomes a candidate`() {
        val result = discover(
            "$stem--map-v0.pmtiles" to null,
            "$stem--map-v0.pmtiles.manifest.json" to manifestText("$stem--map-v0.pmtiles"),
            "$stem.pmtiles" to null,
            "$stem.pmtiles.manifest.json" to manifestText("$stem.pmtiles"),
        )

        assertEquals(AreaMapDiscoveryResult.None, result)
    }

    // ---- discovery outcomes --------------------------------------------------------------

    @Test
    fun `nothing in the folder means nothing to offer`() {
        assertEquals(AreaMapDiscoveryResult.None, discover())
    }

    @Test
    fun `exactly one complete package is offered`() {
        val result = discover(mapName(3) to null, "m.json" to manifestText(mapName(3)))

        assertEquals(3, (result as AreaMapDiscoveryResult.One).candidate.version)
    }

    @Test
    fun `unrelated files do not hide the current package`() {
        val result = discover(
            mapName(1) to null,
            "m.json" to manifestText(mapName(1)),
            otherMapName(7) to null,
            "${otherMapName(7)}.manifest.json" to manifestText(otherMapName(7)),
            "readme.txt" to null,
        )

        assertEquals(1, (result as AreaMapDiscoveryResult.One).candidate.version)
    }

    @Test
    fun `several versions are offered newest first and never activated`() {
        val result = discover(
            mapName(1) to null,
            "m1.json" to manifestText(mapName(1)),
            mapName(2) to null,
            "m2.json" to manifestText(mapName(2)),
            mapName(12) to null,
            "m12.json" to manifestText(mapName(12)),
        )

        val several = result as AreaMapDiscoveryResult.Several
        assertEquals(12, several.preferred.version)
        assertEquals(listOf(2, 1), several.alternatives.map(AreaMapCandidate::version))
    }

    @Test
    fun `the highest numeric version wins regardless of the listing order`() {
        val manifestTexts = mapOf(
            "m1.json" to manifestText(mapName(1)),
            "m2.json" to manifestText(mapName(2)),
            "m12.json" to manifestText(mapName(12)),
        )
        val listing = listOf(mapName(2), "m2.json", mapName(12), "m12.json", mapName(1), "m1.json")
        fun run(order: List<String>) = discoverAreaMapPackages(
            expectedAreaStem = stem,
            fileNames = order,
            readManifestText = { name -> manifestTexts[name] },
        )

        val forward = run(listing)
        val reversed = run(listing.reversed())

        assertEquals(12, (forward as AreaMapDiscoveryResult.Several).preferred.version)
        assertEquals(12, (reversed as AreaMapDiscoveryResult.Several).preferred.version)
        assertEquals(
            forward.alternatives.map(AreaMapCandidate::version),
            reversed.alternatives.map(AreaMapCandidate::version),
        )
    }

    @Test
    fun `ambiguity at the newest version is not hidden by an older clear package`() {
        val result = discover(
            mapName(1) to null,
            "m1.json" to manifestText(mapName(1)),
            mapName(5) to null,
            "m5.json" to manifestText(mapName(5), packageId = "one"),
            "another.json" to manifestText(mapName(5), packageId = "two"),
        )

        assertTrue(result is AreaMapDiscoveryResult.Ambiguous)
    }

    @Test
    fun `an unclear old version does not block a clear newer one`() {
        val result = discover(
            mapName(5) to null,
            "m5.json" to manifestText(mapName(5)),
            mapName(1) to null,
            "m1.json" to manifestText(mapName(1), packageId = "one"),
            "another.json" to manifestText(mapName(1), packageId = "two"),
        )

        // The newer version is unambiguous, so it is still offered; the broken old pair is not.
        assertEquals(5, (result as AreaMapDiscoveryResult.One).candidate.version)
    }

    @Test
    fun `an unreadable manifest is simply not a package`() {
        val result = discoverAreaMapPackages(
            expectedAreaStem = stem,
            fileNames = listOf(mapName(1), "${mapName(1)}.manifest.json"),
            readManifestText = { null },
        )

        assertEquals(AreaMapDiscoveryResult.None, result)
    }

    @Test
    fun `the candidate exposes a human readable name`() {
        val result = discover(mapName(4) to null, "m.json" to manifestText(mapName(4)))

        assertEquals("Лух--7e82a310--map-v4", (result as AreaMapDiscoveryResult.One).candidate.displayName)
    }
}

/**
 * Discovery over a real folder.
 *
 * These cases use a temporary directory that stands in for the exchange folder, which is exactly the
 * situation of files Bee Search itself stored. Files another application put in shared storage are a
 * different case and are observed on the device instead.
 */
class AndroidAreaMapDiscoveryTest {
    private lateinit var root: File

    private val stem = "Лух--7e82a310"

    @Before
    fun setUp() {
        root = Files.createTempDirectory("bee-search-discovery").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun discovery() = AndroidAreaMapDiscovery(
        BeeSearchExchangeStorage(publicRoot = root, variantName = "Test"),
    )

    private fun offlineMaps(): File =
        File(root, "BeeSearch/Test/Exchange/OfflineMaps").apply { mkdirs() }

    private fun manifestText(pmtilesFileName: String) = """
        {
          "schemaVersion": 1,
          "packageId": "package-1",
          "territoryCompatibility": { "policy": "coverage_fragments_only" },
          "datasetVersion": "dataset-v1",
          "profileId": "bee-search-field",
          "profileVersion": "v1",
          "styleVersion": "vector-pmtiles-v1",
          "coverageFragments": [{ "west": 38.0, "south": 56.0, "east": 39.0, "north": 57.0 }],
          "minZoom": 8,
          "maxZoom": 15,
          "pmtilesFile": "$pmtilesFileName",
          "pmtilesByteLength": 127,
          "pmtilesSha256": "${"a".repeat(64)}"
        }
    """.trimIndent()

    @Test
    fun `a package the app stored itself is discovered`() = runBlocking {
        val folder = offlineMaps()
        File(folder, "$stem--map-v2.pmtiles").writeBytes(ByteArray(127))
        File(folder, "$stem--map-v2.pmtiles.manifest.json").writeText(manifestText("$stem--map-v2.pmtiles"))

        val result = discovery().discover(stem)

        assertEquals(2, (result as AreaMapDiscoveryResult.One).candidate.version)
    }

    @Test
    fun `a missing exchange folder is nothing found rather than a failure`() = runBlocking {
        assertEquals(AreaMapDiscoveryResult.None, discovery().discover(stem))
    }

    @Test
    fun `an empty exchange folder is nothing found`() = runBlocking {
        offlineMaps()

        assertEquals(AreaMapDiscoveryResult.None, discovery().discover(stem))
    }
}
