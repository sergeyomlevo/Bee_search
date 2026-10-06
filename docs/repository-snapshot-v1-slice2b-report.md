# Repository V1 Production Slice 2B Report

Date: 2026-10-06. Implementation and bounded verification complete; owner review pending.
No Slice 2B commit/push and no automatic format freeze.

Subsequent final acceptance audit: [repository-snapshot-v1-final-acceptance.md](repository-snapshot-v1-final-acceptance.md).
That audit supersedes the pending-review status and reduced-boundary-test gap below:
actual V1 limit counters/parser boundaries, all-collection determinism and additional
failure injection pass; final regression is 574 tests, zero failures/errors.
This implementation report retains its original campaign results as historical evidence.

## 1. Contract commit

`dc40b2092154f4d1a3b7dd3d1e70526e9ee95c7a`: only approved
`docs/snapshot-2b-contract-preflight.md`; pushed as explicitly requested.

## 2. Baseline

Initial HEAD/origin main: `3db5dda29f5306bd76c5dc33cafc6190499870f6`.
Only the expected untracked preflight was reported. After contract preservation,
HEAD=origin/main=dc40b209; reported tree clean. Git reports permission warnings for
pre-existing `.review-tmp` directories: they were not inspected or changed; this
limits an absolute claim about inaccessible untracked contents.

## 3. Files changed

Modified: `app/build.gradle.kts` (shared test corpus assets only), `AppContainer.kt`,
`data/backup/BackupCore.kt` (narrow shared graph/entity codec bridges),
`data/backuprepository/{RepositoryBinding,RepositoryFoundation,RepositoryStorage}.kt`;
`.agent/handoff.md`; docs architecture/data-model/decisions/product-requirements/
user-workflows/repository-v1-foundation.

Added production: `data/backuprepository/RepositorySnapshots.kt` and
`data/backupsnapshot/{RepositorySnapshotService,SnapshotArchive,SnapshotCapture,
SnapshotContract,SnapshotDomainCodec,SnapshotJson,SnapshotManifest}.kt`.
Added JVM tests: RepositorySnapshotsTest, SnapshotArchiveTest, SnapshotDomainCodecTest,
SnapshotIndependentFixtureTest, SnapshotJsonTest, SnapshotSafetyTest.
Added Android tests: SnapshotCaptureTransactionTest, SnapshotConformanceDeviceTest,
SnapshotDeviceTest, SnapshotSizingDeviceTest. Preserved `test/resources/backupsnapshot/
canonical-vectors.json` with the accepted PoC bytes/hex/SHA expectations.
Added docs repository-snapshot-v1 and this report. No dependency or Room migration.

## 4. Exact schema

Manifest: snapshotFormat=`beesearch-snapshot`, snapshotFormatVersion=1; snapshotId,
repositoryId, variant, createdAtEpochMs; snapshotProfile=`METADATA_ONLY`,
creationResult=`COMPLETE`, evidencePolicy=`NO_MEDIA_EVIDENCE`, creationIssues=[];
entries=16 sorted `{path,byteSize,sha256}` descriptors; mediaReferences=
`{path:"references/media-blobs.jsonl",recordCount}`. Exactly17 ZIP paths from the
approved preflight, empty collections included. Whole SHA only in external filename.
Unknown format/version/profile/result/policy/combinations reject.

## 5. Logical profile

All13 approved Room collections, current Territory/Observer, persisted coverage
geometry, media metadata and unique reference set. No media file reads or presence
requirement for COMPLETE. Excludes offline maps, repository/binding, grants,
Staging/caches/UI, PC/coverage/handoff state. Explicit service API; no scheduler/UI.

## 6. Room transaction

