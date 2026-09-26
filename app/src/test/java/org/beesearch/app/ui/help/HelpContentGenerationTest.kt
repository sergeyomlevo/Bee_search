package org.beesearch.app.ui.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the in-app help and its canonical Markdown source in step.
 *
 * The application never parses Markdown: it uses the generated data in `HelpContent.kt`. This test
 * is the only place that reads the Markdown, so the two cannot drift apart unnoticed.
 *
 * Regenerate after editing `docs/ui/help/help-v2.md`:
 * `$env:HELP_REGENERATE="1"; .\gradlew.bat :app:testDebugUnitTest --tests "*HelpContentGenerationTest*"`
 */
class HelpContentGenerationTest {
    private val regenerate = System.getenv("HELP_REGENERATE").let { it == "1" || it.equals("true", true) }

    @Test
    fun generatedHelpMatchesCanonicalMarkdown() {
        val parsed = parseHelp(HelpSource.readSource())

        if (regenerate) {
            HelpSource.generatedFile().writeText(renderHelpContent(parsed), Charsets.UTF_8)
            println("regenerated ${HelpSource.generatedFile()}")
            return
        }

        assertEquals(
            "the intro block drifted from docs/ui/help/help-v2.md; regenerate HelpContent.kt",
            parsed.intro,
            helpIntro,
        )
        assertEquals(
            driftMessage(parsed.sections, detailedHelpSections),
            parsed.sections,
            detailedHelpSections,
        )
    }

    private fun driftMessage(expected: List<HelpSection>, actual: List<HelpSection>): String {
        if (expected.size != actual.size) {
            return "help has ${actual.size} sections but docs/ui/help/help-v2.md declares ${expected.size}; " +
                "regenerate HelpContent.kt from the Markdown"
        }
        val index = expected.indices.firstOrNull { expected[it] != actual[it] } ?: 0
        return "section ${index + 1} ('${expected[index].title}') drifted from docs/ui/help/help-v2.md; " +
            "regenerate HelpContent.kt from the Markdown"
    }

    @Test
    fun canonicalSourceIsParsedByTheDocumentedRules() {
        val parsed = parseHelp(
            """
            # Служебная шапка

            Этот текст в справку не попадает.

            ## Краткий старт

            Вводный абзац.

            1. Первый шаг.
            2. Второй шаг.

            > Вводная подсказка.

            ## Первый раздел

            Абзац раздела,
            продолженный на следующей строке.

            - Первый пункт.
            - Второй пункт.

            > Первая подсказка.
            > Вторая подсказка.

            ![Подпись изображения](help_example)

            ```text
            строка один
            строка два
            ```

            ### Подраздел

            Текст подраздела.
            """.trimIndent(),
        )

        assertEquals("Краткий старт", parsed.intro.title)
        assertEquals(
            listOf(
                HelpBlock.Paragraph("Вводный абзац."),
                HelpBlock.Steps(listOf("Первый шаг.", "Второй шаг.")),
                HelpBlock.Note("Вводная подсказка."),
            ),
            parsed.intro.blocks,
        )

        assertEquals(2, parsed.sections.size)
        val section = parsed.sections.first()
        assertEquals("Первый раздел", section.title)
        assertEquals(1, section.level)
        assertEquals(
            listOf(
                HelpBlock.Paragraph("Абзац раздела, продолженный на следующей строке."),
                HelpBlock.Bullets(listOf("Первый пункт.", "Второй пункт.")),
                HelpBlock.Note("Первая подсказка. Вторая подсказка."),
                HelpBlock.Visual(HelpVisual("help_example", "Подпись изображения")),
                HelpBlock.Preformatted("строка один\nстрока два"),
            ),
            section.blocks,
        )
        assertEquals("Подраздел", parsed.sections.last().title)
        assertEquals(2, parsed.sections.last().level)
    }

    @Test
    fun subsectionsKeepTheirLevelAndFollowTheirSection() {
        val parsed = parseHelp(HelpSource.readSource())
        val subsection = parsed.sections.single { it.title == "Создание участков ареала" }

        assertEquals(2, subsection.level)
        assertTrue(
            parsed.sections.indexOf(subsection) > parsed.sections.indexOfFirst { it.title == "Ареал и офлайн-карта" },
        )
        assertTrue(parsed.sections.all { it.level in 1..2 })
    }

    @Test
    fun everyDeclaredImageSlotHasADrawableNameAndADescription() {
        val parsed = parseHelp(HelpSource.readSource())
        val visuals = (listOf(parsed.intro) + parsed.sections).flatMap { section ->
            section.blocks.filterIsInstance<HelpBlock.Visual>().map { it.visual }
        }

        assertTrue("the canonical help declares no image slots", visuals.isNotEmpty())
        visuals.forEach { visual ->
            assertTrue(visual.resourceName, visual.resourceName.startsWith("help_"))
            assertTrue(visual.contentDescription, visual.contentDescription.length > 10)
        }
    }

    @Test
    fun exchangePathPlaceholderIsResolvedForTheRunningBuild() {
        val sections = helpSections("Download/BeeSearch/Beta/Exchange")
        val exchange = sections.single { it.title == "Где находятся файлы Bee Search" }
        val exchangeText = exchange.blocks.filterIsInstance<HelpBlock.Paragraph>().map { it.text }

        assertTrue(exchangeText.contains("Download/BeeSearch/Beta/Exchange"))
        assertTrue(
            "the placeholder must not reach the user",
            (listOf(helpIntro) + sections).none { section ->
                section.blocks.any { block ->
                    when (block) {
                        is HelpBlock.Paragraph -> block.text.contains(EXCHANGE_PATH_TOKEN)
                        is HelpBlock.Steps -> block.items.any { it.contains(EXCHANGE_PATH_TOKEN) }
                        is HelpBlock.Bullets -> block.items.any { it.contains(EXCHANGE_PATH_TOKEN) }
                        is HelpBlock.Note -> block.text.contains(EXCHANGE_PATH_TOKEN)
                        is HelpBlock.Preformatted -> block.text.contains(EXCHANGE_PATH_TOKEN)
                        is HelpBlock.Visual -> false
                    }
                }
            },
        )
    }
}
