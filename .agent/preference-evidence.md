# Bee Search — Preference Evidence

## 1. Purpose

This document records the evidence behind preferences stored in:

```text
.agent/preferences.yaml
```

Its purpose is to distinguish:

* stable development preferences;
* repeated user choices;
* project-specific constraints;
* temporary implementation decisions.

This file is not a source of product truth.

Product and architecture truth belongs in:

```text
docs/
```

This document exists only to explain why an agent believes a given development preference is likely to be stable.

---

## 2. Evidence rules

### 2.1. Strong evidence

Strong evidence includes:

* explicit statements of general preference;
* repeated choices across different decisions;
* rejection of alternatives for the same underlying reason;
* direct correction of agent behavior.

### 2.2. Medium evidence

Medium evidence includes:

* the same preference appearing in several related project choices;
* consistent acceptance of one class of solution over another;
* repeated preference inferred from project discussions.

### 2.3. Weak evidence

Weak evidence includes:

* a single situational choice;
* a temporary workaround;
* a decision forced by platform limitations;
* a choice that may apply only to one feature.

Weak evidence should not normally create a high-confidence preference.

### 2.4. Evidence metadata

Every preference record must include structured provenance metadata:

```text
Evidence type: explicit_statement | repeated_direct_choice | inferred_pattern | cross_context_inference | legacy_unverified
Confirmation: not_required | confirmed_by_user | not_confirmed | unknown_legacy
Scope: general | project_specific | task_specific
Optional: Rationale, Lifecycle state, Context notes, Confirmed, Last reviewed, Superseded, Related preferences
```

Interpretation:

* `explicit_statement` — the user directly stated a reusable preference;
* `repeated_direct_choice` — the user repeatedly chose or rejected a class of solutions, but did not state a reusable general rule;
* `inferred_pattern` — the preference is inferred from a broader pattern of work or accepted decisions;
* `cross_context_inference` — evidence comes partly or wholly from another project or software context;
* `legacy_unverified` — a legacy preference exists but no adequate evidence record was preserved.

`not_required` is valid for a reusable explicit statement. A non-explicit
inference may be `high` only after `confirmed_by_user`. `unknown_legacy` must
not be treated as confirmation.

`Scope` prevents a project-specific or task-specific choice from silently
becoming a general preference.

Optional metadata may be added when it improves future interpretation:

* `Rationale` — a user-provided reason that helps define why the preference matters;
* `Lifecycle state` — `active`, `needs_revalidation`, or `superseded`;
* `Context notes` — known conditions or exceptions that materially affect applicability;
* `Confirmed` — when the user explicitly confirmed the durable preference;
* `Last reviewed` — when its meaning or scope was materially revalidated;
* `Superseded` — when the user explicitly replaced or withdrew it;
* `Related preferences` — concrete relationships to other stored preferences when those relationships aid interpretation.

Do not invent a rationale or psychological explanation when the user did not provide one.
Optional metadata must not be backfilled mechanically. Add it only when supported
by actual evidence or a real lifecycle event.

### 2.5. Behavioral evidence is a hypothesis until confirmed

Repeated behavior may justify asking whether a preference exists, but it must
not be treated as proof of the user's underlying preference. A choice may be
shaped by time pressure, task constraints, attention, fatigue, mood, health, or
other circumstances that are not reliably observable to the agent.

For behaviorally inferred preferences:

1. accumulate enough evidence for the hypothesis to matter;
2. ask whether the observed pattern is correctly understood;
3. clarify whether it is general or context-dependent when relevant;
4. ask for the reason only when it helps define scope, exceptions, or trade-offs;
5. only then record it as a confirmed durable preference.

Do not infer a hidden psychological cause. Record only what the user confirms
or what is directly observable from the interaction.

If several non-urgent preference hypotheses accumulate, avoid turning them into
a rapid sequence of confirmation questions. Defer them to a natural pause and,
when practical, group closely related hypotheses into one concise discussion.

### 2.6. Situational choices and constraints

