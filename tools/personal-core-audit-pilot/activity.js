'use strict';

// Activity observation helpers for the audit-only pilot.
//
// This module answers one question only: "what did the agent observably do with
// tools?". It never decides whether that work was correct, complete, or
// compliant, and it never stores tool arguments, commands, file contents,
// patch bodies, or tool responses.

const fs = require('node:fs');
const path = require('node:path');

const CATEGORIES = Object.freeze(['READ', 'MUTATION', 'COMMAND', 'OTHER']);
const MAX_RESOURCES = 20;
const MAX_RESOURCE_LENGTH = 512;

// Tool names verified inside this repository:
// - Codex sessions: `apply_patch` (present in historical v1 audit records).
// - DSH harness packages (read from tool packages): read, read_image, write,
//   edit, glob, grep, pwsh, bash, str_replace_editor, todo_write, skill,
//   web_fetch, web_search, create_goal, get_goal, update_goal, job_list,
//   job_output, job_kill.
// Anything else falls back to conservative heuristics or OTHER.
const TOOL_TABLE = Object.freeze({
  apply_patch: { category: 'MUTATION', operation: 'unknown' },
  write: { category: 'MUTATION', operation: 'create_or_modify' },
  edit: { category: 'MUTATION', operation: 'modify' },
  str_replace_editor: { category: 'MUTATION', operation: 'modify' },
  read: { category: 'READ', operation: 'unknown' },
  read_image: { category: 'READ', operation: 'unknown' },
  glob: { category: 'READ', operation: 'unknown' },
  grep: { category: 'READ', operation: 'unknown' },
  web_fetch: { category: 'READ', operation: 'unknown' },
  web_search: { category: 'READ', operation: 'unknown' },
  job_list: { category: 'READ', operation: 'unknown' },
  job_output: { category: 'READ', operation: 'unknown' },
  get_goal: { category: 'READ', operation: 'unknown' },
  pwsh: { category: 'COMMAND', operation: 'unknown' },
  bash: { category: 'COMMAND', operation: 'unknown' },
});

const RESOURCE_KEYS = Object.freeze([
  'file_path', 'filePath', 'path', 'file', 'target', 'resource', 'destination', 'source',
]);

function text(value) {
  return typeof value === 'string' && value ? value : undefined;
}

function classify(toolName) {
  const name = text(toolName);
  if (!name) return { category: 'OTHER', operation: 'unknown', source: 'unknown_tool' };
  const exact = TOOL_TABLE[name.toLowerCase()];
  if (exact) return { category: exact.category, operation: exact.operation, source: 'tool_name_table' };
  const lower = name.toLowerCase();
  if (/(shell|exec|command|terminal|pwsh|bash)/.test(lower)) return { category: 'COMMAND', operation: 'unknown', source: 'name_heuristic' };
  if (/(patch|write|edit|replace|create_file|apply)/.test(lower)) return { category: 'MUTATION', operation: 'unknown', source: 'name_heuristic' };
  if (/(read|get|list|search|fetch|glob|grep)/.test(lower)) return { category: 'READ', operation: 'unknown', source: 'name_heuristic' };
  return { category: 'OTHER', operation: 'unknown', source: 'name_heuristic' };
}

/**
 * Repository-relative POSIX path that preserves case. Unlike the comparator's
 * identity path (lowercased), this keeps the real case so case-sensitive
 * filesystems keep their identity.
 */
function relativeResource(repoRoot, input) {
  if (typeof input !== 'string' || !input.trim()) return undefined;
  const raw = input.trim().replaceAll('"', '');
  if (raw.length > MAX_RESOURCE_LENGTH) return undefined;
  const root = path.resolve(repoRoot);
  const resolved = path.resolve(root, raw.replaceAll('/', path.sep));
  const relative = path.relative(root, resolved);
  if (!relative || relative === '..' || relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative)) return undefined;
  return relative.replaceAll(path.sep, '/');
}

function applyPatchResources(command) {
  if (typeof command !== 'string') return [];
  const found = [];
  const re = /^\*\*\* (Add|Update|Delete) File:\s*(.+?)\s*$/gm;
  let match;
  while ((match = re.exec(command)) !== null) {
    const kind = match[1] === 'Add' ? 'create' : (match[1] === 'Update' ? 'modify' : 'delete');
    found.push({ raw: match[2], kind });
  }
  return found;
}

function genericResources(toolInput) {
  if (!toolInput || typeof toolInput !== 'object' || Array.isArray(toolInput)) return [];
  const found = [];
  for (const key of RESOURCE_KEYS) {
    const value = toolInput[key];
    if (typeof value === 'string') found.push({ raw: value, kind: 'unknown' });
    else if (Array.isArray(value)) {
      for (const item of value) if (typeof item === 'string') found.push({ raw: item, kind: 'unknown' });
    }
  }
  if (Array.isArray(toolInput.files)) {
    for (const item of toolInput.files) {
      if (typeof item === 'string') found.push({ raw: item, kind: 'unknown' });
      else if (item && typeof item === 'object' && typeof item.path === 'string') found.push({ raw: item.path, kind: 'unknown' });
    }
  }
  return found;
}

