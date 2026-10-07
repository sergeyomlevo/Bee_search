# Bee Search handoff

## Current continuation — backup documentation reconciliation

The owner resolved the D101 ↔ approved `backup-v2.md` §5.6 conflict in favour of `backup-v2`.
D101 still permits FULL with zero required media blobs: Success describes saved research data and
settings, with no `0 из 0` count or claim of media verification. The operation design now links to the
approved `backup-v2.md` / `backup-v2.png`; the authoritative contracts are aligned on this case.
The ordinary-opening S5 blocker is now architecturally resolved: the owner accepted a separate
`readPublishedSummary()` contract for snapshot-container/metadata validation without media reads.
Existing strong `discover()` and the strong evidence gate before FULL publication remain unchanged.
The Ready last-backup date describes a recognized published artifact, not current media evidence or
a journal of historical UI Success returns. Workflows §45–§47 now describe the approved operation
and preserve the historical S3 acceptance and standalone backend semantics.
Production implementation remains **not started**, including the new summary boundary.
Next: implement `readPublishedSummary()`, `CreateBackupOperation` and the approved `backup-v2` UI
from the clean `HEAD == origin/main` after `docs: separate backup summary from strong discovery`.
Do not reopen the mockup-first gate. The rest of S5 policy remains future work.

## Current continuation — S6B FULL / LOCAL_VERIFIED Snapshot (finalized and pushed; backup mockup approved, mockup-first gate closed)

Baseline: clean `main`, `HEAD == origin/main == 6b629587fb744f1dc777ae582df011342aed1ba4`
(«Implement repository media protection»). S6B was finalized as two commits and pushed to `origin/main`:
`16e2fdd` «Extend Snapshot V1 with the local full-evidence profile» (contract §7.1 + shared vector file +
D101) and `b8612fe` «Implement the local full-evidence backup profile» (implementation, tests, DEV
harness, repository-aware PC verifier and descriptive docs). Working tree is clean.

Next gate: the single-button backup UI. The mockup-first gate is **CLOSED / APPROVED**: the owner
visually approved `docs/ui/mockups/backup-v2.png` as the reference for the current backup slice, with its
contract `backup-v2.md` (five states of one screen) and the design-only single-capture operation
specification `docs/backup-operation-design.md`, which is committed as the design contract of this
stage. The approved version has a real Back arrow in all five app bars, no app-bar help icon, a purely
informational (non-interactive) storage path row, the collapsed summary `Данные исследований,
настройки, фото и видео`, a down chevron when collapsed and an up chevron when expanded, Working
without percentages and without Cancel, Success with media verification, Error with `Повторить`, one
primary bottom button and the always-visible boundary line `Копия хранится на этом телефоне. Передача
на компьютер выполняется отдельно.` The intermediate `backup-v1.*` drafts were deleted as the owner
requested. Next separate stage: production implementation of that one operation — **not started**; no
production UI, no `CreateBackupOperation`.

Scope: a second supported Snapshot V1 evidence profile. `snapshotFormatVersion` stays 1 and the
structural envelope is unchanged (same 17 entries, same manifest field set, same descriptor/JSON/digest
rules and limits); V1 now accepts exactly two tuples — `METADATA_ONLY`/`NO_MEDIA_EVIDENCE`/`COMPLETE`
(unchanged) and `FULL`/`LOCAL_VERIFIED`/`COMPLETE` (new, D101). Every other combination, unknown token,
non-empty `creationIssues` and any DEGRADED/PARTIAL/INCOMPLETE/REMOTE_VERIFIED/PC_VERIFIED value is
refused; a reader that knows only the first tuple fails closed instead of misreading the second. The
policy is normative in `docs/snapshot-v1-wire-schema.md` §7.1 and the shared vector file
`docs/test-vectors/snapshot-v1-evidence-profiles.json` is consumed by both the Android and the PC test
suites.

What FULL means: the ZIP still contains metadata and references only (no payload bytes, no `Media/*`
entry). The required set comes from the SAME immutable `SnapshotDomainEntries` that is serialized, and
every blob of it is strongly verified in the same bound repository immediately before publication —
canonical `Media/<sha256>.<canonicalExtension>`, exact size, actual SHA-256 recomputed from repository
bytes, single unambiguous canonical identity. `RepositoryMediaEvidence` is read-only: it never ingests
and never writes. A blob that is missing, differently sized, differently valued, under another
canonical extension or ambiguous fails the creation with a typed error
(`MEDIA_EVIDENCE_MISSING` / `MEDIA_EVIDENCE_MISMATCH` / `MEDIA_EVIDENCE_INCONSISTENT`; access/identity
failures keep their own errors and cancellation propagates), publishes nothing and never falls back to
`METADATA_ONLY`. Repository discovery refuses a declared FULL candidate whose required blobs are no
longer verifiable without deleting, rewriting or reinterpreting it, so an older valid `METADATA_ONLY`
snapshot can remain the newest usable one. `LOCAL_VERIFIED` is local evidence only: it says nothing
about a PC, cloud or external copy and does not protect against losing the phone.

No UI, no automatic ingest, no S4/S5, no stale-`Staging` cleanup, no offload or restore; media limits
are untouched. The backup screen still creates the metadata-only profile, and `createFull()` has no
product caller.

Verification. App JVM `:app:testDebugUnitTest` **721 tests / 0 failures / 0 errors / 0 skipped** (28
new across `SnapshotEvidenceProfileTest`, `RepositoryMediaEvidenceTest`, `RepositoryFullSnapshotTest`);
PC verifier `test` **93 tests / 0 failures** (13 new in `EvidenceProfileTest` and the FULL
repository-aware cases); `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` PASS (0 errors,
23 warnings, 2 hints — all remaining warnings are pre-existing and outside this change); `git diff
--check` clean. APK SHA-256: app `48dd291cfb9e8dc034eba5262e53e8f54a16dff9a6327ae42c381c86fddaa9fd`,
test `e797bd014b234de7e54700f6f05a1834a229bf5257d80d94bc8af6d77a7943ce`.

Samsung SM-S938B / API 36 / `RFCY90MBYVZ`, DEV updated in place (no clear, no uninstall;
`firstInstallTime` unchanged, `lastUpdateTime=2026-10-07 18:02:09`, device read-back APK SHA equals the
artifact above). Pre-state matched the accepted S6A state exactly: repository `df4d52a9-…` / `Dev`,
`repository.json` sha `162feffe…828a`, 3 snapshots, 4 canonical Media blobs, empty `Staging`, and the
same 4 private originals — so the captured required set had not changed. Real creation through the
production backend produced `snapshot-a38408e3-fb92-43d9-8e26-07963edb45d9-ccdccf0e31634bf738ec645784f59a534d310a14b3af795f8736b6dae6e02ac3.zip`
(12434 bytes, 17 entries, `FULL/LOCAL_VERIFIED/COMPLETE`, `recordCount=4` with exactly the accepted
blob SHAs/sizes). A fresh instrumentation process then reconstructed the state from repository contents
alone: 4 candidates, 3 usable `METADATA_ONLY` plus 1 usable FULL, and the FULL one as `latest`. Media
blobs and the 3 old snapshots stayed byte-identical, `Staging` ended empty and `repository.json` was
unchanged. No media payload exists inside the ZIP.

