package org.beesearch.app.data.backuprepository

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

internal class MemoryRepositoryStorage : RepositoryStorage {
    val directories = mutableSetOf("")
    val files = mutableMapOf<String, ByteArray>()
    private val owned = mutableMapOf<UUID, String>()
    var available: Long? = Long.MAX_VALUE
    var fault: RepositoryError? = null
    var finalMutation: ((ByteArray) -> ByteArray)? = null
    var afterMoveError = false
    var beforeReader: ((String) -> Unit)? = null
    var afterWrite: ((Long) -> Unit)? = null
    var beforeList: ((String) -> Unit)? = null
    override fun ensureDirectory(path: String) {
        if (files.containsKey(path)) throw RepositoryException(RepositoryError.DIRECTORY_CONFLICT)
        directories += path
    }
    override fun inspect(path: String): RepositoryEntry? = when {
        path in directories -> RepositoryEntry(path, true, 0)
        path in files -> RepositoryEntry(path, false, files.getValue(path).size.toLong())
        else -> null
    }
    override fun list(path: String): List<RepositoryEntry> {
        beforeList?.invoke(path)
        return (directories + files.keys)
            .filter { it.isNotEmpty() && it.substringBeforeLast('/', "") == path }.mapNotNull(::inspect)
    }
    override fun createOwnedStage(operationId: UUID): String {
        val parent = "Staging/$operationId"
        check(parent !in directories)
        directories += parent
        return "$parent/candidate.part".also { files[it] = byteArrayOf(); owned[operationId] = it }
    }
    override fun openTruncatedWriter(path: String): OutputStream = object : ByteArrayOutputStream() {
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (fault == RepositoryError.WRITE_FAILED) throw RepositoryException(fault!!)
            super.write(b, off, len)
            afterWrite?.invoke(size().toLong())
        }
        override fun close() { files[path] = toByteArray(); super.close() }
    }
    override fun openReader(path: String): InputStream {
        beforeReader?.invoke(path)
        return ByteArrayInputStream(files[path] ?: throw RepositoryException(RepositoryError.NOT_FOUND))
    }
    override fun sync(path: String) { if (fault == RepositoryError.SYNC_FAILED) throw RepositoryException(fault!!) }
    override fun requirePublicationCapability() {
        if (fault == RepositoryError.UNSUPPORTED_PUBLICATION_PATH) throw RepositoryException(fault!!)
    }
    override fun moveOwnedStage(stage: String, target: String) {
        if (fault == RepositoryError.PUBLISH_FAILED) throw RepositoryException(fault!!)
        check(target !in files)
        files[target] = finalMutation?.invoke(files.getValue(stage)) ?: files.getValue(stage)
        files.remove(stage)
        if (afterMoveError) throw RepositoryException(RepositoryError.PUBLISH_FAILED)
    }
    override fun deleteOwnedStage(operationId: UUID) {
        owned.remove(operationId)?.let(files::remove)
        val parent = "Staging/$operationId"
        if (list(parent).isEmpty()) directories.remove(parent)
    }
    override fun availableBytes(): Long? = available
}

