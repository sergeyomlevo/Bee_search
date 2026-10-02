# Bee Search handoff

## Active uncommitted milestone — Physical Object Export/Delete V1

Implemented on top of clean baseline `eb130c2764bdad99574f3b5190fd7f6a57477834` and left
uncommitted for owner review. Do not commit or push before owner review.

Scope: Дупло (`Hollow`) and Колода (`LogHive`). `Apiary` stays out of scope: it keeps no creation UI,
no category, no card, no media and no delete path, and the new export refuses it fail-closed instead of
writing a half-shaped package. Recorded as **D093**; next free durable decision is **D094**.

Export: a new isolated package `data/objectexport` implements the portable
`SINGLE_PHYSICAL_OBJECT` `formatVersion = 1` profile (field-level contract in
`docs/physical-object-export-v1.md`). A package carries the object's own data (identity, subtype
properties, owned media metadata and bytes) plus a minimal read-only labelling/provenance snapshot of
the Territory (id/code/name) and the creator Observer (id/code/ФИО). Bee, FlightCycle, ObservationPoint,
their weather and their attachments never enter a package, and an object referenced by a Bee still
exports. The source read uses `PhysicalObjectRepository`, which has no read path to observation data at
all. The ObservationPoint codec was deliberately not refactored into a shared framework; the ZIP/hash
JSON mechanics are repeated locally so the verified point export keeps its regression surface.
Robustness: strict reader (declared profile/version/type, declared entry set, declared JSON field set,
size+SHA-256 for `object.json` and every media entry, deterministic media paths, entry/archive caps,
`ZipEntry.time = 0`, canonical media order). SAF write is staged: the archive is built and verified in
app cache and only a complete package is copied into the picked document, so a missing or damaged media
file cannot leave a truncated package. The actual guarantee is "no partial or corrupt package is
written"; the picker may already have created an empty destination document, which the app does not
delete because it is the user's chosen file.

Collection export extends the same uncommitted D093 with a separate
`PHYSICAL_OBJECT_COLLECTION`, `formatVersion = 1` profile. The `Дупла` and `Колоды` list toolbars now
have one `⋮` action — `Экспортировать все дупла` or `Экспортировать все колоды` — and explicitly no
bulk delete. One action creates one ZIP for the current Territory and one concrete type; no nested
single-object ZIPs, Apiary or mixed-type export. Entries are `manifest.json`, `territory.json`,
`observers.json`, `objects/<object-id>.json` and `media/<object-id>/<media-id>`. Territory is stored
once, used Observer snapshots are deduplicated, and object/media entries are canonical, size/hash
declared and strict. The source uses `listForTerritory`, selects only the requested concrete type and
loads only its owned media. Empty collections return a typed result before SAF; one damaged object or
media aborts the whole package. The staged adapter re-decodes the temporary archive before opening
the single SAF destination. Suggested names are `DEV--hollows--YYYY-MM-DD.zip` and
`DEV--log-hives--YYYY-MM-DD.zip` through the existing sanitizer.

Delete: blocking references are collected inside the delete transaction and returned as a structured
model (`PhysicalObjectReferenceKind` + count, today only `BEE`) instead of a scalar count. With blockers
nothing is removed — no subtype row, no media rows, no media bytes — and the card shows a dedicated
dialog `Объект нельзя удалить` naming the blocking data and its amount (`Пчёлы — 3`) with no force
delete, cascade or "delete anyway" action. The blocked-deletion result is a one-shot request on the
route with an explicit consume step, not a durable flag. `RESTRICT` FKs stay the second, fail-safe line
of defence, and `PhysicalObjectReferenceRestrictTest` now also fails when a non-owned table referencing
`physical_objects` is not represented by a blocker kind. Numbering high-water state is still untouched
by an ordinary deletion.

