# D106 Stage 1B — GPX parser, profile, security fixtures and canonical identity contract

Status: **ACCEPTED WITH AMENDMENTS — OWNER · 2026-10-13**.
Documentation only. No parser, test code, fixtures, persistence or rendering implemented.

Independent review: ACCEPT WITH NON-BLOCKING AMENDMENTS; BLOCKER0/HIGH0,
MEDIUM1 documentation/provenance only, not a GPX-contract defect.
OWNER accepted the contract; LOW-1…LOW-6 incorporated. The unknown-provenance
decisions.md NO-GO hunk is preserved unstaged and EXCLUDED FROM COMMIT.
Implementation is NOT authorized by this acceptance.

## 1. Scope and authority

Parent OWNER truth: [accepted Stage 1A specification](d106-stage1-gpx-spec.md).
This document translates it into a technical contract, not a replacement product
specification. Stage 1A wins within Stage 1 over older references.

Labels used here:
- **OWNER DECISION**: inherited literally from Stage 1A.
- **DERIVED CONTRACT**: consequences or necessary local technical definitions for review,
  not a new approval of user-visible behavior or persistent content-derived identity.
- **PROVISIONAL TEST/SAFETY CEILING**: unchanged Stage 1A numbers, not production capacity.
- **OPEN QUESTION**: missing evidence/detail; genuinely new product choices are separate.

Scope: input envelope/profile, canonical representation, normalization, identity
boundaries, diagnostics and deterministic fixture plan. No Room schema/migration,
UI/picker implementation, parser implementation/library integration, MapLibre rendering,
export/sync or Stage 2–4. Planned fixture IDs below are documentation, not files.

Incoming repository: HEAD e2d74cc609efde7f2e18a38947ccbcf226c5a351 on
task/d106-stage0-overlay-registry. Uncommitted Stage 1A documents and the unrelated
decisions.md hunk remain separate from this task.

## 2. Stage 1A inheritance and historical references

| OWNER decision | Stage 1B consequence |
|---|---|
| GPX1.1 only; tracks; mixed warning; no samples rejected | No GPX1.0/rte/wpt promotion or element recovery |
| Preserve empty tracks/segments and singleton | Canonical structure includes them; singleton need not be a line |
| Reimport same bytes after confirmation; fresh UUIDs | No hash/content uniqueness or silent logical dedup |
| Visibility local/Territory/default visible; excluded from protection | Outside canonical identity; restore defaults visible |
| Capture target before picker; cancel on Territory change | Parser does not choose ownership; future coordinator cannot retarget |
| Missing original retains viewing; FULL protection declines | Canonical geometry is materialized at import, URI is not storage truth |
| UTF8/UTF16, exact originals, no DTD/external I/O, fail closed | Strict byte decoding/security adapter requirements |
| Sample address SegmentUUID + XML ordinal | No sample UUID or content-derived sample identity |

Superseded/obsolete references are not rewritten:
- Stage 1A §9 supersedes the earlier proposal to keep every hidden GPX overlay resident
  with visible=false. Default is visible-set materialization/unregister.
- Older D106 open wording about UUID/ordinal mapping is specialized by Stage 1A §5.
- Historical Stage0 pending/NO-GO passages in decisions/evidence are historical;
  Stage 1A §1 carries accepted scoped Stage0 verification. No investigation/reopening.

## 3. Accepted structural profile

Actions: C = ACCEPT + CANONICALIZE; P = ACCEPT + PRESERVE in exact original only;
I = ACCEPT + IGNORE for canonical fields, with bounded unsupported-content diagnostic;
R = REJECT DOCUMENT. P/I do not normalize original bytes.
There is **no REJECT ELEMENT/SEGMENT recovery** for an invalid supported track.

GPX QName means exact URI http://www.topografix.com/GPX/1/1 plus local name.
Required root attributes version/creator and point lat/lon are unqualified.
Namespace prefixes and attribute order do not identify data.

| Construct/context | Action / canonical effect | Identity effect | Diagnostic |
|---|---|---|---|
| One document root gpx, correct QName | C; profile envelope | Profile context in comparison material | Otherwise UNSUPPORTED_PROFILE/INVALID_STRUCTURE |
| version | Exact string1.1 required, no trimming to make it match | Comparison value, not UUID | Missing/wrong rejected |
| creator | Required string; empty allowed by string type; preserve decoded text | Comparison metadata | Missing rejected |
| Namespace URI/prefix | URI exact; prefix ignored | URI/profile context; prefix excluded | Wrong/absent URI rejected |
| metadata | Optional unique direct child; source metadata fields | Supported values included | Duplicate supported container rejected |
| metadata/name, metadata/desc | Optional single text scalar | Included, null distinct from empty | Duplicate/nested markup rejected |
| metadata/author/name | Optional author container/name; no Observer inference | Included | Other author fields I |
| trk | Direct root child, ordered list | Structure/order included | Wrong supported structural placement rejected |
| trk/name, trk/desc | Optional single text scalar | Included | Duplicate/nested markup rejected |
| trk/type | I; no canonical type | Excluded | UNSUPPORTED_CONTENT |
| extensions (root/metadata/track/segment/point) | P/I; opaque canonical subtree | Excluded | UNSUPPORTED_CONTENT; security/limits still apply |
| trkseg | Direct trk child, ordered; empty allowed | Boundary/ordinal included | Wrong placement rejected |
| trkpt | Direct trkseg child, ordered | Ordinal/values included | Invalid supported point rejects document |
| lat/lon | C exact decimal values, required | Included | INVALID_COORDINATE |
| ele | C optional exact decimal | Included, null distinct from0 | Invalid present value rejected |
| time under trkpt | C raw lexical + status + optional UTC | Included | Unsupported lexical/zone becomes nullable with warning |
| Other point children | P/I, never FieldPoint | Excluded | UNSUPPORTED_CONTENT |
| Multiple tracks/segments | C without sorting/merging | Ordered boundaries included | None |
| Empty track / empty segment | C with ordinal, no invented sample | Included | No rejection if another usable sample exists |
| Singleton segment | C one sample | Included | Not a promise of line rendering |
| Root wpt / rte | P/I, never converted to track | Excluded | Mixed warning; no tracks => NO_IMPORTABLE_TRACK |
| Unknown standard elements/attributes | P/I | Excluded | Bounded unsupported summary |
| Foreign namespace subtree | P/I; matching local name not promoted | Excluded | Bounded unsupported summary |
| Missing supported samples in entire document | R after successful structure/security validation | No canonical import | NO_IMPORTABLE_TRACK |

