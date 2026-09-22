#!/usr/bin/env python3
"""Inspect OSM PBF/PMTiles artifacts and create the Bee Search D065 sidecar."""

from __future__ import annotations

import argparse
import bisect
import gzip
import hashlib
import json
import math
import struct
import sys
import zlib
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any


PROFILE_ID = "bee-search-field"
PROFILE_VERSION = "v1"
STYLE_VERSION = "vector-pmtiles-v1"
PMTILES_HEADER_BYTES = 127
COORDINATE_EPSILON = 1e-7


class ContractError(ValueError):
    pass


@dataclass(frozen=True)
class Bounds:
    west: float
    south: float
    east: float
    north: float

    def validate(self) -> None:
        values = (self.west, self.south, self.east, self.north)
        if not all(math.isfinite(value) for value in values):
            raise ContractError("BBOX coordinates must be finite")
        if not (-180 <= self.west < self.east <= 180):
            raise ContractError("BBOX must satisfy -180 <= west < east <= 180")
        if not (-90 <= self.south < self.north <= 90):
            raise ContractError("BBOX must satisfy -90 <= south < north <= 90")

    def contains(self, other: "Bounds", epsilon: float = 0.0) -> bool:
        return (
            other.west >= self.west - epsilon
            and other.south >= self.south - epsilon
            and other.east <= self.east + epsilon
            and other.north <= self.north + epsilon
        )


@dataclass(frozen=True)
class DirectoryEntry:
    tile_id: int
    offset: int
    length: int
    run_length: int


def read_area_bounds(path: Path) -> tuple[dict[str, Any], list[Bounds]]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or value.get("formatVersion") != 1:
        raise ContractError("Area JSON formatVersion must be 1")
    raw_bounds = value.get("bounds")
    if not isinstance(raw_bounds, list) or not raw_bounds:
        raise ContractError("Area JSON bounds must be a non-empty array")
    bounds: list[Bounds] = []
    for index, raw in enumerate(raw_bounds):
        if not isinstance(raw, dict) or set(raw) != {"west", "south", "east", "north"}:
            raise ContractError(f"Area JSON bounds[{index}] is invalid")
        try:
            parsed = Bounds(**raw)
            parsed.validate()
        except (TypeError, ValueError) as error:
            raise ContractError(f"Area JSON bounds[{index}] is invalid") from error
        bounds.append(parsed)
    return value, bounds


def outer_bounds(bounds: list[Bounds]) -> Bounds:
    return Bounds(
        west=min(item.west for item in bounds),
        south=min(item.south for item in bounds),
        east=max(item.east for item in bounds),
        north=max(item.north for item in bounds),
    )


def haversine_km(lat_a: float, lon_a: float, lat_b: float, lon_b: float) -> float:
    lat_delta = math.radians(lat_b - lat_a)
    lon_delta = math.radians(lon_b - lon_a)
    lat_a_rad = math.radians(lat_a)
    lat_b_rad = math.radians(lat_b)
    haversine = (
        math.sin(lat_delta / 2) ** 2
        + math.cos(lat_a_rad) * math.cos(lat_b_rad) * math.sin(lon_delta / 2) ** 2
    )
    return 6371.0088 * 2 * math.asin(math.sqrt(haversine))


def bounds_metrics(bounds: Bounds) -> dict[str, float]:
    center_lat = (bounds.north + bounds.south) / 2
    center_lon = (bounds.east + bounds.west) / 2
    width = haversine_km(center_lat, bounds.west, center_lat, bounds.east)
    height = haversine_km(bounds.south, center_lon, bounds.north, center_lon)
    return {"widthKm": width, "heightKm": height, "areaKm2": width * height}


def read_varint(data: bytes, offset: int) -> tuple[int, int]:
    value = 0
    shift = 0
    while offset < len(data) and shift < 70:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, offset
        shift += 7
    raise ContractError("Invalid varint")


