# Snapshot V1 reader alignment — bounded continuation status

2026-10-06; HEAD/origin main `b3eec7d9e54011f1e482e68e7ee8c5e94efc0eba`.
The six pre-existing MIME reconciliation files remain uncommitted and preserved.
No format, shared Repository MIME policy or legacy Complete Backup change.

## Weather domain-state gate

**TEST_FIXTURE_ONLY** for the discovered UNAVAILABLE/source="none" combination in
the current normal application flow (source inspection, not a live database audit).
New points insert PENDING/all-null weather (`RoomObservationRepository.kt`).
`WeatherBackfillRunner` supplies a payload only as LOADED; permanent errors call
markUnavailable. Repository storeLoaded requires LOADED and the DAO prevents
LOADED rows from transitioning to UNAVAILABLE/PENDING. Legacy Complete Backup
import checks the full matrix before Room insertion. Nullable columns allow direct
malformed seeding; that is not evidence of a normal write path. Existing malformed
data is not repaired or assumed absent from a device that was not inspected.

## Implemented common weather boundary

SnapshotDomainCodec's common graph validation now checks exactly one weather row
per point and the normative status matrix for encode, candidate validation,
final readback and discovery. UNAVAILABLE/source="none" fails typed
LOGICAL_STATE_INCONSISTENT with a weather category. No field is nulled, trimmed,
replaced or mutated. The valid determinism fixture now has source=null; the invalid
case remains an explicit negative test. Normative weather schema is unchanged.

Focused command: `:app:testDebugUnitTest --tests "*.SnapshotWeatherMatrixTest"
--tests "*.SnapshotMimeParityTest" --tests "*.SnapshotAllCollectionsDeterminismTest"
--offline` — final PASS, 9 tests (6 weather, 2 MIME, 1 determinism).
Initial helper compilation/timestamp-mutation errors were corrected, not hidden
by weakening assertions. Writer and reader cases cover valid statuses, null matrix,
missing LOADED values, numeric ranges/nonfinite creation values, source, FK and
cardinality. G1 matrix/cardinality aligned; full closed-schema/lexical alignment
remains dependent on unfinished G3/G6.

Independent PC command: `test --offline` in tools/pc/snapshot-verifier — SUCCESS,
UP-TO-DATE, existing 66 tests / zero failures/errors retained. Independence and
16 single-hint / 9 aggregation vectors remain unchanged. No real ZIP/device rerun.

## Historical writer STOP gate (superseded by owner G2–G6 authorization)

**WRITER_DISCREPANCY: data/bees.jsonl**, 11 records for one observationPointId.
Wire §3 requires at most 10. Current Snapshot writer/reader only delegates to
legacy validateGraph, which checks mark uniqueness, FKs and presence result,
but no upper bound. An owned temporary JVM probe used one otherwise valid
BEES_FOUND point, null PENDING weather, 11 distinct nonblank mark tuples and no
cycles. `SnapshotDomainCodec.encode` and `SnapshotArchive.build`/final validation
succeeded; returned recordCounts["data/bees.jsonl"] was 11. The 1-test diagnostic
command passed, demonstrating the discrepancy, NOT format acceptance.
The owned probe source was removed after execution; no generated ZIP is committed.
No user data, unknown/review artifacts or device state was read or deleted.

Per the owner's writer gate, G2–G6 implementation did not begin. Map coverage
alignment, full regression, assemble/lint and full acceptance were not performed.
Independent read-only critic confirmed the normal weather write path, common
matrix without normalization, and the missing Bee-count check. External technology
research: not applicable to this contract-driven validation change.

Verdict: **NOT_ALIGNED — WRITER_DISCREPANCY (11 Bees accepted)**.
Next owner review: authorize Snapshot-specific creation AND reader validation
for the remaining G2–G6 semantics, retaining one common semantic boundary and
unchanged legacy Complete Backup behavior. No commit/push; no R2/FULL/restore/UI.

## Final approved G2–G6 alignment

The owner subsequently authorized creation and reader alignment for all known
G2–G6 gaps without a per-gap STOP. The preceding diagnostic remains historical
evidence; it is no longer the current implementation status.

G2 domain-state gate: **NORMAL_PRODUCTION_MAX_10**. Normal capture inserts a Bee
only through `RoomObservationRepository.startFirstFlight`, with Room count and
`BeeMarkCatalog.MAX_BEES_PER_OBSERVATION_POINT` checked inside the same transaction
before insertion. The existing instrumentation test
`repositoryLimitsRealBeesToTenAndCancellationFreesTheSlot` documents the boundary;
it was inspected, not rerun on a device in this slice. Arbitrary malformed direct
database seeding/import input is not evidence of a supported capture state.

