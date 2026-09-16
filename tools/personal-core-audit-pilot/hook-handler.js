'use strict';

// Audit-only runtime telemetry observer for tool activity and (optionally) turn
// boundaries. It records metadata only, never returns hook control fields, and
// never stores prompts, responses, commands, patch bodies, file contents, or
// tool responses.
//
// Two dimensions are deliberately separate:
//   activity   — what was observably attempted/observed via tools;
//   governance — whether the personal-core comparator evaluated that action.
// "COMPLETED" means the observed tool lifecycle ended; it is not correctness.

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const activity = require('./activity');
const pilot = require('./pilot');

const SCHEMA = 'bee-search.personal-core-audit.v2';
const SCHEMA_VERSION = 2;
const HOOK_EVENTS = ['PreToolUse', 'PostToolUse', 'UserPromptSubmit', 'Stop', 'Interrupt'];
const BOUNDARY_EVENTS = ['UserPromptSubmit', 'Stop', 'Interrupt'];
const PENDING_DIR = '.pending';

function text(value) {
  return typeof value === 'string' && value ? value : undefined;
}

function correlation(event) {
  const out = {};
  for (const key of ['session_id', 'turn_id', 'agent_id', 'tool_use_id']) {
    const value = text(event && event[key]);
    out[key] = value === undefined ? null : value;
  }
  return out;
}

// Harness provenance supplied by an adapter. Only known scalar keys are copied,
// so a harness can never smuggle arguments, prompts, or results into a record
// through this channel.
const SOURCE_KEYS = ['harness', 'transport', 'event_type', 'boundary_reason', 'mapping_confidence'];
const SOURCE_NUMBER_KEYS = ['seq', 'event_time_ms', 'turn', 'step'];

function sanitizeSource(source) {
  if (!source || typeof source !== 'object' || Array.isArray(source)) return null;
  const out = {};
  for (const key of SOURCE_KEYS) {
    const value = text(source[key]);
    if (value !== undefined) out[key] = value.slice(0, 120);
  }
  for (const key of SOURCE_NUMBER_KEYS) {
    const value = source[key];
    if (typeof value === 'number' && Number.isFinite(value)) out[key] = value;
  }
  return Object.keys(out).length ? out : null;
}

function pendingFile(auditDir, toolUseId) {
  const hash = crypto.createHash('sha256').update(String(toolUseId)).digest('hex').slice(0, 32);
  return path.join(auditDir, PENDING_DIR, `${hash}.json`);
}

function readPending(auditDir, toolUseId) {
  try {
    return JSON.parse(fs.readFileSync(pendingFile(auditDir, toolUseId), 'utf8'));
  } catch (_error) {
    return null;
  }
}

function writePending(auditDir, toolUseId, resources) {
  try {
    const dir = path.join(auditDir, PENDING_DIR);
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(pendingFile(auditDir, toolUseId), `${JSON.stringify({ tool_use_id: toolUseId, resources })}\n`, { encoding: 'utf8', flag: 'w' });
  } catch (_error) {
    // Fail-open: without a fingerprint the later effect is UNKNOWN.
  }
}

function clearPending(auditDir, toolUseId) {
  try {
    fs.unlinkSync(pendingFile(auditDir, toolUseId));
  } catch (_error) {
    // Nothing to clear.
  }
}

/**
 * Governance evaluation via the existing personal-core comparator. Actions the
 * comparator cannot represent stay NOT_EVALUATED; that is an activity-visible
 * outcome, not a telemetry failure.
 */
function governance(event) {
  if (!event || event.tool_name !== 'apply_patch') {
    return { status: 'NOT_EVALUATED', reason_code: 'comparator_supports_apply_patch_only' };
  }
  if (event.decision_class !== 'routine') {
    return { status: 'NOT_EVALUATED', reason_code: 'decision_class_not_routine' };
  }
  try {
    const audited = pilot.auditEvent({ event });
    return {
      status: audited.match ? 'MATCH' : 'MISMATCH',
      reason_code: audited.personalCore.reason?.code || 'audit_comparison_completed',
      disposition: audited.personalCore.disposition,
      authoritative_action: audited.authoritative.action,
      comparator: 'personal-core-v0.1',
    };
  } catch (error) {
    const message = String((error && error.message) || 'comparator failed');
    if (/\[out_of_scope\]|\[correlation_error\]|must explicitly be routine/.test(message)) {
      return { status: 'NOT_EVALUATED', reason_code: message.replace(/^\[[a-z_]+\]\s*/, '').slice(0, 120) };
    }
    return { status: 'ERROR', reason_code: 'comparator_error', error_class: message.slice(0, 120) };
  }
}

