package org.beesearch.app.data.local.room

import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Territory

internal fun TerritoryEntity.toDomain(): Territory = Territory(
    id = id,
    code = code,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ObservationPointEntity.toDomain(): ObservationPoint = ObservationPoint(
    id = id,
    territoryId = territoryId,
    observerCode = observerCode,
    code = code,
    latitude = latitude,
    longitude = longitude,
    gpsLatitude = gpsLatitude,
    gpsLongitude = gpsLongitude,
    gpsAccuracyM = gpsAccuracyM,
    createdAt = createdAt,
    completedAt = completedAt,
)

internal fun BeeEntity.toDomain(): Bee = Bee(
    id = id,
    observationPointId = observationPointId,
    markColor = markColor,
    markPosition = markPosition,
    createdAt = createdAt,
)

internal fun FlightCycleEntity.toDomain(): FlightCycle = FlightCycle(
    id = id,
    beeId = beeId,
    sequenceNumber = sequenceNumber,
    departureTime = departureTime,
    returnTime = returnTime,
    azimuthDeg = azimuthDeg,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
