from __future__ import annotations

import copy
import csv
import hashlib
import io
import json
import shutil
import socket
import sys
import tempfile
import unittest
import uuid
import warnings
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).parent))
import backup_reader
import explorer


TESTDATA = Path(__file__).parent / "testdata"


def uid(number: int) -> str:
    return str(uuid.UUID(f"00000000-0000-0000-0000-{number:012d}"))


def base_rows() -> dict[str, list[dict]]:
    territory_id = uid(1)
    observer_id = uid(2)
    point_id = uid(3)
    bee_id = uid(4)
    return {
        "territories": [{
            "id": territory_id, "code": "T", "name": "Territory", "region": "R",
            "district": "D", "createdAt": 1, "updatedAt": 1,
        }],
        "observers": [{
            "id": observer_id, "code": "O", "lastName": "L", "firstName": "F",
            "middleName": None, "contact": "private", "createdAt": 1, "updatedAt": 1,
        }],
        "observation-points": [{
            "id": point_id, "territoryId": territory_id, "observerId": observer_id,
            "observationYear": 2026, "pointNumber": 1, "beePresenceResult": "BEES_FOUND",
            "code": None, "latitude": 55.0, "longitude": 37.0, "gpsLatitude": None,
            "gpsLongitude": None, "gpsAccuracyM": None, "createdAt": 1,
            "initialGroupReleaseAt": 1_000, "completedAt": 70_000,
        }],
        "bees": [{
            "id": bee_id, "observationPointId": point_id, "markColor": "red",
            "markPosition": "LEFT_WING", "createdAt": 2,
        }],
        "flight-cycles": [{
            "id": uid(5), "beeId": bee_id, "sequenceNumber": 1, "departureTime": 1_000,
            "returnTime": 61_000, "azimuthDeg": 0.0, "azimuthCaptureConsumed": True,
            "initialGroupLaunch": True, "initialGroupLaunchCorrectionEligible": False,
            "createdAt": 1_000, "updatedAt": 61_000,
        }],
        "portable-settings": [{"currentTerritoryId": territory_id, "currentObserverId": observer_id}],
        "map-coverage": [{"territoryId": territory_id, "encoded": "v1|56.0,37.0,55.0,38.0"}],
    }


def point_record(number: int, *, year: int = 2026, point_number: int = 1) -> dict:
    return {
        "id": uid(number), "territoryId": uid(1), "observerId": uid(2),
        "observationYear": year, "pointNumber": point_number, "beePresenceResult": "NO_BEES_FOUND",
        "code": None, "latitude": 55.0, "longitude": 37.0, "gpsLatitude": None,
        "gpsLongitude": None, "gpsAccuracyM": None, "createdAt": number,
        "initialGroupReleaseAt": None, "completedAt": number + 1,
    }


def cycle_record(
    number: int,
    *,
    bee_id: str = uid(4),
    sequence: int = 1,
    departure: int = 1_000,
    returned: int | None = 61_000,
    azimuth: float | None = None,
    consumed: bool = False,
    initial: bool = True,
    correction: bool = False,
) -> dict:
    return {
        "id": uid(number), "beeId": bee_id, "sequenceNumber": sequence,
        "departureTime": departure, "returnTime": returned, "azimuthDeg": azimuth,
        "azimuthCaptureConsumed": consumed, "initialGroupLaunch": initial,
        "initialGroupLaunchCorrectionEligible": correction, "createdAt": departure,
        "updatedAt": returned if returned is not None else departure,
    }


