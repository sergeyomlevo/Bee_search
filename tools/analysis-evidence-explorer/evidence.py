"""Evidence-critical validation and deterministic result construction."""

from __future__ import annotations

import math
from pathlib import Path
from typing import Any

from backup_reader import (
    BackupError,
    boolean,
    canonical_uuid,
    integer,
    integer32,
    read_archive,
    require_fields,
)

EXPLORER_VERSION = "0.2.0"
RESULT_SCHEMA_VERSION = 1
RULE_SET_VERSION = 2
# D058 was retired from the Explorer analytical rule set.  The raw launch
# provenance remains in every cycle for compatibility, but no cycle is
# excluded solely because it is sequence 1 or shorter than one minute.
APPLIED_RULES: tuple[dict[str, Any], ...] = ()


def _number(value: Any, label: str, minimum: float | None = None,
            maximum: float | None = None, maximum_inclusive: bool = True) -> int | float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise BackupError(f"{label} must be a finite number")
    if minimum is not None and value < minimum:
        raise BackupError(f"{label} is out of range")
    if maximum is not None and (value > maximum if maximum_inclusive else value >= maximum):
        raise BackupError(f"{label} is out of range")
    return value


def _nullable_string(value: Any, label: str) -> str | None:
    if value is not None and not isinstance(value, str):
        raise BackupError(f"{label} must be a string or null")
    return value


def _validate_coverage(encoded: Any) -> None:
    if not isinstance(encoded, str):
        raise BackupError("map coverage must be a string")
    parts = encoded.split("|")
    if not parts or parts[0] != "v1":
        raise BackupError("invalid map coverage")
    for item in parts[1:]:
        try:
            values = [float(value) for value in item.split(",")]
        except ValueError as error:
            raise BackupError("invalid map coverage") from error
        if len(values) != 4 or not all(math.isfinite(value) for value in values):
            raise BackupError("invalid map coverage")
        north, west, south, east = values
        if not (-90 <= north <= 90 and -90 <= south <= 90 and
                -180 <= west <= 180 and -180 <= east <= 180 and north >= south):
            raise BackupError("invalid map coverage")


def _validate_identities(groups: tuple[tuple[str, list[dict[str, Any]]], ...]) -> None:
    all_ids: set[str] = set()
    for label, records in groups:
        for record in records:
            identifier = canonical_uuid(record.get("id"), f"{label}.id")
            if identifier in all_ids:
                raise BackupError(f"duplicate identity: {identifier}")
            all_ids.add(identifier)


