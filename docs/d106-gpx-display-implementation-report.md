# D106 — saved GPX display implementation, 2026-10-19

Base: `574f099`, branch `task/d106-stage0-overlay-registry`.
Contract: [OWNER-approved GPX display v1](ui/mockups/gpx-display-v1.md).
Stage1 and H05 remain OPEN. Samsung deployment authorized separately by OWNER; no push.

## Scope and implementation

- Existing Tracks page opens the saved-GPX list inside the same sheet; standard
  arrow/system Back returns to Tracks. Existing scrim/swipe dismissal is retained.
- READY metadata catalog is Territory-filtered through the existing DAO, checked
  transaction and reconciliation gate. It reads no hidden canonical graph.
- Names retain displayName; missing/duplicate names use local import date and a
  deterministic presentation number as specified by the approved MD.
- Explicit selected UUID set in the existing Settings DataStore, per Territory;
  new imports OFF. Atomic write precedes UI selection and map changes. Failed
  writes retain the prior selection and retry the same user intent.
- Single cancellable producer reads only selected canonical graphs through
  `GpxRuntime.readReady`. Separate LineString per multi-sample segment, Point per
  singleton, empty segments omitted; no bridges, smoothing or original reparse.
- Immutable snapshots enter existing MapOverlay/Registry/session/BeeMap lifecycle.
  Hide unregisters; style reload creates fresh SDK objects. Scope/epoch/revision
  reject late reads and native callbacks. No automatic camera request is added.
- Persisted selection is distinct from preparation and native full-attach status;
  canonical/attach failures are reported for the specific file. Missing/corrupt
  original alone does not gate canonical display or generate a list warning.
- Direct cancellable JSON serialization avoids a second Feature/Point graph.
  Exact JSON size is checked against live JVM heap headroom before builder/String
  allocation. Allocation failures are per-file errors with an action to disable
  some files and retry. No fixed product sample/file capacity was invented.
- Room schema, parser, Coordinator, original storage, existing user import,
  research filters, My points, export and Backup/Restore were not expanded.

## Verification record

