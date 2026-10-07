package org.beesearch.app.ui.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.beesearch.app.data.backuprepository.BackupAccessOutcome
import org.beesearch.app.data.backuprepository.BackupAccessProblem
import org.beesearch.app.data.backuprepository.BackupAccessStatus
import org.beesearch.app.data.backuprepository.BackupCreateOutcome
import org.beesearch.app.data.backuprepository.BackupSnapshotStatus

/**
 * What the backup screen can show. Small and explicit on purpose: repository error names and
 * storage detail stay below this boundary, and every state names exactly one user action.
 *
 * One model covers the whole screen: repository access (S1), what the repository currently holds,
 * and the state of a manual backup creation.
 */
internal sealed interface BackupScreenState {
    /** The fixed backup path is not known yet. */
    data object Loading : BackupScreenState

    /** An access check, an access setup attempt or a snapshot read is running. */
    data object Working : BackupScreenState

    /** No access yet: the user must grant it to the fixed folder. */
    data class NeedsGrant(val path: String) : BackupScreenState

    /** Repository access is established and reachable. */
    data class Ready(
        val path: String,
        /** What the repository holds. [BackupSnapshotsUi.Reading] claims nothing until it is read. */
        val snapshots: BackupSnapshotsUi = BackupSnapshotsUi.Reading,
        val operation: BackupOperationUi = BackupOperationUi.Idle,
    ) : BackupScreenState

    /** Access is gone or was never persisted; granting it again is offered. */
    data class AccessLost(val path: String, val message: String) : BackupScreenState

    /**
     * The folder cannot be used as this variant's repository; no automatic repair is offered.
     *
     * [offerAccess] is set when the user's own selection was the problem (a different folder), so the
     * screen offers the system picker again instead of a plain re-check.
     */
    data class Failed(
        val path: String,
        val message: String,
        val offerAccess: Boolean = false,
    ) : BackupScreenState
}

/** What the repository currently holds, in user terms. Never a UUID, SHA, file name or profile token. */
internal sealed interface BackupSnapshotsUi {
    /** The repository has not been read yet in this visit. */
    data object Reading : BackupSnapshotsUi

    /** No backup exists yet. */
    data object None : BackupSnapshotsUi

    /**
     * The latest recognized published snapshot time.
     *
     * [warning] records unrecognized snapshot containers. This ordinary read makes no claim about
     * current media evidence; the operation's verified count is transient and lives separately.
     */
    data class Latest(val createdAt: String, val warning: String?) : BackupSnapshotsUi

    /** No valid backup could be read; no last-backup claim is made. */
    data class Problem(val message: String) : BackupSnapshotsUi
}

/** The state of a manual backup creation, kept next to the access state in one screen model. */
internal sealed interface BackupOperationUi {
    data object Idle : BackupOperationUi

    /** A creation is running; the screen offers no second one and no percentage. */
    data object Creating : BackupOperationUi

    /** A committed and verified backup was created in this attempt. */
    data class Created(val verifiedMediaCount: Int = 0) : BackupOperationUi

    /** The required media set was not fully protected; no snapshot was published. */
    data class MediaFailed(val failedCount: Int, val totalCount: Int) : BackupOperationUi

    /** The attempt stopped before finishing; never shown as success. */
    data object Cancelled : BackupOperationUi

    /** The attempt failed with a problem the user can act on. */
    data class Problem(val message: String) : BackupOperationUi
}

/** The path fragment of the current state, so the screen renders one path line in every state. */
internal val BackupScreenState.pathOrEmpty: String
    get() = when (this) {
        BackupScreenState.Loading, BackupScreenState.Working -> ""
        is BackupScreenState.NeedsGrant -> path
        is BackupScreenState.Ready -> path
        is BackupScreenState.AccessLost -> path
        is BackupScreenState.Failed -> path
    }

internal fun BackupAccessStatus.toScreenState(
    path: String,
    snapshots: BackupSnapshotsUi = BackupSnapshotsUi.Reading,
    operation: BackupOperationUi = BackupOperationUi.Idle,
): BackupScreenState = when (this) {
    BackupAccessStatus.GrantRequired -> BackupScreenState.NeedsGrant(path)
    is BackupAccessStatus.Ready -> BackupScreenState.Ready(path, snapshots, operation)
    is BackupAccessStatus.AccessLost -> BackupScreenState.AccessLost(path, accessProblemMessage(problem))
    is BackupAccessStatus.Failed -> BackupScreenState.Failed(path, accessProblemMessage(problem))
}

