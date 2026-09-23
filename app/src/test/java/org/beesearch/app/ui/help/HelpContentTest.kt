package org.beesearch.app.ui.help

import org.beesearch.app.data.exchange.BeeSearchExchangeStorage
import org.beesearch.app.ui.map.CREATE_AREA_LABEL
import org.beesearch.app.ui.map.CREATE_COVERAGE_FRAGMENT_LABEL
import org.beesearch.app.ui.map.DELETE_AREA_LABEL
import org.beesearch.app.ui.map.EDIT_AREA_SECTIONS_LABEL
import org.beesearch.app.ui.map.LOAD_AREA_MAP_LABEL
import org.beesearch.app.ui.map.OTHER_AREA_MAPS_LABEL
import org.beesearch.app.ui.map.SEND_AREA_LABEL
import org.beesearch.app.ui.map.VIEW_AREA_ON_MAP_LABEL
import org.beesearch.app.ui.map.ADD_COVERAGE_FRAGMENT_LABEL
import org.beesearch.app.ui.map.CLEAR_COVERAGE_LABEL
import org.beesearch.app.ui.map.DISCARD_COVERAGE_CHANGES_LABEL
import org.beesearch.app.ui.map.DONE_COVERAGE_SELECTION_LABEL
import org.beesearch.app.ui.map.SAVE_COVERAGE_CHANGES_LABEL
import org.beesearch.app.ui.map.SHOW_ALL_COVERAGE_LABEL
import org.beesearch.app.ui.map.STAY_IN_COVERAGE_SELECTION_LABEL
import org.beesearch.app.ui.map.UNDO_COVERAGE_FRAGMENT_LABEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Content invariants of the in-app help.
 *
 * These strings are user-visible guidance, so the test asserts the exact promise the app makes
 * (and the exact wording that must not come back) rather than a paraphrase of it. Expansion UX and
 * accessibility semantics are covered by the instrumentation test.
 */
class HelpContentTest {
    private val expectedTitles = listOf(
        "О приложении",
        "Территория и наблюдатель",
        "Точка наблюдения",
        "Метки и первый вылет",
        "Возвраты и следующие циклы",
        "Азимут",
        "Точки",
        "Точка наблюдения: просмотр",
        "Экспорт и удаление данных",
        "Карты",
        AREA_HELP_TITLE,
        AREA_VIEW_HELP_TITLE,
        SEND_AREA_HELP_TITLE,
        "Создание участка офлайн-карты",
        "Загрузка офлайн-карты",
        EXCHANGE_HELP_TITLE,
    )

    private fun exchangeStorage(variant: String = "Beta") = BeeSearchExchangeStorage(
        publicRoot = File("Download"),
        variantName = variant,
    )

    private fun sections() = helpSections(exchangeStorage())

    private fun sectionText(title: String): String {
        val section = sections().single { it.title == title }
        return (listOf(section.title) + section.paragraphs).joinToString("\n")
    }

    private fun allHelpText(): String =
        (quickStartHelp + sections().flatMap { listOf(it.title) + it.paragraphs })
            .joinToString("\n")

    @Test
    fun sectionTitlesAreTheTopicOnly() {
        assertEquals(expectedTitles, sections().map { it.title })
        expectedTitles.forEach { title ->
            assertFalse(title, title.contains("Развернуть"))
            assertFalse(title, title.contains("Свернуть"))
        }
    }

    @Test
    fun quickStartStaysShortAndLeavesDetailsToTheSections() {
        assertEquals(3, quickStartHelp.size)
        assertFalse(quickStartHelp.toString(), quickStartHelp.any { it.contains("Пчёлы отсутствуют") })
        assertFalse(quickStartHelp.toString(), quickStartHelp.any { it.contains("прицел") })
    }

    @Test
    fun pointSectionDescribesAutomaticContinuation() {
        val text = sectionText("Точка наблюдения")
        assertTrue(text, text.contains("открывается автоматически"))
        assertFalse(text, text.contains("предложит"))
        assertTrue(text, text.contains("прицел"))
        assertTrue(text, text.contains("«Добавить»"))
        assertTrue(text, text.contains("Заранее пчёлы не создаются"))
        assertTrue(text, text.contains("«Пчёлы отсутствуют»"))
        assertTrue(text, text.contains("Для нового наблюдения в другой день создайте новую точку"))
        assertTrue(text, text.contains("Завершённая точка повторно не открывается"))
        // The point no longer waits for a first research result to be persisted.
        assertFalse(text, text.contains("первым результатом"))
        assertFalse(text, text.contains("первая добавленная пчела"))
        // The app does not detect a new day itself; the guidance must ask the user to create the point.
        assertFalse(text, text.contains("создаёт новую точку"))
    }

