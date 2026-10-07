package org.beesearch.app.data.backuprepository

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backupsnapshot.SnapshotDomainEntries
import org.beesearch.app.data.backupsnapshot.SnapshotDomainCodec
import org.beesearch.app.data.backupsnapshot.SnapshotEvidenceProfile
import org.beesearch.app.data.local.room.HollowEntity
import org.beesearch.app.data.local.room.ObserverEntity
import org.beesearch.app.data.local.room.PhysicalObjectEntity
import org.beesearch.app.data.local.room.PhysicalObjectMediaEntity
import org.beesearch.app.data.local.room.TerritoryEntity
import org.beesearch.app.domain.model.PhysicalObjectMediaType
import org.beesearch.app.domain.model.PhysicalObjectType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The local full-evidence profile at the repository boundary: one immutable capture defines both the
 * serialized metadata and the required media set, publication requires verified repository bytes, and a
 * failure publishes nothing instead of falling back to the metadata profile.
 */
class RepositoryFullSnapshotTest {
    @get:Rule val temp = TemporaryFolder()

    private val at = Instant.parse("2026-01-01T00:00:00Z")
    private val territory = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val observer = UUID.fromString("00000000-0000-4000-8000-000000000002")
    private val objectId = UUID.fromString("00000000-0000-4000-8000-000000000003")

    private fun shaOf(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun entries(vararg blobs: ByteArray): SnapshotDomainEntries = SnapshotDomainCodec.encode(
        Graph(
            territories = listOf(TerritoryEntity(territory, "T1", "Territory", "R", "D", at, at)),
            observers = listOf(ObserverEntity(observer, "O1", "Observer", "A", null, null, at, at)),
            physicalObjects = listOf(
                PhysicalObjectEntity(objectId, territory, PhysicalObjectType.HOLLOW, 1, 55.7, 37.6, at, observer),
            ),
            hollows = listOf(HollowEntity(objectId, "oak", 120.0, 90, 40.0, 30.0, "h", "H")),
            objectMedia = blobs.mapIndexed { index, bytes ->
                PhysicalObjectMediaEntity(
                    UUID.fromString("00000000-0000-4000-8000-%012d".format(100 + index)),
                    objectId,
                    PhysicalObjectMediaType.IMAGE,
                    "objects/$index.jpg",
                    "$index.jpg",
                    "image/jpeg",
                    bytes.size.toLong(),
                    shaOf(bytes),
                    at,
                )
            },
            points = emptyList(),
            bees = emptyList(),
            cycles = emptyList(),
        ),
        PortableSettingsSnapshot(null, null, emptyMap()),
    )

    private fun publish(storage: MemoryRepositoryStorage, bytes: ByteArray, extension: String = "jpg") {
        storage.files["Media/${shaOf(bytes)}.$extension"] = bytes
    }

    private suspend fun setup(variant: String = "Dev"): Triple<MemoryRepositoryStorage, RepositoryFoundation, UUID> {
        val storage = MemoryRepositoryStorage()
        val foundation = RepositoryFoundation(storage, variant)
        return Triple(storage, foundation, foundation.initializeNew().valueOrThrow().repositoryId)
    }

    private fun entryNames(bytes: ByteArray): List<String> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }

