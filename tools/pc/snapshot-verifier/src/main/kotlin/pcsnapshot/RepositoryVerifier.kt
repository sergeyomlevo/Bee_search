package pcsnapshot

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

/** One blob the selected snapshot requires, and what the repository actually holds for it. */
data class RequiredBlobCheck(
    val sha256: String,
    val byteSize: Long,
    val canonicalExtension: String,
    val expectedPath: String,
    val outcome: String,
    val actualSha256: String? = null,
    val actualByteSize: Long? = null,
    /** Context for a check that did not pass; null for a verified blob, where the outcome says it all. */
    val message: String? = null,
)

data class RepositoryVerificationReport(
    val repositoryRoot: String,
    val snapshotPath: String,
    var repositoryId: String? = null,
    var variant: String? = null,
    var snapshotVerdict: String? = null,
    var snapshotId: String? = null,
    var required: MutableList<RequiredBlobCheck> = mutableListOf(),
    var extras: MutableList<String> = mutableListOf(),
    var issues: MutableList<Issue> = mutableListOf(),
) {
    /** The declared, immutable manifest evidence of the verified snapshot. Never inferred here. */
    var snapshotEvidenceProfile: EvidenceProfile? = null
    var snapshotProfile: String? = null
    var evidencePolicy: String? = null
    var creationResult: String? = null

    /**
     * True when the snapshot itself declares the local full-evidence profile, i.e. when the required
     * blobs below are a requirement of that snapshot rather than a coincidence of the current
     * repository contents.
     */
    val repositoryEvidenceRequired: Boolean get() = snapshotEvidenceProfile?.repositoryEvidenceRequired == true

    val verdict: String get() = if (issues.isEmpty()) "PASS" else "FAIL"
    val verifiedRequired: Int get() = required.count { it.outcome == VERIFIED }
    val requiredCount: Int get() = required.size

    companion object {
        const val VERIFIED = "VERIFIED"
        const val MISSING = "MISSING"
        const val NOT_REGULAR = "NOT_REGULAR_FILE"
        const val SIZE_MISMATCH = "SIZE_MISMATCH"
        const val SHA_MISMATCH = "SHA_MISMATCH"
        const val READ_ERROR = "READ_ERROR"
    }
}

/**
 * Repository-aware verification, implemented from the documented text and independent of Android code.
 *
 * It answers a second question the standalone snapshot verifier cannot: does the repository actually
 * hold the bytes the selected snapshot requires? The standalone mode is unchanged and remains the
 * authority for the snapshot itself; this mode reuses it for that part and adds the repository context.
 *
 * Rules:
 *
 * - the repository header is format/version/UUID/variant identity, never the directory path;
 * - the snapshot must be a valid Snapshot V1 file;
 * - snapshot repository UUID and variant must match the header;
 * - every blob in the snapshot's `references/media-blobs.jsonl` must exist at
 *   `Media/<sha256>.<canonicalExtension>` as a regular file with the exact size and SHA;
 * - additional valid blobs are allowed (older or newer snapshots may require them);
 * - a blob present under a second canonical name, or a Media entry that is not a canonical blob, is a
 *   repository-integrity problem rather than an allowed extra.
 */
class RepositoryVerifier(private val snapshotVerifier: Verifier = Verifier()) {
    private val canonicalBlob = Regex("^([0-9a-f]{64})\\.(jpg|mp4|bin)$")

