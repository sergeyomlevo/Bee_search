'use strict';

// Read-only aggregator for the audit-only pilot. It never writes and never
// evaluates behaviour: it only counts what the observer actually recorded.

const fs = require('node:fs');
const path = require('node:path');

const DEFAULT_DIRECTORY = path.join(__dirname, '.audit');
const V2 = 'bee-search.personal-core-audit.v2';
const NOT_OBSERVABLE = 'NOT OBSERVABLE (no turn-boundary events recorded)';

function loadRows(dir) {
  if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) return [];
  const rows = [];
  for (const name of fs.readdirSync(dir)) {
    if (!name.endsWith('.json')) continue;
    try {
      const value = JSON.parse(fs.readFileSync(path.join(dir, name), 'utf8'));
      if (value && typeof value === 'object') rows.push(value);
    } catch (_error) {
      rows.push({ schema: 'unparseable', audit: { status: 'AUDIT_ERROR', error_class: `invalid_record ${name}` } });
    }
  }
  return rows.sort((a, b) => String(a.observed_at || '').localeCompare(String(b.observed_at || '')));
}

function distinct(rowsIn, pick) {
  const set = new Set();
  for (const row of rowsIn) { const value = pick(row); if (value) set.add(value); }
  return set;
}

function countBy(rowsIn, pick) {
  const map = new Map();
  for (const row of rowsIn) { const key = pick(row) || 'UNKNOWN'; map.set(key, (map.get(key) || 0) + 1); }
  return map;
}

function countLines(title, map, order) {
  const keys = order ? order.filter((key) => map.has(key)).concat([...map.keys()].filter((key) => !order.includes(key))) : [...map.keys()].sort();
  return keys.map((key) => `${title}\t${key}=${map.get(key)}`);
}

