# Independent Snapshot V1 Verifier Alignment Report

Baseline HEAD/origin: `92b99438040ddeafbba36d164bb990c0fbb21f31`.
Only `tools/pc/snapshot-verifier/` changed; Android/normative docs untouched.
Independence: YES. Text-only normative sources; no app source/tests/imports/calls.
External research: no new ecosystem mechanism/dependency; existing offline JDK17
and Gson upstream evidence in README retained. No network/device/Android tasks.

CF1 → wire sections 1–3/6–8 → RecordSchema/GraphRules/WireRecords → RESOLVED_BY_SCHEMA.
CF2 → wire section 4/6 → Coverage/WireRecords → RESOLVED_BY_SCHEMA.
CF3 → wire section 5/7 → eligibility/recomputed references → RESOLVED_BY_SCHEMA.
All 13 closed field inventories, nullable/optional media fields, exact types/enums,
collection-specific identity/order, named FK graph and subtype rules implemented.
Settings and v1/v2 geometry checked; no maps/source media are opened.
All media metadata retained; only H + positive canonical-safe size reaches references.
Same SHA eligible size/type conflicts reject; generic hints lose to recognized MIME.
No global UUID restriction, mandatory sequence synthesis, clock freshness check,
normalization, byte protection claim, restore/repair/ingest/offload added.

Independent generator includes all 13 nonempty collections, all three subtypes,
settings, coverage, known positive media and zero/incomplete metadata identities.
Typed negatives cover graph/settings/subtypes/weather/11 Bees, closed fields,
UUID/SHA, enums, references, conflicts, v1/v2, order and manifest counter.
Positives cover unrelated shared UUID, absent sequence row, member-order permutation,
future timestamp, historical properties, exact MIME, unknown/ineligible media.
Old corruption/container/truncation/ZIP safety/format/digest regressions retained.
Canonical vectors: 9/9 exact canonical bytes/UTF-8 hex/digest; binary64 vectors pass.
ZipFile + ZipInputStream agreement required; local/CEN disagreement rejects
ZIP_VIEW_MISMATCH. Raw entry hashes precede semantic validation; no reserialized hash.
Parser depth/string/key/member/array exact/+1 tests preserved.
Byte exact/+1 tests use the actual production Budget, not claims that arbitrary
padding creates semantically valid full snapshots. Actual streamed ZIP bomb rejects.

| Production gap (future Android alignment only) | Wire rule | PC enforces | Real ZIP can expose |
|---|---|---|---|
| G1 weather | §2/3 full matrix + one row per point | YES | YES, if emitted graph violates it |
| G2 Bee limit | §3 <=10 per point | YES | YES, if violated |
| G3 strictness/order | §1/6/8 closed objects + explicit tuples | YES | YES, if malformed emitted; normal output may not reveal reader gap |
| G4 media eligibility | §2/5 positive-safe known identity; ineligible metadata preserved | YES | YES, if refs violate eligibility; normal positive rows may not expose reader over-rejection |
| G5 UUID scope | §3 within collection, named FK/subtype only | YES | Only if such a legal shared-ID graph can be emitted; ordinary ZIP cannot prove absence of Android over-rejection |
| G6 lexical behavior | §1/4/5 exact UUID/name/geometry/MIME, no normalization | YES | YES, if actual recorded hints/encoding show a discrepancy |

REAL_APPLICATION_SNAPSHOT = NOT_RUN: no PC path provided; no ADB retrieval.
DISCREPANCIES: none established; no real Android bytes examined.
No remaining CONTRACT_FINDING; ordinary generated corpus PASS is not real writer proof.
Allowed execution: `.\gradlew.bat -p tools\pc\snapshot-verifier test --offline`.
Final run: BUILD SUCCESSFUL, 65 tests, 0 failures/errors. Suite counts:
CLI 1, Graph 22, SafeJson 7, Verifier 20, WireAlignment 15.
Local commit hash is returned in the owner report (not embedded in its own commit).
Independent read-only critic found no concrete implementation blocker; synthetic
budget-boundary scope is explicit, not claimed full-valid 64 MiB archive generation.
Verdict: READY_FOR_REAL_SNAPSHOT_TEST.

## MIME vector alignment status (2026-10-06)

Historical execution counts and findings above are retained. The verifier now
derives `jpg`/`mp4` from the trimmed, Unicode-lowercased MIME hint using the
fixed wire whitespace set, with `bin` fallback; recognized hints override
generic hints and conflicting recognized classes retain `MEDIA_IDENTITY_CONFLICT`.
`WireAlignmentTest` exercises every shared vector in
`docs/test-vectors/repository-v1-mime-extensions.json` through a complete
nonempty fixture and recomputed references. The approved offline test command
completed successfully: 66 tests, zero failures/errors (CLI 1, Graph 22,
SafeJson 7, Verifier 20, WireAlignment 16). All 16 single-hint and 9 merge data
vectors pass, including jpg + bin -> jpg. Conflict classification remains the
existing independent `MEDIA_IDENTITY_CONFLICT`; the data's Repository error token
is explicitly mapped by tests, not imposed as a shared implementation.
No real snapshot rerun or device execution is claimed. The earlier real fixture
had no media records and cannot prove MIME parity.

Production continuation is STOPPED separately: the unchanged
`SnapshotAllCollectionsDeterminismTest` successfully builds archives containing
UNAVAILABLE weather with nonnull `source="none"`, contrary to the wire
ObservationPointWeather schema.
This is a writer validation discrepancy; it is not a PC verifier defect or an
invitation to weaken the normative weather matrix. G1–G6 production alignment
has not been performed in this MIME reconciliation diff.

STOP: no push, Android changes, device access, R2, UI, PC ingest or new slice.