Fixed defect: an unsafe stored media path used to make post-commit cleanup throw, so the UI reported
"Не удалось удалить объект" although the database deletion had committed. Cleanup is now best-effort:
such a path yields `fileCleanupComplete = false` and the existing
`Объект удалён, но не все файлы медиа удалось удалить` message, while path safety still refuses to
resolve or delete the escaping path.

Verification: JVM `:app:testDebugUnitTest` `419/419 PASS` (24 new object-export tests plus the Help
drift check after regenerating `HelpContent.kt` from `docs/ui/help/help-v2.md`); `assembleDebug`,
`assembleDebugAndroidTest` and `lintDebug` pass; `git diff --check` clean.

Preserving Samsung SM-S938B instrumentation (`android.injected.androidTest.leaveApksInstalledAfterRun`
stays enabled, no clear and no uninstall): a focused run of the new and touched classes —
`PhysicalObjectDeletionBlockerTest`, `PhysicalObjectExportDocumentContractTest`,
`PhysicalObjectReferenceRestrictTest`, `FileAwarePhysicalObjectDeletionTest`,
`PhysicalObjectNumberingAndDeletionTest`, `PhysicalObjectCardsTest` — reports `53 tests / 0 failed`,
including all `RESTRICT` and staged-write cases. The full connected suite was not re-run.

Manual device walk on `org.beesearch.app.dev`, at the owner's real system `font_scale = 1.7`, with the
existing DEV objects left intact: map → `Объекты` → `Дупла` → card `Дупло 3` (`дупло в старой липе`)
showed `Редактировать характеристики`, `Экспортировать объект`, `Удалить объект` with full labels;
the picker opened in `Download/BeeSearch/Dev/Exchange/Data` with the suggested
`DEV--hollow-3--2026-09-27--68711d39.zip`, saving it produced a 954-byte package, and the pulled file
contains exactly `manifest.json` (profile `SINGLE_PHYSICAL_OBJECT`, `formatVersion 1`, type `HOLLOW`,
`object.json` descriptor) and `object.json` (identity, `HOLLOW` properties, empty media, Territory
snapshot `DEV / DEV Territory`, Observer snapshot `DEV-OBS1 / Testerov Ivan`) with no Bee, FlightCycle,
ObservationPoint, weather or attachment data. The `Колода 1` card suggested
`DEV--log-hive-1--2026-10-01--d170bb70.zip`; cancelling that picker returned to the card with no file,
no success message and no error. The ordinary confirmation `Удалить Дупло 3?` is unchanged and
cancelling it changed nothing.

Collection-export verification added after that V1 pass: full `:app:testDebugUnitTest`, Help
generation/drift/content checks, `assembleDebug`, `assembleDebugAndroidTest`,
`compileDebugAndroidTestKotlin`, `lintDebug` and `git diff --check` pass. New focused tests cover both
types, deterministic/canonical output, strict profile/version/entry/identity/media rules, caps,
observer deduplication, nullable state, empty result, all-or-nothing corruption, staged SAF and the
Room source Territory/type boundary. A preserving Samsung run of
`PhysicalObjectCardsTest`, `PhysicalObjectListResetTest`,
`PhysicalObjectExportDocumentContractTest` and `PhysicalObjectCollectionExportSourceTest` reports
`29 tests / 0 failed`; DEV remained installed and app data was not cleared.

Manual Samsung SM-S938B check at the existing real `font_scale = 1.7`: `Дупла` contained two saved
objects and showed a readable one-item menu with only `Экспортировать все дупла`; one SAF flow opened
in `Exchange/Data` with `DEV--hollows--2026-10-02.zip`. Saving produced a 4,102,162-byte package with
profile/version `PHYSICAL_OBJECT_COLLECTION/1`, type `HOLLOW`, `objectCount = 2`, exactly one Territory
and observers entry, two object entries and the owned media entry, with no LogHive or observation
payload. `Колоды` showed only `Экспортировать все колоды`, suggested
`DEV--log-hives--2026-10-02.zip`, and cancelling returned neutrally with no feedback. No real object
was created or deleted for testing; empty-list behavior is therefore instrumentation-only.

