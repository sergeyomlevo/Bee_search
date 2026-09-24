@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.area

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.beesearch.app.data.exchange.AreaExchangeFileName
import org.beesearch.app.data.exchange.AreaExchangeMirror
import org.beesearch.app.data.exchange.AreaMapCandidate
import org.beesearch.app.data.exchange.AreaMapDiscovery
import org.beesearch.app.data.exchange.AreaMapDiscoveryResult
import org.beesearch.app.data.exchange.AreaSendResult
import org.beesearch.app.data.exchange.AreaTransport
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.data.exchange.ExchangeFolder
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.map.AREA_MAP_READY_DESCRIPTION
import org.beesearch.app.ui.map.AREA_MAP_READY_LABEL
import org.beesearch.app.ui.map.CREATE_AREA_LABEL
import org.beesearch.app.ui.map.DELETE_AREA_LABEL
import org.beesearch.app.ui.map.DeleteAreaDialog
import org.beesearch.app.ui.map.LOAD_AREA_MAP_DESCRIPTION
import org.beesearch.app.ui.map.LOAD_AREA_MAP_LABEL
import org.beesearch.app.ui.map.MAP_PACKAGE_COVERAGE_MISMATCH_MESSAGE
import org.beesearch.app.ui.map.MapArea
import org.beesearch.app.ui.map.MapAreaChangeResult
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapAreaStore
import org.beesearch.app.ui.map.MapPackageAvailability
import org.beesearch.app.ui.map.MapPackageImportResult
import org.beesearch.app.ui.map.MapPackageStore
import org.beesearch.app.ui.map.SEND_AREA_DESCRIPTION
import org.beesearch.app.ui.map.SEND_AREA_LABEL
import org.beesearch.app.ui.map.VIEW_AREA_ON_MAP_DESCRIPTION
import org.beesearch.app.ui.map.VIEW_AREA_ON_MAP_LABEL
import org.beesearch.app.ui.map.areaUnionKm2
import org.beesearch.app.ui.map.coverageFragments
import org.beesearch.app.ui.map.formatSquareKilometers
import org.beesearch.app.ui.map.rememberMapPackageImportSession

internal const val AREA_SCREEN_TAG = "area-screen"
internal const val AREA_NOT_CREATED_TAG = "area-not-created"
internal const val AREA_CARD_TAG = "area-card"
internal const val AREA_CORRUPT_TAG = "area-corrupt"
internal const val AREA_NAME_TAG = "area-name"
internal const val AREA_SECTION_COUNT_TAG = "area-section-count"
internal const val AREA_TOTAL_AREA_TAG = "area-total-area"
internal const val CREATE_AREA_TAG = "create-area"
internal const val VIEW_AREA_ON_MAP_TAG = "view-area-on-map"
internal const val SEND_AREA_TAG = "send-area"
internal const val LOAD_AREA_MAP_TAG = "load-area-map"
internal const val AREA_MAP_READY_TAG = "area-map-ready"
internal const val AREA_MAP_GUIDANCE_TAG = "area-map-guidance"
internal const val AREA_MAP_MESSAGE_TAG = "area-map-message"
internal const val AREA_MAP_CHOOSE_ANOTHER_ACTION_TAG = "area-map-choose-another-action"
internal const val DELETE_AREA_TAG = "delete-area"
internal const val CREATE_AREA_DESCRIPTION = "Создать ареал офлайн-карты"
internal const val DELETE_AREA_DESCRIPTION = "Удалить ареал территории"

/** Label of the derived total area. The value itself is never stored. */
internal const val AREA_TOTAL_AREA_LABEL = "Общая площадь"
internal const val AREA_SECTION_COUNT_LABEL = "Участков"

/** Shown when a discovered package turns out not to cover the current Ареал. */
internal const val AREA_MAP_COVERAGE_MISMATCH_AREA_MESSAGE =
    "Карта найдена, но она не покрывает текущий ареал."
internal const val CHOOSE_ANOTHER_MAP_ACTION_LABEL = "Выбрать другую карту"

/**
 * The Ареал of the current Territory: one object, so this screen is a card rather than a list.
 *
 * The card answers what the Ареал is, how to look at it on the map, how to send its file and how to
 * get the offline map for it. Loading a map is an entry point into the one existing import flow:
 * `Загрузить карту` first tries to recognise the package by its file name and otherwise opens the
 * standard Android file picker. Nothing here validates or installs a package itself, and a recognised
 * name never replaces the real manifest, integrity and coverage checks.
 */
