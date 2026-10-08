package org.beesearch.app.data.backuprepository

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.beesearch.app.data.backupsnapshot.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositorySnapshotsTest {
    @get:Rule val temp = TemporaryFolder()
    private fun entries(version: Int = 2) = SnapshotDomainCodec.encode(Graph(emptyList(), emptyList(),
        points = emptyList(), bees = emptyList(), cycles = emptyList()),
        PortableSettingsSnapshot(null, null, emptyMap()), version = version)
    private suspend fun setup(): Triple<MemoryRepositoryStorage, RepositoryFoundation, UUID> {
        val s = MemoryRepositoryStorage()
        val f = RepositoryFoundation(s, "Dev")
        return Triple(s, f, f.initializeNew().valueOrThrow().repositoryId)
    }
    @Test fun publicationReopensAndDiscoverySurvivesNewFoundation() = runBlocking {
        val (s, f, id) = setup()
        val first = f.createMetadataSnapshot(id, temp.root, ::entries, createdAtEpochMs = 10).valueOrThrow()
        assertEquals(17, first.snapshot.metrics.entryBytes.size)
        assertEquals(first.snapshot.wholeSha256, SnapshotJson.sha(s.files.getValue(first.path)))
        assertTrue(first.path.endsWith("-${first.snapshot.wholeSha256}.zip"))
        assertTrue(s.list("Media").isEmpty())
        val second = f.createMetadataSnapshot(id, temp.root, ::entries, createdAtEpochMs = 20).valueOrThrow()
        val discovery = RepositoryFoundation(s, "Dev").discoverSnapshots(id, temp.root).valueOrThrow()
        assertEquals(2, discovery.candidates.count { it.snapshot != null })
        assertEquals(second.snapshot.identity.snapshotId, discovery.latest!!.identity.snapshotId)
        assertTrue(s.list("Staging").isEmpty())
    }

    @Test fun mixedV1AndV2DiscoveryKeepsBothValidAndSelectsLatest() = runBlocking {
        val (s, f, id) = setup()
        val v1 = f.createMetadataSnapshot(id, temp.root, { entries(version = 1) }, createdAtEpochMs = 30).valueOrThrow()
        val v2 = f.createMetadataSnapshot(id, temp.root, { entries(version = 2) }, createdAtEpochMs = 20).valueOrThrow()
        val discovery = RepositoryFoundation(s, "Dev").discoverSnapshots(id, temp.root).valueOrThrow()
        assertEquals(2, discovery.candidates.count { it.snapshot != null })
        assertEquals(1, discovery.candidates.count { it.snapshot?.formatVersion == 1 })
        assertEquals(1, discovery.candidates.count { it.snapshot?.formatVersion == 2 })
        assertEquals(v1.snapshot.identity.snapshotId, discovery.latest!!.identity.snapshotId)
        assertEquals(1, discovery.latest!!.formatVersion)
        assertEquals(v2.snapshot.identity.snapshotId, discovery.candidates.first { it.snapshot?.formatVersion == 2 }.snapshot!!.identity.snapshotId)
    }
    @Test fun allStorageFailuresPreservePriorHistory() = runBlocking {
        for (fault in listOf(RepositoryError.WRITE_FAILED, RepositoryError.SYNC_FAILED,
            RepositoryError.PUBLISH_FAILED, RepositoryError.UNSUPPORTED_PUBLICATION_PATH)) {
            val (s, f, id) = setup()
            val previous = f.createMetadataSnapshot(id, temp.root, ::entries).valueOrThrow()
            val saved = s.files.getValue(previous.path).copyOf()
            s.fault = fault
            assertEquals(fault, (f.createMetadataSnapshot(id, temp.root, ::entries) as RepositoryResult.Failure).error)
            assertArrayEquals(saved, s.files.getValue(previous.path))
            assertEquals(1, s.list("Snapshots").size)
        }
    }
    @Test fun unknownAndLowCapacityRejectBeforeCaptureOrStaging() = runBlocking {
        for (available in listOf(null, 1L)) {
            val (s, f, id) = setup(); s.available = available
            var captured = false
            val result = f.createMetadataSnapshot(id, temp.root, { captured = true; entries() })
            assertEquals(if (available == null) RepositoryError.CAPACITY_UNKNOWN else RepositoryError.CAPACITY_INSUFFICIENT,
                (result as RepositoryResult.Failure).error)
            assertFalse(captured); assertTrue(s.list("Snapshots").isEmpty()); assertTrue(s.list("Staging").isEmpty())
        }
    }
    @Test fun rootChangedAfterStagingCannotPublishOrDeleteAgainstReplacement() = runBlocking {
        val (s, f, id) = setup()
        s.afterWrite = { s.files["repository.json"] = RepositoryHeaderCodec.encode(RepositoryHeader(UUID.randomUUID(), "Dev")) }
        assertEquals(RepositoryError.ROOT_IDENTITY_MISMATCH,
            (f.createMetadataSnapshot(id, temp.root, ::entries) as RepositoryResult.Failure).error)
        assertTrue(s.list("Snapshots").isEmpty()); assertFalse(s.list("Staging").isEmpty())
    }
    @Test fun replacementImmediatelyBeforePublicationFailsClosed() = runBlocking {
        val (s, _, id) = setup()
        var moved = false
        val boundaryStorage = object : RepositoryStorage by s {
            override fun inspect(path: String): RepositoryEntry? {
                if (path.startsWith("Snapshots/snapshot-")) {
                    s.files["repository.json"] = RepositoryHeaderCodec.encode(RepositoryHeader(UUID.randomUUID(), "Dev"))
                }
                return s.inspect(path)
            }
            override fun moveOwnedStage(stage: String, target: String) {
                moved = true; s.moveOwnedStage(stage, target)
            }
        }
        val result = RepositoryFoundation(boundaryStorage, "Dev").createMetadataSnapshot(id, temp.root, ::entries)
        assertEquals(RepositoryError.ROOT_IDENTITY_MISMATCH, (result as RepositoryResult.Failure).error)
        assertFalse(moved); assertTrue(s.list("Snapshots").isEmpty())
    }
    @Test fun finalCorruptionIsNotCommittedAndPreviousSnapshotRemains() = runBlocking {
        val (s, f, id) = setup()
        val good = f.createMetadataSnapshot(id, temp.root, ::entries).valueOrThrow()
        val original = s.files.getValue(good.path).copyOf()
        s.finalMutation = { it.copyOf().also { data -> data[data.lastIndex] = (data.last() + 1).toByte() } }
        assertEquals(RepositoryError.INVALID_SNAPSHOT,
            (f.createMetadataSnapshot(id, temp.root, ::entries) as RepositoryResult.Failure).error)
        assertArrayEquals(original, s.files.getValue(good.path))
        val results = f.discoverSnapshots(id, temp.root).valueOrThrow().candidates
        assertEquals(1, results.count { it.snapshot != null }); assertEquals(1, results.count { it.error != null })
    }
    @Test fun failedMoveResponseRequiresFinalFullReadback() = runBlocking {
        val (s, f, id) = setup(); s.afterMoveError = true
        val result = f.createMetadataSnapshot(id, temp.root, ::entries).valueOrThrow()
        assertEquals(result.snapshot.wholeSha256, SnapshotJson.sha(s.files.getValue(result.path)))
    }
    @Test fun capacityDropAfterStagingNeverPublishes() = runBlocking {
        val (s, f, id) = setup()
        s.afterWrite = { s.available = 1L }
        val result = f.createMetadataSnapshot(id, temp.root, ::entries)
        assertEquals(RepositoryError.CAPACITY_INSUFFICIENT, (result as RepositoryResult.Failure).error)
        assertTrue(s.list("Snapshots").isEmpty())
    }
    @Test fun cancellationAndInvalidCandidateNeverPublish() = runBlocking {
        val (s, f, id) = setup()
        assertEquals(RepositoryError.CANCELLED,
            (f.createMetadataSnapshot(id, temp.root, ::entries, cancelled = { true }) as RepositoryResult.Failure).error)
        val bad = entries().copy(portable = "not json".toByteArray())
        assertTrue(f.createMetadataSnapshot(id, temp.root, { bad }) is RepositoryResult.Failure)
        assertTrue(s.list("Snapshots").isEmpty())
    }
    @Test fun stagingUnknownAndDuplicateSnapshotIdentityRemainVisible() = runBlocking {
        val (s, f, id) = setup()
        s.directories += "Staging/unknown"; s.files["Staging/unknown/partial.zip"] = byteArrayOf(1)
        val first = f.createMetadataSnapshot(id, temp.root, ::entries).valueOrThrow()
        val duplicate = "Snapshots/snapshot-${first.snapshot.identity.snapshotId}-${"a".repeat(64)}.zip"
        s.files[duplicate] = s.files.getValue(first.path)
        s.files["Snapshots/unknown.txt"] = byteArrayOf(2)
        val found = f.discoverSnapshots(id, temp.root).valueOrThrow()
        assertNull(found.latest)
        assertEquals(2, found.candidates.count { it.error == RepositoryError.SNAPSHOT_ID_CONFLICT })
        assertEquals(1, found.candidates.count { it.error == RepositoryError.INVALID_SNAPSHOT })
        assertArrayEquals(byteArrayOf(1), s.files.getValue("Staging/unknown/partial.zip"))
    }

    @Test fun captureSerializationAndCandidateFailuresPreservePriorHistory() = runBlocking {
        val cases: List<Pair<RepositoryError, suspend () -> SnapshotDomainEntries>> = listOf(
            RepositoryError.LOGICAL_STATE_INCONSISTENT to {
                throw SnapshotException(SnapshotError.LOGICAL_STATE_INCONSISTENT, "CAPTURE")
            },
            RepositoryError.INVALID_SNAPSHOT to {
                entries().copy(records = entries().records + ("data/territories.jsonl" to listOf("\n")))
            },
            RepositoryError.LOGICAL_STATE_INCONSISTENT to {
                entries().copy(portable = "{\"currentObserverId\":null,\"currentTerritoryId\":\"${UUID.randomUUID()}\"}".toByteArray())
            },
        )
        for ((expected, capture) in cases) {
            val (s, f, id) = setup()
            val previous = f.createMetadataSnapshot(id, temp.root, ::entries).valueOrThrow()
            val saved = s.files.getValue(previous.path).copyOf()
            val result = f.createMetadataSnapshot(id, temp.root, capture)
            assertEquals(expected, (result as RepositoryResult.Failure).error)
            assertArrayEquals(saved, s.files.getValue(previous.path))
            assertEquals(listOf(previous.path), s.list("Snapshots").map { it.path })
            assertTrue(s.list("Staging").isEmpty())
        }
    }

    @Test fun stagedAndFinalReopenFailuresNeverReturnCommit() = runBlocking {
        for (atFinal in listOf(false, true)) {
            val (s, f, id) = setup()
            val previous = f.createMetadataSnapshot(id, temp.root, ::entries).valueOrThrow()
            val saved = s.files.getValue(previous.path).copyOf()
            s.beforeReader = { path ->
                if ((atFinal && path.startsWith("Snapshots/") && path != previous.path) ||
                    (!atFinal && path.startsWith("Staging/"))) {
                    throw RepositoryException(RepositoryError.VERIFY_FAILED)
                }
            }
            val result = f.createMetadataSnapshot(id, temp.root, ::entries)
            assertEquals(RepositoryError.VERIFY_FAILED, (result as RepositoryResult.Failure).error)
            assertArrayEquals(saved, s.files.getValue(previous.path))
            assertEquals(if (atFinal) 2 else 1, s.list("Snapshots").size)
            // After move a verified-on-later-discovery file may exist; failed operation
            // still returned no commit. Never roll back published files automatically.
            assertTrue(s.list("Staging").isEmpty())
        }
    }

    @Test fun stagedMutationAndCancellationAfterWritePreservePriorHistory() = runBlocking {
        for (cancel in listOf(false, true)) {
            val (s, f, id) = setup()
            val previous = f.createMetadataSnapshot(id, temp.root, ::entries).valueOrThrow()
            val saved = s.files.getValue(previous.path).copyOf()
            var cancelled = false
            if (cancel) s.afterWrite = { cancelled = true }
            else s.beforeReader = { path ->
                if (path.startsWith("Staging/")) {
                    s.files[path] = s.files.getValue(path).copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
                }
            }
            val result = f.createMetadataSnapshot(id, temp.root, ::entries, cancelled = { cancelled })
            assertEquals(if (cancel) RepositoryError.CANCELLED else RepositoryError.INVALID_SNAPSHOT,
                (result as RepositoryResult.Failure).error)
            assertArrayEquals(saved, s.files.getValue(previous.path))
            assertEquals(listOf(previous.path), s.list("Snapshots").map { it.path })
        }
    }
}
