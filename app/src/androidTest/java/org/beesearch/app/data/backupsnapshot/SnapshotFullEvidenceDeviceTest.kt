package org.beesearch.app.data.backupsnapshot

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.beesearch.app.BeeSearchApplication
import org.beesearch.app.data.backuprepository.RepositoryResult
import org.beesearch.app.data.backuprepository.SnapshotDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicit DEV harness for the local full-evidence profile on the owner's real repository.
 *
 * This is not a product entry point: nothing in the application calls [RepositorySnapshotService.createFull]
 * and the backup screen still creates the metadata-only profile. The harness never ingests media, never
 * deletes or rewrites anything, and reads the repository only through the production service.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotFullEvidenceDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = (context.applicationContext as BeeSearchApplication).container

    private fun log(message: String) {
        android.util.Log.i(TAG, message)
        println(message)
    }

    private suspend fun discovery(): SnapshotDiscovery =
        (container.repositorySnapshots.discover() as RepositoryResult.Success).value

    private fun describe(discovery: SnapshotDiscovery): String =
        discovery.candidates.joinToString(" | ") { candidate ->
            val snapshot = candidate.snapshot
            "${candidate.path.substringAfterLast('/')} error=${candidate.error} " +
                "profile=${snapshot?.evidenceProfile?.token} references=${snapshot?.references?.size}"
        }

    /**
     * Creation through the production backend only: one capture, its required media set verified in the
     * same bound repository, and nothing ingested.
     */
    @Test
    fun createsAnExplicitLocalFullEvidenceSnapshot() = runBlocking {
        val before = discovery()
        val metadataReferenceSets = before.candidates.mapNotNull { it.snapshot }
            .filter { !it.evidenceProfile.requiresRepositoryMediaEvidence }
            .map { snapshot -> snapshot.references.map { it.sha256 to it.byteSize }.toSet() }
        log("S6B before: candidates=${before.candidates.size} usable=${before.candidates.count { it.snapshot != null }}")

        val created = container.repositorySnapshots.createFull()
        assertTrue("FULL creation failed: $created", created is RepositoryResult.Success)
        val committed = (created as RepositoryResult.Success).value
        val snapshot = committed.snapshot

        log(
            "S6B created path=${committed.path} id=${snapshot.identity.snapshotId} " +
                "sha=${snapshot.wholeSha256} size=${snapshot.byteSize} entries=${snapshot.metrics.entryBytes.size} " +
                "profile=${snapshot.evidenceProfile.token} references=${snapshot.references.size}",
        )
        snapshot.references.forEach { log("S6B required sha256=${it.sha256} size=${it.byteSize} ext=${it.canonicalExtension}") }

        assertEquals(SnapshotEvidenceProfile.FULL_LOCAL_VERIFIED, snapshot.evidenceProfile)
        assertTrue(snapshot.evidenceProfile.requiresRepositoryMediaEvidence)
        assertEquals(17, snapshot.metrics.entryBytes.size)
        assertTrue("a FULL snapshot must require the captured media set", snapshot.references.isNotEmpty())
        // The same immutable capture produced these references as the metadata-only snapshots of the
        // same research state, and every reference is strongly verified in this repository.
        val referenceSet = snapshot.references.map { it.sha256 to it.byteSize }.toSet()
        assertTrue("expected an accepted metadata reference set: $referenceSet", metadataReferenceSets.contains(referenceSet))
        assertTrue(snapshot.references.all { it.canonicalExtension in setOf("jpg", "mp4", "bin") })

        val after = discovery()
        log("S6B after: ${describe(after)}")
        assertEquals(before.candidates.size + 1, after.candidates.size)
        val full = after.candidates.mapNotNull { it.snapshot }.filter { it.evidenceProfile.requiresRepositoryMediaEvidence }
        assertEquals(1, full.size)
        assertEquals(snapshot.wholeSha256, full.single().wholeSha256)
        assertTrue(after.candidates.mapNotNull { it.snapshot }.any { !it.evidenceProfile.requiresRepositoryMediaEvidence })
    }

    /**
     * Runs in a separate instrumentation process: discovery must reconstruct the declared evidence from
     * repository contents alone, with no stored "last FULL" flag anywhere.
     */
    @Test
    fun discoveryReconstructsDeclaredEvidenceWithoutAnyLocalFlag() = runBlocking {
        val found = discovery()
        log("S6B discovery: ${describe(found)}")

        val usable = found.candidates.mapNotNull { it.snapshot }
        val full = usable.filter { it.evidenceProfile.requiresRepositoryMediaEvidence }
        val metadata = usable.filter { !it.evidenceProfile.requiresRepositoryMediaEvidence }
        assertEquals("exactly one usable local FULL snapshot", 1, full.size)
        assertTrue("the earlier metadata-only snapshots stay usable", metadata.isNotEmpty())
        assertTrue("every candidate is either usable or carries a typed failure",
            found.candidates.all { it.snapshot != null || it.error != null })
        full.single().references.forEach {
            log("S6B discovered required sha256=${it.sha256} size=${it.byteSize} ext=${it.canonicalExtension}")
        }
        assertEquals("the newest usable snapshot is the declared FULL one",
            full.single().identity.snapshotId, found.latest!!.identity.snapshotId)
        assertTrue("the metadata-only profile is still what the old snapshots declare",
            metadata.all { !it.evidenceProfile.requiresRepositoryMediaEvidence })
    }

    private companion object {
        const val TAG = "S6B_FULL"
    }
}