function fingerprint(repoRoot, relativePath) {
  try {
    const stat = fs.statSync(path.resolve(repoRoot, relativePath));
    return { existed_before: true, size_before: stat.size, mtime_ms_before: Math.round(stat.mtimeMs) };
  } catch (_error) {
    return { existed_before: false, size_before: null, mtime_ms_before: null };
  }
}

/** Resources for one event, without persisting any argument text. */
function extractResources({ repoRoot, toolName, toolInput, category }) {
  if (category === 'COMMAND') return [];
  const raw = toolName && toolName.toLowerCase() === 'apply_patch'
    ? applyPatchResources(toolInput && toolInput.command)
    : genericResources(toolInput);
  const seen = new Set();
  const out = [];
  for (const candidate of raw) {
    const relative = relativeResource(repoRoot, candidate.raw);
    if (!relative || seen.has(relative)) continue;
    seen.add(relative);
    out.push({ path: relative, operation: candidate.kind });
    if (out.length >= MAX_RESOURCES) break;
  }
  return out;
}

function withFingerprints(repoRoot, category, resources) {
  if (category !== 'MUTATION') return resources;
  return resources.map((resource) => ({ ...resource, ...fingerprint(repoRoot, resource.path) }));
}

/**
 * Post-event outcome probe. Field names are probed defensively; the mapping is
 * a hypothesis about the hook payload and is recorded as such
 * (`technical_outcome_source: "field_probe"`).
 */
function technicalOutcome(event) {
  const response = event && typeof event.tool_response === 'object' && event.tool_response ? event.tool_response : undefined;
  if (response && response.is_error === true) return { outcome: 'FAILED', error_class: 'error_field', source: 'field_probe' };
  if (response && response.error) return { outcome: 'FAILED', error_class: 'error_field', source: 'field_probe' };
  if (response && response.success === false) return { outcome: 'FAILED', error_class: 'error_field', source: 'field_probe' };
  const exitCode = event ? (event.exit_code ?? event.exit_status ?? (response ? response.exit_code : undefined)) : undefined;
  if (typeof exitCode === 'number' && exitCode !== 0) return { outcome: 'FAILED', error_class: 'nonzero_exit', source: 'field_probe' };
  const status = text(event && event.status) || text(response && response.status);
  if (status && /^(error|failed|failure|timeout|cancelled|canceled|aborted)$/i.test(status)) {
    return { outcome: 'FAILED', error_class: `status_${status.toLowerCase()}`, source: 'field_probe' };
  }
  if (event && event.error) return { outcome: 'FAILED', error_class: 'error_field', source: 'field_probe' };
  return { outcome: 'COMPLETED', error_class: null, source: 'field_probe' };
}

/**
 * Observable effect for a Post event. PostToolUse alone is never evidence of a
 * change: only a pre/post fingerprint difference on the same resource counts.
 */
function observableEffect({ repoRoot, category, resources, before }) {
  if (category !== 'MUTATION') return { observable_effect: 'NOT_APPLICABLE', evidence: 'category_is_not_mutation' };
  if (!resources.length) return { observable_effect: 'UNKNOWN', evidence: 'no_file_resources_observed' };
  if (!Array.isArray(before) || before.length === 0) return { observable_effect: 'UNKNOWN', evidence: 'no_pre_fingerprint' };
  let changed = 0;
  let unchanged = 0;
  let unknown = 0;
  for (const resource of resources) {
    const prior = before.find((item) => item.path === resource.path);
    if (!prior) { unknown += 1; continue; }
    const now = currentStat(repoRoot, resource.path);
    if (!now.exists) { unknown += 1; continue; }
    if (!prior.existed_before) changed += 1;
    else if (prior.size_before !== now.size || prior.mtime_ms_before !== now.mtime_ms) changed += 1;
    else unchanged += 1;
  }
  if (changed > 0) {
    return { observable_effect: 'OBSERVED_CHANGE', evidence: `changed_resources=${changed},unknown_resources=${unknown}` };
  }
  if (unchanged > 0 && unknown === 0) {
    return { observable_effect: 'NO_OBSERVED_CHANGE', evidence: `unchanged_resources=${unchanged}` };
  }
  return { observable_effect: 'UNKNOWN', evidence: `unchanged=${unchanged},unknown=${unknown}` };
}

function currentStat(repoRoot, relativePath) {
  try {
    const stat = fs.statSync(path.resolve(repoRoot, relativePath));
    return { exists: true, size: stat.size, mtime_ms: Math.round(stat.mtimeMs) };
  } catch (_error) {
    return { exists: false };
  }
}

module.exports = {
  CATEGORIES,
  TOOL_TABLE,
  classify,
  extractResources,
  withFingerprints,
  relativeResource,
  technicalOutcome,
  observableEffect,
};
