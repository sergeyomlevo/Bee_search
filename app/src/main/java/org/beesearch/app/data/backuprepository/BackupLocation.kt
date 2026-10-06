package org.beesearch.app.data.backuprepository

import java.io.File

/**
 * The one fixed public location of the Bee Search backup repository for one build variant.
 *
 * Product decision (S1): the user never chooses a backup folder. Bee Search owns exactly one
 * predictable location per build variant under the public Downloads collection, and the system
 * picker is used only to grant access to that already defined folder. There is deliberately no
 * custom backup folder setting, no "change folder" action, no SD-card selection and no second
 * repository.
 *
 * The path exists once here. [BackupDirectoryBootstrap] and the access coordinator both derive
 * from it, so no screen builds a backup path of its own.
 */
internal class BackupLocation(val variant: String) {
    init {
        require(variant in VARIANTS) { "Unsupported backup variant: $variant" }
    }

    /** `BeeSearch/Dev/Backup`, relative to the public Downloads collection. */
    val relativePath: String = "$ROOT_DIRECTORY_NAME/$variant/$BACKUP_DIRECTORY_NAME"

    /**
     * Storage-access-framework id of the exact expected tree,
     * e.g. `primary:Download/BeeSearch/Dev/Backup`.
     */
    val documentId: String = "primary:$PUBLIC_DOWNLOADS_DIRECTORY/$relativePath"

    /** The fixed backup directory inside the public Downloads collection. */
    fun rootDirectory(publicDownloads: File): File = File(publicDownloads, relativePath)

    /**
     * True only for the exact expected tree.
     *
     * The picker lets the user navigate anywhere, so a result for `Download` itself, the `BeeSearch`
     * root, the exchange folder, another variant, a nested folder or an arbitrary directory is not
     * this location and must never reach the repository.
     */
    fun accepts(pickedDocumentId: String): Boolean = pickedDocumentId == documentId

    internal companion object {
        val VARIANTS = setOf("Stable", "Beta", "Dev")

        /** The public collection the fixed backup folder lives in, as it appears on disk. */
        const val PUBLIC_DOWNLOADS_DIRECTORY = "Download"

        const val ROOT_DIRECTORY_NAME = "BeeSearch"
        const val BACKUP_DIRECTORY_NAME = "Backup"

        /** The only provider whose primary-volume tree this feature accepts. */
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}