PC evidence: `C:\App\BeeSearchBackupResearch\S6B\Backup` (**TRANSPORT = ADB_PULL**, 9 files /
18,345,964 bytes; every PC hash equals the device hash; S3 already proved manual USB/MTP byte identity
separately, this slice does not re-prove MTP). Standalone verifier: the three old snapshots PASS (exit
0) and the FULL one returns `REPOSITORY_EVIDENCE_REQUIRED` (exit 3), never a final PASS without a
repository root. Repository-aware verifier: FULL → PASS with required 4/4 verified and the declared
tuple; an old `METADATA_ONLY` snapshot → PASS and still described as metadata-only evidence.

Not verified / limits: real media here is ≤16 MiB JPEG; large-video behaviour is unproven. The negative
FULL evidence cases (missing, wrong size, wrong bytes, wrong extension, ambiguity, identity/variant
mismatch, cancellation, discovery refusal, media-after-capture boundary) are proven by JVM fixtures and
temporary in-memory repositories, never by corrupting the owner's live repository. No PC/cloud/handoff
layer exists yet, so `LOCAL_VERIFIED` remains local-only evidence.

Next (owner decision): implement the single-button backup UI and the single-capture operation component
described in `docs/backup-operation-design.md`, following the approved mockup
`docs/ui/mockups/backup-v2.png` with its contract `backup-v2.md` (S6A protection and S6B full-evidence
publication stay internal stages of that one operation; no separate «Сохранить фото и видео» button).
That implementation is a separate task and has **not** started. After that: stale-`Staging`
reconciliation, then the S5 large-media policy — with the mandatory S5 constraint that ordinary screen
opening and ordinary discovery must not re-read all media bytes, and that the cheap display state is
distinguished from the strong verification performed during a backup operation.

## Previous milestone — S6A Media Protection Foundation (finalized)

Baseline: clean `main`, `HEAD == origin/main == d562734a34cb905e99e3b95d63c636b7d5a238dc`
(«Implement metadata backup UI»). This slice was finalized as two commits — `Document backup verification
safeguards` (pre-existing documentation debt: the `AGENTS.md` device/worktree rule, the D099
access-class nuance, the S3 acceptance record) and `Implement repository media protection` (this slice) —
and pushed to `origin/main`. S6B was not started.

Scope: the minimum backend foundation that copies the media blobs the current research state
requires into the already existing `Download/BeeSearch/<variant>/Backup/Media/` through the already
implemented `BoundRepository.ingest()`. There is no UI, no automatic caller, no FULL snapshot, no
restore/offload, no cleanup, no limit change and no snapshot side effect. Protection duplicates
storage: it protects nothing away and frees no phone space. `METADATA_ONLY` snapshots stay
`METADATA_ONLY` and still contain metadata only.

One shared required-media-set definition. `RequiredMediaSet` (data/backuprepository) now owns
eligibility (present SHA, size `1..9007199254740991`), SHA grouping, conflicting-size rejection,
MIME aggregation through `CanonicalExtension` (`jpg`/`mp4`/`bin`) and result ordering.
`SnapshotDomainCodec.references` was reduced to a caller of that same object, so snapshot
`references/media-blobs.jsonl` and protection cannot drift apart; wire behaviour is byte-identical
(same two logical error strings, same order of checks) and a parity test proves both paths agree on
identical input, including the refusal cases.

Protection semantics. `MediaStateCapture` reads the graph in one Room transaction, exactly like
snapshot creation, and never writes; the plan is derived from the captured rows only, so the only
way protection reaches private bytes is a captured metadata record naming them. `MediaProtectionService`
is deliberately not a transaction: every required blob gets its own typed outcome (`INGESTED`,
`ALREADY_PRESENT`, `SOURCE_MISSING`, `SOURCE_CHANGED`, `CAPACITY_BLOCKED`, `VERIFY_FAILED`,
`EXTENSION_MISMATCH`, `REPOSITORY_ERROR`, `SKIPPED_AFTER_GLOBAL_BLOCKER`) and one blob's failure never
rolls back or hides another's bytes. `ALL_PROTECTED` / `PARTIALLY_PROTECTED` / `NOTHING_PROTECTED` /
`REPOSITORY_BLOCKED` (plus `CANCELLED` and `METADATA_INCONSISTENT`) are distinguishable, and only a
repository-wide error stops the run — then the untouched blobs are reported as skipped, never hidden.
A rerun reports `ALREADY_PRESENT` only after the repository hashed the existing canonical file again,
so idempotency is also a byte-level re-verification; a corrupt canonical blob yields `VERIFY_FAILED`.
Protection never deletes, moves or rewrites a private original and never touches Room, DataStore or
snapshots. `AppContainer` only wires the service; nothing calls it.

Device defect found by real acceptance and fixed: `PrivateBlobSource.checkPath` required the raw file
path to be lexically inside the raw private root. On the phone the same app-private directory is
spelled `/data/user/0/...` by the application context and `/data/data/...` by the filesystem, so every
real blob was refused with `SOURCE_CHANGED` (`NOTHING_PROTECTED`) before any byte was read. Containment
is now proven on resolved paths, which is exactly what `docs/repository-v1-foundation.md` already
documented for the pinned canonical mapping; a source that resolves outside the root, a non-file and a
changed root mapping are still refused, and a link below the boundary is refused both on the device
(real `Os.symlink`, with an inside-root and an outside-root target) and, where the host can create a
link, in JVM. The two JVM alias tests are guards for the accepted behaviour rather than a reproduction
of the device's bind-mount alias shape: a Windows drive-letter alias would have satisfied the old
lexical check too, so the device logcat spelling and the instrumented symlink test are the evidence
that matters. A second defect was found in the new PC verifier output: a
verified blob still reported `message = "MISSING"`; the message is now absent for verified blobs and
explicit for every failure, with two tests pinning that.

Verification after hardening. App JVM `:app:testDebugUnitTest` **693 tests / 0 failures / 0 errors /
0 skipped** (this slice adds 34: 10 `RequiredMediaSetTest` incl. the snapshot parity proof, 21
`MediaProtectionTest` incl. the two size-agreement tests and the dedicated `EXTENSION_MISMATCH` test,
3 path-containment tests in `BackupDirectoryBootstrapTest`); `assembleDebug`, `assembleDebugAndroidTest`,
`lintDebug` PASS (0 errors, 24 warnings, 2 hints — unchanged); PC verifier `test` **80 tests /
0 failures** (14 new); `git diff --check` clean. Hardened APK SHA-256: app
`5E710C7F208DC585FD26E750DA2A20D3ED545F34B0A8E4BDA18AB2213A795272`, test
`15D634D30B4347EF17913C539503DBDBFCD1A77CBB19AE9FF75EC9EB0F1A38BE`.