    @Test
    fun flyingAwayGuidanceKeepsTheCurrentPhrase() {
        val required =
            "Если первый вылет отмечен ошибочно, отмените его в карточке пчелы: пчела и её цикл " +
                "будут удалены, а метка снова станет доступным вариантом. После зарегистрированного " +
                "возврата такая отмена недоступна."
        val text = allHelpText()
        assertTrue(text, text.contains(required))
    }

    @Test
    fun firstFlightIsRegisteredPerBeeWithoutPreparedBees() {
        val text = sectionText("Метки и первый вылет")
        assertTrue(text, text.contains("«УЛЕТЕЛА»"))
        assertTrue(text, text.contains("индивидуальное время вылета"))
        assertTrue(text, text.contains("не расходуют лимит"))
        assertTrue(text, text.contains("не более 10 пчёл"))
        // The retired group release must not come back into user-facing guidance.
        assertFalse(text, text.contains("группов"))
        assertFalse(text, text.contains("Выпустить всех"))
        assertFalse(text, text.contains("подготовленных пчёл"))
    }

    @Test
    fun noBeesActionIsExplainedInTheFirstFlightSection() {
        val text = sectionText("Метки и первый вылет") + sectionText("Точка наблюдения")
        assertTrue(text, text.contains("«Пчёлы отсутствуют»"))
        assertTrue(text, text.contains("завершается"))
    }

    @Test
    fun markingGuidanceCoversConsecutivePointsOfOneSearch() {
        val text = sectionText("Метки и первый вылет")
        assertTrue(text, text.contains("последовательных точках одного поиска"))
        assertTrue(text, text.contains("отличимые от меток предыдущих точек"))
        assertTrue(text, text.contains("могут прилетать"))
        assertTrue(text, text.contains("невозможно надёжно определить"))
        // The distinction is a choice of marking, not a colour-only rule.
        assertTrue(text, text.contains("цветом"))
        assertTrue(text, text.contains("положением метки"))
        assertTrue(text, text.contains("их сочетанием"))
        // Guidance about marking only: no nest claim and no app-side restriction.
        assertFalse(text, text.contains("одного гнезда"))
        assertFalse(text, text.contains("запрещ"))
    }

    @Test
    fun theRemovedSubMinuteFlightRuleIsNotRestored() {
        val text = allHelpText()
        assertFalse(text, text.contains("одной минуты"))
        assertFalse(text, text.contains("менее одной минуты"))
    }

    @Test
    fun azimuthGuidanceDoesNotPromiseReplacement() {
        val text = sectionText("Азимут")
        assertTrue(text, text.contains("необязательно"))
        assertTrue(text, text.contains("можно удалить"))
        assertFalse(text, text.contains("замен"))
        assertFalse(text, text.contains("повторн"))
    }

    @Test
    fun undoIsScopedToTheLastReversibleActionOfOneBee() {
        val text = sectionText("Возвраты и следующие циклы")
        assertTrue(text, text.contains("последнему обратимому действию конкретной пчелы"))
        assertFalse(text, text.contains("любое действие"))
    }

    @Test
    fun exportFactsAreStated() {
        val text = sectionText("Экспорт и удаление данных")
        assertTrue(text, text.contains("один файл"))
        assertTrue(text, text.contains("не удаляются"))
        assertTrue(text, text.contains("не отправляет"))
        assertTrue(text, text.contains("в экспорт не входят"))
        assertTrue(text, text.contains("покрытие карты"))
        assertTrue(text, text.contains("Одну завершённую точку можно удалить выборочно"))
        assertTrue(text, text.contains("не удаляя остальные наблюдения"))
    }

    @Test
    fun pointsAndPointScreensAreExplainedWithTheirOwnMenus() {
        val points = sectionText("Точки")
        assertTrue(points, points.contains("территория и год"))
        assertTrue(points, points.contains("не меняет текущую территорию работы"))
        assertTrue(points, points.contains("«Карта» / «Таблица»"))
        assertTrue(points, points.contains("действия сразу над всеми точками"))

        val point = sectionText("Точка наблюдения: просмотр")
        assertTrue(point, point.contains("«Точка №16»"))
        assertTrue(point, point.contains("описание"))
        assertTrue(point, point.contains("фотографии"))
        assertTrue(point, point.contains("погода"))
        assertTrue(point, point.contains("циклами полёта"))
        assertTrue(point, point.contains("Отдельного экрана свойств точки нет"))
        assertTrue(point, point.contains("«Добавить описание»"))
        assertTrue(point, point.contains("«Сделать фото»"))
        assertTrue(point, point.contains("«Выбрать фото»"))
        assertFalse(allHelpText(), allHelpText().contains("Свойства точки"))
    }

