# Bee Search — Handoff

## Current milestone

Bee preparation for an active ObservationPoint and the focused reliability-hardening follow-up are committed, automatically verified, and physically accepted on Samsung. ObservationPoint creation remains complete. No implementation milestone is currently in progress.

## Last completed commits

- `ee54c16` — Room/DataStore persistence foundation
- `53fc334` — Territory, settings and startup flow
- `50829b4` — MapLibre, foreground GPS and project handoff
- `656519a` — ObservationPoint map placement workflow
- `0db416b` — Bee preparation workflow

The reliability-hardening follow-up is the current HEAD commit containing this handoff.

## Verified

- MapLibre MapView and the temporary demo map display on physical Samsung SM-S938B.
- Foreground location permission and requests work.
- A real GPS fix was received; the UI displayed 3.8 m accuracy and the current position.
- The `Центр` action moved and zoomed the camera to the real current position.
- The first ObservationPoint implementation passed `test`, `assembleDebug`, `assembleDebugAndroidTest`, `lint`, and 8/8 instrumented tests before the manual UX failure was found.
- The manual test did create an active ObservationPoint in Room, and startup recovery displayed it after navigation/restart.
- The corrective implementation passes `test`, `assembleDebug`, `assembleDebugAndroidTest`, and `lint`.
- Direct non-streaming instrumentation passes 10/10 on Samsung SM-S938B, including a Compose layout test that keeps the screen-space crosshair centered across camera-coordinate recomposition and a completion-confirmation UI test.
- The corrective APK was installed with `-r`; the existing active ObservationPoint remained intact and was recovered from Room after cold launch.
- The recovery screen now exposes explicit `Завершить наблюдение` / confirmation actions backed by the existing repository transaction. It routes to the Territory map only after Room reports successful completion, so the retained active point can be completed before creating the next one.
- Physical use confirmed that completing the restored point returns to the current Territory map while retaining the completed observation in Room.
- The ObservationPoint creation layout now uses a dedicated map-dominant mode: the AppBar and Territory summary are removed while editing, MapView fills the available safe area, and compact GPS/selection information plus `К GPS`, `Отмена`, and `Подтвердить точку` overlay the map without reducing its viewport.
- The updated layout passes `test`, `assembleDebug`, `assembleDebugAndroidTest`, and `lint`; direct non-streaming instrumentation still passes 10/10 on Samsung SM-S938B.
- The updated APK was installed with `--no-streaming -r`, and a physical-device screenshot confirms that the creation-mode MapView occupies approximately 80% of the full display height with the crosshair centered and all three actions visible at the current large system font scale.
- A subsequent UI-polish build compacts the configured-observer placement overlay to two user-facing lines: original-fix GPS accuracy and geodesic manual offset from that saved fix to the current camera-center/crosshair coordinates. Raw coordinates and the configured observer code are hidden. The continuously updated device marker is a smaller blue dot with a thin white stroke; crosshair size and contrast are unchanged.
- The UI-polish build passes `test`, `assembleDebug`, `assembleDebugAndroidTest`, and `lint`; its direct non-streaming instrumentation passes 10/10 on Samsung SM-S938B, and the APK is installed over the retained app data.
- JVM tests cover zero/geodesic offset and field formatting: one decimal below 10 m and whole meters from 10 m. The offset is derived in UI code and is not persisted.
- A physical Samsung screenshot confirms the final compact overlay renders as two unwrapped lines (`Точность GPS: ±3,8 м`, `Смещение от GPS: 13 м`) while the map, fixed crosshair, secondary blue GPS marker, and all actions remain visible.
- The user completed the focused Samsung field-UX checklist successfully: moving the map changes the derived offset without changing original GPS accuracy, the compact overlay leaves the map dominant, `К GPS` returns the offset near zero, and all three placement actions remain accessible.
- Bee preparation uses the current visual-mark catalog: `WHITE`, `YELLOW`, `BLUE`, `RED`, `GREEN` with `NONE`, `RIGHT_WING`, `LEFT_WING`. Availability is calculated from unused `mark_color + mark_position` combinations; no numeric Bee limit or Room schema change was introduced.
- Prepared Bee records are persisted immediately through the existing `ObservationRepository`. The screen restores its list from Room, blocks duplicate combinations, and disables add/remove after any FlightCycle exists for the active point.
- The preparation screen provides direct color and position chips, readable text plus a color swatch, remove actions before first release, and a disabled visible `Выпустить всех` transition; it does not create FlightCycle records.
- Final `test`, `assembleDebug`, `assembleDebugAndroidTest`, and `lint` pass. The debug APK was installed over retained Samsung data and the app was opened.
- On Samsung, direct sequential instrumented execution passes 11/11 `RoomPersistenceTest` tests and 3/3 Bee-preparation Compose tests. A full `connectedDebugAndroidTest` run remains runner-flaky: after some classes it can lose the Compose hierarchy, including an existing crosshair test. Do not interpret that aggregate failure as a product failure.
- Physical Samsung smoke test confirms that a prepared Bee can be removed before first release. After stopping the app and rebooting the phone, Bee Search restored the active ObservationPoint, reopened Bee preparation, and preserved the prepared Bee set from Room.
- Reliability hardening removes `getLastKnownLocation` as an input to the creation draft, so an original GPS measurement always comes from a new location update. It also stops foreground location updates when the map screen is not visible or the Activity is stopped, releases MapLibre `MapView` when its Compose view leaves composition, maps a Territory-code SQLite uniqueness race to a user-facing domain error, checks the row count when saving flight azimuth, and requires an explicit valid Bee mark selection rather than silently replacing an unavailable one.
- The reliability pass passes `test`, `assembleDebug`, `assembleDebugAndroidTest`, and `lint`. On Samsung SM-S938B, direct `RoomPersistenceTest` passes 11/11 and the isolated new BeeSelector test passes 1/1. The current debug APK was installed with `adb install -r`, preserving app data.
- The focused reliability follow-up balances MapView shutdown when the Compose view is released while the Activity remains active: only lifecycle calls not already delivered are sent in `onPause` → `onStop` → `onDestroy` order. Lifecycle creation catch-up remains delegated to `Lifecycle.addObserver()`.
- Location permission truth and tracking activity are now separate inputs. Leaving the map or stopping the Activity cancels updates and returns to `WaitingForFix` without claiming that permission was revoked; returning to an eligible foreground map starts a new collection and therefore still requires a fresh fix.
- Location permission is requested only by the existing explicit `Разрешить доступ к местоположению` control. Lifecycle transitions no longer reopen the system dialog automatically.
- `updateTerritory`, like `createTerritory`, maps the Room/SQLite unique-code constraint to `DuplicateTerritoryCodeException`. The database UNIQUE constraint remains authoritative, and the instrumented Room test covers both operations.
- The focused follow-up passes `test`, `assembleDebug`, `assembleDebugAndroidTest`, and `lint`. Its targeted `RoomPersistenceTest` passes 11/11 on Samsung SM-S938B.
- Physical Samsung verification confirms repeated map → other screen → map navigation does not blank or degrade the map; the map and current point restore after restart; granted location permission remains correct; the permission dialog does not reappear automatically; and the explicit `Разрешить доступ к местоположению` action still opens the system dialog when permission is needed. The reliability follow-up is physically accepted.

## Not yet verified

- No remaining physical verification is required for the current Bee preparation and reliability-hardening milestone.

## Open items

- Offline map format, provider, and durable map metadata remain unresolved.
- The temporary MapLibre demo source has no useful basemap detail at high zoom. This is a known source/style limitation and is not part of the current fix.
- Historical editing policy remains open.
- Backup strategy is needed before real field data exists.

## Next task

The next planned milestone is the initial group release. Do not start it without an explicit user request.

## Important constraints

- Do not implement offline-map persistence yet.
- Do not create placeholder map-state entities.
- Keep `LocationProvider` as the boundary for Android location access.
- ObservationPoint coordinates are user-confirmed coordinates; GPS coordinates remain the original measurement.
- The correction UX is the accepted fixed center crosshair; do not replace it with a draggable marker.
- Treat `docs/` and accepted decisions as project truth; this file is only an operational handoff.
