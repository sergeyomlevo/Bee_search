# Snapshot V1 — normative wire schema

Status: **NORMATIVE — O1/O2/O3 APPROVED BY OWNER**. Baseline examined:
`4c62cbbef0cc2d15d2c4c43f0b00ae735d4924a9`. This closes textual gaps and records
the explicit owner decisions. It does not claim current app/verifier conformance,
implement code, or establish runtime/real-snapshot acceptance.
Existing accepted envelope/profile/limits remain in
[preflight](snapshot-2b-contract-preflight.md) and
[Snapshot V1](repository-snapshot-v1.md). This document supplies field-level rules;
an independent implementation needs no Android source. For field-level semantics,
this owner closure supersedes conflicting earlier wire drafts; historical reports
remain evidence of their original implementation, not proof of new-rule alignment.

Evidence labels: **EXISTING CONTRACT** = those documents/D097/domain/data model;
**OBSERVED PRODUCTION MODEL** = persisted Room/domain/settings definitions;
**OBSERVED CURRENT WRITER** = emitted representation, not normative authority;
**NORMATIVE WIRE CONTRACT** = requirements below under current owner approval.
The final reconciliation section distinguishes these explicitly. No external
technology change is designed here; external research is not applicable.

## 1. Common record rules

All tables below are closed field inventories. Every listed field is **required
to be present**, including nullable fields: `null` is a value, absence is not.
`?` means explicit JSON null is allowed. The ONLY omittable fields are media
`sha256` and `byteSize` in the two media collections, as explicitly stated below.
Omission represents unavailable identity metadata, not a fabricated default.
Unknown enum values, wrong types, missing fields and duplicate JSON keys FAIL.
Closed-schema policy: reject additional fields in manifest, descriptors, domain
records, settings, references and embedded geometry. Missing any other field,
including a nullable field, is invalid. No silent forward compatibility within V1.

UTF-8 without BOM; malformed UTF-8 and unpaired surrogates FAIL. JSONL consists
of one JSON object per LF-terminated record, no blank lines; empty collection is
zero bytes, not `[]`, `{}` or a missing ZIP entry. Final LF is required. Domain
JSON object member order and formatting whitespace are NOT semantic constraints;
entry integrity always uses the original bytes. Writer output must remain stable
for one captured graph; readers must not parse/reserialize to check hashes.

Type abbreviations used in every schema:

For the nonblank/edge-whitespace validation predicates, whitespace is exactly Unicode code points
U+0009..000D, U+001C..0020, U+00A0, U+1680, U+2000..200A, U+2028,
U+2029, U+202F, U+205F, U+3000. Nonblank means at least one other code point;
an edge-whitespace test only checks whether first/last code point belongs to this
set. No reader trims/replaces/case-folds/NFC/NFD-normalizes stored user values.
MIME canonical-extension DERIVATION is the explicit exception described in §5;
it does not rewrite the stored mimeType value. Existing
domain requirements that a particular field have no edge whitespace are VALIDATION
predicates (reject the value), never instructions to repair it or compare its trimmed
form. All other strings are retained and compared exactly as recorded.

| Type | JSON / semantic contract |
|---|---|
| S | JSON string, valid Unicode; no normalization |
| U | JSON string, lowercase ASCII UUID `8-4-4-4-12` hexadecimal; no braces/uppercase, no UUID-version restriction |
| I | JSON number with integer token grammar `-?(0|[1-9][0-9]*)`, signed 32-bit range; fractions/exponents/strings forbidden |
| L | Same integer token grammar, signed 64-bit range |
| T | L, epoch milliseconds, not ISO text, not seconds; no implicit timezone; domain negative epochs not categorically rejected |
| D | JSON number parsed as finite IEEE-754 binary64; decimal/exponent/integer spellings allowed (`56`, `56.0`, `5.6e1`); signed zero retained |
| B | JSON `true`/`false`, not 0/1 or strings |
| E | Case-sensitive JSON string from the explicit enum set |
| H | JSON string, exactly 64 lowercase hexadecimal SHA-256 characters |

No NaN/Infinity or numeric token overflowing binary64 is allowed. Domain I/L/T
are NOT routed through Double; readers must preserve integer precision. Canonical
manifest/reference integers instead use the accepted safe range
[-9007199254740991,9007199254740991] and restricted canonical rules. This is not a
safe-integer restriction on all domain timestamps. No derived timers/status,
display designations or independent Bee/cycle coordinates are serialized.

Semantic issue categories: `WIRE_SCHEMA_INVALID` (field/type/enum),
`LOGICAL_STATE_INCONSISTENT` (FK/lifecycle/identity conflict),
`MEDIA_IDENTITY_INVALID`, `MEDIA_REFERENCE_SET_MISMATCH`,
`MAP_COVERAGE_INVALID`; equivalent typed names are acceptable. Never report a
structural/schema failure as successful verification or merely a storage READ_ERROR.
Existing integrity/container/limit issue categories remain separate.

## 2. Domain collections: exact records

Entry basenames below live under `data/`. Ordering keys are in section 6.
All U owner/reference fields must resolve as specified in section 3.

### Territory — territories.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| id | U | Primary identity |
| code | S | Unique within territories, nonblank |
| name | S | Nonblank name |
| region | S | Nonblank region |
| district | S | Nonblank district |
| createdAt | T | Creation timestamp |
| updatedAt | T | Must be >= createdAt |

