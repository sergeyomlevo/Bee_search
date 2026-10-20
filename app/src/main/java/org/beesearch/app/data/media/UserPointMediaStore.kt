package org.beesearch.app.data.media

import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.UserPointPhoto
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** UserPoint-owned image storage, backed by the existing streaming/activation pipeline. */
internal class UserPointMediaStore(filesDirectory: File, cacheDirectory: File) {
    private val delegate = PhysicalObjectMediaFileStore(
        filesDirectory = filesDirectory,
        cacheDirectory = cacheDirectory,
        mediaDirectory = PhysicalObjectMediaFileStore.USER_POINT_MEDIA_DIRECTORY,
        stagingDirectory = PhysicalObjectMediaFileStore.USER_POINT_MEDIA_STAGING_DIRECTORY,
    )

    suspend fun stageDraftMedia(
        draftSessionId: UUID,
        mediaId: UUID,
        originalFileName: String?,
        mimeType: String?,
        createdAt: Instant,
        source: () -> InputStream,
    ): StagedUserPointPhoto = delegate.stageDraftMedia(
        draftSessionId, mediaId, PhysicalObjectMediaType.IMAGE,
        originalFileName, mimeType, createdAt, source,
    ).let(::StagedUserPointPhoto)

    fun resolveDraft(relativePath: String): File = delegate.resolveDraft(relativePath)

    fun resolve(relativePath: String): File = delegate.resolve(relativePath)

    suspend fun deleteDraft(item: StagedUserPointPhoto): Boolean = delegate.deleteDraft(item.delegate)

    suspend fun discardDraft(draftSessionId: UUID): Boolean = delegate.discardDraft(draftSessionId)

    suspend fun prepareDraftActivation(
        draftSessionId: UUID,
        userPointId: UUID,
        items: List<StagedUserPointPhoto>,
    ): UserPointMediaActivation = UserPointMediaActivation(
        delegate.prepareDraftActivation(draftSessionId, userPointId, items.map { it.delegate }),
    )

    fun cameraCaptureFile(captureId: UUID): File = delegate.cameraCaptureFile(captureId)
}

internal class StagedUserPointPhoto internal constructor(
    internal val delegate: StagedPhysicalObjectMedia,
) {
    val id: UUID get() = delegate.id
    val relativePath: String get() = delegate.relativePath
    val originalFileName: String? get() = delegate.originalFileName
    val mimeType: String? get() = delegate.mimeType
    val byteSize: Long get() = delegate.byteSize
    val sha256: String get() = delegate.sha256
    val createdAt: Instant get() = delegate.createdAt
}

internal class UserPointMediaActivation internal constructor(
    private val delegate: PhysicalObjectMediaActivation,
) {
    val photos: List<UserPointPhoto>
        get() = delegate.media.map { media ->
            UserPointPhoto(
                id = media.id,
                userPointId = media.physicalObjectId,
                relativePath = media.relativePath,
                originalFileName = media.originalFileName,
                mimeType = media.mimeType,
                byteSize = media.byteSize,
                sha256 = media.sha256,
                createdAt = media.createdAt,
            )
        }

    suspend fun commit() = withContext(NonCancellable) { delegate.commit() }

    suspend fun rollback() = withContext(NonCancellable) { delegate.rollback() }
}
