# Personal Core v0.1 audit-only pilot

This is a bounded, non-authoritative comparison pilot for Bee Search. It uses
the existing `.agent/decision-policy.yaml` as the only policy source and tests
one explicit decision class: `routine`. The class is suitable because the
authoritative rule says `action: decide_and_continue` and the pilot can observe
safe, reversible `apply_patch` events. `significant_reversible` and approval or
destructive classes are outside this pilot: Personal Core v0.1 cannot represent
the former's preferred-option/reporting semantics without expanding scope, and
the latter classes require user-control semantics excluded from this pilot.
Classification as `routine` is an explicit authoritative input; this pilot does
not infer or broaden that classification.

The runner accepts replayed Codex `PreToolUse`/`PostToolUse` events with
`session_id`, `turn_id`, `tool_use_id`, `tool_name`, `tool_input`, and explicit
`decision_class`. Only a single `Add File` or `Update File` resource in an
`apply_patch` `tool_input.command` payload is supported. The path is normalized to a lowercase,
forward-slash, repository-relative identity before comparison.

The repository-local [`.codex/hooks.json`](../../.codex/hooks.json) connects
both official Codex `PreToolUse` and `PostToolUse` events to
`hook-handler.js`, with the exact matcher `^apply_patch$`, `async: true`, and a
15 second timeout. The POSIX command resolves the handler from
`git rev-parse --show-toplevel`; `commandWindows` does the same through
PowerShell. Restart Codex after changing `hooks.json`, then use `/hooks` to
review and trust the exact project-local hook definition. Until that review,
Codex skips the non-managed hook.

The handler reads the event JSON from stdin, writes one sanitized JSON record
per event to `.audit/`, and exits 0 after best-effort recording. It emits no
stdout and no hook control fields, so it cannot block, allow, or alter
execution. Storage failures go to stderr. Records omit patch content,
`tool_response`, `transcript_path`, prompts, and secrets. Async hooks may be
observed after the tool completes and records can arrive out of order; use the
correlation fields to associate them.

Only the explicitly controlled prefix
`tools/personal-core-audit-pilot/.audit-smoke/` is audited as `routine` by
invoking the existing comparator. Other valid `apply_patch` events are logged
`OUT_OF_SCOPE`; an explicitly supplied non-routine class remains out of scope
even under the smoke prefix. Malformed events are logged `ERROR`. Bee Search's
policy remains authoritative. The adapter does not use O_wiki's DSH `ask_user_question`,
`tools/pre-execute`, or `tools/result` events.

The minimal `personal-core.js` loader/evaluator/evidence correlation pieces are
adapted from O_wiki Personal Core v0.1, tag `personal-core-v0.1`, commit
`a84eabd7ddd45d3cb3e6fc36523e81f99339ca1b`. O_wiki paths, ownership zones,
`wiki_structure`, context mapping, DSH adapter, and stored policy rules are not
copied. A fresh PC snapshot is generated for each event from the authoritative
`routine` block and the exact normalized action/resource.
The audit API binds that source to the repository's own
`.agent/decision-policy.yaml`; callers cannot substitute another policy file.

Run the fixture matrix from the repository root:

```powershell
node tools/personal-core-audit-pilot/run-fixtures.js
```

Run the hook integration fixtures and report stored records from a session:

```powershell
node tools/personal-core-audit-pilot/run-hook-fixtures.js
node tools/personal-core-audit-pilot/report-audit.js
node tools/personal-core-audit-pilot/report-audit.js path\to\another\.audit
```

For a controlled live smoke check, after restarting Codex, apply a harmless
patch to a file under `tools/personal-core-audit-pilot/.audit-smoke/`. Inspect
the resulting files with `report-audit.js`; the corresponding PreToolUse and
PostToolUse rows should be `MATCH`. Files in that directory are Git-ignored;
do not use it for application or policy files. The hook is observational and
does not add approval.

The fixture matrix covers slash, Windows backslash, absolute, and dot-relative
paths. It also checks outside-root, delete, multi-resource, unsupported class,
null/empty policy, and cross-application evidence correlation failures.

Audit matrix produced by the fixtures:

| Case | Bee Search policy | Personal Core audit | Match |
| --- | --- | --- | --- |
| slash | routine → decide_and_continue | ALLOW | MATCH |
| backslash | routine → decide_and_continue | ALLOW | MATCH |
| absolute | routine → decide_and_continue | ALLOW | MATCH |
| dot-relative | routine → decide_and_continue | ALLOW | MATCH |

Bee Search remains authoritative. The pilot does not implement enforcement,
policy migration, preference generalization, persistence, Android integration,
or a new runtime adapter framework. Routine fixture success is not written to
`.agent/evaluation-log.md`, in accordance with `.agent/evaluation-policy.yaml`.
The matrix validates the narrow semantic translation `decide_and_continue →
ALLOW`. It deliberately does not implement an independent Bee Search classifier,
because that would create the second policy source this pilot is designed to avoid.
