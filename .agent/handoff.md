# Bee Search — Handoff

This file is operational continuation context, not project truth. Current
`docs/`, ACCEPTED decisions, explicit user instructions, and verified
repository state take precedence.

For any UI work, read `.agent/ui-policy.md` before implementation.

## Observation workflow milestone (D075, 2026-09-15)

The initial group release is retired. Confirming a prepared point with `Добавить`
creates the `ObservationPoint` with `bee_presence_result = null` and opens the
observation; no Bee is created in advance. The observation screen derives the
available mark choices (5 catalog colors × `NONE` / `RIGHT_WING` / `LEFT_WING`,
minus marks of existing bees); `NONE` is displayed as `Грудь`. A choice card has
a grey background, the state `Выбор` and the action `УЛЕТЕЛА`; it is not a Bee
and does not consume the limit.

`startFirstFlight` atomically creates the real Bee, its `FlightCycle 1` with that
bee's individual `departure_time`, and `BEES_FOUND`. The real bee card appears
above the choices and reuses the existing in-flight card. Local `↶` on a first
departure deletes the bee together with its cycle and returns the mark as a
choice, but only until a `return_time` was ever recorded; afterwards that
deletion is impossible even if the return is later undone. At most 10 real bees
per point. First-cycle 60-second analysis exclusion (D058) is retired; D022,
D058 and D067 are SUPERSEDED by D075. The negative result `NO_BEES_FOUND` remains
available both from the preparation draft and from an open observation with no
bees yet.

Room schema and the backup contract are unchanged: the column
`initial_group_launch_correction_eligible` keeps its name and type while its
meaning narrows to "this first departure can still be cancelled".

Legacy UI still present but unreachable from the active route:
`ui/observation/BeePreparationScreen.kt` and `BeeSelector` remain in the tree
together with `ResumeObservationScreenTest`; their removal is a separate
decision and was not part of this milestone.

## Help and data management milestone (2026-09-10)

The Settings surface now routes to separate common `ui/help` and `ui/data`
features through the existing app route mechanism. Help is fully offline and
documents only the current workflow. Data export uses Android SAF
`CreateDocument` and a thin adapter over the existing D069-D073 logical
`BackupService`; restore UI remains out of scope. Observation cleanup shows
counts and explicit confirmation, then deletes FlightCycle, Bee and
ObservationPoint rows in one Room transaction while preserving Territory,
Observer, DataStore settings and map packages.

Production compilation, unit tests, debug assembly and androidTest compilation
pass. On Samsung SM-S938B, the 11 focused Help/Data/export/cleanup tests pass;
the full connected suite also passed with its expected opt-in map/bootstrap
skips. No beta variant, restore UI, signing work or general MainViewModel
decomposition has started. The next implementation milestone requires an
explicit user choice.

## Agent routing setup (2026-09-08)

Project routing is configured in `.codex/config.toml`; see the routing section
in `AGENTS.md` and the `luna-worker` / `luna-verifier` role files. TOML parsing
and strict Codex `config/read` verified the project defaults as Luna/medium.
The CLI reported a successful custom-role Git smoke, but did not expose child
model/effort metadata. Separate direct spawns explicitly passed Luna/medium;
that tool has no custom-agent selector. Start a fresh Codex session to load the
new project configuration. The app milestone below is unchanged.

## Large local Map Package completed (2026-09-09)

The user-selected production `MapCoverageSelection` rectangle is persisted for
the current DEV Territory as W/S/E/N `42.2055994 / 55.7058541 / 43.0389210 /
56.4944621`. Builder metrics are 51.680839730550545 x 87.68932983280553 km and
4531.858201168607 km2.

The Volga extract polygon alone does not cover the north-west part of this
rectangle. The completed source is the ignored local streaming osmium merge
`tools/map-poc/work/central-volga-260830-v2.osm.pbf` (central + volga Geofabrik
260830 extracts), 1,643,902,028 bytes, SHA256
`354BF5B246ADFF9D8C6C3DF885D6CC541443B647C577ABD25CC534E372B468B4`.
Its header legitimately has no bbox; coverage was derived from the two official
extract polygons, including a 301 x 301 union sample with zero uncovered
points. Planetiler read 220,950,594 nodes, 23,733,503 ways and 665,529
relations. An earlier locally merged source contained nodes but no ways; it is
invalid. The builder now rejects that condition before creating a manifest.

