"""Read-only parser for the published Bee Search logical backup v1 contract."""

from __future__ import annotations

import hashlib
import json
import re
import uuid
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

PROFILE = "COMPLETE_BACKUP"
MAX_ENTRIES = 64
MAX_ENTRY_BYTES = 16 * 1024 * 1024
MAX_TOTAL_BYTES = 64 * 1024 * 1024
READ_CHUNK_BYTES = 1024 * 1024
COLLECTIONS = {
    "territories": "research/territories.json",
    "observers": "research/observers.json",
    "observation-points": "research/observation-points.json",
    "bees": "research/bees.json",
    "flight-cycles": "research/flight-cycles.json",
    "portable-settings": "settings/portable-settings.json",
    "map-coverage": "settings/map-coverage.json",
}
UUID_PATTERN = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
)
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
INT32_MIN = -(2**31)
INT32_MAX = 2**31 - 1
INT64_MIN = -(2**63)
INT64_MAX = 2**63 - 1


class BackupError(ValueError):
    """The input is not a supported, trustworthy Bee Search backup."""


@dataclass(frozen=True)
class Collection:
    name: str
    path: str
    data: bytes
    rows: tuple[dict[str, Any], ...]
    record_count: int
    sha256: str


@dataclass(frozen=True)
class ParsedArchive:
    manifest: dict[str, Any]
    collections: dict[str, Collection]
    source_archive_sha256: str
    source_archive_byte_length: int
    logical_content_sha256: str


def require_fields(value: Any, required: set[str], label: str) -> dict[str, Any]:
    if not isinstance(value, dict) or not required.issubset(value):
        raise BackupError(f"missing or invalid fields in {label}")
    return value


def integer(value: Any, label: str, minimum: int = INT64_MIN, maximum: int = INT64_MAX) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise BackupError(f"{label} must be an integer")
    if value < minimum or value > maximum:
        raise BackupError(f"{label} is outside its integer range")
    return value


def integer32(value: Any, label: str) -> int:
    return integer(value, label, INT32_MIN, INT32_MAX)


