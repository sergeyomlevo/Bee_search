package org.beesearch.app.data.backuprepository

import android.os.Environment
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beesearch.app.BuildConfig

/** Directory skeleton only: never reads, creates or replaces repository identity. */
internal class BackupDirectoryBootstrap(publicDownloads: File, variant: String) {
    val root = File(publicDownloads, "BeeSearch/$variant/Backup")
    init { require(variant in setOf("Stable", "Beta", "Dev")) }

    suspend fun ensure(): RepositoryResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val targets = listOf(root) + listOf("Media", "Snapshots", "Staging").map { File(root, it) }
            targets.forEach { directory ->
                var parent: File? = directory
                while (parent != null) {
                    if (Files.isSymbolicLink(parent.toPath())) {
                        throw RepositoryException(RepositoryError.DIRECTORY_CONFLICT)
                    }
                    parent = parent.parentFile
                }
                if (Files.exists(directory.toPath(), LinkOption.NOFOLLOW_LINKS) && !directory.isDirectory) {
                    throw RepositoryException(RepositoryError.DIRECTORY_CONFLICT)
                }
                if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                    throw RepositoryException(RepositoryError.WRITE_FAILED)
                }
            }
            RepositoryResult.Success(Unit)
        } catch (e: RepositoryException) { RepositoryResult.Failure(e.error) }
        catch (_: SecurityException) { RepositoryResult.Failure(RepositoryError.PERMISSION_LOST) }
        catch (_: Exception) { RepositoryResult.Failure(RepositoryError.PROVIDER_FAILURE) }
    }
}

@Suppress("DEPRECATION")
internal fun beeSearchBackupBootstrap() = BackupDirectoryBootstrap(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    BuildConfig.EXCHANGE_VARIANT,
)