The completed immutable workstation package is
`tools/map-poc/work/packages/user-large-coverage-20260909-v2`. Planetiler took
90.144 s; total build took 92.264 s. The artifact is PMTiles v3, 21,107,744
bytes, zoom 8-15, header bounds W/S/E/N `42.2055993 / 55.7058541 / 43.0389210 /
56.4944621`, 13,371 addressed tiles, 13,282 entries and 13,167 tile contents.
SHA256 is
`315f9a87d26ab1120518a942e7df0a6c83a3fa5ee9adabbf182515cc3a625a9a`.
The artifact-derived D065 manifest uses schema 1, package ID
`user-large-coverage-20260909-v2`, dataset `central-volga-260830`, profile
`bee-search-field/v1`, style `vector-pmtiles-v1`, one exact selected coverage
fragment and policy `coverage_fragments_only`.

`push-map-package-for-import.ps1` delivered the pair only to
`/sdcard/Download/BeeSearch/user-large-coverage-20260909-v2`, verified remote
SHA256, and now media-scans both files for immediate DocumentsUI visibility.
The actual Bee Search DEV UI imported manifest then PMTiles, passed D065
validation, created an immutable directory and atomically changed the DataStore
pointer. Manual vector-map pan and rendering were observed. An opt-in device
test rendered features at four distant points, overview z9 and overscaled z20;
warm in-process camera/query observations were 0.001-0.069 s. No fatal,
MapLibre, or OOM error was observed.

Cold start retained Ready/active state. With system airplane mode enabled and
an IP ping failing, another cold start rendered the local map; the four remote
points and z20 device test passed offline. Airplane mode was restored to its
original disabled state. The required negative UI replacement (new manifest +
old PMTiles) was rejected with the size-mismatch message in 2.594 s; staging was
empty afterward, the DataStore pointer and immutable-directory count did not
change, and the large active package continued rendering.

Repeated Android activity-result delivery during diagnostics exposed duplicate
immutable copies of the already active package. Import now stages and validates
the candidate, then treats an identical active manifest as an idempotent
success. The device idempotence test retained the same active path and directory
count. Existing inactive DEV duplicates from the pre-fix diagnostic runs were
left untouched; server/downloader work remains out of scope. The device is left
with `org.beesearch.app.dev` open on the active `Векторная карта`; the protected
`org.beesearch.app` was not targeted.

## Current state

The repository currently uses Room schema v5 with separate saved `Territory`
and `Observer` entities under ACCEPTED D061 and the non-blocking Settings flow
under ACCEPTED D062. Each new `ObservationPoint` stores immutable
`territory_id` and `observer_id` links. The one-time migration 4 → 5 reset was
limited to the approved development-stage test hierarchy; it is not a general
destructive-migration policy.

ACCEPTED D063 selects versioned PMTiles as the offline vector Map Package
format, and ACCEPTED D065 fixes its sidecar-manifest v1 contract. The current
uncommitted milestone implements the first production acquisition adapter:
Android Files picker selects manifest then PMTiles, stages both under
app-private storage, validates D065 compatibility/integrity and atomically
switches a DataStore pointer for the current Territory. Renderer uses only that
active validated package; the normal `Векторная карта` control no longer uses a
benchmark fixture. A failed replacement retains the old active package. Server
download, progress/resume, update cadence and satellite imagery lifecycle
remain open. `docs/server-sync-architecture.md` remains a non-normative draft;
its candidate server/sync decisions still require explicit review before
implementation.

The exact benchmark BBOX remains:

```text
west  = 42.288289
south = 56.153038
east  = 42.729915
north = 56.444694
27.2 × 32.4 km; approximately 883.6 km²
```

