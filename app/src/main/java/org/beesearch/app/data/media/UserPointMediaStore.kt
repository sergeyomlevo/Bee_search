package org.beesearch.app.data.media

import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.UserPointPhoto
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** UserPoint-owned image storage, backed by the existing streaming/activation pipeline. */
internal class UserPointMediaStore(filesDirectory: File, cacheDirectory: File) {
    private val managedRoot = File(filesDirectory, "user-point-media").canonicalFile
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

    /** Fail closed for another owner, malformed metadata, or an owner-directory symlink. */
    private fun ownedFile(pointId: UUID, photo: UserPointPhoto): File {
        require(photo.userPointId == pointId && photo.relativePath == "user-point-media/$pointId/${photo.id}")
        val file = delegate.resolve(photo.relativePath)
        require(file == File(managedRoot, "$pointId/${photo.id}")) { "Чужое фото" }
        return file
    }

    suspend fun requireAvailable(pointId: UUID, photos: List<UserPointPhoto>) = withContext(Dispatchers.IO) {
        photos.forEach { photo ->
            val file = ownedFile(pointId, photo)
            require(file.isFile && file.length() == photo.byteSize) { "Фото недоступно" }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            require(hash == photo.sha256) { "Фото повреждено" }
        }
    }

    /** Only call AFTER the owning metadata transaction. Missing bytes are an idempotent success. */
    suspend fun cleanupOwned(pointId: UUID, photos: List<UserPointPhoto>, removeDirectory: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            var complete = true
            photos.forEach { photo ->
                complete = cleanupStep {
                    ownedFile(pointId, photo)
                    delegate.delete(photo.relativePath)
                } && complete
            }
            if (removeDirectory) complete = cleanupStep {
                val directory = File(managedRoot, pointId.toString())
                require(directory.canonicalFile == directory) { "Чужая папка фото" }
                delegate.deleteObjectDirectory(pointId)
            } && complete
            complete
        }

    private suspend fun cleanupStep(step: suspend () -> Boolean): Boolean = try {
        step()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { false }

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
