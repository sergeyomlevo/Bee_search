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
