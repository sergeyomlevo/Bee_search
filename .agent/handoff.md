# Bee Search handoff

## Current milestone

ObservationPoint creation now treats description and photos as properties of
the unsaved preparation draft. Both ordinary creation and `NO_BEES_FOUND`
creation commit them together with the point; cancelling preparation removes
the app-owned draft photo directory and persists nothing.

Draft photos live under
`files/observation-attachments-staging/<draftSessionId>/<attachmentId>`. On
successful creation they move to the existing permanent
`files/observation-attachments/<pointId>/<attachmentId>` layout before Room
atomically inserts the point, pending weather row and attachment metadata. A
failed Room operation rolls the files back to the draft. The historical/active
Properties screen remains available for later editing.

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
