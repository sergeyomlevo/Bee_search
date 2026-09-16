# Personal Core v0.1 audit-only telemetry pilot

This is a bounded, non-authoritative observation pilot for Bee Search. It records
what agents observably did with tools, and separately reports how much of that
activity the existing Personal Core v0.1 comparator was able to evaluate. It
observes and reports; it never decides, blocks, allows, or approves anything.

Two dimensions are deliberately kept apart:

- **activity observation** — what tool events were seen, on which resources, with
  which technical outcome and observable effect;
- **governance evaluation** — whether the comparator could evaluate that specific
  event, and what it concluded.

A single event can be fully observed and still unevaluated. That is a coverage
statement, not a failure of either dimension.

## 1. What is observed

Both hooks are configured in [`.codex/hooks.json`](../../.codex/hooks.json) with
the broad matcher `.*`, `async: true`, and a 15 second timeout, so every tool call
is observed rather than only `apply_patch`. Each event is classified into one of
four categories:

| Category | Meaning | Examples of verified tool names |
| --- | --- | --- |
| `READ` | reads or searches state | `read`, `read_image`, `glob`, `grep`, `web_fetch`, `web_search`, `job_output`, `get_goal` |
| `MUTATION` | may change repository files | `apply_patch`, `write`, `edit`, `str_replace_editor` |
| `COMMAND` | runs a command or process | `pwsh`, `bash` |
| `OTHER` | everything else, including unknown tools | unrecognized names |

Classification uses an explicit table of tool names verified in this repository
and its harness packages; unknown names fall back to a conservative name
heuristic and then to `OTHER`, and the record states which path was taken
(`tool_name_table`, `name_heuristic`, `unknown_tool`). No tool is ever silently
dropped for being unrecognized.

Resources are extracted only for file-oriented tools, are stored as
repository-relative POSIX paths, and preserve case. For `apply_patch`, every
`*** Add|Update|Delete File:` header in one payload is captured, so multi-file
patches are visible. Command text is never parsed for file names, so a
`COMMAND` event reports no resources rather than guessed ones.

## 2. Lifecycle and pairing

`PreToolUse` and `PostToolUse` records are paired by `tool_use_id` into:

| State | Meaning |
| --- | --- |
| `PRE_OBSERVED` / `COMPLETED` | a Pre was seen and the observed tool lifecycle ended |
| `FAILED` | the tool reported an error (probed `is_error`, `error`, `success: false`, non-zero exit, or an error status) |
| Pre without Post | the observed attempt has no observed completion |
| Post without Pre | a completion was seen with no observed attempt |
| `UNKNOWN` | the event could not be classified, e.g. an unsupported hook event name |

A differing Pre and Post count is **not** by itself an anomaly: interrupted,
cancelled, or still-running calls legitimately leave Pre records unpaired, and
the report says so rather than flagging it.

`COMPLETED` means only that the observed lifecycle ended. It is not evidence that
the change was correct, complete, wanted, or permitted.

## 3. Technical outcome versus observable effect

These are recorded separately because they answer different questions:

- `technical_outcome` — did the tool call itself succeed or fail
  (`technical_outcome_source: field_probe`; the probe field mapping is a
  documented hypothesis about the hook payload, not a verified schema);
- `observable_effect` — did repository state observably change:
  `OBSERVED_CHANGE`, `NO_OBSERVED_CHANGE`, `NOT_APPLICABLE` (not a mutation),
  or `UNKNOWN`.

Effect is derived from a per-resource fingerprint taken at `PreToolUse`
(existence, size, mtime), stored under `.audit/.pending/` keyed by a hash of
`tool_use_id` and deleted when the Post arrives. Consequences:

- a `PostToolUse` event alone never proves a change;
- a file that was already dirty before the call is not counted as a new change;
- a missing fingerprint or missing resource yields `UNKNOWN`, never a guess.

## 4. Governance evaluation (separate from observation)

The comparator is the existing `personal-core.js` / `pilot.js` pair, reading
`.agent/decision-policy.yaml` as the only policy source. Per event it reports:

