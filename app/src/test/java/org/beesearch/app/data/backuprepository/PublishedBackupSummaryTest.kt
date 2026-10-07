package org.beesearch.app.data.backuprepository

import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backupsnapshot.SnapshotArchive
import org.beesearch.app.data.backupsnapshot.SnapshotDomainCodec
import org.beesearch.app.data.backupsnapshot.SnapshotDomainEntries
import org.beesearch.app.data.backupsnapshot.BackupSnapshotOperations
import org.beesearch.app.data.backupsnapshot.SnapshotEvidenceProfile
import org.beesearch.app.data.backupsnapshot.SnapshotIdentity
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

/** Ordinary screen summary tests. This contract validates published snapshot containers only. */
class PublishedBackupSummaryTest {
    @get:Rule val temp = TemporaryFolder()

    private fun emptyEntries(): SnapshotDomainEntries = SnapshotDomainCodec.encode(
        Graph(
            territories = emptyList(), observers = emptyList(), points = emptyList(),
            bees = emptyList(), cycles = emptyList(),
        ),
        PortableSettingsSnapshot(null, null, emptyMap()),
    )

    private fun setup(): Triple<MemoryRepositoryStorage, RepositoryFoundation, UUID> = runBlocking {
        val storage = MemoryRepositoryStorage()
        val foundation = RepositoryFoundation(storage, "Dev")
        Triple(storage, foundation, foundation.initializeNew().valueOrThrow().repositoryId)
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun candidateName(snapshotId: UUID, wholeSha: String = "0".repeat(64)) =
        "Snapshots/snapshot-$snapshotId-$wholeSha.zip"

    private fun putArchiveCandidate(
        storage: MemoryRepositoryStorage,
        repositoryId: UUID,
        variant: String = "Dev",
        createdAt: Long = 10,
        snapshotId: UUID = UUID.randomUUID(),
    ): Pair<UUID, String> {
        val file = File(temp.root, "candidate-$snapshotId.zip")
        val built = SnapshotArchive().build(
            file, SnapshotIdentity(snapshotId, repositoryId, variant, createdAt), emptyEntries(),
            evidenceProfile = SnapshotEvidenceProfile.METADATA_ONLY,
        )
        storage.files["Snapshots/snapshot-$snapshotId-${built.wholeSha256}.zip"] = file.readBytes()
        return snapshotId to built.wholeSha256
    }

    private fun mutateZipEntry(source: ByteArray, entryName: String, mutate: (ByteArray) -> ByteArray): ByteArray {
        val input = File(temp.root, "mutate-input.zip").also { it.writeBytes(source) }
        val output = File(temp.root, "mutate-output.zip")
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream()).use { out ->
                zip.entries().asSequence().forEach { entry ->
                    out.putNextEntry(ZipEntry(entry.name))
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    out.write(if (entry.name == entryName) mutate(bytes) else bytes)
                    out.closeEntry()
                }
            }
        }
        return output.readBytes()
    }

    private fun resultSummary(
        foundation: RepositoryFoundation,
        id: UUID,
    ): PublishedBackupSummary = runBlocking {
        foundation.readPublishedSummary(id, temp.root).valueOrThrow()
    }

    @Test fun metadataOnlyAndFullAreRecognizedWithoutReadingMedia() = runBlocking {
        val (storage, foundation, id) = setup()
        val media = ByteArray(32) { 3 }
        storage.files["Media/${sha(media)}.jpg"] = media
        val metadata = foundation.createMetadataSnapshot(id, temp.root, { emptyEntries() }, createdAtEpochMs = 10)
            .valueOrThrow()
        val full = foundation.createFullSnapshot(id, temp.root, { emptyEntriesWithMedia(media) }, createdAtEpochMs = 20)
            .valueOrThrow()

        storage.beforeReader = { path ->
            if (path.startsWith("Media/")) fail("published summary read media blob: $path")
        }
        val summary = foundation.readPublishedSummary(id, temp.root).valueOrThrow()

        assertEquals(full.snapshot.identity.snapshotId, summary.latest!!.snapshotId)
        assertEquals(20, summary.latest!!.createdAtEpochMs)
        assertFalse(summary.hasUnrecognizedCandidates)
        assertNotEquals(metadata.snapshot.identity.snapshotId, summary.latest!!.snapshotId)
        val operations = object : BackupSnapshotOperations {
            override suspend fun create(): RepositoryResult<CommittedSnapshot> = error("not a screen read")
            override suspend fun discover(): RepositoryResult<SnapshotDiscovery> = error("ordinary inspect called strong discovery")
            override suspend fun readPublishedSummary() = foundation.readPublishedSummary(id, temp.root)
        }
        val coordinator = BackupOperationCoordinator(operations) { error("not a screen read") }
        assertEquals(BackupSnapshotStatus.Latest(20, false), coordinator.inspect())
    }

    @Test fun missingOrSameSizeCorruptMediaDoesNotChangeSummaryButStrongDiscoverRejects() = runBlocking {
        for (corrupt in listOf(false, true)) {
            val (storage, foundation, id) = setup()
            val media = ByteArray(32) { 4 }
            storage.files["Media/${sha(media)}.jpg"] = media
            val full = foundation.createFullSnapshot(id, temp.root, { emptyEntriesWithMedia(media) }, createdAtEpochMs = 20)
                .valueOrThrow()
            if (corrupt) storage.files["Media/${sha(media)}.jpg"] = ByteArray(media.size) { 9 }
            else storage.files.remove("Media/${sha(media)}.jpg")

            val summary = foundation.readPublishedSummary(id, temp.root).valueOrThrow()
            assertEquals(full.snapshot.identity.snapshotId, summary.latest!!.snapshotId)
            val strong = foundation.discoverSnapshots(id, temp.root).valueOrThrow()
            assertNull(strong.latest)
        }
    }

    @Test fun latestUsesCreatedAtThenSnapshotUuid() = runBlocking {
        val (storage, foundation, id) = setup()
        val first = foundation.createMetadataSnapshot(id, temp.root, { emptyEntries() }, createdAtEpochMs = 20)
            .valueOrThrow()
        val second = foundation.createMetadataSnapshot(id, temp.root, { emptyEntries() }, createdAtEpochMs = 20)
            .valueOrThrow()
        val latest = if (first.snapshot.identity.snapshotId.toString() > second.snapshot.identity.snapshotId.toString()) first else second
        assertEquals(latest.snapshot.identity.snapshotId, resultSummary(foundation, id).latest!!.snapshotId)
        assertEquals(2, storage.list("Snapshots").size)
    }

    @Test fun newerInvalidCandidateLeavesOlderValidLatestAndWarning() = runBlocking {
        val (storage, foundation, id) = setup()
        val older = foundation.createMetadataSnapshot(id, temp.root, { emptyEntries() }, createdAtEpochMs = 10)
            .valueOrThrow()
        storage.files[candidateName(UUID.randomUUID())] = byteArrayOf(1, 2, 3)

        val summary = resultSummary(foundation, id)
        assertEquals(older.snapshot.identity.snapshotId, summary.latest!!.snapshotId)
        assertTrue(summary.hasUnrecognizedCandidates)
    }

    @Test fun digestMismatchMakesCandidateUnrecognizedWithoutMediaRead() = runBlocking {
        val (storage, foundation, id) = setup()
        val snapshot = foundation.createMetadataSnapshot(id, temp.root, { emptyEntries() }, createdAtEpochMs = 10)
            .valueOrThrow()
        val path = snapshot.path
        val original = storage.files.getValue(path)
        storage.files[path] = mutateZipEntry(original, "settings/portable.json") { bytes ->
            bytes.copyOf().also { it[it.lastIndex - 1] = if (it[it.lastIndex - 1] == '{'.code.toByte()) ' '.code.toByte() else '{'.code.toByte() }
        }
        val summary = resultSummary(foundation, id)
        assertNull(summary.latest)
        assertTrue(summary.hasUnrecognizedCandidates)
    }

    @Test fun repositoryAndVariantIdentityMismatchesAreUnrecognized() = runBlocking {
        val (storage, foundation, id) = setup()
        putArchiveCandidate(storage, UUID.randomUUID(), createdAt = 10)
        putArchiveCandidate(storage, id, variant = "Beta", createdAt = 20)
        val summary = resultSummary(foundation, id)
        assertNull(summary.latest)
        assertTrue(summary.hasUnrecognizedCandidates)
    }

    @Test fun duplicateSnapshotUuidIsAmbiguousAndCannotBecomeLatest() = runBlocking {
        val (storage, foundation, id) = setup()
        val snapshotId = UUID.randomUUID()
        val first = putArchiveCandidate(storage, id, createdAt = 10, snapshotId = snapshotId)
        val secondFile = File(temp.root, "duplicate-$snapshotId.zip")
        val second = SnapshotArchive().build(
            secondFile, SnapshotIdentity(snapshotId, id, "Dev", 20), emptyEntries(),
            evidenceProfile = SnapshotEvidenceProfile.METADATA_ONLY,
        )
        storage.files["Snapshots/snapshot-$snapshotId-${second.wholeSha256}.zip"] = secondFile.readBytes()
        assertNotEquals(first.second, second.wholeSha256)
        val summary = resultSummary(foundation, id)
        assertNull(summary.latest)
        assertTrue(summary.hasUnrecognizedCandidates)
    }

    @Test fun invalidEvidenceProfileIsUnrecognized() = runBlocking {
        val (storage, foundation, id) = setup()
        val valid = foundation.createMetadataSnapshot(id, temp.root, { emptyEntries() }, createdAtEpochMs = 10)
            .valueOrThrow()
        val mutated = mutateZipEntry(storage.files.getValue(valid.path), "manifest.json") { bytes ->
            bytes.toString(Charsets.UTF_8).replace("METADATA_ONLY", "UNSUPPORTED").toByteArray()
        }
        // Keep identity valid and the whole-ZIP digest correct, isolating the profile rejection.
        storage.files.remove(valid.path)
        val path = "Snapshots/snapshot-${valid.snapshot.identity.snapshotId}-${sha(mutated)}.zip"
        storage.files[path] = mutated
        val summary = resultSummary(foundation, id)
        assertNull(summary.latest)
        assertTrue(summary.hasUnrecognizedCandidates)
    }

    @Test fun emptySnapshotsHasNoLatestAndStagingIsIgnored() = runBlocking {
        val (storage, foundation, id) = setup()
        storage.directories += "Staging/foreign"
        storage.files["Staging/foreign/candidate.part"] = byteArrayOf(1)
        val summary = resultSummary(foundation, id)
        assertNull(summary.latest)
        assertFalse(summary.hasUnrecognizedCandidates)
    }

    @Test fun onlyInvalidCandidatesAreReportedAsUnrecognized() = runBlocking {
        val (storage, foundation, id) = setup()
        storage.files[candidateName(UUID.randomUUID())] = byteArrayOf(1)
        val summary = resultSummary(foundation, id)
        assertNull(summary.latest)
        assertTrue(summary.hasUnrecognizedCandidates)
    }

    @Test fun summaryLeavesRepositoryUnchangedAndCleansPrivateScratch() = runBlocking {
        val (storage, foundation, id) = setup()
        foundation.createMetadataSnapshot(id, temp.root, { emptyEntries() }, createdAtEpochMs = 10).valueOrThrow()
        val beforeFiles = storage.files.mapValues { it.value.copyOf() }
        val beforeDirectories = storage.directories.toSet()

        resultSummary(foundation, id)

        assertEquals(beforeDirectories, storage.directories)
        assertEquals(beforeFiles.keys, storage.files.keys)
        beforeFiles.forEach { (path, bytes) -> assertArrayEquals(bytes, storage.files.getValue(path)) }
        assertTrue(temp.root.listFiles()?.none { it.name.startsWith("repository-snapshot-") } ?: true)
    }

    @Test fun wrongRepositoryIdentityIsTypedFailureNotEmptySummary() = runBlocking {
        val (_, foundation, _) = setup()
        val result = foundation.readPublishedSummary(UUID.randomUUID(), temp.root)
        assertEquals(RepositoryResult.Failure(RepositoryError.ROOT_IDENTITY_MISMATCH), result)
    }

    @Test fun listFailureIsTypedFailureNotEmptySummary() = runBlocking {
        val (storage, foundation, id) = setup()
        storage.fault = RepositoryError.PERMISSION_LOST
        storage.beforeList = { if (it == "Snapshots") throw RepositoryException(RepositoryError.PERMISSION_LOST) }
        val result = foundation.readPublishedSummary(id, temp.root)
        assertEquals(RepositoryResult.Failure(RepositoryError.PERMISSION_LOST), result)
    }

    @Test fun identityMutationAfterNonEmptyListingFailsBeforeReturningSummary() = runBlocking {
        val (storage, foundation, id) = setup()
        foundation.createMetadataSnapshot(id, temp.root, { emptyEntries() }, createdAtEpochMs = 10).valueOrThrow()
        var mutated = false
        storage.beforeList = { path ->
            if (path == "Snapshots" && !mutated) {
                mutated = true
                storage.files["repository.json"] = RepositoryHeaderCodec.encode(RepositoryHeader(UUID.randomUUID(), "Dev"))
            }
        }
        val result = foundation.readPublishedSummary(id, temp.root)
        assertEquals(RepositoryResult.Failure(RepositoryError.ROOT_IDENTITY_MISMATCH), result)
    }

    @Test fun identityMutationOnEmptyListingIsRecheckedBeforeReturningEmptySummary() = runBlocking {
        val (storage, foundation, id) = setup()
        var mutated = false
        storage.beforeList = { path ->
            if (path == "Snapshots" && !mutated) {
                mutated = true
                storage.files["repository.json"] = RepositoryHeaderCodec.encode(RepositoryHeader(UUID.randomUUID(), "Dev"))
            }
        }
        val result = foundation.readPublishedSummary(id, temp.root)
        assertEquals(RepositoryResult.Failure(RepositoryError.ROOT_IDENTITY_MISMATCH), result)
    }

    /** A FULL fixture with one reference is used only to prove summary does not inspect Media. */
    private fun emptyEntriesWithMedia(bytes: ByteArray): SnapshotDomainEntries {
        val at = java.time.Instant.parse("2026-01-01T00:00:00Z")
        val territory = UUID.fromString("00000000-0000-4000-8000-000000000011")
        val observer = UUID.fromString("00000000-0000-4000-8000-000000000012")
        val objectId = UUID.fromString("00000000-0000-4000-8000-000000000013")
        return SnapshotDomainCodec.encode(
            Graph(
                territories = listOf(TerritoryEntity(territory, "T1", "Territory", "R", "D", at, at)),
                observers = listOf(ObserverEntity(observer, "O1", "Observer", "A", null, null, at, at)),
                physicalObjects = listOf(PhysicalObjectEntity(objectId, territory, PhysicalObjectType.HOLLOW, 1, 55.7, 37.6, at, observer)),
                hollows = listOf(HollowEntity(objectId, "oak", 120.0, 90, 40.0, 30.0, "h", "H")),
                objectMedia = listOf(PhysicalObjectMediaEntity(
                    UUID.fromString("00000000-0000-4000-8000-000000000014"), objectId,
                    PhysicalObjectMediaType.IMAGE, "objects/0.jpg", "0.jpg", "image/jpeg",
                    bytes.size.toLong(), sha(bytes), at,
                )),
                points = emptyList(), bees = emptyList(), cycles = emptyList(),
            ),
            PortableSettingsSnapshot(null, null, emptyMap()),
        )
    }
}
