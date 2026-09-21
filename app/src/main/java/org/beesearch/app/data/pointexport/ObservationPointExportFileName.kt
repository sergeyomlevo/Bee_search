package org.beesearch.app.data.pointexport

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.beesearch.app.domain.model.ObservationPointDetail

internal fun observationPointExportFileName(detail: ObservationPointDetail): String {
    val territory = sanitizeFileNamePart(detail.territory.code).ifBlank { "territory" }
    val date = DateTimeFormatter.ISO_LOCAL_DATE.format(detail.point.createdAt.atZone(ZoneOffset.UTC))
    val shortId = detail.point.id.toString().replace("-", "").take(8)
    return "$territory--point-${detail.point.pointNumber}--$date--$shortId.zip"
}

internal fun sanitizeFileNamePart(value: String): String = value
    .trim()
    .map { character ->
        when {
            character.isLetterOrDigit() -> character
            character == '.' || character == '_' || character == '-' -> character
            else -> '-'
        }
    }
    .joinToString("")
    .replace(Regex("-+"), "-")
    .trim('-', '.')
