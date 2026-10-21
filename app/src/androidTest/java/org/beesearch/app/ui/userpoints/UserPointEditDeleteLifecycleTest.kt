package org.beesearch.app.ui.userpoints

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.beesearch.app.data.local.room.*
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.data.repository.RoomUserPointRepository
import org.beesearch.app.domain.model.*
import org.beesearch.app.domain.repository.UserPointRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserPointEditDeleteLifecycleTest {
    private lateinit var db: BeeSearchDatabase
    private lateinit var repo: RoomUserPointRepository
    private lateinit var media: UserPointMediaStore
    private lateinit var root: File
    private val territory = UUID.randomUUID()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java)
            .allowMainThreadQueries().build()
        db.backupDao().insertTerritories(listOf(TerritoryEntity(
            territory, "T", "T", "R", "D", Instant.EPOCH, Instant.EPOCH,
        )))
        repo = RoomUserPointRepository(db)
        root = File(context.cacheDir, "user-point-edit-delete-${UUID.randomUUID()}")
        media = UserPointMediaStore(root.resolve("files"), root.resolve("cache"))
    }

    @After fun cleanup() {
        main { stores.forEach(ViewModelStore::clear) }
        db.close()
        root.deleteRecursively()
    }

    @Test fun editingPreservesIdentityVisibilityAndDefersSavedPhotoRemoval() = runBlocking {
        val original = createPoint(visible = false, photos = listOf("old-a", "old-b"))
        val oldBytes = original.photos.associate { it.id to media.resolve(it.relativePath).readBytes().toList() }
        val vm = editor(original)
        awaitIdle(vm)
        val removed = vm.state.value.existingPhotos.first()
        val addedBytes = "new-photo".encodeToByteArray()
        main {
            vm.setTitle("Updated")
            vm.setNote("Note")
            vm.setCoordinates(57.1, 61.2)
            vm.removePhoto(removed.id)
            vm.addPhotos(listOf(
                PendingUserPointPhoto("new-a.jpg", "image/jpeg", source = { addedBytes.inputStream() }),
                PendingUserPointPhoto("new-b.jpg", "image/jpeg", source = { "new-photo-2".encodeToByteArray().inputStream() }),
            ))
        }
        assertEquals(original, repo.getPoint(territory, original.id))
        assertTrue(media.resolve(removed.relativePath).isFile)
        awaitIdle(vm)
        main { vm.save {} }
        withTimeout(10_000) { vm.state.first { it.isSaved } }
        val persisted = requireNotNull(repo.getPoint(territory, original.id))
        assertEquals(original.id, persisted.id)
        assertFalse(persisted.isVisible)
        assertEquals("Updated", persisted.title)
        assertEquals("Note", persisted.note)
        assertEquals(57.1, persisted.latitude, 0.0)
        assertEquals(61.2, persisted.longitude, 0.0)
        assertEquals(3, persisted.photos.size)
        assertEquals(oldBytes[original.photos.last().id], media.resolve(original.photos.last().relativePath).readBytes().toList())
        assertFalse(media.resolve(removed.relativePath).exists())
    }

    @Test fun cancelLeavesOriginalHashesAndDiscardsOnlyDraftBytes() = runBlocking {
        val original = createPoint(photos = listOf("original"))
        val hashBefore = media.resolve(original.photos.single().relativePath).readBytes().toList()
        val vm = editor(original)
        awaitIdle(vm)
        main { vm.removePhoto(original.photos.single().id) }
        assertEquals(original, repo.getPoint(territory, original.id))
        assertTrue(media.resolve(original.photos.single().relativePath).isFile)
        main { vm.addPhotos(listOf(PendingUserPointPhoto("draft.jpg", "image/jpeg", source = { "draft".encodeToByteArray().inputStream() }))) }
        awaitIdle(vm)
        val draft = vm.state.value.photos.single()
        val cancelled = CompletableDeferred<Unit>()
        main { vm.cancel { cancelled.complete(Unit) } }
        withTimeout(10_000) { cancelled.await() }
        assertEquals(hashBefore, media.resolve(original.photos.single().relativePath).readBytes().toList())
        assertFalse(media.resolveDraft(draft.relativePath).exists())
        assertEquals(original, repo.getPoint(territory, original.id))
    }

    @Test fun failedUpdateRestoresDraftAndRetryCommitsWithoutChangingOldRows() = runBlocking {
        val original = createPoint(photos = listOf("old"))
        val oldPhoto = original.photos.single()
        var fail = true
        val failing = object : UserPointRepository by repo {
            override suspend fun update(point: UserPoint): UserPoint {
                if (fail) error("write failure")
                return repo.update(point)
            }
        }
        val vm = editor(original, failing)
        awaitIdle(vm)
        main {
            vm.setTitle("Retry")
            vm.addPhotos(listOf(PendingUserPointPhoto("new.jpg", "image/jpeg", source = { "retry".encodeToByteArray().inputStream() })))
        }
        awaitIdle(vm)
        val draft = vm.state.value.photos.single()
        main { vm.save {} }
        withTimeout(10_000) { vm.state.first { !it.isWorking } }
        assertNotNull(vm.state.value.error)
        assertEquals(original, repo.getPoint(territory, original.id))
        assertTrue(media.resolveDraft(draft.relativePath).isFile)
        fail = false
        main { vm.save {} }
        withTimeout(10_000) { vm.state.first { it.isSaved } }
        val retried = requireNotNull(repo.getPoint(territory, original.id))
        assertEquals("Retry", retried.title)
        assertEquals(2, retried.photos.size)
        assertTrue(media.resolve(oldPhoto.relativePath).isFile)
    }

    @Test fun failedCleanupDoesNotResurrectPointAndRetryDeletesOnlyCleanupReceipt() = runBlocking {
        val deleted = createPoint(photos = listOf("delete-me"))
        val other = createPoint(photos = listOf("keep-me"))
        val source = root.resolve("external-source.jpg").apply { writeBytes("source".encodeToByteArray()) }
        val research = root.resolve("files/physical-object-media/research.bin").apply {
            parentFile!!.mkdirs()
            writeBytes("research".encodeToByteArray())
        }
        val deletedFile = media.resolve(deleted.photos.single().relativePath)
        val preservedOther = media.resolve(other.photos.single().relativePath).readBytes().toList()
        deletedFile.delete()
        assertTrue(deletedFile.mkdirs())
        deletedFile.resolve("blocking-child").writeText("keep cleanup from deleting this directory")
        var deleteCalls = 0
        val counting = object : UserPointRepository by repo {
            override suspend fun delete(territoryId: UUID, id: UUID): UserPoint {
                deleteCalls++
                return repo.delete(territoryId, id)
            }
        }
        val vm = deletion(counting)
        main { vm.delete(territory, deleted.id) {} }
        withTimeout(10_000) { vm.state.first { !it.isWorking && it.pending.isNotEmpty() } }
        assertEquals(1, deleteCalls)
        assertNull(repo.getPoint(territory, deleted.id))
        assertNotNull(repo.getPoint(territory, other.id))
        assertTrue(vm.state.value.showCleanupNotice)
        deletedFile.resolve("blocking-child").delete()
        assertTrue(deletedFile.delete())
        main { vm.retryCleanup() }
        withTimeout(10_000) { vm.state.first { !it.isWorking && it.pending.isEmpty() } }
        assertEquals(1, deleteCalls)
        assertEquals(preservedOther, media.resolve(other.photos.single().relativePath).readBytes().toList())
        assertEquals("source", source.readText())
        assertEquals("research", research.readText())
    }

    @Test fun sameLengthCorruptRetainedPhotoFailsSaveAndPreservesDraftAndRow() = runBlocking {
        val original = createPoint(photos = listOf("old"))
        val oldFile = media.resolve(original.photos.single().relativePath)
        oldFile.writeBytes("bad".encodeToByteArray())
        val vm = editor(original)
        awaitIdle(vm)
        main { vm.addPhotos(listOf(PendingUserPointPhoto("new.jpg", "image/jpeg", source = { "new".encodeToByteArray().inputStream() }))) }
        awaitIdle(vm)
        val draft = vm.state.value.photos.single()
        main { vm.save {} }
        withTimeout(10_000) { vm.state.first { !it.isWorking } }
        assertNotNull(vm.state.value.error)
        assertEquals(original, repo.getPoint(territory, original.id))
        assertTrue(media.resolveDraft(draft.relativePath).isFile)
        assertEquals("bad", oldFile.readText())
    }

    @Test fun deletingVisibleAndHiddenPointsPersistsAcrossRoomReopen() = runBlocking {
        val databaseName = "user-point-delete-${UUID.randomUUID()}.db"
        context.deleteDatabase(databaseName)
        val disk = Room.databaseBuilder(context, BeeSearchDatabase::class.java, databaseName).build()
        try {
            val diskRepo = RoomUserPointRepository(disk)
            disk.backupDao().insertTerritories(listOf(TerritoryEntity(
                territory, "T", "T", "R", "D", Instant.EPOCH, Instant.EPOCH,
            )))
            val visible = createPoint(repository = diskRepo, visible = true)
            val hidden = createPoint(repository = diskRepo, visible = false)
            val vm = deletion(diskRepo)
            val visibleDeleted = CompletableDeferred<Unit>()
            main { vm.delete(territory, visible.id) { visibleDeleted.complete(Unit) } }
            withTimeout(10_000) { visibleDeleted.await() }
            val hiddenDeleted = CompletableDeferred<Unit>()
            main { vm.delete(territory, hidden.id) { hiddenDeleted.complete(Unit) } }
            withTimeout(10_000) { hiddenDeleted.await() }
            disk.close()
            val reopened = Room.databaseBuilder(context, BeeSearchDatabase::class.java, databaseName).build()
            try {
                val reopenedRepo = RoomUserPointRepository(reopened)
                assertNull(reopenedRepo.getPoint(territory, visible.id))
                assertNull(reopenedRepo.getPoint(territory, hidden.id))
            } finally {
                reopened.close()
            }
        } finally {
            if (disk.isOpen) disk.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test fun ownerDirectorySymlinkCannotDeleteAnotherOwnerDirectoryOrPhoto() = runBlocking {
        val owner = UUID.randomUUID()
        val other = UUID.randomUUID()
        val base = root.resolve("files/user-point-media")
        val otherDirectory = File(base, other.toString()).apply { mkdirs() }
        val ownerLink = File(base, owner.toString())
        android.system.Os.symlink(otherDirectory.absolutePath, ownerLink.absolutePath)
        try {
            assertFalse(media.cleanupOwned(owner, emptyList(), removeDirectory = true))
            assertTrue(otherDirectory.isDirectory)
            val photoId = UUID.randomUUID()
            val file = File(otherDirectory, photoId.toString()).apply { writeText("other-owner") }
            val forged = org.beesearch.app.domain.model.UserPointPhoto(
                photoId, owner, "user-point-media/$owner/$photoId", "keep.jpg", "image/jpeg",
                file.length(), "a".repeat(64), Instant.EPOCH,
            )
            assertFalse(media.cleanupOwned(owner, listOf(forged)))
            assertEquals("other-owner", file.readText())
        } finally {
            // Remove only the test-created symlink before the isolated fixture's normal teardown.
            ownerLink.delete()
        }
    }

    @Test fun userPointDeletionPreservesPopulatedResearchGpxPhysicalRowsAndUnrelatedBytes() = runBlocking {
        val observer = UUID.randomUUID()
        val researchPoint = UUID.randomUUID()
        val bee = UUID.randomUUID()
        val cycle = UUID.randomUUID()
        val hollow = UUID.randomUUID()
        val logHive = UUID.randomUUID()
        val gpxImport = UUID.randomUUID()
        val gpxRequest = UUID.randomUUID()
        val track = UUID.randomUUID()
        val segment = UUID.randomUUID()
        val now = Instant.parse("2026-10-21T08:00:00Z")
        db.backupDao().insertObservers(listOf(ObserverEntity(
            observer, "R-OBS", "Research", "Observer", null, null, now, now,
        )))
        db.backupDao().insertObservationPoints(listOf(ObservationPointEntity(
            LocalDate.of(2026, 10, 21), researchPoint, territory, observer, 2026, 1,
            BeePresenceResult.BEES_FOUND, "R1", 56.1, 42.7, 56.1, 42.7, 4.0,
            now, now, null, "protected research",
        )))
        db.backupDao().insertBees(listOf(BeeEntity(bee, researchPoint, "WHITE", MarkPosition.THORAX, now)))
        db.backupDao().insertFlightCycles(listOf(FlightCycleEntity(
            cycle, bee, 1, now, now.plusSeconds(60), 90.0, false, true, false, now, now.plusSeconds(60),
        )))
        db.backupDao().insertPhysicalObjects(listOf(
            PhysicalObjectEntity(hollow, territory, PhysicalObjectType.HOLLOW, 1, 56.2, 42.8, now, observer),
            PhysicalObjectEntity(logHive, territory, PhysicalObjectType.LOG_HIVE, 1, 56.3, 42.9, now, observer),
        ))
        db.backupDao().insertHollows(listOf(HollowEntity(hollow, "oak", 120.0, 180, 40.0, 25.0, "keep")))
        db.backupDao().insertLogHives(listOf(LogHiveEntity(logHive, "pine", 100.0, 90, 45.0, "wood", 30.0, 80.0, "keep")))
        db.gpxDao().insertImport(GpxImportEntity(
            gpxImport, territory, observer, UUID.randomUUID(), "a".repeat(64), 3,
            displayName = "protected.gpx", gpxVersion = "1.1", parserProfileVersion = "test",
            importedAtEpochMs = now.toEpochMilli(), creator = "fixture",
        ))
        db.gpxDao().insertTracks(listOf(GpxSourceTrackEntity(track, gpxImport, 0, "track", "keep")))
        db.gpxDao().insertSegments(listOf(GpxTrackSegmentEntity(segment, track, 0)))
        db.gpxDao().insertSamples(listOf(GpxTrackSampleEntity(segment, 0, "56.1", "42.7", "10", null, "KNOWN", 1_760_000_000, 0)))
        assertEquals(1, db.gpxDao().publishReady(gpxImport, gpxRequest))
        val researchBytes = root.resolve("files/physical-object-media/research-source.bin").apply {
            parentFile!!.mkdirs()
            writeBytes("research bytes".encodeToByteArray())
        }
        val sourceBytes = root.resolve("external-source.gpx").apply { writeBytes("source bytes".encodeToByteArray()) }
        val beforeResearchPoints = db.backupDao().observationPoints()
        val beforeBees = db.backupDao().bees()
        val beforeCycles = db.backupDao().flightCycles()
        val beforePhysical = db.backupDao().physicalObjects()
        val beforeHollows = db.backupDao().hollows()
        val beforeLogHives = db.backupDao().logHives()
        val beforeGpx = db.gpxDao().importById(gpxImport)
        val beforeTracks = db.gpxDao().tracks(gpxImport)
        val beforeSegments = db.gpxDao().segments(track)
        val beforeSamples = db.gpxDao().samples(segment)
        val userPoint = createPoint(photos = listOf("owned"))
        val deleted = CompletableDeferred<Unit>()
        val vm = deletion()
        main { vm.delete(territory, userPoint.id) { deleted.complete(Unit) } }
        withTimeout(10_000) { deleted.await() }
        assertNull(repo.getPoint(territory, userPoint.id))
        assertTrue(vm.state.value.pending.isEmpty())
        assertFalse(media.resolve(userPoint.photos.single().relativePath).exists())
        assertEquals(beforeResearchPoints, db.backupDao().observationPoints())
        assertEquals(beforeBees, db.backupDao().bees())
        assertEquals(beforeCycles, db.backupDao().flightCycles())
        assertEquals(beforePhysical, db.backupDao().physicalObjects())
        assertEquals(beforeHollows, db.backupDao().hollows())
        assertEquals(beforeLogHives, db.backupDao().logHives())
        assertEquals(beforeGpx, db.gpxDao().importById(gpxImport))
        assertEquals(beforeTracks, db.gpxDao().tracks(gpxImport))
        assertEquals(beforeSegments, db.gpxDao().segments(track))
        assertEquals(beforeSamples, db.gpxDao().samples(segment))
        assertEquals("research bytes", researchBytes.readText())
        assertEquals("source bytes", sourceBytes.readText())
    }

    private suspend fun createPoint(
        repository: UserPointRepository = repo,
        visible: Boolean = true,
        photos: List<String> = emptyList(),
    ): UserPoint {
        val id = UUID.randomUUID()
        // Build the point through the normal activation path so each managed file has a valid owner.
        val session = UUID.randomUUID()
        val owned = photos.mapIndexed { index, text ->
            media.stageDraftMedia(session, UUID.randomUUID(), "p$index.jpg", "image/jpeg", Instant.EPOCH) {
                text.encodeToByteArray().inputStream()
            }
        }
        val committed = media.prepareDraftActivation(session, id, owned)
        committed.commit()
        val point = UserPoint(id, territory, "Original", "old note", 56.8, 60.6, visible,
            Instant.EPOCH, Instant.EPOCH, committed.photos)
        repository.create(point)
        if (!visible) repository.setVisible(territory, id, false)
        return repository.getPoint(territory, id)!!
    }

    private fun editor(point: UserPoint, repository: UserPointRepository = repo): UserPointEditorViewModel =
        model(UserPointEditorTarget(
            requestId = UUID.randomUUID(), territoryId = territory, pointId = point.id,
            latitude = point.latitude, longitude = point.longitude,
        ), repository)

    private fun model(target: UserPointEditorTarget, repository: UserPointRepository): UserPointEditorViewModel {
        val store = ViewModelStore().also(stores::add)
        lateinit var vm: UserPointEditorViewModel
        main { vm = ViewModelProvider(store, UserPointEditorViewModel.factory(target, repository, media))[UserPointEditorViewModel::class.java] }
        return vm
    }

    private fun deletion(repository: UserPointRepository = repo): UserPointDeletionViewModel {
        val store = ViewModelStore().also(stores::add)
        lateinit var vm: UserPointDeletionViewModel
        main { vm = ViewModelProvider(store, UserPointDeletionViewModel.factory(repository, media))[UserPointDeletionViewModel::class.java] }
        return vm
    }

    private suspend fun awaitIdle(vm: UserPointEditorViewModel) {
        withTimeout(10_000) { vm.state.first { !it.isWorking && it.isLoaded } }
    }

    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