function buildRecord(event, options) {
  const now = options.now || new Date().toISOString();
  const repoRoot = options.repoRoot || pilot.REPO_ROOT;
  const record = {
    schema: SCHEMA,
    schema_version: SCHEMA_VERSION,
    observed_at: now,
    hook_event_name: HOOK_EVENTS.includes(event && event.hook_event_name) ? event.hook_event_name : null,
    ...correlation(event),
    tool_name: text(event && event.tool_name) || null,
    activity: null,
    technical_outcome: null,
    technical_outcome_source: null,
    error_class: null,
    effect: { observable_effect: 'NOT_APPLICABLE', evidence: 'no_event' },
    lifecycle: { phase: 'UNKNOWN' },
    governance: { status: 'NOT_EVALUATED', reason_code: 'no_event' },
    audit: { status: 'OK', error_class: null },
    source: sanitizeSource(event && event.source),
  };
  if (!record.hook_event_name) {
    record.audit = { status: 'AUDIT_ERROR', error_class: 'unsupported_hook_event' };
    return { record, pending: null };
  }
  if (BOUNDARY_EVENTS.includes(record.hook_event_name)) {
    record.lifecycle = { phase: 'BOUNDARY' };
    record.activity = { category: 'OTHER', category_source: 'turn_boundary_event', operation: 'unknown', resources: [] };
    record.governance = { status: 'NOT_EVALUATED', reason_code: 'turn_boundary_event' };
    record.turn_boundary = { event: record.hook_event_name };
    return { record, pending: null };
  }

  const classified = activity.classify(record.tool_name);
  const toolInput = event && typeof event.tool_input === 'object' ? event.tool_input : undefined;
  const rawResources = activity.extractResources({
    repoRoot,
    toolName: record.tool_name,
    toolInput,
    category: classified.category,
  });
  const isPre = record.hook_event_name === 'PreToolUse';
  const resources = isPre ? activity.withFingerprints(repoRoot, classified.category, rawResources) : rawResources;
  record.activity = {
    category: classified.category,
    category_source: classified.source,
    operation: classified.operation,
    resources,
  };

  if (isPre) {
    record.lifecycle = { phase: 'PRE' };
    record.technical_outcome = 'PRE_OBSERVED';
    record.technical_outcome_source = 'event_phase';
    record.governance = governance(event);
    const pending = record.tool_use_id ? { toolUseId: record.tool_use_id, resources } : null;
    return { record, pending };
  }

  const prior = record.tool_use_id ? readPending(options.auditDir, record.tool_use_id) : null;
  const probe = activity.technicalOutcome(event);
  record.lifecycle = { phase: 'POST', pre_fingerprint_seen: Boolean(prior) };
  record.technical_outcome = probe.outcome;
  record.technical_outcome_source = probe.source;
  record.error_class = probe.error_class;
  record.effect = activity.observableEffect({
    repoRoot,
    category: classified.category,
    resources,
    before: prior ? prior.resources : null,
  });
  record.governance = governance(event);
  return { record, pending: null, clearPendingFor: record.tool_use_id };
}

function storeRecord(record, auditDir) {
  fs.mkdirSync(auditDir, { recursive: true });
  const stamp = `${new Date().toISOString().replace(/[^0-9A-Za-z]/g, '')}-${process.pid}-${crypto.randomBytes(6).toString('hex')}.json`;
  fs.writeFileSync(path.join(auditDir, stamp), `${JSON.stringify(record)}\n`, { encoding: 'utf8', flag: 'wx' });
}

function auditRecord(event, options = {}) {
  const auditDir = options.auditDir || path.join(options.repoRoot || pilot.REPO_ROOT, 'tools', 'personal-core-audit-pilot', '.audit');
  let built;
  try {
    built = buildRecord(event, { ...options, auditDir });
  } catch (error) {
    return {
      schema: SCHEMA,
      schema_version: SCHEMA_VERSION,
      observed_at: options.now || new Date().toISOString(),
      hook_event_name: text(event && event.hook_event_name) || null,
      ...correlation(event),
      tool_name: text(event && event.tool_name) || null,
      activity: null,
      technical_outcome: null,
      technical_outcome_source: null,
      error_class: null,
      effect: { observable_effect: 'UNKNOWN', evidence: 'audit_error' },
      lifecycle: { phase: 'UNKNOWN' },
      governance: { status: 'NOT_EVALUATED', reason_code: 'audit_error' },
      audit: { status: 'AUDIT_ERROR', error_class: String((error && error.message) || 'build_failure').slice(0, 160) },
      source: sanitizeSource(event && event.source),
    };
  }
  if (built.pending) writePending(auditDir, built.pending.toolUseId, built.pending.resources);
  if (built.clearPendingFor) clearPending(auditDir, built.clearPendingFor);
  return built.record;
}

function recordEvent(event, options = {}) {
  const record = auditRecord(event, options);
  const auditDir = options.auditDir || path.join(options.repoRoot || pilot.REPO_ROOT, 'tools', 'personal-core-audit-pilot', '.audit');
  try {
    storeRecord(record, auditDir);
  } catch (error) {
    process.stderr.write(`personal-core audit storage failed: ${error.message}\n`);
  }
  return record;
}

function main() {
  let event;
  try {
    event = JSON.parse(fs.readFileSync(0, 'utf8'));
  } catch (error) {
    event = { hook_event_name: null, tool_name: null, parse_error: error.message };
  }
  try {
    recordEvent(event, { repoRoot: pilot.REPO_ROOT, auditDir: process.env.BEE_PC_AUDIT_DIR || undefined });
  } catch (error) {
    process.stderr.write(`personal-core audit failed: ${error.message}\n`);
  }
  // Observational only: no stdout, no control fields, exit 0.
}

if (require.main === module) main();

module.exports = {
  SCHEMA,
  SCHEMA_VERSION,
  HOOK_EVENTS,
  BOUNDARY_EVENTS,
  auditRecord,
  recordEvent,
  governance,
  sanitizeSource,
};
