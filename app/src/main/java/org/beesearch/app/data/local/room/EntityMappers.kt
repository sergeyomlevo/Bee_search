package org.beesearch.app.data.local.room

import org.beesearch.app.domain.model.Bee
import org.beesearch.app.domain.model.FlightCycle
import org.beesearch.app.domain.model.ObservationPoint
import org.beesearch.app.domain.model.Observer
import org.beesearch.app.domain.model.Territory
import org.beesearch.app.domain.model.ObservationPointAttachment
import org.beesearch.app.domain.model.ObservationPointWeather

internal fun TerritoryEntity.toDomain(): Territory = Territory(
    id = id,
    code = code,
    name = name,
    region = region,
    district = district,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ObserverEntity.toDomain(): Observer = Observer(
    id = id,
    code = code,
    lastName = lastName,
    firstName = firstName,
    middleName = middleName,
    contact = contact,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ObservationPointEntity.toDomain(): ObservationPoint = ObservationPoint(
    id = id,
    territoryId = territoryId,
    observerId = observerId,
    observationYear = observationYear,
    pointNumber = pointNumber,
    beePresenceResult = beePresenceResult,
    code = code,
    latitude = latitude,
    longitude = longitude,
    gpsLatitude = gpsLatitude,
    gpsLongitude = gpsLongitude,
    gpsAccuracyM = gpsAccuracyM,
    createdAt = createdAt,
    initialGroupReleaseAt = initialGroupReleaseAt,
    completedAt = completedAt,
    description = description,
)

internal fun ObservationPointAttachmentEntity.toDomain() = ObservationPointAttachment(
    id, observationPointId, type, relativePath, originalFileName, mimeType, byteSize, sha256, createdAt,
)

internal fun ObservationPointWeatherEntity.toDomain() = ObservationPointWeather(
    observationPointId, status, temperatureC, windSpeedMps, windDirectionDeg, sampleAt, fetchedAt, source,
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
    azimuthCaptureConsumed = azimuthCaptureConsumed,
    isInitialGroupLaunch = isInitialGroupLaunch,
    isFirstDepartureCancellationEligible = isFirstDepartureCancellationEligible,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
