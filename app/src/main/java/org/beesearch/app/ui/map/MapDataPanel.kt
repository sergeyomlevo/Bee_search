@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.ResearchDateInterval
import org.beesearch.app.domain.model.CountRange
import org.beesearch.app.domain.model.MeasurementRange
import org.beesearch.app.domain.model.ResearchObjectFilterSet
import org.beesearch.app.domain.model.ObservationPointFilterSet
import org.beesearch.app.domain.model.PhysicalObjectFilterSet
import org.beesearch.app.ui.points.CompactScreenHeader

internal const val MAP_DATA_PANEL_TAG = "map-data-panel"
internal const val MAP_DATA_CONTENT_TAG = "map-data-content"
internal const val MAP_DATA_SECTION_TAG = "map-data-section"

/** Horizontal inset of the approved panel; the calendars inside use their own, smaller one. */
private val PANEL_INSET = 16.dp
internal const val MAP_DATA_RESET_TAG = "map-data-reset"
internal const val MAP_DATA_DONE_TAG = "map-data-done"
internal const val MAP_DATA_FILTER_CONTENT_TAG = "map-data-filter-content"
internal const val MAP_DATA_FILTER_FOOTER_TAG = "map-data-filter-footer"
internal const val MAP_DATA_CLOSE_DESCRIPTION = "Закрыть"

/**
 * «Данные на карте»: the approved entry point into research-data display of the current Territory.
 *
 * The panel is a modal bottom sheet inside the map screen, so the map stays visible above it and
 * every change applies immediately. Each available type owns its visibility and its own period; the
 * panel never offers one period for the whole map and never shows a type without a user-reachable
 * lifecycle.
 *
 * The session — whether the sheet is open and which filter screen it shows — is owned by the caller,
 * so a visibility switch, a period tap or a reset keeps the panel open and keeps the user where they
 * are. Only `Готово`, `✕`, a system Back from the type list, a swipe down or a tap on the scrim close
 * it, and all of those are explicit user actions.
 */
