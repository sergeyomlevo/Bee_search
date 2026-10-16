package org.beesearch.app.data.gpx

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.IOException
import java.io.OutputStream

internal enum class GpxOriginalStoreError {
    OWNERSHIP_VIOLATION, UNSAFE_PATH_OR_FILE_TYPE, CONFLICT, IDENTITY_MISMATCH,
    INPUT_LIMIT_EXCEEDED, INVALID_EMPTY_SOURCE, STORAGE_EXHAUSTED, IO_FAILURE,
    UNSUPPORTED_FILESYSTEM_OPERATION, INCOMPLETE_PUBLICATION,
}
internal enum class GpxOriginalStage { COPY, OWNER, VERIFY, RESERVE, PROOF, FINAL_COPY, DIRECTORY_SYNC, CLEANUP, OPEN, INSPECT, READ, WRITE, CLOSE, SYNC }
internal class GpxOriginalStoreException(
    val code: GpxOriginalStoreError,
    val stage: GpxOriginalStage,
    val finalMayExist: Boolean = false,
    cause: Throwable? = null,
) : IOException("${code.name}:${stage.name}", cause)

/** Android POSIX boundary only; never chooses ownership, lifecycle or persistence state. */
internal class GpxOriginalFiles(private val root: File) {
    val staging = File(root, "gpx-staging")
    val originals = File(root, "gpx-originals")

    fun prepare() {
        requireDirectory(root)
        for (dir in listOf(staging, originals)) {
            if (stat(dir) == null) {
                try { Os.mkdir(dir.path, 448) } catch (e: ErrnoException) {
                    if (e.errno != OsConstants.EEXIST) throw e
                }
                syncDirectory(root)
            }
            requireDirectory(dir)
        }
        if (Os.stat(staging.path).st_dev != Os.stat(originals.path).st_dev) {
            throw GpxOriginalStoreException(GpxOriginalStoreError.UNSUPPORTED_FILESYSTEM_OPERATION, GpxOriginalStage.RESERVE)
        }
    }

    fun stat(file: File): StructStat? = try { Os.lstat(file.path) } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) null else throw e
    }

    fun requireDirectory(dir: File) {
        val info = stat(dir)
        if (info == null || !OsConstants.S_ISDIR(info.st_mode)) unsafe()
    }

    fun checkParents(file: File) {
        val path = file.absoluteFile.toPath().normalize()
        val base = root.toPath()
        if (!path.startsWith(base) || path == base) unsafe()
        var parent = file.parentFile
        while (parent != null && parent != root) {
            requireDirectory(parent)
            parent = parent.parentFile
        }
        if (parent != root) unsafe()
        requireDirectory(root)
    }

    fun open(file: File): FileInputStream {
        checkParents(file)
        val fd = Os.open(file.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK, 0)
        try {
            val info = Os.fstat(fd)
            val pathInfo = stat(file)
            if (!OsConstants.S_ISREG(info.st_mode) || pathInfo == null || !same(info, pathInfo)) unsafe()
            return FileInputStream(fd)
        } catch (error: Throwable) {
            try { Os.close(fd) } catch (close: Throwable) { error.addSuppressed(close) }
            throw error
        }
    }

    fun create(file: File): FileOutputStream {
        checkParents(file)
        val fd = Os.open(file.path, OsConstants.O_WRONLY or OsConstants.O_CREAT or
            OsConstants.O_EXCL or OsConstants.O_NOFOLLOW, 384)
        try {
            if (!OsConstants.S_ISREG(Os.fstat(fd).st_mode)) unsafe()
            return object : FileOutputStream(fd) {
                override fun close() {
                    try { super.close() } catch (error: IOException) {
                        throw GpxOriginalStoreException(GpxOriginalStoreError.IO_FAILURE, GpxOriginalStage.CLOSE, cause = error)
                    }
                }
            }
        } catch (error: Throwable) {
            try { Os.close(fd) } catch (close: Throwable) { error.addSuppressed(close) }
            throw error
        }
    }

    fun verify(file: File, expected: GpxOriginalIdentity, check: () -> Unit) {
        validateDescriptor(expected)
        val actual = open(file).use { input -> copyGpxOriginal(input, object : OutputStream() {
            override fun write(value: Int) = Unit
            override fun write(bytes: ByteArray, offset: Int, count: Int) = Unit
        }, check) }
        if (actual != expected) throw GpxOriginalStoreException(
            GpxOriginalStoreError.IDENTITY_MISMATCH, GpxOriginalStage.VERIFY)
    }

    fun syncFile(output: FileOutputStream) {
        try { output.fd.sync() } catch (error: IOException) {
            throw GpxOriginalStoreException(GpxOriginalStoreError.IO_FAILURE, GpxOriginalStage.SYNC, cause = error)
        }
    }

    fun sameFile(a: File, b: File): Boolean = open(a).use { first ->
        open(b).use { second -> same(Os.fstat(first.fd), Os.fstat(second.fd)) }
    }

    fun syncDirectory(dir: File) {
        requireDirectory(dir)
        val fd = Os.open(dir.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        var primary: Throwable? = null
        try {
            if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode) || !same(Os.fstat(fd), Os.lstat(dir.path))) unsafe()
            try { Os.fsync(fd) } catch (error: ErrnoException) {
                val code = if (error.errno == OsConstants.EINVAL || error.errno == OsConstants.EOPNOTSUPP ||
                    error.errno == OsConstants.ENOSYS) GpxOriginalStoreError.UNSUPPORTED_FILESYSTEM_OPERATION
                else GpxOriginalStoreError.IO_FAILURE
                throw GpxOriginalStoreException(code, GpxOriginalStage.DIRECTORY_SYNC, cause = error)
            }
        } catch (error: Throwable) { primary = error; throw error } finally {
            try { Os.close(fd) } catch (close: Throwable) {
                if (primary != null) primary.addSuppressed(close) else throw GpxOriginalStoreException(
                    GpxOriginalStoreError.IO_FAILURE, GpxOriginalStage.CLOSE, cause = close)
            }
        }
    }

    fun unlink(file: File) {
        checkParents(file)
        // A symlink is never an operation-owned payload, even if its target is valid.
        open(file).use { }
        Os.remove(file.path)
    }

    companion object {
        fun same(a: StructStat, b: StructStat) = a.st_dev == b.st_dev && a.st_ino == b.st_ino
        fun validateDescriptor(value: GpxOriginalIdentity) {
            if (value.length !in 1..GpxXmlLimits().bytes || !value.sha256.matches(Regex("[0-9a-f]{64}"))) {
                throw GpxOriginalStoreException(GpxOriginalStoreError.IDENTITY_MISMATCH, GpxOriginalStage.VERIFY)
            }
        }
        fun unsafe(): Nothing = throw GpxOriginalStoreException(
            GpxOriginalStoreError.UNSAFE_PATH_OR_FILE_TYPE, GpxOriginalStage.OPEN)
    }
}
