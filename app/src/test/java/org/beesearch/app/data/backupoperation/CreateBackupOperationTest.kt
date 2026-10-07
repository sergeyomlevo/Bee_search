package org.beesearch.app.data.backupoperation

import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backuprepository.*
import org.beesearch.app.data.backupsnapshot.SnapshotDomainCodec
import org.beesearch.app.data.backupsnapshot.SnapshotDomainEntries
import org.beesearch.app.data.backupsnapshot.SnapshotEvidenceProfile
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.HollowEntity
import org.beesearch.app.data.local.room.PhysicalObjectEntity
import org.beesearch.app.data.local.room.PhysicalObjectMediaEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Orchestration tests: capture, protection, publication and terminal result are one operation. */
class CreateBackupOperationTest {
    @get:Rule val temporary = TemporaryFolder()

    private val instant = Instant.parse("2026-01-01T00:00:00Z")
    private val territory = UUID.fromString("00000000-0000-4000-8000-000000000021")
    private val observer = UUID.fromString("00000000-0000-4000-8000-000000000022")
    private val objectId = UUID.fromString("00000000-0000-4000-8000-000000000023")

    private fun emptyGraph() = Graph(emptyList(), emptyList(), points = emptyList(), bees = emptyList(), cycles = emptyList())

    private fun entries(graph: Graph = emptyGraph(), settings: PortableSettingsSnapshot = PortableSettingsSnapshot(null, null, emptyMap())) =
        SnapshotDomainCodec.encode(graph, settings)

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun mediaGraph(first: ByteArray, second: ByteArray? = null): Graph {
        val rows = listOfNotNull(first, second).mapIndexed { index, bytes ->
            PhysicalObjectMediaEntity(
                UUID.nameUUIDFromBytes("media-$index".toByteArray()), objectId,
                PhysicalObjectMediaType.IMAGE, "objects/$index.jpg", "$index.jpg", "image/jpeg",
                bytes.size.toLong(), sha(bytes), instant,
            )
        }
        return Graph(
            territories = listOf(TerritoryEntity(territory, "T", "Territory", "R", "D", instant, instant)),
            observers = listOf(ObserverEntity(observer, "O", "Observer", "A", null, null, instant, instant)),
            physicalObjects = listOf(PhysicalObjectEntity(objectId, territory, PhysicalObjectType.HOLLOW, 1, 55.7, 37.6, instant, observer)),
            hollows = listOf(HollowEntity(objectId, "oak", 120.0, 90, 40.0, 30.0, "h", "H")),
            objectMedia = rows,
            points = emptyList(), bees = emptyList(), cycles = emptyList(),
        )
    }

    private fun repository(): Triple<MemoryRepositoryStorage, RepositoryFoundation, UUID> = runBlocking {
        val storage = MemoryRepositoryStorage()
        val foundation = RepositoryFoundation(storage, "Dev")
        Triple(storage, foundation, foundation.initializeNew().valueOrThrow().repositoryId)
    }

    private class TestBindingStore(var binding: RepositoryBinding?) : RepositoryBindingStore {
        override suspend fun read() = binding
        override suspend fun replace(expected: RepositoryBinding?, next: RepositoryBinding?) {
            check(binding == expected)
            binding = next
        }
    }

    private class TestRoots(private val foundation: RepositoryFoundation) : RepositoryRootResolver {
        override fun resolve(locator: String) = foundation
    }

    private class TestMediaResolver(private val files: Map<String, File>) : MediaSourceResolver {
        override fun resolve(row: CapturedMediaRow): File? = files[row.relativePath]?.takeIf { it.isFile }
    }

    private fun report(captured: CapturedMediaState, protected: Int = captured.rows.size): MediaProtectionReport {
        val rows = captured.rows.take(protected).map { row ->
            ProtectedBlobResult(row.sha256!!, row.byteSize!!, "jpg", MediaProtectionOutcome.INGESTED, row)
        }
        val missing = captured.rows.drop(protected).map { row ->
            ProtectedBlobResult(row.sha256!!, row.byteSize!!, "jpg", MediaProtectionOutcome.SOURCE_MISSING, row)
        }
        val results = rows + missing
        return MediaProtectionReport(
            results = results,
            summary = when {
                results.isEmpty() || protected == results.size -> MediaProtectionSummary.ALL_PROTECTED
                protected == 0 -> MediaProtectionSummary.NOTHING_PROTECTED
                else -> MediaProtectionSummary.PARTIALLY_PROTECTED
            },
        )
    }

