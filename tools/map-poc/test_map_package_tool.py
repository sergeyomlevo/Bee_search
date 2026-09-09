from __future__ import annotations

import json
import struct
import tempfile
import unittest
import zlib
from pathlib import Path

import map_package_tool as tool


def varint(value: int) -> bytes:
    result = bytearray()
    while value >= 0x80:
        result.append((value & 0x7F) | 0x80)
        value >>= 7
    result.append(value)
    return bytes(result)


def field(number: int, wire_type: int, value: bytes | int) -> bytes:
    key = varint((number << 3) | wire_type)
    if wire_type == 0:
        return key + varint(int(value))
    payload = bytes(value)
    return key + varint(len(payload)) + payload


def zigzag(value: int) -> int:
    return (value << 1) ^ (value >> 63)


def write_source(path: Path, bounds: tool.Bounds | None) -> None:
    bbox_field = b""
    if bounds is not None:
        bbox = b"".join(
            (
                field(1, 0, zigzag(round(bounds.west * 1_000_000_000))),
                field(2, 0, zigzag(round(bounds.east * 1_000_000_000))),
                field(3, 0, zigzag(round(bounds.north * 1_000_000_000))),
                field(4, 0, zigzag(round(bounds.south * 1_000_000_000))),
            )
        )
        bbox_field = field(1, 2, bbox)
    header_block = bbox_field + field(4, 2, b"OsmSchema-V0.6") + field(16, 2, b"test")
    blob = field(2, 0, len(header_block)) + field(3, 2, zlib.compress(header_block))
    blob_header = field(1, 2, b"OSMHeader") + field(3, 0, len(blob))
    path.write_bytes(struct.pack(">I", len(blob_header)) + blob_header + blob)


def write_pmtiles(path: Path, bounds: tool.Bounds) -> None:
    header = bytearray(tool.PMTILES_HEADER_BYTES)
    header[:7] = b"PMTiles"
    header[7] = 3
    struct.pack_into("<Q", header, 72, 120)
    struct.pack_into("<Q", header, 80, 118)
    struct.pack_into("<Q", header, 88, 115)
    header[96] = 1
    header[97] = 2
    header[98] = 2
    header[99] = 1
    header[100] = 8
    header[101] = 15
    for offset, value in (
        (102, bounds.west),
        (106, bounds.south),
        (110, bounds.east),
        (114, bounds.north),
    ):
        struct.pack_into("<i", header, offset, round(value * 10_000_000))
    path.write_bytes(header + b"tile-data")


class MapPackageToolTest(unittest.TestCase):
    def test_source_header_and_selected_metrics(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "source.osm.pbf"
            expected = tool.Bounds(west=41.0, south=55.0, east=44.0, north=58.0)
            write_source(source, expected)

            info = tool.read_osm_pbf_header(source)

            self.assertEqual(expected, tool.Bounds(**info["bounds"]))
            self.assertEqual("test", info["generator"])
            self.assertTrue(expected.contains(tool.Bounds(42.0, 56.0, 43.0, 57.0)))

    def test_manifest_is_derived_from_real_pmtiles_header_and_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "large-field-v1.pmtiles"
            manifest_path = Path(f"{artifact}.manifest.json")
            coverage = tool.Bounds(west=42.2, south=56.1, east=42.8, north=56.5)
            write_pmtiles(artifact, coverage)

            manifest, info = tool.manifest_for(artifact, "large-field-v1", "source-20260830", coverage)
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            validated = tool.validate_manifest(json.loads(manifest_path.read_text()), artifact)

            self.assertEqual(artifact.stat().st_size, manifest["pmtilesByteLength"])
            self.assertEqual(tool.sha256(artifact), manifest["pmtilesSha256"])
            self.assertEqual(120, info["addressedTiles"])
            self.assertEqual(8, validated["artifact"]["minZoom"])
            self.assertEqual(15, validated["artifact"]["maxZoom"])

    def test_merged_source_without_header_bbox_remains_inspectable(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "merged.osm.pbf"
            write_source(source, None)

            info = tool.read_osm_pbf_header(source)

            self.assertIsNone(info["bounds"])
            self.assertEqual("test", info["generator"])

    def test_manifest_rejects_hash_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "large-field-v1.pmtiles"
            coverage = tool.Bounds(west=42.2, south=56.1, east=42.8, north=56.5)
            write_pmtiles(artifact, coverage)
            manifest, _ = tool.manifest_for(artifact, "large-field-v1", "source-20260830", coverage)
            manifest["pmtilesSha256"] = "0" * 64

            with self.assertRaisesRegex(tool.ContractError, "SHA-256"):
                tool.validate_manifest(manifest, artifact)


if __name__ == "__main__":
    unittest.main()