    fun verify(repositoryRoot: Path, snapshot: Path): RepositoryVerificationReport {
        val report = RepositoryVerificationReport(repositoryRoot.toString(), snapshot.toString())
        val header = readHeader(repositoryRoot, report)
        val snapshotReport = try {
            snapshotVerifier.verify(snapshot)
        } catch (_: Exception) {
            report.issues += Issue("SNAPSHOT_UNREADABLE", snapshot.toString())
            return report
        }
        report.snapshotVerdict = snapshotReport.verdict
        // The declared manifest evidence is authoritative and is reported as declared: this mode never
        // promotes a metadata-only snapshot to FULL and never demotes a FULL snapshot to metadata-only.
        report.snapshotEvidenceProfile = snapshotReport.evidenceProfile
        report.snapshotProfile = snapshotReport.snapshotProfile
        report.evidencePolicy = snapshotReport.evidencePolicy
        report.creationResult = snapshotReport.creationResult
        if (snapshotReport.issues.isNotEmpty()) {
            report.issues += Issue("SNAPSHOT_INVALID", snapshot.toString(), message = snapshotReport.issues.first().code)
            return report
        }
        val manifest = readManifest(snapshot, report) ?: return report
        report.snapshotId = manifest.string("snapshotId")
        val snapshotRepositoryId = manifest.string("repositoryId")
        val snapshotVariant = manifest.string("variant")

        if (header != null) {
            if (snapshotRepositoryId != header.string("repositoryId")) {
                report.issues += Issue("REPOSITORY_UUID_MISMATCH", "context",
                    observed = snapshotRepositoryId, expected = header.string("repositoryId"))
            }
            if (snapshotVariant != header.string("variant")) {
                report.issues += Issue("VARIANT_MISMATCH", "context",
                    observed = snapshotVariant, expected = header.string("variant"))
            }
        }

        val references = readReferences(snapshot, report)
        val requiredPaths = references.mapTo(mutableSetOf()) { "Media/${it.sha256}.${it.canonicalExtension}" }
        references.forEach { reference ->
            val expectedPath = "Media/${reference.sha256}.${reference.canonicalExtension}"
            val check = checkBlob(repositoryRoot, expectedPath, reference)
            report.required += check
            if (check.outcome != RepositoryVerificationReport.VERIFIED) {
                report.issues += Issue(check.outcome, expectedPath,
                    message = check.message ?: check.outcome, observed = check.actualSha256, expected = reference.sha256)
            }
        }

        inspectMediaExtras(repositoryRoot, requiredPaths, references, report)
        return report
    }