An explicit instruction such as "do this quickly" is not automatically an
explicit reusable preference. Distinguish between:

* a durable preference;
* a contextual preference;
* a temporary constraint;
* a one-off choice.

When the user explicitly distinguishes an ideal preference from a temporary
constraint, preserve both meanings. For example, "normally I want a detailed
analysis, but today I have no time" supports the durable preference for detail
and records the current speed requirement as situational rather than reversing
the durable preference.

### 2.7. Confirmed preferences remain revisable

A confirmed preference is not permanent truth. Continue to compare it with
later evidence. A single conflicting choice is normally an exception. If
behavior repeatedly and materially conflicts with a stated or confirmed
preference, record the conflict and ask the user to reconcile it.

The revalidation question should neutrally describe both sides, for example:

> You previously said you prefer X, but in several comparable recent cases you
> chose Y. Has your preference changed, does it depend on context, or were those
> choices situational?

Do not silently replace an explicit preference with inferred behavior. Possible
outcomes are: keep the preference, narrow its scope, revise it, supersede it, or
mark it as needing revalidation when the conflict remains unresolved.

### 2.8. Selective use and write-back

This file is a provenance log, not a document that should be loaded in full for
every task. When provenance is needed, locate the relevant record by preference
key and inspect only the applicable section unless a broader audit is explicitly
required.

Behaviorally inferred preferences require a confirmation loop before durable
write-back: formulate the interpretation and scope, present it to the user, and
write it to both `.agent/preferences.yaml` and this file only after explicit
confirmation. If the user directly states a reusable preference and explicitly
asks to record, remember, update, or remove it, that request itself is sufficient
confirmation and must not trigger a redundant approval question.

After every write-back, verify that both files agree on the preference key,
confidence, scope, confirmation status, and lifecycle meaning. Preserve previous
evidence and lifecycle history instead of silently rewriting it.

### 2.9. Lightweight temporal history

When useful, record simple lifecycle dates rather than treating a confirmed
preference as timeless. `Confirmed`, `Last reviewed`, and `Superseded` are
optional metadata. They help future agents distinguish a recently confirmed
preference from one that has not been revisited for a long time and preserve
the chronology of actual lifecycle events.

Do not invent historical dates and do not retrofit these fields across all
existing records merely for completeness. Add them only when the corresponding
event is known.

### 2.10. Optional relations between preferences

When one stored preference concretely changes the interpretation of another, an
optional `Related preferences` field may record one of these relations:

```text
supersedes: <preference-key>
narrows: <preference-key>
conflicts_with: <preference-key>
```

Use relations to preserve meaningful history, not to build a graph for its own
sake. Do not retrofit relations onto the existing preference set without
actual evidence for a specific relationship. `Lifecycle state` remains useful
and is not replaced by these relations.

---

# 3. Prefer simple solutions

**Preference key:**

```text
architecture.prefer_simple_solutions
```

**Confidence:** medium
**Evidence type:** repeated_direct_choice
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The user explicitly rejected unnecessary additional domain entities when discussing `ObservationSession`.

The reasoning was that an extra entity should not exist unless it represents something genuinely needed in the real workflow.

The same principle was applied when discussing:

* `GroupRelease`;
* `Observer`;
* repeated observation locations.

### Interpretation

Prefer the smallest model that accurately represents the real process.

Do not add layers, entities, or abstractions merely because they are common architectural patterns.

---

# 4. Avoid speculative abstractions

**Preference key:**

```text
architecture.avoid_speculative_abstractions
```

**Confidence:** medium
**Evidence type:** repeated_direct_choice
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The user repeatedly preferred postponing structures that may become useful later until a real need exists.

Examples include:

* no separate `ObservationSession`;
* no separate `GroupRelease`;
* no `PhysicalPlace` entity yet;
* no server implementation before the local workflow is proven;
* no premature sync fields.

### Interpretation

Future compatibility is useful, but future functionality should not be implemented without a current requirement.

---

# 5. Avoid unnecessary entities

**Preference key:**

