# Bee Search — Handoff

This file is operational continuation context, not project truth. Current
`docs/`, ACCEPTED decisions, explicit user instructions, and verified
repository state take precedence.

For any UI work, read `.agent/ui-policy.md` before implementation.

## Area exchange file, Area view mode and transport-independent send (D083, 2026-09-19)

A saved Ареал now has a portable file and a life outside the editor. The canonical value is
unchanged: still the device-local `map_coverage_<territoryId>` `v2` entry. Everything added here is
derived from it.

`data/exchange/AreaExchangeFile.kt` holds the public contract: `AreaExchangeCodec` writes and reads
Area JSON v1 (`formatVersion`, full `areaId`, non-blank `name`, 1..N `bounds` in stored order, exact
coordinates, UTF-8, locale-independent numbers) and `AreaExchangeFileName` builds the deterministic
human-readable name `Лух--7e82a310.json`. The file carries the whole Ареал and nothing device-local:
no territory id, variant, storage path, active package, UI state, timestamps or derived area. A
quoted version, an unknown field, a bad UUID, a blank name, an empty bound list or an out-of-range
coordinate are all reported as `Invalid`, never as a repaired Ареал.

`AreaExchangeMirror` owns one managed file in `<variant>/Exchange/Areas`: it writes it atomically
(temp file plus a replacing move), skips a rewrite when the file is already current, refuses to touch
a file carrying another `areaId`, and removes only its own file. `MirroringMapAreaStore` attaches the
mirror to the store, so every canonical save refreshes the file and no screen can forget it. Order is
enforced there: canonical first, mirror second, and a mirror failure never changes the canonical
result. Reads never touch the file. The two places that need the file immediately - opening
`Objects → Ареал` and sending - call `sync`, which also performs the lazy backfill for an Ареал saved
by an earlier version.

The user-visible surface changed as D083 describes. The Ареал card is now the name, `Участков: N`,
`Общая площадь: …` and the actions `Посмотреть на карте`, `Отправить ареал`, `Удалить ареал`.
`Переименовать` is gone from the interface (the store's `rename` still exists and still mirrors, but
nothing in the app calls it - keep it or remove it deliberately, not accidentally), and
`Копировать bbox` was removed from the editor panel, the help, the tests, `MapAreaCodec.encodeLegacy`
and the `formatMapPackageBuilderBounds` helper, so manual coordinate handover is gone.

`BeeMapMode.AREA_VIEW` is the third map mode, reached through the `AppRoute.AreaView` route: it draws
all stored участки unchanged (no merging, no simplifying), frames the outer extent once, shows no
editor panel and offers one small `Изменить участки` action. `mapAreaPresentation` (pure, in
`MapCoverageSelection.kt`) decides what each mode draws, and `AreaViewControlsTest` plus
`MapAreaPresentationTest` fix that separation. Editing started from the Ареал workflow records its
origin in `MainViewModel.areaEditReturnRoute` and `completeAreaSectionsEditing` sends the user back to
the card or the view; other entry points to the same editor keep the previous behaviour.

`Отправить ареал` goes through `AreaTransport`. The current implementation,
`AndroidAreaShareTransport`, writes a byte-identical cache copy of the Area JSON, exposes it with a
`FileProvider` (`${applicationId}.fileprovider`, `res/xml/file_paths.xml` → cache `area-share/`) as a
`content://` URI with a temporary read grant and the human-readable name, and opens the system
chooser (`application/json`). No `file://`, no storage permission, no receiving-app-specific code, so
the same action can later be a server upload without touching the Area JSON or the canonical model.

The total area is derived: `areaUnionKm2` compresses the latitude and longitude edges into a grid,
counts every covered cell once and measures a cell as `R² × Δλ × (sin φnorth − sin φsouth)` with
`R = 6371.0088 km`. Overlaps count once, a nested участок adds nothing and the gaps between separate
участки are excluded. `coverageBoundsSummary` now uses the same helper, so one участок cannot show
two different areas in the editor and on the card.

Verification: `:app:testDebugUnitTest` green (230 tests, of which the new `AreaExchangeCodecTest` 17,
`AreaExchangeFileNameTest` 11, `AreaExchangeMirrorTest` 14, `MapAreaGeometryTest` 12 and
`MapAreaPresentationTest` 7) and the instrumented suite green on the SM-S938B (219 tests, expected
opt-in skips). The unit tests caught a real portability bug: replacing an existing file by renaming
onto it silently failed on a filesystem that refuses the overwrite, which is why the mirror now moves
with `REPLACE_EXISTING`.

