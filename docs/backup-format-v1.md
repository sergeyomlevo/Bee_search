# Logical backup format v1

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
ignored only after their declared path, length, and hash are valid. Future
first-class collections and attachment bytes require an explicit archive schema
evolution; v1 does not create speculative Room entities.