```text
architecture.avoid_unnecessary_entities
```

**Confidence:** high
**Evidence type:** explicit_statement
**Confirmation:** not_required
**Scope:** general

### Evidence

The user explicitly stated:

> Не стоит плодить лишние сущности.

This was said when discussing whether `ObservationSession` should exist.

The same preference remained consistent throughout later data-model discussions.

### Interpretation

Before introducing a domain entity, ask whether it represents a distinct real-world concept with independent behavior or lifecycle.

If not, prefer a property, relationship, derived value, or existing entity.

---

# 6. Prefer explicit domain models

**Preference key:**

```text
architecture.prefer_explicit_domain_model
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The user chose to build project documentation in stages:

```text
product requirements
→ workflows
→ domain model
→ data model
→ architecture
```

before significant implementation work.

### Interpretation

Important domain concepts and rules should be explicitly documented before they become hidden implementation assumptions.

---

# 7. Prefer incremental architecture

**Preference key:**

```text
architecture.prefer_incremental_architecture
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The project has repeatedly been developed by resolving one layer of uncertainty at a time.

Examples:

* first validate Android development environment;
* then write requirements;
* then workflows;
* then domain model;
* then data model;
* then architecture;
* server postponed.

### Interpretation

Prefer architecture that can grow in controlled steps rather than committing early to the final imagined system.

---

# 8. Avoid enterprise complexity without need

**Preference key:**

```text
architecture.avoid_enterprise_complexity_without_need
```

**Confidence:** medium
**Evidence type:** repeated_direct_choice
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The user has consistently preferred direct practical structures and rejected unnecessary intermediate concepts.

The architecture intentionally avoids introducing:

* mandatory Hilt;
* complex Clean Architecture layers;
* speculative DTO chains;
* server infrastructure before it is needed.

### Interpretation

Use enterprise patterns only where they solve an actual current problem.

---

# 9. Prefer incremental changes

**Preference key:**

```text
development.prefer_incremental_changes
```

**Confidence:** high
**Evidence type:** explicit_statement
**Confirmation:** not_required
**Scope:** general

### Evidence

The user explicitly described the project as something that should be assembled gradually:

> проект надо понемногу собирать

The entire setup has proceeded step by step rather than through a large one-shot implementation.

### Interpretation

Break work into small coherent milestones that can be reviewed and tested independently.

---

# 10. Prefer small coherent steps

**Preference key:**

```text
development.prefer_small_coherent_steps
```

**Confidence:** medium
**Evidence type:** repeated_direct_choice
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The user repeatedly requested sequential progression:

* Android Studio setup step by step;
* document creation one file at a time;
* review before moving to the next model layer.

### Interpretation

Prefer one well-defined change at a time over broad mixed tasks.

---

# 11. Prefer working prototype before advanced features

**Preference key:**

```text
development.prefer_working_prototype_before_advanced_features
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The project intentionally delays:

* server synchronization;
* web interface;
* advanced GIS;
* automatic nest estimation.

The field workflow is expected to be validated first.

### Interpretation

Prioritize the smallest real field-useful version before advanced analysis or infrastructure.

---

# 12. Prefer real-world validation

**Preference key:**

```text
development.prefer_real_world_validation
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

Several requirements were derived directly from practical observation:

* GPS can be wrong and must be manually corrected;
* azimuth may be unreliable;
* bees may follow terrain instead of flying directly;
* first release happens almost simultaneously;
* several bees must be tracked at once.

### Interpretation

When theoretical design conflicts with observed field behavior, field behavior should drive the product.

---

# 13. Prefer physical-device testing when relevant

**Preference key:**

```text
development.prefer_physical_device_testing_when_relevant
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The project setup explicitly included testing on a real Android phone.

The application depends on:

* GPS;
* compass;
* sensor quality;
* actual field ergonomics.

### Interpretation

Do not treat emulator results as sufficient for sensor-dependent functionality.

---

# 14. Avoid large unrelated refactors

**Preference key:**

```text
development.avoid_large_unrelated_refactors
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The development process is intentionally staged and focused.

