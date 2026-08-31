# Bee Search — Handoff

## Current milestone

The minimal Bee Search vector-map PoC, map-first main screen and unified
GPS-reference/measurement/ObservationPoint-target workflow are implemented and
validated as the local baseline before structural map refactoring. The main map
now owns both ordinary browsing and coordinate confirmation; the former second
placement map has been removed. OfflineRegion is not yet implemented. I002 and
I004 remain ideas pending user review of the device UI.

## Implemented

- `tools/map-poc/` contains a reproducible small-area pipeline:
  official OSM API extracts → pyosmium merge → Planetiler 0.10.0 YAML profile →
  MBTiles → static XYZ/MVT/style/TileJSON/glyph resources.
- Dataset `central-russia-poc-20260830` covers the three accepted test areas and
  one nearby diagnostic `power=line` way. Downloads and generated output are
  ignored by Git.
- Field profile preserves cutlines, power line/minor line, raw and normalized
  surface, tracktype, roads/paths, minor waterways, landcover, buildings,
  settlements, railway and bridges. Planetiler profile verification is 8/8.
- Static canonical layout is
  `/field/v1/central-russia-poc-20260830/style-v3/`. Source maxzoom is 15;
  Android UI maxzoom is 20 through vector overscaling.
- `style-v3` uses inline canonical XYZ/MVT URLs so MapLibre Android retains the
  source attribution; companion TileJSON remains available. Noto Sans packages
  Latin, Cyrillic and required general-punctuation glyph ranges. No sprites are
  required by the PoC style.
- Android uses a small `BeeMapProfile`/BuildConfig boundary. HTTP is allowed only
  in the debug manifest; the production placeholder remains HTTPS. Existing
  MapView lifecycle, GPS, recenter, center-target and ObservationPoint flow were not
  rewritten. Room and Territory schemas are unchanged.
- The main Territory route uses a map-first layout with no large title,
  Territory heading or full-width map actions. GPS accuracy is a compact map
  overlay; recenter and ObservationPoint creation are icon controls over the
  map. A 56 dp bottom panel reserves space for future safe top-level actions;
  it currently contains only a right-aligned 30 dp Settings glyph in a 48 dp
  touch target. The redundant Map self-navigation and visible Settings label
  are absent. No placeholder Points or Analysis destination was added.
- Settings shows the current Territory and provides the route to Territory
  management. The main map always shows a fixed 8 dp red center dot with a thin
  light halo to 10 dp; the former 72 dp crosshair/ring is removed. Panning shows
  geodesic distance plus true/geographic initial bearing from the latest GPS
  coordinate to that target; recenter returns the target to GPS. New GPS fixes
  move the GPS marker and update the measurement without discarding a manually
  selected map center.
- The main-map `+` explicitly confirms the current camera center. It preserves
  D054 by keeping the latest GPS coordinate/accuracy as the original measurement
  while the map center becomes the confirmed ObservationPoint coordinate. If
  `observer_code` is missing, only the existing required metadata dialog follows;
  there is no second placement map. Pan/measurement alone writes no research data.
- Distance/bearing calculations are deterministic and sensor-independent:
  HeadingProvider, phone orientation and magnetic correction are not involved.
  The eight displayed direction sectors are `С/СВ/В/ЮВ/Ю/ЮЗ/З/СЗ`; sub-0.5 m
  displacement is suppressed only in presentation.

## Verification

- Deterministic MVT inspection passed for cutline, power, track/tracktype/raw
  surface/surface class, water/river/stream/drain, forest, wetland, buildings,
  railway, settlement and Cyrillic labels. Export: 278 tiles, 161,439 raw MVT
  bytes; Planetiler MBTiles: 176 KiB.
- `BeeMapPocDeviceTest` passed on Samsung SM-S938B using localhost HTTP through
  `adb reverse`. It confirmed style/source/layers, all three real areas,
  Russian labels, power lines, rendered tracktype/surface properties and cutline
  overscaling at UI z17 and z20.
- Existing center-target UI test passed on Samsung. Manual checks confirmed app
  launch, GPS marker/accuracy, recenter, coordinate target without
  saving an ObservationPoint, and the OSM attribution dialog.
- Direct LAN access was blocked by the workstation firewall, so no firewall rule
  was added. The opt-in device test temporarily overrides MapLibre connectivity
  only for the ADB-localhost test transport.
- `gradlew test assembleDebug assembleDebugAndroidTest lintDebug`, tracked and
  untracked whitespace checks, and `git diff --check` pass.
