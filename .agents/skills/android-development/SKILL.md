---
name: android-development
description: Use when working on the Bee Search Android app: implementing or reviewing Kotlin/Jetpack Compose features, Room/DataStore persistence, Gradle changes, Android tests, ADB/device runs, GPS/location, sensors/heading, permissions, and Android build/debug workflows. Follow the repository AGENTS.md and relevant docs first. Keep changes incremental, offline-first, and verified.
---

# Bee Search Android Development

## Purpose

Provide a repeatable Android engineering workflow for the Bee Search repository.

This skill defines **how to execute Android development work**. It does not define Bee Search product truth.

Project truth lives in:

- `AGENTS.md`
- `docs/`
- `.agent/`

Always follow those files when they apply.

## Trigger

Use this skill when the task involves one or more of:

- Kotlin or Jetpack Compose implementation
- Gradle or Android Gradle Plugin configuration
- Room / SQLite
- DataStore
- Android navigation
- permissions
- GPS / location
- compass / heading / sensors
- MapLibre integration
- Android unit tests
- instrumentation tests
- ADB
- installing or running the app on a physical device
- Logcat or Android runtime debugging
- building APKs
- Android-specific refactoring or dependency changes

Do not trigger for documentation-only edits that require no Android implementation.

## 1. Start with repository context

Before editing:

1. Locate the repository root.
2. Read the root `AGENTS.md`.
3. Determine the type of change.
4. Read only the relevant documents in `docs/` as directed by `AGENTS.md`.
5. Apply `.agent/decision-policy.yaml` for non-trivial decisions.
6. Use `.agent/preferences.yaml` only as a tie-breaker where project truth leaves a real choice open.

Do not infer Bee Search domain rules from this skill.

## 2. Inspect before editing

Inspect the existing implementation before proposing structure.

At minimum, check as relevant:

```text
settings.gradle.kts
build.gradle.kts
gradle/libs.versions.toml
app/build.gradle.kts
app/src/main/
app/src/test/
app/src/androidTest/
```

Also inspect existing packages and naming patterns.

Prefer extending a valid existing pattern over introducing a parallel one.

## 3. Keep the change scoped

Implement the smallest coherent change required by the task.

Do not add:

- speculative server code
- synchronization code unless requested
- unused abstractions
- large dependency frameworks for small problems
- unrelated refactoring
- new domain entities without a documented project decision

If implementation exposes a conflict with project documentation, stop and follow the repository decision policy.

## 4. Gradle workflow

Prefer the Gradle Wrapper from the repository.

On Windows use:

```powershell
.\gradlew.bat <task>
```

On Unix-like systems use:

```bash
./gradlew <task>
```

Do not require a globally installed Gradle.

Before changing dependencies:

1. inspect existing version management;
2. prefer the existing dependency declaration style;
3. use stable, maintained libraries;
4. verify Android/API compatibility;
5. avoid dependency duplication.

After Gradle changes, run an appropriate sync/build check.

## 5. Default verification sequence

Choose checks proportional to the change.

For ordinary Kotlin/domain work, prefer:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

For changes that may affect Android resources, Compose, manifests, or static Android correctness, add:

```powershell
.\gradlew.bat lint
```

For targeted modules/tasks, use narrower Gradle tasks when that is faster and still sufficient.

Never claim a task passed unless it was actually run.

If verification is blocked by environment, tooling, network, emulator, or device availability, report exactly what was not verified.

## 6. Room / SQLite work

Before modifying persistence:

1. read `docs/data-model.md`;
2. read relevant decisions;
3. inspect the current Room schema;
4. preserve documented invariants.

Use transactions for domain operations where partial completion would create invalid state.

For Bee Search, the first group release is a canonical transactional operation.

Prefer database constraints for invariants that can be safely enforced there, plus domain validation where needed.

Do not duplicate derived state without a documented reason.

Before real field data exists, schema recreation may be acceptable during prototyping if the project policy allows it.

Once real field observations exist:

- do not use destructive migration as a shortcut;
- increment the Room schema version;
- add an explicit migration;
- add/adjust migration tests;
- preserve existing observations.

## 7. DataStore work

Use DataStore for small device-local settings, not research history.

