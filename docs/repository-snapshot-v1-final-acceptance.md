# Slice 2B Final Acceptance Report

Later independent-verifier follow-up:
[Snapshot V1 normative wire schema](snapshot-v1-wire-schema.md) closes textual gaps
under owner-approved O1/O2/O3 and lists current production validation gaps.
It does not establish implementation conformance to the closure; the original
Slice 2B evidence and verdict below are not rewritten.

2026-10-06. Final audit of the existing Slice 2B only; no R2, PC verifier or UI.

## 1. Worktree safety

Baseline HEAD=origin/main=`dc40b2092154f4d1a3b7dd3d1e70526e9ee95c7a`.
The approved contract is already preserved. `git diff --check` passes.
Pre-existing `.review-tmp` is not tracked/indexed; inaccessible review contents
were not read, deleted or changed. Explicit file allowlisting, not `git add -A`,
is required for this commit. Generated build/device data are not source evidence
to stage. The final staged path list must be inspected before commit.

## 2. Determinism

`SnapshotDomainCodecTest.nonEmptyGraphIsDeterministicAndDeduplicatesSharedMedia`
checks reversed query/list order, portable bytes and shared references.
`SnapshotAllCollectionsDeterminismTest.all collections and settings are byte deterministic under input order changes`
strengthens this with a nonempty all-13-collection fixture and independent
raw ZIP-entry SHA comparison. Both archive builds also pass the full production
reader. Snapshot-instance identity/time remain in
manifest only; neither manifest nor whole ZIP identity is promised.

## 3. Room transaction proof

Production: `RepositorySnapshotService.create` -> bound foundation operation ->
`SnapshotCapture.capture` -> `database.withTransaction { backupDao().snapshot() }`.
All thirteen DAO reads occur inside that same transaction. Serialization operates
on the detached graph after the transaction, not on a live database-file copy.

Previously run on Samsung:
`SnapshotCaptureTransactionTest.captureReadsOneRoomTransactionBeforeConcurrentWriterCommits`.
The capture holds the transaction while a coupled Territory+Observer writer waits;
the first capture sees neither new row, the next sees both. This is actual device
test evidence, not a claim based solely on a callback or annotation.

## 4. Settings consistency

`settings.snapshot()` occurs separately after Room capture. Global Room+DataStore
atomicity is not claimed. Before any staging publication, current Territory,
current Observer and coverage Territory references must resolve against that graph.
Actual null is allowed; invalid non-null is never silently nulled.

Tests: `SnapshotCaptureTransactionTest.nonNullCurrentTerritoryMustExistInCapturedGraph`
(Samsung), `SnapshotDomainCodecTest.settingsForeignKeysFailClosed`,
`invalidObserverAndCoverageReferencesFailClosed`, and the audit's publication
failure test `captureSerializationAndCandidateFailuresPreservePriorHistory`.
Failure is `LOGICAL_STATE_INCONSISTENT`, with no publication.

## 5. Failure/interruption matrix

| Stage | Concrete test/evidence |
|---|---|
| Logical capture | `captureSerializationAndCandidateFailuresPreservePriorHistory` |
| Serialization/framing | same test, invalid newline JSONL record |
| Staging write / sync | `allStorageFailuresPreservePriorHistory` |
| Candidate validation | `cancellationAndInvalidCandidateNeverPublish`; audit capture/candidate test |
| Capacity preflight | `unknownAndLowCapacityRejectBeforeCaptureOrStaging` |
| Capacity drop | `capacityDropAfterStagingNeverPublishes` |
| UUID revalidation | `replacementImmediatelyBeforePublicationFailsClosed` |
| Move/publication | `allStorageFailuresPreservePriorHistory`; `failedMoveResponseRequiresFinalFullReadback` |
| Stage/final reopen | `stagedAndFinalReopenFailuresNeverReturnCommit` |
| Whole SHA | `finalCorruptionIsNotCommittedAndPreviousSnapshotRemains`; audit staged mutation test |
| ZIP validation | `SnapshotSafetyTest.traversalAbsoluteBackslashMissingUnexpectedAndDuplicateEntriesFail`; malformed-central-directory test |
| Raw entry digest | `SnapshotArchiveTest` parseable-byte-mutation test; `SnapshotIndependentFixtureTest.sameSemanticsDifferentRawBytesRejected` |
| Semantic parse after valid hashes | `SnapshotIndependentFixtureTest.validEntryDigestsDoNotBypassSemanticAndJsonValidation` |
| Cancellation before/during write | `cancellationAndInvalidCandidateNeverPublish`; `stagedMutationAndCancellationAfterWritePreservePriorHistory` |

