"""Deterministic human-readable renderings of the canonical evidence result.

Markdown and CSV are derived presentations of the frozen canonical
``evidence.json`` v1 model. They are built from the same immutable result
object that produces the canonical bytes and never recompute evidence
semantics: eligibility, diagnostics, counts and the applied rule set are read
from the canonical model, never derived again here.

Properties guaranteed by this module:

* no clock, host, path, locale or environment value enters the output;
* every ordering is the ordering already present in the canonical model;
* null becomes an empty CSV field and ``(not stored)`` in Markdown, while
  ``0`` and ``false`` stay distinguishable from absent values;
* booleans render as lowercase ``true`` / ``false``;
* numbers use Python's shortest round-trip representation, matching the
  canonical serializer's numeric lexical form.
"""

from __future__ import annotations

import csv
import io
import json
from datetime import datetime, timedelta, timezone
from typing import Any

EMPTY_MARKDOWN = "(not stored)"
NO_DURATION_MARKDOWN = "(no duration)"
OPEN_MARKDOWN = "OPEN"
DIAGNOSTIC_SEPARATOR = ";"

POINT_COLUMNS = (
    "observationPointId",
    "territoryId",
    "observerId",
    "observationYear",
    "pointNumber",
    "beePresence",
    "latitude",
    "longitude",
    "gpsAccuracyM",
    "createdAt",
    "initialGroupReleaseAt",
    "completedAt",
    "beeCount",
    "flightCycleCount",
)

BEE_COLUMNS = (
    "beeId",
    "observationPointId",
    "markColor",
    "markPosition",
    "createdAt",
    "totalCycles",
    "completedCycles",
    "openCycles",
    "eligibleDurations",
    "excludedByD058",
)

CYCLE_COLUMNS = (
    "flightCycleId",
    "beeId",
    "observationPointId",
    "sequenceNumber",
    "departureTime",
    "returnTime",
    "durationMs",
    "durationEvidenceStatus",
    "azimuthDeg",
    "azimuthCaptureConsumed",
    "initialGroupLaunch",
    "initialGroupLaunchCorrectionEligible",
    "diagnosticCodes",
    "createdAt",
    "updatedAt",
)

# datetime covers 0001-01-01T00:00:00Z .. 9999-12-31T23:59:59.999999Z. A stored
# epoch-millisecond value outside that range is still valid canonical evidence,
# so the ISO form is simply omitted for it instead of failing the report.
_ISO_MIN_MS = -62_135_596_800_000
_ISO_MAX_MS = 253_402_300_799_999
_EPOCH = datetime(1970, 1, 1, tzinfo=timezone.utc)


def _number_text(value: Any) -> str:
    """Render a canonical number without locale or platform formatting."""
    if isinstance(value, float):
        return json.dumps(value, allow_nan=False)
    return str(value)


