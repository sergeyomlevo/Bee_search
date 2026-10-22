# D106 — Samsung GPX deletion qualification, 2026-10-19

Result: **DEV update, migration and deletion lifecycle PASS; UI qualification PARTIAL**.
A filename-persistence defect prevents accepting the long-filename deletion case.
Production code was not changed. No commit or push. Stage1/H05 remain OPEN.

## Exact device and artifact

- Repository branch `task/d106-stage0-overlay-registry`; HEAD `b29af077c6e0abcbefb2b1a6d53de54364adb693`.
- Samsung S25 Ultra SM-S938B, serial RFCY90MBYVZ, physical Android API36.
- Actual system fontScale1.7, unchanged throughout. Other attached emulators were not targeted.
- Installed only `org.beesearch.app.dev`, version1.3.0 (versionCode8), using `adb -s RFCY90MBYVZ install -r`.
- APK SHA256 `e8f449b3b2b88084378de09f5adb8e427efdb3c9d01015e38b4bf0f916fb6549`.
  Pulled/read installed base.apk after qualification and matched the built artifact exactly.
- Installed/new APK certificate SHA256 `2fd4f10ac24e19eb2e8c1bae08dca48b4402e751e9c33d6a33f59025b0bfb654`.
  Same signer and versionCode; no downgrade, uninstall, data reset or destructive fallback.
- DEV lastUpdateTime changed from2026-10-19 13:17:58 to16:08:40 (device reported times).
- Stable/Beta code paths, versionCode/versionName, firstInstallTime and lastUpdateTime unchanged.

## Data preservation method and results

Only DEV was force-stopped for coherent read-only snapshots. ADB run-as SHA256
inventoried files/databases/shared_prefs/no_backup; DB/WAL/SHM and DataStore were
copied out without changing their contents. SQL analysis ran on local copies:
copy DB+WAL without Android SHM, then SQLite backup into a portable local snapshot.
An Android SHM copy caused a local locking-protocol error during initial analysis;
the local-copy method resolved it. No on-device DB error or repair occurred.

Before update:4078 files, Room15,4 READY imports. After installation **before first
launch**, all4078 hashes matched exactly and Room remained15. First launch migrated
to16. `integrity_check=ok`; `foreign_key_check=[]`.

All19 pre-existing application/Room auxiliary tables (excluding room_master_table
identity hash) have exactly equal full row sets before update, after migration,
after deletion and after restart. All4 existing import UUIDs and their metadata,
canonical geometry and managed originals are unchanged. Visibility remained:
Territory `48ef6a6c-59d4-4405-838a-b9a40bbe32c0`, selected existing import
`833946de-25d5-4971-9854-93160f754c44` only, except while the new test was enabled.
The selected original route was visually confirmed after deletion and restart.
Existing research filters were not operated; research rows remained identical.
No claim of visually checking every off-screen research marker is made.

Final inventory again4078 files, no missing or added baseline paths. Expected
changed bytes are Room DB/WAL/SHM, MapLibre cache `files/mbgl-offline.db`,
`files/profileInstalled`, ActivityThread IDs and WorkManager DB/WAL/SHM.
No old GPX originals, media or offline map package file changed.

## Only authorized test import

- External name: `Тест удаления GPX — длинное название для проверки переноса Samsung 2026-10-19.gpx`.
- External path: `/sdcard/Download/BeeSearch/Dev/Exchange/GPX/Тест удаления GPX — длинное название для проверки переноса Samsung 2026-10-19.gpx`.
- SHA256 `f6d9e92f8a5cf31097b35c7eb21eba8faa2194e07cbb451037c5af9300fc04b2`,438 bytes.
- New import UUID `6555d752-311b-4627-8658-0ff9c9c70fd6`.
- Territory `48ef6a6c-59d4-4405-838a-b9a40bbe32c0` (DEV Territory).
- Managed original `6cd2cf88-cbd8-42d9-9e2c-e70dcd7b300a`, original bytes/hash confirmed.
- One track, one segment, four points forming a visible triangle near the current GPS viewport.
- Exact test UUID was absent from baseline; no pre-existing import was deleted or toggled.
- New import was OFF; enabling added only this UUID and displayed its triangle.

## Scenarios

