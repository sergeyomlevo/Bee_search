package org.beesearch.app.domain.repository

import kotlinx.coroutines.flow.Flow
import org.beesearch.app.domain.model.UserPoint
import java.util.UUID

internal interface UserPointRepository {
    fun observePoints(territoryId: UUID): Flow<List<UserPoint>>
    suspend fun getPoint(territoryId: UUID, id: UUID): UserPoint?
    suspend fun create(point: UserPoint): UserPoint
    suspend fun update(point: UserPoint): UserPoint
    suspend fun setVisible(territoryId: UUID, id: UUID, visible: Boolean)
    /** Returns the deleted snapshot for owner-scoped file cleanup AFTER the transaction. */
    suspend fun delete(territoryId: UUID, id: UUID): UserPoint
}