def boolean(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise BackupError(f"{label} must be a boolean")
    return value


def canonical_uuid(value: Any, label: str) -> str:
    if not isinstance(value, str) or not UUID_PATTERN.fullmatch(value):
        raise BackupError(f"invalid canonical UUID in {label}")
    try:
        parsed = uuid.UUID(value)
    except ValueError as error:
        raise BackupError(f"invalid canonical UUID in {label}") from error
    if str(parsed) != value:
        raise BackupError(f"invalid canonical UUID in {label}")
    return value


def _strict_json(data: bytes, label: str) -> Any:
    def object_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise BackupError(f"duplicate JSON key in {label}")
            result[key] = value
        return result

    def invalid_constant(value: str) -> Any:
        raise BackupError(f"invalid JSON number {value} in {label}")

    try:
        return json.loads(
            data.decode("utf-8", errors="strict"),
            object_pairs_hook=object_pairs,
            parse_constant=invalid_constant,
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise BackupError(f"invalid UTF-8 or JSON in {label}") from error


def _safe_path(name: str) -> bool:
    parts = name.split("/")
    return (
        bool(name)
        and not name.startswith("/")
        and "\\" not in name
        and ":" not in name
        and all(part not in ("", ".", "..") for part in parts)
    )


def _file_sha256(path: Path) -> tuple[str, int]:
    digest = hashlib.sha256()
    length = 0
    with path.open("rb") as source:
        while chunk := source.read(READ_CHUNK_BYTES):
            digest.update(chunk)
            length += len(chunk)
    return digest.hexdigest(), length


def _read_zip_entries(path: Path) -> dict[str, bytes]:
    entries: dict[str, bytes] = {}
    total = 0
    try:
        with zipfile.ZipFile(path, "r") as archive:
            infos = archive.infolist()
            if not infos or len(infos) > MAX_ENTRIES:
                raise BackupError("invalid ZIP entry count")
            for info in infos:
                name = info.filename
                if info.is_dir() or not _safe_path(name):
                    raise BackupError("unsafe ZIP path")
                if name in entries:
                    raise BackupError("duplicate ZIP entry")
                if info.file_size > MAX_ENTRY_BYTES:
                    raise BackupError("ZIP entry too large")
                chunks: list[bytes] = []
                size = 0
                with archive.open(info, "r") as stream:
                    while chunk := stream.read(READ_CHUNK_BYTES):
                        size += len(chunk)
                        if size > MAX_ENTRY_BYTES:
                            raise BackupError("ZIP entry too large")
                        chunks.append(chunk)
                if size != info.file_size:
                    raise BackupError("truncated ZIP entry")
                total += size
                if total > MAX_TOTAL_BYTES:
                    raise BackupError("archive is too large")
                entries[name] = b"".join(chunks)
    except BackupError:
        raise
    except (
        OSError,
        EOFError,
        RuntimeError,
        NotImplementedError,
        zipfile.BadZipFile,
        zipfile.LargeZipFile,
    ) as error:
        raise BackupError("malformed archive") from error
    return entries


def _rows(data: bytes, label: str) -> tuple[dict[str, Any], ...]:
    try:
        text = data.decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        raise BackupError(f"invalid UTF-8 in {label}") from error
    result: list[dict[str, Any]] = []
    for line in re.split(r"\r\n|\n|\r", text):
        if line.strip():
            row = _strict_json(line.encode("utf-8"), label)
            if not isinstance(row, dict):
                raise BackupError(f"record in {label} must be an object")
            result.append(row)
    return tuple(result)


def read_archive(path: Path) -> ParsedArchive:
    source_hash, source_length = _file_sha256(path)
    entries = _read_zip_entries(path)
    manifest_data = entries.get("manifest.json")
    if manifest_data is None:
        raise BackupError("missing manifest.json")
    manifest = require_fields(
        _strict_json(manifest_data, "manifest.json"),
        {
            "backupFormatVersion", "archiveSchemaVersion", "archiveId", "createdAt",
            "sourceAppVersion", "roomSchemaVersion", "profile", "collections",
        },
        "manifest",
    )
    if integer32(manifest["backupFormatVersion"], "backupFormatVersion") != 1:
        raise BackupError("unsupported backup format")
    if integer32(manifest["archiveSchemaVersion"], "archiveSchemaVersion") != 1:
        raise BackupError("unsupported archive schema")
    if manifest["profile"] != PROFILE:
        raise BackupError("unsupported backup profile")
    canonical_uuid(manifest["archiveId"], "archiveId")
    integer(manifest["createdAt"], "createdAt")
    integer32(manifest["roomSchemaVersion"], "roomSchemaVersion")
    if not isinstance(manifest["sourceAppVersion"], str) or not manifest["sourceAppVersion"].strip():
        raise BackupError("sourceAppVersion must not be blank")
    descriptors = manifest["collections"]
    if not isinstance(descriptors, list):
        raise BackupError("collections must be an array")

    collections: dict[str, Collection] = {}
    described_names: set[str] = set()
    described_paths: set[str] = set()
    listed_paths = {"manifest.json"}
    fields = {"name", "path", "collectionSchemaVersion", "required", "recordCount", "byteLength", "sha256"}
    for raw in descriptors:
        descriptor = require_fields(raw, fields, "collection descriptor")
        name = descriptor["name"]
        collection_path = descriptor["path"]
        if not isinstance(name, str) or name in described_names:
            raise BackupError("duplicate or invalid collection name")
        if not isinstance(collection_path, str) or collection_path in described_paths or not _safe_path(collection_path):
            raise BackupError("duplicate or unsafe collection path")
        described_names.add(name)
        described_paths.add(collection_path)
        required = boolean(descriptor["required"], f"{name}.required")
        record_count = integer32(descriptor["recordCount"], f"{name}.recordCount")
        byte_length = integer(descriptor["byteLength"], f"{name}.byteLength")
        expected_hash = descriptor["sha256"]
        if record_count < 0 or byte_length < 0 or not isinstance(expected_hash, str) or not SHA256_PATTERN.fullmatch(expected_hash):
            raise BackupError(f"invalid integrity descriptor for {name}")
        if name not in COLLECTIONS:
            if required:
                raise BackupError(f"unknown required collection: {name}")
            optional_data = entries.get(collection_path)
            if optional_data is not None:
                if len(optional_data) != byte_length or hashlib.sha256(optional_data).hexdigest() != expected_hash:
                    raise BackupError(f"integrity mismatch for optional collection {name}")
                listed_paths.add(collection_path)
            continue
        if not required:
            raise BackupError(f"known required collection marked optional: {name}")
        if integer32(descriptor["collectionSchemaVersion"], f"{name}.collectionSchemaVersion") != 1:
            raise BackupError(f"unsupported collection schema: {name}")
        if collection_path != COLLECTIONS[name]:
            raise BackupError(f"unexpected collection path: {name}")
        data = entries.get(collection_path)
        if data is None:
            raise BackupError(f"missing collection: {name}")
        if len(data) != byte_length or hashlib.sha256(data).hexdigest() != expected_hash:
            raise BackupError(f"integrity mismatch: {name}")
        parsed_rows = _rows(data, name)
        if len(parsed_rows) != record_count:
            raise BackupError(f"record count mismatch: {name}")
        collections[name] = Collection(name, collection_path, data, parsed_rows, record_count, expected_hash)
        listed_paths.add(collection_path)
    if set(collections) != set(COLLECTIONS):
        raise BackupError("missing required collection")
    if set(entries) != listed_paths:
        raise BackupError("unlisted ZIP entry")

    logical_digest = hashlib.sha256()
    for name in sorted(collections):
        logical_digest.update(name.encode("utf-8"))
        logical_digest.update(b"\0")
        logical_digest.update(collections[name].data)
    verified_hash, verified_length = _file_sha256(path)
    if verified_hash != source_hash or verified_length != source_length:
        raise BackupError("source archive changed while it was being read")
    return ParsedArchive(manifest, collections, source_hash, source_length, logical_digest.hexdigest())
