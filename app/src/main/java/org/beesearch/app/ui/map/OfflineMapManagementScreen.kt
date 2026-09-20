package org.beesearch.app.ui.map

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.data.exchange.ExchangeFolder
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
 * The pickers, the two-step selection and the import call live in
 * `rememberMapPackageImportSession`, which the Ареал screen uses as well, so
 * there is exactly one import flow in the application. D063/D065 contracts,
 * MapPackageStore semantics and the active-package lifecycle are unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineMapManagementScreen(
    territory: Territory?,
    mapAreaStore: MapAreaStore,
    mapPackageStore: MapPackageStore,
    exchangeStorage: BeeSearchExchangeStorage,
    onBack: () -> Unit,
    onEditCoverageOnMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val territoryId = territory?.id

    var coverage by remember { mutableStateOf(emptyList<MapCoverageFragment>()) }
    var availability by remember {
        mutableStateOf<MapPackageAvailability>(MapPackageAvailability.Missing)
    }
    var checking by remember { mutableStateOf(false) }
    // The pickers, the two-step selection and the import call live in the shared session, so this
    // screen and the Ареал screen cannot drift into two different import flows.
    val importSession = rememberMapPackageImportSession(
        territoryId = territoryId,
        desiredCoverage = coverage,
        mapPackageStore = mapPackageStore,
        exchangeStorage = exchangeStorage,
    ) { result ->
        when (result) {
            is MapPackageImportResult.Activated ->
                availability = MapPackageAvailability.Ready(result.activePackage)

            // State A is recomputed from the pointer; the failure belongs to B.
            is MapPackageImportResult.Rejected ->
                if (territoryId != null) availability = mapPackageStore.loadActive(territoryId, coverage)
        }
    }

    LaunchedEffect(exchangeStorage) {
        exchangeStorage.ensure()
    }

    LaunchedEffect(territoryId, mapAreaStore, territory?.name) {
        if (territoryId == null) return@LaunchedEffect
        // Reading also migrates a readable legacy selection into a named Ареал.
        val loadedArea = try {
            mapAreaStore.load(territoryId, territory.name)
        } catch (_: Exception) {
            MapAreaReadResult.Corrupt(CORRUPT_AREA_MESSAGE)
        }
        val loadedCoverage = (loadedArea as? MapAreaReadResult.Present)?.area?.coverageFragments().orEmpty()
        coverage = loadedCoverage
        checking = true
        availability = mapPackageStore.loadActive(territoryId, loadedCoverage)
        checking = false
    }

    val importing = importSession.importing
    val selectedManifest = importSession.acceptedManifest
    val lastAttemptSucceeded = importSession.lastAttemptSucceeded
    val lastAttemptMessage = importSession.lastAttemptMessage

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
                Text("Ареал не создан. Создайте ареал на карте.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Участки ареала", style = MaterialTheme.typography.titleSmall)
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
                        onImport = { importSession.startFromPicker() },
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
                            onClick = { importSession.choosePmtilesForAcceptedManifest() },
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
                        if (!lastSucceeded && lastAttemptMessage == MAP_PACKAGE_NOT_A_MANIFEST_MESSAGE) {
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
