# D106 — Samsung final: ca9d4e3 install, show-on-map and marker

**2026-10-22. D106 — CLOSED / ACCEPTED within tested scope. TARGETED SAMSUNG
QUALIFICATION PASS for the two requested fixes; DEV updated in place with data
preservation.** This closes the agreed My Points functional scope A–D, the
post-review UI corrections, VECTOR preservation and the uniform marker. It does
not convert any NOT RUN fault/accessibility/basemap scenario into PASS. Stage1/H05
remain OPEN; unified Backup/Restore and full process-restart basemap persistence
remain separate future work. The production tree is unchanged; the D106
documentation is committed in the final documentation-only commit. Referenced
Samsung screenshots/backups remain local Git-ignored evidence.

## 1. Builds and device

- Repository `C:\App\Bee_search_stage0_overlay_registry`, branch
  `task/d106-stage0-overlay-registry`, HEAD
  `ca9d4e3de305e9952f1d41bf4500b8322e50be64`.
- Samsung SM-S938B, serial `RFCY90MBYVZ`, Android 16/API36, 1080×2340,
  system fontScale `1.7`.
- Previous installed DEV: `org.beesearch.app.dev` 1.3.0 (versionCode 8),
  lastUpdateTime `2026-10-22 08:12:10`, APK SHA-256
  `3C3E8459388CD2F6F880AFB8579B87D84E2C2D11D20B5B6CED2BF22F9ED39E57`.
- New DEV from `ca9d4e3`: versionCode 8, versionName 1.3.0, APK SHA-256
  `90D7F6282AB96A6A93C7B6FD6D7ED62E8F8E724F27CF024E97B7D55C11EEC594`.
- Both APKs: `applicationId org.beesearch.app.dev`; V2 certificate SHA-256
  `2fd4f10ac24e19eb2e8c1bae08dca48b4402e751e9c33d6a33f59025b0bfb654`
  (same signer). Built and installed APK hashes matched.

## 2. In-place update

`adb -s RFCY90MBYVZ install -r app-debug.apk` returned `Success`.
No uninstall, `pm clear`, wipe, restore, stable/Beta install or app-private
deletion was performed. Updating the DEV package was the only device mutation.

| Fact | Before | After |
|---|---|---|
| versionCode | 8 | 8 |
| versionName | 1.3.0 | 1.3.0 |
| firstInstallTime | 2026-09-16 08:13:50 | **2026-09-16 08:13:50 (preserved)** |
| lastUpdateTime | 2026-10-22 08:12:10 | 2026-10-22 09:41:25 |
| installed APK SHA-256 | `3C3E…39E57` | `90D7…C594` |

The app opened successfully. No destructive Room fallback or schema change is
present: Room remains version 17, `PRAGMA integrity_check = ok`, foreign-key
check empty. No production/schema diff exists after `ca9d4e3`.

## 3. Data preservation

### Fresh backup before install

The app was stopped for a consistent snapshot. This is a new backup, not an
old-copy claim:

- internal app-private data: **4067 files / 305 973 547 bytes**, archive SHA-256
  `64c4043f9e81d2e0fa7d11349f9a7c1049135a0aa1447be2df37b94a43c74444`;
- external app-private data: **2128 files / 377 538 169 bytes**, archive SHA-256
  `0f2f4e537298516aa2c4d33135d989ac3a7a528a5094f81b00460afcadf90adc`;
- every archive member hash matched the on-device manifest; no missing, extra
  or mismatched files;
- the earlier `current-before.tar` (309 171 712 bytes) still matches its verified
  SHA-256 `a8e3aef95c9442404ddc2154fe65efdfd874c1864871ec4782c0f8ce47f3204a`.

`restore` was **NOT RUN**; the backup was not added to Git.

### Immediately after install, before launching

Internal and external file sets were **byte-identical** to the pre-install
snapshot: 4067/4067 and 2128/2128 files, zero missing, zero extra, zero changed.

### Final comparison after all UI checks

- Room tables: all **23** tables have identical counts and normalized row
  SHA-256; `table_changes = {}`; version 17; integrity `ok`; FK empty.
