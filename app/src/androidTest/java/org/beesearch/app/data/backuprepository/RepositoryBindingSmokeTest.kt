package org.beesearch.app.data.backuprepository

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeTrue

/** Opt-in only; isolated install-state file and newly owned empty repositories, no app binding reset. */
class RepositoryBindingSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id get() = UUID.fromString(InstrumentationRegistry.getArguments().getString("bindingSmokeRunId")).toString()
    private val relative get() = "Download/BeeSearch/_poc/ProductionSlice1/$id/Backup"
    private val root get() = File(Environment.getExternalStorageDirectory(), relative)
    private val tree get() = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:$relative")
    private fun locator(name: String) = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:$relative/$name").toString()
    private val roots get() = AndroidRepositoryRoots(context, "Dev", Mutex())
    private val store by lazy {
        DataStoreRepositoryBindingStore(PreferenceDataStoreFactory.create(produceFile = {
            File(context.filesDir, "install-state/repository-binding-smoke-$id.preferences_pb")
        }), "Dev")
    }
    @Before fun safety() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("bindingSmokeRunId") != null)
        assertEquals("org.beesearch.app.dev", context.packageName)
        assertEquals("SM-S938B", Build.MODEL); assertEquals(36, Build.VERSION.SDK_INT)
    }
    @Test fun prepare() {
        assertFalse(root.exists())
        assertTrue(File(root, "RepositoryA").mkdirs())
        assertTrue(File(root, "RepositoryB").mkdirs())
        Log.i("RepositoryBindingSmoke", "PREPARED root=$root")
    }
    @Test fun bind(): Unit = runBlocking {
        val bound = BoundRepository(store, roots)
        assertEquals(RepositoryConnection.Unbound, bound.probe())
        val binding = bound.initializeNew(locator("RepositoryA")).valueOrThrow()
        val other = roots.resolve(locator("RepositoryB")).initializeNew().valueOrThrow()
        assertNotEquals(binding.expectedRepositoryId, other.repositoryId)
        assertEquals(RepositoryConnection.Bound(binding), bound.probe())
        Log.i("RepositoryBindingSmoke", "BOUND UUID=${binding.expectedRepositoryId} other=${other.repositoryId}")
    }
    @Test fun reconnectAndRecreate(): Unit = runBlocking {
        val bound = BoundRepository(store, roots)
        val original = store.read() ?: error("Expected durable binding after restart")
        assertEquals(RepositoryConnection.Bound(original), bound.probe())
        assertEquals(original, bound.reconnect(locator("RepositoryA")).valueOrThrow())
        val other = roots.resolve(locator("RepositoryB"))
        val before = other.inspectHeader().valueOrThrow()
        assertEquals(RepositoryResult.Failure(RepositoryError.ROOT_IDENTITY_MISMATCH), bound.reconnect(locator("RepositoryB")))
        assertEquals(original, store.read()); assertEquals(before, other.inspectHeader().valueOrThrow())
        assertEquals(original, bound.reconnect(locator("RepositoryA")).valueOrThrow())
        // Destructive characterization is limited to this new owned EMPTY synthetic root.
        val storage = SafRepositoryStorage(context, Uri.parse(locator("RepositoryA")), File(root, "RepositoryA"), context.filesDir)
        assertEquals(setOf("repository.json", "Media", "Snapshots", "Staging"), storage.list("").map { it.path }.toSet())
        assertTrue(storage.list("Media").isEmpty()); assertTrue(storage.list("Snapshots").isEmpty())
        assertTrue(storage.list("Staging").isEmpty())
        assertEquals(original.expectedRepositoryId, roots.resolve(original.rootLocator).inspectHeader().valueOrThrow().repositoryId)
        assertTrue(DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(original.rootLocator)))
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:$relative")
        assertNotNull(DocumentsContract.createDocument(context.contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, "RepositoryA"))
        assertEquals(RepositoryConnection.Failed(RepositoryError.BOUND_REPOSITORY_MISSING), bound.probe())
        assertEquals(RepositoryResult.Failure(RepositoryError.BINDING_CHANGED), bound.initializeNew(original.rootLocator))
        assertEquals(original, store.read())
        assertTrue(SafRepositoryStorage(context, Uri.parse(original.rootLocator), File(root, "RepositoryA"), context.filesDir).list("").isEmpty())
        Log.i("RepositoryBindingSmoke", "RESTART/MISMATCH/RETURN/RECREATED EMPTY FAIL-CLOSED PASS UUID=${original.expectedRepositoryId}")
    }
}