One `database.withTransaction { backupDao.snapshot() }` captures all tables.
Physical Samsung test holds the read boundary while a coupled Territory+Observer
writer attempts to commit; capture sees the old graph and the next capture sees
both new rows. No live SQLite-file copy.
Primary source: AndroidX [Room transaction API](https://developer.android.com/reference/kotlin/androidx/room/package-summary#withTransaction(androidx.room.RoomDatabase,kotlin.coroutines.SuspendFunction0)).
The large reference page was not retrievable by the browser in this campaign;
existing installed Room usage plus the actual physical transaction test provide
the version-specific implementation evidence here.

## 7. Settings consistency

DataStore is read separately; no global Room+DataStore atomicity claimed.
Non-null current Territory/Observer and coverage Territory references must resolve.
Missing entity => LOGICAL_STATE_INCONSISTENT, no publication, no silent nulling.

## 8. Deterministic ordering

UUID text for main identities, subtype owner UUID, weather point UUID, sequence
tuple `(territoryId,objectType.name)`, coverage Territory, references SHA.
Duplicate stable keys fail closed, including subtype/media/weather identities.
Reversed nonempty query/list order test checks byte-identical records/settings/refs.
Instance UUID/time belongs only to manifest; whole ZIP byte identity is not promised.

## 9. Numeric conformance

Domain finite JSON numbers are parsed as binary64. Host and Samsung vectors cover
0.1, signed -0.0, smallest positive representative, largest finite value,
latitude55.751244, longitude37.618423, fractional12.5/2.75; expected raw bits tested.
NaN/±Infinity and overflow to Infinity reject. Canonical manifest/ref core is
integer-only; domain is not JCS. No arbitrary future codec-version byte guarantee.

## 10. Raw-byte integrity

SHA and size use the exact persisted entry bytes, including JSONL LF, before semantic
parsing. Semantically unchanged whitespace mutation fails ENTRY_DIGEST_MISMATCH.
Independent hand-authored nonempty ZIP uses standard JDK ZIP/SHA, not production
manifest/archive writer. The accepted canonical corpus runs on host and Samsung.
Core port retains [RFC8785](https://www.rfc-editor.org/rfc/rfc8785) restricted safe-integer
rules; no floating-point canonicalization or Gson dependency was introduced.

## 11. ZIP/parser limits

64MiB ZIP;128MiB actual streamed uncompressed total including manifest; exactly17
regular entries; manifest/portable1MiB, references32MiB, JSONL record1MiB includingLF.
Depth32, string/key65536 UTF-16 units, object256, array100000. Equality allowed,
first excess fails with category/observed/limit. No special FlightCycles cap.
Traversal/absolute/backslash/duplicates/unexpected/missing paths, truncated central
directory and expansion budget failures reject. CRC/actual size also checked.
Boundary tests use measured files and injected smaller caps; a full128MiB metadata
fixture was not generated. This establishes the common enforcement path, not a
physical-device maximum-size/heap stress claim.
The fixed-file reader uses [JDK ZipFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/ZipFile.html), not legacy sequential ZIP acceptance.

## 12. Publication

Bound gate + Foundation maintenance lock; capture/validate; private fixed candidate;
owned public Staging; close/sync; full staged validation; whole SHA; capacity guard;
fresh format/version/variant/expectedUUID check immediately adjacent to same-storage
move; final actual file readback; whole/entry SHA + ZIP/profile/graph validation;
only then committed result. Canonical target is never a streaming write target.
No copy fallback. Temporary scratch peak320MiB plus artifact slack and20GiB reserve
is conservatively preflighted; long operations recheck capacity. It is not an Android
exclusive reservation or a claim about precise provider amplification.

## 13. Discovery/reopen

Actual candidates independently validated from fixed readbacks; filename UUID/SHA,
repository/variant, manifest, all17 entries/digests and graph/reference consistency.
Latest by `(createdAtEpochMs,snapshotId)`, not filename. New invalid evidence remains
visible; duplicate snapshot UUID candidates conflict. No mutable history index.

## 14. Failure/crash

Staging-only is noncommitted. Corrupt final is invalid history. Failed move response
requires final full readback rather than presumed success. Prior snapshots unchanged.
Cancellation, capacity drop, stage/sync/move/candidate/readback failures fail closed;
UUID replacement immediately before publication produces no canonical file in B.
Only owned artifacts cleaned; unrelated staging retained. Atomic rename and sudden
power-loss durability are not claimed or physically proven.

## 15. DEV sizing — OBSERVED

Read-only real DEV graph, privately built/validated scratch, only aggregate logs.
Final measurement ZIP13105 bytes; uncompressed44761; largest record484. An earlier
run ZIP13106 with identical entry sizes illustrates instance/ZIP nondeterminism.

| Collection/entry | Count | Bytes | Average | Max record |
|---|---:|---:|---:|---:|
| Territories |1|184|184|184|
| Observers |1|190|190|190|
| PhysicalObjects |3|854|284.67|286|
| Apiaries |0|0|0|0|
| Hollows |2|470|235|235|
| LogHives |1|278|278|278|
| Sequences |2|186|93|94|
| PhysicalObjectMedia |2|802|401|401|
| ObservationPoints |5|2410|482|484|
| Bees |21|4162|198.19|200|
| FlightCycles |88|29971|340.58|342|
| Weather |5|1090|218|218|
| Attachments |2|810|405|405|
| MapCoverage |1|247|247|247|
| Media references |4|496|124|124|
| Portable settings |one object|120|—|—|
| Manifest |one object|2491|—|—|

Usage: ZIP0.01953%; total0.03335%; manifest0.23756%; portable0.01144%;
references0.001478%; largest record0.04616% of1MiB. Entry count17/17 as required.

DERIVED extrapolation, not measured seasons: at observed FlightCycle average,
10000 cycles≈3.41MB,100000≈34.06MB (25.38% total ceiling before other collections).
Observed max342 gives34.2MB for100000. ZIP compression is not extrapolated.
The small DEV graph does not establish all future field sizes; unusually long text,
geometry and growing record counts still must respect the caps. No current limit
proximity blocker; this sizing gate passes, without automatic format freeze.

## 16. Host tests/builds

Final command: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug
:app:assembleDebugAndroidTest :app:lintDebug --offline` — PASS.
564 tests,0 failures,0 errors, including Slice1/2A and Complete Backup V1–V6 regression.
Lint0 errors,24 warnings,2 hints (not a warning-free claim). git diff --check PASS.
Initial failures were test-fixture construction errors (duplicate ZIP writer refused
fixture; missing synthetic subtype), corrected and rerun, not suppressed.

## 17. Samsung smoke

SM-S938B/API36/RFCY90MBYVZ; only preserving DEV/test updates, no uninstall/clear.
Room+canonical/numeric tests4 PASS; aggregate sizing1 PASS. Opt-in prepare/create/
restart-discovery/corrupt-copy each1 PASS. Owner selected the fresh tree.
Root: `Download/BeeSearch/_poc/ProductionSlice1/38f11516-fa3e-466a-8b7d-317c8445d8ee/Backup`.
Final APK create adds snapshotdb78bc59-f91e-4377-84cc-4394e25a49bd,
SHAe123cd9fdd5630a18443d22f8f7de722127dbe23ccfc100d2c8fc868c4f37bcc,3525bytes.
After force-stop/relaunch,2 valid snapshots remain;2 corrupt disposable copies are
rejected. Prior valid snapshot6584c2c5-ae4f-4081-a024-bbbc02957107 hash independently
confirmed by device sha256sum=1d7fee104800589f4ac7be29ffa4af7a783636edc564103726004dd05f51ff62.
No user media touched; disposable public evidence retained. Harness void/fixture
initial errors were corrected before acceptance, not treated as PASS.

## 18. Critic findings/fixes

Separate critic reviewed production diff. Fixed restricted canonical-number scope,
full semantic reader, media-reference context/count, typed I/O versus ZIP failures,
non-object manifest rejection; root added adjacent-publication UUID and duplicate
subtype checks. Critic confirmed disk320MiB bound after excluding heap capture;
no production blocker found. Additional independent/boundary tests followed review.
Decomposition reviewed; BackupCore kept cohesive because the narrow bridge reuses
private existing entity parsers/serializers/invariants without changing legacy flow.
New capture/codec/archive/publication are separate cohesive files, each below300lines.

## 19. Restore limitation

Immutable exported metadata and verifiable archive evidence only. No in-app restore,
media recovery or persistent unavailable-media restore state. Not a complete
recovery solution. Historical COMPLETE never means fresh restore verification.

## 20. Remaining gaps

No physical power-loss proof or maximum-metadata heap stress. Reduced injected caps
exercise shared limit paths rather than allocating every full production ceiling.
Provider-internal concurrent root mutation during move remains the documented
unsupported external-writer race; fresh adjacent reread closes the application gap.
No production screen/automation/restore/media protection was added. Broader parser
fuzzing and every final-reader subfailure injection are useful follow-up coverage;
final whole-SHA rejection already subsumes changed final content in this scope.
Review does not claim exhaustive proof of every possible provider/fault schedule.

## 21. Next layer

Snapshot foundation supports later media/FULL or PC handoff contracts without changing
old METADATA_ONLY promises. Those remain separate owner-authorized slices, not
automatically unblocked implementation authority. Proposed next: R2 media evidence
capture/FULL preparation; restore and coverage/handoff/offload remain explicit gates.

## 22. Git status / STOP

HEAD=origin/main=dc40b209. Production/tests/docs/handoff diff remains unstaged and
uncommitted; no Slice2B push. All reported changed paths belong to this slice.
Pre-existing inaccessible .review-tmp warnings remain untouched. Owner review required.
Skills used: android-development for preserving DEV verification; critic-workflow for
separate critical review and durable evidence/handoff. STOP; no next slice started.