    @Test
    fun mapFactsAreStated() {
        val text = sectionText("Карты")
        assertTrue(text, text.contains("требует подключения к сети"))
        assertTrue(text, text.contains("работает без сети"))
        assertTrue(text, text.contains("переключается"))
        // The overview points at the two detailed sections instead of repeating their steps.
        assertTrue(text, text.contains("создаётся ареал территории"))
    }

    @Test
    fun areaSectionExplainsTheModelAndManagementPlace() {
        val text = sectionText(AREA_HELP_TITLE)
        assertTrue(text, text.contains("один ареал"))
        assertTrue(text, text.contains("один прямоугольник"))
        assertTrue(text, text.contains("«Объекты»"))
        assertTrue(text, text.contains("название территории"))
        assertTrue(text, text.contains("«$VIEW_AREA_ON_MAP_LABEL»"))
        assertTrue(text, text.contains("«$SEND_AREA_LABEL»"))
        assertTrue(text, text.contains("«$DELETE_AREA_LABEL»"))
    }

    @Test
    fun areaSectionExplainsTheAutomaticallyCreatedAreaFile() {
        val text = sectionText(AREA_HELP_TITLE) + sectionText("Создание участка офлайн-карты")
        // The user must not be told to copy coordinates by hand: the app owns the file.
        assertTrue(text, text.contains("сам создаёт файл всего ареала"))
        assertTrue(text, text.contains("все участки ареала"))
        assertTrue(text, text.contains("Копировать координаты вручную не требуется"))
        assertTrue(text, text.contains("обновляет файл всего ареала"))
    }

    @Test
    fun areaSectionExplainsHowTheTotalAreaIsCounted() {
        val text = sectionText(AREA_HELP_TITLE)
        assertTrue(text, text.contains("Общая площадь"))
        // Overlap once, gaps not at all - the two cases a user could otherwise misread.
        assertTrue(text, text.contains("перекрытие двух участков учитывается один раз"))
        assertTrue(text, text.contains("промежутки между отдельными участками в площадь не входят"))
    }

    @Test
    fun areaViewSectionSeparatesLookingFromEditing() {
        val text = sectionText(AREA_VIEW_HELP_TITLE)
        assertTrue(text, text.contains("«$VIEW_AREA_ON_MAP_LABEL»"))
        assertTrue(text, text.contains("все его участки"))
        assertTrue(text, text.contains("Панели редактирования на этом экране нет"))
        assertTrue(text, text.contains("«$EDIT_AREA_SECTIONS_LABEL»"))
    }

    @Test
    fun sendSectionExplainsTheSystemMenuAndThePreparedFile() {
        val text = sectionText(SEND_AREA_HELP_TITLE)
        assertTrue(text, text.contains("«$SEND_AREA_LABEL»"))
        assertTrue(text, text.contains("меню Android"))
        assertTrue(text, text.contains("сам готовит файл ареала"))
        assertTrue(text, text.contains("искать файл в «Загрузках» вручную не нужно"))
    }

    @Test
    fun helpExplainsExplicitAreaDeletionInsteadOfClearingTheEditor() {
        val text = sectionText(AREA_HELP_TITLE) + sectionText("Создание участка офлайн-карты")
        assertTrue(text, text.contains("пустой ареал не сохраняется"))
        assertTrue(text, text.contains("только отдельным действием"))
        assertTrue(text, text.contains("территория, точки наблюдения и установленная офлайн-карта остаются"))
    }

    @Test
    fun helpExplainsFirstSaveNamingWithoutOfferingRename() {
        val text = sectionText("Создание участка офлайн-карты")
        assertTrue(text, text.contains("предлагает название ареала"))
        assertTrue(text, text.contains("дальше имя не спрашивается"))
        assertTrue(text, text.contains("сохраняет и название, и свой идентификатор"))
        // The name is set once and the card has no rename action any more, so help must not offer one.
        assertFalse(allHelpText(), allHelpText().contains("«Переименовать»"))
    }