def build_evidence(path: Path) -> dict[str, Any]:
    parsed = read_archive(path)
    rows = {name: list(collection.rows) for name, collection in parsed.collections.items()}
    territories, observers, points, bees, cycles = (
        rows[name] for name in ("territories", "observers", "observation-points", "bees", "flight-cycles")
    )
    portable_settings = rows["portable-settings"]
    if len(portable_settings) != 1:
        raise BackupError("portable-settings must contain exactly one record")
    settings = require_fields(
        portable_settings[0], {"currentTerritoryId", "currentObserverId"}, "portable-settings"
    )
    _validate_identities((("territory", territories), ("observer", observers),
                          ("observation point", points), ("bee", bees), ("flight cycle", cycles)))

    territory_fields = {"id", "code", "name", "region", "district", "createdAt", "updatedAt"}
    for territory in territories:
        require_fields(territory, territory_fields, "territory")
        for field in ("code", "name", "region", "district"):
            if not isinstance(territory[field], str) or not territory[field].strip():
                raise BackupError(f"blank territory field: {field}")
        created = integer(territory["createdAt"], "territory.createdAt")
        updated = integer(territory["updatedAt"], "territory.updatedAt")
        if updated < created:
            raise BackupError("invalid territory timestamp order")
    if len({territory["code"] for territory in territories}) != len(territories):
        raise BackupError("duplicate territory code")

    observer_fields = {"id", "code", "lastName", "firstName", "middleName", "contact", "createdAt", "updatedAt"}
    for observer in observers:
        require_fields(observer, observer_fields, "observer")
        for field in ("code", "lastName", "firstName"):
            if not isinstance(observer[field], str) or not observer[field].strip():
                raise BackupError(f"blank observer field: {field}")
        _nullable_string(observer["middleName"], "observer.middleName")
        _nullable_string(observer["contact"], "observer.contact")
        created = integer(observer["createdAt"], "observer.createdAt")
        updated = integer(observer["updatedAt"], "observer.updatedAt")
        if updated < created:
            raise BackupError("invalid observer timestamp order")
    if len({observer["code"] for observer in observers}) != len(observers):
        raise BackupError("duplicate observer code")

    territory_ids = {territory["id"] for territory in territories}
    observer_ids = {observer["id"] for observer in observers}
    for value, label in ((settings["currentTerritoryId"], "currentTerritoryId"),
                         (settings["currentObserverId"], "currentObserverId")):
        if value is not None:
            canonical_uuid(value, label)
    coverage_ids: set[str] = set()
    for coverage in rows["map-coverage"]:
        require_fields(coverage, {"territoryId", "encoded"}, "map-coverage")
        territory_id = canonical_uuid(coverage["territoryId"], "map-coverage.territoryId")
        if territory_id in coverage_ids or territory_id not in territory_ids:
            raise BackupError("invalid map coverage territory")
        _validate_coverage(coverage["encoded"])
        coverage_ids.add(territory_id)

    point_fields = {
        "id", "territoryId", "observerId", "observationYear", "pointNumber",
        "beePresenceResult", "code", "latitude", "longitude", "gpsLatitude",
        "gpsLongitude", "gpsAccuracyM", "createdAt", "initialGroupReleaseAt", "completedAt",
    }
    point_keys: set[tuple[str, int, str, int]] = set()
    for point in points:
        require_fields(point, point_fields, "observation point")
        territory_id = canonical_uuid(point["territoryId"], "point.territoryId")
        observer_id = canonical_uuid(point["observerId"], "point.observerId")
        year = integer32(point["observationYear"], "point.observationYear")
        number = integer32(point["pointNumber"], "point.pointNumber")
        if territory_id not in territory_ids or observer_id not in observer_ids:
            raise BackupError("broken observation point foreign key")
        if year <= 0 or number <= 0:
            raise BackupError("invalid observation point number")
        key = (territory_id, year, observer_id, number)
        if key in point_keys:
            raise BackupError("duplicate observation point number")
        point_keys.add(key)
        _number(point["latitude"], "point.latitude", -90, 90)
        _number(point["longitude"], "point.longitude", -180, 180)
        _nullable_string(point["code"], "point.code")
        if point["beePresenceResult"] not in (None, "BEES_FOUND", "NO_BEES_FOUND"):
            raise BackupError("invalid bee presence result")
        if point["gpsLatitude"] is not None:
            _number(point["gpsLatitude"], "point.gpsLatitude", -90, 90)
        if point["gpsLongitude"] is not None:
            _number(point["gpsLongitude"], "point.gpsLongitude", -180, 180)
        if point["gpsAccuracyM"] is not None:
            _number(point["gpsAccuracyM"], "point.gpsAccuracyM", 0)
        created = integer(point["createdAt"], "point.createdAt")
        initial_release, completed = point["initialGroupReleaseAt"], point["completedAt"]
        if initial_release is not None and integer(initial_release, "point.initialGroupReleaseAt") < created:
            raise BackupError("invalid initial group release timestamp")
        if completed is not None and integer(completed, "point.completedAt") < created:
            raise BackupError("invalid observation point completion timestamp")
        if completed is not None and point["beePresenceResult"] is None:
            raise BackupError("completed point has no presence result")

    point_ids = {point["id"] for point in points}
    bee_fields = {"id", "observationPointId", "markColor", "markPosition", "createdAt"}
    bee_keys: set[tuple[str, str, str]] = set()
    bees_by_point: dict[str, list[dict[str, Any]]] = {}
    for bee in bees:
        require_fields(bee, bee_fields, "bee")
        point_id = canonical_uuid(bee["observationPointId"], "bee.observationPointId")
        if point_id not in point_ids:
            raise BackupError("broken bee foreign key")
        if not isinstance(bee["markColor"], str) or not bee["markColor"].strip():
            raise BackupError("blank bee mark color")
        if bee["markPosition"] not in ("NONE", "RIGHT_WING", "LEFT_WING"):
            raise BackupError("invalid bee mark position")
        integer(bee["createdAt"], "bee.createdAt")
        bee_key = (point_id, bee["markColor"], bee["markPosition"])
        if bee_key in bee_keys:
            raise BackupError("duplicate bee mark")
        bee_keys.add(bee_key)
        bees_by_point.setdefault(point_id, []).append(bee)
    for point in points:
        bee_count = len(bees_by_point.get(point["id"], []))
        presence = point["beePresenceResult"]
        if presence == "NO_BEES_FOUND" and (bee_count != 0 or point["completedAt"] is None):
            raise BackupError("invalid NO_BEES_FOUND point")
        if presence == "BEES_FOUND" and bee_count == 0:
            raise BackupError("BEES_FOUND point has no bees")
        if bee_count > 0 and presence != "BEES_FOUND":
            raise BackupError("point with bees lacks BEES_FOUND result")

    bee_ids = {bee["id"] for bee in bees}
    cycle_fields = {
        "id", "beeId", "sequenceNumber", "departureTime", "returnTime", "azimuthDeg",
        "azimuthCaptureConsumed", "initialGroupLaunch", "initialGroupLaunchCorrectionEligible",
        "createdAt", "updatedAt",
    }
    cycles_by_bee: dict[str, list[dict[str, Any]]] = {}
    sequence_keys: set[tuple[str, int]] = set()
    for cycle in cycles:
        require_fields(cycle, cycle_fields, "flight cycle")
        bee_id = canonical_uuid(cycle["beeId"], "cycle.beeId")
        sequence = integer32(cycle["sequenceNumber"], "cycle.sequenceNumber")
        if bee_id not in bee_ids:
            raise BackupError("broken flight cycle foreign key")
        if sequence <= 0 or (bee_id, sequence) in sequence_keys:
            raise BackupError("invalid or duplicate flight cycle sequence")
        sequence_keys.add((bee_id, sequence))
        departure = integer(cycle["departureTime"], "cycle.departureTime")
        returned = cycle["returnTime"]
        if returned is not None and integer(returned, "cycle.returnTime") < departure:
            raise BackupError("negative flight duration")
        if cycle["azimuthDeg"] is not None:
            _number(cycle["azimuthDeg"], "cycle.azimuthDeg", 0, 360, False)
        boolean(cycle["azimuthCaptureConsumed"], "cycle.azimuthCaptureConsumed")
        initial = boolean(cycle["initialGroupLaunch"], "cycle.initialGroupLaunch")
        correction = boolean(cycle["initialGroupLaunchCorrectionEligible"], "cycle.initialGroupLaunchCorrectionEligible")
        created = integer(cycle["createdAt"], "cycle.createdAt")
        updated = integer(cycle["updatedAt"], "cycle.updatedAt")
        if updated < created:
            raise BackupError("invalid flight cycle timestamp order")
        if initial and sequence != 1:
            raise BackupError("initial group launch must be sequence 1")
        if correction and (not initial or returned is not None):
            raise BackupError("invalid initial group launch correction provenance")
        cycles_by_bee.setdefault(bee_id, []).append(cycle)
    for bee_id, bee_cycles in cycles_by_bee.items():
        bee_cycles.sort(key=lambda cycle: (cycle["sequenceNumber"], cycle["id"]))
        if [cycle["sequenceNumber"] for cycle in bee_cycles] != list(range(1, len(bee_cycles) + 1)):
            raise BackupError(f"non-contiguous cycle sequence for bee {bee_id}")
        open_indices = [index for index, cycle in enumerate(bee_cycles) if cycle["returnTime"] is None]
        if len(open_indices) > 1 or (open_indices and open_indices[0] != len(bee_cycles) - 1):
            raise BackupError(f"open cycle is not the unique latest cycle for bee {bee_id}")

    points_by_id = {point["id"]: point for point in points}
    bees_by_id = {bee["id"]: bee for bee in bees}
    for cycle in cycles:
        if cycle["initialGroupLaunch"]:
            point = points_by_id[bees_by_id[cycle["beeId"]]["observationPointId"]]
            if point["initialGroupReleaseAt"] != cycle["departureTime"]:
                raise BackupError("initial group release timestamp mismatch")

    points_output: list[dict[str, Any]] = []
    point_order = lambda item: (item["observationYear"], item["territoryId"], item["observerId"],
                                item["pointNumber"], item["createdAt"], item["id"])
    for point in sorted(points, key=point_order):
        bees_output: list[dict[str, Any]] = []
        point_cycle_count = 0
        for bee in sorted(bees_by_point.get(point["id"], []), key=lambda item: (item["createdAt"], item["id"])):
            cycles_output: list[dict[str, Any]] = []
            # excluded stays a v1 compatibility count: rule set 2 never excludes a
            # cycle, so it is emitted as 0 rather than dropped from the contract.
            eligible = excluded = completed_count = open_count = 0
            for cycle in cycles_by_bee.get(bee["id"], []):
                returned = cycle["returnTime"]
                duration = None if returned is None else returned - cycle["departureTime"]
                if duration is None:
                    status = "NO_DURATION_OPEN"; open_count += 1
                else:
                    status = "ELIGIBLE"; completed_count += 1; eligible += 1
                diagnostics: list[str] = []
                cycles_output.append({**{field: cycle[field] for field in cycle_fields},
                                      "durationMs": duration, "durationEvidenceStatus": status,
                                      "diagnosticCodes": diagnostics})
            point_cycle_count += len(cycles_output)
            bees_output.append({**{field: bee[field] for field in bee_fields},
                                 "counts": {"totalCycles": len(cycles_output), "completedCycles": completed_count,
                                            "openCycles": open_count, "eligibleDurations": eligible,
                                            "excludedByD058": excluded},
                                 "flightCycles": cycles_output})
        points_output.append({**{field: point[field] for field in point_fields},
                              "counts": {"bees": len(bees_output), "flightCycles": point_cycle_count},
                              "bees": bees_output})

    observer_contexts = [{field: observer[field] for field in
                          ("id", "code", "lastName", "firstName", "middleName", "createdAt", "updatedAt")}
                         for observer in observers]
    integrity = [{"name": item.name, "path": item.path, "collectionSchemaVersion": 1,
                  "recordCount": item.record_count, "byteLength": len(item.data), "sha256": item.sha256}
                 for item in sorted(parsed.collections.values(), key=lambda value: value.name)]
    return {
        "resultSchemaVersion": RESULT_SCHEMA_VERSION,
        "explorerVersion": EXPLORER_VERSION,
        "ruleSetVersion": RULE_SET_VERSION,
        "appliedRules": [dict(rule) for rule in APPLIED_RULES],
        "provenance": {
            "sourceArchiveSha256": parsed.source_archive_sha256,
            "sourceArchiveByteLength": parsed.source_archive_byte_length,
            "logicalContentSha256": parsed.logical_content_sha256,
            "archiveId": parsed.manifest["archiveId"],
            "archiveCreatedAt": parsed.manifest["createdAt"],
            "backupFormatVersion": parsed.manifest["backupFormatVersion"],
            "archiveSchemaVersion": parsed.manifest["archiveSchemaVersion"],
            "profile": parsed.manifest["profile"],
            "sourceAppVersion": parsed.manifest["sourceAppVersion"],
            "roomSchemaVersion": parsed.manifest["roomSchemaVersion"],
            "collections": integrity,
        },
        "contexts": {
            "territories": sorted([{field: territory[field] for field in territory_fields}
                                     for territory in territories], key=lambda item: item["id"]),
            "observers": sorted(observer_contexts, key=lambda item: item["id"]),
        },
        "observationPoints": points_output,
        "counts": {"observationPoints": len(points), "bees": len(bees), "flightCycles": len(cycles)},
    }