The generated artifact is outside the repository at
`C:\App\Bee_search_test_maps\pmtiles\territory-benchmark-v1.pmtiles`; its
D065 sidecar is checked in at
`tools/map-poc/fixtures/territory-benchmark-v1.pmtiles.manifest.json`. For
manual-import development verification, select the pair through the Android
Files picker in `org.beesearch.app.dev`. The old `files/map-poc/` copy remains
only for opt-in diagnostic PoC checks, never for the normal vector renderer.

The current uncommitted milestone implements ACCEPTED D064. Confirming map
coordinates now opens a transient `Подготовка точки` draft rather than creating
an ObservationPoint. Before a meaningful result, explicit `Отмена` and system
Back discard that draft and return to the main Territory map without a confirm
dialog or Room research records. The first Bee creates ObservationPoint + Bee +
`BEES_FOUND` atomically; explicit `Пчёлы отсутствуют` creates ObservationPoint
with `NO_BEES_FOUND` and completion atomically. Existing persistent observations are
not deleted by later navigation. The user reported that this transient lifecycle,
including `Отмена` on `Подготовка точки`, was verified on Samsung.

The D064 data lifecycle is accepted. Its preparation UI has been reworked in
the current uncommitted worktree: prepared Bee rows, the repeat editor,
`Выпустить всех`, and collapsed `ВАЖНО! Первый выпуск` guidance are one natural
scroll container. On entry and after a successful Add, the viewport returns the
editor beside the newest Bee rows; a normal user scroll toward the list gives
the list the viewport and moves the editor below it. The header and field
labels are more compact (`Цвет метки`, `Расположение метки`); the separate
latest-Bee card and sticky working area were removed. Opening the guidance
uses the same scroll container and moves to the instruction, so the editor does
not cover its text. The user explicitly accepted this unified preparation UI
after Samsung review. Do not treat viewport positioning as a domain or
persistence change.

The active-observation cards in the same uncommitted milestone are now ordered
for field work without changing derived Bee/FlightCycle semantics: `В полёте`
cards come first, then `На точке`; flights sort first by current
`FlightCycle.sequenceNumber` so higher cycles are nearer the group boundary,
then by current duration so the longest flight of the same cycle is nearer that
boundary. The `На точке` ordering remains longest stay nearest that boundary.
Equal state-start timestamps retain input order, so timers alone do not
reshuffle cards. `В полёте` has a stable light-blue card background and dark-blue
border; `На точке` retains its existing container. Both retain explicit state
text. The header deliberately says only `Наблюдение`, leaving the primary
`Завершить` action fully visible at large font scale. When a successful
`Улетела` moves a Bee into the flight group, the list scrolls only if its
reordered card is not fully visible, leaving its azimuth action immediately
available. Feedback is rendered below that stable header, never in its narrow
title slot or as an overlay over cards: ordinary transient success and
persistent error use the full width and push the list below them.

ACCEPTED D066 adds a local persisted correction to only the Bee card whose
latest FlightCycle has a safely reversible last action. It is derived from the
latest persisted state, not stored as a generic history: remove the latest
azimuth, clear the latest `returnTime`, or delete only the latest open repeat
FlightCycle. D067 adds one bounded exception: the still-open first FlightCycle
created by the atomic initial group launch may be removed for that individual
Bee only while it has never received a `returnTime`. This lets an operator
correct a Bee that never actually left without fabricating an arrival; its later
actual departure creates its real FlightCycle #1. The eligibility is persisted
and irreversibly closes on the first recorded return, so ordinary Undo cannot
delete historical first-flight evidence.

