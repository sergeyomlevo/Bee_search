"""Export Planetiler MBTiles and build versioned static MapLibre resources."""

from __future__ import annotations

import argparse
import gzip
import json
import shutil
import sqlite3
import zipfile
from pathlib import Path


PROFILE_ID = "bee-search-field"
PROFILE_VERSION = "v1"
DATASET_VERSION = "central-russia-poc-20260830"
STYLE_VERSION = "style-v3"
SOURCE_MIN_ZOOM = 8
SOURCE_MAX_ZOOM = 15
UI_MAX_ZOOM = 20
FONT_STACK = "Noto Sans Regular"
# Latin, Cyrillic, and general punctuation used by real labels in the PoC data.
GLYPH_RANGES = ("0-255.pbf", "1024-1279.pbf", "8192-8447.pbf")
OSM_ATTRIBUTION = (
    '<a href="https://www.openstreetmap.org/copyright">'
    '© OpenStreetMap contributors</a>'
)
BOUNDS = [42.4960, 56.0615, 42.7940, 56.2365]
CENTER = [42.6650, 56.1400, 12]


def export_tiles(mbtiles: Path, destination: Path) -> tuple[int, int]:
    tile_count = 0
    byte_count = 0
    with sqlite3.connect(mbtiles) as database:
        for zoom, column, tms_row, tile_data in database.execute(
            "SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles"
        ):
            xyz_row = (1 << zoom) - 1 - tms_row
            tile_path = destination / str(zoom) / str(column) / f"{xyz_row}.pbf"
            tile_path.parent.mkdir(parents=True, exist_ok=True)
            data = bytes(tile_data)
            if data.startswith(b"\x1f\x8b"):
                data = gzip.decompress(data)
            tile_path.write_bytes(data)
            tile_count += 1
            byte_count += len(data)
    return tile_count, byte_count


def extract_glyphs(font_zip: Path, destination: Path) -> None:
    with zipfile.ZipFile(font_zip) as archive:
        names = archive.namelist()
        for glyph_range in GLYPH_RANGES:
            suffix = f"/{FONT_STACK}/{glyph_range}"
            matches = [name for name in names if name.endswith(suffix) or name == f"{FONT_STACK}/{glyph_range}"]
            if len(matches) != 1:
                raise RuntimeError(f"Expected one {suffix} in {font_zip}, found {matches}")
            target = destination / FONT_STACK / glyph_range
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(matches[0]) as source, target.open("wb") as output:
                shutil.copyfileobj(source, output)


