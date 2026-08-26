# AGENTS.md — Bee Search

## 1. Purpose

This file defines how coding agents work with the **Bee Search** repository.

`AGENTS.md` is a routing and control document. It does not duplicate detailed
product, domain, data, or architecture documentation.

Before making changes:

1. inspect the relevant existing implementation;
2. read the smallest relevant set of project documents;
3. apply the decision policy from `.agent/decision-policy.yaml`;
4. make the smallest coherent change;
5. verify the result;
6. update project documentation when the change affects documented behavior.

---

## 2. Project

Bee Search is an Android application for field observations of marked bees.

Current technical direction:

- Android;
- Kotlin;
- Jetpack Compose;
- Room / SQLite;
- DataStore;
- MapLibre Native for Android;
- Android location APIs;
- Android sensor APIs;
- Kotlin Coroutines / Flow.

The application is **offline-first**.

The current priority is a reliable and fast field workflow.

Do not implement server synchronization, desktop/web functionality,
automatic nest estimation, or other future functionality unless explicitly
required by the current task.

---

## 3. Project Sources of Truth

Detailed project knowledge lives in `docs/`.

```text
docs/
├── product-requirements.md
├── user-workflows.md
├── domain-model.md
├── data-model.md
├── architecture.md
├── decisions.md
└── glossary.md
```

Responsibilities:

- `product-requirements.md` — product requirements and MVP scope;
- `user-workflows.md` — user workflows and interaction sequences;
- `domain-model.md` — domain concepts, relationships, rules, and invariants;
- `data-model.md` — persistent entities, fields, constraints, and derived data;
- `architecture.md` — technical architecture and component boundaries;
- `decisions.md` — accepted, provisional, deferred, and superseded decisions;
- `glossary.md` — canonical project terminology.

Do not duplicate detailed project rules in `AGENTS.md`.

---

## 4. What to Read

Do not mechanically read every project document for every task.

Read the smallest set needed to perform the requested change safely.

### UI or workflow

Read:

```text
docs/product-requirements.md
docs/user-workflows.md
```

Add `docs/domain-model.md` when the UI action changes domain behavior.

### Domain behavior

Read:

```text
docs/domain-model.md
docs/data-model.md
docs/decisions.md
```

### Database or persistence

Read:

```text
docs/domain-model.md
docs/data-model.md
docs/architecture.md
docs/decisions.md
```

### Map, GPS, compass, or sensors

Read:

```text
docs/product-requirements.md
docs/user-workflows.md
docs/architecture.md
docs/decisions.md
```

### Architecture or major dependency

Read:

```text
docs/architecture.md
docs/decisions.md
```

and any affected product/domain documents.

### Terminology uncertainty

Read:

```text
docs/glossary.md
```

---

## Project handoff

For continuation of ongoing work, read:

```text
.agent/handoff.md
```

It contains only the current milestone, verification status, unresolved items,
and next task.

Do not treat it as project truth when it conflicts with `docs/` or accepted
decisions.

Update it at the end of a significant milestone or before ending a development
session.

## 5. Decision Precedence

Use the following precedence when sources conflict:

```text
explicit current user instruction
        ↓
ACCEPTED decisions
        ↓
product requirements and workflows
        ↓
domain model
        ↓
data model
        ↓
architecture
        ↓
agent preferences
        ↓
existing implementation
```

This hierarchy is not permission to silently create inconsistencies.

If the current request conflicts with an accepted decision or established
domain rule:

1. identify the conflict;
2. explain its practical consequence;
3. propose the smallest coherent resolution;
4. obtain approval when required by the decision policy;
5. update affected documentation together with the implementation.

Existing code is not automatically authoritative.

---

## 6. Decision Statuses

`docs/decisions.md` may contain:

- `ACCEPTED`
- `PROVISIONAL`
- `DEFERRED`
- `SUPERSEDED`

Interpret them as follows:

**ACCEPTED**  
Treat as a project constraint. Do not silently change it.

**PROVISIONAL**  
May be refined when new implementation, device, or field evidence appears.

**DEFERRED**  
Do not implement merely because it may be useful later.

**SUPERSEDED**  
Do not use as the basis for new work. Follow the replacing decision.

---

## 7. Agent Decision Policy

Agent-specific decision behavior is defined in:

```text
.agent/
├── decision-policy.yaml
├── preferences.yaml
└── preference-evidence.md
```

Use:

