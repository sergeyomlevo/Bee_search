# Bee Search handoff

## Current milestone

Main now contains the validated integration of the completed ObservationPoint
properties v1 line and the completed Area/offline-map UX line. The integration
baseline was `812516b02553b5bc980041cfd0c0ecd1b7e0a6a8`; the merged source tip
was `63145545018e25f225aaef45e19d774340f26a1d`.

The combined application contains, without changing their accepted semantics:

- Room schema v7, ObservationPoint description, photo attachments, weather
  snapshots, backup v2 and v1 restore compatibility;
- provider-neutral `WeatherProvider`, Open-Meteo adapter and constrained
  WorkManager backfill for the original point coordinates and time;
- Bee marks on `THORAX` / `ABDOMEN` rendered with the Bee icon, including the
  existing persisted-token compatibility reader;
- device-local Territory Area v2, Area View, exchange mirror/share, map package
  discovery and the existing D065 import-validation boundary;
- one-shot Area editor navigation (`request -> handle -> consume`);
- no layout-shifting success feedback for routine Bee departure/return actions.

Decision numbering is reconciled in the current documentation: D078 is Bee
marking, D079-D084 are the Area/offline-map decisions and D085 is
ObservationPoint properties/weather.

## Verification status

Source worktrees were clean and matched the requested baselines before the
merge. The Bee-marking production files in `f874def` match the completed
`bee-marking-abdomen` line; its remaining differences were subsequent Area
tests and documentation.

Offline `compileDebugKotlin`, the full debug unit suite (342/342), androidTest
compilation, lint and both debug APK assemblies pass. The preserving Samsung
SM-S938B run reports `OK (250 tests)`: 238 ordinary passes, 12 expected opt-in
skips and no failures. Update-in-place installation kept the DEV Room DB/WAL,
DataStore, installed PMTiles package and Area JSON byte-identical by SHA-256.

Manual smoke on that one combined DEV APK covered the retained vector map,
Objects/Area, Area View, explicit one-shot editing, repeated map -> Objects ->
map navigation without editor replay, Area share chooser, Points Map/Table,
historical detail, Properties, loaded Open-Meteo weather with attribution and
photo-picker cancellation without an attachment. Historical detail rendered a
Bee icon with the mark on its body. There was no active ObservationPoint, so no
field event was fabricated merely to exercise the active Properties entry or a
departure/return; those paths are covered by the passing instrumented tests.

The source branches and worktrees remain intact as integration safety copies.

## Deferred work

- Move Description and photo controls into ObservationPoint creation while
  retaining the Properties screen for later viewing/editing.
- Investigate live Open-Meteo separately only if the provider error repeats.
- Server map transport.
- Area import.
- Manifest recovery.
- Multi-section map generator.
