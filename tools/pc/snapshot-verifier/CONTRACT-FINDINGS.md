# Contract findings — independent text-only reader

## Current disposition after normative wire-schema alignment

The historical findings below are retained, not rewritten as prior PASS evidence.
Current source: `docs/snapshot-v1-wire-schema.md` (owner-approved normative text).

| Finding | Exact normative sections | Implementation | Coverage | Status |
|---|---|---|---|---|
| CF1 | 1–3, 6–8: all 13 schemas, named FKs/subtypes/lifecycle, closed fields, types/order | RecordSchema, GraphRules, WireRecords; closed manifest descriptors | GraphTest and WireAlignmentTest: full nonempty graph, all-collection schema/order matrices, FK/subtype/settings/weather/Bee negatives | RESOLVED_BY_SCHEMA |
| CF2 | 4, 6: territoryId/encoded, v1/v2 exact grammar, coordinates and ordering | Coverage and WireRecords | Valid v1/v2/antimeridian; invalid rectangle, whitespace, empty v2 bounds/name and ordering | RESOLVED_BY_SCHEMA |
| CF3 | 2 media inventories, 5 reachability, 7 counter | RecordSchema and WireRecords.derive | Known/unknown/zero/negative/unsafe sizes, exact MIME, size/type conflicts, missing/extra set/count | RESOLVED_BY_SCHEMA |

No remaining blocking CONTRACT_FINDING. No Android implementation was consulted
for this alignment. A malformed nonnull SHA remains a schema failure; it is not
silently treated as unknown. This follows section 5 of the normative text.
Real application snapshot comparison remains NOT_RUN: no PC path supplied.

## Historical initial slice findings (superseded text gaps)

Only approved Snapshot contract documents and R0-PC-C canonical materials were
used. These findings do not amend production contracts or diagnose Android code.

## CF1 — domain wire schema incomplete (BLOCKER for nonempty graph)

Sources: `docs/snapshot-2b-contract-preflight.md` sections 2, 4, 6;
`docs/repository-snapshot-v1.md` Wire contract;
`docs/repository-snapshot-v1-final-acceptance.md` section 10.

The texts name 13 collections, stable keys and shared domain codec, but do not
enumerate each record's complete field set, required/null types, enum domains,
fractional field names/ranges or field-level graph foreign keys/lifecycle rules.
The acceptance report explicitly says wire shapes derive from codec/fixtures.
Those implementation sources are excluded by the current independence boundary.

Reasonable interpretations: reuse legacy codec schemas; new Snapshot-specific
schemas preserving the same logical state. Text alone cannot choose/implement them.
The verifier can validate UTF-8/JSON, finite numbers, documented keys/order,
portable selection references and raw byte integrity, but cannot assert all
nonempty records preserve a valid research graph. Nonempty graph therefore emits
`CONTRACT_UNSPECIFIED_DOMAIN_SCHEMA` and nonzero exit, even after structural checks.
Empty collections with null selections are the only independently generatable
fully supported graph subset. This is not full Snapshot V1 acceptance.

## CF2 — map coverage payload schema unspecified (BLOCKER when nonempty)

Sources: preflight sections 2 and 6; Snapshot V1 Wire contract.
`territoryId` sorting/reference is explicit. Payload field(s), geometry encoding,
versions and geometry validation rules are not. Possibilities include structured
geometry versus an encoded string. We do not infer either from application code.
Nonempty coverage is structurally checked but produces
`CONTRACT_UNSPECIFIED_COVERAGE_SCHEMA`; no full PASS for such snapshots.

## CF3 — media reachability/hint field-level schema incomplete (BLOCKER when used)

Sources: preflight section 2; Snapshot V1 Wire contract.
Reference row fields and SHA uniqueness/sorting are explicit. The source media
rows' complete shape and MIME-hint field mapping are not. Recognized JPEG plus
generic hints resolve to jpg, conflicting recognized hints reject, but reconstructing
that evidence from domain rows requires CF1's wire schema. Structural references
cannot substitute for exact recomputed reachability. Nonempty source media remains
contract-blocked; Media bytes are never required for METADATA_ONLY.

## Clarifications which are NOT findings

- Snapshot V1 Wire contract explicitly defines `mediaReferences.recordCount`;
  it is checked against actual reference JSONL records. Other counters are not invented.
- Creation issues must be empty for this supported profile.
- UUIDs/variant/profile/version, digest descriptors and reference rows are named
  explicitly in the permitted text; they need no application-code lookup.
- ZIP equality boundaries test the production limiter, not semantic validity of
  arbitrary padding. A byte-budget PASS is not full-snapshot PASS.

No real application snapshot path was supplied. Real comparison is NOT_RUN;
there are no application discrepancies to classify.