Not verified manually: the blocked-deletion dialog. No current screen can create a `Bee → object` link
(`setBeeSourceObject` has no UI caller), and fabricating one in the owner's DEV database would alter
real data, so that scenario is covered by instrumentation only.

## Previous milestone — Help V2 illustrations

The owner-approved Help V2 illustration pass is complete in the current dirty
worktree, based on baseline HEAD `28ebc00f9ebb2265b4e85ed6c9b059477320085c`.
Do not discard the surrounding uncommitted UI/map/observation work and do not
commit or push before owner review.

`docs/ui/help/help-v2.md` remains the canonical source. It now declares exactly
three real Samsung screenshot resources for `Основная информация карты`,
`Управление картой` and `Экран активного наблюдения`; the generated
`HelpContent.kt` is synchronized through the existing generator. The phone
layout is intentionally vertical: full-width screenshot followed by native
Help text, with normal vertical scrolling instead of a narrow two-column
composition. After owner review, the detailed-help order is initial setup,
map information, map controls, then observer and territory settings.

Verification completed: Help generation and drift/content tests, Help Compose
instrumentation (`8/8` on Samsung SM-S938B), `assembleDebug`,
`compileDebugAndroidTestKotlin`, `lintDebug` and `git diff --check` pass. DEV
was installed in place as `org.beesearch.app.dev`; no clear or uninstall was
used. Manual device review at system `font_scale=1.7` confirmed all three
screenshots and their native explanations remain readable, scroll correctly
and do not overlap. The map controls, GPS-to-target guide, all three observation
states and Bee direction arrows are visually distinguishable.

## Current release

Beta `1.3.0-beta.4` (versionCode 7) was published locally from commit
`434e319b` on clean `main` with the canonical workflow
`tools/beta-release/beta_release.py`:

- artifact: `C:\App\Bee_search_beta_releases\1.3.0-beta.4\bee-search-1.3.0-beta.4-434e319.apk`
- SHA-256: `a1f6d88a5191472762e5722a7fab659eab1de4f8db4be24ae6da16ec5f9e9dcb`
- package `org.beesearch.app.beta`, signing certificate unchanged
  (`2fd4f10a…b654`), Stable and Dev were not touched, nothing was pushed.

The Beta packages the help rework from `bdfbb340` `feat: rebuild the in-app help
around user workflows`: the in-app help is now workflow-oriented and generated
from `docs/ui/help/help-v2.md`. Its purpose is tester feedback on the help, so
the declared image slots are deliberately still empty: no screenshots were added
and no PDF exists.

Release validation for this Beta: `:app:testDebugUnitTest` `375 tests / 0
failures` (this includes the help source-drift check), `:app:assembleBeta` and
`:app:lintBeta` pass (32 warnings, 0 errors), `beta_release.py --check-only`
reports `PASS`, and the published APK was verified with `aapt` and `apksigner`
(package, versionCode, versionName and certificate). No device installation was
performed: the workflow verifies the artifact metadata itself, so the Beta is
handed to testers as the archived APK.

Next step: collect tester feedback about the help — where it does not say what
to do, where it says too much, and where finding a control is hard without an
image. Choose screenshots only after that feedback, then prepare the next Beta.

## Current milestone

Optional user names for Hollow/LogHive and the unambiguous entrance-direction instruction are
implemented. `name` is nullable, editable and non-unique; it is stored in the concrete subtype
row, shown above the unchanged `Дупло N` / `Колода N` designation, and does not affect UUID or
numbering. The compass still persists the production true heading of the phone's top edge; only
the instruction changed to `Направьте верх телефона в ту сторону, куда направлен леток`.

Room schema v11 adds only nullable `name` columns to `hollows` and `log_hives`
(`MIGRATION_10_11`). Complete Backup V6 carries the names and reads V1–V5 with null names.
D091 records these boundaries; D092 records the accepted Sentinel/Hybrid research reference.
The next free durable decision is D093.

