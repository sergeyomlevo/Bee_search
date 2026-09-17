package org.beesearch.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun CreateRecordTypeChooserDialog(
    onDismiss: () -> Unit,
    onCreateObservationPoint: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что создать?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CreateTypeButton(
                    label = "Точка наблюдения",
                    enabled = true,
                    onClick = onCreateObservationPoint,
                    testTag = "create-observation-point-type",
                )
                FutureCreateTypeButton("Дупло")
                FutureCreateTypeButton("Колода")
                FutureCreateTypeButton("Ловушка")
                FutureCreateTypeButton("Пасека")
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss-create-type-chooser")) {
                Text("Отмена")
            }
        },
        modifier = Modifier.testTag("create-type-chooser"),
    )
}

@Composable
private fun FutureCreateTypeButton(label: String) {
    CreateTypeButton(
        label = "$label — пока недоступно",
        enabled = false,
        onClick = {},
        testTag = "future-create-type-${label.lowercase()}",
    )
}

@Composable
private fun CreateTypeButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
        Text(label)
    }
}
