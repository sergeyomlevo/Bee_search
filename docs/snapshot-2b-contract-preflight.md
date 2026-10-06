# Snapshot 2B Contract Closure

Wire-schema reconciliation follow-up (2026-10-06):
[Snapshot V1 normative wire schema](snapshot-v1-wire-schema.md) supplies field-level
schemas with O1/O2/O3 now owner-approved. Current implementation validation gaps
are listed there; this does not rewrite the historical acceptance below or claim
that app/verifier alignment has already been implemented.

Status: owner-approved design contract, READY_FOR_SLICE_2B; implementation not started.
Date: 2026-10-06. Baseline: `3db5dda29f5306bd76c5dc33cafc6190499870f6`.

This closes the preceding chat preflight and its critic review. It supersedes that
preflight's FULL-first/LOCAL_VERIFIED-first recommendation and proposed per-collection
size caps, following the explicit owner closure decisions. It does not change Complete
Backup V1–V6. Repository foundation/binding constraints remain in
[repository-v1-foundation.md](repository-v1-foundation.md), D095/D096.
Only this design report is updated; production code, other project docs, devices,
commits and pushes are outside this task. Accepted decisions must be integrated into
the relevant normative project docs with the future implementation, without claiming
that this design report is evidence of implemented behavior.

## 1. Final profile semantics

Implement the first useful snapshot before requiring full large-media ingest:

```text
snapshotProfile = METADATA_ONLY
evidencePolicy = NO_MEDIA_EVIDENCE
creationResult = COMPLETE
```

COMPLETE is relative to the declared profile: the complete logical graph and consistent
portable metadata were captured; all expected entries, exact-byte entry digests,
manifest, publication and published readback are valid. It promises neither media
protection nor current phone/PC recoverability. Missing Backup/Media bytes do not
prevent this profile. Invalid media metadata/reachability still prevents publication.

Both METADATA_ONLY and future FULL keep large bytes outside the snapshot ZIP in
`Media/<sha256>.<ext>`. FULL is not implemented in Slice 2B; its intended policy is
LOCAL_VERIFIED, requiring verified required canonical blobs for COMPLETE. Readers
must reject unknown or unsupported profile/policy combinations, even when each name
is individually known. Historical METADATA_ONLY snapshots never acquire FULL promises.

## 2. Manifest and exact logical profile

Manifest fields:

```text
snapshotFormat = "beesearch-snapshot"
snapshotFormatVersion = 1
snapshotId, repositoryId, variant, createdAtEpochMs
snapshotProfile, creationResult, evidencePolicy
entry digest metadata, media-reference metadata, creationIssues
```

UUID, variant and version validation is strict. Unknown format/version/profile/
evidencePolicy/creationResult fails closed. In the first implementation, only the
METADATA_ONLY + NO_MEDIA_EVIDENCE + COMPLETE combination is accepted; future DEGRADED
must not be silently interpreted as COMPLETE. Creation time is provenance, not proof
of copy freshness. Whole-file SHA is computed from the closed ZIP and placed in the
external snapshot filename, never inside that ZIP (no cyclic digest).

The following are the exact proposed V1 paths, giving 17 regular-file entries;
empty collections are present as empty JSONL files. Path spellings are contract
targets for implementation/conformance, not evidence of an already frozen format.

| Entry | Logical content / stable ordering key |
|---|---|
| `manifest.json` | Canonical manifest; digest descriptors sorted by entry path |
| `data/territories.jsonl` | Territory: `id` |
| `data/observers.jsonl` | Observer: `id` |
| `data/physical-objects.jsonl` | PhysicalObject: `id` |
| `data/apiaries.jsonl` | Apiary: `physicalObjectId` |
| `data/hollows.jsonl` | Hollow: `physicalObjectId` |
| `data/log-hives.jsonl` | LogHive: `physicalObjectId` |
| `data/physical-object-sequences.jsonl` | PhysicalObjectSequence: tuple `(territoryId, objectType.name)` |
| `data/physical-object-media.jsonl` | PhysicalObjectMedia metadata: `id` |
| `data/observation-points.jsonl` | ObservationPoint: `id` |
| `data/bees.jsonl` | Bee: `id` |
| `data/flight-cycles.jsonl` | FlightCycle: `id` |
| `data/observation-point-weather.jsonl` | Weather: `observationPointId` |
| `data/observation-point-attachments.jsonl` | Attachment metadata: `id` |
| `settings/portable.json` | One deterministic object: current Territory / Observer |
| `settings/map-coverage.jsonl` | User Area/coverage geometry: `territoryId` |
| `references/media-blobs.jsonl` | Unique blob records: lowercase SHA-256 |