Device scenarios ran on the real DEV app at `font_scale=1.7`. A useful accident made the upgrade path
real rather than simulated: the device already carried an Ареал saved by the previous build (two
участки, `DEV Territory`, `e6b55f2d-…`) with **no** managed file, and opening `Objects → Ареал`
created `DEV Territory--e6b55f2d.json` with both участки by itself. Adding a third участок kept the
same `areaId` and the same file name and grew the JSON to three bounds; the card showed the name,
`Участков: 3` and `Общая площадь: 4.8 км²` with no rename action; the view mode showed all участки
with their overlap and framed them; the editor opened from the view mode without `Копировать bbox`
and returned to the view after `Готово`; the share sheet appeared with the document named
`DEV Territory--e6b55f2d.json` and Telegram, VK, Gmail and Drive among the targets; deleting the
managed file by hand and pressing `Отправить ареал` restored it; the DataStore was byte-identical
across sends; and deleting the Ареал removed the managed file while Room files, the PMTiles package
and the territory pointer stayed untouched.

Creation from scratch was then driven through the interface as well: with no Ареал, `Создать ареал`
opened the editor, two участки were added, `Готово` asked for the name (prefilled from the Territory)
and the save produced a new canonical `v2` Ареал (`12af9565-…`, two участки) together with
`DEV Territory--12af9565.json` containing exactly the same two участки, which the card then showed as
`Участков: 2`. The DEV DataStore was restored byte-identically
(md5 `5AA4B4D2BB02C3237A35F6FE36FE71B1`, 530 bytes) and the exchange `Areas` folder was left empty, as
it was found.

## Named Ареал Phase B: creation, rename, delete, empty protection (D082, 2026-09-19)

Phase B gives the D081 model its user-facing lifecycle. An Ареал is now created, renamed
and deleted only through explicit actions, and geometry editing can no longer turn an
existing Ареал into no Ареал.

First creation happens together with the name. When no Ареал exists and the user finishes
with at least one участок, `Готово` opens the `Название ареала` dialog, prefilled from the
trimmed `Territory.name` (otherwise `Ареал`). A blank name keeps the dialog open with
`Введите название ареала` and writes nothing; `Отмена` creates nothing, keeps the draft and
stays in the editor. `Готово` and Back → `Сохранить` share one commit path
(`commitCoverage` plus the pure `planAreaCommit`), so both exits produce the same plan, the
same name dialog and the same refusals; there is no second saving implementation.

`Очистить всё` followed by `Готово` no longer deletes the Ареал: the commit is refused, the
user sees `Ареал должен содержать хотя бы один участок.`, the stored value is untouched and
the editor stays open with the empty draft. An absent Ареал with an empty draft just leaves
the editor — no name dialog, and no empty value written. Deletion is the explicit
`Удалить ареал` action on the new Ареал screen, behind a confirmation that names the Ареал
and states that Territory and observation points remain. No new `v1` is written anywhere:
`v1` is read/migration-only, and the two transitional `PHASE B` branches were removed from
`DataStoreMapAreaStore`.

`MapAreaStore` now exposes exactly `load`, `create(name, bounds)`, `updateBounds`, `rename`
and `delete`, plus the unconditional `clear`/`snapshot`/`restore` used when a Territory
itself is deleted. Decision and write happen inside a single DataStore `edit`, so a refusal
provably leaves the stored value untouched. `create` refuses an existing, damaged or
unmigrated value; `updateBounds`/`rename` refuse an absent Ареал or empty bounds; `delete`
is idempotent when absent and refuses a damaged value. UI state never enters the store.

The entry point is `Objects → Ареал`, not Settings. `AreaScreen` shows `Ареал не создан`
with `Создать ареал`, or the card with the name, `Участков: N`, `Изменить участки`,
`Переименовать` and `Удалить ареал`; a damaged or unmigrated value shows `Данные ареала
повреждены` and offers no lifecycle action. Rename changes only the name — `areaId` and
участки stay identical — and no file is updated, because the Ареал file format (Phase C)
does not exist yet. Help gained the `Ареал` section, and the offline-map sections now say
`создайте ареал`/`участок` instead of the retired `выберите участок` wording.

Verification: `:app:testDebugUnitTest` green (165 tests; new `MapAreaCommitPlanTest` 11 and
a rewritten `DataStoreMapAreaStoreTest` for the final API) and the instrumented suite green
on the SM-S938B (203 tests, expected opt-in skips) through
`tools/run-preserving-device-tests.ps1`. Device scenarios A–F ran on the real DEV app at
`font_scale=1.7` against the real DataStore file: first creation with the prefilled
territory name (blank name refused, file byte-identical), a third участок added to an
existing Ареал with the same `areaId` and no name dialog, rename changing only `name` with
identical bounds, `Очистить всё` + `Готово` refused with the Ареал intact, Back →
`Сохранить` refused the same way, first creation reached through Back → `Сохранить`,
`Отмена` in the delete confirmation changing nothing, and deletion after confirmation
leaving Territory, Room files, the PMTiles package and the active pointer unchanged. The
DEV DataStore was restored byte-identically afterwards
(md5 `f7061adad65f60b6ad76e8b46b157d86`, 293 bytes).

