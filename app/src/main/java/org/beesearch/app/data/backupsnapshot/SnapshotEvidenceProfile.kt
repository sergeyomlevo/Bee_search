package org.beesearch.app.data.backupsnapshot

/**
 * The closed Snapshot V1 evidence-profile model (wire contract §7.1).
 *
 * Profile, evidence policy and creation result are immutable manifest evidence: they are written once
 * and read back as declared. They are never inferred from repository contents, and old snapshots never
 * change meaning when media bytes happen to exist.
 *
 * Exactly two tuples are supported. Every other combination is an unsupported manifest, not a
 * partially valid one: it is refused instead of being given a fallback meaning.
 */
internal enum class SnapshotProfile(val token: String) {
    METADATA_ONLY("METADATA_ONLY"),
    FULL("FULL"),
}

internal enum class SnapshotEvidencePolicy(val token: String) {
    NO_MEDIA_EVIDENCE("NO_MEDIA_EVIDENCE"),
    LOCAL_VERIFIED("LOCAL_VERIFIED"),
}

internal enum class SnapshotCreationResult(val token: String) {
    COMPLETE("COMPLETE"),
}

internal data class SnapshotEvidenceProfile(
    val profile: SnapshotProfile,
    val evidencePolicy: SnapshotEvidencePolicy,
    val creationResult: SnapshotCreationResult,
) {
    /**
     * True for the local full-evidence profile: the capture's required media set must be strongly
     * verified in the same bound repository before this snapshot may be published as usable.
     */
    val requiresRepositoryMediaEvidence: Boolean get() = this == FULL_LOCAL_VERIFIED

    val supported: Boolean get() = this == METADATA_ONLY || this == FULL_LOCAL_VERIFIED

    val token: String get() = "${profile.token}/${evidencePolicy.token}/${creationResult.token}"

    companion object {
        /** Research metadata only; makes no claim about media bytes. */
        val METADATA_ONLY = SnapshotEvidenceProfile(
            SnapshotProfile.METADATA_ONLY,
            SnapshotEvidencePolicy.NO_MEDIA_EVIDENCE,
            SnapshotCreationResult.COMPLETE,
        )

        /** The same capture's required media set was verified in the same bound local repository. */
        val FULL_LOCAL_VERIFIED = SnapshotEvidenceProfile(
            SnapshotProfile.FULL,
            SnapshotEvidencePolicy.LOCAL_VERIFIED,
            SnapshotCreationResult.COMPLETE,
        )

        val SUPPORTED = listOf(METADATA_ONLY, FULL_LOCAL_VERIFIED)

        /** Refuses every unsupported profile/policy/result combination, including unknown tokens. */
        fun parse(profile: String, evidencePolicy: String, creationResult: String): SnapshotEvidenceProfile {
            val candidate = SnapshotEvidenceProfile(
                SnapshotProfile.entries.firstOrNull { it.token == profile } ?: invalid(),
                SnapshotEvidencePolicy.entries.firstOrNull { it.token == evidencePolicy } ?: invalid(),
                SnapshotCreationResult.entries.firstOrNull { it.token == creationResult } ?: invalid(),
            )
            if (!candidate.supported) invalid()
            return candidate
        }

        private fun invalid(): Nothing = throw SnapshotException(SnapshotError.INVALID_FORMAT, "MANIFEST")
    }
}
