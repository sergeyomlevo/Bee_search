package org.beesearch.app.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

internal data class StagedPhysicalObjectMedia(
    val id: UUID,
    val draftSessionId: UUID,
    val type: PhysicalObjectMediaType,
    val relativePath: String,
    val originalFileName: String?,
    val mimeType: String?,
    val byteSize: Long,
    val sha256: String,
    val createdAt: Instant,
)

/** App-owned bytes for media attached to a durable Hollow or LogHive. */
internal class PhysicalObjectMediaFileStore(
    filesDirectory: File,
    cacheDirectory: File,
) {
    internal val mediaRoot = File(filesDirectory, MEDIA_DIRECTORY)
    internal val stagingRoot = File(cacheDirectory, MEDIA_STAGING_DIRECTORY)
    private val draftRoot = File(filesDirectory, MEDIA_STAGING_DIRECTORY)

    suspend fun stageDraftMedia(
        draftSessionId: UUID,
        mediaId: UUID,
        type: PhysicalObjectMediaType,
        originalFileName: String?,
        mimeType: String?,
        createdAt: Instant,
        source: () -> InputStream,
    ): StagedPhysicalObjectMedia = withContext(Dispatchers.IO) {
        val relativePath = draftRelativePath(draftSessionId, mediaId)
        val destination = requireDraftFile(relativePath)
        val partial = File(destination.parentFile, "${destination.name}.part")
        destination.parentFile?.mkdirs()
        try {
            val stored = writeValidated(partial, source)
            if (destination.exists() || !partial.renameTo(destination)) {
                throw AttachmentStorageException("Не удалось подготовить медиа")
            }
            StagedPhysicalObjectMedia(
                id = mediaId,
                draftSessionId = draftSessionId,
                type = type,
                relativePath = relativePath,
                originalFileName = originalFileName,
                mimeType = mimeType,
                byteSize = stored.byteSize,
                sha256 = stored.sha256,
                createdAt = createdAt,
            )
        } finally {
            partial.delete()
        }
    }

    fun resolveDraft(relativePath: String): File = requireDraftFile(relativePath)

    suspend fun deleteDraft(item: StagedPhysicalObjectMedia): Boolean = withContext(Dispatchers.IO) {
        val file = requireDraftFile(item.relativePath)
        val deleted = !file.exists() || file.delete()
        val directory = draftDirectory(item.draftSessionId)
        if (directory.listFiles()?.isEmpty() == true) directory.delete()
        deleted
    }

    suspend fun discardDraft(draftSessionId: UUID): Boolean = withContext(Dispatchers.IO) {
        val directory = draftDirectory(draftSessionId)
        !directory.exists() || directory.deleteRecursively()
    }

    suspend fun prepareDraftActivation(
        draftSessionId: UUID,
        physicalObjectId: UUID,
        items: List<StagedPhysicalObjectMedia>,
    ): PhysicalObjectMediaActivation = withContext(Dispatchers.IO) {
        require(items.all { it.draftSessionId == draftSessionId }) { "Draft media belongs to another session" }
        val moves = mutableListOf<AttachmentFileMove>()
        try {
            val media = items.map { item ->
                val source = requireDraftFile(item.relativePath)
                require(source.isFile) { "Draft media is missing" }
                if (source.length() != item.byteSize || sha256(source) != item.sha256) {
                    throw AttachmentStorageException("Временное медиа повреждено")
                }
                val finalPath = relativePath(physicalObjectId, item.id)
                val destination = requireManagedFile(finalPath)
                destination.parentFile?.mkdirs()
                if (destination.exists() || !source.renameTo(destination)) {
                    throw AttachmentStorageException("Не удалось сохранить медиа объекта")
                }
                moves += AttachmentFileMove(source, destination)
                PhysicalObjectMedia(
                    id = item.id,
                    physicalObjectId = physicalObjectId,
                    type = item.type,
                    relativePath = finalPath,
                    originalFileName = item.originalFileName,
                    mimeType = item.mimeType,
                    byteSize = item.byteSize,
                    sha256 = item.sha256,
                    createdAt = item.createdAt,
                )
            }
            PhysicalObjectMediaActivation(draftDirectory(draftSessionId), media, moves)
        } catch (error: Throwable) {
            moves.asReversed().forEach { move ->
                move.original.parentFile?.mkdirs()
                move.staged.renameTo(move.original)
            }
            throw error
        }
    }

    suspend fun importMedia(
        physicalObjectId: UUID,
        mediaId: UUID,
        source: () -> InputStream,
    ): StoredAttachmentFile = withContext(Dispatchers.IO) {
        val finalPath = relativePath(physicalObjectId, mediaId)
        val destination = requireManagedFile(finalPath)
        val partial = File(stagingRoot, "import-$mediaId.part")
        partial.parentFile?.mkdirs()
        destination.parentFile?.mkdirs()
        try {
            val stored = writeValidated(partial, source)
            if (destination.exists() || !partial.renameTo(destination)) {
                throw AttachmentStorageException("Не удалось восстановить медиа объекта")
            }
            StoredAttachmentFile(finalPath, stored.byteSize, stored.sha256)
        } finally {
            partial.delete()
        }
    }

    fun resolve(relativePath: String): File = requireManagedFile(relativePath)

    suspend fun delete(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = requireManagedFile(relativePath)
        !file.exists() || file.delete()
    }

    fun cameraCaptureFile(captureId: UUID): File = File(stagingRoot, "camera-$captureId.jpg").also {
        it.parentFile?.mkdirs()
    }

    private fun writeValidated(target: File, source: () -> InputStream): StoredAttachmentFile {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        source().use { input ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    size += read
                    if (size > MAX_MEDIA_BYTES) throw PhysicalObjectMediaTooLargeException(MAX_MEDIA_BYTES)
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        if (size == 0L) throw EmptyAttachmentException()
        return StoredAttachmentFile(target.name, size, digest.digest().toHexString())
    }

    private fun requireDraftFile(relativePath: String): File = requireSafeFile(
        root = draftRoot,
        base = requireNotNull(draftRoot.parentFile),
        relativePath = relativePath,
        description = "draft object media",
    )

    private fun requireManagedFile(relativePath: String): File = requireSafeFile(
        root = mediaRoot,
        base = requireNotNull(mediaRoot.parentFile),
        relativePath = relativePath,
        description = "object media",
    )

    private fun draftDirectory(draftSessionId: UUID): File = requireNotNull(
        requireDraftFile("$MEDIA_STAGING_DIRECTORY/$draftSessionId/session").parentFile,
    )

    internal companion object {
        const val MEDIA_DIRECTORY = "physical-object-media"
        const val MEDIA_STAGING_DIRECTORY = "physical-object-media-staging"
        const val MAX_MEDIA_BYTES = 16L * 1024 * 1024

        fun relativePath(physicalObjectId: UUID, mediaId: UUID): String =
            "$MEDIA_DIRECTORY/$physicalObjectId/$mediaId"

        fun draftRelativePath(draftSessionId: UUID, mediaId: UUID): String =
            "$MEDIA_STAGING_DIRECTORY/$draftSessionId/$mediaId"
    }
}

internal class PhysicalObjectMediaActivation(
    private val draftDirectory: File,
    val media: List<PhysicalObjectMedia>,
    private val moves: List<AttachmentFileMove>,
) {
    suspend fun commit() = withContext(Dispatchers.IO) {
        if (draftDirectory.exists() && !draftDirectory.deleteRecursively()) {
            throw AttachmentStorageException("Не удалось очистить временные медиа")
        }
    }

    suspend fun rollback() = withContext(Dispatchers.IO) {
        moves.asReversed().forEach { move ->
            move.original.parentFile?.mkdirs()
            if (move.staged.exists() && !move.staged.renameTo(move.original)) {
                throw AttachmentStorageException("Не удалось восстановить временные медиа")
            }
        }
    }
}

internal class PhysicalObjectMediaTooLargeException(maxBytes: Long) :
    AttachmentStorageException("Медиа превышает допустимый размер ${maxBytes / (1024 * 1024)} МБ")

private fun requireSafeFile(root: File, base: File, relativePath: String, description: String): File {
    require(relativePath.isNotBlank() && !relativePath.contains('\\')) { "Invalid $description path" }
    val relative = File(relativePath)
    require(!relative.isAbsolute && relativePath.split('/').none { it.isBlank() || it == "." || it == ".." }) {
        "Invalid $description path"
    }
    val canonicalRoot = root.canonicalFile
    val resolved = File(base, relativePath).canonicalFile
    require(resolved.path.startsWith(canonicalRoot.path + File.separator)) { "$description path escapes storage root" }
    return resolved
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHexString()
}
