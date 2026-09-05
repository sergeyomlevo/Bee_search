# Bee Search — Handoff

This file is operational continuation context, not project truth. Current
`docs/`, ACCEPTED decisions, explicit user instructions, and verified
repository state take precedence.

## Current state

The repository currently uses Room schema v5 with separate saved `Territory`
and `Observer` entities under ACCEPTED D061 and the non-blocking Settings flow
under ACCEPTED D062. Each new `ObservationPoint` stores immutable
`territory_id` and `observer_id` links. The one-time migration 4 → 5 reset was
limited to the approved development-stage test hierarchy; it is not a general
destructive-migration policy.

ACCEPTED D063 selects versioned PMTiles as the offline vector Map Package
format. The benchmark integration and local rendering PoC are committed; the
generated benchmark artifact remains outside this repository. Server API,
package manifest details, downloader, update cadence, and satellite imagery
lifecycle remain open. `docs/server-sync-architecture.md` remains a
non-normative draft; its candidate server/sync decisions still require explicit
review before implementation.

The exact benchmark BBOX remains:

```text
west  = 42.288289
south = 56.153038
east  = 42.729915
north = 56.444694
27.2 × 32.4 km; approximately 883.6 km²
```

The generated artifact is outside the repository at
`C:\App\Bee_search_test_maps\pmtiles\territory-benchmark-v1.pmtiles`. The
committed debug integration expects its device copy at
`files/map-poc/territory-benchmark-v1.pmtiles`. These paths describe the
existing benchmark setup, not a production acquisition design.

The current uncommitted work adds minimal epistemic discipline to the existing
decision policy and a signal-based evaluation policy and empty log under
`.agent/`. `AGENTS.md` routes agents to those files without making the log part
of routine implementation context. It does not change Android code, Room, UI,
domain behavior, accepted decisions, decision statuses, autonomy classes,
preference values, taxonomy, confidence, scope, or lifecycle.

## Historical reported verification

Earlier milestone reports record that the full Gradle test/build/lint suite
passed for the Territory/Observer implementation. A Samsung upgrade using
`adb install -r -t` was reported successful without uninstall or data clearing;
Settings and Observer creation were exercised. The Compose Territory form was
not reliably completed by automation, and the old report left multi-entry
switching and linked-delete for manual review.

The user later reported successful physical-device checks on Samsung SM-S938B
for the Sapunovo offline profile: airplane mode, reboot, cold start, geometry,
Cyrillic place labels, water/waterway labels, road labels, and no observed
network dependency. The user also reported successful rendering, readability,
responsiveness, and smooth pan/zoom for the exact Territory benchmark above.

Those checks were not rerun during the current documentation-only maintenance.
Treat them as historical reported evidence, not as independent current
verification.

## Unresolved or uncertain items

The prior handoff required Gradle checks and a data-safe Samsung review of
Territory-specific coverage before committing that milestone. Commit `a723ee7`
later recorded the implementation, but the currently inspected docs and Git
history do not establish whether every listed manual review step was completed.
That old gate's final status is therefore unknown; do not infer either success
or failure. Recheck only if a future task depends on those exact behaviors.

Production Map Package acquisition and the candidate server/sync decisions
remain future project work. Do not implement them merely because the committed
draft describes possible directions.

## Next task

Review the epistemic-discipline and signal-evaluation governance diff. Do not
commit or push it without a separate user instruction.
