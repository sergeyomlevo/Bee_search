# D106 My points implementation evidence

2026-10-20. Staged implementation in task/d106-stage0-overlay-registry.
OWNER approval commit: 8329291. No push or Samsung deployment authorized.
Stage1/H05 OPEN; unified Backup/Restore and Android Auto Backup untouched.

## Stage B targeted fix and qualification — current result, 2026-10-20

Stage B emulator qualification PASS on API29 emulator-5556 at actual fontScale
1.7. This section supersedes the historical blocked results below. C/D NOT
STARTED; Stage1/H05 OPEN. Samsung, TalkBack and real camera NOT RUN.

Draft viewer failure came from files/user-point-media-staging being absent from
FileProvider paths while only cache staging was configured. Added the exact
files staging directory; existing ACTION_VIEW content URI/read-only temporary
grant and managed media activation/rollback/cleanup remain in use. Provider
qualification rejects private files, sibling-prefix paths and parent traversal.

Local PNG composition corrections: pinned title/subtitle, six-decimal coordinate
formatting, photos divider and card hero aspect 1.8. Material palette/font metrics
remain platform adaptations. Before/after runtime screenshots, comparison table,
manual save/card, rotation, URI permissions and cleanup evidence:
[detailed qualification](d106-my-points-stage-b-qualification.md).

Final verification: JVM1067/1067; API29 Android16/16 (creation3, lifecycle2,
EXIF2, PhysicalObject editor/media6, provider/camera3); debug app/test APK builds;
lintDebug 0 errors,33 warnings,6 hints; diff-check PASS. Camera success/cancel use
controlled ActivityResultRegistry results, not an external camera/hardware test.
Production save/card and draft/saved external photo opening have runtime evidence.
Process-death recovery is not claimed. Evidence and machine-readable status are
linked from the detailed qualification and stage-b-checks.json.

Local B commit contains only B implementation/tests/reports and its evidence.
Prior handoff diff, research, GPX reports and earlier recovery evidence are
preserved; no push or physical-device operation. Decomposition review remains
recorded below. Stop after B; C/D require a separate task.

## Historical infrastructure recovery — superseded by targeted fix below

Infrastructure RECOVERED; Stage B QUALIFICATION BLOCKED by a reproduced product
photo-preview defect. No B commit. C/D NOT STARTED. Stage1/H05 OPEN.
This section supersedes the historical blocked-environment status below.

Preflight confirmed branch task/d106-stage0-overlay-registry, HEAD
8ebcf06bd550c9642db8cb5cbc189900b1c83ef3 and empty index. Every baseline-hashed
production file, prior Samsung report and research file still matches the saved
pre-recovery SHA-256. Production, dependencies, Gradle and SDK were not changed
for qualification. Only the two UI test files contain recovery adjustments:
exact combined-coordinate expectation, unambiguous chooser screenshot root,
and export of already-asserted EXIF preview images. The first API29 run's XML
retains the original assertion/root/focus failures; no assertions were disabled.

Recovery history supplied with this continuation identified the old API29 owner
as emulator PID23976 / QEMU24504 from the completed GPX session, with all122 QEMU
threads Suspended. Targeted signed Microsoft PsSuspend resume restored shell
briefly, then that instance exited. One cold start of the same AVD, without
snapshot or data wipe, completed in156229ms (preserved coldboot log). No global
adb restart, process termination, AVD recreation or additional competing instance
was used. Current process inspection confirms API29 emulator16728/QEMU48472 on
port5556; API37 emulator57512/QEMU24640 on5558 remains separate and untouched.
Process suspension cause is UNKNOWN; the prior owner diagnosis comes from the
supplied recovery transcript, not a new observation of the terminated PID.

Actual current target: emulator-5556, BeeSearch_ZIP64_API29, Android10/API29,
font_scale1.7, physical1080x2280/density440, override density480. The earlier
transcript's1080x2340 claim is corrected by current wm output and the coldboot log.
Installed org.beesearch.app.dev APK equals the local debug APK, SHA-256
1519e7a782ab250fd6a9b2fc11e9ede3a7a28f810e4546efc803eefde011d351.
Only this emulator was used for the recovery tests/smoke; no Samsung operation.