def protobuf_fields(data: bytes) -> list[tuple[int, int, Any]]:
    fields: list[tuple[int, int, Any]] = []
    offset = 0
    while offset < len(data):
        key, offset = read_varint(data, offset)
        field_number = key >> 3
        wire_type = key & 7
        if wire_type == 0:
            value, offset = read_varint(data, offset)
        elif wire_type == 1:
            if offset + 8 > len(data):
                raise ContractError("Truncated fixed64 protobuf field")
            value = data[offset : offset + 8]
            offset += 8
        elif wire_type == 2:
            length, offset = read_varint(data, offset)
            end = offset + length
            if end > len(data):
                raise ContractError("Truncated length-delimited protobuf field")
            value = data[offset:end]
            offset = end
        elif wire_type == 5:
            if offset + 4 > len(data):
                raise ContractError("Truncated fixed32 protobuf field")
            value = data[offset : offset + 4]
            offset += 4
        else:
            raise ContractError(f"Unsupported protobuf wire type {wire_type}")
        fields.append((field_number, wire_type, value))
    return fields


def zigzag_decode(value: int) -> int:
    return (value >> 1) ^ -(value & 1)


def first_field(fields: list[tuple[int, int, Any]], number: int) -> Any | None:
    return next((value for field, _, value in fields if field == number), None)


