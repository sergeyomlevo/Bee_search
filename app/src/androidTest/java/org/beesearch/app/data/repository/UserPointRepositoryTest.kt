package org.beesearch.app.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.model.UserPointPhoto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPointRepositoryTest {
    private lateinit var database: BeeSearchDatabase
    private lateinit var repository: RoomUserPointRepository
    private val territoryA = UUID.randomUUID()
    private val territoryB = UUID.randomUUID()
    private val now = Instant.parse("2026-10-20T08:00:00Z")
    private val later = Instant.parse("2026-10-20T09:00:00Z")

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), BeeSearchDatabase::class.java,
        ).allowMainThreadQueries().build()
        database.backupDao().insertTerritories(listOf(
            TerritoryEntity(territoryA, "A", "A", "R", "D", now, now),
            TerritoryEntity(territoryB, "B", "B", "R", "D", now, now),
        ))
        database.backupDao().insertObservers(listOf(
            ObserverEntity(UUID.randomUUID(), "O", "Last", "First", null, null, now, now),
        ))
        repository = RoomUserPointRepository(database, Clock.fixed(later, ZoneOffset.UTC))
    }

    @After fun tearDown() = database.close()

    @Test
    fun createForcesDefaultVisibleAndPersistsMultiplePhotos() = runBlocking {
        val created = repository.create(point(territoryId = territoryA, isVisible = false, photos = listOf(photo(), photo())))

        assertTrue(created.isVisible)
        assertEquals("Точка", created.title)
        assertEquals(2, repository.getPoint(territoryA, created.id)?.photos?.size)
    }

    @Test
    fun territoryScopeProtectsLookupUpdateVisibilityAndDelete() = runBlocking {
        val created = repository.create(point(territoryId = territoryA))

        assertNull(repository.getPoint(territoryB, created.id))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update(created.copy(territoryId = territoryB)) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.setVisible(territoryB, created.id, false) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.delete(territoryB, created.id) }
        }
        assertEquals(created.id, repository.getPoint(territoryA, created.id)?.id)
    }

    @Test
    fun updatePreservesCreationAndVisibilityFacts() = runBlocking {
        val created = repository.create(point(territoryId = territoryA, isVisible = false))
        repository.setVisible(territoryA, created.id, false)

        val updated = repository.update(created.copy(title = "  Новое имя  ", isVisible = true, latitude = 57.0))

        assertEquals(created.createdAt, updated.createdAt)
        assertFalse(updated.isVisible)
        assertEquals("Новое имя", updated.title)
        assertEquals(57.0, updated.latitude, 0.0)
        assertEquals(later, updated.updatedAt)
    }

    @Test
    fun foreignKeysRestrictTerritoryAndCascadePhotosWhenPointIsDeleted() = runBlocking {
        val created = repository.create(point(territoryId = territoryA, photos = listOf(photo(), photo())))
        val sqlite = database.openHelper.writableDatabase

        assertThrows(SQLiteConstraintException::class.java) {
            sqlite.execSQL("DELETE FROM territories WHERE id = ?", arrayOf(territoryA.toString()))
        }
        repository.delete(territoryA, created.id)
        sqlite.query("SELECT COUNT(*) FROM user_point_photos WHERE user_point_id = ?", arrayOf(created.id.toString())).use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun photoInsertionCollisionRollsBackPointAndExistingPhotos() = runBlocking {
        val sharedPhotoId = UUID.randomUUID()
        val original = repository.create(point(territoryId = territoryA, photos = listOf(photo(id = sharedPhotoId))))
        val replacement = point(territoryId = territoryA, id = UUID.randomUUID(), photos = listOf(photo(id = sharedPhotoId)))

        assertThrows(SQLiteConstraintException::class.java) { runBlocking { repository.create(replacement) } }
        val after = repository.getPoint(territoryA, original.id)
        assertEquals(original, after)
        assertEquals(1, database.userPointDao().getPoint(territoryA, original.id)?.photos?.size)
        assertNull(repository.getPoint(territoryA, replacement.id))
    }

    @Test
    fun photoPrimaryKeyCollisionRollsBackUpdateTitleAndPhotos() = runBlocking {
        val sharedPhotoId = UUID.randomUUID()
        repository.create(point(territoryId = territoryA, photos = listOf(photo(id = sharedPhotoId))))
        val original = repository.create(point(territoryId = territoryA, photos = listOf(photo())))
        val conflictingPhoto = photo(id = sharedPhotoId, userPointId = original.id)

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { repository.update(original.copy(title = "Изменённая", photos = listOf(conflictingPhoto))) }
        }
        assertEquals(original, repository.getPoint(territoryA, original.id))
    }

    private fun point(
        territoryId: UUID,
        id: UUID = UUID.randomUUID(),
        isVisible: Boolean = true,
        photos: List<UserPointPhoto> = emptyList(),
    ) = UserPoint(
        id, territoryId, "Точка", null, 56.8, 60.6, isVisible, now, now,
        photos.map { it.copy(userPointId = id, relativePath = "user-point-media/$id/${it.id}") },
    )

    private fun photo(
        id: UUID = UUID.randomUUID(),
        userPointId: UUID = UUID(0, 0),
        relativePath: String? = null,
    ): UserPointPhoto {
        return UserPointPhoto(id, userPointId, relativePath ?: "user-point-media/$userPointId/$id", "photo.jpg", "image/jpeg", 1, "a".repeat(64), now)
    }
}
