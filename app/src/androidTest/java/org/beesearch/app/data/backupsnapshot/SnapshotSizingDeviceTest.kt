package org.beesearch.app.data.backupsnapshot

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.backup.DataStorePortableSettingsStore
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.settings.settingsDataStore
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit read-only research capture; only aggregate numbers leave private scratch. */
class SnapshotSizingDeviceTest {
    @Test fun measure(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("snapshotSizing") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("org.beesearch.app.dev", context.packageName)
        assertEquals("SM-S938B", Build.MODEL)
        val database = BeeSearchDatabase.create(context)
        val scratch = Files.createTempDirectory(context.cacheDir.toPath(), "snapshot-sizing-").toFile()
        try {
            val entries = SnapshotCapture(database, DataStorePortableSettingsStore(context.settingsDataStore)).capture()
            val result = SnapshotArchive().build(File(scratch, "measurement.zip"),
                SnapshotIdentity(UUID.randomUUID(), UUID.randomUUID(), "Dev", System.currentTimeMillis()), entries)
            val m = result.metrics
            m.entryBytes.forEach { (path, bytes) ->
                val count = m.recordCounts[path] ?: 0
                Log.i("SnapshotSizing", "entry=$path count=$count bytes=$bytes avg=${if (count == 0L) 0.0 else bytes.toDouble()/count} max=${m.maxRecordBytesByEntry[path] ?: 0}")
            }
            Log.i("SnapshotSizing", "TOTAL zip=${m.zipBytes} uncompressed=${m.totalBytes} largestRecord=${m.maxRecordBytes}")
        } finally { scratch.deleteRecursively(); database.close() }
    }
}