Verification for this increment: JVM `376/376 PASS`; debug APK, androidTest APK and lint pass;
focused preserving Samsung instrumentation `93/93 PASS`. At real Samsung font scale 1.7, the
Hollow flow was checked through create, card, rename, typed list and deletion; designation stayed
`Дупло 2`. Manual LogHive completion was not finished after semantic automation left the form,
although its create/update/name paths passed instrumentation. No DEV data reset occurred.

Safe physical object deletion, monotonic numbering and an explicit numbering
reset are implemented over the approved Objects UI and accepted by the owner,
who also verified them manually on the phone.

Last functional commit: `50c75b2e` `feat: add safe physical object deletion and
numbering reset`; the help rework followed in `bdfbb340`, and `434e319b` only
bumps the Beta version metadata.

What is in place:

- Objects V1 UI is implemented: `+ -> Дупло / Колода`, crosshair coordinates,
  live/manual azimuth, media, saved-object cards, characteristic editing and
  coordinate correction.
- The browser hierarchy is corrected: `Объекты` shows the categories
  `Ареал`, `Точки наблюдения`, `Дупла`, `Колоды`; a row is `Дупло N` / `Колода N`
  without repeating the type; Back goes card -> its list -> `Объекты`.
- `карточка -> Показать на карте -> Back` returns to the same card, and an
  ordinary map opening never inherits that return target.
- An unused Hollow or LogHive can be deleted from its card after confirmation
  (`Удалить объект`); the object, its subtype row, its media rows and its
  app-owned media files are removed, and the deletion returns to the typed list.
- Deleting a protected object is blocked: a `Bee -> object` reference (and any
  future `Inspection` reference) refuses the deletion with a readable message
  and keeps the card open.
- Persistent numbering high-water mark (`physical_object_sequences`, scope
  `Territory + object_type`) is allocated inside the create transaction as
  `max(last_issued, live MAX) + 1`; a failed creation consumes no number.
- Ordinary deletion never frees a number: deleting `Дупло 4` still gives the
  next created object `Дупло 5`.
- An explicit, fail-closed reset restarts one scope from 1, only while that
  scope provably holds no objects, no dependent rows and no references; it is
  offered in the empty typed list and re-checked in the repository. The stable
  UUID remains the object identity: a repeated designation is a new object.
- Room schema v11 (`MIGRATION_9_10` backfills sequence state; `MIGRATION_10_11` adds nullable
  Hollow/LogHive names without rebuilding identity tables).
- Complete Backup V6 carries `physical-object-sequences` and Hollow/LogHive names; the reader
  accepts V1-V5 and bootstraps archives without sequence state from stored numbers.
- D090 (ACCEPTED) records numbering semantics; D091 records optional Hollow/LogHive names;
  D092 records the accepted Sentinel/Hybrid research reference. Next free durable decision: D093.
- The RESTRICT invariant has an automatic test: `PhysicalObjectReferenceRestrictTest`
  reads the foreign keys of the live database and fails if any reference to
  `physical_objects` is not `ON DELETE RESTRICT`.

## Verification status

JVM unit suite `390 tests / 0 failures`; `assembleDebug` and
`assembleDebugAndroidTest` pass; `lintDebug` passes with no new findings;
`git diff --check` clean.

Preserving Samsung SM-S938B instrumentation: the full connected suite reports
`386 tests, 12 skipped, 0 failed`, and a focused run of the numbering, deletion,
media, RESTRICT, backup, migration, browser and reset classes reports
`105/105 PASS`. Device walk on `org.beesearch.app.dev` covered create -> delete
(confirmation, return to `Дупла`, absence after restart), no reuse of the
deleted number, LogHive deletion with its own confirmation text, and a reset in
a disposable Territory (counter 2 -> next object `Дупло 1`) that was then
removed again. Pre-existing DEV objects were preserved
(`ceDataInode=438163`, `deDataInode=424656` unchanged); DEV was updated in
place without clearing data.

