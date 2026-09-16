'use strict';

// Fixture suite for the DeepSeek Harness → v2 adapter.
//
// The payload shapes used here were captured from REAL live DSH events during
// this task (see README § DSH integration and the live probe records); the suite
// itself replays them through the adapter's mapping functions and the real v2
// observer, so it proves the adapter→observer path, not live delivery.
//
// Every case writes into OS temp directories: the historical `.audit/` store and
// the repository are never touched.

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const handler = require('./hook-handler');
const { renderReport } = require('./report-audit');

const SECRET = 'SECRET-SENTINEL-DO-NOT-STORE';
const tempDirs = [];
const temp = (prefix) => { const dir = fs.mkdtempSync(path.join(os.tmpdir(), prefix)); tempDirs.push(dir); return dir; };

const store = temp('bee-dsh-audit-');
const observationRoot = temp('bee-dsh-root-');
const otherRoot = temp('bee-dsh-other-');
fs.mkdirSync(path.join(observationRoot, 'notes'), { recursive: true });

const listeners = {};
const ctx = { on: (name, fn) => { (listeners[name] = listeners[name] || []).push(fn); return () => {}; } };
const emit = (name, ...args) => { for (const fn of listeners[name] || []) fn(...args); };
const storedNames = () => fs.readdirSync(store).filter((n) => n.endsWith('.json'));
const recordAddedBy = (action) => {
  const before = new Set(storedNames());
  action();
  const added = storedNames().filter((n) => !before.has(n));
  assert.equal(added.length, action.expected === undefined ? 1 : action.expected, 'unexpected number of new records');
  return added.map((n) => JSON.parse(fs.readFileSync(path.join(store, n), 'utf8')));
};
const rowsOf = () => storedNames().map((n) => JSON.parse(fs.readFileSync(path.join(store, n), 'utf8')))
  .sort((a, b) => String(a.observed_at).localeCompare(String(b.observed_at)));

const SESSION_ID = 'session-fixture-dsh';
const session = { id: SESSION_ID, header: { cwd: observationRoot } };
const otherSession = { id: 'session-elsewhere', header: { cwd: otherRoot } };
const call = (n) => `call_00_fixture${n}`;
const sessionEvent = (type, data, seq = 100) => ({ type, seq, time: 1789107310000 + seq, data });

