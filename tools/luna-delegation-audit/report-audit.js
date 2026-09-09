'use strict';

const fs = require('fs');
const path = require('path');

const directory = path.resolve(process.argv[2] || path.join(__dirname, '.audit'));
const rows = [];
if (fs.existsSync(directory) && fs.statSync(directory).isDirectory()) {
  for (const name of fs.readdirSync(directory)) {
    if (!name.endsWith('.json')) continue;
    try {
      const value = JSON.parse(fs.readFileSync(path.join(directory, name), 'utf8'));
      if (value && typeof value === 'object') rows.push(value);
    } catch (_error) {
      // Ignore incomplete/unrelated files: this is a human-readable report.
    }
  }
}
rows.sort((a, b) => String(a.observed_at || '').localeCompare(String(b.observed_at || '')));

const counts = { SubagentStart: 0, SubagentStop: 0, ERROR: 0 };
for (const row of rows) {
  if (row.result === 'ERROR') counts.ERROR += 1;
  else if (row.hook_event_name in counts) counts[row.hook_event_name] += 1;
}

console.log('Luna delegation audit (audit-only)');
console.log(`Directory: ${directory}`);
console.log(`Counts: start=${counts.SubagentStart} stop=${counts.SubagentStop} error=${counts.ERROR}`);
console.log('observed_at | hook_event_name | session_id | turn_id | agent_id | agent_type | model');
for (const row of rows) {
  console.log([
    row.observed_at || '',
    row.result === 'ERROR' ? 'ERROR' : (row.hook_event_name || ''),
    row.session_id || '', row.turn_id || '', row.agent_id || '', row.agent_type || '', row.model || ''
  ].join(' | '));
}
