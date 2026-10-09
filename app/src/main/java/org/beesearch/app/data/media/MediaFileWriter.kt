package org.beesearch.app.data.media

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Owns only its new partial file and publishes it after successful stream/close validation. */
internal suspend fun writeAndPublishMediaFile(
    partial: File,
    destination: File,
    source: () -> InputStream,
    publicationError: String,
    output: (File) -> OutputStream = { it.outputStream() },
): StoredAttachmentFile {
    var ownsPartial = false
    var published = false
    try {
        return withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            if (destination.exists()) throw AttachmentStorageException(publicationError)
            partial.parentFile?.let { parent ->
                if (!parent.mkdirs() && !parent.isDirectory) throw IOException("Cannot create media staging directory")
            }
            destination.parentFile?.let { parent ->
                if (!parent.mkdirs() && !parent.isDirectory) throw IOException("Cannot create media directory")
            }
            if (!partial.createNewFile()) throw IOException("Media staging file already exists")
            ownsPartial = true
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            source().use { input ->
                output(partial).buffered().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        currentCoroutineContext().ensureActive()
                        if (read < 0) break
                        if (read == 0) continue
                        size = Math.addExact(size, read.toLong())
                        sink.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }
            if (size == 0L) throw EmptyAttachmentException()
            if (partial.length() != size) throw IOException("Incomplete media staging file")
            currentCoroutineContext().ensureActive()
            if (destination.exists() || !partial.renameTo(destination)) {
                throw AttachmentStorageException(publicationError)
            }
            published = true
            currentCoroutineContext().ensureActive()
            StoredAttachmentFile(destination.name, size, digest.digest().joinToString("") { "%02x".format(it) })
        }
    } catch (error: Throwable) {
        // Also catches prompt cancellation when the IO result returns to the caller's dispatcher.
        withContext(NonCancellable + Dispatchers.IO) {
            for (file in listOfNotNull(partial.takeIf { ownsPartial }, destination.takeIf { published })) {
                try {
                    if (file.exists() && !file.delete()) throw IOException("Cannot remove incomplete media: ${file.name}")
                } catch (cleanupError: Exception) {
                    error.addSuppressed(cleanupError)
                }
            }
        }
        throw error
    }
}
