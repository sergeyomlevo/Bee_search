#!/usr/bin/env python3
"""Inspect OSM PBF/PMTiles artifacts and create the Bee Search D065 sidecar."""

from __future__ import annotations

import argparse
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
    raise ContractError("Invalid protobuf varint in OSM PBF header")


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


def manifest_for(
    artifact: Path,
    package_id: str,
    dataset_version: str,
    coverage: Bounds,
) -> tuple[dict[str, Any], dict[str, Any]]:
    if not package_id or Path(package_id).name != package_id:
        raise ContractError("PackageId must be a non-empty path-safe name")
    if not dataset_version.strip():
        raise ContractError("DatasetVersion must not be blank")
    coverage.validate()
    info = read_pmtiles(artifact)
    artifact_bounds = Bounds(**info["bounds"])
    if not artifact_bounds.contains(coverage, COORDINATE_EPSILON):
        raise ContractError("PMTiles header bbox does not contain the requested coverage")
    manifest = {
        "schemaVersion": 1,
        "packageId": package_id,
        "territoryCompatibility": {"policy": "coverage_fragments_only"},
        "datasetVersion": dataset_version,
        "profileId": PROFILE_ID,
        "profileVersion": PROFILE_VERSION,
        "styleVersion": STYLE_VERSION,
        "coverageFragments": [asdict(coverage)],
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
    if not isinstance(fragments, list) or len(fragments) != 1:
        raise ContractError("This builder expects exactly one D065 coverage fragment")
    require_exact_keys(fragments[0], {"west", "south", "east", "north"}, "coverage fragment")
    coverage = Bounds(**fragments[0])
    coverage.validate()
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
    if not Bounds(**info["bounds"]).contains(coverage, COORDINATE_EPSILON):
        raise ContractError("D065 coverage is outside the PMTiles header bbox")
    return {"manifest": manifest, "artifact": info, "coverageMetrics": bounds_metrics(coverage)}


def parse_bounds(args: argparse.Namespace) -> Bounds:
    bounds = Bounds(west=args.west, south=args.south, east=args.east, north=args.north)
    bounds.validate()
    return bounds


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


def main() -> None:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    source_parser = subparsers.add_parser("source-info")
    source_parser.add_argument("--source", type=Path, required=True)
    add_bounds_arguments(source_parser)

    artifact_parser = subparsers.add_parser("artifact-info")
    artifact_parser.add_argument("--artifact", type=Path, required=True)

    create_parser = subparsers.add_parser("create-manifest")
    create_parser.add_argument("--artifact", type=Path, required=True)
    create_parser.add_argument("--manifest", type=Path, required=True)
    create_parser.add_argument("--package-id", required=True)
    create_parser.add_argument("--dataset-version", required=True)
    add_bounds_arguments(create_parser)

    validate_parser = subparsers.add_parser("validate-package")
    validate_parser.add_argument("--artifact", type=Path, required=True)
    validate_parser.add_argument("--manifest", type=Path, required=True)

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
    elif args.command == "artifact-info":
        write_json(read_pmtiles(args.artifact))
    elif args.command == "create-manifest":
        coverage = parse_bounds(args)
        manifest, artifact_info = manifest_for(
            artifact=args.artifact,
            package_id=args.package_id,
            dataset_version=args.dataset_version,
            coverage=coverage,
        )
        args.manifest.parent.mkdir(parents=True, exist_ok=True)
        write_json(manifest, args.manifest)
        write_json({"manifest": manifest, "artifact": artifact_info, "coverageMetrics": bounds_metrics(coverage)})
    elif args.command == "validate-package":
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        write_json(validate_manifest(manifest, args.artifact))


if __name__ == "__main__":
    try:
        main()
    except (ContractError, OSError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2) from error
