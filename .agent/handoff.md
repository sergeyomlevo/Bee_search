# Bee Search — Handoff

This file is operational continuation context, not project truth. Current
`docs/`, ACCEPTED decisions, explicit user instructions, and verified
repository state take precedence.

For any UI work, read `.agent/ui-policy.md` before implementation.

## Bee marking on thorax/abdomen milestone (D078, worktree `bee-marking-abdomen`)

A Bee is now identified by one of ten marks: 5 catalog colors × `THORAX` /
`ABDOMEN`. Wing variants are retired from the active product: `КП`, `КЛ`,
`правое крыло` and `левое крыло` appear nowhere in the running UI. D017 is
SUPERSEDED by D078, and D055's quoted capacity is now 10.

Confirmed field semantics drive the compatibility mapping. Real field data
recorded on 2026-09-17 persisted `NONE` for a thorax mark and `RIGHT_WING` for a
mark that was physically on the abdomen, so both tokens are read through
`MarkPosition.fromPersistedToken`: `NONE` -> `THORAX`, `RIGHT_WING` -> `ABDOMEN`.
`LEFT_WING` has no confirmed meaning, is not reinterpreted, is never offered for a
new Bee, and does not consume one of the ten marks; it survives only as an
internal legacy compatibility value for old and test records.

Persistence stayed backward compatible without a schema migration. `mark_position`
remains TEXT, Room stays at version 6 and no schema JSON changed, and the backup
format stays at `backupFormatVersion = 1`. Duplicate-mark protection matches on
`MarkPosition.persistedTokens`, so a legacy `NONE` row still occupies the
`THORAX` slot and a legacy `RIGHT_WING` row still occupies the `ABDOMEN` slot.

The former circular color swatch and the `КП` / `КЛ` text labels are replaced by
`ui/observation/BeeMarkIcon.kt`, a parametric Compose Canvas drawing: head (small
circle), thorax (noticeably larger circle) and abdomen (vertical oval). The mark
color is painted on the thorax for `THORAX` and on the abdomen for `ABDOMEN`; other
segments stay dark, so position and color are read from the same glance and no
visible position label exists. WHITE keeps its semantics and gains visibility from
an explicit dark outline instead of being greyed. A legacy `LEFT_WING` row draws a
fully dark body plus a mark-colored elliptical ring, because no segment can
honestly carry that color. `contentDescription` carries the meaning for
accessibility, e.g. `Жёлтая метка, грудь`. No per-combination asset exists.

The chromatic mark colours were brightened after field review reported that the
first palette went blind in sunlight: `RED C62828`, `BLUE 1565C0` and
`GREEN 2E7D32` had a relative luminance of only 0.13-0.16, which is close to the
dark body colour, so the filled segment stopped separating from the rest of the
silhouette (contrast against the body was only 2.7-3.1). They are now
`RED FF3B30`, `BLUE 0A84FF` and `GREEN 4CAF50`, which raises the contrast against
the dark body to 4.3-5.7 while keeping every pair of colours distinguishable.
`WHITE` and `YELLOW` are unchanged.

The mark is sized and proportioned from an explicit visual review. Its slot is
tall and narrow (width ~0.36 of its height), the abdomen is the dominant mass,
and the graphic spans the full height of a Bee card, so the mark is the largest
element of the card while the state row and the action row sit beside it. On
Samsung SM-S938B at `font_scale=1.7` the drawn mark measures about 95dp tall in a
104dp card, against a reviewed reference silhouette whose width/height ratio is
0.363 (implementation: 0.357). A consequence of spanning both rows is that a Bee
card is slightly shorter than before, so more cards fit the field viewport.

The icon is used in the observation screen's `Выбор` (AVAILABLE), `В полёте`
(IN_FLIGHT) and `На точке` (AT_POINT) cards, and in the Points Browser detail Bee
history, which no longer shows a mark text label.

Verification: `compileDebugKotlin`, `compileDebugUnitTestKotlin`,
`compileDebugAndroidTestKotlin` and `assembleDebug` pass; the JVM unit suite
passes; the full `connectedDebugAndroidTest` run on Samsung SM-S938B finished 171
tests with 12 expected opt-in skips and 0 failures. On that device at the system
`font_scale=1.7`, the untouched DEV database was read through the new code: the
rows persisted as `RIGHT_WING` render with a colored abdomen and the rows
persisted as `NONE` render with a colored thorax, while the single legacy
`LEFT_WING` row renders as an uninterpreted legacy mark. `org.beesearch.app.dev`
and its database/WAL bytes were unchanged by the runs, and the field package
`org.beesearch.app` was never targeted.

Known out-of-scope consequence: `tools/analysis-evidence-explorer/evidence.py`
still validates `markPosition` against the closed old triple
(`NONE`/`RIGHT_WING`/`LEFT_WING`), so it rejects an archive produced by this
version with "invalid bee mark position". The Explorer was deliberately not
modified in this iteration. Extending that accepted set (and mapping the legacy
tokens for its duplicate-mark key) is a required, purely additive follow-up
before new backups are fed to the Explorer.

