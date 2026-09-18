package org.beesearch.app.data.exchange

import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exchange tree as it really exists on the device: created by this build's variant, writable
 * without any broad storage permission, and stable across repeated runs.
 */
class BeeSearchExchangeStorageDeviceTest {
    private val storage = beeSearchExchangeStorage()

    @Test
    fun exchangeTreeUsesTheRunningVariantOnTheRealDevice() = runBlocking {
        val state = storage.ensure()
        assertTrue("ensure failed: $state", state is ExchangeStorageState.Ready)

        assertTrue(storage.exchangeDirectory.isDirectory)
        ExchangeFolder.entries.forEach { folder ->
            assertTrue(folder.directoryName, storage.directoryOf(folder).directory.isDirectory)
        }

        assertEquals("Download/BeeSearch/${storage.variantName}/Exchange", storage.userVisiblePath())
        assertTrue(
            storage.exchangeDirectory.absolutePath.endsWith("/Download/BeeSearch/${storage.variantName}/Exchange"),
        )
    }

    @Test
    fun everyExchangeFolderIsWritableWithoutBroadStoragePermission() = runBlocking {
        storage.ensure()

        ExchangeFolder.entries.forEach { folder ->
            val directory = storage.directoryOf(folder).directory
            val probe = File(directory, "bee-search-write-probe.tmp")
            try {
                probe.writeText("probe")
                assertTrue("${folder.directoryName} must be writable", probe.isFile)
                assertEquals("probe", probe.readText())
            } finally {
                probe.delete()
            }
            assertFalse("probe must be cleaned up", probe.exists())
        }
    }

    @Test
    fun ensureIsIdempotentAndKeepsUserFiles() = runBlocking {
        storage.ensure()
        val data = storage.directoryOf(ExchangeFolder.DATA).directory
        val userFile = File(data, "bee-search-device-test-keep.zip")
        userFile.writeText("user data")
        try {
            val second = storage.ensure()
            assertEquals(ExchangeStorageState.Ready(emptyList()), second)
            assertTrue(userFile.isFile)
            assertEquals("user data", userFile.readText())
        } finally {
            userFile.delete()
        }
    }

    @Test
    fun variantExchangeRootsAreDistinctDirectories() {
        val roots = listOf("Stable", "Beta", "Dev").map { variant ->
            BeeSearchExchangeStorage(storage.exchangeDirectory.parentFile!!.parentFile!!.parentFile!!, variant)
                .exchangeDirectory.absolutePath
        }

        assertEquals(roots.size, roots.toSet().size)
        assertTrue(roots.all { it.contains("/Download/BeeSearch/") })
        assertTrue(roots.none { it.contains("//") })
    }

    @Test
    fun pickerContractsTargetThisVariantsOfflineMapsAndDataFolders() {
        val offlineMaps = storage.initialDocumentUri(ExchangeFolder.OFFLINE_MAPS)
        val data = storage.initialDocumentUri(ExchangeFolder.DATA)

        assertEquals("content", offlineMaps.scheme)
        assertEquals(
            "primary:Download/BeeSearch/${storage.variantName}/Exchange/OfflineMaps",
            DocumentsContract.getDocumentId(offlineMaps),
        )
        assertEquals(
            "primary:Download/BeeSearch/${storage.variantName}/Exchange/Data",
            DocumentsContract.getDocumentId(data),
        )
        assertNotEquals(offlineMaps, data)
    }
}