| Check | Result and actual scope |
|---|---|
| UserPointCreationUiTest | PASS3/3: chooser callbacks/disabled states, validation and Room save, coordinate return/draft and cancel |
| PhotoPreviewUiTest | PASS2/2: portrait/landscape EXIF pixel assertions in thumbnail and enlarged preview |
| Test execution | PASS5 executed,0 failure/error/skip; nonzero scenario durations55.169s total, captured runtime form/chooser/previews |
| Prior safety/navigation | PASS4/4 on API37, preserved evidence; not rerun or claimed as one API29 nine-test run |
| Prior JVM/build/lint | Previously PASS7/7, assembleDebug/lintDebug; unchanged production; not rerun in this continuation |
| Production entry/navigation | PASS current observer prerequisite, + -> chooser -> MyPoint -> map OK -> form |
| System image picker | PASS cancel returns to draft; two image PNG fixtures selected together from Downloads and shown as thumbnails |
| Draft photo ACTION_VIEW | FAIL: tapping portrait thumbnail shows 'Не удалось открыть фотографию', stays in form |
| Back and owned draft cleanup | PASS system Back returns to map; two exact draft files before Back, zero afterwards |
| Font1.7/scroll/bottom commands | PASS vertical scroll reaches complete media, Cancel and Save controls; production full-screen evidence retained |
| Approved PNG+MD composition | FAIL visual comparison: production coordinates render raw double strings over two lines; mockup has compact six-decimal single row. Header composition also differs (missing subtitle and header scrolls away rather than remaining in the bottom reference). Theme color differences alone are not treated as failure |
| Production save -> card / card Back | NOT RUN after reproduced blocker; isolated UI test proves save persistence, not whole-app card navigation |
| Camera capture, TalkBack, Samsung, process-death recovery | NOT RUN |

Observed photo failure is independent of Espresso. The two managed draft files
exist under files/user-point-media-staging/<session>/<id>. Source inspection:
PhysicalObjectMediaFileStore.draftRoot uses filesDirectory, while file_paths.xml
contains only cache-path for user-point-media-staging and files-path for final
user-point-media. Thus the existing FileProvider cannot issue a draft URI;
openManagedPhoto catches IllegalArgumentException and shows the observed toast.
This causal diagnosis is source-derived, corroborated by the runtime screenshot
and file inventory; no uncaught exception stack trace is claimed.
Official FileProvider documentation defines files-path/cache-path separately and
getUriForFile's out-of-root exception:
https://developer.android.com/reference/androidx/core/content/FileProvider.
No broader storage exposure, file movement or production fix was attempted.

Runtime comparison used approved my-points-v1 PNG+MD (chooser, location, empty
form and bottom actions); no mockup or acceptance contract was altered. Large
font controls are reachable, but that does not establish composition acceptance.
Evidence: [recovery metadata](ui/evidence/my-points-stage-b-20261020/recovery-metadata.json),
[5-test XML](ui/evidence/my-points-stage-b-20261020/api29-five-results.xml),
[empty form](ui/evidence/my-points-stage-b-20261020/runtime-create-empty.png),
[bottom actions](ui/evidence/my-points-stage-b-20261020/runtime-create-bottom.png),
[two photos](ui/evidence/my-points-stage-b-20261020/runtime-create-two-photos.png),
[preview failure toast](ui/evidence/my-points-stage-b-20261020/runtime-photo-viewer.png),
[Back to map](ui/evidence/my-points-stage-b-20261020/runtime-back-to-map.png).
Prior API37 XML, first failed API29 XML, baseline hashes and coldboot log retained
alongside the screenshots. Earlier evidence files were preserved unchanged.

Stop within TARGETED QUALIFICATION RECOVERY. B commit is withheld because the
required runtime photo behavior/composition did not pass. The next separate scope
must address the narrow production FileProvider draft-path defect and visual
differences, then repeat affected checks and complete card/camera navigation
qualification before committing B. Do not begin C/D automatically. No push.
Emulator-only public B20 fixtures and QB20 test observer remain; the unsaved
point and its staged photos were discarded by normal Back. No database clear,
uninstall or removal of pre-existing user data occurred.

## Stage A — data

Room 2.8.4; additive schema 16 to 17, user_points and user_point_photos.
Point and photo metadata create/update share a transaction. Creation forces
is_visible=true in the same row. Editing preserves saved visibility and creation
facts. All reads and changes are Territory scoped; point deletion cascades only
its photo rows, and Territory deletion is blocked while it owns points.
Managed-file cleanup is not implemented or claimed at this stage.

Current run: offline connectedDebugAndroidTest on emulator-5558/API37, explicitly
isolated with ANDROID_SERIAL; 21/21 PASS (UserPointMigrationTest 1,
UserPointRepositoryTest 6, GpxDeletionMigrationTest 1,
RoomPhysicalObjectRepositoryTest 13). UserPointValidationTest JVM 2/2 PASS.
MigrationTestHelper validates schema and compares all legacy table row sets in
the fixture; seeded Territory, Observer, physical object and READY GPX survive,
GPX structural and deletion triggers pass verification; foreign_key_check empty.
This does not qualify a physical-device migration or unseeded historical states.