@Composable
internal fun MapDataPanel(
    state: MapDataUiState,
    onVisibilityChange: (MapDataType, Boolean) -> Unit,
    onPeriodChange: (MapDataType, ResearchDateInterval?) -> Unit,
    onFiltersChange: (MapDataType, ResearchObjectFilterSet) -> Unit = { _, _ -> },
    onResetPeriod: (MapDataType) -> Unit,
    onResetFilters: () -> Unit,
    onOpenType: (MapDataType) -> Unit,
    onCloseType: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(MAP_DATA_PANEL_TAG),
    ) {
        // Back returns to the panel from a type screen and only then closes the panel; it never
        // resets a filter and never changes visibility.
        BackHandler(enabled = state.openedType != null) { onCloseType() }
        MapDataPanelBody(
            state = state,
            openedType = state.openedType,
            onOpenType = onOpenType,
            onBack = onCloseType,
            onVisibilityChange = onVisibilityChange,
            onPeriodChange = onPeriodChange,
            onFiltersChange = onFiltersChange,
            onResetPeriod = onResetPeriod,
            onResetFilters = onResetFilters,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Content of the panel: either the type list or the filter screen of one type.
 *
 * The two levels live in the same container, so there is no second navigation surface and every
 * change is applied immediately.
 */
@Composable
internal fun MapDataPanelBody(
    state: MapDataUiState,
    openedType: MapDataType?,
    onOpenType: (MapDataType) -> Unit,
    onBack: () -> Unit,
    onVisibilityChange: (MapDataType, Boolean) -> Unit,
    onPeriodChange: (MapDataType, ResearchDateInterval?) -> Unit,
    onFiltersChange: (MapDataType, ResearchObjectFilterSet) -> Unit = { _, _ -> },
    onResetPeriod: (MapDataType) -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (openedType == null) {
        MapDataPanelContent(
            state = state,
            onVisibilityChange = onVisibilityChange,
            onOpenType = onOpenType,
            onResetFilters = onResetFilters,
            onDismiss = onDismiss,
            modifier = modifier,
        )
    } else {
        MapDataFiltersScreen(
            state = state,
            type = openedType,
            onBack = onBack,
            // Finishing one type returns to the list: the panel itself is closed only from there.
            onDone = onBack,
            onPeriodChange = { onPeriodChange(openedType, it) },
            onFiltersChange = { onFiltersChange(openedType, it) },
            onResetPeriod = { onResetPeriod(openedType) },
            modifier = modifier,
        )
    }
}

@Composable
private fun MapDataPanelContent(
    state: MapDataUiState,
    onVisibilityChange: (MapDataType, Boolean) -> Unit,
    onOpenType: (MapDataType) -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .testTag(MAP_DATA_CONTENT_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Данные на карте",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    // A full square: the Material container is narrower than the approved target.
                    .size(48.dp)
                    .semantics { contentDescription = MAP_DATA_CLOSE_DESCRIPTION }
                    .testTag("map-data-close"),
            ) { Text("✕", style = MaterialTheme.typography.titleMedium) }
        }

        Text(
            text = "ИССЛЕДОВАТЕЛЬСКИЕ ДАННЫЕ",
            modifier = Modifier.testTag(MAP_DATA_SECTION_TAG),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Only types with a user-reachable lifecycle appear; there is no disabled row and no
        // placeholder for a future type.
        MapDataType.entries.forEach { type ->
            MapDataTypeRow(
                type = type,
                display = state.display.display(type),
                onVisibilityChange = { onVisibilityChange(type, it) },
                onOpen = { onOpenType(type) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onResetFilters,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(MAP_DATA_RESET_TAG),
            ) { Text("Сбросить фильтры") }
            Button(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).testTag(MAP_DATA_DONE_TAG),
            ) { Text("Готово") }
        }
    }
}

/**
 * One type row: visibility control, user name, filter summary and the affordance into its filters.
 *
 * The two actions never mix: the visibility control changes visibility only, and the rest of the row
 * opens the filter screen without touching visibility. The approved composition keeps the name at the
 * left and the summary at the right edge before the affordance; at the largest system font the row
 * stacks the value under the name instead of squeezing both into half the width, so neither the name
 * nor the value is truncated.
 */
@Composable
private fun MapDataTypeRow(
    type: MapDataType,
    display: MapTypeDisplay,
    onVisibilityChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val stacked = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    val summary = filterSummary(display.filters)
    val summaryColor = if (display.visible && display.filters != defaultFilters(type)) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onOpen)
            .testTag("map-data-type-${type.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Switch(
            checked = display.visible,
            onCheckedChange = onVisibilityChange,
            modifier = Modifier
                .heightIn(min = 48.dp)
                // Merged so the control announces its own name together with its on/off state.
                .semantics(mergeDescendants = true) {
                    contentDescription = "Показывать: ${type.userName}"
                }
                .testTag("map-data-visibility-${type.name}"),
        )
        if (stacked) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = type.userName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = summary,
                    modifier = Modifier.fillMaxWidth().testTag("map-data-summary-${type.name}"),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    color = summaryColor,
                )
            }
        } else {
            Text(
                text = type.userName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = summary,
                modifier = Modifier.weight(1f).testTag("map-data-summary-${type.name}"),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.End,
                color = summaryColor,
            )
        }
        // Decorative affordance only: cleared from semantics so the row keeps its own name.
        Box(Modifier.clearAndSetSemantics { }) {
            Text("›", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Filter screen with independent accordion criteria for the selected research type. */
@Composable
private fun MapDataFiltersScreen(
    state: MapDataUiState,
    type: MapDataType,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onPeriodChange: (ResearchDateInterval?) -> Unit,
    onFiltersChange: (ResearchObjectFilterSet) -> Unit,
    onResetPeriod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(type.name) { mutableStateOf("period") }
    var beeResetToken by rememberSaveable(type.name + "-bee-reset") { mutableStateOf(0) }
    var cycleResetToken by rememberSaveable(type.name + "-cycle-reset") { mutableStateOf(0) }
    var entranceResetToken by rememberSaveable(type.name + "-entrance-reset") { mutableStateOf(0) }
    var diameterResetToken by rememberSaveable(type.name + "-diameter-reset") { mutableStateOf(0) }
    val filters = state.display.display(type).filters
    val period = filters.dateInterval
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("map-data-type-screen-${type.name}"),
    ) {
        CompactScreenHeader(
            title = type.userName,
            onBack = onBack,
            modifier = Modifier.testTag("map-data-type-back-${type.name}"),
        )
        Box(
            modifier = Modifier
                .weight(1f, fill = true)
                .verticalScroll(rememberScrollState())
                .testTag(MAP_DATA_FILTER_CONTENT_TAG),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                FilterAccordion(
                    type = type,
                    key = "period",
                    title = "Период",
                    summary = periodSummary(period),
                    expanded = expanded == "period",
                    onToggle = { expanded = if (expanded == "period") "" else "period" },
                    rowTag = "period-row-${type.name}",
                    summaryTag = "period-summary-${type.name}",
                ) {
                    MapPeriodEditor(
                        type = type, period = period,
                        availableYears = state.yearsOf(type), availableMonths = state.monthsOf(type),
                        hasRecords = state.hasRecordsOf(type), today = state.today,
                        onPeriodChange = onPeriodChange,
                    )
                    FilterSectionActions(
                        resetLabel = "Сбросить период", resetTag = "period-reset-${type.name}",
                        onReset = onResetPeriod,
                    )
                }
                when (filters) {
                    is ObservationPointFilterSet -> {
                        FilterAccordion(type, "bee-count", "Количество пчёл", rangeSummary(filters.beeCount.min, filters.beeCount.max), expanded == "bee-count", { expanded = if (expanded == "bee-count") "" else "bee-count" }, "filter-row-bee-count-${type.name}", "filter-summary-bee-count-${type.name}") {
                            MapCountRangeEditor(type, "bee-count", filters.beeCount, onChange = { range -> onFiltersChange(filters.copy(beeCount = range)) }, resetToken = beeResetToken)
                            FilterSectionActions("Сбросить", "filter-reset-bee-count-${type.name}", { beeResetToken++ ; onFiltersChange(filters.copy(beeCount = CountRange())) })
                        }
                        FilterAccordion(type, "flight-cycle-count", "Количество циклов", rangeSummary(filters.flightCycleCount.min, filters.flightCycleCount.max), expanded == "flight-cycle-count", { expanded = if (expanded == "flight-cycle-count") "" else "flight-cycle-count" }, "filter-row-flight-cycle-count-${type.name}", "filter-summary-flight-cycle-count-${type.name}") {
                            MapCountRangeEditor(type, "flight-cycle-count", filters.flightCycleCount, onChange = { range -> onFiltersChange(filters.copy(flightCycleCount = range)) }, resetToken = cycleResetToken)
                            FilterSectionActions("Сбросить", "filter-reset-flight-cycle-count-${type.name}", { cycleResetToken++ ; onFiltersChange(filters.copy(flightCycleCount = CountRange())) })
                        }
                    }
                    is PhysicalObjectFilterSet -> {
                        FilterAccordion(type, "entrance-height", "Высота летка, см", rangeSummary(filters.entranceHeightCm.min, filters.entranceHeightCm.max), expanded == "entrance-height", { expanded = if (expanded == "entrance-height") "" else "entrance-height" }, "filter-row-entrance-height-${type.name}", "filter-summary-entrance-height-${type.name}") {
                            MapMeasurementRangeEditor(type, "entrance-height", filters.entranceHeightCm, onChange = { range -> onFiltersChange(filters.copy(entranceHeightCm = range)) }, resetToken = entranceResetToken)
                            FilterSectionActions("Сбросить", "filter-reset-entrance-height-${type.name}", { entranceResetToken++ ; onFiltersChange(filters.copy(entranceHeightCm = MeasurementRange())) })
                        }
                        FilterAccordion(type, "outer-diameter", "Наружный диаметр, см", rangeSummary(filters.outerDiameterCm.min, filters.outerDiameterCm.max), expanded == "outer-diameter", { expanded = if (expanded == "outer-diameter") "" else "outer-diameter" }, "filter-row-outer-diameter-${type.name}", "filter-summary-outer-diameter-${type.name}") {
                            MapMeasurementRangeEditor(type, "outer-diameter", filters.outerDiameterCm, onChange = { range -> onFiltersChange(filters.copy(outerDiameterCm = range)) }, resetToken = diameterResetToken)
                            FilterSectionActions("Сбросить", "filter-reset-outer-diameter-${type.name}", { diameterResetToken++ ; onFiltersChange(filters.copy(outerDiameterCm = MeasurementRange())) })
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PANEL_INSET, vertical = 8.dp)
                .testTag(MAP_DATA_FILTER_FOOTER_TAG),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onDone,
                modifier = Modifier.heightIn(min = 48.dp).testTag("filter-done-${type.name}"),
            ) { Text("Готово") }
        }
    }
}

@Composable
private fun FilterAccordion(
    type: MapDataType,
    key: String,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    rowTag: String,
    summaryTag: String,
    content: @Composable () -> Unit,
) {
    val stacked = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_INSET).heightIn(min = 48.dp)
            .semantics { role = Role.Button; stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто" }
            .clickable(onClick = onToggle).testTag(rowTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (stacked) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(summary, modifier = Modifier.fillMaxWidth().testTag(summaryTag), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
            }
        } else {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(summary, modifier = Modifier.testTag(summaryTag), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
        }
        Box(Modifier.clearAndSetSemantics { }) { Text(if (expanded) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium) }
    }
    if (expanded) content()
}

@Composable
private fun FilterSectionActions(
    resetLabel: String,
    resetTag: String,
    onReset: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_INSET), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(resetTag)) { Text(resetLabel) }
    }
}
