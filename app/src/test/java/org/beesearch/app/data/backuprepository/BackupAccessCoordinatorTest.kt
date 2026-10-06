package org.beesearch.app.data.backuprepository

import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** In-memory durable binding, with an injectable persistence failure. */
private class RecordingBindingStore(initial: RepositoryBinding? = null) : RepositoryBindingStore {
    var value: RepositoryBinding? = initial

    /** Null means "persistence works"; otherwise the exact failure production would raise. */
    var failure: RepositoryError? = null

    override suspend fun read(): RepositoryBinding? = value

    override suspend fun replace(expected: RepositoryBinding?, next: RepositoryBinding?) {
        failure?.let { throw RepositoryException(it) }
        if (value != expected) throw RepositoryException(RepositoryError.BINDING_CHANGED)
        value = next
    }
}

/** Locator → foundation map, so a test can point one locator at any repository. */
private class RecordingRoots : RepositoryRootResolver {
    val foundations = mutableMapOf<String, RepositoryFoundation>()
    var calls = 0
    var resolverFailure: RepositoryError? = null

    override fun resolve(locator: String): RepositoryFoundation {
        calls++
        resolverFailure?.let { throw RepositoryException(it) }
        return foundations[locator] ?: throw RepositoryException(RepositoryError.NOT_FOUND)
    }
}

private data class TestRepository(
    val storage: MemoryRepositoryStorage,
    val foundation: RepositoryFoundation,
    val id: UUID,
)

/**
 * Production access setup for the fixed Bee Search Backup folder.
 *
 * These are the S1 acceptance cases: an exact-folder grant decides initialize / adopt / reconnect
 * through the durable binding, and every other outcome fails closed without touching the repository
 * or the binding.
 */
class BackupAccessCoordinatorTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val location = BackupLocation("Dev")
    private val locator = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2F" +
        "BeeSearch%2FDev%2FBackup"
    private val picked = BackupTreeSelection.Picked(locator, location.documentId)

    private fun repository(variant: String = "Dev"): TestRepository = runBlocking {
        val storage = MemoryRepositoryStorage()
        val foundation = RepositoryFoundation(storage, variant)
        val header = (foundation.initialize() as RepositoryResult.Success).value
        TestRepository(storage, foundation, header.repositoryId)
    }

    private fun newCoordinator(store: RecordingBindingStore, roots: RecordingRoots) = BackupAccessCoordinator(
        bootstrap = BackupDirectoryBootstrap(temporary.root, location.variant),
        repository = BoundRepository(store, roots),
        location = location,
    )

    private fun skeleton() = File(temporary.root, location.relativePath)

    // 1. Unbound and no repository.json: the fixed empty skeleton gets a new identity.
    @Test
    fun unboundRepositoryWithoutHeaderIsInitialized() = runBlocking {
        val storage = MemoryRepositoryStorage()
        val roots = RecordingRoots().also { it.foundations[locator] = RepositoryFoundation(storage, "Dev") }
        val store = RecordingBindingStore()

        val outcome = newCoordinator(store, roots).connect(picked, persistGrant = { true })

        val ready = outcome as BackupAccessOutcome.Ready
        assertEquals(BackupAccessAction.INITIALIZED, ready.action)
        val header = RepositoryHeaderCodec.decode(storage.files.getValue("repository.json"))
        assertEquals(header.repositoryId, ready.repositoryId)
        assertEquals(RepositoryBinding(header.repositoryId, locator), store.value)
    }

    // 2. Unbound but repository.json exists (reinstall / lost binding): adopt, never recreate.
    @Test
    fun unboundRepositoryWithExistingHeaderIsAdopted() = runBlocking {
        val existing = repository()
        val headerBefore = existing.storage.files.getValue("repository.json").copyOf()
        val roots = RecordingRoots().also { it.foundations[locator] = existing.foundation }
        val store = RecordingBindingStore()

        val outcome = newCoordinator(store, roots).connect(picked, persistGrant = { true })

        val ready = outcome as BackupAccessOutcome.Ready
        assertEquals(BackupAccessAction.ADOPTED, ready.action)
        assertEquals(existing.id, ready.repositoryId)
        assertEquals(RepositoryBinding(existing.id, locator), store.value)
        assertArrayEquals(headerBefore, existing.storage.files.getValue("repository.json"))
    }

    // 3. Existing binding for the same UUID: reconnect, keep the identity.
    @Test
    fun existingBindingReconnectsToTheSameRepository() = runBlocking {
        val existing = repository()
        val previous = "content://com.android.externalstorage.documents/tree/primary%3Aold"
        val roots = RecordingRoots().also {
            it.foundations[previous] = existing.foundation
            it.foundations[locator] = existing.foundation
        }
        val store = RecordingBindingStore(RepositoryBinding(existing.id, previous))

        val outcome = newCoordinator(store, roots).connect(picked, persistGrant = { true })

        val ready = outcome as BackupAccessOutcome.Ready
        assertEquals(BackupAccessAction.RECONNECTED, ready.action)
        assertEquals(existing.id, ready.repositoryId)
        assertEquals(RepositoryBinding(existing.id, locator), store.value)
    }

    // 4. Another repository in the fixed folder: fail closed, binding untouched, no writes.
    @Test
    fun differentRepositoryInTheFolderFailsClosed() = runBlocking {
        val bound = repository()
        val foreign = repository()
        val previous = "content://com.android.externalstorage.documents/tree/primary%3Aold"
        val foreignHeader = foreign.storage.files.getValue("repository.json").copyOf()
        val roots = RecordingRoots().also {
            it.foundations[previous] = bound.foundation
            it.foundations[locator] = foreign.foundation
        }
        val binding = RepositoryBinding(bound.id, previous)
        val store = RecordingBindingStore(binding)

        val outcome = newCoordinator(store, roots).connect(picked, persistGrant = { true })

        assertEquals(BackupAccessOutcome.Failed(BackupAccessProblem.IDENTITY_MISMATCH), outcome)
        assertEquals(binding, store.value)
        assertArrayEquals(foreignHeader, foreign.storage.files.getValue("repository.json"))
        assertTrue(foreign.storage.list("Media").isEmpty())
    }

    // 5. Invalid header: fail closed, no adoption, no overwrite.
    @Test
    fun invalidHeaderFailsClosedWithoutWrites() = runBlocking {
        val storage = MemoryRepositoryStorage()
        storage.files["repository.json"] = "not-json".toByteArray()
        val roots = RecordingRoots().also { it.foundations[locator] = RepositoryFoundation(storage, "Dev") }
        val store = RecordingBindingStore()

        val outcome = newCoordinator(store, roots).connect(picked, persistGrant = { true })

        assertEquals(BackupAccessOutcome.Failed(BackupAccessProblem.REPOSITORY_NOT_RECOGNIZED), outcome)
        assertNull(store.value)
        assertEquals("not-json", storage.files.getValue("repository.json").decodeToString())
    }

    // A repository.json from another (newer) Bee Search version is not this variant's repository.
    @Test
    fun unsupportedRepositoryFormatFailsClosed() = runBlocking {
        val storage = MemoryRepositoryStorage()
        storage.files["repository.json"] = (
            "{\"repositoryFormat\":\"beesearch-repository\",\"repositoryFormatVersion\":2," +
                "\"repositoryId\":\"${UUID.randomUUID()}\",\"variant\":\"Dev\"}"
            ).toByteArray()
        val roots = RecordingRoots().also { it.foundations[locator] = RepositoryFoundation(storage, "Dev") }
        val store = RecordingBindingStore()

        val outcome = newCoordinator(store, roots).connect(picked, persistGrant = { true })

        assertEquals(BackupAccessOutcome.Failed(BackupAccessProblem.REPOSITORY_NOT_RECOGNIZED), outcome)
        assertNull(store.value)
    }

    // The fixed skeleton is prepared before the access probe, so a removed child directory is
    // repaired even when the probe then fails for an unrelated reason. Identity is untouched.
    @Test
    fun fixedSkeletonIsPreparedBeforeTheAccessProbe() = runBlocking {
        val existing = repository()
        val bootstrap = BackupDirectoryBootstrap(temporary.root, location.variant)
        assertTrue(bootstrap.ensure() is RepositoryResult.Success)
        val media = File(temporary.root, "${location.relativePath}/Media")
        assertTrue("the fixture starts with a real skeleton", media.isDirectory)
        assertTrue(media.delete())
        assertFalse(media.exists())

        val roots = RecordingRoots().also { it.foundations[locator] = existing.foundation }
        val store = RecordingBindingStore(RepositoryBinding(existing.id, locator))
        roots.resolverFailure = RepositoryError.PERMISSION_LOST
        val repositoryContentBefore = existing.storage.files.mapValues { it.value.copyOf() }
        val access = BackupAccessCoordinator(
            bootstrap = bootstrap,
            repository = BoundRepository(store, roots),
            location = location,
        )

        assertEquals(BackupAccessStatus.AccessLost(BackupAccessProblem.PERMISSION), access.status())

        assertTrue("the removed child directory is restored", media.isDirectory)
        assertEquals(repositoryContentBefore.keys, existing.storage.files.keys)
        repositoryContentBefore.forEach { (path, bytes) ->
            assertArrayEquals(bytes, existing.storage.files.getValue(path))
        }
        assertEquals(RepositoryBinding(existing.id, locator), store.value)
    }

    // Conflicting content in the fixed folder is a real, honestly reported failure.
    @Test
    fun conflictingContentInTheFixedFolderFailsClosedBeforeAnyRepositoryWork() = runBlocking {
        val storage = MemoryRepositoryStorage()
        val roots = RecordingRoots().also { it.foundations[locator] = RepositoryFoundation(storage, "Dev") }
        val store = RecordingBindingStore()
        // A file occupies the expected Backup directory itself.
        val blocked = File(temporary.root, location.relativePath).apply {
            parentFile?.mkdirs()
            writeText("not a folder")
        }

        val outcome = newCoordinator(store, roots).connect(picked, persistGrant = { true })

        assertEquals(BackupAccessOutcome.Failed(BackupAccessProblem.AMBIGUOUS_OR_INVALID), outcome)
        assertNull(store.value)
        assertEquals(0, roots.calls)
        assertEquals("not a folder", blocked.readText())
    }

    // 6. Ambiguous folder: no header but unexpected content, so no initialization.
    @Test
    fun ambiguousFolderIsNeverInitialized() = runBlocking {
        val storage = MemoryRepositoryStorage()
        storage.files["unexpected.txt"] = byteArrayOf(1, 2, 3)
        val roots = RecordingRoots().also { it.foundations[locator] = RepositoryFoundation(storage, "Dev") }
        val store = RecordingBindingStore()

        val outcome = newCoordinator(store, roots).connect(picked, persistGrant = { true })

        assertEquals(BackupAccessOutcome.Failed(BackupAccessProblem.AMBIGUOUS_OR_INVALID), outcome)
        assertNull(store.value)
        assertFalse(storage.files.containsKey("repository.json"))
        assertTrue(storage.files.containsKey("unexpected.txt"))
    }

    // 7. Lost permission: a recoverable state, and the durable binding survives it.
    @Test
    fun lostPermissionKeepsTheBindingAndAsksForAccessAgain() = runBlocking {
        val existing = repository()
        val previous = "content://com.android.externalstorage.documents/tree/primary%3Aold"
        val roots = RecordingRoots().also { it.foundations[previous] = existing.foundation }
        val binding = RepositoryBinding(existing.id, previous)
        val store = RecordingBindingStore(binding)
        val access = newCoordinator(store, roots)
        roots.resolverFailure = RepositoryError.PERMISSION_LOST

        assertEquals(BackupAccessStatus.AccessLost(BackupAccessProblem.PERMISSION), access.status())
        assertEquals(binding, store.value)

        assertEquals(
            BackupAccessOutcome.Failed(BackupAccessProblem.PERMISSION),
            access.connect(picked, persistGrant = { true }),
        )
        assertEquals(binding, store.value)
    }

    // 8. Persistence failure: the repository may exist, but the phone is not bound and is never
    // reported as ready. Both the provider-level and the production binding failure are covered.
    @Test
    fun bindingPersistenceFailureNeverReportsReady() = runBlocking {
        for ((failure, problem) in listOf(
            RepositoryError.PROVIDER_FAILURE to BackupAccessProblem.PROVIDER,
            RepositoryError.BINDING_PERSISTENCE_FAILED to BackupAccessProblem.BINDING,
        )) {
            val storage = MemoryRepositoryStorage()
            val roots = RecordingRoots().also { it.foundations[locator] = RepositoryFoundation(storage, "Dev") }
            val store = RecordingBindingStore().also { it.failure = failure }
            val access = newCoordinator(store, roots)

            assertEquals(
                BackupAccessOutcome.Failed(problem),
                access.connect(picked, persistGrant = { true }),
            )
            assertNull(store.value)
            assertNotNull(storage.inspect("repository.json"))
            assertEquals(BackupAccessStatus.GrantRequired, access.status())
        }
    }

    // 9. No usable tree (the picker was cancelled or returned nothing): nothing happens at all.
    @Test
    fun noTreeSelectionNeverTouchesRepositoryOrBinding() = runBlocking {
        val roots = RecordingRoots()
        val store = RecordingBindingStore()
        var grantRequested = false

        val outcome = newCoordinator(store, roots)
            .connect(BackupTreeSelection.Unsupported) { grantRequested = true; true }

        assertEquals(BackupAccessOutcome.WrongFolder, outcome)
        assertNull(store.value)
        assertFalse("no grant may be persisted for a selection that was not accepted", grantRequested)
        assertEquals(0, roots.calls)
        assertFalse(skeleton().exists())
    }

    // 10. Any neighbouring or foreign folder: rejected before any repository operation, and before a
    // durable grant is taken for a folder Bee Search does not use.
    @Test
    fun wrongFolderIsRejectedBeforeAnyRepositoryOperation() = runBlocking {
        val roots = RecordingRoots()
        val store = RecordingBindingStore()
        val access = newCoordinator(store, roots)
        var grantRequests = 0
        val neighbours = listOf(
            "primary:Download",
            "primary:Download/BeeSearch",
            "primary:Download/BeeSearch/Dev",
            "primary:Download/BeeSearch/Dev/Exchange",
            "primary:Download/BeeSearch/Dev/Backup/Media",
            "primary:Download/BeeSearch/Beta/Backup",
            "primary:Download/BeeSearch/Stable/Backup",
            "primary:Download/BeeSearch/Dev/Backup2",
        )

        neighbours.forEach { documentId ->
            assertEquals(
                "selection '$documentId' must not be used as the fixed folder",
                BackupAccessOutcome.WrongFolder,
                access.connect(BackupTreeSelection.Picked("locator-$documentId", documentId)) {
                    grantRequests++
                    true
                },
            )
        }

        assertEquals("a rejected selection must not persist any grant", 0, grantRequests)
        assertNull(store.value)
        assertEquals(0, roots.calls)
        assertFalse(skeleton().exists())
    }

    // The grant is asked for exactly once and only for the exact fixed folder.
    @Test
    fun grantIsPersistedOnlyForTheExactFixedFolder() = runBlocking {
        val storage = MemoryRepositoryStorage()
        val roots = RecordingRoots().also { it.foundations[locator] = RepositoryFoundation(storage, "Dev") }
        val store = RecordingBindingStore()
        var rejectedRequests = 0
        var acceptedRequests = 0

        newCoordinator(store, roots).connect(
            BackupTreeSelection.Picked("foreign", "primary:Download/BeeSearch/Dev/Exchange"),
        ) { rejectedRequests++; true }

        val outcome = newCoordinator(store, roots).connect(picked) { acceptedRequests++; true }

        assertEquals("the rejected folder must not persist a grant", 0, rejectedRequests)
        assertEquals(1, acceptedRequests)
        assertTrue(outcome is BackupAccessOutcome.Ready)
    }

    // A grant that could not be persisted would not survive a restart, so it never binds.
    @Test
    fun unpersistedGrantNeverBindsOrInitializes() = runBlocking {
        val storage = MemoryRepositoryStorage()
        val roots = RecordingRoots().also { it.foundations[locator] = RepositoryFoundation(storage, "Dev") }
        val store = RecordingBindingStore()

        val outcome = newCoordinator(store, roots).connect(picked, persistGrant = { false })

        assertEquals(BackupAccessOutcome.Failed(BackupAccessProblem.PERMISSION), outcome)
        assertNull(store.value)
        assertFalse(storage.files.containsKey("repository.json"))
        assertEquals(0, roots.calls)
    }

    // Read-only status: a reachable bound repository, and an unbound one that stays untouched.
    @Test
    fun statusReportsReadyAndNeverInitializesOnItsOwn() = runBlocking {
        val existing = repository()
        val boundRoots = RecordingRoots().also { it.foundations[locator] = existing.foundation }
        val boundStore = RecordingBindingStore(RepositoryBinding(existing.id, locator))
        assertEquals(
            BackupAccessStatus.Ready(existing.id),
            newCoordinator(boundStore, boundRoots).status(),
        )

        val storage = MemoryRepositoryStorage()
        val unboundRoots = RecordingRoots().also { it.foundations[locator] = RepositoryFoundation(storage, "Dev") }
        val unboundStore = RecordingBindingStore()
        assertEquals(
            BackupAccessStatus.GrantRequired,
            newCoordinator(unboundStore, unboundRoots).status(),
        )
        assertNull(unboundStore.value)
        assertFalse(storage.files.containsKey("repository.json"))
        assertTrue(skeleton().isDirectory)
    }

    // An access failure is never converted into absence.
    @Test
    fun unavailableRootIsAccessLostNotUnbound() = runBlocking {
        val binding = RepositoryBinding(UUID.randomUUID(), locator)
        val store = RecordingBindingStore(binding)

        assertEquals(
            BackupAccessStatus.AccessLost(BackupAccessProblem.PERMISSION),
            newCoordinator(store, RecordingRoots()).status(),
        )
        assertEquals(binding, store.value)
    }

    // A malformed durable binding is not "unbound": it must not be silently replaced, and it must
    // not turn into an accidental initialization of a new repository.
    @Test
    fun brokenBindingIsNotTreatedAsUnbound() = runBlocking {
        val storage = MemoryRepositoryStorage()
        val roots = RecordingRoots().also { it.foundations[locator] = RepositoryFoundation(storage, "Dev") }
        val store = object : RepositoryBindingStore {
            override suspend fun read(): RepositoryBinding? =
                throw RepositoryException(RepositoryError.BINDING_INVALID)

            override suspend fun replace(expected: RepositoryBinding?, next: RepositoryBinding?) = Unit
        }
        val access = BackupAccessCoordinator(
            bootstrap = BackupDirectoryBootstrap(temporary.root, location.variant),
            repository = BoundRepository(store, roots),
            location = location,
        )

        assertEquals(BackupAccessStatus.Failed(BackupAccessProblem.BINDING), access.status())
        assertEquals(
            BackupAccessOutcome.Failed(BackupAccessProblem.BINDING),
            access.connect(picked, persistGrant = { true }),
        )
        assertFalse(storage.files.containsKey("repository.json"))
    }
}