- `.agent/decision-policy.yaml` to determine whether to decide, report, propose,
  or ask before implementation;
- `.agent/preferences.yaml` as a tie-breaker between solutions already
  compatible with project truth;
- `.agent/preference-evidence.md` when a preference is ambiguous, conflicting,
  or needs revision.

Project truth in `docs/` always takes precedence over `.agent/`.

Before asking the user a technical question, first determine whether the
answer can be obtained by inspecting the repository and relevant docs.

Do not ask the user to choose between trivial technically equivalent options.

---

## 8. Domain Safety

Before changing domain behavior, consult the domain and data documentation.

Canonical domain hierarchy:

```text
Territory
    └── ObservationPoint
            └── Bee
                    └── FlightCycle
```

Do not introduce new domain entities merely to represent UI state,
implementation convenience, or speculative future needs.

Particularly important established rules include:

- coordinates belong to `ObservationPoint`;
- `Bee` and `FlightCycle` do not have independent coordinates;
- repeated observation at the same physical location may create a new
  `ObservationPoint`;
- there is no separate `ObservationSession` entity in the MVP;
- there is no separate `Observer` entity in the MVP;
- the first release is represented through the first `FlightCycle` records,
  not a separate `GroupRelease` entity;
- a Bee may have at most one open `FlightCycle`;
- flight duration is derived from timestamps;
- Bee state is derived rather than stored as duplicate status;
- azimuth is optional and may be removed when unreliable;
- `0°` is a valid azimuth.

These reminders do not replace `docs/domain-model.md`,
`docs/data-model.md`, or `docs/decisions.md`.

---

## 9. Offline-First and Data Safety

Field observation must work without network access.

Critical field actions follow this principle:

```text
user action
    ↓
local validation
    ↓
local persistence
    ↓
persisted state
    ↓
UI update
```

Do not require a server response to record a field event.

Use:

- Room / SQLite for research data;
- DataStore for small device-local settings.

Do not use UI or ViewModel state as the only copy of a field observation.

Do not report a critical field action as successfully completed before
persistence succeeds.

Use database transactions when partial completion could create invalid data.

---

## 10. Data Evolution

Persistent domain entities use locally generated stable identifiers as defined
in the data model.

Do not make identity depend on display codes, list positions, or future
server-generated IDs.

Before real field data exists, destructive schema changes may be acceptable
during prototyping.

Once real field observations exist:

- preserve existing data;
- use explicit Room migrations;
- test migrations;
- do not reset the database merely to simplify development.

Any potentially destructive operation requires explicit approval according to
`.agent/decision-policy.yaml`.

---

## 11. Time and Measurements

Persist actual event timestamps, not continuously updated timer values.

Derived values should remain derived unless the data model explicitly changes.

Use a consistent and testable time source where practical.

For sensor-derived measurements:

- preserve uncertainty;
- do not invent values when a reliable measurement is unavailable;
- do not confuse raw sensor readings with confirmed observations;
- allow documented manual correction or removal where required.

Real-device behavior matters for GPS and compass functionality.

---

## 12. Map and Sensor Boundaries

MapLibre is the selected map engine.

Do not confuse:

- map engine;
- map source;
- map style;
- offline map storage.

Do not commit the project to a specific offline-map format or provider while
that decision remains open.

Keep Android platform access behind appropriate boundaries such as:

```text
LocationProvider
HeadingProvider
```

when consistent with `docs/architecture.md`.

Do not assume emulator behavior proves real-device GPS or compass behavior.

---

## 13. UI Priorities

Bee Search is a field data collection tool.

Optimize frequent observation actions for:

- speed;
- low cognitive overhead;
- few interactions;
- clear current state;
- rapid switching between concurrently observed bees.

The workflow must support approximately ten concurrently tracked bees.

Do not design the primary observation workflow as a wizard that forces the
user to finish one Bee before interacting with another.

Field ergonomics take priority over visual or architectural sophistication.

---

## 14. Architecture Discipline

Prefer the simplest architecture that:

- satisfies documented requirements;
- preserves domain invariants;
- protects data;
- remains testable;
- does not block known future requirements.

Current general direction:

```text
Compose
    ↓
ViewModel
    ↓
Domain logic
    ↓
Repository
    ↓
Room / DataStore
```

Do not introduce abstractions merely because they are common in enterprise
Android projects.

Avoid unnecessary chains of DTOs, models, wrappers, services, or layers when
they do not solve a concrete problem.

