# Bee Search — Handoff

## Current milestone

ObservationPoint creation is uncommitted and remains open after the first manual Samsung test found two UX bugs. A corrective implementation is present in the working tree and awaits a focused phone retest.

## Last completed commits

- `ee54c16` — Room/DataStore persistence foundation
- `53fc334` — Territory, settings and startup flow
- `50829b4` — MapLibre, foreground GPS and project handoff

The ObservationPoint creation milestone is currently uncommitted in the working tree.

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
- The ObservationPoint creation layout now uses a dedicated map-dominant mode: the AppBar and Territory summary are removed while editing, MapView fills the available safe area, and compact GPS/selection information plus `К GPS`, `Отмена`, and `Подтвердить точку` overlay the map without reducing its viewport.
- The updated layout passes `test`, `assembleDebug`, `assembleDebugAndroidTest`, and `lint`; direct non-streaming instrumentation still passes 10/10 on Samsung SM-S938B.
- The updated APK was installed with `--no-streaming -r`, and a physical-device screenshot confirms that the creation-mode MapView occupies approximately 80% of the full display height with the crosshair centered and all three actions visible at the current large system font scale.
- A subsequent UI-polish build compacts the configured-observer placement overlay to two user-facing lines: original-fix GPS accuracy and geodesic manual offset from that saved fix to the current camera-center/crosshair coordinates. Raw coordinates and the configured observer code are hidden. The continuously updated device marker is a smaller blue dot with a thin white stroke; crosshair size and contrast are unchanged.
- The UI-polish build passes `test`, `assembleDebug`, `assembleDebugAndroidTest`, and `lint`; its direct non-streaming instrumentation passes 10/10 on Samsung SM-S938B, and the APK is installed over the retained app data.
- JVM tests cover zero/geodesic offset and field formatting: one decimal below 10 m and whole meters from 10 m. The offset is derived in UI code and is not persisted.
- A physical Samsung screenshot confirms the final compact overlay renders as two unwrapped lines (`Точность GPS: ±3,8 м`, `Смещение от GPS: 13 м`) while the map, fixed crosshair, secondary blue GPS marker, and all actions remain visible.

## Not yet verified

- The first manual test reported that the apparent selected point moved with the map instead of remaining as an unmistakable fixed crosshair.
- The first manual test reported that `К GPS` produced no visible camera movement.
- The corrective build makes the selected-point reticle a larger high-contrast Compose screen overlay, removes the ambiguous original-GPS map ring, and sends `К GPS` through an explicit draft camera request using the original fix while preserving zoom. This still requires physical retest.
- The completion action is visible in the installed Samsung build, but it has deliberately not been pressed by the agent; actual completion and return to the map still require the user's explicit confirmation.
- The map-dominant creation layout is visually verified by screenshot, but precise pan/zoom ergonomics and button use still require the user's hands-on Samsung retest.
- During screenshot collection the device subsequently displayed a restored active ObservationPoint. The agent did not complete or delete it; review the point on the phone before starting another creation attempt.

## Open items

- Offline map format, provider, and durable map metadata remain unresolved.
- The temporary MapLibre demo source has no useful basemap detail at high zoom. This is a known source/style limitation and is not part of the current fix.
- Historical editing policy remains open.
- Backup strategy is needed before real field data exists.

## Next task

Run the manual Samsung smoke test:

1. on the restored active-point screen, press `Завершить наблюдение`, review the confirmation dialog, and confirm;
2. verify the app returns to the existing Territory map only after successful completion;
3. obtain a GPS fix and start `Новая точка`;
4. pan the map and verify the high-contrast crosshair stays fixed in the viewport center;
5. pinch-zoom and verify the crosshair stays fixed;
6. verify `Смещение от GPS` changes as the map moves while original GPS accuracy remains unchanged;
7. press `К GPS` and verify the camera returns to the original captured fix without resetting zoom and the displayed offset returns near zero;
8. move the map again, confirm, and verify Room stores the coordinates under the crosshair.

After this smoke test is accepted, continue with Bee preparation. Do not start that milestone before reviewing the phone result.

## Important constraints

- Do not implement offline-map persistence yet.
- Do not create placeholder map-state entities.
- Keep `LocationProvider` as the boundary for Android location access.
- ObservationPoint coordinates are user-confirmed coordinates; GPS coordinates remain the original measurement.
- The correction UX is the accepted fixed center crosshair; do not replace it with a draggable marker.
- Treat `docs/` and accepted decisions as project truth; this file is only an operational handoff.
