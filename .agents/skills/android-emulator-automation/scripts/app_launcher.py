#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import time

from common import resolve_serial, run_adb


def inspect_state(serial: str, package: str) -> dict[str, object]:
    pid_result = run_adb(['shell', 'pidof', package], serial=serial, check=False)
    activity_result = run_adb(['shell', 'dumpsys', 'activity', 'activities'], serial=serial, check=False, timeout=60)
    focused = package in activity_result.stdout and 'topResumedActivity' in activity_result.stdout
    return {
        'action': 'state',
        'package': package,
        'running': pid_result.returncode == 0 and bool(pid_result.stdout.strip()),
        'pid': pid_result.stdout.strip(),
        'focused': focused,
    }


def wait_until_running(serial: str, package: str, timeout_secs: int) -> dict[str, object]:
    deadline = time.time() + timeout_secs
    last_state = inspect_state(serial, package)
    while time.time() < deadline:
        last_state = inspect_state(serial, package)
        if last_state['running'] or last_state['focused']:
            return last_state
        time.sleep(0.5)
    return last_state


def launch(serial: str, package: str, activity: str | None, wait_secs: int) -> dict[str, object]:
    if activity:
        result = run_adb(['shell', 'am', 'start', '-n', f'{package}/{activity}'], serial=serial, check=False)
    else:
        result = run_adb(
            ['shell', 'monkey', '-p', package, '-c', 'android.intent.category.LAUNCHER', '1'],
            serial=serial,
            check=False,
        )
    state = wait_until_running(serial, package, timeout_secs=wait_secs)
    return {
        'action': 'launch',
        'ok': result.returncode == 0 and (state['running'] or state['focused']),
        'stdout': result.stdout.strip(),
        'stderr': result.stderr.strip(),
        'state': state,
    }


def install(serial: str, apk_path: str) -> dict[str, object]:
    result = run_adb(['install', '-r', apk_path], serial=serial, check=False, timeout=180)
    return {'action': 'install', 'ok': result.returncode == 0, 'stdout': result.stdout.strip(), 'stderr': result.stderr.strip()}


def terminate(serial: str, package: str) -> dict[str, object]:
    result = run_adb(['shell', 'am', 'force-stop', package], serial=serial, check=False)
    return {'action': 'terminate', 'ok': result.returncode == 0, 'stdout': result.stdout.strip(), 'stderr': result.stderr.strip()}


def main() -> int:
    parser = argparse.ArgumentParser(description='Install, launch, stop, or inspect Android packages via adb.')
    parser.add_argument('--install', help='APK path to install')
    parser.add_argument('--launch', help='Package name to launch')
    parser.add_argument('--activity', help='Optional activity name for --launch')
    parser.add_argument('--wait-secs', type=int, default=5, help='Seconds to wait for a launched app to become active')
    parser.add_argument('--terminate', help='Package name to force-stop')
    parser.add_argument('--state', help='Package name to inspect')
    parser.add_argument('--json', action='store_true', help='Output JSON')
    parser.add_argument('--serial', help='Optional device serial')
    args = parser.parse_args()

    serial = resolve_serial(args.serial)
    if args.install:
        payload = install(serial, args.install)
    elif args.launch:
        payload = launch(serial, args.launch, args.activity, args.wait_secs)
    elif args.terminate:
        payload = terminate(serial, args.terminate)
    elif args.state:
        payload = inspect_state(serial, args.state)
    else:
        parser.error('Choose one of --install, --launch, --terminate, or --state')

    if args.json:
        print(json.dumps(payload, indent=2))
    else:
        print(payload)
    return 0 if payload.get('ok', True) else 1


if __name__ == '__main__':
    raise SystemExit(main())
