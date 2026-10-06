package org.beesearch.app.data.backupsnapshot

import java.io.ByteArrayOutputStream
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

class SnapshotSafetyTest {
    private val identity = SnapshotIdentity(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        UUID.fromString("22222222-2222-2222-2222-222222222222"), "Dev", 1_728_000_000_000L,
    )

    private fun entries() = SnapshotDomainCodec.encode(
        Graph(emptyList(), emptyList(), points = emptyList(), bees = emptyList(), cycles = emptyList()),
        PortableSettingsSnapshot(null, null, emptyMap()),
    )

    private fun valid(root: File, limits: SnapshotLimits = SnapshotLimits()): File {
        val file = File(root, "snapshot.zip")
        SnapshotArchive(limits).build(file, identity, entries())
        return file
    }

    private fun expectInvalid(file: File, limits: SnapshotLimits = SnapshotLimits()) {
        try {
            SnapshotArchive(limits).validate(file, identity.repositoryId, identity.variant)
            error("expected SnapshotException")
        } catch (error: SnapshotException) {
            assertTrue(error.error in setOf(SnapshotError.INVALID_ZIP, SnapshotError.INVALID_FORMAT,
                SnapshotError.SNAPSHOT_LIMIT_EXCEEDED, SnapshotError.ENTRY_DIGEST_MISMATCH,
                SnapshotError.READ_FAILED))
        }
    }

    private fun rewrite(source: File, target: File, transform: (String, ByteArray) -> Pair<String, ByteArray>) {
        ZipFile(source).use { input -> ZipOutputStream(target.outputStream()).use { output ->
            input.entries().asSequence().forEach { entry ->
                val (name, bytes) = transform(entry.name, input.getInputStream(entry).use { it.readBytes() })
                output.putNextEntry(ZipEntry(name)); output.write(bytes); output.closeEntry()
            }
        } }
    }

    @Test fun traversalAbsoluteBackslashMissingUnexpectedAndDuplicateEntriesFail() {
        val root = Files.createTempDirectory("snapshot-safety").toFile()
        try {
            val source = valid(root)
            listOf("../manifest.json", "/manifest.json", "C:/manifest.json", "data\\x").forEachIndexed { n, unsafe ->
                val out = File(root, "unsafe$n.zip")
                rewrite(source, out) { name, bytes -> if (name == "manifest.json") unsafe to bytes else name to bytes }
                expectInvalid(out)
            }
            val missing = File(root, "missing.zip")
            rewrite(source, missing) { name, bytes -> if (name == "manifest.json") "unexpected.json" to bytes else name to bytes }
            expectInvalid(missing)
            val duplicate = File(root, "duplicate.zip")
            ZipOutputStream(duplicate.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("entry-a")); zip.write("{}".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("entry-b")); zip.write("{}".toByteArray()); zip.closeEntry()
            }
            val raw = duplicate.readBytes(); val from = "entry-b".toByteArray(); val to = "entry-a".toByteArray()
            for (offset in 0..raw.size - from.size) if (from.indices.all { raw[offset + it] == from[it] }) to.copyInto(raw, offset)
            duplicate.writeBytes(raw)
            expectInvalid(duplicate)
        } finally { root.deleteRecursively() }
    }

    @Test fun malformedCentralDirectoryAndActualExpansionBombFail() {
        val root = Files.createTempDirectory("snapshot-safety").toFile()
        try {
            val source = valid(root)
            val truncated = File(root, "truncated.zip")
            truncated.writeBytes(source.readBytes().copyOf(source.length().toInt() - 12)); expectInvalid(truncated)
            val bomb = File(root, "bomb.zip")
            ZipOutputStream(bomb.outputStream()).use { zip ->
                SnapshotContract.paths.forEach { path ->
                    zip.putNextEntry(ZipEntry(path)); zip.write(if (path == "settings/portable.json") ByteArray(2 * 1024 * 1024) else ByteArray(0)); zip.closeEntry()
                }
            }
            expectInvalid(bomb, SnapshotLimits(portableBytes = 1024))
        } finally { root.deleteRecursively() }
    }

    @Test fun unsupportedManifestValuesAndParseableByteChangeFailClosed() {
        val root = Files.createTempDirectory("snapshot-safety").toFile()
        try {
            val source = valid(root)
            listOf("METADATA_ONLY" to "UNKNOWN_PROFILE", "COMPLETE" to "DEGRADED", "NO_MEDIA_EVIDENCE" to "UNKNOWN_POLICY", "snapshotFormatVersion\":1" to "snapshotFormatVersion\":2").forEachIndexed { n, (from, to) ->
                val out = File(root, "manifest$n.zip")
                rewrite(source, out) { name, bytes -> if (name == "manifest.json") name to String(bytes).replace(from, to).toByteArray() else name to bytes }
                expectInvalid(out)
            }
            val changed = File(root, "changed.zip")
            rewrite(source, changed) { name, bytes -> if (name == "settings/portable.json") name to (" " + String(bytes)).toByteArray() else name to bytes }
            expectInvalid(changed)
        } finally { root.deleteRecursively() }
    }

    @Test fun measuredLimitEqualityPassesAndOneBelowFails() {
        val root = Files.createTempDirectory("snapshot-safety").toFile()
        try {
            val source = valid(root)
            val metrics = SnapshotArchive().validate(source, identity.repositoryId, identity.variant).metrics
            SnapshotArchive(SnapshotLimits(zipBytes = source.length(), totalBytes = metrics.totalBytes)).validate(source, identity.repositoryId, identity.variant)
            expectInvalid(source, SnapshotLimits(zipBytes = source.length() - 1))
            expectInvalid(source, SnapshotLimits(totalBytes = metrics.totalBytes - 1))
            SnapshotArchive(SnapshotLimits(referencesBytes = 0, recordBytes = 0)).validate(source, identity.repositoryId, identity.variant)
            expectInvalid(source, SnapshotLimits(manifestBytes = 1))
        } finally { root.deleteRecursively() }
    }
}