### Observer — observers.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| id | U | Primary identity |
| code | S | Unique within observers, nonblank |
| lastName | S | Nonblank surname |
| firstName | S | Nonblank given name |
| middleName | S? | Optional patronymic |
| contact | S? | Optional contact, not an FK |
| createdAt | T | Creation timestamp |
| updatedAt | T | >= createdAt |

### PhysicalObject — physical-objects.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| id | U | Shared physical identity |
| territoryId | U | Territory FK / numbering scope |
| objectType | E | `APIARY`, `HOLLOW`, `LOG_HIVE` |
| sequenceNumber | I | >=1; unique (territoryId,objectType,sequenceNumber) |
| latitude | D | [-90,90], actual object coordinate |
| longitude | D | [-180,180] |
| createdAt | T | Identity creation timestamp |
| creatorObserverId | U? | Observer FK; null valid for historical foundation rows |

### Apiary — apiaries.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| physicalObjectId | U | Primary key AND PhysicalObject FK, type APIARY |
| name | S? | Nonunique optional name, not identity |

### Hollow — hollows.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| physicalObjectId | U | PK/FK, type HOLLOW |
| tree | S? | Populated row: nonblank, no edge whitespace; reject rather than trim |
| entranceHeightCm | D? | Populated row: >0 |
| entranceAzimuthDeg | I? | Populated row: 0..359 |
| outerDiameterCm | D? | Populated row: >0 |
| internalDiameterCm | D? | If nonnull: >0 |
| notes | S? | Populated row: nonempty with no edge whitespace, or null |
| name | S? | Optional nonunique name, not designation |

Historical foundation row: tree, entranceHeightCm, entranceAzimuthDeg,
outerDiameterCm are all null; internalDiameterCm and notes must then be null.
Otherwise all four core fields must be nonnull. Name remains independently nullable.
Partially populated core is invalid; zero dimensions do not mean unknown.

### LogHive — log-hives.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| physicalObjectId | U | PK/FK, type LOG_HIVE |
| tree | S? | Nonblank with no edge whitespace in populated row |
| entranceHeightCm | D? | >0 in populated row |
| entranceAzimuthDeg | I? | 0..359 in populated row |
| outerDiameterCm | D? | >0 in populated row |
| material | S? | Nonblank with no edge whitespace in populated row |
| internalDiameterCm | D? | >0 in populated row |
| internalHeightCm | D? | >0 in populated row |
| notes | S? | Nonempty with no edge whitespace, or null |
| name | S? | Optional nonunique name |

All seven core fields (tree through internalHeightCm, excluding notes/name) are
either all null (historical, notes also null) or all nonnull (populated). No mixed
core; name is independent. This retains historical rows, not a new create workflow.

### PhysicalObjectSequence — physical-object-sequences.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| territoryId | U | Territory FK, composite PK component |
| objectType | E | APIARY / HOLLOW / LOG_HIVE, composite PK component |
| lastIssued | I | >=0 and >= largest live sequenceNumber in that scope |

Historical empty scopes may retain positive lastIssued; zero is an explicit reset.
Only actually captured sequence rows are serialized/validated. A missing scope row,
even for a live scope, is NOT an invalid snapshot by itself. No synthesis or restore
recomputation is defined here; present rows still obey FK/key/lastIssued constraints.

### PhysicalObjectMedia — physical-object-media.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| id | U | Media record identity, not blob identity |
| physicalObjectId | U | Owner PhysicalObject FK |
| type | E | IMAGE / VIDEO; logical media kind, not MIME sniffing |
| relativePath | S | Private working source locator metadata; never open during metadata verification |
| originalFileName | S? | Display/original name; not content identity |
| mimeType | S? | Optional MIME hint; only this field contributes extension evidence |
| byteSize | L? (may be absent) | Recorded size, including <=0 metadata; only positive eligible size can reach references |
| sha256 | H? (may be absent) | Known identity when nonnull; absent/null means unknown, never invented |
| createdAt | T | Record creation timestamp |

### ObservationPoint — observation-points.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| id | U | Primary identity |
| territoryId | U | Territory FK |
| observerId | U | Immutable historical Observer FK |
| observationYear | I | >0, stored year; do not recalculate from createdAt |
| pointNumber | I | >0, unique (territoryId,observationYear,observerId,pointNumber) |
| beePresenceResult | E? | BEES_FOUND / NO_BEES_FOUND or null |
| code | S? | Optional legacy/display metadata |
| latitude | D | Confirmed latitude [-90,90] |
| longitude | D | Confirmed longitude [-180,180] |
| gpsLatitude | D? | Original GPS latitude [-90,90] |
| gpsLongitude | D? | Original GPS longitude [-180,180] |
| gpsAccuracyM | D? | >=0 |
| createdAt | T | Observation creation timestamp |
| initialGroupReleaseAt | T? | Legacy initial-group timestamp; >=createdAt when nonnull |
| completedAt | T? | Completion timestamp; >=createdAt when nonnull |
| description | S? | Optional persisted description |

### Bee — bees.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| id | U | Primary identity |
| observationPointId | U | ObservationPoint FK |
| markColor | S | Nonblank, not a closed enum |
| markPosition | E | Exact allowed wire tokens: THORAX / ABDOMEN / LEFT_WING / NONE / RIGHT_WING; explicit semantic mapping below |
| createdAt | T | Creation timestamp |
| sourceObjectId | U? | Source PhysicalObject FK; no invented same-territory/type requirement |

