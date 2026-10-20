package org.beesearch.app.domain.model

import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Test

class UserPointValidationTest {
    private val pointId = UUID.randomUUID()
    private val territoryId = UUID.randomUUID()
    private val now = Instant.parse("2026-10-20T08:00:00Z")

    @Test
    fun rejectsBlankTitleAndOutOfRangeOrNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException::class.java) { point(title = " ").validateUserPoint() }
        assertThrows(IllegalArgumentException::class.java) { point(latitude = 90.01).validateUserPoint() }
        assertThrows(IllegalArgumentException::class.java) { point(longitude = -180.01).validateUserPoint() }
        assertThrows(IllegalArgumentException::class.java) { point(latitude = Double.NaN).validateUserPoint() }
        assertThrows(IllegalArgumentException::class.java) { point(longitude = Double.POSITIVE_INFINITY).validateUserPoint() }
    }

    @Test
    fun rejectsPhotoOutsideOwnerNamespaceOrNonPhotoMetadata() {
        val photoId = UUID.randomUUID()
        assertThrows(IllegalArgumentException::class.java) {
            point(photos = listOf(photo(photoId, userPointId = UUID.randomUUID()))).validateUserPoint()
        }
        assertThrows(IllegalArgumentException::class.java) {
            point(photos = listOf(photo(photoId, mimeType = "video/mp4"))).validateUserPoint()
        }
        assertThrows(IllegalArgumentException::class.java) {
            point(photos = listOf(photo(photoId, byteSize = 0))).validateUserPoint()
        }
        assertThrows(IllegalArgumentException::class.java) {
            point(photos = listOf(photo(photoId, sha256 = "bad"))).validateUserPoint()
        }
    }

    private fun point(
        title: String = "Точка",
        latitude: Double = 56.8,
        longitude: Double = 60.6,
        photos: List<UserPointPhoto> = emptyList(),
    ) = UserPoint(
        id = pointId,
        territoryId = territoryId,
        title = title,
        note = null,
        latitude = latitude,
        longitude = longitude,
        isVisible = false,
        createdAt = now,
        updatedAt = now,
        photos = photos,
    )

    private fun photo(
        id: UUID,
        userPointId: UUID = pointId,
        mimeType: String? = "image/jpeg",
        byteSize: Long = 1,
        sha256: String = "a".repeat(64),
    ) = UserPointPhoto(
        id = id,
        userPointId = userPointId,
        relativePath = "user-point-media/$userPointId/$id",
        originalFileName = "photo.jpg",
        mimeType = mimeType,
        byteSize = byteSize,
        sha256 = sha256,
        createdAt = now,
    )
}