## Navigation shell milestone (D077, 2026-09-17)

The main map is now the common creation entry. Its red center point is the
selected coordinate; tapping the plus captures that exact map-center latitude
and longitude in a transient draft and opens `Что создать?`. Choosing the only
implemented type, `Точка наблюдения`, opens the existing preparation workflow
without another GPS fix or coordinate-confirmation screen. Later GPS updates do
not mutate the captured coordinates. Opening or dismissing the chooser does not
persist research data, and cancelling preparation still discards the draft.

The chooser visibly reserves disabled entries for `Дупло`, `Колода`, `Ловушка`
and `Пасека`; no entity, form, placeholder record, Room migration, or backup
change exists for them. The separate bottom action `Объекты` opens a small
view-only catalog whose implemented entry `Точки наблюдения` routes to the
unchanged Points Browser v1. Points was removed from Settings. Back navigation
is detail -> Points Browser -> Objects -> main map.

Debug compilation, the full debug unit suite, lint, androidTest compilation and
debug assembly pass. On Samsung SM-S938B at `font_scale=1.7`, 15 focused UI
tests passed while preserving `org.beesearch.app.dev`. Manual checks covered a
manually displaced red point, the create-type chooser, direct preparation
entry, cancellation without a new point, Settings without Points, the full
Objects/Points/detail back chain, and the retained offline vector Map Package.

## Points Browser v1 milestone (D076, 2026-09-17)

The Objects catalog now opens a dedicated `Точки` route for the current Territory. A
single ViewModel state filters the Territory's saved ObservationPoint summaries
by the most recent available year, another available year, or all years, and
feeds both the MapLibre map and compact table. Points are ordered newest first;
summary rows include Bee and completed-FlightCycle aggregates. Map markers
distinguish `BEES_FOUND`, `NO_BEES_FOUND`, and unresolved `null` results and
open the same read-only detail route as table rows.

Detail loads one ObservationPoint transactionally with its Territory, Observer,
Bees, and ordered FlightCycles. It shows persisted coordinates/GPS accuracy,
raw result, completed duration, optional azimuth, and explicitly labels an open
cycle. Room remains version 6; no entity, migration, backup, observation-entry,
or analysis behavior changed. I003 remains an idea only for refinements beyond
this accepted v1.

Debug compilation, the full debug unit suite, lint, androidTest compilation,
and debug assembly pass. On Samsung SM-S938B, 11 focused Points/Room/main-map
tests pass while preserving `org.beesearch.app.dev`. Manual checks at system
`font_scale=1.7` used existing DEV research data to open Points, switch
Map/Table, select all years, open the same point from a row and marker, inspect
Bee/FlightCycle history, and render the retained offline vector Map Package.

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

The retired Bee preparation UI is gone: `ui/observation/BeePreparationScreen.kt`,
`BeeSelector`, `BeeSelectorSelectionLogic`, their layout-only helpers and the
`ResumeObservationScreenTest` coverage were removed in the legacy UI cleanup
commit, because they were unreachable from the active route and asserted the
superseded group-release workflow. Persisted compatibility (Room columns,
`initialGroupLaunch` / `initialGroupLaunchCorrectionEligible`,
`initialGroupReleaseAt`, backup keys, migrations, `LegacyObservationTestFixtures`)
was deliberately left untouched. `BeePreparationUiState` keeps its historical
name because it is the active observation screen state holder.

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

No pending implementation task is recorded for the Observation workflow. The
paragraph that used to stand here described the retired preparation screen
(`Добавить`, `Выпустить всех`, collapsed `ВАЖНО! Первый выпуск`, prepared Bee
rows) and its `ResumeObservationScreenTest` results; that screen and those tests
no longer exist, so it was replaced by this note. Current device evidence for
the active workflow is in the Observation workflow milestone section above.

The manual-import milestone was directly checked on Samsung SM-S938B through
`org.beesearch.app.dev`: valid D065 fixture import reached Ready; active vector
rendering survived a cold start and rendered while airplane mode was enabled;
a bad-SHA replacement was rejected while the prior package stayed Ready; a
coverage fragment outside the fixture made the active package not Ready until
the valid desired coverage was restored. The field package metadata remained
unchanged. `connectedDebugAndroidTest` then passed `108/108` (the 8 fixture-
dependent legacy Map PoC tests were SKIPPED); at that time Gradle removed the
development container as part of the test deployment, and the current debug APK
was reinstalled without recreating any dev data. Connected runs now keep the DEV
package and its app-private state instead, because
`android.injected.androidTest.leaveApksInstalledAfterRun=true` is set in
`gradle.properties` (see AGENTS.md "Development and field package safety"). This
is current device evidence for the stated paths only; it does not prove a future
downloader or other package artifacts.

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