- The single existing user point is unchanged: same row, `created_at`
  1792599663690 (card date `21.10.2026`), **visibility ON**; one photo row and
  the same photo file with SHA-256
  `92cc7a5ed4b64c9b1c2522fc5c22d608ec05302c58198dfd327bcd2ab5b83be6`.
- The private title/coordinates/photo are deliberately not reproduced here.
- DataStore settings file SHA-256 unchanged:
  `4b1163f4de287a1eec0f70c16f3187d7889b67c3719c09feeedd0ba478400b73`.
- External app-private files: 2128/2128, **zero changed**.
- Internal files: 4067/4067, no missing/extra; six runtime files changed:
  `databases/bee_search.db-shm`, `files/mbgl-offline.db`,
  `files/profileInstalled`, and WorkManager
  `no_backup/androidx.work.workdb{,-shm,-wal}`. The main Room DB and WAL are
  byte-identical; these are runtime/cache/WorkManager state, not user records.
- GPX/import tables and research/physical tables are unchanged (e.g. GPX imports
  0; 5 observation points, 6 physical objects, 19 bees, 87 flight cycles,
  7 GPX deletions — all unchanged).
- No data-loss signal was observed; restore was not needed.

The current dataset had exactly one user point; it is the owner-designated
TEST-S25-02 target used for this run. No point was created, deleted or edited;
visibility remained ON.

## 4. VECTOR → карточка → «Показать на карте»

**PASS.** Samsung real-device steps:

1. Selected `Векторная карта`; actual offline vector tiles rendered, source
   selector reported `Источник карты: Векторная карта`
   ([03-vector-before-card](../build/evidence/my-points-samsung-show-on-map-20261022/03-vector-before-card.png)).
2. Opened the existing point card from the list; date/photo/coordinates were
   present ([06-card-vector](../build/evidence/my-points-samsung-show-on-map-20261022/06-card-vector.png)).
3. Pressed `Показать на карте`.
4. Source remained **`Векторная карта`**; it did **not** switch to ONLINE.
   The selected triangle bounds `[468,990][612,1134]` contain the fixed red
   centre `[522,1044][558,1080]`; no amber halo
   ([07-vector-after-show](../build/evidence/my-points-samsung-show-on-map-20261022/07-vector-after-show.png)).

This is the primary requested fix.

## 5. ONLINE → карточка → «Показать на карте»

**PASS.** Explicitly selected `Онлайн карта`, opened the same card and pressed
`Показать на карте`. Source remained `Источник карты: Онлайн карта`; the point
centred under the red pointer and the marker had the same graphite triangle
without amber
([14-online-after-show](../build/evidence/my-points-samsung-show-on-map-20261022/14-online-after-show.png)).

## 6. Back из карточки

- VECTOR → card → Back: source remained `Векторная карта`
  ([10-vector-after-back](../build/evidence/my-points-samsung-show-on-map-20261022/10-vector-after-back.png)).
  The old card did not reopen.
- ONLINE → card → Back: source remained `Онлайн карта`
  ([16-online-after-card-back](../build/evidence/my-points-samsung-show-on-map-20261022/16-online-after-card-back.png)).
- The map was opened both from the list and by tapping the triangle; both kept
  the current source in the same Activity. Repeated card opening
  ([15-card-online-reopened](../build/evidence/my-points-samsung-show-on-map-20261022/15-card-online-reopened.png))
  did not add amber.

## 7. No amber selected marker

**PASS.** Across vector/online, before/after show, after pan, after Back and
after repeated card opening, the marker remained the single fixed graphite
triangle with a light outline; selected state was internal only (`checked=true`
in accessibility) and did not add amber, enlarge or recolor it. The built
overlay has one `markers` SymbolLayer and one style image.

## 8. Centering and red pointer

**PASS.** After show-on-map the triangle was centred under the red pointer:
triangle `[468,990][612,1134]`, red pointer `[522,1044][558,1080]`. The red
pointer and GPS indicator remained separate; the measurement labels were
present. No measurement code or red-pointer code was changed.

## 9. Manual map movement

A one-finger map drag was accepted on Samsung. After the drag:

