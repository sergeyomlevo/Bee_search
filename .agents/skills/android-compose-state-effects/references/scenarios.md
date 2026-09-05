# Android Compose State Effects Scenarios

These are conceptual scenarios for Bee Search. Select actual repository tests
through the root `AGENTS.md` and `android-development` skill; no separate
OrbitTasks fixture or skill-trigger benchmark is included here.

## Happy path
- Goal: Collect task state and snackbar events in Compose without duplicate launches.
- Check: Run the relevant Bee Search unit or UI test for the affected state and
  event flow.

## Edge case
- Goal: Handle recomposition when permission state and sync state change together without restarting the wrong effect.
- Check: Build the affected Bee Search variant and exercise the relevant
  lifecycle transition.

## Failure recovery
- Goal: Disambiguate Compose runtime side effects from broader state-holder architecture requests.
- Check: Apply the trigger rules in this skill and the repository routing in
  `AGENTS.md`.
