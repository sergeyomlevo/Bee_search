# Temporal I6 — owner corrections, final integration

Latest continuation: [Owner visual corrections #2](temporal-i6-owner-visual-corrections-2.md).
Evidence below describes the preceding integration gate; current corrections/gates are in that report.

Base HEAD: `20a7c55835f2d129908da899f743e9583414f42a` (`feat: add research object map markers`).
Continued from existing dirty I6 tree; no reset/add/commit/push. Protected verifier TOML excluded.

## Architecture and semantics

Main existing map = operational + unified ObservationPoint/Hollow/LogHive map. Points = table/records
browser with year filters, navigation, detail and allowed editing. No Visualization route.
Removed PointsViewMode, table/map switch, POINT_BROWSER, browser-specific BeeMap arguments/camera
fitting/selection/card overlay. Removed MarkerVisualPreview debug/beta/release implementations and
preview-specific tests/wiring. Kept BeeMap, ResearchObjectMarker, ResearchMarkerType,
SavedObjectMarkersOverlay, production D102 catalogue/pictograms, normal32dp and selected halo.
Other DEV diagnostics remain. ObservationPoint has no real «Показать на карте» action; none added.
Existing physical-object show-on-map navigation remains.

Previous panel auto-close root cause: settings DataStore writes re-emitted resolved initial setup
Ready→Loading→Ready and destroyed CurrentTerritoryScreen. Existing generation guard preserved;
MapDataViewModel owns panelOpen/openedType. Live filter/visibility mutations don't close either
sheet or type screen. Type Done/Back returns to list, main Done/Close/Back/swipe/scrim closes.

MapTypeDisplay uses typed ResearchObjectFilterSet. ObservationPointFilterSet = dateInterval,
beeCount, flightCycleCount; PhysicalObjectFilterSet = dateInterval, entranceHeightCm, outerDiameterCm.
MapObjectFilters carries per-type FilterSets to repositories, SQL applies all optional predicates
before materialization. Bees = owned Bee records, COUNT(DISTINCT b.id); cycles = all owned Bee
FlightCycle records, COUNT(c.id), including open cycles. Existing completedFlightCycleCount display
remains distinct. Count/measurement bounds are inclusive, zero is valid, null bounds unrestricted.
Date remains I5 canonical inclusive research date; bounded physical dates exclude NULL fixation_date,
unbounded dates retain NULL. Bounded unknown measurements don't match; no synthetic values.

Per-Territory DataStore codec v2 reads legacy v1 visibility/period with unrestricted new criteria.
Default store state needs no new persistent entry. Global reset clears all criteria, keeps visibility
and sheet; period reset preserves counts; local numeric reset clears only its criterion/invalid draft.
Main summary: Все данные if unrestricted, period summary if period only, concise combined conditions
otherwise. Accordion reflows title/summary at font1.7.

DAY: single day=[day,day], 10→15May2026=[2026-05-10,2026-05-15]. MONTH singleMay2026=
[2026-05-01,2026-05-31], May→July2026=[2026-05-01,2026-07-31], March→September2026=
[2026-03-01,2026-09-30], Dec2025→Feb2026=[2025-12-01,2026-02-28]. YEAR single2026=
[2026-01-01,2026-12-31], 2024→2026=[2024-01-01,2026-12-31]. Second boundary doesn't replace first.

## Hollow / LogHive field audit

| Field | Type | Applies | Structured? | Decision / usefulness |
|---|---|---|---|---|
| fixation_date | LocalDate nullable | both | yes | period filter; research fixation date |
| entrance_height_cm | positive Double | both | yes | implemented inclusive range; comparable entrance height |
| outer_diameter_cm | positive Double | both | yes | implemented inclusive range; comparable object diameter |
| internal_diameter_cm | Double; nullable Hollow/legacy | both | yes | useful candidate; deferred to keep this correction bounded, unknown needs explicit semantics |
| internal_height_cm | positive Double | LogHive | yes | useful candidate; deferred, not required for current minimal shared measurement slice |
| entrance_azimuth_deg | Int0..359 | both | yes | deferred; circular ranges need a separate UX contract; 0 remains valid |
| tree | String | both | free text | rejected as categorical filter; uncontrolled vocabulary |
| material | String | LogHive | free text | rejected as categorical filter; uncontrolled vocabulary |
| name, notes | String nullable | both | free text | rejected; human labels/notes lack structured research semantics |
| created_at | Instant | both | yes | rejected research-date predicate; technical provenance only |
| creator_observer_id | UUID nullable | both | yes | provenance candidate deferred; not a measured object property |
| territory_id | UUID | both | yes | already scope, not an additional criterion |
| object_type | enum | both | yes | already separate type, not another criterion |
| sequence_number | Int | both | yes | identity/lookup, not biological property |
| latitude / longitude | Double | both | yes | map placement; no new spatial-filter capability |
| media | 1:N metadata/files | both | partially | no inspection/semantic media capability added |

Evidence: PhysicalObjects.kt / Entities.kt / PhysicalObjectEditor.kt / PhysicalObjectCards.kt,
RoomPhysicalObjectRepository.kt, domain-model.md/data-model.md. No Inspection capability or new fields.

## Verification

Results are recorded after current-code gates; earlier I6 runs do not establish final acceptance.
Room stays v13; no schema columns or migrations added. Owner DB has no test records added.

Focused JVM: 109 tests, 13 suites, no failures/errors/skips. Full JVM: 877 tests, 107 suites,
no failures/errors/skips (`build/i6-owner-evidence/{focused,full}-jvm.json`). Android current-code
suites cover the panel, session ViewModel, wiring, Room temporal/count/measurement queries,
DataStore, Points table, D102 rendering, real pointer touch above a native View, compact cards
and record navigation/context: 83 tests, zero failures/errors/skips. Focused marker-record Android
run: 22/22.
Required debug/test APK builds, AndroidTest Kotlin, beta/release Kotlin and lintDebug are included
in `build/i6-marker-record-quality-final.log`; lint has no errors (28 warnings, 6 hints, not a zero-warning
claim). Final independent read-only review is performed after this document and the entire final
diff; its severity counts are reported in the final owner response, not inferred from older reviews.

Samsung RFCY90MBYVZ: safe `install -r` only, DEV application ID verified. Installed APK SHA256
`98F34958FDFF1B733CB23B072236FEE6E1FAC1003B95C4DDD2A05F85699AC4FC`, update time
2026-10-10 08:54:26. FontScale 1.7. One panel opening supports all three visibility switches,
type transitions, date/count/measurement edits and reset. May→July remained a continuous range;
numeric fields, accordion summaries and buttons were visually inspected. Points table has no
map switch; DEV marker preview entry is absent, other diagnostics remain. ObservationPoint,
Hollow and LogHive each render their real pictogram and selected white halo (settled screenshots
`touch-selected.png`, `hollow-halo-final.png`, `loghive-halo-final.png`). Accessibility exposes
selection as a checkable node's checked state; inner label nodes alone are not selection evidence.

Owner extension: each real visible marker selects its UUID and opens MapSelectedObjectCard;
«Открыть запись» dispatches the exact type/UUID to the existing PointDetailRoute or
PhysicalObjectDetailRoute. No duplicate domain/detail data. Route origin returns Back to the
main map, including coordinate-edit and show-on-map subflows. MapDataViewModel retains per-Territory
selection/filter state and transient camera target/zoom/bearing/tilt; BeeMap restores that snapshot
before GPS auto-centering. This guarantees ordinary record navigation, not process-death recovery.
Closing preview changes selection only; filtered-out IDs cannot be selected.

Samsung actual records: Point16, Hollow13 and LogHive1 opened their existing full records and
returned to the same selected preview. Point and LogHive marker screen bounds matched before/after
Back, with zoom15; Hollow return was visually checked. Preview controls remain accessible at
fontScale1.7, long labels truncate after three lines with full accessible text. Screenshots/XML:
build/i6-owner-evidence/record-{point,hollow,loghive}-*. No fake records. Non-default camera17.25
retention is automated state evidence; a separate non-default zoom device trial was not performed.
Owner visual acceptance remains required.

Real Samsung points: adding bees≥4 removed zero-bee markers 22/20; adding total cycles≥10
also removed 19/16, leaving 18/17 in the viewport. This is viewport evidence, not a complete
DB cardinality assertion. Deterministic A=2 bees/5 cycles, B=5/12, C=8/20 and zero-point Room
fixtures prove inclusive/open/exact bounds and combinations without fake owner records.
Owner pre-test display restored: ObservationPoint visible, September–October 2026, all counts
unbounded; Hollow/LogHive hidden, all their criteria unbounded. v1→v2 persistence changed the
encoding, not those settings. Read-only settings snapshots are in build/i6-owner-evidence.

Changed files are enumerated by final `git status --short` in the owner response; it includes the
preserved prior I6 work and the intentionally dirty protected TOML. Index remains empty.
Protected SHA256: `7388F2F233AB86694C69F15CB0BF8076C39A65932D2248D44046CFE2E46A1F31`.
NO COMMIT. NO PUSH. NO git add. I7 NOT STARTED.

External research: existing Room @Query named nullable bounds and SQLite SELECT/HAVING/COUNT are
sufficient; no custom query builder/dependency. Primary references:
https://developer.android.com/reference/androidx/room/Query
https://www.sqlite.org/lang_select.html
https://www.sqlite.org/lang_aggfunc.html

Decomposition reviewed; kept cohesive because: Daos.kt defines existing Room schema query boundaries;
RoomObservationRepository and RoomPhysicalObjectRepository keep their established repository mapping
responsibility. Large existing map files keep cohesive declarative screen composition and prior
marker helpers; independent numeric input lives in MapNumericRangeEditor.kt, filter values in
ResearchObjectFilters.kt, query assembly in MapResearchObjects.kt. No unrelated cleanup.

Marker navigation research: https://developer.android.com/develop/ui/compose/state-saving
Existing MapLibre CameraPosition API is reused; no new dependency.
Decomposition reviewed; kept cohesive because: MainViewModel owns the existing route/origin state
machine; BeeMap owns native map lifecycle/camera. Independent compact card and camera value type
are extracted into MapSelectedObjectCard.kt and MapCameraContext.kt.