UUID keys use lowercase canonical UUID text, lexicographic ordering; composite keys
are tuples, not ambiguous concatenations. Equal record identity keys are rejected
before serialization, not tie-broken by input order. JSONL uses UTF-8 without BOM,
one record per line, LF delimiters, final LF for non-empty files; an empty file is zero
bytes. Domain field emission is defined by one shared Bee Search domain codec.
Settings field emission and semantic-set ordering are explicit, not iteration order.
Domain records also reject duplicate JSON object keys and malformed UTF-8/JSON;
being outside JCS does not relax parser safety or the shared resource bounds.

Preserve research fields, subtype details, numbering state, persisted lifecycle and
provenance flags. Reuse existing domain serialization/validation boundaries where
practical, not BackupCore's Android orchestration or embedded-media ByteArray pipeline.
Do not inherit Complete Backup wire paths or versions automatically.

Required/reference blobs are derived independently from PhysicalObjectMedia
(`physical_object_media`) and ObservationPointAttachment (`observation_point_attachments`):
`sha256`, `byteSize`, `canonicalExtension`, with exact equality to the declared unique
reference set. Multiple logical records may share a blob; conflicting SHA-associated
size/extension fails closed. The manifest describes the reference entry's path/digest
and reference-set context; the bounded reference file carries the full list, avoiding
duplication of a potentially 32 MiB list inside the 1 MiB manifest. METADATA_ONLY records
requirements, not verified presence. Retained-history reachability is a union of
selected snapshots, not just the latest snapshot.

Exclude offline map bytes/package installation state, Backup contents/binding,
transient Staging, grants, caches and UI state. User Area geometry is research metadata
and is included. No coverage/handoff/offload store is added.

## 3. Canonical/domain digest boundary

Reuse the approved R0-PC-C restricted RFC 8785 profile; do not design a second scheme:

- UTF-8 without BOM or formatting whitespace; UTF-16-code-unit key ordering.
- Arrays retain order; semantic sets are explicitly sorted by schema keys.
- Integer-only numbers within +/- 9007199254740991; -0 canonicalizes to 0.
- Duplicate object keys, malformed UTF-8 and unpaired surrogates rejected.
- Deterministic JSON escaping, no HTML-specific escaping, no Unicode normalization.

Apply this only to manifest, applicable digest payloads and blob/reference metadata.
Domain JSONL is NOT RFC 8785 canonical JSON. Its integrity is SHA-256 of exact stored
UTF-8 bytes, not a digest of parsed/re-emitted objects. Byte-identical domain entries
for one graph are established by shared codec/order tests, not by claiming general JCS.

Existing sources/corpus:

- `C:\App\BeeSearchBackupResearch\R0-PC-C\src\main\kotlin\poc\Json.kt`
- `C:\App\BeeSearchBackupResearch\R0-PC-C\src\main\kotlin\poc\Hardening.kt`
- `C:\App\BeeSearchBackupResearch\R0-PC-C-results\b67365d9-2ebc-43c5-92ce-483946f2207b\canonical-vectors.json`
- `C:\App\BeeSearchBackupResearch\R0-PC-C\independent-vectors.cjs`

Carry all nine positive vectors and negative parser cases into reusable repository
test resources during implementation. Add set-permutation tests; the existing sorted
set vector alone does not prove sorting. Gson in the PoC is not automatically a
production dependency. Source standard: https://www.rfc-editor.org/rfc/rfc8785 .
Broad prior-art research and this canonical choice are already complete; this closure
adds no external technology mechanism and requires no new research cycle.

## 4. Fractional numeric contract and mandatory vectors

Domain fractions are JSON numbers, not strings. NaN and positive/negative Infinity
are forbidden. Readers recover finite IEEE-754 binary64 / Double values. Preserve
signed zero semantically; never route domain -0.0 through integer canonicalization.
Tokens that overflow into Infinity are invalid even if syntactically valid JSON.
Exact-byte entry digest remains representation identity; equivalent numeric spellings
are not interchangeable under that digest. Writer uses the shared codec's deterministic
finite Double emission, not an independent PC reserializer.

