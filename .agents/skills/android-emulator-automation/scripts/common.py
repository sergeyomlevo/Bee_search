#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import time
from pathlib import Path


def find_adb_path() -> str:
    android_home = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if android_home:
        candidate = Path(android_home) / 'platform-tools' / 'adb'
        if candidate.exists():
            return str(candidate)
    try:
        subprocess.run(['adb', 'version'], check=True, capture_output=True, text=True)
        return 'adb'
    except (FileNotFoundError, subprocess.CalledProcessError):
        pass
    for candidate in (
        Path.home() / 'Library/Android/sdk/platform-tools/adb',
        Path.home() / 'Android/Sdk/platform-tools/adb',
    ):
        if candidate.exists():
            return str(candidate)
    return 'adb'


ADB_PATH = find_adb_path()


def run_adb(args: list[str], serial: str | None = None, check: bool = True, timeout: int = 30) -> subprocess.CompletedProcess[str]:
    command = [ADB_PATH]
    if serial:
        command.extend(['-s', serial])
    command.extend(args)
    return subprocess.run(command, check=check, capture_output=True, text=True, timeout=timeout)


def list_devices() -> list[str]:
    result = run_adb(['devices'], check=True)
    devices: list[str] = []
    for line in result.stdout.splitlines()[1:]:
        if not line.strip():
            continue
        serial, _, state = line.partition('\t')
        if state.strip() == 'device':
            devices.append(serial.strip())
    return devices


def resolve_serial(serial: str | None = None) -> str:
    devices = list_devices()
    if serial:
        if serial not in devices:
            raise RuntimeError(f"Device '{serial}' not found. Connected devices: {devices or ['none']}")
        return serial
    if not devices:
        raise RuntimeError('No Android devices or emulators are connected.')
    if len(devices) > 1:
        raise RuntimeError(f"Multiple devices connected: {', '.join(devices)}. Use --serial.")
    return devices[0]


def wait_for_boot(serial: str, timeout_secs: int = 120) -> bool:
    deadline = time.time() + timeout_secs
    while time.time() < deadline:
        result = run_adb(['shell', 'getprop', 'sys.boot_completed'], serial=serial, check=False)
        if result.returncode == 0 and result.stdout.strip() == '1':
            return True
        time.sleep(2)
    return False
