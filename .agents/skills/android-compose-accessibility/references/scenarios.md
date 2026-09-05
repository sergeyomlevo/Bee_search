# Android Compose Accessibility Scenarios

These are conceptual scenarios for Bee Search. Select actual repository tests
through the root `AGENTS.md` and `android-development` skill; no separate
OrbitTasks fixture or skill-trigger benchmark is included here.

## Happy path
- Goal: Add semantics and readable labels to the affected Bee Search controls.
- Check: Run the relevant Bee Search UI test and perform device review when the
  behavior depends on TalkBack, font scaling, or field ergonomics.

## Edge case
- Goal: Validate font scaling, contrast, and touch targets in narrow layouts.
- Check: Build the affected Bee Search variant and review the relevant UI on a
  suitable device configuration.

## Failure recovery
- Goal: Differentiate accessibility requests from general UI validation or theme work.
- Check: Apply the trigger rules in this skill and the repository routing in
  `AGENTS.md`.
