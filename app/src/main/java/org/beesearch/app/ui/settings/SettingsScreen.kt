@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import java.util.UUID
import org.beesearch.app.ui.territory.TerritoryRow

@Composable
internal fun SettingsScreen(
    observers: List<Observer>,
    currentObserverId: UUID?,
    territories: List<Territory>,
    currentTerritoryId: UUID?,
    onBack: () -> Unit,
    onOpenOfflineMaps: () -> Unit = {},
    onSelectObserver: (UUID) -> Unit,
    onCreateObserver: (String, String, String, String, String) -> Unit,
    onUpdateObserver: (Observer) -> Unit = {},
    onDeleteObserver: (Observer) -> Unit = {},
    onSelectTerritory: (UUID) -> Unit,
    onCreateTerritory: (String, String, String, String) -> Unit,
    onUpdateTerritory: (Territory) -> Unit = {},
    onDeleteTerritory: (Territory) -> Unit = {},
) {
    var addingObserver by rememberSaveable { mutableStateOf(false) }
    var addingTerritory by rememberSaveable { mutableStateOf(false) }
    var editingObserver by remember { mutableStateOf<Observer?>(null) }
    var editingTerritory by remember { mutableStateOf<Territory?>(null) }
    var deleteObserver by remember { mutableStateOf<Observer?>(null) }
    var deleteTerritory by remember { mutableStateOf<Territory?>(null) }
    var observerCode by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var middleName by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var territoryCode by rememberSaveable { mutableStateOf("") }
    var territoryName by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var district by rememberSaveable { mutableStateOf("") }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
            TopAppBar(title = { Text("Настройки") }, navigationIcon = {
                TextButton(onClick = {
                    if (addingObserver || addingTerritory || editingObserver != null || editingTerritory != null) {
                        addingObserver = false
                        addingTerritory = false
                        editingObserver = null
                        editingTerritory = null
                    } else onBack()
                }) { Text("Назад") }
            })
        },
    ) { padding ->
        BackHandler(enabled = addingObserver || addingTerritory || editingObserver != null || editingTerritory != null) {
            addingObserver = false; addingTerritory = false; editingObserver = null; editingTerritory = null
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Офлайн-карты", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Покрытие, состояние активной карты, импорт и замена.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onOpenOfflineMaps) { Text("Открыть") }
                }
            }
            Text("Наблюдатель", style = MaterialTheme.typography.titleMedium)
            if (observers.isEmpty()) Text("Наблюдателей пока нет.")
            observers.forEach { observer ->
                ObserverRow(
                    observer, observer.id == currentObserverId,
                    onSelect = { onSelectObserver(observer.id) },
                    onEdit = { editingObserver = observer; addingObserver = false },
                    onDelete = { deleteObserver = observer },
                )
            }
            TextButton(onClick = { addingObserver = !addingObserver }) {
                Text(if (addingObserver) "Скрыть форму наблюдателя" else "+ Добавить наблюдателя")
            }
            if (addingObserver || editingObserver != null) {
                val edit = editingObserver
                Text(if (edit == null) "Добавить наблюдателя" else "Изменить наблюдателя", style = MaterialTheme.typography.titleSmall)
                val codeValue = edit?.code ?: observerCode
                val lastValue = edit?.lastName ?: lastName
                val firstValue = edit?.firstName ?: firstName
                val middleValue = edit?.middleName ?: middleName
                val contactValue = edit?.contact ?: contact
                OutlinedTextField(codeValue, { if (edit == null) observerCode = it else editingObserver = edit.copy(code = it) }, Modifier.fillMaxWidth(), label = { Text("Код") }, singleLine = true)
                OutlinedTextField(lastValue, { if (edit == null) lastName = it else editingObserver = edit.copy(lastName = it) }, Modifier.fillMaxWidth(), label = { Text("Фамилия") }, singleLine = true)
                OutlinedTextField(firstValue, { if (edit == null) firstName = it else editingObserver = edit.copy(firstName = it) }, Modifier.fillMaxWidth(), label = { Text("Имя") }, singleLine = true)
                OutlinedTextField(middleValue, { if (edit == null) middleName = it else editingObserver = edit.copy(middleName = it) }, Modifier.fillMaxWidth(), label = { Text("Отчество (необязательно)") }, singleLine = true)
                OutlinedTextField(contactValue, { if (edit == null) contact = it else editingObserver = edit.copy(contact = it) }, Modifier.fillMaxWidth(), label = { Text("Контакт (необязательно)") })
                Button(onClick = {
                    if (edit == null) { onCreateObserver(observerCode, lastName, firstName, middleName, contact); addingObserver = false }
                    else { onUpdateObserver(edit); editingObserver = null }
                }, enabled = codeValue.isNotBlank() && lastValue.isNotBlank() && firstValue.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (edit == null) "Добавить наблюдателя" else "Сохранить") }
            }
            HorizontalDivider()
            Text("Территории", style = MaterialTheme.typography.titleMedium)
            if (territories.isEmpty()) Text("Территорий пока нет.")
            territories.forEach { territory ->
                TerritoryRow(territory, territory.id == currentTerritoryId, { onSelectTerritory(territory.id) }, { editingTerritory = territory; addingTerritory = false }, { deleteTerritory = territory })
            }
            TextButton(onClick = { addingTerritory = !addingTerritory }) {
                Text(if (addingTerritory) "Скрыть форму территории" else "+ Добавить территорию")
            }
            if (addingTerritory || editingTerritory != null) {
                val edit = editingTerritory
                Text(if (edit == null) "Добавить территорию" else "Изменить территорию", style = MaterialTheme.typography.titleSmall)
                val codeValue = edit?.code ?: territoryCode; val nameValue = edit?.name ?: territoryName; val regionValue = edit?.region ?: region; val districtValue = edit?.district ?: district
                OutlinedTextField(codeValue, { if (edit == null) territoryCode = it else editingTerritory = edit.copy(code = it) }, Modifier.fillMaxWidth(), label = { Text("Код") }, singleLine = true)
                OutlinedTextField(nameValue, { if (edit == null) territoryName = it else editingTerritory = edit.copy(name = it) }, Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
                OutlinedTextField(regionValue, { if (edit == null) region = it else editingTerritory = edit.copy(region = it) }, Modifier.fillMaxWidth(), label = { Text("Область / регион") }, singleLine = true)
                OutlinedTextField(districtValue, { if (edit == null) district = it else editingTerritory = edit.copy(district = it) }, Modifier.fillMaxWidth(), label = { Text("Район") }, singleLine = true)
                Button(onClick = {
                    if (edit == null) { onCreateTerritory(territoryCode, territoryName, region, district); addingTerritory = false }
                    else { onUpdateTerritory(edit); editingTerritory = null }
                }, enabled = codeValue.isNotBlank() && nameValue.isNotBlank() && regionValue.isNotBlank() && districtValue.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (edit == null) "Добавить территорию" else "Сохранить") }
            }
        }
    }
    deleteObserver?.let { observer -> AlertDialog(onDismissRequest = { deleteObserver = null }, title = { Text("Удалить наблюдателя?") }, text = { Text(observer.displayName) }, confirmButton = { TextButton(onClick = { onDeleteObserver(observer); deleteObserver = null }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deleteObserver = null }) { Text("Отмена") } }) }
    deleteTerritory?.let { territory -> AlertDialog(onDismissRequest = { deleteTerritory = null }, title = { Text("Удалить территорию?") }, text = { Text("${territory.code} — ${territory.name}") }, confirmButton = { TextButton(onClick = { onDeleteTerritory(territory); deleteTerritory = null }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deleteTerritory = null }) { Text("Отмена") } }) }
}

@Composable
private fun ObserverRow(observer: Observer, isCurrent: Boolean, onSelect: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !isCurrent, onClick = onSelect)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text((if (isCurrent) "● " else "○ ") + observer.displayName, fontWeight = FontWeight.Bold)
            Text("Код: ${observer.code}")
            observer.contact?.let { Text("Контакт: $it") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Изменить") }
                TextButton(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}
