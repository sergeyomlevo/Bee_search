---
name: "android-compose-state-effects"
description: "Manage Compose state, remember APIs, side effects, snapshots, and lifecycle-aware collection without leaks or loops."
metadata:
  version: "0.1.0"
  category: "ui"
  tags: ["android", "compose", "state", "side-effects"]
  triggers:
    include: ["compose side effect problem", "remember vs derivedstateof", "collect flow in compose screen", "launchedeffect issue android", "compose state hoisting", "android-compose-state-effects skill", "rememberupdatedstate compose", "snapshotflow compose issue", "launchedeffect issue android before release"]
    exclude: ["xml recycler issue", "apk alignment", "play console release", "release automation", "android signing and release"]
  owners: ["@android-agent-skills/maintainers"]
---
# Android Compose State Effects

## When To Use
- Use this skill when the request is about: compose side effect problem, remember vs derivedstateof, collect flow in compose screen.
- Primary outcome: Manage Compose state, remember APIs, side effects, snapshots, and lifecycle-aware collection without leaks or loops.
- Reach for this skill when the problem lives inside a composable or Compose runtime primitive, not when redesigning app-wide state holders or reducers.
- This skill is about runtime primitives inside Compose. If the question is reducer design, screen contracts, or ViewModel ownership, hand off to `android-state-management`.
- Handoff skills when the scope expands:
- `android-state-management`
- `android-compose-performance`
- These are upstream capability labels and may not be installed as local Bee
  Search skills. Use them only when the current environment provides them.

## Workflow
1. Classify the issue first: local remembered state, saved state, derived state, lifecycle-aware collection, or one-off side effects.
2. Pick the narrowest Compose primitive that matches that problem: `remember`, `rememberSaveable`, `derivedStateOf`, `produceState`, `collectAsStateWithLifecycle`, `LaunchedEffect`, `DisposableEffect`, `SideEffect`, or `snapshotFlow`.
3. Keep durable UI state separate from transient events such as snackbars, navigation, and analytics.
4. Stabilize effect keys and callback references so recomposition does not relaunch work or capture stale lambdas.
5. Hand off broader state-holder design to `android-state-management` only after the Compose-runtime issue is isolated.

## Guardrails
- Prefer lifecycle-aware collection such as `collectAsStateWithLifecycle` for UI-facing flows.
- Use `rememberUpdatedState` when an effect should see the latest lambda or value without restarting.
- Keep one-shot effects out of immutable screen state when they are really events.
- Do not launch coroutines or trigger navigation directly from the composable body outside the appropriate effect APIs.

## Anti-Patterns
- Using `LaunchedEffect(Unit)` or unstable keys when the effect should restart on real dependency changes.
- Collecting flows in multiple places and wondering why events duplicate.
- Storing transient events as persistent booleans in screen state and then manually resetting them.
- Using `derivedStateOf` or `snapshotFlow` when plain state reads would be simpler and cheaper.

## Review Focus
- Which runtime primitive owns this behavior?
- Are recomposition and effect restarts intentional?
- Are state and event channels modeled separately?
- Is collection lifecycle-aware and cancellation-safe?

## Examples

The upstream skill examples referenced fixture and benchmark paths that are not
part of Bee Search. For this repository, select the relevant Gradle and UI
checks through the root `AGENTS.md` and the `android-development` skill. Do not
cite an upstream fixture run as Bee Search verification.

## Done Checklist
- The chosen Compose primitive matches the actual runtime problem.
- Effects have stable keys and do not duplicate on recomposition.
- State and events are separated clearly.
- Broader app-state architecture work is handed off instead of mixed into the composable fix.

## Official References
- [https://developer.android.com/develop/ui/compose/state](https://developer.android.com/develop/ui/compose/state)
- [https://developer.android.com/develop/ui/compose/side-effects](https://developer.android.com/develop/ui/compose/side-effects)
- [https://developer.android.com/reference/kotlin/androidx/lifecycle/compose/package-summary](https://developer.android.com/reference/kotlin/androidx/lifecycle/compose/package-summary)
- [https://developer.android.com/topic/architecture/ui-layer/state-production](https://developer.android.com/topic/architecture/ui-layer/state-production)
