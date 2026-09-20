package org.beesearch.app.data.exchange

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.beesearch.app.BuildConfig

/**
 * The user-facing file exchange area of Bee Search.
 *
 * This is deliberately *not* application storage. It is the short, predictable place through which
 * the user moves files in and out of the app: map packages, exported data and (later) area files.
 * Everything the app needs to keep working - the Room database, DataStore, the installed copy of an
 * active PMTiles package - stays in app-owned storage. Deleting a file here can therefore never
 * damage an imported map or the database.
 *
 * The physical root is the public `Download` collection. Scoped storage does not let an app create
 * `BeeSearch/` in the root of shared storage, and Bee Search does not ask for broad filesystem
 * permission to force it; `Download/BeeSearch/...` is the nearest standard user-visible path and is
 * already the location this project delivers map packages to.
 */
internal enum class ExchangeFolder(val directoryName: String) {
    /** Files describing saved offline-map areas. */
    AREAS("Areas"),

    /** Ready offline-map packages: `*.pmtiles` together with `*.pmtiles.manifest.json`. */
    OFFLINE_MAPS("OfflineMaps"),

    /** Exported Bee Search data and the files used to import it back. */
    DATA("Data"),
}

internal data class ExchangeDirectory(
    val folder: ExchangeFolder,
    val relativePath: String,
    val directory: File,
)

/** Outcome of materialising the exchange tree. */
internal sealed interface ExchangeStorageState {
    /** Every exchange directory exists. [created] names only what this call had to create. */
    data class Ready(val created: List<String>) : ExchangeStorageState

    /**
     * The platform refused to materialise the tree. Callers degrade to an unstenciled picker rather
     * than failing the operation.
     */
    data class Unavailable(val reason: String) : ExchangeStorageState
}

internal class BeeSearchExchangeStorage(
    private val publicRoot: File,
    val variantName: String,
) {
    /** e.g. `BeeSearch/Beta/Exchange`. */
    val exchangeRelativePath: String = "$ROOT_DIRECTORY_NAME/$variantName/$EXCHANGE_DIRECTORY_NAME"

    val exchangeDirectory: File get() = File(publicRoot, exchangeRelativePath)

    /** What the user sees in a file manager, e.g. `Download/BeeSearch/Beta/Exchange/OfflineMaps`. */
    fun userVisiblePath(folder: ExchangeFolder? = null): String {
        val folderSuffix = folder?.let { "/${it.directoryName}" }.orEmpty()
        return "${publicRoot.name}/$exchangeRelativePath$folderSuffix"
    }

    fun directoryOf(folder: ExchangeFolder): ExchangeDirectory = ExchangeDirectory(
        folder = folder,
        relativePath = "$exchangeRelativePath/${folder.directoryName}",
        directory = File(exchangeDirectory, folder.directoryName),
    )

    /**
     * Storage-access-framework document id for a folder inside the primary shared volume, e.g.
     * `primary:Download/BeeSearch/Beta/Exchange/OfflineMaps`. Kept separate from [initialDocumentUri]
     * so the layout stays verifiable without an Android runtime.
     */
    fun documentId(folder: ExchangeFolder? = null): String = "primary:${userVisiblePath(folder)}"

    /** Initial location handed to the system picker so it opens inside the exchange folder. */
    fun initialDocumentUri(folder: ExchangeFolder? = null): Uri =
        DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId(folder))

    /**
     * Creates the exchange tree if it is missing. Idempotent by construction: existing directories
     * are used as they are, and nothing is ever deleted, renamed or duplicated.
     */
    suspend fun ensure(): ExchangeStorageState = withContext(Dispatchers.IO) {
        val folderDirectories = ExchangeFolder.entries.map { directoryOf(it).directory }
        val targets = listOf(exchangeDirectory) + folderDirectories
        val created = mutableListOf<String>()
        targets.filterNot { it.isDirectory }.forEach { directory ->
            directory.mkdirs()
            if (directory.isDirectory) created += directory.name
        }
        val missing = targets.filterNot { it.isDirectory }
        val unwritable = folderDirectories.filterNot { it.canWrite() }
        when {
            missing.isNotEmpty() -> ExchangeStorageState.Unavailable(
                "Не удалось подготовить папку обмена: ${missing.first().absolutePath}",
            )

            unwritable.isNotEmpty() -> ExchangeStorageState.Unavailable(
                "Папка обмена недоступна для записи: ${unwritable.first().absolutePath}",
            )

            else -> ExchangeStorageState.Ready(created)
        }
    }

    internal companion object {
        const val ROOT_DIRECTORY_NAME = "BeeSearch"
        const val EXCHANGE_DIRECTORY_NAME = "Exchange"
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}

/**
 * The exchange storage of the running build. The variant comes from the generated build
 * configuration, so Stable, Beta and Dev each get their own branch of the tree.
 */
@Suppress("DEPRECATION")
internal fun beeSearchExchangeStorage(): BeeSearchExchangeStorage = BeeSearchExchangeStorage(
    // Verified on the target device: this resolves to /storage/emulated/0/Download and the app can
    // create nested directories under it without any storage permission.
    publicRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    variantName = BuildConfig.EXCHANGE_VARIANT,
)
