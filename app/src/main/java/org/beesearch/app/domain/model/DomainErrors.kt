package org.beesearch.app.domain.model

class RequiredFieldException(field: String) : IllegalArgumentException("$field must not be empty after trimming.")

class ObserverRequiredException : IllegalStateException("A current Observer is required before an observation point is created.")

class TerritoryRequiredException : IllegalStateException("A current Territory is required before an observation point is created.")

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

class ObservationPointNotCompletedException : IllegalStateException(
    "Only a completed observation point can be deleted.",
)

class DuplicateBeeMarkException : IllegalStateException(
    "A bee with the same mark already exists in this observation point.",
)

class BeeLimitReachedException : IllegalStateException(
    "An observation point may contain at most 10 bees.",
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

class InvalidEventTimeException : IllegalStateException(
    "An event timestamp cannot be earlier than the event it follows.",
)

class InvalidAzimuthException : IllegalArgumentException(
    "Azimuth must be in the range 0 (inclusive) to 360 (exclusive).",
)

class DuplicateObserverCodeException : IllegalStateException(
    "An Observer with this code already exists on this device.",
)
class ObserverInUseException : IllegalStateException("Observer is used by observation points")
class TerritoryInUseException : IllegalStateException("Territory is used by observation points")

class AzimuthCaptureRequiresOpenFlightCycleException : IllegalStateException(
    "Field azimuth capture requires an open flight cycle.",
)

class AzimuthCaptureAlreadyConsumedException : IllegalStateException(
    "The field azimuth capture opportunity has already been consumed.",
)

class NoReversibleBeeActionException : IllegalStateException(
    "The bee has no last reversible action in the active observation workflow.",
)