    @Test
    fun participantSectionExplainsSelectionVocabulary() {
        val text = sectionText("Создание участка офлайн-карты")
        assertTrue(text, text.contains("прямоугольная область"))
        assertTrue(text, text.contains("Оранжевая рамка"))
        assertTrue(text, text.contains("синим прямоугольником"))
        assertTrue(text, text.contains("Участков может быть несколько"))
        assertTrue(text, text.contains("«Офлайн-карты»"))
        assertTrue(text, text.contains("«$CREATE_AREA_LABEL»"))
        assertTrue(text, text.contains("«$EDIT_AREA_SECTIONS_LABEL»"))
    }

    @Test
    fun helpNamesTheAreaLifecycleActionsTheScreensShow() {
        val text = allHelpText()
        listOf(
            CREATE_AREA_LABEL,
            VIEW_AREA_ON_MAP_LABEL,
            SEND_AREA_LABEL,
            EDIT_AREA_SECTIONS_LABEL,
            DELETE_AREA_LABEL,
        ).forEach { label ->
            assertTrue("help must name the Ареал action «$label»", text.contains("«$label»"))
        }
        // The retired coverage-only label must not survive as a current action name.
        assertFalse(text, text.contains("«Выбрать участок»"))
        assertFalse(text, text.contains("«Изменить участок»"))
    }

    @Test
    fun participantSectionNamesExactlyTheActionsTheEditorShows() {
        val text = sectionText("Создание участка офлайн-карты")
        listOf(
            CREATE_COVERAGE_FRAGMENT_LABEL,
            ADD_COVERAGE_FRAGMENT_LABEL,
            UNDO_COVERAGE_FRAGMENT_LABEL,
            SHOW_ALL_COVERAGE_LABEL,
            CLEAR_COVERAGE_LABEL,
            DONE_COVERAGE_SELECTION_LABEL,
        ).forEach { label ->
            assertTrue("help must name the editor action «$label»", text.contains("«$label»"))
        }
        // Copying a bbox by hand was retired with the Area file: it must not be offered again.
        assertFalse(text, text.contains("Копировать bbox"))
        assertFalse(text, text.contains("bbox"))
    }

    @Test
    fun retiredEditorActionNamesAreNotPresentedAsCurrent() {
        val text = allHelpText()
        // The editor used to end with a plain "Выйти", and clear used to be called "Сброс".
        // "Отмена" is now deliberately scoped to the unfinished current fragment.
        assertFalse(text, text.contains("«Выйти»"))
        assertFalse(text, text.contains("«Сброс»"))
    }

    @Test
    fun doneSavesAndLeavesTheEditor() {
        val text = sectionText("Создание участка офлайн-карты")
        assertTrue(
            text,
            text.contains("«$DONE_COVERAGE_SELECTION_LABEL» сохраняет выбранные участки и завершает редактирование"),
        )
    }

    @Test
    fun unsavedChangesDecisionIsExplainedWithoutYesNo() {
        val text = sectionText("Создание участка офлайн-карты")
        listOf(
            SAVE_COVERAGE_CHANGES_LABEL,
            DISCARD_COVERAGE_CHANGES_LABEL,
            STAY_IN_COVERAGE_SELECTION_LABEL,
        ).forEach { label ->
            assertTrue("help must name the choice «$label»", text.contains("«$label»"))
        }
        assertTrue(text, text.contains("Назад"))
        assertTrue(text, text.contains("Если изменений нет"))
        // The confirmation offers named outcomes, never an ambiguous yes/no pair.
        assertFalse(text, text.contains("«Да»"))
        assertFalse(text, text.contains("«Нет»"))
    }

    @Test
    fun clearingIsDescribedAsConfirmedAndDraftScoped() {
        val text = sectionText("Создание участка офлайн-карты")
        assertTrue(text, text.contains("спрашивает подтверждение"))
        assertTrue(text, text.contains("изменения существуют только на экране"))
    }

    @Test
    fun mapLoadingSectionNamesTheTwoFilesOfThePair() {
        val text = sectionText("Загрузка офлайн-карты")
        // The user has to pick these two files, so their names are operational guidance rather
        // than an implementation detail; the offline-map screen shows the same names.
        assertTrue(text, text.contains("*.pmtiles.manifest.json"))
        assertTrue(text, text.contains("*.pmtiles"))
        assertTrue(text, text.contains("Нужны оба файла"))
        assertTrue(text, text.contains("имя файла карты должно точно совпадать"))
    }

    @Test
    fun mapLoadingSectionDescribesTheRealImportWorkflow() {
        val text = sectionText("Загрузка офлайн-карты")
        assertTrue(text, text.contains("«Офлайн-карты»"))
        assertTrue(text, text.contains("«Импортировать карту»"))
        assertTrue(text, text.contains("«Заменить карту»"))
        assertTrue(text, text.contains("сначала выберите файл описания карты, затем — файл самой карты"))
        assertTrue(text, text.contains("импортирована и активирована"))
        assertTrue(text, text.contains("«Онлайн карта»"))
        assertTrue(text, text.contains("«Векторная карта»"))
    }

