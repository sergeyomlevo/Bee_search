# D106 My points — Stage B targeted qualification

2026-10-20. Branch `task/d106-stage0-overlay-registry`, baseline `8ebcf06`.
Stage B only. Stage1/H05 OPEN. C/D NOT STARTED. No push or Samsung operation.

Status: PASS for Stage B emulator qualification. Owner Samsung/TalkBack and real camera remain NOT RUN. C/D remain NOT STARTED.

## FileProvider diagnosis and correction

`PhysicalObjectMediaFileStore` deliberately stores capture output under cache,
but stages imported draft bytes under files. `UserPointMediaStore` reuses that
pipeline with its own namespace. The previous XML exposed only the cache staging
path and final files path; `getUriForFile` could not map files/user-point-media-staging.
The viewer caught its IllegalArgumentException and showed the observed failure toast.

Added only `<files-path name="user_point_media_draft" path="user-point-media-staging/" />`.
Existing cache capture and managed paths remain separate. No root/files/cache-wide
path or storage permission was added. Existing ACTION_VIEW uses content URI,
FLAG_GRANT_READ_URI_PERMISSION and ClipData; no file URI or new media framework.
The physical-object namespace/defaults, streaming writer and activation/rollback
mechanism remain in use. The fix does not relocate files or change cleanup rules.

External basis: [official FileProvider reference](https://developer.android.com/reference/androidx/core/content/FileProvider)
(files-path versus cache-path and temporary URI permissions), and the
[ActivityResultRegistry testing API](https://developer.android.com/training/basics/intents/result#test).
The existing framework mechanisms satisfy the requirement; no custom provider or runner.

## Runtime UI comparison

Compared approved my-points-v1 PNG+MD with actual API29 screenshots at system
fontScale 1.7, 1080x2280, density override 480. No approved artifact was edited.

| Screen/state | Expected from PNG | Observed before | Use impact / disposition |
|---|---|---|---|
| Create header | Back, title and subtitle above scroll body | Header lacked the approved subtitle/composition and scrolled with body | Structural mismatch; fixed local pinned header with subtitle |
| Coordinates | Compact six-decimal coordinates on one line | Raw Double representation wrapped | Reduced readability; fixed formatting to six decimals, runtime one line |
| Create photos boundary | Divider between location and photos | Divider absent | Grouping mismatch; added divider |
| Saved card hero | Approximately 1.8 aspect ratio | Source/runtime used 1.2 | Main element too tall; fixed to 1.8, runtime 984/547 pixels |
| Fields, controls and colors | Native Material controls; mockup Samsung palette | API29 palette, font metrics and paddings differ | Acceptable Android adaptation; no overlap, horizontal text truncation or inaccessible commands; retained |
| Photos-bottom | Camera, gallery, thumbnails, Cancel, Save | Same order after fixes; runtime has 2 fixture photos versus 3 illustrative PNG photos | No defect; data difference and ordinary vertical scrolling retained |

Current read-only B card deliberately has no list/marker/show-on-map/edit/delete
commands. Those remain C/D work and are not claimed as implemented or qualified.

## Runtime observations

- Draft and saved managed photos both opened in the installed external viewer.
  Captured grants are content URIs with read-only mode 0x1, persistable=0,
  persisted=0; Back returns to the existing form/card.
- Two gallery photos imported together through DocumentsUI and rendered as
  thumbnails. Current EXIF tests assert colors/orientation in thumbnail and
  enlarged preview for portrait orientation 6 and landscape orientation 1.
- Draft title, note and both photos survive landscape/portrait configuration
  recreation; the same text and files then save successfully into the card.
  No process-death recovery is implemented or claimed.
- Real production save opens the persisted read-only card. Managed file inventory
  contains 4 photos belonging to the 2 test points created in this qualification.
- A separate two-photo draft was cancelled via the existing Cancel action:
  staged files 2 -> 0; the 4 saved files remain. Existing Back cleanup was also
  exercised in the retained earlier recovery evidence. No original external
  image, unrelated draft or pre-existing user record was intentionally deleted.
- fontScale remains 1.7. Both bottom commands are fully reachable by ordinary
  vertical scroll; no font/density reduction used to obtain a pass.

## Evidence and verification

[Evidence folder](ui/evidence/my-points-stage-b-fix-20261020/):
`before-runtime-create-empty.png` versus `23-final-empty-form.png`;
`before-runtime-create-two-photos.png` versus `24-final-photos-bottom.png`;
`before-card-ratio.png` versus `25-final-card-hero.png`;
`29-final-draft-viewer.png`, `26-final-managed-viewer.png`, URI grants,
rotation screenshots, cleanup inventories and raw instrumentation output.
Earlier evidence/research and GPX Samsung reports are preserved.

PASS: full JVM suite 1067/1067, zero failures/errors/skips (including UserPoint media 3, PhysicalObject media 2, validation 2).
PASS: API29 core instrumentation 10/10: creation UI 3, save lifecycle 2,
EXIF preview 2, PhysicalObject media UI 1, one-shot coordinate navigation 2.
PASS: final API29 instrumentation 16/16: creation UI 3, save lifecycle 2, EXIF 2, existing PhysicalObject editor/media 6, provider/camera qualification 3. The earlier 10-test run remains separate historical evidence.
PASS: camera fake-result success and cancel via injected ActivityResultRegistry, real FileProvider output bytes, production callback, Room save and saved card. This does not prove an external camera app or hardware.
PASS: assembleDebug, assembleDebugAndroidTest and lintDebug (0 errors, 33 warnings, 6 hints); git diff --check.
Final raw outputs: api29-final-16-tests.txt and final-build-jvm-lint.log in the evidence folder. Installed emulator APK SHA-256 equals local artifact: c66bd654dfadf3b3052e6d36e3d178f860c462a29416b66ee9e5f2a4937febe8; package update 2026-10-20 18:39:49. fontScale read back as 1.7.

Real camera app/hardware, TalkBack and physical Samsung: NOT RUN.
Camera callback fake-result qualification must be reported separately from real camera.

Decomposition reviewed; kept cohesive because: MainActivity/MainViewModel only
extend their current navigation dispatcher, MainMapScreen threads the chooser
callback, and PhysicalObjectEditor exposes its existing media section with optional
label/open callbacks. The new editor, route, model, media adapter and card are
separate feature files. No unrelated split or global media redesign.
