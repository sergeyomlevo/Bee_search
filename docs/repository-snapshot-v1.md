# Repository Snapshot V1 — Slice 2B

Temporal I3: V1 remains readable under its frozen schema. New snapshots use
[V2 canonical date carriage](snapshot-v2-wire-schema.md); inventory, evidence
profiles and publication/discovery safety below also apply to V2.

Owner-approved metadata profile; implementation pending final Slice 2B acceptance.
No format freeze is inferred from compilation. Independent contract:
[snapshot-2b-contract-preflight.md](snapshot-2b-contract-preflight.md).

## Promise and exclusions

Only `METADATA_ONLY / NO_MEDIA_EVIDENCE / COMPLETE` is implemented. COMPLETE means
successful capture and validated publication of the declared logical metadata, not
photo/video protection or recoverability. No restore, FULL, DEGRADED creation,
coverage, handoff, PC ingest, cleanup or scheduling is added by this slice, and this
slice itself added no UI. The user-facing entry point for creating this profile by hand
was added later by D099 and only drives this service; the profile, its limits and its
promises are unchanged. Legacy Complete Backup
V1–V6 remains separate. Never label this profile “Полная резервная копия”.

The 13 Room collections, current Territory/Observer and persisted Area geometry are
included. Media metadata is included but media bytes, offline maps, binding, SAF
grants, caches, Staging and transient UI state are excluded. Capture reads all Room
tables within one `withTransaction`; settings are read separately and validated
against that graph. No cross-Room/DataStore atomicity is claimed. Non-null dangling
settings and duplicate identities fail as LOGICAL_STATE_INCONSISTENT.

## Wire contract

Exactly the 17 paths in the preflight contract are required, including empty files.
Manifest identity is `beesearch-snapshot`, version 1, with snapshotId, repositoryId,
variant, createdAtEpochMs, profile/result/policy and empty creationIssues. `entries`
contains 16 descriptors `{path, byteSize, sha256}` sorted by path. `mediaReferences`
is `{path:"references/media-blobs.jsonl", recordCount}`; descriptors protect its
exact bytes, and semantic validation recomputes reachability from the graph.
Reference rows are `{sha256, byteSize, canonicalExtension}`, sorted/unique by SHA;
JPEG + generic resolves to jpg; conflicting recognized types or sizes fail.
No presence/evidence assertion is made by a reference.

Manifest and references use the port of the accepted R0-PC-C restricted JCS core:
UTF-8, UTF-16 key sorting, safe integers ±9007199254740991, deterministic non-HTML
escaping, strict duplicate keys/Unicode. The preserved corpus includes exact bytes,
hex and SHA expectations and runs unchanged on host and Android. No Gson dependency.

Domain JSONL uses the shared existing entity codec, not Complete Backup ZIP paths
or version semantics. Records sort by canonical UUID, subtype owner UUID, weather
point UUID, sequence `(territoryId, objectType.name)` and coverage Territory UUID.
UTF-8, LF, final LF for nonempty JSONL; an empty collection is zero bytes.
Numbers are finite binary64; signed zero is preserved. Integrity uses the original
stored bytes, never parsed/reserialized objects. Semantic parse follows size/SHA
verification. Future codec versions need conformance; whole-ZIP dedup is not promised.

## Limits and publication

ZIP ≤64 MiB, streamed uncompressed total ≤128 MiB, exactly17 regular entries;
manifest/portable ≤1 MiB each, references ≤32 MiB, JSONL record including LF ≤1 MiB.
No FlightCycles-specific cap. Parser: depth32, string/key65536 UTF-16 units,
object256 members, array100000 entries. Equality passes; first excess is a typed
SNAPSHOT_LIMIT_EXCEEDED with category, observed value and limit. No truncation or
automatic DEGRADED. ZIP paths/duplicates/missing/unexpected entries fail closed;
actual streamed expansion and CRC are checked, not just advertised sizes.

The bound Foundation maintenance gate coordinates snapshot/blob writes. Capture,
serialization, private fixed candidate, owned public Staging, sync and complete
validation precede same-storage move. UUID/header is reread immediately before
that irreversible move. Final actual file is copied to owned private fixed scratch
and independently revalidated (whole SHA, all entries/digests/semantics/identity).
Only then SNAPSHOT_COMMITTED may be returned. Filename:
`Snapshots/snapshot-<uuid>-<whole-file-sha>.zip`; SHA is not inside the ZIP.

Temporary validation spools consume disk, so preflight includes a conservative
peak of `3×64 MiB + 128 MiB = 320 MiB`, approved artifact slack and the20 GiB
reserve. This is an upper-bound implementation budget, not measured provider
amplification: build peak includes build+reader spools and candidate; later peak
includes candidate+public Staging+readback+reader spools. The build spools are
released before public Staging. Periodic transfer capacity checks fail closed.
Discovery also needs bounded private readback scratch and may report insufficient
capacity rather than pretending validation completed.

## Discovery and recovery boundary

Discovery checks actual Snapshots candidates, filename UUID/SHA, container, limits,
manifest/repository/variant and entry digests plus graph/reference consistency.
Latest valid sorts by `(createdAtEpochMs,snapshotId)`. Invalid/unknown candidates
are surfaced even when newer; duplicate snapshot UUID candidates conflict.
No mutable snapshot index or GC. Staging-only is not committed; corrupt final is
invalid evidence; previous valid files remain unchanged. Atomic move/power-loss
durability is not claimed. Failure cleanup touches only owned operation artifacts.

This provides immutable exported metadata and verifiable PC-copyable evidence,
not in-app restore or a complete recovery solution. Future restore must explicitly
represent unavailable media; future FULL needs its own LOCAL_VERIFIED policy.
Same-UUID physical copies remain allowed; discovery metadata can later inform an
explicit reconnect UI without inventing a physical-copy UUID.

## Acceptance gates

Tests/builds/critic/device results and aggregate DEV sizing are recorded in the
Slice 2B report. Sizing includes counts, raw bytes, averages/maxima, ZIP and limit
usage only; no research content leaves private scratch. Multi-season estimates
are extrapolation, not measurement. Limits are not frozen before this gate.
