package org.beesearch.app.ui.help

import org.beesearch.app.ui.map.CREATE_AREA_LABEL
import org.beesearch.app.ui.map.DELETE_AREA_LABEL
import org.beesearch.app.ui.map.EDIT_AREA_SECTIONS_LABEL
import org.beesearch.app.ui.map.LOAD_AREA_MAP_LABEL
import org.beesearch.app.ui.map.OTHER_AREA_MAPS_LABEL
import org.beesearch.app.ui.map.SEND_AREA_LABEL
import org.beesearch.app.ui.map.VIEW_AREA_ON_MAP_LABEL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Content contract of the in-app help.
 *
 * The help must let somebody who has never seen Bee Search perform the real field actions, so the
 * tests below check the meaning: the workflow order, the words the user actually sees on screen, the
 * rules that are easy to get wrong (numbering, deletion, reset) and the absence of internal or
 * promised-but-missing functionality. They deliberately do not pin every sentence.
 */
class HelpContentTest {
    private fun sections() = helpSections("Download/BeeSearch/Beta/Exchange")

    private fun HelpSection.text(): String = blocks.joinToString("\n") { block ->
        when (block) {
            is HelpBlock.Paragraph -> block.text
            is HelpBlock.Steps -> block.items.joinToString("\n")
            is HelpBlock.Bullets -> block.items.joinToString("\n")
            is HelpBlock.Note -> block.text
            is HelpBlock.Preformatted -> block.text
            is HelpBlock.Visual -> block.visual.contentDescription
        }
    }

    private fun section(title: String): HelpSection = sections().single { it.title == title }

    private fun sectionText(title: String): String = section(title).text()

    private fun allText(): String = (listOf(helpIntro) + sections()).joinToString("\n") { it.text() }

    @Test
    fun helpFollowsTheUsersWorkflowOrder() {
        assertEquals(
            listOf(
                "О Bee Search",
                "Первый запуск: Начальная настройка",
                "Наблюдатель и территория",
                "Главный экран: карта",
                "Создание точки наблюдения",
                "Метки и первый вылет",
                "Возврат и следующие вылеты",
                "Азимут",
                "Результаты: точки",
                "Что находится в разделе «Объекты»",
                "Дупла и колоды",
                "Карточка объекта",
                "Удаление объекта и правило номера",
                "Сброс нумерации",
                "Работа без интернета",
                "Ареал и офлайн-карта",
                "Экспорт, удаление данных и файлы",
            ),
            sections().filter { it.level == 1 }.map { it.title },
        )
    }

    @Test
    fun quickStartNamesTheRealScreensAndButtons() {
        val intro = helpIntro.text()

        assertEquals("Краткий старт", helpIntro.title)
        listOf(
            "Начальная настройка",
            "Создать запись здесь",
            "Точка наблюдения",
            "Подготовка точки",
            "Добавить",
            "УЛЕТЕЛА",
            "ПРИЛЕТЕЛА",
            "Объекты",
            "Дупла",
            "Колоды",
            "Точки наблюдения",
            "Настройки",
            "Помощь",
        ).forEach { label ->
            assertTrue("the quick start must name «$label»", intro.contains(label))
        }
    }

    @Test
    fun mainScreenExplainsWhereObjectsAndSettingsAre() {
        val text = sectionText("Главный экран: карта")

        assertTrue(text, text.contains("четыре квадрата"))
        assertTrue(text, text.contains("шестерёнка"))
        assertTrue(text, text.contains("Создать запись здесь"))
        assertTrue(text, text.contains("Онлайн карта"))
        assertTrue(text, text.contains("Векторная карта"))
        assertTrue(text, text.contains("Текущая территория не найдена"))
        assertTrue(text, text.contains("Выбрать территорию"))
    }

