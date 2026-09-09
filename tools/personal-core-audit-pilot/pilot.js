'use strict';

const fs = require('node:fs');
const path = require('node:path');
const pc = require('./personal-core');

const PILOT_CLASS = 'routine';
const REPO_ROOT = path.resolve(__dirname, '../..');
const POLICY_PATH = path.join(REPO_ROOT, '.agent', 'decision-policy.yaml');

function readRoutinePolicy(policyPath) {
  if (typeof policyPath !== 'string' || !policyPath) throw new Error('[load_error] authoritative policy path is missing');
  let text;
  try {
    text = fs.readFileSync(policyPath, 'utf8');
  } catch (error) {
    throw new Error(`[load_error] authoritative policy could not be read: ${error.code || error.message}`);
  }
  return parseRoutinePolicy(text, policyPath);
}

function parseRoutinePolicy(text, source = '<memory>') {
  if (typeof text !== 'string' || !text.trim()) throw new Error('[load_error] authoritative policy is empty');
  const classes = text.match(/^decision_classes:\s*\r?\n([\s\S]*?)(?=^[A-Za-z_][A-Za-z0-9_]*:\s*\r?$)/m)?.[1];
  if (!classes) throw new Error('[load_error] decision_classes policy block not found');
  const match = classes.match(/(^  routine:\r?\n)([\s\S]*?)(?=^  [A-Za-z_][A-Za-z0-9_]*:\r?$)/m);
  if (!match) throw new Error('[load_error] routine policy block not found');
  const block = match[2];
  const action = block.match(/^    action:\s*([^\r\n#]+)\s*$/m)?.[1]?.trim();
  const report = block.match(/^    report:\s*([^\r\n#]+)\s*$/m)?.[1]?.trim();
  if (action !== 'decide_and_continue' || report !== 'only_if_relevant') throw new Error('[load_error] routine policy block has unsupported shape');
  return { decisionClass: PILOT_CLASS, action, report, source: path.normalize(source) };
}

function canonicalPath(repoRoot, input) {
  if (typeof input !== 'string' || !input.trim()) throw new Error('[out_of_scope] empty resource path');
  if (typeof repoRoot !== 'string' || !repoRoot.trim()) throw new Error('[load_error] repository root is missing');
  const windowsInput = input.trim().replaceAll('/', '\\');
  if (/^[A-Za-z]:[^\\]/.test(windowsInput)) throw new Error('[out_of_scope] drive-relative resource paths are unsupported');
  const root = path.win32.resolve(repoRoot.replaceAll('/', '\\'));
  const resolved = path.win32.resolve(root, windowsInput);
  const relative = path.win32.relative(root, resolved);
  if (!relative) throw new Error('[out_of_scope] resource path resolves to repository root');
  if (relative === '..' || relative.startsWith(`..${path.win32.sep}`) || path.win32.isAbsolute(relative)) {
    throw new Error('[out_of_scope] resource is outside repository root');
  }
  return relative.replaceAll('\\', '/').toLowerCase();
}

function extractApplyPatchResource(patch) {
  if (typeof patch !== 'string') throw new Error('[out_of_scope] apply_patch payload missing');
  const headers = [...patch.matchAll(/^\*\*\* (Add|Update|Delete) File:\s*(.+?)\s*$/gm)];
  if (headers.length !== 1) throw new Error('[out_of_scope] only one Add/Update File resource is supported');
  if (headers[0][1] === 'Delete') throw new Error('[out_of_scope] Delete File is outside pilot scope');
  return { kind: headers[0][1] === 'Add' ? 'create' : 'modify', path: headers[0][2] };
}

function normalizeEvent(event, repoRoot) {
  if (!event || (event.hook_event_name !== 'PreToolUse' && event.hook_event_name !== 'PostToolUse')) throw new Error('[out_of_scope] hook_event_name must be PreToolUse or PostToolUse');
  if (event.tool_name !== 'apply_patch') throw new Error('[out_of_scope] only apply_patch is supported');
  for (const field of ['session_id', 'turn_id', 'tool_use_id']) {
    if (typeof event[field] !== 'string' || !event[field]) throw new Error(`[correlation_error] ${field} is required`);
  }
  const input = event.tool_input?.command;
  const resource = extractApplyPatchResource(input);
  return { decisionClass: event.decision_class, operation: { kind: resource.kind }, resource: { type: 'file', id: canonicalPath(repoRoot, resource.path) }, correlation: { session_id: event.session_id, turn_id: event.turn_id, tool_use_id: event.tool_use_id } };
}

function auditEvent({ event }) {
  if (event?.decision_class !== PILOT_CLASS) return { status: 'out_of_scope', reason: 'decision_class must explicitly be routine' };
  const authoritative = readRoutinePolicy(POLICY_PATH);
  const normalized = normalizeEvent(event, REPO_ROOT);
  const application = { application_id: `${event.session_id}:${event.turn_id}:${event.tool_use_id}` };
  pc.receiveEvidence(application, { application_id: application.application_id, evidence_type: event.hook_event_name });
  const snapshot = pc.loadPolicy({ resources: { target: { type: 'file', match: { kind: 'exact', value: normalized.resource.id } } }, rules: { routine: { resource_ref: 'target', operations: [normalized.operation.kind], disposition: 'ALLOW', reason: { code: 'bee_search_routine_decide_and_continue', summary: authoritative.action } } } });
  const personalCore = pc.evaluate(normalized, snapshot);
  const match = personalCore.disposition === 'ALLOW' && authoritative.action === 'decide_and_continue';
  return { status: 'audited', case: normalized, authoritative: { decision_class: PILOT_CLASS, action: authoritative.action, report: authoritative.report, source: authoritative.source }, personalCore, match };
}

module.exports = { PILOT_CLASS, REPO_ROOT, POLICY_PATH, parseRoutinePolicy, readRoutinePolicy, canonicalPath, extractApplyPatchResource, normalizeEvent, auditEvent };
