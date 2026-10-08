package org.beesearch.app.data.objectexport

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.beesearch.app.domain.model.HollowProperties
import org.beesearch.app.domain.model.LogHiveProperties
import org.beesearch.app.domain.model.PhysicalObjectMedia
import org.beesearch.app.domain.model.PhysicalObjectType

/**
 * The portable package of exactly one Physical Object: profile `SINGLE_PHYSICAL_OBJECT` version 1.
 *
 * This is a separate profile and not an ObservationPoint package variant. A Physical Object is a
 * durable entity, so its package contains what belongs to the object itself - its identity, its
 * concrete subtype properties and its app-owned media - and nothing from the observation graph.
 * Bees, FlightCycles, ObservationPoints, their weather and their attachments are other entities with
 * their own lifecycle and are never pulled into an object package, not even when a Bee currently
 * points at this object.
 *
 * Territory and creator Observer travel as a minimal read-only labelling snapshot, not as owned data
 * and not as exported entities: the human designation `Дупло N` is only unique inside one Territory,
 * so the package has to say which Territory that is, and `createdAt`/creator are provenance. The
 * snapshot is limited to the fields declared below; it carries no other object, no observation point
 * and no setting of that Territory.
 *
 * The full field-level contract is documented in `docs/physical-object-export-v1.md`.
 */
internal object PhysicalObjectExportContract {
    const val PROFILE = "SINGLE_PHYSICAL_OBJECT"
    const val FORMAT_VERSION = 2
    const val LEGACY_FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val OBJECT_ENTRY = "object.json"
    const val MEDIA_PREFIX = "media/"

    /** Caps mirror the ObservationPoint profile; one media item may not exceed the media store limit. */
    const val MAX_ENTRIES = 64
    const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    fun mediaEntry(id: UUID): String = "$MEDIA_PREFIX$id"
}

/**
 * The persisted subtype properties of one concrete object type, kept concrete.
 *
 * `null` as a whole means the stored subtype row carries no properties, which is a real persisted
 * state for foundation rows created before the subtype tables existed. The package exports that state
 * as it is instead of inventing values.
 */
internal sealed interface PhysicalObjectExportProperties {
    data class Hollow(val value: HollowProperties) : PhysicalObjectExportProperties
    data class LogHive(val value: LogHiveProperties) : PhysicalObjectExportProperties
}

/**
 * The minimal read-only Territory labelling snapshot of a package.
 *
 * It is deliberately not the `Territory` entity: the package carries only what identifies the object
 * to a human outside the source database (the designation `Дупло N` is unique only inside one
 * Territory), so no region, district, timestamp or setting of that Territory is part of the format.
 * A dedicated type keeps that boundary visible and keeps the reader from inventing values it does not
 * have.
 */
internal data class TerritoryExportSnapshot(
    val id: UUID,
    val code: String,
    val name: String,
)

/** The minimal read-only creator Observer snapshot: id, code and the human name that exists. */
internal data class ObserverExportSnapshot(
    val id: UUID,
    val code: String,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
)

/**
 * One object-owned snapshot plus its read-only labelling context.
 *
 * [territoryId] is the object's own stored reference; [territory] is the minimal context snapshot of
 * that same Territory and [observer] the snapshot of the creator. The validator requires them to
 * agree, so a package cannot claim an object of one Territory while labelling it with another.
 */
internal data class PhysicalObjectExportGraph(
    val id: UUID,
    val type: PhysicalObjectType,
    val territoryId: UUID,
    val sequenceNumber: Int,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Instant,
    val creatorObserverId: UUID?,
    val name: String?,
    val properties: PhysicalObjectExportProperties?,
    val media: List<PhysicalObjectMedia>,
    val territory: TerritoryExportSnapshot,
    val observer: ObserverExportSnapshot?,
    /** I2 canonical date; V1 payloads deliberately cannot carry non-null values. */
    val fixationDate: LocalDate? = null,
)

internal data class DecodedPhysicalObjectExport(
    val graph: PhysicalObjectExportGraph,
    val mediaBytes: Map<UUID, ByteArray>,
)

internal sealed class PhysicalObjectExportException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** The package itself is malformed, unsafe or does not match this profile. */
internal class InvalidPhysicalObjectExport(message: String, cause: Throwable? = null) :
    PhysicalObjectExportException(message, cause)

internal class LegacyPhysicalObjectExportNotRepresentable(message: String) :
    PhysicalObjectExportException(message)

/** The stored data and its bytes disagree; the package must not look successful. */
internal class PhysicalObjectExportIntegrityError(message: String) :
    PhysicalObjectExportException(message)

/** The object or one of its app-owned files is not available, so no package can be produced. */
internal class PhysicalObjectExportSourceMissing(message: String) :
    PhysicalObjectExportException(message)

/**
 * The object type has no user-facing lifecycle yet, so exporting it would produce a package no screen
 * can explain. V1 fails closed instead of writing a half-shaped Apiary package.
 */
internal class UnsupportedPhysicalObjectExportType(message: String) :
    PhysicalObjectExportException(message)
