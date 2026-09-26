package org.beesearch.app.ui.help

import java.io.File

/**
 * Parses the canonical help source and renders the generated Kotlin file from it.
 *
 * This code lives in the unit-test source set on purpose: the application never parses Markdown, it
 * only uses the generated data in `HelpContent.kt`. The supported markup is the small subset
 * documented in `docs/ui/help/README.md`, so the parser stays short and predictable.
 */
internal data class ParsedHelp(val intro: HelpSection, val sections: List<HelpSection>)

internal object HelpSource {
    private const val SOURCE_PATH = "docs/ui/help/help-v2.md"
    private const val GENERATED_PATH = "app/src/main/java/org/beesearch/app/ui/help/HelpContent.kt"

    /** Repository root, found by walking up from the working directory of the test JVM. */
    fun repositoryRoot(): File {
        var current: File? = File("").absoluteFile
        repeat(8) {
            val candidate = current ?: return@repeat
            if (File(candidate, SOURCE_PATH).isFile) return candidate
            current = candidate.parentFile
        }
        error("cannot find $SOURCE_PATH above ${File("").absolutePath}")
    }

    fun sourceFile(): File = File(repositoryRoot(), SOURCE_PATH)

    fun generatedFile(): File = File(repositoryRoot(), GENERATED_PATH)

    fun readSource(): String = sourceFile().readText(Charsets.UTF_8)
}

private const val INTRO_TITLE = "Краткий старт"

private enum class Pending { PARAGRAPH, STEPS, BULLETS, NOTES }

/**
 * Parses the canonical help text.
 *
 * Everything before the first `## ` heading is the document preamble and is not part of the help.
 * The first section becomes the intro block; the remaining sections follow in source order and keep
 * their level (`## ` = 1, `### ` = 2).
 */
internal fun parseHelp(source: String): ParsedHelp {
    class Draft(val title: String, val level: Int) {
        val blocks = mutableListOf<HelpBlock>()
    }

    val drafts = mutableListOf<Draft>()
    var current: Draft? = null
    var pending: Pending? = null
    var paragraph = StringBuilder()
    val items = mutableListOf<String>()
    var fence = StringBuilder()
    var inFence = false

    fun flushPending() {
        val draft = current ?: return
        when (pending) {
            Pending.PARAGRAPH -> if (paragraph.isNotEmpty()) draft.blocks.add(HelpBlock.Paragraph(paragraph.toString().trim()))
            Pending.STEPS -> if (items.isNotEmpty()) draft.blocks.add(HelpBlock.Steps(items.toList()))
            Pending.BULLETS -> if (items.isNotEmpty()) draft.blocks.add(HelpBlock.Bullets(items.toList()))
            Pending.NOTES -> if (items.isNotEmpty()) draft.blocks.add(HelpBlock.Note(items.joinToString(" ")))
            null -> Unit
        }
        paragraph = StringBuilder()
        items.clear()
        pending = null
    }

    fun accumulate(kind: Pending, line: String) {
        if (pending != null && pending != kind) flushPending()
        pending = kind
        if (kind == Pending.PARAGRAPH) {
            if (paragraph.isNotEmpty()) paragraph.append(' ')
            paragraph.append(line.trim())
        } else {
            items.add(line)
        }
    }

    source.lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()
        when {
            line.startsWith("```") -> {
                if (inFence) {
                    flushPending()
                    current?.blocks?.add(HelpBlock.Preformatted(fence.toString().trimEnd()))
                    fence = StringBuilder()
                    inFence = false
                } else {
                    flushPending()
                    inFence = true
                }
            }
            inFence -> fence.appendLine(line)
            line.startsWith("### ") -> {
                flushPending()
                current = Draft(line.removePrefix("### ").trim(), level = 2).also(drafts::add)
            }
            line.startsWith("## ") -> {
                flushPending()
                current = Draft(line.removePrefix("## ").trim(), level = 1).also(drafts::add)
            }
            line.startsWith("# ") || line == "---" -> flushPending()
            current == null -> Unit
            line.isBlank() -> flushPending()
            line.startsWith("> ") -> {
                val note = line.removePrefix("> ").trim()
                if (pending == Pending.NOTES) items.add(note) else accumulate(Pending.NOTES, note)
            }
            line.startsWith("- ") -> accumulate(Pending.BULLETS, line.removePrefix("- ").trim())
            ORDERED_ITEM.matchEntire(line) != null ->
                accumulate(Pending.STEPS, ORDERED_ITEM.matchEntire(line)!!.groupValues[1].trim())
            line.matches(VISUAL) -> {
                flushPending()
                val match = VISUAL.matchEntire(line) ?: error("unreachable")
                val (caption, resourceName) = match.destructured
                require(caption.isNotBlank()) { "an image of '${current?.title}' needs a description" }
                require(resourceName.matches(Regex("[a-z][a-z0-9_]*"))) {
                    "image resource name '$resourceName' must be a lowercase drawable name"
                }
                current?.blocks?.add(HelpBlock.Visual(HelpVisual(resourceName, caption.trim())))
            }
            else -> accumulate(Pending.PARAGRAPH, line)
        }
    }
    flushPending()

    require(drafts.size >= 2) { "the canonical help must contain the intro section and at least one section" }
    val intro = drafts.first()
    require(intro.title == INTRO_TITLE) { "the first section must be '$INTRO_TITLE', got '${intro.title}'" }
    val sections = drafts.drop(1).map { HelpSection(it.title, it.level, it.blocks.toList()) }
    return ParsedHelp(HelpSection(intro.title, intro.level, intro.blocks.toList()), sections)
}

private val ORDERED_ITEM = Regex("""^\d+\.\s+(.*)$""")
private val VISUAL = Regex("""!\[(.*)]\(([^)]+)\)""")

