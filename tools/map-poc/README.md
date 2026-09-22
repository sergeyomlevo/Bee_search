# Bee Search vector map PoC

This directory contains the small, reproducible development pipeline used to
validate the accepted Bee Search vector-map architecture. It is not an offline
download implementation and does not contain research data.

## What it builds

The pinned pipeline is:

```text
small OpenStreetMap API extracts
    -> pyosmium merge
    -> Planetiler 0.10.0 YAML field profile
    -> MBTiles
    -> uncompressed static XYZ/MVT tree
    -> MapLibre style + TileJSON + Noto Sans glyphs
```

The source maximum zoom is 15. Android may visually overscale these vector
tiles through UI zoom 20. Generated output and downloads live in ignored
`work/` and `out/` directories.

## Field profile

The profile deliberately contains only the field-useful subset needed for the
PoC. It preserves raw `surface` and normalizes it to:

| `surface_class` | Raw values |
|---|---|
| `paved` | asphalt, concrete, concrete:plates, paving_stones, sett, cobblestone |
| `compacted_gravel` | compacted, gravel, fine_gravel, pebblestone |
| `ground_dirt` | ground, dirt, earth, unpaved |
| `sand` | sand |
| `grass_mud` | grass, grass_paver, mud |
| `unknown` | missing or any value not listed above |

It also preserves `tracktype`, `man_made=cutline`, `power=line/minor_line`,
minor waterways, field landcover, buildings, settlements, railway and bridges.
The YAML examples are executable contract tests via Planetiler `verify`.

## Build

Requirements already used by the Android project workstation:

- PowerShell 7;
- `uv`;
- Python 3.11 (the pyosmium merge script is pinned to `>=3.11,<3.12`);
- Java 21+ (the Android Studio JBR works).

For an emulator:

```powershell
.\tools\map-poc\prepare-map.ps1
python .\tools\map-poc\serve.py
.\gradlew.bat installDebug
```

For a phone on the same trusted LAN, use the PC address in both generated
resources and the debug build. Example:

```powershell
$base = 'http://192.168.0.11:8080/field/v1/central-russia-poc-20260830/style-v3'
.\tools\map-poc\prepare-map.ps1 -BaseUrl $base
python .\tools\map-poc\serve.py
.\gradlew.bat installDebug "-PbeeMapStyleUrl=$base/style.json"
```

If the workstation firewall does not accept LAN connections, keep the server
local and use Android Debug Bridge port reversal instead of opening a firewall
port:

```powershell
$base = 'http://127.0.0.1:8080/field/v1/central-russia-poc-20260830/style-v3'
.\tools\map-poc\prepare-map.ps1 -BaseUrl $base
adb reverse tcp:8080 tcp:8080
.\gradlew.bat installDebug "-PbeeMapStyleUrl=$base/style.json"
```

The style uses the same canonical XYZ/MVT URLs that a future OfflineRegion
will request. `tiles.json` is a companion TileJSON manifest for diagnostics and
other clients; MapLibre Android receives the tile template inline in the style
so its built-in attribution UI retains the OSM attribution.

The ADB route exists only while the development device connection is active;
it is not part of the production map architecture.

`app/src/debug/AndroidManifest.xml` permits cleartext only in debug builds for
this LAN transport. The accepted production transport remains HTTPS.

The output URL is versioned and immutable in shape:

```text
/field/v1/central-russia-poc-20260830/style-v3/
  profile.json
  style.json
  tiles.json
  tiles/{z}/{x}/{y}.pbf
  glyphs/Noto Sans Regular/{range}.pbf
```

Runtime and future OfflineRegion preparation must use the same canonical
resource URLs. The PoC style does not use icons, so it intentionally has no
sprite dependency.

## Local D063/D065 map-package build

This is the canonical operator workflow when a user provides an Area JSON and
asks to create an offline map. Do not manually copy only the first `bounds`
item, and do not treat the outer bbox or a successful Android import as proof
that the source contains map data for every requested area.

An Area request is complete only when all entries in `bounds` pass both gates:

1. before Planetiler, the union of the selected source-region `.poly`
   footprints covers every `bounds[i]`;
2. after Planetiler, the PMTiles directory contains usable spatial tile data
   across every `bounds[i]`.

If the Area crosses source-region boundaries, first use a canonical merged PBF
containing every required extract and pass every component `.poly` footprint.
The builder fails closed when any requested bounds is not covered. A PBF header
bbox is only an envelope around an irregular extract and is deliberately not
accepted as the Area-source proof.

