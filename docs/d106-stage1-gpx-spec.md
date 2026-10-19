# D106 Stage 1 — external GPX import, storage and protection specification

Status: **ACCEPTED WITH AMENDMENTS — OWNER · 2026-10-12**.
Stage 1A specification only; GPX functionality is **NOT IMPLEMENTED**.

This is the single detailed normative Stage 1 specification, specializing
[D106](decisions.md#d106--territory-user-cartography-domain-boundaries-and-staged-roadmap).
Decisions/architecture link here; checklist/handoff record execution status only.
Do not duplicate this contract into those documents or replace Stage 2–4 design.

Labels: OBSERVED = inspected baseline code; DOCUMENTED = existing project constraint;
ACCEPTED = OWNER-approved contract; DERIVED = required integration consequence;
PROPOSED = engineering detail not frozen as an API; UNKNOWN = evidence still needed.
Acceptance is not implementation or device proof. Numeric ceilings are provisional,
and the visible-set contract in §9 supersedes the original hidden-residency proposal.

## 1. Scope, baseline and prerequisite

ACCEPTED scope: **EXTERNAL GPX IMPORT / PERSISTENCE / OFFLINE FIELD VIEWING**.
Territory owns TrackImport → SourceTrack → TrackSegment → ordered samples and exact
immutable original bytes. Area is device-local coverage, never GPX ownership.
Area replacement/removal cannot remove or hide GPX by ownership side effect.

In scope: multiple tracks/segments, real gaps, nullable supported values, stable
identity/addressing, restart, minimal visibility, and byte-identical original
preservation. **AMENDED 2026-10-17:** separate GPX Complete Backup/Repository
Snapshot coverage and restore move to the future unified local Backup/Restore and
are not a Stage 1 or user-import activation gate (see
[decisions.md](decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17)).

Out of scope: UserPath, PathSourceLink, FieldPoint, RasterLayer, recorder, editing,
smoothing, snapping, interpolation, merging, route planning, sync, GPX removal UI,
Stage 2–4, fifth toolbar action or changes to AREA_VIEW coverage workflow.

OBSERVED baseline: task checkout HEAD e2d74cc609efde7f2e18a38947ccbcf226c5a351;
Stage 0 production 1e88fe8765f1ab7a32ae6aeff129635d2d936185, verification commit
ab24fff9591d0c4cdb4db59aca063825ef845fa3. Room v14, Complete Backup V8, Snapshot V3.

OWNER-ACCEPTED prerequisite: STAGE0_VERIFICATION_PASS within the declared emulator
+ Samsung scope. [Lifecycle evidence](map-overlay-lifecycle-evidence.md) separately
retains KNOWN PRE-STAGE0 EMULATOR NATIVE BLOCKER, ROOT CAUSE UNKNOWN.
It is not a Stage 0 regression or progression blocker. Do not reopen it or extend
acceptance to visual readiness, performance, GPS/heading, ergonomics or general
MapLibre stability.

OBSERVED integration:
- Room uses Entities.kt, Daos.kt, explicit migrations, exported schemas, UUID TEXT,
  indexed FKs and ABORT inserts. Its common Instant converter is millisecond-only.
- Managed attachment/media stores demonstrate checked paths, streamed copy/hash
  and operation-owned cleanup, not a GPX coordinator or cross-system atomicity.
- OpenExchangeDocument exists. MapPackage ownership handling after picker return
  is not a safe substitute for GPX ownership captured before launch.
- DataStoreMapDataDisplayStore persists per-Territory research display state.
- MapOverlayRegistry / MapLibreOverlaySession.sync / BeeMap runtimeOverlays and
  opaque scope provide the existing generic presentation path.
- BackupCore streams files but currently recognizes only existing managed-media paths.
- Snapshot V3 stores canonical metadata with external blobs. No Snapshot-to-Room
  restore service exists; its adapter is a required Stage 1 integration dependency.

## 2. GPX parsing profile

ACCEPTED:
| Input | Stage 1 result |
|---|---|
| GPX 1.0 / 1.1 | Supported track profiles (OWNER amendment 2026-10-19) |
| trk | Canonicalized |
| rte / wpt | Not canonicalized; never converted into tracks |
| Mixed document | Import supported tracks with explicit unsupported-content warning |
| No supported track samples | Reject entire import |
| Multiple tracks/segments | Preserve each node and original XML order |
| Empty track/segment | Preserve canonical structure and ordinal |
| Singleton segment | Preserve; may render as a point |
| Missing elevation/time | Nullable |
| Invalid/missing coordinates or malformed XML | Reject entire import |

Namespace URI/version must match exactly: http://www.topografix.com/GPX/1/0 + 1.0
or http://www.topografix.com/GPX/1/1 + 1.1; prefix is irrelevant. Only supported GPX elements in their proper structural
context count; vendor elements with the same local name are not GPX nodes.
Validate required profile fields, including root creator. Supported scalar duplicates
and malformed critical structure fail explicitly.

GPX 1.1 coordinates: latitude -90 ≤ value ≤ 90; longitude -180 ≤ value < 180.
Non-finite values are invalid. Do not normalize +180 or drop invalid samples:
that could change geometry or bridge gaps.
Primary reference: [official GPX 1.1 schema](https://www.topografix.com/gpx/1/1/).

Unknown attributes/elements/extensions remain in exact originals, not canonical
fields. Skip with bounded parsing and an aggregated unsupported-content summary,
not an unbounded per-sample warning collection.

## 3. Encoding and XML/input security

ACCEPTED: UTF-8 and UTF-16 XML under the BOM/declaration contract; explicitly reject
other encodings. Present BOM/declaration must match decoded input; contradictory
or invalid input fails. Never normalize, transcode or rewrite original bytes.
Provider filename is metadata, never a managed path.

DTD, external entities and external resource/network resolution are forbidden.
Unsupported parser security configuration fails closed.

Use bounded platform XmlPullParser, namespace processing enabled, DTD processing
and validation disabled with configuration checked. Use nextToken and explicitly
reject DOCDECL; do not register custom entity replacements/resolvers. Only standard
XML escapes/numeric references are supported. Unsupported entity references fail.
No external schema, stylesheet, DTD or network resolution.
[Android XmlPullParser API](https://developer.android.com/reference/org/xmlpull/v1/XmlPullParser)
documents why disabled DOCDECL processing alone is not an explicit DTD rejection.

Copy/parse are cancellable; check streamed reads, parser events and before batch
writes/publication. No DOM/readText/base64 for originals. UNKNOWN: the parser may
allocate a token before application checks. File ceilings bound input, but do not
prove constant-memory parsing or preallocation token safety. Security/encoding
fixtures and platform memory measurements remain required.

## 4. Territory workflow and picker

ACCEPTED: capture immutable request UUID, target Territory UUID and applicable
importing Observer before picker launch. Never use the current Territory at URI
return as ownership. A pending import never moves between Territories.

Explicit request → handle → consume, with saved request context. Missing/obsolete/
consumed request fails or cancels explicitly. Check captured Territory/Observer
existence again at publication. Current Territory change cancels incomplete work,
never retargets. READY is the success boundary; later navigation does not undo it.

PROPOSED picker: existing OpenDocument with */*, clear GPX purpose and authoritative
content/profile validation; do not depend on one provider MIME. No new exchange-folder
contract, broad storage permission or source write/delete operation. Managed copy
removes source URI dependency after success.
[Android SAF workflow](https://developer.android.com/training/data-storage/shared/documents-files).

## 5. Canonical identity, model and provenance

ACCEPTED:
| Object | Identity/address |
|---|---|
| TrackImport | Stable UUID I, Territory ownership |
| Original object | Stable internal object identity O |
| SourceTrack | Stable UUID T; unique import + XML track ordinal |
| TrackSegment | Stable UUID S; unique track + XML segment ordinal |
| TrackSample | Composite address Segment UUID S + XML sample ordinal |

Full source address: I/T/S/sampleOrdinal. No sample UUID unless later evidence
demonstrates a need. Empty nodes participate in XML ordinal mapping.
Restart, restore and rebuild preserve logical identity/addressing.

SHA-256 identifies content/integrity, not logical source. Filename and import time
are not identity. Same bytes may be imported again after explicit confirmation;
new import/track/segment UUIDs result. No hash uniqueness or silent logical dedup.
Repository physical blob dedup must not merge logical imports.

Logical fields:
- TrackImport: id, territoryId, original identity, byte length/SHA-256, nullable
  display name, GPX version, parser-profile version, technical importedAt,
  nullable importing Observer, supported source metadata, bounded warning summary.
- SourceTrack: id, import parent, XML ordinal, supported name/description.
- TrackSegment: id, track parent, XML ordinal.
- TrackSample: segment/ordinal, latitude/longitude, nullable elevation, raw time,
  nullable UTC seconds/nanos and explicit time-support status.

GPX author, importing Observer, technical import time and sample time are distinct.
No automatic factual confirmation. Canonical graph/provenance/originals are durable;
GeoJSON/render objects/indexes are rebuildable; import jobs are operational.
No independent destructive child lifecycle or empty future PathSourceLink schema.

## 6. Timestamp and attribute semantics

Supported canonical subset: coordinates, optional elevation/time, root creator/version,
source name/description/author name and track name/description. Other content remains
in originals with bounded warnings.

Keep XML order, never timestamp-sort. Duplicate/non-monotonic times are preserved.
Missing time is null. Timezone-less input retains raw lexical value with null canonical
UTC and explicit status; never reinterpret it as device local.

Canonical UTC seconds+nanos avoid the existing millisecond-only Room converter.
Supported subset: explicit Z/offset, representable years 1–9999, at most nanosecond
fraction precision. Unsupported/unrepresentable forms, including leap-second/excess
precision, retain raw time with null canonical value and warning; no silent truncation.
Missing elevation is null; present malformed/non-finite elevation rejects the import.

## 7. Original storage and publication/recovery

Dedicated GPX store follows existing checked-path/streamed-copy patterns.
PROPOSED paths: durable files/gpx-staging/<jobUUID>/... and
files/gpx-originals/<originalObjectUUID>.gpx. Exact names/API are not frozen.

App-private storage, internal IDs, checked containment/regular files, no symlink
escape/overwrite. Stream exact bytes and length/SHA, close/synchronize before final
transition, cleanup only owned files. Source URI is unnecessary after READY.

ACCEPTED recoverable protocol, not a cross-Room/filesystem atomic transaction:
SELECTED → COPYING → COPIED (bytes/hash/length) → PARSING (invisible PENDING graph)
→ PARSED (complete validated graph) → FILE_PUBLISHED (verified final original)
→ READY (Room publication) → job cleanup.

Durable operational job journal; expose only coherent READY imports. Serialize
live jobs/reconciliation/restore appropriately.

| Interruption | Required outcome |
|---|---|
| Copy incomplete | Remove owned partial; no accessible import |
| Before/during incomplete parse | Remove pending graph/staging; no automatic URI resume |
| Complete PARSED graph | May finish verified staging without reparse/new IDs |
| DB prepared, final transition incomplete | Finish verified transition or fail closed |
| Final original exists, READY incomplete | Verify owned object/bytes and finish publication |
| READY committed, journal remains | Cleanup journal without deleting published original |
| Cancellation/I/O before READY | Compensate owned pending records/files; retain journal until cleanup completes |
| Cancellation after READY | Do not misreport committed success as cancellation rollback |
| Conflicting/unknown final object | Never adopt or overwrite it |

Reconcile before exposing READY data at startup/first access. In-flight imports are
not completed protection records.

ACCEPTED missing-original policy: retain canonical geometry and viewing with explicit
internal degraded/unavailable-original diagnostic state (no original-status warning
in the approved display list). FULL backup/snapshot fails/declines while required
bytes are unavailable; no omission or identity-regenerating reparse. Verify originals
for protection/restore; do not hash on recomposition.

## 8. Room and migration shape

Start v14, not a frozen Stage 1 invariant. If baseline is unchanged, derive sequential
explicit migration 14→15; recheck before implementation. Additive schema/export/tests,
preserve existing research graph, no destructive fallback or global time-converter change.

Canonical imports/tracks/segments/samples plus operational journal. Indexed Territory/
Observer RESTRICT FKs and extended deletion guards. Aggregate-controlled child writes/
deletes; unique parent+ordinal, non-negative ordinals, coordinate/time constraints,
ABORT conflicts, no hash uniqueness. Pending rows are invisible/excluded from capture.
Restore preserves exact READY IDs/values. Kotlin/DDL names are engineering details.

## 9. Visible overlay contract — OWNER amendment B

ACCEPTED default:
READY imports → apply Territory + visibility → VISIBLE presentation set
→ materialize bounded GeoJSON/MapOverlay declarations → existing Registry/session.

Visibility and native residency are distinct. Hidden imports need not retain full
GeoJSON/Sources/Layers merely with visible=false. Prefer removing feature declarations/
unregistering through existing lifecycle. Show may build/register fresh objects.
Bounded hidden residency is a future optimization only after measured benefit and
acceptable memory cost; no Registry semantic change.

Feature owner knows Territory; Registry sees opaque scope/revisions. PROPOSED one
overlay/import, gpx-<importUUID>, local keys geometry/lines/singletons; Registry builds
full IDs. Separate LineString per segment with ≥2 samples, singleton Point/
CircleLayer (OWNER-approved 2026-10-19), no empty geometry. No bridges, smoothing or canonical decimation.

Immutable latest snapshots; captured Territory/epoch/revision gates stale work.
A cannot republish into B. Fresh SDK objects on reload/re-registration, no stored
SDK references. Use Stage 0 restoration, never GPX setStyle/raw Style mutation.
No missing generic capability was identified.

Importability does not imply simultaneous MapLibre residency. Explicit presentation
ceiling failure/defer, never silent partial-track omission or canonical data loss.

## 10. Visibility and UI

ACCEPTED — OWNER amendments 2026-10-18/19: device-local per-Territory explicit
selected import IDs in the existing Settings DataStore. New imports default OFF;
import/storage and display are independent. Persistence must succeed before applying
selection to presentation. Visibility is not canonical provenance and is excluded
from Complete Backup/Snapshot. The former default-visible/hidden-IDs rule is superseded
for ordinary new imports. The existing restored-default-visible policy is unchanged and remains future work
with unified Backup/Restore; this implementation does not implement restore.

Entry: Визуализация → Данные на карте → Треки → Показать трек. One file-level
switch selects its entire supported canonical graph; multiple files may be visible.
Singleton segments are points. No summaries or original-status warning in this list:
a missing/corrupt original with valid canonical geometry keeps displaying; canonical
failure produces a clear per-file error. Original status may be internal diagnostics.
Import picker/progress/cancel/duplicate behavior is unchanged, success only at READY.
Restart preserves imports and local visibility. Approved visual/behavior contract:
[GPX display v1 PNG+MD](ui/mockups/gpx-display-v1.md).

No fifth action, AREA_VIEW controls, removal UI or research-filter redesign.
Behavioral/textual specification is not an approved visual artifact. New production
UI still requires existing OWNER-approved PNG+MD mockup workflow.

## 11. Four budget domains — OWNER amendment A

Numbers are **PROVISIONAL SAFETY / TEST CEILINGS**, not final production capacity
guarantees. Record configured ceilings/fail-closed outcomes in implementation tests.
No final user-facing limits before corpus measurement and relevant physical evidence.

| Domain | Provisional ceilings / outstanding evidence |
|---|---|
| A — INPUT SAFETY | Original16 MiB; tracks128, segments4096 incl empty, samples100000/import; XML depth32, attributes64/element, events2000000; semantic text1024 chars, used token8192, coordinate/time lexical128 |
| B — CANONICAL STORAGE | Proposed imports1024 / samples200000 per Territory; managed original storage, available space and application-wide aggregate constraints still require measurement/bounds |
| C — PRESENTATION | Simultaneous GeoJSON geometry, visible Sources/Layers and memory-sensitive bounds are not numerically established; visible set only, measured rebuild/reload, explicit ceiling handling |
| D — PROTECTION | Observed Backup64 entries, metadata16 MiB/entry and64 MiB total; proposed V9 GPX-samples64 MiB/entry, total256 MiB, entries4096, other entry limits unchanged. Snapshot ZIP64 MiB/uncompressed128 MiB. Original/blob bytes and staging capacity accounted separately |

One domain does not imply another. No silent ceiling increase or record omission.
Final values require evidence-backed closure, not specification acceptance.

Corpus: small field track; multiple tracks/segments and empty/singleton cases;
near-limit import; duplicate same-byte imports; many originals stressing entry count;
aggregate multi-Territory data with existing research/media; metadata near/over limits;
managed-original/staging space failure; visible-set memory/hide/show/reload on device.

Illustrative calculation, not measurement: sample XML96 bytes; canonical JSONL
about218 bytes with newline;100000 samples ≈9.16 MiB XML but20.79 MiB metadata,
exceeding current16 MiB entry limit. Streaming metadata is necessary; exact format
sizes and final capacities must be measured.

## 12. Complete Backup

**DEFERRED 2026-10-17:** GPX Complete Backup protection moves to the future unified
local Backup/Restore stage and is not a Stage 1 exit or user-import activation gate
([OWNER decision](decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17)).
The technical requirements below remain obligations of that future stage.

DERIVED extension to existing versioned system. Proposed next contract V9 subject
to baseline recheck: four canonical collections, typed original descriptors and streamed
file payloads. Closed paths, exact hash/length, streamed sample JSONL. No original
JSON/base64 transport, silent optional V8 omission or parallel backup system.

Capture coherent READY roster/graph and verify every required original; missing,
corrupt or over-budget data prevents FULL success/publication.

Restore validates graph/inventory/digests, stages originals and inserts preserved
UUIDs/addresses/values via recoverable publication. Never invoke GPX parser.
Current empty-destination policy, no merge. Legacy V1–V8 has empty GPX graph; old
readers reject new required contract. Post-commit settings failure follows retry
semantics and never deletes committed restored originals.

## 13. Repository Snapshot

**DEFERRED 2026-10-17:** GPX Repository Snapshot protection/restore moves to the
future unified local Backup/Restore stage and is not a Stage 1 exit or user-import
activation gate ([OWNER decision](decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17)).
The technical requirements below remain obligations of that future stage.

Proposed V4: canonical GPX JSONL, typed original references (object identity/hash/length),
external blobs and verified FULL protection. Existing Media/<SHA>.bin transport can
carry exact bytes; physical hash dedup never merges canonical imports.
Snapshot ZIP is metadata, not original transport or standalone complete restore material.

FULL capture ingests/verifies required originals before publication; missing/corrupt
blob fails closed. Metadata-only is not full GPX protection. V1–V3 remains readable
with empty GPX graph; new required records are explicitly versioned.

DERIVED dependency: Snapshot-to-Room restore adapter is absent at baseline. Resolve/
verify blobs, materialize preserved originals and insert validated canonical graph
through compatible restore machinery, preserving existing snapshot data too.
No GPX reparse or parallel backup/merge platform. Only disposable indexes may rebuild.

## 14. Verification and exit gates

Specification acceptance does not execute these gates.

| Gate | Required evidence |
|---|---|
| Parser/security | Versions/namespaces, malformed XML/coordinates, DTD/entities, encoding, unknown content, all input ceilings/cancellation |
| Identity/order | Multiple tracks/segments/gaps, empty/singletons, nullable values, duplicate/non-monotonic time, distinct same-byte identities |
| Storage/recovery | Interruption/fault at every boundary, restart reconciliation, missing/corrupt originals, owned cleanup, no accessible partial graph |
| Room | Legacy-data-preserving migration, exported schema/FKs/order/uniqueness, Territory/Observer guards |
| Backup | Exact graph/original roundtrip, missing/corrupt payload, legacy compatibility, budgets/capacity, no omission |
| Snapshot | Verified references/FULL capture, missing/corrupt blobs, identity-preserving restore, legacy compatibility |
| Overlay/native | Real consumer through Registry, gaps, visible-set residency, hide/unregister/show, reload/fresh objects, stale Territory rejection, disposal |
| UI | A→picker→B cancellation, progress/cancel/errors/READY success, Territory isolation, restart visibility, approved mockup comparison |
| Regression | Research filters/markers/preview, FIELD/AREA_VIEW, online/offline, Area editor/coverage and lifecycle |
| Build | Relevant/full JVM; Debug/Beta/Release compile; androidTest compile/assemble; assembleDebug; lint; diff hygiene |
| Physical | Separately authorized DEV Samsung smoke, visual artifact comparison and declared corpus/presentation measurements |

**AMENDED 2026-10-17:** the GPX Backup and Snapshot rows above are no longer Stage 1
exit or user-import activation gates; they move to the future unified local
Backup/Restore ([OWNER decision](decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17)).
H05 remains OPEN there. The other gate rows remain applicable.

When that future protection stage is implemented, before both protection roundtrips
record bytes/digest/length, import/original/track/segment identities, sample
addresses/order/values and provenance. Require equality after restore and byte-for-byte
original comparison. A parser-forbidden restore test proves no reparsing. Include
aggregate multi-Territory and missing/corrupt payload fixtures.
Stage 1 no longer requires these protection/restore gates before ship or before user
import. Known emulator blocker does not waive new assertion failures/native signatures
or establish performance.

## 15. Expected changes and unchanged components

Expected: cohesive GPX domain/model/parser/store/coordinator/Room repository; Room
entities/DAOs/database/migration/schema; Territory/Observer guards; AppContainer/routes;
feature snapshot producer/MainMapScreen/MapDataPanel; dedicated display settings;
Complete Backup contract/capture/validation/activation; Snapshot contract/codec/capture/
references/restore; existing protection capture/blob verification; tests/docs.

**AMENDED 2026-10-17:** the Complete Backup/Snapshot GPX protection changes listed above
move to the future unified local Backup/Restore stage and are not part of the current
Stage 1 activation path ([OWNER decision](decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17)).

No expected change: Registry generic semantics, MapLibreOverlaySession, BeeMap
lifecycle, profile factories, research/GPS/Area, MapPackage contracts, common research
time converter, signing/release/version or dependencies. Missing generic capability
would be a reported blocker, not permission to redesign Stage 0. Room/protection
wire versions evolve explicitly; baseline versions are not frozen.

## 16. Execution order and continuation

Stage 1B design is accepted; its technical contract is linked below.
A separate implementation contract and OWNER authorization are required before coding.

Then managed originals/Room/recovery → visible overlay producer → approved UI/visibility
→ full gates/physical corpus. **AMENDED 2026-10-17:** the former "Complete Backup
roundtrip → Snapshot protection/restore" step and the sentence "Protection is an exit
obligation, not a deferred feature" are superseded; GPX protection/restore is deferred
to the future unified local Backup/Restore and is not a Stage 1 exit or user-import
activation gate ([OWNER decision](decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17)).

UNKNOWNs: parser allocation/platform security evidence; aggregate storage/presentation/
protection measurements; provider failures; Snapshot restore integration effort;
approved visual artifacts. Accepted product choices are no longer open questions.


## 17. Stage 1B technical design reference

[Parser/profile/security fixtures and canonical identity contract](d106-stage1b-gpx-parser-identity-contract.md)
is the DERIVED technical contract, ACCEPTED WITH AMENDMENTS by OWNER on2026-10-13.
Stage 1A remains the parent product truth; acceptance is not implementation authorization.

## GPX 1.0 profile amendment — OWNER, 2026-10-19

Both versions use the existing security, canonical, identity, managed-original,
READY/recovery and display chain. In1.0 root name/desc/scalar author map to source
metadata; track name/desc, segments, lat/lon/ele/time keep source order. Root time
and speed/other unsupported data are preserved in exact original only, with the
existing bounded UNSUPPORTED_CONTENT summary. Mixed rte/wpt do not become track
samples; zero supported track samples still rejects. No schema/speed field added.
GPX1.1 interpretation and CM1 encoding remain unchanged. Declared1.0 and profile
GPX_1_0_STAGE_1 use the existing persisted version fields and CM1 version field;
no existing IDs/originals are rewritten. Earlier1.1 qualification remains historical.
Primary source: [TopoGrafix1.0 manual](https://www.topografix.com/gpx_manual.asp).
