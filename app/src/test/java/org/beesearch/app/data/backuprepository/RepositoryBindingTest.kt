package org.beesearch.app.data.backuprepository

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class TestBindingStore(initial: RepositoryBinding? = null) : RepositoryBindingStore {
    var value: RepositoryBinding? = initial
    var failReplace = false
    override suspend fun read(): RepositoryBinding? = value
    override suspend fun replace(expected: RepositoryBinding?, next: RepositoryBinding?) {
        if (failReplace) throw RepositoryException(RepositoryError.PROVIDER_FAILURE)
        if (value != expected) throw RepositoryException(RepositoryError.BINDING_CHANGED)
        value = next
    }
}

private class FirstReplaceGateStore : RepositoryBindingStore {
    var value: RepositoryBinding? = null
    val firstReplaceEntered = CompletableDeferred<Unit>()
    val releaseFirstReplace = CompletableDeferred<Unit>()
    private val gateFirstReplace = AtomicBoolean(true)

    override suspend fun read(): RepositoryBinding? = value

    override suspend fun replace(expected: RepositoryBinding?, next: RepositoryBinding?) {
        if (gateFirstReplace.compareAndSet(true, false)) {
            firstReplaceEntered.complete(Unit)
            releaseFirstReplace.await()
        }
        if (value != expected) throw RepositoryException(RepositoryError.BINDING_CHANGED)
        value = next
    }
}

private class TestRoots : RepositoryRootResolver {
    val foundations = mutableMapOf<String, RepositoryFoundation>()
    var calls = 0
    var resolverFailure: RepositoryError? = null
    override fun resolve(locator: String): RepositoryFoundation {
        calls++
        resolverFailure?.let { throw RepositoryException(it) }
        return foundations[locator] ?: throw RepositoryException(RepositoryError.NOT_FOUND)
    }
}

private data class TestRoot(val storage: MemoryRepositoryStorage, val foundation: RepositoryFoundation, val id: UUID)

private fun testRoot(variant: String = "Dev"): TestRoot = runBlocking {
    val storage = MemoryRepositoryStorage()
    val foundation = RepositoryFoundation(storage, variant)
    val header = (foundation.initialize() as RepositoryResult.Success).value
    TestRoot(storage, foundation, header.repositoryId)
}

private fun failure(result: RepositoryResult<*>, error: RepositoryError) {
    assertEquals(RepositoryResult.Failure(error), result)
}

private fun blobSource(bytes: ByteArray = byteArrayOf(1, 2, 3)): BlobSource = object : BlobSource {
    override val byteSize: Long = bytes.size.toLong()
    override val mimeHints: List<String?> = listOf("image/jpeg")
    override val expectedSha256: String? = null
    override fun open(): InputStream = ByteArrayInputStream(bytes)
    override fun checkUnchanged() = Unit
}

class RepositoryBindingTest {
    @Test fun devCannotAdoptBetaOrStableAndMalformedHeadersFailWithoutWrites() = runBlocking {
        for (variant in listOf("Beta", "Stable")) {
            val root = testRoot(variant)
            val roots = TestRoots().also { it.foundations["foreign"] = RepositoryFoundation(root.storage, "Dev") }
            val before = root.storage.files.mapValues { it.value.copyOf() }
            failure(BoundRepository(TestBindingStore(), roots).adoptExisting("foreign"), RepositoryError.ROOT_IDENTITY_MISMATCH)
            assertEquals(before.keys, root.storage.files.keys)
            before.forEach { (key, bytes) -> org.junit.Assert.assertArrayEquals(bytes, root.storage.files[key]) }
        }
        for ((header, error) in listOf("not-json" to RepositoryError.INVALID_HEADER,
            "{\"repositoryFormat\":\"beesearch-repository\",\"repositoryFormatVersion\":2,\"repositoryId\":\"${UUID.randomUUID()}\",\"variant\":\"Dev\"}" to RepositoryError.UNSUPPORTED_FORMAT)) {
            val root = testRoot(); root.storage.files["repository.json"] = header.toByteArray()
            val roots = TestRoots().also { it.foundations["A"] = root.foundation }
            val store = TestBindingStore()
            failure(BoundRepository(store, roots).adoptExisting("A"), error)
            assertNull(store.value); assertEquals(header, root.storage.files.getValue("repository.json").decodeToString())
        }
    }

