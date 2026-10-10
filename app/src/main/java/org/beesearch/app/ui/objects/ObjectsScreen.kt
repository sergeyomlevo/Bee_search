@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.objects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The top level of `Объекты` lists categories only.
 *
 * Physical Object instances belong to the `Дупла` and `Колоды` lists, so nothing about a concrete
 * object is presented or expanded here.
 */
@Composable
internal fun ObjectsScreen(
    onBack: () -> Unit,
    onOpenObservationPoints: () -> Unit,
    onOpenHollows: () -> Unit = {},
    onOpenLogHives: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Объекты") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("Точки наблюдения") },
                supportingContent = { Text("Таблица и история наблюдений") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenObservationPoints)
                    .testTag("objects-observation-points"),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Дупла") },
                supportingContent = { Text("Естественные гнёзда этой территории") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenHollows)
                    .testTag("objects-hollows"),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Колоды") },
                supportingContent = { Text("Искусственные гнёзда этой территории") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenLogHives)
                    .testTag("objects-log-hives"),
            )
            HorizontalDivider()
        }
    }
}
