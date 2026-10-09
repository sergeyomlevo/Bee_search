package org.beesearch.app.domain.model

import java.time.LocalDate

/** Inclusive, zone-free research-date bounds. A null interval at query boundaries means all time. */
data class ResearchDateInterval(val fromDate: LocalDate, val toDate: LocalDate) {
    init {
        parseResearchDate(fromDate.toString())
        parseResearchDate(toDate.toString())
        require(fromDate <= toDate) { "research date interval is reversed" }
    }
}

/** Canonical wire spelling shared by versioned research-date representations. */
fun parseResearchDate(value: String): LocalDate {
    require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "invalid research date" }
    return try { LocalDate.parse(value) } catch (error: java.time.format.DateTimeParseException) {
        throw IllegalArgumentException("invalid research date", error)
    }
}
