@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage

@Composable
internal fun HelpScreen(
    exchangeStorage: BeeSearchExchangeStorage,
    onBack: () -> Unit,
) {
    val sections = remember(exchangeStorage) { helpSections(exchangeStorage) }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Помощь") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("help-screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Краткий старт", style = MaterialTheme.typography.headlineSmall)
            }
            itemsIndexed(quickStartHelp) { index, step ->
                Text("${index + 1}. $step")
            }
            item {
                Text(
                    "Подробная помощь",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            itemsIndexed(sections) { index, section ->
                HelpSectionCard(section = section, index = index)
            }
        }
    }
}

@Composable
private fun HelpSectionCard(section: HelpSection, index: Int) {
    var expanded by rememberSaveable(section.title) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        role = Role.Button
                        stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто"
                    }
                    .testTag("help-section-$index"),
            ) {
                // The visible header is the topic only: the expansion state is carried by the
                // indicator below and by `stateDescription`, never by the wording of the title.
                Text(
                    text = section.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                // Decorative affordance only: cleared from semantics so the header keeps the
                // topic as its accessible name.
                Text(
                    text = if (expanded) "▴" else "▾",
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clearAndSetSemantics { },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    section.paragraphs.forEach { paragraph -> Text(paragraph) }
                }
            }
        }
    }
}
