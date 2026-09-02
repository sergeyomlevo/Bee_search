# Bee Search — Handoff

> 2026-09-02 — coverage-selection prototype review accepted and committed as
> `c77044a Prototype composite offline coverage selection`. It remains an
> ephemeral map-screen UI prototype: geographic viewport rectangles, visible
> overlap/gaps, Undo, confirmed Clear, Show all and Done. No OfflineRegion,
> download, tile persistence or Room/domain coverage model was introduced.
> The Show-all camera-padding regression is fixed: after Done ordinary recenter
> again aligns GPS marker and center target without changing zoom.

## Current milestone

Uncommitted implementation normalizes the saved Territory/Observer model and
Settings. Room v5 adds `Observer`, mandatory Territory `region`/`district`, and
mandatory immutable `ObservationPoint.territory_id` + `observer_id` links.
`current_territory_id` and `current_observer_id` are DataStore UUID selections.
Settings lists saved entries, marks the current one and provides compact forms
to add/select/edit/delete them; new entries become current. Settings is not a
blocking onboarding screen: the map remains available without full context,
while new ObservationPoint creation is guarded until both current IDs are
valid. Forms use IME-aware scrolling and Back first closes an open form.

Migration 4 → 5 is an explicitly approved one-time development-stage reset. It
clears only existing test Territory/ObservationPoint/Bee/FlightCycle records,
because fabricating required person/region/district values would corrupt the
target model. It does not establish destructive migration as a policy: later
migrations must preserve real research data by default. Legacy `observer_code`
is no longer read by the target model and is removed on next observer selection.

## Verification status

- Phase-0 coverage commit worktree was clean; no push was performed.
- Full `test assembleDebug lintDebug assembleDebugAndroidTest` passed.
- Samsung upgrade via `adb install -r -t` succeeded without crash and without
  uninstall/clear-data. Settings and Observer creation were exercised. The
  Compose Territory form was not reliably completed by automation; multi-entry
  switching and linked-delete remain for manual review.

## Next task

1. User review of edit/delete UX and IME behavior on Samsung.
2. Do not commit or push the Territory/Observer milestone until user review.