Samsung SM-S938B / API 36 / `RFCY90MBYVZ`, DEV package updated in place (no clear, no uninstall;
`firstInstallTime` unchanged): the installed app APK SHA-256 read back from the device equals the
hardened build above, update time `2026-10-06 23:55:50`. The original acceptance run (earlier build)
reported the required set = the 4 real JPEG blobs from 2 physical-object-media rows and 2
observation-point attachments, `ALL_PROTECTED` with 4 × `INGESTED`, then 4 × `ALREADY_PRESENT`, and
left `Media/` with 4 canonical `<sha256>.jpg` files (18,294,728 bytes total) whose device-computed
SHA-256 equals their own file names. On the hardened build the same harness ran twice more over that
unchanged dataset: `OK (2 tests)` then `OK (1 test)`, both times `ALL_PROTECTED required=4 protected=4`
with every blob `ALREADY_PRESENT`, snapshots 3 before and after, `Staging/` empty, no `Media/` file
rewritten (mtimes unchanged) and the 4 private originals byte-identical. The symlink probe logged
`root=/data/user/0/org.beesearch.app.dev/files canonical=/data/data/org.beesearch.app.dev/files` and
refused both the outside-root and the inside-root link while accepting a real file. Device
`stay_on_while_plugged_in` was set to 15 for the runs and restored to 0.

PC evidence (`TRANSPORT = ADB_PULL`, 8 files / 18,333,530 bytes) is in
`C:\App\BeeSearchBackupResearch\S6A\Backup`; every pulled file's SHA-256 matches the device. All three
real snapshots require exactly those 4 blobs, and each snapshot was verified twice on the PC: standalone
`PASS` 17/17 entries, and the new repository-aware mode `PASS` with 4/4 required blobs verified and
`repository.json` identity `df4d52a9-7033-43c5-9620-e0e5b2fb3e16` / `Dev`. Four negative checks on
workspace copies of that repository fail closed with exit 1: a removed blob (`MISSING`), a same-size
byte flip (`SHA_MISMATCH`), `variant = Beta` (`VARIANT_MISMATCH`) and a stray `Media/junk.jpg`
(`NONCANONICAL_MEDIA_ENTRY`). The new verifier mode is additive; standalone behaviour is unchanged and
is covered by a regression test. After hardening the same six verifications were re-run against the
unchanged accepted copy and reproduced exactly (`PASS` 17/17 and `required 4/4 verified`, exit 0), so
the accepted PC evidence still applies to the committed production bytes.

Not verified / limits to state honestly: real media in this slice is ≤16 MiB JPEG only, so large-video
protection is unproven; repository behaviour under real capacity exhaustion and provider failure was
exercised by tests, not by a real device; protection's own UI, its automatic caller, FULL snapshots,
restore, offload and stale-`Staging` reconciliation are all still absent by design; the known
pre-existing `CleanStartupIntegrationTest` blocked-deletion timeout is unchanged and unrelated.

Hardening after the first acceptance (same slice, before finalization): protection now refuses a
source candidate whose actual size differs from the size the required set declares and never asks the
repository to publish it, so `ALL_PROTECTED` means agreement of SHA-256, size and canonical extension
with the required set; another captured source for the same SHA may still satisfy the blob. Two
mandatory size tests, a dedicated `EXTENSION_MISMATCH` test and dedicated link/symlink rejection
evidence were added. The symlink guarantee is proved on the device
(`MediaProtectionDeviceTest.rejectsASymlinkBelowThePrivateRootAndStillAcceptsARealFile`, real
`Os.symlink`, both an inside-root and an outside-root target refused while a real file is accepted)
and additionally in JVM with a directory junction, because a Windows host cannot create a real symlink
without elevation (`BackupDirectoryBootstrapTest.aLinkBelowTheRootThatResolvesOutsideItIsRefused`).
Independent reviews: `S6A_CRITIC_NO_BLOCKER` before the hardening and `S6A_FINAL_CRITIC_NO_BLOCKER`
after it; the one remaining correctness nuance the first review raised (required size not compared with
the actual source size) is now closed in code and covered by tests, and the second review's findings
are documentation-accuracy NITs about this handoff's wording plus three accepted coverage NITs.

Earlier slice recorded for context (owner-accepted, unchanged here): S3 produced
`S3_PIPELINE_PASS` and `S3_DATASET_COVERAGE_SUFFICIENT` — the real DEV repository holds the three
snapshots from that acceptance (`06c4610b`, `1f9e43e7`, `8d6a9be2`), each requiring the same four
media blobs that S6A has now protected.

Next (owner decision, in order): S6B (FULL snapshot plus repository-aware PC verification), then
stale-`Staging` reconciliation, then the S5 large-media policy, then the PC handoff, offload and
restore. S4 (streaming export) was not started and is not authorized by this slice; protection still
has no UI and no automatic caller.

## Current continuation — S2 METADATA_ONLY Backup UI (working tree, owner review pending)

S2 extends `Настройки → Резервное копирование` so the user can create the already accepted
Repository Snapshot V1 METADATA_ONLY backup by hand. Fixed Backup location, SAF grant, exact-folder
validation and initialize/adopt/reconnect are S1's and unchanged.

`BackupSnapshotOperations` is the only seam the screen may use for snapshots; its production
implementation is a pass-through to the accepted `RepositorySnapshotService` (which itself goes
through `BoundRepository`), so no UI code touches Repository V1, captures a graph or writes an
archive. `BackupOperationCoordinator` (Android-free, JVM-tested) owns two rules: **one creation at a
time** (a second request while one runs returns `AlreadyRunning` and publishes nothing) and **the
repository is the source of truth** — every read calls `discover()`, a successful creation
immediately re-reads it, and no timestamp is stored in Room or DataStore. Discovery maps to
`None` / `Latest(createdAt, warning)` / `Unusable`: unusable candidates are never hidden, a valid
latest backup is still shown next to the warning, and when nothing can be validated no last-backup
claim is made. Creation problems are grouped into access/space/logical-data/snapshot/identity/
provider/cancelled groups; an access-class problem sends the screen back to the S1 access states
(«Восстановить доступ») rather than opening a second access branch.

Owner decisions for the S2 UI, taken from the owner-provided mockup
(`C:\App\Bee_search_ui_input\Макет интерфейса резервного копирования.png`, deliberately not copied
into the repository), recorded here for later slices: **no numeric progress** (indeterminate only),
**no technical tokens** in normal UI (`METADATA_ONLY`, ZIP file name, SHA, snapshot UUID, repository
UUID), **no explicit «Отмена»** button and no new cancellation architecture, and
history/details/rename/delete/restore/PC/offload stay out of scope. The implementing screen was
compared against that mockup on the Samsung at the owner's real font scale 1.7 (title, explanation,
tinted photo/video notice, last-copy line, full-width primary action); the mockup's percentage ring,
`METADATA_ONLY` row, file name and «Отмена» were deliberately not implemented. The screen keeps the
approved wording «Резервная копия создана. Данные исследований сохранены. Фото и видео в эту копию
не входят.» and never says «полная резервная копия» или «все данные сохранены».