def write_backup(
    path: Path,
    *,
    rows: dict[str, list[dict]] | None = None,
    manifest_overrides: dict | None = None,
    descriptor_overrides: dict[str, dict] | None = None,
    raw_payloads: dict[str, bytes] | None = None,
    extra_entries: dict[str, bytes] | None = None,
) -> None:
    records = copy.deepcopy(rows if rows is not None else base_rows())
    payloads = {
        name: b"".join(
            json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8") + b"\n"
            for value in records[name]
        )
        for name in explorer.COLLECTIONS
    }
    payloads.update(raw_payloads or {})
    descriptors = []
    for name, collection_path in explorer.COLLECTIONS.items():
        payload = payloads[name]
        descriptor = {
            "name": name,
            "path": collection_path,
            "collectionSchemaVersion": 1,
            "required": True,
            "recordCount": len(records[name]),
            "byteLength": len(payload),
            "sha256": hashlib.sha256(payload).hexdigest(),
        }
        descriptor.update((descriptor_overrides or {}).get(name, {}))
        descriptors.append(descriptor)
    manifest = {
        "backupFormatVersion": 1, "archiveSchemaVersion": 1, "archiveId": uid(99),
        "createdAt": 123, "sourceAppVersion": "test", "roomSchemaVersion": 6,
        "profile": "COMPLETE_BACKUP", "collections": descriptors,
    }
    manifest.update(manifest_overrides or {})
    def write_entry(archive: zipfile.ZipFile, name: str, payload: bytes) -> None:
        info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_STORED
        info.create_system = 0
        info.external_attr = 0
        archive.writestr(info, payload)

    with zipfile.ZipFile(path, "w") as archive:
        write_entry(archive, "manifest.json", json.dumps(manifest, separators=(",", ":")).encode("utf-8"))
        for name, collection_path in explorer.COLLECTIONS.items():
            write_entry(archive, collection_path, payloads[name])
        for name, payload in (extra_entries or {}).items():
            write_entry(archive, name, payload)


def golden_rows() -> dict[str, list[dict]]:
    rows = base_rows()
    first_point = rows["observation-points"][0]
    first_point.update({
        "latitude": 55.5,
        "longitude": 37.5,
        "createdAt": 1_000,
        "initialGroupReleaseAt": 2_000,
        "completedAt": 500_000,
    })
    second_point = point_record(30, year=2025, point_number=2)
    second_point.update({
        "latitude": 55.5,
        "longitude": 37.5,
        "createdAt": 900,
        "completedAt": 901,
    })
    rows["observation-points"] = [first_point, second_point]
    rows["bees"] = [
        {
            "id": uid(4), "observationPointId": uid(3), "markColor": "red",
            "markPosition": "LEFT_WING", "createdAt": 3_000,
        },
        {
            "id": uid(8), "observationPointId": uid(3), "markColor": "blue",
            "markPosition": "RIGHT_WING", "createdAt": 2_500,
        },
    ]
    rows["flight-cycles"] = [
        cycle_record(5, bee_id=uid(4), sequence=1, departure=3_000,
                     returned=62_999, azimuth=None, initial=False),
        cycle_record(6, bee_id=uid(4), sequence=2, departure=70_000,
                     returned=130_000, azimuth=45.5, consumed=True, initial=False),
        cycle_record(7, bee_id=uid(4), sequence=3, departure=140_000,
                     returned=140_001, azimuth=None, initial=False),
        cycle_record(9, bee_id=uid(4), sequence=4, departure=150_000,
                     returned=None, azimuth=None, initial=False),
        cycle_record(10, bee_id=uid(8), sequence=1, departure=2_000,
                     returned=62_000, azimuth=0.0, initial=True),
    ]
    return rows


