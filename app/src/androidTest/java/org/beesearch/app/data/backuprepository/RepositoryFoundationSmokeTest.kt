package org.beesearch.app.data.backuprepository

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.Assume.assumeTrue

/** Opt-in, bounded, DEV-only. Existing user media, roots and R0 evidence are never touched. */
class RepositoryFoundationSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @Before fun optIn() {
        assumeTrue("Disposable smoke must be explicitly selected", InstrumentationRegistry.getArguments().getString("repositorySmokeRunId") != null)
        safety()
    }
    private val runId: String get() = UUID.fromString(
        InstrumentationRegistry.getArguments().getString("repositorySmokeRunId") ?: error("Opt-in runId required"),
    ).toString()
    private val publicRoot: File get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "BeeSearch/_poc/ProductionSlice1/$runId/Backup")
    private val fixtures: File get() = File(context.cacheDir, "repository-slice1-smoke/$runId")
    private val tree: Uri get() = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents",
        "primary:Download/BeeSearch/_poc/ProductionSlice1/$runId/Backup")

    private fun safety() {
        assertEquals("org.beesearch.app.dev", context.packageName)
        assertEquals("SM-S938B", Build.MODEL)
        assertEquals(36, Build.VERSION.SDK_INT)
    }
    private fun foundation(): RepositoryFoundation {
        safety()
        context.contentResolver.takePersistableUriPermission(tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return RepositoryFoundation(storage(), "Dev")
    }
    private fun storage() = SafRepositoryStorage(context, tree, publicRoot, context.filesDir)
    private fun publicHash(path: String): String = storage().openReader(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val b = ByteArray(128 * 1024)
        while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun source(name: String, mime: String): PrivateBlobSource = PrivateBlobSource(
        context.cacheDir, File(fixtures, name), listOf(mime), hash(File(fixtures, name)),
    )
    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val b = ByteArray(128 * 1024)
            while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    @Test fun prepare(): Unit = runBlocking {
        safety()
        assertFalse("A fresh runId is required", publicRoot.exists())
        assertFalse(fixtures.exists())
        assertTrue(BackupDirectoryBootstrap(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Dev").ensure() is RepositoryResult.Success)
        assertTrue(publicRoot.mkdirs()); assertTrue(fixtures.mkdirs())
        SyntheticRepositoryMedia.createJpeg(File(fixtures, "source.jpg"), android.graphics.Color.YELLOW)
        SyntheticRepositoryMedia.createJpeg(File(fixtures, "conflict.jpg"), android.graphics.Color.BLUE)
        SyntheticRepositoryMedia.createMp4(File(fixtures, "source.mp4"))
        assertTrue(File(fixtures, "source.mp4").length() > 0)
        Log.i("RepositorySlice1", "PREPARED runId=$runId root=$publicRoot tree=$tree")
    }
    @Test fun publish(): Unit = runBlocking {
        val f = foundation()
        val header = (f.initialize() as RepositoryResult.Success).value
        File(fixtures, "repository-id.txt").writeText(header.repositoryId.toString())
        for ((name, mime) in listOf("source.jpg" to "image/jpeg", "source.mp4" to "video/mp4")) {
            val blob = (f.ingest(header.repositoryId, source(name, mime)) as RepositoryResult.Success).value
            // Restartable smoke: an earlier attempt may have initialized the header or a blob.
            assertEquals(hash(File(fixtures, name)), publicHash(blob.path))
            assertEquals(File(fixtures, name).length(), storage().inspect(blob.path)!!.byteSize)
            assertTrue((f.ingest(header.repositoryId, source(name, mime)) as RepositoryResult.Success).value.alreadyPresent)
            Log.i("RepositorySlice1", "COMMITTED ${blob.path} bytes=${blob.byteSize} SHA=${blob.sha256}")
        }
        assertEquals(2, storage().list("Media").size)
        assertTrue(storage().list("Staging").isEmpty())
        Log.i("RepositorySlice1", "PUBLISH PASS repositoryId=${header.repositoryId}")
    }
    @Test fun restartAndConflict(): Unit = runBlocking {
        val f = foundation()
        val id = UUID.fromString(File(fixtures, "repository-id.txt").readText())
        assertEquals(id, (f.open(id) as RepositoryResult.Success).value.repositoryId)
        for ((name, mime) in listOf("source.jpg" to "image/jpeg", "source.mp4" to "video/mp4")) {
            assertTrue((f.ingest(id, source(name, mime)) as RepositoryResult.Success).value.alreadyPresent)
        }
        val blob = (f.ingest(id, source("conflict.jpg", "image/jpeg")) as RepositoryResult.Success).value
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree,
            "primary:Download/BeeSearch/_poc/ProductionSlice1/$runId/Backup/${blob.path}")
        val corrupt = File(fixtures, "conflict.jpg").readBytes().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(corrupt) }
        assertEquals(RepositoryResult.Failure(RepositoryError.VERIFY_FAILED), f.ingest(id, source("conflict.jpg", "image/jpeg")))
        assertEquals(blob.byteSize, storage().inspect(blob.path)!!.byteSize)
        assertNotEquals(blob.sha256, publicHash(blob.path))
        assertEquals(3, storage().list("Media").size)
        assertTrue(File(fixtures, "conflict.jpg").isFile)
        Log.i("RepositorySlice1", "RESTART UUID + DUPLICATE + CORRUPT TARGET FAIL-CLOSED PASS id=$id")
    }
}
