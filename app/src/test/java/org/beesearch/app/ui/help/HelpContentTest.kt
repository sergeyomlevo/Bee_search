package org.beesearch.app.ui.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        "Экспорт и очистка данных",
        "Карты",
    )

    private fun sectionText(title: String): String {
        val section = detailedHelpSections.single { it.title == title }
        return (listOf(section.title) + section.paragraphs).joinToString("\n")
    }

    private fun allHelpText(): String =
        (quickStartHelp + detailedHelpSections.flatMap { listOf(it.title) + it.paragraphs })
            .joinToString("\n")

    @Test
    fun sectionTitlesAreTheTopicOnly() {
        assertEquals(expectedTitles, detailedHelpSections.map { it.title })
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
        val text = sectionText("Экспорт и очистка данных")
        assertTrue(text, text.contains("один файл"))
        assertTrue(text, text.contains("не удаляются"))
        assertTrue(text, text.contains("не отправляет"))
        assertTrue(text, text.contains("в экспорт не входят"))
        assertTrue(text, text.contains("покрытие карты"))
        assertTrue(text, text.contains("Одну завершённую точку можно удалить выборочно"))
        assertTrue(text, text.contains("не удаляя остальные наблюдения"))
    }

    @Test
    fun mapFactsAreStated() {
        val text = sectionText("Карты")
        assertTrue(text, text.contains("требует подключения к сети"))
        assertTrue(text, text.contains("экране офлайн-карт"))
        assertTrue(text, text.contains("PMTiles"))
        assertTrue(text, text.contains("выбирается на карте"))
        assertTrue(text, text.contains("переключать"))
    }

    @Test
    fun noInternalTerminologyOrArchiveInternals() {
        val text = allHelpText()
        assertFalse(text, Regex("D0\\d\\d").containsMatchIn(text))
        assertFalse(text, text.contains(".json"))
        assertFalse(text, text.contains("manifest"))
        assertFalse(text, text.contains("Room"))
    }

    @Test
    fun noFutureMapLayersArePromised() {
        val text = allHelpText()
        assertFalse(text, text.contains("спутник"))
        assertFalse(text, text.contains("слои"))
        assertFalse(text, text.contains("собственн"))
    }
}
