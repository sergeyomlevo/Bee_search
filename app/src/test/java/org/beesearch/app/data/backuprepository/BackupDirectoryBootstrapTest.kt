package org.beesearch.app.data.backuprepository

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupDirectoryBootstrapTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun skeletonIdempotentAndPreservesIdentityAndContents() = runBlocking {
        val bootstrap = BackupDirectoryBootstrap(temporary.root, "Dev")
        assertTrue(bootstrap.ensure() is RepositoryResult.Success)
        val header = File(bootstrap.root, "repository.json"); header.writeText("existing identity")
        File(bootstrap.root, "Snapshots").delete()
        assertTrue(bootstrap.ensure() is RepositoryResult.Success)
        assertEquals("existing identity", header.readText())
        assertTrue(File(bootstrap.root, "Snapshots").isDirectory)
        assertFalse(File(temporary.root, "BeeSearch/Beta").exists())
    }
    @Test fun fileConflictNeverOverwritten() = runBlocking {
        val bootstrap = BackupDirectoryBootstrap(temporary.root, "Dev")
        bootstrap.root.mkdirs(); File(bootstrap.root, "Media").writeText("unknown")
        assertEquals(RepositoryResult.Failure(RepositoryError.DIRECTORY_CONFLICT), bootstrap.ensure())
        assertEquals("unknown", File(bootstrap.root, "Media").readText())
    }
    @Test fun privateSourceCannotEscapeAndNeverDeleted() {
        val privateRoot = temporary.newFolder("private")
        val source = File(privateRoot, "owned.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val input = PrivateBlobSource(privateRoot, source, listOf("image/jpeg"))
        assertEquals(3, input.byteSize)
        input.open().use { assertEquals(1, it.read()) }
        source.appendBytes(byteArrayOf(4))
        try { input.checkUnchanged(); fail() } catch (e: RepositoryException) { assertEquals(RepositoryError.SOURCE_CHANGED, e.error) }
        assertTrue(source.exists())
        val outside = temporary.newFile("outside")
        try { PrivateBlobSource(privateRoot, outside, listOf(null)); fail() }
        catch (e: RepositoryException) { assertEquals(RepositoryError.SOURCE_CHANGED, e.error) }
    }
    @Test fun normalizedPrivateRootAndNestedSourceAreAccepted() {
        val root = temporary.newFolder("nested-private")
        val nested = File(root, "media").apply { mkdir() }
        val source = File(nested, "one.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val aliasedLexicalRoot = File(root, ".")
        val input = PrivateBlobSource(aliasedLexicalRoot, source, listOf("image/jpeg"))
        assertEquals(3L, input.byteSize)
        input.open().use { assertEquals(1, it.read()) }
    }
}