@Composable
internal fun AreaRoute(
    territory: Territory?,
    areaStore: MapAreaStore,
    areaMirror: AreaExchangeMirror,
    areaTransport: AreaTransport,
    mapPackageStore: MapPackageStore,
    mapDiscovery: AreaMapDiscovery,
    exchangeStorage: BeeSearchExchangeStorage,
    onCreate: () -> Unit,
    onViewOnMap: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val territoryId = territory?.id
    var read by remember(territoryId) { mutableStateOf<MapAreaReadResult?>(null) }
    var deleteVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    var foundCandidate by remember { mutableStateOf<AreaMapCandidate?>(null) }
    var alternatives by remember { mutableStateOf(emptyList<AreaMapCandidate>()) }
    var alternativesVisible by remember { mutableStateOf(false) }
    var coverageMismatch by remember { mutableStateOf(false) }
    val area = (read as? MapAreaReadResult.Present)?.area
    val coverage = area?.coverageFragments().orEmpty()
    var mapAvailability by remember(territoryId, coverage) {
        mutableStateOf<MapPackageAvailability?>(null)
    }

    // The offline-map import lives in this shared session: the Ареал screen only decides which pair to
    // hand it, and the session ends in the same MapPackageStore.import as every other entry point.
    val importSession = rememberMapPackageImportSession(
        territoryId = territoryId,
        desiredCoverage = coverage,
        mapPackageStore = mapPackageStore,
        exchangeStorage = exchangeStorage,
    ) { result ->
        when (result) {
            is MapPackageImportResult.Activated -> {
                mapAvailability = MapPackageAvailability.Ready(result.activePackage)
                message = null
                coverageMismatch = false
            }

            is MapPackageImportResult.Rejected -> {
                coverageMismatch = result.message == MAP_PACKAGE_COVERAGE_MISMATCH_MESSAGE
                message = if (coverageMismatch) {
                    AREA_MAP_COVERAGE_MISMATCH_AREA_MESSAGE
                } else {
                    result.message
                }
                if (territoryId != null) {
                    mapAvailability = mapPackageStore.loadActive(territoryId, coverage)
                }
            }
        }
    }

    LaunchedEffect(territoryId, areaStore, territory?.name) {
        val loaded = if (territoryId == null) {
            MapAreaReadResult.Absent
        } else {
            try {
                areaStore.load(territoryId, territory.name)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                MapAreaReadResult.Corrupt("не удалось прочитать ареал")
            }
        }
        read = loaded
        // Lazy backfill: an Ареал that was saved before this iteration, or whose public file the user
        // deleted, gets its managed Area file back the next time this screen is opened. The canonical
        // Ареал is only read here, and a failure to write the file changes nothing.
        (loaded as? MapAreaReadResult.Present)?.let { areaMirror.sync(it.area) }
    }

    LaunchedEffect(territoryId, mapPackageStore, coverage) {
        if (territoryId == null || area == null) return@LaunchedEffect
        mapAvailability = try {
            mapPackageStore.loadActive(territoryId, coverage)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            MapPackageAvailability.Unavailable("Не удалось проверить офлайн-карту")
        }
    }

    /** Loads one discovered package through the shared import flow. */
    fun loadCandidate(candidate: AreaMapCandidate) {
        foundCandidate = null
        alternativesVisible = false
        val folder = exchangeStorage.directoryOf(ExchangeFolder.OFFLINE_MAPS).directory
        importSession.importPair(
            manifestUri = Uri.fromFile(File(folder, candidate.manifestFileName)),
            pmtilesUri = Uri.fromFile(File(folder, candidate.pmtilesFileName)),
        )
    }

    /**
     * `Загрузить карту`: look for a package of this Ареал by its file name first.
     *
     * A found package is only offered, never imported by itself. Nothing found, an unclear pairing or
     * a platform that will not let the app list the folder all lead to the standard file picker, which
     * is an ordinary outcome rather than an error the user has to understand.
     */
    fun startMapLoading() {
        val current = area ?: return
        if (discovering) return
        discovering = true
        message = null
        coverageMismatch = false
        scope.launch {
            val result = try {
                mapDiscovery.discover(AreaExchangeFileName.externalAreaStem(current))
            } catch (_: Exception) {
                AreaMapDiscoveryResult.Unavailable("Не удалось проверить папку обмена")
            }
            discovering = false
            when (val decision = areaMapLoadDecision(result)) {
                is AreaMapLoadDecision.OfferFound -> {
                    alternatives = decision.alternatives
                    foundCandidate = decision.candidate
                }

                AreaMapLoadDecision.OpenPicker -> importSession.startFromPicker()
            }
        }
    }

    AreaScreen(
        territoryCode = territory?.code,
        read = read,
        message = message,
        sending = sending,
        discovering = discovering,
        mapAvailability = mapAvailability,
        coverageMismatch = coverageMismatch,
        onCreate = onCreate,
        onViewOnMap = onViewOnMap,
        onSend = {
            val current = area
            if (current != null && !sending) {
                sending = true
                scope.launch {
                    val result = areaTransport.send(current)
                    sending = false
                    // A failed send never touches the Ареал; the user only needs to know.
                    if (result is AreaSendResult.Failed) message = result.message
                }
            }
        },
        onLoadMap = { startMapLoading() },
        onChooseAnotherMap = { importSession.startFromPicker() },
        onDelete = { deleteVisible = true },
        onBack = onBack,
        modifier = modifier,
    )

    val currentId = territoryId
    if (deleteVisible && area != null && currentId != null) {
        DeleteAreaDialog(
            areaName = area.name,
            onConfirm = {
                deleteVisible = false
                scope.launch {
                    when (val result = areaStore.delete(currentId)) {
                        // The Ареал is gone; Territory, observations and the map package stay. The
                        // managed Area file is removed by the store that owns the exchange mirror.
                        MapAreaChangeResult.Deleted -> read = MapAreaReadResult.Absent
                        is MapAreaChangeResult.Refused -> message = result.reason
                        is MapAreaChangeResult.Saved -> read = MapAreaReadResult.Present(result.area)
                    }
                }
            },
            onDismiss = { deleteVisible = false },
        )
    }

    foundCandidate?.let { candidate ->
        AreaMapFoundDialog(
            areaName = area?.name.orEmpty(),
            candidate = candidate,
            hasAlternatives = alternatives.isNotEmpty(),
            onLoad = { loadCandidate(candidate) },
            onShowAlternatives = {
                foundCandidate = null
                alternativesVisible = true
            },
            onChooseAnother = {
                foundCandidate = null
                alternatives = emptyList()
                importSession.startFromPicker()
            },
            onDismiss = {
                foundCandidate = null
                alternatives = emptyList()
            },
        )
    }

    if (alternativesVisible) {
        AreaMapAlternativesDialog(
            areaName = area?.name.orEmpty(),
            alternatives = alternatives,
            onPick = { loadCandidate(it) },
            onChooseAnother = {
                alternativesVisible = false
                alternatives = emptyList()
                importSession.startFromPicker()
            },
            onDismiss = {
                alternativesVisible = false
                alternatives = emptyList()
            },
        )
    }
}

