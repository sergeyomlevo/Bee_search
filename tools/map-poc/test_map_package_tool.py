from __future__ import annotations

import json
import gzip
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


def write_area(path: Path, bounds: list[tool.Bounds]) -> None:
    path.write_text(
        json.dumps(
            {
                "formatVersion": 1,
                "areaId": "9cc6cc3a-b2f9-4c91-89df-e0d322987f77",
                "name": "Beta Test Territory",
                "bounds": [vars(item) for item in bounds],
            }
        ),
        encoding="utf-8",
    )


def serialize_directory(entries: list[tool.DirectoryEntry]) -> bytes:
    payload = bytearray(varint(len(entries)))
    last_id = 0
    for entry in entries:
        payload.extend(varint(entry.tile_id - last_id))
        last_id = entry.tile_id
    for entry in entries:
        payload.extend(varint(entry.run_length))
    for entry in entries:
        payload.extend(varint(entry.length))
    next_byte = 0
    for index, entry in enumerate(entries):
        payload.extend(varint(0 if index > 0 and entry.offset == next_byte else entry.offset + 1))
        next_byte = entry.offset + entry.length
    return gzip.compress(bytes(payload))


def cell_tiles(bounds: tool.Bounds, cell_grid: int = 5, zoom: int = 12) -> set[tuple[int, int, int]]:
    result = set()
    for row in range(cell_grid):
        latitude = bounds.north - (bounds.north - bounds.south) * (row + 0.5) / cell_grid
        for column in range(cell_grid):
            longitude = bounds.west + (bounds.east - bounds.west) * (column + 0.5) / cell_grid
            result.add((zoom, tool.lon_to_tile_x(longitude, zoom), tool.lat_to_tile_y(latitude, zoom)))
    return result


def write_indexed_pmtiles(path: Path, bounds: tool.Bounds, tiles: set[tuple[int, int, int]]) -> None:
    tile_ids = sorted(tool.zxy_to_tile_id(*tile) for tile in tiles)
    entries = [tool.DirectoryEntry(tile_id, index, 1, 1) for index, tile_id in enumerate(tile_ids)]
    directory = serialize_directory(entries)
    metadata = gzip.compress(b"{}")
    tile_data = b"x" * len(entries)
    header = bytearray(tool.PMTILES_HEADER_BYTES)
    header[:7] = b"PMTiles"
    header[7] = 3
    root_offset = tool.PMTILES_HEADER_BYTES
    metadata_offset = root_offset + len(directory)
    leaf_offset = metadata_offset + len(metadata)
    tile_offset = leaf_offset
    for offset, value in (
        (8, root_offset), (16, len(directory)), (24, metadata_offset), (32, len(metadata)),
        (40, leaf_offset), (48, 0), (56, tile_offset), (64, len(tile_data)),
        (72, len(entries)), (80, len(entries)), (88, len(entries)),
    ):
        struct.pack_into("<Q", header, offset, value)
    header[96] = 1
    header[97] = 2
    header[98] = 2
    header[99] = 1
    header[100] = 8
    header[101] = 15
    for offset, value in ((102, bounds.west), (106, bounds.south), (110, bounds.east), (114, bounds.north)):
        struct.pack_into("<i", header, offset, round(value * 10_000_000))
    path.write_bytes(header + directory + metadata + tile_data)