The owner additionally checked deletion, number non-reuse and the explicit
reset manually on the phone and confirmed that the behaviour matches the intent.

Sentinel/Hybrid research is closed by D092. Owner review on Samsung SM-S938B
accepted the 2026-07-18 source-direct linear-B rendering
(`display = uint8(round(255 * clip(7.5 * reflectance, 0, 1)))`) in
`sentinel-area-z10-13-linear-b.pmtiles`, SHA-256
`135246b8f2f5e99e50b2361fb63a0539dff1c071074a075982d125ee2c09daff`.
The raster has real z10-z13 and both the DEV Sentinel and Sentinel-based Hybrid
stop at UI z13. Hybrid was more informative than bare Sentinel: independent
vector roads, tracks/paths, waterways and offline labels sit above the raster,
then Bee Search overlays. Sparse place labels are current package data, not a
glyph failure. Cutlines and power lines exist in the vector package but are not
enabled in this first Hybrid overlay.

The accepted artifact remains external at
`C:\App\Bee_search_test_maps\sentinel-area\sentinel-area-z10-13-linear-b.pmtiles`.
The byte-identical DEV staged file is
`/sdcard/Android/data/org.beesearch.app.dev/files/poc-sentinel/sentinel-area-z10-13.pmtiles`.
The existing selector preserves four DEV modes: `Онлайн карта`, `Векторная
карта`, `Спутник Sentinel`, `Гибрид`. Raster/vector stay independent; this is a
research/reference capability and no production raster package lifecycle exists.

The current vector package remains true z8-z15 with z16+ MapLibre overzoom.
Planetiler stays pinned at 0.10.0 and `field-profile.yml` stays at maxzoom 15.
Owner decision: do not update Planetiler or generate true vector z16-z18 now.
When a quality high-resolution raster is available, first test it with the
existing z15 vector overzoom and revisit vector generation only if field evidence
shows insufficient detail.

## Unverified / known residue (no action required for the accepted feature)

- Media deletion was not exercised through the real camera / system picker UI;
  file ownership and cleanup are covered by device instrumentation instead.
- Deleting a protected object was not exercised through real Bee-history UI; the
  blocking path is covered by repository, integration and schema tests.
- The new delete/reset UI was not separately inspected at a large system font
  scale (it is text-button based and the system scale was left untouched).
- A restore rollback warning does not exist: the application has no restore
  flow at all (only export), so there is nowhere to compare the current and the
  incoming numbering state. Restore semantics are documented in D090.

## Next task

No active functional implementation task remains after the Sentinel/Hybrid PoC
closure. Continue from the current committed `main` HEAD. The current release
context remains the local Beta `1.3.0-beta.4` (versionCode 7) described above;
this PoC closure does not build or release Beta/Stable and does not change the
product version.

The Sentinel/Hybrid feasibility cycle is closed. Do not repeat transport,
brightness, Sentinel zoom or Hybrid composition experiments without a new
question. The next architecture task is to design a source-neutral offline
raster preparation/package workflow covering validation, reprojection/mosaic,
real zoom pyramids, raster PMTiles, metadata/manifest and verification for both
Sentinel and future high-resolution georeferenced imagery. Production
import/acquisition/lifecycle is not implemented; legal high-resolution source
and licensing research remains separate. Do not turn the preserved DEV profiles
into a production architecture implicitly.

## Previous milestone

The in-app help was rebuilt around user workflows in `bdfbb340`: the canonical
text lives in `docs/ui/help/help-v2.md`, the in-app content is generated from it
and checked by a drift test, and the help now explains the first launch, the main
map screen, the observation workflow, `Объекты`, дупла and колоды, the object
card, deletion with the number rule, the numbering reset, offline against online
behaviour and export. Before that, safe physical object deletion, monotonic
numbering and the explicit numbering reset were implemented in `50c75b2e`.