Prior published snapshots remain byte-identical. There is no commit-success result
before the final full reader succeeds. Reader-specific ZIP/digest/parse faults are
tested directly; changed final bytes first fail whole SHA, so there is no dishonest
claim that each later reader stage can be reached by corrupting bytes while retaining
the original trusted whole SHA.

**Crash-model clarification:** before move, at most owned noncommitted Staging is
left. After move, a final file can remain when acknowledgement/readback fails.
It is not an acknowledged commit; discovery independently validates it or surfaces
invalid evidence. Automatically deleting/rolling back that final file would conflict
with the accepted immutable-history recovery contract. This is not false success.
Power-loss durability and all possible provider fault schedules are not proven.

## 6. UUID immediately before publication

`BoundRepository.createMetadataSnapshot` holds the binding gate; Foundation holds
the maintenance mutex and supplies `identity(expectedRepositoryId)`.
`RepositorySnapshots.create` calls that fresh identity callback immediately before
`storage.moveOwnedStage(stage, target)`; no intervening lookup, hashing, capacity
query or suspension. `readHeader()` validates format/version/variant; `identity`
checks durable expected UUID on the current adapter root.

`replacementImmediatelyBeforePublicationFailsClosed` changes the header at the last
canonical-target lookup: the fresh check rejects it, `moveOwnedStage` is never called
and the replacement repository receives no snapshot. The earlier after-staging
replacement test also preserves foreign-root Staging. Provider-external mutation
inside the move itself is not an atomic compare-and-move guarantee.

## 7. Exact limit boundary matrix

The audit exposes the existing `SnapshotArchive.addBound` and `BoundedOutput` to
internal tests only; no second limiter or enforcement algorithm is introduced.
Bounded synthetic chunk streams exercise actual V1 caps without huge semantic ZIPs.
Reduced-cap integration fixtures still prove the ZIP reader/writer call those paths.
`SnapshotExactLimitsTest` exact test names:

- `default ZIP stream accepts 64 MiB and rejects the first byte after`;
- `default stream counters accept exact limits and reject the first byte after`;
- `parser default depth boundary starts at root zero`;
- `parser default string and key boundaries are exact`;
- `parser default object and array boundaries are exact`.

| Limit | Equality / first excess |
|---|---|
| Whole ZIP | 67,108,864 / 67,108,865 bytes |
| Actual total uncompressed | 134,217,728 / 134,217,729 bytes |
| Manifest | 1,048,576 / 1,048,577 bytes |
| Portable | 1,048,576 / 1,048,577 bytes |
| References | 33,554,432 / 33,554,433 bytes |
| JSONL record including LF | 1,048,576 / 1,048,577 bytes |
| Depth | 32 / 33 (root depth 0) |
| String / key UTF-16 units | 65,536 / 65,537 |
| Object members | 256 / 257 |
| Array entries | 100,000 / 100,001 |

Excess errors carry `SNAPSHOT_LIMIT_EXCEEDED`, category, observed and configured
limit. `SnapshotSafetyTest` additionally covers actual decompression expansion,
duplicate/missing/unexpected entries and traversal/absolute/backslash paths.
No physical-device maximum-size/heap-stress claim is made.

## 8. Detailed DEV sizing

OBSERVED, reused from the already completed sizing; no new user graph read.
Raw bytes include JSONL LF. Average values below are rounded for display.

| Collection / entry | Count | Bytes | Average | Max record |
|---|---:|---:|---:|---:|
| Territories | 1 | 184 | 184 | 184 |
| Observers | 1 | 190 | 190 | 190 |
| PhysicalObjects | 3 | 854 | 284.67 | 286 |
| Apiaries | 0 | 0 | 0 | 0 |
| Hollows | 2 | 470 | 235 | 235 |
| LogHives | 1 | 278 | 278 | 278 |
| Sequences | 2 | 186 | 93 | 94 |
| PhysicalObjectMedia | 2 | 802 | 401 | 401 |
| ObservationPoints | 5 | 2,410 | 482 | 484 |
| Bees | 21 | 4,162 | 198.19 | 200 |
| FlightCycles | 88 | 29,971 | 340.58 | 342 |
| Weather | 5 | 1,090 | 218 | 218 |
| Attachments | 2 | 810 | 405 | 405 |
| MapCoverage | 1 | 247 | 247 | 247 |
| Media references | 4 | 496 | 124 | 124 |
| Portable settings | 1 object | 120 | — | — |
| Manifest | 1 object | 2,491 | — | — |

ZIP=13,105 bytes (0.01953% of 64 MiB), total actual uncompressed=44,761
(0.03335% of 128 MiB), largest record=484 (0.04616% of 1 MiB).
Manifest=0.23756%, portable=0.01144%, references=0.001478% of their caps.
Collection entries have no separate byte cap: their maximum individual usage of
the total ceiling is FlightCycles, 29,971/134,217,728 = 0.02233%.
Exactly 17/17 entries is required, not spare capacity.

