package org.beesearch.app.data.backuprepository

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

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
            // Containment is proven on resolved paths. The trusted app-private root is reachable
            // through Android's system path alias (observed on the device: `/data/user/0` from the
            // application context, `/data/data` from the filesystem), so the raw spellings of the
            // root and of the file legitimately differ while both name the same private directory.
            // Resolving both forms is what makes "inside the trusted private root" decidable;
            // a source that resolves outside that root is still refused.
            val resolved = try { file.canonicalFile.toPath() } catch (e: Exception) {
                throw RepositoryException(RepositoryError.SOURCE_CHANGED, e)
            }
            if (!resolved.startsWith(root) || resolved == root) throw RepositoryException(RepositoryError.SOURCE_CHANGED)
            if (!Files.isRegularFile(resolved)) throw RepositoryException(RepositoryError.SOURCE_CHANGED)
            // A symlink below the trusted boundary must never redirect the read, even when it still
            // resolves inside: check the resolved chain, and also the raw chain when the two raw
            // spellings are directly comparable.
            rejectSymbolicLinksBelow(root, resolved)
            val lexicalRoot = privateRoot.absoluteFile.toPath().normalize()
            val lexicalFile = file.absoluteFile.toPath().normalize()
            if (lexicalFile.startsWith(lexicalRoot) && lexicalFile != lexicalRoot) {
                rejectSymbolicLinksBelow(lexicalRoot, lexicalFile)
            }
        } catch (e: RepositoryException) { throw e } catch (e: Exception) {
            throw RepositoryException(RepositoryError.SOURCE_CHANGED, e)
        }
    }

    private fun rejectSymbolicLinksBelow(root: Path, path: Path) {
        var part: Path? = path
        while (part != null && part != root) {
            if (Files.isSymbolicLink(part)) throw RepositoryException(RepositoryError.SOURCE_CHANGED)
            part = part.parent
        }
    }
}
