package org.beesearch.app.data.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

internal data class StoredAttachmentFile(
    val relativePath: String,
    val byteSize: Long,
    val sha256: String,
)

/** App-owned byte storage for ObservationPoint attachments. Room stores only [relativePath]. */
internal class ObservationAttachmentFileStore(
    filesDirectory: File,
    cacheDirectory: File,
) {
    internal val attachmentsRoot = File(filesDirectory, ATTACHMENTS_DIRECTORY)
    internal val stagingRoot = File(cacheDirectory, ATTACHMENT_STAGING_DIRECTORY)

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
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            source().use { input ->
                staging.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        size += read
                        if (size > MAX_PHOTO_BYTES) {
                            throw AttachmentTooLargeException(MAX_PHOTO_BYTES)
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (size == 0L) throw EmptyAttachmentException()
            if (destination.exists() || !staging.renameTo(destination)) {
                throw AttachmentStorageException("Не удалось сохранить фотографию")
            }
            StoredAttachmentFile(relativePath, size, digest.digest().toHex())
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
