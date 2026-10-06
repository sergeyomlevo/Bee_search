package org.beesearch.app.data.backuprepository

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreRepositoryBindingStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val binding = RepositoryBinding(UUID.randomUUID(), "content://provider/tree/one")
    private suspend fun <T> disk(file: File, block: suspend (DataStore<Preferences>) -> T): T {
        val job = SupervisorJob()
        // Android FileStorage uses File.renameTo, which cannot replace on Windows.
        // Exercise real disk persistence with the existing cross-platform backend;
        // Android FileStorage is independently exercised by the device smoke.
        val store = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.absolutePath.toPath() },
            scope = CoroutineScope(Dispatchers.IO + job),
        )
        return try { block(store) } finally { job.cancelAndJoin() }
    }
    @Test fun persistedPairSurvivesReopenAndUpdateStyleReopen() = runBlocking {
        val file = File(temp.root, "binding.preferences_pb")
        disk(file) { ds ->
            val store = DataStoreRepositoryBindingStore(ds, "Dev")
            assertNull(store.read()); store.replace(null, binding); assertEquals(binding, store.read())
        }
        repeat(2) { disk(file) { assertEquals(binding, DataStoreRepositoryBindingStore(it, "Dev").read()) } }
    }
    @Test fun compareReplaceClearAndNamespaces() = runBlocking {
        disk(File(temp.root, "binding.preferences_pb")) { ds ->
            val dev = DataStoreRepositoryBindingStore(ds, "Dev")
            val beta = DataStoreRepositoryBindingStore(ds, "Beta")
            val stable = DataStoreRepositoryBindingStore(ds, "Stable")
            dev.replace(null, binding); assertNull(beta.read()); assertNull(stable.read())
            try { dev.replace(null, binding.copy(rootLocator = "other")); fail() }
            catch (e: RepositoryException) { assertEquals(RepositoryError.BINDING_CHANGED, e.error) }
            assertEquals(binding, dev.read())
            val next = binding.copy(rootLocator = "content://provider/tree/two")
            dev.replace(binding, next); assertEquals(next, dev.read())
            beta.replace(null, binding); dev.replace(next, null)
            assertNull(dev.read()); assertEquals(binding, beta.read())
        }
    }
    @Test fun partialMalformedPairFailsClosedWithoutOverwrite() = runBlocking {
        disk(File(temp.root, "binding.preferences_pb")) { ds ->
            val key = stringPreferencesKey("expected_repository_uuid_Dev")
            val locator = stringPreferencesKey("repository_root_locator_Dev")
            val store = DataStoreRepositoryBindingStore(ds, "Dev")
            for (id in listOf(binding.expectedRepositoryId.toString(), "invalid")) {
                ds.edit { it[key] = id; it.remove(locator) }
                try { store.read(); fail() } catch (e: RepositoryException) { assertEquals(RepositoryError.BINDING_INVALID, e.error) }
                try { store.replace(null, binding); fail() } catch (e: RepositoryException) { assertEquals(RepositoryError.BINDING_INVALID, e.error) }
                ds.edit { it[locator] = " " }
                try { store.read(); fail() } catch (e: RepositoryException) { assertEquals(RepositoryError.BINDING_INVALID, e.error) }
            }
        }
    }
    @Test fun unreadablePersistenceIsNotUnbound() = runBlocking {
        val failed = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("synthetic read error") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("synthetic write error")
        }
        val store = DataStoreRepositoryBindingStore(failed, "Dev")
        try { store.read(); fail() } catch (e: RepositoryException) { assertEquals(RepositoryError.BINDING_PERSISTENCE_FAILED, e.error) }
        try { store.replace(null, binding); fail() } catch (e: RepositoryException) { assertEquals(RepositoryError.BINDING_PERSISTENCE_FAILED, e.error) }
    }

    @Test fun corruptPersistedBytesFailClosedAsPersistenceError() = runBlocking {
        val file = File(temp.root, "corrupt.preferences_pb")
        file.writeBytes(byteArrayOf(0x7f, 0x00, 0x13, 0x55, 0x01))
        disk(file) { ds ->
            val store = DataStoreRepositoryBindingStore(ds, "Dev")
            try {
                store.read()
                fail("corrupt DataStore bytes must not become UNBOUND")
            } catch (e: RepositoryException) {
                assertEquals(RepositoryError.BINDING_PERSISTENCE_FAILED, e.error)
            }
        }
    }

    @Test fun concurrentInitialBindsHaveOneWinnerAndNeverTornPair() = runBlocking {
        disk(File(temp.root, "concurrent-bind.preferences_pb")) { ds ->
            val store = DataStoreRepositoryBindingStore(ds, "Dev")
            val first = binding.copy(rootLocator = "content://provider/tree/first")
            val second = binding.copy(expectedRepositoryId = UUID.randomUUID(), rootLocator = "content://provider/tree/second")
            val results = coroutineScope {
                listOf(first, second).map { candidate ->
                    async(Dispatchers.Default) {
                        runCatching { store.replace(null, candidate) }
                    }
                }.awaitAll()
            }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.exceptionOrNull() is RepositoryException && (it.exceptionOrNull() as RepositoryException).error == RepositoryError.BINDING_CHANGED })
            assertTrue(store.read() == first || store.read() == second)
        }
    }

    @Test fun concurrentClearAndRebindHaveOneWinnerAndNeverTornPair() = runBlocking {
        disk(File(temp.root, "concurrent-update.preferences_pb")) { ds ->
            val store = DataStoreRepositoryBindingStore(ds, "Dev")
            val original = binding.copy(rootLocator = "content://provider/tree/original")
            val rebound = original.copy(rootLocator = "content://provider/tree/rebound")
            store.replace(null, original)
            val results = coroutineScope {
                listOf(
                    async(Dispatchers.Default) { runCatching { store.replace(original, null) } },
                    async(Dispatchers.Default) { runCatching { store.replace(original, rebound) } },
                ).awaitAll()
            }
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, results.count { it.exceptionOrNull() is RepositoryException && (it.exceptionOrNull() as RepositoryException).error == RepositoryError.BINDING_CHANGED })
            assertTrue(store.read() == null || store.read() == rebound)
        }
    }
}