Next: owner review of the S2 diff, then the separate **S3** slice (real non-empty dataset → snapshot
through the production UI → copied to PC → independent PC verifier → acceptance). S2 proves only
that the production UI drives the accepted service correctly. The known pre-existing
`CleanStartupIntegrationTest` blocked-deletion timeout is unchanged by S2 (same single failure as the
S1 baseline `09b148a`).

## Previous milestone — S1 Production Backup Repository Access (accepted and finalized)

Finalized as commit `09b148a25affa4c509ba8a619ced47feb09c146f`
(«Implement production backup repository access»), pushed to `origin/main`. Its S1 verdict was
`S1_READY_FOR_OWNER_REVIEW`; the owner then accepted it and the working tree was clean at that commit.

Original implementation report: fixed per-variant backup location, one-time SAF grant for that exact
folder, automatic initialize/adopt/reconnect.

Recorded as **D098** (S1) and **D099** (S2). The user never chooses a backup folder:
`BackupLocation` is the single source of `BeeSearch/<variant>/Backup` and of its SAF document id
`primary:Download/BeeSearch/<variant>/Backup`, and `BackupDirectoryBootstrap` derives its skeleton
from it. The production picker accepts ONLY that exact tree — `Download` itself, the `BeeSearch`
root, `Exchange`, another variant, nested folders, other providers and SD cards are rejected before
any repository call. A `…/tree/<expected>/document/<other>` result is rejected as well, because the
repository layer honours the document segment of such a URI. The grant is persisted only after that
validation (device-verified: `persisted=0x0` for a rejected tree, `persisted=0x3` for the accepted
one), and `status()` prepares the fixed skeleton before the access probe, so a removed `Media`
child directory is repaired while conflicting content still fails closed. The Android-free
`BackupAccessCoordinator` owns the state machine: existing binding → `reconnect` of the same UUID;
UNBOUND → `adoptExisting`; only a genuinely absent `repository.json` → `initializeNew` in an
otherwise valid empty skeleton; foreign UUID, invalid/unsupported header, ambiguous content,
unreadable binding and UNKNOWN capacity fail closed with the previous binding preserved. UI errors
are grouped into typed problems with Russian messages; raw repository errors never reach a screen.
Route: `Настройки → Резервное копирование` (`AppRoute.Backup`), Back → Settings. Startup is
unchanged (skeleton + read-only probe, no modal); field capture is never blocked.

Verification: JVM 629 tests / 0 failures/errors (S1: 18 coordinator, 7 UI-state, 4 location);
`assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` PASS (0 errors, 24 warnings, 2 hints —
unchanged from baseline); `git diff --check` clean. Samsung SM-S938B/API36 with preserving DEV
updates (no clear/uninstall): 12 focused instrumentation tests PASS (`BackupScreenTest`,
`AndroidBackupTreeAccessDeviceTest`, the Settings→Backup→Settings route test). Manual DEV smoke
PASS: the picker opens at `Download/BeeSearch/Dev/Backup`; a wrong folder shows the recoverable
message, offers the picker again and creates no `repository.json`; the exact folder gives
`✓ Доступно` with `repository.json` `df4d52a9-7033-43c5-9620-e0e5b2fb3e16` (sha `162feffe…828a`);
force-stop/relaunch keeps `✓ Доступно` with a byte-identical header; with only
`repository_binding.preferences_pb` removed (reinstall-equivalent) the same folder is ADOPTED —
same UUID, same mtime, binding restored. Independent critic: **S1_CRITIC_NO_BLOCKER**; both
high-priority concerns were closed before acceptance. Detailed pre-fix defect found on the device:
the grant was persisted before validation (stray durable grant for a rejected folder) — fixed and
re-verified.

Known pre-existing failure, verified on the untouched baseline `fd0e380` in a separate worktree:
`CleanStartupIntegrationTest.deletingAnObjectUsedByObservationDataIsRefusedAndKeepsTheCardOpen`
times out waiting for the blocked-deletion feedback. Not caused by S1, not fixed here.

Residue: DEV still holds a persisted grant for `Download/BeeSearch/Dev`, created by the pre-fix
intermediate build during that verification; the final code never persists a grant for a rejected
folder, and the entry disappears with a DEV data reset.

## Previous continuation — Snapshot G1–G6 aligned; owner diff review pending

2026-10-06: HEAD/origin main b3eec7d9e54011f1e482e68e7ee8c5e94efc0eba.
Preserved approved MIME/weather dirty diff; owner authorized G2–G6 creation and
reader validation together. G2 normal capture gate NORMAL_PRODUCTION_MAX_10:
count guard and insertion inside one Room transaction. Shared Snapshot raw
semantic boundary now enforces closed schemas, exact collection order, per-point
Bee limit, weather, raw nullable/incomplete media eligibility/reachability,
per-collection UUID scope, strict lexical rules and non-normalizing v1/v2 coverage.
General strings unchanged; RepositoryPolicy unchanged; PC verifier independent.
Legacy graph UUID scope unchanged by default, Snapshot-only opt-out tested.
Focused Snapshot 69/69 PASS; full JVM 600 tests, 0 failures/errors/skipped.
assembleDebug/assembleDebugAndroidTest/lintDebug PASS (0 errors,24 warnings,2 hints).
PC offline command SUCCESS/UP-TO-DATE, retained 66 tests/0 failures/errors.
Independent cross-author critic found no remaining concrete blockers; typed
embedded coverage limit preservation fixed. git diff --check PASS. No device,
real-media ZIP rerun, R2/FULL/restore/UI/stage/commit/push. Details and historical
STOP evidence: docs/snapshot-v1-reader-alignment-status.md.
Verdict READER_ALIGNED_WITH_WIRE_V1; STOP for owner review of the complete dirty diff.

## Historical continuation — Snapshot weather aligned; writer gate STOP

2026-10-06: HEAD/origin main b3eec7d9e54011f1e482e68e7ee8c5e94efc0eba.
Approved MIME reconciliation diff is preserved and uncommitted. Weather state
gate: TEST_FIXTURE_ONLY in current normal production paths (not a live DB audit).
SnapshotDomainCodec now shares exact weather matrix/cardinality checks between
creation and reader; no data normalization. Corrected valid fixture and retained
negative UNAVAILABLE/source="none" tests. Focused weather/MIME/determinism 9/9
PASS; independent PC offline test command SUCCESS/UP-TO-DATE, 66-test corpus.
Required writer recheck found 11 Bees on one point can still be encoded, built
and reopened successfully, against wire maximum 10. Owned 1-test JVM diagnostic
confirmed it; temporary probe source removed. STOP before G2–G6 implementation.
No full regression/build/lint/device/commit/push. Owner review must authorize the
remaining creation AND reader validation scope. Details:
docs/snapshot-v1-reader-alignment-status.md. Do not claim reader alignment complete.

## Current milestone — Slice 2B final acceptance audit ACCEPT

