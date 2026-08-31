# Android Emulator Automation Patterns

## Selection Notes
- Category: `quality-release`
- Best fit when the request needs semantic app launch, UI hierarchy inspection, or agent-driven emulator interaction without writing full instrumentation tests.
- Reach for `android-testing-ui` when the goal shifts from smoke automation to stable regression coverage.

## Default Review Sequence
1. Resolve the device serial and boot state.
2. Launch or install the target package.
3. Dump the UI hierarchy and identify semantic selectors.
4. Perform one narrow interaction and re-check the state.
5. Escalate persistent flows into UI tests if they need ongoing coverage.

## Handoff Shortlist
- `android-testing-ui`
- `android-permissions-activity-results`
