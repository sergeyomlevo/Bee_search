# Bee Search — Handoff

## Current milestone

MapLibre + foreground GPS implemented; ObservationPoint creation is not yet implemented.

## Last completed commits

- `ee54c16` — Room/DataStore persistence foundation
- `53fc334` — Territory, settings and startup flow

The MapLibre/GPS milestone is currently uncommitted in the working tree.

## Verified

- MapLibre screen opens on the physical Samsung SM-S938B.
- Foreground location permission dialog works.
- Location update requests are started while the map screen is open.
- JVM tests pass.
- Instrumented tests pass: 7/7 through the direct non-streaming runner.
- `assembleDebug`, `assembleDebugAndroidTest`, and `lint` pass.

## Not yet verified

- A real GPS fix was not received during the last device smoke test.
- GPS accuracy display was not verified with a real reading.
- Recenter behavior was not verified with a real reading.

## Open items

- Offline map format, provider, and durable map metadata remain unresolved.
- Historical editing policy remains open.
- Backup strategy is needed before real field data exists.

## Next task

Verify a real GPS fix outdoors. After GPS is confirmed, implement ObservationPoint creation on the map:

- current GPS position;
- temporary point;
- manual correction;
- confirmation;
- Room persistence.

Before implementation, choose the correction UX: draggable marker or fixed crosshair with map movement.

## Important constraints

- Do not implement offline-map persistence yet.
- Do not create placeholder map-state entities.
- Keep `LocationProvider` as the boundary for Android location access.
- ObservationPoint coordinates are user-confirmed coordinates; GPS coordinates remain the original measurement.
- Treat `docs/` and accepted decisions as project truth; this file is only an operational handoff.
