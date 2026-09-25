# Luna delegation audit

This is a dependency-free, audit-only observer for the official Codex
`SubagentStart` and `SubagentStop` command-hook events. It records what the
hook received; it does not block, approve, rewrite, trigger, or select an
agent. Background/asynchronous hooks cannot provide those controls.

The handler stores one JSON record per invocation in `.audit/` (or in the
directory named by `BEE_LUNA_AUDIT_DIR`). The command-hook contract can also
provide `transcript_path`, `cwd`, and
`permission_mode`; `SubagentStart` provides `agent_id` and `agent_type`, while
`SubagentStop` additionally provides `agent_transcript_path`,
`stop_hook_active`, and `last_assistant_message`. The observer intentionally
uses only the safe stored subset below:

| Field | Availability / meaning |
| --- | --- |
| `schema`, `version` | Audit record schema (`luna-delegation-audit`, `1`) |
| `observed_at` | Handler receipt time; this is not an event timestamp supplied by Codex |
| `hook_event_name` | `SubagentStart` or `SubagentStop` |
| `session_id`, `turn_id` | Available in the hook contract when supplied |
| `agent_id`, `agent_type` | Available for subagent events when supplied; type may be a profile/type name |
| `model` | Available model identifier when supplied |
| `result`, `reason` | Only on sanitized malformed/unsupported-input errors |

The handler intentionally does not store `transcript_path`,
`agent_transcript_path`, `last_assistant_message`, prompts, `cwd`,
`permission_mode`, `stop_hook_active`, raw input, or an invented profile or
event-timestamp field. Successful invocations print exactly `{}` as JSON and
exit zero, including `SubagentStop`.

Run deterministic checks and a report with:

```powershell
node tools/luna-delegation-audit/run-fixtures.js
node tools/luna-delegation-audit/report-audit.js
node tools/luna-delegation-audit/report-audit.js path\to\audit-dir
```

Official contract reference: [Codex Hooks](https://developers.openai.com/codex/hooks).

## Live verification after configuration changes

1. **EXPLICIT DELEGATION**: assign a safe, bounded read-only task to
   `luna-worker` or `luna-verifier` and confirm matching `SubagentStart` /
   `SubagentStop` records with model `gpt-5.6-luna`. This verifies the technical
   route, not autonomous routing policy.
2. **AUTONOMOUS DELEGATION**: in a later ordinary task with no mention of
   Luna/subagents, observe whether the root delegates due to the AGENTS
   preference.

When changing hooks themselves, reload and trust them before relying on a
new live audit record.
