package org.beesearch.app.data.local.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import java.time.Instant
import java.util.UUID

@Entity(tableName = "user_points", foreignKeys = [ForeignKey(
    entity = TerritoryEntity::class, parentColumns = ["id"], childColumns = ["territory_id"],
    onDelete = ForeignKey.RESTRICT,
)], indices = [Index("territory_id")])
internal data class UserPointEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "territory_id") val territoryId: UUID,
    val title: String,
    val note: String?,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "is_visible", defaultValue = "1") val isVisible: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "user_point_photos", foreignKeys = [ForeignKey(
    entity = UserPointEntity::class, parentColumns = ["id"], childColumns = ["user_point_id"],
    onDelete = ForeignKey.CASCADE,
)], indices = [Index("user_point_id"), Index(value = ["relative_path"], unique = true)])
internal data class UserPointPhotoEntity(
    @PrimaryKey val id: UUID,
    @ColumnInfo(name = "user_point_id") val userPointId: UUID,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "original_file_name") val originalFileName: String?,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    val sha256: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

internal data class UserPointWithPhotos(
    @Embedded val point: UserPointEntity,
    @Relation(parentColumn = "id", entityColumn = "user_point_id") val photos: List<UserPointPhotoEntity>,
)
