# Logical backup formats v1, v2 and v3

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

Version 2 retains the v1 research and
portable-settings collections and adds `observation-point-weather`,
`observation-point-attachments`, and photo entries under `attachments/`.
ObservationPoint records also carry nullable `description`. Every attachment entry
is tied to exactly one metadata row and validated by owner, deterministic relative
path, byte size, and SHA-256; missing, extra, duplicate, unsafe, or mismatched files
reject the archive before research data is written. Files are staged and validated,
then activated before the single Room restore transaction; activation and database
failures compensate activated files.

Version 3 was the Complete backup contract for Room schema v8. It adds
required `physical-objects` and `apiaries` collections to v2 and a nullable
`sourceObjectId` field to each Bee record. The object collection stores UUID,
Territory UUID, concrete type, immutable sequence number, coordinates and creation
time. The Apiary collection stores its object UUID and nullable name. Restore
validates identities, Territory and Bee references, unique designation scope,
and the exact Apiary subtype relationship before inserting objects and Apiaries
ahead of Bees in the same Room transaction. No point-export D086 contract changes.

The importer remains backward-compatible with v1 and v2. Older archives restore
without physical objects and with null Bee source links. A v1 point restores with
`description = null`, no attachments, and a `PENDING` weather row whose snapshot
values are null. No historical weather value is invented.

All formats retain the bounded reader limits: at most 64 entries, 16 MiB per entry,
and 64 MiB total uncompressed payload. Consequently each imported photo is limited
to 16 MiB and a Complete backup containing all photos must fit the total limit.
Offline PMTiles remain excluded.

Android cloud backup/device transfer includes ordinary app-owned attachment files by
default because only map packages and DEV bootstrap files are excluded by Bee Search
rules. Android cloud backup has a platform quota and is not the canonical Complete
backup; logical backup v4 is the explicit portable research archive.

## Version 4

Version 4 is the Complete backup contract for Room schema v9 and Objects V1. It retains all
v3 collections and adds `hollows`, `log-hives` and `physical-object-media`. Physical-object
records additionally carry nullable `creatorObserverId`; subtype records carry the stable
characteristics of Hollow and LogHive. Each object-media row identifies one `IMAGE` or `VIDEO`
item, its owning object, deterministic relative path, byte size and SHA-256. Media bytes are
stored as separate `physical-object-media/<physicalObjectId>/<mediaId>` entries, not embedded
in Room JSON.

Restore validates object ownership, subtype/type matching, creator Observer references, media
paths, sizes and hashes before the Room transaction; file activation and database failure are
compensated. v4 readers preserve backward readability of supported v1, v2 and v3 archives. v3
physical objects restore with null creator and null historical subtype properties; no values are
invented. PMTiles/device-local map packages remain excluded.

All existing archive limits remain unchanged: at most 64 entries, 16 MiB per entry and 64 MiB
total uncompressed payload. Track/GPX and Inspection data are not part of v4.


## Temporal I3 — Complete Backup V7 (2026-10-08)

The current writer emits `backupFormatVersion=7`, `archiveSchemaVersion=7`
and records `roomSchemaVersion=13`. V7 retains V6's fifteen required collection
paths, media file paths, descriptor inventory, `collectionSchemaVersion=1`,
settings, identity, ordering, ZIP limits, graph validation and restore safety.
The format/archive version selects the new record representation.

V7 ObservationPoint records add REQUIRED `observationDate`: a valid ISO calendar
date spelled exactly `YYYY-MM-DD`. PhysicalObject records add REQUIRED
`fixationDate`: the same string representation or explicit JSON null. Missing
keys, invalid dates or wrong types fail closed before DB/media/settings restore.
Values come directly from persisted canonical dates and never from createdAt,
current date, import time or the reader's timezone.

Readers retain Complete Backup V1–V6. Only those historical formats reconstruct
ObservationPoint dates once from createdAt in the approved legacy local-calendar
convention; physical-object fixation dates materialize as NULL. The accepted
legacy timezone caveat remains. A guarded explicit V6 serialization path remains
for compatibility tests; it rejects corrected/unrepresentable point dates or
non-null object dates before writing. The default V7 path does not use those
legacy representability guards.

No Export wire evolution is included. I2 + I3 + I4 remain one deployment unit;
I4 carriage and pending isolated Room/device verification block Samsung install.


## Complete Backup V8 — physical-object temporal moments (D103)

Current writer: backupFormatVersion/archiveSchemaVersion 8, roomSchemaVersion 14. Physical-object records retain V7 fixationDate and add REQUIRED nullable `fixationAt` / `updatedAt`, integer epoch milliseconds. Non-null fixationAt requires a known fixationDate. Restore explicitly preserves all fields without deriving dates/instants from createdAt or restore time.

Readers retain V1–V7: new fields materialize NULL. V6 retains its original canonical-date representability guards; V7 can still represent canonical dates. Both legacy writers reject non-null new instant fields. Inventory, settings, media, UUIDs, restore transaction and validation remain unchanged; malformed new keys fail before mutation.