Two defects were found and fixed during that device pass: the name dialog reached from
Back → `Сохранить` appeared on top of the still-open unsaved-changes dialog, and the
refusal message was long enough that its Toast truncated the actionable half at
`font_scale=1.7`, so the message is now one short sentence and the deletion hint lives in
Help.

Deliberately not implemented: Ареал JSON exchange, a managed Ареал file, Area
import/export, union area, multi-section map generation, manifest recovery and
manifest/PMTiles schema changes. No file belongs to an Ареал yet, so rename and delete
cannot affect an installed Map Package.

## Named Ареал Phase A: model, codec v2 and migration (D081, 2026-09-18)

Phase A of the Area model is implemented without any user-facing change. A Territory now
has at most one named Ареал holding a stable UUID, a human-readable name and 1..N
участки (`MapArea` in `ui/map/MapArea.kt`), persisted in the existing
`map_coverage_<territoryId>` DataStore entry as `v2|{"areaId":…,"name":…,"bounds":[…]}`.
The `v1` encoding stays readable, so old archives still restore, and an existing non-empty
selection is migrated once on first read: one DataStore transaction, a new stable UUID and
an initial name taken from `Territory.name` (trimmed, otherwise `Ареал`). Geometry is
carried over untouched — no re-rounding, re-normalisation, merging, sorting or
de-overlapping.

Reading now distinguishes "no Ареал", "Ареал read" and "stored value damaged"
(`MapAreaReadResult`). A damaged value is never reported as absent, never replaced by an
empty selection and never overwritten: saving is refused and the editor says so. An empty
legacy `v1` means no Ареал, never an Ареал with zero участки.

The territory name reaches the store as a plain parameter from the screen that already has
the Territory, so the DataStore layer gained no dependency on Room. `MapCoverageValidator`
now validates `v2` structurally instead of by prefix, which makes a damaged value fail the
backup export explicitly while leaving it in DataStore; `backupFormatVersion`,
`archiveSchemaVersion` and `roomSchemaVersion` are unchanged and the Ареал travels in the
existing `map-coverage` settings rows. `TerritoryCoverageDeletion` snapshots and restores
the exact stored value, so even a damaged one survives a failed territory deletion.

Phase A left two transitional branches in `DataStoreMapAreaStore`; Phase B removed both. A
territory without an Ареал no longer stores an unnamed legacy selection, and the
zero-участки refusal now runs through the single commit path described in the Phase B
section above.

Map workflow compatibility is unchanged: `Area.bounds` are passed as `desiredCoverage` to
the existing D065 containment check, the active package stays keyed by Territory and no
`areaId` enters the manifest.

Verification: full debug unit suite green (150 tests; new `MapAreaCodecTest` 22,
`MapAreaMigrationTest` 10, `MapCoverageValidatorTest` 5, `TerritoryCoverageDeletionTest` 5)
and 50 instrumented tests on the SM-S938B (`DataStoreMapAreaStoreTest` 11, `BackupServiceTest`
including a new v2 round-trip, `ObservationDataMaintenanceTest`, plus the map/help/data UI
tests). On-device migration was exercised for real: two участка saved through the UI while no
Ареал existed, then a restart migrated the value to `v2` with `"name":"DEV Territory"`; the
UUID stayed identical across further restarts. The DEV DataStore was backed up first
(md5 `f7061adad65f60b6ad76e8b46b157d86`, 293 bytes) and restored byte-identically afterwards.
Note: the DEV saved selection was already the empty legacy value before this work, so no user
geometry was in the file; the cause of that earlier emptying is not established.

Phase B implemented the first-`Готово` name dialog, the `Objects → Ареал` screen, rename
and delete; see the Phase B section above. Rename was removed from the interface in the
next iteration (D083) and the store method is now unused by the app.

## Bee Search file exchange directory (D080, 2026-09-18)

User-facing file exchange now has one predictable place per build variant:
`Download/BeeSearch/<Stable|Beta|Dev>/Exchange/` with `Areas`, `OfflineMaps` and
`Data`. The variant comes from the generated `BuildConfig.EXCHANGE_VARIANT`, set per
build type, so no code inspects the package name. `BeeSearchExchangeStorage` is the
single contract that owns the variant root, the three folders, the picker start
location and directory materialisation; UI, import and export never build paths
themselves. Folders are created idempotently on first use — nothing is deleted,
renamed or duplicated.

The exchange directory is a user-facing transfer contract, not canonical app
storage. Room, DataStore, cache, the installed PMTiles copy and map-package runtime
state stay app-owned; import still copies into app-owned storage, so deleting an
exchange file cannot damage an imported map.