DERIVED profile validation is a local supported-profile check, not remote/full XSD
validation. Use GPX1.1 ordering/cardinality for the supported projection: metadata
before supported tracks, track name/desc before segments, point ele before time;
name before desc before author within supported metadata. Unknown/ignored nodes
do not become extracted nodes or alter supported ordinal numbering.
Opaque skipped subtrees are still completely checked for XML/security/budgets.
A supported structural node misplaced in an active core context is invalid; a
track-shaped element inside ignored extensions is ignored, never a second track.
Repeated supported scalar/container, nested markup in a supported scalar, non-whitespace
text in core structural containers, missing fields or extra document root reject.
Unknown content does not grant permission to repair malformed XML.

Ignored containers/elements may appear anywhere within their otherwise valid parent
where this accepted Bee Search profile permits them to be ignored. They do not
become supported elements, affect supported-element ordering or ordinals, enter CM1,
or promote a foreign matching local name into a GPX element. XML well-formedness,
security and resource budgets still apply; this adds no supported data or recovery.

Syntactically valid is not canonically importable: empty gpx, track with no segment,
or only empty segments can be valid but reject if total supported sample count is0.
Mix them with singleton/multipoint segments and preserve all empty ordinals.

Primary profile reference: [GPX1.1 schema](https://www.topografix.com/GPX/1/1/).
It informs URI/cardinality/decimal types; Bee Search accepts only the Stage 1A subset.

## 4. Canonical representation — no platform or persistence types

DERIVED: parser output is a validated canonical draft, not a published TrackImport.
No XML objects/DOM/Android URI/MapLibre classes/Room rows occur in this representation.
The future import owner adds captured context and assigns UUIDs once after acceptance.

| Field/concept | Source; requiredness | Normalization | Logical identity / comparison | Future persistence / presentation |
|---|---|---|---|---|
| Profile version | Contract + root version; required | Stable profile token | Context / included | Preserve interpretation; not drawing input |
| Creator | Root creator; required | Decoded XML string | Not UUID / included | Canonical provenance |
| Source name/description/authorName | metadata fields; optional | String rules §5 | Not UUID / included | Persist; display/provenance |
| Tracks | Each direct trk incl empty | XML order | UUID assigned later / included | Preserve list/ordinal |
| Track ordinal | Zero-based encounter index | Nonnegative integer | Parent address / included | Persist; never sorted |
| Track name/description | Direct supported text; optional | String rules | Not UUID / included | Persist; display |
| Segments | Each direct trkseg incl empty | XML order | UUID later / boundaries included | Preserve gaps |
| Segment ordinal | Zero-based within track | Nonnegative integer | Parent address / included | Persist |
| Samples | Each direct supported trkpt | XML order | SegmentUUID+ordinal / included | Preserve sequence |
| Sample ordinal | Zero-based within segment | Nonnegative integer | IN logical address / included | Persist |
| Lat/lon | Required unqualified attributes | Exact decimal §5 | Not logical key / included | Persist exact canonical value; rendering conversion separate |
| Elevation | Optional ele | Exact decimal §5 | Not logical key / included | Persist, nullable presentation input |
| Raw sample time | Optional time text | XML decoded text, no lossy rewrite | Not logical key / included | Canonical lexical provenance |
| Time support status | Derived from raw time | MISSING / UTC / NO_ZONE / UNSUPPORTED | Not logical key / included | Preserve null reason |
| UTC seconds/nanos | Usable zoned time only | Exact epoch integer + nanos | Not logical key / included | Persist independently of millisecond converter |
| Warning summary | Validation observations | Category/count/bounded addresses | NOT IN IDENTITY/material | Operational/provenance summary, not geometry |
| UUIDs/original object/owner/import time | Future coordinator, not XML parser | Preserve allocated values | IN logical record/context, NOT comparison | Canonical record envelope |
| Original length/SHA/filename | Managed copy/context | Exact bytes hash; filename advisory | Original identity/context, NOT comparison | Persist descriptor; not geometry |
| Import display name | Nullable coordinator/import-envelope context, not metadata/name | No mandatory derivation from metadata/name | Not logical UUID or CM1 input | Future persistence mapping; not parser structural truth |

Display name is distinct from supported GPX source metadata; Stage 1B does not
require it to equal metadata/name or design a Room field.

Canonical truth consists of supported ordered values and source addressing.
Presentation truth is a bounded derivative, not a replacement canonical geometry.

## 5. Normalization and numeric/time rules

DERIVED coordinate/elevation grammar: edge XML whitespace IS collapsed;
remaining ASCII lexical form is optional sign + decimal digits with optional dot
(at least one digit total, e.g. .5/1. legal). No exponent notation, comma, internal
whitespace, NaN/Infinity, locale parsing or non-ASCII digits.
Reference: [XSD decimal](https://www.w3.org/TR/xmlschema-2/#decimal).

Parse exact finite decimal, not binary float then round. Check range on exact value:
lat [-90,90], lon [-180,180). Normalize optional plus, leading integer zeros,
trailing fractional zeros and negative zero; numeric zero becomes0.
Plain decimal, no exponent; at least one integer digit; omit dot when fraction zero.
Thus -0, +0.0, 000.000 =>0; +056.1200 =>56.12; .5 =>0.5.
This is Bee Search framing normalization, not a claim of XSD canonical lexical spelling.

Excess precision within lexical128 and other input ceilings is preserved exactly.
No spatial rounding, snapping or approximation for identity/comparison. Two close
but different decimals remain different even if a future MapLibre double conversion
would coincide. Future persistence must preserve this distinction; schema is not
designed here. Conversion for presentation cannot overwrite canonical value.

String rules: use mandatory XML decoding/entity/line-end/attribute normalization,
then preserve text exactly, with no trim/casefold/Unicode NFC. Missing=null, present
empty="", space-only distinct. Indentation outside supported scalar is not metadata.
CDATA versus equivalent text/entities gives the same decoded scalar.
These definitions do not rewrite original bytes.

Time: retain decoded raw text exactly; trim only XML edge whitespace for semantic
analysis. Stage1A explicit zone/range/precision subset applies, no lenient rollover.
Valid dateTime without zone => NO_ZONE/null; malformed/unrepresentable/leap-second/
excess precision => UNSUPPORTED/null, warning; missing => MISSING/null.
Valid Z/offset yields UTC seconds+nanos with status UTC; offset max14:00 per dateTime,
14 requires minute00. Valid24:00:00 normalizes to next day if representable; invalid
calendar/date/offset never silently rolls. Seconds/nanos include no guessed timezone.
Different raw time strings for one instant have equal UTC values but different full
canonical material because raw lexical provenance is canonical. No time-based sorting.

## 6. Identity concepts and inputs

**OWNER DECISION:** logical I/T/S UUIDs and sample(S,ordinal) are not hashes.
Each newly confirmed import gets new logical IDs even for identical content.
Restore preserves IDs; rebuilding presentation never allocates new canonical IDs.

Three distinct concepts:
1. Logical record/source/sample identity: allocated immutable UUID hierarchy/address.
2. Original content identity: recorded SHA-256 over exact managed bytes + length.
3. Canonical comparison material CM1: deterministic supported-value structure for
   fixtures/rebuild comparison, NOT a source ID, uniqueness rule or new duplicate policy.

DERIVED: Stage1A does not require an extra persisted semantic digest. CM1 equality
must not trigger user-facing dedup/confirmation as if byte-identical. Only original
SHA equality is the inherited repeated-file signal; confirmation never merges records.
No canonical content digest/algorithm is selected or required to implement parsing.

| Potential input | Logical identity | Original SHA identity | CM1 |
|---|---|---|---|
| Allocated import/track/segment UUID | IN IDENTITY | Excluded | Excluded |
| SegmentUUID + sample ordinal | IN sample address | Excluded | Ordinal/order included; UUID excluded |
| Filename/extension/display name | CONTEXT ONLY | Not input | Excluded |
| URI/path/document ID | CONTEXT ONLY before copy | Not input | Excluded |
| Exact bytes | NOT logical identity | IN IDENTITY | Only supported decoded values |
| Encoding/BOM/XML declaration | NOT logical identity | Included as bytes | Excluded if accepted and semantics unchanged |
| XML whitespace/comments/prefix/attribute order | NOT logical identity | Included as bytes | Excluded except whitespace within preserved text |
| Supported metadata/creator/track name | NOT logical key | Included as bytes | Included |
| Ignored metadata/type/extensions/routes/waypoints | NOT logical key | Included as bytes | Excluded |
| Track/segment/sample order and boundaries | IN source addressing | Included as bytes | Included |
| Coordinates/elevation/time | NOT logical key | Included as bytes | Included per §5 |
| Territory/importing Observer/import time | Ownership/provenance CONTEXT | Not input | Excluded |
| Visibility/availability/SDK resource ID | NOT IN IDENTITY | Not input | Excluded |

A→B→C versus C→B→A and swapped distinguishable segments have different material:
direction/order are authoritative, not sorted or made direction-insensitive.
Repeated equal points can make a reorder value-equivalent; ordinal identity still
belongs to its own immutable import. No test can distinguish an observationally
identical sequence merely by claiming it was reversed.

## 7. Formal duplicate/equality matrix

Assume each row is a second accepted import, where applicable, compared to the
first; logical IDs are DIFFERENT after required byte-duplicate confirmation.
SAME means equality in the named domain, never permission to merge. Mutations below
change only the stated content and preserve profile validity.

| Pair | Original bytes/SHA content | CM1 | Logical result / reason |
|---|---|---|---|
| Same file repeated | SAME | SAME | DIFFERENT fresh UUIDs, confirmation |
| Same bytes/different filename | SAME | SAME | DIFFERENT; name is context |
| Only structural indentation | DIFFERENT | SAME | DIFFERENT; no semantic dedup |
| Equivalent XML declaration variation | DIFFERENT | SAME | DIFFERENT if accepted encoding/XML envelope |
| UTF8 vs UTF8 BOM | DIFFERENT | SAME | DIFFERENT |
| Prefix changes with identical QNames | DIFFERENT | SAME | DIFFERENT |
| Comments added/removed | DIFFERENT | SAME | DIFFERENT |
| Supported metadata changed | DIFFERENT | DIFFERENT | DIFFERENT |
| Track name changed | DIFFERENT | DIFFERENT | DIFFERENT |
| Ignored extension/type changed | DIFFERENT | SAME | DIFFERENT |
| Coordinate lexical form, same exact decimal | DIFFERENT | SAME | DIFFERENT |
| One actual coordinate changed | DIFFERENT | DIFFERENT | DIFFERENT, no rounding |
| Distinguishable point order changed | DIFFERENT | DIFFERENT | DIFFERENT |
| Distinguishable track reversed | DIFFERENT | DIFFERENT | DIFFERENT |
| Segment boundary changed | DIFFERENT | DIFFERENT | DIFFERENT; gaps matter |
| Elevation value/null changed | DIFFERENT | DIFFERENT | DIFFERENT |
| Raw or usable timestamp changed | DIFFERENT | DIFFERENT | DIFFERENT; raw is preserved |
| Same bytes in another Territory | SAME | SAME | DIFFERENT; new owner, lookup/prompt scope deferred to coordinator |
| Original later missing | Existing recorded SHA unchanged; bytes unavailable | Existing material unchanged | SAME existing logical import; degraded, FULL unavailable |

Sha equality is a cryptographic content signal, not a mathematical proof that every
different byte string has a different digest. If exact equality is needed, compare
bytes; do not assert collision-free hashes. No collision can merge logical records.
Same supported semantics with different XML is not Stage1A's byte-duplicate condition.
Exact-byte SHA equality is the repeated-original content signal, not a uniqueness
constraint or logical merge. Confirmed repeated imports receive fresh UUIDs; CM1
equality alone never triggers a duplicate prompt. Exact SHA lookup/prompt scope
(per-Territory, app-wide or other inventory) is outside this parser/canonical contract.
The future import/UI coordinator implementation contract must fix it before implementing
that workflow; this document chooses no lookup scope and introduces no new OWNER rule.

## 8. Deterministic CM1 byte framing and versioning

DERIVED fixture/rebuild comparison contract, not a new storage or backup format.
Never hash an object's toString, unordered map or ordinary JSON serialization.
All lengths/counts below are unsigned32 big-endian; tags unsigned16 big-endian.
UTF8 encodes valid Unicode scalar values without BOM. Length overflow rejects the
comparison operation explicitly, never wraps; material can be streamed. Integers are minimal signed
ASCII decimal (no plus/leading zeros/-0); decimal text uses §5.

Material = ASCII magic BEEGPXCM + unsigned16 version1 + root record payload.
Each field E(tag,kind,payload) = tag + one-byte kind + length + payload.
Kind0=null (length0); kind1=UTF8 scalar; kind2=record; kind3=list.
Record = all defined fields exactly once in ascending tag order, including nulls.
List = count + each element E(1,kind2,record) in XML order.
Null and empty string/list/record are distinct. No duplicate/unknown tags in CM1.

Fixed field tags:
| Record | Ascending fields |
|---|---|
| Root | 1 GPX version,2 creator,3 sourceName,4 sourceDesc,5 authorName,6 tracks(list) |
| Track | 1 ordinal,2 name,3 description,4 segments(list) |
| Segment | 1 ordinal,2 samples(list) |
| Sample | 1 ordinal,2 lat,3 lon,4 elevation,5 rawTime,6 timeStatus,7 UTCseconds,8 nanos |

All non-null primitives use kind1; optional strings/numbers null use kind0.
GPX QName/profile semantics are fixed by CM1 version; no namespace prefix field.
Ignored content, warnings, UUIDs and coordinator context are excluded.

A future CM2/profile change must be explicitly versioned and never reinterpret CM1
or regenerate UUIDs/sample addresses. Parser version, comparison version and logical
identity are different. Restore uses stored canonical truth, not latest parser rules.
If a later digest is needed, require named cryptographic algorithm+CM version,
unambiguous framing and full-material comparison for decisive equality; selection
and golden digest belong to that separate contract, not this task.

## 9. Original, Territory and presentation boundaries

OWNER DECISION: original remains required protection material, but not a read dependency
for viewing after import. Materialize supported values, order, identity and provenance
at import. URI loss is irrelevant once managed copy succeeds; original URI is never
authoritative storage. Later original loss changes availability, not stored geometry/IDs.

Territory is the owner, not a component of semantic CM1 content. The same material
can belong to two independently identified Territory imports. Captured owner and
Observer/context are assigned by the future coordinator, never XML author metadata.
No Area association is used.

## 10. Security and encoding capability contract

OWNER DECISION: no parser-controlled external I/O. The only input is caller-supplied
bytes/stream; no network, file URI, schema, XInclude, stylesheet or entity resolver.
A schemaLocation/PI/foreign extension must never become an I/O instruction.
DTD (internal/external), parameter/general custom/recursive entities and expansion
bombs reject. Built-in XML escapes/numeric references are allowed, counted after
decoding and cannot define recursion. Undefined/invalid reference rejects.
Security configuration unsupported/unverifiable => SECURITY_CONFIGURATION_UNAVAILABLE,
no fallback to an unsafe parser.

Require namespace-aware, strict XML1.0 well-formedness and strict decoder behavior:
truncation, duplicate QName attributes, malformed UTF/surrogates/control characters,
unbound prefixes, multiple roots reject; never replace malformed bytes with U+FFFD.
XML declaration version1.0; XML1.1 is not this profile. No full external XSD validation.

DERIVED encoding envelope from Stage1A + [XML1.0 encoding rules](https://www.w3.org/TR/xml/#charencoding):
UTF8 with/without BOM, optional UTF-8 declaration; UTF16 with LE/BE BOM,
optional UTF-16 declaration. BOM determines byte order and is not text.
Encoding names are case-insensitive; contradicting declaration/bytes fails.
No UTF16 without required BOM, UTF32, UTF-16LE/BE named alternative encodings,
CESU8 or arbitrary legacy encodings in this profile. Stage1A's UTF16 is XML's UTF-16
term, not automatic acceptance of related encoding labels. Declaration sniffing
must be bounded and never trust provider MIME as encoding authority.

Limits apply also to opaque ignored content, huge attributes/text/extensions,
namespace declarations and pathological numbers. Cancellation/no-I/O/counting must
be testable using a supplied stream and a resolver-denying adapter.
Do not promise the selected platform parser is secure until fixtures prove capability.
Stage1A names platform XmlPullParser; this design specifies capabilities, not calls
or a new dependency. [Platform API](https://developer.android.com/reference/org/xmlpull/v1/XmlPullParser).

## 11. Budgets — exact inherited ceilings and counting

All numeric entries are PROVISIONAL TEST/SAFETY CEILINGS, never capacity guarantees.
MiB=1048576 bytes. Below/exact passes this boundary if all other rules pass;
one over returns LIMIT_EXCEEDED at the responsible future layer.

| ID | Inherited ceiling | Where/count/check | Fixture |
|---|---|---|---|
| G01 | Original16 MiB | Raw bytes incl BOM/comments, during read; reject before accepting next byte | L-1/L/L+1 |
| G02 | Tracks128/import | Accepted direct trk START incl empty, before allocating129th | L-1/L/L+1 |
| G03 | Segments4096/import | All accepted trkseg incl empty across tracks, not per track | L-1/L/L+1 |
| G04 | Samples100000/import | All supported trkpt across tracks/segments, not per segment | L-1/L/L+1 |
| G05 | XML depth32 | Root depth1; every subtree including ignored, at START | L-1/L/L+1 |
| G06 | Attributes64/element | All lexical attributes incl namespace declarations, at START | L-1/L/L+1 |
| G07 | XML events2000000 | Logical events defined below, increment before handling | L-1/L/L+1 |
| G08 | Semantic text1024 characters | Decoded supported metadata/creator scalar, cumulative before retaining | L-1/L/L+1 |
| G09 | Used token8192 characters | Decoded text run/comment/PI/attribute/name/namespace value incl ignored | L-1/L/L+1 |
| G10 | Coordinate/time lexical128 characters | Decoded original scalar incl edge whitespace, before numeric/time analysis | L-1/L/L+1 |
| G11 | Imports1024/Territory | Future coordinator READY inventory plus candidate; not parser | L-1/L/L+1 |
| G12 | Samples200000/Territory | Future persisted inventory + candidate; not per document | L-1/L/L+1 |
| G13 | Current Backup64 entries | Existing protection layer ZIP inventory | L-1/L/L+1 |
| G14 | Current Backup16 MiB metadata/entry | Protection bytes, not GPX input | L-1/L/L+1 |
| G15 | Current Backup64 MiB aggregate metadata | Protection sum, not original payload bytes | L-1/L/L+1 |
| G16 | Proposed V9 GPX-samples64 MiB/entry | Future protection profile, not current implementation | L-1/L/L+1 |
| G17 | Proposed V9 total metadata256 MiB | Future protection checked sum | L-1/L/L+1 |
| G18 | Proposed V9 entries4096 | Future archive inventory | L-1/L/L+1 |
| G19 | Snapshot ZIP64 MiB | Existing/future protection envelope compressed bytes | L-1/L/L+1 |
| G20 | Snapshot uncompressed128 MiB | Protection decoded sum | L-1/L/L+1 |
| G21 | No numeric capacity fixed | Managed storage/app-wide aggregate, presentation geometry/sources/layers/RAM, blobs/staging space | Deterministic available-space/visibility scenarios; measurements later |

Character count means Unicode scalar values after mandatory XML decoding; no trimming
before lexical-length checks. Token limit counts full logical token/text run, never
resets because the parser split a callback. Attribute count includes xmlns declarations
even if a parser exposes them separately. Numeric digit budget is lexical128, not
a new arbitrary spatial precision limit; elevation is bounded by token/input ceilings.

Logical events: START and END each count1 (empty element still2); one TEXT event per
maximal adjacent decoded character-data run, coalescing CDATA/allowed entity splits;
COMMENT and PI each1. XML declaration counts1 if present; BOM/END_DOCUMENT not events.
No count dependence on buffer size/callback chunking. DTD security rejection does not
need to reach an event ceiling. Namespace/attribute events belong to START, not extra
events. Long logical text is rejected before buffering the entire run when possible.

Input/storage/presentation/protection remain independent. A G11/G12 failure is an
admission result of the future coordinator, not malformed XML. G13–G21 fixtures are
specified now but belong to later implementation slices, not the parser test suite.
No new per-track/per-segment/application-wide numeric ceiling is introduced.

Aggregate corpus must combine several Territories, repeated imports, existing
research/media, near-limit canonical data, protection inventories and visible subsets.
Importable storage need not all be resident in native map. Unknown numeric domains
cannot claim below/exact/over proof until a measured ceiling is approved.

## 12. Streaming, cancellation and diagnostics

DERIVED: no architecture requiring a whole-document DOM. Streaming bounded scratch/
bounded batches, with tentative canonical draft/spool managed by the caller.
No promise of a particular RAM value or constant-space output: a canonical dataset
itself has size. Counts/text limits constrain work; parser pre-token allocation remains
a capability/evidence risk, not hidden by the contract.

Abort during read, events, batch transfer and before final accepted result.
Tentative records emitted by a streaming sink are untrusted until end-of-document
validation accepts the entire draft. Sink must support discard/abort; no public
partial canonical import may survive rejection/cancellation. Coordinator owns future
pending-store recovery, not parser code or MapLibre.

| Result/category | Deterministic category? | Reject? / canonical output | Safe diagnostic |
|---|---|---|---|
| ACCEPTED | Yes for fixed bytes/profile/ceilings | Valid complete draft, warnings allowed | Counts/addresses |
| UNSUPPORTED_PROFILE | Yes | Reject; no public draft | Version/QName category |
| UNSUPPORTED_ENCODING | Yes | Reject | Encoding category, not copied declaration |
| MALFORMED_XML | Yes for isolated defect | Reject | Bounded location/reason |
| INVALID_STRUCTURE | Yes | Reject | Supported path/ordinal |
| INVALID_COORDINATE | Yes | Reject | Lat/lon category and address |
| INVALID_OPTIONAL_VALUE | Yes for ele | Reject | Field/address |
| NO_IMPORTABLE_TRACK | Yes after complete validation | Reject | Zero supported samples |
| SECURITY_REJECTED | Yes for prohibited construct | Reject | DTD/entity/I/O category |
| SECURITY_CONFIGURATION_UNAVAILABLE | Adapter/environment dependent | Fail closed | Capability category |
| LIMIT_EXCEEDED | Yes with fixed logical counting | Reject/admission refusal at responsible layer | Domain/ceiling/count |
| CANCELLED / INPUT_IO_ERROR | Execution dependent | Abort/discard | No parser exception/URI leakage |

Warnings: UNSUPPORTED_CONTENT and UNSUPPORTED_TIME; fixed categories/counts and at
most the first structural address per category, not attacker-controlled excerpts.
Do not create one warning category per unknown QName or retain unbounded addresses. Time null/status isn't element recovery.
Never expose raw exception, huge text, full URI/path or original content as UI wording.
Exact diagnostic code naming/location is internal; do not freeze translated UI text.
For multi-defect input, first observed deterministic normalized-event failure wins;
equivalence fixtures concern valid inputs. Parser-specific exception text is not golden.

## 13. Fixture taxonomy and generator contract

Documentation only: no fixture files/generators/tests created.
Each row below names distinguishing content, parser outcome/canonical assertions,
identity relation and security/budget intent. Use CM1/bytes/logical domains explicitly.
AC=ACCEPTED; W=warning; "=" / "≠" compare to base or stated pair, not UUID identity.
Every newly admitted import has fresh logical IDs unless testing restore of the same record.
All ordinary cases stay below ceilings; limit families isolate one ceiling.

Corpus totals92 fixture families: A9/B9/C9/D14/E10/F13/G21/H7.
G01–G20 are numerical boundary families with below/exact/over cases.
B-family asserts canonical equivalence/CM1 relation; E-family asserts encoding/envelope
acceptance or rejection. B02/E02, B03/declaration cases and B09/E03/E04 intentionally
reuse byte/document pairs for independent assertions, not a duplicated fixture oracle.

Future generator: fixed seed-free template, explicit parameter N, bounded literal
padding/fields, deterministic UTF8/UTF16 encoding; no current time/random UUID.
Return exact byte length/event/structure counts and expected result independently of
parser. Large files need not be checked in. Do not derive oracle by calling parser.
Chunk the same stream differently to verify counting/canonical equality.

## 14. Fixture matrix

### A — ACCEPTED CORE

| ID | Minimal distinguishing content/purpose | Parser/canonical expectation | Identity / budget |
|---|---|---|---|
| A01 | Minimal gpx1.1 creator, one track/segment/point0,0 | AC; singleton, null optional fields | CM1 golden §15; below |
| A02 | Three distinct points A→B→C | AC; exact order | Base direction/gap comparisons |
| A03 | Two segments separated in XML | AC; two boundaries | ≠ flattened A02, no bridge |
| A04 | Two distinct tracks | AC; independent tracks | ≠ merged track |
| A05 | Name/desc/author, track metadata, ele/time | AC; supported values | Metadata/time material baseline |
| A06 | Unicode metadata incl supplementary character | AC; exact text | No NFC/casefold |
| A07 | lat±90, lon-180 and value just below180 | AC; exact values | Boundary, no rounding |
| A08 | Missing ele/time plus non-monotonic/duplicate usable time | AC; nulls/order preserved | No sort |
| A09 | Usable UTC offset/nanoseconds | AC; exact seconds/nanos + raw | UTC equivalence separate from raw |

### B — CANONICAL EQUIVALENCE

| ID | Minimal change | Parser/canonical expectation | Identity relation |
|---|---|---|---|
| B01 | Structural whitespace only | AC; same supported values | CM1=, bytes≠ |
| B02 | UTF8 BOM/no BOM | AC | CM1=, bytes≠ |
| B03 | Valid XML declaration spelling/presence | AC | CM1=, bytes≠ |
| B04 | Prefix renamed, QNames unchanged | AC | CM1=, bytes≠ |
| B05 | Comments added/removed | AC | CM1=, bytes≠ |
| B06 | -0/0.0/+000.0 and leading/trailing zeros | AC; same exact decimal | CM1=, bytes≠ |
| B07 | Unknown/type/extension-only change | AC+W | CM1=, bytes≠; unsupported summary may differ |
| B08 | Attribute order/text versus equivalent CDATA/reference | AC | CM1=, bytes≠ |
| B09 | Same logical XML UTF8 vs accepted UTF16 | AC | CM1=, bytes≠ |

### C — CANONICAL DIFFERENCE

| ID | Minimal change | Parser/canonical expectation | Identity relation |
|---|---|---|---|
| C01 | Actual lat decimal changes | AC; no rounding | CM1≠ |
| C02 | Distinct point reorder | AC; new order | CM1≠ |
| C03 | A→B→C reversed | AC | CM1≠, not direction-insensitive |
| C04 | Same points with boundary split | AC; new gaps | CM1≠ |
| C05 | Distinct tracks/segments swapped | AC; new ordinals | CM1≠ |
| C06 | Supported metadata/creator/track name changes | AC | CM1≠ |
| C07 | Ignored metadata/extensions change | AC+W | CM1=; original≠, non-identity metadata example |
| C08 | Elevation null→0 or value changes | AC | CM1≠ |
| C09 | Raw timestamp/UTC value change | AC or AC+time W | CM1≠, including same instant/different raw |

### D — STRUCTURAL EDGE CASES

| ID | Minimal content | Parser/canonical expectation | Identity / intent |
|---|---|---|---|
| D01 | Empty valid gpx | NO_IMPORTABLE_TRACK | No import |
| D02 | Document whose only supported track content is a track without a segment | NO_IMPORTABLE_TRACK | Zero supported samples; mixed usable/empty case is D04 |
| D03 | Only empty segment | NO_IMPORTABLE_TRACK | No import |
| D04 | Empty tracks/segments mixed with singleton/normal | AC; all ordinals incl empty | Different CM1 if empty node removed |
| D05 | Empty input / wrong well-formed root / second root | MALFORMED_XML / UNSUPPORTED_PROFILE / MALFORMED_XML respectively | Separate isolated variants |
| D06 | GPX1.0/wrong URI/missing namespace | UNSUPPORTED_PROFILE | No accidental promotion |
| D07 | Missing version/creator, duplicate supported scalar | INVALID_STRUCTURE | Separate isolated variants |
| D08 | Missing lat/lon | INVALID_COORDINATE | Whole document rejects |
| D09 | lat>90/lon=180/out-of-range negative | INVALID_COORDINATE | No normalization |
| D10 | Exponent/comma/NaN/Infinity/non-numeric coordinate | INVALID_COORDINATE | Whole document rejects |
| D11 | Invalid/nested supported structure or non-whitespace core text | INVALID_STRUCTURE | No element salvage |
| D12 | rte/wpt only versus mixed supported tracks | NO_IMPORTABLE_TRACK / AC+W | Never convert |
| D13 | Foreign trk or track-shaped extension subtree | Ignored; AC+W only with real samples | Cannot smuggle samples |
| D14 | Time no zone/leap-second/bad time; malformed present ele | AC+time W / INVALID_OPTIONAL_VALUE | Time status/null vs fatal ele |

### E — ENCODING

| ID | Minimal bytes/declaration | Expected result | Canonical / identity |
|---|---|---|---|
| E01 | Valid UTF8 no BOM | AC | Golden |
| E02 | Valid UTF8 BOM | AC | CM1= E01; original≠ |
| E03 | UTF16 LE BOM + UTF-16 declaration | AC | CM1= E01 |
| E04 | UTF16 BE BOM, declaration absent | AC | CM1= E01 |
| E05 | Contradicting encoding declaration/BOM | UNSUPPORTED_ENCODING | No canonical draft |
| E06 | UTF16 no BOM | UNSUPPORTED_ENCODING | Required XML UTF16 envelope |
| E07 | Legacy encoding/UTF32/UTF-16LE declaration | UNSUPPORTED_ENCODING | Related labels not auto-accepted |
| E08 | Malformed UTF8 byte sequence | MALFORMED_XML | No replacement character recovery |
| E09 | Broken UTF16 surrogate/truncated code unit | MALFORMED_XML | No canonical draft |
| E10 | XML1.1 declaration/illegal XML1.0 control | UNSUPPORTED_PROFILE / MALFORMED_XML | Isolated variants |

### F — SECURITY

| ID | Minimal attack | Expected result | Security expectation |
|---|---|---|---|
| F01 | DOCTYPE internal subset | SECURITY_REJECTED | Zero external I/O |
| F02 | External DTD URL | SECURITY_REJECTED | No request |
| F03 | General entity with file URI | SECURITY_REJECTED | No file open |
| F04 | General entity with HTTP URI | SECURITY_REJECTED | No network |
| F05 | Parameter entity/external parameter entity | SECURITY_REJECTED | No resolver |
| F06 | Recursive/expanding entity bomb | SECURITY_REJECTED at DTD | No expansion |
| F07 | Undefined entity without DTD | MALFORMED_XML | No fallback resolution |
| F08 | Duplicate QName attribute/unbound prefix | MALFORMED_XML | Strict namespaces |
| F09 | schemaLocation/stylesheet/XInclude in otherwise valid mixed input | AC+W as ignored content | No external I/O ever |
| F10 | Huge ignored attribute/text/extension | LIMIT_EXCEEDED at appropriate G ceiling | Ignored ≠ unchecked |
| F11 | Deep ignored extension | LIMIT_EXCEEDED G05 | All subtrees counted |
| F12 | Pathological numeric string / overflow nonfinite spelling | LIMIT_EXCEEDED G10 or INVALID_COORDINATE | Isolated variants; no float overflow |
| F13 | Adapter cannot prove safe config | SECURITY_CONFIGURATION_UNAVAILABLE | Fail closed before parsing |

### G — RESOURCE LIMITS

G01–G20 in §11 each define three fixtures suffix -below/-exact/-over, with L-1/L/L+1
units. Assertions include exact counter, limit category, no partial published draft,
and unchanged other relevant fields. G11–G20 are future admission/protection fixtures,
not parser implementation requirements. G21 is capacity/aggregate scenario family.

Generator feasibility: for track/segment/sample boundary tests distribute nodes so
other ceilings stay below; tracks may be empty except one sample. For event2M tests,
use minimal comments separated by bounded whitespace (logical units) to fit16 MiB.
Byte padding uses many bounded comments rather than one oversized text token.
Oversized tokens are generated separately, not masked by the byte cap.
Protection families isolate archive entry/metadata/ZIP sums with deterministic
incompressible/compressible payload plans, not fake successful parser output.

### H — MULTI-TERRITORY / DUPLICATE / BOUNDARY IDENTITY

| ID | Scenario | Expected result | Identity / context |
|---|---|---|---|
| H01 | Same bytes twice, explicit confirmation | Both parse AC, two admitted imports | SHA/CM1 same; I/T/S fresh |
| H02 | Same bytes, different filename/URI | Same parse result | Context excluded |
| H03 | Same bytes in Territories A/B | Separate records, target captured independently | CM1 same, ownership/UUIDs distinct |
| H04 | URI/original lost after READY | No reparse required for viewing | IDs/geometry unchanged, FULL unavailable |
| H05 | Restart/restore/rebuild same admitted record | No parser needed on restore | Fixed identities/ordinals preserved |
| H06 | Hide→unregister→show; stale A result during B | Fresh presentation, reject stale result | Canonical unchanged; no SDK identity |
| H07 | Aggregate multi-Territory corpus + G11/G12/capacity | Admission/protection distinguish limits | No accidental app-wide/per-document cap |

## 15. Golden sample identity/material plan

A01 exact readable baseline XML (documentation example, not a fixture file):
```xml
<gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="fixture"><trk><trkseg><trkpt lat="0" lon="0"/></trkseg></trk></gpx>
```

Expected: one track ordinal0, one segment ordinal0, one sample ordinal0;
lat=0, lon=0, elevation=null, rawTime=null, MISSING, UTCseconds/nanos=null;
root creator="fixture"; all optional supported metadata null.

Golden CM1 tree, using the exact §8 framing operators:
```text
root(1:"1.1",2:"fixture",3:null,4:null,5:null,
  6:list[track(1:"0",2:null,3:null,
    4:list[segment(1:"0",2:list[
      sample(1:"0",2:"0",3:"0",4:null,5:null,6:"MISSING",7:null,8:null)
    ])])])])
```
String values mean kind1; null kind0; named records kind2; list kind3.
Every field's length is UTF8 payload byte length per §8, not character count.
This tree + fixed framing defines exact golden bytes without object serialization.
Future cross-implementation fixture must freeze literal bytes/hex and verify independent
construction; no golden digest is invented because CM1 digest is not required.

Identity harness assigns fixed test-only UUIDs (not content-derived):
I=00000000-0000-4000-8000-000000000001,
T=00000000-0000-4000-8000-000000000002,
S=00000000-0000-4000-8000-000000000003,
O=00000000-0000-4000-8000-000000000004.
Sample address=(S,0). Reimport harness uses another fixed I/T/S/O set; restore keeps
the first set. Parser receives no UUID generator or current time.
Original SHA must cover the exact baseline bytes, with encoding/BOM variations tested
separately; no digest value is claimed computed in this documentation task.

## 16. Trust and ownership flow

```text
Untrusted caller-supplied GPX stream
  → parser adapter: strict decoding, no resolver, input ceilings
  → profile validation: supported contexts, counts, whole-document acceptance
  → canonical draft: exact ordered values; tentative until complete
  → comparison material: CM1; original hash supplied by managed-copy owner
  → future import coordinator: captured Territory/Observer, confirmation, UUID allocation
  → future persistence/publication: recoverable READY graph + exact managed original
  → future visible-set producer: canonical reads + device visibility, stale scope gate
  → Stage0 Registry/session: fresh factories, controlled runtime lifecycle.
```

Parser owns interpretation, not URI/ownership/DB. Coordinator owns logical allocation/
publication, not XML author identity. Persistence owns committed canonical truth;
restore never substitutes latest reparse. Presentation owns disposable conversion and
visible selection. Registry parses no GPX and knows no TrackImport/Territory/Area/Room.
CM1/logical identity never depends on MapLibre IDs. Area owns no imported GPX.

## 17. Visible-set residency consequences

Hidden != deleted. Visibility outside canonical geometry/material and protection.
Hide unregisters/removes feature presentation, retaining all canonical data/original.
Show builds fresh presentation objects from persisted canonical values; no URI or
original parsing dependency. SDK objects are neither persistent identity nor content.
No toggle UI, style, Registry API or rendering implementation is designed here.

## 18. Invariants and future obligations

This task changes none of Room14, BackupV8, SnapshotV3, DataStore, Area ownership,
Registry/session/BeeMap semantics, Research overlays, dependencies, version/signing.
Future Stage1 needs explicit data/protection evolution and migration/roundtrip gates
from Stage1A; no migration/schema design is performed in Stage1B.
Stage0 accepted scoped gates remain satisfied; implementation still requires separate
OWNER authorization and any genuinely necessary future gates, not reopening the known
pre-Stage0 blocker. No build/test/emulator/Samsung execution is authorized here.

## 19. Open questions and authority

OWNER DECISIONS REQUIRED BEFORE IMPLEMENTATION: **NONE identified** for this bounded
parser/model contract. No semantic content digest, dedup policy or new user limit
was silently adopted. Technical definitions above are accepted with these clarifications.

OPEN QUESTION / evidence obligations (not new product choices):
- Platform adapter proof of strict malformed-byte/security/counter behavior and
  pre-token allocation risk; fail closed if required capabilities are unavailable.
- Measured aggregate storage/presentation/protection ceilings remain Stage1A work.
- Exact future persistence mapping must preserve canonical decimals/IDs/raw-time
  status, and restore must not reparse; no final physical schema chosen here.
- Freeze independent golden CM1 bytes when fixtures are separately authorized.
If implementation cannot satisfy this contract, report a blocker before loosening it.

## 20. Stage 1B exit criteria and self-review

Design exit: OWNER/reviewer acceptance recorded; inherited decisions are unchanged,
all profile/normalization/identity outcomes have fixtures, references/provenance are
clear and no implementation/files/tests are smuggled into this recording.
Runtime security and future fixture PASS are not design exit claims.

| Review question | Contract answer |
|---|---|
| Q1 accepted/rejected profile implementable? | §§3,5,10,12 fix interpretation; no new product choice |
| Q2 duplicate without filename/URI/time? | Original SHA signal + explicit confirmation; fresh logical IDs |
| Q3 same cases equal? | Compare correct domain; §7 distinguishes SHA,CM1,logical records |
| Q4 different cases distinguishable? | Exact decimals/order/boundaries/framing, full material comparison, no hash collision guarantee |
| Q5 hostile external I/O? | NO; adapter incapable of resolution, fail closed |
| Q6 deterministic numeric ceiling fixtures? | G01–G20 below/exact/over; unspecified G21 has no invented numeric promise |
| Q7 parser needs MapLibre? | NO |
| Q8 viewing needs original after READY? | NO; degraded state and protection refusal preserved |
| Q9 Territory confused with content? | NO; owner context, not CM1 |
| Q10 Registry change required? | NO |

## 21. Explicit non-goals and next action

No production/test code, fixture files/generator, Room/DAO/migration, UI/picker,
backup/snapshot implementation, SDK adapter/rendering, dependency/config/version
changes, device actions or Stage2–4. Documentation-only acceptance commit is separately authorized.

Design acceptance is closed. Next possible action: prepare a separate parser/profile/
security-fixture and canonical-model implementation contract and obtain OWNER
authorization. Do not implement automatically or reopen accepted Stage0 verification.