| Status | Meaning |
| --- | --- |
| `MATCH` | the comparator evaluated this event and agreed with the authoritative action |
| `MISMATCH` | the comparator evaluated this event and disagreed |
| `NOT_EVALUATED` | the comparator cannot represent this event; `reason_code` says why |
| `ERROR` | comparator load/normalize failure on an otherwise eligible event |

Current eligibility is narrow, and the reason is always recorded:
`comparator_supports_apply_patch_only` for any non-`apply_patch` tool,
`decision_class_not_routine` when the payload carries no explicit routine
decision class, and the `pilot.js` out-of-scope reasons for multi-resource,
`Delete`, malformed, or correlation-incomplete payloads. `ALLOW`, `ASK`, and
`DENY` inside `personal-core.js` remain comparator-internal dispositions and are
never emitted as decisions by this pilot.

Two honest limits of the current comparator wiring:

1. `MATCH` requires an explicit `decision_class: "routine"` field in the hook
   payload. Whether real hook payloads carry that field is unverified, so
   live-style payloads may all remain `NOT_EVALUATED`. The fixtures assert this
   explicitly.
2. The comparator has no path restriction of its own; it evaluates any single
   `Add`/`Update File` routine patch, not only the smoke prefix. The pilot builds
   the comparator snapshot from the authoritative `routine` block, so a `MATCH`
   is a consistency check between the authoritative block and the comparator's
   reading of it — it is not independent proof that the work was correct.

## 5. Privacy minimization

Records contain correlation ids (`session_id`, `turn_id`, `tool_use_id`),
`observed_at`, hook event name, tool name, category, relative resource paths,
the fingerprint numbers, the outcome/effect fields, and the governance verdict.
They never contain prompts, model responses, command text, patch bodies, file
contents, `tool_response`, or `transcript_path`. The fixture suite fails if a
sentinel secret appears anywhere in a stored record. Async hooks may be observed
after the tool completes and records can arrive out of order; use the correlation
fields to associate them.

## 6. Storage, schema, and what this does not claim

- Records are written as schema `bee-search.personal-core-audit.v2`
  (`schema_version: 2`) into `.audit/`, one JSON file per event. The directory is
  Git-ignored.
- Historical v1 records stay untouched and are never rewritten; the report
  prints them in a separate legacy section with the mapping
  `MATCH`/`MISMATCH` = comparator verdict, `OUT_OF_SCOPE` = `NOT_EVALUATED`,
  `ERROR` = audit or comparator error.
- The handler is fail-open and observational: it exits 0, writes no stdout, and
  returns no hook control fields, so it cannot block, alter, or approve a call.
  Storage failures are reported on stderr only.
- Audit is not enforcement; `MATCH` is not permission, and `MISMATCH` blocks
  nothing.
- Absence of recorded events is not evidence that no work happened. If no
  turn-boundary events are recorded, the report prints
  `NOT OBSERVABLE (no turn-boundary events recorded)` instead of a zero, and it
  never claims that an agent "did nothing".
- Turn boundaries (`UserPromptSubmit`, `Stop`, `Interrupt`) are supported by the
  handler and stored as boundary records, but they are **not registered** in
  `hooks.json`: support for those events is unverified in the agent harnesses
  used here, and this pilot does not register hooks it cannot verify. Until they
  are delivered, per-turn activity/quiet inference is unavailable.
- Findings come from a single project and a bounded event sample. They do not
  transfer to other projects or to "the agent in general".

## 7. Running and verifying

From the repository root:

```powershell
node tools/personal-core-audit-pilot/run-fixtures.js        # comparator matrix
node tools/personal-core-audit-pilot/run-hook-fixtures.js   # 17 telemetry fixtures
node tools/personal-core-audit-pilot/report-audit.js        # default .audit store
node tools/personal-core-audit-pilot/report-audit.js path\to\another\.audit
```

