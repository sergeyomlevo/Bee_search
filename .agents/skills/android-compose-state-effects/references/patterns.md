# Android Compose State Effects Patterns

## Selection Notes
- Category: `ui`
- Best fit when the request matches the trigger language in `SKILL.md` and the implementation focus is `Manage Compose state, remember APIs, side effects, snapshots, and lifecycle-aware collection without leaks or loops.`
- Reach for neighboring skills only after this skill has framed the main problem.

## Primitive Selection Guide
- Local ephemeral UI state:
  Prefer: `remember`
- State that should survive configuration or process recreation:
  Prefer: `rememberSaveable` or hoisted state from a ViewModel
- Derived view-only calculation:
  Prefer: `derivedStateOf`
- Convert async source to Compose state:
  Prefer: `collectAsStateWithLifecycle` or `produceState`
- One-off coroutine work tied to keys:
  Prefer: `LaunchedEffect`
- Register and clean up listeners:
  Prefer: `DisposableEffect`
- Need latest lambda/value inside a long-lived effect:
  Prefer: `rememberUpdatedState`

## Default Review Sequence
1. Decide whether the bug is state ownership, effect lifecycle, or event delivery.
2. Pick the narrowest Compose runtime primitive that fits.
3. Separate durable state from one-off effects.
4. Check key stability and stale-capture risk.
5. Hand off state-holder design only if the problem is no longer Compose-runtime-specific.

## Handoff Shortlist
- `android-state-management`
- `android-compose-performance`
