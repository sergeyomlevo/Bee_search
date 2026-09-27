# Bee Search handoff

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
D091 records these boundaries; the next free durable decision is D092. The raster harness and
the completed raster research commit remain unchanged.

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
- D090 (ACCEPTED) records numbering semantics; D091 records optional Hollow/LogHive names.
  Next free durable decision: D092.
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

Raster/imagery research is paused with a reproducible checkpoint in I014. On
Samsung SM-S938B, offline local XYZ and raster PMTiles both worked; raster
PMTiles is the preferred future transport candidate and local XYZ remains a PoC
tool. A real 0.30 m/px reference was useful at z18 and the owner found no value
in z19. Real Sentinel-2 RGB 10 m/px was useful only as an overview through z13
(already beginning to blur); z14+ is not a working field zoom. The opt-in,
DEV-only `RasterBasemapPocDeviceTest` is now preserved in Git, while all large
fixtures remain external. No production raster layer exists. The next separate
mapping research task is finding a legal high-resolution source for real Russian
territories; licensing, the production raster package contract, a high-zoom
packer, possible Sentinel inclusion and intermediate-scale coverage remain open.

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

No active functional task. Beta `1.3.0-beta.4` (versionCode 7) is the current
test build and its only purpose is the tester feedback round on the help, see
«Current release» above. Continue from commit `434e319b` on `main`. Nothing was
pushed, no release beyond this local Beta artifact was made, the product version
`1.3.0` is unchanged and Stable was not touched.

The raster/imagery feasibility cycle is also closed for now. Do not repeat the
offline transport, Sentinel zoom or 0.30 m reference experiments unless a new
question requires it; the next mapping step is the separate source/licensing
research described above, not production implementation.

## Previous milestone

The in-app help was rebuilt around user workflows in `bdfbb340`: the canonical
text lives in `docs/ui/help/help-v2.md`, the in-app content is generated from it
and checked by a drift test, and the help now explains the first launch, the main
map screen, the observation workflow, `Объекты`, дупла and колоды, the object
card, deletion with the number rule, the numbering reset, offline against online
behaviour and export. Before that, safe physical object deletion, monotonic
numbering and the explicit numbering reset were implemented in `50c75b2e`.
