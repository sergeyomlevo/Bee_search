'use strict';

const assert = require('node:assert/strict');
const { auditEvent, canonicalPath, parseRoutinePolicy, readRoutinePolicy, REPO_ROOT: repoRoot, POLICY_PATH: policyPath } = require('./pilot');
const base = { session_id: 's-fixture', turn_id: 't-fixture', tool_name: 'apply_patch', hook_event_name: 'PreToolUse', decision_class: 'routine' };
const fixtures = [
  ['slash', 'tools/personal-core-audit-pilot/fixtures/sample.txt'],
  ['backslash', 'tools\\personal-core-audit-pilot\\fixtures\\sample.txt'],
  ['absolute', `${repoRoot}\\tools\\personal-core-audit-pilot\\fixtures\\sample.txt`],
  ['dot-relative', '.\\tools\\personal-core-audit-pilot\\fixtures\\..\\fixtures\\sample.txt'],
];

function eventFor(name, file, i) {
  return { ...base, tool_use_id: `u-${i}`, tool_input: { command: `*** Begin Patch\n*** Update File: ${file}\n@@\n-old\n+new\n*** End Patch` }, name };
}

function run() {
  assert.equal(readRoutinePolicy(policyPath).action, 'decide_and_continue');
  const rows = fixtures.map(([name, file], i) => {
    const result = auditEvent({ event: eventFor(name, file, i) });
    assert.equal(result.match, true, `${name} did not match`);
    return [name, result.authoritative.action, result.personalCore.disposition, 'MATCH', result.personalCore.reason.code];
  });
  assert.throws(() => canonicalPath(repoRoot, `${repoRoot}/../outside.txt`), /outside repository|escapes/);
  assert.throws(() => canonicalPath(repoRoot, 'C:outside.txt'), /drive-relative/);
  assert.throws(() => auditEvent({ event: { ...eventFor('delete', 'tools/personal-core-audit-pilot/fixtures/a.txt', 10), tool_input: { command: '*** Begin Patch\n*** Delete File: tools/personal-core-audit-pilot/fixtures/a.txt\n*** End Patch' } } }), /out_of_scope/);
  assert.throws(() => auditEvent({ event: { ...eventFor('multi', 'tools/personal-core-audit-pilot/fixtures/a.txt', 11), tool_input: { command: '*** Begin Patch\n*** Update File: tools/personal-core-audit-pilot/fixtures/a.txt\n*** Update File: tools/personal-core-audit-pilot/fixtures/b.txt' } } }), /out_of_scope/);
  assert.equal(auditEvent({ event: { ...eventFor('wrong-class', 'tools/personal-core-audit-pilot/fixtures/a.txt', 12), decision_class: 'significant_reversible' } }).status, 'out_of_scope');
  assert.throws(() => require('./personal-core').loadPolicy(null), /load_error/);
  assert.throws(() => require('./personal-core').loadPolicy({ resources: {}, rules: {} }), /load_error/);
  assert.throws(() => parseRoutinePolicy(''), /load_error/);
  assert.throws(() => parseRoutinePolicy('other:\n  routine:\n    action: decide_and_continue\n    report: only_if_relevant\n  next:\n'), /load_error/);
  assert.throws(() => require('./personal-core').loadPolicy({ resources: { target: { type: 'file', match: { kind: 'exact', value: 'x' } } }, rules: { routine: { resource_ref: 'target', operations: ['delete'], disposition: 'ALLOW', reason: { code: 'x' } } } }), /load_error/);
  assert.throws(() => require('./personal-core').receiveEvidence({ application_id: 'a' }, { application_id: 'b' }), /correlation_error/);
  assert.throws(() => require('./personal-core').receiveEvidence({}, {}), /correlation_error/);
  assert.throws(() => auditEvent({ event: { ...eventFor('missing-turn', 'tools/personal-core-audit-pilot/fixtures/a.txt', 13), turn_id: '' } }), /correlation_error/);
  console.log('Case\tBee Search policy\tPersonal Core audit\tMatch\tReason');
  for (const row of rows) console.log(row.join('\t'));
  console.log(`PASS: ${rows.length} audit cases + risk/out-of-scope checks`);
}

try { run(); } catch (error) { console.error(`FAIL: ${error.message}`); process.exitCode = 1; }