DERIVED count-only estimates, **not measured seasons**. Each cell is bytes at
observed average / conservative observed maximum record size. Counts scale by
10 or 100; record sizes stay unchanged. Empty Apiaries provide no size evidence.

| Collection | 10x bytes avg/max | 100x bytes avg/max |
|---|---:|---:|
| Territories | 1,840 / 1,840 | 18,400 / 18,400 |
| Observers | 1,900 / 1,900 | 19,000 / 19,000 |
| PhysicalObjects | 8,540 / 8,580 | 85,400 / 85,800 |
| Apiaries | 0 / 0 | 0 / 0 |
| Hollows | 4,700 / 4,700 | 47,000 / 47,000 |
| LogHives | 2,780 / 2,780 | 27,800 / 27,800 |
| Sequences | 1,860 / 1,880 | 18,600 / 18,800 |
| PhysicalObjectMedia | 8,020 / 8,020 | 80,200 / 80,200 |
| ObservationPoints | 24,100 / 24,200 | 241,000 / 242,000 |
| Bees | 41,620 / 42,000 | 416,200 / 420,000 |
| FlightCycles | 299,710 / 300,960 | 2,997,100 / 3,009,600 |
| Weather | 10,900 / 10,900 | 109,000 / 109,000 |
| Attachments | 8,100 / 8,100 | 81,000 / 81,000 |
| MapCoverage | 2,470 / 2,470 | 24,700 / 24,700 |
| Media references | 4,960 / 4,960 | 49,600 / 49,600 |

Keeping portable+manifest approximately 2,611 bytes, total estimates are:
10x=424,111 / 425,901 bytes; 100x=4,217,611 / 4,235,511 bytes
(about 3.14–3.16% of total ceiling). Manifest descriptor sizes can grow slightly;
this estimate is not a hard bound. ZIP compression is not extrapolated. Longer
text, geometry expansion and unseen Apiaries are not covered by count scaling.
Current measured/extrapolated usage shows no proximity blocker.

## 9. Current user entry point

**C: code/tests only.** `AppContainer.repositorySnapshots` exposes the service;
no production/DEV UI action calls it. Instrumentation smoke invokes it explicitly.
The SAF-selection test helper is not a snapshot-creation action. No user-facing
"full backup" label was introduced. A future entry-point UX is a separate
mockup-first slice; metadata-only wording must exclude photo/video protection.

## 10. Independent PC verifier readiness

Specification, exact path list, limits, manifest namespaces, numeric/digest rules,
canonical corpus and independent fixtures provide a sufficient seed for a separate
PC-verifier task. No implementation is started here. That implementation must be
independent, not a call into the production Android Snapshot reader. Reusing the
specification and conformance vectors is appropriate; sharing one reader is not
the independent acceptance check requested by the owner.

Required pipeline: fixed ZIP -> whole SHA/limits/ZIP safety -> manifest -> exact
entries -> raw entry size/SHA -> domain semantic parse -> media references ->
concise PASS/FAIL with nonzero failure exit. It must not claim media protection
for METADATA_ONLY. Domain field-level wire shapes presently derive from the
explicit codec and fixtures; extracting a PC-facing specification is supporting
work in that future verifier slice, not permission to create another format.

## 11. Verification / remaining blockers

Focused Snapshot run initially found an invalid new synthetic determinism fixture
(NO_BEES_FOUND with a Bee); the fixture, including point/flight lifecycle fields,
was corrected without changing production domain invariants. Final full command:
`gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline`.
PASS: 574 JVM tests, zero failures/errors; both builds and lint pass. Lint has
0 errors, 24 warnings and 2 hints; not a warning-free claim. `git diff --check` PASS.
Independent read-only critic found no blocking contradiction in transaction,
ordering, raw-byte hashing, bounds, identity/publication/recovery or legacy reuse.
Root reviewed the critical production paths and added failure-injection tests.
Decomposition reviewed; BackupCore kept cohesive because only narrow codec/graph
bridges and visibility changed, not its legacy workflow. New archive/domain/
capture/publication responsibilities stay in separate cohesive files below 300 lines.
The earlier Samsung transaction/conformance/smoke evidence is reused; sizing is
not repeated. No destructive device actions are required by this audit.
Physical power-loss, maximum heap stress and provider-internal mutation races
remain explicit limitations, not established guarantees.

## 12. Verdict / commit boundary

**ACCEPT.** No exact remaining blocker. Stage only the reviewed Slice 2B file
allowlist; review/temp/generated artifacts explicitly excluded. Commit message:
`feat: add metadata-only repository snapshot v1`.
The resulting hash is returned to the owner after commit rather than embedded
cyclically in its own committed report. STOP BEFORE PUSH; no next slice.
