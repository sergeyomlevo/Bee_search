package org.beesearch.app.ui.map

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.UUID
import kotlinx.coroutines.launch
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.data.exchange.ExchangeFolder
import org.beesearch.app.data.exchange.OpenExchangeDocument

/** The manifest step produced a file that is not a map description. */
internal const val MAP_PACKAGE_NOT_A_MANIFEST_MESSAGE =
    "Выбранный файл не является описанием карты. " +
        "Выберите файл *.pmtiles.manifest.json из той же пары, что и файл карты."

/**
 * One offline-map import session, shared by every entry point.
 *
 * There is deliberately only one import flow in the application: the Settings page and the Ареал
 * screen both use this session, and both end in the same `MapPackageStore.import`, which owns
 * staging, manifest/integrity/D065 validation and atomic activation. Only the way a pair is chosen
 * differs - the user picks two files, or automatic discovery already found them.
 *
 * The two-step picker exists because a user cannot be expected to know which of two files is which:
 * the first picker only accepts a manifest, the screen then states the exact expected map file, and
 * the second picker takes the map.
 */
internal class MapPackageImportSession internal constructor(
    val importing: Boolean,
    /** The accepted manifest, once step one succeeded. */
    val acceptedManifest: MapPackageManifest?,
    val lastAttemptSucceeded: Boolean?,
    val lastAttemptMessage: String?,
    private val openManifestPicker: () -> Unit,
    private val openPmtilesPicker: () -> Unit,
    private val runImport: (manifestUri: Uri, pmtilesUri: Uri) -> Unit,
) {
    /** Opens the picker for step one (the map description). */
    fun startFromPicker() = openManifestPicker()

    /** Opens the picker for step two, for an already accepted manifest. */
    fun choosePmtilesForAcceptedManifest() = openPmtilesPicker()

    /** Imports a pair that is already known, for example one automatic discovery found. */
    fun importPair(manifestUri: Uri, pmtilesUri: Uri) = runImport(manifestUri, pmtilesUri)
}

