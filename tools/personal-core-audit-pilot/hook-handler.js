'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { auditEvent, REPO_ROOT, canonicalPath, extractApplyPatchResource } = require('./pilot');

const SMOKE_PREFIX = 'tools/personal-core-audit-pilot/.audit-smoke/';
const SCHEMA = 'bee-search.personal-core-audit.v1';

function gitRoot() {
  return execFileSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' }).trim();
}

function correlation(event) {
  return {
    session_id: typeof event?.session_id === 'string' ? event.session_id : null,
    turn_id: typeof event?.turn_id === 'string' ? event.turn_id : null,
    tool_use_id: typeof event?.tool_use_id === 'string' ? event.tool_use_id : null,
  };
}

function baseRecord(event, now) {
  const record = {
    schema: SCHEMA,
    version: 1,
    observed_at: now,
    ...correlation(event),
    hook_event_name: event?.hook_event_name || null,
    tool_name: event?.tool_name || null,
    result: 'ERROR',
    reason: null,
  };
  if (typeof event?.permission_mode === 'string' && event.permission_mode) record.permission_mode = event.permission_mode;
  return record;
}

function parseResource(event, repoRoot) {
  const command = event?.tool_input?.command;
  if (typeof command !== 'string' || !/^\s*\*\*\* Begin Patch\r?\n[\s\S]*\r?\n\*\*\* End Patch\s*$/.test(command)) {
    throw new Error('[error] malformed apply_patch command');
  }
  const extracted = extractApplyPatchResource(command);
  const normalized = canonicalPath(repoRoot, extracted.path);
  return { normalized, operation: extracted.kind, smoke: normalized.startsWith(SMOKE_PREFIX) };
}

function auditRecord(event, options = {}) {
  const now = options.now || new Date().toISOString();
  const repoRoot = options.repoRoot || REPO_ROOT;
  const record = baseRecord(event, now);
  try {
    if (!event || (event.hook_event_name !== 'PreToolUse' && event.hook_event_name !== 'PostToolUse')) {
      record.reason = 'hook_event_name must be PreToolUse or PostToolUse';
      return record;
    }
    if (event.tool_name !== 'apply_patch') {
      record.result = 'OUT_OF_SCOPE';
      record.reason = 'only apply_patch is supported';
      return record;
    }
    const parsed = parseResource(event, repoRoot);
    record.normalized = { resource: parsed.normalized, operation: parsed.operation };
    if (!parsed.smoke) {
      record.result = 'OUT_OF_SCOPE';
      record.reason = 'resource is outside the controlled routine smoke prefix';
      return record;
    }
    if (event.decision_class !== undefined && event.decision_class !== 'routine') {
      record.result = 'OUT_OF_SCOPE';
      record.reason = 'explicit decision_class is outside the routine pilot';
      return record;
    }
    const audited = auditEvent({ event: { ...event, decision_class: 'routine' } });
    record.result = audited.match ? 'MATCH' : 'MISMATCH';
    record.reason = audited.personalCore.reason?.summary || 'audit comparison completed';
    record.authoritative = {
      decision_class: audited.authoritative.decision_class,
      action: audited.authoritative.action,
      report: audited.authoritative.report,
    };
    record.personal_core = {
      disposition: audited.personalCore.disposition,
      reason_code: audited.personalCore.reason?.code || null,
    };
    return record;
  } catch (error) {
    record.result = error.message?.startsWith('[out_of_scope]') ? 'OUT_OF_SCOPE' : 'ERROR';
    record.reason = error.message || 'audit failed';
    return record;
  }
}

function writeRecord(record, auditDir) {
  fs.mkdirSync(auditDir, { recursive: true });
  const stamp = `${Date.now()}-${process.hrtime.bigint()}-${Math.random().toString(16).slice(2)}`;
  fs.writeFileSync(path.join(auditDir, `${stamp}.json`), `${JSON.stringify(record)}\n`, { encoding: 'utf8', flag: 'wx' });
}

function recordEvent(event, options = {}) {
  const record = auditRecord(event, options);
  writeRecord(record, options.auditDir || path.join(options.repoRoot || REPO_ROOT, 'tools', 'personal-core-audit-pilot', '.audit'));
  return record;
}

function main() {
  let event;
  try { event = JSON.parse(fs.readFileSync(0, 'utf8')); } catch (error) { event = { hook_event_name: null, tool_name: null, parse_error: error.message }; }
  try {
    recordEvent(event, {
      repoRoot: gitRoot(),
      auditDir: process.env.BEE_PC_AUDIT_DIR || undefined,
    });
  } catch (error) {
    process.stderr.write(`personal-core audit storage failed: ${error.message}\n`);
  }
  // Hooks are strictly observational: no JSON is written to stdout and no control fields are returned.
}

if (require.main === module) main();

module.exports = { SCHEMA, SMOKE_PREFIX, auditRecord, recordEvent };