    private fun operation(
        graph: Graph,
        settings: PortableSettingsSnapshot = PortableSettingsSnapshot(null, null, emptyMap()),
        protect: suspend (CapturedMediaState) -> MediaProtectionReport,
        publish: suspend (SnapshotDomainEntries) -> RepositoryResult<CommittedSnapshot>,
        preflight: suspend () -> RepositoryResult<Unit> = { RepositoryResult.Success(Unit) },
        reads: MutableList<String> = mutableListOf(),
    ) = CreateBackupOperation(
        BackupOperationCapture(
            readGraph = { reads += "graph"; graph },
            readSettings = { reads += "settings"; settings },
        ),
        protect,
        publish,
        preflight,
    )

    @Test fun successfulOperationCapturesEachSourceOnceAndPublishesFull() = runBlocking {
        val (storage, foundation, repositoryId) = repository()
        val bytes = ByteArray(16) { 3 }
        storage.files["Media/${sha(bytes)}.jpg"] = bytes
        val graph = mediaGraph(bytes)
        val reads = mutableListOf<String>()
        var published: SnapshotDomainEntries? = null
        val result = operation(
            graph = graph,
            protect = { captured -> report(captured) },
            publish = { captured ->
                published = captured
                foundation.createFullSnapshotFromCaptured(repositoryId, temporary.root, captured, createdAtEpochMs = 10)
            },
            reads = reads,
        ).create()

        assertTrue(result is CreateBackupResult.Created)
        assertEquals(1, reads.count { it == "graph" })
        assertEquals(1, reads.count { it == "settings" })
        assertEquals(1, (result as CreateBackupResult.Created).verifiedMediaCount)
        assertEquals(SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED, result.committed.snapshot.evidenceProfile)
        assertEquals(published!!.references, result.committed.snapshot.references)
    }

    @Test fun zeroMediaIsAFullSuccessWithoutMediaCount() = runBlocking {
        val (_, foundation, repositoryId) = repository()
        val result = operation(
            graph = emptyGraph(),
            protect = { captured -> report(captured) },
            publish = { captured -> foundation.createFullSnapshotFromCaptured(repositoryId, temporary.root, captured) },
        ).create()

        assertEquals(CreateBackupResult.Created::class, result::class)
        assertEquals(0, (result as CreateBackupResult.Created).verifiedMediaCount)
        assertTrue(result.committed.snapshot.references.isEmpty())
    }

    @Test fun partialProtectionReturnsMediaFailureAndDoesNotPublish() = runBlocking {
        val (_, foundation, repositoryId) = repository()
        val first = ByteArray(16) { 1 }
        val second = ByteArray(17) { 2 }
        var publishCalls = 0
        val result = operation(
            graph = mediaGraph(first, second),
            protect = { captured -> report(captured, protected = 1) },
            publish = { captured ->
                publishCalls++
                foundation.createFullSnapshotFromCaptured(repositoryId, temporary.root, captured)
            },
        ).create()

        assertEquals(CreateBackupResult.MediaFailed(1, 2), result)
        assertEquals(0, publishCalls)
        assertTrue(foundation.discoverSnapshots(repositoryId, temporary.root).valueOrThrow().candidates.isEmpty())
    }

    @Test fun repositoryBlockerHasPriorityOverMediaCountMismatchAndDoesNotPublish() = runBlocking {
        val (_, foundation, repositoryId) = repository()
        val first = ByteArray(16) { 1 }
        val second = ByteArray(17) { 2 }
        var publishCalls = 0
        val result = operation(
            graph = mediaGraph(first, second),
            protect = { captured ->
                report(captured, protected = 1).copy(
                    summary = MediaProtectionSummary.REPOSITORY_BLOCKED,
                    blocker = RepositoryError.PERMISSION_LOST,
                )
            },
            publish = { captured ->
                publishCalls++
                foundation.createFullSnapshotFromCaptured(repositoryId, temporary.root, captured)
            },
        ).create()

        assertEquals(CreateBackupResult.Failed(RepositoryError.PERMISSION_LOST), result)
        assertEquals(0, publishCalls)
        assertTrue(foundation.discoverSnapshots(repositoryId, temporary.root).valueOrThrow().candidates.isEmpty())
    }

