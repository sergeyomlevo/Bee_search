@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.beesearch.app.ui.observation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeMarkCatalog
import org.beesearch.app.domain.model.BeeMarkCombination
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.BeeSelectorSelectionLogic

@Composable
internal fun BeeSelector(
    bees: List<Bee>,
    enabled: Boolean,
    onAddBee: (String, MarkPosition) -> Unit,
) {
    val used = bees.mapTo(mutableSetOf()) { BeeMarkCombination(it.markColor, it.markPosition) }
    val available = BeeMarkCatalog.availableCombinations(used)
    var selectedColor by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPositionName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingColor by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPositionName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedColorValue = selectedColor
    val selectedPosition = selectedPositionName?.let(MarkPosition::valueOf)
    val selectedCombination = if (selectedColorValue != null && selectedPosition != null) {
        BeeMarkCombination(selectedColorValue, selectedPosition)
    } else {
        null
    }
    val pendingCombination = if (pendingColor != null && pendingPositionName != null) {
        BeeMarkCombination(pendingColor!!, MarkPosition.valueOf(pendingPositionName!!))
    } else {
        null
    }

    fun updateSelection(combination: BeeMarkCombination?) {
        selectedColor = combination?.markColor
        selectedPositionName = combination?.markPosition?.name
    }

    fun clearPendingAddition() {
        pendingColor = null
        pendingPositionName = null
    }

    LaunchedEffect(available, pendingCombination, selectedCombination, used) {
        when {
            pendingCombination != null && pendingCombination in used -> {
                updateSelection(
                    BeeSelectorSelectionLogic.nextAfterAdded(pendingCombination, available),
                )
                clearPendingAddition()
            }
            pendingCombination == null -> updateSelection(
                BeeSelectorSelectionLogic.reconcileSelection(selectedCombination, available),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Добавить пчелу", style = MaterialTheme.typography.labelMedium)
        if (available.isEmpty()) {
            Text("Все поддерживаемые сочетания меток уже добавлены.")
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().testTag("add-bee"),
            ) { Text("Добавить") }
        } else {
            Text("Цвет метки", style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BeeMarkCatalog.colors.forEach { color ->
                    val hasAvailablePosition = available.any { it.markColor == color.value }
                    val isSelected = selectedColor == color.value
                    val isEnabled = enabled && hasAvailablePosition
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .selectable(
                                selected = isSelected,
                                enabled = isEnabled,
                                role = Role.RadioButton,
                                onClick = {
                                    clearPendingAddition()
                                    updateSelection(
                                        BeeSelectorSelectionLogic.firstAvailableForColor(
                                            color.value,
                                            available,
                                        ),
                                    )
                                },
                            )
                            .semantics {
                                contentDescription = "Цвет метки: ${color.displayName}"
                            }
                            .testTag("mark-color-${color.value}"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(markColorValue(color.value), CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Text(
                                    text = "✓",
                                    color = if (color.value == "WHITE" || color.value == "YELLOW") {
                                        Color.Black
                                    } else {
                                        Color.White
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
            Text("Расположение метки", style = MaterialTheme.typography.labelSmall)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BeeMarkCatalog.positions.forEachIndexed { index, position ->
                    val combination = selectedColor?.let { BeeMarkCombination(it, position) }
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = BeeMarkCatalog.positions.size,
                        ),
                        selected = selectedPosition == position,
                        onClick = {
                            clearPendingAddition()
                            selectedColor?.let { color ->
                                updateSelection(
                                    BeeSelectorSelectionLogic.manualPosition(
                                        color,
                                        position,
                                        available,
                                    ),
                                )
                            }
                        },
                        enabled = enabled && combination in available,
                        modifier = Modifier.testTag("mark-position-${position.name}"),
                        label = { Text(BeeMarkCatalog.positionDisplayName(position)) },
                    )
                }
            }
            Button(
                onClick = {
                    selectedCombination?.let { combination ->
                        pendingColor = combination.markColor
                        pendingPositionName = combination.markPosition.name
                        onAddBee(combination.markColor, combination.markPosition)
                    }
                },
                enabled = enabled && selectedCombination in available,
                modifier = Modifier.fillMaxWidth().testTag("add-bee"),
            ) { Text(if (enabled) "Добавить" else "Сохранение…") }
        }
    }
}