| Allowed exact token | Semantic mark position |
|---|---|
| THORAX | THORAX |
| NONE | THORAX (historical token) |
| ABDOMEN | ABDOMEN |
| RIGHT_WING | ABDOMEN (historical token) |
| LEFT_WING | LEFT_WING (historical position, never reassigned) |

This five-token set is exhaustive and case-sensitive; e.g. `none` and `RIGHT_WING `
are unknown and rejected, not normalized. Uniqueness of
(observationPointId,markColor,semantic mark position) uses exactly the table above.
This is an explicit enum interpretation, not normalization of user strings.
New writers emit THORAX/ABDOMEN/LEFT_WING; accepting
historical aliases does not rewrite raw bytes or change their digest.

### FlightCycle — flight-cycles.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| id | U | Primary identity |
| beeId | U | Bee FK |
| sequenceNumber | I | >=1, unique (beeId,sequenceNumber) |
| departureTime | T | Actual departure timestamp |
| returnTime | T? | >=departureTime; null means open/unreturned |
| azimuthDeg | D? | [0,360); zero valid |
| azimuthCaptureConsumed | B | Persisted one-shot capture state |
| initialGroupLaunch | B | Legacy provenance; true requires sequenceNumber=1 |
| initialGroupLaunchCorrectionEligible | B | Wire name differs from current model property; true requires sequenceNumber=1 and returnTime=null |
| createdAt | T | Creation timestamp |
| updatedAt | T | >=createdAt |

No derived duration is stored. See section 3 for lifecycle constraints.

### ObservationPointWeather — observation-point-weather.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| observationPointId | U | PK/FK to point |
| status | E | PENDING / LOADED / UNAVAILABLE |
| temperatureC | D? | Degrees C, no invented temperature range |
| windSpeedMps | D? | If nonnull: >=0, m/s |
| windDirectionDeg | D? | If nonnull: [0,360) |
| sampleAt | T? | Weather sample timestamp |
| fetchedAt | T? | Fetch timestamp |
| source | S? | Provider/source label |

Existing intended weather lifecycle (D085, data model and provider model) has the
following exact per-status matrix. Nullable storage supports pending/unavailable,
not a partially loaded reading:

| status | temperatureC | windSpeedMps | windDirectionDeg | sampleAt/fetchedAt | source |
|---|---|---|---|---|---|
| PENDING | null | null | null | both null | null |
| UNAVAILABLE | null | null | null | both null | null |
| LOADED | finite D, nonnull | finite D >=0, nonnull | finite D in [0,360), nonnull | both nonnull T | nonblank S |

Every captured ObservationPoint requires exactly one weather row, including a
PENDING/UNAVAILABLE row with null payload; a missing row is not invented as PENDING
by the reader. No ordering between weather and observation timestamps is invented.
The legacy Complete Backup validator also checks this full matrix/cardinality;
that source observation supports reconciliation but is not itself a new norm.
Current Snapshot validation gap: section 10.

### ObservationPointAttachment — observation-point-attachments.jsonl

| Wire field | Type / nullable | Meaning / validation |
|---|---|---|
| id | U | Attachment record identity |
| observationPointId | U | Owner ObservationPoint FK |
| type | E | PHOTO only |
| relativePath | S | Private working source locator metadata |
| originalFileName | S? | Optional original/display name |
| mimeType | S? | Optional MIME hint |
| byteSize | L? (may be absent) | Recorded size; <=0/absent/null means ineligible blob, metadata preserved |
| sha256 | H? (may be absent) | Known SHA when nonnull; absent/null is unknown |
| createdAt | T | Record creation timestamp |

## 3. Graph/FK and subtype validation

Every nonnull FK must resolve against the captured dataset, not live device state.
Dangling references FAIL as LOGICAL_STATE_INCONSISTENT; never drop/null them.

| Source.collection.field | Target/key | Nullable |
|---|---|---|
| PhysicalObject.territoryId | Territory.id | No |
| PhysicalObject.creatorObserverId | Observer.id | Yes |
| Apiary.physicalObjectId | PhysicalObject.id, APIARY | No |
| Hollow.physicalObjectId | PhysicalObject.id, HOLLOW | No |
| LogHive.physicalObjectId | PhysicalObject.id, LOG_HIVE | No |
| PhysicalObjectSequence.territoryId | Territory.id | No |
| PhysicalObjectMedia.physicalObjectId | PhysicalObject.id | No |
| ObservationPoint.territoryId | Territory.id | No |
| ObservationPoint.observerId | Observer.id | No |
| Bee.observationPointId | ObservationPoint.id | No |
| Bee.sourceObjectId | PhysicalObject.id | Yes |
| FlightCycle.beeId | Bee.id | No |
| ObservationPointWeather.observationPointId | ObservationPoint.id | No |
| ObservationPointAttachment.observationPointId | ObservationPoint.id | No |
| portable.currentTerritoryId | Territory.id | Yes |
| portable.currentObserverId | Observer.id | Yes |
| map-coverage.territoryId | Territory.id | No |

Each PhysicalObject has exactly ONE matching concrete subtype row, with the same
UUID. Missing subtype, wrong type, multiple subtype tables for one owner and
duplicate subtype rows are invalid, including historical foundation objects.
Historical compatibility uses a present all-null property row, not a missing row.

