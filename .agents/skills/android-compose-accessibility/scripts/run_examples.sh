#!/usr/bin/env bash
    set -euo pipefail

printf '%s\n' \
    'No runnable accessibility example is included in this Bee Search checkout.' \
    'Select relevant Gradle, UI, and physical-device checks through AGENTS.md' \
    'and the android-development skill. No verification was performed.' >&2
exit 2
