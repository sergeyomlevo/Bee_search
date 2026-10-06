package org.beesearch.app.data.backuprepository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Android-only translation between a picked storage-access-framework tree and the fixed
 * [BackupLocation].
 *
 * Deliberately separate from [BackupAccessCoordinator]: the access state machine stays free of
 * Android types, so the whole decision tree is testable on the host. This class decides nothing
 * about the repository — it only describes what the user picked and records the grant.
 */
internal class AndroidBackupTreeAccess(private val context: Context) {

    /**
     * The initial location handed to the system picker, so it opens at the fixed Backup folder
     * instead of the last place the user browsed. The folder already exists because the startup
     * bootstrap materialises the skeleton; a picker that cannot resolve the hint simply opens at
     * its own default location, which the exact-folder validation still rejects.
     */
    fun pickerInitialUri(location: BackupLocation): Uri = DocumentsContract.buildDocumentUri(
        BackupLocation.EXTERNAL_STORAGE_AUTHORITY,
        location.documentId,
    )

    /**
     * Describes a picker result. Nothing here is a decision about the repository.
     *
     * Only a plain tree URI is accepted. The `…/tree/<expected>/document/<other>` form is rejected
     * even though its tree segment names the expected folder: the repository layer prefers the
     * *document* segment of such a URI, so accepting it could validate one folder and then bind
     * another. A picker is free to return more than it was asked for, so this stays strict.
     */
    fun describe(uri: Uri?): BackupTreeSelection {
        if (uri == null) return BackupTreeSelection.Unsupported
        if (uri.scheme != "content") return BackupTreeSelection.Unsupported
        if (uri.authority != BackupLocation.EXTERNAL_STORAGE_AUTHORITY) return BackupTreeSelection.Unsupported
        if (!DocumentsContract.isTreeUri(uri)) return BackupTreeSelection.Unsupported
        if (DocumentsContract.isDocumentUri(context, uri)) return BackupTreeSelection.Unsupported
        val documentId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: Exception) {
            null
        } ?: return BackupTreeSelection.Unsupported
        return BackupTreeSelection.Picked(locator = uri.toString(), treeDocumentId = documentId)
    }

    /**
     * Persists read and write access to the picked tree.
     *
     * A grant that is not persisted would work until the next restart and then fail, so the caller
     * treats `false` as a failed access setup rather than as a working one.
     */
    fun remember(uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    } catch (_: Exception) {
        // The platform refused to persist the grant (SecurityException when the result carried no
        // persistable permission). Fail closed instead of crashing the screen: without a persisted
        // grant the access would not survive a restart, so it is not treated as established.
        false
    }
}