Large unrelated restructuring would make review and understanding harder.

### Interpretation

Keep changes tightly scoped to the current task unless a supporting refactor is necessary for correctness.

---

# 15. Prefer agent autonomy for trivial choices

**Preference key:**

```text
decision_making.prefer_agent_autonomy_for_trivial_choices
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The decision-policy design explicitly distinguishes routine choices from project-level decisions.

The goal is to avoid forcing the user to answer unnecessary technical questions.

### Interpretation

Agents should decide routine reversible implementation details independently.

---

# 16. Prefer discussion for important project decisions

**Preference key:**

```text
decision_making.prefer_discussion_for_important_project_decisions
```

**Confidence:** medium
**Evidence type:** repeated_direct_choice
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The user repeatedly reviewed and corrected significant design choices before accepting them.

Examples:

* how Territory behaves;
* whether Observer is a separate entity;
* repeated ObservationPoint semantics;
* first group release representation.

### Interpretation

Product, domain, architecture, and persistent-data semantics should be discussed before being changed.

---

# 17. Prefer recommendation over neutral option dump

**Preference key:**

```text
decision_making.prefer_recommendation_over_neutral_option_dump
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

Throughout the project discussion, progress has been made by selecting a preferred path after comparing alternatives rather than leaving all options unresolved.

### Interpretation

When several valid choices exist, provide a preferred option and explain the important trade-off.

Do not merely enumerate alternatives without guidance.

---

# 18. Prefer reversible choices under uncertainty

**Preference key:**

```text
decision_making.prefer_reversible_choices_when_uncertain
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

Several decisions were intentionally deferred when they were not yet required:

* offline-map format;
* map source;
* sync architecture;
* PC interface;
* physical-place modeling.

### Interpretation

When evidence is insufficient, prefer solutions that preserve future options.

---

# 19. Avoid questions resolvable from repository

**Preference key:**

```text
decision_making.avoid_questions_resolvable_from_repository
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The agent policy was explicitly designed so the agent should inspect files and project documentation before asking questions.

### Interpretation

Ask the user only when repository inspection cannot resolve the uncertainty or when a real project decision is required.

---

# 20. Avoid premature commitment on open questions

**Preference key:**

```text
decision_making.avoid_premature_commitment_on_open_questions
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The project deliberately leaves unresolved decisions marked as open or deferred rather than choosing arbitrarily.

Examples:

* map format;
* map source;
* point display code;
* synchronization conflict model.

### Interpretation

Open questions remain open until implementation genuinely requires resolution.

---

# 21. Prefer minimal domain entities

**Preference key:**

```text
domain_modeling.prefer_minimal_domain_entities
```

**Confidence:** medium
**Evidence type:** repeated_direct_choice
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

Strong repeated evidence:

* no ObservationSession;
* no GroupRelease entity;
* no Observer entity for MVP;
* no PhysicalPlace entity yet.

### Interpretation

Model only concepts that have independent meaning in the current domain.

---

# 22. Prefer derived values over duplicate state

**Preference key:**

```text
domain_modeling.prefer_derived_values_over_duplicate_state
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The project deliberately derives:

* Bee state from FlightCycle;
* duration from timestamps;
* initial group release from `sequence_number = 1`.

### Interpretation

Avoid storing redundant state that can drift out of sync with source data.

---

# 23. Prefer observed data over inferred data

**Preference key:**

```text
domain_modeling.prefer_real_observed_data_over_inferred_data
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The user emphasized that an incorrect azimuth may add error and should be removable.

The application should record what was actually observed rather than fabricate certainty.

### Interpretation

Store observations directly and separate them from later analysis or inference.

---

# 24. Avoid false precision

**Preference key:**

```text
domain_modeling.avoid_false_precision
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

Examples include:

* first release is treated as one group time rather than pretending each bee has a precisely distinct start;
* unreliable azimuth should be absent;
* GPS may be corrected manually.

### Interpretation

Do not create precise-looking data when the real observation does not justify that precision.

