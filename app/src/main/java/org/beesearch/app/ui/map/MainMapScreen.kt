package org.beesearch.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.beesearch.app.MapMeasurement
import org.beesearch.app.ObservationPointCreationDraft
import org.beesearch.app.domain.location.LocationUiState
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.formatMapMeasurement

@Composable
internal fun CurrentTerritoryScreen(
    territory: Territory?,
    locationState: LocationUiState,
    observationPointDraft: ObservationPointCreationDraft?,
    locationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onStartObservationPointCreation: (Double, Double) -> Unit,
    onCancelObservationPointCreation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTerritories: () -> Unit,
) {
    MapFirstScaffold(
        onOpenSettings = onOpenSettings,
    ) { mapModifier ->
        Box(modifier = mapModifier) {
            if (territory != null) {
                BeeMap(
                    locationState = locationState,
                    isCreatingObservationPoint = observationPointDraft != null,
                    locationPermissionGranted = locationPermissionGranted,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onCreateObservationPointAt = onStartObservationPointCreation,
                    modifier = Modifier.fillMaxSize().testTag(MAIN_MAP_VIEWPORT_TAG),
                )
            } else {
                Surface(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Текущая территория не найдена")
                        TextButton(onClick = onOpenTerritories) { Text("Выбрать территорию") }
                    }
                }
            }
        }
    }
}

internal const val MAIN_MAP_VIEWPORT_TAG = "main-map-viewport"
internal const val MAIN_BOTTOM_PANEL_TAG = "main-bottom-panel"
internal const val RECENTER_MAP_DESCRIPTION = "Вернуться к текущему местоположению"
internal const val CREATE_OBSERVATION_POINT_DESCRIPTION = "Создать точку наблюдения"
internal const val SETTINGS_DESCRIPTION = "Настройки"

@Composable
internal fun MapFirstScaffold(
    onOpenSettings: () -> Unit,
    content: @Composable BoxScope.(Modifier) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MapBottomPanel(onOpenSettings = onOpenSettings)
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
internal fun MapBottomPanel(
    onOpenSettings: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp)
                .testTag(MAIN_BOTTOM_PANEL_TAG),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = SETTINGS_DESCRIPTION },
            ) {
                SettingsGlyph(Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun SettingsGlyph(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val innerRadius = size.minDimension * 0.23f
        val spokeStartRadius = innerRadius
        val spokeEndRadius = size.minDimension * 0.46f
        drawCircle(
            color = color,
            radius = innerRadius,
            center = center,
            style = Stroke(width = 2.2.dp.toPx()),
        )
        repeat(8) { index ->
            val angle = Math.PI * index / 4.0
            val start = Offset(
                x = center.x + (kotlin.math.cos(angle) * spokeStartRadius).toFloat(),
                y = center.y + (kotlin.math.sin(angle) * spokeStartRadius).toFloat(),
            )
            val end = Offset(
                x = center.x + (kotlin.math.cos(angle) * spokeEndRadius).toFloat(),
                y = center.y + (kotlin.math.sin(angle) * spokeEndRadius).toFloat(),
            )
            drawLine(color = color, start = start, end = end, strokeWidth = 2.8.dp.toPx())
        }
        drawCircle(color = color, radius = 1.8.dp.toPx(), center = center)
    }
}

@Composable
internal fun CompactMapStatus(
    accuracyMeters: Double,
    measurement: MapMeasurement?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompactGpsAccuracy(accuracyMeters = accuracyMeters)
        if (measurement != null) {
            MapMeasurementOverlay(measurement = measurement)
        }
    }
}

@Composable
internal fun MapMeasurementOverlay(
    measurement: MapMeasurement,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("map-measurement-overlay"),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Text(
            text = formatMapMeasurement(measurement),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun CompactGpsAccuracy(
    accuracyMeters: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("gps-accuracy-overlay"),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
    ) {
        Text(
            text = "Точность ${accuracyMeters.formatMeters()} м",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

@Composable
internal fun MapIdleControls(
    canRecenter: Boolean,
    canCreateObservationPoint: Boolean,
    onRecenter: () -> Unit,
    onCreateObservationPoint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalIconButton(
            onClick = onRecenter,
            enabled = canRecenter,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = RECENTER_MAP_DESCRIPTION }
                .testTag("map-recenter"),
        ) { RecenterGlyph() }
        if (canCreateObservationPoint) {
            Button(
                onClick = onCreateObservationPoint,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(56.dp)
                    .semantics { contentDescription = CREATE_OBSERVATION_POINT_DESCRIPTION }
                    .testTag("create-observation-point"),
            ) { AddPointGlyph() }
        }
    }
}

@Composable
private fun RecenterGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.28f
        drawCircle(color = color, radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
        val inset = 1.dp.toPx()
        drawLine(color, Offset(center.x, inset), Offset(center.x, center.y - radius), 2.dp.toPx())
        drawLine(color, Offset(center.x, center.y + radius), Offset(center.x, size.height - inset), 2.dp.toPx())
        drawLine(color, Offset(inset, center.y), Offset(center.x - radius, center.y), 2.dp.toPx())
        drawLine(color, Offset(center.x + radius, center.y), Offset(size.width - inset, center.y), 2.dp.toPx())
        drawCircle(color = color, radius = 2.dp.toPx(), center = center)
    }
}

@Composable
private fun AddPointGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val half = size.minDimension * 0.30f
        drawLine(color, Offset(center.x - half, center.y), Offset(center.x + half, center.y), 2.5.dp.toPx())
        drawLine(color, Offset(center.x, center.y - half), Offset(center.x, center.y + half), 2.5.dp.toPx())
    }
}

private fun Double.formatMeters(): String = "%.1f".format(this)
