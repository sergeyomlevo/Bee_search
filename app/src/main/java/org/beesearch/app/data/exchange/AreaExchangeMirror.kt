package org.beesearch.app.data.exchange

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beesearch.app.ui.map.MapArea

/** What happened to the managed Area file. */
internal sealed interface AreaMirrorOutcome {
    /** The managed file exists and matches the canonical Ареал. */
    data object Synced : AreaMirrorOutcome

    /** The managed copy was removed, or was already absent. */
    data object Removed : AreaMirrorOutcome

    /** Nothing was changed on purpose: the file is not this Ареал's managed copy. */
    data class Skipped(val reason: String) : AreaMirrorOutcome

    /** The exchange tree is unavailable or the write failed. Never a canonical failure. */
    data class Failed(val reason: String) : AreaMirrorOutcome
}

/**
 * Keeps one managed Area file in `Exchange/Areas` for the saved Ареал of a Territory.
 *
 * The canonical Ареал is the DataStore `v2` value; this file is a mirror for the user and for the
 * map workflow. It is deliberately **not** canonical storage: a missing file never means a missing
 * Ареал, and a failed write never rolls back a canonical save. The user-facing copy can always be
 * regenerated from the canonical Ареал, which is exactly what [sync] does.
 *
 * Ownership is decided by the full `areaId` inside the file, not by the file name: a file that
 * carries another Ареал's id is left alone, and nothing outside the exchange `Areas` folder is ever
 * read or removed.
 */
internal class AreaExchangeMirror(
    private val storage: BeeSearchExchangeStorage,
) {
    /** The exact file this Ареал owns, e.g. `…/Exchange/Areas/Лух--7e82a310.json`. */
    fun managedFile(area: MapArea): File =
        File(storage.directoryOf(ExchangeFolder.AREAS).directory, AreaExchangeFileName.of(area))

    /**
     * Writes the managed file when it is missing or out of date. Idempotent: an already current file
     * is not rewritten.
     */
    suspend fun sync(area: MapArea): AreaMirrorOutcome = withContext(Dispatchers.IO) {
        val file = managedFile(area)
        try {
            when (val state = storage.ensure()) {
                is ExchangeStorageState.Unavailable -> return@withContext AreaMirrorOutcome.Failed(state.reason)
                is ExchangeStorageState.Ready -> Unit
            }
            val text = AreaExchangeCodec.encode(area)
            // A read failure must not stop the mirror: fall through and try to write.
            val existingText = runCatching { file.takeIf(File::isFile)?.readText() }.getOrNull()
            if (existingText == text) return@withContext AreaMirrorOutcome.Synced
            existingText?.let { existing ->
                val existingArea = (AreaExchangeCodec.decode(existing) as? AreaExchangeReadResult.Present)?.area
                if (existingArea != null && existingArea.id != area.id) {
                    return@withContext AreaMirrorOutcome.Skipped("в этом файле сохранён другой ареал")
                }
            }
            writeAtomically(file, text)
            AreaMirrorOutcome.Synced
        } catch (error: Exception) {
            AreaMirrorOutcome.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    /**
     * Removes this Ареал's managed file after the canonical Ареал is gone.
     *
     * Only the one known path is touched, and only when it really carries this `areaId`. An absent
     * file is a normal outcome, and a removal failure never restores or blocks anything.
     */
    suspend fun remove(area: MapArea): AreaMirrorOutcome = withContext(Dispatchers.IO) {
        val file = managedFile(area)
        try {
            if (!file.isFile) return@withContext AreaMirrorOutcome.Removed
            when (val existing = AreaExchangeCodec.decode(file.readText())) {
                is AreaExchangeReadResult.Present -> when {
                    existing.area.id != area.id ->
                        AreaMirrorOutcome.Skipped("в этом файле сохранён другой ареал")

                    file.delete() -> AreaMirrorOutcome.Removed
                    else -> AreaMirrorOutcome.Failed("не удалось удалить файл ареала")
                }

                is AreaExchangeReadResult.Invalid ->
                    AreaMirrorOutcome.Skipped("файл не читается как файл ареала")
            }
        } catch (error: Exception) {
            AreaMirrorOutcome.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    /**
     * Writes through a temporary file in the same directory so an interrupted write cannot leave a
     * half-written Area file in the user's folder.
     *
     * `REPLACE_EXISTING` is required: replacing a file by renaming onto it is not portable, and the
     * managed file is rewritten in place on every change.
     */
    private fun writeAtomically(file: File, text: String) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(text)
        try {
            moveReplacing(temporary, file)
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private fun moveReplacing(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            // Shared storage on Android does not always support an atomic move across the boundary.
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
