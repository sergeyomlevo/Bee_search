package org.beesearch.app.data.local.room

import org.beesearch.app.domain.model.PhysicalObjectReferenceKind

/**
 * The tables whose rows are owned by one Physical Object.
 *
 * The distinction is a lifecycle fact, not a name convention: a row of these tables is written
 * together with the object and removed by the explicit deletion inside its transaction. A table that
 * references `physical_objects` but is *not* listed here holds working or historical data that
 * outlives the object, so it blocks the deletion instead of being removed with it.
 */
internal val ownedPhysicalObjectTables: Set<String> = setOf(
    "hollows",
    "log_hives",
    "apiaries",
    "physical_object_media",
)

/**
 * The table that stores each blocking reference kind.
 *
 * This mapping is the single place where a user-facing [PhysicalObjectReferenceKind] becomes a
 * storage fact. It exists so the blocker model and the schema cannot drift apart: the exhaustive
 * `when` refuses to compile when a kind is added without naming its table, and the schema test
 * `PhysicalObjectReferenceRestrictTest` fails when a table starts referencing `physical_objects`
 * without a kind that reports it. Without that pair a new reference would surface to the user as an
 * unexplained foreign-key failure.
 */
internal fun PhysicalObjectReferenceKind.blockingTable(): String = when (this) {
    PhysicalObjectReferenceKind.BEE -> "bees"
}