---

# 25. Prefer nullable value over fake placeholder

**Preference key:**

```text
domain_modeling.prefer_nullable_value_over_fake_placeholder
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The strongest example is azimuth:

```text
null
```

means unavailable.

`0°` must remain a real north direction.

Similarly, no fake return time is created for a bee that did not return.

### Interpretation

Represent missing observations explicitly instead of inventing sentinel values that overlap with valid data.

---

# 26. Prefer local-first data capture

**Preference key:**

```text
persistence.prefer_local_first_data_capture
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

Field work may occur without network access.

The project explicitly adopted offline-first architecture.

### Interpretation

Local persistence must complete independently of server availability.

---

# 27. Prefer immediate persistence of events

**Preference key:**

```text
persistence.prefer_immediate_persistence_of_events
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The observation workflow requires events such as `Вернулась` to be saved immediately.

A final general Save button is intentionally avoided.

### Interpretation

Persist critical field events at the moment they occur.

---

# 28. Prefer data safety over implementation convenience

**Preference key:**

```text
persistence.prefer_data_safety_over_implementation_convenience
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The architecture requires:

* transactions;
* restoration after process death;
* migration protection once real field data exists;
* future backup planning.

### Interpretation

Convenient development shortcuts must not endanger real observations.

---

# 29. Avoid destructive migrations after real data exists

**Preference key:**

```text
persistence.avoid_destructive_migrations_after_real_data_exists
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

This rule was explicitly adopted in `architecture.md` and `AGENTS.md`.

### Interpretation

Once real observations exist, schema evolution must preserve them.

---

# 30. Prefer stable IDs for future synchronization

**Preference key:**

```text
persistence.prefer_stable_ids_for_future_sync
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

UUIDs are deliberately created locally even before server synchronization exists.

### Interpretation

Prepare the data model for future merging without implementing synchronization prematurely.

---

# 31. Prefer fast field interaction

**Preference key:**

```text
user_interface.prefer_fast_field_interaction
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The core problem is handling multiple bees that may return in unpredictable order and close succession.

### Interpretation

Field actions should be optimized around speed of event capture.

---

# 32. Prefer fewer taps for frequent actions

**Preference key:**

```text
user_interface.prefer_fewer_taps_for_frequent_actions
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The user described situations where one bee may return while another is still being tracked.

Deep forms or repeated navigation would interfere with observation.

### Interpretation

Common actions such as return and departure should be directly accessible.

---

# 33. Prefer one working screen for parallel activity

**Preference key:**

```text
user_interface.prefer_single_working_screen_for_parallel_activity
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

Up to approximately ten bees may be active at the same time.

The user needs to jump between them in arbitrary order.

### Interpretation

The observation workflow should remain centered on a shared active-bee screen.

---

# 34. Prefer visible current state

**Preference key:**

```text
user_interface.prefer_visible_current_state
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The user needs to know immediately which bee:

* is flying;
* has returned;
* is ready for another release.

### Interpretation

Important states should be visible without opening detail screens.

---

# 35. Avoid deep navigation for frequent actions

**Preference key:**

```text
user_interface.avoid_deep_navigation_for_frequent_actions
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The multi-bee workflow makes deep per-bee navigation impractical.

### Interpretation

Details may exist separately, but common field actions belong on the main observation screen.

---

# 36. Prefer manual correction when sensor data may be wrong

**Preference key:**

```text
user_interface.prefer_manual_correction_when_sensor_data_can_be_wrong
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The user specifically required manual correction of GPS position because field GPS can be inaccurate.

The same philosophy applies to removable azimuth.

### Interpretation

Sensor automation should assist the observer, not override observed reality.

---

# 37. Prefer maintained dependencies

**Preference key:**

```text
dependencies.prefer_active_maintained_dependencies
```

**Confidence:** medium
**Evidence type:** cross_context_inference
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

In previous software choices, lack of maintenance was explicitly treated as a concern.

For Bee Search, dependencies are expected to have active support where reasonable.