2026-10-06: reviewed task-only diff from dc40b209. Exact default V1 limit boundary
tests and all-13-collection deterministic raw-entry comparison added; production
limiter only gains internal test visibility, no second algorithm. Capture/serialization/
staging/final-reopen/cancellation injection preserves prior history. Adjacent fresh UUID
publication check and Room transaction device evidence confirmed. Independent critic:
no blocker. Final full regression 574 JVM tests, zero failures/errors;
assembleDebug/assembleDebugAndroidTest/lintDebug PASS (0 errors,24 warnings,2 hints).
Existing Samsung smoke/sizing reused, no new device actions or user-content read.
Detailed audit: docs/repository-snapshot-v1-final-acceptance.md. Only explicit reviewed
files may be committed; .review-tmp/temp/generated excluded and preserved.
Owner authorized separate Slice 2B commit; STOP BEFORE PUSH. No R2 or PC verifier.
Snapshot user entry point remains code/tests only; future UX requires mockup-first.

## Current milestone — Repository V1 Production Slice 2B (owner review pending)

Baseline 3db5dda; approved preflight committed/pushed alone as dc40b2092154f4d1a3b7dd3d1e70526e9ee95c7a.
HEAD/origin main remain that contract commit; Slice 2B source/tests/docs are uncommitted.
Explicit METADATA_ONLY / NO_MEDIA_EVIDENCE / COMPLETE service and strict fixed ZIP reader;
17 entries, transactional Room capture, portable FK checks, deterministic domain records,
raw-byte digests, preserved canonical corpus, guarded owned staging/move/final readback, discovery.
No new UI, FULL/R2, DEGRADED creation, restore, PC ingest, handoff, coverage or offload.
Samsung SM-S938B API36: preserving DEV/test update, canonical+numeric+transaction tests PASS;
read-only DEV sizing: ZIP13106, uncompressed44761, maxrecord484 bytes. No user content exported.
Disposable smoke run38f11516-fa3e-466a-8b7d-317c8445d8ee under _poc/ProductionSlice1:
snapshot6584c2c5-ae4f-4081-a024-bbbc02957107, SHA1d7fee104800589f4ac7be29ffa4af7a783636edc564103726004dd05f51ff62;
create/restart/discovery/corrupt-copy rejection PASS; valid original unchanged. Evidence retained.
Critic: no production blocker after mediaReferences context and typed IO fixes; disk peak320MiB
rechecked (not heap). Independent hand-authored nonempty ZIP and bounds tests added.
Final regression:564 JVM tests,0 failures/errors; assembleDebug/AndroidTest/lint PASS
(lint0errors,24warnings,2hints). Final APK smoke again PASS,2valid/2corrupt evidence retained.
Latest synthetic snapshotdb78bc59-f91e-4377-84cc-4394e25a49bd,
SHAe123cd9fdd5630a18443d22f8f7de722127dbe23ccfc100d2c8fc868c4f37bcc,3525bytes.
Final aggregateDEV ZIP13105/uncompressed44761/maxrecord484. Report:
docs/repository-snapshot-v1-slice2b-report.md. No Slice2B commit/push;
STOP for owner review. Inaccessible pre-existing .review-tmp directories were not touched.

## Current milestone — Repository V1 Production Slice 2A (final audit ACCEPT)

Slice 1 owner ACCEPTED and committed separately:
`1f353497be746187354cf4d2868262be962a7f12`; clean tree confirmed before Slice 2A. No push.
Slice 2A: durable install-local expected UUID + SAF locator, explicit init/adopt/reconnect/rebind,
fresh identity gate before writes. No snapshots/restore/PC/handoff/coverage/offload/production UI.
DataStore file under excluded install-state; no portable settings/Room migration.
Verification 2026-10-06: 525 JVM tests, 0 failures/errors; assembleDebug,
assembleDebugAndroidTest and lintDebug PASS. Independent critic found no concrete binding
blocker; root reviewed the final task diff. git diff --check PASS.
Windows FileStorage replacement failed two initial persistence tests; host-only tests now
use the existing official OkioStorage/PreferencesSerializer backend, without dependency or
Android storage changes. Android FileStorage was exercised on Samsung.
Samsung RFCY90MBYVZ / SM-S938B / API36: preserving DEV/test update; no clear/uninstall.
Owner selected the new disposable tree; prepare/bind/reconnectAndRecreate each PASS (1 test).
Run: 879198d5-f933-4f30-b565-331cfa660731 under _poc/ProductionSlice1.
Expected UUID: 8880d149-db43-41f6-98fb-da8ef361f699;
other UUID: b062eb8d-cd45-4b2e-94cc-6b161748d7bd.
Restart preserved binding; mismatch left binding/header unchanged; return PASS.
Only newly owned EMPTY RepositoryA was deleted/recreated; old binding rejected empty
replacement as BOUND_REPOSITORY_MISSING, no automatic initialization/writes.
Isolated smoke DataStore did not change the actual app binding. Recreated A empty; B intact.
Final audit: publication UUID reread moved directly before move; replacement-before-publication
test PASS. Corrupt protobuf/CAS concurrency/competing adopt tests added; 531 JVM tests green.
Standalone tools/repository-binding-audit built from production sources (no production Gradle edits).
Initial missing Main-dispatcher harness crash fixed by matching test-only coroutines-android 1.9.0.
New run a55ab34b-f43f-4f93-be89-7aeaef8b4930; UUID b5a04173-d5aa-4e48-863f-0fc65ff2fd24.
Samsung normal tiny JPEG publication/duplicate PASS; restart BOUND; uninstall/reinstall ONLY
org.beesearch.bindingaudit => UNBOUND; explicit adopt => original UUID. Public header/blob SHA unchanged.
No media rehydration, no DEV/Stable/Beta uninstall/clear. Same-UUID stale-copy limitation documented.
Owner authorizes separate Slice 2A commit after final ACCEPT. No push; do not start Slice 2B.
Final full regression/DEV and test assemble/lint PASS (531 tests, zero failures/errors);
standalone audit assemble/lint PASS. Exact diff reviewed, generated data excluded,
git diff --check PASS. Independent review found no concrete blocker; test-only dispatcher
correction separately reviewed. Accepted limitations: provider move is not UUID-conditional,
mid-persistence crash physically unproven, stale same-UUID copy needs future snapshot context.
Slice 2A checkpoint is recorded by the separate Git commit containing this handoff. STOP before push/2B.
Never reuse/delete prior Slice 1/R0 evidence or real repositories for recreation test.

## Accepted Slice 1 evidence (historical)

### Repository V1 Production Slice 1

Baseline: clean `main`, HEAD/origin/main `4ef0de06715e5065d54c2ed5b46c22c6e5ec703e`.
Owner authorized foundation only: skeleton, explicit header UUID initialization/open, typed SAF
adapter, fixed configurable 20 GiB reserve, immutable flat SHA blob publication via owned Staging
and same-storage move with full staged/final verification. See D095 and
`docs/repository-v1-foundation.md`; next free decision is D096.