The common Snapshot boundary now enforces:

- G1: unchanged weather status matrix, exactly one row per point, no normalization.
- G2: at most 10 Bees **per point**, on both creation and archive reader paths.
- G3: exact closed schemas/types/enums and strict stable-key ordering for all 13
  collections, coverage and references. Domain object-member order is irrelevant.
- G4: raw media records retain omitted/null/zero/nonpositive identity metadata;
  eligible known positive safe-size identities alone derive references. Exact
  reference set/count, size conflicts, jpg/mp4 conflicts and jpg/bin/mp4/bin
  precedence are checked. No media file is opened or identity fabricated.
- G5: Snapshot uniqueness is per collection. The shared legacy graph validator
  retains its original global scope by default; only Snapshot opts out.
- G6: exact lowercase UUID/SHA lexemes and case-sensitive tokens; no repair of
  input. MIME normalization remains solely the approved shared Repository policy.

`SnapshotDomainCodec.validateWireState` is the common raw semantic boundary for
creation and integrity-checked reader/discovery. `SnapshotRecordSchema` owns wire
shape/lexical/order checks; `SnapshotCoverageValidator` owns strict v1/v2 geometry.
Coverage never invokes legacy migration/normalization. Existing non-media graph
FK/subtype/lifecycle checks are reused with Snapshot-specific UUID scope. Raw media
owner FKs are checked separately because nullable/absent wire identity cannot be
represented by the current nonnullable Room media fields. Stored bytes are retained.
Embedded coverage limit failures preserve SNAPSHOT_LIMIT_EXCEEDED rather than
being wrapped as logical inconsistency. Incorrect reference counter now produces
typed LOGICAL_STATE_INCONSISTENT/REFERENCE_COUNT, not generic ZIP failure.

Focused Snapshot suite: **69 tests, 0 failures/errors**. This includes valid
all-collection graph/determinism, every collection's schema/order checks, weather
matrix, 0/1/10/11 Bees, two points with 10 each, unrelated shared UUIDs, raw media
eligibility/conflicts, MIME parity, v1/v2 coverage and raw digest/counter checks.
Initial compilation/fixture/assertion defects were fixed; no semantic assertion
was weakened to accept invalid data. Independent PC command `test --offline`:
SUCCESS/UP-TO-DATE, **66 tests, 0 failures/errors** in retained XML results.

Independent cross-author critic: no remaining concrete blockers. Identified
typed-limit preservation issue was fixed; exact descriptor inventory is already
enforced by SnapshotArchive. Speculative whitespace mismatch was checked and not
confirmed. Root reviewed the integration and task-only diff. Decomposition
reviewed: BackupCore remains cohesive; the only change to the large shared file
is a default-preserving validation option and its Snapshot-only bridge call.
New shape and geometry responsibilities are separately cohesive files.

No shared RepositoryPolicy, Room/schema, UI, capacity/binding/publication rules,
Complete Backup serializers or legacy format behavior changed. Independent PC
implementation remains separate. No device/user-content access, real ZIP rerun,
R2/FULL/restore/cleanup, commit, stage or push. A future non-empty-media Android
fixture remains useful end-to-end MIME evidence, not claimed by these JVM vectors.

Final acceptance command (offline): `:app:testDebugUnitTest :app:assembleDebug
:app:assembleDebugAndroidTest :app:lintDebug` — **BUILD SUCCESSFUL**.
JVM XML: **600 tests across 78 suites, 0 failures/errors/skipped**. Foundation
29, binding 12, DataStore binding 7, header 5, bootstrap 4, policy 5, Repository
Snapshots 13; all green. CompleteBackupZipCharacterizationTest (11) and legacy
MapCoverageValidatorTest (5) remain green within the full regression. Lint:
0 errors, 24 warnings, 2 hints. No instrumentation/device tests executed.
`git diff --check` passes (only existing LF/CRLF conversion warnings).

Current verdict: **READER_ALIGNED_WITH_WIRE_V1**. No new writer contradiction,
contract ambiguity or supported-domain-state blocker found in the approved
alignment areas. This is bounded contract/test acceptance, not proof of all
possible live database states or FULL/media recovery. Preserve the dirty diff
for owner review. No stage/commit/push.
