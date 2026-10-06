package org.beesearch.app.data.backuprepository

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupLocationTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun eachVariantOwnsOneFixedBackupFolder() {
        assertEquals("BeeSearch/Stable/Backup", BackupLocation("Stable").relativePath)
        assertEquals("BeeSearch/Beta/Backup", BackupLocation("Beta").relativePath)
        assertEquals("BeeSearch/Dev/Backup", BackupLocation("Dev").relativePath)

        assertEquals("primary:Download/BeeSearch/Stable/Backup", BackupLocation("Stable").documentId)
        assertEquals("primary:Download/BeeSearch/Beta/Backup", BackupLocation("Beta").documentId)
        assertEquals("primary:Download/BeeSearch/Dev/Backup", BackupLocation("Dev").documentId)
    }

    @Test
    fun publicRootAndBootstrapDeriveFromTheSameLocation() {
        val downloads = File("storage/emulated/0/Download")
        val location = BackupLocation("Dev")
        assertEquals(File(downloads, "BeeSearch/Dev/Backup"), location.rootDirectory(downloads))

        val bootstrap = BackupDirectoryBootstrap(downloads, "Dev")
        assertEquals(location.relativePath, bootstrap.location.relativePath)
        assertEquals(location.documentId, bootstrap.location.documentId)
        assertEquals(location.rootDirectory(downloads), bootstrap.root)
    }

    @Test
    fun unexpectedVariantIsRejected() {
        for (variant in listOf("dev", "Release", "", "Dev ", "Dev/Backup")) {
            try {
                BackupLocation(variant)
                fail("variant '$variant' must not be accepted")
            } catch (_: IllegalArgumentException) {
                // The fixed location exists only for the three real build variants.
            }
        }
    }

    @Test
    fun onlyTheExactFixedTreeIsAccepted() {
        val dev = BackupLocation("Dev")
        assertTrue(dev.accepts("primary:Download/BeeSearch/Dev/Backup"))

        val neighbours = listOf(
            "primary:Download",
            "primary:Download/",
            "primary:Download/BeeSearch",
            "primary:Download/BeeSearch/Dev",
            "primary:Download/BeeSearch/Dev/Exchange",
            "primary:Download/BeeSearch/Dev/Exchange/Data",
            "primary:Download/BeeSearch/Dev/Backup/Media",
            "primary:Download/BeeSearch/Dev/Backup/Snapshots",
            "primary:Download/BeeSearch/Dev/Backup2",
            "primary:Download/BeeSearch/Dev/backup",
            "primary:Download/BeeSearch/Beta/Backup",
            "primary:Download/BeeSearch/Stable/Backup",
            "primary:Other/BeeSearch/Dev/Backup",
            "primary:Download/BeeSearch/Dev/Backup ",
            "",
        )
        neighbours.forEach { documentId ->
            assertFalse("'$documentId' is not the fixed Backup folder", dev.accepts(documentId))
        }
    }
}
