package org.beesearch.app.ui.physicalobjects

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PHYSICAL_OBJECT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
private val PHYSICAL_OBJECT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/**
 * The user-facing fixation moment keeps the exact timestamp when it is available, preserves a
 * known calendar date when only that precision was saved, and never derives a research moment from
 * the technical object creation timestamp.
 */
internal fun physicalObjectFixationText(
    fixationAt: Instant?,
    fixationDate: LocalDate?,
): String = when {
    fixationAt != null -> fixationAt.atZone(ZoneId.systemDefault()).format(PHYSICAL_OBJECT_DATE_TIME_FORMATTER)
    fixationDate != null -> "${fixationDate.format(PHYSICAL_OBJECT_DATE_FORMATTER)}, время фиксации неизвестно"
    else -> "момент фиксации неизвестен"
}
