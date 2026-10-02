package org.beesearch.app.data.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.repository.RoomPhysicalObjectRepository
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.NewHollow
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The file half of a Physical Object deletion.
 *
 * The database commits first, so a filesystem failure can only leave orphan bytes: it must never
 * resurrect the deleted object, remove a file of another object, or delete anything outside app
 * storage.
 */
@RunWith(AndroidJUnit4::class)
class FileAwarePhysicalObjectDeletionTest {
    private lateinit var context: Context
    private lateinit var database: BeeSearchDatabase
    private lateinit var store: PhysicalObjectMediaFileStore
    private lateinit var deletion: FileAwarePhysicalObjectDeletion
    private val filesRoot = File(System.getProperty("java.io.tmpdir"), "object-media-${UUID.randomUUID()}")
    private val cacheRoot = File(System.getProperty("java.io.tmpdir"), "object-media-cache-${UUID.randomUUID()}")
    private val territoryId = UUID.randomUUID()
    private val observerId = UUID.randomUUID()

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        filesRoot.mkdirs()
        cacheRoot.mkdirs()
        database = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.backupDao().insertTerritories(
            listOf(TerritoryEntity(territoryId, "T", "Territory", "R", "D", NOW, NOW)),
        )
        database.backupDao().insertObservers(
            listOf(ObserverEntity(observerId, "O", "Last", "First", null, null, NOW, NOW)),
        )
        store = PhysicalObjectMediaFileStore(filesRoot, cacheRoot)
        val repository = RoomPhysicalObjectRepository(
            database,
            database.physicalObjectDao(),
            database.physicalObjectSequenceDao(),
            database.territoryDao(),
            database.observerDao(),
            database.beeDao(),
            Clock.fixed(NOW, ZoneOffset.UTC),
        )
        deletion = FileAwarePhysicalObjectDeletion(repository, store)
    }

    @After
    fun tearDown() {
        database.close()
        filesRoot.deleteRecursively()
        cacheRoot.deleteRecursively()
    }

    @Test
    fun deletingAnObjectRemovesItsAppOwnedFilesAndItsDirectory() = runBlocking {
        val (hollowId, mediaId) = createHollowWithMedia()
        val managed = store.resolve(PhysicalObjectMediaFileStore.relativePath(hollowId, mediaId))
        assertTrue(managed.isFile)

        val outcome = deletion.deleteHollow(hollowId)

        assertTrue(outcome.fileCleanupComplete)
        assertFalse(managed.exists())
        assertFalse(File(filesRoot, "physical-object-media/$hollowId").exists())
        assertTrue(database.backupDao().physicalObjectMedia().isEmpty())
    }

    @Test
    fun otherObjectsAndExternalOriginalsAreUntouched() = runBlocking {
        val (keepId, keepMediaId) = createHollowWithMedia()
        val kept = store.resolve(PhysicalObjectMediaFileStore.relativePath(keepId, keepMediaId))
        val externalOriginal = File(cacheRoot, "picked-photo.jpg").apply { writeText("original") }
        val (removeId, removeMediaId) = createHollowWithMedia()
        assertTrue(store.resolve(PhysicalObjectMediaFileStore.relativePath(removeId, removeMediaId)).isFile)

        assertTrue(deletion.deleteHollow(removeId).fileCleanupComplete)

        assertTrue(kept.isFile)
        assertTrue(externalOriginal.isFile)
        assertEquals(1, database.backupDao().physicalObjectMedia().size)
    }

    /**
     * A cleanup failure is reported, not thrown: the object stays deleted, nothing else is damaged,
     * and the operation does not pretend that the bytes were removed.
     */
    @Test
    fun filesystemCleanupFailureKeepsTheObjectDeleted() = runBlocking {
        val (hollowId, mediaId) = createHollowWithMedia()
        val managed = store.resolve(PhysicalObjectMediaFileStore.relativePath(hollowId, mediaId))
        // A non-empty directory cannot be removed by File.delete(), which is exactly the failure the
        // design has to survive.
        managed.delete()
        managed.mkdirs()
        File(managed, "blocker").writeText("blocker")

        val outcome = deletion.deleteHollow(hollowId)

        assertFalse(outcome.fileCleanupComplete)
        assertNull(database.physicalObjectDao().getById(hollowId))
        assertTrue(database.backupDao().physicalObjects().isEmpty())
        assertTrue(File(managed, "blocker").isFile)
    }

    /**
     * An unsafe stored media path must not be reported as a failed deletion.
     *
     * The path safety check of the media store refuses to resolve a row that escapes the managed root;
     * that refusal is a cleanup that could not be completed, not a deletion that did not happen. The
     * database deletion stays authoritative, the escaping path is never used to touch the filesystem,
     * and a file that happens to sit outside the managed root is left alone.
     */
    @Test
    fun unsafeMediaPathIsIncompleteCleanupAndNeverAFailedDeletion() = runBlocking {
        val hollowId = UUID.randomUUID()
        val mediaId = UUID.randomUUID()
        val outside = File(filesRoot, "outside-target").apply { writeText("must survive") }
        val escaping = "../outside-target"
        val media = PhysicalObjectMedia(
            mediaId, hollowId, PhysicalObjectMediaType.IMAGE, escaping, "photo.jpg",
            "image/jpeg", 10, "a".repeat(64), NOW,
        )
        val repository = RoomPhysicalObjectRepository(
            database,
            database.physicalObjectDao(),
            database.physicalObjectSequenceDao(),
            database.territoryDao(),
            database.observerDao(),
            database.beeDao(),
            Clock.fixed(NOW, ZoneOffset.UTC),
        )
        repository.createHollow(
            NewHollow(hollowId, territoryId, observerId, 56.1, 42.7, HollowProperties("дуб", 180.0, 123, 40.0, 25.0, null), listOf(media)),
        )

        val outcome = deletion.deleteHollow(hollowId)

        assertFalse("an unavailable media path is incomplete cleanup", outcome.fileCleanupComplete)
        assertNull("the database deletion stays authoritative", database.physicalObjectDao().getById(hollowId))
        assertTrue("the escaping path must never be resolved", outside.isFile)
        assertEquals("must survive", outside.readText())
    }

    private suspend fun createHollowWithMedia(): Pair<UUID, UUID> {
        val hollowId = UUID.randomUUID()
        val mediaId = UUID.randomUUID()
        val relativePath = PhysicalObjectMediaFileStore.relativePath(hollowId, mediaId)
        val managed = store.resolve(relativePath)
        managed.parentFile?.mkdirs()
        managed.writeText("media-bytes")
        val media = PhysicalObjectMedia(
            mediaId, hollowId, PhysicalObjectMediaType.IMAGE, relativePath, "photo.jpg",
            "image/jpeg", managed.length(), "a".repeat(64), NOW,
        )
        val repository = RoomPhysicalObjectRepository(
            database,
            database.physicalObjectDao(),
            database.physicalObjectSequenceDao(),
            database.territoryDao(),
            database.observerDao(),
            database.beeDao(),
            Clock.fixed(NOW, ZoneOffset.UTC),
        )
        repository.createHollow(
            NewHollow(hollowId, territoryId, observerId, 56.1, 42.7, HollowProperties("дуб", 180.0, 123, 40.0, 25.0, null), listOf(media)),
        )
        return hollowId to mediaId
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-24T09:00:00Z")
    }
}