    @Test fun boundEmptyReplacementNeverInitializesAndRootAbsenceIsTyped() = runBlocking {
        val storage = MemoryRepositoryStorage()
        val roots = TestRoots().also { it.foundations["A"] = RepositoryFoundation(storage, "Dev") }
        val binding = RepositoryBinding(UUID.randomUUID(), "A")
        val store = TestBindingStore(binding); val repo = BoundRepository(store, roots)
        assertEquals(RepositoryConnection.Failed(RepositoryError.BOUND_REPOSITORY_MISSING), repo.probe())
        failure(repo.initializeNew("A"), RepositoryError.BINDING_CHANGED)
        failure(repo.ingest(blobSource()), RepositoryError.BOUND_REPOSITORY_MISSING)
        assertTrue(storage.files.isEmpty()); assertEquals(binding, store.value)
        storage.directories.clear()
        assertEquals(RepositoryConnection.Failed(RepositoryError.BOUND_ROOT_UNAVAILABLE), repo.probe())
    }
    @Test fun explicitAdoptionIsRequiredAndNewInitializationRejectsExisting() = runBlocking {
        val root = testRoot(); val roots = TestRoots().also { it.foundations["A"] = root.foundation }
        val store = TestBindingStore(); val repo = BoundRepository(store, roots)
        assertTrue(repo.probe() is RepositoryConnection.Unbound)
        val adopted = repo.adoptExisting("A") as RepositoryResult.Success
        assertEquals(root.id, adopted.value.expectedRepositoryId)
        assertEquals(RepositoryResult.Failure(RepositoryError.BINDING_CHANGED), repo.initializeNew("A"))

        val existingUnboundRepo = BoundRepository(TestBindingStore(), roots)
        assertEquals(RepositoryResult.Failure(RepositoryError.AMBIGUOUS_REPOSITORY), existingUnboundRepo.initializeNew("A"))
    }

    @Test fun sameIdentityPathChangeReconnectsAndWrongIdentityCannotReconnect() = runBlocking {
        val root = testRoot(); val roots = TestRoots().also { it.foundations["old"] = root.foundation; it.foundations["new"] = root.foundation }
        val binding = RepositoryBinding(root.id, "old"); val store = TestBindingStore(binding); val repo = BoundRepository(store, roots)
        val changed = repo.reconnect("new") as RepositoryResult.Success
        assertEquals(RepositoryBinding(root.id, "new"), changed.value)
        val wrong = testRoot(); roots.foundations["wrong"] = wrong.foundation
        assertEquals(RepositoryResult.Failure(RepositoryError.ROOT_IDENTITY_MISMATCH), repo.reconnect("wrong"))
        assertEquals(changed.value, store.value)
    }

    @Test fun wrongUuidVariantInvalidUnsupportedAndMissingHeaderDoNotWriteBinding() = runBlocking {
        val good = testRoot(); val wrong = testRoot("Dev"); val beta = testRoot("Beta")
        val roots = TestRoots().apply { foundations["wrong"] = wrong.foundation; foundations["beta"] = RepositoryFoundation(beta.storage, "Dev") }
        val expected = RepositoryBinding(good.id, "good"); val store = TestBindingStore(expected); val repo = BoundRepository(store, roots)
        assertEquals(RepositoryResult.Failure(RepositoryError.ROOT_IDENTITY_MISMATCH), repo.reconnect("wrong"))
        assertEquals(RepositoryResult.Failure(RepositoryError.ROOT_IDENTITY_MISMATCH), repo.reconnect("beta"))
        roots.resolverFailure = RepositoryError.INVALID_HEADER
        assertEquals(RepositoryResult.Failure(RepositoryError.INVALID_HEADER), repo.reconnect("invalid"))
        roots.resolverFailure = RepositoryError.UNSUPPORTED_FORMAT
        assertEquals(RepositoryResult.Failure(RepositoryError.UNSUPPORTED_FORMAT), repo.reconnect("unsupported"))
        roots.resolverFailure = null
        roots.foundations["missing"] = RepositoryFoundation(MemoryRepositoryStorage(), "Dev")
        assertEquals(RepositoryResult.Failure(RepositoryError.BOUND_REPOSITORY_MISSING), repo.reconnect("missing"))
        assertEquals(expected, store.value)
    }

    @Test fun emptyRecreatedRootAndUnavailableRootAreDistinct() = runBlocking {
        val root = testRoot(); val binding = RepositoryBinding(root.id, "empty"); val store = TestBindingStore(binding); val roots = TestRoots()
        roots.foundations["empty"] = RepositoryFoundation(MemoryRepositoryStorage(), "Dev")
        val repo = BoundRepository(store, roots)
        assertEquals(RepositoryConnection.Failed(RepositoryError.BOUND_REPOSITORY_MISSING), repo.probe())
        roots.resolverFailure = RepositoryError.NOT_FOUND
        assertEquals(RepositoryConnection.Failed(RepositoryError.BOUND_ROOT_UNAVAILABLE), repo.probe())
        roots.resolverFailure = RepositoryError.PERMISSION_LOST
        assertEquals(RepositoryConnection.Failed(RepositoryError.PERMISSION_LOST), repo.probe())
        assertEquals(binding, store.value)
        roots.resolverFailure = RepositoryError.PROVIDER_FAILURE
        assertEquals(RepositoryConnection.Failed(RepositoryError.PROVIDER_FAILURE), repo.probe())
        assertEquals(binding, store.value)
    }