    @Test fun fullCreationPublishesOnlyWhenEveryRequiredBlobIsVerified() = runBlocking {
        val (storage, foundation, id) = setup()
        val first = ByteArray(16) { 1 }
        val second = ByteArray(24) { 2 }
        publish(storage, first)
        publish(storage, second)

        val committed = foundation.createFullSnapshot(id, temp.root, { entries(first, second) }, createdAtEpochMs = 10)
            .valueOrThrow()

        assertEquals(SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED, committed.snapshot.evidenceProfile)
        assertTrue(committed.snapshot.evidenceProfile.requiresRepositoryMediaEvidence)
        assertEquals(2, committed.snapshot.references.size)
        assertEquals(17, committed.snapshot.metrics.entryBytes.size)
        assertTrue(storage.list("Staging").isEmpty())
        val names = entryNames(storage.files.getValue(committed.path))
        assertEquals(17, names.size)
        assertTrue(names.none { it.startsWith("Media/") || it.endsWith(".jpg") || it.endsWith(".mp4") })
        // The repository media itself is untouched by verification.
        assertArrayEquals(first, storage.files.getValue("Media/${shaOf(first)}.jpg"))
        assertArrayEquals(second, storage.files.getValue("Media/${shaOf(second)}.jpg"))
        // Discovery accepts the candidate while its evidence still holds.
        val discovery = foundation.discoverSnapshots(id, temp.root).valueOrThrow()
        assertEquals(1, discovery.candidates.size)
        assertEquals(SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED, discovery.latest!!.evidenceProfile)
    }

    @Test fun fullCreationWithZeroRequiredBlobsIsValidEvidence() = runBlocking {
        val (storage, foundation, id) = setup()

        val committed = foundation.createFullSnapshot(id, temp.root, { entries() }, createdAtEpochMs = 10).valueOrThrow()

        assertTrue(committed.snapshot.references.isEmpty())
        assertEquals(SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED, committed.snapshot.evidenceProfile)
        assertTrue(storage.list("Media").isEmpty())
        assertNotNull(foundation.discoverSnapshots(id, temp.root).valueOrThrow().latest)
    }

    @Test fun metadataOnlyCreationIsUnchangedAndStillIgnoresRepositoryMedia() = runBlocking {
        val (storage, foundation, id) = setup()
        val bytes = ByteArray(16) { 7 }

        val committed = foundation.createMetadataSnapshot(id, temp.root, { entries(bytes) }, createdAtEpochMs = 10)
            .valueOrThrow()

        assertEquals(SnapshotEvidenceProfile.METADATA_ONLY, committed.snapshot.evidenceProfile)
        assertFalse(committed.snapshot.evidenceProfile.requiresRepositoryMediaEvidence)
        assertEquals(1, committed.snapshot.references.size)
        assertTrue(storage.list("Media").isEmpty())
        // The reference is metadata identity only: no repository bytes are needed or claimed.
        assertTrue(foundation.discoverSnapshots(id, temp.root).valueOrThrow().latest!!.references.isNotEmpty())
    }

    private class EvidenceFailure(
        val name: String,
        val expected: RepositoryError,
        val prepare: (MemoryRepositoryStorage) -> Unit,
    )

    @Test fun everyMediaEvidenceFailurePublishesNothingAndKeepsTheOlderSnapshot() = runBlocking {
        val blob = ByteArray(16) { 3 }
        val failures = listOf(
            EvidenceFailure("missing blob", RepositoryError.MEDIA_EVIDENCE_MISSING) { },
            EvidenceFailure("wrong size", RepositoryError.MEDIA_EVIDENCE_MISMATCH) { s ->
                s.files["Media/${shaOf(blob)}.jpg"] = ByteArray(15) { 3 }
            },
            EvidenceFailure("wrong bytes", RepositoryError.MEDIA_EVIDENCE_MISMATCH) { s ->
                s.files["Media/${shaOf(blob)}.jpg"] = ByteArray(16) { 4 }
            },
            EvidenceFailure("wrong extension", RepositoryError.MEDIA_EVIDENCE_INCONSISTENT) { s ->
                s.files["Media/${shaOf(blob)}.mp4"] = blob
            },
            EvidenceFailure("ambiguous sha", RepositoryError.MEDIA_EVIDENCE_INCONSISTENT) { s ->
                s.files["Media/${shaOf(blob)}.jpg"] = blob
                s.files["Media/${shaOf(blob)}.bin"] = blob
            },
        )

        for (failure in failures) {
            val (storage, foundation, id) = setup()
            val older = foundation.createMetadataSnapshot(id, temp.root, { entries(blob) }, createdAtEpochMs = 5)
                .valueOrThrow()
            val olderBytes = storage.files.getValue(older.path).copyOf()
            failure.prepare(storage)

            val result = foundation.createFullSnapshot(id, temp.root, { entries(blob) }, createdAtEpochMs = 10)

            assertEquals(failure.name, failure.expected, (result as RepositoryResult.Failure).error)
            assertEquals(failure.name, listOf(older.path), storage.list("Snapshots").map { it.path })
            assertArrayEquals(failure.name, olderBytes, storage.files.getValue(older.path))
            assertTrue(failure.name, storage.list("Staging").isEmpty())
            // An older metadata-only snapshot still validates exactly as the metadata profile.
            val discovery = foundation.discoverSnapshots(id, temp.root).valueOrThrow()
            assertEquals(failure.name, SnapshotEvidenceProfile.METADATA_ONLY, discovery.latest!!.evidenceProfile)
        }
    }

