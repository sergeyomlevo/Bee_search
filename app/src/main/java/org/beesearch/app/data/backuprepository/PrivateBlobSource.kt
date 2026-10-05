package org.beesearch.app.data.backuprepository

import java.io.File
import java.io.InputStream
import java.nio.file.Files

/** Repeatable read of an app-private original. Never exposes a delete operation. */
internal interface BlobSource {
    val byteSize: Long
    val mimeHints: List<String?>
    val expectedSha256: String?
    fun open(): InputStream
    fun checkUnchanged()
}

internal class PrivateBlobSource(
    private val privateRoot: File,
    private val file: File,
    override val mimeHints: List<String?>,
    override val expectedSha256: String? = null,
) : BlobSource {
    private val initialCanonicalRoot = try { privateRoot.canonicalFile.toPath() } catch (e: Exception) {
        throw RepositoryException(RepositoryError.SOURCE_CHANGED, e)
    }
    private val initialSize: Long
    private val initialModified: Long
    init {
        checkPath()
        initialSize = file.length()
        initialModified = file.lastModified()
    }
    override val byteSize: Long get() = initialSize
    override fun open(): InputStream {
        checkUnchanged()
        return try { file.inputStream() } catch (e: Exception) {
            throw RepositoryException(RepositoryError.SOURCE_CHANGED, e)
        }
    }
    override fun checkUnchanged() {
        checkPath()
        if (file.length() != initialSize || file.lastModified() != initialModified) {
            throw RepositoryException(RepositoryError.SOURCE_CHANGED)
        }
    }
    private fun checkPath() {
        try {
            val root = privateRoot.canonicalFile.toPath()
            if (root != initialCanonicalRoot) throw RepositoryException(RepositoryError.SOURCE_CHANGED)
            // Android's trusted app-private root can itself use a system alias (/data/user/0).
            // Resolve that root once conceptually, but reject aliases below the trusted boundary.
            val lexicalRoot = privateRoot.absoluteFile.toPath().normalize()
            val lexicalPath = file.absoluteFile.toPath().normalize()
            if (!lexicalPath.startsWith(lexicalRoot) || lexicalPath == lexicalRoot) {
                throw RepositoryException(RepositoryError.SOURCE_CHANGED)
            }
            val path = root.resolve(lexicalRoot.relativize(lexicalPath))
            if (file.canonicalFile.toPath() != path) {
                throw RepositoryException(RepositoryError.SOURCE_CHANGED)
            }
            var part = path
            while (part != root) {
                if (Files.isSymbolicLink(part)) throw RepositoryException(RepositoryError.SOURCE_CHANGED)
                part = part.parent
            }
            if (!file.isFile) throw RepositoryException(RepositoryError.SOURCE_CHANGED)
        } catch (e: RepositoryException) { throw e } catch (e: Exception) {
            throw RepositoryException(RepositoryError.SOURCE_CHANGED, e)
        }
    }
}