    @Test fun metadataInconsistencyHasPriorityOverMediaCountMismatchAndDoesNotPublish() = runBlocking {
        val (_, foundation, repositoryId) = repository()
        val first = ByteArray(16) { 1 }
        val second = ByteArray(17) { 2 }
        var publishCalls = 0
        val result = operation(
            graph = mediaGraph(first, second),
            protect = { captured ->
                report(captured, protected = 1).copy(
                    summary = MediaProtectionSummary.METADATA_INCONSISTENT,
                    inconsistency = "test metadata inconsistency",
                )
            },
            publish = { captured ->
                publishCalls++
                foundation.createFullSnapshotFromCaptured(repositoryId, temporary.root, captured)
            },
        ).create()

        assertEquals(CreateBackupResult.Failed(RepositoryError.LOGICAL_STATE_INCONSISTENT), result)
        assertEquals(0, publishCalls)
        assertTrue(foundation.discoverSnapshots(repositoryId, temporary.root).valueOrThrow().candidates.isEmpty())
    }

    @Test fun retryUsesANewCaptureAndCanPublishAfterPreviouslySavedMediaIsReused() = runBlocking {
        val (storage, foundation, repositoryId) = repository()
        val bytes = ByteArray(16) { 4 }
        val graph = mediaGraph(bytes)
        storage.files["Media/${sha(bytes)}.jpg"] = bytes
        var captures = 0
        var failFirst = true
        val operation = CreateBackupOperation(
            BackupOperationCapture({ captures++; graph }, { PortableSettingsSnapshot(null, null, emptyMap()) }),
            { captured -> if (failFirst) { failFirst = false; report(captured, 0) } else report(captured) },
            { captured -> foundation.createFullSnapshotFromCaptured(repositoryId, temporary.root, captured) },
            { RepositoryResult.Success(Unit) },
        )

        assertEquals(CreateBackupResult.MediaFailed(1, 1), operation.create())
        assertTrue(operation.create() is CreateBackupResult.Created)
        assertEquals(2, captures)
        assertEquals(1, storage.list("Snapshots").size)
    }

    @Test fun realProtectionRetainsPartialMediaAndRetryReusesItWithANewCapture() = runBlocking {
        val (storage, foundation, repositoryId) = repository()
        val binding = RepositoryBinding(UUID.randomUUID(), "memory")
        val bound = BoundRepository(TestBindingStore(binding.copy(expectedRepositoryId = repositoryId)), TestRoots(foundation))
        val firstBytes = ByteArray(16) { 4 }
        val secondBytes = ByteArray(17) { 5 }
        val firstPath = File(temporary.root, "objects/first.jpg").apply { parentFile!!.mkdirs(); writeBytes(firstBytes) }
        val secondPath = File(temporary.root, "objects/second.jpg")
        val sources = mapOf("objects/0.jpg" to firstPath, "objects/1.jpg" to secondPath)
        val protection = MediaProtectionService(bound, TestMediaResolver(sources), temporary.root)
        var liveGraph = mediaGraph(firstBytes, secondBytes)
        var liveSettings = PortableSettingsSnapshot(null, null, emptyMap())
        var graphReads = 0
        var settingsReads = 0
        val reports = mutableListOf<MediaProtectionReport>()
        var firstCapturedRows = -1
        var firstAttempt = true
        var publishedEntries: SnapshotDomainEntries? = null
        val operation = CreateBackupOperation(
            BackupOperationCapture(
                readGraph = { graphReads++; liveGraph },
                readSettings = { settingsReads++; liveSettings },
            ),
            { captured ->
                if (firstAttempt) {
                    // Mutating live state after capture cannot change this immutable media plan.
                    firstCapturedRows = captured.rows.size
                    liveSettings = liveSettings.copy(currentObserverId = observer)
                    liveGraph = mediaGraph(firstBytes, secondBytes)
                }
                val report = protection.protect(captured)
                reports += report
                firstAttempt = false
                report
            },
            { captured ->
                publishedEntries = captured
                foundation.createFullSnapshotFromCaptured(repositoryId, temporary.root, captured)
            },
            { RepositoryResult.Success(Unit) },
        )

        assertEquals(CreateBackupResult.MediaFailed(1, 2), operation.create())
        assertEquals(2, firstCapturedRows)
        assertTrue(storage.list("Snapshots").isEmpty())
        assertEquals(1, graphReads)
        assertEquals(1, settingsReads)
        val firstResult = reports[0].results.associateBy { it.sha256 }
        assertEquals(MediaProtectionOutcome.INGESTED, firstResult.getValue(sha(firstBytes)).outcome)
        assertEquals(MediaProtectionOutcome.SOURCE_MISSING, firstResult.getValue(sha(secondBytes)).outcome)
        val firstStored = storage.files.getValue("Media/${sha(firstBytes)}.jpg")
        assertArrayEquals(firstBytes, firstStored)

        secondPath.writeBytes(secondBytes)
        val second = operation.create()
        assertTrue(second is CreateBackupResult.Created)
        assertEquals(2, graphReads)
        assertEquals(2, settingsReads)
        assertEquals(2, reports[1].protectedCount)
        val retryResult = reports[1].results.associateBy { it.sha256 }
        assertEquals(MediaProtectionOutcome.ALREADY_PRESENT, retryResult.getValue(sha(firstBytes)).outcome)
        assertEquals(MediaProtectionOutcome.INGESTED, retryResult.getValue(sha(secondBytes)).outcome)
        assertSame(firstStored, storage.files.getValue("Media/${sha(firstBytes)}.jpg"))
        assertArrayEquals(firstBytes, firstStored)
        assertTrue((second as CreateBackupResult.Created).committed.snapshot.references.size == 2)
        assertTrue(String(publishedEntries!!.portable).contains(observer.toString()))
    }

