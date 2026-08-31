#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import subprocess
import sys

from common import ADB_PATH, resolve_serial, run_adb, wait_for_boot


def check_health(serial: str, timeout_secs: int) -> dict[str, object]:
    adb_version = run_adb(['version'], check=True).stdout.splitlines()[0]
    boot_ready = wait_for_boot(serial, timeout_secs=timeout_secs)
    uiautomator = run_adb(['shell', 'uiautomator', 'help'], serial=serial, check=False)
    package_check = run_adb(['shell', 'pm', 'list', 'packages'], serial=serial, check=False)
    return {
        'adb_path': ADB_PATH,
        'adb_version': adb_version,
        'serial': serial,
        'boot_completed': boot_ready,
        'uiautomator_available': uiautomator.returncode == 0,
        'package_manager_available': package_check.returncode == 0,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description='Check emulator and adb readiness for Android automation.')
    parser.add_argument('--serial', help='Optional device serial')
    parser.add_argument('--timeout', type=int, default=120, help='Seconds to wait for boot completion')
    parser.add_argument('--json', action='store_true', help='Output machine-readable JSON')
    args = parser.parse_args()

    try:
        serial = resolve_serial(args.serial)
        report = check_health(serial, args.timeout)
    except (RuntimeError, subprocess.CalledProcessError) as exc:
        print(str(exc), file=sys.stderr)
        return 1

    if args.json:
        print(json.dumps(report, indent=2))
    else:
        print(f"ADB: {report['adb_version']}")
        print(f"Device: {report['serial']}")
        print(f"Boot completed: {report['boot_completed']}")
        print(f"UIAutomator available: {report['uiautomator_available']}")
        print(f"Package manager available: {report['package_manager_available']}")
    return 0 if report['boot_completed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
