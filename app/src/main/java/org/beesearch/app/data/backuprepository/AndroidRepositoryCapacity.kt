package org.beesearch.app.data.backuprepository

import android.content.Context
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

/**
 * Android-side capacity adapter. This deliberately exposes only fresh
 * StatFs.availableBytes; allocatable/cache-reclaimable bytes are not a field
 * safety reserve.
 */
internal class AndroidRepositoryCapacity(
    context: Context,
    private val privateFilesDir: File,
    private val mappedPublicRoot: File,
) {
    private val storageManager: StorageManager? =
        context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

    /** True only when both paths are existing directories on the same volume. */
    fun hasKnownSameVolume(): Boolean = volumeUuid() != null

    /** Fresh available bytes for the mapped public volume, or null when mapping is unknown. */
    fun availableBytes(): Long? {
        if (!hasKnownSameVolume()) return null
        return runCatching { StatFs(mappedPublicRoot.absolutePath).availableBytes }.getOrNull()
    }

    private fun volumeUuid(): String? {
        if (!privateFilesDir.isDirectory || !mappedPublicRoot.isDirectory) return null
        val manager = storageManager ?: return null
        return runCatching {
            val privateUuid = manager.getUuidForPath(privateFilesDir)
            val publicUuid = manager.getUuidForPath(mappedPublicRoot)
            if (privateUuid == publicUuid) privateUuid.toString() else null
        }.getOrNull()
    }
}