Each collection's primary/stable key is unique WITHIN that collection. There is
NO global UUID uniqueness check across the 13 collections (nor a special six-table
set). Identical UUID text in different entity types is valid unless an explicit
subtype/FK rule is violated. FK resolution always uses the named target collection;
never resolve from a global ID map. Subtype owner IDs intentionally equal the parent.

Point lifecycle: at most one point has completedAt=null. A completed point requires
a nonnull beePresenceResult. NO_BEES_FOUND requires zero Bees and a completion;
BEES_FOUND requires >=1 Bee; any point with Bees must be BEES_FOUND.
The accepted domain limit is at most 10 Bees per ObservationPoint, including
completed observations (not 10 per file or per whole repository).
Cycles per Bee have contiguous sequenceNumbers 1..N. At most one returnTime=null,
and it is the greatest sequence; closed points may retain this unreturned cycle.
A Bee with no cycles is representable for legacy correction compatibility, not a
new primary capture workflow. Every initialGroupLaunch=true cycle must have
departureTime equal to its owning point's initialGroupReleaseAt. No inferred
cross-cycle timestamp ordering or azimuth/consumed coupling is added.

## 4. Portable settings and research geometry

`settings/portable.json` is one domain JSON object, not JSONL. Exact fields:

| Field | Type | Rule |
|---|---|---|
| currentTerritoryId | U? | Nonnull resolves Territory.id |
| currentObserverId | U? | Nonnull resolves Observer.id |

Null is valid, absent is invalid. No Room+DataStore global atomicity promise.

`settings/map-coverage.jsonl` has records:

| Field | Type | Rule |
|---|---|---|
| territoryId | U | Unique Territory FK and ordering key |
| encoded | S | Exact persisted Area/legacy selection encoding below |

This is persisted user Area intent/geometry metadata, NOT offline map packages,
tiles, package coverage manifests or a legal Territory boundary. Snapshot preserves
the encoded string; validation must decode it without generating IDs, migrating,
renaming, sorting/merging rectangles or opening map files.

Accepted encoded forms:

1. Exact `v1`: legacy empty selection, valid historical metadata.
2. `v1|north,east,south,west[|north,east,south,west...]`: >=1 rectangle;
   exactly four finite decimal binary64 tokens per fragment in this order.
   Lexical grammar: JSON-number grammar without quotes/whitespace;
   source-reader permissiveness beyond that grammar is not format authority.
3. `v2|` followed by a JSON object with exactly `areaId` (U), `name` (S,
   nonblank with no edge whitespace) and `bounds` (nonempty array). Every bounds object has
   exactly `north`, `east`, `south`, `west`, each D. Embedded JSON gets the same
   duplicate/Unicode/parser limits, measured anew from embedded root depth 0;
   encoded outer string must still fit the 65536 UTF-16-unit limit.

For every rectangle: north/south in [-90,90], east/west in [-180,180], north>=south.
No east>=west restriction: antimeridian representation is not silently normalized.
Degenerate equality is allowed; bounds array order is significant and preserved.
areaId is metadata identity of the Area, not a Room FK; no physical-copy UUID.
Unknown prefixes, empty/malformed fragments, empty v2 bounds, invalid numbers/
geometry FAIL. No implicit absent-state substitution. Names and UUID strings are
validated exactly as recorded, never trimmed or case-normalized on read.

## 5. Media identity / exact reachability algorithm

Private `relativePath` is preserved source metadata, never binary identity, ZIP
entry path, or evidence of bytes. Independent METADATA_ONLY validation does not
resolve it against a filesystem and does not infer extension from its suffix.
V1 promises string metadata, not a newly normalized path encoding; future restore
must validate local path safety separately before any use. originalFileName is
also not identity. Known SHA is identity; canonical blob path would be
`Media/<sha256>.<extension>`, but no Media existence check is made here.

1. Participants are ALL PhysicalObjectMedia and ObservationPointAttachment records
   in the captured graph; owners must resolve. No latest-only/history filter.
2. Preserve EVERY media metadata record, independently of blob eligibility.
   `sha256` and `byteSize` are optional-presence/nullable identity fields in these
   two schemas only. Absence/null records unknown information, never an invented
   SHA, zero default or new enum. Present nonnull SHA must be H; an invalid nonnull
   token is a schema violation, not silently repaired to unknown. Present nonnull
   byteSize must be L; zero/negative are retained as recorded metadata.
3. A record is eligible iff SHA is known H AND size is in
   1..9007199254740991 (representable positive canonical reference integer).
   All other otherwise schema-valid records are ineligible: preserved in domain
   entry but excluded from blob references. An out-of-range canonical size still
   fitting L is preserved/ineligible, not rounded. Neither source nor Repository
   Media bytes are required/read. No existence test, automatic DEGRADED or media
   protection promise applies to METADATA_ONLY COMPLETE. Tuple B (§7.1) cannot count
   an ineligible record as a LOCAL_VERIFIED protected blob either.
4. Group only ELIGIBLE records from both collections by exact SHA. All positive
   sizes in an eligible group must match; disagreement FAILS. Ineligible records
   (including a zero-size record sharing a SHA with a positive record) do not supply
   evidence or hints to the group and are not dropped from domain metadata.
