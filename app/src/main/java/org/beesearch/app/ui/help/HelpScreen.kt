@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.help

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.beesearch.app.data.exchange.BeeSearchExchangeStorage

/**
 * The in-app help.
 *
 * The text comes from the generated [helpSections] of the canonical Markdown source; this screen
 * only arranges blocks, so a wording change never requires touching the UI.
 */
@Composable
internal fun HelpScreen(
    exchangeStorage: BeeSearchExchangeStorage,
    onBack: () -> Unit,
) {
    val sections = remember(exchangeStorage) { helpSections(exchangeStorage.userVisiblePath()) }
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
                Text(helpIntro.title, style = MaterialTheme.typography.headlineSmall)
            }
            itemsIndexed(helpIntro.blocks) { index, block ->
                HelpBlockView(block = block, tag = "help-intro-$index", level = 1)
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
    val subsection = section.level > 1
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (subsection) 16.dp else 0.dp),
    ) {
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
                    style = if (subsection) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
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
                    section.blocks.forEachIndexed { blockIndex, block ->
                        HelpBlockView(block = block, tag = "help-section-$index-block-$blockIndex", level = section.level)
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpBlockView(block: HelpBlock, tag: String, level: Int) {
    when (block) {
        is HelpBlock.Paragraph -> Text(block.text, modifier = Modifier.testTag(tag))
        is HelpBlock.Steps -> Column(
            modifier = Modifier.testTag(tag),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            block.items.forEachIndexed { index, step -> Text("${index + 1}. $step") }
        }
        is HelpBlock.Bullets -> Column(
            modifier = Modifier.testTag(tag),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            block.items.forEach { item -> Text("• $item") }
        }
        is HelpBlock.Note -> Surface(
            modifier = Modifier.fillMaxWidth().testTag(tag),
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(block.text, modifier = Modifier.padding(12.dp))
        }
        is HelpBlock.Preformatted -> Surface(
            modifier = Modifier.fillMaxWidth().testTag(tag),
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.small,
        ) {
            Text(block.text, modifier = Modifier.padding(12.dp), fontFamily = FontFamily.Monospace)
        }
        is HelpBlock.Visual -> HelpVisualView(block.visual, tag)
    }
}

/**
 * Shows the image of a section when the drawable actually exists.
 *
 * Images are added to the help later, so a declared slot must stay invisible until then: the
 * resource is looked up by the name recorded in the canonical source, and a missing resource simply
 * leaves no gap. The description from the source is used as the accessible name.
 */
@Suppress("DiscouragedApi")
@Composable
private fun HelpVisualView(visual: HelpVisual, tag: String) {
    val resources = LocalResources.current
    val id = resources.getIdentifier(visual.resourceName, "drawable", LocalContext.current.packageName)
    if (id == 0) return
    Image(
        painter = painterResource(id),
        contentDescription = visual.contentDescription,
        modifier = Modifier.fillMaxWidth().testTag(tag),
        contentScale = ContentScale.FillWidth,
    )
}
