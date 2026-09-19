@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.points

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.repository.ObservationRepository
import org.beesearch.app.ui.observation.BeeMarkIcon

@Composable
internal fun PointDetailRoute(
    pointId: UUID,
    repository: ObservationRepository,
    onBack: () -> Unit,
) {
    val detailViewModel: PointDetailViewModel = viewModel(
        key = "point-detail-$pointId",
        factory = PointDetailViewModel.factory(repository, pointId),
    )
    val state by detailViewModel.uiState.collectAsStateWithLifecycle()
    PointDetailScreen(state = state, onBack = onBack)
}

@Composable
internal fun PointDetailScreen(
    state: PointDetailUiState,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Просмотр точки") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.notFound -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Точка не найдена")
            }
            state.detail != null -> PointDetailContent(
                detail = state.detail,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun PointDetailContent(
    detail: ObservationPointDetail,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp).testTag("point-detail-list"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Основное", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            DetailLine("Территория", "${detail.territory.code} — ${detail.territory.name}")
            DetailLine("Точка", pointDisplayName(detail.point.pointNumber, detail.point.code))
            DetailLine("Дата и время", formatPointDateTime(detail.point.createdAt))
            DetailLine(
                "Координаты",
                String.format(Locale.ROOT, "%.6f, %.6f", detail.point.latitude, detail.point.longitude),
            )
            detail.point.gpsAccuracyM?.let { accuracy ->
                DetailLine("Точность GPS", String.format(Locale.ROOT, "%.1f м", accuracy))
            }
            DetailLine("Наблюдатель", "${detail.observer.displayName} (${detail.observer.code})")
            DetailLine("Результат", pointResultLabel(detail.point.beePresenceResult))
        }
        item { Text("Пчёлы", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
        if (detail.beeHistories.isEmpty()) {
            item { Text("Пчёл: 0", modifier = Modifier.padding(bottom = 16.dp)) }
        } else {
            items(detail.beeHistories, key = { it.bee.id }) { history ->
                Card(Modifier.fillMaxWidth().testTag("detail-bee-${history.bee.id}")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BeeMarkIcon(
                            markColor = history.bee.markColor,
                            markPosition = history.bee.markPosition,
                            height = PointDetailBeeMarkHeight,
                        )
                        Text("Циклов: ${history.flightCycles.size}")
                        history.flightCycles.forEach { cycle ->
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            Text("Цикл ${cycle.sequenceNumber}", fontWeight = FontWeight.Bold)
                            DetailLine("Вылет", formatPointDateTime(cycle.departureTime))
                            if (cycle.returnTime == null) {
                                Text("Открыт", color = MaterialTheme.colorScheme.primary)
                            } else {
                                DetailLine("Прилёт", formatPointDateTime(cycle.returnTime))
                                DetailLine(
                                    "Длительность",
                                    formatCompletedFlightDuration(cycle.departureTime, cycle.returnTime),
                                )
                            }
                            cycle.azimuthDeg?.let { azimuth ->
                                DetailLine("Азимут", String.format(Locale.ROOT, "%.0f°", azimuth))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", fontWeight = FontWeight.SemiBold)
        Text(value, modifier = Modifier.weight(1f))
    }
}

/** Sized to stay readily identifiable inside a Point history card. */
private val PointDetailBeeMarkHeight = 72.dp

internal fun formatCompletedFlightDuration(departure: Instant, returned: Instant): String {
    val seconds = Duration.between(departure, returned).seconds.coerceAtLeast(0)
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, remainingSeconds)
}