def _csv_cell(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "true" if value else "false"
    return _number_text(value)


def _csv_bytes(columns: tuple[str, ...], rows: list[tuple[Any, ...]]) -> bytes:
    buffer = io.StringIO(newline="")
    writer = csv.writer(
        buffer,
        delimiter=",",
        quotechar='"',
        quoting=csv.QUOTE_MINIMAL,
        lineterminator="\n",
    )
    writer.writerow(list(columns))
    for row in rows:
        writer.writerow([_csv_cell(value) for value in row])
    text = buffer.getvalue()
    return text.encode("utf-8")


def format_timestamp(value: int) -> str:
    """Deterministic UTC rendering of a canonical epoch-millisecond instant."""
    if _ISO_MIN_MS <= value <= _ISO_MAX_MS:
        moment = _EPOCH + timedelta(milliseconds=value)
        iso = "%s.%03dZ" % (moment.strftime("%Y-%m-%dT%H:%M:%S"), value % 1000)
    else:
        iso = "out-of-ISO-range"
    return f"{iso} ({value} ms)"


def format_duration(duration_ms: int) -> str:
    """Exact HH:MM:SS.mmm rendering; milliseconds are never rounded away."""
    sign = "-" if duration_ms < 0 else ""
    hours, remainder = divmod(abs(duration_ms), 3_600_000)
    minutes, remainder = divmod(remainder, 60_000)
    seconds, milliseconds = divmod(remainder, 1_000)
    return f"{sign}{hours:02d}:{minutes:02d}:{seconds:02d}.{milliseconds:03d}"


def _markdown_escape(text: str) -> str:
    """Keep a data value from breaking out of a Markdown table cell."""
    return (
        text.replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\r\n", " ")
        .replace("\n", " ")
        .replace("\r", " ")
    )


def _markdown_cell(value: Any) -> str:
    """Render a value for a Markdown table cell without losing or inventing facts."""
    if value is None:
        return EMPTY_MARKDOWN
    if isinstance(value, bool):
        return "true" if value else "false"
    return _markdown_escape(_number_text(value))


def _diagnostics_text(codes: list[str]) -> str:
    if not codes:
        return EMPTY_MARKDOWN
    return ", ".join(codes)


def render_points_csv(result: dict[str, Any]) -> bytes:
    """One row per ObservationPoint, including points with no Bees."""
    rows: list[tuple[Any, ...]] = []
    for point in result["observationPoints"]:
        counts = point["counts"]
        rows.append((
            point["id"],
            point["territoryId"],
            point["observerId"],
            point["observationYear"],
            point["pointNumber"],
            point["beePresenceResult"],
            point["latitude"],
            point["longitude"],
            point["gpsAccuracyM"],
            point["createdAt"],
            point["initialGroupReleaseAt"],
            point["completedAt"],
            counts["bees"],
            counts["flightCycles"],
        ))
    return _csv_bytes(POINT_COLUMNS, rows)


def render_bees_csv(result: dict[str, Any]) -> bytes:
    """One row per Bee, including Bees without any FlightCycle."""
    rows: list[tuple[Any, ...]] = []
    for point in result["observationPoints"]:
        for bee in point["bees"]:
            counts = bee["counts"]
            rows.append((
                bee["id"],
                bee["observationPointId"],
                bee["markColor"],
                bee["markPosition"],
                bee["createdAt"],
                counts["totalCycles"],
                counts["completedCycles"],
                counts["openCycles"],
                counts["eligibleDurations"],
                counts["excludedByD058"],
            ))
    return _csv_bytes(BEE_COLUMNS, rows)


def render_cycles_csv(result: dict[str, Any]) -> bytes:
    """One row per FlightCycle in canonical order, with its owning point id."""
    rows: list[tuple[Any, ...]] = []
    for point in result["observationPoints"]:
        for bee in point["bees"]:
            for cycle in bee["flightCycles"]:
                rows.append((
                    cycle["id"],
                    cycle["beeId"],
                    point["id"],
                    cycle["sequenceNumber"],
                    cycle["departureTime"],
                    cycle["returnTime"],
                    cycle["durationMs"],
                    cycle["durationEvidenceStatus"],
                    cycle["azimuthDeg"],
                    cycle["azimuthCaptureConsumed"],
                    cycle["initialGroupLaunch"],
                    cycle["initialGroupLaunchCorrectionEligible"],
                    DIAGNOSTIC_SEPARATOR.join(cycle["diagnosticCodes"]),
                    cycle["createdAt"],
                    cycle["updatedAt"],
                ))
    return _csv_bytes(CYCLE_COLUMNS, rows)


def _provenance_rows(result: dict[str, Any]) -> list[tuple[str, str]]:
    provenance = result["provenance"]
    return [
        ("Explorer version", result["explorerVersion"]),
        ("Result schema version", str(result["resultSchemaVersion"])),
        ("Rule set version", str(result["ruleSetVersion"])),
        ("Source archive SHA-256", provenance["sourceArchiveSha256"]),
        ("Source archive byte length", str(provenance["sourceArchiveByteLength"])),
        ("Logical content SHA-256", provenance["logicalContentSha256"]),
        ("Backup format version", str(provenance["backupFormatVersion"])),
        ("Archive schema version", str(provenance["archiveSchemaVersion"])),
        ("Profile", str(provenance["profile"])),
        ("Source app version", str(provenance["sourceAppVersion"])),
        ("Room schema version", str(provenance["roomSchemaVersion"])),
        ("Archive id", str(provenance["archiveId"])),
        ("Archive created at", format_timestamp(provenance["archiveCreatedAt"])),
    ]


def render_markdown(result: dict[str, Any]) -> bytes:
    """Human-readable report of the recorded evidence, derived from the canonical model."""
    lines: list[str] = []
    append = lines.append

    append("# Analysis Evidence Explorer")
    append("")

    append("## Provenance")
    append("")
    append("| Field | Value |")
    append("| --- | --- |")
    for label, value in _provenance_rows(result):
        append(f"| {label} | {value} |")
    append("")
    append(
        "All timestamps are UTC. The canonical source of every timestamp is the "
        "epoch-millisecond value in `evidence.json`; the ISO-8601 form is derived "
        "deterministically from it."
    )
    append("")

    append("## Applied rules")
    append("")
    append("| Rule | Rule set version | Threshold (ms) |")
    append("| --- | --- | --- |")
    for rule in result["appliedRules"]:
        append(
            "| {ruleId} | {ruleSetVersion} | {thresholdMs} |".format(
                ruleId=rule["ruleId"],
                ruleSetVersion=rule["ruleSetVersion"],
                thresholdMs=rule["thresholdMs"],
            )
        )
    append("")
    append(
        "The applied rules are evidence eligibility rules. A completed "
        "`sequenceNumber` 1 FlightCycle shorter than the D058 threshold is excluded "
        "from duration-based analysis. This is not a biological rule."
    )
    append("")
    append(
        "`diagnosticCodes` are stable non-error observations. "
        "`D058_APPLIED_TO_NON_GROUP_FIRST_CYCLE` records that D058 was applied to a "
        "first cycle whose stored provenance does not mark it as an initial group "
        "launch. It does not change eligibility and does not assert a defect."
    )
    append("")

    totals = result["counts"]
    eligible = 0
    excluded = 0
    open_cycles = 0
    for point in result["observationPoints"]:
        for bee in point["bees"]:
            eligible += bee["counts"]["eligibleDurations"]
            excluded += bee["counts"]["excludedByD058"]
            open_cycles += bee["counts"]["openCycles"]

    append("## Overall counts")
    append("")
    append("| Measure | Count |")
    append("| --- | --- |")
    append(f"| Observation points | {totals['observationPoints']} |")
    append(f"| Bees | {totals['bees']} |")
    append(f"| Flight cycles | {totals['flightCycles']} |")
    append(f"| Eligible durations | {eligible} |")
    append(f"| Excluded by D058 | {excluded} |")
    append(f"| Open cycles | {open_cycles} |")
    append("")

    append("## Observation points")
    append("")
    if not result["observationPoints"]:
        append("No ObservationPoint is recorded in this archive.")
        append("")
        return "\n".join(lines).encode("utf-8")

    for index, point in enumerate(result["observationPoints"], start=1):
        counts = point["counts"]
        append(
            f"### {index}. Point {point['observationYear']} / {point['pointNumber']}"
            f" - {point['id']}"
        )
        append("")
        append("| Field | Value |")
        append("| --- | --- |")
        append(f"| Observation point id | {point['id']} |")
        append(f"| Territory id | {point['territoryId']} |")
        append(f"| Observer id | {point['observerId']} |")
        append(f"| Point code | {_markdown_cell(point['code'])} |")
        append(f"| Observation year | {point['observationYear']} |")
        append(f"| Point number | {point['pointNumber']} |")
        append(f"| Bee presence result | {_markdown_cell(point['beePresenceResult'])} |")
        append(f"| Latitude | {_number_text(point['latitude'])} |")
        append(f"| Longitude | {_number_text(point['longitude'])} |")
        append(f"| GPS latitude | {_markdown_cell(point['gpsLatitude'])} |")
        append(f"| GPS longitude | {_markdown_cell(point['gpsLongitude'])} |")
        append(f"| GPS accuracy (m) | {_markdown_cell(point['gpsAccuracyM'])} |")
        append(f"| Created at | {format_timestamp(point['createdAt'])} |")
        append(
            "| Initial group release at | "
            f"{_render_optional_timestamp(point['initialGroupReleaseAt'])} |"
        )
        append(
            "| Completed at | "
            f"{_render_optional_timestamp(point['completedAt'])} |"
        )
        append(f"| Bees | {counts['bees']} |")
        append(f"| Flight cycles | {counts['flightCycles']} |")
        append("")

        if point["beePresenceResult"] == "NO_BEES_FOUND":
            append(
                "The stored bee presence result for this ObservationPoint is "
                "`NO_BEES_FOUND`: the observer explicitly recorded that no bees were "
                "found. This is stored negative evidence and context, not an "
                "exclusion area and not a statement about nest location."
            )
            append("")

        if not point["bees"]:
            append("No Bee record is stored for this ObservationPoint.")
            append("")
            continue

        append("#### Bees")
        append("")
        append(
            "| Bee id | Mark color | Mark position | Created at | Total cycles | "
            "Completed cycles | Open cycles | Eligible durations | Excluded by D058 |"
        )
        append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        for bee in point["bees"]:
            bee_counts = bee["counts"]
            append(
                f"| {bee['id']} | {_markdown_cell(bee['markColor'])} | "
                f"{_markdown_cell(bee['markPosition'])} | "
                f"{format_timestamp(bee['createdAt'])} | {bee_counts['totalCycles']} | "
                f"{bee_counts['completedCycles']} | {bee_counts['openCycles']} | "
                f"{bee_counts['eligibleDurations']} | {bee_counts['excludedByD058']} |"
            )
        append("")

        for bee in point["bees"]:
            append(
                f"#### Flight cycles - Bee {bee['id']}"
                f" ({_markdown_cell(bee['markColor'])} / {_markdown_cell(bee['markPosition'])})"
            )
            append("")
            if not bee["flightCycles"]:
                append("No FlightCycle is stored for this Bee.")
                append("")
                continue
            append(
                "| Sequence | Departure | Return | Duration | Duration evidence status | "
                "Azimuth (deg) | Azimuth capture consumed | Initial group launch | "
                "Correction eligible | Diagnostics |"
            )
            append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
            for cycle in bee["flightCycles"]:
                return_text = (
                    OPEN_MARKDOWN
                    if cycle["returnTime"] is None
                    else format_timestamp(cycle["returnTime"])
                )
                duration_text = (
                    NO_DURATION_MARKDOWN
                    if cycle["durationMs"] is None
                    else f"{format_duration(cycle['durationMs'])} ({cycle['durationMs']} ms)"
                )
                append(
                    f"| {cycle['sequenceNumber']} | {format_timestamp(cycle['departureTime'])} | "
                    f"{return_text} | {duration_text} | {cycle['durationEvidenceStatus']} | "
                    f"{_markdown_cell(cycle['azimuthDeg'])} | "
                    f"{_markdown_cell(cycle['azimuthCaptureConsumed'])} | "
                    f"{_markdown_cell(cycle['initialGroupLaunch'])} | "
                    f"{_markdown_cell(cycle['initialGroupLaunchCorrectionEligible'])} | "
                    f"{_diagnostics_text(cycle['diagnosticCodes'])} |"
                )
            append("")

    return "\n".join(lines).encode("utf-8")


def _render_optional_timestamp(value: int | None) -> str:
    return EMPTY_MARKDOWN if value is None else format_timestamp(value)