No Complete Backup, Room schema, snapshots, PC ingest, handoff, offload or production backup UI
changes. No commit/push. Current JVM result: 511 tests, 0 failures, including 42 foundation tests.
Final production DEV assemble/unit tests/lint passed; test-only video generator was then adjusted
to a supported 256x256 frame. Its final assemble and lint passed.
Samsung: RFCY90MBYVZ / SM-S938B / API36, ~98 GB available. DEV/test APKs updated with install -r -t,
no clear/uninstall. Old run `b1c39536-0cf4-49f9-aa61-bd73898506ab` failed fixture preparation
at AVC start and is preserved. New preparation PASS (1 test), runId:
`605340a6-3a60-44d2-86ed-e64d0410c72f`.
First owner selection through the test APK did NOT persist a DEV grant; #publish failed with
SecurityException before header creation. Replaced forwarding harness with debug-only
RepositorySmokeAccessActivity in the DEV UID: takes exact leaf grant immediately.
Old unregistered test helper removed. DEV APK preserving update succeeded; corrected system
picker reopened with runId `605340a6-3a60-44d2-86ed-e64d0410c72f`.
Second owner confirmation persisted the DEV grant. Device-discovered private-root alias defect
fixed: lexical containment below trusted root, pinned canonical-root mapping, descendant symlinks
rejected. Separate correction review cycle 2 found no new blocker. Test assertions now use SAF.
#publish PASS and #restartAndConflict PASS after explicit DEV force-stop/relaunch.
UUID `ba879c42-77ad-46f8-a46a-70fee456798b` preserved; JPEG 1146 bytes and MP4 1549 bytes
full SHA matches independent shell sha256sum. Third owned synthetic blob intentionally same-size
corrupted: VERIFY_FAILED, no overwrite. Evidence retained; source fixtures remain private.
Final testDebugUnitTest, assembleDebug, assembleDebugAndroidTest and lintDebug passed.
Never use production root or old R0 evidence for the corruption test. Preserve DEV package/data.

Next: owner review of Slice 1 diff. Expected UUID binding persistence/selection remains a later
integration boundary; foundation writes require explicit expected UUID. No backup UI yet.
Do not begin Slice 2 automatically.

## Historical operational context — Physical Object Export/Delete V1

Implemented on top of clean baseline `eb130c2764bdad99574f3b5190fd7f6a57477834` and left
uncommitted for owner review. Do not commit or push before owner review.

Scope: Дупло (`Hollow`) and Колода (`LogHive`). `Apiary` stays out of scope: it keeps no creation UI,
no category, no card, no media and no delete path, and the new export refuses it fail-closed instead of
writing a half-shaped package. Recorded as **D093**; next free durable decision is **D094**.

Export: a new isolated package `data/objectexport` implements the portable
`SINGLE_PHYSICAL_OBJECT` `formatVersion = 1` profile (field-level contract in
`docs/physical-object-export-v1.md`). A package carries the object's own data (identity, subtype
properties, owned media metadata and bytes) plus a minimal read-only labelling/provenance snapshot of
the Territory (id/code/name) and the creator Observer (id/code/ФИО). Bee, FlightCycle, ObservationPoint,
their weather and their attachments never enter a package, and an object referenced by a Bee still
exports. The source read uses `PhysicalObjectRepository`, which has no read path to observation data at
all. The ObservationPoint codec was deliberately not refactored into a shared framework; the ZIP/hash
JSON mechanics are repeated locally so the verified point export keeps its regression surface.
Robustness: strict reader (declared profile/version/type, declared entry set, declared JSON field set,
size+SHA-256 for `object.json` and every media entry, deterministic media paths, entry/archive caps,
`ZipEntry.time = 0`, canonical media order). SAF write is staged: the archive is built and verified in
app cache and only a complete package is copied into the picked document, so a missing or damaged media
file cannot leave a truncated package. The actual guarantee is "no partial or corrupt package is
written"; the picker may already have created an empty destination document, which the app does not
delete because it is the user's chosen file.

Collection export extends the same uncommitted D093 with a separate
`PHYSICAL_OBJECT_COLLECTION`, `formatVersion = 1` profile. The `Дупла` and `Колоды` list toolbars now
have one `⋮` action — `Экспортировать все дупла` or `Экспортировать все колоды` — and explicitly no
bulk delete. One action creates one ZIP for the current Territory and one concrete type; no nested
single-object ZIPs, Apiary or mixed-type export. Entries are `manifest.json`, `territory.json`,
`observers.json`, `objects/<object-id>.json` and `media/<object-id>/<media-id>`. Territory is stored
once, used Observer snapshots are deduplicated, and object/media entries are canonical, size/hash
declared and strict. The source uses `listForTerritory`, selects only the requested concrete type and
loads only its owned media. Empty collections return a typed result before SAF; one damaged object or
media aborts the whole package. The staged adapter re-decodes the temporary archive before opening
the single SAF destination. Suggested names are `DEV--hollows--YYYY-MM-DD.zip` and
`DEV--log-hives--YYYY-MM-DD.zip` through the existing sanitizer.

Delete: blocking references are collected inside the delete transaction and returned as a structured
model (`PhysicalObjectReferenceKind` + count, today only `BEE`) instead of a scalar count. With blockers
nothing is removed — no subtype row, no media rows, no media bytes — and the card shows a dedicated
dialog `Объект нельзя удалить` naming the blocking data and its amount (`Пчёлы — 3`) with no force
delete, cascade or "delete anyway" action. The blocked-deletion result is a one-shot request on the
route with an explicit consume step, not a durable flag. `RESTRICT` FKs stay the second, fail-safe line
of defence, and `PhysicalObjectReferenceRestrictTest` now also fails when a non-owned table referencing
`physical_objects` is not represented by a blocker kind. Numbering high-water state is still untouched
by an ordinary deletion.

Fixed defect: an unsafe stored media path used to make post-commit cleanup throw, so the UI reported
"Не удалось удалить объект" although the database deletion had committed. Cleanup is now best-effort:
such a path yields `fileCleanupComplete = false` and the existing
`Объект удалён, но не все файлы медиа удалось удалить` message, while path safety still refuses to
resolve or delete the escaping path.

Verification: JVM `:app:testDebugUnitTest` `419/419 PASS` (24 new object-export tests plus the Help
drift check after regenerating `HelpContent.kt` from `docs/ui/help/help-v2.md`); `assembleDebug`,
`assembleDebugAndroidTest` and `lintDebug` pass; `git diff --check` clean.

Preserving Samsung SM-S938B instrumentation (`android.injected.androidTest.leaveApksInstalledAfterRun`
stays enabled, no clear and no uninstall): a focused run of the new and touched classes —
`PhysicalObjectDeletionBlockerTest`, `PhysicalObjectExportDocumentContractTest`,
`PhysicalObjectReferenceRestrictTest`, `FileAwarePhysicalObjectDeletionTest`,
`PhysicalObjectNumberingAndDeletionTest`, `PhysicalObjectCardsTest` — reports `53 tests / 0 failed`,
including all `RESTRICT` and staged-write cases. The full connected suite was not re-run.

