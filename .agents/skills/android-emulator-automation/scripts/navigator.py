#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import time

from common import resolve_serial, run_adb
from screen_mapper import ScreenMapper


def normalize(value: object) -> str:
    return str(value or '').strip().lower()


def matches(query: str | None, value: object, exact: bool) -> bool:
    if not query:
        return True
    lhs = normalize(value)
    rhs = normalize(query)
    return lhs == rhs if exact else rhs in lhs


def element_matches(query: str | None, element: dict[str, object], exact: bool) -> bool:
    if not query:
        return True
    return any(
        matches(query, candidate, exact)
        for candidate in (
            element.get('text'),
            element.get('content_desc'),
            element.get('resource_id'),
            element.get('label'),
            ' '.join(element.get('descendant_labels', [])),
        )
    )


def find_element(analysis: dict[str, object], text: str | None, resource_id: str | None, class_name: str | None, exact: bool, index: int) -> dict[str, object] | None:
    elements = analysis['elements']
    assert isinstance(elements, list)
    candidates = []
    for element in elements:
        if not element_matches(text, element, exact):
            continue
        if not matches(resource_id, element.get('resource_id'), exact):
            continue
        if not matches(class_name, element.get('class'), exact):
            continue
        candidates.append(element)
    candidates.sort(
        key=lambda element: (
            0 if element.get('clickable') else 1,
            0 if element.get('label') else 1,
            0 if element.get('bounds') else 1,
        )
    )
    if index >= len(candidates):
        return None
    return candidates[index]


def tap(serial: str, bounds: dict[str, int]) -> None:
    run_adb(['shell', 'input', 'tap', str(bounds['center_x']), str(bounds['center_y'])], serial=serial)


def enter_text(serial: str, text: str) -> None:
    escaped = text.replace(' ', '%s')
    run_adb(['shell', 'input', 'text', escaped], serial=serial)


def main() -> int:
    parser = argparse.ArgumentParser(description='Find and interact with Android UI elements semantically.')
    parser.add_argument('--find-text', help='Find an element by visible text or content description')
    parser.add_argument('--find-id', help='Find an element by resource ID')
    parser.add_argument('--find-class', help='Find an element by class name')
    parser.add_argument('--index', type=int, default=0, help='Select the Nth matching element')
    parser.add_argument('--exact', action='store_true', help='Use exact matching instead of substring matching')
    parser.add_argument('--tap', action='store_true', help='Tap the matched element')
    parser.add_argument('--enter-text', help='Enter text after optionally tapping the matched element')
    parser.add_argument('--json', action='store_true', help='Print the matched element as JSON')
    parser.add_argument('--serial', help='Optional device serial')
    args = parser.parse_args()

    serial = resolve_serial(args.serial)
    analysis = ScreenMapper(serial).analyze()
    element = find_element(analysis, args.find_text, args.find_id, args.find_class, args.exact, args.index)
    if not element:
        print('Element not found.')
        return 1

    if args.tap:
        bounds = element.get('bounds')
        if not isinstance(bounds, dict):
            print('Matched element has no tappable bounds.')
            return 1
        tap(serial, bounds)
        if args.enter_text:
            time.sleep(0.5)

    if args.enter_text:
        try:
            enter_text(serial, args.enter_text)
        except subprocess.CalledProcessError as exc:
            print(str(exc))
            return 1

    if args.json:
        print(json.dumps(element, indent=2))
    else:
        label = element.get('label') or element.get('text') or element.get('content_desc') or element.get('resource_id') or '<unlabeled>'
        print(f"Matched {element.get('class')}: {label}")
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
