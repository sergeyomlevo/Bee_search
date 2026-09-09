'use strict';

// Minimal runtime-neutral subset adapted from O_wiki Personal Core v0.1
// (tag personal-core-v0.1, commit a84eabd7ddd45d3cb3e6fc36523e81f99339ca1b).
// This module is an audit comparator; it does not make execution decisions.

const DISPOSITIONS = ['ALLOW', 'ASK', 'DENY'];
const OPERATIONS = ['create', 'modify'];

function loadPolicy(rep) {
  if (!rep || typeof rep !== 'object') throw new Error('[load_error] policy snapshot is missing');
  if (!rep.resources || typeof rep.resources !== 'object' || Object.keys(rep.resources).length === 0) {
    throw new Error('[load_error] policy snapshot has no resources');
  }
  if (!rep.rules || typeof rep.rules !== 'object' || Object.keys(rep.rules).length === 0) {
    throw new Error('[load_error] policy snapshot has no rules');
  }
  const resources = {};
  for (const [name, def] of Object.entries(rep.resources)) {
    if (!def || def.type !== 'file' || !def.match || def.match.kind !== 'exact' || typeof def.match.value !== 'string' || !def.match.value) {
      throw new Error(`[load_error] malformed resource "${name}"`);
    }
    resources[name] = { type: 'file', match: { kind: 'exact', value: def.match.value } };
  }
  const rules = [];
  for (const [id, rule] of Object.entries(rep.rules)) {
    if (!rule || !resources[rule.resource_ref] || !Array.isArray(rule.operations) || rule.operations.length === 0 || rule.operations.some((operation) => !OPERATIONS.includes(operation)) || !DISPOSITIONS.includes(rule.disposition) || !rule.reason || typeof rule.reason.code !== 'string') {
      throw new Error(`[load_error] malformed rule "${id}"`);
    }
    rules.push({ operations: [...rule.operations], resource: resources[rule.resource_ref], disposition: rule.disposition, reason: { code: rule.reason.code, summary: rule.reason.summary || '' }, policy_ref: `policy#rules.${id}` });
  }
  return Object.freeze({ rules: Object.freeze(rules) });
}

function evaluate(action, snapshot) {
  if (!snapshot || !Array.isArray(snapshot.rules)) throw new Error('[load_error] invalid policy snapshot');
  const applicable = snapshot.rules.filter((rule) => rule.operations.includes(action.operation.kind) && rule.resource.type === action.resource.type && rule.resource.match.value === action.resource.id);
  if (applicable.length === 0) return { disposition: 'ALLOW', reason: { code: 'no_applicable_rule', summary: 'No applicable governance restriction in the pilot snapshot.' }, provenance: { policy_refs: [] } };
  const dispositions = new Set(applicable.map((r) => r.disposition));
  if (dispositions.size > 1) return { disposition: 'DENY', reason: { code: 'policy_conflict', summary: 'Applicable rules conflict; fail-safe DENY.' }, provenance: { policy_refs: applicable.map((r) => r.policy_ref) } };
  return { disposition: applicable[0].disposition, reason: applicable[0].reason, provenance: { policy_refs: applicable.map((r) => r.policy_ref) } };
}

function receiveEvidence(application, evidence) {
  if (
    !application ||
    !evidence ||
    typeof application.application_id !== 'string' ||
    application.application_id.length === 0 ||
    typeof evidence.application_id !== 'string' ||
    evidence.application_id !== application.application_id
  ) {
    throw new Error('[correlation_error] evidence application_id does not match GuidanceApplication');
  }
  return { accepted: true, application_id: application.application_id, evidence_type: evidence.evidence_type };
}

module.exports = { loadPolicy, evaluate, receiveEvidence };