@Composable
internal fun AreaScreen(
    territoryCode: String?,
    read: MapAreaReadResult?,
    message: String?,
    sending: Boolean,
    discovering: Boolean,
    mapAvailability: MapPackageAvailability?,
    coverageMismatch: Boolean,
    onCreate: () -> Unit,
    onViewOnMap: () -> Unit,
    onSend: () -> Unit,
    onLoadMap: () -> Unit,
    onChooseAnotherMap: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ареал") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(AREA_SCREEN_TAG),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = territoryCode?.let { "Территория: $it" } ?: "Текущая территория не выбрана",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            message?.let { detail ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag(AREA_MAP_MESSAGE_TAG),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(detail, style = MaterialTheme.typography.bodyMedium)
                            if (coverageMismatch) {
                                // The map was found but cannot be used: offer the ordinary picker
                                // instead. The Ареал itself is untouched either way.
                                TextButton(
                                    onClick = onChooseAnotherMap,
                                    modifier = Modifier.testTag(AREA_MAP_CHOOSE_ANOTHER_ACTION_TAG),
                                ) { Text(CHOOSE_ANOTHER_MAP_ACTION_LABEL) }
                            }
                        }
                    }
                }
            }
            item {
                when (read) {
                    null -> Text("Проверяем ареал…")
                    MapAreaReadResult.Absent -> AreaNotCreated(
                        territoryMissing = territoryCode == null,
                        onCreate = onCreate,
                    )

                    is MapAreaReadResult.Present -> AreaCard(
                        area = read.area,
                        sending = sending,
                        discovering = discovering,
                        mapAvailability = mapAvailability,
                        onViewOnMap = onViewOnMap,
                        onSend = onSend,
                        onLoadMap = onLoadMap,
                        onDelete = onDelete,
                    )

                    // A damaged value is never presented as "not created", and it is never
                    // created over or deleted from here.
                    is MapAreaReadResult.Corrupt -> AreaCorrupt()
                    is MapAreaReadResult.Legacy -> AreaCorrupt()
                }
            }
        }
    }
}

