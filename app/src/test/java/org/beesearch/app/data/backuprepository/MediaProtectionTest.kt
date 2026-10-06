package org.beesearch.app.data.backuprepository

import java.io.File
import java.security.MessageDigest
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

private const val SHA_ONE = "1111111111111111111111111111111111111111111111111111111111111111"
private const val SHA_TWO = "2222222222222222222222222222222222222222222222222222222222222222"

private class ProtectionBindingStore(initial: RepositoryBinding? = null) : RepositoryBindingStore {
    var value: RepositoryBinding? = initial
    var failReplace = false

    override suspend fun read(): RepositoryBinding? = value

    override suspend fun replace(expected: RepositoryBinding?, next: RepositoryBinding?) {
        if (failReplace) throw RepositoryException(RepositoryError.BINDING_PERSISTENCE_FAILED)
        if (value != expected) throw RepositoryException(RepositoryError.BINDING_CHANGED)
        value = next
    }
}

private class ProtectionRoots(private val foundation: RepositoryFoundation) : RepositoryRootResolver {
    var failure: RepositoryError? = null

    override fun resolve(locator: String): RepositoryFoundation {
        failure?.let { throw RepositoryException(it) }
        return foundation
    }
}

/** Only files this map knows about are usable sources; anything else is an unusable captured path. */
private class MapSourceResolver(private val files: Map<String, File>) : MediaSourceResolver {
    override fun resolve(row: CapturedMediaRow): File? = files[row.relativePath]
}

/**
 * Media protection: what actually gets copied into the repository, what each required blob reports,
 * and what a partial run means.
 */
class MediaProtectionTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val storage = MemoryRepositoryStorage()
    private val foundation = RepositoryFoundation(storage, "Dev")
    private val locator = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FBeeSearch%2FDev%2FBackup"
    private lateinit var binding: RepositoryBinding
    private val roots = ProtectionRoots(foundation)
    private val bindingStore = ProtectionBindingStore()

    private fun repository(): BoundRepository = runBlocking {
        val header = (foundation.initialize() as RepositoryResult.Success).value
        binding = RepositoryBinding(header.repositoryId, locator)
        bindingStore.value = binding
        BoundRepository(bindingStore, roots)
    }

    private fun shaOf(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** A private original under the trusted root, exactly as a managed store would place it. */
    private fun privateOriginal(relativePath: String, bytes: ByteArray): File {
        val file = File(temporary.root, relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    private fun objectRow(
        relativePath: String,
        sha: String?,
        size: Long?,
        mime: String? = "image/jpeg",
        id: UUID = UUID.randomUUID(),
    ) = CapturedMediaRow(
        kind = MediaSourceKind.PHYSICAL_OBJECT_MEDIA,
        recordId = id,
        ownerId = UUID.randomUUID(),
        relativePath = relativePath,
        sha256 = sha,
        byteSize = size,
        mimeType = mime,
    )

    private fun attachmentRow(
        relativePath: String,
        sha: String?,
        size: Long?,
        mime: String? = "image/jpeg",
        id: UUID = UUID.randomUUID(),
    ) = CapturedMediaRow(
        kind = MediaSourceKind.OBSERVATION_POINT_ATTACHMENT,
        recordId = id,
        ownerId = UUID.randomUUID(),
        relativePath = relativePath,
        sha256 = sha,
        byteSize = size,
        mimeType = mime,
    )

    private fun service(files: Map<String, File>) = MediaProtectionService(
        repository = repository(),
        sources = MapSourceResolver(files),
        privateRoot = temporary.root,
    )

    /** The same service with a recorder in front of the repository, so publication attempts are visible. */
    private fun recordingService(files: Map<String, File>, attempted: MutableList<Long>): MediaProtectionService {
        val bound = repository()
        return MediaProtectionService(bound, MapSourceResolver(files), temporary.root) { source, cancelled ->
            attempted += source.byteSize
            bound.ingest(source, cancelled)
        }
    }

    private fun mediaEntries() = storage.list("Media").map { it.path }.sorted()

    // --- required set to real bytes ---

    @Test
    fun everyRequiredBlobIsPublishedUnderItsCanonicalPath() = runBlocking {
        val objectBytes = ByteArray(64) { it.toByte() }
        val attachmentBytes = ByteArray(128) { (it * 3).toByte() }
        val objectFile = privateOriginal("physical-object-media/object/one", objectBytes)
        val attachmentFile = privateOriginal("observation-attachments/point/two", attachmentBytes)
        val shaObject = shaOf(objectBytes)
        val shaAttachment = shaOf(attachmentBytes)

        val report = service(
            mapOf(
                "physical-object-media/object/one" to objectFile,
                "observation-attachments/point/two" to attachmentFile,
            ),
        ).protect(
            CapturedMediaState(
                listOf(
                    objectRow("physical-object-media/object/one", shaObject, objectBytes.size.toLong()),
                    attachmentRow("observation-attachments/point/two", shaAttachment, attachmentBytes.size.toLong()),
                ),
            ),
        )

        assertEquals(MediaProtectionSummary.ALL_PROTECTED, report.summary)
        assertEquals(2, report.requiredCount)
        assertEquals(2, report.protectedCount)
        assertTrue(report.results.all { it.outcome == MediaProtectionOutcome.INGESTED })
        assertEquals(listOf("Media/$shaAttachment.jpg", "Media/$shaObject.jpg"), mediaEntries())
        assertArrayEquals(objectBytes, storage.files.getValue("Media/$shaObject.jpg"))
        assertArrayEquals(attachmentBytes, storage.files.getValue("Media/$shaAttachment.jpg"))
    }

    @Test
    fun unknownMimeIsPublishedAsTheGenericExtension() = runBlocking {
        val bytes = ByteArray(16) { 7 }
        val file = privateOriginal("physical-object-media/object/bin", bytes)
        val sha = shaOf(bytes)

        val report = service(mapOf("physical-object-media/object/bin" to file)).protect(
            CapturedMediaState(
                listOf(objectRow("physical-object-media/object/bin", sha, bytes.size.toLong(), mime = null)),
            ),
        )

        assertEquals(MediaProtectionSummary.ALL_PROTECTED, report.summary)
        assertEquals(listOf("Media/$sha.bin"), mediaEntries())
    }

    // --- idempotency ---

    @Test
    fun rerunReusesStronglyVerifiedBlobsAndPublishesNothingNew() = runBlocking {
        val bytes = ByteArray(32) { 5 }
        val file = privateOriginal("physical-object-media/object/one", bytes)
        val sha = shaOf(bytes)
        val protect = service(mapOf("physical-object-media/object/one" to file))
        val captured = CapturedMediaState(listOf(objectRow("physical-object-media/object/one", sha, 32)))

        val first = protect.protect(captured)
        val entriesAfterFirst = mediaEntries()
        val second = protect.protect(captured)

        assertEquals(MediaProtectionOutcome.INGESTED, first.results.single().outcome)
        assertEquals(MediaProtectionSummary.ALL_PROTECTED, second.summary)
        assertEquals(MediaProtectionOutcome.ALREADY_PRESENT, second.results.single().outcome)
        assertEquals(entriesAfterFirst, mediaEntries())
        assertArrayEquals(bytes, storage.files.getValue("Media/$sha.jpg"))
    }

    @Test
    fun aCorruptExistingCanonicalBlobIsNotReportedAsProtected() = runBlocking {
        val bytes = ByteArray(32) { 9 }
        val file = privateOriginal("physical-object-media/object/one", bytes)
        val sha = shaOf(bytes)
        // The repository is created first, then an unrelated process leaves a corrupt blob behind.
        val protect = service(mapOf("physical-object-media/object/one" to file))
        storage.files["Media/$sha.jpg"] = ByteArray(32) { 1 }

        val report = protect.protect(
            CapturedMediaState(listOf(objectRow("physical-object-media/object/one", sha, 32))),
        )

        assertEquals(MediaProtectionOutcome.VERIFY_FAILED, report.results.single().outcome)
        assertEquals(MediaProtectionSummary.NOTHING_PROTECTED, report.summary)
        assertFalse(report.protectedCount == report.requiredCount && report.requiredCount > 0)
    }

    // --- per-blob problems never roll back or block unrelated blobs ---

    @Test
    fun aMissingSourceLeavesOtherBlobsProtected() = runBlocking {
        val goodBytes = ByteArray(48) { 3 }
        val goodFile = privateOriginal("physical-object-media/object/good", goodBytes)
        val shaGood = shaOf(goodBytes)

        val report = service(mapOf("physical-object-media/object/good" to goodFile)).protect(
            CapturedMediaState(
                listOf(
                    objectRow("physical-object-media/object/missing", SHA_ONE, 12),
                    objectRow("physical-object-media/object/good", shaGood, 48),
                ),
            ),
        )

        assertEquals(MediaProtectionSummary.PARTIALLY_PROTECTED, report.summary)
        val missing = report.results.single { it.sha256 == SHA_ONE }
        val good = report.results.single { it.sha256 == shaGood }
        assertEquals(MediaProtectionOutcome.SOURCE_MISSING, missing.outcome)
        assertEquals(MediaProtectionOutcome.INGESTED, good.outcome)
        assertEquals(listOf("Media/$shaGood.jpg"), mediaEntries())
    }

    @Test
    fun aChangedSourceLeavesOtherBlobsProtected() = runBlocking {
        val changedBytes = ByteArray(24) { 4 }
        val changedFile = privateOriginal("physical-object-media/object/changed", changedBytes)
        val goodBytes = ByteArray(24) { 6 }
        val goodFile = privateOriginal("physical-object-media/object/good", goodBytes)
        val shaGood = shaOf(goodBytes)

        val report = service(
            mapOf(
                "physical-object-media/object/changed" to changedFile,
                "physical-object-media/object/good" to goodFile,
            ),
        ).protect(
            CapturedMediaState(
                listOf(
                    // The metadata claims a different SHA than the file actually has.
                    objectRow("physical-object-media/object/changed", SHA_TWO, 24),
                    objectRow("physical-object-media/object/good", shaGood, 24),
                ),
            ),
        )

        assertEquals(MediaProtectionOutcome.SOURCE_CHANGED, report.results.single { it.sha256 == SHA_TWO }.outcome)
        assertEquals(MediaProtectionOutcome.INGESTED, report.results.single { it.sha256 == shaGood }.outcome)
        assertEquals(MediaProtectionSummary.PARTIALLY_PROTECTED, report.summary)
    }

    @Test
    fun anUnresolvableCapturedPathIsABlobLocalProblem() = runBlocking {
        val report = service(emptyMap()).protect(
            CapturedMediaState(listOf(objectRow("physical-object-media/object/escape", SHA_ONE, 4))),
        )

        assertEquals(MediaProtectionOutcome.SOURCE_MISSING, report.results.single().outcome)
        assertEquals(MediaProtectionSummary.NOTHING_PROTECTED, report.summary)
        assertTrue(mediaEntries().isEmpty())
    }

    @Test
    fun duplicateShaFallsBackToTheNextCapturedSource() = runBlocking {
        val bytes = ByteArray(20) { 2 }
        val file = privateOriginal("observation-attachments/point/second", bytes)
        val sha = shaOf(bytes)

        val report = service(mapOf("observation-attachments/point/second" to file)).protect(
            CapturedMediaState(
                listOf(
                    // The first captured row for this SHA points at bytes that are gone.
                    objectRow("physical-object-media/object/first", sha, 20),
                    attachmentRow("observation-attachments/point/second", sha, 20),
                ),
            ),
        )

        val result = report.results.single()
        assertEquals(MediaProtectionOutcome.INGESTED, result.outcome)
        assertEquals(MediaSourceKind.OBSERVATION_POINT_ATTACHMENT, result.source?.kind)
        assertEquals(MediaProtectionSummary.ALL_PROTECTED, report.summary)
        assertEquals(listOf("Media/$sha.jpg"), mediaEntries())
    }

    @Test
    fun duplicateShaWithEverySourceUnusableIsSourceMissing() = runBlocking {
        val report = service(emptyMap()).protect(
            CapturedMediaState(
                listOf(
                    objectRow("physical-object-media/object/first", SHA_ONE, 20),
                    attachmentRow("observation-attachments/point/second", SHA_ONE, 20),
                ),
            ),
        )

        val result = report.results.single()
        assertEquals(MediaProtectionOutcome.SOURCE_MISSING, result.outcome)
        assertNull(result.source)
        assertEquals(1, report.requiredCount)
        assertTrue(mediaEntries().isEmpty())
    }

    // --- the required size must be the size really published ---

    @Test
    fun aSourceWhoseActualSizeDiffersFromTheRequiredSizeIsRefusedAndNeverIngested() = runBlocking {
        // The private original really hashes to the declared SHA (24 bytes, so the repository would
        // happily publish it), but the captured metadata declares 23 bytes. Publishing those bytes
        // would report a required size the repository does not hold, so the candidate is refused
        // before the repository is asked to do anything.
        val bytes = ByteArray(24) { 5 }
        val file = privateOriginal("physical-object-media/object/size-mismatch", bytes)
        val sha = shaOf(bytes)
        val attempted = mutableListOf<Long>()

        val report = recordingService(
            mapOf("physical-object-media/object/size-mismatch" to file),
            attempted,
        ).protect(
            CapturedMediaState(listOf(objectRow("physical-object-media/object/size-mismatch", sha, 23))),
        )

        val result = report.results.single()
        assertEquals(MediaProtectionOutcome.SOURCE_CHANGED, result.outcome)
        assertEquals(RepositoryError.SOURCE_CHANGED, result.error)
        assertEquals(MediaProtectionSummary.NOTHING_PROTECTED, report.summary)
        assertNull(report.blocker)
        assertTrue("the repository must never be asked to publish this candidate", attempted.isEmpty())
        assertTrue(mediaEntries().isEmpty())
        assertEquals(24L, file.length())
    }

    @Test
    fun duplicateShaFallsBackToTheCorrectlySizedSourceAfterAWrongSizeCandidate() = runBlocking {
        val bytes = ByteArray(20) { 2 }
        val good = privateOriginal("observation-attachments/point/second", bytes)
        val wrong = privateOriginal("physical-object-media/object/first", ByteArray(21) { 2 })
        val sha = shaOf(bytes)
        val attempted = mutableListOf<Long>()

        val report = recordingService(
            mapOf(
                "physical-object-media/object/first" to wrong,
                "observation-attachments/point/second" to good,
            ),
            attempted,
        ).protect(
            CapturedMediaState(
                listOf(
                    // The first captured row for this SHA resolves to a candidate of the wrong size.
                    objectRow("physical-object-media/object/first", sha, 20),
                    attachmentRow("observation-attachments/point/second", sha, 20),
                ),
            ),
        )

        val result = report.results.single()
        assertEquals(MediaProtectionOutcome.INGESTED, result.outcome)
        assertEquals(MediaSourceKind.OBSERVATION_POINT_ATTACHMENT, result.source?.kind)
        assertEquals(MediaProtectionSummary.ALL_PROTECTED, report.summary)
        assertEquals("only the correctly sized candidate may be attempted", listOf(20L), attempted)
        assertEquals(listOf("Media/$sha.jpg"), mediaEntries())
        assertArrayEquals(bytes, storage.files.getValue("Media/$sha.jpg"))
        assertEquals(21L, wrong.length())
    }

    // --- the repository's canonical extension must agree with the required set ---

    @Test
    fun aPublicationWhoseCanonicalExtensionDisagreesWithTheRequiredOneIsNeverProtected() = runBlocking {
        // Required jpg, bin and mp4 blobs, with the repository answering a different canonical
        // extension for the jpg and the bin blob. That answer is a disagreement about the canonical
        // path, so those blobs are not protected and the run cannot look complete.
        val jpgBytes = ByteArray(18) { 4 }
        val binBytes = ByteArray(19) { 6 }
        val mp4Bytes = ByteArray(20) { 8 }
        val shaJpg = shaOf(jpgBytes)
        val shaBin = shaOf(binBytes)
        val shaMp4 = shaOf(mp4Bytes)
        val files = mapOf(
            "physical-object-media/object/jpg" to privateOriginal("physical-object-media/object/jpg", jpgBytes),
            "physical-object-media/object/bin" to privateOriginal("physical-object-media/object/bin", binBytes),
            "physical-object-media/object/mp4" to privateOriginal("physical-object-media/object/mp4", mp4Bytes),
        )
        val bound = repository()
        // An earlier run already protected the mp4 blob; it must survive both disagreements.
        storage.files["Media/$shaMp4.mp4"] = mp4Bytes
        val disagreeing = mapOf(shaJpg to "mp4", shaBin to "jpg")
        val protect = MediaProtectionService(bound, MapSourceResolver(files), temporary.root) { source, cancelled ->
            val reported = disagreeing[source.expectedSha256]
            if (reported == null) {
                bound.ingest(source, cancelled)
            } else {
                RepositoryResult.Success(
                    CommittedBlob(source.expectedSha256!!, source.byteSize, reported, alreadyPresent = false),
                )
            }
        }

        val report = protect.protect(
            CapturedMediaState(
                listOf(
                    objectRow("physical-object-media/object/jpg", shaJpg, jpgBytes.size.toLong(), mime = "image/jpeg"),
                    objectRow("physical-object-media/object/bin", shaBin, binBytes.size.toLong(), mime = null),
                    objectRow("physical-object-media/object/mp4", shaMp4, mp4Bytes.size.toLong(), mime = "video/mp4"),
                ),
            ),
        )

        val jpg = report.results.single { it.sha256 == shaJpg }
        val bin = report.results.single { it.sha256 == shaBin }
        val mp4 = report.results.single { it.sha256 == shaMp4 }
        assertEquals("jpg", jpg.canonicalExtension)
        assertEquals("bin", bin.canonicalExtension)
        assertEquals("mp4", mp4.canonicalExtension)
        assertEquals(MediaProtectionOutcome.EXTENSION_MISMATCH, jpg.outcome)
        assertEquals(MediaProtectionOutcome.EXTENSION_MISMATCH, bin.outcome)
        assertEquals(MediaProtectionOutcome.ALREADY_PRESENT, mp4.outcome)
        assertEquals(3, report.requiredCount)
        assertEquals(1, report.protectedCount)
        assertEquals(MediaProtectionSummary.PARTIALLY_PROTECTED, report.summary)
        // The unrelated blob protected earlier is neither removed nor damaged.
        assertEquals(listOf("Media/$shaMp4.mp4"), mediaEntries())
        assertArrayEquals(mp4Bytes, storage.files.getValue("Media/$shaMp4.mp4"))
    }

    // --- repository-wide blockers ---

    @Test
    fun capacityFailureStopsTheRunWithoutLosingOlderBlobs() = runBlocking {
        val firstBytes = ByteArray(40) { 8 }
        val firstFile = privateOriginal("physical-object-media/object/first", firstBytes)
        val secondBytes = ByteArray(40) { 9 }
        val secondFile = privateOriginal("physical-object-media/object/second", secondBytes)
        val shaFirst = shaOf(firstBytes)
        val shaSecond = shaOf(secondBytes)
        val protect = service(
            mapOf(
                "physical-object-media/object/first" to firstFile,
                "physical-object-media/object/second" to secondFile,
            ),
        )

        val committed = protect.protect(
            CapturedMediaState(listOf(objectRow("physical-object-media/object/first", shaFirst, 40))),
        )
        assertEquals(MediaProtectionSummary.ALL_PROTECTED, committed.summary)

        storage.available = 1L
        val blocked = protect.protect(
            CapturedMediaState(
                listOf(
                    objectRow("physical-object-media/object/first", shaFirst, 40),
                    objectRow("physical-object-media/object/second", shaSecond, 40),
                ),
            ),
        )

        assertEquals(MediaProtectionSummary.REPOSITORY_BLOCKED, blocked.summary)
        assertEquals(RepositoryError.CAPACITY_INSUFFICIENT, blocked.blocker)
        val firstResult = blocked.results.single { it.sha256 == shaFirst }
        val secondResult = blocked.results.single { it.sha256 == shaSecond }
        assertEquals(MediaProtectionOutcome.ALREADY_PRESENT, firstResult.outcome)
        assertEquals(MediaProtectionOutcome.CAPACITY_BLOCKED, secondResult.outcome)
        assertEquals(listOf("Media/$shaFirst.jpg"), mediaEntries())
        assertArrayEquals(firstBytes, storage.files.getValue("Media/$shaFirst.jpg"))
    }

    @Test
    fun permissionLossStopsTheRunAndNeverRewritesTheBinding() = runBlocking {
        val bytes = ByteArray(8) { 1 }
        val file = privateOriginal("physical-object-media/object/one", bytes)
        val sha = shaOf(bytes)
        val protect = service(mapOf("physical-object-media/object/one" to file))
        roots.failure = RepositoryError.PERMISSION_LOST

        val report = protect.protect(
            CapturedMediaState(
                listOf(
                    objectRow("physical-object-media/object/one", sha, 8),
                    objectRow("physical-object-media/object/two", SHA_TWO, 8),
                ),
            ),
        )

        assertEquals(MediaProtectionSummary.REPOSITORY_BLOCKED, report.summary)
        assertEquals(RepositoryError.PERMISSION_LOST, report.blocker)
        assertEquals(2, report.requiredCount)
        assertEquals(0, report.protectedCount)
        assertEquals(
            1,
            report.results.count { it.outcome == MediaProtectionOutcome.SKIPPED_AFTER_GLOBAL_BLOCKER },
        )
        assertEquals(
            1,
            report.results.count {
                it.outcome == MediaProtectionOutcome.REPOSITORY_ERROR && it.error == RepositoryError.PERMISSION_LOST
            },
        )
        assertEquals(binding, bindingStore.value)
        assertTrue(mediaEntries().isEmpty())
    }

    @Test
    fun repositoryIdentityMismatchStopsTheRun() = runBlocking {
        val firstBytes = ByteArray(8) { 3 }
        val firstFile = privateOriginal("physical-object-media/object/one", firstBytes)
        val secondBytes = ByteArray(8) { 4 }
        val secondFile = privateOriginal("physical-object-media/object/two", secondBytes)
        val shaFirst = shaOf(firstBytes)
        val shaSecond = shaOf(secondBytes)
        // Both blobs have usable sources, so exactly one of them reports the blocker and the other is
        // reported as untried rather than silently disappearing.
        val protect = service(
            mapOf(
                "physical-object-media/object/one" to firstFile,
                "physical-object-media/object/two" to secondFile,
            ),
        )
        roots.failure = RepositoryError.ROOT_IDENTITY_MISMATCH

        val report = protect.protect(
            CapturedMediaState(
                listOf(
                    objectRow("physical-object-media/object/one", shaFirst, 8),
                    objectRow("physical-object-media/object/two", shaSecond, 8),
                ),
            ),
        )

        assertEquals(MediaProtectionSummary.REPOSITORY_BLOCKED, report.summary)
        assertEquals(RepositoryError.ROOT_IDENTITY_MISMATCH, report.blocker)
        assertEquals(2, report.requiredCount)
        assertEquals(0, report.protectedCount)
        assertEquals(
            1,
            report.results.count {
                it.outcome == MediaProtectionOutcome.REPOSITORY_ERROR &&
                    it.error == RepositoryError.ROOT_IDENTITY_MISMATCH
            },
        )
        assertEquals(
            1,
            report.results.count { it.outcome == MediaProtectionOutcome.SKIPPED_AFTER_GLOBAL_BLOCKER },
        )
        assertEquals(binding, bindingStore.value)
        assertTrue(mediaEntries().isEmpty())
    }

    @Test
    fun cancellationBeforeAnyBlobProtectsNothingAndReportsCancellation() = runBlocking {
        val bytes = ByteArray(8) { 2 }
        val file = privateOriginal("physical-object-media/object/one", bytes)
        val sha = shaOf(bytes)

        val report = service(mapOf("physical-object-media/object/one" to file)).protect(
            CapturedMediaState(listOf(objectRow("physical-object-media/object/one", sha, 8))),
            cancelled = { true },
        )

        assertEquals(MediaProtectionSummary.CANCELLED, report.summary)
        assertEquals(0, report.protectedCount)
        assertTrue(mediaEntries().isEmpty())
    }

    // --- safety properties ---

    @Test
    fun protectionCreatesNoSnapshotAndPublishesNoReferenceFile() = runBlocking {
        val bytes = ByteArray(12) { 3 }
        val file = privateOriginal("physical-object-media/object/one", bytes)
        val sha = shaOf(bytes)

        service(mapOf("physical-object-media/object/one" to file)).protect(
            CapturedMediaState(listOf(objectRow("physical-object-media/object/one", sha, 12))),
        )

        assertTrue(storage.files.keys.none { it.startsWith("Snapshots/") })
        assertTrue(storage.list("Snapshots").isEmpty())
        assertEquals(listOf("Media/$sha.jpg"), storage.files.keys.filter { it.startsWith("Media/") })
    }

    @Test
    fun privateOriginalsAreNeverDeletedOrModified() = runBlocking {
        val bytes = ByteArray(12) { 4 }
        val file = privateOriginal("physical-object-media/object/one", bytes)
        val sha = shaOf(bytes)
        val before = file.readBytes()

        service(mapOf("physical-object-media/object/one" to file)).protect(
            CapturedMediaState(listOf(objectRow("physical-object-media/object/one", sha, 12))),
        )

        assertTrue(file.isFile)
        assertArrayEquals(before, file.readBytes())
        assertEquals(
            listOf("physical-object-media/object/one"),
            temporary.root.walkTopDown().filter { it.isFile }.map {
                it.relativeTo(temporary.root).path.replace(File.separatorChar, '/')
            }.toList(),
        )
    }

    @Test
    fun anIncoherentRequiredSetIsRejectedBeforeAnyIngest() = runBlocking {
        val bytes = ByteArray(8) { 5 }
        val file = privateOriginal("physical-object-media/object/one", bytes)
        val sha = shaOf(bytes)

        val report = service(mapOf("physical-object-media/object/one" to file)).protect(
            CapturedMediaState(
                listOf(
                    objectRow("physical-object-media/object/one", sha, 8, mime = "image/jpeg"),
                    attachmentRow("observation-attachments/point/two", sha, 8, mime = "video/mp4"),
                ),
            ),
        )

        assertEquals(MediaProtectionSummary.METADATA_INCONSISTENT, report.summary)
        assertEquals(RequiredMediaSet.CONFLICTING_TYPE, report.inconsistency)
        assertTrue(report.results.isEmpty())
        assertTrue(mediaEntries().isEmpty())
    }

    // --- planning ---

    @Test
    fun thePlanGroupsSourcesByShaInADeterministicOrder() = runBlocking {
        val first = UUID.fromString("00000000-0000-4000-8000-000000000001")
        val second = UUID.fromString("00000000-0000-4000-8000-000000000002")
        val plan = MediaProtectionPlanner.plan(
            CapturedMediaState(
                listOf(
                    attachmentRow("observation-attachments/point/a", SHA_ONE, 10, id = second),
                    objectRow("physical-object-media/object/b", SHA_ONE, 10, id = first),
                    objectRow("physical-object-media/object/c", SHA_TWO, 11, id = first),
                ),
            ),
        )

        assertEquals(listOf(SHA_ONE, SHA_TWO), plan.items.map { it.blob.sha256 })
        val shared = plan.items.first { it.blob.sha256 == SHA_ONE }
        assertEquals(2, shared.sources.size)
        assertEquals(
            listOf("physical-object-media/object/b", "observation-attachments/point/a"),
            shared.sources.map { it.relativePath },
        )
    }

    @Test
    fun anEmptyResearchStateRequiresNothing() = runBlocking {
        val report = service(emptyMap()).protect(CapturedMediaState(emptyList()))
        assertEquals(MediaProtectionSummary.ALL_PROTECTED, report.summary)
        assertEquals(0, report.requiredCount)
        assertNotNull(report.results)
    }
}
