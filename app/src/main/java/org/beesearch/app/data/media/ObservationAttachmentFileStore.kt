package org.beesearch.app.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.domain.model.ObservationPointAttachment

internal data class StoredAttachmentFile(
    val relativePath: String,
    val byteSize: Long,
    val sha256: String,
)

internal data class StagedObservationPointPhoto(
    val id: UUID,
    val draftSessionId: UUID,
    val relativePath: String,
    val originalFileName: String?,
    val mimeType: String?,
    val byteSize: Long,
    val sha256: String,
    val createdAt: Instant,
)

/** App-owned byte storage for ObservationPoint attachments. Room stores only [relativePath]. */
internal class ObservationAttachmentFileStore(
    filesDirectory: File,
    cacheDirectory: File,
) {
    internal val attachmentsRoot = File(filesDirectory, ATTACHMENTS_DIRECTORY)
    internal val stagingRoot = File(cacheDirectory, ATTACHMENT_STAGING_DIRECTORY)
    internal val draftStagingRoot = File(filesDirectory, ATTACHMENT_STAGING_DIRECTORY)

    suspend fun stageDraftPhoto(
        draftSessionId: UUID,
        attachmentId: UUID,
        originalFileName: String?,
        mimeType: String?,
        createdAt: Instant,
        source: () -> InputStream,
    ): StagedObservationPointPhoto = withContext(Dispatchers.IO) {
        val relativePath = draftRelativePath(draftSessionId, attachmentId)
        val destination = requireDraftFile(relativePath)
        val partial = File(destination.parentFile, "${destination.name}.part")
        destination.parentFile?.mkdirs()
        val stored = writeValidatedPhoto(partial, source)
        try {
            if (destination.exists() || !partial.renameTo(destination)) {
                throw AttachmentStorageException("Не удалось подготовить фотографию")
            }
            StagedObservationPointPhoto(
                id = attachmentId,
                draftSessionId = draftSessionId,
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

    fun resolveDraftPhoto(relativePath: String): File = requireDraftFile(relativePath)

    suspend fun deleteDraftPhoto(photo: StagedObservationPointPhoto): Boolean = withContext(Dispatchers.IO) {
        val file = requireDraftFile(photo.relativePath)
        val deleted = !file.exists() || file.delete()
        pruneEmptyDraftDirectory(photo.draftSessionId)
        deleted
    }

    suspend fun discardDraft(draftSessionId: UUID): Boolean = withContext(Dispatchers.IO) {
        val directory = draftSessionDirectory(draftSessionId)
        !directory.exists() || directory.deleteRecursively()
    }

    suspend fun prepareDraftActivation(
        draftSessionId: UUID,
        observationPointId: UUID,
        photos: List<StagedObservationPointPhoto>,
    ): DraftAttachmentActivation = withContext(Dispatchers.IO) {
        require(photos.all { it.draftSessionId == draftSessionId }) { "Draft photo belongs to another session" }
        val moves = mutableListOf<AttachmentFileMove>()
        try {
            val attachments = photos.map { photo ->
                val source = requireDraftFile(photo.relativePath)
                require(source.isFile) { "Draft photo is missing" }
                if (source.length() != photo.byteSize || sha256(source) != photo.sha256) {
                    throw AttachmentStorageException("Временная фотография повреждена")
                }
                val finalRelativePath = relativePath(observationPointId, photo.id)
                val destination = requireManagedFile(finalRelativePath)
                destination.parentFile?.mkdirs()
                if (destination.exists() || !source.renameTo(destination)) {
                    throw AttachmentStorageException("Не удалось сохранить фотографию точки")
                }
                moves += AttachmentFileMove(source, destination)
                ObservationPointAttachment(
                    id = photo.id,
                    observationPointId = observationPointId,
                    type = AttachmentType.PHOTO,
                    relativePath = finalRelativePath,
                    originalFileName = photo.originalFileName,
                    mimeType = photo.mimeType,
                    byteSize = photo.byteSize,
                    sha256 = photo.sha256,
                    createdAt = photo.createdAt,
                )
            }
            DraftAttachmentActivation(draftSessionDirectory(draftSessionId), attachments, moves)
        } catch (error: Throwable) {
            moves.asReversed().forEach { move ->
                move.original.parentFile?.mkdirs()
                move.staged.renameTo(move.original)
            }
            throw error
        }
    }

    suspend fun importPhoto(
        observationPointId: UUID,
        attachmentId: UUID,
        source: () -> InputStream,
    ): StoredAttachmentFile = withContext(Dispatchers.IO) {
        val relativePath = relativePath(observationPointId, attachmentId)
        val destination = requireManagedFile(relativePath)
        val staging = File(stagingRoot, "import-$attachmentId.part")
        staging.parentFile?.mkdirs()
        destination.parentFile?.mkdirs()
        try {
            val stored = writeValidatedPhoto(staging, source)
            if (destination.exists() || !staging.renameTo(destination)) {
                throw AttachmentStorageException("Не удалось сохранить фотографию")
            }
            StoredAttachmentFile(relativePath, stored.byteSize, stored.sha256)
        } finally {
            staging.delete()
        }
    }

    fun resolve(relativePath: String): File = requireManagedFile(relativePath)

    suspend fun delete(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = requireManagedFile(relativePath)
        !file.exists() || file.delete()
    }

    suspend fun stageDeletion(relativePaths: Collection<String>): AttachmentDeletionBatch =
        withContext(Dispatchers.IO) {
            val batchDirectory = File(stagingRoot, "delete-${UUID.randomUUID()}")
            val moves = mutableListOf<AttachmentFileMove>()
            try {
                relativePaths.distinct().forEachIndexed { index, relativePath ->
                    val original = requireManagedFile(relativePath)
                    if (!original.exists()) return@forEachIndexed
                    batchDirectory.mkdirs()
                    val staged = File(batchDirectory, index.toString())
                    if (!original.renameTo(staged)) {
                        throw AttachmentStorageException("Не удалось подготовить удаление фотографии")
                    }
                    moves += AttachmentFileMove(original, staged)
                }
                AttachmentDeletionBatch(batchDirectory, moves)
            } catch (error: Exception) {
                moves.asReversed().forEach { move ->
                    move.original.parentFile?.mkdirs()
                    move.staged.renameTo(move.original)
                }
                batchDirectory.deleteRecursively()
                throw error
            }
        }

    fun cameraCaptureFile(captureId: UUID): File = File(stagingRoot, "camera-$captureId.jpg").also {
        it.parentFile?.mkdirs()
    }

    private fun writeValidatedPhoto(target: File, source: () -> InputStream): StoredAttachmentFile {
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
                    if (size > MAX_PHOTO_BYTES) throw AttachmentTooLargeException(MAX_PHOTO_BYTES)
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        if (size == 0L) throw EmptyAttachmentException()
        return StoredAttachmentFile(target.name, size, digest.digest().toHex())
    }

    private fun requireDraftFile(relativePath: String): File {
        require(relativePath.isNotBlank() && !relativePath.contains('\\')) { "Invalid draft attachment path" }
        val relative = File(relativePath)
        require(!relative.isAbsolute && relativePath.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Invalid draft attachment path"
        }
        val root = draftStagingRoot.canonicalFile
        val resolved = File(filesDirectoryForDrafts(), relativePath).canonicalFile
        require(resolved.path.startsWith(root.path + File.separator)) { "Draft attachment path escapes storage root" }
        return resolved
    }

    private fun filesDirectoryForDrafts(): File = requireNotNull(draftStagingRoot.parentFile)

    private fun draftSessionDirectory(draftSessionId: UUID): File =
        requireNotNull(
            requireDraftFile("$ATTACHMENT_STAGING_DIRECTORY/$draftSessionId/session").parentFile,
        )

    private fun pruneEmptyDraftDirectory(draftSessionId: UUID) {
        val directory = draftSessionDirectory(draftSessionId)
        if (directory.listFiles()?.isEmpty() == true) directory.delete()
    }

    private fun requireManagedFile(relativePath: String): File {
        require(relativePath.isNotBlank() && !relativePath.contains('\\')) { "Invalid attachment path" }
        val relative = File(relativePath)
        require(!relative.isAbsolute && relativePath.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Invalid attachment path"
        }
        val root = attachmentsRoot.canonicalFile
        val resolved = File(attachmentsRoot.parentFile, relativePath).canonicalFile
        require(resolved.path.startsWith(root.path + File.separator)) { "Attachment path escapes storage root" }
        return resolved
    }

    internal companion object {
        const val ATTACHMENTS_DIRECTORY = "observation-attachments"
        const val ATTACHMENT_STAGING_DIRECTORY = "observation-attachments-staging"
        const val MAX_PHOTO_BYTES = 16L * 1024 * 1024

        fun relativePath(observationPointId: UUID, attachmentId: UUID): String =
            "$ATTACHMENTS_DIRECTORY/$observationPointId/$attachmentId"

        fun draftRelativePath(draftSessionId: UUID, attachmentId: UUID): String =
            "$ATTACHMENT_STAGING_DIRECTORY/$draftSessionId/$attachmentId"
    }
}

internal class DraftAttachmentActivation(
    private val draftDirectory: File,
    val attachments: List<ObservationPointAttachment>,
    private val moves: List<AttachmentFileMove>,
) {
    suspend fun commit() = withContext(Dispatchers.IO) {
        if (draftDirectory.exists() && !draftDirectory.deleteRecursively()) {
            throw AttachmentStorageException("Не удалось очистить временные фотографии")
        }
    }

    suspend fun rollback() = withContext(Dispatchers.IO) {
        moves.asReversed().forEach { move ->
            move.original.parentFile?.mkdirs()
            if (move.staged.exists() && !move.staged.renameTo(move.original)) {
                throw AttachmentStorageException("Не удалось восстановить временную фотографию")
            }
        }
    }
}

internal class AttachmentDeletionBatch(
    private val batchDirectory: File,
    private val moves: List<AttachmentFileMove>,
) {
    suspend fun commit() = withContext(Dispatchers.IO) {
        if (!batchDirectory.deleteRecursively() && batchDirectory.exists()) {
            throw AttachmentStorageException("Не удалось окончательно удалить фотографию")
        }
    }

    suspend fun rollback() = withContext(Dispatchers.IO) {
        moves.asReversed().forEach { move ->
            move.original.parentFile?.mkdirs()
            if (move.staged.exists() && !move.staged.renameTo(move.original)) {
                throw AttachmentStorageException("Не удалось восстановить фотографию после ошибки")
            }
        }
        batchDirectory.deleteRecursively()
    }
}

internal data class AttachmentFileMove(val original: File, val staged: File)

internal open class AttachmentStorageException(message: String) : Exception(message)
internal class AttachmentTooLargeException(maxBytes: Long) :
    AttachmentStorageException("Фотография превышает допустимый размер ${maxBytes / (1024 * 1024)} МБ")
internal class EmptyAttachmentException : AttachmentStorageException("Выбран пустой файл")

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

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
    return digest.digest().toHex()
}
