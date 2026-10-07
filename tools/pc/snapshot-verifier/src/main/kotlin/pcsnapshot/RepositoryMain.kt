package pcsnapshot

import com.google.gson.GsonBuilder
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Repository-aware verification entry point: repository root plus one snapshot file.
 *
 * Human summary to stderr; exactly one JSON report to stdout. Read-only: the repository and the
 * snapshot are never modified. The standalone `verify-snapshot` command is unchanged.
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("Usage: verify-repository <repository-root> <snapshot-file.zip>")
        println(
            GsonBuilder().serializeNulls().disableHtmlEscaping().create().toJson(
                mapOf("verdict" to "FAIL", "issues" to listOf(Issue("INVALID_ARGUMENTS"))),
            ),
        )
        exitProcess(2)
    }
    val report = try {
        RepositoryVerifier().verify(Path.of(args[0]), Path.of(args[1]))
    } catch (e: Exception) {
        RepositoryVerificationReport(args[0], args[1]).also {
            it.issues += Issue("INTERNAL_VERIFIER_ERROR", message = e.message ?: "unexpected failure")
        }
    }

    System.err.println(
        "${report.verdict}: repository ${report.repositoryId ?: "?"} variant ${report.variant ?: "?"}; " +
            "snapshot ${report.snapshotId ?: "?"} (${report.snapshotVerdict ?: "?"}); " +
            "declared ${report.snapshotProfile ?: "?"}/${report.evidencePolicy ?: "?"}/${report.creationResult ?: "?"}; " +
            "required ${report.verifiedRequired}/${report.requiredCount} verified; ${report.issues.size} issues",
    )
    report.required.forEach { System.err.println("- ${it.outcome} ${it.sha256} ${it.canonicalExtension} ${it.byteSize}") }
    report.extras.forEach { System.err.println("- EXTRA_ALLOWED $it") }
    report.issues.forEach { System.err.println("- ${it.code} ${it.scope} ${it.message}") }

    val output = linkedMapOf(
        "repositoryRoot" to report.repositoryRoot,
        "snapshotPath" to report.snapshotPath,
        "repositoryId" to report.repositoryId,
        "variant" to report.variant,
        "snapshotId" to report.snapshotId,
        "snapshotVerdict" to report.snapshotVerdict,
        "snapshotProfile" to report.snapshotProfile,
        "evidencePolicy" to report.evidencePolicy,
        "creationResult" to report.creationResult,
        "repositoryEvidenceRequired" to report.repositoryEvidenceRequired,
        "requiredVerified" to report.verifiedRequired,
        "requiredCount" to report.requiredCount,
        "required" to report.required,
        "extras" to report.extras,
        "verdict" to report.verdict,
        "issues" to report.issues,
    )
    println(GsonBuilder().serializeNulls().disableHtmlEscaping().create().toJson(output))
    exitProcess(if (report.issues.isEmpty()) 0 else 1)
}