Mandatory reader vectors (raw token -> expected raw Double bits, hexadecimal):

| JSON token | Expected binary64 bits | Purpose |
|---|---|---|
| `0.1` | `3FB999999999999A` | Fractional rounding |
| `-0.0` | `8000000000000000` | Signed zero |
| `5e-324` | `0000000000000001` | Smallest positive subnormal |
| `1.7976931348623157e308` | `7FEFFFFFFFFFFFFF` | Largest finite Double |
| `55.7558` | `404BE0BE0DED288D` | Latitude example |
| `37.6173` | `4042CF03AFB7E910` | Longitude example |
| `12.5` | `4029000000000000` | Weather temperature |
| `3.6` | `400CCCCCCCCCCCCD` | Weather wind speed |
| `23.75` | `4037C00000000000` | Measurement |

Also reject bare NaN/Infinity/+Infinity/-Infinity, quoted non-number substitutes and
overflowing numeric tokens (e.g. 1e309) for fractional fields. Test writer rejection of
nonfinite Double inputs separately. These are semantic codec vectors; out-of-domain
extreme values still fail latitude/weather/domain range validation where applicable.

Bits above were checked with the host .NET invariant-culture parser/BitConverter during
closure. This is an independent reference calculation, not an Android/JVM conformance
test result. Android/shared JVM/future PC tests must consume the same vector corpus,
compare raw bits and check writer-reader roundtrip. Whole-entry vectors also check
original bytes and SHA. No test files or production implementation are created here.

## 5. Entry integrity and required conformance tests

Manifest contains path, exact byte length and SHA-256 of every other expected entry,
including settings and the reference list. It does not contain a self-digest descriptor
for manifest.json. Validate the bounded canonical manifest independently; whole ZIP
SHA covers the manifest as well as all entries. No parse-reserialize hashing anywhere.

For each domain entry: read raw bytes into a bounded fixed owned spool/candidate view,
verify exact size and SHA, then parse the same verified bytes. Large entries need not
be retained in a ByteArray. Reopening mutable transport content after hashing is not
equivalent to parsing those fixed bytes.

Mandatory tests during Slice 2B:

- Different insertion/query orders give byte-identical JSONL and matching entry SHA.
- Duplicate UUID/composite keys and conflicting blob-reference metadata fail closed.
- A parseable change in whitespace/key order/numeric spelling with an unchanged
  manifest digest fails integrity, even if parsed values are equivalent.
- All fractional and existing canonical positive/negative vectors are shared.
- Missing/unexpected entries, malformed manifest and unsupported profile/policy fail.

These tests are specified, not run by this design-only closure.

## 6. Room/settings consistency

Room graph capture is transactional. Portable settings are captured separately; no
cross-store atomicity is claimed. Validate every domain-bearing setting against the
captured graph. Null selections are valid. Non-null currentTerritoryId/currentObserverId
must resolve; every coverage territory key must resolve and geometry must validate.
An inconsistent non-null value is never silently replaced with null.

Unresolved setting or invalid logical graph produces LOGICAL_STATE_INCONSISTENT and
no snapshot publication. Continue normal field capture; do not mutate research state
to make export succeed. Serialization operates on the fixed captured graph/settings.

## 7. Final V1 limits and ZIP safety

Owner-approved limits; equality is allowed:

| Category | Limit |
|---|---:|
| Whole snapshot ZIP | 64 MiB (67108864 bytes) |
| Total actual streamed uncompressed entries | 128 MiB (134217728 bytes) |
| Entry count | Exactly the 17 expected regular entries |
| Manifest | 1 MiB |
| Portable settings | 1 MiB |
| Required media references | 32 MiB |
| Single JSONL record (UTF-8 bytes including its LF delimiter) | 1 MiB |
| JSON depth | 32 (root depth 0; nesting increments as in existing corpus) |
| String/key | 65536 UTF-16 units |
| Object members | 256 |
| Array entries | 100000 |

No separate FlightCycles or other collection file cap; each is bounded by the total
uncompressed ceiling and record cap. Existing PoC 8 MiB metadata caps and Complete
Backup limits are not inherited. File framing is included in byte budgets.

