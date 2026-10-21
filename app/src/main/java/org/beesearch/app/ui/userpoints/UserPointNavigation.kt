package org.beesearch.app.ui.userpoints

import java.util.UUID

enum class UserPointCardOrigin { MAP, LIST }

/** A single editor session; a coordinate revision is consumed by that editor. */
data class UserPointEditorTarget(
    val requestId: UUID,
    val territoryId: UUID,
    val pointId: UUID? = null,
    val latitude: Double,
    val longitude: Double,
    val coordinateRevision: UUID = requestId,
    val origin: UserPointCardOrigin = UserPointCardOrigin.MAP,
)