### Interpretation

Maintenance status is an important factor when selecting external libraries.

---

# 38. Avoid abandoned dependencies when alternatives exist

**Preference key:**

```text
dependencies.avoid_abandoned_dependencies_when_good_alternatives_exist
```

**Confidence:** medium
**Evidence type:** cross_context_inference
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The user has previously rejected or questioned tools that had gone years without updates.

### Interpretation

Do not choose an abandoned dependency solely because it once solved the problem if a maintained alternative exists.

---

# 39. Prefer project knowledge in repository

**Preference key:**

```text
documentation.prefer_project_knowledge_in_repository
```

**Confidence:** high
**Evidence type:** explicit_statement
**Confirmation:** not_required
**Scope:** project_specific

### Evidence

The user explicitly wanted the discussed requirements moved out of chat into initial documents that models can reference.

This led to the current documentation set.

### Interpretation

Durable project knowledge should live in version-controlled files.

---

# 40. Prefer docs over chat memory for project rules

**Preference key:**

```text
documentation.prefer_docs_over_chat_memory_for_project_rules
```

**Confidence:** high
**Evidence type:** explicit_statement
**Confirmation:** not_required
**Scope:** project_specific

### Evidence

The user explicitly requested project documents so future models could read and respect them rather than relying on chat history.

### Interpretation

Agents should consult repository documentation as the durable source of project context.

---

# 41. Keep documentation and code consistent

**Preference key:**

```text
documentation.keep_documentation_and_code_consistent
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The documentation structure and AGENTS rules were deliberately designed to keep product behavior, domain model, data model, and architecture aligned with implementation.

### Interpretation

A behavior-changing implementation should update the relevant project document.

---

# 42. Separate project truth from agent preferences

**Preference key:**

```text
documentation.prefer_separation_of_project_truth_and_agent_preferences
```

**Confidence:** high
**Evidence type:** explicit_statement
**Confirmation:** not_required
**Scope:** project_specific

### Evidence

The user questioned whether `decisions.md` should live in `.agent`.

The final distinction was:

```text
docs/ = project truth
.agent/ = agent behavior and preferences
```

### Interpretation

Do not store Bee Search domain facts in preference files.

---

# 43. Prefer decisions with rationale

**Preference key:**

```text
documentation.prefer_decisions_with_rationale
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

`decisions.md` was intentionally created as a journal of accepted decisions including reasons rather than a simple technology list.

### Interpretation

Important choices should record not only what was chosen, but why.

---

# 44. Prefer inspect before edit

**Preference key:**

```text
agent_behavior.prefer_inspect_before_edit
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The decision policy explicitly requires repository and documentation inspection before asking or modifying.

### Interpretation

Agents should understand the current state before making changes.

---

# 45. Prefer relevant docs rather than all docs

**Preference key:**

```text
agent_behavior.prefer_read_relevant_docs_not_all_docs
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The AGENTS design intentionally avoids requiring all seven documents to be reread for every trivial task.

### Interpretation

Read enough context to work safely without wasting context on unrelated documents.

---

# 46. Prefer conflict explanation before overriding rules

**Preference key:**

```text
agent_behavior.prefer_explain_conflicts_before_overriding_rules
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

Accepted project decisions are intended to be changed deliberately, not silently.

### Interpretation

When a requested change conflicts with accepted project truth, surface the conflict first.

---

# 47. Prefer reporting unverified parts

**Preference key:**

```text
agent_behavior.prefer_report_unverified_parts
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

Physical sensor behavior and other real-device concerns cannot always be verified automatically.

### Interpretation

State clearly what was tested and what still requires verification.

---

# 48. Avoid claiming success without verification

**Preference key:**

```text
agent_behavior.avoid_claiming_success_without_verification
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The AGENTS and decision-policy files explicitly require honest reporting of tests and build checks.

### Interpretation

Never report a passing build, test, sensor behavior, or migration without actually verifying it.

---

# 49. Avoid inventing requirements

**Preference key:**

```text
agent_behavior.avoid_inventing_requirements
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The project repeatedly separates accepted rules from open questions.