The earlier saved 18/20 run preceded existing fixture corrections; current
21/21 result supersedes it. No GPX production code changed.

External research: official Room migration/testing documentation and installed
2.8.4 test/runtime artifacts; existing explicit Migration and MigrationTestHelper
mechanisms retained. https://developer.android.com/training/data-storage/room/migrating-db-versions

## Stage B — historical pre-recovery implementation/blocker

Current working diff adds the chooser user section, reuses map coordinate
confirmation and the activity-owned editor session, and opens the saved read-only
card. The form has required title, optional note, coordinates, multiple image-only
photos, validation, Back/Cancel, and staged photo cleanup. New media owner paths
are user-point-media and user-point-media-staging. The existing streaming writer,
activation/rollback, FileProvider and EXIF-aware preview are reused.
Room save finalization is NonCancellable; callbacks cannot roll back committed
photos. Terminal saved state rejects duplicate submit/cancel. No persisted draft
or process-death recovery system was added; such recovery is NOT qualified.

Production + Android-test Kotlin compilation PASS. Current JVM tests 7/7 PASS:
UserPointMediaStoreTest 3, existing PhysicalObjectMediaFileStoreTest 2,
UserPointValidationTest 2. Android API37 run: 4/9 PASS, 5/9 FAIL:
- UserPointSaveLifecycleTest 2/2 PASS: Room failure restores staged photo and retry;
  clearing the ViewModel during finalization preserves committed row/photo.
- CleanStartupIntegrationTest selected methods 2/2 PASS: new coordinate-session
  request/consume/cancel and existing physical-object map/card coordinate origin.
- UserPointCreationUiTest 0/3; existing PhotoPreviewUiTest 0/2. All five are blocked
  by the same Espresso initialization failure:
  NoSuchMethodException: android.hardware.input.InputManager.getInstance [].
  These results do not establish a product UI/EXIF regression or success.

Target emulator-5558/API37 has actual system fontScale 1.7, 1080x2280 viewport.
The available API29 emulator-5556 reports device but shell timed out again (10s).
Parallel API29 startup was rejected by Android Emulator, including attempts with
separate data/cache files and read-only mode: another emulator instance running.
No existing emulator was stopped or wiped and no SDK/test dependency was upgraded.
Stage B stops at this actual UI-test environment blocker; C/D are NOT STARTED.
No Stage B commit is made before its required checks pass. Working diff is retained.

No new UI screenshot was produced: Espresso failed before the relevant actions.
The approved overview header was re-rendered in 8ebcf06: pixel differences only
within x202..742/y29..56; everything below y60, including all six screen frames,
is identical. This is static approval evidence, not Android UI qualification.

Final current Stage B assembleDebug and lintDebug PASS (0 errors, 35 warnings, 6 hints);
focused JVM 7/7 PASS. git diff --check PASS. These do not replace blocked UI checks.
Samsung migration/UI/media/camera/fontScale/accessibility qualification NOT RUN.
Samsung was not installed, updated, cleared or otherwise modified. No push.
Stage1/H05 OPEN. Unified Backup/Restore and Android Auto Backup unchanged.

Decomposition reviewed; kept cohesive because: MainViewModel/MainActivity changes
only extend their existing navigation/selection dispatcher; MainMapScreen only
threads the new chooser callback; PhysicalObjectEditor changes only expose its
existing cohesive media section with optional label/open behavior. New editor,
route, viewmodel, media adapter and card are separate feature files.

## Historical continuation — superseded by recovery result above

Obtain a compatible, isolated UI test target and rerun the failed UI/EXIF checks
before committing B. Then implement C (list/card/map Registry integration) and D
(edit/delete), each with its own checks and local commit. Final acceptance matrix,
map restoration, multiple visibility toggles, Territory A-B-A, edit/cancel/delete,
photo lifecycle, full UI screenshots and owner Samsung qualification remain pending.
No final implementation acceptance is claimed.
Pre-existing Samsung reports and dirty handoff remain outside task commits.

## Local commits and working state

- 8329291 — OWNER approval, default ON and authoritative project docs.
- fdb2bfb — Stage A Room/domain/repository/migration tests.
- 8ebcf06 — overview PNG approval label; screen pixels unchanged.

Index empty. Stage B production/tests and this continuation evidence uncommitted.
Prior dirty handoff, Samsung GPX reports and research remain preserved.
Exact current changed/new app file list, per-class results and Git status are in
[d106-my-points-stage-b-checks.json](d106-my-points-stage-b-checks.json).
