# Android Emulator Automation Runnable Scenarios

## Happy path
- Goal: Assemble the Compose fixture, install it, launch it, and dump the initial screen summary.
- Command: `bash skills/android-emulator-automation/scripts/run_examples.sh`

## Edge case
- Goal: Find a semantic control by text or content description and tap it without using raw coordinates.
- Command: `python3 skills/android-emulator-automation/scripts/navigator.py --find-text "Review blocked state" --tap`

## Failure recovery
- Goal: Verify the emulator is boot-complete and `adb` can execute UIAutomator commands.
- Command: `python3 skills/android-emulator-automation/scripts/device_health.py`
