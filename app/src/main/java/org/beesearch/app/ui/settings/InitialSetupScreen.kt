@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.beesearch.app.InitialSetupState
import org.beesearch.app.ui.map.MapAreaReadResult
import org.beesearch.app.ui.map.MapPackageAvailability

@Composable
internal fun InitialSetupScreen(
    state: InitialSetupState,
    onObserver: () -> Unit,
    onTerritory: () -> Unit,
    onArea: () -> Unit,
    onMap: () -> Unit,
    onContinue: () -> Unit,
) {
    BackHandler(onBack = onContinue)
    Scaffold(topBar = { TopAppBar(title = { Text("Начальная настройка") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("initial-setup"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is InitialSetupState.Loading -> item { Text("Проверяем данные…") }
                is InitialSetupState.Ready -> {
                    item {
                        SetupStep("Наблюдатель", state.observer?.code ?: "Укажите наблюдателя",
                            state.observer != null, "setup-observer", onObserver)
                    }
                    item {
                        SetupStep("Территория", state.territory?.name ?: "Создайте или выберите территорию",
                            state.territory != null, "setup-territory", onTerritory)
                    }
                    val area = (state.area as? MapAreaReadResult.Present)?.area
                    item {
                        val detail = when (state.area) {
                            is MapAreaReadResult.Present -> area!!.name
                            is MapAreaReadResult.Corrupt, is MapAreaReadResult.Legacy -> "Проверьте данные ареала"
                            MapAreaReadResult.Absent -> "Создайте ареал"
                        }
                        SetupStep("Ареал", detail, area != null, "setup-area", onArea)
                    }
                    item {
                        val mapReady = area != null && state.map is MapPackageAvailability.Ready
                        val detail = when {
                            area == null -> "Сначала создайте ареал"
                            mapReady -> "Офлайн-карта загружена"
                            state.map is MapPackageAvailability.Unavailable -> "Карта недоступна — проверьте её"
                            else -> "Загрузите карту для этого ареала"
                        }
                        SetupStep("Офлайн-карта", detail, mapReady,
                            "setup-map", onMap)
                    }
                }
            }
            item {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().testTag("setup-continue")) {
                    Text("Продолжить без настройки")
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    title: String,
    detail: String,
    ready: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(tag).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("${if (ready) "✓" else "○"} $title", style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