class ExplorerTest(unittest.TestCase):
    def test_full_canonical_result_matches_golden_v1(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "golden-input.zip"
            second_archive = root / "golden-input-two.zip"
            write_backup(archive, rows=golden_rows())
            write_backup(second_archive, rows=golden_rows())
            first = explorer.write_evidence(archive, root / "first").read_bytes()
            second = explorer.write_evidence(archive, root / "second").read_bytes()
            rebuilt = explorer.write_evidence(second_archive, root / "rebuilt").read_bytes()
            expected = (TESTDATA / "golden_evidence_v1.json").read_bytes()

            self.assertEqual(archive.read_bytes(), second_archive.read_bytes())
            self.assertEqual(first, expected)
            self.assertEqual(second, expected)
            self.assertEqual(rebuilt, expected)
            self.assertFalse(first.startswith(b"\xef\xbb\xbf"))
            self.assertNotIn(b"\r", first)
            self.assertTrue(first.endswith(b"\n"))
            self.assertFalse(first.endswith(b"\n\n"))
            result = json.loads(first)
            self.assertEqual(result["resultSchemaVersion"], 1)
            self.assertEqual(result["explorerVersion"], "0.2.0")
            self.assertEqual(result["ruleSetVersion"], 1)
            self.assertEqual(result["appliedRules"], [{
                "ruleId": "D058", "ruleSetVersion": 1, "thresholdMs": 60_000,
            }])
            for point in result["observationPoints"]:
                for bee in point["bees"]:
                    counts = bee["counts"]
                    self.assertEqual(
                        counts["totalCycles"],
                        counts["eligibleDurations"] + counts["excludedByD058"] + counts["openCycles"],
                    )
                    self.assertEqual(
                        counts["completedCycles"],
                        counts["eligibleDurations"] + counts["excludedByD058"],
                    )
                    for cycle in bee["flightCycles"]:
                        self.assertEqual(cycle["diagnosticCodes"], sorted(cycle["diagnosticCodes"]))
            forbidden = {"generatedAt", "sourceFilename", "sourcePath", "hostName", "executionDuration"}
            self.assertTrue(forbidden.isdisjoint(result))
            self.assertTrue(forbidden.isdisjoint(result["provenance"]))

    def test_empty_research_and_no_bees_found_are_preserved(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "empty.zip"
            rows = base_rows()
            for name in ("territories", "observers", "observation-points", "bees", "flight-cycles", "map-coverage"):
                rows[name] = []
            rows["portable-settings"] = [{"currentTerritoryId": None, "currentObserverId": None}]
            write_backup(archive, rows=rows)
            result = explorer.build_evidence(archive)
            self.assertEqual(result["observationPoints"], [])
            self.assertEqual(result["counts"], {"observationPoints": 0, "bees": 0, "flightCycles": 0})

            rows = base_rows()
            rows["observation-points"] = [point_record(30)]
            rows["bees"] = []
            rows["flight-cycles"] = []
            write_backup(archive, rows=rows)
            point = explorer.build_evidence(archive)["observationPoints"][0]
            self.assertEqual(point["beePresenceResult"], "NO_BEES_FOUND")
            self.assertEqual(point["bees"], [])

    def test_d058_boundaries_open_cycle_and_diagnostic(self) -> None:
        cases = (
            (59_999, 60_999, 1, False, "EXCLUDED_BY_D058", ["D058_APPLIED_TO_NON_GROUP_FIRST_CYCLE"]),
            (60_000, 61_000, 1, True, "ELIGIBLE", []),
        )
        for duration, returned, sequence, initial, expected_status, expected_diagnostics in cases:
            with self.subTest(duration=duration):
                with tempfile.TemporaryDirectory() as directory:
                    archive = Path(directory) / "case.zip"
                    rows = base_rows()
                    rows["observation-points"][0]["initialGroupReleaseAt"] = 1_000 if initial else None
                    rows["flight-cycles"] = [cycle_record(5, sequence=sequence, returned=returned, initial=initial)]
                    write_backup(archive, rows=rows)
                    cycle = explorer.build_evidence(archive)["observationPoints"][0]["bees"][0]["flightCycles"][0]
                    self.assertEqual(cycle["durationMs"], duration)
                    self.assertEqual(cycle["durationEvidenceStatus"], expected_status)
                    self.assertEqual(cycle["diagnosticCodes"], expected_diagnostics)

        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "later-open.zip"
            rows = base_rows()
            rows["flight-cycles"] = [
                cycle_record(5),
                cycle_record(6, sequence=2, departure=70_000, returned=70_001, initial=False),
                cycle_record(7, sequence=3, departure=80_000, returned=None, initial=False),
            ]
            write_backup(archive, rows=rows)
            cycles = explorer.build_evidence(archive)["observationPoints"][0]["bees"][0]["flightCycles"]
            self.assertEqual(cycles[1]["durationMs"], 1)
            self.assertEqual(cycles[1]["durationEvidenceStatus"], "ELIGIBLE")
            self.assertIsNone(cycles[2]["durationMs"])
            self.assertEqual(cycles[2]["durationEvidenceStatus"], "NO_DURATION_OPEN")

    def test_azimuth_does_not_affect_eligibility_and_zero_is_valid(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "azimuth.zip"
            rows = base_rows()
            rows["flight-cycles"] = [cycle_record(5, azimuth=None, consumed=True)]
            write_backup(archive, rows=rows)
            absent = explorer.build_evidence(archive)["observationPoints"][0]["bees"][0]["flightCycles"][0]
            rows["flight-cycles"] = [cycle_record(5, azimuth=0.0, consumed=False)]
            write_backup(archive, rows=rows)
            zero = explorer.build_evidence(archive)["observationPoints"][0]["bees"][0]["flightCycles"][0]
            self.assertEqual(absent["durationEvidenceStatus"], zero["durationEvidenceStatus"])
            self.assertIsNone(absent["azimuthDeg"])
            self.assertEqual(zero["azimuthDeg"], 0.0)

    def test_multiple_bees_cycles_counts_and_completed_point_with_open_cycle(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "multiple.zip"
            rows = base_rows()
            second_bee = {"id": uid(8), "observationPointId": uid(3), "markColor": "blue", "markPosition": "RIGHT_WING", "createdAt": 3}
            rows["bees"].append(second_bee)
            rows["flight-cycles"] = [
                cycle_record(5),
                cycle_record(6, sequence=2, departure=70_000, returned=71_000, initial=False),
                cycle_record(9, bee_id=uid(8), returned=None, correction=True),
            ]
            write_backup(archive, rows=rows)
            point = explorer.build_evidence(archive)["observationPoints"][0]
            self.assertIsNotNone(point["completedAt"])
            self.assertEqual(point["counts"], {"bees": 2, "flightCycles": 3})
            self.assertEqual([bee["id"] for bee in point["bees"]], [uid(4), uid(8)])
            self.assertEqual(point["bees"][0]["counts"]["totalCycles"], 2)
            self.assertEqual(point["bees"][1]["counts"]["openCycles"], 1)

    def test_points_have_total_order_and_duplicate_coordinates_remain_separate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "points.zip"
            rows = base_rows()
            rows["observation-points"] = [point_record(31, year=2026), point_record(30, year=2025)]
            rows["bees"] = []
            rows["flight-cycles"] = []
            write_backup(archive, rows=rows)
            points = explorer.build_evidence(archive)["observationPoints"]
            self.assertEqual([point["id"] for point in points], [uid(30), uid(31)])
            self.assertEqual(points[0]["latitude"], points[1]["latitude"])
            self.assertNotEqual(points[0]["observationYear"], points[1]["observationYear"])

    def test_negative_duration_and_evidence_critical_graph_errors_fail(self) -> None:
        mutations = []
        negative = base_rows()
        negative["flight-cycles"][0]["returnTime"] = 999
        mutations.append(("negative duration", negative))
        broken_fk = base_rows()
        broken_fk["flight-cycles"][0]["beeId"] = uid(77)
        mutations.append(("broken FK", broken_fk))
        gap = base_rows()
        gap["flight-cycles"][0]["sequenceNumber"] = 2
        gap["flight-cycles"][0]["initialGroupLaunch"] = False
        mutations.append(("sequence gap", gap))
        invalid_azimuth = base_rows()
        invalid_azimuth["flight-cycles"][0]["azimuthDeg"] = 360.0
        mutations.append(("invalid azimuth", invalid_azimuth))
        invalid_uuid = base_rows()
        invalid_uuid["bees"][0]["id"] = "1-1-1-1-1"
        invalid_uuid["flight-cycles"][0]["beeId"] = "1-1-1-1-1"
        mutations.append(("non-canonical UUID", invalid_uuid))
        oversized_sequence = base_rows()
        oversized_sequence["flight-cycles"][0]["sequenceNumber"] = 2**31
        oversized_sequence["flight-cycles"][0]["initialGroupLaunch"] = False
        mutations.append(("sequence outside Kotlin Int", oversized_sequence))
        oversized_timestamp = base_rows()
        oversized_timestamp["flight-cycles"][0]["departureTime"] = 2**63
        mutations.append(("timestamp outside Kotlin Long", oversized_timestamp))
        for name, rows in mutations:
            with self.subTest(name=name):
                with tempfile.TemporaryDirectory() as directory:
                    archive = Path(directory) / "invalid.zip"
                    write_backup(archive, rows=rows)
                    with self.assertRaises(explorer.BackupError):
                        explorer.build_evidence(archive)

    def test_unsupported_versions_fail_closed(self) -> None:
        cases = (
            ({"backupFormatVersion": 2}, None),
            ({"archiveSchemaVersion": 2}, None),
            (None, {"flight-cycles": {"collectionSchemaVersion": 2}}),
        )
        for manifest_overrides, descriptor_overrides in cases:
            with self.subTest(manifest=manifest_overrides, descriptor=descriptor_overrides):
                with tempfile.TemporaryDirectory() as directory:
                    archive = Path(directory) / "unsupported.zip"
                    output = Path(directory) / "output"
                    write_backup(archive, manifest_overrides=manifest_overrides, descriptor_overrides=descriptor_overrides)
                    self.assertEqual(explorer.main([str(archive), str(output)]), 2)
                    self.assertFalse((output / "evidence.json").exists())

    def test_unknown_optional_collection_is_integrity_checked_and_ignored(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "optional.zip"
            write_backup(archive)
            with zipfile.ZipFile(archive, "r") as value:
                entries = {info.filename: value.read(info) for info in value.infolist()}
            manifest = json.loads(entries["manifest.json"])
            optional_data = b"future bytes"
            manifest["collections"].append({
                "name": "future", "path": "future/data.bin", "collectionSchemaVersion": 99,
                "required": False, "recordCount": 1, "byteLength": len(optional_data),
                "sha256": hashlib.sha256(optional_data).hexdigest(),
            })
            with zipfile.ZipFile(archive, "w") as value:
                value.writestr("manifest.json", json.dumps(manifest).encode())
                for name, payload in entries.items():
                    if name != "manifest.json":
                        value.writestr(name, payload)
                value.writestr("future/data.bin", optional_data)
            result = explorer.build_evidence(archive)
            self.assertEqual(len(result["provenance"]["collections"]), 7)

    def test_malformed_integrity_count_path_and_utf8_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            malformed = root / "malformed.zip"
            malformed.write_bytes(b"not a zip")
            with self.assertRaises(explorer.BackupError):
                explorer.build_evidence(malformed)

            cases = (
                ("hash", {"territories": {"sha256": "0" * 64}}, None, None),
                ("count", {"territories": {"recordCount": 99}}, None, None),
                ("length", {"territories": {"byteLength": 99}}, None, None),
                ("unsafe path", None, None, {"../escape": b"x"}),
                ("invalid utf8", None, {"territories": b"\xff\n"}, None),
                ("non-NDJSON separator", None, {"territories": b"{}\x0b{}"}, None),
                ("unlisted", None, None, {"extra.bin": b"x"}),
            )
            for name, descriptors, payloads, extras in cases:
                with self.subTest(name=name):
                    archive = root / f"{name}.zip"
                    output = root / f"out-{name}"
                    write_backup(archive, descriptor_overrides=descriptors, raw_payloads=payloads, extra_entries=extras)
                    self.assertEqual(explorer.main([str(archive), str(output)]), 2)
                    self.assertFalse((output / "evidence.json").exists())

    def test_zip_entry_limit_and_missing_required_collection_fail(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "limited.zip"
            write_backup(archive)
            with mock.patch.object(backup_reader, "MAX_ENTRY_BYTES", 4):
                with self.assertRaises(explorer.BackupError):
                    explorer.build_evidence(archive)

            with zipfile.ZipFile(archive, "r") as value:
                entries = {info.filename: value.read(info) for info in value.infolist()}
            missing_path = explorer.COLLECTIONS["flight-cycles"]
            with zipfile.ZipFile(archive, "w") as value:
                for name, payload in entries.items():
                    if name != missing_path:
                        value.writestr(name, payload)
            with self.assertRaises(explorer.BackupError):
                explorer.build_evidence(archive)

    def test_duplicate_zip_entry_and_unknown_required_collection_fail(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "duplicate.zip"
            write_backup(archive)
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(archive, "a") as value:
                    value.writestr("manifest.json", b"{}")
            with self.assertRaises(explorer.BackupError):
                explorer.build_evidence(archive)

            rows = base_rows()
            write_backup(archive, rows=rows)
            with zipfile.ZipFile(archive, "r") as value:
                entries = {info.filename: value.read(info) for info in value.infolist()}
            manifest = json.loads(entries["manifest.json"])
            manifest["collections"].append({
                "name": "future", "path": "future/data.json", "collectionSchemaVersion": 2,
                "required": True, "recordCount": 0, "byteLength": 0,
                "sha256": hashlib.sha256(b"").hexdigest(),
            })
            with zipfile.ZipFile(archive, "w") as value:
                value.writestr("manifest.json", json.dumps(manifest).encode())
                for name, payload in entries.items():
                    if name != "manifest.json":
                        value.writestr(name, payload)
            with self.assertRaises(explorer.BackupError):
                explorer.build_evidence(archive)

    def test_repeat_is_byte_identical_filename_independent_and_input_unchanged(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first_archive = root / "first-name.zip"
            second_archive = root / "renamed.zip"
            write_backup(first_archive)
            before = hashlib.sha256(first_archive.read_bytes()).hexdigest()
            shutil.copyfile(first_archive, second_archive)
            first_output = explorer.write_evidence(first_archive, root / "out-one").read_bytes()
            second_output = explorer.write_evidence(second_archive, root / "out-two").read_bytes()
            after = hashlib.sha256(first_archive.read_bytes()).hexdigest()
            self.assertEqual(before, after)
            self.assertEqual(first_output, second_output)
            self.assertTrue(first_output.endswith(b"\n"))
            self.assertNotIn(str(first_archive).encode(), first_output)

    def test_logical_digest_has_pinned_production_compatible_vector(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "vector.zip"
            write_backup(archive)
            result = explorer.build_evidence(archive)
            self.assertEqual(
                result["provenance"]["logicalContentSha256"],
                "319a1245fe7e21bcf6c0e45f2c9d58f798beca01bcd4c890bbd51da3cb1301e3",
            )
            self.assertEqual(
                result["provenance"]["sourceArchiveSha256"],
                hashlib.sha256(archive.read_bytes()).hexdigest(),
            )
            self.assertNotEqual(
                result["provenance"]["sourceArchiveSha256"],
                result["provenance"]["logicalContentSha256"],
            )
            self.assertNotIn("contact", result["contexts"]["observers"][0])

    def test_existing_output_is_never_overwritten(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "valid.zip"
            output = root / "output"
            output.mkdir()
            target = output / "evidence.json"
            target.write_bytes(b"keep")
            write_backup(archive)
            self.assertEqual(explorer.main([str(archive), str(output)]), 2)
            self.assertEqual(target.read_bytes(), b"keep")


class RendererTest(unittest.TestCase):
    def _write(self, directory: Path, rows: dict | None = None) -> tuple[Path, Path]:
        archive = directory / "render-input.zip"
        output = directory / "output"
        write_backup(archive, rows=rows if rows is not None else golden_rows())
        explorer.write_evidence(archive, output)
        return archive, output

    def test_all_five_artifacts_are_published_and_canonical_json_is_unchanged(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            _, output = self._write(Path(directory))
            self.assertEqual(
                sorted(path.name for path in output.iterdir()),
                ["bees.csv", "cycles.csv", "evidence.json", "evidence.md", "points.csv"],
            )
            self.assertEqual(
                (output / "evidence.json").read_bytes(),
                (TESTDATA / "golden_evidence_v1.json").read_bytes(),
            )

    def test_renderings_are_deterministic_across_builds_and_do_not_mutate_the_result(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first_archive = root / "one.zip"
            second_archive = root / "two.zip"
            write_backup(first_archive, rows=golden_rows())
            write_backup(second_archive, rows=golden_rows())
            first = explorer.build_evidence(first_archive)
            second = explorer.build_evidence(second_archive)
            snapshot = copy.deepcopy(first)
            for renderer in (
                explorer.render_markdown,
                explorer.render_points_csv,
                explorer.render_bees_csv,
                explorer.render_cycles_csv,
            ):
                with self.subTest(renderer=renderer.__name__):
                    self.assertEqual(renderer(first), renderer(first))
                    self.assertEqual(renderer(first), renderer(second))
            self.assertEqual(first, snapshot)

    def test_points_csv_carries_both_presence_results_and_empty_optional_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            _, output = self._write(Path(directory))
            rows = list(csv.reader((output / "points.csv").read_text(encoding="utf-8").splitlines()))
            header, body = rows[0], rows[1:]
            self.assertEqual(header, [
                "observationPointId", "territoryId", "observerId", "observationYear",
                "pointNumber", "beePresence", "latitude", "longitude", "gpsAccuracyM",
                "createdAt", "initialGroupReleaseAt", "completedAt", "beeCount",
                "flightCycleCount",
            ])
            self.assertEqual(len(body), 2)
            self.assertEqual([row[5] for row in body], ["NO_BEES_FOUND", "BEES_FOUND"])
            no_bees = body[0]
            self.assertEqual(no_bees[8], "")            # gpsAccuracyM is null
            self.assertEqual(no_bees[10], "")           # initialGroupReleaseAt is null
            self.assertEqual(no_bees[12:], ["0", "0"])
            bees_found = body[1]
            self.assertEqual(bees_found[10], "2000")
            self.assertEqual(bees_found[12:], ["2", "5"])

    def test_bees_csv_lists_exactly_the_stored_bees(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            _, output = self._write(Path(directory))
            rows = list(csv.reader((output / "bees.csv").read_text(encoding="utf-8").splitlines()))
            self.assertEqual(rows[0], [
                "beeId", "observationPointId", "markColor", "markPosition", "createdAt",
                "totalCycles", "completedCycles", "openCycles", "eligibleDurations",
                "excludedByD058",
            ])
            self.assertEqual(
                [(row[0], row[2], row[3]) for row in rows[1:]],
                [(uid(8), "blue", "RIGHT_WING"), (uid(4), "red", "LEFT_WING")],
            )
            self.assertEqual(rows[1][5:], ["1", "1", "0", "1", "0"])
            self.assertEqual(rows[2][5:], ["4", "3", "1", "2", "1"])

    def test_cycles_csv_covers_every_canonical_evidence_case(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            _, output = self._write(Path(directory))
            rows = list(csv.reader((output / "cycles.csv").read_text(encoding="utf-8").splitlines()))
            self.assertEqual(rows[0], [
                "flightCycleId", "beeId", "observationPointId", "sequenceNumber",
                "departureTime", "returnTime", "durationMs", "durationEvidenceStatus",
                "azimuthDeg", "azimuthCaptureConsumed", "initialGroupLaunch",
                "initialGroupLaunchCorrectionEligible", "diagnosticCodes", "createdAt",
                "updatedAt",
            ])
            cycles = {row[3] + ":" + row[1][-2:]: row for row in rows[1:]}
            self.assertEqual(len(cycles), 5)

            boundary = cycles["1:08"]
            self.assertEqual(boundary[6], "60000")
            self.assertEqual(boundary[7], "ELIGIBLE")
            self.assertEqual(boundary[8], "0.0")        # azimuth 0 is stored, not absent
            self.assertEqual(boundary[10], "true")      # initial group launch
            self.assertEqual(boundary[12], "")

            d058 = cycles["1:04"]
            self.assertEqual(d058[6], "59999")
            self.assertEqual(d058[7], "EXCLUDED_BY_D058")
            self.assertEqual(d058[8], "")               # azimuth is null, so empty field
            self.assertEqual(d058[12], "D058_APPLIED_TO_NON_GROUP_FIRST_CYCLE")

            self.assertEqual(cycles["2:04"][6], "60000")
            self.assertEqual(cycles["2:04"][7], "ELIGIBLE")
            self.assertEqual(cycles["2:04"][8], "45.5")
            self.assertEqual(cycles["2:04"][9], "true")  # azimuth capture consumed

            self.assertEqual(cycles["3:04"][6], "1")     # later short cycle stays eligible
            self.assertEqual(cycles["3:04"][7], "ELIGIBLE")

            opened = cycles["4:04"]
            self.assertEqual(opened[5], "")              # returnTime is null
            self.assertEqual(opened[6], "")              # no duration for an open cycle
            self.assertEqual(opened[7], "NO_DURATION_OPEN")

    def test_csv_headers_are_present_for_an_empty_dataset(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            rows = base_rows()
            for name in ("territories", "observers", "observation-points", "bees",
                         "flight-cycles", "map-coverage"):
                rows[name] = []
            rows["portable-settings"] = [{"currentTerritoryId": None, "currentObserverId": None}]
            _, output = self._write(Path(directory), rows=rows)
            for name, column_count in (("points.csv", 14), ("bees.csv", 10), ("cycles.csv", 15)):
                with self.subTest(artifact=name):
                    text = (output / name).read_text(encoding="utf-8")
                    self.assertEqual(text.count("\n"), 1)
                    self.assertEqual(len(text.splitlines()[0].split(",")), column_count)
            markdown = (output / "evidence.md").read_text(encoding="utf-8")
            self.assertIn("No ObservationPoint is recorded in this archive.", markdown)
            self.assertIn("| Observation points | 0 |", markdown)

    def test_renderings_contain_no_run_metadata_paths_or_time(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / "distinctive-archive-name.zip"
            output = root / "distinctive-output-directory"
            write_backup(archive, rows=golden_rows())
            explorer.write_evidence(archive, output)
            now_prefix = datetime.now(timezone.utc).strftime("%Y-%m-%dT")
            for name in ("evidence.md", "points.csv", "bees.csv", "cycles.csv"):
                text = (output / name).read_text(encoding="utf-8")
                with self.subTest(artifact=name):
                    self.assertNotIn(archive.name, text)
                    self.assertNotIn(str(root), text)
                    self.assertNotIn(str(output), text)
                    self.assertNotIn(socket.gethostname(), text)
                    self.assertNotIn(now_prefix, text)
                    self.assertNotIn("\\", text)

    def test_markdown_reports_exact_evidence_without_interpretation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            _, output = self._write(Path(directory))
            markdown = (output / "evidence.md").read_text(encoding="utf-8")
            self.assertTrue(markdown.startswith("# Analysis Evidence Explorer\n"))
            self.assertIn("| D058 | 1 | 60000 |", markdown)
            self.assertIn("00:00:59.999 (59999 ms)", markdown)
            self.assertIn("00:01:00.000 (60000 ms)", markdown)
            self.assertIn("00:00:00.001 (1 ms)", markdown)
            self.assertIn("(no duration)", markdown)
            self.assertIn("| OPEN |", markdown)
            self.assertIn("| 0.0 |", markdown)
            self.assertIn("D058_APPLIED_TO_NON_GROUP_FIRST_CYCLE", markdown)
            self.assertIn("not an exclusion area and not a statement about nest location", markdown)
            lowered = markdown.lower()
            for forbidden in (
                "probable nest", "belongs to nest", "distance to nest", "distance",
                "confidence", "probability", "bad observation", "erroneous cycle",
                "orientation flight", "mean", "median", "cluster", "annulus",
            ):
                with self.subTest(forbidden=forbidden):
                    self.assertNotIn(forbidden, lowered)

    def test_csv_quoting_survives_comma_quote_and_newline(self) -> None:
        awkward_mark = 'red,"quoted"\nsecond line'
        with tempfile.TemporaryDirectory() as directory:
            rows = base_rows()
            rows["bees"][0]["markColor"] = awkward_mark
            rows["observation-points"][0]["code"] = "code|with|pipes\nand newline"
            archive = Path(directory) / "awkward.zip"
            output = Path(directory) / "output"
            write_backup(archive, rows=rows)
            explorer.write_evidence(archive, output)

            bees_text = (output / "bees.csv").read_text(encoding="utf-8")
            self.assertIn('"red,""quoted""', bees_text)
            parsed = list(csv.reader(io.StringIO(bees_text, newline="")))
            self.assertEqual(parsed[1][2], awkward_mark)

            markdown = (output / "evidence.md").read_text(encoding="utf-8")
            self.assertIn("| Point code | code\\|with\\|pipes and newline |", markdown)


if __name__ == "__main__":
    unittest.main()
