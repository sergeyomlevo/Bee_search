package org.beesearch.app.domain.model

class InvalidObserverCodeException : IllegalArgumentException(
    "Observer code must not be empty after trimming.",
)

class ObserverCodeRequiredException : IllegalStateException(
    "Observer code must be saved before an observation point is created.",
)

class EntityNotFoundException(entity: String) : IllegalStateException("$entity does not exist.")

class DuplicateTerritoryCodeException : IllegalStateException(
    "A Territory with this code already exists on this device.",
)

class ObservationPointAlreadyActiveException : IllegalStateException(
    "Only one observation point may be active at a time.",
)

class ObservationPointNotActiveException : IllegalStateException(
    "The observation point is not active.",
)

class InitialReleaseAlreadyStartedException : IllegalStateException(
    "The initial group release has already started.",
)

class NoPreparedBeesException : IllegalStateException(
    "At least one prepared bee is required for the initial group release.",
)

class BeeHasFlightHistoryException : IllegalStateException(
    "A bee with flight history cannot be removed as a prepared bee.",
)

class DuplicateBeeMarkException : IllegalStateException(
    "A bee with the same mark already exists in this observation point.",
)

class BeePresenceResultRequiredException : IllegalStateException(
    "Bee presence must be established before the observation point is completed.",
)

class NoBeesFoundAlreadyRecordedException : IllegalStateException(
    "A bee cannot be added after no bees were explicitly recorded.",
)

class BeesAlreadyFoundException : IllegalStateException(
    "No bees cannot be recorded after bees were found.",
)

class OpenFlightCycleExistsException : IllegalStateException(
    "A bee cannot have more than one open flight cycle.",
)

class OpenFlightCycleNotFoundException : IllegalStateException(
    "The bee has no open flight cycle.",
)

class InitialFlightCycleRequiredException : IllegalStateException(
    "The initial group release must happen before a later flight can start.",
)

class InvalidEventTimeException : IllegalStateException(
    "An event timestamp cannot be earlier than the event it follows.",
)

class InvalidAzimuthException : IllegalArgumentException(
    "Azimuth must be in the range 0 (inclusive) to 360 (exclusive).",
)