`run-hook-fixtures.js` covers, in order: normal Pre+Post completion; technical
tool failure; Pre without Post; Post without Pre; read; mutation; multi-resource
mutation; command; other/unknown tool; governance `MATCH`; `MISMATCH` (mapping
test — the current matcher cannot produce a disagreement, so the mapping is
exercised at module level); `NOT_EVALUATED`; malformed input; fail-open storage
failure; a pre-existing dirty resource; an interrupted turn; and a turn with no
observed tool activity. It also sweeps every stored record for forbidden fields
and sentinel secrets, checks legacy v1 readability, cross-checks the report
aggregates against the records, and asserts the hook configuration (broad
matcher, no boundary hooks registered, subagent hooks still owned by
`tools/luna-delegation-audit`). Every case writes into a fresh OS temp directory
and uses a temporary observation root, so it never writes into the repository or
into the historical `.audit/` store. Under a sandbox that forbids piped child
stdio, the child-process checks degrade to in-process equivalents and the run
says so.

The report prints records, sessions and turns, tool calls and pairing, activity
per category, effects, governance status plus explicit comparator coverage
(`evaluated / observed tool events`), and lifecycle anomalies with the note that
unequal Pre/Post counts are not themselves anomalies.

The comparator fixture matrix covers slash, Windows backslash, absolute, and
dot-relative paths. It also checks outside-root, delete, multi-resource,
unsupported class, null/empty policy, and cross-application evidence correlation
failures.

| Case | Bee Search policy | Personal Core audit | Match |
| --- | --- | --- | --- |
| slash | routine → decide_and_continue | ALLOW | MATCH |
| backslash | routine → decide_and_continue | ALLOW | MATCH |
| absolute | routine → decide_and_continue | ALLOW | MATCH |
| dot-relative | routine → decide_and_continue | ALLOW | MATCH |

### Codex and DeepSeek Harness

Codex reaches this observer through `.codex/hooks.json`; the DeepSeek Harness
reaches it through a registered Cordis plugin row, not through that file. The
harness transport, the field mapping with its confidence labels, the live
evidence, and the limitations are documented in
[`DSH-INTEGRATION.md`](DSH-INTEGRATION.md).

```powershell
node tools/personal-core-audit-pilot/run-dsh-adapter-fixtures.js   # harness adapter mapping
```

### Controlled live smoke

Restart Codex after changing `hooks.json`, then use `/hooks` to review and trust
the exact project-local hook definition; until that review, Codex skips the
non-managed hook. For a controlled check, apply a harmless patch to a file under
`tools/personal-core-audit-pilot/.audit-smoke/` and inspect the result with
`report-audit.js`. Files in that directory are Git-ignored; do not use it for
application or policy files. If the handler cannot write (sandbox or permissions),
the effect fields stay `UNKNOWN`; the observer never fabricates a change.

Deterministic replay of the exact configured command is not the same as observing
live hook delivery: a replayed event proves what the handler records, not that a
harness delivered the event. Report the two separately.

## 8. Provenance and non-goals

The minimal `personal-core.js` loader/evaluator/evidence correlation pieces are
adapted from O_wiki Personal Core v0.1, tag `personal-core-v0.1`, commit
`a84eabd7ddd45d3cb3e6fc36523e81f99339ca1b`. O_wiki paths, ownership zones,
`wiki_structure`, context mapping, DSH adapter, and stored policy rules are not
copied. A fresh PC snapshot is generated per event from the authoritative
`routine` block and the exact normalized action/resource, and the audit API binds
that source to this repository's own `.agent/decision-policy.yaml`; callers cannot
substitute another policy file. The adapter does not use O_wiki's DSH
`ask_user_question`, `tools/pre-execute`, or `tools/result` events.

Bee Search remains authoritative. The pilot does not implement enforcement,
policy migration, preference generalization, persistence, Android integration, or
a new runtime adapter framework. Routine fixture success is not written to
`.agent/evaluation-log.md`, in accordance with `.agent/evaluation-policy.yaml`.
The comparator matrix validates the narrow semantic translation
`decide_and_continue → ALLOW`; it deliberately does not implement an independent
Bee Search classifier, because that would create the second policy source this
pilot is designed to avoid.
