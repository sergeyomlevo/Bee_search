# D106 Stage 1D — GPX Persistence Implementation Contract

Status: **OWNER-ACCEPTED**.

Documentation-only finalization of the full Persistence Implementation Contract
prepared in the OWNER conversation. Independent DeepSeek conclusion supplied by
OWNER: ACCEPT WITH CONDITIONS; conditions C1–C3 are incorporated below. OWNER
subsequently explicitly accepted this Persistence Foundation contract, including
its physical schema. Acceptance is not implementation authorization or runtime proof.

Baseline: task/d106-stage0-overlay-registry at
3a67dc5d2bb112cf82b4cb932624240592641e42. Slice5 is CLOSED / OWNER-ACCEPTED within
processor-handoff scope; Stage1 remains OPEN. Historical Stage1C §31 is unchanged.

Authority: [Stage1A §§4–8,12–14](d106-stage1-gpx-spec.md),
[Stage1B §§5–7,11–12](d106-stage1b-gpx-parser-identity-contract.md),
[Stage1C §31.4](d106-stage1c-gpx-implementation-contract.md#314-future-owner-integration-contract)
and [D106 OWNER decisions D1/D2](decisions.md#d106-persistence-foundation-owner-decisions-d1d2).
The existing [checklist](unified-territory-map-checklist.md) remains the status source.

Labels: ACCEPTED = inherited normative obligation; OWNER-APPROVED = D1/D2;
OBSERVED = inspected implementation; OWNER-ACCEPTED = this approved Persistence
Foundation contract; UNKNOWN = not established. The physical choices below are
accepted contract requirements, not implemented behavior. No accepted Stage1A/B/C
rule is amended here.

## 1. Accepted constraints and OWNER decisions

ACCEPTED:
- Territory owns TrackImport; Area does not participate in GPX ownership.
- Capture immutable request UUID, Territory and applicable Observer before picker.
  Missing/obsolete/consumed requests cannot be handled again as new imports.
- Preserve separate I/T/S/O identities. Sample address is (Segment UUID, ordinal).
- Original SHA/length describe exact bytes. CM1 is comparison material, not logical
  identity, uniqueness, persistent source key or duplicate-confirmation policy.
- No SHA uniqueness. Confirmed repeated imports receive fresh logical identities.
- Persist exact canonical values, XML order, empty nodes and provenance.
- Durable operational journal; PENDING is inaccessible to ordinary reads/protection.
- READY is the success boundary; cancellation after READY does not undo the import.
- Reconcile before exposing GPX data at startup/first access. Preserve viewing when
  an original is unavailable; FULL protection fails/declines, without silent omission.
- Restore preserves IDs/values/ordinals without GPX reparsing.
- Closed parser/model/CM1/cancellation invariants are not redesigned.

OWNER-APPROVED D1: nullable request UUID on TrackImport, no separate receipt table.
Correlation is operational and excluded from the canonical graph transported by
Complete Backup/Repository Snapshot; after those restores it is NULL.

OWNER-APPROVED D2: first-slice Android Auto Backup/data-transfer rules remain
unchanged, including the common Room database. A restored operational journal must
be reconciled by its future owner. User GPX import stays inactive until agreed
protection/restore gates. D1/D2 remain effective, revisable only through a technically
justified OWNER decision. Separate explicit OWNER acceptance now also covers this
Persistence Foundation contract and physical schema.

**AMENDED 2026-10-17:** the sentence "User GPX import stays inactive until agreed
protection/restore gates" is superseded. GPX protection/restore and H05 move to the
future unified local Backup/Restore and no longer gate user GPX import
([decisions.md](decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17)).
The Android Auto Backup part of D2 and D1 are unchanged.

OBSERVED baseline: Room v14 with explicit migrations, Room2.8.4 and the existing
millisecond Instant converter. Recheck before implementation. No qualification was
rerun for this document. External research: not applicable to this documentation
transfer; the prior design research is retained as references in §6.

## 2. OWNER-ACCEPTED physical schema

Exactly five tables; no receipt, warning, original-object or quota-counter table.
Names are the accepted engineering details here. UUID is TEXT through the existing converter;
Long is INTEGER; enum uses a stable TEXT token, not ordinal. Fields are NOT NULL
unless marked NULL, including TEXT primary keys. FK deletion is RESTRICT and update
NO ACTION. Inserts use ABORT, never REPLACE. Persistence receives IDs/timestamps;
it does not allocate them or choose ownership. IDs/ownership are immutable on ordinary
updates. No new domain entity, source abstraction or general persistence framework.

### 2.1 gpx_imports

| Field | SQLite / meaning |
|---|---|
| id | TEXT PK; import I |
| territory_id | TEXT FK → territories.id; owner |
| importing_observer_id | TEXT NULL FK → observers.id |
| original_object_id | TEXT UNIQUE; original O, immutable |
| original_sha256 | TEXT; exact-byte descriptor |
| original_byte_length | INTEGER; exact length >0 |
| display_name | TEXT NULL; context, not identity |
| gpx_version | TEXT; preserved version |
| parser_profile_version | TEXT; preserved interpretation version |
| imported_at_epoch_ms | INTEGER; technical import time |
| creator | TEXT; canonical root creator |
| source_name | TEXT NULL |
| source_description | TEXT NULL |
| author_name | TEXT NULL; GPX author, not importing Observer |
| publication_state | TEXT; PENDING or READY |
| request_uuid | TEXT NULL UNIQUE; completed-result correlation |

Two fixed warning groups use prefixes unsupported_content_ and unsupported_time_.
Each expands to count INTEGER, first_scope TEXT NULL, first_track_ordinal INTEGER
NULL, first_segment_ordinal INTEGER NULL and first_sample_ordinal INTEGER NULL.
Count is NOT NULL and >=0. At count0 all first_* fields are NULL; at count>0 scope
is required and is a GpxProfileScope token. Present ordinals are nonnegative.
Preserve the actual GpxProfileWarning address; do not invent a stricter address
interpretation. No unbounded warning collection or copied attacker text.

UNIQUE in field tables and the index lists describes the same single unique index
per field, not duplicate inline constraints plus extra indices.

Indices: UNIQUE original_object_id; UNIQUE nullable request_uuid;
(territory_id, publication_state); importing_observer_id; nonunique original_sha256.
The SHA index does not choose duplicate lookup/confirmation scope.

O is logical original-object identity, not SHA or a physical filename. Distinct
imports can have equal bytes and different O. A future physical blob deduplication
mechanism must not merge imports or O identities; no blob-sharing/refcount scheme
is introduced here. Final file location remains a future managed-store concern.

### 2.2 gpx_source_tracks

| Field | SQLite / meaning |
|---|---|
| id | TEXT PK; T |
| import_id | TEXT FK → gpx_imports.id |
| ordinal | INTEGER >=0 |
| name | TEXT NULL |
| description | TEXT NULL |

UNIQUE(import_id, ordinal), also serving parent lookup.

### 2.3 gpx_track_segments

| Field | SQLite / meaning |
|---|---|
| id | TEXT PK; S |
| track_id | TEXT FK → gpx_source_tracks.id |
| ordinal | INTEGER >=0 |

UNIQUE(track_id, ordinal).

### 2.4 gpx_track_samples

| Field | SQLite / meaning |
|---|---|
| segment_id | TEXT FK → gpx_track_segments.id |
| ordinal | INTEGER >=0 |
| latitude | TEXT; canonical exact decimal |
| longitude | TEXT; canonical exact decimal |
| elevation | TEXT NULL; canonical exact decimal |
| time_raw | TEXT NULL |
| time_status | TEXT |
| time_epoch_seconds | INTEGER NULL |
| time_nanos | INTEGER NULL |

Composite PK(segment_id, ordinal) supplies segment lookup. No sample UUID or
surrogate sample key.

### 2.5 gpx_import_jobs

| Field | SQLite / meaning |
|---|---|
| request_uuid | TEXT PK; accepted request identity, no extra job UUID |
| territory_id | TEXT FK → territories.id; captured owner |
| importing_observer_id | TEXT NULL FK → observers.id |
| phase | TEXT; last durably recorded phase, §4 |
| intent | TEXT; CONTINUE or ABORT |
| original_object_id | TEXT NULL UNIQUE |
| original_sha256 | TEXT NULL |
| original_byte_length | INTEGER NULL |
| import_id | TEXT NULL UNIQUE FK → gpx_imports.id; RESTRICT |

Indices: territory_id; importing_observer_id; UNIQUE original_object_id and import_id.
URI, absolute paths, raw exceptions, retry counters and operational timestamps are
not added. Future managed paths derive from internal IDs. A locator genuinely
required by file lifecycle needs explicit justification in that later contract.

## 3. Invariants and transactional boundaries

SQL/Room enforce PK/FK/NOT NULL/UNIQUE plus basic structural invariants through the
uniform mechanism in §6: enum membership, ordinal/count signs, descriptor pairing,
time nullability, warning shape, PENDING ⇒ request_uuid IS NULL, journal matrix.
No SQL decimal validator, numeric CAST-based coordinate validation or semantic
normalization is allowed. Structural checks do not replace Kotlin validation.

The Kotlin aggregate boundary reuses closed value types for exact decimals/ranges,
metadata/time invariants and contiguous 0..N-1 ordinals including empty nodes. It
validates complete graph, journal/owner/O/descriptor agreement and immutability of
published canonical data. Internal DAO visibility alone is not proof: every supported
write/read mapper has tests. Invalid stored semantic data must fail explicitly,
never be silently repaired or returned as a partial graph.

First-slice persistence operations only:
1. Save PENDING graph and journal association.
2. Read a complete graph from a consistent DB snapshot, with explicit internal versus
   ordinary READY-only access.
3. Remove operation-owned PENDING graph.
4. Derived counting over persisted rows.
5. Structural journal validation.

No copying, confirmation, allocation, recovery runner or publication orchestration.
For PENDING deletion, verify not READY, detach journal.import_id if needed, delete
samples→segments→tracks→import in one transaction, and retain journal/O/descriptor
for future file cleanup. Do not provide a general READY-delete API in this slice.
Future explicit import deletion must coordinate any remaining journal and preserve
accepted dependent-link guards; no removal workflow or tombstone table is added.

The future READY transaction must atomically validate complete canonical graph,
ownership, admission and request admissibility; set READY and request correlation;
and update journal to READY. Complete means the validated immutable graph and all
its children are present before the transition, not merely a root row. First slice
exposes no production READY-publication API; READY fixtures are test-only.

### 3.1 Aggregate accounting — C3

No persisted quota counters. Provide facts separately: imports per captured Territory
and publication state; samples via segment→track→import with explicit state filter;
sample count for a candidate import. Do not confuse PENDING visibility with resource
accounting. G11 is READY inventory plus candidate. Exact G12 persisted-inventory plus
candidate formula and double-count exclusion remain future admission work.

Derived counting is NOT a concurrency-safe admission mechanism. Consistent quota
checking and READY publication, including serialization/revalidation, belong to the
future Coordinator Slice. No admission serialization is implemented in this slice.

## 4. Journal and request correlation

Phase records SELECTED, COPYING, COPIED, PARSING, PARSED, FILE_PUBLISHED or READY.
Intent is independently durable: ABORT means cleanup-only and overrides resumption;
retain last phase for recovery. This separates operation intent, PENDING/READY graph
state, last recorded original-file step and completed-request correlation.

| Phase | O | SHA/length | import_id when CONTINUE |
|---|---|---|---|
| SELECTED | NULL | both NULL | NULL |
| COPYING | required | both NULL | NULL |
| COPIED | required | both required | NULL |
| PARSING | required | both required | NULL or PENDING import |
| PARSED | required | both required | complete PENDING import |
| FILE_PUBLISHED | required | both required | complete PENDING import |
| READY | required | both required | READY import |

O is fixed together with entering COPYING, before creating its file. SHA/length
are written together, not separately. ABORT retains the descriptor required by its
last phase; import_id may become NULL after transactional graph cleanup. READY
requires CONTINUE. No ABORT journal may refer to READY. Phase is not proof that a
file still exists; future reconciliation verifies ownership, bytes and actual state.

Accepted cancellation rule: pre-READY compensate; post-READY preserve committed
result. The accepted future protocol serializes ABORT versus READY transitions. A caller
cancellation after commit is not authority to delete READY data. If abort intent
cannot be persisted, durable cancellation cannot be promised. First slice proves DB
state/rollback invariants, not the full race, file cleanup or restart protocol.

### 4.1 D1 concretization — OWNER-ACCEPTED

Globally UNIQUE non-NULL request_uuid among imports; multiple NULL values allowed.
PENDING always has NULL. A normal completed local import receives request UUID in
the READY transaction; Complete Backup/Snapshot restore creates READY with NULL.
Correlation lasts for the import lifetime: no TTL/background clearing. READY journal
cleanup never clears it; future physical import deletion removes it with that row.
No permanent tombstone is introduced and request UUID reuse is not authorized.

Consumed-request handling and read-only result lookup are separate. Lookup does
not handle the request again or create an import.

| Situation | Required boundary |
|---|---|
| Same request associated with same READY import | Existing result may be read |
| Request already associated with another import | Fail closed, no overwrite |
| Request already has an active journal | Do not create another job |
| No correlation found | Not proof of request admissibility |
| New explicitly confirmed import | New request and fresh persistent IDs |

After logical restore, NULL correlation does not make missing/obsolete/consumed
requests valid. Existing captured-request rules still apply. Android Auto Backup
can transfer both correlation and jobs under D2; that is distinct from logical
Complete Backup/Snapshot restore. Future reconciliation precedes GPX access.

## 5. Exact values and time representation

Reuse domain/gpx/GpxCanonicalValues.kt, GpxDecimal and GpxSampleTime.
Write decimal canonicalPlain as TEXT. No Float/Double/REAL canonical storage; source
lexical spelling stays in original bytes, not extra canonical columns.

Read TEXT through GpxDecimal.fromDecimal(BigDecimal(text)); require reconstructed
canonicalPlain == stored TEXT; use existing GpxSample/value rules for coordinate
ranges. This is persistence mapping, not a second decimal validator. Malformed,
noncanonical or out-of-range data is an explicit integrity failure, not a repair.
Do not reapply original XML lexical ceilings to canonical TEXT: normalization may
add a leading zero to a valid .123... spelling. Valid closed-model output must remain
roundtrippable without changing parser limits or closed production behavior.

| Time status | time_raw | seconds/nanos |
|---|---|---|
| MISSING | NULL | both NULL |
| UTC | NOT NULL | both NOT NULL |
| NO_ZONE | NOT NULL | both NULL |
| UNSUPPORTED | NOT NULL | both NULL |

UTC seconds range -62135596800..253402300799; nanos0..999999999. Preserve raw spelling;
existing Kotlin value type checks its Unicode/length invariant. Reconstruct stored
GpxSampleTime fields directly, never reinterpret raw through a newer parser. No global
Instant converter change. Technical imported_at_epoch_ms is separate from sample time.

## 6. Migration 14→15 and structural triggers — C1

OBSERVED baseline v14; reverify before implementation. The accepted additive migration
adds exactly these five tables/indices/constraints, preserves legacy rows/identities/
sequence state, registers explicit14→15, exports schema15, and has no destructive
fallback. Extend Territory/Observer deletion guards for both imports and jobs.
No UI/coordinator/user import entry point.

Room2.8.4 @Entity has PK/FK/indices but no arbitrary CHECK declaration. Do not install
migration-only CHECK constraints absent from fresh DB. Accepted mechanism: Room
PK/FK/NOT NULL/UNIQUE plus small BEFORE INSERT/UPDATE validation triggers, with
identical definitions installed by migration and fresh onCreate. Use a single source
of SQL definitions AND expected names/definitions for both paths. No universal
trigger framework and no SQL decimal validator.

Structural predicates cover enums, nonnegative ordinals/counts, descriptor pairing,
time status/nullability/ranges, warnings, PENDING correlation and local journal
phase/intent matrix. Cross-table graph/owner/descriptor completeness remains an
explicit aggregate transaction responsibility. Constraint failures must explicitly
reject the operation; no catch-and-continue or silent acceptance of invalid data.

Before GPX persistence is used, fail closed unless the actual GPX validation trigger
set and definitions match the expected versioned set. Inspect database schema
metadata, not only Room identity hash, trigger names, or IF NOT EXISTS success.
The comparison procedure must detect a same-name changed definition and missing
or unexpected GPX-owned validation trigger; it must not silently repair them on use.
Define SQL representation deterministically; if metadata normalization is necessary,
it must not erase semantic differences. A failed gate blocks GPX persistence, not
merely logs a warning or reports success. Do not claim this checks arbitrary hostile
modification after verification or replaces semantic mapper validation.

Every future migration rebuilding a GPX table must recreate its required triggers
from the same versioned definitions and pass the gate before GPX use. Fresh/migrated
parity includes actual trigger definitions and behavior in addition to Room exported
schema. Required tests: missing trigger, altered same-name definition, correctly
restored trigger after table reconstruction, and invalid write rejection. No trigger
implementation or migration is executed by this documentation task.

Prior technical references, not new runtime proof:
- [Room Entity](https://developer.android.com/reference/androidx/room/Entity)
- [Room Callback](https://developer.android.com/reference/androidx/room/RoomDatabase.Callback)
- [MigrationTestHelper](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper)
- [SQLite UNIQUE/NULL](https://www.sqlite.org/lang_createindex.html)
- [SQLite structural constraints](https://www.sqlite.org/lang_createtable.html)

## 7. Protection/restore compatibility — C2

**Status 2026-10-17:** this compatibility contract moves to the future unified local
Backup/Restore and is no longer a user-import activation gate
([OWNER decision](decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17)).
The obligations below remain requirements of that future stage; H05 remains OPEN.

| Data | Complete Backup / Repository Snapshot | Android Auto Backup under D2 |
|---|---|---|
| I/T/S/O, parent/ordinal relations | preserve | transferred with DB |
| canonical values/metadata/provenance/warnings | preserve | transferred with DB |
| original descriptor | preserve | transferred with DB |
| managed original bytes | required separate protection payload/blobs | future file-lifecycle rules |
| operational journal | exclude | may transfer with DB |
| request_uuid | exclude; corresponding restore NULL | may transfer with DB |
| device-local visibility | exclude; restored default visible | separate existing settings policy |

request_uuid is explicitly excluded from the canonical comparison set for Stage1A
§14 Complete Backup/Repository Snapshot roundtrips. Compare preserved logical IDs,
addresses/order/values/provenance and exact original bytes/descriptors; do not require
operational request correlation equality after those restores. This does not remove
any canonical/provenance requirement and does not alter Android Auto Backup rules.

Future logical restore inserts preserved IDs/values, never reallocates I/T/S/O or
calls GPX parser, never imports old job rows, sets request_uuid NULL, verifies originals
and uses compatible recoverable publication. No V9/V4 codec or restore implementation
is part of this foundation slice. Existing V8/V3 does not automatically cover GPX.

Android Auto Backup operates on files/database domains, not selected Room tables:
[official backup documentation](https://developer.android.com/identity/data/autobackup).
D2 intentionally leaves that mechanism unchanged. Restored journal reconciliation and
future managed-original/staging rules must be settled before file lifecycle activation.
The foundation must not create a production path admitting user GPX data ahead of
agreed protection/restore gates; tests are not user capability activation.

**AMENDED 2026-10-17:** the final sentence above is superseded — GPX protection/restore
and H05 move to the future unified local Backup/Restore and no longer gate user import
([decisions.md](decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17)).
The managed-original, integrity, ownership, cancellation and recovery rules remain
mandatory; H05 remains OPEN.

## 8. Acceptance tests and build gates — future, NOT RUN

Migration/schema:
1. Migrate populated v14 to15 without losing/changing legacy IDs, relations, values
   or sequence state; verify foreign_key_check.
2. Compare fresh15 and migrated15 tables/columns/PK/FK/indices and trigger definitions/
   behavior; export schema; no destructive fallback; test supported migration chain.

Exact data:
3. Exact decimal roundtrip including long fraction and normalized spelling growth.
4. Reject malformed/noncanonical/out-of-range stored decimals through each supported
   mapper; closed value types unchanged.
5. All four time statuses, raw text, nanoseconds and nullability roundtrip.
6. Empty tracks/segments, singleton and source ordinals survive.
7. Warning count/first address roundtrip.

Integrity/transactions:
8. PK/FK/parent+ordinal uniqueness; repeated SHA allowed; nullable request uniqueness.
9. Reject PENDING with request UUID and basic structural invalid states, including
   direct SQL tests that do not rely solely on Kotlin constructors.
10. Validate semantic mapper failures separately from SQL structural constraints.
11. Territory/Observer guards cover imports and jobs; FK is the final restriction.
12. Batch failure rolls back without an accessible partial graph; graph reads use
    consistent snapshots. PENDING excluded from ordinary reads and READY capture projection.
13. PENDING cleanup preserves journal descriptor and cannot delete READY through its API.
14. INSERT/UPDATE journal matrix, ABORT precedence and descriptor nullability tests.
15. C1 fail-closed tests for missing/altered trigger; correct reconstructed triggers
    restore access; fresh/migration definitions match; constraint failure is explicit.

Scope:
16. No production user-import route, production READY-publication API, coordinator,
    filesystem lifecycle, recovery runner or backup codec changes. READY fixtures test-only.
17. Existing backup regression must not be described as GPX FULL qualification.
18. Protection comparison tests in later codec/restore work exclude request_uuid per C2;
    no full restore test is claimed here. C3 counting is not admission qualification.

Stage1A §14 gates for later authorized execution: targeted persistence JVM tests;
full :app:testDebugUnitTest; :app:compileDebugKotlin; :app:compileBetaKotlin;
:app:compileReleaseKotlin; :app:compileDebugAndroidTestKotlin; :app:assembleDebug;
:app:assembleDebugAndroidTest; :app:lintDebug; relevant existing regression; actual
Android Room migration/constraint/transaction proof on required endpoints.

Use frozen inventories/digests, same APK bytes on sequential API29/API37 and pre/post
source correspondence, with task-scoped diff hygiene including untracked inputs.
Exact execution scope requires separate OWNER authorization. No physical-device
permission is implied. None of these criteria closes full recovery, filesystem
durability, Backup/Snapshot restore, H05 or Stage1.

## 9. Explicit next-slice boundaries

Managed-original store and its backup-placement rules; actual coordinator/ID allocation/
request handling; duplicate prompt scope; G11/G12 admission concurrency; full cancel/READY
race and reconciliation; file/Room recovery/owned cleanup; Complete Backup/Snapshot
codecs and restore adapters; real protection capture; UI/visibility/MapLibre/Registry;
aggregate capacity and remaining Stage1 exit gates are future-owner work.

Do not repair closed-slice INFO findings, create another checklist, second decimal
validator or persisted quota counters. No dependency/version/signing changes. This
contract neither authorizes implementation nor staging/commit/push.

## 10. Acceptance state and unresolved questions

OWNER explicitly accepted this Persistence Foundation contract: physical schema,
correlation lifetime, structural-trigger mechanism, journal matrix, C1–C3 and the
non-activation boundary. D1/D2 require no amendment. Implementation, recovery and
protection/restore work are not completed. Exact G12 formula,
duplicate prompt scope and file recovery actions can remain for later contracts;
the physical schema does not silently choose them.

The first slice remains five-table persistence foundation with exact values and
operational journal, no user import activation and no protection-completion claim.
Stage1 stays OPEN. Documentation preparation creates no runtime/test evidence.

**Status update 2026-10-17:** protection/restore no longer gates user import; it moves
to the future unified local Backup/Restore and H05 remains OPEN there
([OWNER decision](decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17)).
The no-user-import-activation statement above describes this foundation slice, not a
current activation gate.