def read_osm_pbf_header(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise ContractError(f"OSM PBF does not exist: {path}")
    with path.open("rb") as stream:
        raw_size = stream.read(4)
        if len(raw_size) != 4:
            raise ContractError("OSM PBF is missing its first blob header")
        blob_header_size = struct.unpack(">I", raw_size)[0]
        if blob_header_size <= 0 or blob_header_size > 64 * 1024:
            raise ContractError("OSM PBF has an invalid blob header size")
        blob_header = stream.read(blob_header_size)
        header_fields = protobuf_fields(blob_header)
        blob_type = first_field(header_fields, 1)
        blob_size = first_field(header_fields, 3)
        if blob_type != b"OSMHeader" or not isinstance(blob_size, int):
            raise ContractError("OSM PBF first blob is not OSMHeader")
        blob = stream.read(blob_size)
        blob_fields = protobuf_fields(blob)
        raw = first_field(blob_fields, 1)
        if raw is None:
            zlib_data = first_field(blob_fields, 3)
            lz4_data = first_field(blob_fields, 6)
            zstd_data = first_field(blob_fields, 7)
            if zlib_data is not None:
                raw = zlib.decompress(zlib_data)
            elif lz4_data is not None or zstd_data is not None:
                raise ContractError("OSM PBF header compression is unsupported by the local inspector")
            else:
                raise ContractError("OSM PBF header has no readable payload")
        header_block_fields = protobuf_fields(raw)
        bbox_raw = first_field(header_block_fields, 1)
        bounds = None
        if isinstance(bbox_raw, bytes):
            bbox_fields = protobuf_fields(bbox_raw)
            left = first_field(bbox_fields, 1)
            right = first_field(bbox_fields, 2)
            top = first_field(bbox_fields, 3)
            bottom = first_field(bbox_fields, 4)
            if not all(isinstance(value, int) for value in (left, right, top, bottom)):
                raise ContractError("OSM PBF header bbox is incomplete")
            parsed_bounds = Bounds(
                west=zigzag_decode(left) / 1_000_000_000,
                south=zigzag_decode(bottom) / 1_000_000_000,
                east=zigzag_decode(right) / 1_000_000_000,
                north=zigzag_decode(top) / 1_000_000_000,
            )
            parsed_bounds.validate()
            bounds = asdict(parsed_bounds)
        required_features = [
            value.decode("utf-8", errors="replace")
            for field, wire, value in header_block_fields
            if field == 4 and wire == 2
        ]
        generator_raw = first_field(header_block_fields, 16)
        generator = generator_raw.decode("utf-8", errors="replace") if isinstance(generator_raw, bytes) else None
        return {
            "path": str(path.resolve()),
            "byteLength": path.stat().st_size,
            "sha256": sha256(path),
            "bounds": bounds,
            "requiredFeatures": required_features,
            "generator": generator,
        }


def little_u64(data: bytes, offset: int) -> int:
    return struct.unpack_from("<Q", data, offset)[0]


def little_i32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<i", data, offset)[0]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_pmtiles(path: Path, include_sha: bool = True) -> dict[str, Any]:
    if not path.is_file():
        raise ContractError(f"PMTiles file does not exist: {path}")
    with path.open("rb") as stream:
        header = stream.read(PMTILES_HEADER_BYTES)
    if len(header) != PMTILES_HEADER_BYTES or header[:7] != b"PMTiles":
        raise ContractError("Artifact is not a readable PMTiles archive")
    version = header[7]
    if version != 3:
        raise ContractError(f"Unsupported PMTiles version: {version}")
    min_zoom = header[100]
    max_zoom = header[101]
    if min_zoom > max_zoom or max_zoom > 30:
        raise ContractError("PMTiles header has an invalid zoom range")
    bounds = Bounds(
        west=little_i32(header, 102) / 10_000_000,
        south=little_i32(header, 106) / 10_000_000,
        east=little_i32(header, 110) / 10_000_000,
        north=little_i32(header, 114) / 10_000_000,
    )
    bounds.validate()
    result: dict[str, Any] = {
        "path": str(path.resolve()),
        "byteLength": path.stat().st_size,
        "pmtilesVersion": version,
        "minZoom": min_zoom,
        "maxZoom": max_zoom,
        "bounds": asdict(bounds),
        "addressedTiles": little_u64(header, 72),
        "tileEntries": little_u64(header, 80),
        "tileContents": little_u64(header, 88),
        "clustered": bool(header[96]),
        "internalCompression": header[97],
        "tileCompression": header[98],
        "tileType": header[99],
    }
    if include_sha:
        result["sha256"] = sha256(path)
    return result


def read_source_polygon(path: Path) -> list[list[tuple[float, float]]]:
    lines = [line.strip() for line in path.read_text(encoding="utf-8").splitlines()]
    if len(lines) < 4:
        raise ContractError(f"Source coverage polygon is invalid: {path}")
    rings: list[list[tuple[float, float]]] = []
    index = 1
    while index < len(lines):
        section = lines[index]
        index += 1
        if section == "END":
            break
        if section.startswith("!"):
            raise ContractError(f"Source coverage polygon holes are unsupported: {path}")
        ring: list[tuple[float, float]] = []
        while index < len(lines) and lines[index] != "END":
            parts = lines[index].split()
            if len(parts) != 2:
                raise ContractError(f"Source coverage polygon coordinate is invalid: {path}")
            ring.append((float(parts[0]), float(parts[1])))
            index += 1
        if index >= len(lines) or len(ring) < 3:
            raise ContractError(f"Source coverage polygon ring is invalid: {path}")
        index += 1
        rings.append(ring)
    if not rings:
        raise ContractError(f"Source coverage polygon has no rings: {path}")
    return rings


def point_on_segment(point: tuple[float, float], start: tuple[float, float], end: tuple[float, float]) -> bool:
    x, y = point
    ax, ay = start
    bx, by = end
    cross = (x - ax) * (by - ay) - (y - ay) * (bx - ax)
    if abs(cross) > 1e-10:
        return False
    return min(ax, bx) - 1e-10 <= x <= max(ax, bx) + 1e-10 and min(ay, by) - 1e-10 <= y <= max(ay, by) + 1e-10


def point_in_ring(point: tuple[float, float], ring: list[tuple[float, float]]) -> bool:
    x, y = point
    inside = False
    previous = ring[-1]
    for current in ring:
        if point_on_segment(point, previous, current):
            return True
        ax, ay = previous
        bx, by = current
        if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
            inside = not inside
        previous = current
    return inside


def validate_source_coverage(
    requested: list[Bounds],
    polygon_paths: list[Path],
    grid_size: int = 301,
) -> list[dict[str, Any]]:
    if grid_size < 3:
        raise ContractError("Source coverage grid size must be at least 3")
    rings = [ring for path in polygon_paths for ring in read_source_polygon(path)]
    results: list[dict[str, Any]] = []
    for index, bounds in enumerate(requested):
        uncovered: tuple[float, float] | None = None
        for y_index in range(grid_size):
            latitude = bounds.south + (bounds.north - bounds.south) * y_index / (grid_size - 1)
            for x_index in range(grid_size):
                longitude = bounds.west + (bounds.east - bounds.west) * x_index / (grid_size - 1)
                if not any(point_in_ring((longitude, latitude), ring) for ring in rings):
                    uncovered = (longitude, latitude)
                    break
            if uncovered is not None:
                break
        result = {
            "index": index,
            "bounds": asdict(bounds),
            "covered": uncovered is None,
            "gridSize": grid_size,
            "firstUncoveredProbe": None if uncovered is None else {"longitude": uncovered[0], "latitude": uncovered[1]},
        }
        results.append(result)
        if uncovered is not None:
            raise ContractError(
                f"Source coverage does not contain Area bounds[{index}]; "
                f"first uncovered probe={uncovered[0]:.7f},{uncovered[1]:.7f}"
            )
    return results


def decompress_directory(data: bytes, compression: int) -> bytes:
    if compression == 1:
        return data
    if compression == 2:
        return gzip.decompress(data)
    raise ContractError(f"Unsupported PMTiles internal compression for spatial validation: {compression}")


def decode_directory(data: bytes, compression: int) -> list[DirectoryEntry]:
    payload = decompress_directory(data, compression)
    count, offset = read_varint(payload, 0)
    if count <= 0:
        raise ContractError("PMTiles directory must not be empty")
    entries = [DirectoryEntry(0, 0, 0, 0) for _ in range(count)]
    last_id = 0
    for index in range(count):
        delta, offset = read_varint(payload, offset)
        last_id += delta
        entries[index] = DirectoryEntry(last_id, 0, 0, 0)
    for index in range(count):
        run_length, offset = read_varint(payload, offset)
        entries[index] = DirectoryEntry(entries[index].tile_id, 0, 0, run_length)
    for index in range(count):
        length, offset = read_varint(payload, offset)
        if length <= 0:
            raise ContractError("PMTiles directory entry length must be positive")
        entries[index] = DirectoryEntry(entries[index].tile_id, 0, length, entries[index].run_length)
    next_byte = 0
    for index in range(count):
        encoded_offset, offset = read_varint(payload, offset)
        entry_offset = next_byte if index > 0 and encoded_offset == 0 else encoded_offset - 1
        if entry_offset < 0:
            raise ContractError("PMTiles directory entry offset is invalid")
        entries[index] = DirectoryEntry(
            entries[index].tile_id,
            entry_offset,
            entries[index].length,
            entries[index].run_length,
        )
        next_byte = entry_offset + entries[index].length
    if offset != len(payload):
        raise ContractError("PMTiles directory has trailing bytes")
    return entries


def pmtiles_tile_ranges(path: Path) -> list[tuple[int, int]]:
    with path.open("rb") as stream:
        header = stream.read(PMTILES_HEADER_BYTES)
        if len(header) != PMTILES_HEADER_BYTES or header[:7] != b"PMTiles" or header[7] != 3:
            raise ContractError("Artifact is not a readable PMTiles v3 archive")
        root_offset = little_u64(header, 8)
        root_length = little_u64(header, 16)
        leaf_offset = little_u64(header, 40)
        internal_compression = header[97]

        def read_entries(file_offset: int, length: int, depth: int) -> list[DirectoryEntry]:
            if depth > 3:
                raise ContractError("PMTiles directory nesting is too deep")
            stream.seek(file_offset)
            data = stream.read(length)
            if len(data) != length:
                raise ContractError("PMTiles directory is truncated")
            return decode_directory(data, internal_compression)

        ranges: list[tuple[int, int]] = []
        pending = [(root_offset, root_length, 0)]
        while pending:
            directory_offset, directory_length, depth = pending.pop()
            for entry in read_entries(directory_offset, directory_length, depth):
                if entry.run_length > 0:
                    ranges.append((entry.tile_id, entry.tile_id + entry.run_length - 1))
                else:
                    pending.append((leaf_offset + entry.offset, entry.length, depth + 1))
    ranges.sort()
    return ranges


def zxy_to_tile_id(z: int, x: int, y: int) -> int:
    if z < 0 or z > 31 or x < 0 or y < 0 or x >= 1 << z or y >= 1 << z:
        raise ContractError("Tile coordinate is outside the PMTiles range")
    tile_id = ((1 << (z * 2)) - 1) // 3
    for bit in range(z - 1, -1, -1):
        scale = 1 << bit
        rx = scale & x
        ry = scale & y
        tile_id += ((3 * rx) ^ ry) << bit
        if ry == 0:
            if rx != 0:
                x = scale - 1 - x
                y = scale - 1 - y
            x, y = y, x
    return tile_id


def lon_to_tile_x(longitude: float, zoom: int) -> int:
    count = 1 << zoom
    return min(count - 1, max(0, int((longitude + 180.0) / 360.0 * count)))


def lat_to_tile_y(latitude: float, zoom: int) -> int:
    latitude = min(85.05112878, max(-85.05112878, latitude))
    count = 1 << zoom
    value = (1 - math.asinh(math.tan(math.radians(latitude))) / math.pi) / 2 * count
    return min(count - 1, max(0, int(value)))


def tile_range_contains(ranges: list[tuple[int, int]], starts: list[int], tile_id: int) -> bool:
    index = bisect.bisect_right(starts, tile_id) - 1
    return index >= 0 and tile_id <= ranges[index][1]


def validate_artifact_area(
    artifact: Path,
    requested: list[Bounds],
    cell_grid: int = 5,
) -> dict[str, Any]:
    if cell_grid < 2:
        raise ContractError("Artifact coverage cell grid must be at least 2")
    info = read_pmtiles(artifact, include_sha=False)
    zoom = min(info["maxZoom"], 12)
    ranges = pmtiles_tile_ranges(artifact)
    starts = [item[0] for item in ranges]
    results: list[dict[str, Any]] = []
    for index, bounds in enumerate(requested):
        empty_cells: list[dict[str, int]] = []
        for row in range(cell_grid):
            cell_north = bounds.north - (bounds.north - bounds.south) * row / cell_grid
            cell_south = bounds.north - (bounds.north - bounds.south) * (row + 1) / cell_grid
            for column in range(cell_grid):
                cell_west = bounds.west + (bounds.east - bounds.west) * column / cell_grid
                cell_east = bounds.west + (bounds.east - bounds.west) * (column + 1) / cell_grid
                min_x = lon_to_tile_x(cell_west, zoom)
                max_x = lon_to_tile_x(math.nextafter(cell_east, cell_west), zoom)
                min_y = lat_to_tile_y(math.nextafter(cell_north, cell_south), zoom)
                max_y = lat_to_tile_y(cell_south, zoom)
                found = any(
                    tile_range_contains(ranges, starts, zxy_to_tile_id(zoom, x, y))
                    for x in range(min_x, max_x + 1)
                    for y in range(min_y, max_y + 1)
                )
                if not found:
                    empty_cells.append({"row": row, "column": column})
        result = {
            "index": index,
            "bounds": asdict(bounds),
            "zoom": zoom,
            "cellGrid": cell_grid,
            "populatedCells": cell_grid * cell_grid - len(empty_cells),
            "emptyCells": empty_cells,
            "covered": not empty_cells,
        }
        results.append(result)
        if empty_cells:
            raise ContractError(
                f"PMTiles has no usable tile data in Area bounds[{index}] cells={empty_cells} at zoom {zoom}"
            )
    return {"artifact": str(artifact.resolve()), "bounds": results}


def manifest_for(
    artifact: Path,
    package_id: str,
    dataset_version: str,
    coverage: Bounds | list[Bounds],
) -> tuple[dict[str, Any], dict[str, Any]]:
    if not package_id or Path(package_id).name != package_id:
        raise ContractError("PackageId must be a non-empty path-safe name")
    if not dataset_version.strip():
        raise ContractError("DatasetVersion must not be blank")
    coverages = [coverage] if isinstance(coverage, Bounds) else coverage
    if not coverages:
        raise ContractError("At least one coverage fragment is required")
    for fragment in coverages:
        fragment.validate()
    info = read_pmtiles(artifact)
    artifact_bounds = Bounds(**info["bounds"])
    if any(not artifact_bounds.contains(fragment, COORDINATE_EPSILON) for fragment in coverages):
        raise ContractError("PMTiles header bbox does not contain every requested coverage fragment")
    manifest = {
        "schemaVersion": 1,
        "packageId": package_id,
        "territoryCompatibility": {"policy": "coverage_fragments_only"},
        "datasetVersion": dataset_version,
        "profileId": PROFILE_ID,
        "profileVersion": PROFILE_VERSION,
        "styleVersion": STYLE_VERSION,
        "coverageFragments": [asdict(fragment) for fragment in coverages],
        "minZoom": info["minZoom"],
        "maxZoom": info["maxZoom"],
        "pmtilesFile": artifact.name,
        "pmtilesByteLength": info["byteLength"],
        "pmtilesSha256": info["sha256"],
    }
    return manifest, info


def require_exact_keys(value: dict[str, Any], expected: set[str], label: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        extra = sorted(actual - expected)
        raise ContractError(f"{label} fields mismatch; missing={missing}, extra={extra}")


def validate_manifest(manifest: dict[str, Any], artifact: Path) -> dict[str, Any]:
    expected = {
        "schemaVersion", "packageId", "territoryCompatibility", "datasetVersion",
        "profileId", "profileVersion", "styleVersion", "coverageFragments",
        "minZoom", "maxZoom", "pmtilesFile", "pmtilesByteLength", "pmtilesSha256",
    }
    require_exact_keys(manifest, expected, "D065 manifest")
    if manifest["schemaVersion"] != 1:
        raise ContractError("D065 schemaVersion must be 1")
    compatibility = manifest["territoryCompatibility"]
    if compatibility != {"policy": "coverage_fragments_only"}:
        raise ContractError("D065 territoryCompatibility is invalid")
    for name in ("packageId", "datasetVersion", "profileId", "profileVersion", "styleVersion"):
        if not isinstance(manifest[name], str) or not manifest[name].strip():
            raise ContractError(f"D065 {name} must be non-empty")
    if manifest["profileId"] != PROFILE_ID or manifest["profileVersion"] != PROFILE_VERSION:
        raise ContractError("D065 profile is incompatible with Bee Search")
    if manifest["styleVersion"] != STYLE_VERSION:
        raise ContractError("D065 style is incompatible with Bee Search")
    fragments = manifest["coverageFragments"]
    if not isinstance(fragments, list) or not fragments:
        raise ContractError("D065 coverageFragments must be a non-empty array")
    coverages: list[Bounds] = []
    for index, fragment in enumerate(fragments):
        if not isinstance(fragment, dict):
            raise ContractError(f"D065 coverage fragment {index} is invalid")
        require_exact_keys(fragment, {"west", "south", "east", "north"}, f"coverage fragment {index}")
        coverage = Bounds(**fragment)
        coverage.validate()
        coverages.append(coverage)
    if manifest["pmtilesFile"] != artifact.name or artifact.name == ".pmtiles" or not artifact.name.endswith(".pmtiles"):
        raise ContractError("D065 pmtilesFile does not match the artifact basename")
    if not isinstance(manifest["pmtilesByteLength"], int) or manifest["pmtilesByteLength"] <= 0:
        raise ContractError("D065 pmtilesByteLength is invalid")
    manifest_hash = manifest["pmtilesSha256"]
    if (
        not isinstance(manifest_hash, str)
        or len(manifest_hash) != 64
        or manifest_hash.lower() != manifest_hash
        or any(character not in "0123456789abcdef" for character in manifest_hash)
    ):
        raise ContractError("D065 pmtilesSha256 must be lowercase SHA-256")
    info = read_pmtiles(artifact)
    if manifest["pmtilesByteLength"] != info["byteLength"]:
        raise ContractError("D065 byte length does not match the PMTiles artifact")
    if manifest_hash != info["sha256"]:
        raise ContractError("D065 SHA-256 does not match the PMTiles artifact")
    if manifest["minZoom"] != info["minZoom"] or manifest["maxZoom"] != info["maxZoom"]:
        raise ContractError("D065 zoom range does not match the PMTiles header")
    if any(not Bounds(**info["bounds"]).contains(coverage, COORDINATE_EPSILON) for coverage in coverages):
        raise ContractError("D065 coverage is outside the PMTiles header bbox")
    return {
        "manifest": manifest,
        "artifact": info,
        "coverageMetrics": [bounds_metrics(coverage) for coverage in coverages],
    }


def parse_bounds(args: argparse.Namespace) -> Bounds:
    bounds = Bounds(west=args.west, south=args.south, east=args.east, north=args.north)
    bounds.validate()
    return bounds


def parse_coverages(args: argparse.Namespace) -> list[Bounds]:
    if args.area_json is not None:
        if any(value is not None for value in (args.west, args.south, args.east, args.north)):
            raise ContractError("Use either --area-json or explicit bounds, not both")
        return read_area_bounds(args.area_json)[1]
    values = (args.west, args.south, args.east, args.north)
    if any(value is None for value in values):
        raise ContractError("Explicit bounds require --west, --south, --east and --north")
    return [parse_bounds(args)]


def write_json(value: Any, path: Path | None = None) -> None:
    text = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    if path is None:
        sys.stdout.write(text)
    else:
        path.write_text(text, encoding="utf-8")


def add_bounds_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--west", type=float, required=True)
    parser.add_argument("--south", type=float, required=True)
    parser.add_argument("--east", type=float, required=True)
    parser.add_argument("--north", type=float, required=True)


def add_coverage_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--area-json", type=Path)
    parser.add_argument("--west", type=float)
    parser.add_argument("--south", type=float)
    parser.add_argument("--east", type=float)
    parser.add_argument("--north", type=float)


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    source_parser = subparsers.add_parser("source-info")
    source_parser.add_argument("--source", type=Path, required=True)
    add_bounds_arguments(source_parser)

    area_parser = subparsers.add_parser("area-info")
    area_parser.add_argument("--area-json", type=Path, required=True)

    source_coverage_parser = subparsers.add_parser("validate-source-coverage")
    source_coverage_parser.add_argument("--area-json", type=Path, required=True)
    source_coverage_parser.add_argument("--source-coverage-polygon", type=Path, action="append", required=True)
    source_coverage_parser.add_argument("--grid-size", type=int, default=301)

    artifact_parser = subparsers.add_parser("artifact-info")
    artifact_parser.add_argument("--artifact", type=Path, required=True)

    create_parser = subparsers.add_parser("create-manifest")
    create_parser.add_argument("--artifact", type=Path, required=True)
    create_parser.add_argument("--manifest", type=Path, required=True)
    create_parser.add_argument("--package-id", required=True)
    create_parser.add_argument("--dataset-version", required=True)
    add_coverage_arguments(create_parser)

    validate_parser = subparsers.add_parser("validate-package")
    validate_parser.add_argument("--artifact", type=Path, required=True)
    validate_parser.add_argument("--manifest", type=Path, required=True)

    artifact_coverage_parser = subparsers.add_parser("validate-artifact-area")
    artifact_coverage_parser.add_argument("--area-json", type=Path, required=True)
    artifact_coverage_parser.add_argument("--artifact", type=Path, required=True)
    artifact_coverage_parser.add_argument("--cell-grid", type=int, default=5)

    args = parser.parse_args()
    if args.command == "source-info":
        selected = parse_bounds(args)
        source = read_osm_pbf_header(args.source)
        source_bounds = Bounds(**source["bounds"]) if source["bounds"] is not None else None
        result = {
            "selectedBounds": asdict(selected),
            "selectedMetrics": bounds_metrics(selected),
            "source": source,
            "sourceHeaderContainsSelectedBounds": source_bounds.contains(selected) if source_bounds is not None else None,
        }
        write_json(result)
        if result["sourceHeaderContainsSelectedBounds"] is False:
            raise ContractError("Source OSM PBF header bbox does not contain the selected bbox")
    elif args.command == "area-info":
        area, requested = read_area_bounds(args.area_json)
        generation_bounds = outer_bounds(requested)
        write_json(
            {
                "areaId": area.get("areaId"),
                "name": area.get("name"),
                "bounds": [asdict(bounds) for bounds in requested],
                "generationBounds": asdict(generation_bounds),
                "generationMetrics": bounds_metrics(generation_bounds),
            }
        )
    elif args.command == "validate-source-coverage":
        _, requested = read_area_bounds(args.area_json)
        results = validate_source_coverage(requested, args.source_coverage_polygon, args.grid_size)
        write_json(
            {
                "areaJson": str(args.area_json.resolve()),
                "sourceCoveragePolygons": [str(path.resolve()) for path in args.source_coverage_polygon],
                "bounds": results,
            }
        )
    elif args.command == "artifact-info":
        write_json(read_pmtiles(args.artifact))
    elif args.command == "create-manifest":
        coverages = parse_coverages(args)
        manifest, artifact_info = manifest_for(
            artifact=args.artifact,
            package_id=args.package_id,
            dataset_version=args.dataset_version,
            coverage=coverages,
        )
        args.manifest.parent.mkdir(parents=True, exist_ok=True)
        write_json(manifest, args.manifest)
        write_json(
            {
                "manifest": manifest,
                "artifact": artifact_info,
                "coverageMetrics": [bounds_metrics(coverage) for coverage in coverages],
            }
        )
    elif args.command == "validate-package":
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        write_json(validate_manifest(manifest, args.artifact))
    elif args.command == "validate-artifact-area":
        _, requested = read_area_bounds(args.area_json)
        write_json(validate_artifact_area(args.artifact, requested, args.cell_grid))


if __name__ == "__main__":
    try:
        main()
    except (ContractError, OSError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2) from error
