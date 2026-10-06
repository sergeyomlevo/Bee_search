package org.beesearch.bindingaudit

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.beesearch.app.data.backuprepository.*

class AuditApplication : Application() {
    internal val bindingStore by lazy {
        DataStoreRepositoryBindingStore(repositoryBindingDataStore(this), "Dev")
    }
    internal val bound by lazy { BoundRepository(bindingStore, AndroidRepositoryRoots(this, "Dev", Mutex())) }
}

/** Test-only explicit commands; no automatic initialization, adoption or deletion. */
class AuditActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as AuditApplication
    private lateinit var tree: Uri
    private lateinit var command: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "org.beesearch.bindingaudit")
        val runId = UUID.fromString(intent.getStringExtra("runId")).toString()
        tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents",
            "primary:Download/BeeSearch/_poc/Slice2AAudit/$runId/Backup")
        command = intent.getStringExtra("command") ?: "probe"
        if (savedInstanceState != null) return
        scope.launch {
            try {
                val before = app.bound.probe()
                Log.i(TAG, "BEFORE $before")
                if (command == "probe") { finish(); return@launch }
                check(command == "initialize" || command == "adopt")
                check(before == RepositoryConnection.Unbound)
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
                        tree.authority!!, DocumentsContract.getTreeDocumentId(tree)))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }, 1)
            } catch (e: Exception) { Log.e(TAG, "FAIL $command", e); finish() }
        }
    }
    @Deprecated("Test-only result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK || data?.data != tree) {
            Log.e(TAG, "FAIL exact root selection required"); finish(); return
        }
        scope.launch {
            try {
                val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                check(data.flags and rw == rw)
                contentResolver.takePersistableUriPermission(tree, rw)
                val result = if (command == "initialize") app.bound.initializeNew(tree.toString())
                    else app.bound.adoptExisting(tree.toString())
                val binding = result.valueOrThrow()
                check(app.bound.probe() == RepositoryConnection.Bound(binding))
                if (command == "initialize") {
                    // New synthetic JPEG only; no real media read or delete.
                    val source = File(filesDir, "audit-source.jpg")
                    val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                    try { source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
                    finally { bitmap.recycle() }
                    val blob = app.bound.ingest(PrivateBlobSource(filesDir, source, listOf("image/jpeg"))).valueOrThrow()
                    val duplicate = app.bound.ingest(PrivateBlobSource(filesDir, source, listOf("image/jpeg"))).valueOrThrow()
                    check(duplicate.alreadyPresent && duplicate.sha256 == blob.sha256)
                    Log.i(TAG, "PUBLICATION PASS $blob duplicate=true")
                }
                Log.i(TAG, "PASS $command UUID=${binding.expectedRepositoryId} root=$tree")
            } catch (e: Exception) { Log.e(TAG, "FAIL $command", e) }
            finally { finish() }
        }
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    companion object { private const val TAG = "RepositoryBindingAudit" }
}