    @Test
    fun mapLoadingSectionExplainsAutomaticDiscoveryFromTheAreaScreen() {
        val text = sectionText("Загрузка офлайн-карты")
        // The user finds the new entry point, the file place, and what happens when nothing is found.
        assertTrue(text, text.contains("«Объекты» → «Ареал»"))
        assertTrue(text, text.contains("«$LOAD_AREA_MAP_LABEL»"))
        assertTrue(text, text.contains("OfflineMaps"))
        assertTrue(text, text.contains("определить карту этого ареала по имени файла"))
        assertTrue(text, text.contains("«$OTHER_AREA_MAPS_LABEL»"))
        assertTrue(text, text.contains("стандартный выбор файлов Android"))
        assertTrue(text, text.contains("Это обычная ситуация, а не ошибка"))
    }

    @Test
    fun mapLoadingSectionSaysAnExactNameStillGetsValidated() {
        val text = sectionText("Загрузка офлайн-карты")
        assertTrue(text, text.contains("Перед загрузкой карта всегда проверяется"))
        assertTrue(text, text.contains("имя помогает только найти файл"))
        assertTrue(text, text.contains("предыдущая установленная карта остаётся рабочей"))
        // No implementation vocabulary for an ordinary user.
        listOf("UUID", "regex", "Scoped Storage", "MediaStore", "coverageFragments", "SHA").forEach { term ->
            assertFalse(term, text.contains(term))
        }
    }

    @Test
    fun incompleteCoverageRefusalIsExplained() {
        val text = sectionText("Загрузка офлайн-карты")
        assertTrue(text, text.contains("неполном покрытии"))
        assertTrue(text, text.contains("полностью включает выбранные участки"))
        // A failed attempt must not be described as losing the installed map.
        assertTrue(text, text.contains("не удаляется"))
    }

    @Test
    fun exchangeSectionNamesTheFolderOfTheRunningVariant() {
        val text = sectionText(EXCHANGE_HELP_TITLE)
        assertTrue(text, text.contains("Download/BeeSearch/Beta/Exchange"))
        assertTrue(text, text.contains("Areas"))
        assertTrue(text, text.contains("OfflineMaps"))
        assertTrue(text, text.contains("Data"))
        assertTrue(text, text.contains("папка обмена"))
        assertTrue(text, text.contains("не внутреннее хранилище приложения"))
        // Deleting an exchange file must be described as harmless for imported data.
        assertTrue(text, text.contains("не повредит уже импортированную карту"))
    }

    @Test
    fun exchangeSectionIsVariantSpecific() {
        val beta = exchangeHelpSection(exchangeStorage("Beta")).paragraphs.joinToString("\n")
        val stable = exchangeHelpSection(exchangeStorage("Stable")).paragraphs.joinToString("\n")
        val dev = exchangeHelpSection(exchangeStorage("Dev")).paragraphs.joinToString("\n")

        assertTrue(beta, beta.contains("Download/BeeSearch/Beta/Exchange"))
        assertTrue(stable, stable.contains("Download/BeeSearch/Stable/Exchange"))
        assertTrue(dev, dev.contains("Download/BeeSearch/Dev/Exchange"))

        // A variant must never advertise another variant's folder as its own.
        assertFalse(beta, beta.contains("BeeSearch/Stable/") || beta.contains("BeeSearch/Dev/"))
        assertFalse(stable, stable.contains("BeeSearch/Beta/") || stable.contains("BeeSearch/Dev/"))
        assertFalse(dev, dev.contains("BeeSearch/Beta/") || dev.contains("BeeSearch/Stable/"))
    }

    @Test
    fun noInternalTerminologyOrArchiveInternals() {
        val text = allHelpText()
        assertFalse(text, Regex("D0\\d\\d").containsMatchIn(text))
        assertFalse(text, text.contains("Room"))
        assertFalse(text, text.contains("SHA"))
        assertFalse(text, text.contains("Planetiler"))
        assertFalse(text, text.contains("schemaVersion"))
        assertFalse(text, text.contains("PMTiles v3"))
    }

    @Test
    fun noFutureMapLayersArePromised() {
        val text = allHelpText()
        assertFalse(text, text.contains("спутник"))
        assertFalse(text, text.contains("слои"))
        assertFalse(text, text.contains("собственн"))
    }
}
