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

## Next milestone

Device-local desired map coverage persistence is implemented on top of the
committed Territory/Observer model. Preferences DataStore stores versioned `v1`
rectangle lists keyed by Territory UUID; Room remains v5. BeeMap loads only the
current Territory, edits a working copy, persists atomically on Done and cancels
on Back. No tiles, OfflineRegion or download state exists yet.
Deleting an unused Territory also clears only that Territory UUID's persisted
coverage; a Territory referenced by an ObservationPoint remains undeletable and
keeps its coverage.

Verification still required: Gradle checks and data-safe Samsung review of
Territory A/B switching, restart, Done/Cancel/Clear, and no stale overlays.
Do not commit or push this milestone until user review.

## Completed offline vector map PoC (2026-09-04)

The developer selector exposes only `ONLINE`, `VECTOR FOREST`, and `VECTOR
SAPUNOVO`. The two vector profiles read local PMTiles through
`pmtiles://file://`; Sapunovo automatically fits its descriptor bounds once on
selection. Its source stops at z15 and MapLibre vector overscaling remains
available through UI z20.

The ordinary Sapunovo profile contains the field geometry plus simple
`name`-based place, water/waterway, and road labels. Local glyphs use
`asset://map-poc/glyphs/{fontstack}/{range}.pbf` and `Noto Sans Regular`.
Diagnostic profiles and tests remain hidden; they are not selector choices.

Manual user verification on Samsung SM-S938B (not merely automation) passed:
airplane mode, phone reboot, cold app start, Sapunovo geometry, Cyrillic place
labels, water/waterway labels, road labels, and no network dependency observed.
No Room/domain/Territory persistence or production map-download architecture
was changed.

## Next experiment

The user chose the real benchmark BBOX on Samsung; do not normalize it to a
25 × 25 km square and do not persist it as a production Territory:

```text
west  = 42.288289
south = 56.153038
east  = 42.729915
north = 56.444694
27.2 × 32.4 km; approximately 883.6 km²
```

`C:\App\Bee_search_test_maps\pmtiles\territory-benchmark-v1.pmtiles` was
generated from `central-fed-district-260830.osm.pbf` with the unchanged field
profile, bounds above, z8–15 and `--download=false`. The regional PBF header
covers the entire BBOX. The PMTiles is v3/MVT with gzip internal and tile
compression, 5,255,967 bytes, 2,743 entries (2,738 unique contents), and no
missing stored coordinate in its expected z8–15 bbox grid. It is copied to
Samsung app-specific `files/map-poc/` and exposed temporarily as `VECTOR
TERRITORY`; the device regression check passed. The temporary BBOX selector
also remains uncommitted. Airplane/cold-start and field-ergonomics review for
this larger package remain manual checks; do not design Territory download or
packaging workflows yet.

## Territory benchmark review (2026-09-05)

Manual user review on the physical Samsung SM-S938B passed for `VECTOR
TERRITORY`: correct rendering, satisfactory field detail and label readability,
smooth pan/zoom, and no noticeable slowdown. This user-reviewed benchmark is
the exact BBOX above: 27.2 × 32.4 km / approximately 883.6 km², 5.01 MiB
PMTiles, and approximately 37 s local generation from the existing PBF.

Evidence supports the current local pipeline as a practically suitable
foundation for Bee Search offline vector maps:

```text
OSM PBF → Planetiler → custom field profile → PMTiles → MapLibre Android
→ local asset glyphs and labels
```

For this real Territory, storage, generation time, Android rendering,
readability and responsiveness are acceptable. The 5.01 MiB result is not a
universal package estimate: it varies with OSM feature density and profile.
This is PoC evidence, not a silent replacement of the accepted D007 production
OfflineRegion workflow; choosing a production PMTiles delivery mechanism needs
an explicit architectural decision.

`Territory boundary != map coverage`:

- a future Territory boundary is one primary working-area extent;
- MapCoverageSelection remains a separate, multi-fragment device-local map
  coverage mechanism for partial/additive coverage and potential later
  selective satellite data;
- the debug-only `BBOX` flow is ephemeral and neither writes Territory/Room/
  DataStore nor replaces MapCoverageSelection. Its viewport-based interaction
  is a candidate prototype for a later `Set Territory boundary` step, only
  after a dedicated model/UX decision.