/** Renders the generated Kotlin file from the parsed help. Deterministic: same input, same text. */
internal fun renderHelpContent(parsed: ParsedHelp): String = buildString {
    appendLine("// GENERATED FILE — DO NOT EDIT.")
    appendLine("// Source: docs/ui/help/help-v2.md")
    appendLine(
        "// Regenerate: " + '$' + "env:HELP_REGENERATE=\"1\"; .\\gradlew.bat :app:testDebugUnitTest " +
            "--tests \"*HelpContentGenerationTest*\"",
    )
    appendLine("// The canonical help text lives in the Markdown file; this file is derived from it and is")
    appendLine("// verified by HelpContentGenerationTest, which fails when the two diverge.")
    appendLine()
    appendLine("package org.beesearch.app.ui.help")
    appendLine()
    appendLine("/**")
    appendLine(" * One optional image of a help section.")
    appendLine(" *")
    appendLine(" * [resourceName] is the drawable name in `res/drawable-nodpi/help/`; the image is shown only when")
    appendLine(" * such a resource actually exists, so a planned slot never leaves an empty gap.")
    appendLine(" * [contentDescription] describes the image for accessibility.")
    appendLine(" */")
    appendLine("internal data class HelpVisual(val resourceName: String, val contentDescription: String)")
    appendLine()
    appendLine("/** One block of help text, in the order the canonical source declares it. */")
    appendLine("internal sealed interface HelpBlock {")
    appendLine("    data class Paragraph(val text: String) : HelpBlock")
    appendLine("    data class Steps(val items: List<String>) : HelpBlock")
    appendLine("    data class Bullets(val items: List<String>) : HelpBlock")
    appendLine("    data class Note(val text: String) : HelpBlock")
    appendLine("    data class Preformatted(val text: String) : HelpBlock")
    appendLine("    data class Visual(val visual: HelpVisual) : HelpBlock")
    appendLine("}")
    appendLine()
    appendLine("/** One help section: a topic written for the workflow step the user is performing. */")
    appendLine("internal data class HelpSection(val title: String, val level: Int, val blocks: List<HelpBlock>)")
    appendLine()
    appendLine("/** Replaced by [helpSections] with the exchange folder of the running build. */")
    appendLine("internal const val EXCHANGE_PATH_TOKEN = \"%EXCHANGE_PATH%\"")
    appendLine()
    appendLine("internal val helpIntro: HelpSection = ${section(parsed.intro)}")
    appendLine()
    appendLine("internal val detailedHelpSections: List<HelpSection> = listOf(")
    parsed.sections.forEach { appendLine("    ${section(it)},") }
    appendLine(")")
    appendLine()
    appendLine("/**")
    appendLine(" * The help of the running build: the section that names the exchange folder gets the path")
    appendLine(" * this variant actually uses.")
    appendLine(" */")
    appendLine("internal fun helpSections(exchangePath: String): List<HelpSection> = detailedHelpSections.map { section ->")
    appendLine("    section.copy(blocks = section.blocks.map { it.withExchangePath(exchangePath) })")
    appendLine("}")
    appendLine()
    appendLine("private fun HelpBlock.withExchangePath(exchangePath: String): HelpBlock = when (this) {")
    appendLine("    is HelpBlock.Paragraph -> copy(text = text.replace(EXCHANGE_PATH_TOKEN, exchangePath))")
    appendLine("    is HelpBlock.Steps -> copy(items = items.map { it.replace(EXCHANGE_PATH_TOKEN, exchangePath) })")
    appendLine("    is HelpBlock.Bullets -> copy(items = items.map { it.replace(EXCHANGE_PATH_TOKEN, exchangePath) })")
    appendLine("    is HelpBlock.Note -> copy(text = text.replace(EXCHANGE_PATH_TOKEN, exchangePath))")
    appendLine("    is HelpBlock.Preformatted -> copy(text = text.replace(EXCHANGE_PATH_TOKEN, exchangePath))")
    appendLine("    is HelpBlock.Visual -> this")
    appendLine("}")
}

private fun section(value: HelpSection): String = buildString {
    append("HelpSection(")
    append("title = ${literal(value.title)}, ")
    append("level = ${value.level}, ")
    append("blocks = ${blockList(value.blocks)}")
    append(")")
}

private fun blockList(values: List<HelpBlock>): String =
    if (values.isEmpty()) {
        "emptyList()"
    } else {
        values.joinToString(separator = ",\n", prefix = "listOf(\n", postfix = ",\n    )") { "        ${block(it)}" }
    }

private fun block(value: HelpBlock): String = when (value) {
    is HelpBlock.Paragraph -> "HelpBlock.Paragraph(${literal(value.text)})"
    is HelpBlock.Steps -> "HelpBlock.Steps(${itemList(value.items)})"
    is HelpBlock.Bullets -> "HelpBlock.Bullets(${itemList(value.items)})"
    is HelpBlock.Note -> "HelpBlock.Note(${literal(value.text)})"
    is HelpBlock.Preformatted -> "HelpBlock.Preformatted(${literal(value.text)})"
    is HelpBlock.Visual ->
        "HelpBlock.Visual(HelpVisual(${literal(value.visual.resourceName)}, ${literal(value.visual.contentDescription)}))"
}

private fun itemList(values: List<String>): String =
    if (values.isEmpty()) "emptyList()" else values.joinToString(", ") { literal(it) }.let { "listOf($it)" }

/** Kotlin string literal with the characters that would otherwise change the meaning escaped. */
private fun literal(value: String): String {
    val escaped = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                else -> append(character)
            }
        }
    }
    return "\"$escaped\""
}