    @Test
    fun observationWorkflowDescribesTheRealSequence() {
        val creation = sectionText("Создание точки наблюдения")
        val firstFlight = sectionText("Метки и первый вылет")
        val nextFlights = sectionText("Возврат и следующие вылеты")

        listOf("Создать запись здесь", "Что создать?", "Точка наблюдения", "Подготовка точки", "Добавить")
            .forEach { assertTrue("creation help must name «$it»", creation.contains(it)) }
        assertTrue(creation, creation.contains("Пчёлы отсутствуют"))
        assertTrue(firstFlight, firstFlight.contains("УЛЕТЕЛА"))
        assertTrue(firstFlight, firstFlight.contains("не более 10 пчёл"))
        assertTrue(nextFlights, nextFlights.contains("ПРИЛЕТЕЛА"))
        assertTrue(nextFlights, nextFlights.contains("Завершить"))
    }

    @Test
    fun objectsSectionExplainsTheRouteAndTheFourCategories() {
        val text = sectionText("Что находится в разделе «Объекты»")

        assertTrue(text, text.contains("четыре квадрата"))
        listOf("Ареал", "Точки наблюдения", "Дупла", "Колоды").forEach { category ->
            assertTrue("objects help must name «$category»", text.contains(category))
        }
        assertFalse("the objects screen must not promise Пасеки", text.contains("Пасеки"))
    }

    @Test
    fun hollowAndLogHiveWorkflowNamesTheRealFieldsAndActions() {
        val text = sectionText("Дупла и колоды")

        listOf(
            "Объекты",
            "Дупла",
            "Колоды",
            "Создать запись здесь",
            "Подтвердить",
            "Дерево",
            "Азимут",
            "Зафиксировать с компаса",
            "Высота летка",
            "Наружный диаметр",
            "Материал колоды",
            "Внутренний диаметр",
            "Высота внутреннего объёма",
            "Сделать фото",
            "Выбрать из галереи",
            "Создать",
        ).forEach { label ->
            assertTrue("object help must name «$label»", text.contains(label))
        }
    }

    @Test
    fun objectCardExplainsViewingEditingAndTheMapReturn() {
        val text = sectionText("Карточка объекта")

        listOf("Показать на карте", "Изменить место", "Редактировать характеристики", "Удалить объект")
            .forEach { assertTrue("card help must name «$it»", text.contains(it)) }
        assertTrue(text, text.contains("возвращает в ту же карточку"))
        assertTrue(text, text.contains("не меняет номер"))
    }

    @Test
    fun numberingRuleIsExplainedInUserTerms() {
        val text = sectionText("Удаление объекта и правило номера")

        assertTrue(text, text.contains("не освобождает его номер"))
        assertTrue(text, text.contains("Дупло 1"))
        assertTrue(text, text.contains("Дупло 4"))
        assertTrue(text, text.contains("Дупло 5"))
        assertTrue(text, text.contains("для каждой территории"))
        assertTrue(text, text.contains("для каждого типа"))
        assertTrue("the reason must be the user's own records", text.contains("полевых записях"))
    }

    @Test
    fun protectedObjectCannotBeDeletedAndHelpSaysSo() {
        val text = sectionText("Удаление объекта и правило номера")

        assertTrue(text, text.contains("Объект используется в данных наблюдений и не может быть удалён"))
        assertTrue(text, text.contains("Удалить Дупло N?"))
        assertTrue(text, text.contains("Удалить Колоду N?"))
    }

    @Test
    fun resetIsExplainedWithItsConditionsAndIsolation() {
        val text = sectionText("Сброс нумерации")

        listOf(
            "Сбросить нумерацию",
            "Сбросить нумерацию дупел?",
            "Следующее созданное дупло получит номер 1",
            "не сбрасывает нумерацию колод",
            "только на текущую территорию",
            "ничего не удаляет",
        ).forEach { phrase -> assertTrue("reset help must explain «$phrase»", text.contains(phrase)) }
        assertTrue("reset must be described as unavailable while objects exist", text.contains("действия сброса не будет"))
    }