Run preflight before the full Planetiler pass:

```powershell
.\tools\map-poc\build-map-package.ps1 `
  -AreaJson '<exported-area.json>' `
  -SourcePbf '<source-containing-all-required-regions.osm.pbf>' `
  -SourceCoveragePolygon '<region-a.poly>','<region-b.poly>' `
  -PackageId 'Beta Test Territory--9cc6cc3a--map-v1' `
  -PlanOnly
```

Preflight parses the complete `bounds` array, reports the one outer generation
extent, fingerprints the selected PBF, and records a separate PASS for every
requested bounds. It never downloads or silently substitutes source data.

After reviewing preflight, omit `-PlanOnly` to build:

```powershell
.\tools\map-poc\build-map-package.ps1 `
  -AreaJson '<exported-area.json>' `
  -SourcePbf '<source-containing-all-required-regions.osm.pbf>' `
  -SourceCoveragePolygon '<region-a.poly>','<region-b.poly>' `
  -PackageId 'Beta Test Territory--9cc6cc3a--map-v1'
```

This entry point uses the pinned Planetiler 0.10.0 jar and the existing
`field-profile.yml` directly. Planetiler writes PMTiles v3 into a temporary
directory. The builder also refuses a malformed merged source when Planetiler
reports no OSM ways, even if Planetiler itself exits successfully. It then
reads the actual PMTiles header and directory, verifies spatial tile presence
for every original Area bounds, derives zoom and bounds, counts addressed
tiles/entries/contents, computes byte length and SHA-256, creates a D065
sidecar containing every Area coverage fragment, validates the pair, and only
then moves the completed output to the immutable local directory:

```text
work/packages/<packageId>/
  <packageId>.pmtiles
  <packageId>.pmtiles.manifest.json
  build-report.json
  planetiler-build.log
```

An existing final package directory is never overwritten. Failed staging is
removed and cannot replace a completed output. Generated packages remain
ignored workstation artifacts.

The explicit `-West/-South/-East/-North` mode remains available for low-level
diagnostics, but it is not the workflow for an exported Area JSON because it
cannot prove that the operator preserved the complete `bounds` array.

Deliver only the validated pair to user-accessible Samsung storage:

```powershell
.\tools\map-poc\push-map-package-for-import.ps1 `
  -PackageDirectory '.\tools\map-poc\work\packages\<packageId>' `
  -PackageId 'Beta Test Territory--9cc6cc3a--map-v1' `
  -Variant Beta `
  -Serial <device-serial>