class RepositoryFoundationTest {
    private val bytes = ByteArray(256 * 1024) { (it * 31).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun source(hints: List<String?> = listOf("image/jpeg")) = object : BlobSource {
        override val byteSize = bytes.size.toLong()
        override val mimeHints = hints
        override val expectedSha256: String = sha
        override fun open(): InputStream = ByteArrayInputStream(bytes)
        override fun checkUnchanged() = Unit
    }
    private fun setup(): Triple<MemoryRepositoryStorage, RepositoryFoundation, UUID> = runBlocking {
        val storage = MemoryRepositoryStorage()
        val foundation = RepositoryFoundation(storage, "Dev")
        val header = (foundation.initialize() as RepositoryResult.Success).value
        Triple(storage, foundation, header.repositoryId)
    }
    private fun failure(result: RepositoryResult<*>, expected: RepositoryError) {
        assertEquals(RepositoryResult.Failure(expected), result)
    }

    @Test fun skeletonOnlyAndExplicitInitialization() = runBlocking {
        val s = MemoryRepositoryStorage(); val f = RepositoryFoundation(s, "Dev")
        assertTrue(f.bootstrap() is RepositoryResult.Success)
        assertEquals(setOf("", "Media", "Snapshots", "Staging"), s.directories)
        assertFalse(s.files.containsKey("repository.json"))
        val h = (f.initialize() as RepositoryResult.Success).value
        assertEquals(h, (f.initialize() as RepositoryResult.Success).value)
        assertTrue(RepositoryFoundation(s, "Dev").open(h.repositoryId) is RepositoryResult.Success)
    }
    @Test fun fileAtDirectoryFailsWithoutOverwrite() = runBlocking {
        val s = MemoryRepositoryStorage(); s.files["Media"] = bytes
        failure(RepositoryFoundation(s, "Dev").bootstrap(), RepositoryError.DIRECTORY_CONFLICT)
        assertArrayEquals(bytes, s.files["Media"])
    }
    @Test fun nonemptyMissingHeaderIsAmbiguous() = runBlocking {
        val s = MemoryRepositoryStorage(); s.files["unknown"] = bytes
        failure(RepositoryFoundation(s, "Dev").initialize(), RepositoryError.AMBIGUOUS_REPOSITORY)
        assertFalse(s.files.containsKey("repository.json"))
    }
    @Test fun foreignStagingBlocksInitialization() = runBlocking {
        val s = MemoryRepositoryStorage(); s.directories += "Staging/unknown"
        failure(RepositoryFoundation(s, "Dev").initialize(), RepositoryError.AMBIGUOUS_REPOSITORY)
    }
    @Test fun wrongIdentityAndVariantBlockWrites() = runBlocking {
        val (s, f, id) = setup()
        failure(f.ingest(UUID.randomUUID(), source()), RepositoryError.ROOT_IDENTITY_MISMATCH)
        failure(RepositoryFoundation(s, "Beta").open(id), RepositoryError.ROOT_IDENTITY_MISMATCH)
        assertTrue(s.list("Media").isEmpty())
    }
    @Test fun replacedRootIsNotAdopted() = runBlocking {
        val (s, f, id) = setup()
        s.files["repository.json"] = RepositoryHeaderCodec.encode(RepositoryHeader(UUID.randomUUID(), "Dev"))
        failure(f.ingest(id, source()), RepositoryError.ROOT_IDENTITY_MISMATCH)
    }
    @Test fun replacementAfterLastExistingLookupCannotPublishIntoB() = runBlocking {
        val (s, f, id) = setup()
        var mediaListings = 0
        val replacement = RepositoryHeader(UUID.randomUUID(), "Dev")
        s.beforeList = { path ->
            if (path == "Media" && ++mediaListings == 2) {
                s.files["repository.json"] = RepositoryHeaderCodec.encode(replacement)
            }
        }
        failure(f.ingest(id, source()), RepositoryError.ROOT_IDENTITY_MISMATCH)
        assertEquals(2, mediaListings)
        assertTrue(s.list("Media").isEmpty())
        assertEquals(replacement, RepositoryHeaderCodec.decode(s.files.getValue("repository.json")))
        // Identity changed: even owned staging must not be cleaned in the replacement root.
        assertTrue(s.files.keys.any { it.startsWith("Staging/") })
    }
    @Test fun publicationAndDuplicatePreserveRecognizedExtension() = runBlocking {
        val (s, f, id) = setup()
        val blob = (f.ingest(id, source()) as RepositoryResult.Success).value
        assertEquals("Media/$sha.jpg", blob.path); assertFalse(blob.alreadyPresent)
        val second = (f.ingest(id, source(listOf("application/octet-stream"))) as RepositoryResult.Success).value
        assertTrue(second.alreadyPresent); assertEquals("jpg", second.extension)
        assertEquals(1, s.list("Media").size); assertTrue(s.list("Staging").isEmpty())
    }
    @Test fun existingBinRemainsImmutable() = runBlocking {
        val (_, f, id) = setup()
        assertEquals("bin", (f.ingest(id, source(listOf(null))) as RepositoryResult.Success).value.extension)
        assertEquals("bin", (f.ingest(id, source()) as RepositoryResult.Success).value.extension)
    }
    @Test fun newConflictingHintsFail() = runBlocking {
        val (s, f, id) = setup()
        failure(f.ingest(id, source(listOf("image/jpeg", "video/mp4"))), RepositoryError.METADATA_INCONSISTENCY)
        assertTrue(s.list("Media").isEmpty())
    }
    @Test fun duplicatePlacementsFailWithoutDeletion() = runBlocking {
        val (s, f, id) = setup(); s.files["Media/$sha.jpg"] = bytes; s.files["Media/$sha.bin"] = bytes
        failure(f.ingest(id, source()), RepositoryError.METADATA_INCONSISTENCY)
        assertEquals(2, s.list("Media").size)
    }
    @Test fun existingCorruptAndTruncatedTargetsNeverOverwrite() = runBlocking {
        for (bad in listOf(bytes.copyOf(12), bytes.copyOf().also { it[0]++ })) {
            val (s, f, id) = setup(); s.files["Media/$sha.jpg"] = bad
            failure(f.ingest(id, source()), RepositoryError.VERIFY_FAILED)
            assertArrayEquals(bad, s.files["Media/$sha.jpg"])
        }
    }
    @Test fun failuresLeaveCanonicalUntouchedAndUnknownStagingPreserved() = runBlocking {
        for (error in listOf(RepositoryError.WRITE_FAILED, RepositoryError.SYNC_FAILED,
            RepositoryError.PUBLISH_FAILED, RepositoryError.UNSUPPORTED_PUBLICATION_PATH)) {
            val (s, f, id) = setup(); s.fault = error
            s.files["Staging/unknown.part"] = bytes
            failure(f.ingest(id, source()), error)
            assertTrue(s.list("Media").isEmpty())
            assertArrayEquals(bytes, s.files["Staging/unknown.part"])
        }
    }
    @Test fun finalReadbackRejectsSizeAndSameSizeHashMismatch() = runBlocking {
        for (mutate in listOf<(ByteArray) -> ByteArray>({ it.copyOf(12) }, { it.copyOf().also { b -> b[0]++ } })) {
            val (s, f, id) = setup(); s.finalMutation = mutate
            failure(f.ingest(id, source()), RepositoryError.VERIFY_FAILED)
            assertEquals(1, s.list("Media").size) // evidence retained; not reported committed
        }
    }
    @Test fun moveResponseFailureRequiresActualFinalVerification() = runBlocking {
        val (s, f, id) = setup(); s.afterMoveError = true
        assertTrue(f.ingest(id, source()) is RepositoryResult.Success)
    }
    @Test fun stagingOnlyIsNotCommittedAndValidFinalSurvives() = runBlocking {
        val (s, f, id) = setup(); s.files["Staging/foreign.partial"] = bytes.copyOf(12)
        s.files["Media/$sha.jpg"] = bytes
        assertTrue((f.ingest(id, source()) as RepositoryResult.Success).value.alreadyPresent)
        assertTrue("Staging/foreign.partial" in s.files)
    }
    @Test fun stagingPlusCorruptFinalStillFails() = runBlocking {
        val (s, f, id) = setup(); s.files["Staging/foreign.partial"] = bytes; s.files["Media/$sha.jpg"] = bytes.copyOf(12)
        failure(f.ingest(id, source()), RepositoryError.VERIFY_FAILED)
        assertTrue("Staging/foreign.partial" in s.files)
    }
    @Test fun sourceShortLongAndMutationFailClosed() = runBlocking {
        for (changed in listOf(byteArrayOf(), bytes.copyOf(bytes.size + 1), bytes.copyOf().also { it[0]++ })) {
            val (s, f, id) = setup(); var opens = 0
            val changing = object : BlobSource by source() {
                override fun open(): InputStream = ByteArrayInputStream(if (++opens == 1) bytes else changed)
            }
            failure(f.ingest(id, changing), RepositoryError.SOURCE_CHANGED)
            assertTrue(s.list("Media").isEmpty()); assertTrue(s.list("Staging").isEmpty())
        }
    }
    @Test fun disappearedSourceFailsClosed() = runBlocking {
        val (s, f, id) = setup()
        val missing = object : BlobSource by source() {
            override fun open(): InputStream = throw java.io.FileNotFoundException()
        }
        failure(f.ingest(id, missing), RepositoryError.SOURCE_CHANGED); assertTrue(s.list("Media").isEmpty())
    }
    @Test fun sourceDisappearsAfterFirstPass() = runBlocking {
        val (s, f, id) = setup(); var opens = 0
        val missing = object : BlobSource by source() {
            override fun open(): InputStream {
                if (++opens > 1) throw java.io.FileNotFoundException()
                return ByteArrayInputStream(bytes)
            }
        }
        failure(f.ingest(id, missing), RepositoryError.SOURCE_CHANGED)
        assertTrue(s.list("Media").isEmpty()); assertTrue(s.list("Staging").isEmpty())
    }
    @Test fun explicitCancellationNeverPublishes() = runBlocking {
        val (s, f, id) = setup(); var calls = 0
        failure(f.ingest(id, source()) { ++calls > 5 }, RepositoryError.CANCELLED)
        assertTrue(s.list("Media").isEmpty()); assertTrue(s.list("Staging").isEmpty())
    }
    @Test fun capacityUnknownAndInsufficientRejectBeforeStaging() = runBlocking {
        for (available in listOf(null, 1L)) {
            val (s, f, id) = setup(); s.available = available
            failure(f.ingest(id, source()), if (available == null) RepositoryError.CAPACITY_UNKNOWN else RepositoryError.CAPACITY_INSUFFICIENT)
            assertTrue(s.list("Staging").isEmpty())
        }
    }
    @Test fun capacityDropAfterWriteBlocksPublication() = runBlocking {
        val (s, f, id) = setup()
        s.beforeReader = { if (it.startsWith("Staging/")) s.available = 1 }
        failure(f.ingest(id, source()), RepositoryError.CAPACITY_INSUFFICIENT)
        assertTrue(s.list("Media").isEmpty()); assertTrue(s.list("Staging").isEmpty())
    }

    @Test fun capacityDropDuringLongWritePreservesCommittedData() = runBlocking {
        val (s, f, id) = setup()
        s.files["Media/previous.bin"] = bytes
        val large = ByteArray(5 * 1024 * 1024) { it.toByte() }
        val input = object : BlobSource {
            override val byteSize = large.size.toLong()
            override val mimeHints = listOf<String?>(null)
            override val expectedSha256: String? = null
            override fun open() = ByteArrayInputStream(large)
            override fun checkUnchanged() = Unit
        }
        s.afterWrite = { if (it >= 4 * 1024 * 1024) s.available = 1 }
        failure(f.ingest(id, input), RepositoryError.CAPACITY_INSUFFICIENT)
        assertEquals(1, s.list("Media").size)
        assertArrayEquals(bytes, s.files["Media/previous.bin"])
        assertTrue(s.list("Staging").isEmpty())
    }
    @Test fun unreadableExistingBlobIsNotMissingOrVerified() = runBlocking {
        val (s, f, id) = setup(); s.files["Media/$sha.jpg"] = bytes
        s.beforeReader = { if (it.startsWith("Media/")) throw RepositoryException(RepositoryError.PERMISSION_LOST) }
        failure(f.ingest(id, source()), RepositoryError.PERMISSION_LOST)
        assertEquals(1, s.list("Media").size)
    }
    @Test fun rootIdentityChangeNeverAuthorizesStagingDelete() = runBlocking {
        val (s, f, id) = setup()
        s.afterWrite = {
            s.files["repository.json"] = RepositoryHeaderCodec.encode(RepositoryHeader(UUID.randomUUID(), "Dev"))
        }
        failure(f.ingest(id, source()), RepositoryError.ROOT_IDENTITY_MISMATCH)
        assertTrue(s.list("Media").isEmpty())
        assertFalse(s.list("Staging").isEmpty()) // ownership cannot cross a changed repository UUID
    }
    @Test fun independentSha256VectorAndFlatPath() = runBlocking {
        val (_, f, id) = setup()
        val abc = object : BlobSource {
            override val byteSize = 3L
            override val mimeHints = listOf<String?>(null)
            override val expectedSha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            override fun open() = ByteArrayInputStream("abc".toByteArray())
            override fun checkUnchanged() = Unit
        }
        val result = (f.ingest(id, abc) as RepositoryResult.Success).value
        assertEquals("Media/${abc.expectedSha256}.bin", result.path)
    }
    @Test fun noncanonicalCaseAliasBlocksSecondPlacement() = runBlocking {
        val (s, f, id) = setup(); s.files["Media/${sha.uppercase()}.jpg"] = bytes
        failure(f.ingest(id, source()), RepositoryError.METADATA_INCONSISTENCY)
        assertEquals(1, s.list("Media").size)
    }
    @Test fun foreignPartialStagingNeverSatisfiesOrGetsDeletedByIngest() = runBlocking {
        val (s, f, id) = setup()
        s.directories += "Staging/unknown"
        s.files["Staging/unknown/$sha.part"] = bytes.copyOf(12)
        assertFalse((f.ingest(id, source()) as RepositoryResult.Success).value.alreadyPresent)
        assertEquals(12, s.files.getValue("Staging/unknown/$sha.part").size)
        assertEquals(1, s.list("Media").size)
    }
}
