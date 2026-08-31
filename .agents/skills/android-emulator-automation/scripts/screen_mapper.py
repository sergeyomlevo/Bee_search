#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from common import resolve_serial, run_adb


BOUNDS_RE = re.compile(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')


def parse_bounds(bounds: str) -> dict[str, int] | None:
    match = BOUNDS_RE.match(bounds)
    if not match:
        return None
    left, top, right, bottom = map(int, match.groups())
    return {
        'left': left,
        'top': top,
        'right': right,
        'bottom': bottom,
        'width': right - left,
        'height': bottom - top,
        'center_x': (left + right) // 2,
        'center_y': (top + bottom) // 2,
    }


class ScreenMapper:
    def __init__(self, serial: str):
        self.serial = serial
        self.dump_path = Path(tempfile.gettempdir()) / 'android-agent-skills-window-dump.xml'

    def dump(self) -> Path:
        last_error = None
        for remote_path in ('/sdcard/window_dump.xml', '/data/local/tmp/window_dump.xml'):
            for _ in range(2):
                result = run_adb(['shell', 'uiautomator', 'dump', remote_path], serial=self.serial, check=False, timeout=60)
                if result.returncode == 0:
                    pull = run_adb(['pull', remote_path, str(self.dump_path)], serial=self.serial, check=False, timeout=60)
                    if pull.returncode == 0 and self.dump_path.exists():
                        return self.dump_path
                    last_error = RuntimeError(pull.stderr.strip() or pull.stdout.strip() or f'Failed to pull {remote_path}')
                else:
                    last_error = RuntimeError(result.stderr.strip() or result.stdout.strip() or f'uiautomator dump failed for {remote_path}')
                time.sleep(0.5)
        raise last_error or RuntimeError('uiautomator dump failed unexpectedly')

    def analyze(self) -> dict[str, object]:
        tree = ET.parse(self.dump())
        root = tree.getroot()
        elements: list[dict[str, object]] = []

        def visit(node: ET.Element) -> dict[str, object]:
            element = {
                'class': node.get('class', ''),
                'text': node.get('text', ''),
                'resource_id': node.get('resource-id', ''),
                'content_desc': node.get('content-desc', ''),
                'package': node.get('package', ''),
                'clickable': node.get('clickable') == 'true',
                'enabled': node.get('enabled') == 'true',
                'focused': node.get('focused') == 'true',
                'selected': node.get('selected') == 'true',
                'scrollable': node.get('scrollable') == 'true',
                'checkable': node.get('checkable') == 'true',
                'checked': node.get('checked') == 'true',
                'bounds': parse_bounds(node.get('bounds', '')),
            }
            children = [visit(child) for child in list(node)]
            descendant_labels = []
            for child in children:
                label = str(child.get('label', '')).strip()
                if label:
                    descendant_labels.append(label)
                for nested in child.get('descendant_labels', []):
                    if nested and nested not in descendant_labels:
                        descendant_labels.append(nested)
            own_label = element['text'] or element['content_desc'] or element['resource_id']
            element['descendant_labels'] = descendant_labels
            element['label'] = own_label or (descendant_labels[0] if descendant_labels else '')
            elements.append(element)
            return element

        visit(root)
        interactive = [
            element for element in elements
            if element['clickable'] or element['scrollable'] or str(element['class']).endswith('EditText')
        ]
        return {
            'elements': elements,
            'interactive': interactive,
            'buttons': [
                element for element in interactive
                if str(element['class']).endswith('Button') or str(element['class']).endswith('Chip')
            ],
        }

    @staticmethod
    def format_summary(analysis: dict[str, object], limit: int) -> str:
        interactive = analysis['interactive']
        assert isinstance(interactive, list)
        lines = [f"Interactive elements: {len(interactive)}"]
        for element in interactive[:limit]:
            label = element.get('label') or element.get('text') or element.get('content_desc') or element.get('resource_id') or '<unlabeled>'
            suffix = []
            if element.get('selected'):
                suffix.append('selected')
            if element.get('checked'):
                suffix.append('checked')
            if element.get('focused'):
                suffix.append('focused')
            if suffix:
                label = f"{label} ({', '.join(suffix)})"
            lines.append(f"- {element.get('class')}: {label}")
        return '\n'.join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description='Dump and summarize the current Android screen.')
    parser.add_argument('--serial', help='Optional device serial')
    parser.add_argument('--json', action='store_true', help='Output full JSON analysis')
    parser.add_argument('--limit', type=int, default=10, help='Number of interactive elements to summarize')
    args = parser.parse_args()

    mapper = ScreenMapper(resolve_serial(args.serial))
    analysis = mapper.analyze()
    if args.json:
        print(json.dumps(analysis, indent=2))
    else:
        print(ScreenMapper.format_summary(analysis, args.limit))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
