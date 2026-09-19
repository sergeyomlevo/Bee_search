package org.beesearch.app.ui.properties

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.util.UUID
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.model.WeatherStatus
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PointPropertiesScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun descriptionPhotoActionsAndLoadedWeatherAreReachable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = context.cacheDir.resolve("properties-screen-${UUID.randomUUID()}")
        val fileStore = ObservationAttachmentFileStore(root.resolve("files"), root.resolve("cache"))
        val description = mutableStateOf("")
        var saves = 0
        var takePhoto = false
        var pickPhoto = false
        composeRule.setContent {
            Bee_searchTheme {
                PointPropertiesContent(
                    detail = detail(),
                    state = PointPropertiesUiState(
                        detail = detail(),
                        isLoading = false,
                        descriptionDraft = description.value,
                    ),
                    fileStore = fileStore,
                    modifier = androidx.compose.ui.Modifier,
                    onDescriptionChanged = { description.value = it },
                    onSaveDescription = { saves++ },
                    onTakePhoto = { takePhoto = true },
                    onPickPhoto = { pickPhoto = true },
                    onDeletePhoto = {},
                    onRetryWeather = {},
                )
            }
        }
        composeRule.onNodeWithTag("point-properties-description").performTextReplacement("Лесная опушка")
        composeRule.onNodeWithTag("point-properties-save-description").performClick()
        composeRule.onNodeWithTag("point-properties-take-photo").performScrollTo().performClick()
        composeRule.onNodeWithTag("point-properties-pick-photo").performClick()
        composeRule.onNodeWithText("Температура: 18.4 °C").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Направление: 247.0° (ЗЮЗ)").assertIsDisplayed()
        composeRule.onNodeWithTag("weather-attribution").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("Лесная опушка", description.value)
            assertEquals(1, saves)
            assertTrue(takePhoto)
            assertTrue(pickPhoto)
        }
        root.deleteRecursively()
    }

    private fun detail(): ObservationPointDetail {
        val pointId = UUID.randomUUID()
        val territory = Territory(UUID.randomUUID(), "T", "Территория", "Регион", "Район", Instant.EPOCH, Instant.EPOCH)
        val observer = Observer(UUID.randomUUID(), "O", "Иванов", "Иван", null, null, Instant.EPOCH, Instant.EPOCH)
        return ObservationPointDetail(
            point = ObservationPoint(
                pointId, territory.id, observer.id, 2026, 1, null, null,
                56.1, 42.7, 56.1, 42.7, 3.0, Instant.parse("2026-09-17T10:30:00Z"),
                completedAt = null,
            ),
            territory = territory,
            observer = observer,
            beeHistories = emptyList(),
            weather = ObservationPointWeather(
                pointId, WeatherStatus.LOADED, 18.4, 2.1, 247.0,
                Instant.parse("2026-09-17T11:00:00Z"), Instant.parse("2026-09-17T11:01:00Z"), "OPEN_METEO",
            ),
        )
    }
}