    @Test
    fun offlineAndOnlineBehaviourIsSeparated() {
        val text = sectionText("Работа без интернета")

        assertTrue(text, text.contains("Работает без интернета"))
        assertTrue(text, text.contains("Требует интернета"))
        assertTrue(text, text.contains("без сети"))
        assertTrue(text, text.contains("Офлайн-карты"))
    }

    @Test
    fun helpDoesNotPromiseFunctionalityThatDoesNotExist() {
        val text = allText()

        listOf("синхронизац", "Восстановить данные", "Свойства точки", "Пасеки")
            .forEach { term -> assertFalse("help must not promise «$term»", text.contains(term)) }
        assertTrue(
            "the help must state that satellite imagery is not available",
            text.contains("спутниковых снимков"),
        )
    }

    @Test
    fun areaActionsAreNamedExactlyAsTheScreensShowThem() {
        val text = allText()

        listOf(
            CREATE_AREA_LABEL,
            VIEW_AREA_ON_MAP_LABEL,
            SEND_AREA_LABEL,
            DELETE_AREA_LABEL,
            EDIT_AREA_SECTIONS_LABEL,
            LOAD_AREA_MAP_LABEL,
            OTHER_AREA_MAPS_LABEL,
        ).forEach { label ->
            assertTrue("help must name the Ареал action «$label»", text.contains("«$label»"))
        }
        assertFalse(text, text.contains("«Загрузить карту ареала»"))
        assertFalse(text, text.contains("«Другие карты ареала»"))
    }

    @Test
    fun exportHelpMatchesTheRealPlaceAndTheRealContent() {
        val export = sectionText("Экспорт данных")

        assertTrue(export, export.contains("Точки наблюдения"))
        assertTrue(export, export.contains("Экспорт всех данных наблюдений"))
        assertTrue(export, export.contains("дупла и колоды"))
        assertTrue(export, export.contains("фото и видео"))
        assertTrue(export, export.contains("Файлы офлайн-карт в экспорт не входят"))
        assertTrue(export, export.contains("Экспортировать точку"))
        assertFalse(
            "the retired settings path must not be documented",
            allText().contains("Настройки → Данные"),
        )
    }

    @Test
    fun exchangeFolderSectionNamesTheFolderOfTheRunningVariant() {
        val beta = helpSections("Download/BeeSearch/Beta/Exchange").single { it.title == "Где находятся файлы Bee Search" }.text()
        val stable = helpSections("Download/BeeSearch/Stable/Exchange").single { it.title == "Где находятся файлы Bee Search" }.text()
        val dev = helpSections("Download/BeeSearch/Dev/Exchange").single { it.title == "Где находятся файлы Bee Search" }.text()

        assertTrue(beta, beta.contains("Download/BeeSearch/Beta/Exchange"))
        assertTrue(stable, stable.contains("Download/BeeSearch/Stable/Exchange"))
        assertTrue(dev, dev.contains("Download/BeeSearch/Dev/Exchange"))
        assertFalse(beta, beta.contains("BeeSearch/Stable/") || beta.contains("BeeSearch/Dev/"))
        listOf(beta, stable, dev).forEach { text ->
            assertTrue(text, text.contains("Areas"))
            assertTrue(text, text.contains("OfflineMaps"))
            assertTrue(text, text.contains("Data"))
        }
    }

    @Test
    fun helpNeverExposesInternalTerminology() {
        val text = allText()

        listOf(
            "Room",
            "high-water",
            "high water",
            "sequence",
            "schema",
            "UUID",
            "migration",
            "repository",
            "RESTRICT",
            "FK",
            "Backup",
            "physical_object",
            "Planetiler",
        ).forEach { term -> assertFalse("help must not contain «$term»", text.contains(term)) }
        assertFalse("decision ids must stay out of the help", Regex("D0\\d\\d").containsMatchIn(text))
    }
}
