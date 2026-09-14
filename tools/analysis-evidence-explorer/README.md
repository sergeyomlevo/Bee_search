# Analysis Evidence Explorer

Offline, read-only developer tool for Bee Search logical backup v1. It validates
the ZIP and its evidence-critical graph, then writes deterministic
`evidence.json` only after validation succeeds.

```text
python explorer.py backup.zip output-directory
```

The output directory must not already contain `evidence.json`. The tool uses
only Python 3.11 standard-library modules and never contacts a device, network,
Room, or DataStore. It intentionally has no Markdown/CSV rendering, distance,
geometry, grouping, confidence, or ground-truth logic.

The reader supports only `backupFormatVersion=1`, `archiveSchemaVersion=1`,
and `profile=COMPLETE_BACKUP`. It validates all seven required collections,
including settings integrity, before publishing output. `sourceArchiveSha256`
identifies the exact ZIP bytes. `logicalContentSha256` mirrors the production
logical digest over collection names plus their exact payload bytes.

Duration evidence uses only `ELIGIBLE`, `EXCLUDED_BY_D058`, and
`NO_DURATION_OPEN`. D058 is applied literally at the 60,000 ms boundary; the
diagnostic `D058_APPLIED_TO_NON_GROUP_FIRST_CYCLE` reports provenance facts but
does not alter eligibility. Observer contact data is validated but deliberately
omitted from `evidence.json`.

Output ordering is an output-contract choice: points are ordered by
`observationYear, territoryId, observerId, pointNumber, createdAt, id`; bees by
`createdAt, id`; cycles by `sequenceNumber, id`. Context collections are sorted
by UUID.
