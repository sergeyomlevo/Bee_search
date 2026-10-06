package org.beesearch.app.data.backupsnapshot

import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.beesearch.app.data.backup.Graph
import org.beesearch.app.data.backup.PortableSettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class SnapshotArchiveTest {
    private val identity = SnapshotIdentity(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        UUID.fromString("22222222-2222-2222-2222-222222222222"),
        "Dev",
        1_728_000_000_000L,
    )

    @Test fun `writes exact seventeen entries and validates`() {
        val root = Files.createTempDirectory("snapshot-archive").toFile()
        try {
            val file = File(root, "snapshot.zip")
            val result = SnapshotArchive().build(file, identity, SnapshotDomainCodec.encode(
                Graph(emptyList(), emptyList(), points = emptyList(), bees = emptyList(), cycles = emptyList()),
                PortableSettingsSnapshot(null, null, emptyMap()),
            ))
            assertEquals(identity, result.identity)
            ZipFile(file).use { assertEquals(SnapshotContract.paths.toSet(), it.entries().asSequence().map { entry -> entry.name }.toSet()) }
        } finally { root.deleteRecursively() }
    }

    @Test fun `parseable byte mutation fails entry digest`() {
        val root = Files.createTempDirectory("snapshot-archive").toFile()
        try {
            val file = File(root, "snapshot.zip")
            SnapshotArchive().build(file, identity, SnapshotDomainCodec.encode(
                Graph(emptyList(), emptyList(), points = emptyList(), bees = emptyList(), cycles = emptyList()),
                PortableSettingsSnapshot(null, null, emptyMap()),
            ))
            val changed = File(root, "changed.zip")
            ZipFile(file).use { input -> ZipOutputStream(changed.outputStream()).use { output ->
                input.entries().asSequence().forEach { entry ->
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(if (entry.name == "settings/portable.json") (" \n" + bytes.toString(Charsets.UTF_8)).toByteArray() else bytes)
                    output.closeEntry()
                }
            } }
            try { SnapshotArchive().validate(changed, identity.repositoryId, identity.variant) }
            catch (error: SnapshotException) { assertEquals(SnapshotError.ENTRY_DIGEST_MISMATCH, error.error); return }
            error("expected entry digest mismatch")
        } finally { root.deleteRecursively() }
    }

    @Test fun `wrong media reference counter rejects otherwise intact archive`() {
        val root = Files.createTempDirectory("snapshot-counter").toFile()
        try {
            val file = File(root, "snapshot.zip")
            SnapshotArchive().build(file, identity, SnapshotDomainCodec.encode(
                Graph(emptyList(), emptyList(), points = emptyList(), bees = emptyList(), cycles = emptyList()),
                PortableSettingsSnapshot(null, null, emptyMap()),
            ))
            val changed = File(root, "changed.zip")
            ZipFile(file).use { input -> ZipOutputStream(changed.outputStream()).use { output ->
                input.entries().asSequence().forEach { entry ->
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(if (entry.name == "manifest.json") bytes.toString(Charsets.UTF_8)
                        .replace("\"recordCount\":0", "\"recordCount\":1").toByteArray() else bytes)
                    output.closeEntry()
                }
            } }
            assertEquals(SnapshotError.LOGICAL_STATE_INCONSISTENT,
                assertThrows(SnapshotException::class.java) {
                    SnapshotArchive().validate(changed, identity.repositoryId, identity.variant)
                }.error)
        } finally { root.deleteRecursively() }
    }
}
