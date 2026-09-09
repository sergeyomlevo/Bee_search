#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' \
    'No runnable emulator example is included in this Bee Search checkout.' \
    'Build Bee Search with its Gradle wrapper, verify the intended device, and' \
    'use the helper scripts in this skill with development package org.beesearch.app.dev.' \
    'No build or device verification was performed.' >&2
exit 2
