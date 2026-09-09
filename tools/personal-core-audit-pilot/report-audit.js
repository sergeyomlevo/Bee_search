'use strict';

const fs = require('node:fs');
const path = require('node:path');

const auditDir = path.resolve(process.argv[2] || path.join(__dirname, '.audit'));
function load() {
  if (!fs.existsSync(auditDir)) return [];
  return fs.readdirSync(auditDir).filter((name) => name.endsWith('.json')).map((name) => {
    try { return JSON.parse(fs.readFileSync(path.join(auditDir, name), 'utf8')); } catch (error) { return { result: 'ERROR', reason: `invalid record ${name}: ${error.message}`, observed_at: '', tool_use_id: '' }; }
  });
}
const rows = load().sort((a, b) => String(a.observed_at).localeCompare(String(b.observed_at)) || String(a.tool_use_id).localeCompare(String(b.tool_use_id)));
console.log('Observed at\tEvent\tOperation\tResource\tResult\tReason\tTool use');
for (const row of rows) console.log([row.observed_at || '', row.hook_event_name || '', row.normalized?.operation || '', row.normalized?.resource || '', row.result || 'ERROR', row.reason || '', row.tool_use_id || ''].join('\t'));
const counts = Object.fromEntries(['MATCH', 'MISMATCH', 'ERROR', 'OUT_OF_SCOPE'].map((key) => [key, rows.filter((row) => row.result === key).length]));
console.log(`Counts\tMATCH=${counts.MATCH}\tMISMATCH=${counts.MISMATCH}\tERROR=${counts.ERROR}\tOUT_OF_SCOPE=${counts.OUT_OF_SCOPE}\tTOTAL=${rows.length}`);