### Interpretation

A plausible idea is not automatically a requirement.

---

# 50. Avoid silent architectural changes

**Preference key:**

```text
agent_behavior.avoid_silent_architectural_changes
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

Architecture decisions are explicitly documented and assigned statuses.

### Interpretation

Significant architecture changes must be surfaced and documented.

---

# 51. Avoid overengineering

**Preference key:**

```text
agent_behavior.avoid_overengineering
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

This is supported by repeated preference for minimal entities, incremental development, and simple architecture.

### Interpretation

Complexity requires a concrete benefit.

---

# 52. Prefer readable code over clever code

**Preference key:**

```text
code_style.prefer_readable_code_over_clever_code
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** general

### Evidence

The project emphasizes maintainability and future work with coding agents.

Highly clever compact implementation would make both human and agent review harder.

### Interpretation

Prefer explicit readable Kotlin over unnecessarily sophisticated constructs.

---

# 53. Prefer clear names

**Preference key:**

```text
code_style.prefer_clear_names
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

A dedicated `glossary.md` exists specifically to maintain canonical naming.

### Interpretation

Names should match project terminology and reveal domain meaning.

---

# 54. Prefer comments for non-obvious domain rules

**Preference key:**

```text
code_style.prefer_comments_for_non_obvious_domain_rules
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

AGENTS explicitly allows references to decisions for non-obvious rules such as the first group release.

### Interpretation

Comments should preserve reasoning where the code alone cannot explain why a constraint exists.

---

# 55. Prefer future compatibility without premature implementation

**Preference key:**

```text
future_design.prefer_future_compatibility_without_premature_implementation
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The project prepares:

* UUIDs for sync;
* clear repository boundaries;
* map abstraction;

while deliberately not implementing:

* SyncEngine;
* server;
* advanced analysis.

### Interpretation

Leave clean extension points where justified, but do not build unused systems.

---

# 56. Prefer sync-ready IDs without building sync early

**Preference key:**

```text
future_design.prefer_sync_ready_ids_without_building_sync_early
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

UUIDs are accepted now, while server-specific fields are deferred.

### Interpretation

Prepare the data identity model without prematurely designing the synchronization protocol.

---

# 57. Prefer modular boundaries around replaceable infrastructure

**Preference key:**

```text
future_design.prefer_modular_boundaries_around_replaceable_infrastructure
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

The architecture proposes boundaries such as:

* `LocationProvider`;
* `HeadingProvider`;
* `OfflineMapManager`.

This preserves separation from Android and map-engine details.

### Interpretation

Use small boundaries around external infrastructure where replacement or testing is realistically valuable.

Do not generalize this into unnecessary abstraction everywhere.

---

# 58. Avoid server dependency in field workflow

**Preference key:**

```text
future_design.avoid_server_dependency_in_field_workflow
```

**Confidence:** medium
**Evidence type:** inferred_pattern
**Confirmation:** not_confirmed
**Scope:** project_specific

### Evidence

Offline-first is one of the strongest accepted principles of the project.

### Interpretation

Future server features must not make basic field event capture dependent on connectivity.

---


# 59. Prefer existing project structure when reasonable

**Preference key:**

```text
development.prefer_existing_project_structure_when_reasonable
```

**Confidence:** medium
**Evidence type:** legacy_unverified
**Confirmation:** unknown_legacy
**Scope:** general

### Evidence

No adequate evidence record was preserved for this legacy preference. The
preference remains plausible, but this migration does not invent retrospective
evidence for it.

### Interpretation

Treat the existing project structure as a useful default when it remains
reasonable, but do not let this preference preserve a structure that conflicts
with project truth or clearly impedes the current task.

---

# 60. Prefer standard platform capabilities first

**Preference key:**

```text
dependencies.prefer_standard_platform_capabilities_first
```

**Confidence:** medium
**Evidence type:** legacy_unverified
**Confirmation:** unknown_legacy
**Scope:** general