    @Test fun aForeignExpectedRepositoryIdentityNeverPublishesFullEvidence() = runBlocking {
        val (storage, foundation, id) = setup()
        val blob = ByteArray(16) { 5 }
        publish(storage, blob)

        val result = foundation.createFullSnapshot(UUID.randomUUID(), temp.root, { entries(blob) },
            createdAtEpochMs = 10)

        assertEquals(RepositoryError.ROOT_IDENTITY_MISMATCH, (result as RepositoryResult.Failure).error)
        assertTrue(storage.list("Snapshots").isEmpty())
        assertTrue(storage.list("Staging").isEmpty())
    }

    @Test fun aVariantMismatchNeverPublishesFullEvidence() = runBlocking {
        val (storage, _, id) = setup()
        val wrongVariant = RepositoryFoundation(storage, "Beta")
        val blob = ByteArray(16) { 5 }
        publish(storage, blob)

        val result = wrongVariant.createFullSnapshot(id, temp.root, { entries(blob) }, createdAtEpochMs = 10)

        assertEquals(RepositoryError.ROOT_IDENTITY_MISMATCH, (result as RepositoryResult.Failure).error)
        assertTrue(storage.list("Snapshots").isEmpty())
    }

    @Test fun cancellationBeforePublicationPublishesNothing() = runBlocking {
        val (storage, foundation, id) = setup()
        val blob = ByteArray(16) { 6 }
        publish(storage, blob)

        val result = foundation.createFullSnapshot(id, temp.root, { entries(blob) }, createdAtEpochMs = 10,
            cancelled = { true })

        assertEquals(RepositoryError.CANCELLED, (result as RepositoryResult.Failure).error)
        assertTrue(storage.list("Snapshots").isEmpty())
        assertTrue(storage.list("Staging").isEmpty())
    }

    @Test fun aFailedFullCreationNeverFallsBackToTheMetadataProfile() = runBlocking {
        val (storage, foundation, id) = setup()
        val blob = ByteArray(16) { 8 }

        val failed = foundation.createFullSnapshot(id, temp.root, { entries(blob) }, createdAtEpochMs = 10)

        assertEquals(RepositoryError.MEDIA_EVIDENCE_MISSING, (failed as RepositoryResult.Failure).error)
        assertTrue(storage.list("Snapshots").isEmpty())
        val discovery = foundation.discoverSnapshots(id, temp.root).valueOrThrow()
        assertTrue(discovery.candidates.isEmpty())
        assertNull(discovery.latest)
    }

