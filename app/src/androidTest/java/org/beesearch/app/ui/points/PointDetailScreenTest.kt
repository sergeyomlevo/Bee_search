package org.beesearch.app.ui.points

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import java.time.Instant
import java.util.UUID
import org.beesearch.app.data.media.ObservationAttachmentFileStore
import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.BeeObservationHistory
import org.beesearch.app.domain.model.BeePresenceResult
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.MarkPosition
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointDetail
import org.beesearch.app.domain.model.ObservationPointWeather
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.model.WeatherStatus
import org.beesearch.app.domain.model.AttachmentType
import org.beesearch.app.ui.theme.Bee_searchTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PointDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerNamesThePointAndThereIsNoViewTitleOrPropertiesButton() {
        setDetailContent(detail = detail(description = null))

        composeRule.onNodeWithTag("screen-title").assertTextEquals("Точка №16")
        composeRule.onNodeWithText("Просмотр точки").assertDoesNotExist()
        composeRule.onNodeWithText("Свойства").assertDoesNotExist()
        composeRule.onNodeWithTag("open-point-properties").assertDoesNotExist()
    }

    @Test
    fun mainMetadataIsShownAsFullWidthValues() {
        setDetailContent(detail = detail(description = null))

        composeRule.onNodeWithText("Территория").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("DEV-BENCH2 — DEV Territory").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Дата и время").performScrollTo().assertIsDisplayed()
        // The screen renders persisted instants in the device time zone.
        composeRule.onNodeWithText(formatPointDateTime(Instant.parse("2026-09-17T05:00:00Z")))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Координаты").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("56.100000, 43.200000").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Наблюдатель").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Иванов Иван (O1)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun descriptionEmptyStateStoresAndEditsInsideThePointScreen() {
        var startedEditing = false
        setDetailContent(detail = detail(description = null), onStartDescriptionEditing = { startedEditing = true })

        scrollTo("point-description")
        composeRule.onNodeWithTag("point-description").assertIsDisplayed()
        composeRule.onNodeWithTag("point-description-add").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(true, startedEditing) }
    }

    @Test
    fun storedDescriptionIsShownWithAnEditAction() {
        setDetailContent(detail = detail(description = "Встречается у пасеки"))

        scrollTo("point-description")
        composeRule.onNodeWithTag("point-description-text").assertIsDisplayed()
        composeRule.onNodeWithText("Встречается у пасеки").assertIsDisplayed()
        composeRule.onNodeWithTag("point-description-edit").assertIsDisplayed()
    }

    @Test
    fun descriptionEditorIsShownWhileEditing() {
        setDetailContent(
            detail = detail(description = null),
            state = PointDetailUiState(
                detail = detail(description = null),
                isLoading = false,
                descriptionDraft = "Новый текст",
                isDescriptionEditing = true,
            ),
        )

        scrollTo("point-description")
        composeRule.onNodeWithTag("point-description-field").assertIsDisplayed()
        composeRule.onNodeWithTag("point-description-save").assertIsDisplayed()
        composeRule.onNodeWithTag("point-description-cancel").assertIsDisplayed()
    }

    @Test
    fun photosAreShownAndAddedInsideThePointScreen() {
        var pickRequested = 0
        setDetailContent(
            detail = detail(description = null, photos = 1),
            onPickPhoto = { pickRequested += 1 },
        )

        scrollTo("point-photo-take")
        composeRule.onNodeWithTag("point-photo-take").assertIsDisplayed()
        composeRule.onNodeWithTag("point-photo-pick").performScrollTo().assertIsDisplayed().performClick()
        // The former wording is gone and never comes back.
        composeRule.onNodeWithText("Выбрать существующее фото").assertDoesNotExist()
        composeRule.onNodeWithText("Выбрать фото").assertIsDisplayed()
        scrollTo("point-photo-${photoId}")
        composeRule.onNodeWithTag("point-photo-${photoId}").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, pickRequested) }
    }

    @Test
    fun photoDeletionAsksForConfirmationFirst() {
        var deleted: ObservationPointAttachment? = null
        setDetailContent(detail = detail(description = null, photos = 1), onDeletePhoto = { deleted = it })

        scrollTo("point-photo-${photoId}")
        composeRule.onNodeWithContentDescription("Удалить фотографию").performScrollTo().performClick()
        composeRule.onNodeWithText("Удалить фотографию?").assertExists()
        composeRule.onNodeWithTag("cancel-delete-photo").performClick()
        composeRule.onNodeWithText("Удалить фотографию?").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(null, deleted) }
    }

    @Test
    fun weatherSnapshotAndAttributionAreShownInThePointScreen() {
        setDetailContent(detail = detail(description = null, weather = loadedWeather))

        scrollTo("weather-loaded")
        composeRule.onNodeWithTag("weather-loaded").assertIsDisplayed()
        composeRule.onNodeWithText("Температура: 19.1 °C").assertIsDisplayed()
        composeRule.onNodeWithText("Данные погоды: Open-Meteo").assertIsDisplayed()
        composeRule.onNodeWithTag("weather-attribution").assertIsDisplayed()
    }

    @Test
    fun pendingWeatherDoesNotInventValues() {
        setDetailContent(detail = detail(description = null, weather = pendingWeather))

        scrollTo("weather-retry")
        composeRule.onNodeWithText("Погода: ожидает подключения").assertIsDisplayed()
        composeRule.onNodeWithTag("weather-loaded").assertDoesNotExist()
    }

    @Test
    fun beeHistoryKeepsMarksAndCycleFacts() {
        setDetailContent(detail = detail(description = null, bees = 1))

        scrollTo("bee-mark-WHITE-THORAX")
        composeRule.onNodeWithTag("bee-mark-WHITE-THORAX").assertExists()
        scrollTo("detail-bee-$beeId")
        composeRule.onNodeWithTag("detail-bee-$beeId").assertIsDisplayed()
        composeRule.onNodeWithText("Цикл 1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("00:01:10").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("91°").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun completedPointDeleteRequiresConfirmationNamingThePoint() {
        var deleted = 0
        setDetailContent(
            detail = detail(description = null, completed = true),
            onDeletePoint = { deleted += 1 },
        )

        composeRule.onNodeWithTag("point-menu").performClick()
        composeRule.onNodeWithTag("point-menu-delete").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Удалить Точка №16?").assertExists()
        // The confirmation names exactly this point, its date and its Territory.
        composeRule.onNodeWithTag("point-delete-confirm-scope")
            .assertTextContains("DEV-BENCH2 — DEV Territory", substring = true)

        composeRule.onNodeWithTag("cancel-delete-point").performClick()
        composeRule.runOnIdle { assertEquals(0, deleted) }

        composeRule.onNodeWithTag("point-menu").performClick()
        composeRule.onNodeWithTag("point-menu-delete").performClick()
        composeRule.onNodeWithTag("confirm-delete-point").performClick()
        composeRule.runOnIdle { assertEquals(1, deleted) }
    }

    @Test
    fun runningPointOffersNoDeleteAction() {
        // The backend deletes only completed points, so an unfinished point shows no delete command.
        setDetailContent(detail = detail(description = null, completed = false))

        composeRule.onNodeWithTag("point-menu").assertDoesNotExist()
        composeRule.onNodeWithTag("point-menu-delete").assertDoesNotExist()
    }

    @Test
    fun metadataStaysReadableAtLargeSystemFontScale() {
        composeRule.setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density, fontScale = 1.7f),
            ) {
                Bee_searchTheme {
                    PointDetailScreen(
                        state = PointDetailUiState(detail = detail(description = null), isLoading = false),
                        fileStore = null,
                        onBack = {},
                    )
                }
            }
        }

        // The value sits under its label on the full width instead of being squeezed into a
        // right-hand column that becomes unreadable at 1.7 system font scale.
        composeRule.onNodeWithTag("screen-title").assertTextEquals("Точка №16")
        val label = composeRule.onNodeWithText("Территория").performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        val value = composeRule.onNodeWithText("DEV-BENCH2 — DEV Territory").performScrollTo()
            .fetchSemanticsNode().boundsInRoot
        assertEquals(label.left, value.left, 2f)
        assertTrue(
            "The value must start below its label: label=$label value=$value",
            value.top >= label.bottom - 2f,
        )
    }

    @Test
    fun detailMessageIsShownAndDismissible() {
        var dismissed = false
        setDetailContent(
            detail = detail(description = null),
            state = PointDetailUiState(
                detail = detail(description = null),
                isLoading = false,
                message = "Не удалось добавить фотографию",
            ),
            onDismissMessage = { dismissed = true },
        )

        composeRule.onNodeWithTag("point-detail-message").assertIsDisplayed()
        composeRule.onNodeWithTag("point-detail-message-dismiss").performClick()
        composeRule.runOnIdle { assertEquals(true, dismissed) }
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag("point-detail-list").performScrollToNode(hasTestTag(tag))
    }

    private fun setDetailContent(
        detail: ObservationPointDetail,
        state: PointDetailUiState? = null,
        onStartDescriptionEditing: () -> Unit = {},
        onPickPhoto: () -> Unit = {},
        onDeletePhoto: (ObservationPointAttachment) -> Unit = {},
        onDeletePoint: () -> Unit = {},
        onDismissMessage: () -> Unit = {},
    ) {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.setContent {
            Bee_searchTheme {
                PointDetailScreen(
                    state = state ?: PointDetailUiState(detail = detail, isLoading = false),
                    fileStore = ObservationAttachmentFileStore(context.filesDir, context.cacheDir),
                    onBack = {},
                    onStartDescriptionEditing = onStartDescriptionEditing,
                    onPickPhoto = onPickPhoto,
                    onDeletePhoto = onDeletePhoto,
                    onDeletePoint = onDeletePoint,
                    onDismissMessage = onDismissMessage,
                )
            }
        }
    }

    private fun detail(
        description: String?,
        photos: Int = 0,
        bees: Int = 0,
        completed: Boolean = true,
        weather: ObservationPointWeather? = null,
    ) = ObservationPointDetail(
        point = ObservationPoint(
            id = pointId,
            territoryId = devTerritory.id,
            observerId = observer.id,
            observationYear = 2026,
            pointNumber = 16,
            beePresenceResult = BeePresenceResult.BEES_FOUND,
            code = null,
            latitude = 56.1,
            longitude = 43.2,
            gpsLatitude = null,
            gpsLongitude = null,
            gpsAccuracyM = null,
            createdAt = Instant.parse("2026-09-17T05:00:00Z"),
            description = description,
            completedAt = if (completed) Instant.parse("2026-09-17T07:00:00Z") else null,
        ),
        territory = devTerritory,
        observer = observer,
        beeHistories = if (bees == 0) {
            emptyList()
        } else {
            listOf(
                BeeObservationHistory(
                    bee = Bee(
                        id = beeId,
                        observationPointId = pointId,
                        markColor = "WHITE",
                        markPosition = MarkPosition.THORAX,
                        createdAt = Instant.parse("2026-09-17T05:30:00Z"),
                    ),
                    flightCycles = listOf(
                        FlightCycle(
                            id = UUID.randomUUID(),
                            beeId = beeId,
                            sequenceNumber = 1,
                            departureTime = Instant.parse("2026-09-17T05:30:00Z"),
                            returnTime = Instant.parse("2026-09-17T05:31:10Z"),
                            azimuthDeg = 91.0,
                            azimuthCaptureConsumed = true,
                            createdAt = Instant.parse("2026-09-17T05:30:00Z"),
                            updatedAt = Instant.parse("2026-09-17T05:31:10Z"),
                        ),
                    ),
                ),
            )
        },
        weather = weather,
        attachments = List(photos) { index ->
            ObservationPointAttachment(
                id = if (index == 0) photoId else UUID.randomUUID(),
                observationPointId = pointId,
                type = AttachmentType.PHOTO,
                relativePath = "photos/$pointId/photo-$index.jpg",
                originalFileName = "photo-$index.jpg",
                mimeType = "image/jpeg",
                byteSize = 1024L,
                sha256 = "0".repeat(64),
                createdAt = Instant.parse("2026-09-17T05:40:00Z"),
            )
        },
    )

    private companion object {
        val pointId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
        val beeId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
        val photoId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000c1")
        val devTerritory = Territory(
            UUID.fromString("00000000-0000-0000-0000-0000000000d1"),
            "DEV-BENCH2",
            "DEV Territory",
            "Регион",
            "Район",
            Instant.EPOCH,
            Instant.EPOCH,
        )
        val observer = Observer(
            UUID.fromString("00000000-0000-0000-0000-0000000000e1"),
            "O1",
            "Иванов",
            "Иван",
            null,
            null,
            Instant.EPOCH,
            Instant.EPOCH,
        )
        val loadedWeather = ObservationPointWeather(
            observationPointId = pointId,
            status = WeatherStatus.LOADED,
            temperatureC = 19.1,
            windSpeedMps = 2.5,
            windDirectionDeg = 247.0,
            sampleAt = Instant.parse("2026-09-17T05:00:00Z"),
            fetchedAt = Instant.parse("2026-09-17T05:01:00Z"),
            source = "open-meteo",
        )
        val pendingWeather = ObservationPointWeather(
            observationPointId = pointId,
            status = WeatherStatus.PENDING,
            temperatureC = null,
            windSpeedMps = null,
            windDirectionDeg = null,
            sampleAt = null,
            fetchedAt = null,
            source = null,
        )
    }
}
