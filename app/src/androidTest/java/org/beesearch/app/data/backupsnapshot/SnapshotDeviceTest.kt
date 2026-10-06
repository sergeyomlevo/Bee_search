package org.beesearch.app.data.backupsnapshot

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backup.PortableSettingsStore
import org.beesearch.app.data.backuprepository.AndroidRepositoryRoots
import org.beesearch.app.data.backuprepository.BoundRepository
import org.beesearch.app.data.backuprepository.DataStoreRepositoryBindingStore
import org.beesearch.app.data.backuprepository.RepositoryBinding
import org.beesearch.app.data.backuprepository.RepositoryConnection
import org.beesearch.app.data.backuprepository.RepositoryResult
import org.beesearch.app.data.backuprepository.valueOrThrow
import org.beesearch.app.data.backupsnapshot.RepositorySnapshotService
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in only. Uses a fresh synthetic repository and an in-memory graph; never captures DEV data. */
@RunWith(AndroidJUnit4::class)
class SnapshotDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val runId get() = UUID.fromString(InstrumentationRegistry.getArguments().getString("snapshotSmokeRunId")).toString()
    private val relative get() = "Download/BeeSearch/_poc/ProductionSlice1/$runId/Backup"
    private val root get() = File(Environment.getExternalStorageDirectory(), relative)
    private val tree get() = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:$relative")
    private fun locator(name: String) = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:$relative/$name").toString()
    private val store by lazy {
        DataStoreRepositoryBindingStore(PreferenceDataStoreFactory.create(produceFile = {
            File(context.filesDir, "install-state/snapshot-binding-smoke-$runId.preferences_pb")
        }), "Dev")
    }
    private val roots by lazy { AndroidRepositoryRoots(context, "Dev", Mutex()) }

    @Before fun safety() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("snapshotSmokeRunId") != null)
        assertEquals("org.beesearch.app.dev", context.packageName)
        assertEquals("SM-S938B", Build.MODEL); assertEquals(36, Build.VERSION.SDK_INT)
    }

    @Test fun prepare() {
        assertTrue(!root.exists())
        assertTrue(root.mkdirs())
        android.util.Log.i("SnapshotDevice", "PREPARED id=$runId")
    }

    @Test fun createSyntheticMetadataSnapshot(): Unit = runBlocking {
        val bound = bindFresh()
        val database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).allowMainThreadQueries().build()
        try {
            val territory = TerritoryEntity(UUID.randomUUID(), "SNAP-$runId", "Synthetic", "Region", "District", Instant.EPOCH, Instant.EPOCH)
            val observer = ObserverEntity(UUID.randomUUID(), "OBS-$runId", "Synthetic", "Observer", null, null, Instant.EPOCH, Instant.EPOCH)
            database.backupDao().insertTerritories(listOf(territory)); database.backupDao().insertObservers(listOf(observer))
            val settings = FakeSettings(PortableSettingsSnapshot(territory.id, observer.id, emptyMap()))
            val service = RepositorySnapshotService(bound, SnapshotCapture(database, settings), File(context.cacheDir, "snapshot-device-$runId"))
            val result = service.create().valueOrThrow()
            assertEquals(17, result.snapshot.metrics.recordCounts.size)
            File(context.filesDir, "snapshot-smoke-$runId.txt").writeText("${result.path}\n${result.snapshot.identity.snapshotId}\n${result.snapshot.wholeSha256}")
            android.util.Log.i("SnapshotDevice", "CREATED id=$runId path=${result.path} snapshot=${result.snapshot.identity.snapshotId} entries=${result.snapshot.metrics.recordCounts.size} zip=${result.snapshot.metrics.zipBytes}")
        } finally { database.close() }
    }

    @Test fun restartDiscoveryKeepsSyntheticSnapshot(): Unit = runBlocking {
        val binding = store.read() ?: error("createSyntheticMetadataSnapshot must run first")
        val bound = BoundRepository(store, roots)
        assertEquals(RepositoryConnection.Bound(binding), bound.probe())
        val result = bound.discoverSnapshots(File(context.cacheDir, "snapshot-device-$runId")).valueOrThrow()
        val expected = File(context.filesDir, "snapshot-smoke-$runId.txt").readLines()
        assertEquals(expected[1], result.latest!!.identity.snapshotId.toString())
        assertEquals(expected[2], result.latest!!.wholeSha256)
        android.util.Log.i("SnapshotDevice", "RESTART_DISCOVERY id=$runId valid=${result.candidates.count { it.snapshot != null }}")
    }

    @Test fun restartAndRejectsCorruptSyntheticCopy(): Unit = runBlocking {
        val binding = store.read() ?: error("createSyntheticMetadataSnapshot must run first")
        val bound = BoundRepository(store, roots)
        assertEquals(RepositoryConnection.Bound(binding), bound.probe())
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:$relative/Snapshots")
        val fakeId = UUID.randomUUID().toString()
        val corruptName = "snapshot-$fakeId-${"0".repeat(64)}.zip"
        val uri = DocumentsContract.createDocument(context.contentResolver, parent, "application/zip", corruptName)
            ?: error("SAF refused corrupt synthetic copy")
        val expected = File(context.filesDir, "snapshot-smoke-$runId.txt").readLines()
        val source = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:$relative/${expected[0]}")
        val bytes = context.contentResolver.openInputStream(source)!!.use { it.readBytes() }
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
        val result = bound.discoverSnapshots(File(context.cacheDir, "snapshot-device-$runId")).valueOrThrow()
        assertTrue(result.candidates.any { it.path.endsWith(corruptName) && it.snapshot == null })
        assertTrue(result.candidates.any { it.snapshot != null })
        assertEquals(expected[2], result.latest!!.wholeSha256)
        android.util.Log.i("SnapshotDevice", "RESTART_CORRUPT_REJECTED id=$runId corrupt=$fakeId valid=${result.candidates.count { it.snapshot != null }}")
    }

    private suspend fun bindFresh(): BoundRepository {
        val bound = BoundRepository(store, roots)
        if (bound.probe() is RepositoryConnection.Unbound) bound.initializeNew(
            DocumentsContract.buildDocumentUriUsingTree(tree, "primary:$relative").toString()).valueOrThrow()
        return bound
    }

    private class FakeSettings(private var value: PortableSettingsSnapshot) : PortableSettingsStore {
        override suspend fun snapshot() = value
        override suspend fun replace(snapshot: PortableSettingsSnapshot) { value = snapshot }
    }
}
