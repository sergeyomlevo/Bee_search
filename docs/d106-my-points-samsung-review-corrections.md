# D106 — My points: Samsung review UI corrections (2026-10-21)

**Status: implemented, targeted checks PASS, stopped for OWNER review.** This is a
subsequent UI correction to the approved My points v1 package, not a rewrite of
its PNG history.

## Baseline and scope

- Repository `C:\App\Bee_search_stage0_overlay_registry`
- Branch `task/d106-stage0-overlay-registry`
- Baseline HEAD `80f08e6bc3985e94469bb571a9dd95c4a51f96cc`
- Pre-existing dirty/untracked Samsung/GPX/research/evidence files were preserved;
  they were not cleaned, reset or included in this correction commit.
- Three OWNER-approved changes only: secondary chooser headings, dark triangular
  user-point marker, creation date in the card.

The new decisions are recorded in
[decisions.md](decisions.md#d106-mypoint-samsung-review-ui-corrections--owner-decision-2026-10-21)
and the design addendum in
[my-points-v1.md §18](ui/mockups/my-points-v1.md#18-последующая-ui-корректировка-после-samsung-review--owner-2026-10-21).
Initial PNG/HTML/MD history was not redrawn.

## Implementation

### Chooser headings

`CreateRecordTypeChooserDialog` now renders both headings with
`MaterialTheme.typography.labelMedium` and
`MaterialTheme.colorScheme.onSurfaceVariant`, plus stable test tags. The texts,
order, divider, object buttons, disabled states and `Отмена` are unchanged. No
new colour token was introduced. On the API29 emulator's dynamic palette the
token renders as the theme's muted secondary colour; the headings are visibly
smaller than the object button labels and wrap without clipping.

### User-point marker

`UserPointMapOverlay` now declares two `SymbolLayer`s instead of the former blue
`CircleLayer`s:

- normal: graphite `#2B2B2B` upward triangle with a thin `#F2F2F2` outline;
- selected: the same dark triangle with a larger triangular amber `#FFB91F` halo.

Both icons are generated fixed `Bitmap` style images. `MapLibreOverlaySession`
has a narrow optional `MapStyleImage` seam: on each fully loaded Style it installs
missing images before restoring overlay layers and re-installs them after a style
reload. `MapOverlayRegistry` itself and its source/layer ownership were not
changed. `UserPointMarkerTargets` remains the existing 48 dp Compose hit target
with the same semantics/selection/card-opening path. Rotation and pitch alignment
are explicitly viewport-aligned, so the triangle always points up; it is not a
GPS or heading indicator. GPX, research marker and GPS presentation are unchanged.

### Creation date

`userPointCreatedDateText` formats the already persisted
`UserPoint.createdAt` using `dd.MM.yyyy` in the device's local calendar zone.
`UserPointDetailRoute` shows it as a `bodySmall` `onSurfaceVariant` line after the
title/note in the existing card. No Room schema or migration was added. The
existing repository test proves update preserves `createdAt`; the date is absent
from create/edit forms and the list.

## Verification

| Check | Result |
|---|---|
| Full JVM | **1072/1072 PASS**, 129 classes, 0 failures/errors/skips |
| Build/lint gates | all **7/7 PASS** (`assembleDebug/Beta/Release`, `assembleDebugAndroidTest`, `lintDebug/Beta/Release`) |
| API29 targeted Android | **28/28 PASS** (22 user-point creation/card/list/native/stage-D + 6 user-point repository) |
| New chooser checks | section labels at fontScale 1.7, scroll, all actions and pinned `Отмена` PASS |
| New card checks | stored creation date rendered; no date in create/edit/list; edit preservation covered by repository test |
| Native marker checks | both layers are `SymbolLayer`, both style images present, style reload restores them, OFF/ON and scope tests PASS |
| Manual marker tap | tapping the C triangle on the emulator opened the correct `C map marker` card |
| Build/diff | `git diff --check` PASS |

### Emulator evidence

API29 AVD, serial `emulator-5556`, 1080×2280, system `fontScale=1.7`. Device
package `org.beesearch.app.dev`, versionName `1.3.0`, versionCode `8`. Installed
APK SHA-256 equals the local debug build:
`3C3E8459388CD2F6F880AFB8579B87D84E2C2D11D20B5B6CED2BF22F9ED39E57`.

- [Chooser section labels](ui/evidence/my-points-review-20261021/chooser-section-labels-17.png)
- [Two triangles and GPS indicator](ui/evidence/my-points-review-20261021/map-triangles-gps-17.png)
- [Selected triangular marker](ui/evidence/my-points-review-20261021/map-selected-triangle-17.png)
- [Card with creation date](ui/evidence/my-points-review-20261021/card-created-date-17.png)

The two-marker screenshot shows the dark triangles and the separate blue GPS
position dot; the selected screenshot shows the amber triangular halo. The
triangle is dark graphite with a light outline and remains upright.

## Remaining limits / NOT RUN

- New APK was **not installed on Samsung**. TEST-S25-02 and all Samsung private
  data were not changed; no Samsung screenshot or physical visual acceptance is
  claimed.
- Full Android regression suite was not rerun; only the targeted API29 set above.
- Full basemap contrast matrix is NOT RUN. Online light/urban/green areas were
  inspected; the installed vector offline basemap in this emulator did not load
  tiles during this check, so satellite/hybrid/vector matrix is not claimed.
- TalkBack, process death, ENOSPC, provider failure and real sensor/GPS accuracy
  remain NOT RUN; the earlier Samsung NOT RUN rows remain valid.
- Stage1/H05 remain OPEN.

One local implementation commit was prepared with no push. Stop for OWNER
review.
