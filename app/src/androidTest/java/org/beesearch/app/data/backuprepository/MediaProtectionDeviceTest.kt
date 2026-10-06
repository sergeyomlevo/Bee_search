package org.beesearch.app.data.backuprepository

import android.content.Context
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.beesearch.app.BeeSearchApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real DEV acceptance for media protection on the phone's actual research media.
 *
 * This is an explicit harness, not a product entry point: nothing in the application calls protection
 * automatically and no screen exposes it yet. It uses the production service from the application
 * container, so the protected bytes really land in this device's repository.
 */
@RunWith(AndroidJUnit4::class)
class MediaProtectionDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val container get() = (context.applicationContext as BeeSearchApplication).container

    /** Device diagnostics: instrumentation stdout is not always surfaced by `am instrument -r`. */
    private fun log(message: String) {
        android.util.Log.i(TAG, message)
        println(message)
    }

    private suspend fun snapshotPaths(): List<String> {
        val discovery = container.repositorySnapshots.discover()
        assertTrue("snapshot discovery failed: $discovery", discovery is RepositoryResult.Success)
        return (discovery as RepositoryResult.Success).value.candidates.map { it.path }.sorted()
    }

    private fun resolvedState(row: CapturedMediaRow): String {
        val file: File? = try {
            when (row.kind) {
                MediaSourceKind.PHYSICAL_OBJECT_MEDIA ->
                    container.physicalObjectMediaFileStore.resolve(row.relativePath)

                MediaSourceKind.OBSERVATION_POINT_ATTACHMENT ->
                    container.attachmentFileStore.resolve(row.relativePath)
            }
        } catch (e: Exception) {
            log("S6A resolver threw for ${row.relativePath}: $e")
            null
        }
        return when {
            file == null -> "unresolvable"
            !file.isFile -> "not-a-file(${file.absolutePath})"
            else -> "file=${file.absolutePath} size=${file.length()} modified=${file.lastModified()}"
        }
    }

    @Test
    fun protectsEveryRequiredRealMediaBlobAndReusesThemOnARerun() = runBlocking {
        val snapshotsBefore = snapshotPaths()
        log("S6A snapshots before: ${snapshotsBefore.size}")

        val captured = container.mediaStateCapture.capture()
        captured.rows.forEach { row ->
            log(
                "S6A captured ${row.kind} record=${row.recordId} owner=${row.ownerId} " +
                    "path=${row.relativePath} sha=${row.sha256} size=${row.byteSize} mime=${row.mimeType} " +
                    "-> ${resolvedState(row)}",
            )
        }

        val plan = MediaProtectionPlanner.plan(captured)
        log("S6A required set: ${plan.items.size} blob(s)")
        plan.items.forEach { item ->
            log(
                "  required sha256=${item.blob.sha256} size=${item.blob.byteSize} " +
                    "ext=${item.blob.canonicalExtension} sources=${item.sources.size} " +
                    "hints=${item.blob.mimeHints}",
            )
        }
        assertTrue("the DEV research state must require at least one media blob", plan.items.isNotEmpty())

        val first = container.mediaProtection.protect(captured)
        log(
            "S6A first run: summary=${first.summary} required=${first.requiredCount} " +
                "protected=${first.protectedCount} blocker=${first.blocker} inconsistency=${first.inconsistency}",
        )
        first.results.forEach { result ->
            log(
                "  ${result.sha256} size=${result.byteSize} ext=${result.canonicalExtension} " +
                    "outcome=${result.outcome} error=${result.error} " +
                    "source=${result.source?.kind}:${result.source?.relativePath}",
            )
        }
        assertEquals(MediaProtectionSummary.ALL_PROTECTED, first.summary)
        assertEquals(first.requiredCount, first.protectedCount)

        // The second run both proves idempotency and re-verifies the published bytes: the repository
        // reports an existing blob as ALREADY_PRESENT only after hashing the canonical file again.
        val second = container.mediaProtection.protect(container.mediaStateCapture.capture())
        log("S6A second run: summary=${second.summary} protected=${second.protectedCount}")
        second.results.forEach { it -> log("  ${it.sha256} outcome=${it.outcome} error=${it.error}") }
        assertEquals(MediaProtectionSummary.ALL_PROTECTED, second.summary)
        assertTrue(
            "every required blob must be reused, not rewritten",
            second.results.all { it.outcome == MediaProtectionOutcome.ALREADY_PRESENT },
        )

        // Media protection must never publish snapshot evidence on its own.
        assertEquals(snapshotsBefore, snapshotPaths())
        log("S6A snapshots unchanged: ${snapshotsBefore.size}")
    }

    /**
     * The trusted private root is reachable through two spellings on this device (`/data/user/0` from
     * the context, `/data/data` from the filesystem) and protection accepts that alias because it is
     * the same directory. A symlink *below* that root must still be refused, both when it resolves
     * outside the root and when it stays inside it.
     */
    @Test
    fun rejectsASymlinkBelowThePrivateRootAndStillAcceptsARealFile() {
        val root = context.filesDir
        val sandbox = File(root, "s6a-symlink-probe").apply { mkdirs() }
        val insideDirectory = File(sandbox, "inside").apply { mkdirs() }
        val insideFile = File(insideDirectory, "original.jpg").apply { writeBytes(ByteArray(8) { 3 }) }
        val outsideDirectory = File(context.cacheDir, "s6a-symlink-outside").apply { mkdirs() }
        File(outsideDirectory, "outside.jpg").writeBytes(ByteArray(9) { 4 })
        val linkOutside = File(sandbox, "link-outside")
        val linkInside = File(sandbox, "link-inside")

        try {
            Os.symlink(outsideDirectory.absolutePath, linkOutside.absolutePath)
            Os.symlink(insideDirectory.absolutePath, linkInside.absolutePath)
            log("S6A symlink probe: root=${root.absolutePath} canonical=${root.canonicalPath}")

            // A real file below the trusted root is still accepted, under the context's spelling.
            assertEquals(8L, PrivateBlobSource(root, insideFile, listOf("image/jpeg")).byteSize)

            assertSourceChanged(root, File(linkOutside, "outside.jpg"), "resolving outside the root")
            assertSourceChanged(root, File(linkInside, "original.jpg"), "resolving inside the root")
            log("S6A symlink boundary: both symlinked paths refused, the real file accepted")
        } finally {
            linkOutside.delete()
            linkInside.delete()
            outsideDirectory.deleteRecursively()
            sandbox.deleteRecursively()
        }
    }

    private fun assertSourceChanged(root: File, file: File, description: String) {
        try {
            PrivateBlobSource(root, file, listOf("image/jpeg"))
            fail("a symlink below the private root ($description) must be refused")
        } catch (e: RepositoryException) {
            assertEquals(RepositoryError.SOURCE_CHANGED, e.error)
        }
    }

    private companion object {
        const val TAG = "S6A_MEDIA"
    }
}
