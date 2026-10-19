package org.beesearch.app.ui.map

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Approved GPX import surfaces and the active display-list entry point. */
class GpxImportPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tracksPageKeepsImportAndDisplayNavigationIndependent() {
        var imports = 0
        var backs = 0
        var displays = 0
        rule.setContent {
            GpxTracksPage(onBack = { backs++ }, onImport = { imports++ }, onShowTracks = { displays++ })
        }

        rule.onNodeWithText("Треки GPX").assertIsDisplayed()
        rule.onNodeWithText("Импорт GPX").assertIsDisplayed()
        rule.onNodeWithText("Добавить GPX-файл из памяти телефона").assertIsDisplayed()
        rule.onNodeWithText("Показать трек").assertIsDisplayed()
        rule.onNodeWithText("Выбрать сохранённый трек для карты").assertIsDisplayed()
        rule.onNodeWithTag("gpx-import-action").assertIsEnabled().performClick()
        assertEquals(1, imports)
        rule.onNodeWithTag("gpx-show-track-action").assertIsEnabled().performClick()
        assertEquals(1, displays)
        assertEquals(0, backs)
    }

    @Test
    fun progressShowsFileTerritoryPhaseAndCancel() {
        var cancels = 0
        rule.setContent {
            GpxImportTask(
                state = GpxImportUiState.Importing("поход.gpx", "Лесная поляна"),
                onCancel = { cancels++ },
                onRetryCheck = {},
                onChooseFile = {},
                onImportAnother = {},
                onDone = {},
            )
        }

        rule.onNodeWithText("Импорт GPX…").assertIsDisplayed()
        rule.onNodeWithText("поход.gpx").assertIsDisplayed()
        rule.onNodeWithText("В территорию «Лесная поляна»").assertIsDisplayed()
        rule.onNodeWithText("Сохраняем GPX…").assertIsDisplayed()
        rule.onNodeWithTag("gpx-import-cancel").assertIsDisplayed().performClick()
        assertEquals(1, cancels)
    }

    @Test
    fun successUsesApprovedCopyAndDoesNotOfferMapActivation() {
        var another = 0
        var done = 0
        rule.setContent {
            GpxImportTask(
                state = GpxImportUiState.Success("поход.gpx", "Лесная поляна"),
                onCancel = {},
                onRetryCheck = {},
                onChooseFile = {},
                onImportAnother = { another++ },
                onDone = { done++ },
            )
        }

        rule.onNodeWithText("GPX-файл импортирован").assertIsDisplayed()
        rule.onNodeWithText("Сохранён в территории «Лесная поляна».").assertIsDisplayed()
        rule.onNodeWithText("На карте данные пока не отображаются.").assertIsDisplayed()
        rule.onNodeWithTag("gpx-import-another").assertIsDisplayed().performClick()
        rule.onNodeWithTag("gpx-import-done").assertIsDisplayed().performClick()
        assertEquals(1, another)
        assertEquals(1, done)
    }

    @Test
    fun successWarningUsesExplicitUnsupportedContentCopy() {
        rule.setContent {
            GpxImportTask(
                state = GpxImportUiState.Success("поход.gpx", "Лесная поляна", hasWarnings = true),
                onCancel = {},
                onRetryCheck = {},
                onChooseFile = {},
                onImportAnother = {},
                onDone = {},
            )
        }

        rule.onNodeWithText(
            "Некоторые элементы GPX не поддерживаются и не были перенесены. Файл сохранён без изменений.",
        ).assertIsDisplayed()
    }

    @Test
    fun failureUsesApprovedTextsAndChooseFileAction() {
        var choose = 0
        var close = 0
        rule.setContent {
            GpxImportTask(
                state = GpxImportUiState.Failure(
                    GpxImportFailure.INVALID_GPX, "поход.gpx", "Лесная поляна",
                ),
                onCancel = {},
                onRetryCheck = {},
                onChooseFile = { choose++ },
                onImportAnother = {},
                onDone = { close++ },
            )
        }

        rule.onNodeWithText("Не удалось обработать GPX-файл").assertIsDisplayed()
        rule.onNodeWithText("Файл повреждён или имеет неподдерживаемый формат.").assertIsDisplayed()
        rule.onNodeWithText("Выбрать другой файл").assertIsDisplayed()
        rule.onNodeWithTag("gpx-import-choose-file").performClick()
        rule.onNodeWithTag("gpx-import-close").performClick()
        assertEquals(1, choose)
        assertEquals(1, close)
    }

    @Test
    fun noSpaceOffersChooseFileNotRepeat() {
        rule.setContent {
            GpxImportTask(
                state = GpxImportUiState.Failure(
                    GpxImportFailure.NO_SPACE, "поход.gpx", "Лесная поляна",
                ),
                onCancel = {},
                onRetryCheck = {},
                onChooseFile = {},
                onImportAnother = {},
                onDone = {},
            )
        }

        rule.onNodeWithText("Недостаточно места").assertIsDisplayed()
        rule.onNodeWithText("Выбрать файл").assertIsDisplayed()
        rule.onNodeWithText("Повторить").assertDoesNotExist()
    }

    @Test
    fun duplicateDialogNeedsExplicitChoice() {
        var confirms = 0
        var declines = 0
        rule.setContent {
            GpxDuplicateDialog(
                territoryName = "Лесная поляна",
                onConfirm = { confirms++ },
                onDecline = { declines++ },
            )
        }

        rule.onNodeWithText("Этот GPX-файл уже импортирован").assertIsDisplayed()
        rule.onNodeWithText("Импортировать ещё раз").assertIsDisplayed()
        rule.onNodeWithText("Не импортировать").assertIsDisplayed()
        rule.onNodeWithTag("gpx-duplicate-confirm").performClick()
        assertEquals(1, confirms)
        assertEquals(0, declines)
    }

    @Test
    fun largeFontKeepsResultActionsReadable() {
        val state = GpxImportUiState.Success("поход.gpx", "Лесная поляна")
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.7f)) {
                GpxImportTask(
                    state = state,
                    onCancel = {},
                    onRetryCheck = {},
                    onChooseFile = {},
                    onImportAnother = {},
                    onDone = {},
                )
            }
        }

        rule.onNodeWithText("GPX-файл импортирован").assertIsDisplayed()
        rule.onNodeWithTag("gpx-import-another").assertIsDisplayed()
        rule.onNodeWithTag("gpx-import-done").assertIsDisplayed()
    }
}