def style(base_url: str) -> dict:
    source = "bee-search-field"
    label_text = ["coalesce", ["get", "name_ru"], ["get", "name"]]
    return {
        "version": 8,
        "name": "Bee Search Field PoC v1",
        "metadata": {
            "bee_search:profile": f"{PROFILE_ID}/{PROFILE_VERSION}",
            "bee_search:dataset": DATASET_VERSION,
            "bee_search:style": STYLE_VERSION,
            "bee_search:source_maxzoom": SOURCE_MAX_ZOOM,
            "bee_search:ui_maxzoom": UI_MAX_ZOOM,
        },
        "sources": {
            source: {
                "type": "vector",
                "tiles": [f"{base_url}/tiles/{{z}}/{{x}}/{{y}}.pbf"],
                "minzoom": SOURCE_MIN_ZOOM,
                "maxzoom": SOURCE_MAX_ZOOM,
                "bounds": BOUNDS,
                "attribution": OSM_ATTRIBUTION,
            }
        },
        "glyphs": f"{base_url}/glyphs/{{fontstack}}/{{range}}.pbf",
        "layers": [
            {"id": "background", "type": "background", "paint": {"background-color": "#f5f0df"}},
            {
                "id": "open-land",
                "type": "fill",
                "source": source,
                "source-layer": "landcover",
                "filter": ["in", ["get", "class"], ["literal", ["farmland", "meadow", "grassland", "heath"]]],
                "paint": {"fill-color": ["match", ["get", "class"], "farmland", "#eadca8", "heath", "#d9c5a1", "#cfe0a8"], "fill-opacity": 0.8},
            },
            {
                "id": "forest",
                "type": "fill",
                "source": source,
                "source-layer": "landcover",
                "filter": ["==", ["get", "class"], "forest"],
                "paint": {"fill-color": "#75a96b", "fill-opacity": 0.72},
            },
            {
                "id": "scrub",
                "type": "fill",
                "source": source,
                "source-layer": "landcover",
                "filter": ["==", ["get", "class"], "scrub"],
                "paint": {"fill-color": "#9dba79", "fill-opacity": 0.68},
            },
            {
                "id": "wetland",
                "type": "fill",
                "source": source,
                "source-layer": "landcover",
                "filter": ["==", ["get", "class"], "wetland"],
                "paint": {"fill-color": "#83c6b8", "fill-opacity": 0.66, "fill-outline-color": "#287d78"},
            },
            {
                "id": "water",
                "type": "fill",
                "source": source,
                "source-layer": "water",
                "paint": {"fill-color": "#68aee8", "fill-outline-color": "#2d78b7"},
            },
            {
                "id": "minor-waterways",
                "type": "line",
                "source": source,
                "source-layer": "waterway",
                "filter": ["in", ["get", "class"], ["literal", ["stream", "ditch", "drain", "canal"]]],
                "paint": {"line-color": "#277fc0", "line-width": ["interpolate", ["linear"], ["zoom"], 12, 1.2, 18, 2.7], "line-dasharray": [3, 2]},
            },
            {
                "id": "rivers",
                "type": "line",
                "source": source,
                "source-layer": "waterway",
                "filter": ["==", ["get", "class"], "river"],
                "paint": {"line-color": "#176cab", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 1.2, 18, 4.0]},
            },
            {
                "id": "railway-casing",
                "type": "line",
                "source": source,
                "source-layer": "transportation",
                "filter": ["==", ["get", "class"], "rail"],
                "paint": {"line-color": "#fff7e8", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 2.5, 18, 6.0]},
            },
            {
                "id": "railway",
                "type": "line",
                "source": source,
                "source-layer": "transportation",
                "filter": ["==", ["get", "class"], "rail"],
                "paint": {"line-color": "#3c3530", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 1.0, 18, 2.0], "line-dasharray": [2, 2]},
            },
            {
                "id": "road-casing",
                "type": "line",
                "source": source,
                "source-layer": "transportation",
                "filter": ["in", ["get", "class"], ["literal", ["motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential", "service"]]],
                "paint": {"line-color": "#5b4a3c", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 1.4, 18, 7.0]},
            },
            {
                "id": "roads",
                "type": "line",
                "source": source,
                "source-layer": "transportation",
                "filter": ["in", ["get", "class"], ["literal", ["motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential", "service"]]],
                "paint": {"line-color": ["match", ["get", "surface_class"], "paved", "#fff2cf", "compacted_gravel", "#e7c887", "ground_dirt", "#c58f4f", "sand", "#e4bd63", "#d9c39c"], "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.9, 18, 5.0]},
            },
            {
                "id": "tracks",
                "type": "line",
                "source": source,
                "source-layer": "transportation",
                "filter": ["==", ["get", "class"], "track"],
                "paint": {"line-color": ["match", ["get", "tracktype"], "grade1", "#6f5436", "grade2", "#875e32", "grade3", "#9b6834", "grade4", "#aa7a45", "grade5", "#b88b55", "#8c653d"], "line-width": ["interpolate", ["linear"], ["zoom"], 12, 1.6, 18, 4.0], "line-dasharray": [4, 2]},
            },
            {
                "id": "paths",
                "type": "line",
                "source": source,
                "source-layer": "transportation",
                "filter": ["in", ["get", "class"], ["literal", ["path", "footway", "steps"]]],
                "paint": {"line-color": "#7b3f21", "line-width": ["interpolate", ["linear"], ["zoom"], 12, 1.3, 18, 3.0], "line-dasharray": [1.5, 1.5]},
            },
            {
                "id": "cutlines",
                "type": "line",
                "source": source,
                "source-layer": "field_infrastructure",
                "filter": ["==", ["get", "class"], "cutline"],
                "paint": {"line-color": "#6b3fa0", "line-width": ["interpolate", ["linear"], ["zoom"], 12, 2.0, 18, 4.0], "line-dasharray": [5, 2]},
            },
            {
                "id": "power-lines",
                "type": "line",
                "source": source,
                "source-layer": "field_infrastructure",
                "filter": ["==", ["get", "class"], "power"],
                "paint": {"line-color": "#4e454d", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 1.0, 18, 2.0], "line-dasharray": [2, 1]},
            },
            {
                "id": "buildings",
                "type": "fill",
                "source": source,
                "source-layer": "building",
                "paint": {"fill-color": "#b79c86", "fill-outline-color": "#6e5746", "fill-opacity": 0.85},
            },
            {
                "id": "place-labels",
                "type": "symbol",
                "source": source,
                "source-layer": "place",
                "layout": {"text-field": label_text, "text-font": [FONT_STACK], "text-size": ["interpolate", ["linear"], ["zoom"], 8, 12, 16, 17], "text-allow-overlap": False},
                "paint": {"text-color": "#211d19", "text-halo-color": "#fffaf0", "text-halo-width": 1.5},
            },
            {
                "id": "road-labels",
                "type": "symbol",
                "source": source,
                "source-layer": "transportation",
                "filter": ["has", "name"],
                "minzoom": 13,
                "layout": {"symbol-placement": "line", "text-field": label_text, "text-font": [FONT_STACK], "text-size": 12, "text-rotation-alignment": "map"},
                "paint": {"text-color": "#3d3025", "text-halo-color": "#fffaf0", "text-halo-width": 1.2},
            },
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mbtiles", required=True, type=Path)
    parser.add_argument("--font-zip", required=True, type=Path)
    parser.add_argument("--output-root", required=True, type=Path)
    parser.add_argument("--base-url", required=True)
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    destination = args.output_root / "field" / PROFILE_VERSION / DATASET_VERSION / STYLE_VERSION
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)

    tile_count, tile_bytes = export_tiles(args.mbtiles, destination / "tiles")
    extract_glyphs(args.font_zip, destination / "glyphs")

    tilejson = {
        "tilejson": "3.0.0",
        "name": "Bee Search Field PoC",
        "scheme": "xyz",
        "tiles": [f"{base_url}/tiles/{{z}}/{{x}}/{{y}}.pbf"],
        "minzoom": SOURCE_MIN_ZOOM,
        "maxzoom": SOURCE_MAX_ZOOM,
        "bounds": BOUNDS,
        "center": CENTER,
        "attribution": OSM_ATTRIBUTION,
    }
    manifest = {
        "profileId": PROFILE_ID,
        "profileVersion": PROFILE_VERSION,
        "datasetVersion": DATASET_VERSION,
        "styleVersion": STYLE_VERSION,
        "sourceMinZoom": SOURCE_MIN_ZOOM,
        "sourceMaxZoom": SOURCE_MAX_ZOOM,
        "uiMaxZoom": UI_MAX_ZOOM,
        "bounds": BOUNDS,
        "tileCount": tile_count,
        "uncompressedTileBytes": tile_bytes,
        "canonicalBaseUrl": base_url,
        "attribution": "© OpenStreetMap contributors",
        "licenses": {
            "osm": "ODbL 1.0; see https://www.openstreetmap.org/copyright",
            "font": "Noto Sans (SIL Open Font License 1.1)",
        },
    }

    (destination / "tiles.json").write_text(json.dumps(tilejson, ensure_ascii=False, indent=2), encoding="utf-8")
    (destination / "style.json").write_text(json.dumps(style(base_url), ensure_ascii=False, indent=2), encoding="utf-8")
    (destination / "profile.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Built {destination}")
    print(f"Tiles: {tile_count}; uncompressed bytes: {tile_bytes}")


if __name__ == "__main__":
    main()
