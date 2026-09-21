# Bee Search handoff

## Current milestone

Single ObservationPoint export is implemented under D086. `Точка №N → ⋮ →
Экспортировать точку` launches the existing Exchange/Data `CreateDocument`
flow with a readable Territory/point/date/short-UUID filename. The action is a
read-only snapshot and is available for completed and active points; delete
remains restricted to completed points.

`data/pointexport` is deliberately separate from complete backup v2. Its ZIP
format v1 contains `manifest.json`, `point.json`, and optional binary
`attachments/<attachmentId>` entries for exactly one point. The graph includes
Territory/Observer context, persisted point/description/weather, every Bee and
FlightCycle (including open cycles), attachment metadata, and photo bytes. The
codec validates version/profile, graph ownership and duplicate identities,
ZIP paths/duplicates, missing/unlisted entries, and attachment size/SHA-256.
Structured order and ZIP entry timestamps are deterministic.

Room remains v7 and complete backup remains v2. Area, PMTiles, active-map state,
DataStore selections/settings and other ObservationPoints are never read into
the single-point package. Selective import and future share/server transports
remain outside scope.

## Verification status

369 JVM unit tests pass with 0 failures. The preserving Samsung SM-S938B run
reports `OK (38 tests)`: 18 complete-backup v2 tests, the CreateDocument contract,
and 19 Point detail/matrix tests. Lint, `compileDebugKotlin`,
`compileDebugAndroidTestKotlin`, `assembleDebug` and `assembleDebugAndroidTest`
pass.

Manual Samsung smoke exported real point №16 to Exchange/Data with the proposed
name. Package validation passed: profile/version and point checksum matched,
only point №16 was present, with 4 Bees / 8 cycles, LOADED weather and zero
attachments. Point №15 remained visible, and the real matrix still showed
completed and open cycles. The temporary export was removed after validation.
Room DB, DataStore, active PMTiles manifest and PMTiles bytes had identical
SHA-256 values before and after; the DEV package remained installed.

## Next task

Selective point import is not implemented. Future share/server transports must
reuse the package contract without turning it into complete backup v3.

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
