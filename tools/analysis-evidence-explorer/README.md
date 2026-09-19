# Analysis Evidence Explorer

Offline, read-only developer tool for Bee Search logical backup v1. It accepts
the standard `COMPLETE_BACKUP` ZIP and publishes the complete output set only
after validation and rendering succeed:

```text
python explorer.py backup.zip output-directory
```

```text
output-directory/
  evidence.json    canonical machine-readable artifact
  evidence.md      human-readable report
  points.csv       one row per ObservationPoint
  bees.csv         one row per Bee
  cycles.csv       one row per FlightCycle
```

`evidence.json` is the only canonical artifact. `evidence.md` and the three CSV
files are derived presentations of the same immutable result object produced by
a single `build_evidence` call; they must not introduce new evidence semantics,
recompute eligibility or diagnostics, or become a second source of truth. The
tool uses Python 3.11 standard-library modules only and never contacts a device,
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

- `ELIGIBLE` — every completed duration; retired D058 no longer excludes short
  first cycles;
- `EXCLUDED_BY_D058` — retained only as a compatibility status and never
  emitted by rule set 2;
- `NO_DURATION_OPEN` — an open cycle, for which `durationMs` is null.

Invalid input fails closed instead of becoming another status. `appliedRules`
identifies the deterministic rule set actually applied; rule set 2 has no
active duration eligibility rules. D058 fields remain raw provenance, but D058
is not applied or diagnosed by this Explorer version.

Per-cycle `diagnosticCodes` is a sorted array of non-error observations and is
empty for the retired D058 case. No top-level diagnostic summary is stored.
The values of `diagnosticCodes` are stable machine identifiers:
consumers may branch on them. A code must not be renamed or redefined without a
contract and version review. Adding a code is allowed only as a new diagnostic
condition and must not change existing eligibility semantics without a rule set
or version decision.

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

## Mark position compatibility

Bee marks were renamed from a wing vocabulary to a thorax/abdomen vocabulary.
Both vocabularies denote the same physical positions, so an archive written by
either application version is readable. The stored token is canonicalised before
any semantic use:

| Stored token | Canonical position |
|---|---|
| `THORAX` | `THORAX` |
| `NONE` | `THORAX` |
| `ABDOMEN` | `ABDOMEN` |
| `RIGHT_WING` | `ABDOMEN` |
| `LEFT_WING` | `LEFT_WING` |

Any other value fails closed, including a differently cased spelling. The
duplicate-mark invariant is evaluated on the canonical position, so an archive
that holds both spellings of one position for one color on a single
ObservationPoint is rejected as a duplicate instead of passing as two different
marks. `LEFT_WING` carries no confirmed physical meaning, so it stays a separate
legacy value: it is never renamed to `THORAX` or `ABDOMEN`, and it does not block
a real thorax mark of the same color.

Canonicalisation is used for validation and identity only. The `markPosition`
emitted in `evidence.json`, `bees.csv` and `evidence.md` still mirrors the token
stored in the archive, so a result built from an archive written by the previous
application version is byte-identical to before and no version identifier or
canonical field had to change.

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

Adding a canonical field is never a silent extension of frozen v1. A
consumer-visible addition requires an `explorerVersion` bump, and when the
addition changes compatibility or the required interpretation of the existing
machine contract it also requires a `resultSchemaVersion` bump. Adding a field
always changes canonical bytes, so it must be an explicit, reviewed contract
change rather than a side effect of other work.

Renderer capability is deliberately outside the canonical evidence contract:
`explorerVersion` identifies the component that produces `evidence.json`, so
adding or changing `evidence.md` and the CSV renderings does not by itself
change `explorerVersion` or canonical bytes.

`testdata/golden_evidence_v1.json` freezes the complete canonical bytes. A
golden change requires an explained semantic reason and an explicit check of
whether `resultSchemaVersion` must change; ordinary test runs never regenerate
it automatically.

The Explorer intentionally has no distance or aggregation estimator, clustering,
confidence/probability, geometry, ground truth, or biological inference. The
Markdown and CSV renderings preserve the same boundary: they add no analysis of
their own.
