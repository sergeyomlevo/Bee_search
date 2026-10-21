# D106 My points — Stage C implementation and qualification

Baseline: `task/d106-stage0-overlay-registry`,
`489d19d1abec02f47669696654b24318fb45fe31`. OWNER approved C only;
[approved PNG+MD](ui/mockups/my-points-v1.md) and
[qualified B](d106-my-points-stage-b-qualification.md) remain the references.
Stage D, push and Samsung installation are excluded. Stage1/H05 remain OPEN.

## Implementation

- Enabled the existing My points entry in Map → Visualization → Map data.
  The nested list has independent visibility switches and title actions, current
  Territory rows, loading/read error/retry, empty state and vertical scrolling.
- Reused the existing B card and photo viewer. Name and marker open that card;
  Back restores the source context. Edit/delete controls are not offered.
- Room `user_points.is_visible` is the only visibility store. Existing create
  already commits ON together with point/photo metadata; no second settings
  write, migration or storage was introduced. Visibility writes are confirmed
  before success, with per-row failure/retry and no optimistic toggle.
- A `user-points` GeoJSON/CircleLayer declaration is supplied through the existing
  BeeMap/MapLibreOverlaySession/MapOverlayRegistry. Hidden and foreign points are
  excluded. Epoch checks reject old territory reads and write continuations.
  Blue markers with white outlines are distinct from research pictograms; a
  selected point has an amber halo. No icon/color chooser was added.
- The card's show-on-map command first persists ON when required, then selects
  the point and routes through the existing consumed camera request. Failure
  preserves the open card/OFF and issues no camera target. Composition-scoped
  execution and epoch checks prevent stale camera actions after navigation.

