'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { recordEvent } = require('./hook-handler');

const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'bee-audit-'));
const cliDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bee-audit-cli-'));
const repoRoot = path.resolve(__dirname, '../..');
const smoke = 'tools/personal-core-audit-pilot/.audit-smoke/sample.txt';
function event(name, command, extra = {}) {
  return { hook_event_name: name, session_id: 's-hook', turn_id: 't-hook', tool_use_id: `u-${name}`, tool_name: 'apply_patch', tool_input: { command }, permission_mode: 'default', ...extra };
}
const patch = (file) => `*** Begin Patch\n*** Update File: ${file}\n@@\n-old\n+new\n*** End Patch`;
const pre = recordEvent(event('PreToolUse', patch(smoke)), { auditDir: dir, repoRoot, now: '2026-09-08T00:00:00.000Z' });
const post = recordEvent(event('PostToolUse', patch(smoke), { tool_response: 'secret response', transcript_path: 'secret transcript' }), { auditDir: dir, repoRoot, now: '2026-09-08T00:00:01.000Z' });
const outside = recordEvent(event('PreToolUse', patch('app/src/main/Foo.kt')), { auditDir: dir, repoRoot });
const nonRoutine = recordEvent(event('PreToolUse', patch(smoke), { decision_class: 'significant_reversible' }), { auditDir: dir, repoRoot });
const malformed = recordEvent(event('PreToolUse', '*** Begin Patch\n*** Update File: tools/personal-core-audit-pilot/.audit-smoke/a.txt'), { auditDir: dir, repoRoot });
assert.equal(pre.result, 'MATCH');
assert.equal(post.result, 'MATCH');
assert.equal(outside.result, 'OUT_OF_SCOPE');
assert.equal(nonRoutine.result, 'OUT_OF_SCOPE');
assert.equal(malformed.result, 'ERROR');
const records = fs.readdirSync(dir).filter((n) => n.endsWith('.json')).map((n) => JSON.parse(fs.readFileSync(path.join(dir, n), 'utf8')));
assert.equal(records.length, 5);
for (const row of records) {
  assert.equal('tool_response' in row, false);
  assert.equal('transcript_path' in row, false);
  assert.equal('patch' in row, false);
  assert.equal(row.session_id, 's-hook');
  assert.equal(row.turn_id, 't-hook');
}
const report = spawnSync(process.execPath, [path.join(__dirname, 'report-audit.js'), dir], { encoding: 'utf8' });
assert.equal(report.status, 0);
assert.match(report.stdout, /MATCH=2/);
assert.match(report.stdout, /OUT_OF_SCOPE=2/);
assert.match(report.stdout, /ERROR=1/);

const hookConfig = JSON.parse(fs.readFileSync(path.join(repoRoot, '.codex', 'hooks.json'), 'utf8'));
assert.deepEqual(Object.keys(hookConfig.hooks).sort(), ['PostToolUse', 'PreToolUse']);
for (const hookName of ['PreToolUse', 'PostToolUse']) {
  const entry = hookConfig.hooks[hookName][0];
  assert.equal(entry.matcher, '^apply_patch$');
  assert.equal(entry.hooks[0].async, true);
  assert.equal(entry.hooks[0].type, 'command');
  assert.equal('PermissionRequest' in hookConfig.hooks, false);
}

const cli = spawnSync(process.execPath, [path.join(__dirname, 'hook-handler.js')], {
  cwd: repoRoot,
  encoding: 'utf8',
  input: JSON.stringify(event('PreToolUse', patch(smoke))),
  env: { ...process.env, BEE_PC_AUDIT_DIR: cliDir },
});
assert.equal(cli.status, 0);
assert.equal(cli.stdout, '');
assert.equal(fs.readdirSync(cliDir).filter((name) => name.endsWith('.json')).length, 1);
console.log('PASS: Pre/Post MATCH, OUT_OF_SCOPE, ERROR, sanitization, correlation, config, silent CLI, report');