internal fun BackupAccessOutcome.toScreenState(path: String): BackupScreenState = when (this) {
    is BackupAccessOutcome.Ready ->
        BackupScreenState.Ready(path, BackupSnapshotsUi.Reading, BackupOperationUi.Idle)

    BackupAccessOutcome.WrongFolder ->
        BackupScreenState.Failed(path, WRONG_FOLDER_MESSAGE, offerAccess = true)

    is BackupAccessOutcome.Failed -> if (problem.recoverableByGrant) {
        BackupScreenState.AccessLost(path, accessProblemMessage(problem))
    } else {
        BackupScreenState.Failed(path, accessProblemMessage(problem))
    }
}

/**
 * Repository state in user terms.
 *
 * The date is formatted for a Russian-speaking user in the device's own time zone; the snapshot
 * identity, file name and profile stay below this boundary.
 */
internal fun BackupSnapshotStatus.toSnapshotsUi(
    zone: ZoneId,
    locale: Locale,
): BackupSnapshotsUi = when (this) {
    BackupSnapshotStatus.Unknown -> BackupSnapshotsUi.Reading
    BackupSnapshotStatus.None -> BackupSnapshotsUi.None
    is BackupSnapshotStatus.Latest -> BackupSnapshotsUi.Latest(
        createdAt = formatBackupTime(createdAtEpochMs, zone, locale),
        warning = if (hasUnusable) BACKUP_SNAPSHOT_WARNING else null,
    )

    BackupSnapshotStatus.Unusable -> BackupSnapshotsUi.Problem(BACKUP_UNUSABLE_MESSAGE)
    is BackupSnapshotStatus.Failed -> BackupSnapshotsUi.Problem(accessProblemMessage(problem))
}

/** The screen state after one create attempt, without pretending anything it did not verify. */
internal fun BackupCreateOutcome.toScreenState(
    path: String,
    zone: ZoneId,
    locale: Locale,
    snapshots: BackupSnapshotsUi,
): BackupScreenState = when (this) {
    is BackupCreateOutcome.Created ->
        BackupScreenState.Ready(
            path,
            this.snapshots.toSnapshotsUi(zone, locale),
            BackupOperationUi.Created(verifiedMediaCount),
        )

    is BackupCreateOutcome.MediaFailed ->
        BackupScreenState.Ready(
            path,
            snapshots,
            BackupOperationUi.MediaFailed(failedCount, totalCount),
        )

    BackupCreateOutcome.Cancelled ->
        BackupScreenState.Ready(path, snapshots, BackupOperationUi.Cancelled)

    is BackupCreateOutcome.Failed ->
        BackupScreenState.Ready(path, snapshots, BackupOperationUi.Problem(accessProblemMessage(problem)))

    // A running creation keeps its own state; the caller leaves the screen untouched.
    BackupCreateOutcome.AlreadyRunning -> BackupScreenState.Ready(path, snapshots, BackupOperationUi.Creating)
}

/**
 * Where a failed backup belongs.
 *
 * Only a genuine access loss keeps the S1 access states: the fixed folder cannot be reached at all,
 * so the screen offers «Восстановить доступ». A storage operation that failed *while* access was
 * working — a write, sync, verify or publication failure — stays on this screen with its own message
 * and a retry, because re-granting access would not explain or fix it.
 */
internal val BackupAccessProblem.belongsToAccessState: Boolean
    get() = this == BackupAccessProblem.PERMISSION || this == BackupAccessProblem.UNSUPPORTED_LOCATION

/**
 * The screen for a failed creation whose problem *could* be an access loss.
 *
 * The S1 access states replace the failure only when a fresh access probe actually confirms that
 * access is gone. An access probe cannot reproduce every access-class verdict — publication
 * capability, for example, is only exercised while publishing — and granting access again cannot
 * explain or fix a storage failure, so an unconfirmed problem keeps its own message, a retry and the
 * previously known last copy instead of turning into a silent healthy screen.
 */
internal fun accessClassFailureScreenState(
    path: String,
    failure: BackupCreateOutcome.Failed,
    accessAfterFailure: BackupAccessStatus,
    previousSnapshots: BackupSnapshotsUi,
    zone: ZoneId,
    locale: Locale,
): BackupScreenState = if (accessAfterFailure is BackupAccessStatus.Ready) {
    failure.toScreenState(path, zone, locale, previousSnapshots)
} else {
    accessAfterFailure.toScreenState(path)
}

