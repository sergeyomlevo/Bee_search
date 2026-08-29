# Bee Search — Handoff

## Current milestone

Manual azimuth entry for the active Bee observation screen is implemented in the dirty worktree on
top of `31fc20b Implement active bee observation workflow`. No commit has been created.

The existing domain semantics remain authoritative:

- azimuth is stored as nullable `FlightCycle.azimuthDeg` / Room `azimuth_deg`;
- the UI targets the Bee card's explicit latest FlightCycle ID;
- an open latest cycle is the current flight, while a closed latest cycle is the most recently
  completed flight;
- repository validation and active-ObservationPoint protection are unchanged;
- an existing value may be replaced or removed (`null`) according to D030;
- Room remains schema version 2.

The compact card now contains a 48 dp text action `Азимут —` / `Азимут 247°` in the existing second
row. It opens a Material dialog with the Bee label, numeric keyboard, integer validation `0..359`,
and `Отмена` / `Сохранить`; an existing value also exposes `Удалить`. The dialog closes only after
the existing repository operation succeeds. Repository/domain errors use the existing persistent
Russian feedback path. No extra success banner is shown because the Room-driven card value is the
direct confirmation.

## Verified

- The worktree was clean at milestone start and HEAD was `31fc20b`.
- JVM tests cover accepted bounds, empty/text/fraction/negative rejection, lack of modulo
  normalization, and display formatting including valid `0°`.
- Compose tests cover saving `247°` to the explicit latest cycle, rejecting `360` without invoking
  persistence, editing/removing an existing value, and a 48 dp azimuth touch target.
- The Room test covers cycle/Bee isolation, replacement, restoration through a recreated repository,
  a short D058 first cycle retaining its azimuth, a normal FlightCycle 2 without a fabricated
  azimuth, and completion without rewriting any cycles.
- Final Gradle validation passes `test assembleDebug assembleDebugAndroidTest lintDebug`.
- Debug and test APKs are installed on Samsung SM-S938B without clearing retained application data.
- Direct Samsung instrumentation passes `RoomPersistenceTest` 28/28.
- Direct isolated Samsung Compose runs pass 4/4 relevant checks: valid save/display, `360`
  validation, removal, and five complete Bee actions visible without scrolling.
- The density test initially exposed multi-line wrapping of the secondary state after adding the
  azimuth action. The state is now constrained to one line with ellipsis, decorative card vertical
  padding is 4 dp, and the final density test passes while both actions retain 48 dp touch targets.

## Not yet physically verified

- The retained phone database currently has no active ObservationPoint and launches on the Territory
  map. No synthetic research row was inserted merely to exercise the dialog.
- A person still needs to verify on a real active observation: numeric keyboard, manual entry of
  `247`, visible `247°`, rejection of `360`, editing/removal, normal `Прилетела` / `Улетела`, and
  restoration after app restart.
- The whole Compose test class can still hit the known device-runner failure `No compose hierarchies
  found`; the same new tests pass when run directly one at a time. This is runner instability, not an
  assertion failure in the isolated checks.

## Documentation

- `docs/product-requirements.md` now identifies manual integer entry as the reliable current path
  before sensor implementation and explicitly preserves the accepted future `HeadingProvider`
  direction from D033.
- `docs/user-workflows.md` describes the concrete compact-card dialog, editing/removal, and the exact
  latest-cycle targeting used by the current screen.
- No new durable decision was added.

## Open items

- D033 automatic compass/sensor capture remains a separate future milestone.
- Offline map source/format and useful high-zoom basemap detail remain unresolved.
- Backup/export is still required before substantial real field data accumulates.

## Next task

Review the dirty diff and perform the focused Samsung manual checklist above using a deliberately
created test ObservationPoint or other user-approved non-research data. Do not commit until explicitly
requested, and do not start sensor-based azimuth capture automatically.
