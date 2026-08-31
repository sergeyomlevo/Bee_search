#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
APP_DIR="$ROOT/examples/orbittasks-compose"
APK_PATH="$APP_DIR/app/build/outputs/apk/debug/app-debug.apk"

cd "$APP_DIR"
./gradlew :app:assembleDebug

python3 "$ROOT/skills/android-emulator-automation/scripts/device_health.py"
python3 "$ROOT/skills/android-emulator-automation/scripts/app_launcher.py" --install "$APK_PATH"
python3 "$ROOT/skills/android-emulator-automation/scripts/app_launcher.py" --launch dev.androidagentskills.orbittasks.compose
python3 "$ROOT/skills/android-emulator-automation/scripts/screen_mapper.py" --limit 12
