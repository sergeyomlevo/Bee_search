package org.beesearch.app.data.exchange

import java.io.File
import kotlinx.coroutines.runBlocking
import org.beesearch.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Contract of the single user-facing exchange directory.
 *
 * These are the properties that keep Stable, Beta and Dev from mixing files, and that keep the
 * exchange area from becoming a second copy of app storage.
 */
class BeeSearchExchangeStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /** Stands in for the public `Download` collection, so displayed paths match the device. */
    private val downloadsRoot: File by lazy { temporaryFolder.newFolder("Download") }

    private fun storageFor(variant: String): BeeSearchExchangeStorage =
        BeeSearchExchangeStorage(publicRoot = downloadsRoot, variantName = variant)

    @Test
    fun `each variant resolves to its own exchange branch`() {
        val stable = storageFor("Stable")
        val beta = storageFor("Beta")
        val dev = storageFor("Dev")

        assertEquals("BeeSearch/Stable/Exchange", stable.exchangeRelativePath)
        assertEquals("BeeSearch/Beta/Exchange", beta.exchangeRelativePath)
        assertEquals("BeeSearch/Dev/Exchange", dev.exchangeRelativePath)

        assertEquals("Download/BeeSearch/Stable/Exchange", stable.userVisiblePath())
        assertEquals("Download/BeeSearch/Beta/Exchange", beta.userVisiblePath())
        assertEquals("Download/BeeSearch/Dev/Exchange", dev.userVisiblePath())
    }

    @Test
    fun `variant exchange paths never intersect`() {
        val byVariant = listOf("Stable", "Beta", "Dev").associateWith { variant ->
            val storage = storageFor(variant)
            listOf(storage.exchangeRelativePath) +
                ExchangeFolder.entries.map { storage.directoryOf(it).relativePath }
        }

        val allPaths = byVariant.values.flatten()
        assertEquals(allPaths.size, allPaths.toSet().size)

        // No variant's path may be nested inside another variant's path.
        byVariant.forEach { (variant, paths) ->
            byVariant.filterKeys { it != variant }.forEach { (other, otherPaths) ->
                paths.forEach { path ->
                    otherPaths.forEach { otherPath ->
                        assertFalse(
                            "$other path $otherPath overlaps $variant path $path",
                            otherPath == path ||
                                otherPath.startsWith("$path/") ||
                                path.startsWith("$otherPath/"),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `one component provides every exchange folder`() {
        val storage = storageFor("Beta")
        val expected = mapOf(
            ExchangeFolder.AREAS to "BeeSearch/Beta/Exchange/Areas",
            ExchangeFolder.OFFLINE_MAPS to "BeeSearch/Beta/Exchange/OfflineMaps",
            ExchangeFolder.DATA to "BeeSearch/Beta/Exchange/Data",
        )

        assertEquals(expected.keys, ExchangeFolder.entries.toSet())
        expected.forEach { (folder, relativePath) ->
            val directory = storage.directoryOf(folder)
            assertEquals(folder, directory.folder)
            assertEquals(relativePath, directory.relativePath)
            assertEquals(File(downloadsRoot, relativePath), directory.directory)
            assertTrue(directory.directory.absolutePath.startsWith(storage.exchangeDirectory.absolutePath))
        }
    }

    @Test
    fun `document ids point into the exchange folders for picker steering`() {
        val storage = storageFor("Beta")

        assertEquals("primary:Download/BeeSearch/Beta/Exchange", storage.documentId())
        assertEquals(
            "primary:Download/BeeSearch/Beta/Exchange/OfflineMaps",
            storage.documentId(ExchangeFolder.OFFLINE_MAPS),
        )
        assertEquals("primary:Download/BeeSearch/Beta/Exchange/Data", storage.documentId(ExchangeFolder.DATA))
    }

    @Test
    fun `ensure creates the whole tree once and is idempotent afterwards`() = runBlocking {
        val storage = storageFor("Beta")

        val first = storage.ensure()
        assertTrue(first is ExchangeStorageState.Ready)
        assertTrue(storage.exchangeDirectory.isDirectory)
        ExchangeFolder.entries.forEach { folder ->
            assertTrue(folder.directoryName, storage.directoryOf(folder).directory.isDirectory)
        }

        // A second pass reuses the existing directories and creates nothing.
        val second = storage.ensure()
        assertEquals(ExchangeStorageState.Ready(emptyList()), second)
    }

    @Test
    fun `ensure never deletes or renames existing user files`() = runBlocking {
        val storage = storageFor("Beta")
        val offlineMaps = storage.directoryOf(ExchangeFolder.OFFLINE_MAPS).directory
        val data = storage.directoryOf(ExchangeFolder.DATA).directory
        storage.ensure()

        val mapPair = File(offlineMaps, "tester-field-coverage.pmtiles")
        val manifest = File(offlineMaps, "tester-field-coverage.pmtiles.manifest.json")
        val exported = File(data, "bee-search-backup.zip")
        listOf(mapPair, manifest, exported).forEach { it.writeText("user data") }

        storage.ensure()
        storage.ensure()

        listOf(mapPair, manifest, exported).forEach { file ->
            assertTrue(file.name, file.isFile)
            assertEquals("user data", file.readText())
        }
        assertEquals(listOf(mapPair.name, manifest.name).sorted(), offlineMaps.list()!!.sorted())
    }

    @Test
    fun `pre-existing directories are reused rather than replaced`() = runBlocking {
        val storage = storageFor("Dev")
        val areas = storage.directoryOf(ExchangeFolder.AREAS).directory
        assertTrue(areas.mkdirs())
        val existing = File(areas, "existing-area.json").apply { writeText("{}") }

        val state = storage.ensure()

        // `mkdirs()` already created the parents, so only the two missing siblings are new.
        assertEquals(ExchangeStorageState.Ready(listOf("OfflineMaps", "Data")), state)
        assertTrue(existing.isFile)
        assertEquals(areas, existing.parentFile)
    }

    @Test
    fun `exchange addresses only its three folders and never app storage`() {
        val storage = storageFor("Stable")
        val exchangeRoot = storage.exchangeDirectory

        // The tree is closed: nothing outside Exchange/{Areas,OfflineMaps,Data} is addressable, so
        // the Room database, DataStore, cache or the installed PMTiles copy cannot land here.
        val addressed = ExchangeFolder.entries.map { storage.directoryOf(it).directory }
        assertEquals(3, addressed.size)
        addressed.forEach { directory ->
            assertEquals(exchangeRoot, directory.parentFile)
        }
        assertEquals(
            setOf("Areas", "OfflineMaps", "Data"),
            addressed.map { it.name }.toSet(),
        )
        assertFalse(addressed.any { it.name in setOf("databases", "datastore", "cache", "files") })
    }

    @Test
    fun `running build maps its application variant to the exchange variant`() {
        // The variant is a generated build-configuration value, not a package-name heuristic.
        val variant = BuildConfig.EXCHANGE_VARIANT

        assertTrue("unexpected exchange variant '$variant'", variant in setOf("Stable", "Beta", "Dev"))
        val expected = when {
            BuildConfig.APPLICATION_ID.endsWith(".dev") -> "Dev"
            BuildConfig.APPLICATION_ID.endsWith(".beta") -> "Beta"
            else -> "Stable"
        }
        assertEquals(expected, variant)
        // The variant reaches the storage unchanged, so each build gets its own branch.
        assertEquals(variant, storageFor(variant).variantName)
    }
}
