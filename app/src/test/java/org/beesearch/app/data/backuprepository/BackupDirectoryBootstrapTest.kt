package org.beesearch.app.data.backuprepository

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
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

    /**
     * The device exposes the app-private root through two spellings of the same directory
     * (`/data/user/0` from the application context, `/data/data` from the filesystem), so a source
     * that really is inside the private root can carry a different raw spelling than that root.
     * Containment must therefore be decided on the resolved forms, as
     * `docs/repository-v1-foundation.md` already documents for the pinned canonical mapping.
     */
    @Test fun sourceInsideTheRootIsAcceptedUnderADifferentRawSpellingOfThatRoot() {
        val root = temporary.newFolder("aliased-private").canonicalFile
        val source = File(root, "owned.jpg").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val alias = driveCaseAliasOf(root)
        assertEquals(root.canonicalPath, alias.canonicalPath)
        val input = PrivateBlobSource(alias, source, listOf("image/jpeg"))
        assertEquals(3L, input.byteSize)
        input.open().use { assertEquals(7, it.read()) }
        input.checkUnchanged()
    }

    @Test fun sourceOutsideTheRootIsStillRefusedWithAnAliasedRoot() {
        val root = temporary.newFolder("aliased-private-2").canonicalFile
        val alias = driveCaseAliasOf(root)
        val outside = temporary.newFile("aliased-outside")
        try {
            PrivateBlobSource(alias, outside, listOf(null))
            fail()
        } catch (e: RepositoryException) {
            assertEquals(RepositoryError.SOURCE_CHANGED, e.error)
        }
    }

    /**
     * A link below the trusted root that resolves outside it must never be followed, while the
     * bind-mount alias of the root itself stays accepted (see the two tests above).
     *
     * A Windows host cannot create a real symlink without elevation, so this uses a directory
     * junction, which is a reparse point created without elevation. The symlink branch inside the
     * trusted boundary is proved on the real device in `MediaProtectionDeviceTest`.
     */
    @Test fun aLinkBelowTheRootThatResolvesOutsideItIsRefused() {
        val root = temporary.newFolder("link-private").canonicalFile
        val outside = temporary.newFolder("link-outside")
        File(outside, "secret.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
        val created = createDirectoryLink(File(root, "link"), outside)
        assumeTrue("this host cannot create a directory link", created != null)
        val link = created!!

        try {
            PrivateBlobSource(root, File(link, "secret.jpg"), listOf("image/jpeg"))
            fail("a link below the private root must not be followed out of it")
        } catch (e: RepositoryException) {
            assertEquals(RepositoryError.SOURCE_CHANGED, e.error)
        } finally {
            link.delete()
        }
    }

    /** A real symlink where the host allows one, otherwise a junction, otherwise null. */
    private fun createDirectoryLink(link: File, target: File): File? {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            return link
        } catch (_: Exception) {
            // Windows without elevation: fall back to a directory junction.
        }
        return try {
            ProcessBuilder("cmd", "/c", "mklink", "/J", link.absolutePath, target.absolutePath)
                .redirectErrorStream(true)
                .start()
                .waitFor()
            link.takeIf { it.exists() }
        } catch (_: Exception) {
            null
        }
    }

    private fun driveCaseAliasOf(root: File): File {
        val path = root.canonicalPath
        assumeTrue("this host has no drive-letter spelling to alias: $path", path.startsWith("C:"))
        return File("c:" + path.removePrefix("C:"))
    }
}