Research before continuation: official
[Compose testing APIs](https://developer.android.com/develop/ui/compose/testing/apis),
[MapLibre GeoJSON and CircleLayer example](https://maplibre.org/maplibre-native/android/examples/styling/circle-layer/).
These confirm existing platform mechanisms; current web API docs are newer than
13.5.1, so exact project compatibility is established by pinned 13.5.1 compilation
and native runtime tests, not assumed from latest documentation. No new library,
Registry, map host/lifecycle, media pipeline or backup workflow was created.

## Verification record

Runtime target: API29 `emulator-5556`, system fontScale 1.7. API37 Espresso and
physical Samsung were not used. Installation uses verified DEV application IDs
`org.beesearch.app.dev` and `.dev.test`, `install -r -t`, without uninstall/clear.

Evidence: [Stage C folder](ui/evidence/my-points-stage-c-20261020/).
**Stage C targeted qualification PASS on API29**, with the separate existing JVM
backup classification failure below. No whole-suite or Samsung PASS is claimed.

- Full regression selection: **83/83 Android tests PASS** (list, visibility owner,
  card commands, Room/reopen, creation default ON, repository, GPX display and
  existing map-data panel). Raw `api29-regression-83-pass.txt`.
- Native current-Territory snapshots, ON/OFF, multiple features, selection and
  base-style reload: **4/4 PASS**, `native-final-pass.txt`.
- Final list after the local empty-state composition fix: **6/6 PASS**,
  `list-final-6-pass.txt`, complete device screenshots in `component-ui-final/`.
- Final non-native subset from the 14-test run: **10/10 PASS** (list/card and
  existing map targets); that run's native scope check initially failed and was
  corrected/requalified separately, so the raw 13/14 is retained as a failure.
- Research selection/card and real ViewModel navigation round trip regression:
  **4/4 PASS**, `research-selection-4-pass.txt`.
- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` PASS on final source;
  lint has 0 errors, 33 warnings, 6 hints. `git diff --check` PASS.

Final installed DEV APK SHA-256 equals local artifact:
`e6e46373e15493b90e749fb92c670a8f292a3d3091bc62448829aa1adb2fe64b`.
`final-artifact-and-data-verification.json` records API29/fontScale1.7 and identity.

### Requested checks and evidence boundaries

| Check | Actual evidence |
|---|---|
| 1. Current Territory list | Visibility owner stale-read/filter tests + native scope test |
| 2. New point ON | Creation UI/repository tests + real `C map marker` Save and Room row |
| 3. Marker appears | Native attached resources/features + runtime 08 screenshot |
| 4–5. OFF hides / ON restores | Native tests + runtime all-OFF map and final all-ON UI/Room readback |
| 6. Multiple points | Native source has multiple UUIDs; runtime two distinct marker locations |
| 7. Persistence after restart | Actual Room close/reopen test; manual force-stop/new PID and hidden map, then fresh UI/Room final readback |
| 8. A → B → A | Native Source and visibility owner tests, including late noncooperative read/write; actual picker workflow not separately driven |
| 9. Base map change | Native two-style reload retains fresh Sources/Layers and selected feature; production basemap picker not separately driven |
| 10. Title opens card | Real B list title → existing card → system Back returns to list |
| 11. Marker opens same card | Real B and C marker → existing B card route; C marker Back returns to map |
| 12. Show ON/OFF | Real newly created ON and hidden B point → focus/halo; screenshots 03 and 08 |
| 13. Failed ON does not focus | Card test injects repository write failure, checks open card/OFF/no callback, then retry succeeds once |
| 14. Back/close/no replay | Real list/card/map returns plus card tests; camera request uses existing consumed contract |
| 15. fontScale1.7 | Final 6 UI tests and full screenshots: long Russian title, bottom reached, empty/error, independent targets |
| 16. GPX/research regression | Existing GPX and panel tests in 83; selection/card round trip 4; shared native resource coexistence |
| 17. A/B data/photos | Both original point rows exactly match; all 4 photo rows and actual file SHA-256 unchanged |

Manual creation added one emulator fixture `C map marker` at a different
coordinate, without photos. All three fixture points finish ON. The two original
B points are co-located, so a pixel screenshot shows them at one shared position;
UUID membership is checked separately by native tests/Room. The original records,
notes, coordinates, timestamps and photos were not edited/deleted.

### Runtime comparison against approved PNG

Viewed approved d-list/d-empty/e-card-bottom PNG and final runtime images:

| State | Composition result |
|---|---|
| List | Same pinned Back/title and separate left switch/right title; wraps Russian words, scroll reaches final full title, no create command |
| Empty | Found vertical-centering mismatch; fixed message to TopStart under the header. Full final screenshot confirms header/message and no create command |
| Card | Existing B photo hero/strip/coordinates retained; show-on-map added in coordinates block; no fake edit/delete commands |
| Error | Previous OFF remains, explicit inline error and reachable Retry; final error screenshot captures the complete window |
| Map | Separate blue/white user marker and amber selection visible with multiple locations; photos and research data remain separate |

Material/API29 palette, font metrics, two-line empty text and fixture contents
are accepted Android adaptations; no pixel-perfect HTML equality is claimed.
Final component screenshots are from a real API29 composition, not the production
sheet host; 04/09 show the actual production sheet. Earlier `component-ui/`
subtree captures are historical: cached header rendering was omitted by that
capture method. Full-window `component-ui-final/` is used for final visual QA.

A manual automation helper was observed to pull old XML when API29 UIAutomator
reported `null root node` with exit code 0. `diagnostic-stale-helper-restart.json`
is explicitly invalid for qualification; intermediate 02 is before native render.
Final restoration checked unique fresh dump files before each action, and Room
readback independently verifies all ON values. No helper/plugin was changed.

### JVM result and existing backup limitation

Full JVM suite executed: **1066/1067**, one failure in unchanged
`RepositorySnapshotsTest.capacityDropAfterStagingNeverPublishes`:
expected `CAPACITY_INSUFFICIENT`, observed `INVALID_SNAPSHOT`. Its focused rerun
passed. Source review explains the timing-dependent classification: the periodic
capacity check can throw inside `SnapshotArchive.validate`, whose generic catch
wraps it as INVALID_ZIP before RepositorySnapshots maps it to INVALID_SNAPSHOT.
Stage C changes neither that code nor its tests. This existing backup error
classification remains a separate unresolved issue; the full suite is not
reported as wholly passing. Both logs are retained.

### Test fixture corrections

Initial C test compilation failed on AndroidView's positional modifier; fixed
with its named argument. List tests now use one composition with mutable state
instead of calling setContent twice. The first native fixture run captured the
new Style as the previous Style and queried source features before a render
frame was ready; it timed out and later crashed in libmaplibre querySourceFeatures.
The corrected fixture captures the previous Style before requesting another,
and waits for a fully rendered frame of the current Style before querying.
The subsequent A→B→A assertion also needed to await asynchronous native Source
publication after sync; it then passed in the separate final four-test run.
The initial failure/crash logs are retained rather than counted as PASS.

## Review, scope and limitations

Independent read-only review checked diff, scope, epoch checks and persist-before-
focus. Real marker/card/camera navigation is qualified separately from callback
and native-resource tests. Research marker targets retain their existing higher
hit priority: exactly overlapping 48dp targets can cover a user point; that
point remains accessible by its independent list title. Overlap cycling or a
chooser is not introduced without a separate product decision.

Room schema, photo persistence, GPX behavior, research data, creation mechanism
and Android Auto Backup are unchanged. Pre-existing handoff diff, GPX Samsung
reports, research and B evidence are preserved outside the C commit scope.

Decomposition reviewed; kept cohesive because: MainActivity/MainViewModel only
extend existing route/camera dispatch; MainMapScreen wires its existing owners;
BeeMap adds one feature interaction boundary to the existing map. Independent
list, visibility owner and overlay declaration are separate files. MapDataPanel
and MapDataViewModel retain their single panel/session responsibilities; no
unrelated split or cleanup was performed.

NOT RUN: physical Samsung, TalkBack, hardware camera/GPS/compass, process-death
draft recovery, Stage D edit/delete and Backup/Restore. Native renderer fixtures
are not evidence of those device/field behaviors.
