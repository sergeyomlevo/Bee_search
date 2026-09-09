'use strict';

const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const root = __dirname;
const handler = path.join(root, 'hook-handler.js');
const repoRoot = path.resolve(root, '..', '..');
const fixtureDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bee-luna-audit-'));
const forbidden = new Set([
  'transcript_path', 'agent_transcript_path', 'last_assistant_message', 'prompt',
  'cwd', 'permission_mode', 'stop_hook_active', 'raw'
]);

function replay(input) {
  const result = spawnSync(process.execPath, [handler], {
    input, encoding: 'utf8', env: { ...process.env, BEE_LUNA_AUDIT_DIR: fixtureDir }
  });
  assert.strictEqual(result.status, 0, result.stderr);
  assert.strictEqual(result.stdout, '{}\n');
}

replay(JSON.stringify({
  session_id: 'session-start', transcript_path: '/private/start.json', cwd: 'C:\\App\\Bee_search',
  hook_event_name: 'SubagentStart', model: 'gpt-5.6-luna', turn_id: 'turn-1',
  permission_mode: 'default', agent_id: 'agent-1', agent_type: 'luna-worker', prompt: 'secret'
}));
replay(JSON.stringify({
  session_id: 'session-stop', transcript_path: '/private/stop.json', cwd: 'C:\\App\\Bee_search',
  hook_event_name: 'SubagentStop', model: 'gpt-5.6-luna', turn_id: 'turn-2',
  agent_id: 'agent-2', agent_type: 'luna-verifier', agent_transcript_path: '/private/agent.json',
  stop_hook_active: false, last_assistant_message: 'secret message'
}));
replay(JSON.stringify({ hook_event_name: 'SubagentStart', session_id: 'partial' }));
replay('{not-json');
replay(JSON.stringify({ hook_event_name: 'SessionStart', raw: 'secret' }));

const records = fs.readdirSync(fixtureDir).filter(name => name.endsWith('.json'))
  .map(name => JSON.parse(fs.readFileSync(path.join(fixtureDir, name), 'utf8')));
assert.strictEqual(records.length, 5);
assert.strictEqual(records.filter(row => row.hook_event_name === 'SubagentStart').length, 2);
assert.strictEqual(records.filter(row => row.hook_event_name === 'SubagentStop').length, 1);
assert.strictEqual(records.filter(row => row.result === 'ERROR').length, 2);
for (const record of records) {
  for (const key of Object.keys(record)) assert(!forbidden.has(key), `forbidden field: ${key}`);
  assert(!JSON.stringify(record).includes('secret'));
}
assert.deepStrictEqual(
  records.find(row => row.hook_event_name === 'SubagentStart' && row.session_id === 'partial').model,
  undefined
);

const report = spawnSync(process.execPath, [path.join(root, 'report-audit.js'), fixtureDir], { encoding: 'utf8' });
assert.strictEqual(report.status, 0, report.stderr);
assert.match(report.stdout, /Counts: start=2 stop=1 error=2/);

const config = JSON.parse(fs.readFileSync(path.join(repoRoot, '.codex', 'hooks.json'), 'utf8'));
for (const eventName of ['SubagentStart', 'SubagentStop']) {
  const groups = config.hooks[eventName];
  assert.strictEqual(groups.length, 1);
  const hook = groups[0].hooks[0];
  assert.strictEqual(hook.type, 'command');
  assert.strictEqual(hook.async, true);
  assert.match(hook.command, /tools\/luna-delegation-audit\/hook-handler\.js/);
  assert.match(hook.commandWindows, /tools\/luna-delegation-audit\/hook-handler\.js/);
}
for (const eventName of ['PreToolUse', 'PostToolUse']) {
  const group = config.hooks[eventName][0];
  assert.strictEqual(group.matcher, '^apply_patch$');
  assert.match(group.hooks[0].command, /tools\/personal-core-audit-pilot\/hook-handler\.js/);
}
assert.strictEqual(config.hooks.PermissionRequest, undefined);

console.log(`PASS: ${records.length} sanitized audit fixtures, report, async hook config, Personal Core hooks preserved`);
