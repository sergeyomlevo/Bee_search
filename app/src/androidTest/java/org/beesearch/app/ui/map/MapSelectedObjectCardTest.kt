package org.beesearch.app.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MapSelectedObjectCardTest {
    @get:Rule
    val rule = createComposeRule()

    private fun marker(type: MapObjectType, label: String = type.name): MapObjectMarker =
        MapObjectMarker(type, UUID.randomUUID(), 56.0, 43.0, label)

    @Test
    fun openPassesExactMarkerForEveryResearchObjectTypeAndCloseIsDistinct() {
        val markers = MapObjectType.entries.map { marker(it, "Запись ${it.name}") }
        var selected by mutableStateOf(markers.first())
        var opened: MapObjectMarker? = null
        var closed = 0
        rule.setContent {
            MapSelectedObjectCard(selected, onOpen = { opened = it }, onClose = { closed++ })
        }
        markers.forEach { marker ->
            rule.runOnIdle { selected = marker; opened = null; closed = 0 }
            rule.onNodeWithTag(SELECTED_OBJECT_OPEN_TAG, useUnmergedTree = true).performClick()
            assertEquals(marker, opened)
            rule.onNodeWithContentDescription("Закрыть выбранный объект").performClick()
            assertEquals(1, closed)
        }
    }

    @Test
    fun switchingMarkerUpdatesLabelAndOpenIdentity() {
        val first = marker(MapObjectType.HOLLOW, "Первое дупло")
        val second = marker(MapObjectType.LOG_HIVE, "Вторая колода")
        var selected by mutableStateOf(first)
        var opened: MapObjectMarker? = null
        rule.setContent {
            MapSelectedObjectCard(selected, onOpen = { opened = it }, onClose = {}, modifier = Modifier)
        }
        rule.onNodeWithText(first.label).assertIsDisplayed()
        selected = second
        rule.onNodeWithText(second.label).assertIsDisplayed()
        rule.onNodeWithTag(SELECTED_OBJECT_OPEN_TAG, useUnmergedTree = true).performClick()
        assertEquals(second, opened)
    }

    @Test
    fun cardRemainsReadableAtLargeFontScale() {
        val selected = marker(MapObjectType.OBSERVATION_POINT, "Точка наблюдения, пчёлы найдены")
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(1f, 1.7f)) {
                MapSelectedObjectCard(selected, onOpen = {}, onClose = {})
            }
        }
        rule.onNodeWithTag(SELECTED_OBJECT_CARD_TAG, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(selected.label).assertIsDisplayed()
        rule.onNodeWithTag(SELECTED_OBJECT_OPEN_TAG, useUnmergedTree = true).assertIsDisplayed()
    }
}
