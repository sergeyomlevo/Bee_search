package org.beesearch.app.domain.backup

internal object BackupContractV1 {
    const val FORMAT = 1
    const val SCHEMA = 1
    const val PROFILE = "COMPLETE_BACKUP"
    val collections = linkedMapOf(
        "territories" to "research/territories.json",
        "observers" to "research/observers.json",
        "observation-points" to "research/observation-points.json",
        "bees" to "research/bees.json",
        "flight-cycles" to "research/flight-cycles.json",
        "portable-settings" to "settings/portable-settings.json",
        "map-coverage" to "settings/map-coverage.json",
    )
}

/** Logical schema used by complete backups after ObservationPoint properties. */
internal object BackupContractV2 {
    const val FORMAT = 2
    const val SCHEMA = 2
    const val PROFILE = BackupContractV1.PROFILE
    val collections = linkedMapOf(
        "territories" to "research/territories.json",
        "observers" to "research/observers.json",
        "observation-points" to "research/observation-points.json",
        "bees" to "research/bees.json",
        "flight-cycles" to "research/flight-cycles.json",
        "observation-point-weather" to "research/observation-point-weather.json",
        "observation-point-attachments" to "research/observation-point-attachments.json",
        "portable-settings" to "settings/portable-settings.json",
        "map-coverage" to "settings/map-coverage.json",
    )

    const val ATTACHMENT_PREFIX = "attachments/"
}

/** Complete backup schema carrying the D088 physical-object branch and Bee provenance link. */
internal object BackupContractV3 {
    const val FORMAT = 3
    const val SCHEMA = 3
    const val PROFILE = BackupContractV1.PROFILE
    val collections = linkedMapOf(
        "territories" to "research/territories.json",
        "observers" to "research/observers.json",
        "physical-objects" to "research/physical-objects.json",
        "apiaries" to "research/apiaries.json",
        "observation-points" to "research/observation-points.json",
        "bees" to "research/bees.json",
        "flight-cycles" to "research/flight-cycles.json",
        "observation-point-weather" to "research/observation-point-weather.json",
        "observation-point-attachments" to "research/observation-point-attachments.json",
        "portable-settings" to "settings/portable-settings.json",
        "map-coverage" to "settings/map-coverage.json",
    )

    const val ATTACHMENT_PREFIX = BackupContractV2.ATTACHMENT_PREFIX
}

/** Complete backup schema for physical-object characteristics, provenance and media. */
internal object BackupContractV4 {
    const val FORMAT = 4
    const val SCHEMA = 4
    const val PROFILE = BackupContractV1.PROFILE
    val collections = linkedMapOf(
        "territories" to "research/territories.json",
        "observers" to "research/observers.json",
        "physical-objects" to "research/physical-objects.json",
        "hollows" to "research/hollows.json",
        "log-hives" to "research/log-hives.json",
        "physical-object-media" to "research/physical-object-media.json",
        "apiaries" to "research/apiaries.json",
        "observation-points" to "research/observation-points.json",
        "bees" to "research/bees.json",
        "flight-cycles" to "research/flight-cycles.json",
        "observation-point-weather" to "research/observation-point-weather.json",
        "observation-point-attachments" to "research/observation-point-attachments.json",
        "portable-settings" to "settings/portable-settings.json",
        "map-coverage" to "settings/map-coverage.json",
    )

    const val ATTACHMENT_PREFIX = BackupContractV3.ATTACHMENT_PREFIX
    const val OBJECT_MEDIA_PREFIX = "physical-object-media/"
}
internal sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
internal class UnsupportedBackupFormat(message: String) : BackupException(message)
internal class UnsupportedArchiveSchema(message: String) : BackupException(message)
internal class MalformedBackup(message: String, cause: Throwable? = null) : BackupException(message, cause)
internal class BackupIntegrityMismatch(message: String) : BackupException(message)
internal class MissingBackupCollection(message: String) : BackupException(message)
internal class UnknownRequiredBackupCollection(message: String) : BackupException(message)
internal class DuplicateBackupIdentity(message: String) : BackupException(message)
internal class BrokenBackupForeignKey(message: String) : BackupException(message)
internal class BackupDomainInvariantViolation(message: String) : BackupException(message)
internal class BackupDestinationNotEmpty(message: String) : BackupException(message)
internal class BackupDatabaseRestoreFailure(message: String, cause: Throwable? = null) : BackupException(message, cause)
internal class BackupSettingsRestoreFailure(message: String, cause: Throwable? = null) : BackupException(message, cause)
