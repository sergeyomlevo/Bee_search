package org.beesearch.app.data.objectexport

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.beesearch.app.data.pointexport.sanitizeFileNamePart
import org.beesearch.app.domain.model.PhysicalObjectType

/**
 * The suggested file name of one object package: `<territoryCode>--hollow-<N>--<date>--<shortId>.zip`
 * (`log-hive` for a Колода).
 *
 * The name is convenience metadata for the human choosing the file; the manifest and `object.json`
 * are the source of truth for what the package contains. It follows the existing exchange-file
 * convention and reuses the existing sanitizer, so a Territory code containing path characters cannot
 * escape the chosen folder.
 */
internal fun physicalObjectExportFileName(
    type: PhysicalObjectType,
    sequenceNumber: Int,
    territoryCode: String,
    createdAt: Instant,
    objectId: UUID,
): String {
    val territory = sanitizeFileNamePart(territoryCode).ifBlank { "territory" }
    val date = DateTimeFormatter.ISO_LOCAL_DATE.format(createdAt.atZone(ZoneOffset.UTC))
    val shortId = objectId.toString().replace("-", "").take(8)
    return "$territory--${type.fileNameToken()}-$sequenceNumber--$date--$shortId.zip"
}

/** Suggested name of one per-type Territory collection package. */
internal fun physicalObjectCollectionExportFileName(
    type: PhysicalObjectType,
    territoryCode: String,
    exportedAt: Instant,
): String {
    PhysicalObjectExportValidator.validateSupportedType(type)
    val territory = sanitizeFileNamePart(territoryCode).ifBlank { "territory" }
    val date = DateTimeFormatter.ISO_LOCAL_DATE.format(exportedAt.atZone(ZoneOffset.UTC))
    val collection = when (type) {
        PhysicalObjectType.HOLLOW -> "hollows"
        PhysicalObjectType.LOG_HIVE -> "log-hives"
        PhysicalObjectType.APIARY -> error("unreachable unsupported type")
    }
    return "$territory--$collection--$date.zip"
}

/** The type token of the file name. Apiary never reaches this point: the export refuses it earlier. */
private fun PhysicalObjectType.fileNameToken(): String = when (this) {
    PhysicalObjectType.HOLLOW -> "hollow"
    PhysicalObjectType.LOG_HIVE -> "log-hive"
    PhysicalObjectType.APIARY -> "apiary"
}