    private fun readHeader(root: Path, report: RepositoryVerificationReport): JsonObject? {
        val file = root.resolve("repository.json")
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            report.issues += Issue("REPOSITORY_HEADER_MISSING", file.toString())
            return null
        }
        val size = try { Files.size(file) } catch (_: Exception) { -1L }
        if (size !in 1..4096) {
            report.issues += Issue("REPOSITORY_HEADER_INVALID", file.toString(), observed = size.toString(), expected = "1..4096")
            return null
        }
        val parsed = try {
            SafeJson.parse(Files.readAllBytes(file))
        } catch (e: Exception) {
            report.issues += Issue("REPOSITORY_HEADER_INVALID", file.toString(), message = e.message ?: "malformed")
            return null
        }
        if (!parsed.isJsonObject) {
            report.issues += Issue("REPOSITORY_HEADER_INVALID", file.toString())
            return null
        }
        val header = parsed.asJsonObject
        if (header.keySet() != setOf("repositoryFormat", "repositoryFormatVersion", "repositoryId", "variant")) {
            report.issues += Issue("REPOSITORY_HEADER_INVALID", file.toString(), message = "unexpected key set")
            return null
        }
        if (header.string("repositoryFormat") != "beesearch-repository") {
            report.issues += Issue("REPOSITORY_HEADER_INVALID", file.toString(), message = "wrong format")
            return null
        }
        if (header.number("repositoryFormatVersion") != 1L) {
            report.issues += Issue("REPOSITORY_HEADER_INVALID", file.toString(), message = "unsupported version")
            return null
        }
        val id = header.string("repositoryId")
        if (id == null || !isCanonicalUuid(id)) {
            report.issues += Issue("REPOSITORY_HEADER_INVALID", file.toString(), message = "invalid repositoryId")
            return null
        }
        if (header.string("variant") !in setOf("Dev", "Beta", "Stable")) {
            report.issues += Issue("REPOSITORY_HEADER_INVALID", file.toString(), message = "invalid variant")
            return null
        }
        report.repositoryId = id
        report.variant = header.string("variant")
        return header
    }

    private fun readManifest(snapshot: Path, report: RepositoryVerificationReport): JsonObject? = try {
        ZipFile(snapshot.toFile()).use { zip ->
            val entry = zip.getEntry("manifest.json")
            if (entry == null) {
                report.issues += Issue("SNAPSHOT_INVALID", "manifest.json", message = "missing")
                null
            } else {
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                SafeJson.parse(bytes).takeIf { it.isJsonObject }?.asJsonObject ?: run {
                    report.issues += Issue("SNAPSHOT_INVALID", "manifest.json", message = "not an object")
                    null
                }
            }
        }
    } catch (e: Exception) {
        report.issues += Issue("SNAPSHOT_UNREADABLE", snapshot.toString(), message = e.message ?: "read error")
        null
    }

    private fun readReferences(snapshot: Path, report: RepositoryVerificationReport): List<RequiredReference> = try {
        ZipFile(snapshot.toFile()).use { zip ->
            val entry = zip.getEntry("references/media-blobs.jsonl") ?: return emptyList()
            val lines = zip.getInputStream(entry).use { it.readBytes() }
                .toString(Charsets.UTF_8)
                .lineSequence()
                .filter { it.isNotEmpty() }
                .toList()
            val result = mutableListOf<RequiredReference>()
            for (line in lines) {
                val row = SafeJson.parse(line.toByteArray(Charsets.UTF_8), canonical = true).asJsonObject
                val sha = row.string("sha256")
                val size = row.number("byteSize")
                val extension = row.string("canonicalExtension")
                if (sha == null || !sha.matches(Regex("^[0-9a-f]{64}$")) || size == null || size < 1 ||
                    extension == null || extension !in setOf("jpg", "mp4", "bin")
                ) {
                    report.issues += Issue("REFERENCE_INVALID", "references/media-blobs.jsonl", message = line)
                    continue
                }
                result += RequiredReference(sha, size, extension)
            }
            if (result.map { it.sha256 }.distinct().size != result.size) {
                report.issues += Issue("REFERENCE_DUPLICATE", "references/media-blobs.jsonl")
            }
            result
        }
    } catch (e: Exception) {
        report.issues += Issue("SNAPSHOT_UNREADABLE", "references/media-blobs.jsonl", message = e.message ?: "read error")
        emptyList()
    }

    private fun checkBlob(root: Path, expectedPath: String, reference: RequiredReference): RequiredBlobCheck {
        val file = root.resolve(expectedPath)
        val base = RequiredBlobCheck(
            sha256 = reference.sha256,
            byteSize = reference.byteSize,
            canonicalExtension = reference.canonicalExtension,
            expectedPath = expectedPath,
            outcome = RepositoryVerificationReport.MISSING,
        )
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return base.copy(message = "no such file in the repository Media directory")
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return base.copy(
                outcome = RepositoryVerificationReport.NOT_REGULAR,
                message = "the canonical path is not a regular file",
            )
        }
        val size = try { Files.size(file) } catch (_: Exception) { -1L }
        if (size != reference.byteSize) {
            return base.copy(outcome = RepositoryVerificationReport.SIZE_MISMATCH, actualByteSize = size,
                message = "size $size != required ${reference.byteSize}")
        }
        val actual = try {
            Files.newInputStream(file).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            return base.copy(outcome = RepositoryVerificationReport.READ_ERROR, message = e.message ?: "read error")
        }
        if (actual != reference.sha256) {
            return base.copy(outcome = RepositoryVerificationReport.SHA_MISMATCH, actualSha256 = actual,
                actualByteSize = size, message = "content hash differs from the required identity")
        }
        return base.copy(outcome = RepositoryVerificationReport.VERIFIED, actualSha256 = actual, actualByteSize = size)
    }

    private fun inspectMediaExtras(
        root: Path,
        requiredPaths: Set<String>,
        references: List<RequiredReference>,
        report: RepositoryVerificationReport,
    ) {
        val mediaDirectory = root.resolve("Media")
        if (!Files.isDirectory(mediaDirectory, LinkOption.NOFOLLOW_LINKS)) {
            if (references.isNotEmpty()) {
                report.issues += Issue("MEDIA_DIRECTORY_MISSING", "Media")
            }
            return
        }
        val requiredBySha = references.associateBy { it.sha256 }
        val entries = try { Files.list(mediaDirectory).use { it.toList() } } catch (e: Exception) {
            report.issues += Issue("MEDIA_DIRECTORY_UNREADABLE", "Media", message = e.message ?: "read error")
            return
        }
        for (entry in entries) {
            val name = entry.fileName.toString()
            val relative = "Media/$name"
            if (relative in requiredPaths) continue
            val match = canonicalBlob.matchEntire(name)
            if (match == null) {
                report.issues += Issue("NONCANONICAL_MEDIA_ENTRY", relative)
                continue
            }
            val sha = match.groupValues[1]
            val required = requiredBySha[sha]
            if (required != null) {
                // The same blob under a second canonical name: Repository V1 identity is ambiguous.
                report.issues += Issue("DUPLICATE_SHA_ENTRY", relative,
                    message = "the same blob is already required as Media/$sha.${required.canonicalExtension}")
                continue
            }
            report.extras += relative
        }
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.asString

    private fun JsonObject.number(name: String): Long? =
        (get(name) as? JsonPrimitive)?.takeIf { !it.isString }?.asLong

    private fun isCanonicalUuid(value: String): Boolean = try {
        UUID.fromString(value).toString() == value
    } catch (_: IllegalArgumentException) {
        false
    }
}

private data class RequiredReference(val sha256: String, val byteSize: Long, val canonicalExtension: String)
