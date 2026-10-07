package org.beesearch.app.data.backuprepository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import org.beesearch.app.BeeSearchApplication

/**
 * The one Android seam the host tests cannot cover: how a real picked SAF URI becomes a locator and
 * a document id.
 *
 * This is where a wrong field would silently make the exact-folder validation pass or fail for the
 * wrong folder, so it is asserted with real [DocumentsContract] URIs rather than hand-written ids.
 */
@RunWith(AndroidJUnit4::class)
class AndroidBackupTreeAccessDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val access = AndroidBackupTreeAccess(context)
    private val location = BackupLocation("Dev")

    @Test fun directoryIntentViewsTheExactBoundDocumentWithoutPickerOrPersistableGrant() {
        val intent = access.directoryViewIntent(treeUri().toString(), location)!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(DocumentsContract.Document.MIME_TYPE_DIR, intent.type)
        assertEquals(location.documentId, DocumentsContract.getDocumentId(intent.data!!))
        assertEquals(treeUri(), DocumentsContract.buildTreeDocumentUri(intent.data!!.authority,
            DocumentsContract.getTreeDocumentId(intent.data!!)))
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Test fun directoryIntentRefusesParentsForeignProvidersAndDocumentInsideTree() {
        assertEquals(null, access.directoryViewIntent(treeUri("primary:Download").toString(), location))
        assertEquals(null, access.directoryViewIntent("file:///storage/emulated/0/Download", location))
        assertEquals(null, access.directoryViewIntent(
            DocumentsContract.buildDocumentUriUsingTree(treeUri(), "primary:Download").toString(), location))
    }

    @Test fun unboundRepositoryDoesNotLaunchOrChangeBinding() = runBlocking {
        val repository = BoundRepository(object : RepositoryBindingStore {
            override suspend fun read(): RepositoryBinding? = null
            override suspend fun replace(expected: RepositoryBinding?, next: RepositoryBinding?) =
                error("opening must not replace binding")
        }, RepositoryRootResolver { error("no root when unbound") })
        assertFalse(access.openDirectory(repository, location) { error("no launch without access") })
    }

    @Test fun repeatedViewingProbesRealBindingWithoutChangingItOrPersistedPermissions() = runBlocking {
        val repository = (context as BeeSearchApplication).container.boundRepository
        val before = repository.probe()
        assertTrue("DEV must already have the owner's bound repository", before is RepositoryConnection.Bound)
        val permissions = context.contentResolver.persistedUriPermissions.map { it.uri to (it.isReadPermission to it.isWritePermission) }
        var launches = 0
        repeat(2) {
            assertTrue(access.openDirectory(repository, location) { intent ->
                launches++
                assertEquals(DocumentsContract.getTreeDocumentId(Uri.parse((before as RepositoryConnection.Bound).binding.rootLocator)),
                    DocumentsContract.getDocumentId(intent.data!!))
            })
        }
        assertEquals(2, launches)
        assertEquals(before, repository.probe())
        assertEquals(permissions, context.contentResolver.persistedUriPermissions.map { it.uri to (it.isReadPermission to it.isWritePermission) })
    }

    private fun treeUri(documentId: String = location.documentId) = DocumentsContract.buildTreeDocumentUri(
        BackupLocation.EXTERNAL_STORAGE_AUTHORITY,
        documentId,
    )

    @Test
    fun exactBackupTreeIsDescribedWithItsOwnDocumentId() {
        val uri = treeUri()
        val selection = access.describe(uri)

        assertTrue("a plain tree URI must be usable", selection is BackupTreeSelection.Picked)
        selection as BackupTreeSelection.Picked
        assertEquals(location.documentId, selection.treeDocumentId)
        assertEquals(uri.toString(), selection.locator)
        assertTrue(location.accepts(selection.treeDocumentId))
    }

    @Test
    fun detailUrisOfOtherFoldersDescribeTheirOwnFolder() {
        // A tree for a different folder must not be described as the fixed Backup folder.
        val exchange = access.describe(treeUri("primary:Download/BeeSearch/Dev/Exchange"))
        exchange as BackupTreeSelection.Picked
        assertEquals("primary:Download/BeeSearch/Dev/Exchange", exchange.treeDocumentId)
        assertEquals(false, location.accepts(exchange.treeDocumentId))

        val downloadsRoot = access.describe(treeUri("primary:Download"))
        downloadsRoot as BackupTreeSelection.Picked
        assertEquals(false, location.accepts(downloadsRoot.treeDocumentId))
    }

    @Test
    fun documentInsideTreeFormIsRejectedEvenWhenItsTreeSegmentIsTheFixedFolder() {
        // `…/tree/<fixed>/document/<other>`: the repository layer would honour the document segment,
        // so accepting it could validate one folder and then bind another. It must be rejected.
        val tree = treeUri()
        val detail = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:Download")

        assertEquals(BackupTreeSelection.Unsupported, access.describe(detail))
        // The tree URI itself stays usable.
        assertNotNull(access.describe(tree))
    }

    @Test
    fun documentOnlyUrisForeignProvidersAndNothingAtAllAreRejected() {
        assertEquals(
            BackupTreeSelection.Unsupported,
            access.describe(
                DocumentsContract.buildDocumentUri(
                    BackupLocation.EXTERNAL_STORAGE_AUTHORITY,
                    location.documentId,
                ),
            ),
        )
        assertEquals(
            BackupTreeSelection.Unsupported,
            access.describe(Uri.parse("content://com.example.other/tree/${location.documentId}")),
        )
        assertEquals(BackupTreeSelection.Unsupported, access.describe(null))
    }

    @Test
    fun pickerInitialUriPointsAtTheFixedBackupFolder() {
        val uri = access.pickerInitialUri(location)
        assertEquals(BackupLocation.EXTERNAL_STORAGE_AUTHORITY, uri.authority)
        assertEquals(location.documentId, DocumentsContract.getDocumentId(uri))
    }
}