Manual device walk on `org.beesearch.app.dev`, at the owner's real system `font_scale = 1.7`, with the
existing DEV objects left intact: map → `Объекты` → `Дупла` → card `Дупло 3` (`дупло в старой липе`)
showed `Редактировать характеристики`, `Экспортировать объект`, `Удалить объект` with full labels;
the picker opened in `Download/BeeSearch/Dev/Exchange/Data` with the suggested
`DEV--hollow-3--2026-09-27--68711d39.zip`, saving it produced a 954-byte package, and the pulled file
contains exactly `manifest.json` (profile `SINGLE_PHYSICAL_OBJECT`, `formatVersion 1`, type `HOLLOW`,
`object.json` descriptor) and `object.json` (identity, `HOLLOW` properties, empty media, Territory
snapshot `DEV / DEV Territory`, Observer snapshot `DEV-OBS1 / Testerov Ivan`) with no Bee, FlightCycle,
ObservationPoint, weather or attachment data. The `Колода 1` card suggested
`DEV--log-hive-1--2026-10-01--d170bb70.zip`; cancelling that picker returned to the card with no file,
no success message and no error. The ordinary confirmation `Удалить Дупло 3?` is unchanged and
cancelling it changed nothing.

Collection-export verification added after that V1 pass: full `:app:testDebugUnitTest`, Help
generation/drift/content checks, `assembleDebug`, `assembleDebugAndroidTest`,
`compileDebugAndroidTestKotlin`, `lintDebug` and `git diff --check` pass. New focused tests cover both
types, deterministic/canonical output, strict profile/version/entry/identity/media rules, caps,
observer deduplication, nullable state, empty result, all-or-nothing corruption, staged SAF and the
Room source Territory/type boundary. A preserving Samsung run of
`PhysicalObjectCardsTest`, `PhysicalObjectListResetTest`,
`PhysicalObjectExportDocumentContractTest` and `PhysicalObjectCollectionExportSourceTest` reports
`29 tests / 0 failed`; DEV remained installed and app data was not cleared.

Manual Samsung SM-S938B check at the existing real `font_scale = 1.7`: `Дупла` contained two saved
objects and showed a readable one-item menu with only `Экспортировать все дупла`; one SAF flow opened
in `Exchange/Data` with `DEV--hollows--2026-10-02.zip`. Saving produced a 4,102,162-byte package with
profile/version `PHYSICAL_OBJECT_COLLECTION/1`, type `HOLLOW`, `objectCount = 2`, exactly one Territory
and observers entry, two object entries and the owned media entry, with no LogHive or observation
payload. `Колоды` showed only `Экспортировать все колоды`, suggested
`DEV--log-hives--2026-10-02.zip`, and cancelling returned neutrally with no feedback. No real object
was created or deleted for testing; empty-list behavior is therefore instrumentation-only.

Not verified manually: the blocked-deletion dialog. No current screen can create a `Bee → object` link
(`setBeeSourceObject` has no UI caller), and fabricating one in the owner's DEV database would alter
real data, so that scenario is covered by instrumentation only.

## Previous milestone — Help V2 illustrations

The owner-approved Help V2 illustration pass is complete in the current dirty
worktree, based on baseline HEAD `28ebc00f9ebb2265b4e85ed6c9b059477320085c`.
Do not discard the surrounding uncommitted UI/map/observation work and do not
commit or push before owner review.

`docs/ui/help/help-v2.md` remains the canonical source. It now declares exactly
three real Samsung screenshot resources for `Основная информация карты`,
`Управление картой` and `Экран активного наблюдения`; the generated
`HelpContent.kt` is synchronized through the existing generator. The phone
layout is intentionally vertical: full-width screenshot followed by native
Help text, with normal vertical scrolling instead of a narrow two-column
composition. After owner review, the detailed-help order is initial setup,
map information, map controls, then observer and territory settings.

Verification completed: Help generation and drift/content tests, Help Compose
instrumentation (`8/8` on Samsung SM-S938B), `assembleDebug`,
`compileDebugAndroidTestKotlin`, `lintDebug` and `git diff --check` pass. DEV
was installed in place as `org.beesearch.app.dev`; no clear or uninstall was
used. Manual device review at system `font_scale=1.7` confirmed all three
screenshots and their native explanations remain readable, scroll correctly
and do not overlap. The map controls, GPS-to-target guide, all three observation
states and Bee direction arrows are visually distinguishable.

## Current release

Beta `1.3.0-beta.4` (versionCode 7) was published locally from commit
`434e319b` on clean `main` with the canonical workflow
`tools/beta-release/beta_release.py`:

- artifact: `C:\App\Bee_search_beta_releases\1.3.0-beta.4\bee-search-1.3.0-beta.4-434e319.apk`
- SHA-256: `a1f6d88a5191472762e5722a7fab659eab1de4f8db4be24ae6da16ec5f9e9dcb`
- package `org.beesearch.app.beta`, signing certificate unchanged
  (`2fd4f10a…b654`), Stable and Dev were not touched, nothing was pushed.

The Beta packages the help rework from `bdfbb340` `feat: rebuild the in-app help
around user workflows`: the in-app help is now workflow-oriented and generated
from `docs/ui/help/help-v2.md`. Its purpose is tester feedback on the help, so
the declared image slots are deliberately still empty: no screenshots were added
and no PDF exists.

Release validation for this Beta: `:app:testDebugUnitTest` `375 tests / 0
failures` (this includes the help source-drift check), `:app:assembleBeta` and
`:app:lintBeta` pass (32 warnings, 0 errors), `beta_release.py --check-only`
reports `PASS`, and the published APK was verified with `aapt` and `apksigner`
(package, versionCode, versionName and certificate). No device installation was
performed: the workflow verifies the artifact metadata itself, so the Beta is
handed to testers as the archived APK.

Next step: collect tester feedback about the help — where it does not say what
to do, where it says too much, and where finding a control is hard without an
image. Choose screenshots only after that feedback, then prepare the next Beta.

## Current milestone

Optional user names for Hollow/LogHive and the unambiguous entrance-direction instruction are
implemented. `name` is nullable, editable and non-unique; it is stored in the concrete subtype
row, shown above the unchanged `Дупло N` / `Колода N` designation, and does not affect UUID or
numbering. The compass still persists the production true heading of the phone's top edge; only
the instruction changed to `Направьте верх телефона в ту сторону, куда направлен леток`.

Room schema v11 adds only nullable `name` columns to `hollows` and `log_hives`
(`MIGRATION_10_11`). Complete Backup V6 carries the names and reads V1–V5 with null names.
D091 records these boundaries; D092 records the accepted Sentinel/Hybrid research reference.
The next free durable decision is D093.

Verification for this increment: JVM `376/376 PASS`; debug APK, androidTest APK and lint pass;
focused preserving Samsung instrumentation `93/93 PASS`. At real Samsung font scale 1.7, the
Hollow flow was checked through create, card, rename, typed list and deletion; designation stayed
`Дупло 2`. Manual LogHive completion was not finished after semantic automation left the form,
although its create/update/name paths passed instrumentation. No DEV data reset occurred.

Safe physical object deletion, monotonic numbering and an explicit numbering
reset are implemented over the approved Objects UI and accepted by the owner,
who also verified them manually on the phone.

