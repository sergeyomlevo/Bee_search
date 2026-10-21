# D106 My points — Stage D implementation and qualification

Baseline: `task/d106-stage0-overlay-registry`,
`effe8f4ff0b8a87d4c3ea08893f914cb21aaaf12`. Explicit OWNER Stage D scope;
[approved PNG+MD](ui/mockups/my-points-v1.md), [B qualification](d106-my-points-stage-b-qualification.md)
and [C qualification](d106-my-points-stage-c-qualification.md) remain references.
No push or Samsung install. Stage1/H05 OPEN.

Status: **STAGE D EMULATOR QUALIFICATION PASS**. Ready for OWNER review.
Physical Samsung/TalkBack acceptance is not claimed. Local commit only; stop
after this stage.

## Implementation

- Existing card opens the existing form in edit mode with current title, note,
  coordinates and photos. UUID-keyed editor sessions survive map selection;
  only confirmed coordinates reach the draft, and Save is the first Room update.
- Existing managed photos are retained until successful Save. Removing one in
  the form changes only the draft. New photos use existing camera/picker,
  FileProvider, previews and activation/rollback. Cancel discards only new draft
  resources. No collection count limit was added.
- Retained files must match exact owner/photo identity, canonical owner path,
  length and SHA-256 before update. New bytes activate before Room references
  them; a failed Room update rolls them back and keeps the editable draft.
  Removed old bytes are cleaned after the transaction, with separate partial
  cleanup feedback. The repository preserves identity and current visibility.
- Delete uses the approved conditional text, explicit confirmation, scrollable
  body and vertical actions. Room deletes the exact Territory/point once; FK
  CASCADE removes photo rows, and visibility disappears with the point row.
- Managed cleanup checks exact owner UUID/path and is idempotent. Session receipts
  support cleanup-only Retry without SQL replay or resurrection. The card closes
  to its existing map/list origin; the shared notice reports partial cleanup.
- No schema/migration, second media framework, GPX deletion journal, new backup,
  Android Auto Backup or research-card behavior was introduced.

## Research and review

