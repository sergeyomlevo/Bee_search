# Logical backup formats v1 and v2

The Bee Search backup is a logical ZIP archive, not a SQLite file. Version 1
uses `backupFormatVersion=1` and `archiveSchemaVersion=1` and has these fixed
paths: `manifest.json`, five `research/*.json` collections, and the two
portable settings files under `settings/`.

All seven payload files are required collections in `manifest.json`, each with
`collectionSchemaVersion`, `recordCount`, `byteLength`, and SHA-256. Research
records and map-coverage records are newline-delimited canonical JSON; portable
settings contains exactly one JSON record. Records are sorted by lowercase UUID
text and object keys are emitted in the field order fixed by format v1.
Instants are Unix epoch milliseconds; enums use their persisted names; null is
JSON null. Manifest collection byte lengths, line counts, and SHA-256 values
are over the exact UTF-8 bytes. ZIP metadata is not part of logical equality.
`archiveId` and `createdAt` are archive provenance and are excluded when
comparing a round trip. The canonical logical fingerprint hashes each payload
collection name and its canonical bytes in collection-name order; it therefore
remains stable across export/import/export even when ZIP metadata and archive
provenance change.

The first importer accepts only an empty research database. It validates the
complete archive, graph identities, foreign keys, domain ranges, and coverage
before one Room transaction. Portable DataStore settings are applied only
after that transaction. Map package bytes, local paths, active package keys,
and app-private pointers are deliberately excluded. Applying portable settings
replaces current IDs and coverage only; unrelated DataStore keys, including
`map_package_active_*`, remain unchanged. A settings-only retry revalidates the
archive and does not re-import Room data.

The reader rejects duplicate or unsafe ZIP paths, unlisted entries, more than
64 entries, a payload over 16 MiB, or total uncompressed payload over 64 MiB.
Unknown required collections are rejected. Unknown optional collections may be
ignored only after their declared path, length, and hash are valid.

Version 2 is the current Complete backup contract. It retains the v1 research and
portable-settings collections and adds `observation-point-weather`,
`observation-point-attachments`, and photo entries under `attachments/`.
ObservationPoint records also carry nullable `description`. Every attachment entry
is tied to exactly one metadata row and validated by owner, deterministic relative
path, byte size, and SHA-256; missing, extra, duplicate, unsafe, or mismatched files
reject the archive before research data is written. Files are staged and validated,
then activated before the single Room restore transaction; activation and database
failures compensate activated files.

The importer remains backward-compatible with v1. A v1 point restores with
`description = null`, no attachments, and a `PENDING` weather row whose snapshot
values are null. No historical weather value is invented.

Both formats retain the bounded reader limits: at most 64 entries, 16 MiB per entry,
and 64 MiB total uncompressed payload. Consequently each imported photo is limited
to 16 MiB and a Complete backup containing all photos must fit the total limit.
Offline PMTiles remain excluded.

Android cloud backup/device transfer includes ordinary app-owned attachment files by
default because only map packages and DEV bootstrap files are excluded by Bee Search
rules. Android cloud backup has a platform quota and is not the canonical Complete
backup; logical backup v2 is the explicit portable research archive.
