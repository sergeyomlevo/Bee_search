# /// script
# requires-python = ">=3.11,<3.12"
# dependencies = ["osmium==4.3.1"]
# ///

"""Merge small OSM XML extracts into the PBF input expected by Planetiler."""

from __future__ import annotations

import argparse
from pathlib import Path

import osmium


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    parser.add_argument("inputs", nargs="+", type=Path)
    args = parser.parse_args()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    reader = osmium.MergeInputReader()
    for input_path in args.inputs:
        reader.add_file(str(input_path))

    with osmium.SimpleWriter(str(args.output), overwrite=True) as writer:
        reader.apply(writer, idx="flex_mem")


if __name__ == "__main__":
    main()