First byte/count over a limit produces SNAPSHOT_LIMIT_EXCEEDED with affected
entry/category, observed bytes/count and configured limit. Report string length in
UTF-16 units and structural counts in their own units, not mislabeled as bytes.
Missing expected entries are structural errors, not size-limit success. No truncation,
implicit loss, automatic DEGRADED or success publication after failure.

Reject traversal, absolute/backslash paths, duplicate/unexpected entries and malformed
ZIP, including truncated directories. Enforce actual streamed expansion bounds, not
only advertised ZIP sizes; no compression-ratio-only substitute for those bounds.
Validate fixed candidate bytes. Reuse shared ZipSafety where applicable, with the new
profile limits supplied explicitly rather than changing legacy limits.

Snapshot publication retains current bound-root identity revalidation immediately
before canonical move, same-storage move-only behavior, owned Staging, capacity guard,
and full published readback. No atomic rename/power-loss guarantee, no copy+delete
fallback, and no global mutable index/GC are introduced.

## 8. DEGRADED and future FULL

Retain a stable typed creationIssues model: mediaRecordKind, mediaRecordId, ownerId,
expectedBlob (SHA/size/extension) and reason. Reserved reasons from preflight:
SOURCE_MISSING, SOURCE_UNREADABLE, SOURCE_CHANGED, LOCAL_BLOB_INVALID.
Provider exception strings are not canonical reasons. Unknown/corrupt media identity
is a validation failure, not a way to invent expected bytes.

Slice 2B publishes COMPLETE only with empty creationIssues, relative to METADATA_ONLY.
Absence of media bytes is not degradation for that profile. Future FULL/DEGRADED
support requires its own explicit validation semantics and tests; a valid local blob
can satisfy FULL even if its original source is absent. Availability, historical PC
coverage and fresh restore verification remain separate; none rewrites historical
creation results. Unknown/unsupported result values fail closed now.

## 9. Reconnect, MTP and offload

Do not introduce physical-copy UUID. Discovery provides selected location and latest
valid snapshot createdAt, snapshotId, whole-file SHA, profile and creation result.
Deterministic ordering uses (createdAtEpochMs, snapshotId); newer invalid candidates
remain visible as errors, not silently hidden by an older valid result. UI is deferred.

Accepted product evidence, not newly rerun here: flat 1000 JPEG/MP4 PASS; flat 1052
files / 10898907256 bytes PASS with full SHA; interrupted flat transfer failed closed.
Explorer completion is not integrity evidence; PC receive requires strong verification.
Old bucket/.bin defect is UNRESOLVED, NON-BLOCKING, outside this closure.

Offload is already required: fresh matching PC verification can later authorize deletion
of exact external Backup/Media duplicates only. Private working originals remain.
Accepted C2 Samsung evidence established shared storage pool/practical space recovery;
this is not a universal-provider guarantee. Coverage/handoff/cleanup are not Slice 2B.

## 10. Representative sizing gate

Before final format freeze (not necessarily before coding), serialize a read-only
capture of the real DEV logical graph using the implemented codec, without modifying
or publishing user research data. Use private/disposable generated output, not live
repository mutation. Report only aggregate counts/sizes, never user content.

Record: count per collection; raw bytes per entry; total uncompressed; resulting ZIP
bytes; largest JSONL record; manifest/settings/reference sizes; percentage of every
applicable limit. Include maximum depth/string/object/array observations where
applicable. For exact entry count, report 17/17 rather than misleading headroom.

If data reaches/exceeds a limit, STOP before freeze. Also report exact numbers and
STOP before freeze if representative data is already close to a bound; show remaining
headroom and the growth context instead of silently declaring the cap sufficient.
No additional production limit/default or automatic cap increase is introduced.
Previous synthetic growth estimates are not a substitute for this gate.

## 11. Remaining decisions and verdict

No unresolved owner decision blocks beginning Slice 2B. Profile, numeric/digest scope,
limits and development order are settled by the current owner instruction. Exact
wire conformance and real DEV sizing remain implementation/format-freeze acceptance
gates, not requests for another architecture research cycle.

READY_FOR_SLICE_2B

This readiness does not authorize implementation in this task. STOP after closure;
no production changes, commit or push.
