package org.beesearch.app.data.backup

import org.beesearch.app.domain.backup.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32

/** Characterization of the pre-refactor private ZIP reader and graph parser. */
class CompleteBackupZipCharacterizationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun parsesMinimalValidV1ThroughV6Archives() {
        (1..6).forEach { format -> parse(readArchive(validArchive(format))) }
    }

    @Test fun parsesNonEmptyV6AndReorderedEntries() {
        val base = validEntries(6)
        val territory = "{\"id\":\"00000000-0000-0000-0000-000000000002\",\"code\":\"DEV\",\"name\":\"Test\",\"region\":\"R\",\"district\":\"D\",\"createdAt\":1,\"updatedAt\":1}\n"
        val entries = base + ("research/territories.json" to territory.toByteArray())
        val complete = entries + (MANIFEST to manifest(6, CONTRACTS[5]!!, entries).toByteArray())
        val file = temporary.newFile().also { it.writeBytes(zipEntries(complete.toList().reversed())) }
        parse(readArchive(file))
    }

    @Test fun readArchiveHasInclusiveEntryAndByteBoundaries() {
        assertReadOk(zipWithEntries(64, 1))
        assertReadFailure(zipWithEntries(65, 1), MalformedBackup::class.java, "too many ZIP entries")
        assertReadOk(zipWithEntries(1, 16 * 1024 * 1024))
        assertReadFailure(zipWithEntries(1, 16 * 1024 * 1024 + 1), MalformedBackup::class.java, "ZIP entry too large")
        assertReadOk(zipWithEntries(4, 16 * 1024 * 1024))
        assertReadFailure(zipWithSizes(List(4) { 16 * 1024 * 1024 } + 1), MalformedBackup::class.java, "archive is too large")
    }

    @Test fun rejectsUnsafeNamesAndDuplicatesBeforeParsing() {
        listOf("../x", "/x", "a\\b", "a//b", "a/./b", "a/../b", "a:b", "", "x/").forEach { name ->
            assertReadFailure(zipEntries(listOf(name to byteArrayOf())), MalformedBackup::class.java, "unsafe ZIP path")
        }
        assertReadFailure(duplicateZip("same"), MalformedBackup::class.java, "duplicate ZIP entry")
        assertReadFailure(zipEntries((0 until 64).map { "e$it" to byteArrayOf(1) } + ("../late" to byteArrayOf(1))), MalformedBackup::class.java, "too many ZIP entries")
    }

    @Test fun rejectsEmptyNonZipCrcDamageAndTruncation() {
        val empty = temporary.newFile("empty.zip")
        empty.writeBytes(byteArrayOf())
        assertReadFailure(empty, MalformedBackup::class.java, "empty archive")
        val nonZip = temporary.newFile("plain.zip"); nonZip.writeText("not a zip")
        assertReadFailure(nonZip, MalformedBackup::class.java, "empty archive")
        val valid = storedZip()
        val crcDamaged = temporary.newFile("crc.zip"); crcDamaged.writeBytes(valid.clone().also { it[33] = (it[33].toInt() xor 1).toByte() })
        assertReadFailure(crcDamaged, MalformedBackup::class.java, "malformed archive")
        val truncated = temporary.newFile("truncated.zip"); truncated.writeBytes(valid.copyOf(34))
        assertReadFailure(truncated, MalformedBackup::class.java, "malformed archive")
    }

    @Test fun truncatedCentralDirectoryIsAcceptedWhenLocalEntriesAreComplete() {
        val full = zipEntries(listOf("complete" to byteArrayOf(1, 2, 3)))
        val central = full.indexOfSignature(0x50, 0x4b, 0x01, 0x02)
        val file = temporary.newFile("central-truncated.zip")
        file.writeBytes(full.copyOfRange(0, central))
        assertEquals(byteArrayOf(1, 2, 3).toList(), readArchive(file).getValue("complete").toList())
    }

    @Test fun parseReportsRequiredMissingExtraMalformedAndIntegrityCases() {
        val base = validEntries(1)
        val requiredPath = "research/territories.json"
        assertParseFailure(base - requiredPath, MissingBackupCollection::class.java, "territories")
        assertParseFailure(base + ("extra.bin" to byteArrayOf(1)), MalformedBackup::class.java, "unlisted ZIP entry")
        assertParseFailure(base + (MANIFEST to "{".toByteArray()), MalformedBackup::class.java, "invalid JSON in manifest")
        val badManifest = manifest(1, CONTRACTS[1]!!.toList(), base).replace("\"sha256\":\"", "\"sha256\":\"0")
        assertParseFailure(base + (MANIFEST to badManifest.toByteArray()), BackupIntegrityMismatch::class.java, "sha256 mismatch")
        val badMeta = manifest(1, CONTRACTS[1]!!.toList(), base).replace("\"recordCount\":0", "\"recordCount\":1")
        assertParseFailure(base + (MANIFEST to badMeta.toByteArray()), BackupIntegrityMismatch::class.java, "recordCount mismatch")
        val badSize = manifest(1, CONTRACTS[1]!!, base).replaceFirst("\"byteLength\":0", "\"byteLength\":1")
        assertParseFailure(base + (MANIFEST to badSize.toByteArray()), BackupIntegrityMismatch::class.java, "byteLength mismatch")
        val malformedJson = base + (requiredPath to "{bad-json".toByteArray())
        assertParseFailure(malformedJson + (MANIFEST to manifest(1, CONTRACTS[1]!!.toList(), malformedJson).toByteArray()), MalformedBackup::class.java, "invalid JSON")
    }

    @Test fun parseRejectsVersionSchemaProfileAndCollectionDescriptors() {
        val base = validEntries(1)
        fun fail(manifest: String, type: Class<out Throwable>, message: String) = assertParseFailure(base + (MANIFEST to manifest.toByteArray()), type, message)
        fail(manifest(7, CONTRACTS[1]!!.toList(), base), UnsupportedBackupFormat::class.java, "unsupported backup format")
        fail(manifest(1, CONTRACTS[1]!!.toList(), base).replace("\"archiveSchemaVersion\":1", "\"archiveSchemaVersion\":2"), UnsupportedArchiveSchema::class.java, "unsupported archive schema")
        fail(manifest(1, CONTRACTS[1]!!.toList(), base).replace("COMPLETE_BACKUP", "WRONG"), UnsupportedBackupFormat::class.java, "unsupported profile")
        val unknown = manifest(1, CONTRACTS[1]!!.toList(), base).replace(
            "]}", ",{\"name\":\"unknown-required\",\"path\":\"x\",\"collectionSchemaVersion\":1,\"required\":true,\"recordCount\":0,\"byteLength\":0,\"sha256\":\"${"0".repeat(64)}\"}]}"
        )
        fail(unknown, UnknownRequiredBackupCollection::class.java, "unknown-required")
    }

    @Test fun parseAcceptsUnknownOptionalCollectionWhenAbsent() {
        val base = validEntries(1)
        val optional = manifest(1, CONTRACTS[1]!!.toList(), base).replace(
            "]}", ",{\"name\":\"future-optional\",\"path\":\"future/optional.json\",\"collectionSchemaVersion\":1,\"required\":false,\"recordCount\":0,\"byteLength\":0,\"sha256\":\"${"0".repeat(64)}\"}]}"
        )
        parse(base + (MANIFEST to optional.toByteArray()))
    }

    @Test fun duplicateDescriptorsAndUnsupportedCollectionSchemaAreRejected() {
        val base = validEntries(1)
        val names = CONTRACTS[1]!!
        assertParseFailure(base + (MANIFEST to manifest(1, names + names.first(), base).toByteArray()), MalformedBackup::class.java, "duplicate collection")
        assertParseFailure(base + (MANIFEST to manifest(1, names, base).replaceFirst("\"collectionSchemaVersion\":1", "\"collectionSchemaVersion\":2").toByteArray()), UnsupportedArchiveSchema::class.java, "unsupported collection schema")
    }

    @Test fun unknownOptionalPresentIsValidatedAndMissingManifestIsRejected() {
        val base = validEntries(1) + ("future/data" to "hello".toByteArray())
        val descriptor = "{\"name\":\"future\",\"path\":\"future/data\",\"required\":false,\"recordCount\":0,\"byteLength\":5,\"sha256\":\"${sha(base.getValue("future/data"))}\"}"
        val manifest = manifest(1, CONTRACTS[1]!!, base).dropLast(2) + ",$descriptor]}"
        parse(base + (MANIFEST to manifest.toByteArray()))
        assertParseFailure(base + (MANIFEST to manifest.replace("\"byteLength\":5", "\"byteLength\":6").toByteArray()), BackupIntegrityMismatch::class.java, "byteLength mismatch")
        assertParseFailure(base - MANIFEST, MissingBackupCollection::class.java, MANIFEST)
    }

    private fun readArchive(file: File): Map<String, ByteArray> = invoke("readArchive", file)
    private fun assertReadFailure(bytes: ByteArray, type: Class<out Throwable>, message: String) =
        assertReadFailure(temporary.newFile().also { it.writeBytes(bytes) }, type, message)
    private fun parse(entries: Map<String, ByteArray>) { invoke<Any>("parse", entries) }

    private inline fun <reified T> invoke(name: String, arg: Any): T {
        val method: Method = Class.forName("org.beesearch.app.data.backup.BackupCoreKt").declaredMethods.single { it.name == name }
        method.isAccessible = true
        return try { method.invoke(null, arg) as T } catch (e: InvocationTargetException) { throw e.targetException }
    }

    private fun assertReadOk(file: File) { readArchive(file) }
    private fun assertReadFailure(file: File, type: Class<out Throwable>, message: String) {
        try { readArchive(file); throw AssertionError("expected $type") } catch (e: Throwable) { assertEquals(type, e.javaClass); assertTrue("${e.message}", e.message!!.contains(message)) }
    }
    private fun assertParseFailure(entries: Map<String, ByteArray>, type: Class<out Throwable>, message: String) {
        try { parse(entries); throw AssertionError("expected $type") } catch (e: Throwable) { assertEquals(type, e.javaClass); assertTrue("${e.message}", e.message!!.contains(message)) }
    }

    private fun validArchive(format: Int): File = temporary.newFile("v$format.zip").also { it.writeBytes(zipEntries(validEntries(format).toList())) }
    private fun validEntries(format: Int): Map<String, ByteArray> {
        val contract = CONTRACTS[if (format == 6) 5 else format]!!
        val values = contract.associate { (name, path) ->
            path to (if (name == "portable-settings") "{\"currentTerritoryId\":null,\"currentObserverId\":null}\n" else "").toByteArray()
        }
        return values + (MANIFEST to manifest(format, contract.toList(), values).toByteArray())
    }
    private fun manifest(format: Int, names: List<Pair<String, String>>, entries: Map<String, ByteArray>): String {
        val descriptors = names.joinToString(",") { (name, path) -> "{\"name\":\"$name\",\"path\":\"$path\",\"collectionSchemaVersion\":1,\"required\":true,\"recordCount\":${entries[path]!!.toString(Charsets.UTF_8).lineSequence().count { it.isNotBlank() }},\"byteLength\":${entries[path]!!.size},\"sha256\":\"${sha(entries[path]!!)}\"}" }
        return "{\"backupFormatVersion\":$format,\"archiveSchemaVersion\":$format,\"archiveId\":\"00000000-0000-0000-0000-000000000001\",\"createdAt\":1,\"sourceAppVersion\":\"test\",\"roomSchemaVersion\":11,\"profile\":\"COMPLETE_BACKUP\",\"collections\":[$descriptors]}"
    }

    private fun zipWithEntries(count: Int, size: Int): File = temporary.newFile("limits-$count-$size.zip").also { file ->
        ZipOutputStream(FileOutputStream(file)).use { zip -> repeat(count) { zip.putNextEntry(ZipEntry("e$it")); repeat(size / 8192) { zip.write(ZEROS) }; repeat(size % 8192) { zip.write(0) }; zip.closeEntry() } }
    }
    private fun zipWithSizes(sizes: List<Int>): File = temporary.newFile("aggregate.zip").also { file ->
        ZipOutputStream(FileOutputStream(file)).use { zip -> sizes.forEachIndexed { index, size -> zip.putNextEntry(ZipEntry("e$index")); repeat(size / 8192) { zip.write(ZEROS) }; repeat(size % 8192) { zip.write(0) }; zip.closeEntry() } }
    }
    private fun zipEntries(entries: List<Pair<String, ByteArray>>): ByteArray = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()
    private fun storedZip(): ByteArray = ByteArrayOutputStream().also { out ->
        val bytes = byteArrayOf(1, 2, 3, 4)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("crc").apply { method = ZipEntry.STORED; size = bytes.size.toLong(); crc = CRC32().apply { update(bytes) }.value })
            zip.write(bytes); zip.closeEntry()
        }
    }.toByteArray()
    private fun duplicateZip(name: String): File = temporary.newFile("duplicate.zip").also { file ->
        val first = zipEntries(listOf(name to byteArrayOf(1)))
        val second = zipEntries(listOf(name to byteArrayOf(2)))
        val central = first.indexOfSignature(0x50, 0x4b, 0x01, 0x02)
        val secondLocalEnd = second.indexOfSignature(0x50, 0x4b, 0x01, 0x02)
        file.writeBytes(first.copyOfRange(0, central) + second.copyOfRange(0, secondLocalEnd) + first.copyOfRange(central, first.size))
    }
    private fun ByteArray.indexOfSignature(vararg signature: Int): Int {
        outer@ for (i in 0..size - signature.size) { for (j in signature.indices) if ((this[i + j].toInt() and 255) != signature[j]) continue@outer; return i }
        return -1
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private companion object {
        const val MANIFEST = "manifest.json"
        val ZEROS = ByteArray(8192)
        val CONTRACTS = mapOf(
            1 to listOf("territories" to "research/territories.json", "observers" to "research/observers.json", "observation-points" to "research/observation-points.json", "bees" to "research/bees.json", "flight-cycles" to "research/flight-cycles.json", "portable-settings" to "settings/portable-settings.json", "map-coverage" to "settings/map-coverage.json"),
            2 to listOf("territories" to "research/territories.json", "observers" to "research/observers.json", "observation-points" to "research/observation-points.json", "bees" to "research/bees.json", "flight-cycles" to "research/flight-cycles.json", "observation-point-weather" to "research/observation-point-weather.json", "observation-point-attachments" to "research/observation-point-attachments.json", "portable-settings" to "settings/portable-settings.json", "map-coverage" to "settings/map-coverage.json"),
            3 to listOf("territories" to "research/territories.json", "observers" to "research/observers.json", "physical-objects" to "research/physical-objects.json", "apiaries" to "research/apiaries.json", "observation-points" to "research/observation-points.json", "bees" to "research/bees.json", "flight-cycles" to "research/flight-cycles.json", "observation-point-weather" to "research/observation-point-weather.json", "observation-point-attachments" to "research/observation-point-attachments.json", "portable-settings" to "settings/portable-settings.json", "map-coverage" to "settings/map-coverage.json"),
            4 to listOf("territories" to "research/territories.json", "observers" to "research/observers.json", "physical-objects" to "research/physical-objects.json", "hollows" to "research/hollows.json", "log-hives" to "research/log-hives.json", "physical-object-media" to "research/physical-object-media.json", "apiaries" to "research/apiaries.json", "observation-points" to "research/observation-points.json", "bees" to "research/bees.json", "flight-cycles" to "research/flight-cycles.json", "observation-point-weather" to "research/observation-point-weather.json", "observation-point-attachments" to "research/observation-point-attachments.json", "portable-settings" to "settings/portable-settings.json", "map-coverage" to "settings/map-coverage.json"),
            5 to listOf("territories" to "research/territories.json", "observers" to "research/observers.json", "physical-objects" to "research/physical-objects.json", "hollows" to "research/hollows.json", "log-hives" to "research/log-hives.json", "physical-object-media" to "research/physical-object-media.json", "apiaries" to "research/apiaries.json", "physical-object-sequences" to "research/physical-object-sequences.json", "observation-points" to "research/observation-points.json", "bees" to "research/bees.json", "flight-cycles" to "research/flight-cycles.json", "observation-point-weather" to "research/observation-point-weather.json", "observation-point-attachments" to "research/observation-point-attachments.json", "portable-settings" to "settings/portable-settings.json", "map-coverage" to "settings/map-coverage.json"),
            6 to emptyList(),
        )
    }
}
