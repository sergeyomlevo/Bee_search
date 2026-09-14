#!/usr/bin/env python3
"""Generate deterministic Bee Search evidence JSON from logical backup v1."""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

from backup_reader import BackupError, COLLECTIONS
from evidence import build_evidence

__all__ = ["BackupError", "COLLECTIONS", "build_evidence", "main", "write_evidence"]


def canonical_json_bytes(result: dict[str, Any]) -> bytes:
    text = json.dumps(
        result,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )
    return (text + "\n").encode("utf-8")


def write_evidence(archive: Path, output_directory: Path) -> Path:
    result = build_evidence(archive)
    target = output_directory / "evidence.json"
    temporary = output_directory / ".evidence.json.tmp"
    if target.exists() or temporary.exists():
        raise BackupError("output evidence file already exists")
    output_directory.mkdir(parents=True, exist_ok=True)
    try:
        with temporary.open("xb") as stream:
            stream.write(canonical_json_bytes(result))
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, target)
    finally:
        if temporary.exists():
            temporary.unlink()
    return target


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
