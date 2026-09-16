# DeepSeek Harness integration (audit-only)

This document records how the v2 observer receives telemetry from the real
DeepSeek Harness, what was verified live, and what remains unverified. Codex
integration is separate and unchanged: it reaches the same observer through
`.codex/hooks.json` → `hook-handler.js`.

## 1. Where the mechanism actually lives

DSH does not read `.codex/hooks.json`; that file is Codex configuration. DSH's
own lifecycle is Cordis **events**, delivered to **plugins**:

| Layer | Location |
| --- | --- |
| Event catalog and contracts | `ctx.on(...)` on the live Cordis context; inspectable through the `Event` Inspect provider |
| Tool execution pipeline | `@deepseek-ai/dsh-tools` (`README.md` § pipeline), events `tools/pre-execute`, `tools/execute`, `tools/post-execute`, `tools/result` |
| Durable session log feed | `@deepseek-ai/dsh-session` (`lib/types/types.d.ts`, `SessionEventMap`), emitted as the `session/event` firehose |
| Plugin registration | the profile composition: bundle layers → the profile's `cordis.patch.yml` (user layer) → `$DSH_HOME/cordis.patch.yml` → `--patch` overlays |
| Live patch-layer reload | `@deepseek-ai/dsh-app-boot` (`lib/index.js`, `watchUserPatches`) registers a config watch on the two user patch files and re-applies the composed tree |

Verified event names and dispatch modes:

| Event | Mode | Meaning |
| --- | --- | --- |
| `session/event` | emit | durable, post-commit append feed; carries `tool/call`, `tool/result`, `turn/start`, `turn/end`, and other log events |
| `tools/result` | emit | live, observe-only notification of the frozen final tool outcome |
| `tools/pre-execute` | waterfall | allow/deny/ask gate — **not used**: an observer must not sit in the decision path |
| `tools/post-execute` | waterfall | may replace or block a result — **not used** |
| `tools/execute` | waterfall | timeout/retry/metrics wrapper — **not used** |

## 2. What the adapter consumes

[`dsh-adapter.mjs`](dsh-adapter.mjs) subscribes to exactly two emit events and
never to a waterfall, so it can observe but not decide:

| DSH source | Normalized v2 field | Confidence / limitation |
| --- | --- | --- |
| `session/event` type `tool/call`, `data.callId` | `tool_use_id` | verified live; the identity also appears on `tools/result`, which is what pairs the lifecycle records |
| `session/event` `tool/call`, `data.name` | `tool_name` | verified live |
| `tools/result`, `exec.callId` | `tool_use_id` | verified live (identical to the attempt's id) |
| `tools/result`, `exec.name` | `tool_name` | verified live |
| `tools/result`, `result.isError` | `tool_response.is_error` → `technical_outcome` | verified live |
| `session`/`agent.session` `header.cwd` | workspace filter (not stored) | verified live; sessions outside the configured workspace are skipped entirely |
| `session.id` / `agent.id` | `session_id` | verified live |
| `session/event` `data.turn`, `data.step` | `source.turn`, `source.step` | verified live; native numbers kept verbatim |
| `session/event` `seq`, `time` | `source.seq`, `source.event_time_ms`; `observed_at` | verified live |
| `turn/start` | boundary slot `UserPromptSubmit` | verified live; DSH semantics are "turn opened", not "prompt submitted" |
| `turn/end` with `reason.kind` | `Stop`, or `Interrupt` when `aborted` | verified live; native kind preserved in `source.boundary_reason` |
| tool arguments | `tool_input` reduced to resource keys | only `file_path`/`path`/`files`-style values cross the boundary; every other argument stays inside the harness |
| — | `turn_id` | **no analog**: DSH logs a numeric turn, so `turn_id` stays `null` rather than being synthesized |
| — | `agent_id` | **no analog** on either transport; stays `null` |

Because each phase is taken from exactly one transport, nothing is
double-counted: the attempt comes from the durable log feed, the settled outcome
from the live pipeline notification. The durable `tool/result` event is
deliberately ignored.

## 3. How it is registered

The row lives in the **profile user layer** (host plane, because the observer
must see every session in the process):

```yaml
# $DSH_HOME/profiles/web/cordis.patch.yml
- insert:
    - id: bee-search-personal-core-audit
      name: 'file:///C:/App/Bee_search/tools/personal-core-audit-pilot/dsh-adapter.mjs'
      config:
        workspaceRoots:
          - 'C:\App\Bee_search'
```

Removing the row detaches the observer; nothing else depends on it.

**Restart: not required.** `watchUserPatches` watches this exact file
(`@deepseek-ai/dsh-app-boot`, `lib/index.js`), so editing it re-composes the
tree live. This was verified: after the row was written, real tool calls in the
running session appeared in `.audit/` without restarting the harness.

An in-session alternative exists — a dynamic Cordis package defined through the
Cordis toolset — but such a package dies with the process and cannot `require`
the observer, so it was used only as the minimal live *probe*, never as the
integration.

## 4. What was proven live

Chain verified on 2026-09-11 in this DSH installation (session
`session-da023cb5-ed96-47e5-a042-ee4394adc6e5`):

```text
real DSH tool call (read, pwsh)
    -> real DSH lifecycle event (session/event tool/call, tools/result)
    -> dsh-adapter.mjs (registered plugin row)
    -> existing v2 observer (hook-handler.js)
    -> record on disk (tools/personal-core-audit-pilot/.audit/*.json)
```

Observed in the store: `READ` and `COMMAND` activity, paired Pre/Post records
sharing one call id, `COMPLETED` outcomes, and `source.harness:
"deepseek-harness"` provenance. Every record carries the DSH session id, so live
records cannot be confused with Codex-era v1 records.

## 5. Verification and fixtures

```powershell
node tools/personal-core-audit-pilot/run-dsh-adapter-fixtures.js   # adapter mapping + observer integration
node tools/personal-core-audit-pilot/run-hook-fixtures.js          # Codex-side telemetry suite
node tools/personal-core-audit-pilot/run-fixtures.js               # comparator matrix
node tools/personal-core-audit-pilot/report-audit.js               # aggregate the store
```

The adapter fixtures replay payload shapes captured from live DSH events through
the real observer, into temporary directories; they prove the mapping, not
delivery. Live delivery is proven by the records described above.

## 6. Known limitations

- **Activity, not intent.** A `tool/call` record proves the model requested a
  call; a `tools/result` record proves a settled outcome. Neither proves the work
  was correct, wanted, or compliant.
- **Effect detection needs file resources.** `observable_effect` is
  fingerprint-based, so it only ever says anything for tools whose arguments
  name files; commands stay `NOT_APPLICABLE` or `UNKNOWN`.
- **No per-turn counters.** DSH logs numeric turns, and the report refuses to
  print a turn count it cannot key, showing `NOT OBSERVABLE (records carry a
  numeric turn, not a turn id)` instead of a misleading zero.
- **Subagent sessions count too.** Any session whose `cwd` is the configured
  workspace is observed, including nested ones; records are kept apart by
  `session_id`.
- **The row depends on a repository path.** If the adapter file disappears, the
  profile reports the row as unresolvable; remove the row to detach cleanly.
- **Not an enforcement point.** No allow, deny, ask, approval, blocking, or
  redirect exists anywhere in this integration, by design.