Scoped storage does not allow an app to create `BeeSearch` in the root of shared
storage and Bee Search asks for no broad filesystem permission, so the physical root
is the public `Download` collection. Measured on the target device (API 36,
targetSdk 37): top-level `mkdirs()` is denied, `Download/BeeSearch/...` and
`Documents/BeeSearch/...` are creatable and writable, files created by another writer
are **not** readable by the app (EACCES, and `list()` is empty), and MediaStore
insert with a nested `RELATIVE_PATH` also works. Because foreign files cannot be read
directly, map import keeps using the system picker and only steers its start location
via `DocumentsContract.EXTRA_INITIAL_URI`.

Map import (`OfflineMapManagementScreen`) and data export (`DataRoute`) both point
their pickers at the variant's `OfflineMaps`/`Data` folder and display the exact path
as text. Map-package contract, importer semantics and backup format are unchanged.
In-app help gained the `Где находятся файлы Bee Search` section, generated from the
running variant so it always shows the real path.

Verification: full debug unit suite green (116 tests, including 9 exchange-contract
tests) plus `BeeSearchExchangeStorageDeviceTest`, `HelpScreenTest`, `DataScreenTest`
and `MapCoverageSelectionUiTest` (26 instrumented tests). On the SM-S938B at
`font_scale=1.7`: the Dev tree was created, the OfflineMaps picker opened inside
`Dev > Exchange > OfflineMaps`, a map pair copied there was listed and selectable, the
export picker opened inside `Dev > Exchange > Data` with `bee-search-backup.zip`, and
the offline vector map kept rendering after the source pair was moved out of
`Download`. The Dev coverage DataStore file stayed byte-identical
(md5 `41bc475150c6e97fb3edf12101a13449`).

Known, unrelated defect found while checking the export: on this DEV device the
backup export fails with `IllegalArgumentException: No enum constant
org.beesearch.app.domain.model.MarkPosition.THORAX` from
`RoomConverters.stringToMarkPosition` while reading Bee rows. That vocabulary belongs
to the bee-marking branch; it is a cross-branch DEV database incompatibility, not
caused by the exchange directory, and it is not fixed here.

## Offline-map coverage editor UX and in-app help (D079, 2026-09-18)

The coverage editor no longer offers a plain exit. `Готово` is the only way out
and it saves the selected areas and closes the editor, so a finished selection
can no longer be thrown away by an ordinary dismissal. Opening the editor is not
a change: the draft starts as the persisted selection, and dirty state appears
only after `Добавить участок`, `Отменить последний` or `Очистить всё`. System
Back with unsaved changes shows `Сохранить изменения участка?` with the named
choices `Сохранить`, `Выйти без сохранения` and `Остаться`; with no unsaved
changes Back closes the editor directly. `Отмена` and `Сброс` became
`Отменить последний` and `Очистить всё`, the clear confirmation is
`Очистить выбранные участки?`, and clearing stays draft-scoped until `Готово`,
so leaving without saving restores the previous persisted selection.
`Копировать bbox` is no longer gated on `BuildConfig.DEBUG` and is available in
every build type while exactly one area is selected.

The two actions that need long labels moved out of the old three-button row:
`Отменить последний` takes a full-width row and `Обзор`/`Очистить всё` share the
next one, which keeps every label readable at `font_scale=1.7`.

In-app help gained `Создание участка офлайн-карты` and `Загрузка офлайн-карты`.
Their action names are asserted against the same constants the editor renders, so
a rename cannot leave the help describing a button that no longer exists.

Verification: `:app:testDebugUnitTest` (all classes green; 14 coverage-selection
and 23 help-content tests), `:app:compileDebugAndroidTestKotlin`,
`:app:assembleBeta`, 15 instrumented UI tests (`MapCoverageSelectionUiTest`,
`HelpScreenTest`) and 11 existing map instrumentation tests (`MainMapScreenTest`,
`MapCenterTargetTest`, `MapViewLifecycleControllerTest`) passed on the Samsung
SM-S938B at `font_scale=1.7` through `tools/run-preserving-device-tests.ps1`.
Device scenario checks ran against the real screen: create then `Готово` persists
and survives reopening; Back + `Выйти без сохранения` keeps the previous
selection; Back + `Сохранить` commits the change; declining the clear
confirmation loses nothing; clear then Back + discard leaves the saved selection
untouched. The DEV coverage DataStore file was byte-identical before and after
(md5 `41bc475150c6e97fb3edf12101a13449`, 427 bytes).

Not covered automatically: the `BeeMap` wiring itself. The Back confirmation and
the `Готово` commit path are verified by device checks plus unit tests of the
draft semantics, not by an automated integration test.

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