Official [Room ForeignKey](https://developer.android.com/reference/androidx/room/ForeignKey),
[Room Transaction](https://developer.android.com/reference/androidx/room/Transaction),
and [secure sharing/FileProvider](https://developer.android.com/training/secure-file-sharing)
confirm the existing platform mechanisms. Project versions are Room2.8.4,
core1.10.1, MapLibre13.5.1; no dependency changed. Exact compatibility is checked
by this checkout's build and API29 execution, not assumed from latest web docs.
The installed standard media pipeline and existing deletion mechanisms were
inspected before choosing a narrow UserPoint adapter.

Independent read-only safety review found the narrow adapter sufficient; it
also found that retained-photo validation initially checked size without SHA.
The implementation now checks SHA-256, with same-length-corruption coverage.
No external verdict substitutes for root acceptance or runtime verification.

Decomposition reviewed; kept cohesive because: MainActivity and MainViewModel
and the existing map host/view model receive only small routing/return callbacks
within their existing responsibilities.
Media, draft state and deletion/cleanup state stay in the existing feature
boundaries and separate cohesive files, all below300 production lines.

## Verification record

- Full JVM suite repeated:1069/1069 before the final owner-directory case;
  1070/1070 after it and again after the navigation fix, all0 failures/errors/skips.
  Final XML summary: `jvm-post-navigation-summary.json`.
- The previously unstable `RepositorySnapshotsTest.capacityDropAfterStagingNeverPublishes`
  passed in the repeated full suites and in its separate focused run
  (`backup-focused.txt/xml`). The historical C failure was not reproduced; no
  Backup/Restore source was changed.
- Post-fix production/test APK assemblies and lint passed in
  `build-jvm-lint-post-navigation.txt`; lint0 errors,33 warnings,6 hints.
- API29 final combined run: **115/115 PASS** (`api29-final-115.txt`).
  Additional populated-isolation test was then added without production changes;
  its complete lifecycle class passed **8/8** (`api29-populated-isolation-lifecycle-8.txt`),
  so116 unique Android tests passed across these final runs. The test APK was
  reinstalled alone; `additional-test-artifact.json` records its new SHA.
- Native Save/Cancel/delete/source-origin screenshots and exact data checks are
  below. The added populated-test assembly/lint also passed
  (`build-populated-isolation-test.txt`, `lint-populated-isolation-final.txt`),
  again0 errors,33 warnings,6 hints.
- `final-data-preservation.json`: all23 baseline table fingerprints unchanged;
  original3 points/4 photo rows and all4 managed SHA hashes match; external
  sources match; no disposable managed files or draft staging remain. Exact
  installed production APK/source identity also matches the qualified build.
- `runtime/restart-verified.json` and native32/33 captures confirm cold restart
  shows the ordinary map and original list, with no deleted point/card/editor
  replay. `preserved-workspace-before-handoff.json` confirms all31 preexisting
  dirty files are unchanged before the additive end-of-stage handoff update.
Evidence: [Stage D folder](ui/evidence/my-points-stage-d-20261021/).
The baseline has3 existing point rows and4 owned photo files. Workspace hashes
cover the prior handoff, GPX reports, research and earlier untracked B evidence.

## Limits

- Cleanup receipts survive navigation/recreation in the current Activity process;
  durable retry after process death is not implemented by the existing lifecycle.
  A deleted point stays deleted, but unreferenced managed bytes can remain if
  cleanup fails or the process dies. No new recovery system is claimed.
- Physical Samsung, TalkBack and hardware camera/GPS verification are NOT RUN.
- Arbitrary process kill during new-photo activation / rollback, ENOSPC and
  provider failures are not qualified unless explicitly recorded below.


## Runtime finding during qualification

The first production Cancel round trip preserved the exact point/photo rows,
managed hashes and cleaned only the two new staged resources. However, opening
coordinate selection closed the existing map-data sheet, so Back from the saved
card returned to the map instead of its source list. The failure is retained in
runtime/13-failed-card-back-returned-map PNG/XML. The fix carries an explicit
LIST/MAP origin through the card/editor target and restores the source through
the existing shared MapDataViewModel on explicit Back/Delete. No durable reopen
flag or extra route/storage was introduced. Source and session regression checks
were added. Post-fix JVM1070/build/lint and two focused navigation/session tests
passed. Native runtime/19-post-fix-return-list proves the corrected return after
map Back, editor Cancel and card Back; runtime/26-delete-return-list and
runtime/30-deleted-marker-map prove both deletion origins.


## Native runtime evidence

API29 `emulator-5556`, actual `fontScale=1.7`; DEV package only. The final
installed APK SHA-256 is
`af3f27cdb4ef30a4207b2f9d9f101503409d41be3128ae15d0ea83c99b3283a2`.
`installed-artifacts-post-navigation.json` links that binary to the production
source hashes. Samsung and API37 were excluded from all write commands.

Two disposable points were created through the existing production chooser.
The original three points were never edited, toggled or deleted.

- [Draft after map OK](ui/evidence/my-points-stage-d-20261021/runtime/22-draft-after-map-ok.png):
  title, note, three photos and new coordinates survive the map round trip.
- [Edit bottom](ui/evidence/my-points-stage-d-20261021/runtime/23-draft-three-photos.png):
  existing multi-photo strip, vertical Cancel/Save and scrolling at fontScale1.7.
- [Saved card](ui/evidence/my-points-stage-d-20261021/runtime/24-saved-card.png):
  same UUID/visibility/creation time, changed fields and three verified media files.
- [Delete with photos](ui/evidence/my-points-stage-d-20261021/runtime/25-delete-confirmation.png),
  [without photos](ui/evidence/my-points-stage-d-20261021/runtime/29-delete-no-photos.png):
  approved conditional copy and full-width stacked actions remain readable.
- [List after deletion](ui/evidence/my-points-stage-d-20261021/runtime/26-delete-return-list.png),
  [marker before deletion](ui/evidence/my-points-stage-d-20261021/runtime/27-map-origin-marker.png),
  [map after deletion](ui/evidence/my-points-stage-d-20261021/runtime/30-deleted-marker-map.png).

Root compared native runtime captures with approved F edit-top/edit-bottom/delete
PNGs and the MD contract: existing form/card composition is retained; large text
uses scrolling and vertical actions. UIAutomator XML accompanies native PNGs.
Component captures cover long-title, IME and failure states separately. Their
cached static header can be absent from UIAutomation captures; native production
captures are the visual composition evidence. These are emulator observations,
not physical OWNER visual approval.

`runtime/cancel-verified.json` proves exact old point/photo rows and managed SHA
survived draft photo removal/addition, changed fields, map pan/Back and Cancel;
only the two new staged resources were removed. `runtime/save-before-room-unchanged.json`
checks original Room/files immediately before Save. `runtime/save-verified.json`
records the committed fields, retained photo identity, two new photos and removal
of the old managed copy. `runtime/delete-confirmation-verified.json` records that
Cancel kept the saved row and explicit confirmation returned to the source list.
ADB `run-as test` was unavailable during an evidence assertion; directory listing
was used to verify the removed file. This was a tooling check failure, not an app
failure. No raw database copy is stored in this report.

## Acceptance coverage

| Requested checks | Evidence and scope |
| --- | --- |
| 1–4: title/note, map OK/Back, draft retention | Native production round trips; StageDUiTest coordinate revision; CleanStartupIntegrationTest session/origin/no replay |
| 5–8: multiple photos, remove, Cancel, Save composition | Native batch picker and SHA/Room checks; EditDeleteLifecycleTest deferred removal, Cancel and failed Save/retry |
| 9: EXIF | Existing PhotoPreviewUiTest portrait EXIF rotation in thumbnail/enlarged view; existing provider pipeline retained |
| 10: failed Save | Injected repository update failure preserves Room/original bytes and restores new staging; retry commits |
| 11–14: confirm/cancel, visible/hidden, marker/visibility | Native list/map origins and marker removal; Room visible+hidden delete/reopen; StageDUiTest SQL failure/confirmation |
| 15: unrelated data | Repository identity/FK isolation tests; final baseline fingerprints and file hashes recorded separately |
| 16: cleanup failure | Injected/malformed owner path cannot remove foreign bytes; UI pending notice, cleanup-only Retry and no second SQL delete; owner-directory symlink regression |
| 17–18: restart, Back/Cancel/no replay | Room close/reopen tests, MainViewModel session test, MapDataViewModel source restoration and native cold restart |
| 19–20: large text/scroll/IME/semantics | API29 fontScale1.7 native screenshots and Compose semantic action assertions; physical IME/TalkBack NOT RUN |
| 21: A/B/C | Combined targeted suite includes repository/migration/creation/media/list/visibility/map and GPX panel regressions |

The preservation check's GPX/research tables are empty on this emulator baseline;
matching fingerprints alone do not prove populated real field-data preservation.
The additional isolated lifecycle test populates GPX, ObservationPoint/Bee/FlightCycle,
Hollow/LogHive rows and unrelated bytes before deleting a UserPoint; it passed as part of the8/8 lifecycle run. No Samsung field-data or real GPX deletion
experiment was performed.


## Qualification failures retained

Initial source/test compilation failures were corrected before qualification.
The first targeted Android run was17/18: the existing camera success test waited
for the card container, which exists before asynchronous title loading. Its
focused repeat passed; the test now waits for the actual loaded title and retains
the display assertion. Later combined runs cover it again. Lint first hit an
internal FIR owner exception, then reported a test-only unremembered FocusRequester;
remembering it fixed the actionable error. Failure logs remain in the evidence
folder. Terminal-log trailing spaces were normalized for Git whitespace checks;
test output and results are retained. The production list-origin defect and its runtime fix are described above.
None of these failures is omitted from the final result history.


## Completion boundary

The D commit contains feature code, targeted regression tests, architecture/index
and implementation reports, and only the new Stage D evidence directory. The
preexisting dirty handoff, GPX Samsung reports, My points research and earlier
untracked Stage B evidence are excluded. The handoff receives an additive D
summary while its previous contents are preserved. `git diff --check` is required
on the final staged set before commit. No checkout/reset/stash, schema change,
push, Samsung install, GPX feature change or Backup/Auto Backup work was performed.
Stage1/H05 remain OPEN. Further feature work stops here for OWNER review.