@Composable
private fun AreaNotCreated(
    territoryMissing: Boolean,
    onCreate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(AREA_NOT_CREATED_TAG),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ареал не создан", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (territoryMissing) {
                    "Сначала выберите текущую территорию в Настройках."
                } else {
                    "Участки ареала задаются на карте: добавьте один или несколько участков и сохраните ареал."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!territoryMissing) {
                Button(
                    onClick = onCreate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CREATE_AREA_TAG)
                        .semantics { contentDescription = CREATE_AREA_DESCRIPTION },
                ) { Text(CREATE_AREA_LABEL) }
            }
        }
    }
}

/**
 * What the user needs to know about a saved Ареал: its name, how many участки it has, how large it is,
 * and whether an offline map is ready for it. No UUID, no raw bounding boxes, no storage path, no
 * manifest and no hashes - those are implementation, and the files are handled by the actions.
 */
@Composable
private fun AreaCard(
    area: MapArea,
    sending: Boolean,
    discovering: Boolean,
    mapAvailability: MapPackageAvailability?,
    onViewOnMap: () -> Unit,
    onSend: () -> Unit,
    onLoadMap: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(AREA_CARD_TAG),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(area.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag(AREA_NAME_TAG))
            Text(
                text = "$AREA_SECTION_COUNT_LABEL: ${area.bounds.size}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(AREA_SECTION_COUNT_TAG),
            )
            Text(
                // Overlapping участки are counted once and the gaps between them are not counted at
                // all, so this is the area of the Ареал itself rather than of its outer rectangle.
                text = "$AREA_TOTAL_AREA_LABEL: ${formatSquareKilometers(areaUnionKm2(area.bounds))} км²",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(AREA_TOTAL_AREA_TAG),
            )
            Button(
                onClick = onViewOnMap,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VIEW_AREA_ON_MAP_TAG)
                    .semantics { contentDescription = VIEW_AREA_ON_MAP_DESCRIPTION },
            ) { Text(VIEW_AREA_ON_MAP_LABEL) }
            Button(
                onClick = onSend,
                enabled = !sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SEND_AREA_TAG)
                    .semantics { contentDescription = SEND_AREA_DESCRIPTION },
            ) { Text(if (sending) "Отправка…" else SEND_AREA_LABEL) }
            Button(
                onClick = onLoadMap,
                enabled = !discovering,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LOAD_AREA_MAP_TAG)
                    .semantics { contentDescription = LOAD_AREA_MAP_DESCRIPTION },
            ) { Text(if (discovering) "Поиск карты…" else LOAD_AREA_MAP_LABEL) }
            if (mapAvailability is MapPackageAvailability.Ready) {
                Text(
                    text = AREA_MAP_READY_LABEL,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .testTag(AREA_MAP_READY_TAG)
                        .semantics { contentDescription = AREA_MAP_READY_DESCRIPTION },
                )
            } else if (mapAvailability != null) {
                Text(
                    text = if (mapAvailability is MapPackageAvailability.Unavailable) {
                        "Карта недоступна. Отправьте ареал кнопкой „Отправить ареал“ тому, кто создаст карту. Полученную карту добавьте через „Загрузить карту“."
                    } else {
                        "Для этого ареала нет готовой офлайн-карты. Отправьте его кнопкой „Отправить ареал“ тому, кто создаст карту. Полученную карту добавьте через „Загрузить карту“."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(AREA_MAP_GUIDANCE_TAG),
                )
            }
            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DELETE_AREA_TAG)
                    .semantics { contentDescription = DELETE_AREA_DESCRIPTION },
            ) { Text(DELETE_AREA_LABEL) }
        }
    }
}

@Composable
private fun AreaCorrupt() {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(AREA_CORRUPT_TAG),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Данные ареала повреждены", style = MaterialTheme.typography.titleMedium)
            Text(
                "Сохранённые участки не читаются, поэтому обычное редактирование и сохранение недоступны. " +
                    "Данные не изменяются.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
