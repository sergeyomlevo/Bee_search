'use strict';

// Best-effort, audit-only Subagent hook observer. It never returns control
// fields and never persists transcript paths, prompts, or assistant messages.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const auditDir = process.env.BEE_LUNA_AUDIT_DIR
  ? path.resolve(process.env.BEE_LUNA_AUDIT_DIR)
  : path.join(__dirname, '.audit');

function text(value) {
  return typeof value === 'string' ? value : undefined;
}

function uniqueName(observedAt) {
  const stamp = observedAt.replace(/[^0-9A-Za-z]/g, '');
  return `${stamp}-${process.pid}-${crypto.randomBytes(6).toString('hex')}.json`;
}

function errorRecord(observedAt, reason) {
  return {
    schema: 'luna-delegation-audit',
    version: 1,
    observed_at: observedAt,
    result: 'ERROR',
    reason
  };
}

function validRecord(input, observedAt) {
  const record = {
    schema: 'luna-delegation-audit',
    version: 1,
    observed_at: observedAt,
    hook_event_name: input.hook_event_name
  };
  for (const key of ['session_id', 'turn_id', 'agent_id', 'agent_type', 'model']) {
    const value = text(input[key]);
    if (value !== undefined) record[key] = value;
  }
  return record;
}

function store(record) {
  fs.mkdirSync(auditDir, { recursive: true });
  const target = path.join(auditDir, uniqueName(record.observed_at));
  fs.writeFileSync(target, `${JSON.stringify(record)}\n`, { encoding: 'utf8', flag: 'wx' });
}

let input = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => { input += chunk; });
process.stdin.on('end', () => {
  const observedAt = new Date().toISOString();
  let record;
  try {
    const parsed = JSON.parse(input);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      record = errorRecord(observedAt, 'invalid_json_object');
    } else if (parsed.hook_event_name !== 'SubagentStart' && parsed.hook_event_name !== 'SubagentStop') {
      record = errorRecord(observedAt, 'unsupported_hook_event');
    } else {
      record = validRecord(parsed, observedAt);
    }
  } catch (_error) {
    record = errorRecord(observedAt, 'invalid_json');
  }

  try {
    store(record);
  } catch (error) {
    process.stderr.write(`luna-delegation-audit: storage failure: ${error.message}\n`);
  }

  // Codex SubagentStop requires JSON stdout. Empty object is deliberately not
  // a decision/control response and is emitted for every input path.
  process.stdout.write('{}\n');
});
