package org.beesearch.app.ui.backup

import org.beesearch.app.data.backuprepository.BackupAccessOutcome
import org.beesearch.app.data.backuprepository.BackupAccessProblem
import org.beesearch.app.data.backuprepository.BackupAccessStatus

/**
 * What the backup screen can show. Small and explicit on purpose: repository error names and
 * storage detail stay below this boundary, and every state names exactly one user action.
 */
internal sealed interface BackupScreenState {
    /** The fixed backup path is not known yet. */
    data object Loading : BackupScreenState

    /** An access check or an access setup attempt is running. */
    data object Working : BackupScreenState

    /** No access yet: the user must grant it to the fixed folder. */
    data class NeedsGrant(val path: String) : BackupScreenState

    /** Repository access is established and reachable. */
    data class Ready(val path: String) : BackupScreenState

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

/** The path fragment of the current state, so the screen renders one path line in every state. */
internal val BackupScreenState.pathOrEmpty: String
    get() = when (this) {
        BackupScreenState.Loading, BackupScreenState.Working -> ""
        is BackupScreenState.NeedsGrant -> path
        is BackupScreenState.Ready -> path
        is BackupScreenState.AccessLost -> path
        is BackupScreenState.Failed -> path
    }

internal fun BackupAccessStatus.toScreenState(path: String): BackupScreenState = when (this) {
    BackupAccessStatus.GrantRequired -> BackupScreenState.NeedsGrant(path)
    is BackupAccessStatus.Ready -> BackupScreenState.Ready(path)
    is BackupAccessStatus.AccessLost -> BackupScreenState.AccessLost(path, accessProblemMessage(problem))
    is BackupAccessStatus.Failed -> BackupScreenState.Failed(path, accessProblemMessage(problem))
}

internal fun BackupAccessOutcome.toScreenState(path: String): BackupScreenState = when (this) {
    is BackupAccessOutcome.Ready -> BackupScreenState.Ready(path)
    BackupAccessOutcome.WrongFolder ->
        BackupScreenState.Failed(path, WRONG_FOLDER_MESSAGE, offerAccess = true)

    is BackupAccessOutcome.Failed -> if (problem.recoverableByGrant) {
        BackupScreenState.AccessLost(path, accessProblemMessage(problem))
    } else {
        BackupScreenState.Failed(path, accessProblemMessage(problem))
    }
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
}

internal const val WRONG_FOLDER_MESSAGE =
    "Нужно разрешить доступ именно к папке резервных копий Bee Search. Другая папка не подходит."

/** The Downloads collection as the user sees it in a file manager. */
internal const val USER_VISIBLE_DOWNLOADS = "Загрузки"

internal const val BACKUP_PATH_PREFIX = "Резервные копии Bee Search сохраняются в:"
internal const val BACKUP_GRANT_EXPLANATION =
    "Чтобы создавать резервные копии, Android должен разрешить Bee Search доступ к этой папке."
internal const val BACKUP_READY_LABEL = "✓ Доступно"
internal const val BACKUP_WORKING_LABEL = "Проверка доступа к папке…"
