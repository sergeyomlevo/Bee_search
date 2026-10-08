package org.beesearch.app.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reconstructs only legacy representations that never carried an explicit research date.
 * Do not use as a fallback for future formats carrying corrected canonical dates.
 */
fun legacyObservationDate(createdAt: Instant, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    createdAt.atZone(zoneId).toLocalDate()
