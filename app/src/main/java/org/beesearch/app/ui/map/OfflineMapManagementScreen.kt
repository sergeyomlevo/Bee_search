package org.beesearch.app.ui.map

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.data.exchange.ExchangeFolder
import org.beesearch.app.data.exchange.OpenExchangeDocument
import org.beesearch.app.domain.model.Territory

/**
 * Administrative offline-map page (Settings → Офлайн-карты).
 *
 * State A = the current active Map Package (readiness vs desired coverage).
 * State B = the outcome of the last import/replacement attempt made here.
 *
 * The two-step import keeps the user from guessing file types:
 *  1) the first picker is for the D065 manifest only (application-side checks
 *     recognise and reject a PMTiles picked there);
 *  2) after a valid manifest, the UI states the exact expected PMTiles
 *     filename (packageId + basename) before the second picker;
 *  3) a PMTiles whose name does not match the manifest is rejected with a
 *     readable "another file" message before any size/SHA/D065 validation.
 *
 * D063/D065 contracts, MapPackageStore semantics and the active-package
 * lifecycle are not modified: only the picker UX is improved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineMapManagementScreen(
    territory: Territory?,
    mapCoverageStore: MapCoverageStore,
    mapPackageStore: MapPackageStore,
    exchangeStorage: BeeSearchExchangeStorage,
    onBack: () -> Unit,
    onEditCoverageOnMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val territoryId = territory?.id
    val scope = rememberCoroutineScope()
    val contentResolver = LocalContext.current.contentResolver
    // Pickers start inside the exchange folder so the user does not have to hunt for map files.
    val exchangeOfflineMapsUri = remember(exchangeStorage) {
        exchangeStorage.initialDocumentUri(ExchangeFolder.OFFLINE_MAPS)
    }

    var coverage by remember { mutableStateOf(emptyList<MapCoverageFragment>()) }
    var availability by remember {
        mutableStateOf<MapPackageAvailability>(MapPackageAvailability.Missing)
    }
    var checking by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var lastAttemptSucceeded by remember { mutableStateOf<Boolean?>(null) }
    var lastAttemptMessage by remember { mutableStateOf<String?>(null) }
    // Steps of the current import session.
    var pendingManifestUri by remember { mutableStateOf<Uri?>(null) }
    var selectedManifest by remember { mutableStateOf<MapPackageManifest?>(null) }

    LaunchedEffect(exchangeStorage) {
        exchangeStorage.ensure()
    }

    LaunchedEffect(territoryId) {
        if (territoryId == null) return@LaunchedEffect
        val loadedCoverage = try {
            mapCoverageStore.load(territoryId)
        } catch (_: Exception) {
            emptyList()
        }
        coverage = loadedCoverage
        checking = true
        availability = mapPackageStore.loadActive(territoryId, loadedCoverage)
        checking = false
    }

    fun setImportAttempt(succeeded: Boolean, message: String) {
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
        contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().use { it.readText() }
        }
    } catch (_: Exception) {
        null
    }

    val pmtilesPicker = rememberLauncherForActivityResult(
        contract = OpenExchangeDocument(exchangeOfflineMapsUri),
    ) { pmtilesUri ->
        if (pmtilesUri == null) return@rememberLauncherForActivityResult
        val manifest = selectedManifest
        val manifestUri = pendingManifestUri
        val currentTerritoryId = territoryId
        if (manifest == null || manifestUri == null || currentTerritoryId == null) {
            pendingManifestUri = null
            selectedManifest = null
            setImportAttempt(false, "Выберите сначала файл описания карты (*.pmtiles.manifest.json).")
            return@rememberLauncherForActivityResult
        }
        // Friendly basename check BEFORE size/SHA/D065 validation.
        val displayName = readDisplayName(pmtilesUri)
        if (displayName != null && displayName != manifest.pmtilesFile) {
            setImportAttempt(false, "Выбран другой файл карты. Ожидается: ${manifest.pmtilesFile}")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            importing = true
            try {
                when (
                    val result = mapPackageStore.import(
                        territoryId = currentTerritoryId,
                        desiredCoverage = coverage,
                        manifestUri = manifestUri,
                        pmtilesUri = pmtilesUri,
                    )
                ) {
                    is MapPackageImportResult.Activated -> {
                        availability = MapPackageAvailability.Ready(result.activePackage)
                        setImportAttempt(true, "Карта успешно импортирована и активирована")
                    }
                    is MapPackageImportResult.Rejected -> {
                        // State A is recomputed from the pointer; the failure belongs to B.
                        availability = mapPackageStore.loadActive(currentTerritoryId, coverage)
                        setImportAttempt(false, result.message ?: "Не удалось импортировать карту")
                    }
                }
            } finally {
                importing = false
            }
            pendingManifestUri = null
            selectedManifest = null
        }
    }

    val manifestPicker = rememberLauncherForActivityResult(
        contract = OpenExchangeDocument(exchangeOfflineMapsUri),
    ) { manifestUri ->
        if (manifestUri == null) return@rememberLauncherForActivityResult
        val displayName = readDisplayName(manifestUri)
        val looksLikePmtiles =
            displayName?.endsWith(".pmtiles") == true ||
                (readFirstBytes(manifestUri, 16)?.let { bytes ->
                    bytes.size >= 7 && bytes.copyOf(7).decodeToString() == "PMTiles"
                } == true)
        if (looksLikePmtiles) {
            pendingManifestUri = null
            selectedManifest = null
            setImportAttempt(false, "Сначала выберите файл описания карты: *.pmtiles.manifest.json")
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
            selectedManifest = null
            setImportAttempt(
                false,
                "Выбранный файл не является описанием карты. " +
                    "Выберите файл *.pmtiles.manifest.json из той же пары, что и файл карты.",
            )
            return@rememberLauncherForActivityResult
        }
        pendingManifestUri = manifestUri
        selectedManifest = parsed
        // Manifest is valid: state the expected file and open the second picker.
        pmtilesPicker.launch(arrayOf("*/*"))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Офлайн-карты") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (territory == null) {
                Text("Сначала выберите текущую территорию в Настройках.")
                TextButton(onClick = onBack) { Text("Назад") }
                return@Column
            }

            Text("Территория: ${territory.code} · ${territory.name}", style = MaterialTheme.typography.titleMedium)

            if (coverage.isEmpty()) {
                Text("Покрытие не выбрано. Выберите участок на карте.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Выбранное покрытие", style = MaterialTheme.typography.titleSmall)
                coverage.forEachIndexed { index, fragment ->
                    val b = fragment.bounds
                    Text(
                        "Участок ${index + 1}: С ${"%.6f".format(Locale.ROOT, b.north)}, " +
                            "Ю ${"%.6f".format(Locale.ROOT, b.south)}, " +
                            "З ${"%.6f".format(Locale.ROOT, b.west)}, " +
                            "В ${"%.6f".format(Locale.ROOT, b.east)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Состояние активной карты (A)", style = MaterialTheme.typography.labelMedium)
                    OfflineMapPackagePanel(
                        desiredCoverageConfigured = coverage.isNotEmpty(),
                        availability = availability,
                        isLoading = checking,
                        isImporting = importing,
                        message = null,
                        onSelectCoverage = { onEditCoverageOnMap() },
                        onImport = { manifestPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val manifest = selectedManifest
            if (manifest == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 1.dp,
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Как установить карту", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Для установки карты нужны два файла одной пары:\n" +
                                "1. *.pmtiles.manifest.json — файл описания карты;\n" +
                                "2. *.pmtiles — файл самой карты.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Папка обмена: ${exchangeStorage.userVisiblePath(ExchangeFolder.OFFLINE_MAPS)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Шаг 1 выполнен: манифест принят", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "packageId: ${manifest.packageId}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Теперь выберите файл:\n${manifest.pmtilesFile}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        TextButton(
                            onClick = { pmtilesPicker.launch(arrayOf("*/*")) },
                            enabled = !importing,
                        ) { Text("Выбрать файл карты") }
                    }
                }
            }

            val lastSucceeded = lastAttemptSucceeded
            if (lastSucceeded != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (lastSucceeded) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Последняя попытка импорта (B)", style = MaterialTheme.typography.labelMedium)
                        Text(
                            lastAttemptMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (lastSucceeded) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                        if (!lastSucceeded && lastAttemptMessage == "Выбранный файл не является корректным manifest карты") {
                            Text(
                                "Проверьте, что на 1-м шаге выбран файл описания карты " +
                                    "(*.pmtiles.manifest.json), а на 2-м шаге — сам файл карты (*.pmtiles).",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
