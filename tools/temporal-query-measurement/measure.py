#!/usr/bin/env python3
"""Reproducible SQLite measurements for temporal DAO query candidates.

This is a host-side benchmark only.  It creates fresh temporary SQLite files
from the exported Room schema and never opens an application/user database.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import math
import platform
import shutil
import sqlite3
import statistics
import re
import sys
import tempfile
import time
import uuid
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
SCHEMA_PATH = ROOT / "app/schemas/org.beesearch.app.data.local.room.BeeSearchDatabase/13.json"
DAO_PATH = ROOT / "app/src/main/java/org/beesearch/app/data/local/room/Daos.kt"
DEFAULT_OUTPUT = ROOT / "app/build/reports/temporal-i5/query-measurement.json"
DEFAULT_MARKDOWN = ROOT / "app/build/reports/temporal-i5/query-measurement.md"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--markdown", type=Path, default=DEFAULT_MARKDOWN)
    parser.add_argument("--points", type=int, default=50_000)
    parser.add_argument("--objects", type=int, default=50_000)
    parser.add_argument("--territories", type=int, default=10)
    parser.add_argument("--warmups", type=int, default=3)
    parser.add_argument("--repetitions", type=int, default=15)
    parser.add_argument("--seed", type=int, default=1309)
    parser.add_argument("--keep-workdir", action="store_true")
    return parser.parse_args()


def iso_day(day: dt.date) -> str:
    return day.isoformat()


def schema_sql() -> list[str]:
    raw = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    entities = raw["database"]["entities"]
    # The exported entity order is already dependency-safe.  Make the
    # substitution explicit because Room's ${TABLE_NAME} is not SQLite SQL.
    statements: list[str] = []
    for entity in entities:
        table = entity["tableName"]
        statements.append(entity["createSql"].replace("${TABLE_NAME}", table))
        statements.extend(
            index["createSql"].replace("${TABLE_NAME}", table)
            for index in entity.get("indices", [])
        )
    statements.append("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
    statements.append(
        "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '50622e9209b66df46e845b92ad979008')"
    )
    return statements


def connect(path: Path) -> sqlite3.Connection:
    db = sqlite3.connect(path)
    db.execute("PRAGMA foreign_keys=ON")
    db.execute("PRAGMA journal_mode=DELETE")
    db.execute("PRAGMA synchronous=NORMAL")
    for statement in schema_sql():
        db.execute(statement)
    return db


def make_rows(points: int, objects: int, territories: int, seed: int) -> dict[str, list[tuple[Any, ...]]]:
    # The fixture is deterministic and deliberately skewed: T000 owns 40% of
    # points/objects, while the remaining territories share the rest.
    assert territories >= 2
    start = dt.date(2017, 1, 1)
    span = 3653
    def uid(namespace: int, index: int) -> str:
        return str(uuid.UUID(int=namespace + index))

    territory_ids = [uid(0x10000000000000000000000000000000, i) for i in range(territories)]
    observer_ids = [uid(0x20000000000000000000000000000000, i) for i in range(2)]
    territories_rows = [
        (tid, f"T{i:03d}", f"Territory {i:03d}", "Region", "District", 1_600_000_000 + i, 1_600_000_100 + i)
        for i, tid in enumerate(territory_ids)
    ]
    observers_rows = [
        (oid, f"O{i:03d}", "Last", f"First{i}", None, None, 1_600_000_000, 1_600_000_100)
        for i, oid in enumerate(observer_ids)
    ]

    def territory_for(index: int, total: int) -> int:
        if index < int(total * 0.40):
            return 0
        return 1 + ((index - int(total * 0.40)) % (territories - 1))

    point_rows: list[tuple[Any, ...]] = []
    bee_rows: list[tuple[Any, ...]] = []
    cycle_rows: list[tuple[Any, ...]] = []
    for i in range(points):
        territory = territory_for(i, points)
        day = start + dt.timedelta(days=(i * 37 + seed) % span)
        point_id = uid(0x30000000000000000000000000000000, i)
        created = 1_500_000_000 + i * 97
        point_rows.append(
            (iso_day(day), point_id, territory_ids[territory], observer_ids[i % 2], day.year, i + 1,
             "BEES_FOUND", f"P{i:06d}", 55.0 + territory / 100.0, 37.0 + (i % 100) / 1000.0,
             None, None, None, created, None, None, None)
        )
        for bee_no in range(2):
            bee_id = uid(0x40000000000000000000000000000000, i * 2 + bee_no)
            bee_rows.append((bee_id, point_id, ("BLUE", "RED")[bee_no], ("THORAX", "ABDOMEN")[bee_no], created + bee_no, None))
            for cycle_no in range(3):
                cycle_id = uid(0x50000000000000000000000000000000, i * 6 + bee_no * 3 + cycle_no)
                departure = created + bee_no * 100 + cycle_no * 10_000
                cycle_rows.append((cycle_id, bee_id, cycle_no + 1, departure, departure + 3600,
                                   90.0 + cycle_no, 1, 0, 0, departure, departure + 3600))

    type_names = ("HOLLOW", "LOG_HIVE", "APIARY")
    object_rows: list[tuple[Any, ...]] = []
    apiary_rows: list[tuple[Any, ...]] = []
    hollow_rows: list[tuple[Any, ...]] = []
    log_hive_rows: list[tuple[Any, ...]] = []
    sequence: dict[tuple[int, str], int] = {}
    for i in range(objects):
        territory = territory_for(i, objects)
        object_type = type_names[i % len(type_names)]
        key = territory, object_type
        sequence[key] = sequence.get(key, 0) + 1
        day = start + dt.timedelta(days=(i * 19 + seed) % span)
        fixation = iso_day(day) if i % 5 else None
        oid = uid(0x60000000000000000000000000000000, i)
        if object_type == "APIARY":
            fixation = None
        object_rows.append((oid, territory_ids[territory], object_type, sequence[key],
                            55.0 + territory / 100.0, 37.0 + (i % 100) / 1000.0,
                            1_500_000_000 + i * 103, observer_ids[i % 2], fixation))
        if object_type == "APIARY":
            apiary_rows.append((oid, f"Apiary {i}"))
        elif object_type == "HOLLOW":
            hollow_rows.append((oid, "Oak", 120.0, 180, 40.0, 30.0, None, f"Hollow {i}"))
        else:
            log_hive_rows.append((oid, "Pine", 100.0, 90, 45.0, "Wood", 35.0, 60.0, None, f"Log hive {i}"))
    sequence_rows = [(territory_ids[t], typ, value) for (t, typ), value in sorted(sequence.items())]
    return {
        "territories": territories_rows,
        "observers": observers_rows,
        "observation_points": point_rows,
        "bees": bee_rows,
        "flight_cycles": cycle_rows,
        "physical_objects": object_rows,
        "apiaries": apiary_rows,
        "hollows": hollow_rows,
        "log_hives": log_hive_rows,
        "physical_object_sequences": sequence_rows,
    }


INSERTS: dict[str, str] = {
    "territories": "INSERT INTO territories VALUES (?, ?, ?, ?, ?, ?, ?)",
    "observers": "INSERT INTO observers VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
    "observation_points": "INSERT INTO observation_points VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    "bees": "INSERT INTO bees VALUES (?, ?, ?, ?, ?, ?)",
    "flight_cycles": "INSERT INTO flight_cycles VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    "physical_objects": "INSERT INTO physical_objects VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
    "apiaries": "INSERT INTO apiaries VALUES (?, ?)",
    "hollows": "INSERT INTO hollows VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
    "log_hives": "INSERT INTO log_hives VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
    "physical_object_sequences": "INSERT INTO physical_object_sequences VALUES (?, ?, ?)",
}


def load_rows(db: sqlite3.Connection, rows: dict[str, list[tuple[Any, ...]]]) -> None:
    # Parent and child order follows the Room foreign-key graph.
    order = ("territories", "observers", "observation_points", "physical_objects",
             "physical_object_sequences", "apiaries", "hollows", "log_hives", "bees", "flight_cycles")
    with db:
        for table in order:
            db.executemany(INSERTS[table], rows[table])


def validate_fixture(rows: dict[str, list[tuple[Any, ...]]]) -> None:
    """Fail before loading if the synthetic data leaves the domain contract."""
    uuid_columns = {
        "territories": (0,), "observers": (0,), "observation_points": (1, 2, 3),
        "bees": (0, 1), "flight_cycles": (0, 1), "physical_objects": (0, 1, 7),
        "apiaries": (0,), "hollows": (0,), "log_hives": (0,),
        "physical_object_sequences": (0,),
    }
    for table, columns in uuid_columns.items():
        for row in rows[table]:
            for column in columns:
                uuid.UUID(row[column])
    for row in rows["observation_points"]:
        if row[0][:4] != str(row[4]) or row[6] != "BEES_FOUND":
            raise ValueError("observation fixture violates date/year or bee-presence contract")
    for row in rows["bees"]:
        if row[2] not in {"BLUE", "RED"} or row[3] not in {"THORAX", "ABDOMEN"}:
            raise ValueError("bee fixture contains an unsupported mark enum")
    for row in rows["physical_objects"]:
        if row[2] == "APIARY" and row[8] is not None:
            raise ValueError("apiary fixture must have null fixation_date")


def query_params(case: dict[str, Any]) -> dict[str, Any]:
    params = {"territoryId": case["territory_id"]}
    if "observation_year" in case:
        params["observationYear"] = case["observation_year"]
    if "object_type" in case:
        params["objectType"] = case["object_type"]
    if "from_date" in case:
        params["fromDate"] = case["from_date"]
        params["toDate"] = case["to_date"]
    return params


def cases() -> list[dict[str, Any]]:
    t = str(uuid.UUID(int=0x10000000000000000000000000000000))
    return [
        {"name": "observeSummaries", "method": "observeSummaries", "kind": "observation", "territory_id": t, "observation_year": None, "date_predicate": ""},
        {"name": "observeSummaries_year", "method": "observeSummaries", "kind": "observation", "territory_id": t, "observation_year": 2022, "date_predicate": ""},
        {"name": "observeSummariesInDateInterval_day", "method": "observeSummariesInDateInterval", "kind": "observation", "territory_id": t, "observation_year": None, "date_predicate": "AND p.observation_date >= :fromDate AND p.observation_date <= :toDate", "from_date": "2022-06-15", "to_date": "2022-06-15"},
        {"name": "observeSummariesInDateInterval_month", "method": "observeSummariesInDateInterval", "kind": "observation", "territory_id": t, "observation_year": None, "date_predicate": "AND p.observation_date >= :fromDate AND p.observation_date <= :toDate", "from_date": "2022-06-01", "to_date": "2022-06-30"},
        {"name": "observeSummariesInDateInterval_year", "method": "observeSummariesInDateInterval", "kind": "observation", "territory_id": t, "observation_year": None, "date_predicate": "AND p.observation_date >= :fromDate AND p.observation_date <= :toDate", "from_date": "2022-01-01", "to_date": "2022-12-31"},
        {"name": "observeSummariesInDateInterval_wide", "method": "observeSummariesInDateInterval", "kind": "observation", "territory_id": t, "observation_year": None, "date_predicate": "AND p.observation_date >= :fromDate AND p.observation_date <= :toDate", "from_date": "2017-01-01", "to_date": "2026-12-31"},
        {"name": "getForTerritory", "method": "getForTerritory", "kind": "physical_original", "territory_id": t},
        {"name": "getForTerritoryByType", "method": "getForTerritoryByType", "kind": "physical_typed", "territory_id": t, "object_type": "HOLLOW"},
        {"name": "getForTerritoryByTypeInDateInterval_day", "method": "getForTerritoryByTypeInDateInterval", "kind": "physical_typed_date", "territory_id": t, "object_type": "HOLLOW", "from_date": "2022-06-15", "to_date": "2022-06-15"},
        {"name": "getForTerritoryByTypeInDateInterval_month", "method": "getForTerritoryByTypeInDateInterval", "kind": "physical_typed_date", "territory_id": t, "object_type": "HOLLOW", "from_date": "2022-06-01", "to_date": "2022-06-30"},
        {"name": "getForTerritoryByTypeInDateInterval_year", "method": "getForTerritoryByTypeInDateInterval", "kind": "physical_typed_date", "territory_id": t, "object_type": "HOLLOW", "from_date": "2022-01-01", "to_date": "2022-12-31"},
        {"name": "getForTerritoryByTypeInDateInterval_wide", "method": "getForTerritoryByTypeInDateInterval", "kind": "physical_typed_date", "territory_id": t, "object_type": "HOLLOW", "from_date": "2017-01-01", "to_date": "2026-12-31"},
    ]


def sql_for(case: dict[str, Any], dao_queries: dict[str, str]) -> str:
    return dao_queries[case["method"]]


def explain(db: sqlite3.Connection, sql: str, params: dict[str, Any]) -> list[str]:
    return [" | ".join(str(value) for value in row) for row in db.execute("EXPLAIN QUERY PLAN " + sql, params)]


def percentile(values: list[float], p: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, math.ceil(p * len(ordered)) - 1))
    return ordered[index]


def measure(db: sqlite3.Connection, sql: str, params: dict[str, Any], warmups: int, repetitions: int) -> dict[str, Any]:
    for _ in range(warmups):
        list(db.execute(sql, params))
    timings: list[float] = []
    row_count = 0
    for _ in range(repetitions):
        started = time.perf_counter_ns()
        rows = list(db.execute(sql, params))
        elapsed = (time.perf_counter_ns() - started) / 1_000_000
        timings.append(elapsed)
        row_count = len(rows)
    return {"rows": row_count, "median_ms": statistics.median(timings), "p95_ms": percentile(timings, 0.95), "samples_ms": timings}


def database_bytes(path: Path) -> dict[str, int]:
    db = sqlite3.connect(path)
    page_count = int(db.execute("PRAGMA page_count").fetchone()[0])
    page_size = int(db.execute("PRAGMA page_size").fetchone()[0])
    db.close()
    return {"page_count": page_count, "page_size": page_size, "bytes": page_count * page_size}


def add_candidate_indexes(db: sqlite3.Connection) -> float:
    started = time.perf_counter_ns()
    with db:
        db.execute("CREATE INDEX i5_observation_territory_date ON observation_points (territory_id, observation_date)")
        db.execute("CREATE INDEX i5_physical_territory_type_fixation ON physical_objects (territory_id, object_type, fixation_date)")
    return (time.perf_counter_ns() - started) / 1_000_000


def clone(source: sqlite3.Connection, path: Path) -> sqlite3.Connection:
    # SQLite backup replaces the destination database; opening an initialized
    # schema first makes the destination connection "in use" on some hosts.
    destination = sqlite3.connect(path)
    source.backup(destination)
    destination.execute("PRAGMA foreign_keys=ON")
    return destination


def write_once(db: sqlite3.Connection, count: int = 1000) -> float:
    # A controlled transaction with the same two indexed parent tables.  IDs
    # are outside the fixture range, so this never modifies benchmark rows.
    t = str(uuid.UUID(int=0x10000000000000000000000000000000))
    observer = str(uuid.UUID(int=0x20000000000000000000000000000000))
    obs = [("2024-01-01", str(uuid.UUID(int=0x70000000000000000000000000000000 + i)), t, observer, 2024, 90_000 + i, "BEES_FOUND", None, 55.0, 37.0, None, None, None, 9_000_000_000 + i, None, None, None) for i in range(count)]
    objs = [(str(uuid.UUID(int=0x80000000000000000000000000000000 + i)), t, "HOLLOW", 100_000 + i, 55.0, 37.0, 9_000_100_000 + i, observer, "2024-01-01") for i in range(count)]
    started = time.perf_counter_ns()
    with db:
        db.executemany(INSERTS["observation_points"], obs)
        db.executemany(INSERTS["physical_objects"], objs)
    return (time.perf_counter_ns() - started) / 1_000_000


def write_cost(source: sqlite3.Connection, repetitions: int = 15, warmups: int = 3, count: int = 1000) -> dict[str, Any]:
    samples: list[float] = []
    for _ in range(warmups + repetitions):
        path = Path(tempfile.mktemp(prefix="bee-write-", suffix=".sqlite"))
        cloned = clone(source, path)
        elapsed = write_once(cloned, count)
        cloned.close()
        path.unlink(missing_ok=True)
        if len(samples) >= repetitions:
            continue
        # The first warmups are intentionally excluded from samples.
        if _ >= warmups:
            samples.append(elapsed)
    return {"rows_per_table": count, "median_ms": statistics.median(samples), "p95_ms": percentile(samples, 0.95), "samples_ms": samples}


def source_hashes() -> dict[str, str]:
    result = {}
    for path in (SCHEMA_PATH, DAO_PATH, Path(__file__)):
        result[str(path.relative_to(ROOT))] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


DAO_METHODS = (
    "observeSummaries",
    "observeSummariesInDateInterval",
    "getForTerritory",
    "getForTerritoryByType",
    "getForTerritoryByTypeInDateInterval",
)


def extract_dao_queries() -> dict[str, str]:
    source = DAO_PATH.read_text(encoding="utf-8")
    pattern = re.compile(
        r'@Query\(\s*(?:"""(?P<triple>.*?)"""|"(?P<single>(?:\\.|[^\"])*)")\s*,?\s*\)\s*(?:suspend\s+)?fun\s+(?P<method>\w+)\b',
        re.DOTALL)
    queries: dict[str, str] = {}
    for match in pattern.finditer(source):
        method = match.group("method")
        if method not in DAO_METHODS:
            continue
        value = match.group("triple") or match.group("single")
        if match.group("single"):
            value = bytes(value, "utf-8").decode("unicode_escape")
        queries[method] = value.strip()
    missing = [method for method in DAO_METHODS if method not in queries]
    if missing:
        raise RuntimeError(f"could not extract @Query for {', '.join(missing)} from {DAO_PATH}")
    return queries


def without_order_by(sql: str) -> str:
    return re.sub(r"\s+ORDER BY\s+[^\n]+\s*$", "", sql, flags=re.IGNORECASE).strip()


def run(args: argparse.Namespace) -> dict[str, Any]:
    if args.points < 1 or args.objects < 1 or args.territories < 2:
        raise ValueError("points, objects must be positive and territories >= 2")
    fixture = make_rows(args.points, args.objects, args.territories, args.seed)
    validate_fixture(fixture)
    dao_queries = extract_dao_queries()
    workdir = Path(tempfile.mkdtemp(prefix="bee-search-temporal-i5-"))
    try:
        baseline_path = workdir / "baseline.sqlite"
        candidate_path = workdir / "candidate.sqlite"
        baseline = connect(baseline_path)
        load_rows(baseline, fixture)
        baseline.commit()
        candidate = clone(baseline, candidate_path)
        before_size = database_bytes(candidate_path)
        index_build_ms = add_candidate_indexes(candidate)
        candidate.commit()
        after_size = database_bytes(candidate_path)
        result: dict[str, Any] = {
            "metadata": {
                "generated_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
                "python": sys.version,
                "sqlite": sqlite3.sqlite_version,
                "platform": platform.platform(),
                "machine": platform.machine(),
                "seed": args.seed,
                "parameters": {"points": args.points, "objects": args.objects, "territories": args.territories, "warmups": args.warmups, "repetitions": args.repetitions},
                "schema": str(SCHEMA_PATH.relative_to(ROOT)),
                "source_hashes_sha256": source_hashes(),
                "workdir": str(workdir),
                "host_only_limitation": "SQLite/Python host timings are not Android/Room/device timings.",
                "dao_query_methods": list(dao_queries),
            },
            "distribution": {table: len(values) for table, values in fixture.items()},
            "index": {"names": ["i5_observation_territory_date", "i5_physical_territory_type_fixation"], "build_ms": index_build_ms, "size_before": before_size, "size_after": after_size, "size_delta_bytes": after_size["bytes"] - before_size["bytes"]},
            "cases": {},
        }
        territory_counts = {tid: int(baseline.execute("SELECT COUNT(*) FROM observation_points WHERE territory_id = ?", (tid,)).fetchone()[0]) for tid in (str(uuid.UUID(int=0x10000000000000000000000000000000 + i)) for i in range(args.territories))}
        result["distribution"]["observation_points_by_territory"] = territory_counts
        result["distribution"]["physical_null_fixation"] = int(baseline.execute("SELECT COUNT(*) FROM physical_objects WHERE fixation_date IS NULL").fetchone()[0])
        result["distribution"]["foreign_key_violations"] = [list(row) for row in baseline.execute("PRAGMA foreign_key_check")]
        result["distribution"]["observation_year_mismatches"] = int(baseline.execute("SELECT COUNT(*) FROM observation_points WHERE observation_year != CAST(substr(observation_date, 1, 4) AS INTEGER)").fetchone()[0])

        prepared: list[tuple[dict[str, Any], str, dict[str, Any], list[str], list[str]]] = []
        for case in cases():
            sql = sql_for(case, dao_queries)
            params = query_params(case)
            baseline_plan = explain(baseline, sql, params)
            candidate_plan_before_analyze = explain(candidate, sql, params)
            prepared.append((case, sql, params, baseline_plan, candidate_plan_before_analyze))
        baseline.execute("ANALYZE")
        candidate.execute("ANALYZE")
        for case, sql, params, baseline_plan, candidate_plan_before_analyze in prepared:
            baseline_plan_analyze = explain(baseline, sql, params)
            candidate_plan_analyze = explain(candidate, sql, params)
            result["cases"][case["name"]] = {
                "method": case["method"], "kind": case["kind"], "sql": sql, "params": params,
                "query_plan": {"baseline_before_analyze": baseline_plan, "candidate_before_analyze": candidate_plan_before_analyze, "baseline_after_analyze": baseline_plan_analyze, "candidate_after_analyze": candidate_plan_analyze},
                "baseline": measure(baseline, sql, params, args.warmups, args.repetitions),
                "candidate": measure(candidate, sql, params, args.warmups, args.repetitions),
            }
            if case["name"] in {"observeSummaries", "observeSummaries_year", "observeSummariesInDateInterval_year", "observeSummariesInDateInterval_wide", "getForTerritory", "getForTerritoryByType", "getForTerritoryByTypeInDateInterval_year", "getForTerritoryByTypeInDateInterval_wide"}:
                no_order_sql = without_order_by(sql)
                result["cases"][case["name"]]["no_order_by_diagnostic"] = {
                    "sql": no_order_sql,
                    "baseline": measure(baseline, no_order_sql, params, args.warmups, args.repetitions),
                    "candidate": measure(candidate, no_order_sql, params, args.warmups, args.repetitions),
                }

        result["write_cost"] = {"baseline": write_cost(baseline), "candidate": write_cost(candidate)}
        baseline.close()
        candidate.close()
        return result
    finally:
        if not args.keep_workdir:
            shutil.rmtree(workdir, ignore_errors=True)


def markdown(report: dict[str, Any]) -> str:
    metadata = report["metadata"]
    lines = ["# Temporal query measurement", "", "Host-side SQLite benchmark; timings are not Android/Room/device evidence.", "", f"- Python: `{metadata['python'].split()[0]}`; SQLite: `{metadata['sqlite']}`", f"- Fixture: `{metadata['parameters']}`", f"- Seed: `{metadata['seed']}`", f"- Candidate index build: `{report['index']['build_ms']:.3f} ms`; size delta: `{report['index']['size_delta_bytes']} bytes`", "", "| Case | Rows | Baseline median ms | Candidate median ms | Baseline p95 ms | Candidate p95 ms |", "|---|---:|---:|---:|---:|---:|"]
    for name, case in report["cases"].items():
        lines.append(f"| {name} | {case['baseline']['rows']} | {case['baseline']['median_ms']:.3f} | {case['candidate']['median_ms']:.3f} | {case['baseline']['p95_ms']:.3f} | {case['candidate']['p95_ms']:.3f} |")
    lines += ["", "Recommendation is intentionally left to the root agent after reviewing plans, timings, row counts, write cost, and host/device limitations.", ""]
    return "\n".join(lines)


def main() -> int:
    args = parse_args()
    report = run(args)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    args.markdown.parent.mkdir(parents=True, exist_ok=True)
    args.markdown.write_text(markdown(report), encoding="utf-8")
    print(f"Wrote {args.output}")
    print(f"Wrote {args.markdown}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
