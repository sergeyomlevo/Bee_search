# Map overlay lifecycle — durable device evidence

> Current D106 Stage0 verification (2026-10-12): **STAGE0_VERIFICATION_PASS within the declared
> emulator + Samsung scope.** Samsung ONLINE and AreaEditor recreation methods PASS; emulator
> retains its known pre-Stage0 blocker. See Samsung physical closure for scope and remaining UNKNOWNs.

> Previous emulator-only D106 Stage0 closure (2026-10-12):
> **EMULATOR_VERIFICATION_PASS_WITH_KNOWN_PREEXISTING_NATIVE_BLOCKER / SAMSUNG_VERIFICATION_GO.**
> ONLINE and the known AreaEditor recreation method remain BLOCKED, not PASS; physical verification
> is NOT PERFORMED. Earlier NO-GO attempts are historical. See the final independent-gates section
> of lifecycle evidence for current results and the explicit unverified assertions.

Purpose: retain the evidence behind owner acceptance of `architecture.md` §73.3 and
[D102](decisions.md#d102--map-overlay-rendering-split-и-lifecycle-safety), independently of disposable
build reports. This is a textual observation summary, not production implementation or a benchmark.

## Context and provenance

- Date: 2026-10-08; Samsung SM-S938B, Android 16, system fontScale 1.7.
- MapLibre Native Android 13.5.1; Bee Search baseline
  `8f0ee48e04eb6226e3e4c7b1374a3a1bd53c78ac`.
- Only `org.beesearch.app.dev` / its test APK, update in place; no field/beta changes.
- Read before acceptance: `app/build/reports/unified-map-overlay-spike/report.md`,
  its `device/events.txt`, `app/build/reports/unified-map-overlay-closure/report.md`, and the final
  closure `device/unified-map-overlay-closure/events.txt`. Raw logs/screenshots remain ignored.
- Version-pinned primary-source context:
  [MapLibreMap.java](https://github.com/maplibre/maplibre-native/blob/android-v13.5.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/maps/MapLibreMap.java),
  [Style.java](https://github.com/maplibre/maplibre-native/blob/android-v13.5.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/maps/Style.java).

## First spike: style ownership and restore

Temporary androidTest added one GeoJSON point Source and a conspicuous CircleLayer to the existing
map. UI switched Online → Vector → Online → Sentinel → Hybrid → Online. On all five raw transitions,
the loaded new Style lacked the runtime Source/Layer; old Style access was invalid. Screenshots and
rendered-feature checks confirmed disappearance, then visibility after explicit recreation with fresh
SDK objects. Loaded-listener restoration also succeeded through repeated transitions of all four
profiles. SDK offline connectivity override exercised existing local Vector/Sentinel/Hybrid packages;
device radios were not disabled. Screen-fixed Compose target survived, but was not research-marker
projection evidence.

A separate deliberate duplicate-add attempt reported duplicate Source/Layer SDK exceptions, followed
by asynchronous native SIGSEGV before any basemap switch. Exact native causal attribution remains
unknown; it was not a demonstrated setStyle race. This is sufficient crash-risk evidence to prohibit
exception-driven duplicate handling. Closure did not repeat this experiment.

## Closure: superseded request context

Two inline JSON setStyle calls completed synchronously. A test-only localhost server then held an
actual SDK request for synthetic style A (generation 3), proving A remained pending. B (generation 4)
superseded A before its response was released. B setter received current fully-loaded B and restored
once after absence checks. A queued getStyle callback registered under A arrived after B setter,
receiving current B Style with stale captured A generation/profile. Guard rejected it before SDK add.
A setter was suppressed; after releasing A, B remained final current Style.

Current Style alone is therefore insufficient request identity for deferred/request-scoped work.
Generation/request identity must be stored before setStyle and revalidated before mutation. No stale
production callback was detected in the four current local-JSON profiles; their ordinary loads were
very fast. The controlled scenario supports a preventive invariant, not a claim of a user-facing bug.

## Closure: production coordinate-projected Compose markers

Five existing ObservationPoints used the production `SavedObjectMarkersOverlay` /
`projection.toScreenLocation()` path, without record edits. They remained visible and geographically
anchored through Online → Vector → Sentinel → Hybrid → Online. After camera moves/zoom, measured
screen positions changed consistently with current geographic projection; screenshots, semantics and
inverse-projection checks supported this result. They needed no MapLibre Source/Layer restoration.
Comparisons partly share the production projection mechanism; this is lifecycle/anchoring evidence
for these markers, not independent proof of projection mathematics or large-set performance.

## Data safety and scope

First spike preserved recorded settings/binding and map-package hashes; it did not audit database
contents. Closure pre/post hashes matched for seven persistent files including DB/WAL/SHM,
settings/binding and vector/Sentinel packages. Counts and canonical row hashes of all 14 database
tables matched. No evidence of research-data writes. No uninstall, data clear, map-package change or
backup operation. Temporary androidTest probes were removed; production source was unchanged.

## Owner conclusion and remaining verification

**Owner accepts §73.3 on 2026-10-08**, based on both spikes and subsequent review: MapLibre for
style-owned/bulk/static content; Compose for interactive research/UI overlays. D102 accepts the
generation/current-loaded-Style contract, owned ID namespace, typed/fail-closed unexpected collision,
pre-add duplicate prevention and deterministic Sources-before-Layers ordering. Synthetic generation
and ID policy are accepted requirements, not implemented production features.

OPEN implementation/device verification remains: Samsung synthetic 200–1000 Compose markers
(frame time/gestures/projection cost, no preselected limit); style failure/cancellation consistency;
recreation/return-to-map and stale work; bounded restore frame budget; basemap-specific layer anchors;
transition/flicker UX; regression checks after substantial MapLibre upgrades. These do not block
acceptance. Separate Samsung marker visual pass must verify shape-first type identity, selected type
preservation and vector/Sentinel/raster/Hybrid contrast. Exact icons/colours/sizes are not approved.

## D106 Stage 0 — 2026-10-12

**IMPLEMENTED / AUTOMATED GATES PASS / PHYSICAL SAMSUNG VERIFICATION PENDING.**
Owner-approved implementation from `a8939f504219d5133f54b1a9bfd958f7587d8083`, independent clone
`C:\App\Bee_search_stage0_overlay_registry`, branch `task/d106-stage0-overlay-registry`.
No physical-device operations, release checkout changes or Stage 1 implementation.

### Request correlation evidence

Callback/listener correlation uses a unique app-owned marker in the standard non-rendering root
metadata property `beesearch:overlay-request`. This is app request identity, not SDK-provided
request identity. Generation and marker are stored before setStyle, including synchronous paths.
Both signals use actual/current Style identity, loaded state and the marker read from Style.json.
Stale setter callbacks cannot perform cleanup or restore. Unscoped loaded/failure events can clean
only retired journal resources whose actual attachment/ownership is still provable.

Primary sources inspected for Android 13.5.1:

- [Root metadata specification](https://maplibre.org/maplibre-style-spec/root/#metadata): metadata does not influence rendering.
- [Style::Impl](https://github.com/maplibre/maplibre-native/blob/android-v13.5.1/src/mbgl/style/style_impl.cpp): load stores `json = json_`; getJSON returns that string.
- [Android JNI](https://github.com/maplibre/maplibre-native/blob/android-v13.5.1/platform/android/MapLibreAndroid/src/cpp/native_map_view.cpp): getStyleJson returns native Style.getJSON.
- [MapLibreMap.java](https://github.com/maplibre/maplibre-native/blob/android-v13.5.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/maps/MapLibreMap.java): setter clears old Style; callback/getter coordination is not per-request SDK identity.

The original opt-in native test checked Style name readback across A -> B -> A,
fresh SDK Source objects and exactly one fixture Source/Layer per Style. Existing metadata is
preserved; an existing reserved marker causes a refusal rather than overwrite. Resource IDs,
Sources/Layers, camera settings and basemap composition are unchanged by the marker.

### Automated evidence

Baseline: `:app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
passed before source edits: 897 tests, 0 failures/errors/skipped. No prior-run count was reused. The baseline count was recorded during that run; its XML was not archived separately and was overwritten by subsequent runs. Final 927/927 is supported by XML; the delta of 30 Registry/correlation test methods is confirmed.

Final batch (Gradle 9.5.0, isolated task-local Gradle/Android user homes and temp directories):
`:app:testDebugUnitTest :app:compileDebugKotlin :app:compileBetaKotlin :app:compileReleaseKotlin
:app:assembleDebug :app:compileDebugAndroidTestKotlin :app:assembleDebugAndroidTest :app:lintDebug`
passed. JVM: 927 tests, including 30 Registry/correlation tests, 0 failures/errors/skipped.
Lint: 0 errors, 44 warnings, 6 hints; new LogNotTimber warnings follow the existing Android Log
pattern and add no dependency. Full JVM also exercises existing map/Area/display/location/heading logic.
An offline packaging attempt lacked a cached coroutines-test artifact; it was downloaded only into
the task-local cache. Interim compilation/test assertion failures were corrected; none remain.

Native: `ANDROID_SERIAL=emulator-5554`, Android 10/API29 `BeeSearch_ZIP64_API29`.
Only `org.beesearch.app.dev` and `.dev.test`; artifact application IDs checked before the run.
`:app:connectedDebugAndroidTest` with runner arguments
`class=org.beesearch.app.ui.map.MapOverlayRegistryLifecycleTest`, `beeOverlayRegistry=true`:
3 tests, 0 failures/errors/skipped/aborted, PASS. Connected target isolation and the existing
leave-APKs-installed policy were retained. No device PoC/import suite or destructive cleanup ran.

Tests cover standalone SDK restoration/visibility/scope/collision and actual BeeMap FIELD ->
disposal -> AREA_VIEW recreation. They do not prove all field workflows, GPS/sensor behavior,
profile-specific visual ordering, large-data frame budget or physical ergonomics.
JVM proves out-of-order request/data completion; a deliberately delayed SDK/network request
was not exercised and remains separate SDK verification debt.

Local artifacts: `build/evidence/baseline.log`, `final-gates.log`, `native-gate.log`, JVM XML under
`app/build/test-results/testDebugUnitTest`, native XML under
`app/build/outputs/androidTest-results/connected/debug`. These logs are ignored artifacts,
not the sole durable source of this summary. DEV APK SHA-256:
`13296FFD2C04AD0A3F40312F15422D90B072771E35A678E95A35F96279CFC286`.

Independent read-only Luna review identified callback correlation and retired-scope cleanup issues;
root corrected them and added regression tests. Re-review found no blocking production issue.
Physical Samsung verification was explicitly prohibited in this task and is not closed here.

## D106 Stage 0 — targeted emulator closure attempt (2026-10-12)

**BLOCKED — ACTION REQUIRED / NO NEW COMMIT / SAMSUNG VERIFICATION: NO-GO.**
Starting HEAD `1e88fe8765f1ab7a32ae6aeff129635d2d936185`, parent `a8939f504219d5133f54b1a9bfd958f7587d8083`.
The test/docs-only authorization does not permit a production fix. Production sources, dependencies,
persistence, signing and versions remain unchanged. No Samsung operation or push was performed.

### Runtime contract clarification

`SESSION_UNSAFE` belongs to a particular Style attachment. Ordinary validation/collision/factory
failure, or partial attach followed by successful cleanup, is isolated to that overlay. Unknown
ownership, a mutation that throws after inserting an unjournaled resource, or failed cleanup freezes
further mutations of that attachment for all overlays. Latest desired state survives; a newly
confirmed Style creates a fresh attachment and can restore it. Release invalidates the host and
ends the session. Diagnostics are typed outcomes, Android logs and `registry.failures`; Stage 0
has no UI error indicator. Existing JVM regression
`targetMutationThatAddsThenThrowsMakesSessionUnsafeAndLeavesUnjournaledResource` already covers
unsafe attachment -> blocked mutation -> new confirmed Style -> successful restore. It passed in
the current full JVM suite; no duplicate recovery test was added.

Style correlation requires patchable local JSON supplied by mandatory `BeeMapProfile.styleJson`.
All four present production factories satisfy this contract. URI/non-JSON styles are not represented
by the current profile contract; future URI support is outside Stage 0. Native `Style.getJson()`
readback and real source/layer parity are assertions in the new opt-in profile test, but that test
has NOT RUN because the earlier regression gate crashed.

### Reproducible commands and isolated environment

Executed from `C:\App\Bee_search_stage0_overlay_registry` in PowerShell:

```powershell
$taskRoot=(Get-Location).Path
$env:GRADLE_USER_HOME="$taskRoot\build\gradle-home"
$env:ANDROID_HOME='C:\Users\user\AppData\Local\Android\Sdk'
$env:ANDROID_USER_HOME="$taskRoot\build\android-user-home"
$env:TEMP="$taskRoot\build\tmp"
$env:TMP=$env:TEMP
$env:ANDROID_SERIAL='emulator-5554'
$gradle='C:\Users\user\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat'
& $gradle --offline --no-daemon '-Djava.io.tmpdir=C:\App\Bee_search_stage0_overlay_registry\build\tmp' '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:compileDebugKotlin :app:compileBetaKotlin :app:compileReleaseKotlin :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

All eight tasks PASS. Final batch log: `build/evidence/closure/build.log` (1m30s).
Current XML: 927 tests, 0 failures, 0 errors, 0 skipped; Registry/correlation 30 methods.
Lint: 0 errors, 44 warnings, 6 hints. Three LogNotTimber warnings point to
`MapLibreOverlaySession.kt`; BeeMap has ModifierParameter/LogNotTimber warnings and two
AutoboxingStateCreation hints. A prior handoff's 42 warnings was not a strict lint baseline on
the same baseline commit; a regression of +2 warnings is NOT proven. No lint/config change made.

Before native runs, `adb devices -l` listed only `emulator-5554`; its kernel qemu property was 1.
Device: `BeeSearch_ZIP64_API29`, Android 10/API29, x86_64. APK IDs were checked as
`org.beesearch.app.dev` and `org.beesearch.app.dev.test`. Every command set ANDROID_SERIAL explicitly.
No uninstall/data-clear operation was used.

Executed commands (using the environment above):

```powershell
& $gradle --offline --no-daemon '-Djava.io.tmpdir=C:\App\Bee_search_stage0_overlay_registry\build\tmp' '-Pkotlin.compiler.execution.strategy=in-process' '-Pandroid.testInstrumentationRunnerArguments.class=org.beesearch.app.MapViewLifecycleControllerTest' :app:connectedDebugAndroidTest
& $gradle --offline --no-daemon '-Djava.io.tmpdir=C:\App\Bee_search_stage0_overlay_registry\build\tmp' '-Pkotlin.compiler.execution.strategy=in-process' '-Pandroid.testInstrumentationRunnerArguments.class=org.beesearch.app.ui.map.AreaEditorEntryUiTest' '-Pandroid.testInstrumentationRunnerArguments.beeOverlayRegistry=true' :app:connectedDebugAndroidTest
```

Pending opt-in commands, NOT executed in this closure attempt:

```powershell
& $gradle --offline --no-daemon '-Djava.io.tmpdir=C:\App\Bee_search_stage0_overlay_registry\build\tmp' '-Pkotlin.compiler.execution.strategy=in-process' '-Pandroid.testInstrumentationRunnerArguments.class=org.beesearch.app.ui.map.MapOverlayProductionProfilesTest' '-Pandroid.testInstrumentationRunnerArguments.beeOverlayRegistry=true' :app:connectedDebugAndroidTest
& $gradle --offline --no-daemon '-Djava.io.tmpdir=C:\App\Bee_search_stage0_overlay_registry\build\tmp' '-Pkotlin.compiler.execution.strategy=in-process' '-Pandroid.testInstrumentationRunnerArguments.class=org.beesearch.app.ui.map.MapOverlayRegistryLifecycleTest' '-Pandroid.testInstrumentationRunnerArguments.beeOverlayRegistry=true' :app:connectedDebugAndroidTest
```

For remaining existing tests use the same targeted command with the exact FQCN below. The
`MapMarkersTest.kt` file declares `org.beesearch.app.MapCenterTargetTest`; it is not silently
substituted by a different marker test.

| Class | Planned | Started/completed XML | Passed | Failures | Errors | Skipped | Aborted/uncompleted | Wall seconds | Result |
|---|---:|---:|---:|---:|---:|---:|---|---:|---|
| org.beesearch.app.MapViewLifecycleControllerTest | 4 | 4 | 4 | 0 | 0 | 0 | 0 | 40.369 | PASS |
| org.beesearch.app.ui.map.AreaEditorEntryUiTest | 6 | 2 | 1 | 1 | 0 | 0 | run aborted; 4 uncompleted | 82.658 | ABORTED / XML failure |
| org.beesearch.app.ui.map.MapDataWiringTest | 5 | 0 | 0 | 0 | 0 | 0 | not started | — | NOT RUN |
| org.beesearch.app.ui.map.MapResearchObjectsQueryTest | 3 | 0 | 0 | 0 | 0 | 0 | not started | — | NOT RUN |
| org.beesearch.app.MapCenterTargetTest | 2 | 0 | 0 | 0 | 0 | 0 | not started | — | NOT RUN |
| org.beesearch.app.MapCoverageSelectionUiTest | 12 | 0 | 0 | 0 | 0 | 0 | not started | — | NOT RUN |
| org.beesearch.app.ui.map.MapOverlayProductionProfilesTest | 4 | 0 | 0 | 0 | 0 | 0 | not started | — | NOT RUN |
| org.beesearch.app.ui.map.MapOverlayRegistryLifecycleTest (enhanced) | 3 | 0 | 0 | 0 | 0 | 0 | not started | — | NOT RUN |

Constructor mismatch was confirmed: the private production controller takes Function1, not a
zero-argument constructor. Test-side reflection now passes the lambda. All original four lifecycle
assertions remain; beforeRelease is additionally checked once and before onDestroy. XML suite time
0.211s (aggregate run 0.614s). This test uses RecordingMapView; it is not native-renderer proof.

### Native blocker evidence

AreaEditorEntryUiTest's `addingAFragmentReturnsToTheFreeMapAndCancelingTheNextKeepsIt` passed.
`aFinishedEditorSessionNeverReopensOnALaterVisit` started, then process 31791 crashed at
2026-10-12 16:21:38 UTC while the test exercises returning/recreating map instances. XML records
one empty failure, not a Kotlin assertion stack, and `Instrumentation run failed due to Process crashed`.
Aggregate XML time 65.145s; class time 52.354s. Four other methods were not completed.

```text
Fatal signal 11 (SIGSEGV), SEGV_MAPERR, fault addr 0x0, tid 31991 (RenderThread 95)
#00 libmaplibre.so mln::android::MapRenderer::render(_JNIEnv&)+151
#09 org.maplibre.android.maps.renderer.MapRenderer.onDrawFrame
#15 MapLibreVulkanSurfaceView$VulkanThread.guardedRun
#18 MapLibreSurfaceView$RenderThread.run
BuildId: 097847da4cc01ece0bc5525282c208b27782fdee
```

Evidence retained immediately after each run:
`build/evidence/closure/MapViewLifecycleControllerTest.log`, class-named XML/report directory and
`MapViewLifecycleControllerTest-logcat.txt`; equivalent AreaEditorEntryUiTest artifacts including
full native crash backtrace in `AreaEditorEntryUiTest-logcat.txt`. These are ignored local artifacts,
not staged binaries. The SIGSEGV occurred on the real BeeMap renderer path, but the stack alone
cannot establish whether Stage 0 lifecycle or emulator/native instability caused it. This is a
verification BLOCKER; no production defect or environment-only explanation has been proven.
No repeated native attempts were made, and subsequent native gates stopped as required.

All four new real-profile checks (ONLINE, ACTIVE_VECTOR, SENTINEL, HYBRID) compile but are NOT RUN.
They call the actual factories, use test-only empty PMTiles v3 archives when needed, and assert native
metadata, exact source/layer ID sets, original source/layer JSON structure, hidden visibility,
fresh SDK objects after reload and stale-work refusal. Empty archives intentionally do not establish
tile coverage/render completeness. Actual BeeMap startup signals/recreation instrumentation was
added but NOT RUN; the previous fixture's 3/3 result is prior evidence, not a pass for this revision.

Independent read-only Luna review found no blocker/high finding in the test diff; native execution
and closure remain pending. No new commit may be created while this unexplained native blocker
remains. Next owner decision: authorize bounded crash diagnosis/baseline comparison if desired;
do not fix production or bypass the gate within this narrow task. Samsung, visual/performance,
large-marker budget and delayed SDK-request verification remain open.

## Bounded native crash diagnosis — 2026-10-12

**Classification: PREEXISTING_OR_ENVIRONMENTAL_SUPPORTED.** This supersedes UNKNOWN attribution
in the preceding closure attempt; it does not turn crashed runs or unexecuted gates into PASS.
No production fix is justified by this comparison. No source/test edit, dependency/graphics/image
change, secondary variation or new commit was performed in the diagnosis.

### Exact states and controlled execution

Stage 0: `1e88fe8765f1ab7a32ae6aeff129635d2d936185`.
Comparison: `a8939f504219d5133f54b1a9bfd958f7587d8083`, its immediate parent and documented
pre-Stage 0 Beta5 baseline. Neither moving main nor a guessed earlier revision was used.
Exact commit snapshots were extracted by git archive into ignored `build/diagnosis-sources/{stage0,baseline}`;
current uncommitted test/docs work remained intact and did not enter either snapshot.
AreaEditorEntryUiTest has identical Git blob `a207e9f612e252caf23ec0ae3446bbc5babe7f12` in both states.
AreaEditorSession, Gradle/dependencies and MapLibre version have no difference between these commits.

Both snapshots built with the same command and existing Gradle 9.5.0 executable, offline/no-daemon,
in-process Kotlin, common task-local GRADLE_USER_HOME/ANDROID_USER_HOME/TEMP/TMP/java.io.tmpdir:
`:app:assembleDebug :app:assembleDebugAndroidTest`. Builds PASS (Stage 0 2m05s; baseline 2m04s).
No builds ran concurrently with either native test. Both APK pairs were Debug/dev+dev.test, installed
with adb install -r from the same fixed paths under `build/evidence/diagnosis/runner`:
`app-debug.apk`, `app-debug-androidTest.apk`. No pm clear, uninstall or database reset was performed.
Before each run, only `am force-stop org.beesearch.app.dev` and `am force-stop org.beesearch.app.dev.test`
reset processes. Persistent emulator DEV data/cache was retained. Test state itself is newly created
per-process with FakeAreaStore and NoMapPackageStore; it performs no user package import.

Same emulator-5554, API29/x86_64, unchanged graphics/settings and fingerprint:
`google/sdk_gphone_x86_64/generic_x86_64:10/QSR1.211112.011/13135432:userdebug/dev-keys`.
Only emulator was listed before each run and every adb command selected it explicitly.
Both APKs contain byte-identical x86_64 libmaplibre.so (13.5.1), SHA-256:
`5587B6592C8EACF10202647E1BA70CB32D6C24AE4FF1C4CCF5DC07A8056D8570`.

Exact execution command for BOTH states:

```powershell
& $adb -s emulator-5554 shell am instrument -w -r -e class 'org.beesearch.app.ui.map.AreaEditorEntryUiTest#aFinishedEditorSessionNeverReopensOnALaterVisit' -e beeOverlayRegistry true org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner
```

Direct adb transport was matched between comparison states; the preceding closure had used Gradle
connected transport. Runner readback confirms exactly numtests=1 in both diagnostic runs. adb exit 0
is NOT test success: both report `shortMsg=Process crashed.` No Kotlin assertion stack exists.

| State/run | Result | Signal | Thread | Top native frame | Crash timestamp UTC | Wall seconds |
|---|---|---|---|---|---|---:|
| Stage 0 / 1 | PROCESS_CRASH | SIGSEGV / SEGV_MAPERR / null | RenderThread 91 | MapRenderer::render(_JNIEnv&)+151 | 2026-10-12 16:51:53.429 | 28.390 |
| Baseline / 1 | PROCESS_CRASH | SIGSEGV / SEGV_MAPERR / null | RenderThread 92 | MapRenderer::render(_JNIEnv&)+151 | 2026-10-12 16:52:26.951 | 28.487 |

Same native PC `0x596ca7`, BuildId `097847da4cc01ece0bc5525282c208b27782fdee`, renderer/SurfaceView/
VulkanThread stack family and same isolated test scenario. The prior closure crash also matches.
Both runs crash about 25 seconds after method start; baseline logs repeated style loads/SurfaceView
activity before the crash. Exact recreation iteration and release-vs-render ordering are not logged.
One execution per state is mechanically decisive under the owner rule: baseline itself reproduces
the matching failure family without Stage 0. Runs 2/3 and optional secondary variation were not used.
This does not distinguish a preexisting app/native lifecycle defect from emulator-specific instability,
or measure whether Stage 0 changes recurrence probability.

### Bounded lifecycle/diff map

| Changed behavior | Possible relation | Evidence for relation | Evidence against attribution to Stage 0 |
|---|---|---|---|
| Both setStyle paths now use marker-correlated session | changes request/completion timing | real BeeMap failing scenario traverses these paths | baseline direct setStyle crashes at identical native frame; double initial paths already existed |
| Session listeners + beforeRelease hook before onDestroy | adds teardown work/timing | recreation scenario includes disposal; ordering visible in source | controller identity guard still blocks duplicate release; matching baseline crash without the hook |
| Released-host guard in getMapAsync and session ownership invalidation | changes late callback handling | source explicitly rejects disposed host work | no late callback/reuse violation in bounded logs; these guards are absent in matching baseline crash |
| Registry runtime resource ownership | could affect native source/layer lifetime when registered | none in this test: default runtimeOverlays is empty | no fixture overlay/resource attach in failing method; baseline has no Registry and still crashes |

Existing RecordingMapView lifecycle tests (4/4 from closure) verify once-only callbacks and
beforeRelease-before-onDestroy but do not establish native render/dispose ordering. Existing crash
logs do not mechanically show double destroy/release, unexpected overlapping host generations,
resource reuse after disposal, render-after-release or duplicate startup request identity. Multiple
RenderThreads/style loads alone are expected in recreation and do not prove an overlap defect.
No production logging was added. A Stage-0-specific causal violation remains unsupported.

Evidence: `build/evidence/diagnosis/{stage0,baseline}-build.log`, `native-hashes.json`, and
`{stage0,baseline}-run1/{instrumentation.txt,logcat.txt,result.json,devices.txt,fingerprint.txt,apk-hashes.txt}`.
These are ignored local artifacts. After comparison, emulator DEV/test APKs are the baseline pair;
future Stage 0 verification must explicitly install its intended APK pair again. Samsung/main/release
checkout were not modified. Previously uncommitted test/docs changes remain, with empty index.

**SAMSUNG_REMAINS_NO_GO:** attribution is classified, but the previously required four production
profile and remaining BeeMap closure gates are still NOT RUN; this diagnosis does not waive them.
One next owner action: decide how the proven pre-Stage0 native blocker is accounted for in the
remaining emulator closure gate. No implementation correction is proposed or performed.

## Remaining emulator gates after owner crash classification — 2026-10-12

**EMULATOR_VERIFICATION_FAIL / SAMSUNG_VERIFICATION_NO_GO.**
Owner classified the already baseline-reproduced AreaEditor recreation failure as
`BLOCKED_BY_PREEXISTING_NATIVE_FAILURE`, not PASS and not Stage-0 FAIL. That method was excluded
and NOT rerun. The known-family evidence is recorded once in the bounded diagnosis section above;
root cause remains preexisting app/native defect OR emulator-specific instability — UNKNOWN.

Stage0 production remains exact `1e88fe8765f1ab7a32ae6aeff129635d2d936185`, branch
`task/d106-stage0-overlay-registry`, with no production/config/dependency diff. Current androidTest
APK includes the previously authorized test-only constructor/fixture/profile changes. All eight
JVM/compile/assemble/lint tasks were repeated using the established task-local environment, PASS
(50s): JVM927/927, 0 failures/errors/skipped; lint0errors44warnings6hints. No new test/source edit
was made in this attempt. The prior baseline emulator APKs were replaced by this Stage0 DEV pair:

- `app/build/outputs/apk/debug/app-debug.apk`, SHA256
  `13296FFD2C04AD0A3F40312F15422D90B072771E35A678E95A35F96279CFC286` (same implementation artifact).
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, SHA256
  `00A79C25F01FC8ADB992B512EBF2C8E874CCB8E4D1BDE600ECB72814FBCE08D3`.

Packages verified `.dev` and `.dev.test`; install -r and force-stop only, no data clear/uninstall.
All adb operations selected emulator-5554 explicitly. No Samsung action.

### First isolated gate and mandatory stop

Executed exactly once:

```powershell
& $adb -s emulator-5554 shell am instrument -w -r -e class 'org.beesearch.app.ui.map.MapOverlayProductionProfilesTest#fieldProfileRestoresRegistryOverlayAndPreservesStyleResources' -e beeOverlayRegistry true org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner
```

Result `STAGE0_VERIFICATION_FAIL`, 1 started/1 failure, 0 skipped, no process crash;
JUnit18.389s, wall19.944s. Failure at `MapOverlayProductionProfilesTest.kt:130`:

```text
java.lang.AssertionError
expected: [openstreetmap-standard, test-areas, beesearch-overlay-16-production-field-source-points]
actual:   [openstreetmap-standard, test-areas, org.maplibre.annotations, beesearch-overlay-16-production-field-source-points]
```

Assertions before line130 establish current Style fully loaded and native JSON request metadata
matches current generation. Actual source readback includes all expected profile IDs and the owned
fixture source, but an extra `org.maplibre.annotations` violates the test's exact native-source-set
expectation. Layer ID parity, original JSON structure parity, complete Registry overlay/visibility,
reload/fresh objects/stale rejection and lifecycle completion assertions were not reached. They
must not be reported PASS. This is a verification assertion failure; it does not by itself establish
a production Registry defect. Provenance/expected treatment of the additional source has not been
confirmed in this task. No test relaxation or production correction was attempted.

| Gate | Result | Evidence scope |
|---|---|---|
| ONLINE real production profile | STAGE0_VERIFICATION_FAIL | loaded/metadata assertions completed; exact source-set assertion fails |
| ACTIVE_VECTOR / SENTINEL / HYBRID | NOT_RUN_WITH_REASON | mandatory stop after JUnit failure |
| MapDataWiringTest / MapResearchObjectsQueryTest | NOT_RUN_WITH_REASON | same stop |
| MapCenterTargetTest / MapCoverageSelectionUiTest | NOT_RUN_WITH_REASON | same stop |
| Enhanced actual BeeMap startup/recreation fixture | NOT_RUN_WITH_REASON | same stop |
| Standalone SDK session / foreign collision fixture methods | NOT_RUN_WITH_REASON | same stop |
| Five non-blocked AreaEditorEntryUiTest methods | NOT_RUN_WITH_REASON | same stop |
| Known AreaEditor recreation method | BLOCKED_BY_PREEXISTING_NATIVE_FAILURE | prior exact-baseline evidence; excluded, NOT rerun |

No new native failure family occurred. Runner stopped immediately after the first JUnit failure;
no retry, later native gate or known-crash diagnosis ran. Exact planned filters/execution command
and fail-closed result classification are preserved in ignored
`build/evidence/remaining-gates/run-gates.ps1`. Per-gate evidence:
`profile-online/{instrumentation.txt,logcat.txt,result.json,devices.txt,device-start.txt}`;
build/APK/source provenance in the same remaining-gates directory. No new commit/push because
closure is not complete. Previously authorized dirty test/docs work and empty index are preserved.

One next action: verify the provenance of `org.maplibre.annotations` and the exact native-source-set
contract before any separately authorized test-side correction or rerun. The known pre-Stage0 crash
classification remains unchanged and is not responsible for this assertion failure.

Independent read-only Luna review confirmed the stop/result and no production defect proven.
Bounded inspection found an existing precedent in RasterBasemapPocDeviceTest.kt:1096: its native
layer identity assertion filters the org.maplibre.annotations namespace. This is a layer assertion,
not direct proof of the extra source's provenance. It supports reviewing the new test's expected
native resource set; it is not permission to broadly filter unexpected resources or declare PASS.
Only one ONLINE execution occurred, so repeatability of this assertion failure was not measured.

## Test-only native resource parity contract — 2026-10-12

**SDK_OWNED provenance established / strict contract tests PASS / ONLINE native BLOCKED /
EMULATOR CLOSURE INCOMPLETE / SAMSUNG_VERIFICATION_NO_GO.** No new commit.

Investigation followed the owner order: no app/test imperative marker/polyline/polygon annotation
API usage or construction of org.maplibre.annotations was found; production Registry only adds
its deterministic app IDs. Existing RasterBasemapPocDeviceTest.kt:1096 excludes annotation-prefix
LAYERS for its PoC identity assertion; it has no equivalent source/image exception and was not
copied blindly. Current dependency is android-sdk13.5.1. The installed native artifact contains
exact IDs and its x86_64 lib SHA matches the already recorded implementation/baseline artifact.

Primary version-matched evidence:
[AnnotationManager android-v13.5.1](https://github.com/maplibre/maplibre-native/blob/android-v13.5.1/src/mbgl/annotation/annotation_manager.cpp):
constants SourceID and PointLayerID name org.maplibre.annotations and org.maplibre.annotations.points;
onStyleLoaded calls updateStyle, which inserts AnnotationSource and a SymbolLayer into native Style
when the source is absent, even with no user annotations. This establishes SDK infrastructure
ownership, rather than an inference from a prefix. Shape layers and prefixed annotation images
are tied to annotation API operations; this fixture uses none. No image exception was added.

Old test compared ALL native source/layer IDs to profile-defined plus Registry IDs, omitting the
SDK's automatic additions. The corrected androidTest-only helper MapOverlayNativeResourceParity
uses closed exact singleton SDK source/layer sets, separately requires those infrastructure IDs,
rejects duplicates and application declarations colliding with them, and asserts exact equality of
expected application IDs against actual IDs minus only that exact SDK set. It does not use subset
parity, arbitrary namespace filtering, or exempt org.maplibre.annotations.shape.* resources.
Original profile JSON sources/layers equality and all subsequent overlay/visibility/reload/fresh-object/
stale-callback assertions are unchanged.

Current-task test edits only:
- MapOverlayProductionProfilesTest.kt: replace the two native source/layer ID-set assertions with
  the shared strict helper.
- MapOverlayResourceParityTest.kt: test-only helper and seven contract tests; no MapLibre renderer
  needed for these checks. Missing/wrong/unexpected application source, unknown SDK-looking IDs,
  SDK collisions/duplicates/missing infrastructure and extra layer IDs are proven to fail.

All required eight JVM/compile/assemble/lint gates PASS (2m06s), JVM927/927 with no failures/errors/
skipped; lint0errors44warnings6hints. The seven new instrumented contract tests PASS (0.049s JUnit,
1.769s wall), no failures/skips/abort. They add no framework/dependency/build configuration.

Stage0 production1e88fe8 remains unchanged, app APK SHA
13296FFD2C04AD0A3F40312F15422D90B072771E35A678E95A35F96279CFC286 unchanged.
Current test APK SHA A850A4ED5804680361194D0DFA152412488FA49A31C4E3541AABC29C080BA914.
DEV pair installed with install -r on emulator-5554/API29 only; process force-stop resets between
runs, no data clear/uninstall. All adb operations address explicit emulator serial; Samsung untouched.

After contract tests, only isolated ONLINE was run once with the same opt-in method command recorded
above. It reports Process crashed, no JUnit assertion, at 2026-10-12 17:25:19.931 UTC:
SIGSEGV/SEGV_MAPERR/null, RenderThread93, libmaplibre.so MapRenderer::render+151,
PC0x596ca7, BuildId097847da4cc01ece0bc5525282c208b27782fdee. This exactly matches the known
baseline family recorded in the bounded diagnosis section. Result is
BLOCKED_BY_PREEXISTING_NATIVE_FAILURE, not PASS or Stage0 FAIL. Wall23.595s; native assertion
phase is not logged, so completed load/parity/reload/cleanup assertions cannot be claimed individually.

Current owner Section7 requires ONLINE FULL PASS before the other three profiles. ONLINE did not
complete; ACTIVE_VECTOR/SENTINEL/HYBRID and remaining BeeMap/startup/non-blocked AreaEditor gates
are NOT_RUN_WITH_REASON (unsatisfied prerequisite). Known crashing AreaEditor method excluded and
not rerun. No retry, secondary graphics/backend variation, known-crash investigation, additional
assertion change or production fix followed the crash.

Independent read-only test review found no blocker/high/medium finding in the strict correction.
Evidence under build/evidence/parity-contract: build.log, apk-hashes.txt, run-gates.ps1,
parity-negative-contract and profile-online directories with result.json/instrumentation.txt/logcat.txt.
Direct instrumentation commands in runner preserve opt-in beeOverlayRegistry=true. Production,
config/dependencies/release/persistence and main/release checkouts unchanged; prior uncommitted
closure edits retained. Verification incomplete, so no commit/push.

One next owner action: explicitly decide whether the remaining isolated gates may proceed when
ONLINE is blocked by the established pre-Stage0 family instead of the Section7 FULL PASS prerequisite.
No production correction is justified by this result. Root cause remains as previously classified;
this attempt does not establish a Stage0-specific failure or full native assertion acceptance.

## Final independent emulator gates — 2026-10-12

**EMULATOR_VERIFICATION_PASS_WITH_KNOWN_PREEXISTING_NATIVE_BLOCKER /
SAMSUNG_VERIFICATION_GO / PHYSICAL SAMSUNG VERIFICATION NOT PERFORMED.**
This is the current closure classification under the owner's replacement §§11-12 rule. Earlier
NO-GO/FULL-PASS-prerequisite entries above are historical attempts and are superseded for this gate
classification. Blocked assertions remain unverified; this is not FULLY VERIFIED or ONLINE PASS.

Pre-execution HEAD1e88fe8765f1ab7a32ae6aeff129635d2d936185, branch task/d106-stage0-overlay-registry.
Production/config/dependency diff empty; prior authorized test/docs edits preserved, index empty.
Same corrected strict parity contract/test APK as prior task; no intervening source/test changes.
AppSHA13296FFD2C04AD0A3F40312F15422D90B072771E35A678E95A35F96279CFC286;
testSHAA850A4ED5804680361194D0DFA152412488FA49A31C4E3541AABC29C080BA914.
Only emulator-5554/API29/x86_64 was listed. Same image fingerprint as bounded comparison.
DEV/test package IDs verified, install -r to explicitly identify APKs; only force-stop resets,
no uninstall/data-clear/global-device operation. Every adb mutation targeted emulator-5554.

Frozen gates: ONLINE and AreaEditorEntryUiTest#aFinishedEditorSessionNeverReopensOnALaterVisit
are BLOCKED_BY_PREEXISTING_NATIVE_FAILURE and were NOT rerun. Canonical blocker wording:
KNOWN PRE-STAGE0 NATIVE BLOCKER — exact baseline-reproduced MapLibre RenderThread crash family,
root cause still UNKNOWN. Exact baseline/signature proof remains in the bounded diagnosis section;
no further crash diagnosis was performed.

All 13 authorized independent gates completed, 31 tests PASS, failures/errors/skipped/aborted0;
no native crash or new signature. One execution per gate; native execution wall sum669.324s.
No retries, graphics changes, dependencies or production fixes. Exact commands: same command below,
with each filter in the table; beeOverlayRegistry=true retained for opt-in native fixtures.

```powershell
& $adb -s emulator-5554 shell am instrument -w -r -e class $filter -e beeOverlayRegistry true org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner
```

| Gate | Exact filter | Result | Tests PASS | Wall seconds |
|---|---|---|---:|---:|
| area-add-cancel | org.beesearch.app.ui.map.AreaEditorEntryUiTest#addingAFragmentReturnsToTheFreeMapAndCancelingTheNextKeepsIt | PASS | 1 | 35.393 |
| area-explicit-request | org.beesearch.app.ui.map.AreaEditorEntryUiTest#anExplicitRequestOpensTheEditorPanelExactlyOnce | PASS | 1 | 22.688 |
| area-name-flow | org.beesearch.app.ui.map.AreaEditorEntryUiTest#doneAfterTheFirstFragmentUsesTheExistingAreaNameFlow | PASS | 1 | 27.534 |
| area-new-request | org.beesearch.app.ui.map.AreaEditorEntryUiTest#aNewAreaRequestStartsOnTheFreeMapAndOpensTheFragmentEditorExplicitly | PASS | 1 | 25.752 |
| area-plain-visit | org.beesearch.app.ui.map.AreaEditorEntryUiTest#aPlainVisitToTheMapShowsNoEditorPanel | PASS | 1 | 30.191 |
| fixture-startup-recreation | org.beesearch.app.ui.map.MapOverlayRegistryLifecycleTest#actualBeeMapHostProvidesAndReleasesOverlaySession | PASS | 1 | 30.157 |
| MapCenterTargetTest | org.beesearch.app.MapCenterTargetTest | PASS | 2 | 41.272 |
| MapCoverageSelectionUiTest | org.beesearch.app.MapCoverageSelectionUiTest | PASS | 12 | 249.662 |
| MapDataWiringTest | org.beesearch.app.ui.map.MapDataWiringTest | PASS | 5 | 132.42 |
| MapResearchObjectsQueryTest | org.beesearch.app.ui.map.MapResearchObjectsQueryTest | PASS | 3 | 4.603 |
| profile-active-vector | org.beesearch.app.ui.map.MapOverlayProductionProfilesTest#activePmtilesProfileRestoresRegistryOverlayAndPreservesStyleResources | PASS | 1 | 20.011 |
| profile-hybrid | org.beesearch.app.ui.map.MapOverlayProductionProfilesTest#devHybridProfileRestoresRegistryOverlayAndPreservesStyleResources | PASS | 1 | 25.865 |
| profile-sentinel | org.beesearch.app.ui.map.MapOverlayProductionProfilesTest#devSentinelProfileRestoresRegistryOverlayAndPreservesStyleResources | PASS | 1 | 23.775 |


ACTIVE_VECTOR/SENTINEL/HYBRID: production factories, fully-loaded native Style, current metadata,
strict application source/layer parity and exact SDK separation, JSON source/layer structure parity,
fixture restore/hidden visibility, repeated style load/fresh SDK objects/stale-work refusal and test
completion/teardown all PASS. PMTiles fixtures remain test-only empty archives; complete tile coverage,
field map visual quality and full map package installation are not asserted by these tests.

Enhanced actual BeeMap fixture PASS: logged FIELD request generations1->2, each loaded signal with
matching marker, latest marker ends:2 and unique owned Source/Layer. First session released, AREA_VIEW
recreated with a different host marker and a valid generation1; one owned Source/Layer, no stale
ownership assertion failed. Counts are observations, not a fixed product setStyle-count contract.
Original RecordingMapView constructor/lifecycle tests4/4 and strict parity contract7/7 remain prior
valid PASS evidence; standalone unchanged SDK fixture results retain their earlier evidence.

Explicitly NOT VERIFIED because of frozen blocked native gates:
- ONLINE corrected full test: native load/current metadata, strict application/SDK source and layer
  parity, original JSON source/layer structure, complete Registry restore and resource uniqueness,
  hidden visibility, repeated reload/fresh Source and Layer objects, stale callback no-mutation and
  native test completion/cleanup. No phase-level assertion completion is inferred from its crash.
- Known AreaEditor recreation method: completion of explicit open/consume/finish flow, no editor
  resurrection across all four recreated maps, and final handledRequests==1 assertion. Not relabeled
  PASS even though independent non-blocked editor methods pass.

JVM927/927, Debug/Beta/Release compilation, androidTest compile, assemblies and lint0errors44warnings
6hints remain PASS from preceding task; no intervening test edits justify repeating them here.
Room14/CompleteBackupV8/SnapshotV3, DataStore, Area ownership, version/signing and Stage1-4 unchanged.
Protected main verifier hash preserved; main/release checkout and Beta archive operations absent.

Current evidence under build/evidence/independent-remaining: source-head.txt, production-diff.txt,
git-status.txt, devices/fingerprint/APK hashes, run-gates.ps1, results-table.md, per-gate directories
with instrumentation.txt/logcat.txt/result.json. They are ignored local artifacts, not committed bins.
Independent evidence review verified each PASS, no hidden skip or retry. Current owner rule permits
GO with the frozen known blockers; GO authorizes planning a separate physical verification task,
not Samsung execution here. Native full ONLINE/known-editor assertions and visual/performance/GPS/
sensor/field ergonomics remain for another valid environment. Stage1 is not started.

## Samsung physical closure — formerly emulator-blocked assertions (2026-10-12)

**SAMSUNG_STAGE0_BLOCKED_ASSERTIONS_PASS.** Combined with the separately recorded emulator
EMULATOR_VERIFICATION_PASS_WITH_KNOWN_PREEXISTING_NATIVE_BLOCKER, the result is
**STAGE0_VERIFICATION_PASS within the declared emulator + Samsung verification scope.**
The earlier emulator-only unverified labels describe that environment; the two assertion groups
below are now physically verified. This does not claim general MapLibre stability or performance.

### SAMSUNG PHYSICAL EVIDENCE

Owner authorized only ONLINE and the known AreaEditor recreation method, in that order and with
stop-on-failure policy. Samsung SM-S938B, Android16/API36; serial recorded in local device-identity/
devices evidence, every adb command used explicit RFCY90MBYVZ. Image fingerprint recorded locally.
Before mutation: DEVversionCode7/versionName1.3.0 and testpackage present; foreground launcher.
Source checkout C:\App\Bee_search_stage0_overlay_registry, HEADab24fff9591d0c4cdb4db59aca063825ef845fa3;
production baseline1e88fe8765f1ab7a32ae6aeff129635d2d936185, identical app/src/main tree hash.
Index empty; only incoming docs/decisions.md diff existed, outside verification commit and unchanged.

Built :app:assembleDebug :app:assembleDebugAndroidTest with the established offline Gradle9.5.0/
task-local cache/temp environment, PASS17s. Packages verified org.beesearch.app.dev and
org.beesearch.app.dev.test, Debug variant. APKs:
- app/build/outputs/apk/debug/app-debug.apk, SHA256
  13296FFD2C04AD0A3F40312F15422D90B072771E35A678E95A35F96279CFC286.
- app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk, SHA256
  A850A4ED5804680361194D0DFA152412488FA49A31C4E3541AABC29C080BA914.

Both installed with adb -s RFCY90MBYVZ install -r; Success. Existing DEV/test data directories and
firstInstallTime retained (DEV ceDataInode438163/deDataInode424656). DEVversionCode became8, the
already existing Stage0 build version; no source version bump/signing/config change. DEV/test
force-stop only before installation/second gate. No pm clear/uninstall/permission grant/settings
change/user-map import or modification. Stable/release and Beta before/after version/update-time/
data-inode metadata match; no operations targeted their app data. This is update-in-place/package
metadata evidence, not a full file-by-file DEV database audit.

Exact executed commands, one method each:

```powershell
& $adb -s RFCY90MBYVZ shell am instrument -w -r -e class 'org.beesearch.app.ui.map.MapOverlayProductionProfilesTest#fieldProfileRestoresRegistryOverlayAndPreservesStyleResources' -e beeOverlayRegistry true org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner
& $adb -s RFCY90MBYVZ shell am instrument -w -r -e class 'org.beesearch.app.ui.map.AreaEditorEntryUiTest#aFinishedEditorSessionNeverReopensOnALaterVisit' -e beeOverlayRegistry true org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner
```

| Physical gate | Result | Started/PASS | Failures/skips/aborts | JUnit seconds | Observed wall seconds |
|---|---|---|---|---:|---:|
| ONLINE full production profile | PASS | 1/1 | 0/0/0 | 0.860 | 2.047 |
| AreaEditor recreation method | PASS | 1/1 | 0/0/0 | 1.704 | 2.694 |

ONLINE completed the complete unchanged assertion sequence: current native Style fully loaded,
current request metadata readback, exact application Source/Layer parity with exact closed SDK
infrastructure allowance, no unexpected extras, original JSON Source/Layer structure parity,
Registry fixture attach and hidden visibility, reload and fresh Source/Layer objects, stale callback
refusal/no current-resource mutation, session release and normal test-rule MapView teardown.
Cleanup coverage is limited to assertions and normal teardown already present in this test;
no new cleanup assertion or production logging was added.

AreaEditor method completed explicit open, request consumption, Done/session finish, all four
keyed MapView recreations with no editor panel, final handledRequests==1 and normal teardown.
No JUnit failure, ignored/skipped test, process crash, native Fatal signal or AndroidRuntime fatal
was observed in either run. No Samsung baseline reproduction, broader physical suite or emulator
crash diagnosis ran.

Bounded visual evidence: one screenshot per short run, captured via exec-out screencap to host
artifacts only. ONLINE frame (~1.54s) shows grey map surface without resolved tile detail; its timing
is transient, so tile visual readiness/absence of a persistent blank state is not established by it.
AreaEditor frame (~1.53s) visibly shows rendered world map, target and ordinary controls, no editor
panel. Screenshots do not constitute continuous flicker/restart observation. No obvious pathological
behavior was established; no performance benchmark or subjective smoothness PASS is claimed.

### EMULATOR EVIDENCE CARRIED FORWARD

Emulator classification remains EMULATOR_VERIFICATION_PASS_WITH_KNOWN_PREEXISTING_NATIVE_BLOCKER.
Physical PASS closes only the two missing assertion groups; it does not relabel emulator crashes
PASS or fixed. KNOWN PRE-STAGE0 EMULATOR NATIVE BLOCKER — ROOT CAUSE UNKNOWN. Exact baseline
proof remains in the bounded diagnosis section. No general cause attribution or SDK defect decision.

Evidence: build/evidence/samsung-blocked-assertions with device identity, before/installed/after
package metadata, source/index/diff/APK/build provenance, and online/area-recreation directories
containing instrumentation output, result.json, logcat and bounded PNG captures. Serial/timestamps
remain in local evidence. No source/test change required; operational documentation only updated.
Production/config/dependencies, Room14/BackupV8/SnapshotV3/DataStore/Area ownership/version/signing
unchanged. Protected main/release checkouts preserved. No Stage1 code, no push.

Remaining topics outside this declared verification scope: emulator root cause, full tile visual/
readiness checks, performance/large marker budget, GPS/heading/field ergonomics and any separately
open delayed SDK-request experiment. These are not asserted PASS by the two physical gates.
Next action belongs to owner: review and accept the combined scoped Stage0 evidence; do not
start a subsequent stage automatically.