---

## 15. Dependencies

Before adding a dependency:

1. verify that it solves a current requirement;
2. check whether Android or Kotlin already provides sufficient functionality;
3. prefer actively maintained and documented libraries;
4. consider Android and offline compatibility;
5. avoid large frameworks for small problems.

Do not add dependencies for speculative future functionality.

Major architectural dependencies should be handled according to
`.agent/decision-policy.yaml`.

---

## 16. Scope Control

Implement the smallest coherent change that satisfies the current task.

Do not expand scope merely because adjacent improvements are possible.

Avoid:

- speculative features;
- premature generalization;
- unrelated refactoring;
- unused abstractions;
- future server code;
- future synchronization code;
- premature advanced analytics.

Small supporting changes are appropriate when required for correctness,
testing, or consistency.

---

## 17. Testing and Verification

Verification effort should be proportional to risk.

Prioritize tests around behavior that could corrupt or misrepresent field
observations.

Important areas include:

- domain invariants;
- first group release;
- FlightCycle sequencing;
- prevention of multiple open cycles for one Bee;
- timestamp validity;
- nullable and removable azimuth;
- mark uniqueness;
- Room constraints;
- database migrations;
- recovery of persistent active work.

Use physical-device testing where relevant for:

- GPS;
- manual map correction;
- offline maps;
- heading/compass;
- sensor accuracy;
- field ergonomics.

Never claim a build, test, migration, or device behavior succeeded unless it
was actually verified.

If something could not be verified, state exactly what remains unverified.

---

## 18. Documentation Discipline

Documentation and implementation must remain aligned.

Update the relevant document when a change affects:

| Change | Document |
|---|---|
| Product behavior or MVP scope | `product-requirements.md` |
| User workflow | `user-workflows.md` |
| Domain rule or invariant | `domain-model.md` |
| Persistent data | `data-model.md` |
| Architecture | `architecture.md` |
| Durable project decision | `decisions.md` |
| Canonical terminology | `glossary.md` |

Do not update project documents for formatting-only changes or private
implementation details that do not affect documented behavior.

Durable project decisions belong in `docs/decisions.md`, not `.agent/`.

---

## 19. Naming

Use canonical terminology from:

```text
docs/glossary.md
```

Prefer established names such as:

```text
Territory
ObservationPoint
Bee
FlightCycle
observerCode
markColor
markPosition
sequenceNumber
departureTime
returnTime
azimuthDeg
```

Do not introduce alternative names for concepts that already have canonical
project terminology without a concrete reason.

---

## 20. Change Workflow

For non-trivial tasks:

1. inspect the relevant code;
2. identify the type of change;
3. read the relevant docs listed in this file;
4. check applicable decisions;
5. apply `.agent/decision-policy.yaml`;
6. use `.agent/preferences.yaml` only where genuine implementation freedom
   remains;
7. implement the smallest coherent solution;
8. run relevant verification;
9. update affected documentation;
10. report what changed and what remains unverified.

Do not perform unrelated cleanup during a focused task unless necessary for
correctness.

---

## 21. Completion Criteria

A task is complete when, as applicable:

- the requested behavior is implemented;
- code compiles;
- relevant tests pass;
- domain invariants remain valid;
- persistence remains correct;
- offline behavior is preserved;
- existing workflows are not accidentally broken;
- relevant documentation is updated;
- no unrelated changes were introduced.

If any criterion could not be checked, report that explicitly.

---

## 22. Development Direction

Proceed incrementally.

Current broad sequence:

```text
foundation
    ↓
local persistence
    ↓
territory management
    ↓
map + GPS
    ↓
ObservationPoint creation
    ↓
Bee preparation
    ↓
initial group release
    ↓
multi-Bee observation workflow
    ↓
repeated FlightCycles
    ↓
azimuth
    ↓
offline-map completion
    ↓
field testing
    ↓
iteration
```

This sequence is guidance, not a reason to implement future stages during an
unrelated task.

---

## 23. Primary Principle

**Bee Search is first and foremost a field data collection tool.**

When choosing between a more elaborate solution and a simpler one, prefer the
simpler solution when it:

- satisfies the documented requirements;
- accurately represents field observations;
- preserves data safety;
- remains understandable and testable;
- keeps reasonable future extension paths open.

Reliability, accurate representation of observations, and speed of field entry
take priority over architectural sophistication.