class MapPackageToolTest(unittest.TestCase):
    def test_package_id_accepts_canonical_unicode_and_space_stems(self) -> None:
        for package_id in (
            "Лух--7e82a310--map-v1",
            "Beta Test Territory--9cc6cc3a--map-v1",
            "territory-benchmark-v1",
        ):
            self.assertEqual(package_id, tool.validate_package_id(package_id))

    def test_package_id_rejects_path_like_or_unsafe_stems(self) -> None:
        for package_id in (
            "",
            ".",
            "..",
            "../escape",
            "folder\\escape",
            "bad:name",
            "CON",
            "nul.txt",
            "COM1.map",
            "COM¹.map",
            "bad\nname",
            "bad\u0085name",
            " leading",
            "-leading",
            "trailing ",
            "trailing.",
        ):
            with self.subTest(package_id=package_id), self.assertRaises(tool.ContractError):
                tool.validate_package_id(package_id)

    def test_package_id_rejects_stems_over_established_length_limit(self) -> None:
        with self.assertRaises(tool.ContractError):
            tool.validate_package_id("я" * 97)

    def test_area_json_preserves_every_bounds_and_derives_generation_extent(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            area = Path(directory) / "area.json"
            requested = [
                tool.Bounds(42.3008374, 55.8953709, 43.1391514, 56.5712028),
                tool.Bounds(42.003214, 55.8927276, 42.6968315, 56.5750692),
            ]
            write_area(area, requested)

            _, parsed = tool.read_area_bounds(area)

            self.assertEqual(requested, parsed)
            self.assertEqual(tool.Bounds(42.003214, 55.8927276, 43.1391514, 56.5750692), tool.outer_bounds(parsed))

    def test_source_coverage_validation_checks_each_area_bounds(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            east_polygon = Path(directory) / "east.poly"
            east_polygon.write_text(
                "east\n1\n 42.6 55.8\n 43.2 55.8\n 43.2 56.7\n 42.6 56.7\n 42.6 55.8\nEND\nEND\n",
                encoding="utf-8",
            )
            west_polygon = Path(directory) / "west.poly"
            west_polygon.write_text(
                "west\n1\n 42.0 55.8\n 42.6 55.8\n 42.6 56.7\n 42.0 56.7\n 42.0 55.8\nEND\nEND\n",
                encoding="utf-8",
            )
            requested = [
                tool.Bounds(42.7, 55.9, 43.1, 56.5),
                tool.Bounds(42.05, 55.9, 42.5, 56.5),
            ]

            results = tool.validate_source_coverage(requested, [east_polygon, west_polygon], grid_size=11)

            self.assertEqual([True, True], [result["covered"] for result in results])

            with self.assertRaisesRegex(tool.ContractError, r"bounds\[1\]"):
                tool.validate_source_coverage(requested, [east_polygon], grid_size=11)

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

    def test_manifest_preserves_multiple_area_coverage_fragments(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "multi-area.pmtiles"
            extent = tool.Bounds(42.0, 55.8, 43.2, 56.7)
            requested = [
                tool.Bounds(42.3, 55.9, 43.1, 56.5),
                tool.Bounds(42.05, 55.9, 42.6, 56.5),
            ]
            write_pmtiles(artifact, extent)

            manifest, _ = tool.manifest_for(artifact, "multi-area", "source-v1", requested)
            validated = tool.validate_manifest(manifest, artifact)

            self.assertEqual([vars(item) for item in requested], manifest["coverageFragments"])
            self.assertEqual(2, len(validated["coverageMetrics"]))

    def test_artifact_area_validation_rejects_partial_spatial_tile_data(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "partial.pmtiles"
            full_extent = tool.Bounds(42.0, 55.8, 43.2, 56.7)
            east = tool.Bounds(42.3, 55.9, 43.1, 56.5)
            west = tool.Bounds(42.05, 55.9, 42.6, 56.5)
            write_indexed_pmtiles(artifact, full_extent, cell_tiles(east))

            east_result = tool.validate_artifact_area(artifact, [east])
            self.assertTrue(east_result["bounds"][0]["covered"])

            with self.assertRaisesRegex(tool.ContractError, r"bounds\[0\]"):
                tool.validate_artifact_area(artifact, [west])

    def test_artifact_area_validation_accepts_tile_data_across_every_bounds(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            artifact = Path(directory) / "complete.pmtiles"
            full_extent = tool.Bounds(42.0, 55.8, 43.2, 56.7)
            requested = [
                tool.Bounds(42.3, 55.9, 43.1, 56.5),
                tool.Bounds(42.05, 55.9, 42.6, 56.5),
            ]
            write_indexed_pmtiles(artifact, full_extent, cell_tiles(requested[0]) | cell_tiles(requested[1]))

            result = tool.validate_artifact_area(artifact, requested)

            self.assertEqual([True, True], [item["covered"] for item in result["bounds"]])

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
