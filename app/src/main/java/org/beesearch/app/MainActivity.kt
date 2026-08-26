@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.ui.theme.Bee_searchTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BeeSearchApp() }
    }
}

@Composable
private fun BeeSearchApp() {
    val application = androidx.compose.ui.platform.LocalContext.current.applicationContext as BeeSearchApplication
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(application))
    val route by viewModel.route.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val territories by viewModel.territories.collectAsStateWithLifecycle()
    val currentTerritory by viewModel.currentTerritory.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    Bee_searchTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                feedback?.let { FeedbackBanner(it, viewModel::clearFeedback) }
                when (val currentRoute = route) {
                    AppRoute.Loading -> LoadingScreen()
                    AppRoute.Settings -> SettingsScreen(
                        initialObserverCode = settings.observerCode,
                        onBack = viewModel::returnToStartup,
                        onSave = viewModel::saveObserverCode,
                    )
                    AppRoute.TerritoryManagement -> TerritoryManagementScreen(
                        territories = territories,
                        currentTerritoryId = settings.currentTerritoryId,
                        onBack = viewModel::returnToStartup,
                        onOpenSettings = viewModel::openSettings,
                        onSelectTerritory = viewModel::setCurrentTerritory,
                        onCreateTerritory = viewModel::createTerritory,
                    )
                    AppRoute.CurrentTerritory -> CurrentTerritoryScreen(
                        territory = currentTerritory,
                        onOpenSettings = viewModel::openSettings,
                        onOpenTerritories = viewModel::openTerritoryManagement,
                    )
                    is AppRoute.ResumeObservation -> ResumeObservationScreen(
                        point = currentRoute.point,
                        onOpenTerritories = viewModel::openTerritoryManagement,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = onDismiss) { Text("Закрыть") }
    }
    HorizontalDivider()
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Загрузка сохранённых данных")
    }
}

@Composable
private fun SettingsScreen(
    initialObserverCode: String?,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var observerCode by rememberSaveable(initialObserverCode) {
        mutableStateOf(initialObserverCode.orEmpty())
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Настройки") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Назад") }
        })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Код наблюдателя", style = MaterialTheme.typography.titleMedium)
            Text("Код сохраняется на устройстве и копируется в новые ObservationPoint.")
            OutlinedTextField(
                value = observerCode,
                onValueChange = { observerCode = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("observer_code") },
                singleLine = true,
            )
            Button(
                onClick = { onSave(observerCode) },
                enabled = observerCode.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
    }
}

@Composable
private fun TerritoryManagementScreen(
    territories: List<Territory>,
    currentTerritoryId: UUID?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTerritory: (UUID) -> Unit,
    onCreateTerritory: (String, String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Территории") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Назад") }
        }, actions = {
            TextButton(onClick = onOpenSettings) { Text("Настройки") }
        })
    }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text("Новая территория", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Код территории") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название территории") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onCreateTerritory(code, name) },
                enabled = code.isNotBlank() && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Создать") }
            Spacer(Modifier.height(16.dp))
            Text("Сохранённые территории", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (territories.isEmpty()) {
                Text("Территорий пока нет.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(territories, key = { it.id }) { territory ->
                        TerritoryRow(
                            territory = territory,
                            isCurrent = territory.id == currentTerritoryId,
                            onSelect = { onSelectTerritory(territory.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerritoryRow(territory: Territory, isCurrent: Boolean, onSelect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(territory.code, fontWeight = FontWeight.Bold)
            Text(territory.name)
            if (isCurrent) {
                Text("Текущая территория", color = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onSelect) { Text("Сделать текущей") }
            }
        }
    }
}

@Composable
private fun CurrentTerritoryScreen(
    territory: Territory?,
    onOpenSettings: () -> Unit,
    onOpenTerritories: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Bee Search") }, actions = {
            TextButton(onClick = onOpenSettings) { Text("Настройки") }
        })
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (territory == null) {
                Text("Текущая территория не найдена")
            } else {
                Text("Текущая территория", style = MaterialTheme.typography.titleMedium)
                Text(territory.code, style = MaterialTheme.typography.headlineSmall)
                Text(territory.name)
            }
            Text("Карта и подготовка офлайн-карты будут подключены отдельным этапом. Состояние карты сейчас не моделируется.")
            Button(onClick = onOpenTerritories, modifier = Modifier.fillMaxWidth()) {
                Text("Управление территориями")
            }
        }
    }
}

@Composable
private fun ResumeObservationScreen(point: ObservationPoint, onOpenTerritories: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Незавершённое наблюдение") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Наблюдение восстановлено из Room.", style = MaterialTheme.typography.titleMedium)
            Text("Код наблюдателя: ${point.observerCode}")
            Text("Координаты: ${point.latitude}, ${point.longitude}")
            point.code?.let { Text("Код точки: $it") }
            Text("Рабочий экран регистрации событий будет следующим этапом. Сохранённая точка не потеряна.")
            Button(onClick = onOpenTerritories, modifier = Modifier.fillMaxWidth()) {
                Text("Открыть территории")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TerritoryRowPreview() {
    Bee_searchTheme {
        TerritoryRow(
            territory = Territory(
                id = UUID.randomUUID(),
                code = "KLYAZMA-01",
                name = "Клязьминская пойма",
                createdAt = java.time.Instant.EPOCH,
                updatedAt = java.time.Instant.EPOCH,
            ),
            isCurrent = true,
            onSelect = {},
        )
    }
}