    @Test fun unboundIngestFailsBeforeRootResolution() = runBlocking {
        val roots = TestRoots(); val repo = BoundRepository(TestBindingStore(), roots)
        failure(repo.ingest(blobSource()), RepositoryError.UNBOUND)
        assertEquals(0, roots.calls)
    }

    @Test fun clearAndRebindRequireExplicitExpectedBinding() = runBlocking {
        val first = testRoot(); val second = testRoot(); val roots = TestRoots().apply { foundations["one"] = first.foundation; foundations["two"] = second.foundation }
        val original = RepositoryBinding(first.id, "one"); val store = TestBindingStore(original); val repo = BoundRepository(store, roots)
        assertEquals(RepositoryResult.Failure(RepositoryError.BINDING_CHANGED), repo.clear(RepositoryBinding(UUID.randomUUID(), "one")))
        assertEquals(original, store.value)
        val rebound = repo.rebind(original, "two", second.id) as RepositoryResult.Success
        assertEquals(RepositoryBinding(second.id, "two"), rebound.value)
        assertTrue(repo.clear(rebound.value) is RepositoryResult.Success)
        assertNull(store.value)
    }

    @Test fun persistenceFailureLeavesRepositoryCreatedButBindingUnbound() = runBlocking {
        val storage = MemoryRepositoryStorage(); val foundation = RepositoryFoundation(storage, "Dev"); val roots = TestRoots().also { it.foundations["new"] = foundation }
        val store = TestBindingStore().also { it.failReplace = true }; val repo = BoundRepository(store, roots)
        failure(repo.initializeNew("new"), RepositoryError.PROVIDER_FAILURE)
        assertNull(store.value)
        assertNotNull(storage.inspect("repository.json"))
    }

    @Test fun ingestUsesStoredIdentityAndMismatchBlocksWithoutWriting() = runBlocking {
        val good = testRoot(); val wrong = testRoot(); val roots = TestRoots().apply { foundations["bound"] = good.foundation }
        val binding = RepositoryBinding(good.id, "bound"); val store = TestBindingStore(binding); val repo = BoundRepository(store, roots)
        assertTrue(repo.ingest(blobSource()) is RepositoryResult.Success)
        roots.foundations["bound"] = wrong.foundation
        val before = wrong.storage.list("Media")
        failure(repo.ingest(blobSource()), RepositoryError.ROOT_IDENTITY_MISMATCH)
        assertEquals(before, wrong.storage.list("Media"))
    }

    @Test fun concurrentRebindAndClearHaveOneWinnerAndNoTornBinding() = runBlocking {
        val first = testRoot(); val second = testRoot()
        val roots = TestRoots().apply {
            foundations["first"] = first.foundation
            foundations["second"] = second.foundation
        }
        val original = RepositoryBinding(first.id, "first")
        val rebound = RepositoryBinding(second.id, "second")
        val store = TestBindingStore(original)
        val clearRepo = BoundRepository(store, roots)
        val rebindRepo = BoundRepository(store, roots)
        val results = coroutineScope {
            listOf(
                async { clearRepo.clear(original) },
                async { rebindRepo.rebind(original, "second", second.id) },
            ).awaitAll()
        }
        assertEquals(1, results.count { it is RepositoryResult.Success })
        assertEquals(1, results.count { it == RepositoryResult.Failure(RepositoryError.BINDING_CHANGED) })
        assertTrue(store.value == null || store.value == rebound)
    }

    @Test fun adoptAndCompetingInitializationUseDeterministicCompareAndReplace() = runBlocking {
        val existing = testRoot()
        val newStorage = MemoryRepositoryStorage()
        val newFoundation = RepositoryFoundation(newStorage, "Dev")
        val roots = TestRoots().apply {
            foundations["existing"] = existing.foundation
            foundations["new"] = newFoundation
        }
        val store = FirstReplaceGateStore()
        val adopter = BoundRepository(store, roots)
        val initializer = BoundRepository(store, roots)

        val adoption = async { adopter.adoptExisting("existing") }
        store.firstReplaceEntered.await()

        val initialization = async { initializer.initializeNew("new") }
        val initialized = initialization.await()
        assertTrue(initialized is RepositoryResult.Success)

        store.releaseFirstReplace.complete(Unit)
        val adopted = adoption.await()
        assertEquals(RepositoryResult.Failure(RepositoryError.BINDING_CHANGED), adopted)
        val finalBinding = store.value
        assertNotNull(finalBinding)
        assertEquals((initialized as RepositoryResult.Success).value, finalBinding)
        assertEquals(newFoundation.inspectHeader().valueOrThrow().repositoryId, finalBinding?.expectedRepositoryId)
    }
}