Final build/test run: **PASS**, `final-tests.log` (3m55s).
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`: PASS.
- `:app:testDebugUnitTest`: **1061 tests,0 failures**.
- Targeted connected API29 suite: **103 tests,0 failures/errors/skips**.
- Final-source `:app:lintDebug`: **PASS**, 0 errors,35 warnings,6 hints
  (`final-lint.log`,6m29s). No lint rule was disabled.

| Scenario | Evidence |
|---|---|
| Current-Territory READY only; PENDING excluded; hidden corrupt graph not read for catalog | GpxReadyCatalogDeviceTest3 + Runtime gate tests |
| New import OFF, including after another file is already ON | GpxDisplayViewModelTest |
| One/multiple ON, OFF removes runtime data without deleting catalog | ViewModel + Registry lifecycle tests |
| Selection persistence, new ViewModel and fresh DataStore reading the same persisted file | ViewModel persistence test; this is disk reopen, not a process-kill experiment |
| A→B→A, late results and same-Territory OFF→ON race | ViewModel epoch/generation tests |
| Style reload, scope return, hide/show, fresh native attach | MapOverlayRegistryLifecycleTest4, opt-in native renderer |
| Several tracks/segments, singleton Point, empty segment, exact lon/lat | GpxGeometryDisplayTest5 |
| Missing original with valid canonical geometry | GpxRuntimeDeviceTest.missingOriginalDoesNotBlockCanonicalDisplay |
| Invalid canonical geometry; native factory fails without partial source/layer; retry | Geometry + ViewModel + native lifecycle tests; research layer retained |
| Back, loading/error/empty, full names and long-list scrolling at1.7 | GpxDisplayPageTest5 + panel/session/wiring tests |
| Import and research regressions | Import UI8/VM10; research panel38/session6/wiring5; Runtime10 |

Final DEV APK SHA256:
`15d6476fe2744437b39d82f7a05cb9aea2795cc3e33cb97e25d2a7adc16b96a6`.
Version1.3.0(8), applicationId `org.beesearch.app.dev`.

Screenshots (actual API29 production composables with test-owned state):
- [List at real system fontScale1.7](../build/evidence/d106-gpx-display-implementation-20261019/d106-gpx-display/panel-font17.png)
- [Empty state at1.7](../build/evidence/d106-gpx-display-implementation-20261019/d106-gpx-display/empty-font17.png)

The long filename wraps horizontally into two lines; other filenames and switches
remain fully readable, with no original-status warning. Empty caption uses the same
body typography as list labels, matching the approved composition. API29 fallback
theme colors differ from Samsung dynamic colors; no application theme was changed.

100000-sample synthetic canonical preparation measurement (not native capacity):
1625ms,1160125 JSON bytes; JVM heap39676400→52532048 bytes (non-peak observation;
fixture graph already allocated before measurement). Previous run1772ms with a
smaller heap delta illustrates GC/allocation variability. Zero-headroom refusal
was also tested. Raw record: `d106-gpx-display/capacity.txt` in evidence.

API37 first pass: 36 non-Compose state/catalog/runtime/import tests passed.
UI/native Compose tests could not start with installed Espresso3.5.1 because
API37 lacks the reflected InputManager.getInstance method. Existing API29 AVD is
used instead; no test dependency/toolchain change was introduced.
An initial new geometry-test signature/fixture problem was corrected before the
final API29 run. Initial lint analyses crashed internally while sources were being
edited; final analysis runs against stable sources without disabled rules.

API29 target: `emulator-5556`, AVD `BeeSearch_ZIP64_API29`, actual system
fontScale1.7,1080x2340,density480. Target isolation uses `ANDROID_SERIAL`; the
physical Samsung is not a target. DEV APK applicationId checked with aapt2:
`org.beesearch.app.dev`. Existing leaveApksInstalledAfterRun=true is preserved.

Ignored local raw evidence:
`build/evidence/d106-gpx-display-implementation-20261019/`.

## Limits and physical acceptance

The JSON guard is not a proven end-to-end native/GPU capacity and does not bound
Room's initial complete graph load. The feature catches allocation failures in
selected graph preparation; actual device memory/native capacity still needs
measurement. A large canonical serialization measurement is not a native rendering
capacity guarantee. No canonical data are clipped or changed to satisfy a renderer.

Compose screenshots use test-owned state and the production panel components;
native lifecycle tests use the production Registry/session with an offline fixture
style. These are distinguished from a full production-app/Samsung acceptance.
### Authorized Samsung deployment and bounded smoke

After separate OWNER installation permission, updated only `org.beesearch.app.dev`
with `adb -s RFCY90MBYVZ install -r -t` on SM-S938B (Samsung S25 Ultra).
Version1.3.0(8), installed APK SHA256 matches the final build above;
lastUpdateTime `2026-10-19 09:33:58`. Installed/new signing certificates matched
SHA256 `2fd4f10ac24e19eb2e8c1bae08dca48b4402e751e9c33d6a33f59025b0bfb654`.
No uninstall, clear, downgrade or data reset. Before the first updated launch,
all **4075 app-private files matched exactly by path and SHA256**. First install
time and data inode unchanged. Stable/Beta package metadata unchanged.

Actual system fontScale1.7: correct main-panel order and disabled My points;
three READY rows initially OFF; two simultaneous selections; persistence after
force-stop/relaunch; arrow and system Back to Tracks; natural wrapping and
scroll to fully readable last row; existing picker open/cancel smoke PASS.
No per-file error appeared. Restored all three selections to initial OFF.
No GPX was imported or deleted by this smoke.

Actual Samsung screenshots:
- [Main panel](../build/evidence/d106-gpx-display-samsung-20261019/main-panel-font17.png)
- [Two files ON](../build/evidence/d106-gpx-display-samsung-20261019/list-two-on-font17.png)
- [Scrolled complete list](../build/evidence/d106-gpx-display-samsung-20261019/list-scrolled-font17.png)

Raw device evidence: `build/evidence/d106-gpx-display-samsung-20261019/verification.json`.
This bounded phone smoke does not repeat the emulator's Territory/style/fault
matrix or establish physical native/GPU capacity, TalkBack or field ergonomics.
The current viewport did not establish visual line placement for the saved files;
actual native line/point attach, style restore and research-overlay coexistence
are verified by the API29 offline fixture tests, not claimed as Samsung proof.

Decomposition reviewed; kept cohesive because: existing BeeMap, MainMapScreen,
MapDataPanel and AppContainer own their respective host/wiring/composition boundaries. GPX presentation,
state owner and list UI are separate focused files; no unrelated split/refactor.

Primary technology references checked before implementation:
- [Settings DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Room DAO column projection](https://developer.android.com/training/data-storage/room/accessing-data)
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
- [Pinned MapLibre Android13.5.1 GeoJsonSource](https://raw.githubusercontent.com/maplibre/maplibre-native/android-v13.5.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/style/sources/GeoJsonSource.kt)