The card action row now has stable left / correction / right semantic slots:
azimuth stays in the left slot, the 48dp icon-only `↶` stays in the same
horizontal position when neighboring controls appear or disappear, centered
between the nearest edges of the azimuth and primary-action slots; the full
`ПРИЛЕТЕЛА`/`УЛЕТЕЛА` primary action stays at the right edge. Meaningful empty
slots preserve muscle memory without adding a separate text row. The
local correction remains one action at a time and never affects another Bee.
Samsung SM-S938B verification at `font_scale=1.7` observed compact flight and
point cards, full primary labels, the icon-only local correction, a group-launch
correction moving one Bee to `На точке` with no timer, and its later individual
`УЛЕТЕЛА` returning it to `В полёте`. `connectedDebugAndroidTest` passed 117
tests with 9 expected skips. These checks are correction/presentation evidence,
not a change to the FlightCycle meaning or D064 persistence boundary.

The stable-slot action-row follow-up was verified on the same SM-S938B at the
system `font_scale=1.7`: all 24 `BeeObservationScreenTest` tests passed through
direct instrumentation against `org.beesearch.app.dev` and
`org.beesearch.app.dev.test`. Device screenshots covered flight/point cards
with and without azimuth and Undo; visual review confirmed stable Undo placement,
right-aligned full primary labels, the existing correction presentation, and
unchanged compact card height.

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

Server Map Package download and the candidate server/sync decisions remain
future project work. Do not implement them merely because the draft describes
possible directions; they must use the existing D065 staging/validation/
activation boundary rather than a parallel map architecture.

## Next task

Samsung SM-S938B verification at `font_scale=1.7` confirms that five 56dp
visible circles, `Грудь`/`КП`/`КЛ`, `Добавить`, `Выпустить всех`, and collapsed
`ВАЖНО! Первый выпуск` fit above the navigation bar in that order. A normal
scroll toward the list showed all three prepared Bee rows at once while the
editor left the viewport; scrolling back restored the editor. UIAutomator
inspection also confirmed that expanded guidance is in that same scrollable
layout with all three paragraphs outside the editor area. The direct Android
instrumentation class `ResumeObservationScreenTest` now passes 16/16,
including successful Add showing the new Bee while the editor remains visible
and a manual-scroll scenario at explicit `fontScale=1.7`: an eight-Bee list
gets the viewport while the editor naturally moves below it. The transient
preparation class `ObservationPointPreparationScreenTest` passes 4/4. The
draft header no longer uses ellipsis for `Подготовка точки`, and the redundant
visual color swatch in a prepared Bee row is decorative rather than a second
accessibility label. These direct checks are implementation evidence; user
visual acceptance is recorded above.
The manual-import milestone was directly checked on Samsung SM-S938B through
`org.beesearch.app.dev`: valid D065 fixture import reached Ready; active vector
rendering survived a cold start and rendered while airplane mode was enabled;
a bad-SHA replacement was rejected while the prior package stayed Ready; a
coverage fragment outside the fixture made the active package not Ready until
the valid desired coverage was restored. The field package metadata remained
unchanged. `connectedDebugAndroidTest` then passed `108/108` (the 8 fixture-
dependent legacy Map PoC tests were SKIPPED); Gradle removed the development
container as part of that test deployment, and the current debug APK was
reinstalled without recreating any dev data. This is current device evidence
for the stated paths only; it does not prove a future downloader or other
package artifacts.

Earlier Gradle builds remain reported evidence only for their directly checked
properties.

## Development review bootstrap

After `connectedDebugAndroidTest` deploys its fresh development container, run
`tools/dev-bootstrap.ps1 -Serial <device-serial> -Adb <adb-path>` to recreate
manual-review state. The explicit script verifies debug artifact identities,
clears only `org.beesearch.app.dev`, grants its declared foreground location
permissions, stages the territory benchmark D065 pair, and invokes an opt-in
instrumentation bootstrap. It leaves a DEV Observer/current Territory, valid
coverage, a Ready active PMTiles package, and one active ten-Bee observation:
five first-cycle flights, two repeat flights, and three Bees at the point
(12 FlightCycles total). It never targets the field package; this is
reproducible DEV state, not a field-data recovery path.

Earlier preparation checks created a persistent test ObservationPoint before
the first group release. Its current contents are not asserted by this handoff;
it is not field data, so do not modify or delete it without user direction.
The D064 lifecycle itself is user-reported as verified. Do not commit or push
without a separate user instruction.
