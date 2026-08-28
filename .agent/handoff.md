# Bee Search — Handoff

## Current milestone

The D057 no-bees UI milestone is implemented and automatically verified, but still awaits the requested manual field-flow check. An empty active ObservationPoint now offers an explicit confirmed `Пчёлы отсутствуют` action backed by the existing atomic `recordNoBeesFound` repository operation. Expected domain failures are shown as Russian user messages rather than raw exception text. Room schema remains v2.

## Last completed commits

- `ee54c16` — Room/DataStore persistence foundation
- `53fc334` — Territory, settings and startup flow
- `50829b4` — MapLibre, foreground GPS and project handoff
- `656519a` — ObservationPoint map placement workflow
- `0db416b` — Bee preparation workflow
- `ec957cd` — Project ideas workflow
- `3304e94` — Observation metadata, Bee presence result, and Room v2
- `8db15b0` — Delayed departure analysis rule
- `a689357` — Agent decision policy refinement

The decision-policy refinement is the current HEAD commit; this no-bees UI milestone remains uncommitted for review.

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
- D056 records `observationYear` from the device-local calendar at creation and assigns `pointNumber` transactionally within `Territory + observationYear + observerCode`. UUID remains identity, the four-column Room UNIQUE index is authoritative, and O005 remains open for the final display-code format.
- D057 adds nullable `BeePresenceResult`: first Bee atomically sets `BEES_FOUND`, removing the final prepared Bee before release restores `null`, `NO_BEES_FOUND` blocks Bee creation, ordinary completion rejects `null`, and `recordNoBeesFound` atomically saves the explicit result and completion timestamp.
- Room schema is version 2 with an explicit v1→v2 migration. Prototype rows keep every UUID and row; year is derived from `created_at` in the device's local zone at migration time, and numbering is assigned by `created_at`, then UUID, per scope. Existing points with Bee rows backfill to `BEES_FOUND`; empty points backfill to `null`.
- Final `test`, `assembleDebug`, `assembleDebugAndroidTest`, and `lint` pass. The exported v2 schema is present. Targeted Samsung execution passes 23/23 tests: 22 Room/domain tests plus the v1→v2 migration/backfill/schema-validation test.
- The final debug APK was installed successfully on Samsung SM-S938B and Bee Search launched without an immediate Room/opening crash. The package had been absent before installation, so this launch used a fresh database rather than the phone's former prototype v1 database.
- D058 preserves the common first group-release timestamp and derives `FlightCycle.isExcludedFromFlightDurationAnalysis` only when cycle 1 has returned in less than 60 seconds. The raw cycle remains unchanged; exactly 60 seconds and later short cycles are not excluded. No Room field, schema change, or nest-distance analysis was added.
- The D058 JVM boundary tests pass as part of `test`; `assembleDebug`, `assembleDebugAndroidTest`, and `lint` also pass. Targeted `RoomPersistenceTest` execution passes 23/23 on Samsung, including closing a 20-second first cycle and later creating FlightCycle 2 with its actual individual departure timestamp. The final debug APK was reinstalled and launched after device testing.
- The Bee-preparation screen now shows `Пчёлы отсутствуют` only while the active point has no Bee and `beePresenceResult` is still `null`. A confirmation dialog explains that the historical point will be saved and completed; cancellation does not invoke persistence, while confirmation calls the existing atomic repository operation and returns routing to the current Territory after Room no longer reports an active point.
- Ordinary completion is disabled while `beePresenceResult` is `null`. Repository safeguards remain authoritative: an attempted no-bees result after a Bee was found is rejected without changing the Bee or `BEES_FOUND`.
- Expected domain exceptions now pass through one compact Russian UI-message mapping. The mappings cover a missing Bee presence result, conflicting `BEES_FOUND` / `NO_BEES_FOUND`, inactive or completed ObservationPoint, and the existing expected Territory, Bee, FlightCycle, observer-code, time, and azimuth errors. Unexpected failures use an operation-specific neutral Russian fallback instead of exposing `exception.message`.
- Final `test`, `assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` pass. On Samsung SM-S938B, direct final execution passes the v1→v2 migration test 1/1, `RoomPersistenceTest` 24/24, and `ResumeObservationScreenTest` 6/6. The final debug and test APKs are installed.
- Removing the explicit `debugRuntimeOnly(kotlinx-serialization-json)` was tested rather than inferred. Without it, the Samsung migration test fails with `AbstractMethodError`: DataStore contributes serialization 1.7.3 to the debug app while Room 2.8 MigrationTestHelper uses 1.8.x-generated serializers. The dependency remains at 1.8.1 with a Gradle comment documenting this runtime alignment.
- Backup/export was not implemented. The risk is already tracked as open decision O007 and in the architecture backup section, so no duplicate idea entry was added.

## Not yet verified

- A real retained Bee Search v1 database was not available on the Samsung for a manual in-place upgrade. The equivalent v1→v2 migration, preservation, backfill, and Room schema validation pass as an instrumented test on that device.
- The new year, number, and Bee presence values are not yet exposed by a new UI, so there is no additional visual field-UX claim in this milestone.
- D058 has no dedicated UI yet because the initial group-release/observation screen is still a future milestone; its eventual field interaction therefore remains to be verified when that UI exists.
- The new no-bees flow has not yet been manually exercised through both requested Samsung scenarios. Automated device tests cover persistence and Compose behavior, but a person still needs to verify the wording, cancellation, successful return to the map, cold-start routing, next-point creation, and action unavailability after adding a Bee.

## Open items

- Offline map format, provider, and durable map metadata remain unresolved.
- The temporary MapLibre demo source has no useful basemap detail at high zoom. This is a known source/style limitation and is not part of the current fix.
- Historical editing policy remains open.
- Backup strategy is needed before real field data exists.

## Next task

Manually verify the two no-bees scenarios on Samsung before accepting or committing this milestone. Do not start the initial group-release milestone automatically. Ideas I001–I004 remain non-authoritative unless explicitly promoted.

## Important constraints

- Do not implement offline-map persistence yet.
- Do not create placeholder map-state entities.
- Keep `LocationProvider` as the boundary for Android location access.
- ObservationPoint coordinates are user-confirmed coordinates; GPS coordinates remain the original measurement.
- The correction UX is the accepted fixed center crosshair; do not replace it with a draggable marker.
- Treat `docs/` and accepted decisions as project truth; this file is only an operational handoff.
