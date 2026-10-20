package org.beesearch.app.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
internal interface UserPointDao {
    @Transaction @Query("SELECT * FROM user_points WHERE territory_id = :territory ORDER BY created_at, id")
    fun observePoints(territory: UUID): Flow<List<UserPointWithPhotos>>
    @Transaction @Query("SELECT * FROM user_points WHERE territory_id = :territory AND id = :id")
    suspend fun getPoint(territory: UUID, id: UUID): UserPointWithPhotos?
    @Query("SELECT COUNT(*) FROM user_points WHERE territory_id = :territory")
    suspend fun countForTerritory(territory: UUID): Int
    @Insert suspend fun insert(point: UserPointEntity)
    @Update suspend fun update(point: UserPointEntity): Int
    @Insert suspend fun insertPhotos(photos: List<UserPointPhotoEntity>)
    @Query("DELETE FROM user_point_photos WHERE user_point_id = :id")
    suspend fun deletePhotos(id: UUID)
    @Query("DELETE FROM user_points WHERE territory_id = :territory AND id = :id")
    suspend fun delete(territory: UUID, id: UUID): Int
    @Query("UPDATE user_points SET is_visible = :visible WHERE territory_id = :territory AND id = :id")
    suspend fun setVisible(territory: UUID, id: UUID, visible: Boolean): Int
}