```

The script validates the local pair, pushes it to
`/sdcard/Download/BeeSearch/<Variant>/Exchange/OfflineMaps/`, verifies the
device SHA-256, and registers both user files with Android media storage so
they are immediately visible in the system document picker. It
does not write app-private storage. Use `-Variant Stable`, `-Variant Beta`, or
`-Variant Dev` to match the installed application. In Bee Search choose
`Импортировать карту` or `Заменить карту`, select the manifest first and the
matching PMTiles second. The real `MapPackageStore` then performs staging, D065
validation, immutable installation, and active-pointer replacement.

`PackageId` is the map file stem, not a delivery directory. For an exported
Area, use the canonical `<areaStem>--map-v<N>` form from D083/D084. For
example, `Лух--7e82a310--map-v1` and the real Unicode/space stem
`Beta Test Territory--9cc6cc3a--map-v1` are valid;
`Lukh--7e82a310--map-v1` is an ASCII/Latin example. The PC tools
preserve Unicode and spaces, but reject path separators, traversal, control
characters, Windows-forbidden characters/device names, unsafe leading or
trailing characters, and names longer than 96 Unicode code points.

The Area JSON is normally obtained from
`Download/BeeSearch/<Variant>/Exchange/Areas/`. Generate the pair on the PC,
deliver it to `Download/BeeSearch/<Variant>/Exchange/OfflineMaps/`, then use
the Bee Search SAF import flow. The picker starts in that folder when the
platform permits the initial URI; externally copied files are still selected
manually: manifest first, then the matching `.pmtiles`.

For a negative atomic-replacement check, keep a valid package active and choose
the new manifest followed by a different old PMTiles file. Rejection must leave
the previous active package ready and renderable.

## Local PMTiles device fixtures

The legacy local PMTiles diagnostics use ignored workstation fixtures from
`C:\App\Bee_search_test_maps\pmtiles`. Stage them only into the installed
development package:

```powershell
$adb = 'C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe'
.\tools\map-poc\stage-dev-pmtiles.ps1 -Serial <device-serial> -Adb $adb
```

The script has no package override and verifies every copied SHA-256. It stages
only into `org.beesearch.app.dev/files/map-poc`; it never reads, replaces, or
clears `org.beesearch.app`.

Run this staging step after `connectedDebugAndroidTest`: the Gradle device-test
deployment may recreate the development container and remove its fixtures.

## Recreate the complete Bee Search DEV review state

For manual UI review after the full device suite, use the explicit development
bootstrap rather than rebuilding settings, coverage, an active package, and a
sample observation by hand:

```powershell
$adb = 'C:\Users\user\AppData\Local\Android\Sdk\platform-tools\adb.exe'
.\tools\dev-bootstrap.ps1 -Serial <device-serial> -Adb $adb
```

The command deliberately clears and recreates **only**
`org.beesearch.app.dev`, then seeds a DEV Observer, a current DEV Territory,
benchmark coverage, an active D065 package, and an active observation with
four marked Bees in mixed flight/at-point states. It also grants the declared
foreground location permissions to `.dev`, so the first-run permission prompt
does not cover manual map review. It installs and invokes a
development-only instrumentation entrypoint; it has no package override and
does not read, install, clear, stage files into, or launch `org.beesearch.app`.
It is therefore idempotent for development review, not a startup seed or a
field-data recovery mechanism.

The D065 manual-import development fixture is the pair
`territory-benchmark-v1.pmtiles` and
`territory-benchmark-v1.pmtiles.manifest.json`. It must be selected through the
Android Files picker in `org.beesearch.app.dev`; it is not a normal-map
dependency and must never be staged into or tested against `org.beesearch.app`.
The checked-in sidecar contract is in
`tools/map-poc/fixtures/territory-benchmark-v1.pmtiles.manifest.json`; the
binary remains an ignored workstation fixture.

After staging, the local PMTiles checks can be run through the development
runner:

```powershell
adb -s <device-serial> shell am instrument -w -r `
  -e beePmtiles true `
  -e beeSapunovoPmtiles true `
  -e beeTerritoryPmtiles true `
  -e beePmtilesDiagnostic true `
  -e beePmtilesLayerIsolation true `
  -e beeLabelDiagnostic true `
  -e class org.beesearch.app.BeeMapPocDeviceTest `
  org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner
```

`org.beesearch.app.BeeMapPocDeviceTest` above is a Kotlin class name, not the
Android package identity. The online-endpoint and custom glyph-URI checks
remain opt-in and require their own explicit arguments.

## Samsung validation

With the endpoint and ADB reverse active, build/install both APKs and run the
opt-in real-resource test:

```powershell
$debugPackage = 'org.beesearch.app.dev'
$debugTestPackage = "$debugPackage.test"
.\gradlew.bat installDebug assembleDebugAndroidTest "-PbeeMapStyleUrl=$base/style.json"
adb install -r -t .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -r -e beeMapPoc true `
  -e class org.beesearch.app.BeeMapPocDeviceTest `
  "$debugTestPackage/androidx.test.runner.AndroidJUnitRunner"
```

`$debugPackage` and `$debugTestPackage` are Android application identities for
the development artifacts. The class after `-e class` remains in the Kotlin
namespace `org.beesearch.app`; it is not the installed application ID.

The opt-in test temporarily tells MapLibre that the localhost ADB transport is
connected. This is test-only: the field package continues to use Android's
normal network status. The test verifies style/profile identity, required
rendered features at the three real areas, Russian labels, and z15 vector
overscaling at UI zooms 17 and 20.

## Attribution and provenance

- Map data: © OpenStreetMap contributors, ODbL 1.0.
- Extract provenance and exact bboxes/object IDs: `areas.json`.
- Tile generator: Planetiler 0.10.0, Apache License 2.0.
- Glyphs: OpenMapTiles font bundle, Noto Sans under SIL Open Font License 1.1.
  The PoC packages Latin, Cyrillic, and general-punctuation ranges observed in
  the real labels; a production dataset must package every range requested by
  its complete label set.

Do not commit generated extracts, binaries, MBTiles, tiles, or server logs.
