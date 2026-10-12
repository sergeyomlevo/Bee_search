# Map overlay lifecycle — durable device evidence

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

The opt-in native test read the marker back across A -> B -> A, confirmed distinct markers,
fresh SDK Source objects and exactly one fixture Source/Layer per Style. Existing metadata is
preserved; an existing reserved marker causes a refusal rather than overwrite. Resource IDs,
Sources/Layers, camera settings and basemap composition are unchanged by the marker.

### Automated evidence

Baseline: `:app:testDebugUnitTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`
passed before source edits: 897 tests, 0 failures/errors/skipped. No prior-run count was reused.

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
