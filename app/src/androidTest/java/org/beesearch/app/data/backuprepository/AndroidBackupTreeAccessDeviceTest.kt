package org.beesearch.app.data.backuprepository

import android.content.Context
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
