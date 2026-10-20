# Bee Search handoff

## Latest qualification — My points Stage B PASS on emulator (2026-10-20)

Targeted fixes complete on task/d106-stage0-overlay-registry, baseline8ebcf06.
Draft FileProvider now permits exactly files/user-point-media-staging; existing
content URI/read-only temporary grants, ownership cleanup and managed lifecycle
remain intact. Local approved-composition fixes: title/subtitle, six-decimal
coordinates, photo divider, card hero1.8. No approved PNG was changed.
JVM1067/1067 and final API29 Android16/16 PASS at actual fontScale1.7; debug app
and test APK builds and lintDebug PASS (0 errors,33 warnings,6 hints); diff-check
PASS. Installed emulator APK SHA c66bd654...937febe8 equals local build.
Manual draft/saved external viewing, multiple gallery photos, save/card, rotation,
Back/cancel and owned cleanup evidence retained. Camera fake success/cancel PASS;
real camera, TalkBack and owner Samsung NOT RUN. Process-death recovery not claimed.
Report: ../docs/d106-my-points-stage-b-qualification.md; final logs/screenshots:
../docs/ui/evidence/my-points-stage-b-fix-20261020/.
Local B commit only; prior handoff diff/research/GPX reports/recovery evidence
preserved outside its scope. No push, Samsung operation or C/D implementation.
STOP after B. Stage1/H05 OPEN. Further C/D or owner qualification needs a new task.


## Latest fix — GPX provider filename (2026-10-19)

Base b29af07; targeted fix: SAF name arrived after capture() and previously
remained only in UI. execute now binds the provider name to the same request;
Room/READY persist it. No schema/parser/geometry/deletion/UI composition changes.
Old four Samsung imports have no reliable saved filename; no backfill performed.
JVM1062 PASS; DEV/test assemblies PASS; lint0 errors/36 warnings/6 hints;
API29 fontScale1.7 final69/69 plus2 process-restart checks PASS. Long Cyrillic
name/list/dialog/actual scroll checked. First failed test-harness run recorded.
Report: docs/d106-gpx-filename-report.md. Samsung NOT updated; physical SAF/UI
qualification and TalkBack pending separate OWNER scope. Stage1/H05 OPEN.
Prior dirty handoff and Samsung report bytes preserved outside this commit.
One local fix commit only; no push. Stop; no next-stage implementation.


## Latest task — safe saved GPX deletion (2026-10-19)

OWNER approved deletion PNG+MD and durable journal plan. Base97d4d67; Room15→16,
permanent exact-ID tombstone, RESTRICT-safe logical deletion, owned-original
inode proof and independent file/settings debt retries are implemented. Existing
Registry/session invalidates deleted overlays and rejects stale geometry/catalog.
Accessible name long press → Delete → confirmation; Cancel/Back preserve data.
Report: docs/d106-gpx-deletion-report.md. JVM1062 PASS; API29 95 unique targeted
checks (27 deletion/UI +64 regressions +4 native), final UI recheck1 and API37
backend13 PASS. Final lint/diff status is recorded in the report.
Prior handoff bytes/diff preserved outside this task commit. No push. Samsung
was NOT updated or used for deletion; new DEV update needs separate OWNER
permission, followed only by a specially created test-GPX qualification.
Stage1/H05 OPEN. Stop; no My points/backup/export or next task implementation.

## Latest task - GPX1.0, deletion draft and Exchange picker (2026-10-19)

A implemented:2412661. Existing pipeline supports exact1.0/1.1 profiles; original
Topokarty33339-byte SHA256dc390810...9e175d8 found read-only on Samsung. All271
coordinates/elevations/times and original bytes verified on API29 and37. No schema
or1.1 CM1/UUID/original changes. docs/d106-gpx10-report.md.
B STOP:eb0859e is documentation only. PNG+MD deletion addendum DRAFT and durable
delete-journal/tombstone migration plan await OWNER review. No delete production
UI/backend/schema and no destructive Samsung tests. Stage1/H05 OPEN.
Folder slice: existing Exchange/OpenExchangeDocument, per-variant GPX directory;
best-effort preparation, normal SAF fallback. JVM1062 PASS; API29 A53 unique and
folder/import target19 PASS; API37 GPX/Exchange10 PASS. Final lint0 errors,35
warnings,6 hints. API29 public-directory write limitation documented, no broad
permission added. Independent Luna unavailable; SELF_REVIEW, reduced independence.
After separate OWNER permission, installed only org.beesearch.app.dev1.3.0(8) on
Samsung SM-S938B/API36/font1.7. APK SHA256
66ba1e813a6278feff7e8c1c26b3feae009d2c319d6ce756e68b640ad59f8eb1.
All4078 private files exactly match before update/first launch; Stable/Beta unchanged.
Missing Exchange/GPX folder created by app; fresh picker automatically opened
BeeSearch/Dev/Exchange/GPX. Screenshot and results:
docs/d106-gpx-picker-folder-report.md. Cancel returned to Tracks, no file selected.
Old handoff diff preserved byte-for-byte outside task commits. No push. Stop;
no automatic deletion implementation, My points or next stage.


## Latest task - saved GPX display implementation and DEV smoke (2026-10-19)

Base574f099; OWNER-approved gpx-display-v1 implemented. READY Territory catalog,
per-Territory persistent multi-file selection (new imports OFF), canonical lines
and singleton points through existing Registry/session. No import/schema/backup
changes. Stage1A default-visible wording aligned with OWNER decision.
Report: docs/d106-gpx-display-implementation-report.md.
JVM1061/1061, API29 targeted103/103 at actual fontScale1.7, DEV/test assemblies
and final lint PASS (0 errors,35 warnings,6 hints).
After separate OWNER permission, Samsung SM-S938B/RFCY90MBYVZ updated only DEV
org.beesearch.app.dev1.3.0(8), APK SHA256
15d6476fe2744437b39d82f7a05cb9aea2795cc3e33cb97e25d2a7adc16b96a6.
All4075 private files exactly match pre-update SHA256 before first launch;
Stable/Beta untouched. Phone font1.7 list/two switches/restart persistence/
scroll/arrow/system Back/picker cancel smoke PASS; selections restored OFF.
Phone native line placement, Territory/style/fault matrix, TalkBack and capacity
not claimed; emulator native lifecycle/geometry tests cover their defined scope.
Prior uncommitted handoff bytes preserved; only this entry belongs to task commit.
No push. Stage1/H05 OPEN. Stop; no automatic continuation to My points.


## Latest task — GPX display mockup APPROVED (2026-10-19)

OWNER approved gpx-display-v1 PNG+MD; mandatory correction completed in both PNGs:
Лесная дорога.gpx, switch ON, no original-status warning/icon. No repeat review.
Valid canonical geometry keeps displaying without an original-status warning;
canonical failure instead produces a per-track display error. Original diagnostics
remain internal. Singleton segments as points and existing Registry are approved.
README, checklist and decisions synchronize this approval. No production/test code,
schema, import, APK install or push. Stage1/H05 OPEN. Next separate task: implement
saved GPX list and map display; do not start automatically or begin My points.
PNG visual/pixel preservation and MD/link/diff checks completed; corrected font17
PNG checked locally, not a new physical-device runtime qualification.
Only this new handoff entry belongs to the approval commit; all prior uncommitted
handoff changes are retained outside it. Earlier DRAFT notes below are historical.


## Current task — universal user-data panel implemented (2026-10-18)

OWNER approved the targeted UI revision. Main panel order is research rows ->
their Reset/Done -> divider -> ПОЛЬЗОВАТЕЛЬСКИЕ ДАННЫЕ -> Треки -> Мои точки.
No subtitles. Tracks opens the unchanged nested GPX page. My points is disabled
with pin icon, arrow and accessibility semantics; no page/entity/persistence.
GPX pipeline/import, research filters, map/layers and Auto Backup unchanged.
Approved v1 PNG+MD updated; only frame1 lower112px changed, all other8 frames
and research rows/buttons pixel-identical. Decision/product/workflow/UI docs
and existing checklist synchronized with explicit OWNER A/B/C roadmap.

Checks: DEV/androidTest assemblies and lint PASS; JVM12/12 PASS; API29 targeted
61/61 PASS (MapDataPanel38+GPX UI8+ViewModel10+Wiring5). Actual emulator system
fontScale1.7, full and constrained1080x1600: both rows readable, real scroll,
disabled touch no-op, Tracks/arrow/system Back verified. AVD settings restored.
Initial test compile missing-click-import failure retained, corrected run PASS.
No full parser/Room/recovery rerun. Physical Samsung new UI NOT INSTALLED:
separate OWNER permission required; prior4cdb640 device evidence remains prior.
Evidence: ../build/evidence/d106-user-data-20261018/report.md and screenshots.
Decomposition reviewed; kept cohesive because: only existing sheet composition
changed, with no independent workflow or infrastructure added.

Next: B — prepare GPX list/one-or-multiple-track visibility PNG+MD, obtain
approval before implementation; persist visibility and reuse layers/Registry.
Import/storage stay independent of map display. After B, C defines minimal
My points model: name/coordinates, create/view/edit/delete, show/hide,
persistence and Territory/layer integration, no bee-observation semantics or
extra attributes/media/classifiers without OWNER. Later unified extensible
local Backup/Restore for all permanent user data; no separate GPX/point backup,
Android Auto Backup untouched. Stage1/H05 OPEN. One local scoped commit; no push.
Prior uncommitted qualification/handoff text below is preserved independently.
Do not begin B implementation, C or Backup automatically.

## Current status — GPX import UI implemented from OWNER-approved mockup v1 (2026-10-17)

OWNER approved the reviewed `gpx-import-v1` PNG+MD (commit `a69eaf3`) and authorized this UI
Slice. `userImportAvailable` is now true. The main «Данные на карте» panel has the `GPX`
section and `Треки GPX` row after research data. The nested page has active `Импорт GPX`
(`Добавить GPX-файл из памяти телефона`) and visible disabled `Показать трек`
(`Выбрать сохранённый трек для карты`, `Скоро`). The system single-select OpenDocument
picker is launched only after the existing workflow captured Territory/Observer/request;
picker Back/cancel returns silently to the nested page. Progress, explicit Cancel/Back
durable READY check, duplicate confirmation/decline, success/warning and the approved errors
follow the MD; success does not enable map display or a track list. `Ready.hasWarnings` is
an additive read-model field; no Room/parser/Coordinator/admission/Original Store/backup or
map/Registry/visibility behavior changed. The UI split is `GpxTracksPage.kt` (nested
navigation) and `GpxImportTask.kt` (task/result/dialog); `GpxImportViewModel` is the UI state
machine and `GpxImportGateway` the test seam over the existing workflow.

Verification on the final tree: full JVM1061/1061 (126 classes, 0 failures/errors/skips);
all7 build/lint gates PASS; API29 focused58/58 PASS (35 MapDataPanel+8 new GPX panel+10 new
ViewModel+5 wiring). API37 new ViewModel10/10 PASS; API37 Compose UI NOT RUN because Espresso
3.5.1 calls removed `InputManager.getInstance` on the installed Android 17 API37 image
(test-harness/toolchain incompatibility reproduced before these tests). Physical Samsung S25
Ultra manual scenario NOT RUN. Frozen inputs manifest:
`build/evidence/d106-stage1-gpx-ui/attempt-1/frozen-inputs.json` — 611/611 MATCH, digest
`D12AB9C600C7CCFB6D249E058B2C15EF95FE66E3E5FEB0F6EEC3F99C5D22AA0C`; closed GPX112/112
unchanged (`65585099B560BE247E5E32B7F604E911D6D16648E61EEE8FB9DB1D8CFD359005`).
`freeze.json` also records the two baseline docs changes already present at the slice
baseline (activation-gate documentation), not part of this UI change. Evidence is ignored.
Stage1/H05 remain OPEN; one local implementation commit authorized after scope checks, no push.

Next minimal step: physical Samsung S25 Ultra manual scenario from the approved MD, then the
separately authorized track list/map visibility/Registry Slice. Do not implement those or
Backup/Restore/Auto Backup automatically. Do not declare Stage1/H05 closed.

## Historical status — GPX activation gate corrected; user import may proceed (2026-10-17)

OWNER decided that the unified local Complete Backup/Restore takes precedence over the
earlier D106 requirement to implement separate GPX protection/restore before user GPX
import. A separate GPX Complete Backup/Snapshot Slice is not required now. GPX
protection/restore and H05 move to the future unified local Backup/Restore; H05 remains
OPEN and is not claimed verified. This is documentation only: no code, Room, parser,
Coordinator, tests, Android Auto Backup or runtime flag changed.