- `MainMapScreenTest` and the center-target test passed directly on
  Samsung SM-S938B. `BeeMapPocDeviceTest` also passed after the redesign,
  covering real rendering and z15/z17/z20 camera movement. Manual checks covered
  GPS accuracy/marker, Settings and its Territory-management route, placement
  and cancel without a research write. The initial redesign grew MapView from
  about 723 px to 1713 px vertically; the compact-panel refinement then grew it
  to 1932 px on the 1080×2340 device. Review screenshot:
  `captures/map-first-compact-main-screen-sm-s938b.png` (ignored by Git).
- Unified-map JVM coverage passes for distance, bearing, normalization, sector
  boundaries, formatting, GPS updates and D054 draft mapping. Direct Samsung
  instrumentation passed 5/5 for the main-map/crosshair UI and 1/1 for Room
  persistence of original GPS A separately from confirmed coordinate B.
- A non-persisting debug-only host rendered the real `BeeMap` on SM-S938B without
  changing the active research workflow. Centered GPS showed no `0 m`; after pan
  it showed `131 м · 310° СЗ`, kept the GPS marker geographically fixed and the
  target centered, and recenter removed the measurement. Device inspection found
  and fixed an initial `(0,0)` camera race by making the first GPS camera update
  synchronous. Screenshots: `captures/unified-map-centered-sm-s938b.png` and
  `captures/unified-map-measurement-sm-s938b.png` (ignored by Git).
- The refined red-dot target passed 5/5 direct main-map/target instrumentation
  tests and the unchanged D054 Room regression passed 1/1. On SM-S938B the red
  and blue dots coincided in centered state with no measurement; after pan the
  red dot stayed at exact screen center on a small line/boundary intersection,
  the blue GPS dot separated, and `137 м · 280° З` appeared. Screenshots:
  `captures/unified-map-red-target-centered-sm-s938b.png` and
  `captures/unified-map-red-target-displaced-sm-s938b.png` (ignored by Git).
- Follow-up device review found that the 10 dp red target halo completely hid
  the smaller GPS marker when centered. A later real-app restart exposed a more
  important issue: the MapLibre GPS source/layer did not exist when the remote
  basemap style failed to load, even though GPS measurement remained available.
  The GPS marker is now an 18 dp Compose overlay positioned through
  `MapLibreMap.projection`; it remains geographically anchored during pan/zoom,
  is independent of style availability, leaves a clear blue rim around the red
  target at coincidence and remains a normal blue dot after pan. Verified
  screenshots with the development style:
  `captures/unified-map-both-markers-centered-sm-s938b.png` and
  `captures/unified-map-both-markers-displaced-sm-s938b.png` (ignored by Git).
- The same behavior was verified on the ordinary APK while its remote style was
  unavailable: `captures/gps-marker-no-style-sm-s938b.png` and
  `captures/gps-marker-no-style-displaced-sm-s938b.png`. Direct main-map/marker
  instrumentation passed 6/6.
- Device-data caveat: the first Gradle `connectedDebugAndroidTest` cleanup
  removed the debug target package. Reinstall restored a local database with
  counts Territory 1 / ObservationPoint 3 / Bee 0 / FlightCycle 0. No pre-run
  count was captured in this session, so this cannot be proven identical to the
  preceding debug-app state. Subsequent smoke actions left those counts
  unchanged, and no diagnostic database copy was found in the repository,
  common user folders or app external storage. Do not attempt restoration or
  further destructive device test setup without user direction.

## Known PoC limits

- OSM API bbox extracts are intentionally tiny and can clip multipolygon
  relations at their boundaries. Production extraction must be relation-complete.
- The sparse static tree returns 404 outside generated non-empty tiles and is
  blank outside the test areas; it is not a production regional dataset.
- The field style is functional rather than polished. A z16 variant was not
  generated; accepted z15 baseline remains unchanged.
- Development delivery is local HTTP. Production delivery remains versioned
  HTTPS. No OfflineRegion, preparation UI, satellite or topo work is included.

## Next milestone

Proceed in separate reviewable commits:

1. add a regression test for late `MapView` lifecycle attach;
2. purely extract the map screen / `BeeMap` without behavior changes;
3. fix lifecycle only if the regression test confirms the defect;
4. mechanically rename `ObservationPointCrosshair` to `MapMarkers` and
   `ObservationPointPlacementMetrics` to `MapMeasurement`, including tests;
5. only then start the production/useful online-map milestone.