async function main() {
  const adapter = await import('./dsh-adapter.mjs');
  adapter.apply(ctx, { workspaceRoots: [observationRoot], auditDir: store, repoRoot: observationRoot });

  assert.ok(Array.isArray(listeners['session/event']));
  assert.ok(Array.isArray(listeners['tools/result']));
  assert.equal(listeners['tools/pre-execute'], undefined, 'the adapter must not sit in the decision pipeline');
  assert.equal(listeners['tools/post-execute'], undefined);
  assert.equal(listeners['tools/execute'], undefined);

  // 1. Attempt, in the live `session/event` tool/call shape, for a read.
  const [pre] = recordAddedBy(() => emit('session/event', session, sessionEvent('tool/call', {
    turn: 12, step: 3, callId: call(1), name: 'read', arguments: JSON.stringify({ file_path: 'AGENTS.md' }),
  }, 501)));
  assert.equal(pre.hook_event_name, 'PreToolUse');
  assert.equal(pre.session_id, SESSION_ID);
  assert.equal(pre.tool_name, 'read');
  assert.equal(pre.tool_use_id, call(1));
  assert.equal(pre.turn_id, null, 'DSH has no turn_id; it must stay null');
  assert.equal(pre.lifecycle.phase, 'PRE');
  assert.equal(pre.technical_outcome, 'PRE_OBSERVED');
  assert.equal(pre.activity.category, 'READ');
  assert.deepEqual(pre.activity.resources.map((r) => r.path), ['AGENTS.md']);
  assert.equal(pre.governance.status, 'NOT_EVALUATED');
  assert.equal(pre.source.harness, 'deepseek-harness');
  assert.equal(pre.source.transport, 'session/event');
  assert.equal(pre.source.event_type, 'tool/call');
  assert.equal(pre.source.turn, 12);
  assert.equal(pre.source.step, 3);
  assert.equal(pre.source.seq, 501);
  assert.equal(pre.observed_at, new Date(1789107310000 + 501).toISOString());

  // 2. The durable `tool/result` event is deliberately not consumed: no double counting.
  assert.equal(adapter.mapDshSessionEvent(session, sessionEvent('tool/result', { turn: 12, step: 3, message: {} })), null);

  // 3. Settled outcome, in the live `tools/result` shape, pairs with the attempt.
  const [post] = recordAddedBy(() => emit('tools/result', {
    callId: call(1), name: 'read', arguments: { file_path: 'AGENTS.md' }, agent: { id: SESSION_ID, session },
  }, { isError: false }));
  assert.equal(post.hook_event_name, 'PostToolUse');
  assert.equal(post.tool_use_id, call(1), 'pairing uses the same call identity as the attempt');
  assert.equal(post.lifecycle.phase, 'POST');
  assert.equal(post.lifecycle.pre_fingerprint_seen, true);
  assert.equal(post.technical_outcome, 'COMPLETED');
  assert.equal(post.effect.observable_effect, 'NOT_APPLICABLE');
  assert.equal(post.source.transport, 'tools/result');

  // 4. Technical failure stays a technical outcome, and command text is never parsed.
  recordAddedBy(() => emit('session/event', session, sessionEvent('tool/call', {
    turn: 12, step: 4, callId: call(2), name: 'pwsh', arguments: JSON.stringify({ command: `Write-Output ${SECRET}` }),
  })));
  const [failed] = recordAddedBy(() => emit('tools/result', {
    callId: call(2), name: 'pwsh', arguments: { command: `Write-Output ${SECRET}` }, agent: { id: SESSION_ID, session },
  }, { isError: true }));
  assert.equal(failed.technical_outcome, 'FAILED');
  assert.equal(failed.error_class, 'error_field');
  assert.equal(failed.activity.category, 'COMMAND');
  assert.deepEqual(failed.activity.resources, [], 'command text must not be parsed for resources');

  // 5. Mutation with a real observed change.
  const [preWrite] = recordAddedBy(() => emit('session/event', session, sessionEvent('tool/call', {
    turn: 12, step: 5, callId: call(3), name: 'write', arguments: JSON.stringify({ file_path: 'notes/created.txt', content: SECRET }),
  })));
  assert.equal(preWrite.activity.category, 'MUTATION');
  assert.equal(preWrite.activity.resources[0].existed_before, false);
  assert.equal(preWrite.activity.resources[0].path, 'notes/created.txt');
  fs.writeFileSync(path.join(observationRoot, 'notes', 'created.txt'), 'created by fixture\n', 'utf8');
  const [postWrite] = recordAddedBy(() => emit('tools/result', {
    callId: call(3), name: 'write', arguments: { file_path: 'notes/created.txt', content: SECRET }, agent: { id: SESSION_ID, session },
  }, { isError: false }));
  assert.equal(postWrite.effect.observable_effect, 'OBSERVED_CHANGE');

  // 6. Multi-resource mutation is captured; arguments are reduced to paths only.
  const [multi] = recordAddedBy(() => emit('session/event', session, sessionEvent('tool/call', {
    turn: 12, step: 6, callId: call(4), name: 'write', arguments: JSON.stringify({ files: ['notes/a.txt', 'notes/b.txt'], token: SECRET }),
  })));
  assert.deepEqual(multi.activity.resources.map((r) => r.path), ['notes/a.txt', 'notes/b.txt']);

  // 7. Turn boundaries: the native DSH reason survives in `source`.
  const [turnStart] = recordAddedBy(() => emit('session/event', session, sessionEvent('turn/start', { turn: 12 }, 601)));
  assert.equal(turnStart.turn_boundary.event, 'UserPromptSubmit');
  const [turnEnd] = recordAddedBy(() => emit('session/event', session, sessionEvent('turn/end', { turn: 12, reason: { kind: 'completed' } }, 602)));
  assert.equal(turnEnd.turn_boundary.event, 'Stop');
  const [interrupted] = recordAddedBy(() => emit('session/event', session, sessionEvent('turn/end', { turn: 13, reason: { kind: 'aborted', reason: 'user' } }, 603)));
  assert.equal(interrupted.turn_boundary.event, 'Interrupt');
  assert.equal(interrupted.source.boundary_reason, 'aborted');
  assert.equal(interrupted.source.turn, 13);

  // 8. Workspace filter: foreign workspaces and unknown cwd never reach the store.
  const before = storedNames().length;
  emit('session/event', otherSession, sessionEvent('tool/call', { turn: 1, step: 1, callId: call(9), name: 'read', arguments: '{}' }));
  emit('session/event', { id: 'session-no-cwd', header: {} }, sessionEvent('tool/call', { turn: 1, step: 1, callId: call(10), name: 'read', arguments: '{}' }));
  emit('tools/result', { callId: call(11), name: 'read', arguments: {}, agent: { id: 'session-elsewhere', session: otherSession } }, { isError: false });
  assert.equal(storedNames().length, before, 'foreign sessions must not be audited');

  // 9. Malformed payloads never throw and never produce a record.
  assert.equal(adapter.mapDshSessionEvent(session, null), null);
  assert.equal(adapter.mapDshSessionEvent(session, { type: 'tool/call', data: { turn: 1 } }), null);
  assert.equal(adapter.mapDshToolResult(null, null), null);
  assert.equal(adapter.mapDshToolResult({ callId: call(12) }, null), null);
  assert.equal(adapter.pickResourceArguments('{not json'), undefined);
  assert.equal(adapter.pickResourceArguments('x'.repeat(9000)), undefined);
  emit('session/event', session, { type: 'tool/call', data: null });
  emit('tools/result', undefined, undefined);
  assert.equal(storedNames().length, before);

  // 10. Privacy boundary: no sentinel, no forbidden keys anywhere.
  const forbidden = ['transcript_path', 'patch', 'command', 'prompt', 'assistant', 'content', 'diff', 'stdout', 'stderr', 'file_contents'];
  for (const row of rowsOf()) {
    assert.equal(JSON.stringify(row).includes(SECRET), false, `secret leaked into ${row.hook_event_name}`);
    for (const key of forbidden) assert.equal(key in row, false, `forbidden key ${key} stored`);
    assert.equal(row.schema, handler.SCHEMA);
    assert.equal(row.schema_version, 2);
  }

  // 11. The report sees the adapter's records, and pairing is visible.
  const report = renderReport(store);
  const rows = rowsOf();
  const preCount = rows.filter((r) => r.lifecycle?.phase === 'PRE').length;
  const postCount = rows.filter((r) => r.lifecycle?.phase === 'POST').length;
  assert.equal(preCount, 4);
  assert.equal(postCount, 3);
  assert.match(report, new RegExp(`^Pre observed\\t${preCount}$`, 'm'));
  assert.match(report, new RegExp(`^Post observed\\t${postCount}$`, 'm'));
  assert.match(report, /^Paired\t3$/m);
  assert.match(report, /^Activity\tREAD=1$/m);
  assert.match(report, /^Activity\tMUTATION=2$/m);
  assert.match(report, /^Activity\tCOMMAND=1$/m);
  assert.match(report, /^Effect\tOBSERVED_CHANGE=1$/m);
  assert.match(report, /^Effect\tNOT_APPLICABLE=2$/m);
  assert.match(report, /^Interrupted turns\t1 \(by boundary event; turn ids unavailable\)$/m);
  assert.match(report, /^Observed turns\tNOT OBSERVABLE \(records carry a numeric turn, not a turn id\)$/m);
  assert.match(report, /^Turns with no observed tool activity\tNOT OBSERVABLE \(records carry a numeric turn, not a turn id\)$/m);
  assert.match(report, /^Note: \d+ record\(s\) come from deepseek-harness provenance and log a numeric turn/m);

  console.log('PASS: DSH adapter mapping (attempt/outcome/boundaries), pairing, workspace filter, privacy, report integration');
  console.log(`fixture records: ${rows.length} (replay of captured live payload shapes, not live delivery)`);
}

main().catch((error) => { console.error(error); process.exitCode = 1; }).finally(() => {
  for (const dir of tempDirs) fs.rmSync(dir, { recursive: true, force: true });
});
