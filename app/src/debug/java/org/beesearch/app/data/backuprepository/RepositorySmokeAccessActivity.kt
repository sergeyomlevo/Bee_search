package org.beesearch.app.data.backuprepository

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import java.util.UUID

/** DEV-only owner-operated permission setup. Never part of Beta/Stable builds. */
class RepositorySmokeAccessActivity : Activity() {
    private fun expectedTree(): Uri {
        check(packageName == "org.beesearch.app.dev")
        val id = UUID.fromString(intent.getStringExtra("runId")).toString()
        return DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents",
            "primary:Download/BeeSearch/_poc/ProductionSlice1/$id/Backup")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tree = try { expectedTree() } catch (_: IllegalArgumentException) { finish(); return }
            catch (_: NullPointerException) { finish(); return }
        if (savedInstanceState == null) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
                    tree.authority!!, DocumentsContract.getTreeDocumentId(tree)))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, 1)
        }
    }

    @Deprecated("Debug-only activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK && data?.data == expectedTree()) {
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            check(flags == (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            contentResolver.takePersistableUriPermission(expectedTree(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        finish()
    }
}
