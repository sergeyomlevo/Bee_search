# Snapshot V2 — canonical research dates

Temporal I3 owner execution contract, 2026-10-08. V1 remains frozen and readable
under [its exact wire schema](snapshot-v1-wire-schema.md). V2 is a new version,
not an optional-key extension of V1.

V2 retains V1's exact 17-entry inventory, manifest fields, identity, evidence
profiles, limits, restricted canonical JSON, ordering, digests, media references,
coverage, graph validation and publication model. The manifest's
`snapshotFormatVersion` is **2**. Readers support versions 1 and 2 and select
the closed record schema from the validated manifest version.

Only two domain inventories change:

- `data/observation-points.jsonl`: all V1 fields plus REQUIRED `observationDate`,
  a JSON string in exact `YYYY-MM-DD` form representing a valid ISO calendar date.
- `data/physical-objects.jsonl`: all V1 fields plus REQUIRED `fixationDate`,
  either a valid `YYYY-MM-DD` string or explicit JSON null.

Missing date keys, invalid calendar dates, noncanonical spelling, wrong JSON
types, duplicate or unknown keys fail closed. The nullable fixationDate key
must be present. Dates are canonical persisted values; V2 never derives them
from createdAt or interprets them through the reader's timezone. Other graph
invariants are unchanged; legacy numbering is not repaired by archive readers.

V1 rejects these additional keys. Its ObservationPoint materialization alone
reconstructs the date from persisted createdAt using the approved legacy local
calendar convention; its PhysicalObject materialization yields NULL. This
one-time legacy compatibility rule never applies to V2.

Android and the independent PC verifier must validate both versions. V1 golden
vectors stay unchanged; V2 date conformance tests are separate. New snapshots
use V2. Latest-valid selection and metadata-only/FULL evidence semantics do not
change. I3 does not add snapshot restore or change media protection.

I2 + I3 + I4 form one deployment unit. I4 export carriage and the pending
isolated Room/device verification gate must close before Samsung deployment.
