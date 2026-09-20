@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.points

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Compact screen header: a back arrow, a one-line title and optional trailing actions.
 *
 * It deliberately replaces a Material `TopAppBar` here. The app-level scaffold already consumes the
 * system bars, and a `TopAppBar` applied the status-bar inset a second time while a text-button
 * `Назад` grew with the system font scale. On the field device at `font_scale=1.7` that bar alone was
 * 96dp; this header stays at the height of one 48dp control.
 */
@Composable
internal fun CompactScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderAction(
            glyph = "←",
            description = "Назад",
            testTag = "screen-back",
            onClick = onBack,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .testTag("screen-title"),
        )
        actions()
    }
}

/** One command of an overflow menu. [isDestructive] marks an irreversible action. */
internal data class HeaderMenuItem(
    val label: String,
    val testTag: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Overflow `⋮` button for a [CompactScreenHeader].
 *
 * The menu is data-driven so a screen can list only the actions it really offers. Nothing is
 * rendered when a screen currently has no action at all.
 */
@Composable
internal fun HeaderMenuButton(
    items: List<HeaderMenuItem>,
    menuDescription: String,
    testTag: String,
) {
    if (items.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        HeaderAction(
            glyph = "⋮",
            description = menuDescription,
            testTag = testTag,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.label,
                            color = if (item.isDestructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        item.onClick()
                    },
                    modifier = Modifier.testTag(item.testTag),
                )
            }
        }
    }
}

@Composable
private fun HeaderAction(
    glyph: String,
    description: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .testTag(testTag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}