    @Test fun liveStateChangedAfterCaptureDoesNotEnterTheCurrentPublication() = runBlocking {
        val (_, foundation, repositoryId) = repository()
        var current = emptyGraph()
        var captures = 0
        var firstEntries: SnapshotDomainEntries? = null
        val operation = CreateBackupOperation(
            BackupOperationCapture(
                readGraph = { captures++; val captured = current; current = mediaGraph(ByteArray(8) { 7 }); captured },
                readSettings = { PortableSettingsSnapshot(null, null, emptyMap()) },
            ),
            { report(it) },
            { captured -> firstEntries = captured; foundation.createFullSnapshotFromCaptured(repositoryId, temporary.root, captured) },
            { RepositoryResult.Success(Unit) },
        )

        val result = operation.create()
        assertTrue(result is CreateBackupResult.Created)
        assertEquals(1, captures)
        assertTrue(firstEntries!!.references.isEmpty())
    }

    @Test fun preflightAndPublicationErrorsRemainTypedFailures() = runBlocking {
        val access = operation(emptyGraph(), protect = { report(it) }, publish = { error("not called") },
            preflight = { RepositoryResult.Failure(RepositoryError.PERMISSION_LOST) }).create()
        assertEquals(CreateBackupResult.Failed(RepositoryError.PERMISSION_LOST), access)

        val capacity = operation(emptyGraph(), protect = { report(it) },
            publish = { RepositoryResult.Failure(RepositoryError.CAPACITY_INSUFFICIENT) }).create()
        assertEquals(CreateBackupResult.Failed(RepositoryError.CAPACITY_INSUFFICIENT), capacity)
    }

    @Test fun strongEvidenceFailureAfterProtectionDoesNotBecomeSuccess() = runBlocking {
        var publishCalls = 0
        val result = operation(
            graph = emptyGraph(),
            protect = { report(it) },
            publish = {
                publishCalls++
                RepositoryResult.Failure(RepositoryError.MEDIA_EVIDENCE_MISMATCH)
            },
        ).create()

        assertEquals(CreateBackupResult.Failed(RepositoryError.MEDIA_EVIDENCE_MISMATCH), result)
        assertEquals(1, publishCalls)
    }

    @Test fun cancellationIsNotConvertedToAUserSuccess() = runBlocking {
        var published = false
        try {
            operation(
                graph = emptyGraph(),
                protect = { throw CancellationException("test cancellation") },
                publish = { published = true; error("not called") },
            ).create()
            fail("expected cancellation")
        } catch (_: CancellationException) {
            assertFalse(published)
        }
    }

    @Test fun concurrentCreateHasOneWinnerAndOneAlreadyRunning() = runBlocking {
        var captures = 0
        val operation = operation(
            graph = emptyGraph(),
            protect = { captures++; delay(50); report(it) },
            publish = { delay(50); RepositoryResult.Failure(RepositoryError.PUBLISH_FAILED) },
        )
        val results = listOf(async { operation.create() }, async { operation.create() }).awaitAll()
        assertEquals(1, results.count { it == CreateBackupResult.AlreadyRunning })
        assertEquals(1, captures)
    }
}
