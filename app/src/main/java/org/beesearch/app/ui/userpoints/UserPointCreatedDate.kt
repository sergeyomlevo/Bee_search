package org.beesearch.app.ui.userpoints

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CreatedDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)

/** Local calendar date of the persisted creation moment; time of day is deliberately not shown. */
internal fun userPointCreatedDateText(
    createdAt: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = "Создана: ${CreatedDateFormat.format(createdAt.atZone(zoneId).toLocalDate())}"
