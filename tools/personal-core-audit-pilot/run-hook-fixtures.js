'use strict';

// Fixture suite for the v2 audit-only telemetry observer.
//
// Every case writes into a fresh OS temp directory; the historical `.audit/`
// store is never touched and nothing is written into the real repository.
// Activity and effect probes run against a temporary "observation root" that
// stands in for the repository root, so the whole suite is self-contained.
//
// Cases 1-17 are the enumerated telemetry requirements. Case 10b and the
// "unsupported hook event" check are additional honesty probes.

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const handler = require('./hook-handler');
const pilot = require('./pilot');
const { renderReport } = require('./report-audit');

const repoRoot = path.resolve(__dirname, '../..');
const SECRET = 'SECRET-SENTINEL-DO-NOT-STORE';
const tempDirs = [];
const temp = (prefix) => { const dir = fs.mkdtempSync(path.join(os.tmpdir(), prefix)); tempDirs.push(dir); return dir; };

const storeTools = temp('bee-audit-tools-');
const storeTurn = temp('bee-audit-turn-');
const storeCli = temp('bee-audit-cli-');
const observationRoot = temp('bee-audit-observed-root-');

const fixturesRel = 'telemetry-fixtures';
const rel = (name) => `${fixturesRel}/${name}`;
const abs = (name) => path.join(observationRoot, rel(name));
fs.mkdirSync(path.join(abs('.')), { recursive: true });
fs.mkdirSync(path.join(observationRoot, 'docs'), { recursive: true });

const event = (hookEventName, extra = {}) => ({
  hook_event_name: hookEventName,
  session_id: 's-fixture',
  turn_id: 't-1',
  tool_use_id: 'u-default',
  ...extra,
});
const patch = (file, kind = 'Update') => `*** Begin Patch\n*** ${kind} File: ${file}\n@@\n-old\n+new\n*** End Patch`;
const record = (ev, dir, now) => handler.recordEvent(ev, { auditDir: dir, repoRoot: observationRoot, ...(now ? { now } : {}) });
const rowsOf = (dir) => fs.readdirSync(dir).filter((n) => n.endsWith('.json'))
  .map((n) => JSON.parse(fs.readFileSync(path.join(dir, n), 'utf8')))
  .sort((a, b) => String(a.observed_at).localeCompare(String(b.observed_at)));
const countBy = (list, pick) => {
  const map = new Map();
  for (const item of list) { const key = pick(item) || 'UNKNOWN'; map.set(key, (map.get(key) || 0) + 1); }
  return map;
};
function report(dir) {
  return renderReport(dir);
}
const lineValue = (stdout, label) => stdout.match(new RegExp(`^${label}\\t([^\\r\\n]+)$`, 'm'))?.[1];

