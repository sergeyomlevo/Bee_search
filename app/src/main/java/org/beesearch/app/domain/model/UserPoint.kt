package org.beesearch.app.domain.model

import java.time.Instant
import java.util.UUID

/** Independent Territory-owned user data, never an ObservationPoint or research object. */
internal data class UserPoint(
    val id: UUID,
    val territoryId: UUID,
    val title: String,
    val note: String?,
    val latitude: Double,
    val longitude: Double,
    val isVisible: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
    val photos: List<UserPointPhoto> = emptyList(),
)

internal data class UserPointPhoto(
    val id: UUID,
    val userPointId: UUID,
    val relativePath: String,
    val originalFileName: String?,
    val mimeType: String?,
    val byteSize: Long,
    val sha256: String,
    val createdAt: Instant,
)

internal fun UserPoint.validateUserPoint() {
    require(title.isNotBlank()) { "Введите название" }
    require(latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0) { "Уточните координаты" }
    require(photos.map { it.id }.toSet().size == photos.size) { "Повторное фото" }
    photos.forEach { photo ->
        require(photo.userPointId == id && photo.relativePath == "user-point-media/$id/${photo.id}") { "Чужое фото" }
        require(photo.byteSize > 0 && photo.sha256.matches(Regex("[0-9a-f]{64}"))) { "Повреждённое фото" }
        require(photo.mimeType == null || photo.mimeType.startsWith("image/")) { "Только фотографии" }
    }
}
