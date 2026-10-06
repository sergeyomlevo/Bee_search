package org.beesearch.app.data.backupsnapshot

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backup.PortableSettingsStore
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SnapshotCaptureTransactionTest {
    private lateinit var database: BeeSearchDatabase

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun captureReadsOneRoomTransactionBeforeConcurrentWriterCommits() = runTest {
        val callbackEntered = CompletableDeferred<Unit>()
        val releaseCapture = CompletableDeferred<Unit>()
        val writerCommitted = CompletableDeferred<Unit>()
        val settings = FakeSettings(PortableSettingsSnapshot(null, null, emptyMap()))
        val territory = territory()
        val observer = observer()
        val capture = SnapshotCapture(database, settings, insideTransaction = {
            assertTrue(database.inTransaction())
            callbackEntered.complete(Unit)
            releaseCapture.await()
        })

        val result = async { capture.capture() }
        callbackEntered.await()
        val writer = async {
            database.withTransaction {
                database.backupDao().insertTerritories(listOf(territory))
                database.backupDao().insertObservers(listOf(observer))
            }
            writerCommitted.complete(Unit)
        }
        yield()
        assertTrue(!writerCommitted.isCompleted)
        releaseCapture.complete(Unit)
        val captured = result.await()
        writer.await()
        assertEquals(emptyList<String>(), captured.records.getValue("data/territories.jsonl"))
        assertEquals(emptyList<String>(), captured.records.getValue("data/observers.jsonl"))
        assertTrue(writerCommitted.isCompleted)
        assertEquals(1, database.backupDao().territories().size)
        assertEquals(1, database.backupDao().observers().size)

        val afterCommit = SnapshotCapture(database, settings).capture()
        assertEquals(1, afterCommit.records.getValue("data/territories.jsonl").size)
        assertEquals(1, afterCommit.records.getValue("data/observers.jsonl").size)
    }

    @Test fun nonNullCurrentTerritoryMustExistInCapturedGraph() = runTest {
        val settings = FakeSettings(PortableSettingsSnapshot(UUID.randomUUID(), null, emptyMap()))
        val error = runCatching { SnapshotCapture(database, settings).capture() }.exceptionOrNull()
        assertTrue(error is SnapshotException)
        assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT, (error as SnapshotException).error)
    }

    private fun territory() = TerritoryEntity(UUID.randomUUID(), "T-${UUID.randomUUID()}", "Test", "Region", "District", Instant.EPOCH, Instant.EPOCH)
    private fun observer() = ObserverEntity(UUID.randomUUID(), "O-${UUID.randomUUID()}", "Test", "Observer", null, null, Instant.EPOCH, Instant.EPOCH)

    private class FakeSettings(private var value: PortableSettingsSnapshot) : PortableSettingsStore {
        override suspend fun snapshot() = value
        override suspend fun replace(snapshot: PortableSettingsSnapshot) { value = snapshot }
    }
}