User import is no longer gated on protection/restore. The remaining precondition is the
OWNER-approved production UI mockup (PNG+MD) and its implementation (Stage1A §10);
visibility/Registry/map and the unified Backup/Restore remain separate future work.
Stage1 remains OPEN. The internal workflow stays `userImportAvailable=false` until an
explicit UI Slice is authorized; no "experimental only" limitation is introduced.
Decision: [decisions.md](../docs/decisions.md#d106-gpx-activation-gate-corrected--owner-decision-2026-10-17).
Next permitted step: OWNER-approved GPX import UI mockup and the user entry slice; no
backup or protection implementation starts automatically.

## Historical status — captured GPX request/workflow QUALIFIED; OWNER review pending (2026-10-17)

Baseline `9f2b0605461dff110ea9933f8bfc9052fe92ba4b`; OWNER accepted that runtime
Slice as PASS within scope. Internal AppContainer.gpxImportWorkflow exposes
capture(displayName), execute(handle, open, confirmRepeatedOriginal=false), lookup(R).
Capture creates one explicit intent with immutable Territory/nullable Observer,
request UUID and technical metadata. Retain its handle for retries; execute never
creates a replacement request. Consumed intent cannot authorize another job; durable
READY lookup wins over lost delivery/current selection. After process loss, journal
recovery uses saved context/IDs, not provider URI or a reconstructed handle.

Runtime serializes whole live workflow against reconciliation. Shared transient
SettingsSelectionGuard orders normal selection writes with final complete; A-B-A
invalidates incomplete intent. No new persisted fields/counters. Cancellation/error
between Prepared and complete delegates NonCancellable abort to Coordinator, preserving
same cancellation and committed READY. Existing duplicate/admission rules stay there.
User import remains unavailable (read-only false), no picker/UI/map/Registry route.

Targeted JVM3/3; full1061/126 classes,0 failures/errors/skips; all7 build/lint PASS.
ATTEMPT2 sequential API29/API37 each264/264 PASS (239 prior+6 settings regressions+
19 new workflow/guard/crash methods), same APK pair/exact method set. Each plus5/5
actual force-stop/recovery probes through workflow lookup, no new requests/IDs/source.
Both AVDs stopped, no physical device. Freeze601/601 MATCH, digest
`C8DB9F7F551F67069E5BB358332503D42A16520A5BE562C93AFFE666CC60F2E9`;
closed GPX112/112 unchanged. Evidence: ignored `build/evidence/d106-workflow/attempt-2/`.
ATTEMPT1 API29 focused12/13 failed due a still-armed cancellation fixture; preserved.
ATTEMPT2 fixes its retry and adds both explicit post-Prepared failure regressions.

Room15/schema, parser, Original Store, Coordinator/admission and backup/media remain
unchanged. Protection/Restore/direct restore-writer integration, actual picker/provider
permission workflow and UI are outside scope. Normal selection writes may await final
completion/cleanup; a failed selection write can conservatively invalidate an intent.
Real ENOSPC NOT TESTED; power-loss UNKNOWN; physical NOT RUN; historical API37 startup
ANR exact cause UNKNOWN. Stage1/H05 OPEN. One local commit authorized after final
scope checks, no push. Stop for OWNER review; separately agree the next activation/
protection gate before adding a user entry. Do not start automatically.

## Historical status — runtime reconciliation / READY access QUALIFIED; OWNER review pending (2026-10-17)

Baseline `081a41cad7bcc36dc4734664c0df5d15932f287b`. OWNER accepted the preceding
READY Slice as PASS within scope. Application.onCreate now starts reconciliation
through AppContainer on application-owned SupervisorJob/IO. GpxRuntime defers
Store construction to IO, serializes startup/first access and calls the unchanged
Coordinator. Successful clean passes are cached; failures gate reads and remain
retryable. Committed READY remains readable despite retryable cleanup debt.
Internal API: container.gpxRuntime.readReady(territoryId, importId). Existing Room
model/validation are reused; PENDING/other Territory return no graph. Original
availability does not erase canonical geometry. READY does not imply map visibility.
No live-import route/raw Coordinator exposed. Future live import must honor the
startup gate and existing Coordinator writer serialization.

Targeted JVM3/3; full1058 tests/125 classes,0 failures/errors/skips; all7 build/lint
PASS. ATTEMPT2: sequential API29/API37 each239/239 PASS (228 existing+9 runtime+2
runtime crash methods), identical APK pair/exact method set, plus5/5 actual host
force-stop/recovery probes each through GpxRuntime. Application/container and main
first-access tests PASS. Both AVDs stopped; no physical device. Freeze593/593 MATCH,
digest `2CEE06C64C5B05C079BA38AD462271D51856A7A820145BBCE26F693F9F40459F`;
closed GPX112/112 unchanged. Evidence: ignored `build/evidence/d106-runtime/attempt-2/`.
ATTEMPT1 retains successful JVM/build and focused API29 proof; its interrupted full
Android result is not PASS. ATTEMPT2 reused unchanged inputs/APKs. Initial overlapping
Gradle runs caused a KSP cache conflict; subsequent sequential full gates passed.

Room15/schema, parser, Coordinator/admission, Original Store, media and backup rules
unchanged. No user import/UI/Registry/protection/Restore. Real ENOSPC NOT TESTED;
power-loss UNKNOWN; physical NOT RUN; historical API37 startup ANR exact cause
UNKNOWN, not reproduced here. Stage1/H05 OPEN. One local implementation commit
authorized after final scope checks, no push. Stop for OWNER review. Next separately
authorized scope may address captured-request/import workflow subject to existing
activation/protection gates. Do not start automatically.

## Historical status — internal READY Slice QUALIFIED; implementation OWNER review pending (2026-10-17)

Baseline `b14552762363f5adbff522a2b2888b2221571ebb`. Explicit OWNER READY rules and
recovery clarification are recorded in decisions.md. Internal complete() finishes
FILE_PUBLISHED to READY; prepare() still returns invisible PENDING preparation.
G11 READY imports+candidate<=1024 and G12 actual READY samples+candidate<=200000
are checked in the same Room transaction as publication/correlation/journal READY.
No other PENDING reservations. Repeats use current READY SHA/length in captured
Territory and require explicit confirmation; non-repeat recovery may complete
without new consent. Unproven repeat confirmation rejects and compensates safely.
Committed request lookup bypasses admission and prevents post-commit compensation.
READY cleanup removes owned staging/witness and journal, preserving final and IDs.

ATTEMPT3 final qualification: policy targeted JVM4/4, full1055/124 classes with
0 failures/errors/skips; all7 build/lint gates PASS. Sequential API29/API37 each
228/228 PASS (202 regression+24 READY+2 crash methods), same APK pair/exact method
set, each plus5/5 real host force-stop/recovery probes at BEFORE_READY,
READY_IMPORT_UPDATED, READY_JOB_UPDATED, READY_COMMITTED and READY_STAGING_REMOVED.
Both AVDs stopped; no physical device. Source/helper freeze587/587 MATCH, digest
`101775DDC403C452EDD8A9E6E10AE7AA036EAD4F59785AC92C9A31A3FB82BD12`;
closed GPX112/112 unchanged. Ignored evidence: `build/evidence/d106-ready/attempt-3/`.
ATTEMPT1 test-signature failure and ATTEMPT2 API29 pass remain historical; final
attempt adds self-suppression cancellation protection and its Android regression.

Room remains v15; schema/migrations, parser, Original Store, media, backup rules,
Stage1A-D and accepted Original Store contract unchanged. No UI/Registry, runtime
user-import or startup wiring, protection/Restore/Backup implementation. Real ENOSPC
NOT TESTED; power-loss UNKNOWN; physical-device NOT RUN; predecessor API37 startup
ANR exact cause UNKNOWN, not reproduced during settled current qualification.
Stage1/H05 OPEN. One local implementation commit is authorized after final scope
checks; no push. Stop for OWNER implementation review. Next separate scope may
connect startup reconciliation and READY access; user import retains its existing
activation/protection gates. Do not start automatically.

## Historical status — Coordinator / Journal / Recovery QUALIFIED through FILE_PUBLISHED (2026-10-16)

Baseline `bd28079fca38154cc74a280d480cbf38d6c38ce0`. OWNER selected FILE_PUBLISHED
as the terminal phase (decisions.md). Internal Coordinator composes the unchanged
processor and Managed Original Store with checked journal transitions, stable IDs
and atomic PENDING graph save. Prepared is not import success: no READY writer,
admission, completed-request correlation, user-import or startup wiring. The
future runtime owner must reconcile before exposing GPX data. Live retries honor
captured-request cancellation; startup recovery uses persistent ownership and live
Territory/Observer existence without choosing the current UI Territory.

Targeted JVM2/2; full1051/123 classes,0 failures/errors/skips; all7 build/lint gates
PASS. Same frozen APKs and exact202 unique-method set on sequential API29/API37:
each202/202 PASS (181 existing+21 new), plus7/7 real host process-stop/recovery
probes per API at COPYING, COPIED, PARSING, PARSED, STORE_FINAL_COPY,
FINAL_VERIFIED and FILE_PUBLISHED. Early incomplete phases compensate without
provider reopen; PARSED/verified publication recovers without reparse/new IDs;
partial final copy fails closed and compensates; FILE_PUBLISHED remains PENDING.
READY/foreign files are protected; failed cleanup retains ABORT journal/descriptor.
Staging/witness remain until future completion or authorized abort.

Frozen579/579 pre/post MATCH (577 source inputs+2 ignored helpers), digest
`E691434EE72D5962D9271FD3668FDEBFDC013FA26848EE5EDB30188EE003C760`;
closed GPX112/112 MATCH. Only GpxDao/GpxPersistenceStore changed intentionally in
the preceding inventory. Parser, Original Store sources/tests and Room15 schema/
migrations unchanged. Ignored evidence: `build/evidence/d106-coordinator/attempt-1/`
(commands, identities, APK hashes, per-method outcomes, separate crash results).

Real ENOSPC NOT TESTED; power-loss UNKNOWN; physical device NOT RUN. Predecessor
API37 startup ANR exact cause remains UNKNOWN; settled current qualification PASS.
No backup/media/UI changes; both AVDs stopped. One local implementation commit
authorized after qualification; no push. Stage1/H05 OPEN. Stop for OWNER review.
Next separately scoped work: READY transition, G11/G12 admission and completed
request correlation, retaining ABORT-versus-READY serialization and existing
protection/restore gates. Do not start automatically.


## Historical acceptance - Managed Original Store OWNER-ACCEPTED / PASS within scope (2026-10-16)

OWNER accepted the exclusive-publication Managed Original Store file Slice.
Qualified production/test bytes remain unchanged: JVM1049/1049, all7 build/lint
gates PASS; ATTEMPT6 API29 and API37 each181/181 PASS, plus separate FINAL_COPY and
DIRECTORY_SYNC process-death/recovery2/2 PASS on each endpoint. Same frozen APKs
and method set. Frozen572/572, diagnostic runner superset573/573 and closed GPX112/112 MATCH.
Evidence: ignored `build/evidence/d106-managed-original-store/attempt-6/` and
`diagnosis-1/`; historical failed attempts below remain preserved.

Accepted limitations: real ENOSPC NOT TESTED; power-loss durability UNKNOWN;
physical-device behavior NOT TESTED; exact old API37 startup ANR blocking call
UNKNOWN despite successful settled-start qualification. These are not verified guarantees.
API29 early emulator heap initialization issue was localized; no production fix
was required during diagnosis. OWNER authorized one local finalization commit,
including implementation/tests, corrected contract and acceptance status; no push.
Stage1/H05 OPEN. Coordinator, READY activation and user GPX import remain future work.
Next requires a separate scoped Coordinator/reconciliation task; no implementation
or Backup/Restore work starts automatically.

## Historical Android diagnosis - qualification completed before OWNER acceptance (2026-10-16)

Targeted diagnosis preserved all implementation/test bytes and the accepted baseline
`a31ce3abc24c7f16b79348e6590d0c4d44d93a34`. API29 ATTEMPT4/5 boot logs show
zygote64 `-Xmx16m` before qemu-props delivered `dalvik.vm.heapsize=512m`;
Android 10 AndroidRuntime uses 16m as the absent-property default. Controlled boot
recorded zygote512m and actual test Runtime.maxMemory=536870912. The eight previously
failing methods passed together8/8; no Store/fixture correction or heap increase.
AVD config.ini files remain byte-identical.

API37 ordinary cold launch, isolated capability and Store tests PASS. The old
startup ANR occurred with high CPU/IO pressure; its retained DropBox trace is empty
(EOFException), so the exact blocking call/root cause remains UNKNOWN. After boot
settling, the same frozen APKs and same181-method set passed on both endpoints in
ATTEMPT6: API29 181/181, API37 181/181, zero failures. Separate host-controlled
process-stop/recovery probes at FINAL_COPY and DIRECTORY_SYNC PASS on each endpoint.
This proves those process-death boundaries, not power-loss durability.

Frozen572/572 pre/post MATCH, digest
`1740C1111B3CB531015AF454DACE4F25C0771E9C8A29DD78922A51613318285F`;
qualification plus the new ignored diagnostic runner573/573 MATCH, digest
`DF41D336DF0F474534A1529254361E02177438AD2F501281B93F088B35B63AF9`.
Closed GPX112/112 unchanged. Prior JVM1049/1049 and7 build/lint PASS remain applicable
to unchanged inputs; not rerun during diagnosis. Evidence: ignored
`build/evidence/d106-managed-original-store/diagnosis-1/` and `attempt-6/`.
Historical failures remain preserved below and in ATTEMPTS1-5.

Only handoff/checklist status updated after frozen verification. No production/test,
Room/parser/media/backup changes; both emulators stopped; physical device untouched.
No staging/commit/push. Real ENOSPC NOT TESTED; power-loss UNKNOWN. Stage1/H05 OPEN.
Next: OWNER/independent review of the completed Android evidence and existing Store
implementation. Do not start Coordinator, READY activation or Backup/Restore.

## Historical continuation — exclusive Managed Original Store BLOCKED / NOT QUALIFIED (2026-10-16)

OWNER authorized the targeted hard-link replacement. Baseline remains
`a31ce3abc24c7f16b79348e6590d0c4d44d93a34`, branch `task/d106-stage0-overlay-registry`.
[Store contract](../docs/D106_Managed_Original_Store_implementation_contract_2026-10-16.md)
now specifies exclusive O-directory / O_CREAT|O_EXCL final creation, bound dev/ino
proof plus retained operation witness and verified marker. Four internal production
files, three test files and documentation remain unstaged; Room/parser/media/backup
rules are unchanged. No implementation commit or push.

Targeted JVM6/6; full1049 tests /122 classes,0 failures/errors/skips; all7 build/lint
gates PASS, including recompilation/lint after the crash-fixture correction.
ATTEMPT3 API29 body181/181 PASS, but the actual process-kill probe failed because its
fixture fit one copy block. That failed attempt is preserved. Fixture now exceeds
32KiB and asserts partial final length; production did not change for this fix.
ATTEMPT4 API29 and bounded unchanged retry ATTEMPT5 each173/181 PASS,8 OOM failures
(7 closed regression +1 new16MiB test), ART reports16MiB growth limit; origin UNKNOWN.
ATTEMPT5 API37 stops after6/181 passes: Process crashed; ActivityManager records
failed-to-complete-startup bg ANR. No mandatory Android PASS or process-death recovery
claim. Same DEV/test APK pair, serial-scoped commands; both emulators stopped.

Frozen572/572 pre/post MATCH, digest `1740C1111B3CB531015AF454DACE4F25C0771E9C8A29DD78922A51613318285F`;
closed GPX112/112 unchanged. Evidence: `build/evidence/d106-managed-original-store/attempt-3/`
through `attempt-5/` (ignored; historical attempts1/2 preserved).
Next minimum step: diagnose the API29 ART limit and API37 startup failure before
re-running the same mandatory frozen qualification. Do not weaken limits/tests or
change AVD settings without an explicit basis/authorization. Real ENOSPC NOT TESTED;
power-loss UNKNOWN; physical device untouched. Stage1/H05 OPEN. No Coordinator,
READY activation, Room migration or Backup/Restore implementation.

## Historical initial Managed Original Store STOP — hard-link publication (2026-10-16)

Baseline a31ce3abc24c7f16b79348e6590d0c4d44d93a34, task/d106-stage0-overlay-registry.
The supplied [full Store contract](../docs/D106_Managed_Original_Store_implementation_contract_2026-10-16.md)
is preserved unchanged. Three internal production files and focused JVM/device tests
are present, unstaged. No implementation commit: mandatory Android publication failed.

Targeted JVM6/6; full1049/122 classes,0 failures/errors/skips; all7 build/lint gates PASS.
ATTEMPT1 API29:155 existing regression methods PASS; the new crash methods failed
at Os.link and the new Store class initially had a JUnit non-void measurement method.
That test defect was corrected before the separate ATTEMPT2 focused diagnostic.
ATTEMPT2 API29/API37 each13/21 PASS,8 FAIL: Os.link returns EACCES; SELinux denies
{ link } for untrusted_app. Same APK pair/method set; both emulators stopped.
The contract's hard-link publication/inode proof cannot qualify on these endpoints.
No fallback, policy relaxation or alternative publication was introduced.

Frozen diagnostic inputs571/571 pre/post MATCH; closed GPX112/112 unchanged.
Evidence: build/evidence/d106-managed-original-store/attempt-1/ and attempt-2/ (ignored).
Real process-kill recovery, post-link cancellation, publication cost and real ENOSPC
remain unqualified; power-loss UNKNOWN. Physical device untouched.
Next requires OWNER acceptance of a minimal publication-contract correction preserving
no-replace and ownership. Do not resume implementation automatically. Stage1/H05 OPEN;
Coordinator, READY activation, Room changes and Backup/Restore remain outside this task.
No staging, commit or push.

## Navigation update — 2026-10-16

Start with the [project map](../README.md) and [documentation index](../docs/README.md).
The [workspace map](../docs/workspace-layout.md) records existing auxiliary
directories and the new common parent `C:\App\Bee_search_support`. Existing
folders have not moved. Changes are documentation-only; application code and GPX
contracts remain unchanged. OWNER authorized one local documentation commit; no push.
D106 status remains in the existing checklist.


## OWNER Backup/Restore planning decision — 2026-10-16

[Accepted decision](../docs/decisions.md#unified-local-complete-backuprestore--owner-decision-2026-10-16):
continue D106, then the necessary user entities/media/user raster maps; return to
one extensible local Complete Backup/Restore after the main stores stabilize.
Restore on a real device is mandatory before full use with irreplaceable data.
Existing Android Auto Backup is unchanged and outside the current work plan.
Stage1/H05 and existing protection/restore/activation gates remain OPEN.
No implementation is authorized by this docs-only task; stop after the local commit.

## Current continuation — Stage1D Implementation Slice1, OWNER-ACCEPTED / PASS

The [Stage1D contract](../docs/d106-stage1d-gpx-persistence-contract.md) remains
OWNER-ACCEPTED. Persistence Foundation Slice1 is implemented and qualified within
its declared foundation scope; implementation is OWNER-ACCEPTED / PASS.
Independent DeepSeek review: 0 BLOCKER/HIGH/MEDIUM, 6 non-blocking INFO; no INFO remediation.
Qualification baseline/HEAD: f753f9ef339f94fffe2f58d74689fd400d0c2f73,
task/d106-stage0-overlay-registry. OWNER authorized one local acceptance commit; no push.

Room15 adds exactly five GPX tables, explicit14→15 migration, shared structural
triggers and fail-closed definition verification, exact canonical mapping and
PENDING transactions/cleanup/count facts. Territory/Observer deletion guards include
imports/jobs. No READY writer or user import route; D1/D2 and closed GPX code unchanged.

Frozen ATTEMPT1:564 qualification inputs pre/post MATCH;
0DB264A569618C6B4D364568D972C4D710D8251FB83A81D5F88AA3413D7AFECF.
Closed S5 inputs112/112 unchanged. Targeted9/9; full1043/121 classes,0 failures/errors/skips;
all7 compile/build/lint gates PASS. Sequential API29/API37 each151/151 methods:
64 closed GPX +74 Room/repository regression +13 new persistence/migration.
Same frozen APK pair and method set; both emulators stopped; physical device untouched.
Evidence: build/evidence/d106-stage1d-slice1/attempt-1/ (ignored).
Development compile/test-fixture corrections precede freeze; their logs remain under
the sibling development/ directory. No failed frozen attempt was replaced.

Next: a separately authorized task is required for further implementation. Coordinator,
managed originals, recovery, admission, Backup/Snapshot/Restore and UI remain future
stages. Stage1 and full H05 remain OPEN. No automatic continuation.
Earlier Slice5 and other next-step wording below records historical closure states.

## Current state — D106 Stage1 Slice5 processor handoff CLOSED / OWNER-accepted (2026-10-15)

**D106 STAGE 1 SLICE 5 CLOSED / OWNER-ACCEPTED / QUALIFIED WITHIN DECLARED PROCESSOR-HANDOFF SCOPE.**
Current HEAD cafb12d4be0fa250898be633b6c309a21fd5e642,
branch task/d106-stage0-overlay-registry. Slice4 accepted local commit67a55452599a57d44d884d5c3dee57df7bfd5313;
accepted cancellation corrective e5cabea is part of the closed chain. No push.
Independent DeepSeek review ACCEPT (0 BLOCKER/HIGH/MEDIUM/LOW); OWNER accepted
and locally committed Slice5 as current HEAD. Four INFO remain non-blocking.
Older pre-commit states below and Stage1C §§27–31 are historical.

S5 adds only internal GpxParseResult/GpxProcessor and JVM/device conformance tests.
Existing open lambda and closed two-pass reader/profile chain are reused unchanged.
Accepted carries canonical draft, exact original SHA/length and frozen warnings;
Rejected is typed/bounded. Cancellation remains the SAME throwable with suppressed
close failure. No partial Accepted, persistent UUID, owner choice, storage or dedup.

ATTEMPT1: targeted16/16 (10 S5+6 corrective); full1034/120 classes with0 failures/errors/skips;
all7 build/lint gates PASS. Sequential API29/API37 each64/64 (52 closed+12 S5), same
frozen source/APKs/method set; both stopped, no physical device. Input→canonical→CM1
matches the independent literal/direct oracle; original identity remains separate.
New S5 two-pass probe:9417bytes,300samples,2 opens/passes;56.5357ms API29 /37.0661ms API37.
Unmeasured heap peak/allocation/actual events UNKNOWN, no new threshold.

Closed108/108 unchanged:365C0281EEA2228F72242B79DB5E75D09766B06A4C203C84BC9845ABF29EA18C.
S5 superset112/112 pre/post MATCH:65585099B560BE247E5E32B7F604E911D6D16648E61EEE8FB9DB1D8CFD359005.
Historical S5 scope-matrix.md unchanged (9896bytes,02D961A5...5C81AB3).
Evidence: `build/evidence/d106-slice5/attempt-1/`, execution matrix and coverage.tsv.
[Stage1C §31](../docs/d106-stage1c-gpx-implementation-contract.md#31-slice5-processor-handoff--executionstatus-2026-10-15)
contains frozen/APK hashes, commands/results and the future-owner integration contract.

Next: review this documentation-only synchronization; its commit requires separate
OWNER authorization. Next permitted step: limited architectural research of the GPX
import lifecycle, grounded in accepted contracts and covering ownership/storage and
Backup/Snapshot/Restore dependencies. This synchronization starts no implementation.
Actual coordinator,
Territory/Observer request capture, persistent IDs/Room/managed original/duplicates,
publication/protection/Backup/Snapshot, full G11–G21/H01–H07 integration (especially H05
restore WITHOUT reparsing), UI/MapLibre/Registry remain OPEN. Stage1 is NOT complete.


## Historical pre-commit state — closed Slice1 cancellation corrective requalified (2026-10-14)

**CORRECTED AND REQUALIFIED — READY FOR INDEPENDENT REVIEW.** HEAD remains
`67a55452599a57d44d884d5c3dee57df7bfd5313`, task/d106-stage0-overlay-registry.
Slice4 was OWNER-accepted and locally committed at that HEAD; no push.
The pre-commit S4 status below and Stage1C §§27–29 are historical.

S5 preflight found that source-close IOException could replace in-flight cancellation.
S5 stopped; a separate OWNER-authorized corrective cycle changed only GpxInput and
both AndroidGpxXmlReader cleanup owners. Same CancellationException now escapes;
bounded close failure is suppressed. Standalone close classification and all other
primary/close behavior remain unchanged. No S5 production implementation was started.

ATTEMPT1: corrective4/4 pre/post MATCH, digest
`2818045720D6394EED61F2E6C3571582BBEF8C4C24602E66F669E6C66B43E6F3`;
combined current GPX108/108 MATCH, digest
`365C0281EEA2228F72242B79DB5E75D09766B06A4C203C84BC9845ABF29EA18C`.
Old S4 digest88C41139...22080709 remains historical (2 intentional production changes,
104 other inputs unchanged). Targeted GPX JVM97/10 classes including6 corrective;
full JVM1024/119 classes,0 failures/errors/skips; all7 build/lint gates PASS.
Sequential API29 emulator-5554 and API37 emulator-5556 each52/52 PASS
(48 regression +4 corrective), same frozen source/APK pair and installed hashes;
both stopped, no physical device. Evidence and exact commands:
`build/evidence/d106-slice1-cancellation-corrective/`; [Stage1C §30](../docs/d106-stage1c-gpx-implementation-contract.md#30-closed-slice1-cancellation-corrective--executionstatus-2026-10-14).

Next: independent corrective review and OWNER acceptance. Diff UNSTAGED/UNCOMMITTED;
no commit/push. Slice5 NOT STARTED / BLOCKED UNTIL CORRECTIVE ACCEPTANCE and fresh
OWNER continuation. No coordinator/persistence/UUID/storage/protection/restore/UI work.
Stage1 as a whole is NOT complete.


## Historical pre-commit state — D106 Stage 1 Slice 4 qualification (2026-10-14)

Baseline/current HEAD fd8e4dbf004e0def176bbe2b53efae805bf492c1,
branch task/d106-stage0-overlay-registry. Slice3 CLOSED / OWNER-accepted / qualified
within profile-parser scope, locally committed as that HEAD; push NOT PERFORMED.
S3 history below and Stage1C §§27–28 remain the pre-commit chronology.

Fresh OWNER S4 authorization only; PAC tool unavailable, fallback recorded.
S4 QUALIFIED WITHIN DECLARED BOUNDARY/RESOURCE SCOPE, not OWNER acceptance.
Production unchanged;5 new JVM/Android test/generator files and3 status docs,
unstaged/uncommitted for review. Contract-derived scope/evidence:
`build/evidence/d106-slice4/scope-matrix.md` and `attempt-3/`.
Frozen106 GPX inputs pre/post MATCH, digest
88C41139C66A1BD15DFB4BA0A3507D66177C36CB800C54407DB50EEC22080709.
Targeted JVM11/11; full1018/118 classes,0 failures/errors/skips; build PASS.
Sequential API29 emulator-5554 and API37 emulator-5556 each48/48 PASS
(37 regressions +11 S4 methods),44 S4 generated executions each including30
G01–G10 below/exact/over cases. B05 event oracle/CM1, F03 test-owned file canary/F09 no-I/O observers,
100000-sample canonical memory, two-pass reads/cancel/late EOF/close covered.
Same frozen source/APK pair, both emulators stopped, AVD configs unchanged;
physical device never targeted. Exact heap peak UNKNOWN; no invented RAM/time cap.
Detailed results and hashes: [Stage1C §29](../docs/d106-stage1c-gpx-implementation-contract.md#29-slice-4-executionstatus--2026-10-14).

Next: independent review of complete S4 unstaged diff. No staging/commit/push.
Slice5 NOT AUTHORIZED / NOT STARTED. Coordinator/persistence/storage/UUIDs,
publication/protection/presentation, full H05 restore-without-reparse and future
storage/protection/aggregate capacity gates remain open; Stage1 is not complete.

## Historical pre-commit state — D106 Stage 1 Slice 3 qualified / independent review PASS (2026-10-14)

**QUALIFIED WITHIN DECLARED PROFILE-PARSER SCOPE — ATTEMPT-2 PASS,
INDEPENDENT READ-ONLY REVIEW PASS CONFIRMED.** HEAD remains
 db03b897e3cc0e995368551c510f7c8d9a93a8a3, branch task/d106-stage0-overlay-registry.
Slice1/2 remain CLOSED. Slice3 remains UNCOMMITTED/UNSTAGED; no commit/push occurred.

Chronology: ATTEMPT1 correctly stopped on API29 (35/37) because new B08 encoded
forbidden U+0001; API37 was not run. OWNER then authorized only B08 fixture correction
and fresh qualification. Two bytes changed long decimal ...001 to ...049 (U+0031,
latitude1), preserving63 digits/66-character reference and328-byte file. No Kotlin,
TSV or other fixture changed. The stopped ATTEMPT1 record below is historical;
its failure/next-action wording is superseded by this current state and Stage1C §28.

ATTEMPT2 frozen83-file source/test/corpus inventory matched83/83 pre/post; SHA256:
96F479A3A0C86EAEC35C226BD918DFD857482FF3E2BAB0F0E6731F0ED49A3488.
Fresh targeted JVM19/19; full JVM1007/116 classes,0 failures/errors/skips; Android
build PASS. Sequential API29 emulator-5554 and API37 emulator-5556 each passed37/37
methods and completed52/52 manifest rows (38 accepted,14 rejected), including all
post-B08 rows and B01/B08 CM1 equality. Same frozen source, DEV/test APK pair,
expectations and test set on both; both AVDs stopped, no physical-device use.

OWNER reports independent DeepSeek read-only artifact review: PASS CONFIRMED,
0 BLOCKER/HIGH/MEDIUM,1 LOW (stale current status, addressed by this documentation
sync), otherwise INFO. Reviewer reran no Gradle/tests/emulators/adb and edited no files.
Evidence: build/evidence/d106-slice3/attempt-2; [current record and APK hashes](../docs/d106-stage1c-gpx-implementation-contract.md#28-slice-3-attempt-2-pass--current-qualification-status-2026-10-14).

Slice4/5 NOT STARTED / NOT AUTHORIZED. Boundary/resource triplets, near-ceiling
canonical memory, later processor/coordinator/persistence/identity/publication/
protection/presentation and full H05 restore-without-reparse gates remain open.
Stage1 is not complete. Next: review this documentation-only diff and await a
separate OWNER acceptance/commit decision; no implementation/staging/commit/push
is authorized by this status synchronization.

## Historical ATTEMPT 1 — D106 Stage 1 Slice 3 stopped qualification (2026-10-14)

Starting/current HEAD db03b897e3cc0e995368551c510f7c8d9a93a8a3; branch
 task/d106-stage0-overlay-registry. Started CLEAN/index EMPTY. Slice1/2 remain CLOSED;
no accepted implementation was changed. Separate OWNER authorization covered Slice3
only; PAC unavailable, fresh authorization fallback. Five new production files,
two tests and76 corpus files remain UNCOMMITTED/UNSTAGED (83 new paths).

FAIL / NOT QUALIFIED: final frozen JVM19/19 and full1007/1007 passed; APK build passed.
API29 emulator-5554 ran37 methods:35 pass,2 fail, both new profile methods hitting
B08.xml. Its long numeric reference ends in001, denoting forbidden XML U+0001, not
text digit1 (U+0031). Slice1 correctly returned MALFORMED_XML:REFERENCE_SCALAR.
This is a new fixture defect, not an observed closed Slice1/2 production defect.
Per runtime-failure STOP, fixture/expectation was not changed; API37 was NOT RUN.
API29 stopped; final device list EMPTY; existing AVD configs unchanged, no physical use.

Final source-pre/source-post inventory83/83 identical; SHA256
B289A88DFC79C108DE454493C599FEEEE8E785A181FF01742E83804F9BDD9FDC.
Evidence: build/evidence/d106-slice3; details, intended52-family J/A mapping, commands,
APK hashes and honest chronology: [Stage1C §27](../docs/d106-stage1c-gpx-implementation-contract.md#27-slice-3-executionstatus--2026-10-14).
Next: OWNER/reviewer review of the fixture defect and separately authorized targeted
correction plus fresh frozen JVM/API29/API37 runs. No correction after STOP was made.
No Slice3 acceptance. Slice4/5 NOT AUTHORIZED. No staging/commit/push. Earlier Slice2
statements that Slice3 was unauthorized are historical and superseded by this task's
separate authorization, not permission for further implementation after this stop.

## Previous state — D106 Stage 1 Slice 2 post-commit closure (historical, 2026-10-14)

HEAD: ae291906b186e2c8fdda94e167af7f07e4dac620.
Parent: bf36ddd481ca6a04d572635699095417f991173e.
Subject: feat(gpx): implement D106 Stage 1 Slice 2.
Slice1: ACCEPTED/QUALIFIED, previously committed locally as the parent, CLOSED.
Slice2: independent implementation review ACCEPT (BLOCKER/HIGH/MEDIUM0/0/0), then
OWNER acceptance and authorization for exactly one local commit of the reviewed state.
Exactly9 reviewed paths committed; build/evidence excluded. Slice2 CLOSED within its
pure-JVM model/canonical values/exact decimal/canonical time/CM1 scope. Push NOT
PERFORMED; no push authorization. Slice3 NOT AUTHORIZED / NOT STARTED.
XML extraction, XML→canonical/provider parity, XML→CM1 and actual XML enforcement of
value bounds remain future gates, as do full H05 persistence/restore/protection/logical
IDs and near-ceiling canonical-memory/resource/capacity evidence. No further work authorized.
Historical implementation/test evidence (37/37 targeted,988/988 full JVM) is preserved:
[Stage1C §26](../docs/d106-stage1c-gpx-implementation-contract.md#26-slice-2-executionstatus--2026-10-14).
The status subsection there records review→OWNER acceptance→local commit chronology.
This housekeeping changes documentation only; no tests/devices/evidence changes,
staging/commit/push or Slice3 work. Earlier pending/uncommitted notes below are historical.

## Previous task — D106 Stage 1 Slice 2 implementation (historical pre-review/pre-commit state)

PASS within pure JVM scope; UNCOMMITTED / READY FOR INDEPENDENT REVIEW.
OWNER separately authorized model/canonical values (including time)/exact decimal/CM1.
Starting clean HEAD bf36ddd481ca6a04d572635699095417f991173e; Slice1 OWNER-accepted
and committed locally there. PAC unavailable; fresh explicit authorization fallback.
Three new production +three JVM test files; no Slice1/Stage1A/Stage1B changes.
Frozen list ownership, exact decimal/time, streamed CM1, independent205-byte A01 hex.
Targeted37/37 and full offline JVM988/988 in115 classes, zero failures/errors/skips;
compilation and diff check PASS. Index EMPTY, no staging/commit/push or device use.
Detailed22 pure row subsets +H05 material subset and exact commands/source hashes:
[Stage1C §26](../docs/d106-stage1c-gpx-implementation-contract.md#26-slice-2-executionstatus--2026-10-14).
Raw local evidence build/evidence/d106-slice2; initial new-test compile error preserved,
fixed within Slice2 then rerun. Root self-review only (delegation service unavailable),
not independent acceptance. XML extraction/bound integration remains S3/S4; H05 restore
and capacity/memory evidence remain future. Slice3+ NOT AUTHORIZED.
Next: independent review of the complete uncommitted Slice2 diff; no implementation
continuation or commit authorization inferred.

## Previous task — D106 Stage 1 Slice 1 evidence closure after independent review

PASS — EVIDENCE CLOSURE COMPLETE; READY FOR FINAL INDEPENDENT RE-REVIEW.
Narrow OWNER authorization; PAC unavailable, explicit fallback used. HEAD13a4e75,
branch task/d106-stage0-overlay-registry; existing Slice1 work remains uncommitted.
No production/test edits: frozen7 production +5 test files matched exactly post-run.
Inventory SHA2561FE815F5D536D45E04BC0AEF9F8E58712EB69F91BF3158420DB1DC3F6C87EF2C.
Fresh rebuild and FULL current25-test Android runs: API29 OK25/68.805s on emulator-5554;
API37 OK25/87.279s on emulator-5556. Actual device/AVD/properties/commands and installed
APK hashes preserved; same built DEV/test pair on both; sequential, both AVDs stopped.
One of25 methods is unguarded context-only characterization, not independent security proof.
Earlier23+3 revisions/GpxXmlSecurityMatrixTest changes remain explicitly historical in
[Stage1C §25](../docs/d106-stage1c-gpx-implementation-contract.md#25-slice-1-execution-evidencestatus--2026-10-13).
OWNER-confirmed API37 preparation preceded Slice1: official android-37.0 google_apis x86_64
revision6, BeeSearch_XML_API37, READY; no app/tests during preparation. No reprovisioning here.
Source observations substantiated by installed-source identities/excerpts, not runtime proof.
Evidence: build/evidence/d106-slice1/evidence-closure, including correspondence.json.
Accepted Stage1A/1B/Stage1C technical requirements unchanged; UNKNOWN peak/isolation metrics
remain UNKNOWN, no new threshold. LOW code observations intentionally not addressed.
Index EMPTY; no stage/commit/push, Samsung or Slice2. Next: final independent re-review;
no OWNER acceptance or continuation authorization inferred.

## Previous task — D106 Stage 1C OWNER acceptance (2026-10-13)

OWNER-ACCEPTED — DESIGN CONTRACT ONLY — [Stage 1C processing design](../docs/d106-stage1c-gpx-implementation-contract.md).
Final independent control review ACCEPT: BLOCKER/HIGH/MEDIUM0/LOW1; R1/R3/R4 CLOSED,
M1–M3 regression NONE. Non-blocking review-heading LOW intentionally retained.
Reviewed design includes
GUARD_SDK_AGREEMENT, assertion-level JVM/Android proof split and reproducible resource
evidence with objective failures/UNKNOWN; compact R5 audit map uses existing variants.
M1–M3 mechanisms, Stage 1A and accepted substantive Stage 1B semantics are preserved.
OWNER accepted on2026-10-13; implementation NOT started and NOT authorized.
Conditional platform XmlPullParser + strict input/lexical guard recommendation; S01–S18
runtime capabilities are NOT VERIFIED. API37 source inspected; API29/37 endpoint probes pending.
92 fixture families mapped; G11–G20 and full coordinator/persistence/protection/UI
assertions remain future owners. Five bounded implementation slices; pure S2 JVM proof
does not bypass required S1 qualification for integrated XML processing.
Documentation-only acceptance closure; no code/test/fixture/generator/dependency/build/device work.
Next technical step: D106 Stage 1 Slice 1 — XML/security capability proof.
Slice1 has NOT started and is NOT authorized by this task; separate explicit OWNER
authorization is required before it. Runtime qualification remains pending; no push.

## Previous task — D106 post-Stage-1 clean baseline (2026-10-13)

Stage 1A and Stage 1B ACCEPTED WITH AMENDMENTS; documentation-only design closure.
[Stage 1A](../docs/d106-stage1-gpx-spec.md) remains product truth;
[Stage 1B](../docs/d106-stage1b-gpx-parser-identity-contract.md) is the derived technical contract.
Independent review ACCEPT WITH NON-BLOCKING AMENDMENTS; BLOCKER0/HIGH0,
MEDIUM1 documentation/provenance only. LOW-1…LOW-6 incorporated,92 fixture families;
no runtime/implementation PASS inferred. Identity/security/provisional budgets preserved.

The unknown-provenance decisions.md NO-GO hunk was intentionally EXCLUDED FROM
acceptance commit e36b115. On2026-10-13 OWNER explicitly recognized that old
SAMSUNG VERIFICATION: NO-GO text as obsolete after committed Stage0 verification
e2d74cc (docs: record D106 Stage 0 Samsung verification), and authorized its removal.
Exact preflight proved it was the only worktree change; file-level restore removed
only that uncommitted hunk. decisions.md now matches accepted HEAD.
The preservation/exclusion notes in acceptance artifacts describe their historical
e36b115 snapshot; this cleanup supersedes the prior operational dirty-worktree note.

Canonical Stage0 remains STAGE0_VERIFICATION_PASS within declared emulator + Samsung
scope; no verification reopening, emulator-crash investigation or broader claims.
Stage1A/1B semantics and canonical files remain unchanged. The single pre-existing
checklist ../decisions.md link is corrected to decisions.md; target verified.
Cleanup uses one small local documentation commit; no push.

No production/tests/fixtures/dependencies/persistence/build/device implementation.
Next possible action only: separately OWNER-authorized Stage1C design task — parser/
canonical-model/security-fixture implementation contract. Stage1C and implementation
are NOT STARTED by this cleanup.

## Previous task — D106 Stage 1A accepted specification recorded (2026-10-12)

OWNER accepted Stage 1A with amendments. Single normative specification:
[docs/d106-stage1-gpx-spec.md](../docs/d106-stage1-gpx-spec.md).
GPX implementation NOT STARTED. Four budget domains remain provisional; hidden GPX
imports prefer unregister and do not require full native residency.
Stage 0 accepted STAGE0_VERIFICATION_PASS within declared emulator + Samsung scope.
Known pre-Stage0 emulator blocker remains separate, root cause UNKNOWN; do not reopen
it or broaden visual/performance/GPS/heading/ergonomic claims.
Documentation-only task: no code/tests/migration/dependency/build/device changes.
Incoming unrelated decisions.md hunk retained outside this task.
Next action only: prepare Stage 1B — parser/profile/security fixtures + canonical
identity model. Do not implement without a separate task.

## Previous task — Samsung closure of emulator-blocked assertions (2026-10-12)

SAMSUNG_STAGE0_BLOCKED_ASSERTIONS_PASS / STAGE0_VERIFICATION_PASS within declared combinedscope.
Samsung SM-S938B Android16/API36, explicitdevice serial in local evidence. ONLINE1/1PASS then
AreaEditorrecreation1/1PASS(all4iterations/handledRequests1), no failures/skips/native/processcrash.
Only two authorizedphysicalmethods ran. DEV/test install-r preserved data directories/firstInstallTime;
DEVversion7->existingStage0version8 expected, no sourceversion/signing/configchange. Stable/Beta metadata
unchanged, no clear/uninstall/settings/userfiles/mapoperations. No code/testfix needed.
Verification sourceab24fff, production1e88fe8 byteequivalent; same app/testSHA13296...FC286/A850...A914.
assembleDebug/AndroidTestPASS17s. Earlier JVM927/compile/lint gates remainvalid; no broadphysicalsuite.
EmulatorstatusretainedPASS_WITH_KNOWN_PREEXISTING_NATIVE_BLOCKER; rootcauseUNKNOWN, notfixed.
ONLINEscreenshotgreytransient(notvisualreadinessPASS); Area screenshotrenderedmap/noeditorpanel.
No performance/generalMapLibre stability claim. Visual/performance/GPS/heading remainseparatetopics.
Evidence docs/map-overlay-lifecycle-evidence.md and build/evidence/samsung-blocked-assertions.
Only lifecycle evidence/checklist/handoff updated; incomingdecisionsdiff untouched; no push.
Nextactiononly: owner review/accept combined scopedStage0 evidence. Do not automaticallystartStage1.

## Previous emulator closure


EMULATOR_VERIFICATION_PASS_WITH_KNOWN_PREEXISTING_NATIVE_BLOCKER / SAMSUNG_VERIFICATION_GO.
Owner replaced ONLINEFULLPASS prerequisite. ONLINE and known AreaEditor recreation method frozen
BLOCKED_BY_PREEXISTING_NATIVE_FAILURE, not rerun/notPASS. Unfinished assertions explicitly listed
in docs/map-overlay-lifecycle-evidence.md; rootcause stillUNKNOWN. Physical Samsung NOT PERFORMED.
13 independentgates/31testsPASS: three realproductionprofiles, MapDataWiring5/Query3/Center2/Coverage12,
enhancedstartup-recreation1, five individualAreaEditor methods. No failure/skip/abort/newnativecrash.
FIELD gens1->2 metadata coherent; firsthostreleased; recreated AREA_VIEWnewhost/gen1, unique resources.
JVM927/927 and compile/assemble/lint0errors44warnings6hints remaingreen; no source/test edits thisrun.
Production1e88fe8 unchanged, appSHA13296...FC286;testSHAA850...A914. Only task-local evidence/docs
updated in this run; earlier authorized androidTest and documentation diff preserved. No push.
Main/release/protected file and Room14/BackupV8/SnapshotV3/DataStore/Area/version/signing preserved.
Evidence build/evidence/independent-remaining and final lifecycle-evidence section. No Stage1 work.
Next action only: owner-authorized Samsung verification of unverified native and physical obligations.
Do not install/run on Samsung automatically. This is not FULLY VERIFIED.

## Previous parity-contract task


SDK_OWNED proven from android-v13.5.1 AnnotationManager source: automatic source
org.maplibre.annotations and point layer org.maplibre.annotations.points. Exact closed-set SDK
separation and strict application parity in androidTest helper;7contracttestsPASS. No genericfilter.
Current-task edits only profile-test two assertion calls and new MapOverlayResourceParityTest.kt;
previous closure test/docs diff retained. Production1e88fe8/config/dependencies unchanged.
BuildgatesPASS;JVM927/927;lint0errors44warnings6hints. AppSHA13296...FC286 unchanged;
testSHAA850A4ED5804680361194D0DFA152412488FA49A31C4E3541AABC29C080BA914 installed emulatoronly.
ONLINE rerun once BLOCKED_BY_PREEXISTING_NATIVE_FAILURE (same RenderThread/MapRenderer+151/
PC/BuildID), noJUnitassertion. ONLINE individualassertions not proven completed. No retry.
OwnerSection7 ONLINEFULLPASS prerequisite not met: otherprofiles/remgatesNOT_RUN_WITH_REASON.
EMULATORCLOSUREINCOMPLETE / SAMSUNG_VERIFICATION_NO_GO. No new commit or push;Samsunguntouched.
Evidence docs/map-overlay-lifecycle-evidence.md and build/evidence/parity-contract.
Nextowneraction: decide explicitly if isolated remaininggates may proceed on knownblockedONLINE,
replacing current FULLPASS prerequisite. No productionfix or architecturechange.

## Previous remaining-gates attempt


EMULATOR_VERIFICATION_FAIL / SAMSUNG_VERIFICATION_NO_GO. No new commit/push.
Stage0 production1e88fe8 unchanged, current test-only APK installed on emulator-5554;
appSHA13296FFD2C04AD0A3F40312F15422D90B072771E35A678E95A35F96279CFC286,
testSHA00A79C25F01FC8ADB992B512EBF2C8E874CCB8E4D1BDE600ECB72814FBCE08D3.
Required eight build gates PASS; JVM927/927; lint0errors44warnings6hints.
ONLINE profile isolated gate JUnit1failure: expected native sources omit org.maplibre.annotations,
actual includes it plus all expected profile/owned IDs. Fullyloaded/metadata assertions completed;
layer/JSON parity/fullrestore/reload/recreation later assertions NOT reached. Not provenproductiondefect.
Owner policy requires stop: remaining gates NOT_RUN_WITH_REASON, no fixes/retries.
Known AreaEditor recreation method BLOCKED_BY_PREEXISTING_NATIVE_FAILURE, excluded/not rerun.
No native crash in this attempt. Samsung untouched. Source/test edits from earlier task retained.
Evidence docs/map-overlay-lifecycle-evidence.md; build/evidence/remaining-gates.
One next action: verify extra source provenance and expected native-source-set contract before
separately authorized test-side correction/rerun. Do not change production or bypass the assertion.

## Previous bounded diagnosis


PREEXISTING_OR_ENVIRONMENTAL_SUPPORTED: exact1e88fe8 and immediate parent a8939f5 snapshots,
one isolated failing-method execution each, same emulator/API29/Gradle9.5.0/Debug/process/command,
byte-identical libmaplibre.so. Both PROCESS_CRASH SIGSEGV/null/RenderThread/MapRenderer::render+151,
same PC and BuildId. No Kotlin assertion stack. Additional runs/secondary variation not needed.
No production/test changes or new commit in diagnosis; prior test/docs diff retained. Evidence/docs
updated intentionally. Environment-only versus preexisting lifecycle cause remains UNKNOWN; no
Stage0-specific regression is supported. Native closure tests remain incomplete, not PASS.
SAMSUNG_REMAINS_NO_GO pending prior profile/regression obligations and owner gate decision.
Emulator DEV/test currently baseline pair; explicitly reinstall intended Stage0 APKs for later checks.
Next owner action only: decide accounting for proven pre-Stage0 crash in remaining closure gate.
Evidence: docs/map-overlay-lifecycle-evidence.md; build/evidence/diagnosis. Samsung untouched.

## Previous closure attempt


BLOCKED — ACTION REQUIRED / SAMSUNG VERIFICATION: NO-GO. No new commit, no push.
Starting/current HEAD1e88fe8765f1ab7a32ae6aeff129635d2d936185, task/d106-stage0-overlay-registry.
Test/docs-only diff: controller reflection constructor fix, enhanced BeeMap startup fixture,
new opt-in four production-profile native tests, evidence/checklist/handoff. Production unchanged.
JVM927/927 and all eight compile/assemble/lint gates PASS; lint0errors44warnings6hints.
MapViewLifecycleControllerTest4/4 PASS on emulator-5554/API29.
AreaEditorEntryUiTest6 planned,2 completed,1passed/1XMLfailure; native SIGSEGV at
libmaplibre.so MapRenderer::render during aFinishedEditorSessionNeverReopensOnALaterVisit.
Four tests uncompleted; remaining classes/profile/startup native gates NOT RUN. Crash cause UNKNOWN;
not established as production defect or environment-only abort. No retries or production fixes.
Reports/logcat saved per class under build/evidence/closure; see docs/map-overlay-lifecycle-evidence.md.
Independent test-diff review found no blocker/high finding; execution closure remains blocked.
Next owner action: decide scoped crash diagnosis/baseline comparison. Do not commit, use Samsung,
start Stage1 or bypass the native gate. Baseline897 XML not archived; final927 XML confirmed.

## Previous implementation task (prior automated evidence)


IMPLEMENTED / AUTOMATED GATES PASS / PHYSICAL SAMSUNG VERIFICATION PENDING.
Independent clone: C:\App\Bee_search_stage0_overlay_registry.
Task branch: task/d106-stage0-overlay-registry; canonical baseline a8939f504219d5133f54b1a9bfd958f7587d8083.
Owner authorized one local implementation commit; no push/merge/rebase or automatic Stage 1 work.
PAC tool unavailable; this task used the explicit one-time owner authorization and scoped escalations.

MapOverlayRegistry and MapLibreOverlaySession implement runtime Sources/Layers ownership, opaque
scope/revision, marker-correlated style callbacks/listeners, visibility, retained journal and safe
retired-scope cleanup. BeeMap keeps both initial style requests through the same session. Existing
research/GPS/Area Compose paths and persistence are unchanged. Runtime factories and tests add no GIS entity.

Baseline JVM897/897; final JVM927/927 (Registry/correlation30/30), Debug/Beta/Release compilation,
assembleDebug, compileDebugAndroidTestKotlin, assembleDebugAndroidTest and lintDebug PASS.
Lint0errors44warnings6hints. emulator-5554/API29 opt-in native3/3 PASS, no skipped/aborted;
FIELD/AREA_VIEW recreation and original Style metadata readback covered. Independent Luna review:
initial blockers corrected, production re-review has no blocking issue.
Evidence: docs/map-overlay-lifecycle-evidence.md, build/evidence/{baseline,final-gates,native-gate}.log.
No Samsung operation; protected main verifier SHA remains 7388F2F233AB86694C69F15CB0BF8076C39A65932D2248D44046CFE2E46A1F31.
Release checkout/Beta archives not modified. Room14/CompleteBackupV8/SnapshotV3 unchanged.

Next action belongs to OWNER after implementation review: authorize remaining physical Samsung
style/recreation/transition/budget and field regressions, and separately close delayed SDK-request debt.
Do not start GPX or automatically continue another stage. This entry is not a FULLY VERIFIED claim.


## Previous task — I7 closure: Temporal roadmap I1–I7 CLOSED (2026-10-11)

I7 CLOSED / VERIFIED / OWNER ACCEPTED. TEMPORAL ROADMAP I1–I7 CLOSED. No temporal
defect was found. This entry records the accepted I7 outcome and its remaining
verification debt. It changes no production code, no tests and no device state, and
re-verifies nothing on hardware.

I7 was the device/deployment acceptance stage for the temporal work. Its closure is
OWNER-accepted; the temporal implementation (I1–I6) is already committed and is not
modified by this entry.

Remaining verification debt (not temporal product defects):

1. `MapDataPanelTest.numericRangesApplyOpenBoundsAndLocalResetKeepsPeriod` — test
   infrastructure verification debt, explicitly not a temporal product defect.
   Investigate Activity / `ComposeTestRule` / `setContent` / interaction ordering. Do
   not record the cause as "slow device" and do not increase timeouts before the cause
   is established.
2. I7 had no pre-run DB fingerprint on the Samsung device, so byte-for-byte immutability
   of the database is NOT proven, although application, DB, DataStore and user state
   were confirmed intact indirectly. At the next pre-authorized instrumented run on the
   owner device, apply a safe pre/post fingerprint verification.
3. The physical timezone switch was deliberately not repeated in I7. The timezone
   invariant is confirmed by implementation and tests; this is a residual physical
   verification note, not a blocker.
4. A connected Gradle task can affect a device that appears during the run. Future
   device-changing runs require explicit target protection, now required by
   `AGENTS.md` §17 "Device target isolation".

Process rules recorded with this closure: `AGENTS.md` §17 requires device target
isolation (`ANDROID_SERIAL`, re-verified after toolchain changes, STOP on an unexpected
device), and §20 requires PAC DECLARATION FIRST — declare before inspection, because an
inspection classified as `read`/`partial` closes the declaration window exactly like a
mutation.

## Previous task — Location OFF→ON recovery (2026-10-10)

D105 OWNER PASS, committed/pushed separately at 85e384d2f845d72b83f8fb8b48e486d92f13d46e
(`feat: move area management to settings`); main==origin/main. Known native MapLibre emulator M1
is accepted D105 verification debt, not fixed by this GPS change. GPS recovery OWNER PASS;
OWNER authorizes a separate GPS commit/push. Protected verifier TOML unchanged; no cleanup, no I7.

Root cause observed in build/gps-startup-diagnostic/: disabled providers threw before listeners
were registered, terminating callbackFlow; foreground OFF→ON had no restart trigger. Provider now
keeps a foreground recovery collection, registers a context MODE_CHANGED/PROVIDERS_CHANGED receiver
before querying, and reconciles GPS/NETWORK requests idempotently. Existing LocationUiState values
flow to the ViewModel; OFF clears Available, ON waits for fix. Main-looper serialization, request
generation checks and cancellation cleanup prevent duplicates/queued old callbacks/receiver leaks.
LocationManager, 2s/1m, permissions and map/STARTED lifecycle unchanged; no Fused/polling/last-known
fallback/manifest receiver, no D105/MapLibre/schema/map-package changes.

Focused core tests10/10 PASS, full JVM897/897 PASS. Required assembleDebug, Android-test Kotlin,
Beta/Release compile and lintDebug PASS (build/gps-recovery-quality-final.log); lint0errors42warnings6hints.
Samsung DEV in-place install after ID/signature checks PASS; capture began before manual switching.
Location ON19:26:22.9 → Bee Search GPS/NETWORK requests19:26:22.936/.940 → GNSS start19:26:23.000.
Second ON19:36:27.262 → requests19:36:27.277/.279 → GNSS start19:36:27.291. No external map app.
OFF showed Unavailable and removed requests. Objects and settled Home/background snapshots have
zero requests/no Location receiver; map return has one GPS plus one NETWORK request. PID9025 unchanged,
no crash; DataStore/manifest/PMTiles hashes identical. An immediate Home snapshot preceded onStop;
use BACKGROUND-stable evidence for the completed lifecycle transition, not that transient snapshot.
Phone indoors; Android had no fix, so actual first-callback/Available timing remains UNKNOWN.
Autonomous OFF→ON request recovery VERIFIED; evidence build/gps-recovery-evidence/.
Final GPS-only independent review: BLOCKER 0 / HIGH 0 / MEDIUM 0. OWNER PASS recorded.
Physical-device T4/T5/T6 remain UNKNOWN because no indoor satellite fix was obtained; outdoor TTFF
was not measured. This does not block the accepted recovery fix. Await a new task after commit/push.

## Current task — D105 Area management in Settings (2026-10-10)

I6 CLOSED / COMMITTED / PUSHED at 5376e53478774734ea778157fb58c2e77143c937;
OWNER toolbar visual PASS. D105 OWNER PASS; OWNER authorizes its separate commit/push before GPS
recovery work. No cleanup, no I7. Protected verifier TOML remains intentionally dirty and untouched.

D105 accepted, linked from the bounded accepted subitem of I006. Area entry moves Objects→Settings
after Territory management. Single per-Territory DataStore Area and 1..N fragments unchanged;
no Room/schema/codec/wire changes. Duplicate Offline Maps route removed, main-map availability
action points to Area. Ready map → Replace via existing shared SAF session; initial load retains
discovery. Back → Settings (checklist origin → checklist). Existing fragments edit through
View on map→Edit sections. I6 territory-scoped queries, FilterSet, toolbar and temporal semantics retained.

Required quality tasks PASS in build/area-quality-final.log, JVM887/887, lint0errors42warnings6hints.
Focused JVM Area/display172 + help19 PASS. Final test APK assemble/compile PASS; final lint PASS
in build/area-lint-final.log. Android: core69/69 PASS (build/area-android-core-final.log), Settings5/5
and other map-loading11/11 PASS in the affected17 run; its initial-load fixture was corrected from
Ready to Missing and that test PASS in core69. Five other editor scenarios PASS independently
(build/area-editor-five-final.log). Total90 distinct Android tests PASS; the remaining existing
aFinishedEditorSessionNeverReopensOnALaterVisit crashes with SIGSEGV in libmaplibre.so RenderThread
on emulator-5554, both combined and isolated. Do not count aborted runs as passes or change the
unchanged map engine for this navigation task. Final independent review follows these last changes.
Verification closure follow-up: Samsung RFCY90MBYVZ / SM-S938B available, fontScale1.7. DEV in-place
install passed after package/signature checks. Objects excludes Area; Settings→Area and Back→Settings
passed; existing DEV Area shows 1 fragment / 0.4 km² / Replace map. Shared picker opened and cancelled,
no import. Existing fragment editor opened and cancelled without edits; four later Area-map visits
showed no editor replay or crash. DataStore/manifest/PMTiles SHA-256 inventories unchanged.
Evidence: build/d105-closure/. Five isolated emulator repetitions: 2 PASS, 3 native SIGSEGV crashes
in libmaplibre MapRenderer::render / Vulkan RenderThread on Android10 x86_64. Test and BeeMap lifecycle
are unchanged from baseline; the test directly drives BeeMap without D105 navigation. Classification:
intermittent native verification issue, no demonstrated D105 regression; exact native cause unknown.
Independent closure review B0/H0/M1: strict M0 not reached. No production/test changes in follow-up.
OWNER accepts D105; the independent native emulator M1 remains known verification debt, explicitly
not a D105 blocker. GPS OFF→ON recovery is separate work and must not enter the D105 commit.
Earlier sections below are historical pre-commit continuation context.

## Current I6 — D104 main-map toolbar owner prototype (2026-10-10)

Current dirty I6 retained; HEAD 20a7c55835f2d129908da899f743e9583414f42a. NO git add/commit/push;
I7 not started; protected verifier TOML untouched. D103 Room 14 and all preceding map/filter/marker
corrections remain intact. OWNER now identifies Hollow 10 / LogHive 1 as test records: leave them
unchanged; their historical fixation provenance is not a toolbar blocker or evidence for mass backfill.

Toolbar is one cohesive MapBottomToolbar.kt component: unchanged 56 dp, four equal icon-only zones
Analysis (disabled reserved), Visualization (existing Map Data panel), Objects, Settings. Background
#555555, active #966B4F tied to panelOpen, three inset dividers; icons visual height 44–46 dp.
#B7E27A is the OWNER-refined small circular active/restriction dot color; its geometry is unchanged.
MainMapScreen only integrates real panelOpen/callbacks. I004 bounded subitem is done, D104 accepted, map-display spec,
product requirements and workflows aligned. No new route, raster icon, dependency or temporal changes.

Latest quality after lime color refinement: build/toolbar-lime-quality.log PASS all required
JVM/build/compile/lint tasks; JVM 887/887, focused JVM 39/39, lint 0 errors / 28 warnings / 6 hints.
Samsung toolbar suite rerun 7/7 (build/toolbar-lime-samsung-tests.log), actual main-map toolbar
168 px, quarters 270 px at fontScale 1.7.
Visualization/Objects/Settings smoke succeeded; settings PB before/after identical. Device evidence:
build/toolbar-evidence/main-map-lime.png, panel.png, settings-real.png, toolbar-active.png.
Final emulator toolbar/map/session regression: 32/32 PASS (build/toolbar-android-final2.log).
The two legacy entry/indicator tests also PASS (build/toolbar-android-legacy-final.log): emulator total
34/34 across the two runs. A parallel-user Samsung legacy attempt reported no Compose hierarchy and
is not accepted as evidence; the independent Samsung toolbar suite remains 7/7 PASS. Independent
post-documentation review is the final acceptance gate; use the final report for its verdict.
Comparison: build/toolbar-evidence/prototype-comparison.html.
Decomposition performed: toolbar, vectors and tokens extracted into cohesive MapBottomToolbar.kt;
MainMapScreen retains the map scaffold and operational controls (418 lines).
OWNER final visual acceptance is required before any commit. Older sections below are historical.


## Current I6 — D103 physical temporal owner correction (2026-10-10)

Base HEAD 20a7c55835f2d129908da899f743e9583414f42a; current dirty I6 preserved. NO git add/commit/push; I7 not started; protected verifier TOML unchanged.

D103 accepted and routed via ideas I017. Authoritative semantics: temporal-data-model-design §25 / data-model physical temporal facts. Room14 migration13→14 adds nullable fixation_at and updated_at without legacy promotion. New Hollow/LogHive capture one millisecond Instant for fixationAt/createdAt/updatedAt plus captured fixationDate. Own actual edits/direct media association update only updatedAt; no-op/read/query operations leave timestamps unchanged. Inspection capability absent, separate parent/child boundary documented. Fixation UI shared by map preview/full card; known timestamp, date-only unknown-time, or honest unknown. No createdAt fallback.

Durable transfer: Complete Backup8, Snapshot3, physical single/collection Export3; prior readers retain NULL new fields; legacy writers reject silent loss. ObservationPoint Export2 unchanged. PC Snapshot verifier accepts1/2/3. Preserve all previous I6 counts, unified map, records/navigation/camera context, FilterSet and whole-editor Done composition.

Samsung installed DEV SHA781CC81D77CC47FF04697586CDF27E522990B9394B664B63881F5F7229320AA8, update2026-10-10 10:25:05; fontScale1.7. Narrow readonly audit: Hollow10 and LogHive1 fixation_date/fixation_at/updated_at NULL, old created_at preserved. Both full cards show honest unknown; LogHive marker preview → correct record → Back checked. Owner filters/visibility restored byte-identically. No owner records created/edited. Evidence build/i6-physical-evidence. Unknown legacy is intentional pending separate owner provenance/migration decision.

Current verification: JVM887/887, PC101/101; Android data33/33 + cards17/17 = 50/50; narrow Samsung readonly audit1/1. Final independent review of this current diff is the final owner-report gate; evidence is in build/i6-physical-android-* and the final response. All required builds/variant compiles/lint pass (0errors28warnings6hints), diffcheck passes. Emulator system crash was retried separately; do not use aborted runs as passes.

Next: await OWNER Samsung visual acceptance; no blind legacy backfill. Previous sections below are historical.

## I6 owner visual corrections #2 (2026-10-10)

Architecture owner-functionally accepted; retained. Preview now uses real Bee/ALL cycle query
aggregates with Russian forms/zero-bee wording. Every type editor has one separate whole-editor
footer Done, local reset only in sections; Samsungfont1.7/IME checked.
STOP on physical canonical-date data correction: H10UUID14bf5e39-d3ca-4245-a1cb-8287174c3490 and
L1UUIDd170bb70-8e91-4b2a-85b9-dd6803c8f948 have fixation_dateNULL; full records show created_at
08.10.2026 10:59 / 01.10.2026 13:25. No fallback/backfill/schema/migration change. Owner must decide
legitimate date provenance/legacy policy before this item can be fixed. Report/evidence:
docs/temporal-i6-owner-visual-corrections-2.md, build/i6-visual2-evidence.
FocusedJVM17/full878/Android85 pass; additional narrow on-device readonlyaudit1pass.
Final build/lint log build/i6-visual2-quality-final.log; final independent review follows current
changes, use final response for B/H/M. Current owner settings restored exactly (OPbees4–5,
Hollowheight4–6cm, bothvisibleSepOct; Lhiddenunbounded), not previous turn defaults.
NOadd/commit/push/I7; protectedTOMLuntouched. Next: final review, owner UI verification and
separate physical legacy-date decision. Prior sections below are historical continuation evidence.


## I6 owner corrections + marker records — final owner visual gate (2026-10-10)

Continued dirty tree at 20a7c55835f2d129908da899f743e9583414f42a. No add/commit/push.
Resolved-setup generation guard and ViewModel panel session preserved. Main map is unified;
Points table/records only; temporary D102 preview removed, production32dp/pictograms/halo retained.
Typed FilterSets: OP Bee-record/all FlightCycle-record counts; H/L existing entrance height and
outer diameter. Room13/schema unchanged; optional SQL bounds retain I5/NULL dates; codec2 reads1.
All three marker types now open compact preview → existing full record by real UUID. Record origin
returns to main map; per-Territory camera target/zoom/bearing/tilt, selection and filters survive
ordinary record navigation. No process-death guarantee claimed. Historical selection-only notes
below are superseded by this owner extension.

Focused JVM109/full JVM877 pass, current Android83/83, focused marker-record Android22/22.
All requested build/compile/lint gates pass: build/i6-marker-record-quality-final.log.
Lint0errors/28warnings/6hints. Fresh final complete-diff independent review follows these final
changes; final response records its result. Report: docs/temporal-i6-owner-corrections-report.md.

Samsung DEV safe install-r APK SHA98F34958FDFF1B733CB23B072236FEE6E1FAC1003B95C4DDD2A05F85699AC4FC,
update2026-10-10 08:54:26, fontScale1.7. One panel/multiple live changes, May→July, numeric filters,
real marker filtering and selected halos checked. Actual Point16/Hollow13/LogHive1 → existing record
→ Back to selected preview checked. Point/LogHive marker screen bounds matched, zoom15 retained.
Original display restored: OP visible Sep–Oct2026/no counts; H/L hidden/unbounded; final PB snapshot.
No owner DB fixtures/deletion/clear/uninstall. Protected verifier TOML untouched, intentionally dirty,
SHA7388F2F233AB86694C69F15CB0BF8076C39A65932D2248D44046CFE2E46A1F31.
Next: final independent review, then owner final Samsung UI verification; STOP before commit/push/I7.

## I6 interaction defect fixed — the panel no longer closes on every change (2026-10-10)

Owner-reported defect: in «Данные на карте» every action — a visibility switch, a calendar tap, a
reset — closed the panel and returned the user to the map, so enabling three types took three
openings of the panel. Fixed in the same uncommitted I6 working tree; NO commit, NO push.

### Root cause (measured, not guessed)

Temporary diagnostics on the Samsung device produced this exact sequence on one switch tap:

```text
00:24:12.973  recompose open=true            <- the switch was tapped, display state updated
00:24:13.001  activity route=Loading         <- the route flipped to Loading for ~60 ms
00:24:13.058  PANEL_STATE_CREATED            <- the whole map screen (and the panel) was recreated
00:24:13.060  activity route=CurrentTerritory
00:24:13.061  recompose open=false           <- the panel is gone; the user is back on the map
```

The panel was never asked to dismiss (`ON_DISMISS_REQUESTED` never fired, and no scrim, Back, swipe or
drag was involved). The chain is:

1. `MainViewModel.initialSetup` (`MainViewModel.kt:410`) is
   `combine(setupFacts, setupRefresh).flatMapLatest { flow { emit(InitialSetupState.Loading(...)); … emit(Ready(...)) } }`,
   and `setupFacts` includes `settingsRepository.settings`.
2. Every change in «Данные на карте» writes the display state to that same settings DataStore.
3. `settings` re-emits → `flatMapLatest` restarts the inner flow → it **emits `Loading` first** →
   `startupDestination` → `route = AppRoute.Loading`.
4. `MainActivity` renders routes with a plain `when (route)`, so the transient `Loading` branch
   disposed `CurrentTerritoryScreen` together with the panel's `rememberSaveable` open flag.
5. ~60 ms later `Ready` returned the map, and the panel was closed because its state had been lost.

So the defect was a **state-ownership/lifecycle** bug of exactly the class AGENTS.md §14 warns about
(a durable UI session living in a subtree that can be recreated), triggered by an app-wide
`Loading` flash that any settings write could cause — not a bottom-sheet problem.

### Fixes

- `MainViewModel.initialSetup`: a re-read of an **already resolved generation** no longer emits
  `Loading` (`resolvedSetupGeneration`), so a settings write cannot flash the route through `Loading`
  and tear the visible screen down. A new generation still starts from `Loading`, so startup
  behaviour is unchanged.
- The panel session moved out of the composition into `MapDataViewModel`: `panelOpen` and `openedType`
  are part of `MapDataUiState`, with `openPanel` / `closePanel` / `openTypeFilters` / `closeTypeFilters`.
  The sheet is now stateless with respect to its own navigation, so nothing that merely recomposes the
  map surface can close an open panel or lose the filter screen the user is working in.
- `MapDataFiltersScreen`: «Готово» now finishes **that type** and returns to the type list
  (`onDone = onBack`); only the main panel's `Готово`, `✕`, a system Back from the type list, a swipe
  down or a tap on the scrim close the sheet.
- `MainMapScreen`: the open flag is no longer `rememberSaveable`; the panel is rendered from
  `uiState.panelOpen` and driven by the ViewModel callbacks.

### Behaviour after the fix (owner scenario, reproduced literally on the device)

One opening of «Данные на карте» was enough for: three visibility switches (panel stayed open after
each), the type filter screen, several calendar actions (МАР → СЕН gave the range «мар–сен 2026» with
the boundaries and in-range months drawn as approved), `Готово` in the editor (returned to the type
list, **not** to the map), a second type configured in the same session (Дупла = 2026, with the
approved hint «Один год даёт период с 01.01 по 31.12 этого года.»), and finally `Готово` on the main
panel, which closed the sheet and returned to the map. The list then showed three independent states
(`мар–сен 2026` · `2026` · `Всё время`), which survived a force-stop and relaunch, and
`Сбросить фильтры` cleared the periods without changing visibility and left no stored entry.

### Regression tests (all pass)

Instrumented `MapDataPanelTest` (31 tests) now drives the **real** `ModalBottomSheet` and pins the
owner's sections A–J: visibility toggles of all three types keep the panel open (and never act as a tap
on the parent row); precision and year/month/day selection keep the editor open; both range boundaries
keep the editor open; the in-section reset keeps the editor open; the editor's `Готово` and system Back
return to the list while the panel stays open; Back and `Готово` on the main panel close it; toggling
visibility never resets a period; and a full multi-type session in one opening ends with three
independent states.

Because the panel-level tests cannot reproduce a route-level failure, two further gates cover the
reported mechanism itself:

- `CleanStartupIntegrationTest.writingTheDisplayStateNeverFlashesTheRouteThroughLoading` reaches the map
  as a startup destination, performs the exact write a panel change performs, and asserts that the
  resolved setup state never returns to `Loading` and the route never becomes `Loading`. Its collector
  uses `Dispatchers.Unconfined`, because with cheap reads the `Loading`→`Ready` pair happens inside one
  main-thread turn and a queued collector would conflate it away. Verified both ways: with the fix it
  passes, and with the guard temporarily disabled it fails with `saw [Loading, Ready]`.
- `InitialSetupLoadingRuleTest` pins the rule itself (first load and a new generation show `Loading`;
  re-reading an already resolved generation does not).

`MapDataViewModelSessionTest` (6 instrumented tests) pins the session owner: the panel starts closed and
opens at the list, visibility/period/reset changes keep it open and keep the current type screen, the
type screen's Back/`Готово` return to the list, only an explicit close ends the session, reopening never
resumes the previous type screen, the three types stay independent, and losing the Territory clears the
session and the displayed state. That last test found and fixed a real gap: `setTerritory(null)` used to
leave the panel open over a map with no research data, and now closes it.

### Decomposition (AGENTS.md §14)

`Decomposition reviewed; kept cohesive because:`

- `MainMapScreen.kt` (538 lines) grew only additively: the panel, the type screen and the calendars live
  in `MapDataPanel.kt` and `MapPeriodEditor.kt`, the marker assembly in `MapResearchObjects.kt`, and the
  DEBUG specimens in the source-set files `app/src/{debug,beta,release}/java/org/beesearch/app/ui/map/MarkerVisualPreview.kt`
  (only referenced from `BeeMap.kt`), so what remains is one responsibility — the map screen, its
  scaffold, bottom panel and chrome glyphs.
- `MapPeriodEditor.kt` (582 lines, new) stays one file because it is a single screen-level editor: the
  level composables, the year/month/day grids, the shared cell and its copy constants all change together
  for one reason (the approved period model) and none of them is used anywhere else; splitting the grid
  geometry out would create a second file with one consumer, and the file contains no persistence, I/O,
  domain logic or second workflow.
- `BeeMap.kt` (1169 lines, +30 additive) is unchanged in structure: the new code is two parameters and
  one guarded overlay branch beside the existing overlay; extracting the pre-existing screen out of that
  file is an unrelated refactor and was not performed.
- `MainViewModel.kt` (1564 lines) changed by one guarded emission plus one pure rule; the rest of the
  file is pre-existing and untouched, and splitting it is not part of this task.
- The new presentation files stay near the 300-line review trigger: `MapDataDisplay.kt` 299,
  `MapDataViewModel.kt` 297, `MapDataPanel.kt` 396 (one surface: sheet + list + type screen + rows).

### Evidence

- `app/build/reports/i6-owner-gate/` — screenshots and UI dumps of the scenario
  (`50-scenario-open` … `74-default-clean`), plus `datastore-before-scenario.bin`, a byte copy of the
  display state the owner had before this gate.
- Samsung SM-S938B / RFCY90MBYVZ, in-place `install -r` only: the installed `base.apk`
  `d2b12c02f0df835b14601a9637adc9a00155a426e6ac3156d0e655a71218288e` equals the built APK,
  `firstInstallTime 2026-09-16 08:13:50` unchanged, and the DB and DataStore hashes were identical
  before/after every install. The device is left in the **default** display state (all types visible, no
  periods, no stored entry, DataStore back to its baseline hash) and on the map with all real markers.
- Gates after the last source change: full JVM `873 tests / 107 suites / 0 failures / 0 errors /
  0 skipped`; instrumented I6 set **45/45** (`MapDataPanelTest` 31, `MapDataViewModelSessionTest` 6,
  `DataStoreMapDataDisplayStoreTest` 5, `MapResearchObjectsQueryTest` 2, and
  `CleanStartupIntegrationTest#writingTheDisplayStateNeverFlashesTheRouteThroughLoading`);
  `assembleDebug`, `assembleDebugAndroidTest`, `compileDebugAndroidTestKotlin`, `compileBetaKotlin`,
  `compileReleaseKotlin`, `lintDebug` (0 errors, 24 warnings, 3 hints) and `git diff --check` all PASS.
- The new startup gate was verified in both directions: it passes with the guard and fails without it
  (`saw [Loading, Ready]`), and it had to observe through `Dispatchers.Unconfined` because a queued
  collector conflates the `Loading`→`Ready` pair away.
- Related startup/territory screens re-checked on the device after the route fix:
  `InitialSetupScreenTest` + `TerritoryManagementScreenImeTest` 6/6 PASS.
- The Android emulator (`emulator-5554`) died during one batch run (`Can't find service: package`) and
  was restarted headless; results above are from the restarted, healthy instance.

## Current continuation — Temporal I6: unified data map / «Данные на карте» (2026-10-09)

Baseline HEAD/main/origin/main: `20a7c55835f2d129908da899f743e9583414f42a` (D102 committed/pushed).
Work is uncommitted in the working tree: NO commit, NO push, index empty. I6 (unified visualization +
«Данные на карте» + independent per-type date periods) is IMPLEMENTED and VERIFIED, and it AWAITS
OWNER SAMSUNG UI VERIFICATION. Temporal I7 and the deployment/owner-device migration gate for the
temporal I2–I5 deployment unit are NOT started/closed by this work.

### Authoritative contract used (recovered, not invented)

- `docs/ui/mockups/unified-territory-layers-v2-1.md` — APPROVED UI DIRECTION (2026-10-02), frames A–D.
- `docs/ui/mockups/unified-territory-layers-v2-1-calendar.png` — APPROVED frames E–H (calendar levels);
  the exact copy of the frames was read from the artifact at full resolution and used verbatim.
- `docs/ui/unified-territory-map-display-spec.md` — APPROVED behavioural specification: §8 summary,
  §9–§13 calendar, §15 reset, §17 map result, §18 indicator, §20 per-Territory persistence, §21
  accessibility, §26 deliberately unspecified.
- There is NO separate «Визуализация» screen in any approved artifact: the approved surface is a third
  action in the existing bottom map panel (`Объекты · Данные на карте · Настройки`) whose panel and
  type screen live inside the existing map screen. No map mode, screen or control was added, and no
  second unified-map concept was created.

### Implemented (narrow, in the approved boundary)

- Marker vocabulary reused from D102: `MapObjectType`/`MapDataType` now hold exactly ObservationPoint,
  Hollow and LogHive; each resolves its own `ResearchMarkerType` (yellow wingless bee / green tree /
  brown trunk) through the single existing catalogue. 32 dp visual size, 48 dp targets, no clustering.
- Unified presentation/semantics: `MapDataDisplay.kt` (per-type visibility + own period, summary
  formatting, precision projection, tap rules), `MapDataDisplayCodec.kt` (versioned per-Territory
  value), `MapDataDisplayStore.kt` + `DataStoreMapDataDisplayStore.kt` (existing settings DataStore,
  key `map_data_display_<territoryId>`, default state never written), `MapDataViewModel.kt`,
  `MapResearchObjects.kt` (each type's interval goes only to its own I5 query),
  `MapDataPanel.kt` (sheet + type screen + accordion), `MapPeriodEditor.kt` (Год/Месяц/День).
- Map: the field map draws research markers of visible types, each type filtered by its own period in
  SQL through the I5 query layer. The overlay stays out of the участки/placement modes and AREA_VIEW.
- Room stays **v13**: no schema, migration, index or canonical-date-column change. Periods are
  presentation/query state and never write a research date. No ObservationPoint date-edit UI exists.
- Trap and Apiary remain reserved D102 vocabulary: not in the panel, not in the marker path, not in
  the codec; `Apiary` rows returned by the repository are dropped before any marker or label.

### Automated gates (all after the last source edit)

- Focused JVM: `org.beesearch.app.ui.map.*` including the new `MapDataDisplayTest` (8),
  `MapPeriodSelectionTest` (12), `MapDataDisplayCodecTest` (4), `MapResearchObjectsTest` (5),
  `MapPeriodGridTest` (4) and the extended `SavedObjectMarkerPresentationTest` (3) — PASS.
- Full JVM `:app:testDebugUnitTest`: **870 tests / 106 suites / 0 failures / 0 errors / 0 skipped**.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:compileDebugAndroidTestKotlin`,
  `:app:compileBetaKotlin`, `:app:compileReleaseKotlin`, `:app:lintDebug` — PASS;
  lint **0 errors, 24 warnings, 3 hints** (unchanged from baseline).
- Instrumented, API29 emulator-5554, focused I6 classes (`MapDataPanelTest`,
  `MapResearchObjectsQueryTest`, `DataStoreMapDataDisplayStoreTest`): **30/30 PASS**, including the
  per-type independence, visibility independence, 48 dp targets, reflow, system-back priority,
  undated-record wording, panel reopen and restart-persistence cases.
- The **full** connected suite on this emulator aborts (app process crash on a closed in-memory
  SQLite database). Reproduced identically on an untouched HEAD baseline worktree, so it is a
  pre-existing environment/infrastructure fragility, not an I6 regression. Filtered runs are used.
- Two `CleanStartupIntegrationTest` failures (`deletingAnObjectUsedByObservationDataIsRefused…`
  timeout and `backupAccessScreenOpensFromSettingsAndReturnsToSettings` closed connection pool) were
  reproduced on the untouched HEAD baseline worktree: pre-existing, and the first is the same known
  failure already recorded in this file for baseline `fd0e380`.

### Samsung owner gate (SM-S938B / RFCY90MBYVZ, in-place `install -r`, no uninstall/clear)

- Installed APK SHA-256 `062958f350154c3db5348930df3db085d62ee5926f29134edaa6c17677e70250` equals the
  built `app-debug.apk`; `lastUpdateTime` advanced, `firstInstallTime 2026-09-16 08:13:50` unchanged,
  and DB/WAL/DataStore hashes were identical before/after every install. Rooted in real records only:
  no fake record was created and none was deleted.
- Real data on the production map: ObservationPoint markers (Точка 16–22), Hollow markers
  (Дупло 12–14, «дата фиксации 09.10.2026») and the LogHive marker (Колода 1, «дата фиксации не
  зафиксирована»). The undated LogHive is exactly the legacy NULL-date case.
- Verified on the device: entry action + state indicator (words in the description, dot as a second
  channel); panel rows with independent summaries; hiding a type removes only its markers and keeps
  its period; setting Дупла = 2026 changed only that type (Точки and Колоды stayed «Всё время»); the
  state survived force-stop/relaunch and is persisted per Territory (DataStore value
  `v1|{…"HOLLOW":{"visible":true,"from":"2026-01-01","to":"2026-12-31"}…}` under
  `map_data_display_48ef6a6c-…`); «Колоды» with only undated records shows «Нет записей этого типа с
  известной датой фиксации.» while its marker stays visible; the day level renders the approved
  seven-column weekday calendar with 48 dp cells at the phone's 360 dp width; the panel at the
  owner's system font scale (1.7) shows every row and both actions; «Сбросить фильтры» cleared the
  periods, kept visibility, and returned the store to the default state (entry removed, DataStore
  hash back to the pre-gate value). The phone is left on the default display state.
- Evidence: `app/build/reports/i6-owner-gate/` (ignored, not committed).

### Open items / limitations (for the owner)

1. **Marker tap on the unified map.** Neither V2.1 nor the approved specification defines what
   tapping a research marker on the map does, and V2.1 forbids additional controls over the map, so
   no card, screen or navigation was invented: a tap selects the object (approved D102 selected
   treatment) and a second tap clears it. Opening the object from this map is a separate product
   decision.
2. **No real device record can demonstrate an excluding period.** Every dated record on the phone is
   in 2026 and the only LogHive has no date, so a bounded period that excludes a real record cannot
   be shown without creating data. Exclusion and NULL-exclusion are covered by the in-memory Room
   test `MapResearchObjectsQueryTest` (bounded excludes out-of-range and NULL; unbounded keeps NULL).
3. **Compose marker scale/performance benchmark (200–1000 markers)** from `docs/architecture.md`
   §73.3 remains open; it is a pre-rollout measurement, not an I6 implementation defect.
4. Known device observations already accepted in D102 stay unchanged: nearby markers overlap at low
   zoom, and a point card can occlude the selected marker's halo in the «Точки» browser.
5. `PhysicalObjectCardsTest` reports 2 failures on this emulator (`«Направление летка» is not
   displayed`). The class exercises only unchanged code (`PhysicalObjectCards`/`LogHiveCard` + theme)
   and touches no I6 file; the failure was not reproduced against the baseline worktree, so it is
   reported as an unverified environment/order-dependent failure rather than an I6 regression.
6. Documentation follow-up still owed after the owner gate: Help content, I016 status reconciliation
   in `docs/ideas.md`, and the checklist's documentation-follow-up section.
7. Three independent read-only reviews were run on this diff (final verdicts: BLOCKER 0 / HIGH 0 /
   MEDIUM 0 after each round's fixes). The code left in the tree is the reviewed revision; the
   device screenshots in `app/build/reports/i6-owner-gate/` are the visual evidence for frame
   fidelity at the real font scale.

Protected unrelated file `.codex/agents/luna-verifier.toml` is untouched and excluded from any I6
commit; SHA-256 `7388F2F233AB86694C69F15CB0BF8076C39A65932D2248D44046CFE2E46A1F31`.
STOP before commit and push: the next step is owner Samsung UI verification of the new surface.

## Current continuation — D102 approved marker visual gate (2026-10-09)

Baseline HEAD/main/origin/main: 3c2c4c349b78accaf0d0d1b8c9a21d3efbec1df5.
Owner approved wingless existing Bee Search bee / tree / cut trunk / box / reserved house.
Contract and approved source image: docs/ui/mockups/d102-marker-pictograms-v1.md / .png.
ResearchObjectMarker reuses existing drawBeeMark geometry; colors reinforce distinct shapes.
Selected outline preserves pictogram. Production normal marker size is **32 dp** (owner-approved on
Samsung S25 Ultra, 2026-10-09); 24 dp reads but is less convenient in the field, and 20 dp is never a
normal size. The DEBUG 20/24/32 physical-px candidates are diagnostic only (density comparison), and
no px value defines a normal size. All interactive marker targets remain 48 dp.
DEBUG-only main map entry: DEV: маркеры; specimen rows above actual basemap, no Room writes.
Release/beta boundaries are no-op; the production SavedObjectMarkers path now uses the approved marker
(see the latest continuation below).
No reserved Trap/Apiary capability exposed. Temporal I6/I7, Room13, queries/wire/media unchanged.

Continuation 2026-10-09 recovered intact source/tests; no Kotlin changes needed after the
previous fontScale follow-up. Re-run focused marker/variant/map JVM 90/90 and full JVM 834/834 PASS.
assembleDebug, assembleDebugAndroidTest, compileDebugAndroidTestKotlin, release/beta compile PASS;
lintDebug 0 errors, 24 warnings, 3 hints. API29 emulator marker/preview/map tests 19/19 PASS,
including fontScale1.7 regression after the measured-controls layout fix. git diff --check clean.
Independent read-only full-diff review and follow-up: BLOCKER0/HIGH0/MEDIUM0.
Candidate px means icon canvas including halo space; visible pin is about 81% of that height.
Samsung SM-S938B / RFCY90MBYVZ in-place DEV install-r-t succeeded at 18:33:41 device time.
APK SHA256: 68e8d6ffa070fb898dfe0937ac8005d097abef6e89ad9a14fe3dc595d2f9620d.
UID10156, firstInstallTime 2026-09-16 08:13:50, CE438163/DE424656 unchanged;
all 4077 private + 2128 external file hashes identical immediately before/after installation.
No uninstall/clear or Samsung instrumentation. Technical Samsung screenshot at real fontScale1.7
shows controls and specimens without overlap. Owner approval of pictograms and normal size (32 dp) is
complete, and the remaining Samsung visual gate (selected state, nearby readability, contrast on the
available Онлайн/Векторная/Спутник Sentinel/Гибрид backgrounds, operational-symbol distinction at
32 dp) was accepted by the owner on Samsung S25 Ultra · 2026-10-09: OWNER PASS.
Phone left on main map, vector source, preview open, 24px and nearby specimens enabled.
Path: Bee Search DEV -> main map -> DEV: маркеры. First row tap changes selection;
second row is selected examples; Рядом/Разнести toggles nearby group. Bottom-left stacked-layers
button offers Онлайн карта / Векторная карта / Спутник Sentinel / Гибрид; no separate topo source.
D102 MARKERS IMPLEMENTED — AWAITING OWNER SAMSUNG VISUAL APPROVAL.
Do not resume I6 or claim D102 VERIFIED. No staging/commit/push; index empty.
Protected TOML existing diff preserved; SHA256 remains
7388F2F233AB86694C69F15CB0BF8076C39A65932D2248D44046CFE2E46A1F31.

DeepSeek continuation · 2026-10-09 (recovery audit, no rewrite): Codex D102 work was found intact
and was not restarted. Production: ResearchObjectMarker.kt + the guarded BeeMap hook and the
drawBeeMark visibility change; DEBUG-only: src/debug MarkerVisualPreview.kt with no-op beta/release
stubs; tests: 4 classes; docs: decisions/architecture/checklist/handoff + mockup md/png. Variant
boundary verified (release/beta compile as no-ops, no ResearchMarkerType in BeeMap, reserved
Trap/Apiary only in the catalog and preview), Room stays 13, no Temporal I6, no product capability added.
Two real defects in the untracked androidTest were found and fixed (both inside D102 scope):
ResearchObjectMarkerUiTest.kt did not compile (missing onNodeWithTag import), and
defaultVisualSizeIs32dpAcrossDensities called setContent three times, which fails on any device
(IllegalStateException; 4/5 passed, 1 failed before the fix). The stale claim in the earlier block
(compileDebugAndroidTestKotlin and emulator 19/19 PASS) therefore no longer described this revision.
Minimal corrections: px candidates are explicitly diagnostic (`GATE_*_PX`, `diagnosticSizesPx`) and
only NORMAL_SIZE_DP = 32 defines a normal size (no behaviour change); ResearchObjectMarkerTest now
asserts the approved 32 dp normal size; the mockup MD records that the PNG's "24 px recommended"
caption is the pre-device recommendation; the size/gate statements in the block above were corrected.
Gates after the fixes: full JVM 834/834 PASS (0 failures/errors/skips); focused API29 emulator
MarkerVisualPreviewTest + ResearchObjectMarkerUiTest 5/5 PASS; assembleDebug, assembleDebugAndroidTest,
compileDebugAndroidTestKotlin, compileBetaKotlin, compileReleaseKotlin PASS; lintDebug 0 errors,
24 warnings, 3 hints; git diff --check clean. Independent read-only review of the whole D102 change:
BLOCKER0/HIGH0/MEDIUM0 after the two fixes.
Samsung install was NOT performed in this continuation: RFCY90MBYVZ was not attached (only
emulator-5554). Ready DEBUG APK: app/build/outputs/apk/debug/app-debug.apk, 92,989,679 bytes,
SHA-256 DDB08DEC9A0133C4D2A4A03F08ADF14F8BE6B94CFDA1572101133A4999A34E8A; the in-place install and
preview preparation stay pending device attachment. The build already installed on the phone
(68e8d6ff…) contains the same preview, so the remaining visual gate (select 32 dp, tap Рядом) can be
performed without the new install. No uninstall/clear, no Samsung instrumentation.

## Latest continuation — D102 production marker integration (2026-10-09)

Baseline HEAD/main/origin/main: 3c2c4c349b78accaf0d0d1b8c9a21d3efbec1df5 (no commit in this step).
Owner Samsung visual gate PASS: 32 dp normal size, all five pictograms, selected state, nearby
readability, contrast on the available backgrounds and separation from operational symbols accepted.
Production integration (narrow): `SavedObjectMarkersOverlay` — the map of the «Точки» browser in
POINT_BROWSER mode — now draws each saved object with the approved `ResearchObjectMarker` at 32 dp
instead of the generic circle, keeps the 48 dp target, the Russian accessibility label with the
object's own state, and adds the approved selected halo for the object the host has selected
(`BeeMap(selectedSavedObjectId = …)` from PointsScreen). `MapObjectType.researchMarkerType()` is the
only record→marker mapping, so no colour/size is defined a second time, and pins now anchor by their
tip (`ResearchMarkerCatalog.PIN_TIP_FRACTION`) so the recorded position stays under the tip; the old
presence-tone circle colours were removed (state still announced in the label).
Reserved: Hollow and LogHive have no map screen yet (no production marker surface, no new screen was
created), Trap has no domain type at all, Apiary has no user-reachable lifecycle — none of them
entered the production data flow, filters or UI. Room/schema/migrations, temporal I6, overlays and
lifecycle safeguards are unchanged. The DEBUG preview stays as the visual regression surface.
RasterBasemapPocDeviceTest now reads the approved family colour and the tip anchor instead of the
removed tones.
Owner production map verification · 2026-10-09: **PASS** — production markers on real ObservationPoints
render correctly, 32 dp and the selected state are accepted, and changing the map background did not
require any D102 change. Owner decision on nearby points: overlap at low zoom (including intersecting
touch targets) is accepted behaviour for this version because zoom separates the points; 32 dp is not
reduced, and clustering/spiderfy/auto-spacing stay out of D102 (see docs/decisions.md). Samsung DEV was
updated in place (install -r; UID 10156 and firstInstallTime unchanged, DB/DataStore/binding hashes
identical before/after). D102 CLOSED / VERIFIED / OWNER PASS; this finalization commit uses the message
`feat: add research object map markers`. No push in this step; Temporal I6 not started.

## Historical continuation — Temporal I5 query/filter support (2026-10-09)

Baseline HEAD/main/origin/main: 61a6726109421e28b7860b3d7a9de7f02113b4e8.
I1–I4 verified against current Room v13/repositories/date parser/versioned wire paths;
their accepted semantics are unchanged. Earlier temporal continuation entries below are
historical execution evidence, not the current HEAD/deployment status.

ResearchDateInterval validates canonical LocalDate bounds and rejects reversed ranges.
ObservationPoint summaries accept an optional inclusive observation_date interval with
existing Territory/year/Flow/aggregate/created_at ordering. Physical-object lists accept
independent Hollow and LogHive fixation_date intervals, SQL-filtered before hydration.
Bounded physical queries exclude NULL; all-time queries preserve legacy unknown dates.
Apiary remains unbounded; no Inspection, I6 UI, global period or I7 Samsung gate added.

Host: full JVM 829/829 PASS, including 4 interval tests; assembleDebug,
compileDebugAndroidTestKotlin and assembleDebugAndroidTest PASS. Isolated emulator-5554
API29: 86/86 Room/repository/temporal/migration tests PASS, including 4 new I5 tests.
Initial new-test ordering assertion was corrected to match existing created_at order;
final rerun PASS. No Samsung install or action. No schema/migration/index change.
Measurement evidence and index decision: docs/temporal-i5-query-measurement.md.
Final decision: NO NEW INDEX, based on representative/stress query plans and repeated
timings; wide observation regression and write/storage costs outweigh sub-ms gains at
1,000 rows/Territory. Fresh independent read-only review: B0 / H0 / M0. Diff check PASS.
Finalization interval JVM rerun: 4/4 PASS; final audit/review B0 / H0 / M0.
No production/test/tool code changed after the reviewed state. Collection export and
all existing production consumers remain unbounded via default null intervals.
Verdict: TEMPORAL I5 VERIFIED. I6 UI and I7 device gate remain PENDING.

Owner authorized one isolated local I5 commit; no push or Samsung action.
Protected pre-existing verifier TOML excluded and unchanged:
SHA256 7388F2F233AB86694C69F15CB0BF8076C39A65932D2248D44046CFE2E46A1F31.
I5 implementation is closed; I6/I7 require separate tasks.

## Current continuation — Large Media Ingest (2026-10-09)

Separate from the unfinished EXIF display work retained in the worktree. Its diff is preserved;
no camera/full-view acceptance or EXIF commit is implied by this ingest milestone.
The explicit ingest request supersedes the older operational instruction to retain caps.
Removed the fixed 16 MiB limits from both media stores, for draft/import photo and physical
photo/video. Shared MediaFileWriter retains bounded streaming, actual Long size/SHA,
checked arithmetic, cooperative cancellation and close-before-rename publication.
Cleanup also covers cancellation returning from the IO dispatcher; only new operation-owned
files are removed. Observation draft's pre-try write cleanup gap is closed. Activation/rollback,
Room, metadata/ZIP guards, MIME/name/type and original media bytes stay unchanged.

Host gate PASS: 157 focused JVM tests, 0 failures/errors/skips, including 11 new generated
ingest regressions (20 MiB items, 72 MiB aggregate, exact size/SHA, read/write/close errors,
real Job cancellation and prompt cancellation after IO publication). assembleDebug and
compileDebugAndroidTestKotlin PASS; diff check PASS. Fresh final independent read-only review:
no BLOCKER/HIGH/MEDIUM. Post-review regression repeated: 157/157 PASS, 11 ingest tests;
assembleDebug and compileDebugAndroidTestKotlin PASS. Evidence is ignored under
app/build/reports/large-media-ingest/. LARGE MEDIA INGEST VERIFIED; narrow local commit only.

SAMSUNG / OWNER VERIFIED: RFCY90MBYVZ / SM-S938B / API36, fresh DEV same-signer install -r,
UID10156 and firstInstallTime preserved; all4073 persistent file hashes identical immediately
after update. Research DB/WAL, settings, managed media and map packages retained after launch;
five runtime-state files changed without loss evidence. Installed APK SHA256:
E3AD2A64BF334270F3BB4270BF13D0E2999CBE7F2A086E9B13BE05E71DB909F6.
Owner reported "Все нормально работает." for the agreed real video >16 MiB production-UI
gate: gallery selection, no old cap rejection, object save, reopen, external ACTION_VIEW
playback and persistence after restart. Exact video bytes/SHA were not recorded; do not invent
them. This owner evidence is distinct from generated host fixtures.

Known boundaries (not milestone defects): no maximum size/1GB ingest claim; real ENOSPC,
process kill during copy, cancellation of a permanently blocked provider before it returns,
and all possible Android providers are not verified. EXIF physical gate remains separate.
Finalization performs no device action; one ingest-only local commit, no push.

## Current continuation — M1 photo EXIF display (2026-10-09)

Baseline: main/origin/main 0ddd676; M2B is closed and published. M1 changes only photo
display: raw BitmapFactory preview decoding ignored EXIF, while ingestion retained original
JPEG bytes. Shared ImageDecoder preview decoding now honors orientations 1–8, including
reflections, with pre-decode output bounds of 512/1024 px. Existing photos with retained
EXIF benefit without rewriting/migration; missing orientation is not guessed. Physical-object
editor/detail/thumbnails and point preparation/detail previews use the common decoder.
Full physical-object viewing remains the external ACTION_VIEW consumer of original bytes.

Focused JVM tests: 16 PASS. Samsung SM-S938B/API 36: 6 new native/Compose orientation
tests PASS and 21 existing physical-object cards/editor tests PASS. Synthetic portrait and
landscape rendered correctly in thumbnail/enlarged production preview. Same-signer DEV
install -r succeeded; all 4067 private file hashes matched immediately after installation.
No user DB/settings/maps/media cleared. Cache fixtures cleaned. Independent review found
no BLOCKER/HIGH/MEDIUM. Evidence: ignored app/build/reports/media-m1/.

Initial camera/external-viewer acceptance was pending after the synthetic tests above;
the owner physical gate is now closed by the evidence recorded below. Original bytes,
video, M2B, archives and temporal semantics are unchanged by M1. The later Large Media
Ingest milestone is separate. Protected unrelated TOML stays untouched.

### M1 ObservationPoint external viewer continuation — 2026-10-09

Continuation baseline HEAD/origin/main: 270fc743055aafaff78c4adc6b5c4ff206ff1ebc
(`fix: support large media ingest`). Existing M1 decoder diff retained. Owner previously
confirmed Hollow portrait/landscape previews and external viewing, plus point portrait
preview; point tap failed because AttachmentRow had no click handler. FileProvider also
lacked the persisted observation-attachments root. Point image/fallback now opens the
managed original via content URI, image MIME, ACTION_VIEW, read grant and matching ClipData;
only that narrow files-path was added. Originals/EXIF/storage/video/ingest unchanged.

Fresh host gate: 27 focused JVM tests PASS (including 11 existing LargeMediaIngest tests),
assembleDebug, compileDebugAndroidTestKotlin, assembleDebugAndroidTest, diff check PASS.
Fresh independent read-only review: no BLOCKER/HIGH/MEDIUM. Samsung SM-S938B/API36:
same-signer DEV install -r PASS, UID10156 and firstInstallTime2026-09-16 08:13:50 preserved.
All 4076 private and 2128 external file hashes identical immediately after install.
47/47 scoped instrumentation tests PASS, including real point Image click/intent/URI
regression and existing native EXIF/UI tests. MainActivity launch PASS. After runtime,
research DB/WAL, settings/binding, original media, map-packages/map-poc and external files
unchanged; seven runtime files changed (SHM, WorkManager, mbgl cache, profile/ActivityThread),
no added/deleted files. Read-only host DB/WAL copy: integrity ok, zero FK violations.
Evidence: ignored app/build/reports/media-m1-viewer/.
DEV APK SHA256: 67AAE9FB3A3698BF938ADD624C31D63BFDF30DA5E8ECD918A4DB97E8CD619654.

### M1 owner physical device gate — 2026-10-09

Owner tested real new camera photos on Samsung SM-S938B, using the DEV APK identified
above. Complete physical matrix:

1. PhysicalObject / Hollow — portrait preview: PASS.
2. PhysicalObject / Hollow — portrait external viewer: PASS.
3. PhysicalObject / Hollow — landscape preview: PASS.
4. PhysicalObject / Hollow — landscape external viewer: PASS.
5. ObservationPoint / Point — portrait preview: PASS.
6. ObservationPoint / Point — portrait external viewer: PASS.
7. ObservationPoint / Point — landscape preview: PASS.
8. ObservationPoint / Point — landscape external viewer: PASS.

M1 owner device gate CLOSED / PASS, including actual ObservationPoint portrait and
landscape external viewing. This is owner camera/viewer evidence, separate from fixtures.
No implementation changes after owner PASS; the eight M1 code/test/XML files match the
source hashes recorded for the tested APK. Finalization is limited to M1 evidence and
one authorized isolated commit; no push. Protected verifier TOML remains excluded
(SHA2567388F2F233AB86694C69F15CB0BF8076C39A65932D2248D44046CFE2E46A1F31).

Finalization host regression (2026-10-09), fresh execution with --rerun-tasks: 27/27
focused JVM tests PASS, including 11/11 LargeMediaIngest tests; assembleDebug and
compileDebugAndroidTestKotlin PASS. No failures/errors/skips. No second device install.
Fresh final independent read-only review: 0 BLOCKER / 0 HIGH / 0 MEDIUM; final diff
check PASS. Verdict: M1 PHOTO ORIENTATION VERIFIED. Finalization uses one isolated M1
commit; no push. No further implementation or device work is pending for this gate.

## Current continuation — Large media M2B (2026-10-09)

Temporal work and M2A are owner-accepted CLOSED / PASS / PUSHED. M2A runtime gates
passed public BackupService media round-trip and three production export services through
system SAF; these are historical evidence, not new M2B runtime evidence.

M2B separates finite metadata budgets (16 MiB per entry; 64 MiB total or collection
128 MiB) and finite entry/object counts from declared media payloads. Media has no fixed
application size cap: validated inventory authorizes exact Long size/SHA/path/ownership.
Compressed ZIP is spooled without decompression, central index bounded before ZipFile,
full existing format/domain validation runs before authorized media extraction. Actual
size, SHA, CRC and local/central records are checked; overflow and unexpected entries fail.
Missing/truncated central directories are now malformed; valid legacy formats unchanged.
Closeable staging and validated restore activation/Room ordering remain. Advisory free-space
checks never substitute a fixed media cap; streaming I/O errors clean staging best-effort.

Host validation: 137/137 focused JVM tests PASS, assembleDebug and
compileDebugAndroidTestKotlin PASS. Includes generated media above 16/64 MiB and collection
132 MiB, metadata/count boundaries, exact size/hash, overflow and staging cleanup.
Independent read-only review closed writer entry-count symmetry; no remaining blocking
finding. Shared ZIP authorization infrastructure extracted into AuthorizedArchive.kt;
large codecs/BackupCore retain cohesive format/domain responsibilities.

Import caps remain 16 MiB. No >=1,000,000,000-byte end-to-end or ZIP64 runtime claim.
No ADB/emulator/Samsung actions. EXIF/M1 not started. Next gate: independent M2B review,
then owner-authorized isolated large-media runtime gate; do not deploy M1 automatically.
Direct-SAF Point partial destination and process-kill/disk-full runtime boundaries remain.
Protected unrelated TOML unchanged; local M2B commit only, no push.

### Samsung representative large-media runtime — 2026-10-09

M2B LARGE-MEDIA RUNTIME VERIFIED on RFCY90MBYVZ / SM-S938B / API 36.
Owner-authorized same-signer DEV `install -r` replaced the S3 APK with a fresh build
from e708c50; UID/firstInstallTime and all 4067 persistent private file hashes were
unchanged immediately after install. App startup succeeded. After startup/runtime,
research DB/WAL/SHM, settings/binding and managed-media hashes remained unchanged;
five runtime files changed (including WorkManager and mbgl-offline.db). Their exact
internal change mechanisms were not established; no persistent-file removal was observed.
Opt-in IsolatedLargeMediaArchiveTest uses only a unique cache fixture: production Single
PhysicalObject Export V2 encode/decode/readAuthorized, four generated 20 MiB payloads,
80 MiB aggregate, exact size/SHA, metadata-over-budget rejection and staging cleanup PASS.
1 Android test PASS; fresh focused JVM regression 137/137 PASS. Other profiles were not
rerun with large media on Samsung. Evidence: app/build/reports/media-m2b/samsung-runtime/.
No import-cap/production change; no >=1GB, playback, SAF-publication, ZIP64 or kill/ENOSPC
claim. M1/EXIF not started. This runtime fixture is retained as explicitly opt-in coverage.

### Known limitations — no action now

- `ArchivePayload.declared()` supplies only declared size/SHA for authorization;
  current validators do not read media bytes before authorization. A future validator
  calling a byte-reading API could receive a require/NPE failure rather than typed
  `ZipSafetyException`. This is not a current M2B defect. A future defensive improvement
  may provide an explicit typed fail-closed byte-reading rejection; no production change now.
- Point export retains direct-SAF publication: failure may leave a partial destination.
  M2B does not prove process-kill/ENOSPC durability. Cache/staging publication analogous
  to physical exports is a possible separate milestone/decision, not a new M2B requirement.

## Current continuation — Temporal I4 implemented in worktree, owner review required (2026-10-08)

I1 pushed (f4f5105 = origin/main); I2 committed locally (bd977b3); I3 committed locally
(ac6c7d1 = HEAD). I4 is unstaged/uncommitted; I5–I7 not started. Room stays 13; no migration change.

Current portable writers: SINGLE_OBSERVATION_POINT V2 (required observationDate),
SINGLE_PHYSICAL_OBJECT V2 and PHYSICAL_OBJECT_COLLECTION V2 (required nullable fixationDate per
object). Explicit canonical dates never derive from createdAt/timezone. All three V1 readers
remain compatible: legacy point reconstruction / physical NULL. Internal V1 writers retain typed
representability refusals; current V2 user paths have no legacy guard. Collection keeps D093:
one concrete type of one Territory. No UI/date editor/import-to-Room or caller correction change.

Shared canonical date parser now lives in domain/model/ResearchDate.kt; I3 typed Backup/Snapshot
boundary delegates to the same rule. Backup V7 / Snapshot V2 wire and PC verifier are unchanged.
V1 export contracts are historical; current versioned contract: docs/temporal-export-v2.md.
analysis-evidence-explorer stays V1-only and unchanged.

Current I4 gate: JVM 791/791 PASS; fresh PC Snapshot verifier 99/99 PASS.
assembleDebug, assembleDebugAndroidTest, lintDebug PASS (0 errors/fatal, 23 warnings, 2 hints).
Evidence: ignored app/build/reports/temporal-i4/ (commands, exact suite counts, final worktree).
Android instrumentation PENDING: isolated emulator-5554 read-only getprop timed out after 8s.
No APK installed; no Samsung operation. An initial new three-object test fixture used inconsistent
snapshots for the same Observer UUID; fixed the fixture, preserved validation, reran full gate.
**I2 + I3 + I4 remain one deployment unit. Samsung remains on I1; no I4 install.**
Temporal carriage is implemented; deployment still requires actual Room 12→13 migration,
Android end-to-end V7 restore, Snapshot V2 SAF publication/read-back, V2 export integration and
preservation of existing DEV DB/DataStore/maps/repository. I5/I6 are not this deployment gate.
Do not use old Samsung results as new evidence. No staging/commit/push; unrelated TOML preserved.

## Historical continuation — Temporal I3 (subsequently committed locally as ac6c7d1)

I1 committed/pushed (`f4f51051b49a357237a7f95e7d581d175e87adcf` = origin/main).
I2 committed locally, not pushed (`bd977b393619ab1bccb837a8f0d8c5b297305d50` = HEAD);
I2 isolated Room/device verification remains PENDING. I3 is unstaged/uncommitted; I4 is pending.

Current writers: Complete Backup V7 / archive schema 7 (Room provenance 13), Repository Snapshot V2.
Both carry REQUIRED canonical observationDate and REQUIRED nullable fixationDate as ISO YYYY-MM-DD.
Readers use explicit new-format dates, never createdAt derivation; missing/malformed/wrong-type dates
fail closed. Complete Backup V1–V6 / Snapshot V1 retain one-time legacy ObservationPoint reconstruction
and physical fixationDate NULL. Snapshot V1 closed keys and golden vectors remain unchanged.
V6/V1 legacy writer paths retain representability guards; current V7/V2 paths do not call them.
Single/collection Physical Object Export V1 guards remain until I4; Export wire was not changed.

Snapshot inventory stays 17 entries; profiles, media protection, restricted canonical JSON, digests,
limits, binding, immutable staging/publication and latest-valid discovery remain. V1 and V2 coexist.
Snapshot domain entries are validated before opening the output, then final archive validation runs.
Complete Backup still validates the entire graph before media/DB/settings restore and targets an
empty research database. PC verifier supports both versions, including strict V2 date key/type rules.
No Room migration/schema, numbering, date UI/callers, query/index, weather, map or Dxxx changes.
Normative version additions: `docs/snapshot-v2-wire-schema.md`, `docs/backup-format-v1.md`.

Executed JVM gate: 778/778 PASS; independent PC verifier: 99/99 PASS. assembleDebug,
assembleDebugAndroidTest and lintDebug PASS (0 errors, 23 warnings). Exact commands/counts are
recorded under ignored `app/build/reports/temporal-i3/`.
Android tests compile but instrumentation is **PENDING**: isolated emulator-5554 shell timed out after
8 seconds. Existing Samsung/old connected results are not I3 evidence. No Samsung install or ADB
operation; DEV DB, DataStore, maps, repository binding/directory and Stable/Beta remain untouched.

**I2 + I3 + I4 form one deployment unit. NOT DEPLOYABLE until I4 + Room/device verification.**
Samsung remains on I1. I4 must carry canonical dates in exports before deployment/exposure;
Room 12→13 migration requires actual isolated Room validation before future deployment. I5–I7 not
started. No staging/commit/push; unrelated TOML byte fingerprint preserved.

## Historical continuation — Temporal I2 verification (subsequently committed locally as bd977b3)

Baseline HEAD/origin: `f4f51051b49a357237a7f95e7d581d175e87adcf` (I1 committed).
Room v13 adds nullable ISO LocalDate `physical_objects.fixation_date`; migration 12→13 leaves
every legacy Hollow/LogHive/Apiary NULL and preserves existing columns, relationships and numbering.
New Hollow/LogHive derive fixationDate from the same single clock Instant as createdAt, using the
local device zone (injectable in tests). Technical createApiary continues to store NULL.
No explicit date input, correction API, date UI, date index or temporal queries were added.

**I2 implemented in worktree. NOT DEPLOYABLE until I3 + I4 carriage.** Do not install the intermediate
APK on the working Samsung: new Hollow/LogHive dates cannot be carried by current legacy formats.
Complete Backup V6, Snapshot V1, single and collection Physical Object Export V1 now fail closed
before publication when a serialized physical object has non-null fixationDate. Legacy NULL graphs
remain representable; readers materialize NULL without deriving from createdAt. Wire fields,
versions, layouts, manifests and PC verifier remain unchanged. Existing I1 date guards remain.
I3 must carry canonical dates in versioned Backup/Snapshot formats; I4 must carry them in exports.
Both remain mandatory before deployment/exposure; neither increment has been implemented here.

Verification: 760/760 JVM tests PASS; assembleDebug, assembleDebugAndroidTest and lintDebug PASS.
New migration, persistence/creation, repository-export-source and BackupService instrumentation
tests compile, but execution is **PENDING**: isolated emulator shell times out. No I2 APK was installed
on Samsung; Samsung DEV data/settings/maps and Stable/Beta were not touched. Host SQLite/schema
comparison is supplementary evidence, not Android Room migration validation.
Ignored evidence: `app/build/reports/temporal-i2/`. I7 acceptance is not claimed.
No staging/commit/push; unrelated `.codex/agents/luna-verifier.toml` remains byte-identical.

## Historical continuation — Temporal I1 verification before commit (subsequently committed as f4f5105)

Working tree on HEAD/origin `0686e09124434bd7eaef1f6482d5206d319d662c`: Room v12 adds REQUIRED
ObservationPoint `observation_date` as ISO LocalDate. Migration 11→12 reconstructs legacy dates once
from created_at/systemDefault, preserving old columns, numbering and relationships. Creation derives
observationYear from canonical date; correction API supports same-year preservation and transactional
cross-year MAX+1 renumbering. Correction has no UI/ViewModel/use-case caller; NewObservationPoint
has no explicit date override.

Shared legacy reconstruction adapts Complete Backup V1–V6, Snapshot V1 and export reader constructors.
Complete Backup V6 / Snapshot V1 / export wire inventories and versions remain unchanged; PC verifier
unchanged.

Owner ratified REQUIRED ObservationPoint legacy reconstruction. V6/Snapshot V1 writers now reject
unrepresentable canonical dates before artifact writing. Legacy archives still reconstruct in the
materialization timezone: a different timezone near midnight can change the reconstructed date;
this is an accepted legacy limitation, not fixed by the writer guard. Future versioned I3 formats
MUST carry explicit canonical observationDate; reader MUST use that required field without derivation
from createdAt and fail closed on missing/malformed values. No arbitrary extra-key extension of V1.

These formats do not carry future corrected dates. **I3 + I4 MUST complete before I6 or any
user-reachable explicit/corrected research-date path.** I2 is not started; next temporal increment is I2
only after owner review of I1. No Dxxx was added or altered; no staging/commit/push performed.

Final corrective verification: 755 JVM tests PASS (including 3 Snapshot guard tests);
assembleDebug/assembleDebugAndroidTest/lintDebug gates recorded in ignored evidence.
Samsung SM-S938B: 91 focused tests PASS (14 migration, 45 Room persistence, 28 BackupService,
4 legacy compatibility/Backup guard). DEV update-in-place; corrective-pass pre/post fingerprints
identical for database files, domain tables, DataStore/binding and fingerprinted map packages.
Read-only `device-observation-dates.json` records UUID/created_at/stored/expected/equal for all
five real DEV points: 5/5 equal=true at Europe/Moscow. Current installed I1 DEV APK matches build:
lastUpdateTime 2026-10-08 12:53:50, SHA-256
`B63BE6F2B86524A97DD5A6E375727B889B7D52CD0C586FCE3B6E78F4DDD6D8B8`.
Ignored evidence: `app/build/reports/temporal-i1/`. Full future UI/device acceptance (I7) is not claimed.
Pre-existing `.codex/agents/luna-verifier.toml` remains byte-identical and excluded from I1.

## Historical continuation — Map overlay architecture accepted (2026-10-08)

Owner accepted `docs/architecture.md` §73.3 as D102 after the initial Samsung DEV spike,
targeted lifecycle closure and review. Both architecture-evidence checklist items are closed.
Durable summary: `docs/map-overlay-lifecycle-evidence.md`; raw evidence remains ignored under
`app/build/reports/unified-map-overlay-spike/` and `unified-map-overlay-closure/`.

Future restore requires generation/request identity recorded BEFORE setStyle, stale-work rejection,
current fully-loaded Style, uninterrupted main-thread validation/checks/adds, app-owned ID namespace,
typed/fail-closed unexpected collisions and pre-add duplicate prevention; Sources before Layers.
Generation guard is preventive: no stale production callback was observed in today's local profiles.
Production registry/guard/IDs and Layers/Filters have not been implemented. No further architecture
spikes are required; scale, failures/cancellation, recreation, frame budget, ordering, flicker and
MapLibre upgrade regression remain implementation/device verification in §73.3.

Before production research markers: separate Samsung pictogram visual pass remains mandatory
(shape-first, selected retains type, contrast on vector/Sentinel/raster/Hybrid); exact art is undecided.
Next production stage: **TEMPORAL DATA MODEL IMPLEMENTATION** according to the approved
`docs/temporal-data-model-design.md`. Not started in this docs-only acceptance slice.
Baseline was `8f0ee48e04eb6226e3e4c7b1374a3a1bd53c78ac`; pre-existing dirty
`.codex/agents/luna-verifier.toml` belongs to other work, was preserved and excluded from the commit.

## Historical continuation — single-button backup implementation (subsequently committed as 8f0ee48)

Owner-approved post-device-test UX correction: the owner manually verified the new backup operation
on Samsung and replaced informational-only path behavior with a link opening the current bound
Backup directory in system Android Files UI. The action uses the persisted SAF tree and a read-only
binding/access probe; it does not pick/rebind, persist grants, create folders/backups or run summary/
media verification. Underlined path retains the existing composition without a chevron or extra
button. It is unavailable on access-loss screens and during backup creation. PNG remains unchanged.
Correction verification: full JVM/debug/debugAndroidTest/lint gate PASS (748 JVM tests, zero
failures/errors; lint zero errors, 23 warnings), and 33 focused Samsung UI/Room/SAF tests PASS.
New assertions cover click routing, access loss/restoration, exact directory document intent, no
persistable/write grant, repeated binding/permission preservation, and long-path fontScale 1.0/1.7.
Actual Settings → Backup → path tap opened `BeeSearch/Dev/Backup` in Android Files, showing Media,
Snapshots, Staging and repository.json. System Back traversed parent folders before returning to
Bee Search. Access remained available and binding persistence stayed byte-identical. A real Create
after returning succeeded with 5/5 verified files and last-backup time 7 October 2026, 22:50.
Screenshots are under ignored `app/build/reports/backup-path-review/`. No destructive device action
was performed; one new backup was created as the requested positive regression check.

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
Production implementation is now in the working tree on baseline
`c84270cdea1c5559789d9176efe2fa653b41f6d7`; HEAD and origin/main remain unchanged.
`readPublishedSummary()` validates published snapshot containers without opening Media, with a
separate result and pass-through through the existing bound/service/coordinator layers.
`CreateBackupOperation` captures one Room graph and one portable-settings snapshot per attempt,
protects media derived from that graph and publishes FULL from the same captured entries through
the existing strong evidence gate. Partial media failure publishes no snapshot; accepted blobs remain
and Retry captures current state again. The approved v2 UI uses transient real counts and zero-media
Success without a media claim. Existing access/error semantics and standalone APIs are preserved.

Verification: final `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
:app:lintDebug --offline --max-workers=2` gate PASS (JVM 748 tests, zero failures/errors/skips;
lint zero errors, 23 warnings). The invocation used `-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8`
without changing Gradle configuration. Samsung DEV was updated in place; final focused UI/Room/access
tests passed 27/27 (including the scroll-reset adjustment), and the real-media protection/reuse test
passed 1/1. An intervening locked-device rerun had 20 UI failures (no Compose hierarchy); after owner
unlock the same final APKs passed. No uninstall, data clear, restore,
negative live-repository tests or owner-data deletion was performed.
Nine final UI fixture screenshots at fontScale 1.7 were visually reviewed against v2, including
expanded/scrolled content and both Success variants. Artifacts are under the ignored
`app/build/reports/backup-v2-review/`. Long content remains available by scrolling, with a stable
bottom action. The original implementation pass had no manual end-to-end tap against the owner's
live repository; the subsequent correction verification above now supplies that evidence.

Next: owner review of the uncommitted implementation and final device layout evidence.
No commit or push was performed. Do not reopen the mockup-first gate. The rest of S5 policy remains
future work; no cache, background verification, media-health UI or cleanup policy was introduced.

## Historical continuation — S6B FULL / LOCAL_VERIFIED Snapshot (finalized and pushed; backup mockup approved, mockup-first gate closed)

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
informational (non-interactive) storage path row (historical rule; superseded by the owner correction
above), the collapsed summary `Данные исследований,
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