@Composable
internal fun rememberMapPackageImportSession(
    territoryId: UUID?,
    desiredCoverage: List<MapCoverageFragment>,
    mapPackageStore: MapPackageStore,
    exchangeStorage: BeeSearchExchangeStorage,
    /** Reports the outcome of an import once it finished, so the caller can refresh its own state. */
    onResult: suspend (MapPackageImportResult) -> Unit = {},
): MapPackageImportSession {
    val scope = rememberCoroutineScope()
    val contentResolver = LocalContext.current.contentResolver
    // Pickers start inside the exchange folder so the user does not have to hunt for map files.
    val exchangeOfflineMapsUri = remember(exchangeStorage) {
        exchangeStorage.initialDocumentUri(ExchangeFolder.OFFLINE_MAPS)
    }
    // The import reads the coverage that is current when it runs, not when the lambda was created.
    val latestCoverage by rememberUpdatedState(desiredCoverage)
    var importing by remember { mutableStateOf(false) }
    var lastAttemptSucceeded by remember { mutableStateOf<Boolean?>(null) }
    var lastAttemptMessage by remember { mutableStateOf<String?>(null) }
    var pendingManifestUri by remember { mutableStateOf<Uri?>(null) }
    var acceptedManifest by remember { mutableStateOf<MapPackageManifest?>(null) }

    fun setAttempt(succeeded: Boolean, message: String) {
        lastAttemptSucceeded = succeeded
        lastAttemptMessage = message
    }

    fun readDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    } catch (_: Exception) {
        null
    }

    fun readFirstBytes(uri: Uri, max: Int): ByteArray? = try {
        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(max)
            var total = 0
            while (total < max) {
                val read = input.read(buffer, total, max - total)
                if (read < 0) break
                total += read
            }
            buffer.copyOf(total)
        }
    } catch (_: Exception) {
        null
    }

    fun readManifestText(uri: Uri): String? = try {
        contentResolver.openInputStream(uri)?.use { input -> input.bufferedReader().use { it.readText() } }
    } catch (_: Exception) {
        null
    }

    fun runImport(manifestUri: Uri, pmtilesUri: Uri) {
        val currentTerritoryId = territoryId
        if (currentTerritoryId == null) {
            setAttempt(false, "Сначала выберите текущую территорию в Настройках.")
            return
        }
        scope.launch {
            importing = true
            try {
                val result = mapPackageStore.import(
                    territoryId = currentTerritoryId,
                    desiredCoverage = latestCoverage,
                    manifestUri = manifestUri,
                    pmtilesUri = pmtilesUri,
                )
                when (result) {
                    is MapPackageImportResult.Activated ->
                        setAttempt(true, "Карта успешно импортирована и активирована")

                    is MapPackageImportResult.Rejected ->
                        setAttempt(false, result.message ?: "Не удалось импортировать карту")
                }
                onResult(result)
            } finally {
                importing = false
            }
            pendingManifestUri = null
            acceptedManifest = null
        }
    }

    val pmtilesPicker = rememberLauncherForActivityResult(
        contract = OpenExchangeDocument(exchangeOfflineMapsUri),
    ) { pmtilesUri ->
        if (pmtilesUri == null) return@rememberLauncherForActivityResult
        val manifest = acceptedManifest
        val manifestUri = pendingManifestUri
        if (manifest == null || manifestUri == null) {
            pendingManifestUri = null
            acceptedManifest = null
            setAttempt(false, "Выберите сначала файл описания карты (*.pmtiles.manifest.json).")
            return@rememberLauncherForActivityResult
        }
        // Friendly basename check BEFORE size/SHA/D065 validation.
        val displayName = readDisplayName(pmtilesUri)
        if (displayName != null && displayName != manifest.pmtilesFile) {
            setAttempt(false, "Выбран другой файл карты. Ожидается: ${manifest.pmtilesFile}")
            return@rememberLauncherForActivityResult
        }
        runImport(manifestUri, pmtilesUri)
    }

    val manifestPicker = rememberLauncherForActivityResult(
        contract = OpenExchangeDocument(exchangeOfflineMapsUri),
    ) { manifestUri ->
        if (manifestUri == null) return@rememberLauncherForActivityResult
        val displayName = readDisplayName(manifestUri)
        val looksLikePmtiles = displayName?.endsWith(".pmtiles") == true ||
            (readFirstBytes(manifestUri, 16)?.let { bytes ->
                bytes.size >= 7 && bytes.copyOf(7).decodeToString() == "PMTiles"
            } == true)
        if (looksLikePmtiles) {
            pendingManifestUri = null
            acceptedManifest = null
            setAttempt(false, "Сначала выберите файл описания карты: *.pmtiles.manifest.json")
            return@rememberLauncherForActivityResult
        }
        val text = readManifestText(manifestUri)
        val parsed = try {
            text?.let(MapPackageManifestParser::parse)
        } catch (_: Exception) {
            null
        }
        if (parsed == null) {
            pendingManifestUri = null
            acceptedManifest = null
            setAttempt(false, MAP_PACKAGE_NOT_A_MANIFEST_MESSAGE)
            return@rememberLauncherForActivityResult
        }
        pendingManifestUri = manifestUri
        acceptedManifest = parsed
        // Manifest is valid: state the expected file and open the second picker.
        pmtilesPicker.launch(arrayOf("*/*"))
    }

    return MapPackageImportSession(
        importing = importing,
        acceptedManifest = acceptedManifest,
        lastAttemptSucceeded = lastAttemptSucceeded,
        lastAttemptMessage = lastAttemptMessage,
        openManifestPicker = { manifestPicker.launch(arrayOf("*/*")) },
        openPmtilesPicker = { pmtilesPicker.launch(arrayOf("*/*")) },
        runImport = ::runImport,
    )
}
