# /// script
# requires-python = ">=3.11,<3.12"
# dependencies = ["mapbox-vector-tile==2.2.0"]
# ///

"""Inspect deterministic layer/property evidence from generated MVT files."""

from __future__ import annotations

import argparse
import collections
import json
from pathlib import Path

import mapbox_vector_tile


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("tiles", type=Path)
    args = parser.parse_args()

    layers: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    examples: dict[str, list[dict]] = collections.defaultdict(list)
    all_properties: dict[str, list[dict]] = collections.defaultdict(list)
    tile_count = 0
    for tile_path in args.tiles.rglob("*.pbf"):
        tile_count += 1
        decoded = mapbox_vector_tile.decode(tile_path.read_bytes())
        for layer_name, layer in decoded.items():
            for feature in layer["features"]:
                properties = feature["properties"]
                all_properties[layer_name].append(properties)
                layers[layer_name][str(properties.get("class", "<none>"))] += 1
                if len(examples[layer_name]) < 8:
                    examples[layer_name].append(properties)

    required_classes = {
        "building": {"yes"},
        "field_infrastructure": {"cutline", "power"},
        "landcover": {"forest", "wetland"},
        "place": {"hamlet"},
        "transportation": {"track", "rail"},
        "water": {"water"},
        "waterway": {"river", "stream", "drain"},
    }
    for layer_name, required in required_classes.items():
        missing = required - set(layers[layer_name])
        if missing:
            raise RuntimeError(f"{layer_name} is missing required classes: {sorted(missing)}")

    tracks = [item for item in all_properties["transportation"] if item.get("class") == "track"]
    if not any(item.get("tracktype") in {"grade2", "grade3"} for item in tracks):
        raise RuntimeError("No generated track keeps the expected tracktype")
    if not any(item.get("surface") and item.get("surface_class") for item in tracks):
        raise RuntimeError("No generated track keeps raw and normalized surface")
    labels = [item.get("name_ru") or item.get("name") for item in all_properties["place"]]
    if not any(label and any("А" <= character <= "я" for character in label) for label in labels):
        raise RuntimeError("No Cyrillic settlement label survived generation")

    print(json.dumps({
        "requiredEvidence": "PASS",
        "tileCount": tile_count,
        "layerClassCounts": {name: dict(counts) for name, counts in sorted(layers.items())},
        "propertyExamples": dict(sorted(examples.items())),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