    @Test fun discoveryRefusesAFullCandidateWhoseMediaIsGoneOrCorruptWithoutDeletingIt() = runBlocking {
        for (corrupt in listOf(false, true)) {
            val (storage, foundation, id) = setup()
            val blob = ByteArray(16) { 9 }
            publish(storage, blob)
            val full = foundation.createFullSnapshot(id, temp.root, { entries(blob) }, createdAtEpochMs = 10)
                .valueOrThrow()
            val snapshotBytes = storage.files.getValue(full.path).copyOf()
            if (corrupt) storage.files["Media/${shaOf(blob)}.jpg"] = ByteArray(16) { 10 }
            else storage.files.remove("Media/${shaOf(blob)}.jpg")

            val candidate = foundation.discoverSnapshots(id, temp.root).valueOrThrow().candidates.single()

            assertNull(candidate.snapshot)
            assertEquals(
                if (corrupt) RepositoryError.MEDIA_EVIDENCE_MISMATCH else RepositoryError.MEDIA_EVIDENCE_MISSING,
                candidate.error,
            )
            // The snapshot file itself is never rewritten, deleted or reinterpreted.
            assertArrayEquals(snapshotBytes, storage.files.getValue(full.path))
            assertNull(foundation.discoverSnapshots(id, temp.root).valueOrThrow().latest)
        }
    }

    @Test fun anOlderMetadataOnlySnapshotStaysTheNewestUsableOneWhileFullIsUnverifiable() = runBlocking {
        val (storage, foundation, id) = setup()
        val blob = ByteArray(16) { 11 }
        publish(storage, blob)
        val metadata = foundation.createMetadataSnapshot(id, temp.root, { entries(blob) }, createdAtEpochMs = 10)
            .valueOrThrow()
        val full = foundation.createFullSnapshot(id, temp.root, { entries(blob) }, createdAtEpochMs = 20)
            .valueOrThrow()
        storage.files.remove("Media/${shaOf(blob)}.jpg")

        val discovery = foundation.discoverSnapshots(id, temp.root).valueOrThrow()

        assertEquals(2, discovery.candidates.size)
        assertEquals(metadata.snapshot.identity.snapshotId, discovery.latest!!.identity.snapshotId)
        assertEquals(SnapshotEvidenceProfile.METADATA_ONLY, discovery.latest!!.evidenceProfile)
        val refused = discovery.candidates.single { it.path == full.path }
        assertNull(refused.snapshot)
        assertEquals(RepositoryError.MEDIA_EVIDENCE_MISSING, refused.error)
    }

    /**
     * The snapshot boundary is the capture, not the repository: state added after the capture belongs to
     * the next backup. The checkpoint is deterministic because the mutation happens after the capture
     * lambda has produced its immutable entries and before the repository verification runs.
     */
    @Test fun mediaAddedAfterCaptureBelongsToTheNextBackup() = runBlocking {
        val (storage, foundation, id) = setup()
        val first = ByteArray(16) { 12 }
        val second = ByteArray(20) { 13 }
        publish(storage, first)
        var captures = 0
        var visible = listOf(first)
        val capture: suspend () -> SnapshotDomainEntries = {
            captures++
            val frozen = entries(*visible.toTypedArray())
            visible = visible + second // added after this capture, before verification and publication
            frozen
        }

        val firstFull = foundation.createFullSnapshot(id, temp.root, capture, createdAtEpochMs = 10).valueOrThrow()

        assertEquals(1, captures)
        assertEquals(1, firstFull.snapshot.references.size)
        assertEquals(listOf(shaOf(first)), firstFull.snapshot.references.map { it.sha256 })
        assertEquals(1, storage.list("Snapshots").size)

        // The second capture sees the new media; its blob is not protected, so the second FULL fails.
        val secondFull = foundation.createFullSnapshot(id, temp.root, { entries(*visible.toTypedArray()) },
            createdAtEpochMs = 20)
        assertEquals(RepositoryError.MEDIA_EVIDENCE_MISSING, (secondFull as RepositoryResult.Failure).error)
        assertEquals(listOf(firstFull.path), storage.list("Snapshots").map { it.path })

        // Once the new blob is protected, the next FULL may succeed and requires both blobs.
        publish(storage, second)
        val thirdFull = foundation.createFullSnapshot(id, temp.root, { entries(*visible.toTypedArray()) },
            createdAtEpochMs = 30).valueOrThrow()
        assertEquals(2, thirdFull.snapshot.references.size)
        assertEquals(2, storage.list("Snapshots").size)
    }
}