- red pointer remained at `[522,1044][558,1080]`;
- triangle moved to `[468,610][612,754]`, i.e. it retained its geographic
  position rather than staying screen-fixed;
- source remained `Векторная карта`;
- measurement updated (`3,8 м` / `799 м · 18°`);
- no amber appeared
  ([08-vector-after-pan-onefinger](../build/evidence/my-points-samsung-show-on-map-20261022/08-vector-after-pan-onefinger.png)).

## 10. Samsung screenshots

Real device screenshots (not emulator; local, Git-ignored evidence):

- [01 launch](../build/evidence/my-points-samsung-show-on-map-20261022/01-after-launch.png)
- [03 vector before card](../build/evidence/my-points-samsung-show-on-map-20261022/03-vector-before-card.png)
- [06 card from list](../build/evidence/my-points-samsung-show-on-map-20261022/06-card-vector.png)
- [07 vector after show](../build/evidence/my-points-samsung-show-on-map-20261022/07-vector-after-show.png)
- [08 vector after manual move](../build/evidence/my-points-samsung-show-on-map-20261022/08-vector-after-pan-onefinger.png)
- [09 card from triangle](../build/evidence/my-points-samsung-show-on-map-20261022/09-card-from-triangle-vector.png)
- [10 vector after Back](../build/evidence/my-points-samsung-show-on-map-20261022/10-vector-after-back.png)
- [14 online after show](../build/evidence/my-points-samsung-show-on-map-20261022/14-online-after-show.png)
- [15 card reopened](../build/evidence/my-points-samsung-show-on-map-20261022/15-card-online-reopened.png)
- [16 online after card Back](../build/evidence/my-points-samsung-show-on-map-20261022/16-online-after-card-back.png)
- [17 process restart actual result](../build/evidence/my-points-samsung-show-on-map-20261022/17-after-process-restart.png)
- [18 vector manually restored](../build/evidence/my-points-samsung-show-on-map-20261022/18-vector-restored-after-restart.png)

Card screenshots are private local evidence; title/coordinates/photo are not
reproduced in this report.

## 11. Status matrix

| Check | Status |
|---|---|
| In-place DEV update, signature, firstInstallTime | **PASS** |
| Backup before install + integrity | **PASS** |
| No data loss; Room/photo/settings/GPX/research counts | **PASS** |
| VECTOR → card → show → VECTOR | **PASS** |
| ONLINE → card → show → ONLINE | **PASS** |
| VECTOR → card → Back → VECTOR | **PASS** |
| Open from list / marker; no stale card replay | **PASS** |
| Explicit ONLINE ↔ VECTOR selector switch | **PASS** |
| No amber/enlarged selected marker, repeated states | **PASS** |
| Point centred under red pointer; red fixed after drag | **PASS** |
| Same-Activity basemap persistence | **PASS** |
| Process restart persistence | **NOT REQUIRED / observed reset to ONLINE**; after explicit VECTOR selection vector rendered again. Activity-scoped policy, not a failure of this fix |
| ON/OFF toggle | **NOT RUN**; visibility left ON |
| GPX/research visual regression | **NOT RUN**; DB tables/counts unchanged |
| Full A–D, TalkBack, process-death/fault, sensor, full basemap matrix | **NOT RUN** |
| Samsung Stable/Beta | **not touched** |
| Restore/backup replacement | **NOT RUN** |

**No production-code fix was made on device.** No restart-persistence or other
architecture change was attempted.

## 12. Git status

At qualification time no commit and no push were made. The subsequent
documentation-only finalization commit adds this report and the other D106
reports (hash in handoff/final response); it does not add production code,
backups or screenshots. The remaining worktree after that docs commit is:

```text
 M .agent/handoff.md
?? docs/d106-gpx-deletion-samsung-report.md
?? docs/d106-gpx-filename-samsung-report.md
?? docs/ui/evidence/my-points-stage-b-20261020/
```

The GPX reports and evidence remain outside the D106 docs commit; the production
tree is unchanged from `ca9d4e3`; Samsung DEV was stopped after the final
consistent snapshot. Stage1/H05 remain OPEN.