| Check | Result | Physical evidence / limit |
|---|---|---|
| DEV build/package/signer/update | PASS | Offline assembleDebug; package/signer and installed APK hash verified |
| Room15→16 | PASS | Full row-set comparison, integrity OK, no FK violations |
| Existing GPX and research records | PASS | Four old imports and all19 old tables identical |
| Existing selected GPX line | PASS | Visible after deletion/restart; map camera moved using normal gestures, then returned to GPS |
| Existing visibility | PASS | Same baseline UUID set after migration, deletion and restart |
| Exchange/GPX initial folder | PASS | Samsung picker automatically opened exact DEV Exchange/GPX folder |
| Normal UI test import, managed original | PASS | Five imports after import, exact new UUID/hash, original intact |
| Initial visibility OFF | PASS | New UUID absent from persisted selection; import UI explicitly reports hidden |
| Ordinary name tap/visible line | PASS | Test UUID selected; triangle screenshot on map |
| Long press → Delete → confirmation | PASS | Menu and named confirmation captured; only test row targeted |
| System Back from confirmation | PASS | Returned to list without deletion |
| Cancel | PASS | All after-import table rows unchanged; managed original and selected test retained |
| Confirmed delete | PASS | Test row immediately disappears; triangle disappears without restart |
| Exact-ID scope | PASS | All four old imports/geometry unchanged; previous route still renders |
| Internal cleanup | PASS | Test owned original files absent; one permanent tombstone files_cleaned=1/settings_cleaned=1 |
| Visibility cleanup | PASS | New UUID removed; selection equals baseline |
| External source preservation | PASS | External438-byte source remains with matching SHA256; intentionally not removed |
| Restart / no resurrection | PASS | Four original imports only; one completed permanent tombstone; no pending cleanup |
| FontScale1.7 menu/dialog/buttons | PASS | Real system1.7; readable horizontal text, natural wraps, vertical button arrangement, no clipping |
| Long filename in list/confirmation | FAIL | Source name lost in persistence; fallback date shown instead |
| Long-name dialog scrolling | NOT RUN | Actual dialog receives date fallback; cannot claim long-name scenario from this build |
| Accessibility basic labels/actions | PASS | UIAutomator exposes named row, clickable name, Delete/Cancel labels; touch long press and Back exercised |
| Spoken switch state/custom delete action with TalkBack | NOT RUN | accessibility_enabled=0, services=null; no unauthorized system setting change; XML does not prove custom action execution |
| Fault injection/power-loss | NOT RUN | Not part of physical qualification; no destructive tests on Samsung |

The normal font1.7 confirmation fits without needing scrolling. This is not proof
of scrolling for a long filename. Existing implementation tests are recorded in
[d106-gpx-deletion-report.md](d106-gpx-deletion-report.md); no connected/instrumented
tests were run on the owner's phone during this qualification.

## Observed defect — original filename is not persisted

The picker and import-success UI show the full long name. The newly created Room
row nevertheless has `display_name=NULL`. The list and confirmation show
`GPX от 19.10.2026, 17:54`. All four older imports also had NULL before this update.
This is a filename flow defect, not evidence of a migration changing old rows.

Read-only code inspection: GpxImportViewModel.beginImport calls workflow.capture()
without a name before opening the picker; onFilePicked stores fileName for UI but
does not update the captured persistence request. The display list then correctly
uses its fallback for absent metadata. No production patch was made during this
physical qualification, preserving the requested b29af07 artifact. Follow-up should
carry the selected display name into the existing authorized import boundary and
recheck long-name list/confirmation at1.7; existing data must not be guessed/renamed.

## Screenshots and diagnostics

All screenshots are actual Samsung captures at1.7, not emulator/mockup renders.
Compared menu/confirmation structure with the approved deletion PNG+MD; same
navigation, named warning and separate vertical Cancel/Delete controls.

Local ignored evidence (not part of Git; may be absent from another checkout):
`build/evidence/d106-gpx-delete-samsung-20261019/` contains
`import-picker.png`, `import-result.png`, `list-new.png`, `map-test-visible.png`,
`test-menu.png`, `test-confirm.png`, `cancelled-list.png`, `delete-result.png`,
`map-after-delete.png`, `map-old-route-after-delete.png`, `final-list.png`.

Evidence folder (ignored build outputs, contains private baseline snapshots):
`build/evidence/d106-gpx-delete-samsung-20261019/`.
Includes apk-check.json, migration-comparison.json, deletion-comparison.json,
final-package-check.json, final-files-delta.json and before/after SQL metadata.
Do not publish private snapshots. Test GPX deliberately remains in Exchange/GPX.

## Git / stop point

HEAD unchanged b29af07. Production diff empty; index empty; no commit/push.
Changed only this new report and an appended current-result handoff entry.
Prior dirty handoff was byte-identical to handoff-before.md before documentation;
removing only the new entry reproduces the prior bytes exactly. Prior134 additions/
1 deletion remain preserved. `git diff --check` and local report links checked.
Stage1/H05 OPEN. No My points or next-stage implementation started.
