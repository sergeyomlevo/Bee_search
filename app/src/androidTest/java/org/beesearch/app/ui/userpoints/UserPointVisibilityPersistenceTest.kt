package org.beesearch.app.ui.userpoints

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.local.room.BeeSearchDatabase
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.data.repository.RoomUserPointRepository
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.model.UserPointPhoto
import org.junit.Assert.*
import org.junit.Test

class UserPointVisibilityPersistenceTest {
    @Test fun visibilitySurvivesDatabaseReopenAndDoesNotChangePointOrPhotoMetadata() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "user-point-stage-c-${UUID.randomUUID()}.db"
        val a = UUID.randomUUID(); val b = UUID.randomUUID()
        fun open() = Room.databaseBuilder(context, BeeSearchDatabase::class.java, name).build()
        var database = open()
        try {
            database.backupDao().insertTerritories(listOf(a, b).map {
                TerritoryEntity(it, it.toString(), it.toString(), "R", "D", Instant.EPOCH, Instant.EPOCH)
            })
            val repository = RoomUserPointRepository(database)
            val id = UUID.randomUUID(); val photo = UUID.randomUUID()
            val point = repository.create(UserPoint(id, a, "Длинное русское название", "Не менять заметку",
                56.8, 60.6, false, Instant.EPOCH, Instant.EPOCH,
                listOf(UserPointPhoto(photo, id, "user-point-media/$id/$photo", "portrait.jpg", "image/jpeg", 3, "a".repeat(64), Instant.EPOCH))))
            assertTrue(point.isVisible)
            val other = repository.create(point.copy(id = UUID.randomUUID(), territoryId = b, photos = emptyList()))
            repository.setVisible(a, id, false)
            database.close()
            database = open()
            val reloaded = RoomUserPointRepository(database)
            assertEquals(point.copy(isVisible = false), reloaded.getPoint(a, id))
            assertNull(reloaded.getPoint(b, id))
            assertEquals(other, reloaded.getPoint(b, other.id))
            reloaded.setVisible(a, id, true)
            assertEquals(point, reloaded.getPoint(a, id))
        } finally {
            database.close()
            context.deleteDatabase(name) // Only the uniquely named database owned by this test.
        }
    }
}