/** Concise, actionable Russian text per grouped problem. No provider or enum names are shown. */
internal fun accessProblemMessage(problem: BackupAccessProblem): String = when (problem) {
    BackupAccessProblem.PERMISSION -> "Нет доступа к папке резервных копий."
    BackupAccessProblem.UNSUPPORTED_LOCATION ->
        "Bee Search работает только с папкой резервных копий в основной памяти устройства."

    BackupAccessProblem.PROVIDER -> "Не удалось получить доступ к папке резервных копий."
    BackupAccessProblem.IDENTITY_MISMATCH ->
        "В этой папке находится другая резервная копия Bee Search. Подключение не изменено."

    BackupAccessProblem.REPOSITORY_MISSING ->
        "В папке нет подключённой ранее резервной копии Bee Search. Ничего не изменено."

    BackupAccessProblem.REPOSITORY_NOT_RECOGNIZED ->
        "Файл резервной копии в этой папке повреждён или создан другой версией Bee Search. " +
            "Ничего не изменено."

    BackupAccessProblem.AMBIGUOUS_OR_INVALID ->
        "Bee Search не может использовать эту папку: в ней есть файлы, которые он не создавал."

    BackupAccessProblem.BINDING -> "Состояние доступа к папке повреждено. Повторите попытку."
    BackupAccessProblem.CAPACITY ->
        "На устройстве недостаточно свободного места для резервных копий Bee Search."

    BackupAccessProblem.LOGICAL_DATA ->
        "Не удалось согласованно сохранить текущее состояние данных исследований. " +
            "Данные не изменены — повторите попытку."

    BackupAccessProblem.SNAPSHOT ->
        "Не удалось создать и проверить резервную копию. Существующие копии не изменены."

    BackupAccessProblem.CANCELLED -> BACKUP_CANCELLED_MESSAGE
}

/** The latest backup time as a normal Russian date and time, e.g. `6 октября 2026, 20:15`. */
internal fun formatBackupTime(epochMillis: Long, zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale)
        .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

internal const val WRONG_FOLDER_MESSAGE =
    "Нужно разрешить доступ именно к папке резервных копий Bee Search. Другая папка не подходит."

/** The Downloads collection as the user sees it in a file manager. */
internal const val USER_VISIBLE_DOWNLOADS = "Загрузки"

internal const val BACKUP_PATH_PREFIX = "Резервные копии Bee Search сохраняются в:"
internal const val BACKUP_GRANT_EXPLANATION =
    "Чтобы создавать резервные копии, Android должен разрешить Bee Search доступ к этой папке."
internal const val BACKUP_READY_LABEL = "✓ Доступно"
internal const val BACKUP_WORKING_LABEL = "Проверка доступа к папке…"

internal const val BACKUP_SECTION_TITLE = "Что сохраняется в резервной копии"
internal const val BACKUP_EXPLANATION = "Данные исследований, настройки, фото и видео"
internal const val BACKUP_PROMISE =
    "Копия хранится на этом телефоне. Передача на компьютер выполняется отдельно."
internal const val BACKUP_LAST_PREFIX = "Последняя резервная копия"
internal const val BACKUP_NO_SNAPSHOTS = "Резервных копий пока нет"
internal const val BACKUP_SNAPSHOT_WARNING = "Не удалось проверить одну из сохранённых копий."
internal const val BACKUP_UNUSABLE_MESSAGE =
    "Ни одну из сохранённых копий не удалось проверить. Новые копии можно создавать."

internal const val BACKUP_CREATE_ACTION = "Создать резервную копию"
internal const val BACKUP_CREATE_RETRY = "Повторить"
internal const val BACKUP_CREATING = "Создание резервной копии…"
internal const val BACKUP_CREATED_TITLE = "Резервная копия создана"
internal const val BACKUP_CREATED_BODY =
    "Данные исследований и относящиеся к ним фото и видео сохранены в резервном хранилище на этом телефоне."
internal const val BACKUP_CREATED_NO_MEDIA_BODY =
    "Данные исследований и настройки сохранены в резервном хранилище на этом телефоне."
internal const val BACKUP_MEDIA_ERROR_TITLE = "Резервная копия не создана"
internal const val BACKUP_MEDIA_ERROR_PREFIX = "Не удалось сохранить"
internal const val BACKUP_MEDIA_ERROR_SUFFIX = "файлов фото или видео."
internal const val BACKUP_MEDIA_ERROR_RETRY =
    "Уже сохранённые файлы останутся в резервном хранилище. Повторите попытку."
internal const val BACKUP_CANCELLED_MESSAGE = "Создание резервной копии не завершено."
