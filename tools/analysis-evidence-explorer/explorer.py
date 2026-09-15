#!/usr/bin/env python3
"""Generate deterministic Bee Search evidence reports from logical backup v1.

One ``build_evidence`` call produces a single immutable canonical result. Every
output artifact is rendered from that same result:

* ``evidence.json`` — the canonical machine-readable artifact;
* ``evidence.md`` — human-readable report;
* ``points.csv``, ``bees.csv``, ``cycles.csv`` — normalized tabular views.

The Markdown and CSV files are derived presentations. They add no evidence
semantics and never recompute eligibility or diagnostics.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

from backup_reader import BackupError, COLLECTIONS
from evidence import build_evidence
from render import (
    render_bees_csv,
    render_cycles_csv,
    render_markdown,
    render_points_csv,
)

__all__ = [
    "BackupError",
    "COLLECTIONS",
    "build_evidence",
    "canonical_json_bytes",
    "main",
    "render_artifacts",
    "render_bees_csv",
    "render_cycles_csv",
    "render_markdown",
    "render_points_csv",
    "write_evidence",
]

CANONICAL_ARTIFACT = "evidence.json"


def canonical_json_bytes(result: dict[str, Any]) -> bytes:
    text = json.dumps(
        result,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )
    return (text + "\n").encode("utf-8")


ARTIFACT_RENDERERS = (
    (CANONICAL_ARTIFACT, canonical_json_bytes),
    ("evidence.md", render_markdown),
    ("points.csv", render_points_csv),
    ("bees.csv", render_bees_csv),
    ("cycles.csv", render_cycles_csv),
)


def render_artifacts(result: dict[str, Any]) -> tuple[tuple[str, bytes], ...]:
    """Render every artifact in memory. Nothing is written to disk here."""
    return tuple((name, renderer(result)) for name, renderer in ARTIFACT_RENDERERS)


def write_evidence(archive: Path, output_directory: Path) -> Path:
    """Publish the complete output set, or nothing at all.

    Ordering guarantees that renderers and validation run before publication and
    that every artifact is fully written and flushed to a temporary file before
    the first artifact replaces its final name. No partially rendered set can
    therefore appear: a failure while building or rendering leaves the output
    directory untouched.
    """
    result = build_evidence(archive)
    artifacts = render_artifacts(result)
    targets = {name: output_directory / name for name, _ in artifacts}
    temporaries = {name: output_directory / f".{name}.tmp" for name, _ in artifacts}
    for path in (*targets.values(), *temporaries.values()):
        if path.exists():
            raise BackupError(f"output file already exists: {path.name}")
    output_directory.mkdir(parents=True, exist_ok=True)
    try:
        for name, payload in artifacts:
            with temporaries[name].open("xb") as stream:
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
        for name, _ in artifacts:
            os.replace(temporaries[name], targets[name])
    finally:
        for temporary in temporaries.values():
            if temporary.exists():
                temporary.unlink()
    return targets[CANONICAL_ARTIFACT]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("output_directory", type=Path)
    arguments = parser.parse_args(argv)
    try:
        write_evidence(arguments.archive, arguments.output_directory)
    except (BackupError, OSError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
