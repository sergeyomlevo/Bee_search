package org.beesearch.app.ui.userpoints

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.media.UserPointMediaStore
import org.beesearch.app.data.repository.RoomUserPointRepository
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.repository.UserPointRepository
import org.junit.*
import org.junit.Assert.*

class UserPointSaveLifecycleTest {
    private lateinit var db: BeeSearchDatabase
    private lateinit var repo: RoomUserPointRepository
    private lateinit var media: UserPointMediaStore
    private lateinit var root: java.io.File
    private val territory = UUID.randomUUID()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, BeeSearchDatabase::class.java).build()
        db.backupDao().insertTerritories(listOf(TerritoryEntity(territory, "T", "T", "R", "D", Instant.EPOCH, Instant.EPOCH)))
        repo = RoomUserPointRepository(db)
        root = java.io.File(context.cacheDir, "user-point-lifecycle-${UUID.randomUUID()}")
        media = UserPointMediaStore(root.resolve("files"), root.resolve("cache"))
    }
    @After fun cleanup() { main { stores.forEach { it.clear() } }; db.close(); root.deleteRecursively() }
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun model(repository: UserPointRepository): Pair<UserPointEditorViewModel, ViewModelStore> {
        val store = ViewModelStore().also { stores += it }
        lateinit var vm: UserPointEditorViewModel
        main {
            vm = ViewModelProvider(store, UserPointEditorViewModel.factory(
                UserPointEditorTarget(UUID.randomUUID(), territory, latitude = 56.8, longitude = 60.6), repository, media,
            ))[UserPointEditorViewModel::class.java]
            vm.setTitle("Сохранить мои фото")
            vm.addPhotos(listOf(PendingUserPointPhoto("original.jpg", "image/jpeg", { "photo-bytes".byteInputStream() })))
        }
        runBlocking { withTimeout(10000) { vm.state.first { !it.isWorking } } }
        assertEquals(1, vm.state.value.photos.size)
        return vm to store
    }

    @Test fun failedRoomSaveRestoresDraftAndRetryKeepsManagedPhoto() = runBlocking {
        var fail = true
        val actual = repo
        val failing = object : UserPointRepository by actual {
            override suspend fun create(point: UserPoint): UserPoint {
                if (fail) error("test write failure")
                return actual.create(point)
            }
        }
        val (vm, _) = model(failing)
        val draft = vm.state.value.photos.single()
        var saved: UserPoint? = null
        main { vm.save { saved = it } }
        withTimeout(10000) { vm.state.first { !it.isWorking } }
        assertNull(saved)
        assertTrue(media.resolveDraft(draft.relativePath).isFile)
        assertEquals(0, db.userPointDao().countForTerritory(territory))
        fail = false
        main { vm.save { saved = it } }
        withTimeout(10000) { vm.state.first { it.isSaved } }
        assertNotNull(saved)
        assertTrue(media.resolve(saved!!.photos.single().relativePath).isFile)
        assertEquals(1, db.userPointDao().countForTerritory(territory))
    }

    @Test fun clearingViewModelDuringFinalizationNeverRemovesCommittedPhoto() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val persisted = CompletableDeferred<UserPoint>()
        val actual = repo
        val delayed = object : UserPointRepository by actual {
            override suspend fun create(point: UserPoint): UserPoint {
                entered.complete(Unit)
                proceed.await()
                return actual.create(point).also { persisted.complete(it) }
            }
        }
        val (vm, owner) = model(delayed)
        main { vm.save {} }
        withTimeout(10000) { entered.await() }
        main { owner.clear() }
        proceed.complete(Unit)
        val point = withTimeout(10000) { persisted.await() }
        withTimeout(10000) { vm.state.first { !it.isWorking } }
        assertNotNull(repo.getPoint(territory, point.id))
        assertTrue(media.resolve(point.photos.single().relativePath).isFile)
        assertTrue(point.isVisible)
    }
}
