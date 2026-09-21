# Bee Search handoff

## Current milestone

The ObservationPoint viewing workflow was consolidated. `Точки` is now the
screen that owns all saved points: a compact arrow + one-line title + `⋮` header
instead of the previous `TopAppBar`, Territory-then-year filters on one row, the
Map/Table switch, and a screen menu with export of all research data and
deletion of all ObservationPoints. The Territory filter is a viewing selection
only: `PointsViewModel` has no settings access, so browsing another Territory
cannot switch the operational one.

The separate `Просмотр точки` title and the separate `Свойства точки` screen are
gone. One screen titled `Точка №N` shows the point data, description, photos,
weather snapshot and a Bee × FlightCycle matrix; its menu deletes exactly that
point after a confirmation naming it. One row is one Bee, one column is the same
`sequenceNumber` across Bees, and only the cycle region scrolls horizontally.
Completed cells show compact duration and optional azimuth, open cells show `…`,
and absent cycles show `—`; tapping a real cycle opens its persisted details.
The description/photo/weather
sections and one point ViewModel were reused from the former properties screen,
so there is no duplicated exporter, repository or media path.

Global export and deletion left Settings; Settings keeps user settings, help,
offline maps and the Territory/Observer lists. The export still uses the
existing logical-backup exporter and deletion still uses
`FileAwareObservationDataMaintenance`, so research data semantics, attachment
cleanup, Territory/Observer/settings/Ареал/offline-map preservation, Room v7 and
backup v2 are unchanged. The former `ui/data` screen and its tests were deleted
because every operation they exposed was relocated.

Map presentation now works on a `MapObjectMarker` list with one implemented
`MapObjectType`. No Hollow, LogHive, Apiary or Trap entity, table or marker was
created; adding one later needs only an enum entry and a mapper.

Single-ObservationPoint export does not exist as a backend. `BackupService`
always exports the whole graph and backup v2 validates every point and
attachment, so the point menu deliberately offers no export command rather than
a non-working one.

## Verification status

357 JVM unit tests and the focused 18-test Point detail suite on Samsung
SM-S938B pass with 0 failures. Lint, `compileDebugKotlin`,
`compileDebugAndroidTestKotlin`, `assembleDebug` and `assembleDebugAndroidTest`
also pass.

Measured on that device at system `font_scale=1.7`: chrome above the map fell
from 216dp to 154dp and the map grew from 436dp to 546dp of the 700dp content
area; the old header alone was 96dp because the inner `Scaffold`/`TopAppBar`
re-applied the status-bar inset the app-level scaffold had already consumed.

Manual Samsung checks at system `font_scale=1.7` covered the real points №15
(5 Bees / 12 cycles, columns through `Ц4`) and №16 (4 Bees / 6 completed plus
2 open cycles). Bee identity stayed fixed during horizontal scroll; `…`, `—`,
durations and the cycle dialog were readable. The point №15 `Пчела 1 / Ц4`
dialog showed the correct departure, return and `00:04:35`; the point №16 open
cycle dialog showed the full open/in-flight state. Room DB/WAL and DataStore
SHA-256 values were identical before and after, and the active PMTiles package
remained present. No destructive action was performed on real data.

## Next task

Single-ObservationPoint export, only as a new technical task: the audit
confirmed no per-point export backend exists, and a single-point archive would
need its own contract decision because backup v2 requires every point to have a
weather row and validates all foreign keys.

## Previous milestone

ObservationPoint creation now treats description and photos as properties of
the unsaved preparation draft. Both ordinary creation and `NO_BEES_FOUND`
creation commit them together with the point; cancelling preparation removes
the app-owned draft photo directory and persists nothing.

Draft photos live under
`files/observation-attachments-staging/<draftSessionId>/<attachmentId>`. On
successful creation they move to the existing permanent
`files/observation-attachments/<pointId>/<attachmentId>` layout before Room
atomically inserts the point, pending weather row and attachment metadata. A
failed Room operation rolls the files back to the draft.

Room remains v7 and backup remains v2. Existing attachment metadata,
FileProvider paths, Open-Meteo/WorkManager behavior, Area DataStore and PMTiles
storage contracts are unchanged.

## Verification status

Offline debug Kotlin compilation, the full unit suite, androidTest compilation,
lint, debug APK assembly and debug androidTest APK assembly pass. The focused
preserving Samsung SM-S938B run reports `OK (37 tests)` for preparation UI,
properties Room behavior, historical Properties, point/file deletion and
backup v2.

Manual Samsung checks at `font_scale=1.7` covered picker and camera cancellation,
draft cleanup on creation cancellation, ordinary creation with description and
photo, `NO_BEES_FOUND` creation with photo, historical/active Properties reopen
and photo deletion. DataStore, the installed PMTiles file and its manifest kept
their pre-test SHA-256 values. Area still reports two sections / 4.8 km² and the
offline map as loaded.

## Next task

No follow-up is required for this iteration. Continue new functional work from
the updated `main`; do not revive the pre-integration source worktrees as a
development baseline.