5. Shared Repository V1 canonical-extension policy is normative. Single-hint
   derivation: null -> bin; otherwise remove leading/trailing characters using
   Kotlin `String.trim()` / `Char.isWhitespace()` semantics, then apply Unicode
   `String.lowercase()` with invariant locale. For trimming, the precise code
   point set is the whitespace set in §1 (not Java String.trim's <=U+0020 rule).
   Exact lookup of the resulting string: `image/jpeg` -> jpg, `video/mp4` -> mp4,
   all other values -> bin. No MIME parameter removal/parsing, sniffing, aliases,
   Unicode normalization or filename/type inference. Stored mimeType is preserved.
   Therefore `IMAGE/JPEG` and ` image/jpeg ` derive jpg; `image/jpeg; q=1` derives bin.
   For one SHA, derive extensions only from ELIGIBLE rows, then form the recognized
   set excluding bin. Empty set -> bin; {jpg} -> jpg; {mp4} -> mp4; {jpg,mp4} -> FAIL.
   Bin is lack of recognized evidence, not an independently conflicting type.
   Thus `IMAGE/JPEG` + `image/jpeg; q=1` -> jpg, VALID; size conflicts still FAIL.
   Production Snapshot writer/reader and future ingest use the ONE shared
   `CanonicalExtension.resolve` policy in RepositoryPolicy.kt. Independent PC readers
   implement this TEXT without importing production code.
   Contract vectors: [single/merged MIME hints](test-vectors/repository-v1-mime-extensions.json).
   Primary API references: [trim](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/trim.html),
   [whitespace](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.text/is-whitespace.html),
   [invariant lowercase](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/lowercase.html).
6. Emit exactly `{sha256:H, byteSize:canonical-safe-positive-integer,
   canonicalExtension:"jpg"|"mp4"|"bin"}`, canonical JSON per accepted restricted
   profile, one LF each. Deduplicate equal derived identities, sort by SHA ascending
   and reject duplicate/conflicting stored reference rows.
7. Stored references must equal this recomputed set, field by field. Extra/missing
   identity, wrong size/extension and duplicate/conflicting rows FAIL. Manifest
   mediaReferences.recordCount equals the number of emitted unique rows, not logical
   media records. Zero eligible participants imply empty reference entry and count=0,
   even if many ineligible metadata records are present in the domain collections.

A reference means recorded metadata identity, NOT protected/recoverable bytes.
Conflicts with an already committed Repository extension cannot be settled from a
standalone Snapshot ZIP; this reference extension is derived from snapshot hints,
not a fresh assertion about actual Archive placement. FULL/offload require their
own evidence/placement contract. No new local/PC evidence is implied.

Eligibility examples (otherwise valid owner/record metadata in every case):

| Captured metadata | Domain snapshot | Blob reference result |
|---|---|---|
| H + byteSize=0 | Preserve | None |
| H + byteSize<0 | Preserve | None |
| sha256 absent/null or byteSize absent/null | Preserve | None |
| Invalid nonnull SHA or noninteger nonnull size | Schema FAIL | Not accepted as unknown |
| H + positive safe size + null/unrecognized MIME | Preserve | One bin identity |
| Same H/size, jpeg plus generic MIME | Preserve both records | One jpg identity |
| Same H, zero-size mp4 hint plus eligible jpeg row | Preserve both records | Only eligible row contributes: jpg |
| Same H, two eligible differing positive sizes | Preserve candidate bytes, validation FAIL | Conflicting identity rejected |
| Same H/size, two eligible jpeg/mp4 hints | Preserve candidate bytes, validation FAIL | Conflicting recognized type rejected |

Here preserve means serialize exact captured metadata; it never means mutate the
live domain graph or accept a schema-invalid candidate. Digest metadata protects
the ineligible domain rows just as it protects every other logical record.

## 6. Deterministic record ordering

Strict ascending lexicographic tuple comparison of the keys below. Strings compare
ordinal UTF-16 code units, not locale/case-folding/numeric UUID values. UUID keys
are already lowercase full textual form. All keys are nonnull; no null-order rule
is needed. Enum sequence key is the literal token (APIARY < HOLLOW < LOG_HIVE),
not declaration ordinal. Numeric validation is independent of record ordering.

| Entry | Exact ordering / unique key tuple |
|---|---|
| data/territories.jsonl | (id) |
| data/observers.jsonl | (id) |
| data/physical-objects.jsonl | (id) |
| data/apiaries.jsonl | (physicalObjectId) |
| data/hollows.jsonl | (physicalObjectId) |
| data/log-hives.jsonl | (physicalObjectId) |
| data/physical-object-sequences.jsonl | (territoryId,objectType) |
| data/physical-object-media.jsonl | (id) |
| data/observation-points.jsonl | (id) |
| data/bees.jsonl | (id) |
| data/flight-cycles.jsonl | (id), NOT sequenceNumber |
| data/observation-point-weather.jsonl | (observationPointId) |
| data/observation-point-attachments.jsonl | (id) |
| settings/map-coverage.jsonl | (territoryId) |
| references/media-blobs.jsonl | (sha256) |

Portable settings is a single object, not an ordered collection. Domain object
field order is semantically irrelevant; canonical reference object order and
manifest object order follow the approved core. Descriptor semantic set sorts by
path. Neither writer determinism nor semantic acceptance authorizes rehashing
reserialized values instead of exact persisted bytes.

## 7. Manifest/digest inventory

Required top-level fields, no invented domain record counters:

| Field | Type/value |
|---|---|
| snapshotFormat | S, beesearch-snapshot |
| snapshotFormatVersion | canonical integer, 1 |
| snapshotId | U, matches UUID in filename |
| repositoryId | U, not inferred from filename/location |
| variant | Stable / Beta / Dev |
| createdAtEpochMs | canonical integer, 0..9007199254740991; provenance/order metadata only |
| snapshotProfile | METADATA_ONLY or FULL (§7.1) |
| creationResult | COMPLETE |
| evidencePolicy | NO_MEDIA_EVIDENCE or LOCAL_VERIFIED (§7.1) |
| entries | Array, exactly 16 unique sorted digest descriptors below |
| mediaReferences | Exactly {path:"references/media-blobs.jsonl",recordCount:nonnegative canonical integer} |
| creationIssues | Empty array for both supported combinations |

Only the two evidence tuples of §7.1 are supported. Every other combination of
these three fields FAILS closed; the field set, the field order and the value
types above are unchanged by §7.1.

Each of the 16 non-manifest entries (13 data + portable + coverage + references)
has descriptor with exactly `path:S`, `byteSize:nonnegative canonical integer`,
`sha256:H`. Path must equal one expected entry, never an arbitrary provider path.
Size/SHA are over actual uncompressed persisted entry bytes, including JSONL LF,
not records after parsing. No per-descriptor recordCount, no self-descriptor for
manifest, no whole-ZIP SHA inside ZIP. Metrics counters returned by current service
are not extra manifest fields. Adding domain recordCount later changes the schema.
Whole SHA covers container bytes, verified against canonical filename. All 17
regular entries, approved parser/byte caps and canonical vectors remain mandatory.
Verify raw bytes/limits/digests before semantic parse of those same fixed bytes.

No comparison to system `now`, future-clock tolerance or freshness/security check
is permitted for createdAtEpochMs. Future clock errors do not invalidate its wire
representation. Discovery may sort by (createdAtEpochMs,snapshotId), but UI/restore
must not treat the timestamp as proof that a physical repository copy is fresh.

## 7.1 Supported evidence profiles

Snapshot V1 accepts exactly two semantic tuples of the three manifest fields
`snapshotProfile` / `evidencePolicy` / `creationResult`:

| # | snapshotProfile | evidencePolicy | creationResult | Meaning |
|---|---|---|---|---|
| A | METADATA_ONLY | NO_MEDIA_EVIDENCE | COMPLETE | Research metadata only; no claim about media bytes |
| B | FULL | LOCAL_VERIFIED | COMPLETE | The same capture's required media set was strongly verified in the same bound local Repository immediately before publication |

Both tuples require `creationIssues` to be an empty array. Everything else is
unsupported and FAILS: `METADATA_ONLY`+`LOCAL_VERIFIED`, `FULL`+`NO_MEDIA_EVIDENCE`,
an unknown profile token, an unknown evidence-policy token, an unknown or
non-`COMPLETE` creation result, a non-empty `creationIssues`, and any other token
such as `DEGRADED`, `PARTIAL`, `INCOMPLETE`, `REMOTE_VERIFIED` or `PC_VERIFIED`.
Those tokens are not reserved and not partially accepted; no reader may invent a
fallback meaning for them.

**Compatible evolution policy.** This is an explicit semantic extension of
Snapshot V1, not a new container format. The structural envelope is unchanged:
the same 17 exact entries, the same manifest field set and ordering, the same
descriptor rules, the same canonical JSON/digest semantics and the same limits.
No new manifest field is added, so `snapshotFormatVersion` remains 1: a reader
that implements only tuple A rejects tuple B with an unsupported-profile failure
(§8) instead of misreading it, which is the fail-closed behaviour this policy
requires. A writer must never emit a tuple outside the table above. Any future
profile, policy or result value needs its own documented policy of this kind or
a new `snapshotFormatVersion`.

Contract vectors: [supported and unsupported evidence tuples](test-vectors/snapshot-v1-evidence-profiles.json)
are read by both the Android production model and the independent PC verifier.
This table is the normative text for that file.

**What FULL does not mean.** The ZIP is still metadata/reference only: it contains
no JPEG/MP4 payload, no `Media/*` entry and no blob bytes. `references/media-blobs.jsonl`
continues to be exactly the eligible reference set of §5, emitted from the same
capture as every other entry. Tuple B asserts only that the blobs of that same
required set were present in the same bound Repository, at the canonical path
`Media/<sha256>.<canonicalExtension>`, with the exact declared `byteSize`, with an
actual SHA-256 recomputed from the repository bytes, and with exactly one
unambiguous canonical identity at verification time.

**Boundary of LOCAL_VERIFIED.** Tuple B is local evidence only: it says nothing
about a PC copy, another device, cloud storage, an external disk, survival of
device loss or any independent off-device verification. Off-device protection is a
separate evidence layer (handoff/offload) that this profile must never imply.

**Zero required blobs.** Tuple B with `mediaReferences.recordCount = 0` is valid
when the capture itself is valid: it is a vacuous repository evidence set, not a
claim that photos and videos are protected. A future UI must present that case as
"nothing to save", never as "everything protected".

**Ineligible metadata stays ineligible.** §5 eligibility is unchanged. Tuple B
proves only the eligible reference set; schema-valid but ineligible media metadata
rows stay preserved domain metadata, never repository evidence, and no SHA/size is
invented for them.

**Old snapshots never change meaning.** Profile and evidence policy are immutable
manifest evidence. A snapshot that declares tuple A remains `METADATA_ONLY` /
`NO_MEDIA_EVIDENCE` even when its referenced blobs now exist in the Repository, and
no reader or repository scan may retroactively promote it to tuple B.

**Discovery of tuple B.** A repository snapshot candidate that declares tuple B is
a usable local FULL result only when its required set is currently strongly present
in the same bound Repository. If a required blob later disappears, changes size,
changes bytes, moves to another canonical extension or becomes ambiguous, the
candidate is surfaced as a typed media-evidence failure: the ZIP is neither
deleted, rewritten nor reinterpreted as tuple A, and an older valid tuple A
candidate may still be the newest usable snapshot.

**No automatic protection and no fallback.** Snapshot creation never ingests media
and never protects a missing blob implicitly. If a required blob is not already
strongly present, tuple B creation fails and publishes nothing; it must not fall
back to tuple A and must not silently drop the reference.

## 8. Strictness / compatibility

O1 approved: closed V1 schema everywhere. Reject unknown manifest or domain fields,
unknown enum values, duplicate keys, wrong type and unknown format/profile/policy/
result. Missing fields reject except the explicitly optional sha256/byteSize media
fields. Missing nullable fields are otherwise invalid; null is not a missing field.
No permissive extras, inferred defaults, stored-string normalization or silent loss.
Only MIME extension derivation applies §5 normalization; it never rewrites a field. New
fields/semantics need an explicit compatible evolution policy or new format version;
the two supported evidence profiles of §7.1 are the one such policy in force, and
they change no field, entry, ordering or digest rule. Version namespace remains
independent of Complete Backup V1–V6.
No stricter semantic dependency on domain member order. Existing canonical core is
unchanged. Limits apply to embedded geometry as well as outer records.

## 9. Findings disposition / next acceptance

| Finding | Disposition |
|---|---|
| CF1 domain field/FK schema | RESOLVED_BY_WIRE_SCHEMA; all 13 exact inventories, types, keys and FK/subtype rules above |
| CF2 geometry payload | RESOLVED_BY_WIRE_SCHEMA; exact v1/v2 forms, coordinate/lexical/ordering rules above |
| CF3 media reachability | RESOLVED_BY_WIRE_SCHEMA; metadata vs eligible identity vs reference separated explicitly |

The unchanged `tools/pc/snapshot-verifier/CONTRACT-FINDINGS.md` and its REPORT are
historical results from the prior text-only verifier slice, before this schema and
O1/O2/O3 closure. Their missing-text findings are superseded by THIS section for
contract readiness. Their implementation/test evidence is not rewritten: that
verifier remains unaligned/untested against these new rules. Future implementation
must use this normative schema, not preserve obsolete empty-graph restrictions
because the old findings file still contains its original CONTRACT_BLOCKED verdict.

No schema dependency on Android source remains for implementing the normative
reader. Read-only model/writer evidence below is provenance, not an instruction
to consult code. Real application ZIP acceptance remains NOT_RUN in this task.
Next task only after separate execution authorization: independent implementation from this text,
nonempty independent corpus, real production ZIP, then classify discrepancies
before changing either side. No verifier/app edits or real-ZIP adjustment here.

## 10. Reconciliation and production alignment gaps

EXISTING CONTRACT already approves envelope, paths, profile, raw integrity,
sorting intent, binary64 semantics, stable settings and media MIME dedup. Data model
sections 66/71.1 and domain invariants are the basis for intended graph semantics;
they take precedence over current permissive parser behavior.

OBSERVED PRODUCTION MODEL: Room Entities, domain Models/PhysicalObjects, Area
storage definitions have precisely the persisted fields above. Nullable names/
historical subtype fields and creatorObserverId are retained. At the examined
production baseline, Room media SHA/size are nonnull; neither media table has an
unknown-identity variant. The O2 wire representation above is now deliberately
more permissive than that model; this documents owner semantics, not a Room migration.

OBSERVED CURRENT WRITER comparison (no execution / no real snapshot inspected):

| Classification | Exact comparison |
|---|---|
| DOC_GAP | Shared BackupCore snapshotRows emits the listed camelCase fields; Room snake_case is NOT wire naming. FlightCycle property isFirstDepartureCancellationEligible emits initialGroupLaunchCorrectionEligible. Snapshot IDs/times only in manifest. |
| DOC_GAP | Current snapshot emits all null-capable fields explicitly; 13 ordering tuples match section 6; no domain recordCount in descriptors; mediaReferences.recordCount is emitted. |
| DOC_GAP | Coverage outer record is territoryId/encoded, exact v1/v2 string preserved; not arbitrary geometry JSON or map package metadata. |
| DOC_GAP | Current manifest's nonnegative createdAt and optional sequence scope cardinality match O3B/O3C; present sequences are checked against live maxima. No synthesis is required by the new contract. |
| PRODUCTION_VALIDATION_GAP G1 | Full weather matrix/cardinality is not enforced by current Snapshot validator (finite values/owner uniqueness alone are checked). Must validate all LOADED payload fields/source/wind ranges, completely null PENDING/UNAVAILABLE payload and exactly one row per point. |
| PRODUCTION_VALIDATION_GAP G2 | Current graph validator lacks the accepted maximum 10 Bees per point. |
| PRODUCTION_VALIDATION_GAP G3 | Current domain parsers ignore unknown fields and do not verify input record ordering. Closed-schema membership and strict per-entry ordering/key checks are required; ordinary writer output is already closed/sorted. |
| PRODUCTION_VALIDATION_GAP G4 | Current Snapshot validation rejects all media sizes <=0 and requires nonnull SHA/size. It must preserve schema-valid ineligible metadata, allow explicitly optional/nullable identity fields on read and compute references only from eligible records. References/count must match the new eligibility algorithm, not all media rows. |
| PRODUCTION_VALIDATION_GAP G5 | Current shared graph validator wrongly demands global uniqueness across six primary collections. Remove that cross-type restriction for Snapshot V1 only; retain within-collection identity/subtype/FK checks. No change to legacy Complete Backup is authorized here. |
| PRODUCTION_VALIDATION_GAP G6 | Current embedded Area reader can normalize names/UUIDs and accepts numeric spellings beyond the defined legacy decimal grammar. Geometry/UUID validation must reject forbidden forms and preserve allowed strings verbatim. The previously identified raw-MIME mismatch is superseded by the owner-approved §5 correction: shared MIME derivation normalization is normative, not a production gap. |

G1–G6 are exact requirements for a future bounded Snapshot alignment slice, not
owner decisions. No real invalid snapshot was inspected/observed. Existing writer
may emit otherwise legal ordinary records yet current reader can under-validate
some malformed inputs or over-reject newly approved legal O2/O3 cases. Do not claim
production or independent verifier conformance until alignment and corpus/real-ZIP
acceptance prove it. Findings are not permission to change legacy backup semantics.

Owner decision closure (no remaining decisions in this wire contract):

- **O1 APPROVED**: closed V1; exact lexical identities/enums; no automatic user
  string trim/case folding/Unicode normalization/replacement. Unknown fields reject;
  absent fields are allowed only where explicitly optional in these schemas.
  The later approved §5 MIME extension derivation exception normalizes only a
  temporary hint, never the stored string.
- **O2 APPROVED**: zero-byte/incomplete eligible identity does not block metadata
  COMPLETE; preserve records, omit ineligible references. No DEGRADED/media promise.
- **O3A APPROVED**: within-collection uniqueness only; explicit FK/subtype rules
  govern cross-collection relationships. No global UUID set.
- **O3B APPROVED**: sequence rows optional by scope/cardinality, captured rows
  validated/preserved; no synthetic rows or restore recomputation algorithm.
- **O3C APPROVED**: nonnegative safe-integer provenance timestamp; no system-now
  validation, no freshness/security claim, deterministic discovery tuple unchanged.

Weather validation discrepancy is not an invented owner choice: existing intended
model already defines the rules. A later bounded fix/test is needed before claiming
the current Android semantic validator fully conforms to this normative schema.
No real data, writer or verifier is changed by this finding.

Wire verdict: **WIRE_CONTRACT_READY**. This is textual/schema readiness, NOT current
app/verifier PASS, a format-conformance acceptance or authorization to implement
the next slice. Accepted architecture/profile is not reopened.

## 11. Document-only review and execution boundary

Independent document-only critic reviewed exact fields/FKs, subtype compatibility,
numeric types, media paths/reachability, geometry, ordering and strictness. It found
an incomplete weather state matrix in the first draft; the explicit matrix and
one-row-per-point requirement above replaced it. Re-check confirmed that blocker
fixed, with no further accidental schema gap reported in that prior review. Root
also checked the accepted 10-Bee bound. The owner decision closure now replaces
the draft O1/O2/O3 alternatives with the normative rules above. Fresh independent
document-only critic review completed for this closure: the exhaustive markPosition
token set and the historical-findings scope were made explicit after its first pass;
re-check found no remaining documentary blocker. O1/O2/O3 are supported by the text.
This is documentary review evidence, not a parser/build/device/runtime test.

Only this new document and follow-up links in the two approved existing reports
were changed. Production source was inspected read-only; verifier and its tests
were not edited. No Gradle, ADB, device, real snapshot test, commit or push.

## 12. Later owner MIME reconciliation (implementation acceptance pending)

The previous independent raw-MIME disagreement correctly exposed incompatible
writer/spec semantics. It remains historical evidence of verifier independence.
Owner subsequently chose shared Repository V1 policy as normative: §5 now specifies
both normalized single-hint derivation and recognized-over-bin same-SHA aggregation.
The proposed jpg+bin rejection was explicitly withdrawn. No version/field rename,
MIME parameters parsing, shared production policy change or broader normalization
is authorized. Other G1–G6 rules remain unchanged. Shared data vectors are not
shared implementation. Real Android acceptance used empty media and cannot prove
this new MIME contract; future nonempty device evidence is still pending.

Current reconciliation verification: shared data has 16 single-hint and 9 merge
vectors; production Repository/ Snapshot writer/reader MIME parity passes, and
independent PC offline regression passes 66 tests. Shared Repository policy,
Snapshot writer and reader production code remain unchanged.

Continuation STOP gate: the unchanged all-collections writer determinism test
successfully builds ZIP candidates with UNAVAILABLE weather and `source="none"`.
The ObservationPointWeather schema requires source null for that status. This demonstrates a
WRITER_DISCREPANCY in Snapshot creation validation, not just a reader gap.
No G1–G6 implementation, full Android build/regression, device test, commit or
push is claimed here. Owner review must resolve that writer gate before reader
alignment resumes; the normative weather matrix is not changed by this finding.

Later bounded continuation: the owner-authorized weather validation fix and
subsequent writer gate are recorded in [alignment status](snapshot-v1-reader-alignment-status.md).
The matrix above is unchanged; this later status supersedes the preceding
weather-only STOP as current operational context, not as historical evidence.
