package org.beesearch.app.data.backuprepository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.sync.Mutex
import org.beesearch.app.data.local.settings.INSTALL_STATE_DIRECTORY

/** AppContainer creates exactly one store per application process. Never portable/Auto Backup state. */
internal fun repositoryBindingDataStore(context: Context) = PreferenceDataStoreFactory.create(
    produceFile = { File(context.filesDir, "$INSTALL_STATE_DIRECTORY/repository_binding.preferences_pb") },
)

/** Locator resolves storage only; header UUID/variant are checked independently by the binding gate. */
internal class AndroidRepositoryRoots(
    private val context: Context,
    private val variant: String,
    private val maintenance: Mutex,
) : RepositoryRootResolver {
    override fun resolve(locator: String): RepositoryFoundation {
        try {
            val uri = Uri.parse(locator)
            if (uri.scheme != "content" || uri.authority != "com.android.externalstorage.documents" ||
                !DocumentsContract.isTreeUri(uri)) throw RepositoryException(RepositoryError.UNSUPPORTED_PUBLICATION_PATH)
            val id = if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri)
                else DocumentsContract.getTreeDocumentId(uri)
            if (!id.startsWith("primary:")) throw RepositoryException(RepositoryError.UNSUPPORTED_PUBLICATION_PATH)
            val relative = id.removePrefix("primary:")
            if (relative.isEmpty() || relative.split('/').any { it.isEmpty() || it == "." || it == ".." || '\\' in it }) {
                throw RepositoryException(RepositoryError.UNSUPPORTED_PUBLICATION_PATH)
            }
            val mapped = File(Environment.getExternalStorageDirectory(), relative)
            return RepositoryFoundation(SafRepositoryStorage(context, uri, mapped, context.filesDir), variant,
                maintenance = maintenance)
        } catch (e: RepositoryException) { throw e }
        catch (e: SecurityException) { throw RepositoryException(RepositoryError.PERMISSION_LOST, e) }
        catch (e: Exception) { throw RepositoryException(RepositoryError.PROVIDER_FAILURE, e) }
    }
}