Last functional commit: `50c75b2e` `feat: add safe physical object deletion and
numbering reset`; the help rework followed in `bdfbb340`, and `434e319b` only
bumps the Beta version metadata.

What is in place:

- Objects V1 UI is implemented: `+ -> Дупло / Колода`, crosshair coordinates,
  live/manual azimuth, media, saved-object cards, characteristic editing and
  coordinate correction.
- The browser hierarchy is corrected: `Объекты` shows the categories
  `Ареал`, `Точки наблюдения`, `Дупла`, `Колоды`; a row is `Дупло N` / `Колода N`
  without repeating the type; Back goes card -> its list -> `Объекты`.
- `карточка -> Показать на карте -> Back` returns to the same card, and an
  ordinary map opening never inherits that return target.
- An unused Hollow or LogHive can be deleted from its card after confirmation
  (`Удалить объект`); the object, its subtype row, its media rows and its
  app-owned media files are removed, and the deletion returns to the typed list.
- Deleting a protected object is blocked: a `Bee -> object` reference (and any
  future `Inspection` reference) refuses the deletion with a readable message
  and keeps the card open.
- Persistent numbering high-water mark (`physical_object_sequences`, scope
  `Territory + object_type`) is allocated inside the create transaction as
  `max(last_issued, live MAX) + 1`; a failed creation consumes no number.
- Ordinary deletion never frees a number: deleting `Дупло 4` still gives the
  next created object `Дупло 5`.
- An explicit, fail-closed reset restarts one scope from 1, only while that
  scope provably holds no objects, no dependent rows and no references; it is
  offered in the empty typed list and re-checked in the repository. The stable
  UUID remains the object identity: a repeated designation is a new object.
- Room schema v11 (`MIGRATION_9_10` backfills sequence state; `MIGRATION_10_11` adds nullable
  Hollow/LogHive names without rebuilding identity tables).
- Complete Backup V6 carries `physical-object-sequences` and Hollow/LogHive names; the reader
  accepts V1-V5 and bootstraps archives without sequence state from stored numbers.
- D090 (ACCEPTED) records numbering semantics; D091 records optional Hollow/LogHive names;
  D092 records the accepted Sentinel/Hybrid research reference. Next free durable decision: D093.
- The RESTRICT invariant has an automatic test: `PhysicalObjectReferenceRestrictTest`
  reads the foreign keys of the live database and fails if any reference to
  `physical_objects` is not `ON DELETE RESTRICT`.

## Verification status

JVM unit suite `390 tests / 0 failures`; `assembleDebug` and
`assembleDebugAndroidTest` pass; `lintDebug` passes with no new findings;
`git diff --check` clean.

Preserving Samsung SM-S938B instrumentation: the full connected suite reports
`386 tests, 12 skipped, 0 failed`, and a focused run of the numbering, deletion,
media, RESTRICT, backup, migration, browser and reset classes reports
`105/105 PASS`. Device walk on `org.beesearch.app.dev` covered create -> delete
(confirmation, return to `Дупла`, absence after restart), no reuse of the
deleted number, LogHive deletion with its own confirmation text, and a reset in
a disposable Territory (counter 2 -> next object `Дупло 1`) that was then
removed again. Pre-existing DEV objects were preserved
(`ceDataInode=438163`, `deDataInode=424656` unchanged); DEV was updated in
place without clearing data.

The owner additionally checked deletion, number non-reuse and the explicit
reset manually on the phone and confirmed that the behaviour matches the intent.

Sentinel/Hybrid research is closed by D092. Owner review on Samsung SM-S938B
accepted the 2026-07-18 source-direct linear-B rendering
(`display = uint8(round(255 * clip(7.5 * reflectance, 0, 1)))`) in
`sentinel-area-z10-13-linear-b.pmtiles`, SHA-256
`135246b8f2f5e99e50b2361fb63a0539dff1c071074a075982d125ee2c09daff`.
The raster has real z10-z13 and both the DEV Sentinel and Sentinel-based Hybrid
stop at UI z13. Hybrid was more informative than bare Sentinel: independent
vector roads, tracks/paths, waterways and offline labels sit above the raster,
then Bee Search overlays. Sparse place labels are current package data, not a
glyph failure. Cutlines and power lines exist in the vector package but are not
enabled in this first Hybrid overlay.

The accepted artifact remains external at
`C:\App\Bee_search_test_maps\sentinel-area\sentinel-area-z10-13-linear-b.pmtiles`.
The byte-identical DEV staged file is
`/sdcard/Android/data/org.beesearch.app.dev/files/poc-sentinel/sentinel-area-z10-13.pmtiles`.
The existing selector preserves four DEV modes: `Онлайн карта`, `Векторная
карта`, `Спутник Sentinel`, `Гибрид`. Raster/vector stay independent; this is a
research/reference capability and no production raster package lifecycle exists.

The current vector package remains true z8-z15 with z16+ MapLibre overzoom.
Planetiler stays pinned at 0.10.0 and `field-profile.yml` stays at maxzoom 15.
Owner decision: do not update Planetiler or generate true vector z16-z18 now.
When a quality high-resolution raster is available, first test it with the
existing z15 vector overzoom and revisit vector generation only if field evidence
shows insufficient detail.

## Unverified / known residue (no action required for the accepted feature)

- Media deletion was not exercised through the real camera / system picker UI;
  file ownership and cleanup are covered by device instrumentation instead.
- Deleting a protected object was not exercised through real Bee-history UI; the
  blocking path is covered by repository, integration and schema tests.
- The new delete/reset UI was not separately inspected at a large system font
  scale (it is text-button based and the system scale was left untouched).
- A restore rollback warning does not exist: the application has no restore
  flow at all (only export), so there is nowhere to compare the current and the
  incoming numbering state. Restore semantics are documented in D090.

## Next task

No active functional implementation task remains after the Sentinel/Hybrid PoC
closure. Continue from the current committed `main` HEAD. The current release
context remains the local Beta `1.3.0-beta.4` (versionCode 7) described above;
this PoC closure does not build or release Beta/Stable and does not change the
product version.

The Sentinel/Hybrid feasibility cycle is closed. Do not repeat transport,
brightness, Sentinel zoom or Hybrid composition experiments without a new
question. The next architecture task is to design a source-neutral offline
raster preparation/package workflow covering validation, reprojection/mosaic,
real zoom pyramids, raster PMTiles, metadata/manifest and verification for both
Sentinel and future high-resolution georeferenced imagery. Production
import/acquisition/lifecycle is not implemented; legal high-resolution source
and licensing research remains separate. Do not turn the preserved DEV profiles
into a production architecture implicitly.

## Previous milestone

The in-app help was rebuilt around user workflows in `bdfbb340`: the canonical
text lives in `docs/ui/help/help-v2.md`, the in-app content is generated from it
and checked by a drift test, and the help now explains the first launch, the main
map screen, the observation workflow, `Объекты`, дупла and колоды, the object
card, deletion with the number rule, the numbering reset, offline against online
behaviour and export. Before that, safe physical object deletion, monotonic
numbering and the explicit numbering reset were implemented in `50c75b2e`.
