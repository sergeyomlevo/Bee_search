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

## Samsung validation

With the endpoint and ADB reverse active, build/install both APKs and run the
opt-in real-resource test:

```powershell
.\gradlew.bat installDebug assembleDebugAndroidTest "-PbeeMapStyleUrl=$base/style.json"
adb install -r .\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -r -e beeMapPoc true `
  -e class org.beesearch.app.BeeMapPocDeviceTest `
  org.beesearch.app.test/androidx.test.runner.AndroidJUnitRunner
```

The opt-in test temporarily tells MapLibre that the localhost ADB transport is
connected. This is test-only: the production app continues to use Android's
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
