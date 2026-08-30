# Bee Search — Handoff

## Current milestone

The previous manual-azimuth milestone is committed as
`2c1f86d Add manual flight azimuth capture`.

The one-tap heading milestone is implemented and reviewed. It replaces manual field entry with
one-tap capture of live true/geographic heading while preserving the existing nullable
`FlightCycle.azimuthDeg` field and repository set/clear operations.
The approved one-capture-per-departure rule is persisted in Room schema version 4. Version 3 added
the field; version 4 is a non-structural compatibility migration for an intermediate v3 identity.

D059 is documented as the durable research meaning of persisted azimuth:

- `0°` is true/geographic north;
- the phone's upper short edge is the observed direction;
- Android rotation-vector sensor data provides magnetic heading;
- `GeomagneticField` corrects it using the confirmed ObservationPoint coordinates, the system time
  of each heading calculation, and altitude `0 m` because altitude is not stored;
- only the corrected, normalized true heading can be persisted after an explicit tap.

D060 records the field-capture semantics:

- each new FlightCycle starts with `azimuthDeg == null` and
  `azimuthCaptureConsumed == false`;
- field capture atomically stores the true heading and changes the consumed flag to `true`;
- Undo clears only `azimuthDeg`, so it cannot reopen the measurement opportunity;
- recovery after restart uses the persisted consumed flag rather than transient UI state.

## Implemented

- One lifecycle-aware, injectable `HeadingProvider` serves the whole observation screen.
- `TYPE_ROTATION_VECTOR` is preferred, with `TYPE_GEOMAGNETIC_ROTATION_VECTOR` fallback.
- Display rotation is remapped before calculating the heading of the phone's upper edge.
- UI updates are conflated and deduplicated by rounded degree and reported sensor accuracy.
- Bee cards remain two rows: a 40 dp colored mark with `КП`/`КЛ`, full state and timer, then a
  48 dp heading control and the existing 48 dp `ПРИЛЕТЕЛА` / `УЛЕТЕЛА` action.
- Text color names and the manual numeric azimuth dialog were removed from the field screen.
- A null, not-yet-consumed current-cycle azimuth shows live true heading. One tap uses the dedicated
  atomic repository operation for the explicit current FlightCycle; a persisted value then remains
  fixed and the control becomes read-only.
- The heading control is interactive only for an open current FlightCycle (`returnTime == null`).
  After `Прилетела` the closed cycle shows its saved azimuth read-only, or `—°` when none was saved;
  after `Улетела` the new open cycle restores live one-tap capture without changing card dimensions.
- A successful capture shows a fixed top Undo banner for about three seconds. Its token and explicit
  FlightCycle ID prevent an older timeout/action from affecting a newer capture.
- Undo uses the existing nullable set operation, clears only the value and leaves
  `azimuthCaptureConsumed == true`; that FlightCycle remains read-only as `—°`.
- Unavailable or unreliable heading cannot create a fabricated `0°`; return, departure and
  completion remain available. Low reported accuracy is shown with a visible warning.
- A newly created FlightCycle starts with `azimuthDeg == null` and
  `azimuthCaptureConsumed == false`, so the next departure gets one fresh capture opportunity.
- Migration 2→3 backfills `azimuthCaptureConsumed = true` for existing cycles with an azimuth and
  `false` for cycles without one. The full 1→2→3 migration path remains supported.
- Compatibility migration 3→4 changes no user table. It validates the final v3 structure and lets
  Room replace the intermediate identity hash; 3→4, repeated v4 open and 1→2→3→4 are covered.
- The lower card row uses a weighted spacer: heading remains left while `ПРИЛЕТЕЛА` / `УЛЕТЕЛА`
  stays against the right side without reducing either 48 dp touch target.
- The observation screen now renders one top feedback slot. Persistent feedback keeps priority;
  azimuth Undo replaces ordinary success immediately and suppresses later ordinary success during
  its own three-second window. Ordinary feedback also uses three seconds.
- Ordinary success and azimuth Undo now share the title-side slot inside the existing 52 dp
  observation header. They are single-line (`Прилёт сохранён`, `Вылет сохранён`,
  `N° сохранён — ОТМЕНИТЬ`), never add list height, and leave `Завершить` as a separate visible,
  clickable header action.

## Automatically verified

- `test assembleDebug assembleDebugAndroidTest lintDebug` passes.
- JVM tests cover normalization, positive/negative declination, both north-boundary crossings and
  rounding without producing `360°`.
- Compose tests cover mark/state density, 48 dp controls, live capture/freeze, one-shot consumption,
  Undo remaining disabled, stale-context protection, timeout behavior, failure retry, unavailable
  heading, persisted consumed-state recovery and a new cycle returning to live heading.
- Samsung direct instrumentation passes `BeeSearchMigrationTest` 5/5,
  `RoomPersistenceTest` 31/31, `BeeObservationScreenTest` 18/18 and
  `FeedbackBannerTest` 4/4, and `ResumeObservationScreenTest` 10/10.
- The observation presentation test verifies that transient feedback stays within the header,
  does not overlap `Завершить` or the first Bee card, remains one line, and does not move the list
  when it appears or disappears.
- Repository tests cover atomic value/consumed updates, technical rollback, closed-cycle rejection,
  repeat rejection after capture and Undo, generic history/edit set/clear compatibility, and reset on
  the next FlightCycle.
- `git diff --check` is clean apart from Git's informational LF/CRLF warnings.

## Physical verification still required

The updated debug and test APKs were installed with `adb install -r` on Samsung SM-S938B without
clearing application data. After the isolated tests the retained application opened on the Territory
map with no active ObservationPoint, so the real event sequence was not executed against research
data merely to create a manual smoke scenario.

The retained device database was safely upgraded from the intermediate v3 identity to v4. Before
and after canonical hashes match for all user tables: 1 Territory, 31 ObservationPoints, 152 Bees
and 169 FlightCycles. Fourteen persisted azimuth values and their consumed flags are unchanged.
Normal process restart and force-stop → launch both open MainActivity on the Territory map without a
Room crash. Diagnostic copies before and after are kept under the system temporary directory.

On Samsung, verify with safe test data:

1. `Прилёт сохранён`, `Вылет сохранён` and azimuth Undo remain in the header without covering the
   first Bee card or `Завершить`, and the list does not jump;
2. live heading changes when the phone turns and follows the upper short edge;
3. a known physical direction is plausible relative to true north;
4. one tap freezes the displayed value; Undo removes it but leaves that cycle disabled as `—°`;
5. a newer capture replaces the previous Undo and the banner disappears after about three seconds;
6. `Прилетела`, `Улетела`, restart recovery of consumed-without-value state and a new FlightCycle
   with a fresh capture opportunity work;
7. sensor unavailable/low-accuracy behavior is understandable;
8. six cards, touch targets, outdoor contrast and white/`КП`/`КЛ` marks are usable on the device;
9. maximum-brightness and daylight readability.

## Open items

- Sensor correctness, physical upper-edge direction, true-north plausibility, device density and
  daylight contrast are not proven by builds or fake-provider Compose tests.
- Offline map source/format and useful high-zoom basemap detail remain unresolved.
- Backup/export remains required before substantial real field data accumulates.

## Next task

Manually verify the single-use capture, Undo-without-recapture, restart recovery and next-departure
reset on safe test data. Then continue with the next explicitly selected field-work milestone.
