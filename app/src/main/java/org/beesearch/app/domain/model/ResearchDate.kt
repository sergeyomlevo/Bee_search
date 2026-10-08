package org.beesearch.app.domain.model

import java.time.LocalDate

/** Canonical wire spelling shared by versioned research-date representations. */
fun parseResearchDate(value: String): LocalDate {
    require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "invalid research date" }
    return try { LocalDate.parse(value) } catch (error: java.time.format.DateTimeParseException) {
        throw IllegalArgumentException("invalid research date", error)
    }
}