Current examples include:

```text
current_territory_id
observer_code
```

Do not move structured observation history from Room into DataStore.

## 8. Compose UI work

UI should render persisted/domain state rather than inventing parallel business state.

Typical flow:

```text
user action
→ ViewModel
→ domain/repository operation
→ persistent write
→ Flow/StateFlow update
→ Compose UI
```

For critical field events, do not show success before the write succeeds.

Optimize frequent field operations for:

- few taps
- clear current state
- rapid switching between bees
- readability outdoors
- minimal deep navigation

Do not implement a sequential wizard if the documented workflow requires parallel Bee interaction.

## 9. Time handling

Persist event timestamps, not timer counters.

Displayed elapsed time should be derived from timestamps.

Prefer a testable clock/time source for domain operations when practical.

Do not scatter direct system clock calls throughout composables.

## 10. Location / GPS

Keep platform location access outside composables, behind the project boundary such as `LocationProvider`.

Preserve the distinction between:

- raw/current GPS reading
- user-confirmed ObservationPoint coordinates

Never overwrite manually confirmed point coordinates with later GPS updates unless explicitly requested by the workflow.

Request only permissions that are needed for the current feature.

Do not add background location permission for foreground-only field work.

## 11. Heading / compass / sensors

Keep Android sensor access behind a boundary such as `HeadingProvider`.

Do not equate raw magnetometer readings with a reliable azimuth.

Use appropriate Android orientation/rotation APIs and account for sensor accuracy and device orientation.

Azimuth capture must remain optional where required by project docs.

Real-device verification is mandatory before considering sensor behavior fully validated.

## 12. MapLibre

Treat MapLibre as the map engine, not automatically as the map-data provider.

Keep separate concerns for:

- engine
- style
- source
- offline storage

Do not lock the project into a specific offline format or provider while that project decision remains open.

When adding MapLibre, isolate SDK-specific code enough that domain logic does not depend on MapLibre classes.

Avoid building a generic GIS abstraction framework; use only the boundary needed by current project workflows.

## 13. ADB and physical device

For device-related tasks, first verify ADB availability:

```powershell
adb version
adb devices
```

If the device is shown as `unauthorized`, require USB-debugging authorization on the phone.

If multiple devices are connected, target the intended serial explicitly:

```powershell
adb -s <serial> ...
```

Useful commands include:

```powershell
adb devices
adb install -r <apk>
adb shell pm list packages
adb logcat
```

Prefer Android Studio/Gradle deployment when it is already working; use direct ADB when it improves diagnosis or repeatability.

Do not change device settings unrelated to the task.

## 14. Runtime debugging

When debugging:

1. reproduce the issue;
2. capture the smallest relevant error;
3. inspect Logcat/stack trace;
4. identify whether failure is build-time, install-time, permission, lifecycle, sensor, storage, or runtime;
5. fix the root cause rather than suppressing the symptom;
6. rerun the relevant verification.

Avoid broad dependency upgrades during a focused bug fix unless they are the cause.

## 15. Tests

Prioritize tests for behavior that can corrupt or misrepresent field observations.

Examples:

- first group release uses one timestamp
- group release is atomic
- FlightCycle sequence ordering
- no two open cycles for one Bee
- return time validity
- nullable/removable azimuth
- valid 0° azimuth
- Bee mark uniqueness within an ObservationPoint
- identical coordinates allowed across different ObservationPoints
- restoration of active persistent work

Use JVM unit tests for domain logic where Android runtime is unnecessary.

Use Room/instrumentation tests where Android or SQLite behavior must be verified.

Use physical-device testing for GPS, compass, real permissions, offline-map behavior, and field ergonomics.

## 16. Documentation

If implementation changes documented behavior, update the corresponding file under `docs/`.

Do not rewrite docs for private implementation details that leave behavior unchanged.

Durable product/architecture decisions belong in `docs/decisions.md`, not in this skill.

## 17. Completion report

At the end of an implementation task, report concisely:

- what was changed
- important design choice, if any
- tests/build commands actually run
- result of those checks
- anything that still requires real-device or field verification
- any documentation updated

If the task revealed an unresolved project-level choice, state it instead of silently deciding beyond the decision policy.
