# Analysis Evidence Explorer

Offline, read-only developer tool for Bee Search logical backup v1. It accepts
the standard `COMPLETE_BACKUP` ZIP and publishes one canonical machine-readable
artifact only after validation succeeds:

```text
python explorer.py backup.zip output-directory
```

The output is `evidence.json`. Future Markdown and CSV outputs are renderings of
this canonical model; they must not introduce new evidence semantics. The tool
uses Python 3.11 standard-library modules only and never contacts a device,
network, Room, or DataStore.

## Canonical result v1

The top level contains `resultSchemaVersion`, `explorerVersion`,
`ruleSetVersion`, `appliedRules`, `provenance`, `contexts`, `counts`, and
`observationPoints`. The hierarchy is ObservationPoint → Bee → FlightCycle;
Territory and Observer are context. UUID is canonical identity. Numeric fields
preserve numeric value semantics; the lexical form used in the input JSON is
not part of the result contract.

`durationEvidenceStatus` is mutually exclusive and exhaustive for every valid
FlightCycle:

- `ELIGIBLE` — a completed duration not excluded by D058;
- `EXCLUDED_BY_D058` — sequence 1, completed, and shorter than 60,000 ms;
- `NO_DURATION_OPEN` — an open cycle, for which `durationMs` is null.

Invalid input fails closed instead of becoming another status. `appliedRules`
identifies the deterministic rule set actually applied; v1 contains only D058
and its 60,000 ms threshold. D067 is not an applied eligibility rule.

Per-cycle `diagnosticCodes` is a sorted array of non-error observations. The
code `D058_APPLIED_TO_NON_GROUP_FIRST_CYCLE` does not change eligibility, assert
a defect, or add biological interpretation. No top-level diagnostic summary is
stored.

Bee counts obey:

```text
totalCycles = eligibleDurations + excludedByD058 + openCycles
completedCycles = eligibleDurations + excludedByD058
```

The reader supports only backup format 1, archive schema 1, and profile
`COMPLETE_BACKUP`. It verifies all seven required collections, including their
integrity metadata and defensive evidence-critical invariants, before writing
output. Production `BackupService` remains the authoritative producer and
domain validator. Explorer validation is deliberately not guaranteed to
duplicate the complete production restore validation set; Explorer acceptance
must not be treated as proof that production restore would accept the archive.

## Determinism and versions

Canonical serialization is UTF-8 without BOM, compact JSON with sorted object
keys, `ensure_ascii=False`, `allow_nan=False`, LF, and one final newline. Arrays
have explicit deterministic ordering: points by
`observationYear, territoryId, observerId, pointNumber, createdAt, id`; bees by
`createdAt, id`; cycles by `sequenceNumber, id`; contexts and collection
metadata by stable identity/name. JSON object key order is not semantic, but
canonical bytes for an Explorer version are defined by this serializer.

No current time, input filename/path, hostname, locale-dependent value, or
execution duration enters the result. `sourceArchiveSha256` identifies the ZIP
bytes; `logicalContentSha256` mirrors the production logical digest.

- `resultSchemaVersion` tracks machine-consumer compatibility;
- `explorerVersion` identifies the tool implementation;
- `ruleSetVersion` identifies evidence-rule semantics.

Rename/removal of fields, field type or hierarchy changes, status value
changes, or changed field semantics require a `resultSchemaVersion` bump.
Bug fixes that restore documented semantics, stricter pre-result validation,
README clarifications, and renderer-only changes do not.

`testdata/golden_evidence_v1.json` freezes the complete canonical bytes. A
golden change requires an explained semantic reason and an explicit check of
whether `resultSchemaVersion` must change; ordinary test runs never regenerate
it automatically.

The Explorer intentionally has no Markdown/CSV yet, distance or aggregation
estimator, clustering, confidence/probability, geometry, ground truth, or
biological inference.