/** Aggregates observed telemetry into a human-readable report string. */
function renderReport(directory = DEFAULT_DIRECTORY) {
  const rows = loadRows(directory);
  const v2 = rows.filter((row) => row.schema === V2 || row.schema_version === 2);
  const legacy = rows.filter((row) => row.schema !== V2 && row.schema_version !== 2);
  const toolRows = v2.filter((row) => row.lifecycle && (row.lifecycle.phase === 'PRE' || row.lifecycle.phase === 'POST'));
  const boundaryRows = v2.filter((row) => row.turn_boundary);
  const preRows = toolRows.filter((row) => row.lifecycle.phase === 'PRE');
  const postRows = toolRows.filter((row) => row.lifecycle.phase === 'POST');

  const sessions = distinct(toolRows.concat(boundaryRows), (row) => row.session_id);
  const activityTurns = distinct(toolRows, (row) => row.turn_id);
  const boundaryTurns = distinct(boundaryRows, (row) => row.turn_id);
  const interruptedTurns = distinct(boundaryRows.filter((row) => row.turn_boundary.event === 'Interrupt'), (row) => row.turn_id);
  const noActivityTurns = [...boundaryTurns].filter((turn) => !activityTurns.has(turn));
  const unknownBoundaryTurns = [...activityTurns].filter((turn) => !boundaryTurns.has(turn));

  const byToolUse = new Map();
  for (const row of toolRows) {
    const id = row.tool_use_id || `no-id:${row.observed_at}`;
    const entry = byToolUse.get(id) || { pre: 0, post: 0 };
    if (row.lifecycle.phase === 'PRE') entry.pre += 1; else entry.post += 1;
    byToolUse.set(id, entry);
  }
  const entries = [...byToolUse.values()];
  const paired = entries.filter((entry) => entry.pre >= 1 && entry.post >= 1).length;
  const preWithoutPost = entries.filter((entry) => entry.pre >= 1 && entry.post === 0).length;
  const postWithoutPre = entries.filter((entry) => entry.pre === 0 && entry.post >= 1).length;
  const duplicated = entries.filter((entry) => entry.pre > 1 || entry.post > 1).length;

  const completed = postRows.filter((row) => row.technical_outcome === 'COMPLETED').length;
  const failed = postRows.filter((row) => row.technical_outcome === 'FAILED').length;
  const auditErrors = v2.filter((row) => row.audit && row.audit.status === 'AUDIT_ERROR').length;
  const governanceByStatus = countBy(toolRows, (row) => row.governance && row.governance.status);
  const evaluated = (governanceByStatus.get('MATCH') || 0) + (governanceByStatus.get('MISMATCH') || 0);
  const evaluatedShare = toolRows.length ? `${evaluated}/${toolRows.length} observed tool events` : 'no observed tool events';

  // Per-turn accounting needs a turn identity. A harness that logs a numeric turn
  // instead of a turn id cannot be counted that way, and reporting 0 would claim
  // "no turns" about turns that were plainly observed.
  const NUMERIC_TURN = 'NOT OBSERVABLE (records carry a numeric turn, not a turn id)';
  const numericTurnRows = toolRows.concat(boundaryRows)
    .filter((row) => !row.turn_id && row.source && typeof row.source.turn === 'number');
  const turnIdBasis = (rowsIn) => distinct(rowsIn, (row) => row.turn_id).size > 0 || !rowsIn.length;
  const countTurns = (rowsIn, count) => (turnIdBasis(rowsIn) ? count : NUMERIC_TURN);
  const boundaryBasis = boundaryRows.length > 0 && distinct(boundaryRows, (row) => row.turn_id).size > 0;
  const interruptBoundaryRows = boundaryRows.filter((row) => row.turn_boundary.event === 'Interrupt');
  const interruptedLine = boundaryRows.length === 0
    ? NOT_OBSERVABLE
    : (boundaryBasis
      ? interruptedTurns.size
      : `${interruptBoundaryRows.length} (by boundary event; turn ids unavailable)`);

  const lines = [
    'Personal Core audit pilot (audit-only) — activity telemetry',
    `Directory: ${directory}`,
    `Records: total=${rows.length} v2=${v2.length} legacy_v1=${legacy.length}`,
  ];

  if (numericTurnRows.length) {
    const harnesses = [...distinct(numericTurnRows, (row) => row.source && row.source.harness)].join(', ') || 'unknown';
    lines.push(
      `Note: ${numericTurnRows.length} record(s) come from ${harnesses} provenance and log a numeric turn (kept verbatim in source.turn) instead of a turn id.`,
      'Per-turn counters are reported as NOT OBSERVABLE for those stores rather than as 0.',
    );
  }

  if (!v2.length && legacy.length) {
    lines.push(
      'Note: every record in this store is legacy v1, which carries no activity, lifecycle, or effect fields.',
      'The v2 counters below are therefore 0 by construction, not because no tool activity happened.',
      'Legacy Pre/Post counts and verdicts are summarised at the end of this report.',
    );
  }

  lines.push(
    '',
    'Sessions and turns',
    `Sessions\t${sessions.size}`,
    `Observed turns\t${countTurns(toolRows, distinct(toolRows, (row) => row.turn_id).size)}`,
    `Turns with observed tool activity\t${countTurns(toolRows, activityTurns.size)}`,
    `Turns with no observed tool activity\t${boundaryRows.length === 0 ? NOT_OBSERVABLE : (boundaryBasis ? noActivityTurns.length : NUMERIC_TURN)}`,
    `Interrupted turns\t${interruptedLine}`,
    `Unknown-boundary turns\t${unknownBoundaryTurns.length}`,
    'Note: "no observed tool activity" means exactly that; absence of recorded tool events is not evidence that no work happened.',
    'Note: activity and tool calls are counted per record; turn-level counters additionally require a turn identity.',
    '',
    'Tool calls',
    `Pre observed\t${preRows.length}`,
    `Post observed\t${postRows.length}`,
    `Paired\t${paired}`,
    `Completed\t${completed}`,
    `Failed\t${failed}`,
    `Pre without Post\t${preWithoutPost}`,
    `Post without Pre\t${postWithoutPre}`,
    'Note: COMPLETED means the observed tool lifecycle ended; it is not a correctness or compliance verdict.',
    '',
    'Activity (observed tool calls by category, from Pre events)',
    ...countLines('Activity', countBy(preRows.length ? preRows : toolRows, (row) => row.activity && row.activity.category), ['READ', 'MUTATION', 'COMMAND', 'OTHER']),
    '',
    'Effects (Post events)',
    ...countLines('Effect', countBy(postRows, (row) => row.effect && row.effect.observable_effect), ['OBSERVED_CHANGE', 'NO_OBSERVED_CHANGE', 'NOT_APPLICABLE', 'UNKNOWN']),
    'Note: OBSERVED_CHANGE requires a pre/post fingerprint difference on the same resource; a Post event alone never proves a change.',
    '',
    'Governance comparator',
    `Evaluated\t${evaluated}`,
    `Observed tool events\t${toolRows.length}`,
    `Comparator coverage\t${evaluatedShare}`,
    ...countLines('Governance', governanceByStatus, ['MATCH', 'MISMATCH', 'NOT_EVALUATED', 'ERROR']),
    'Note: activity coverage and governance coverage are different measurements; NOT_EVALUATED is a coverage gap, not a failure.',
    '',
    'Lifecycle anomalies',
    `Tool calls with duplicate Pre/Post records\t${duplicated}`,
    `Pre without Post\t${preWithoutPost}`,
    `Post without Pre\t${postWithoutPre}`,
    `Audit errors (observer-side)\t${auditErrors}`,
    'Note: Pre count != Post count is not by itself an anomaly; unpaired calls can come from interrupted or unfinished turns.',
  );

  if (legacy.length) {
    lines.push(
      '',
      'Legacy v1 records (activity fields absent; not rewritten)',
      ...countLines('Legacy hook event', countBy(legacy, (row) => row.hook_event_name), ['PreToolUse', 'PostToolUse']),
      ...countLines('Legacy tool name', countBy(legacy, (row) => row.tool_name)),
      ...countLines('Legacy result', countBy(legacy, (row) => row.result)),
      'Legacy mapping: MATCH/MISMATCH = comparator verdict; OUT_OF_SCOPE = NOT_EVALUATED; ERROR = audit or comparator error.',
      'Note: legacy records cannot be paired into lifecycles and carry no observable-effect evidence; they are counted only as verdicts.',
    );
  }

  return lines.join('\n');
}

if (require.main === module) {
  process.stdout.write(`${renderReport(path.resolve(process.argv[2] || DEFAULT_DIRECTORY))}\n`);
}

module.exports = { renderReport, loadRows, V2, DEFAULT_DIRECTORY };
