package org.beesearch.app.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import org.beesearch.app.data.local.room.*
import org.beesearch.app.domain.model.UserPoint
import org.beesearch.app.domain.model.UserPointPhoto
import org.beesearch.app.domain.model.validateUserPoint
import org.beesearch.app.domain.repository.UserPointRepository
import java.time.Clock
import java.util.UUID

internal class RoomUserPointRepository(
    private val database: BeeSearchDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : UserPointRepository {
    private val dao = database.userPointDao()
    override fun observePoints(territoryId: UUID) = dao.observePoints(territoryId).map { rows -> rows.map { it.domain() } }
    override suspend fun getPoint(territoryId: UUID, id: UUID) = dao.getPoint(territoryId, id)?.domain()

    override suspend fun create(point: UserPoint): UserPoint = database.withTransaction {
        point.validateUserPoint()
        val saved = point.copy(title = point.title.trim(), note = point.note?.trim()?.takeIf { it.isNotEmpty() }, isVisible = true)
        dao.insert(saved.entity())
        dao.insertPhotos(saved.photos.map { it.entity() })
        saved
    }

    override suspend fun update(point: UserPoint): UserPoint = database.withTransaction {
        point.validateUserPoint()
        val old = requireNotNull(getPoint(point.territoryId, point.id)) { "Точка не найдена" }
        // Visibility, identity, Territory and creation timestamp cannot be overwritten by an editor snapshot.
        val saved = old.copy(title = point.title.trim(), note = point.note?.trim()?.takeIf { it.isNotEmpty() },
            latitude = point.latitude, longitude = point.longitude, photos = point.photos, updatedAt = clock.instant())
        check(dao.update(saved.entity()) == 1)
        dao.deletePhotos(saved.id)
        dao.insertPhotos(saved.photos.map { it.entity() })
        saved
    }

    override suspend fun setVisible(territoryId: UUID, id: UUID, visible: Boolean) {
        check(dao.setVisible(territoryId, id, visible) == 1) { "Точка не найдена" }
    }

    override suspend fun delete(territoryId: UUID, id: UUID): UserPoint = database.withTransaction {
        val old = requireNotNull(getPoint(territoryId, id)) { "Точка не найдена" }
        check(dao.delete(territoryId, id) == 1)
        old
    }
}

private fun UserPointWithPhotos.domain() = UserPoint(point.id, point.territoryId, point.title, point.note,
    point.latitude, point.longitude, point.isVisible, point.createdAt, point.updatedAt,
    photos.sortedWith(compareBy({ it.createdAt }, { it.id })).map { UserPointPhoto(it.id, it.userPointId,
        it.relativePath, it.originalFileName, it.mimeType, it.byteSize, it.sha256, it.createdAt) })
private fun UserPoint.entity() = UserPointEntity(id, territoryId, title, note, latitude, longitude, isVisible, createdAt, updatedAt)
private fun UserPointPhoto.entity() = UserPointPhotoEntity(id, userPointId, relativePath, originalFileName, mimeType, byteSize, sha256, createdAt)