### Evidence

No adequate evidence record was preserved for this legacy preference. Existing
project guidance is compatible with it, but project guidance is not treated as
retrospective proof of a general user preference.

### Interpretation

Consider Android, Kotlin, or other standard platform capabilities before adding
an external dependency when they solve the current problem adequately.

---

# 61. Avoid a large dependency for a small problem

**Preference key:**

```text
dependencies.avoid_large_dependency_for_small_problem
```

**Confidence:** medium
**Evidence type:** legacy_unverified
**Confirmation:** unknown_legacy
**Scope:** general

### Evidence

No adequate evidence record was preserved for the previous high-confidence
value. The confidence is therefore reduced during migration rather than
creating an explanation after the fact.

### Interpretation

Treat dependency size and architectural cost as a factor when the problem is
small, while allowing a larger dependency when it provides a concrete current
benefit that justifies the commitment.

---

# 62. Prefer small functions when natural

**Preference key:**

```text
code_style.prefer_small_functions_when_natural
```

**Confidence:** medium
**Evidence type:** legacy_unverified
**Confirmation:** unknown_legacy
**Scope:** general

### Evidence

No adequate evidence record was preserved for this legacy preference.

### Interpretation

Small functions are a useful readability tool when they follow natural
boundaries, but function extraction should not be performed mechanically.

---

# 63. Avoid commenting obvious code

**Preference key:**

```text
code_style.avoid_commenting_obvious_code
```

**Confidence:** low
**Evidence type:** legacy_unverified
**Confirmation:** unknown_legacy
**Scope:** general

### Evidence

No user-specific evidence record was preserved. This resembles a common coding
style guideline more than an established personal preference, so migration
reduces it to low confidence.

### Interpretation

Avoid redundant comments only as a weak style hint. Preserve comments that add
reasoning, domain context, constraints, or information not obvious from code.

---

# 64. Evidence update policy

When new preference evidence appears:

1. identify the relevant preference key;
2. determine whether the observation is evidence of a durable preference, a contextual preference, or only a situational choice/constraint;
3. record the new evidence;
4. record `Evidence type`, `Confirmation`, and `Scope`;
5. for behaviorally inferred preferences, obtain user confirmation before treating the hypothesis as a confirmed durable preference;
6. record user-provided rationale when it materially clarifies scope, exceptions, or trade-offs;
7. distinguish project-specific constraints from general preferences;
8. adjust confidence only when justified by the current promotion policy;
9. continue monitoring confirmed preferences for sustained contradictory evidence;
10. batch non-urgent preference-confirmation questions when several accumulate;
11. add temporal metadata or preference relations only when a real event or concrete evidence supports them;
12. never mechanically backfill optional provenance fields merely for completeness.

Do not rewrite old evidence merely because a new preference appears. Preserve
exceptions, conflicts, confirmations, and revisions as history.

If later behavior repeatedly conflicts with a stated or confirmed preference,
do not silently overwrite either side. Ask the user whether the preference has
changed, is context-dependent, or the conflicting choices were situational.

---

# 65. Do not duplicate project truth here

The following kinds of statements do **not** belong in this file as preferences:

```text
azimuth is nullable
coordinates belong to ObservationPoint
MapLibre is currently selected
sequence_number = 1 represents first release
```

These are project decisions and belong in `docs/`.

This file may reference those decisions only as evidence for a broader development preference.

---

# 66. Current confidence summary

After the legacy migration, only preferences supported by a reusable explicit
statement or confirmed promotion remain `high`. In particular, the strongest
currently supported high-confidence preferences are:

* avoid unnecessary entities;
* develop the project incrementally;
* keep durable project knowledge in repository documentation;
* prefer repository documentation over chat memory for project rules;
* keep project truth separate from agent preferences.

Other retained preferences remain useful as `medium` or `low` evidence-backed
tie-breakers, but they must not silently acquire `high` confidence merely
because they existed before the current promotion policy.

These preferences should guide choices only when they do not conflict with explicit requirements or accepted project decisions.