try {
  assert.equal(handler.SCHEMA_VERSION, 2);
  assert.deepEqual(handler.BOUNDARY_EVENTS, ['UserPromptSubmit', 'Stop', 'Interrupt']);

  // 1. Normal Pre + Post completion of one routine single-file patch.
  assert.equal(fs.existsSync(abs('created.txt')), false);
  const pre1 = record(event('PreToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-01', decision_class: 'routine', tool_input: { command: patch(rel('created.txt'), 'Add') } }), storeTools, '2026-09-08T00:00:00.000Z');
  fs.writeFileSync(abs('created.txt'), 'created by fixture\n', 'utf8');
  const post1 = record(event('PostToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-01', decision_class: 'routine', tool_input: { command: patch(rel('created.txt'), 'Add') }, tool_response: { output: SECRET } }), storeTools, '2026-09-08T00:00:01.000Z');
  assert.equal(pre1.lifecycle.phase, 'PRE');
  assert.equal(pre1.technical_outcome, 'PRE_OBSERVED');
  assert.equal(pre1.activity.category, 'MUTATION');
  assert.equal(pre1.activity.category_source, 'tool_name_table');
  assert.equal(pre1.activity.resources[0].path, rel('created.txt'));
  assert.equal(pre1.activity.resources[0].existed_before, false);
  assert.equal(pre1.governance.status, 'MATCH');
  assert.equal(post1.lifecycle.phase, 'POST');
  assert.equal(post1.lifecycle.pre_fingerprint_seen, true);
  assert.equal(post1.technical_outcome, 'COMPLETED');
  assert.equal(post1.technical_outcome_source, 'field_probe');
  assert.equal(post1.effect.observable_effect, 'OBSERVED_CHANGE');
  assert.equal(post1.governance.status, 'MATCH');

  // 2. Technical tool failure stays a technical outcome, not a governance verdict.
  const post2 = record(event('PostToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-01', decision_class: 'routine', tool_input: { command: patch(rel('created.txt')) }, tool_response: { is_error: true, message: SECRET } }), storeTools);
  assert.equal(post2.technical_outcome, 'FAILED');
  assert.equal(post2.error_class, 'error_field');
  assert.equal(post2.audit.status, 'OK');

  // 3. Pre without Post: observed attempt, unfinished lifecycle.
  const pre3 = record(event('PreToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-03', tool_input: { command: patch(rel('lonely.txt')) } }), storeTools);
  assert.equal(pre3.lifecycle.phase, 'PRE');
  assert.equal(pre3.technical_outcome, 'PRE_OBSERVED');

  // 4. Post without Pre: outcome visible, effect unknowable.
  const post4 = record(event('PostToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-04', tool_input: { command: patch(rel('created.txt')) }, tool_response: { ok: true } }), storeTools);
  assert.equal(post4.lifecycle.pre_fingerprint_seen, false);
  assert.equal(post4.effect.observable_effect, 'UNKNOWN');
  assert.equal(post4.effect.evidence, 'no_pre_fingerprint');

  // 5. Read activity is recorded; the comparator cannot evaluate it.
  record(event('PreToolUse', { tool_name: 'read', tool_use_id: 'u-05', tool_input: { file_path: 'AGENTS.md' } }), storeTools);
  const post5 = record(event('PostToolUse', { tool_name: 'read', tool_use_id: 'u-05', tool_input: { file_path: 'AGENTS.md' }, tool_response: { content: SECRET } }), storeTools);
  assert.equal(post5.activity.category, 'READ');
  assert.deepEqual(post5.activity.resources.map((r) => r.path), ['AGENTS.md']);
  assert.equal(post5.effect.observable_effect, 'NOT_APPLICABLE');
  assert.equal(post5.effect.evidence, 'category_is_not_mutation');
  assert.equal(post5.governance.status, 'NOT_EVALUATED');
  assert.equal(post5.governance.reason_code, 'comparator_supports_apply_patch_only');

  // 6. Mutation through a non-apply_patch tool, with observed change.
  record(event('PreToolUse', { tool_name: 'write', tool_use_id: 'u-06', tool_input: { file_path: rel('written.txt') } }), storeTools);
  fs.writeFileSync(abs('written.txt'), 'written by fixture\n', 'utf8');
  const post6 = record(event('PostToolUse', { tool_name: 'write', tool_use_id: 'u-06', tool_input: { file_path: rel('written.txt') } }), storeTools);
  assert.equal(post6.activity.category, 'MUTATION');
  assert.equal(post6.activity.operation, 'create_or_modify');
  assert.equal(post6.effect.observable_effect, 'OBSERVED_CHANGE');

  // 7. Multi-resource mutation is captured by activity, refused by governance.
  const multi = record(event('PreToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-07', decision_class: 'routine', tool_input: { command: patch(rel('multi-a.txt')) + patch(rel('multi-b.txt')) } }), storeTools);
  assert.deepEqual(multi.activity.resources.map((r) => r.path).sort(), [rel('multi-a.txt'), rel('multi-b.txt')]);
  assert.equal(multi.governance.status, 'NOT_EVALUATED');
  assert.match(multi.governance.reason_code, /only one Add\/Update File resource is supported/);

  // 8. Command activity: no command text is stored and no resource is claimed.
  const command = record(event('PreToolUse', { tool_name: 'pwsh', tool_use_id: 'u-08', tool_input: { command: `Write-Output ${SECRET}` } }), storeTools);
  assert.equal(command.activity.category, 'COMMAND');
  assert.deepEqual(command.activity.resources, []);
  assert.equal(command.governance.status, 'NOT_EVALUATED');

  // 9. Other and unknown tools stay observable but unevaluable.
  const other = record(event('PreToolUse', { tool_name: 'mystery_tool_xyz', tool_use_id: 'u-09', tool_input: {} }), storeTools);
  assert.equal(other.activity.category, 'OTHER');
  assert.equal(other.activity.category_source, 'name_heuristic');
  const unnamed = record(event('PreToolUse', { tool_name: null, tool_use_id: 'u-09b', tool_input: {} }), storeTools);
  assert.equal(unnamed.activity.category, 'OTHER');
  assert.equal(unnamed.activity.category_source, 'unknown_tool');
  assert.equal(unnamed.tool_name, null);

  // 10. Governance MATCH requires an explicit routine decision class.
  const match10 = record(event('PreToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-10', decision_class: 'routine', tool_input: { command: patch(rel('created.txt')) } }), storeTools);
  assert.equal(match10.governance.status, 'MATCH');
  assert.equal(match10.governance.comparator, 'personal-core-v0.1');
  // 10b. The comparator itself has no repository-path restriction.
  const matchOutsideSmoke = record(event('PreToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-10b', decision_class: 'routine', tool_input: { command: patch('docs/glossary.md') } }), storeTools);
  assert.equal(matchOutsideSmoke.governance.status, 'MATCH');
  assert.deepEqual(matchOutsideSmoke.activity.resources.map((r) => r.path), ['docs/glossary.md']);

  // 11. MISMATCH is currently unreachable, so the mapping is tested at module level.
  const realAuditEvent = pilot.auditEvent;
  pilot.auditEvent = () => ({ match: false, authoritative: { action: 'decide_and_continue' }, personalCore: { disposition: 'ASK', reason: { code: 'fixture_mismatch' } } });
  let mismatch;
  try {
    mismatch = record(event('PreToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-11', decision_class: 'routine', tool_input: { command: patch(rel('created.txt')) } }), storeTools);
  } finally {
    pilot.auditEvent = realAuditEvent;
  }
  assert.equal(mismatch.governance.status, 'MISMATCH');
  assert.equal(mismatch.governance.reason_code, 'fixture_mismatch');

  // 12. NOT_EVALUATED when the hook payload carries no decision class.
  const noClass = record(event('PreToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-12', tool_input: { command: patch(rel('created.txt')) } }), storeTools);
  assert.equal(noClass.governance.status, 'NOT_EVALUATED');
  assert.equal(noClass.governance.reason_code, 'decision_class_not_routine');

  // 13. Malformed input is audited as an observer-side error instead of crashing.
  const malformedRow = record({ hook_event_name: null, tool_name: null, parse_error: 'Unexpected token' }, storeCli);
  assert.equal(malformedRow.audit.status, 'AUDIT_ERROR');
  assert.equal(malformedRow.audit.error_class, 'unsupported_hook_event');
  assert.equal(malformedRow.hook_event_name, null);
  const unsupported = record(event('SessionStart', { tool_use_id: null }), storeCli);
  assert.equal(unsupported.audit.status, 'AUDIT_ERROR');
  assert.equal(unsupported.audit.error_class, 'unsupported_hook_event');

  // 14. Storage failure fails open: the record still comes back, with a stderr diagnostic.
  const notADir = path.join(temp('bee-audit-file-'), 'store.json');
  fs.writeFileSync(notADir, '{}\n', 'utf8');
  const stderrWrite = process.stderr.write;
  let captured = '';
  process.stderr.write = (chunk) => { captured += String(chunk); return true; };
  let brokenRecord;
  try {
    brokenRecord = handler.recordEvent(event('PreToolUse', { tool_name: 'read', tool_use_id: 'u-14', tool_input: { file_path: 'AGENTS.md' } }), { auditDir: notADir, repoRoot: observationRoot });
  } finally {
    process.stderr.write = stderrWrite;
  }
  assert.equal(brokenRecord.audit.status, 'OK');
  assert.match(captured, /personal-core audit storage failed/);

  // CLI-level checks (process boundary, exit code, stdout silence). Under a file
  // sandbox that forbids piped child stdio, child_process returns status=null and
  // these fall back to the in-process equivalents above; the run reports the gap.
  const cli = spawnSync(process.execPath, [path.join(__dirname, 'hook-handler.js')], {
    cwd: repoRoot, encoding: 'utf8', env: { ...process.env, BEE_PC_AUDIT_DIR: storeCli },
    input: JSON.stringify(event('PreToolUse', { tool_name: 'read', tool_use_id: 'u-14b', tool_input: { file_path: 'AGENTS.md' } })),
  });
  const cliAvailable = cli.status !== null;
  if (cliAvailable) {
    assert.equal(cli.status, 0, cli.stderr);
    assert.equal(cli.stdout, '');
    assert.equal(rowsOf(storeCli).length, 3);
  } else {
    assert.equal(rowsOf(storeCli).length, 2);
  }

  // 15. A resource that was already dirty before the call is not a new change.
  fs.writeFileSync(abs('dirty.txt'), 'pre-existing uncommitted content\n', 'utf8');
  const pre15 = record(event('PreToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-15', tool_input: { command: patch(rel('dirty.txt')) } }), storeTools);
  assert.equal(pre15.activity.resources[0].existed_before, true);
  const post15 = record(event('PostToolUse', { tool_name: 'apply_patch', tool_use_id: 'u-15', tool_input: { command: patch(rel('dirty.txt')) }, tool_response: { ok: true } }), storeTools);
  assert.equal(post15.effect.observable_effect, 'NO_OBSERVED_CHANGE');

  // 16. Interrupted turn is only observable through a boundary event.
  const interrupted = record(event('Interrupt', { turn_id: 't-interrupted', tool_use_id: null }), storeTurn);
  assert.equal(interrupted.lifecycle.phase, 'BOUNDARY');
  assert.equal(interrupted.turn_boundary.event, 'Interrupt');
  assert.equal(interrupted.activity.category_source, 'turn_boundary_event');
  // 17. A turn with a start boundary and no tool activity stores no prompt text.
  const started = record(event('UserPromptSubmit', { turn_id: 't-quiet', tool_use_id: null, prompt: SECRET }), storeTurn);
  assert.equal(started.turn_boundary.event, 'UserPromptSubmit');
  const stopped = record(event('Stop', { turn_id: 't-quiet', tool_use_id: null }), storeTurn);
  assert.equal(stopped.turn_boundary.event, 'Stop');

  // Privacy sweep over every stored record.
  const stored = [...rowsOf(storeTools), ...rowsOf(storeTurn), ...rowsOf(storeCli)];
  const forbidden = ['tool_response', 'transcript_path', 'patch', 'command', 'prompt', 'response', 'content', 'diff', 'stdout', 'stderr', 'output', 'file_contents'];
  for (const row of stored) {
    const serialized = JSON.stringify(row);
    assert.equal(serialized.includes(SECRET), false, `secret leaked into ${row.hook_event_name}`);
    for (const key of forbidden) assert.equal(key in row, false, `forbidden key ${key} stored`);
    if (row.schema === handler.SCHEMA) {
      assert.equal(row.schema_version, 2);
      if (row.session_id) assert.equal(row.session_id, 's-fixture');
      assert.ok(row.observed_at);
    }
  }
  for (const name of ['created.txt', 'written.txt']) {
    const row = stored.find((r) => (r.activity?.resources || []).some((res) => res.path === rel(name)));
    assert.ok(row, `${name} activity was not observed`);
  }

  // Legacy v1 records stay readable and are not rewritten.
  const legacy = { schema: 'bee-search.personal-core-audit.v1', schema_version: 1, observed_at: '2026-01-01T00:00:00.000Z', hook_event_name: 'PreToolUse', session_id: 's-legacy', turn_id: 't-legacy', tool_use_id: 'u-legacy', tool_name: 'apply_patch', result: 'OUT_OF_SCOPE' };
  fs.writeFileSync(path.join(storeTools, 'legacy-v1.json'), `${JSON.stringify(legacy)}\n`, 'utf8');

  // Report coverage for tool-telemetry stores.
  const toolsRows = rowsOf(storeTools);
  const v2Rows = toolsRows.filter((row) => row.schema === handler.SCHEMA);
  const preRows = v2Rows.filter((row) => row.lifecycle?.phase === 'PRE');
  const postRows = v2Rows.filter((row) => row.lifecycle?.phase === 'POST');
  const reportTools = report(storeTools);
  assert.match(reportTools, new RegExp(`^Records: total=${toolsRows.length} v2=${v2Rows.length} legacy_v1=1$`, 'm'));
  assert.equal(lineValue(reportTools, 'Pre observed'), String(preRows.length));
  assert.equal(lineValue(reportTools, 'Post observed'), String(postRows.length));
  assert.ok(Number(lineValue(reportTools, 'Pre without Post')) > 0);
  assert.ok(Number(lineValue(reportTools, 'Post without Pre')) > 0);
  for (const [key, value] of countBy(preRows, (row) => row.activity?.category)) {
    assert.match(reportTools, new RegExp(`^Activity\\t${key}=${value}$`, 'm'));
  }
  for (const [key, value] of countBy(postRows, (row) => row.effect?.observable_effect)) {
    assert.match(reportTools, new RegExp(`^Effect\\t${key}=${value}$`, 'm'));
  }
  for (const [key, value] of countBy(v2Rows.filter((row) => row.lifecycle), (row) => row.governance?.status)) {
    assert.match(reportTools, new RegExp(`^Governance\\t${key}=${value}$`, 'm'));
  }
  assert.match(reportTools, /^Legacy result\tOUT_OF_SCOPE=1$/m);
  assert.match(reportTools, /^Note: Pre count != Post count is not by itself an anomaly/m);
  assert.match(reportTools, /NOT OBSERVABLE \(no turn-boundary events recorded\)/);

  // Report coverage for turn boundaries; absence of boundaries must not be invented.
  const reportTurn = report(storeTurn);
  assert.equal(lineValue(reportTurn, 'Interrupted turns'), '1');
  assert.equal(lineValue(reportTurn, 'Turns with no observed tool activity'), '2');
  assert.equal(lineValue(reportTurn, 'Turns with observed tool activity'), '0');
  assert.doesNotMatch(reportTurn, /NOT OBSERVABLE \(no turn-boundary events recorded\)/);

  // Hook configuration: broad observation, no control fields, boundary hooks absent.
  const hooks = JSON.parse(fs.readFileSync(path.join(repoRoot, '.codex', 'hooks.json'), 'utf8')).hooks;
  assert.deepEqual(Object.keys(hooks).sort(), ['PostToolUse', 'PreToolUse', 'SubagentStart', 'SubagentStop']);
  for (const name of ['PreToolUse', 'PostToolUse']) {
    const entry = hooks[name][0];
    assert.equal(entry.matcher, '.*');
    assert.equal(entry.hooks[0].type, 'command');
    assert.equal(entry.hooks[0].async, true);
    assert.match(entry.hooks[0].command, /personal-core-audit-pilot/);
  }
  for (const name of ['SubagentStart', 'SubagentStop']) {
    assert.match(hooks[name][0].hooks[0].command, /luna-delegation-audit/);
  }

  console.log('PASS: 17 telemetry cases + privacy sweep + legacy v1 + report aggregates + hook config');
  console.log(`CLI process checks: ${cliAvailable ? 'executed via child process' : 'DEGRADED - piped child stdio unavailable in this sandbox; in-process equivalents used instead'}`);
  console.log('LIMITATION: turn-boundary hooks (UserPromptSubmit/Stop/Interrupt) are recorded when delivered but are not registered in .codex/hooks.json.');
  console.log('LIMITATION: governance MATCH requires an explicit decision_class=routine field; case 12 shows live-style payloads stay NOT_EVALUATED.');
} finally {
  for (const dir of tempDirs) fs.rmSync(dir, { recursive: true, force: true });
}
