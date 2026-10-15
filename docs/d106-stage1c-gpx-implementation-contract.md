# D106 Stage 1C — GPX processing implementation contract

Status: **D106 STAGE 1C — ACCEPTED**.
**OWNER ACCEPTED: 2026-10-13 · DESIGN CONTRACT ONLY.**
Final independent control review: ACCEPT; BLOCKER0/HIGH0/MEDIUM0/LOW1.
R1/R3/R4 CLOSED; M1/M2/M3 regression NONE. The non-blocking LOW about review
identifiers is intentionally retained. No runtime qualification is inferred.
**IMPLEMENTATION NOT AUTHORIZED.** D106 Stage 1 Slice 1 — XML/security capability
proof requires separate explicit OWNER authorization. No executable work is authorized
by this design acceptance.

## 1. Status and scope

Translate [Stage 1A product truth](d106-stage1-gpx-spec.md) and
[accepted Stage 1B technical contract](d106-stage1b-gpx-parser-identity-contract.md)
into a first executable processing slice. Neither contract is redesigned.

In scope for future implementation: raw-input/encoding boundary, XML adapter and
security proof, profile validation, canonical in-memory values, exact decimals,
CM1 encoding, original byte identity, diagnostics, limits/cancellation and fixtures.
This design is not an import/publication service: Room/DAO/migrations, URI picker/UI,
persistent repository, Territory screens, rendering/Registry, backup/snapshot
implementation and Stage 2–4 remain excluded.

Labels: EXISTING/OBSERVED = repository/source inspection; NEW/PROPOSED = future
component or implementation choice; DOCUMENTED = API contract; SOURCE-OBSERVED =
inspected implementation, not runtime proof; REQUIRES PROBE / UNKNOWN / UNSUPPORTED
are not PASS. A design-ready recommendation can still require Slice 1 capability gates.

## 2. Accepted prerequisites and source precedence

Stage 1A/1B ACCEPTED WITH AMENDMENTS in e36b115. Inherit all eight product decisions,
six LOW clarifications, visible-set residency and four provisional budget domains.
Repeated-byte lookup/prompt scope stays outside this parser slice, to be fixed in
the future import/UI coordinator contract. Display name is coordinator context,
not mandatory metadata/name alias. No new OWNER product decision is identified.

Stage0 scoped verification remains accepted; known pre-Stage0 emulator blocker
is neither reopened nor a waiver for new parser/security failures.
Historical foreign-NO-GO notes in acceptance artifacts describe their snapshot;
cleanup6dae733 removed that obsolete uncommitted text under OWNER authorization.

Starting checkout: C:\App\Bee_search_stage0_overlay_registry,
branch task/d106-stage0-overlay-registry,
HEAD6dae73362dd12a5b8110d235fce7c9834e03f405, clean worktree/index, no untracked files.

## 3. Repository observations

| EXISTING / OBSERVED | Consequence |
|---|---|
| One module :app in settings.gradle.kts | Use existing app tasks, no new module |
| app/build.gradle.kts: minSdk29, compileSdk/targetSdk37, Java11 target | java.time/BigDecimal/CharsetDecoder are platform candidates; no language-target change |
| Catalog: AGP9.3.2, Kotlin2.2.10 for declared compose plugin, Gradle wrapper9.5.0 | These are declared versions, not a fresh resolved compiler/build verification |
| Installed Temurin17.0.18+8 release metadata | Available host JDK, not a proof of the actual future Gradle JVM |
| SDK platform/sources android-37.0 only, API37 revision2 | Source evidence for37; no installed29/36 source proof |
| No local.properties in task checkout | Future build environment must supply existing ANDROID_HOME; no config change here |
| No declared XML library; kxml2 cache entry is undeclared | Do not treat cached jar as project dependency |
| domain/model, domain/repository and data feature packages | NEW domain/gpx and data/gpx form a narrow durable GPX boundary |
| MediaFileWriter.kt: source lambda, streamed SHA/length, ensureActive, owned cleanup | Reuse pattern, not its attachment result or immediate-file-publication semantics |
| data/zip/ZipSafety.kt: bounded copy, checked counters, injected cancellation | Mechanical precedent; do not reuse ZIP error codes for GPX or refactor ZIP |
| domain/model/DomainErrors.kt; local feature typed errors | Bounded typed GPX codes fit existing style |
| testImplementation JUnit4.13.2; AndroidJUnitRunner + AndroidJUnit4 | No Robolectric/MockK/Mockito or new dependency required by this plan |
| androidTest assets include src/test/resources | Shared literal corpus/manifest/goldens need no Gradle source-set change |
| SnapshotConformanceDeviceTest.kt reads backupsnapshot/canonical-vectors.json | Existing host/device conformance pattern |
| InstallStateBackupRulesTest.kt uses JDK DocumentBuilderFactory | Existing test-only XML, not a streaming/security production parser |
| LargeMediaIngestTest/ZipSafetyTest inject failure/cancellation streams | Use deterministic streams, cancellation and cleanup assertions |
| AppContainer.kt, managed-file stores and MainMapScreen | Future owners only; this processing slice does not wire feature/UI/storage |

Observed paths are relative to app/src/main/java/org/beesearch/app unless a test
source set is explicitly named. No GPX production or test implementation exists.

## 4. Proposed component architecture

All files/types in this table are **NEW / PROPOSED**. Rows are cohesive responsibilities,
not a requirement for a class per conceptual name. No service/repository hierarchy.

| Owner / proposed path | Input → output / dependencies | Failure and cancellation | Test owner |
|---|---|---|---|
| domain/gpx/GpxCanonicalModel.kt | Pure immutable metadata/track/segment/sample/value types; no Android/Room/UUID allocation | Constructor/factory invariants; never mutable builder alias | GpxCanonicalModelTest |
| domain/gpx/GpxCanonicalValues.kt | Bounded decimal/time text → exact scalar/status; BigDecimal/java.time | Typed field rejection or unsupported-time status; checkpoints around point batches | GpxCanonicalValuesTest |
| data/gpx/GpxInput.kt | Repeatable caller source → counted/digested bytes, strict decoded Reader, encoding envelope | Encoding/IO/byte limit; cancellation before/after reads; owns opened streams | GpxInputTest |
| data/gpx/GpxXmlGuard.kt | Strict decoded characters → bounded parser-facing Reader + per-tag lexical facts/counters | DTD barrier, incremental reference re-emission, literal attribute normalization, lexical duplicates; no tree/QName resolution | GpxXmlGuardTest + device capability probes |
| data/gpx/AndroidGpxXmlReader.kt | Guarded Reader → normalized XML event source; public platform XmlPullParserFactory | Verified settings; per-START namespace/declaration and expanded-attribute duplicate checks before any profile/skip event; typed bounded failures | GpxXmlCapabilityTest / GpxParserDeviceTest |
| data/gpx/GpxProfileParser.kt | Event source → tentative ordered canonical draft through sink; protocol/collector private or colocated | Profile/field/count errors; discard on any failure, check each event/batch | GpxProfileParserTest |
| data/gpx/GpxCm1Encoder.kt | Valid canonical root → streamed exact CM1; no digest | Checked framing lengths/model/output IO; cancellation on traversal/write | GpxCm1EncoderTest |
| data/gpx/GpxParseResult.kt + GpxProcessor.kt | Typed result/diagnostic; orchestrate preflight + guarded parser pass | No partial Accepted; coroutine cancellation rethrows; source-change failure | GpxProcessorTest / device conformance |
| Test-only fixture helpers | Manifest/literals/generated stream → independent expectations | No production fixture code or permissive parser fallback | JVM and Android harnesses below |

GpxInput combines encoding/original-identity mechanics because both consume the same
raw stream. GpxProfileParser owns its small event protocol and draft collector/sink;
do not create one-method wrapper files. CanonicalValues groups related scalar rules.
Guard is a separate independently testable security boundary. If guard/profile files
exceed project cohesion triggers, review decomposition by responsibility, not line count.
No changes expected to AppContainer, Room, MapLibre, profiles, UI or Registry in slices1–5.

## 5. XML technology recommendation and event strategy

**Conditional recommendation:** public Android platform XmlPullParserFactory with
nextToken, preceded by strict byte decoding and an independently bounded lexical
security guard. It is NOT declared security-qualified before Slice1 proof.

| Candidate | Streaming/namespaces | Security and allocation | JVM/dependency cost | Decision |
|---|---|---|---|---|
| Platform XmlPullParser | DOCUMENTED | Token events available; no public pre-token size ceiling | Real adapter needs Android; zero new dependency | Preferred hardened pipeline, REQUIRES PROBE |
| Existing JDK DOM test usage | Whole document | Violates streaming architecture; not security reference | Host only | Rejected for production |
| Platform/JDK SAX | Streaming callback alternative | Resolver/features exist, but large attribute/pre-callback bounds still need guard/proof | Host JDK usable as test-only reference; Android behavior separate | Not a production replacement in this design |
| Cached upstream kxml2 | Separate old artifact | Token buffers grow; not installed project contract | Would add dependency | Not selected |
| Other library | UNKNOWN | Must prove accepted contracts, including allocation | New dependency/compatibility review | Only after a failed capability gate; no silent fallback |

Primary API evidence:

[XmlPullParser](https://developer.android.com/reference/org/xmlpull/v1/XmlPullParser),
[Xml utility](https://developer.android.com/reference/android/util/Xml),
[JDK17 SAX factory](https://docs.oracle.com/en/java/javase/17/docs/api/java.xml/javax/xml/parsers/SAXParserFactory.html).

SOURCE-OBSERVED API37 installed SDK:
- android/util/Xml.java149–154 enables PROCESS_DOCDECL in newPullParser.
- com/android/org/kxml2/io/KXmlParser.java next/nextToken, readDoctype and readUntil
  parse/capture content before a token is returned.
- setFeature accepts namespace/docdecl/relaxed, but not arbitrary validation flags;
  getFeature returns false for unknown features. False alone is not generic capability proof.
- XmlPullParserFactory uses the built-in implementation; its API37 constructor selects
  KXmlParser, and newPullParser applies only true factory features. The utility path
  above explicitly enables DOCDECL/namespaces; the fresh factory path does not inherit
  those defaults. Configure/verify the actual parser, never transfer utility-path settings.
  Built-in provider is API-documented; concrete KXmlParser identity is source-observed,
  not a guarantee for every supported runtime. Never instantiate hidden com.android
  classes or bind a cached library via reflection.

Verify adapter capability before source opening at the processing entry point; pure
input-boundary tests can exercise preflight independently. Use a fresh public factory/
parser per operation, namespaces enabled, PROCESS_DOCDECL
explicitly false and strict/non-relaxed behavior checked before input. Do not call
every imaginable disable feature and swallow unsupported exceptions. Validation is
required inactive: combine documented non-validating/default state, no validation
activation/resolver, source review and actual capability tests. If the adapter cannot
establish that state, return SECURITY_CONFIGURATION_UNAVAILABLE. Unsupported required
namespace/docdecl/strict settings fail closed. Factory false settings can be ignored
by implementations, so verify the created parser itself, not just factory state.

next() skips lexical signals/aggregates text; use nextToken only. Handle START/END,
TEXT/IGNORABLE_WHITESPACE, CDSECT, ENTITY_REF, COMMENT and PI explicitly. Entity tokens
allow only the five predefined XML escapes and valid numeric references; unknown
resolution is rejected, not mapped to empty text. DOCDECL remains defense-in-depth
rejection, not the first DTD barrier. Avoid nextText and unbounded concatenation.

XML declaration is recognized/counted at the encoding/guard boundary because the
platform can consume it without a standalone returned token. Adapter/guard facts
reconcile namespace declarations, lexical attributes and Stage1B logical events.
Do not count callback boundaries as extra text events.

Upstream corroboration: [AOSP Android12 tagged KXmlParser](https://android.googlesource.com/platform/libcore/+/refs/tags/android-security-12.0.0_r40/xml/src/main/java/com/android/org/kxml2/io/KXmlParser.java).
This source and installed37 source are inspection evidence, not API29/37 runtime proof.

## 6. Security capability matrix

DOCUMENTED means an API primitive exists. REQUIRES PROBE means the selected integrated
mechanism still needs proof; none of these rows has runtime PASS in this task.

| ID / requirement | Required mechanism/component | Evidence/status | Runtime verification needed | Failure |
|---|---|---|---|---|
| S01 DTD forbidden | Guard recognizes actual declaration outside quotes/comments/CDATA and rejects before SDK delivery | SDK DOCDECL documented, early barrier REQUIRES PROBE | F01/F02/F05/F06 delivery trace | SECURITY_REJECTED |
| S02 internal entities | Same DTD barrier; no replacements registered | REQUIRES PROBE | F01/F06 | SECURITY_REJECTED |
| S03 external entities | DTD/custom-ref barrier, no resolver | REQUIRES PROBE | F02/F03/F04 zero I/O | SECURITY_REJECTED |
| S04 parameter entities | Reject declaration before parsing subset | REQUIRES PROBE | F05 | SECURITY_REJECTED |
| S05 external schemas | No validation/schema processor or locator, ignored schema attributes | REQUIRES PROBE | F09 | Capability failure if I/O possible |
| S06 network/file resolution | Reader-only API, guard, zero resolver functionality | SOURCE-OBSERVED, REQUIRES PROBE | F02–F04/F09 + local observers | Stop, no fallback |
| S07 malformed encoding | CharsetDecoder REPORT, EOF/flush, strict scalars | DOCUMENTED, REQUIRES PROBE | E08/E09/truncation on Android | MALFORMED_XML |
| S08 namespaces/strict attributes | Verified SDK namespace feature + guard lexical duplicates + adapter declaration/expanded-name validation (§8.2); guard attribute normalization (§8.3) | SDK alone insufficient in inspected37; integrated checks REQUIRES PROBE | B04/B08/D06/D13/F08 alias/declaration/whitespace variants | MALFORMED_XML/UNSUPPORTED_PROFILE |
| S09 bounded depth | Guard root-depth accounting before delivery, reconcile events | NEW, REQUIRES PROBE | G05/F11 | LIMIT_EXCEEDED |
| S10 bounded attrs | Guard counts all lexical attrs incl xmlns before65th delivery; bounded current-tag name set and adapter reconciliation (§8.2) | NEW, REQUIRES PROBE | G06/F08 incl dropped xmlns attrs | LIMIT_EXCEEDED or MALFORMED_XML for duplicates |
| S11 bounded text/token | Guard counts decoded logical values; incremental references emit bounded equivalent spelling to SDK (§8.1), with no raw-reference ceiling | Native pre-token limit UNSUPPORTED; integrated guard REQUIRES PROBE | B08/G09/F10 long-small refs, CDATA, chunk variants | LIMIT_EXCEEDED; malformed refs MALFORMED_XML |
| S12 bounded events | Guard normalized facts + adapter reconciliation | NEW, REQUIRES PROBE | G07 chunk variants | LIMIT_EXCEEDED |
| S13 bounded bytes | Checked raw counted stream before delivery/hash update | EXISTING pattern, REQUIRES PROBE | G01 incl BOM | LIMIT_EXCEEDED |
| S14 numeric abuse | G10 before decimal/time conversion, G09 before SDK; grammar first | NEW, REQUIRES PROBE | D10/F12/G10 | LIMIT_EXCEEDED/INVALID_COORDINATE |
| S15 cancellation | Injected checkpoint + coroutine context at reads/events/batches/output | EXISTING pattern, REQUIRES PROBE | Deterministic cancellation streams | Rethrow cancellation, discard |
| S16 no partial Accepted | Private collector/abortable draft sink; EOF/identity check before return | NEW, REQUIRES PROBE | Late failure/cancel in S5 | Discard/abort |
| S17 bounded diagnostics | Fixed categories/counts + first supported address/category, no excerpts | Stage1B contract, REQUIRES PROBE | Huge hostile content | Bounded typed error |
| S18 no uncontrolled pre-token allocation | Bounded decoder/guard buffers, incremental reference state + short re-emission, G09 values/G06 attrs/G05 depth before SDK growth; no input-sized reference buffering (§8.1) | Native ceiling UNSUPPORTED; structural and runtime proof REQUIRES QUALIFICATION | B08/F06/F10/G04/G09 delivery/allocation traces; long raw ref must not grow SDK capture | Block qualification on unbounded state/delivery or semantic mismatch |

S18 cannot be closed with one flag, source-buffer size or post-event getText.length.
A fixed internal input buffer does not bound StringBuilder token capture.

GUARD_SDK_AGREEMENT (§8.4) is a mandatory cross-cutting Slice1 gate for all
security/canonicalization-relevant guard → actual-provider interactions above.

## 7. Encoding, input identity and two-pass boundary

PROPOSED GpxInputSource: caller-owned repeatable source opener for completed immutable
bytes, not URI/Context. Each opened stream is owned/closed by the processor. This fits
Stage1A copy-before-parse and existing source-lambda conventions. Tests use fixed byte
arrays/temporary files; a one-shot provider stream belongs to future managed-copy
coordination, not direct parser input. No managed-copy service is implemented here.

Processing:

1. Preflight pass: counted/digested raw bytes → BOM classification → strict decoder →
   bounded lexical guard to real EOF. No native parser or canonical output.
2. Parser pass: reopen same source → same counted/strict/guarded boundary →
   platform tokenizer → profile engine/private sink.
3. Require both lengths/SHA values and optional caller-provided original descriptor
   to match; source changes are INPUT_IO_ERROR with bounded SOURCE_CHANGED detail.
   The second pass stays guarded: a changed source cannot sneak DTD into SDK.
4. Only after END_DOCUMENT, decoder EOF/flush, identity validation, final cancellation
   check and whole-profile acceptance return Accepted. Two-pass cost is measured,
   not optimized away before correctness evidence.

Counters/limits reset per pass: validate the same logical document twice, do not add
both passes into G01/G07 or duplicate canonical samples. Reconcile facts/identity;
additional read cost is overhead to measure, not a new input capacity ceiling.

This creates deterministic preflight-before-profile validation phases. For multi-defect
input preserve first observed failure within this normalized pipeline; isolated-fixture
categories/counts must be chunk-independent. Do not synthesize SDK exception strings
into diagnostic precedence or allow read-ahead to change ceiling counters.

Raw counter/hash precedes BOM removal or decoding; EOF is necessary to finalize it.
A limit witness byte can detect L+1 but is not accepted/delivered as valid input.
Checked Long counters, bounded read requests and no spin on zero-length reads.

Use CharsetDecoder.onMalformedInput(REPORT)/onUnmappableCharacter(REPORT), not String
constructor/replacement decoding or native setInput(InputStream,autodetect).
[CharsetDecoder API](https://developer.android.com/reference/java/nio/charset/CharsetDecoder).

UTF8 optional BOM/declaration; UTF16 LE/BE BOM mandatory, optional UTF-16 declaration.
Implement exactly Stage1B accepted labels/envelope. Unsupported encodings and mismatch
return UNSUPPORTED_ENCODING; invalid declarations/UTF return the accepted categories.
Strip only actual BOM from decoded parsing stream; keep it in raw identity.
Bound declaration sniffing with inherited G09 token/G01 byte limits, quote-aware; don't
invent a smaller declaration capacity. Feed the already validated Reader to SDK.

Strict decoder validates bytes/scalars without XML whitespace conversion. Guard owns raw
XML line-end handling and literal attribute normalization before SDK delivery (§8.3).
Character references retain their referenced scalar semantics through short reference
re-emission (§8.1), never through blanket post-parser whitespace replacement. Preserve
supported strings without trim/NFC; only accepted numeric/time analysis collapses edges.
Original raw bytes and SHA/length remain unchanged by this parser-facing representation.

## 8. Guard, streaming and resource model

Guard is a bounded XML lexical scanner, **not a second tree/GPX/namespace parser**.
States cover markup prefix, start/end tag, names, attribute quote/value, comment,
CDATA, PI/XML declaration, text and allowed reference. Its closed responsibilities:
strict lexical/scalar safety, DTD barrier, bounded input delivery/token counters,
incremental references, literal attribute normalization, current-tag lexical duplicate
checks and cancellation. It does not resolve prefixes, repair malformed syntax, choose
GPX contexts, build canonical nodes or allocate logical identity.
SDK tokenization/namespace resolution remains a candidate, not the sole strictness
authority: the adapter owns the additional checks in §8.2; profile owns GPX rules.

Recognize declarations only in markup state; DOCTYPE text inside CDATA/comment/string
must not false-positive. Keep short unresolved marker lookahead private until classified;
reject actual DOCTYPE before a complete dangerous marker reaches SDK. No regex over
arbitrary raw bytes and no substring-based DTD detector.

### 8.1 M1 — references and parser-visible bounds

G09 counts decoded Unicode scalars in the accepted logical values, not the number of
digits in a raw numeric-reference spelling. No separate raw-reference ceiling is added.
G01 still bounds every original byte, including arbitrarily long leading-zero spelling
within the accepted document budget. Use a streaming reference state: distinguish
decimal `&#...` / hexadecimal `&#x...` and the five predefined names; require digits
and terminating semicolon; validate grammar and XML1.0 legal scalar. Track the numeric
value with checked bounded accumulation against the XML scalar range, not a digit string
or BigInteger. Leading zeros require no growing state; signs, uppercase X introducer,
invalid digits, missing semicolon, surrogates and illegal scalars reject MALFORMED_XML.
Unknown named references reject without retaining an attacker-sized name or resolving it.

Re-emission is limited to character data and element attribute values where XML
references are legal. Do not rewrite names, markup or XML-declaration pseudo-attributes;
invalid reference syntax there is rejected, never repaired into valid XML.
Hold the reference from SDK until complete validation, then emit a short equivalent
numeric spelling (e.g. `&#x41;`) or the verified predefined reference. Never emit the
resolved character as raw markup: referenced quote/less-than/ampersand and TAB/CR/LF
must keep reference semantics in their original text/attribute context. No reference
rewriting occurs in comments, CDATA or PI where that spelling is literal content.
The emitted spelling is bounded by the finite XML scalar range, not a new acceptance
limit; original spelling length is subject only to inherited applicable budgets.
Count its decoded scalar once in the full logical value/run; re-emission adds no G07
event and does not reset G09/G10. Preserve non-recursive semantics such as `&amp;#65;`.
Malformed reference produces no replacement/recovery and no Accepted result.

This representation affects only the parser-facing Reader in both existing passes.
Original bytes are never normalized/transcoded; raw SHA/length are calculated before
this transformation. No CM1/dedup/UUID/persistent identity rule changes.
Guard buffers and emission queue are bounded; it must not concatenate original reference
digits, accumulate a whole document, or enqueue an unbounded read-ahead facts history.
Check cancellation throughout reference consumption, including long zero runs.

S18 qualification must structurally show that downstream reference capture cannot grow
with original digit count; delivery/allocation probes supplement this argument. Ordinary
names/comments/PI/attribute values/logical text remain bounded by G09 before SDK growth,
attributes by G06 and nesting by G05. Derive parser-facing storage bounds from those
existing ceilings, bounded transport buffers and fixed reference output; do not turn
a transport-buffer size into an accepted-input ceiling. A memory measurement alone is
insufficient. If semantic equivalence or bounded allocation cannot be established,
Slice1 FAILS; stop rather than impose a new raw limit or pass giant lexemes to SDK.

### 8.2 M2 — strict attribute and namespace checks

All layers run for every element, including ignored subtrees, before the profile engine
can skip content. [XML namespace attribute uniqueness](https://www.w3.org/TR/xml-names/#uniqAttrs)
applies independently of supported GPX extraction.

| Concern | Explicit owner / check |
|---|---|
| Lexical QName spelling | Guard validates bounded name lexeme, counts each attribute incl xmlns, rejects repeated exact lexical attribute name in the current tag |
| Namespace declarations | Guard classifies `xmlns` / `xmlns:prefix` lexically and rejects duplicates before SDK can discard them; retains only bounded current-tag facts, no binding resolution |
| Declaration bindings and resolved names | SDK supplies namespace resolution; adapter validates declaration/QName constraints and checks per-START lexical facts against the actual namespace-declaration range/attribute list |
| Duplicate expanded ordinary attributes | Adapter enumerates all resolved attrs and rejects duplicate exact (namespaceURI, localName) pairs; unprefixed attrs use empty namespace, not the element default namespace |
| GPX fields/order/ordinals | Profile engine consumes only already checked events; no duplicate-rule exception for ignored content |

Distinct prefixes bound to the same URI do not distinguish attributes: `a:x` and
`b:x` with both prefixes bound to `u` collide as `{u}x`. Equal local names in different
namespace URIs remain distinct. Duplicate same-prefix/default namespace declarations
are caught lexically; legal different-prefix aliases are allowed. Adapter must validate
unbound/reserved/invalid namespace bindings, not trust SDK success as strictness proof.

Current-tag sets/facts are bounded by G06 and G09; expanded-name checks run on at most
the already admitted attributes. Reconcile lexical count with ordinary attrs plus
namespace declarations; no unbounded namespace/event side channel. If provider data
cannot support this verification, fail closed as SECURITY_CONFIGURATION_UNAVAILABLE.
Duplicates/malformed namespaces reject MALFORMED_XML; no lookup-first-wins behavior.
Inspecting API37 parseStartTag/adjustNsp did not establish SDK duplicate rejection;
F08 and actual runtime qualification must prove the integrated checks.

### 8.3 M3 — literal versus referenced attribute whitespace

Guard, while lexical origin is available, implements the mandatory
[XML attribute normalization](https://www.w3.org/TR/REC-xml/#AVNormalize):
raw CRLF becomes one LF and raw lone CR becomes LF; inside quoted attribute values,
each resulting literal LF or literal TAB becomes SPACE (literal SPACE stays SPACE).
No trim or collapse of space runs for these DTD-free undeclared/CDATAtype attributes.
Outside attribute values, raw line-end normalization does not replace TAB by SPACE.

Numeric references producing TAB/CR/LF remain references in the parser-facing stream
(§8.1), bypass literal-whitespace normalization and resolve to those exact characters.
Thus creator with literal TAB/CR/LF/CRLF yields a space, but creator with `&#9;`,
`&#13;` or `&#10;` retains the referenced character. Never replace TAB/CR/LF blindly
after SDK parsing: provenance would already be lost. SDK adapter must verify preserved
reference results and deliver normalized values unchanged; profile performs no second
XML whitespace pass (only the accepted field-specific decimal/time edge handling).

Decoder carries split byte sequences/surrogates; guard carries pending raw CR, quotes
and reference state across chunks. UTF8/allowed UTF16 produce the same XML values.
The XML-declaration encoding check still refers to original encoding, not a fictitious
encoding of this Reader. Slice1 host/device vectors must assert literal-vs-reference
differences as well as equivalent CR/LF/CRLF normalization and supplementary scalars.

CDATA/text/entity adjacency is coalesced exactly as Stage1B; comments/PI break runs.
Logical counters follow normalized original XML semantics, not transformed stream
length or SDK callback fragmentation. Depth/events/limits cover ignored content too.

Reconcile guard and adapter counters at token boundaries/end; mismatch is a technical
blocker, not a silent alternate interpretation. Use latched bounded guard failure so
SDK wrapping an IOException cannot hide the true limit/security cause.
### 8.4 Guard → SDK semantic agreement invariant — R1

For strict decoder → guard/transducer → parser-facing Reader → actual Android provider,
no security- or canonicalization-relevant construct may reach SDK consumption before
every required pre-SDK classification, normalization and bound has been applied.
This does not move namespace resolution or GPX semantics into the lexical guard.

**GUARD_SDK_AGREEMENT:** Slice1 must verify agreement between the guard contract and
actual provider interpretation of classified/bounded parser-facing material. Any
security- or canonicalization-relevant disagreement means **CURRENT XML ARCHITECTURE
NOT QUALIFIED → SLICE1 FAIL → STOP current XML qualification**, with a separate
technical decision for a replacement; no weakened Stage1B, hidden limit, automatic
dependency replacement or expansion into a second tree/profile parser.

Agreement covers construct classification, normalized values, reference semantics,
namespace/declaration and expanded-attribute facts, literal versus referenced whitespace,
and logical lexical/token boundaries that affect security/canonicalization. Bind it
to DTD/declaration delivery, §8.1 re-emission, §8.2 namespace/duplicate checks,
§8.3 normalization and §16 chunk/encoding probes, including ignored content.
Normalize permissible SDK callback fragmentation before semantic comparison; literal
callback identity is not required. Specified counters/token/event totals must agree,
but their equality is **necessary where specified, not sufficient**: equal totals can
hide different values or classification. Independent expectations and delivery traces
must establish the relevant semantic agreement, without building a second XML parser.

No final public result until whole document accepted. No blanket catch Throwable that
turns OOM or programming defects into normal invalid-file success.

Limits exactly Stage1B G01–G10; G11–G20 and capacity domain G21 are future-owner gates.
A named GpxSafetyLimits value may be colocated in GpxInput/Guard; default values equal
Stage1A/1B, no raised/lowered production ceilings. Small injected values only exercise
counter algorithms; actual G triplets must use the inherited defaults.

In-memory collector uses owned builders, freezes immutable lists only at acceptance.
For100000 samples, live sample/decimal/raw-time objects plus list references dominate;
do not retain XML/DOM/SDK events or BigDecimal scratch per sample after normalization.
A future abortable sink can spool private draft batches; parser never publishes Room.
On rejection/cancel all private builders/sink references are discarded/aborted.

No invented RAM budget. Measure peak heap, allocations, runtime and cancellation with
near-limit and aggregate corpus on relevant runtimes. Guard scratch is bounded by
depth/attribute/token ceilings, canonical accumulation is O(samples+supported text).
Near-limit rejection/qualification failure uses the objective conditions in §16.2,
including OOM/uncontrolled growth; no hidden lower capacity or silent point omission.

## 9. Concrete canonical Kotlin model design

NEW types, conceptual field/type design only, no Kotlin implementation:
| Type | Fields / ownership / equality |
|---|---|
| GpxCanonicalDraft | declaredGpxVersion:String=1.1, parserProfileVersion:fixed token, metadata, ordered List<Track>; owned immutable value, no UUID/Territory/display-name field |
| GpxMetadata | creator:String, sourceName/sourceDesc/authorName:String?; exact decoded text, null distinct from empty |
| GpxTrack | ordinal:Int, name/description:String?, segments:List<Segment>; zero-based ordered parent-local index |
| GpxSegment | ordinal:Int, samples:List<Sample>; empty allowed; immutable boundary |
| GpxSample | ordinal:Int, lat/lon:GpxDecimal, elevation:GpxDecimal?, time:GpxSampleTime; no UUID |
| GpxDecimal | validated canonicalPlain:String with private/internal construction; structural equality of canonical text |
| GpxSampleTime | raw:String?, status enum MISSING/UTC/NO_ZONE/UNSUPPORTED, epochSeconds:Long?, nanos:Int? |
| GpxOriginalContentIdentity | byteLength:Long, sha256 lowercase ASCII hex; separate successful-source descriptor |
| GpxParseResult | Accepted(draft,originalIdentity,diagnostics) or Rejected(category,boundedContext); no partial draft |
| GpxDraftSink/collector | Internal tentative event/batch consumer with accept/abort lifecycle; not a database repository |

Declared GPX version supplies CM1 root tag1; parser-profile provenance is separate
context compatible with CM1 version1, not an added CM1 field or logical identity.

Model factories enforce ordinal/list consistency, valid scalar strings, time null/status
coherence and nanos0..999999999. Defensive copies/ownership transfer prevent mutable
aliases; Kotlin read-only List alone is not sufficient. Root structural equality is not
logical import identity; diagnostics and original bytes are separate equality domains.

No parser-generated UUID/time, Android Context/Uri, Room Entity or MapLibre object.
Future coordinator/persistence adds import/track/segment IDs and source address.
Sample address remains SegmentUUID+ordinal. Display name stays future envelope context.
Sink protocol is colocated with profile engine, not a generalized transaction framework.

Time helper uses java.time strict parsing plus explicit Stage1B checks for zone,
year/offset/fraction/calendar and24:00 special case; no default-device timezone,
lenient rollover, timestamp sort or truncation. Retain raw text even when unsupported.
Boundary/extreme calendar vectors must verify accepted Stage1B semantics independently.

## 10. Exact-decimal implementation strategy

Use platform/JDK BigDecimal(String) **after** bounded lexical grammar validation and
mandatory XML edge-whitespace collapse. No Double, exponent, comma, locale parser,
NaN/Infinity or MathContext rounding. Range comparisons are BigDecimal.compareTo
against exact decimal constants, lat[-90,90], lon[-180,180).

Normalize zero to canonical0; otherwise stripTrailingZeros and toPlainString.
Persist only validated canonicalPlain in the draft wrapper; BigDecimal is temporary
comparison/normalization state. Wrapper equality/hashCode follows normalized text,
not scale-sensitive unnormalized BigDecimal.equals.
[BigDecimal API](https://developer.android.com/reference/java/math/BigDecimal).

Preserve excessive precision within inherited lexical/token ceilings. G10 applies to
decoded original coordinate/time string before trim; elevation uses G09/input bounds.
No arbitrary numeric precision threshold or spatial rounding. Guard bounds pathological
text before allocation/BigDecimal work; scale/length arithmetic is checked.
Future renderer's Double conversion cannot change canonical values or CM1.

## 11. CM1 encoder implementation

Use exactly Stage1B §8, no new format/digest. GpxCm1Encoder writes to caller OutputStream:
ASCII BEEGPXCM + u16 version1; tags u16, kind byte, length u32 big-endian, UTF8 scalar,
null kind0/length0, record kind2, list kind3 with count and E(1,record) items.

Two deterministic traversals: checked size calculation, then streaming write.
Cache only bounded structural sizes for tracks/segments as useful; do not buffer the
whole material or serialize/hash an object/ordinary JSON. Fixed ascending tags,
XML list order, minimal ASCII integers, canonical decimal text, explicit nulls.
Strict UTF8 string encoding, no BOM/replacement for invalid constructed models.
Reject u32/checked-Long overflow; never wrap.

Typed encoder/model/output failure is distinct from GPX admission category. Caller owns
partial output cleanup; encoder does not claim filesystem atomicity. Cancellation
propagates at traversal/write boundaries.

Independent oracle: Stage1B A01 tree is hand-authored; freeze reviewed literal hex/bytes
with manually checked offsets/lengths and an independent test-only framing calculator.
Production encoder must not generate its own expected golden. Add null/empty, nesting,
tag/order, Unicode, integer/decimal and overflow vectors. JVM proves these pure encoder
assertions; device parity is optional for pure logic, while actual XML→model integration
still requires Android evidence under §15.
No CM1 SHA, UUID, uniqueness, dedup or persistent-source-key use.

## 12. Original exact-byte identity boundary

GpxInput combines SHA256 MessageDigest with checked raw length; includes BOM,
declaration, comments, whitespace and unsupported content. Decoder/CM1 receive no
authority to rewrite/hash a normalized replacement. Primitive is available already
in MediaFileWriter/ZipSafety, not a reason to invoke their managed-file publication.

Future sequence remains picker target capture → managed staging copy+identity →
closed exact-byte source → this processor → future recoverable publication/READY.
Parser acceptance is not persisted import success. No copy/store/Room service here.
Expected identity from staging and processor's successful passes must match.

Test known independent SHA vectors and literal fixture byte identity, chunk invariance,
zero reads/short read/IO/changed repeatable source. Original identity is finalized only
after complete input; no success hash for partial/rejected input. Byte-duplicate
lookup/prompt scope remains future coordinator contract, not CM1 behavior.

## 13. Result/diagnostics API

Stable machine-readable categories from Stage1B; internal enum naming is proposed.
| Category | Trigger / owner | Bounded context and assertions |
|---|---|---|
| ACCEPTED | Complete preflight + profile + identity/EOF | Complete draft/counts, independent canonical tree |
| UNSUPPORTED_PROFILE | Root QName/version/XML envelope | Fixed reason, no hostile URI excerpt; D05/D06/E10 |
| UNSUPPORTED_ENCODING | BOM/declaration/label mismatch | Encoding reason only; E05–E07 |
| MALFORMED_XML | Strict UTF/scalars/grammar/undefined refs | Numeric location, fixed reason; E08/E09/F07/F08 |
| INVALID_STRUCTURE | Supported context/cardinality/order | Supported path enum + ordinals; D07/D11 |
| INVALID_COORDINATE | Missing/invalid/range decimal | Field/address, no raw value; D08–D10 |
| INVALID_OPTIONAL_VALUE | Present invalid ele | Field/address; D14 |
| NO_IMPORTABLE_TRACK | Complete supported sample count0 | Counts only; D01–D03/D12 |
| SECURITY_REJECTED | Guard DTD/custom declaration | Fixed construct code; F01–F06 |
| SECURITY_CONFIGURATION_UNAVAILABLE | Required adapter capability not established | Capability code, no parser exception; F13 |
| LIMIT_EXCEEDED | Responsible input/profile boundary | Limit ID/domain/count, overflow-safe; G01–G10 |
| CANCELLED / INPUT_IO_ERROR | Operation cancellation/read/close/source-change | No public partial draft; deterministic injected failure |

Fixed warning categories UNSUPPORTED_CONTENT/TIME with count and first supported
address/category. No attacker-defined category names or huge raw exception/position
description. Platform getPositionDescription may contain input; do not expose/log it.
UI wording/mapping is future work.

Preserve coroutine CancellationException as existing repo conventions require:
finally abort private sink, rethrow; future operation owner may classify CANCELLED.
Do not catch it as malformed XML or return a delayed Accepted. CPU loops check context
per events/batches; before/after blocking reads, but don't promise to interrupt arbitrary
provider IO. Future provider cancellation belongs to the managed-copy owner.

## 14. Fixture infrastructure and oracle independence

NEW future corpus: app/src/test/resources/gpx/manifest.json plus small literals under
`gpx/input/<ID>-<variant>.gpx`, raw encoding/security vectors where needed, hand-authored
canonical expectations and reviewed CM1 hex under gpx/golden/. All are planned only.
Existing androidTest asset source-set shares these resources unchanged.

Manifest declares ID, variant, mechanism, expected category/counts/canonical relation,
layer and responsible slice. B/E shared byte pairs retain independent assertions.
Generated large corpus: test-only deterministic streams/templates, explicit N and
known formulas; no random seed/current time/parser-produced expected output.
No requirement to create92 files or20 large triplet archives.

JVM classes NEW under app/src/test/java/org/beesearch/app/{domain,data}/gpx:
CanonicalModel/Values, Input, XmlGuard, ProfileParser, Cm1Encoder, Processor tests.
Test-only JDK SAX reference adapter can run real literals through the same profile
engine; JAXP secure settings/resolver denial must be supported/checked. It is not
Android compatibility proof or a production alternative. Hand event tests supplement,
not replace, real source fixtures. No Robolectric or cached kxml jar dependency.

Android classes NEW under app/src/androidTest/java/org/beesearch/app/data/gpx:
GpxXmlCapabilityTest, GpxParserDeviceTest, GpxResourceDeviceTest.
They use real platform reader and shared literal/expectation assets. Selected large
templates are generated within test-owned memory/streams; small host/device helpers
may differ but must obey the same independently declared formulas. No Gradle test-code
source sharing, production fixture code or debug UI is required.
Heavy resource measurements opt in (proposed beeGpxResources=true); SKIPPED isn't PASS.
Core Java XML security/conformance must run explicitly without MapLibre/native fixture.

## 15. Complete92-family implementation mapping

J=pure JVM core + secure test-only JDK reference where real XML is needed.
A=actual Android platform pipeline; M=resource measurement; FUTURE=outside slices1–5.

**Proof criterion — R3:** JVM evidence is sufficient for an assertion that depends only
on deterministic platform-independent Bee Search logic, isolated from Android XML
behavior. This includes exact decimal, immutable model invariants, CM1 framing and
independent oracle, canonical ordering/permutation/reversal, and pure profile logic
over already-normalized abstract events. An independent JVM test may use hand-authored
canonical trees/events; it does not thereby prove XML integration.

Android is mandatory for assertions depending on actual provider/factory/configuration,
DTD/entities, namespaces/tokenization, parser-facing references/attribute normalization,
lexical/expanded duplicates interacting with provider output, decoder→guard→provider,
read-ahead/S18, XML-triggered I/O, or Android blocking/cancellation. JVM/SAX never
discharges these assertions. Android execution of a pure assertion is optional
regression/conformance and must not block acceptance of its JVM-proven component.

Mixed families are split by assertion, not converted wholesale to J-only. In rows below,
J(core)/A(XML) denotes independent JVM-sufficient core assertions and mandatory Android
XML→same-result assertions. Other J+A rows follow the same criterion whenever a pure
subset exists; A is mandatory only for the provider-dependent subset, not every helper.
No pure subset removes the family's XML integration obligation.
All outcomes inherit Stage1B; no IDs/counts/limits changed. Exact subset/full split
for H-family prevents false parser claims for persistence/UI/protection assertions.
External dependencies listed here are existing JDK/SDK APIs or explicitly future
project owners, never new libraries.

| Family | Fixture mechanism | Expected assertion | Layer | Slice/owner | Dependency/runtime gate |
|---|---|---|---|---|---|
| A01 | literal + hand tree/CM1 hex | singleton/nulls/golden bytes | J(core)/A(XML) | S2/S3 | J independent tree/CM1; A real singleton extraction |
| A02 | hand normalized events + literal | ordered three-point canonical/profile output; actual XML order | J(core)/A(XML) | S2/S3 | J order logic; A tokenizer→profile |
| A03 | canonical segments + literal | two segments/gap preserved; XML boundary extraction | J(core)/A(XML) | S2/S3 | J model/CM1; A XML boundaries |
| A04 | canonical tracks + literal | two ordered tracks; actual extraction | J(core)/A(XML) | S2/S3 | J order/model; A XML |
| A05 | explicit values + literal | supported metadata/ele/raw time values and extraction | J(core)/A(XML) | S2/S3 | J value/model rules; A extraction |
| A06 | scalar vectors + UTF8 literal | Unicode/text equality, no NFC | J(core)/A(XML) | S2/S3 | J string/CM1; A decoding/tokenization |
| A07 | decimal vectors + literal | exact legal coordinate endpoints | J(core)/A(XML) | S2/S3 | J decimal/range; A XML integration |
| A08 | literal | nulls, duplicate/non-monotonic times not sorted | J+A | S3 | SDK |
| A09 | time vectors + literal | offset/nanos/raw precision | J(core)/A(XML) | S2/S3 | J time helper; A extraction |
| B01 | paired literals | CM1 equal, original bytes differ | J+A | S3 | SDK |
| B02 | paired BOM variants | CM1 equal; SHA different | J+A | S1/S3 | strict decoding |
| B03 | declaration variants | same CM1 for accepted envelopes | J+A | S1/S3 | SDK envelope |
| B04 | prefix variants | QNames unchanged, CM1 equal | J+A | S3 | namespace adapter |
| B05 | comment variants | CM1 equal, event counts correct | J+A | S3/S4 | SDK tokens |
| B06 | decimal vectors + XML lexical variants | numeric lexical equivalence, exact CM1 | J(core)/A(XML) | S2/S3 | J decimal/encoder; A decoded XML scalar boundary |
| B07 | ignored-content pair | warning permitted, CM1 unchanged | J+A | S3 | opaque skip |
| B08 | attribute/CDATA/entity variants; long-small numeric refs; literal/reference whitespace controls | equivalent variants keep values/CM1; literal TAB vs referenced TAB deliberately differ, with independent expected strings | J+A | S1/S3 | guard re-emission/normalization + SDK values |
| B09 | UTF8/UTF16 pair | CM1 equal, byte identity different | J+A | S1/S3 | strict decoder |
| C01 | exact canonical decimal pair + literal pair | different exact values/CM1 without Double | J(core)/A(XML) | S2/S3 | J exact values/CM1; A XML extraction |
| C02 | canonical permutation + XML pair | ordered CM1 differs | J(core)/A(XML) | S2/S3 | J traversal/order; A XML order |
| C03 | canonical reversal + XML pair | direction-sensitive CM1 | J(core)/A(XML) | S2/S3 | J ordered data; A XML extraction |
| C04 | canonical split + XML pair | same points, different segment CM1 | J(core)/A(XML) | S2/S3 | J boundaries/CM1; A XML gaps |
| C05 | canonical permutation + XML pair | ordinals/boundaries differ | J(core)/A(XML) | S2/S3 | J order/CM1; A XML extraction |
| C06 | canonical metadata pair + XML pair | supported metadata changes CM1 | J(core)/A(XML) | S2/S3 | J values/CM1; A decoded extraction |
| C07 | ignored-metadata pair | SHA differs; CM1 equal | J+A | S3 | opaque skip |
| C08 | canonical elevation pair + XML pair | null/zero/different decimal distinguished | J(core)/A(XML) | S2/S3 | J values/CM1; A XML extraction |
| C09 | time/value pair + XML pair | raw/UTC retained; CM1 differs | J(core)/A(XML) | S2/S3 | J time/model/CM1; A extraction |
| D01 | empty-root literal | NO_IMPORTABLE_TRACK | J+A | S3 | SDK |
| D02 | only track/no segment literal | zero samples rejected; no D04 usable content | J+A | S3 | SDK |
| D03 | only empty-segment literal | NO_IMPORTABLE_TRACK | J+A | S3 | SDK |
| D04 | mixed empty/usable literal | all empty ordinals preserved | J+A | S3 | SDK |
| D05 | three isolated literals | empty/second root MALFORMED, wrong root UNSUPPORTED | J+A | S1/S3 | grammar |
| D06 | version/URI variants | UNSUPPORTED_PROFILE | J+A | S3 | namespace adapter |
| D07 | normalized event variants + literals | missing/duplicate supported fields INVALID_STRUCTURE | J(core)/A(XML) | S3 | J profile cardinality; A actual event integration |
| D08 | normalized point events + literals | missing lat/lon INVALID_COORDINATE; no partial draft | J(core)/A(XML) | S3 | J profile required fields; A XML extraction |
| D09 | decimal vectors + literals | exact out-of-range rejection | J(core)/A(XML) | S2/S3 | J range helper; A XML scalar boundary |
| D10 | numeric vectors + literals | comma/exponent/nonfinite rejected | J(core)/A(XML) | S2/S3 | J grammar/helper; A XML scalar boundary |
| D11 | nested/core-text variants | INVALID_STRUCTURE, no salvage | J+A | S3 | grammar/profile |
| D12 | routes-only/mixed literals | NO_IMPORTABLE_TRACK or ACCEPTED+warning | J+A | S3 | opaque skip |
| D13 | foreign/extension literals | no local-name promotion/smuggled samples | J+A | S3 | namespace adapter |
| D14 | time/ele vectors + literals | time warning/null vs fatal ele | J(core)/A(XML) | S2/S3 | J values/profile; A XML extraction |
| E01 | literal UTF8 bytes | accepted, baseline CM1 | J+A | S1/S3 | strict decoder |
| E02 | literal UTF8 BOM bytes | accepted, same CM1 as E01 | J+A | S1/S3 | strict decoder |
| E03 | deterministic UTF16LE bytes | accepted BOM/declaration, same CM1 | J+A | S1/S3 | strict decoder |
| E04 | deterministic UTF16BE bytes | accepted BOM/no declaration | J+A | S1/S3 | strict decoder |
| E05 | mismatch byte vectors | UNSUPPORTED_ENCODING | J+A | S1 | envelope |
| E06 | UTF16 without BOM bytes | UNSUPPORTED_ENCODING | J+A | S1 | envelope |
| E07 | unsupported encoding vectors | UNSUPPORTED_ENCODING, no expansion | J+A | S1 | envelope |
| E08 | malformed UTF8 bytes | MALFORMED_XML, no replacement | J+A | S1/S4 | decoder runtime |
| E09 | malformed UTF16 bytes | MALFORMED_XML, no replacement | J+A | S1/S4 | decoder runtime |
| E10 | declaration/control variants | XML1.1 UNSUPPORTED; control MALFORMED | J+A | S1/S3 | grammar/scalar guard |
| F01 | internal-DTD literal | SECURITY_REJECTED before SDK | J+A | S1/S4 | delivery/no-I/O probe |
| F02 | external-DTD local sentinel | SECURITY_REJECTED, no resolver/I/O | J+A | S1/S4 | StrictMode/local observer |
| F03 | test-owned file-entity reference | reject; no XML-triggered disk read | J+A | S1/S4 | StrictMode/canary |
| F04 | loopback entity reference | reject; zero parser connections | J+A | S1/S4 | local observer |
| F05 | parameter-entity variants | SECURITY_REJECTED before SDK | J+A | S1/S4 | delivery probe |
| F06 | recursive/expansion variants | reject DTD, no expansion allocation | J+A+M | S1/S4 | allocation probe |
| F07 | undefined-entity literal | MALFORMED_XML, no custom resolution | J+A | S1/S4 | entity gate |
| F08 | lexical duplicates, duplicate xmlns, alias-expanded duplicates/unbound prefixes; legal different-URI controls incl ignored nodes | malformed variants MALFORMED_XML; distinct expanded names accepted | J+A | S1/S3 | guard lexical facts + adapter namespace/duplicate checks |
| F09 | schema/PI/XInclude literals | accepted ignored content, zero I/O | J+A | S1/S3/S4 | local I/O observer |
| F10 | deterministic oversized token | proper LIMIT_EXCEEDED, ignored checked | J+A+M | S1/S4 | pre-token gate |
| F11 | generated nesting | G05 failure inside ignored subtree | J+A | S1/S4 | pre-token gate |
| F12 | numeric vectors + XML variants | G10 limit or invalid coordinate per isolated variant | J(core)/A(XML) | S2/S4 | J helper bounds; A guard/provider delivery |
| F13 | faulting configuration factory | SECURITY_CONFIGURATION_UNAVAILABLE | J+A | S1 | adapter config |
| G01 | raw-byte padding L-1/L/L+1 | 16 MiB boundary incl BOM/comments | J+A+M | S1/S4 | bounded reader |
| G02 | track-count generator triplet | 128 tracks incl empty | J+A | S3/S4 | profile counter |
| G03 | segment-count generator triplet | 4096 total segments, no per-track cap | J+A | S3/S4 | profile counter |
| G04 | sample-count generator triplet | 100000 total samples; safe failure | J+A+M | S3/S4 | canonical memory |
| G05 | depth generator triplet | 32 root-inclusive depth | J+A | S1/S4 | lexical guard |
| G06 | attribute generator triplet | 64 incl xmlns declarations | J+A | S1/S4 | lexical/SDK parity |
| G07 | logical-event generator triplet | 2000000 normalized events, chunk invariant | J+A+M | S1/S4 | event normalization |
| G08 | semantic-text generator triplet | 1024 Unicode scalars; whole-value count | J+A | S3/S4 | profile counter |
| G09 | token generator triplet + equivalent long-leading-zero references | 8192 decoded logical chars, no callback reset/raw-reference cap; long-small refs remain accepted within other ceilings | J+A+M | S1/S4 | bounded reference re-emission/delivery |
| G10 | decoded scalar triplets + XML generator | 128 decoded chars before trimming | J(core)/A(XML) | S2/S4 | J helper count; A XML decoding/bound integration |
| G11 | future inventory generator triplet | 1024 imports/Territory admission | FUTURE | coordinator/storage | future READY inventory |
| G12 | future inventory generator triplet | 200000 samples/Territory admission | FUTURE | coordinator/storage | future READY inventory |
| G13 | future archive generator triplet | current64 Backup entries, fail closed | FUTURE | protection | existing/future Backup adapter |
| G14 | future metadata generator triplet | current16 MiB entry limit | FUTURE | protection | Backup |
| G15 | future metadata generator triplet | current64 MiB metadata aggregate | FUTURE | protection | Backup |
| G16 | future GPX metadata triplet | proposed64 MiB V9 GPX entry | FUTURE | protection | future versioned contract |
| G17 | future metadata triplet | proposed256 MiB V9 total | FUTURE | protection | future versioned contract |
| G18 | future archive triplet | proposed4096 V9 entries | FUTURE | protection | future versioned contract |
| G19 | future ZIP sizing triplet | Snapshot64 MiB compressed boundary | FUTURE | protection | Snapshot |
| G20 | future decoded sizing triplet | Snapshot128 MiB uncompressed | FUTURE | protection | Snapshot |
| G21 | capacity/aggregate scenarios | no invented numeric RAM/app-wide cap | M+FUTURE | S4 memory subset; future integration | declared corpus/device evidence |
| H01 | same-byte pair + future admission | S5 SHA/CM1 subset; future confirmation/fresh UUIDs | J+A subset; FUTURE full | S5 + coordinator | future duplicate workflow |
| H02 | context variation harness | S5 parser/CM1 excludes name/URI; future envelope | J+A subset; FUTURE full | S5 + coordinator | future captured context |
| H03 | A/B context + same bytes | S5 no ownership in CM1; future distinct owners/IDs | J subset; FUTURE full | S5 + coordinator | Territory persistence later |
| H04 | retained canonical draft + lost source | S5 values/CM1 independent; future viewing/FULL refusal | J subset; FUTURE full | S5 + presentation/protection | future original availability |
| H05 | fixed canonical tree/IDs harness | S2 CM1 stable; actual restart/restore IDs later | J core; FUTURE full | S2/S5 + restore | Android pure parity optional; future persistence/protection |
| H06 | future visibility/stale-scope scenario | unregister/show doesn't delete data; stale A rejected | FUTURE | presentation | existing Stage0 Registry future consumer |
| H07 | aggregate multi-Territory manifest | no conflated input/storage/presentation/protection caps | M subset; FUTURE full | S4 measurement + future integration | future data/protection |

## 16. Safe runtime security probes

Future authorization only; no probes run during design.
Minimum qualification endpoints: API29 (minSdk) and API37 (targetSdk), with actual
runtime/provider/feature identity recorded. Additional points (including API36) require
implementation differences/evidence; they do not replace a missing endpoint. Installed
API37 source is SOURCE INSPECTION, not runtime PASS. An unavailable endpoint remains
pending; no SDK/device installation or execution is authorized by this design revision.

Probe the integrated guard+SDK pipeline with internal/external DTD, file/network/
parameter entities, recursion/expansion, undefined references, namespace misuse,
depth/attributes/text/events, malformed UTF8/16, mismatch/truncation, cancellation,
huge ignored extensions and false-positive DOCTYPE text inside safe CDATA/comment.
M1 variants include decimal/hex references with long leading zeros below G01, malformed/
unterminated/out-of-range references, quotes/less-than/ampersand/non-recursive escapes,
and delivery traces proving only bounded valid re-emission reaches SDK. M2 F08 variants
cover exact lexical duplicates, repeated xmlns declarations, namespace aliases sharing
one expanded attr name, legal same-local-name/different-URI attrs and ignored subtrees.
M3 B08 variants cover literal TAB/CR/LF/CRLF versus referenced TAB/CR/LF, equivalent
reference spellings, UTF8/allowed UTF16 and every relevant split byte/char boundary.
Use literal expected decoded strings; do not ask the guard/parser to create its oracle.

Use supplied ByteArray/test-owned source so expected caller IO is separated from
XML-triggered IO. Instrument guard-to-SDK delivery: forbidden marker must not reach
SDK; factory/feature state and default relaxed/docdecl behavior recorded.
Native API has no resolver-denial callback: don't invent one.
On Android use StrictMode disk/network detection on the parser execution thread
after fixtures load, plus bounded loopback observer and test-owned file canary.
No internet endpoints, user files, Samsung settings or global device reset.
A file canary alone does not prove absence of reads; combine Java IO instrumentation,
source evidence and negative fixtures. [StrictMode is best effort, not a security
mechanism](https://developer.android.com/reference/android/os/StrictMode); zero violations
alone cannot close S06, and JNI access may not be reported. Local observer cannot be treated as universal
syscall proof. Any XML-triggered connection/file read is a failing gate.
Host SAX adapter has rejecting EntityResolver2 plus checked external-access flags.

Measure pathological pre-token allocation/cancellation with test-owned streams,
record read/delivery counts, max guard state, heap/runtime evidence. Reader chunk
boundaries, CDATA/entity adjacency and supplementary Unicode variations must yield
the same canonical values and normalized ceiling counters. For long-small references,
show fixed scanner state/emission bounds independent of raw digit count, and SDK capture
bounded by emitted form; observations alone do not replace the structural argument.
Record PASS/FAIL/SKIPPED/ABORTED/NOT_RUN honestly; no known MapLibre-crash waiver.

### 16.1 Lexical probe audit map — R5, unchanged semantics

Existing variants/capability probes only; no new Stage1B family IDs.
Each probe compares independent semantic expectations and relevant delivery/counters
under GUARD_SDK_AGREEMENT; equivalent callback fragmentation is normalized.

| Lexical rule / variation | Existing family or §16 capability probe |
|---|---|
| Raw CR/LF/CRLF; literal attribute TAB/SPACE vs referenced whitespace | B08 §8.3 variants, E01–E04 encoding counterparts |
| Text whitespace; numeric/predefined references; non-recursive/markup-like escapes | B01/B08/G09, §16 M1 delivery probes |
| Comments, CDATA, PI; harmless DOCTYPE-like content; quoted attribute delimiters | B05/B08/F09/F10 and §16 false-positive/delivery probes |
| UTF8 byte splits, UTF16 code-unit splits, supplementary scalars | A06/B09/E01–E04/E08/E09 and §16 split-boundary probes |
| Buffer transitions, reference/quote/raw-CR states, text/CDATA/entity adjacency | B08/G07/G09 and §16 chunk-invariance probes |

### 16.2 Mandatory resource measurement record — R4

Every future near-limit/resource run must record the following where applicable and
observable. State NOT_APPLICABLE with a reason for irrelevant fields; unavailable or
unreliably measured metrics are **UNKNOWN**, never invented or treated as PASS.

| Record field | Required evidence |
|---|---|
| Scenario/configuration | Corpus/fixture/variant identity, configured inherited ceilings, runtime/API/JVM/provider/build identity and measurement method |
| Input/structure | Exact input bytes and relevant track/segment/sample/depth/attribute/event/text counts |
| Processing transport | Pass count; bytes read/delivered per relevant pass/boundary; distinguish raw bytes from decoded chars/scalars/re-emitted representation |
| Time/memory | Elapsed time with measured interval; best available heap/allocation evidence and method; maximum relevant guard/buffer state if measured |
| Cancellation | Request point and observed outcome when applicable |
| Safety/result | Bound/security violations, OOM outcome, final processing outcome and consistency/absence of partial or corrupt results |

Hard failure/blocked progression means OOM, uncontrolled/unbounded allocation,
inability to enforce an accepted safety bound, required cancellation failure,
security invariant failure, partial/corrupt/inconsistent result, or insufficient
evidence to determine whether a relevant accepted bound/invariant held. UNKNOWN
does not itself prove a violation, but cannot close the affected evidence gate;
a nonessential unavailable metric need not block an otherwise evidenced invariant.
These conditions define formerly vague “unacceptable near-limit behavior”.

No new numerical RAM/time production threshold is introduced. Stage1A ceilings
remain provisional safety/test ceilings. Preserve aggregate multi-Territory corpus
and separate input/storage/presentation/protection domains. If accepted capacity
cannot be supported safely, stop and escalate separately; do not silently lower it.

## 17. Future implementation slices

| Slice | Goal/files | Prerequisites / families | Gates / stop conditions | Excluded |
|---|---|---|---|---|
| S1 XML/security capability proof | Repeatable test input → strict decoder → lexical guard/parser-facing stream → actual platform provider → minimal adapter; no business import | OWNER acceptance+implementation authorization; E/F, B08, G01/G05–G07/G09; A01 tokenizer smoke | API29/37 integrated proof: long-small ref semantics/bounds, lexical/expanded duplicates, literal/ref whitespace, DTD/no IO/strict bytes/cancel/S18; GUARD_SDK_AGREEMENT mandatory; unsafe/unproven capability FAILS | Full GPX parser/canonical model/UI/storage |
| S2 exact model/values/CM1 | CanonicalModel/Values + Cm1Encoder | Separate authorization; pure A/B/C vectors, A01 independent CM1, H05 material subset; no Android/S1 prerequisite for isolated core | Targeted JVM sufficient for pure model/decimal/CM1; device parity optional, no rounding/golden mismatch | XML qualification/profile import/UUID allocation/Room |
| S3 GPX profile parser | Profile state engine/private sink + Processor wiring | S1+S2; all A–D and B/E overlaps | Real host-reference+Android corpus, full ordered trees/empty/gaps/nulls/warnings; reject whole invalid document | Picker/repository/rendering |
| S4 limits/cancellation/full security | All input G01–G10, F/E negatives, resource harness | S3; inherited default ceilings + G21 measurement subset | Boundary triplets, normalized counts, no partial output, late cancel/EOF, §16.2 measurement record/objective failures; stop on allocation/counting/runtime drift | Storage/protection limits G11–G20 |
| S5 boundary handoff | Repeatable source/identity, abortable sink contract, typed result | S1–S4; H01–H05 parser-only subsets, same/mutated source, failures | End-to-end input→canonical→CM1/identity, full JVM/build/lint + Android conformance; future owner integration document | Actual coordinator/UUIDs/Room/UI/Registry/backup |

Each slice is a separately bounded implementation task and reviewable result, not one
giant GPX feature implementation. S1 failure blocks the current XML implementation
path and integrated processing qualification. Independently authorized pure S2 work
can be JVM-proven without S1/device parity; S3–S5 cannot bypass the S1 gate. Test-only standalone capability fixtures may be produced in S1
under separate authorization, never by this design task. S1 needs only the lexical
boundary/minimal adapter and independently specified capability fixtures, not the full
profile engine or canonical graph. PASS means the integrated accepted semantics and
allocation/no-I/O constraints are qualified, not merely that setFeature succeeds.
FAIL stops XML qualification; any replacement technology/dependency is a separate task,
without weakening Stage1B or adding a hidden input limit.
S5 is processing readiness, not Stage1 product ship/backup exit acceptance.

## 18. Future build/test commands — NOT RUN

Use repository wrapper/version and established task-local Gradle/SDK/temp strategy
from [lifecycle evidence](map-overlay-lifecycle-evidence.md). No global settings change.
Existing SDK path C:\Users\user\AppData\Local\Android\Sdk and installed Temurin17
are inspection facts; re-establish actual JAVA_HOME/SDK/toolchain before execution.
The previously used cached Gradle9.5 distribution exists, but wrapper invocation and
offline dependency readiness are future gates, not assumed successes.

Commands below are proposed future commands against real :app tasks. Test FQCNs are
NEW and must exist after implementation; no task was executed to discover/run them.
Start in the task checkout:

- .\gradlew.bat --offline --no-daemon :app:testDebugUnitTest --tests "org.beesearch.app.domain.gpx.*" --tests "org.beesearch.app.data.gpx.*"
- .\gradlew.bat --offline --no-daemon :app:testDebugUnitTest
- .\gradlew.bat --offline --no-daemon :app:compileDebugKotlin :app:compileBetaKotlin :app:compileReleaseKotlin
- .\gradlew.bat --offline --no-daemon :app:compileDebugAndroidTestKotlin :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

After explicit device-test authorization, list adb devices -l, select an emulator-*
serial and isolate ANDROID_SERIAL. Never use ambiguous connected-device selection.
Example targeted core runtime command:
.\gradlew.bat --offline --no-daemon -Pandroid.testInstrumentationRunnerArguments.class=org.beesearch.app.data.gpx.GpxXmlCapabilityTest,org.beesearch.app.data.gpx.GpxParserDeviceTest :app:connectedDebugAndroidTest

Separate opt-in measurement:
.\gradlew.bat --offline --no-daemon -Pandroid.testInstrumentationRunnerArguments.class=org.beesearch.app.data.gpx.GpxResourceDeviceTest -Pandroid.testInstrumentationRunnerArguments.beeGpxResources=true :app:connectedDebugAndroidTest

Preserve existing leaveApksInstalledAfterRun=true and DEV/dev.test package safety.
No physical device required for each pure JVM slice. Actual Android parser security
needs runtime evidence before PASS; corpus measurements on physical device, if needed,
are separately authorized. No broad native/map suite or device clear/uninstall.

## 19. Stop conditions

Stop and report without silent weakening if:
- Required parser/security state cannot be established; default false masks an
  unsupported requirement; forbidden declaration/entity reaches SDK.
- External IO is possible/observed or malformed bytes silently replace characters.
- Guard admits uncontrolled token growth, has chunk/CDATA/reference false positives
  or cannot enforce accepted limits/strict scalar/name behavior.
- Source identity changes between passes; partial draft escapes rejection/cancel.
- Any §16.2 objective resource failure or missing evidence needed to establish an
  accepted bound/invariant; no hidden lower cap or invented RAM/time threshold.
- CM1 differs from independent bytes, decimals go through Double, order/gaps change.
- Android behavior materially differs from host reference/accepted contracts.
- New dependency/product/security/profile/identity/protection decision is required.

No runtime library replacement, new dependency or relaxed fallback is preauthorized.
A failed S18/config gate may require a separate technical decision before any continuation.

## 20. Future integration boundaries

Future managed-copy owner supplies completed repeatable original and captured request;
processor does not parse a URI or decide Territory. Future coordinator controls
confirmation/lookup scope, logical UUIDs, display name and recoverable publication.
Future persistence preserves exact canonical values/IDs; restore does not reparse.
Future visible-set producer reads persisted geometry without requiring original
availability; hide/unregister never deletes canonical data. Area owns no GPX.
Stage0 Registry remains unaware of GPX/Room/Territory and requires no change.
G11–G21 and full H-family protection/presentation assertions remain future obligations,
not a parser-test PASS or Stage1 shipping waiver.

## 21. Open technical questions/evidence obligations

- API29/37 actual feature/strict grammar/normalization/security behavior; only installed37
  source and API documentation inspected, no runtime tests.
- Qualification of §8.1 incremental reference re-emission without a raw ceiling, §8.2
  lexical/declaration/expanded-name checks and §8.3 origin-aware attribute normalization;
  bounded delivery/facts, chunk-invariant logical counters and diagnostic precedence.
- Validation-disabled establishment when public setter unsupported: source/default/API
  state is not enough alone; S1 runtime proof must close the actual capability.
- Peak canonical memory and two-pass CPU/cancellation cost at inherited ceilings.
- Secure host SAX adapter feature support; inability is a host-test blocker, not an
  excuse to claim Android conformance through fakes.
- Future coordinator lookup/prompt scope, display-name mapping, storage/protection
  and measured aggregate capacities remain outside processing implementation.

No real contradiction between accepted Stage1A/1B contracts was identified. Practical
platform constraints above are explicitly conditional; they are not rewritten truth.

## 22. OWNER decisions required

**NONE** for this design scope. Product/profile/identity/security/budgets remain fixed.
Technical component/API names are proposed executor choices. A failed capability
gate requiring weaker behavior/new persistent semantics must return to OWNER instead
of silently choosing a new policy.

## 23. Stage1C design exit criteria

Independent review+OWNER acceptance may close design when every accepted requirement
has a component/gate, conditional XML recommendation is justified, S01–S18 gaps are
visible, model/decimal/CM1 mechanics are unambiguous, all92 IDs have a mapped layer,
future coordinator/protection/UI scope is separated and slices have observable exits.

Checklist: no runtime PASS inferred; no new libraries/UUID/dedup policy; no numeric
capacity guarantee; no Registry change; accepted Stage1A/1B semantics preserved.
This document does not close S1 capability gates or authorize executable changes.

## 24. Explicit non-goals and next action

No production/test/Android-test code, fixture or generator files, managed-copy service,
Room migration/repository, DataStore, backup/snapshot implementation, UI/picker/rendering,
dependency/config/version/signing/release change, emulator/Samsung, Gradle execution,
commit/staging/push or Stage2–4.

Next technical step: **D106 Stage 1 Slice 1 — XML/security capability proof**.
It has NOT started and is NOT authorized by this acceptance. Separate explicit OWNER
authorization is required before Slice1; declared runtime qualification gates remain pending.

## 25. Slice 1 execution evidence/status — 2026-10-13

**Frozen-current-source rerun PASS — evidence closure complete; final independent re-review pending.**
Separate OWNER authorization dated2026-10-13 covered Slice1 implementation/qualification.
Sections1–24 remain the accepted design; their acceptance-time “not started/not authorized”
statements describe that earlier task. This evidence does not authorize Slice2+, commit or push.

Baseline: `task/d106-stage0-overlay-registry`, HEAD
`13a4e75eb163e939e488d92e1cc5c3c0b66eba57`; initially clean/index empty.
PAC tool genuinely unavailable; fresh explicit OWNER fallback used, no simulated declaration.

### Tested implementation and runtime identity

Reusable internal `data/gpx` components: GpxRawInput (exact bytes/count/SHA), GpxInput
(strict envelope/Reader), GpxXmlLexical (scalar/name/reference helpers), GpxXmlGuard
(bounded lexical transducer), GpxXmlSemantics (bounded transient agreement witness),
AndroidGpxXmlReader (fresh configured provider/two-pass verification/normalized adapter),
and GpxXmlBoundary (typed bounded failures/counters). No GPX domain tree, UUID allocation,
CM1, managed-copy storage, coordinator, persistence or map integration was added.

Earlier qualification chronology (historical, NOT primary current-revision proof):
- Earlier JVM record24/24; JVM tests were not rerun during evidence closure.
- Earlier core Android revision:23/23 per endpoint using the core APK hashes below.
- Later targeted probe revision:3/3 per endpoint (API29:60.550s/API37:63.703s), including
  an updated compound test and added predefined/supplementary probes. The old23+3
  accounting is retained as history and is superseded by the complete frozen-current25 runs below.
- GpxXmlSecurityMatrixTest.kt CHANGED between these earlier core/probe inventories:
  earlier-core SHA C69663A9E9C2D89D7CB82DD6E28469F6298E45BB14B22E45A8BE7950E1E78749;
  frozen-current SHA529F411E22BAD82FCB89102B8F18FED1E016B6EFD910A5C5D0E1247C4FE0A5C8.
  Thus those runs were not one unchanged source revision. GpxInput.kt/GpxInputTest.kt
  also have different exact byte hashes after the previously recorded trailing-EOF cleanup.
  The prior unqualified statement about unchanged production is replaced by this chronology;
  only THIS evidence-closure cycle proves all production/test bytes unchanged pre/post.
No Activity/MapLibre/native fixture was initialized by the scoped XML runs.

| Earlier historical endpoint | AVD/serial | Image | Actual provider/configuration | Earlier core result |
|---|---|---|---|---|
| API29 / Android10 / x86_64 | BeeSearch_ZIP64_API29 / emulator-5554 | android-29;google_apis;x86_64 revision13 | com.android.org.kxml2.io.KXmlParser; namespaces=true, docdecl=false, relaxed=false, validation=false | 23/23;69.062s |
| API37 / Android17 / x86_64 | BeeSearch_XML_API37 / emulator-5556 | android-37.0;google_apis;x86_64 revision6 | Same provider class; same checked created-parser feature states | 23/23;86.646s |

API29 fingerprint: `google/sdk_gphone_x86_64/generic_x86_64:10/QSR1.211112.011/13135432:userdebug/dev-keys`.
API37 fingerprint: `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys`.
Emulator37.1.11.0 build15917651; API29/API37 boot/API checked with explicit serial.
Execution was sequential; each AVD stopped before the other booted; both stopped afterward.
Both config.ini SHA values unchanged; no wipe, snapshot save/deletion, physical-device work
or DEV data clear/uninstall. Only DEV/test APK updates via serial-scoped install -r -t.

Earlier core/probe APK SHA256 (historical; not the new frozen-current build):
- DEV: `F3963A06DB6E71CBC31A33652F4A3CD99FD931C77B7D5699D065CBE487D989A1`.
- Core23-test APK: `167402C6E696EA44D94B3CA96874C2537918172050CAEED9CFBC4519ED423D20`.
- Final3-probe APK: `E17B7ACEF140787CA1B16BFBADC41A9A66637C2A6336F860EEC43E6CE5ADE6F9`.

Local raw evidence is under `build/evidence/d106-slice1/` (ignored build artifacts,
not staged): final build logs, api29/api37 qualification.txt and measurements.txt,
final-maxima.txt/final-maxima-measurements.txt, tested-source-hashes.json/final-source-hashes.json,
apk-hashes.json/final-probe-apk-hash.json, before/after AVD hashes.
Earlier inventories and logs are retained. The fresh frozen/post-run inventories below
establish exact current-source provenance independently of the earlier revisions.

### Capability assertions and structural argument

| Gate / existing family variants | Evidence on both endpoints |
|---|---|
| Created parser configuration / F13 positive capability | Fresh public XmlPullParserFactory; set and read back namespace=true, DOCDECL=false, relaxed=false before input is opened |
| Encoding / E subset | UTF8/no BOM/BOM, UTF16LE/BE BOM; malformed UTF8, broken/odd UTF16, truncation, declaration mismatch/unsupported encoding, UTF32 rejection; strict REPORT Reader |
| DTD/entities/I/O / F01–F07/F09 variants | Declarations rejected before SDK delivery, including second-pass mutation; custom undefined references rejected; SYSTEM/PUBLIC/file/network/parameter/recursive inputs blocked; loopback connections0 and parser-thread StrictMode signals[] |
| M1 / B08/G09 variants | Million-leading-zero decimal text plus decimal/hex reference variants (JVM also million-zero hex); bounded numeric state, short reference re-emission; malformed/illegal scalars reject; original exact-byte SHA/length unchanged; all five predefined names, escaped markup and nonrecursive reference-like text checked |
| M2 / F08 variants | Lexical QName/xmlns duplicates before delivery; adapter expanded (URI,local) duplicates, alias prefixes, distinct URIs, default/unbound/reserved namespaces; ignored subtrees checked; independent URI assertions |
| M3 / B08 variants | Literal TAB/CR/LF/CRLF normalize before origin is lost; referenced TAB/CR/LF/SPACE preserved; actual provider values match literal independent expectations |
| GUARD_SDK_AGREEMENT | Bounded S/E/T/C/P witness plus lexical attribute/namespace reconciliation; adjacent text/CDATA/entity fragments compared semantically; missing/different observations fail; counters are additional evidence |
| Chunks / B08/E variants | Raw stream chunks1/2/3/7/64; UTF8 multibyte, UTF16 units/supplementary characters, quotes/references/CRLF/declarations/opaque constructs; same independent normalized values |
| Limits / G01/G05/G06/G07/G09 subset | 16MiB and2million event triplets; depth32/over;64 attributes including xmlns/over;8192-scalar token bounds, declaration triplet, max compound START, ignored content |
| Cancellation / INPUT_IO_ERROR | Actual second-pass cancellation at checkpoint10000 terminates at10000, opens2, no verified/Accepted result; repeat-source SHA/length mismatch and open/read failures abort with bounded categories |

S18 architecture gate: the guard classifies and bounds each lexical piece before handing
it to SDK; one UTF16 unit is delivered per Reader read. Logical text stays bounded across
references/CDATA fragments. Comments/CDATA/PI have fixed delimiter lookahead; attribute
count and each name/value are bounded. The maximal tested START has an8192-scalar
supplementary element name,64 distinct8192-scalar supplementary attribute names and64 values of8192 U+10FFFF scalars
represented as bounded references: largest piece6308099 UTF16 units.
Supplementary text/comment/CDATA/PI and8192-reference logical text runs were also tested;
max parser-visible PI16388/text reference spelling81920 UTF16 units.
This compound bound derives from G06/G09, not a new product ceiling.
Numeric references retain no complete raw digits, and emit at most a fixed scalar spelling.
Pending start-fact and semantic-witness queues are fixed provider-qualification backpressure;
overflow returns SECURITY_CONFIGURATION_UNAVAILABLE, never a new accepted-input limit.
Legal depth/sibling/mixed-content probes passed; largest observed pending-start count2.
No tree/namespace-binding engine/profile/canonical builder was introduced in the guard.

Finite direct-provider20,000-scalar text/comment/CDATA/PI/attribute probes returned
large values (PI includes target/separator); they intentionally bypass the guard.
**CONTEXT ONLY**, not architecture failure or standalone provider security qualification.
Installed API37 source inspection is now backed by evidence-closure/sdk-source-identities.json
and sdk-source-excerpts.txt: sources;android-37.0 revision2; KXmlParser.java lines1481–1526
(fillBuffer stops when its requested minimum is available), StringPool.java lines26–33
(fixed512-entry source cache). Full source/package SHA identities are recorded.
This is SOURCE EVIDENCE ONLY for the installed SDK source, not API29/device bytecode or
runtime proof. The earlier inspection lacked a durable source hash; this current read-only
snapshot does not retroactively establish the earlier inspection's exact byte identity.
No source download/install or new web research was performed.

### Earlier resource measurement records — historical context

Full per-scenario records are in each endpoint measurements.txt. The following exact/max
examples use inherited ceilings bytes16777216/token8192/attrs64/depth32/events2000000.
All rows:2 passes, bytes read in each pass=input bytes; decoded UTF16 counts in each pass
recorded separately from input bytes (Unicode scalar/UTF16/UTF8 units are not conflated); configuration/fingerprints above; max pending
start facts1; cancellation NOT_APPLICABLE; violations NONE; OOM NO; final VERIFIED_XML,
exact original SHA/length checked, no public partial result.

| API | Scenario | Input bytes / bytes per pass | Decoded UTF16/pass | Delivered UTF16 | Events/depth/token | Elapsed ms | Largest piece UTF16 | Heap before→after bytes | ART allocation delta bytes |
|---|---|---|---|---|---|---|---|---|---|
| 29 | G09-declaration-8192 | 8354 | 8354 | 8354 | 11/3/8192 | 21.570 | 8196 | 34331408→37792528 | 3461120 |
| 29 | G07-exact | 4000122 | 4000122 | 4000121 | 2000000/2/34 | 5698.833 | 77 | 75949416→13386720 | 1193111552 |
| 29 | S18-text | 8314 | 8314 | 8313 | 4/1/8192 | 27.377 | 8192 | 24576224→5896648 | UNKNOWN |
| 29 | S18-reference | 1000127 | 1000127 | 127 | 4/1/34 | 934.715 | 77 | 16211728→5787520 | 56676352 |
| 29 | G01-exact | 16777216 | 16777216 | 16777215 | 2050/1/8192 | 18702.427 | 8199 | 209651888→29668280 | 1097338880 |
| 37 | G09-declaration-8192 | 8354 | 8354 | 8354 | 11/3/8192 | 259.035 | 8196 | 30316320→33834784 | 3518464 |
| 37 | G07-exact | 4000122 | 4000122 | 4000121 | 2000000/2/34 | 7980.102 | 77 | 46986064→12276560 | 1233907712 |
| 37 | S18-text | 8314 | 8314 | 8313 | 4/1/8192 | 43.643 | 8192 | 22537040→5059408 | 4177920 |
| 37 | S18-reference | 1000127 | 1000127 | 127 | 4/1/34 | 1092.575 | 77 | 9282384→5096272 | 65257472 |
| 37 | G01-exact | 16777216 | 16777216 | 16777215 | 2050/1/8192 | 21747.382 | 8199 | 143123280→30466896 | 1433923584 |
| 29 | S18-unicode-pi | 32894 | 16510 | 16509 | 4/1/8192 | 23.760 | 16388 | 20160256→22470400 | 2310144 |
| 29 | S18-dense-reference | 82042 | 82042 | 82041 | 4/1/8192 | 127.637 | 81920 | 23211776→36933376 | 13721600 |
| 29 | S18-max-start-tag | 7373181 | 6308221 | 6308220 | 5/2/8192 | 7931.811 | 6308099 | 267493120→52874680 | 539590704 |
| 37 | S18-unicode-pi | 32894 | 16510 | 16509 | 4/1/8192 | 47.743 | 16388 | 18736960→21411648 | 2674688 |
| 37 | S18-dense-reference | 82042 | 82042 | 82041 | 4/1/8192 | 181.560 | 81920 | 21907264→34969408 | 13062144 |
| 37 | S18-max-start-tag | 7373181 | 6308221 | 6308220 | 5/2/8192 | 8624.673 | 6308099 | 130347888→50709360 | 887853152 |

Time: System.nanoTime around the whole verification. Heap before/after:
Runtime.totalMemory-freeMemory (includes test corpus/runtime, not isolated provider peak).
Allocation: Debug.getRuntimeStat art.gc.bytes-allocated difference when monotonic;
reset/nonmonotonic/unavailable values=UNKNOWN. Heap peak and provider-internal isolated
allocation/read-ahead bytes=UNKNOWN; largest guard piece/pending facts and delivered
UTF16 counts are observed boundary measurements, not those unavailable internal metrics.
Structural prevention is primary; favorable heap measurements alone do not prove S18.
Universal syscall tracing=UNKNOWN; zero local I/O observations supplement the structural
declaration/reference barrier and Reader-only/no-resolver design.
No RAM/time production threshold or capacity promise added; G21/full storage/presentation/
protection measurements and full GPX processing remain later slices.

### Commands, failures and scope

Task-local GRADLE_USER_HOME/ANDROID_USER_HOME/TEMP and process-local ANDROID_HOME;
existing Gradle9.5.0 direct launcher, --project-dir this checkout, --offline --no-daemon
--max-workers=2, kotlin.compiler.execution.strategy=in-process.
Executed targeted :app:testDebugUnitTest --tests org.beesearch.app.data.gpx.*,
:app:compileDebugAndroidTestKotlin, :app:assembleDebug and :app:assembleDebugAndroidTest.
Runtime per explicit serial: adb install -r -t DEV/test APKs; adb shell am instrument
-w -r -e class org.beesearch.app.data.gpx.GpxXmlCapabilityTest,org.beesearch.app.data.gpx.GpxXmlSecurityMatrixTest
org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner.
No broad connected/native suite, full JVM regression, beta/release/lint or Samsung run.

First API29 run19/20 exposed a new adapter diagnostic mapping defect for the provider's
unbound-prefix RuntimeException. Fixed in Slice1, final runs above supersede it; failure
log retained. New declaration counting and source IO/UTF32 classification were corrected
before final qualification, without accepted semantic changes. An initial build approval
review rejected checkout scope; explicit Set-Location/--project-dir established the
authorized checkout before successful builds; no wrong-checkout build evidence claimed.

Stage1A/1B and Stage1C requirements,92 families/20 numerical families, identity/CM1,
restore/protection, Territory/Area and all domain budgets unchanged. No dependency,
Gradle/config, Room/DataStore, backup/snapshot, MapLibre/Registry/BeeMap, UI or release change.
Index empty; worktree intentionally uncommitted for independent review. Commit/push NOT
PERFORMED. Slice2+ NOT authorized. Next: OWNER/reviewer inspection; stop pending separate
commit/continuation authorization.

### Frozen-current-source evidence closure after independent review

**PASS — EVIDENCE CLOSURE COMPLETE. READY FOR FINAL INDEPENDENT RE-REVIEW.**
This narrow OWNER-authorized cycle changed no production or test source. No OWNER
acceptance of Slice1 implementation, Slice2 authorization, staging, commit or push is implied.

Primary evidence: `build/evidence/d106-slice1/evidence-closure/`.
Before build/qualification, all7 production +5 JVM/Android test files were frozen in
`frozen-source.json` (relative paths, byte lengths, SHA256); after both runs,
`post-run-source.json` was regenerated and matched exactly.
Both inventory SHA256 values:
`1FE815F5D536D45E04BC0AEF9F8E58712EB69F91BF3158420DB1DC3F6C87EF2C`.
`correspondence.json` connects this inventory, build log, APK hashes, exact commands,
actual endpoint properties and successful25-test results. No mtime-only provenance.

Both APKs were rebuilt from the frozen revision using the existing offline Gradle
launcher, --rerun-tasks :app:assembleDebug :app:assembleDebugAndroidTest:
BUILD SUCCESSFUL,73 tasks executed. DEV package org.beesearch.app.dev;
test package org.beesearch.app.dev.test. Built hashes:
- DEV: `7FD4785D2971168269A69305E45496D72F9A3377D523D2302A2A4194F0B60941`.
- androidTest: `C68CD2F8105070EE3D82391E563B386FDF1806FAB2746D466388B421D6C3854B`.

Serial-scoped pm path + sha256sum proved installed DEV/test hashes equal this same
built pair on EACH endpoint. Installed-path/hash records, install results and explicit
instrumentation commands are in each api29/api37 directory.

| Primary frozen-current run | Actual AVD / serial | Actual runtime | Result |
|---|---|---|---|
| API29 | BeeSearch_ZIP64_API29 / emulator-5554 | API29; Android10; x86_64 | OK (25 tests);68.805s |
| API37 | BeeSearch_XML_API37 / emulator-5556 | API37; Android17; x86_64 | OK (25 tests);87.279s |

Fingerprints are the two recorded endpoint fingerprints above. Actual serials were
confirmed by adb devices -l, emu avd name and serial-scoped getprop; historical serials
were not assumed. Each directory contains devices.txt, avd-name.txt, properties.json,
instrumentation-command.txt, qualification.txt and measurements.txt. Exactly25 completed
successful method executions per endpoint; no failed/skipped/aborted execution.
API29 stopped before API37 boot; devices-between-endpoints.txt proves no connected
emulator. Both stopped afterward, devices-after-all.txt empty. AVD config hashes match
before/after; no wipe, data clear, uninstall, snapshot deletion/save or physical-device work.
Primary measurements are the NEW evidence-closure/api29/api37/measurements.txt records;
the resource table above remains earlier historical measurement context.

The25-method set includes finiteUnguardedProviderCharacterizationIsContextOnly.
That method has no independent semantic/security assertion: it logs finite unguarded
provider behavior and is CONTEXT ONLY. Its completion is not independent proof of
a security invariant and is not an architecture-failure gate. The other assertive
checks and the reviewed structural argument provide qualification evidence.
No assertion, test organization, tentative sink or feature policy was changed here.

#### API37 provisioning provenance — OWNER-confirmed historical fact

OWNER explicitly confirmed that a SEPARATE authorized environment-preparation task
completed BEFORE Slice1 implementation/qualification and returned READY. It installed
`system-images;android-37.0;google_apis;x86_64`, revision6, and created
`BeeSearch_XML_API37`. That task verified Android17/API37/x86_64/sys.boot_completed=1,
reporting historical serial emulator-5556. It installed no Bee Search APK, ran no tests
and implemented no Slice1 code. This is OWNER-confirmed preparation provenance,
separate from the new runtime rerun records.
Current installed-package-metadata.json independently records the existing image ID/revision.
This evidence-closure cycle reused the existing AVD/image: no reinstall, recreation or
configuration change. Slice1 itself did NOT provision API37.

F-1 CLOSED: complete25-test current frozen revision passed on both endpoints.
F-2 CLOSED: actual serial/property/command records and installed APK hashes preserved.
F-3 CLOSED: separate OWNER-authorized pre-Slice1 provisioning recorded without attributing
it to Slice1. Source/test hashes unchanged after qualification; HEAD13a4e75 and index empty.
Next: final independent re-review. No implementation polishing or continuation authorized.
## 26. Slice 2 execution/status — 2026-10-14

The execution record below is historical pre-review/pre-commit evidence. Its pending/
uncommitted wording is superseded only by the post-review/local-commit status at the
end of this section; implementation/test evidence and its original chronology remain unchanged.

**PASS — PURE JVM CORE; UNCOMMITTED / READY FOR INDEPENDENT REVIEW.**
Separate explicit OWNER authorization covers this Slice2 only, including canonical
values/time. It authorizes no staging/commit/push or Slice3. PAC is unavailable;
fresh explicit OWNER authorization fallback used. Starting clean task checkout:
`task/d106-stage0-overlay-registry`, HEAD `bf36ddd481ca6a04d572635699095417f991173e`.
Slice1 was OWNER-accepted and committed at that baseline. Sections1–24 and the entire
Slice1 evidence chronology in §25 are unchanged; this section does not requalify XML.

### Components and invariants

- `domain/gpx/GpxCanonicalModel.kt`: immutable draft/metadata/track/segment/sample;
  defensive frozen lists retain empty nodes, parent-local zero-based ordinals and
  source order. Constructor/copy validation retains those invariants; retained caller
  lists and mutable casts cannot modify the graph. One freeze per list, no append
  API with repeated full geometry copies. Profile provenance is a fixed technical
  token `GPX_1_1_STAGE_1`, separate from the declared GPX1.1 version and absent from CM1.
- `domain/gpx/GpxCanonicalValues.kt`: scalar-count validation before trim, exact
  ASCII decimal grammar → BigDecimal → canonical plain text, exact coordinate bounds,
  nullable elevation without a new range. G10 coordinate/time128 and G09 elevation8192
  remain inherited ceilings; metadata uses G08. No binary float/locale conversion.
  Time preserves raw decoded text, uses explicit lexical/year/fraction/offset checks
  and strict java.time calendar construction; MISSING/UTC/NO_ZONE/UNSUPPORTED have
  coherent nullable seconds/nanos. Years1–9999 including representable UTC, ≤9 fraction
  digits, valid24:00 rollover and ±14:00 endpoints; no guessed zone or truncation.
- `data/gpx/GpxCm1Encoder.kt`: exact Stage1B §8 version1 schema, checked sizing then
  caller-owned OutputStream writing. Structural sizes only are cached (identity keys,
  no second sample tree/full CM1 buffer). Checked add/multiply/u32 are used by the real
  sizing/header paths; overflow tests exercise those helpers without giant allocation.
  Cancellation/IO propagate; caller must discard partial comparison output on failure.

The domain files import only Java standard value/collection APIs, never Slice1/XML,
Android, data/persistence or renderer types. No logical IDs, UUID allocation, SHA or
CM1 digest/dedup/uniqueness/storage wire format is introduced. Original-byte identity,
Territory ownership/Area non-ownership and restore without GPX reparsing remain intact.
Primary API research used the existing platform mechanisms, not a new dependency:
[BigDecimal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html),
[LocalDateTime](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/LocalDateTime.html),
[ZoneOffset](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZoneOffset.html).
These references are API evidence, not Android runtime execution in Slice2.

### Pure assertion coverage — not full mixed-family qualification

Normative basis: Stage1A §§5–6; Stage1B §§4–8/11/14–15; this contract §§9–11/15/17.
All22 requested row subsets below are JVM core assertions only. Actual XML extraction,
normalization/provider→same-tree/CM1 remains a separate S3/S4 integration obligation.
No new family IDs: the Stage1B corpus remains92 families, G01–G20 remain20 boundaries.

| Row | Slice2 assertion / test owner |
|---|---|
| A01 | Singleton/null model + independent literal CM1 oracle; Model/Encoder tests |
| A02 | Unsorted ordered samples/ordinals and encoded order; Model/Encoder |
| A03 | Ordered segments and observable boundaries; Model/Encoder |
| A04 | Multiple ordered tracks including empty nodes; Model/Encoder |
| A05 | Supported exact metadata/elevation/raw+UTC time fields; all three tests |
| A06 | Supplementary Unicode, strict scalar validation, no NFC, UTF8 byte lengths; all three |
| A07 | Exact legal coordinate endpoints; Values |
| A09 | Offset/nanoseconds/raw time and produced CM1 fields; Values/Encoder |
| B06 | Signed zero and leading/trailing zero equivalence; Values/Encoder |
| C01 | Different high-precision decimals retained in values/CM1; Values/Encoder |
| C02 | Permutation changes ordered material; Encoder |
| C03 | Reversal changes ordered material; Encoder |
| C04 | Segment split/empty node changes material; Encoder |
| C05 | Track/segment permutation with new encounter ordinals differs; Encoder |
| C06 | Creator/source/track metadata changes, null/empty/space distinctions; Model/Encoder |
| C08 | Elevation null/zero/value distinguished; Encoder |
| C09 | Raw-time change including same instant differs in full CM1; Values/Encoder |
| D09 | Exact out-of-range rejection, lon+180 excluded; Values |
| D10 | Invalid numeric grammar/locale/non-ASCII/exponent/nonfinite; Values |
| D14 | NO_ZONE/unsupported time retains raw/null UTC vs fatal malformed elevation; Values/Encoder |
| F12 | Bounded numeric analysis, no float overflow, grammar vs limit category; Values |
| G10 | 127/128/129 decoded scalar variants BEFORE trim, supplementary-count helper; Values |

H05 material-only subset: separately constructed canonical trees yield identical CM1.
Actual restart/restore/logical IDs/protection are NOT tested or implemented. Closed CM1
schema/tag audit proves the produced material has no extra context fields. Unsupported
XML content exclusion itself remains XML integration, not a claimed pure parser PASS.

### Independent oracle and execution evidence

`GpxCm1EncoderTest.a01LiteralIndependentGoldenCm1` contains hand-authored literal hex,
not encoder-generated expected bytes. Manual lengths: sample66, samples-list77,
segment92, segments-list103, track132, tracks-list143, root195; magic/version10 →205 bytes.
An independent read-only framing auditor checks other vectors' tags/kinds/UTF8 values;
it does not produce goldens. Other tests cover streaming output, checked overflow,
cancellation/IO, immutable aliases and constructor/copy invariants.

Actual commands used the existing cached Gradle9.5.0 launcher (same wrapper version),
explicit task checkout and existing task-local offline caches. Process-local only:
GRADLE_USER_HOME=`build/gradle-home`, ANDROID_HOME=existing SDK, ANDROID_USER_HOME=
`build/android-user-home`, TEMP/TMP=`build/tmp`. No global setting/local.properties edit.
Gradle daemon used its already-cached Temurin25; Java source/target remains11.

```powershell
$taskRoot='C:\App\Bee_search_stage0_overlay_registry'
$gradle='C:\Users\user\.gradle\wrapper\dists\gradle-9.5.0-bin\bvnork1r7n8i6kp5cnkibsc9q\gradle-9.5.0\bin\gradle.bat'
& $gradle --project-dir $taskRoot --offline --no-daemon --max-workers=2 '-Djava.io.tmpdir=C:\App\Bee_search_stage0_overlay_registry\build\tmp' '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest --tests 'org.beesearch.app.domain.gpx.*' --tests 'org.beesearch.app.data.gpx.GpxCm1EncoderTest'
& $gradle --project-dir $taskRoot --offline --no-daemon --max-workers=2 '-Djava.io.tmpdir=C:\App\Bee_search_stage0_overlay_registry\build\tmp' '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest
```

| Executed gate | Actual result |
|---|---|
| Targeted current Slice2 JVM | BUILD SUCCESSFUL;37 tests = Model8 + Values14 + Encoder15;0 failures/errors/skips |
| Required full offline JVM | BUILD SUCCESSFUL;988 tests/115 classes;0 failures/errors/skips |
| Affected compilation | compileDebugKotlin + compileDebugUnitTestKotlin successful in targeted run |
| Diff/scope | git diff --check PASS; index EMPTY; no stage/commit/push/device use |

Local raw evidence: `build/evidence/d106-slice2/targeted.log`, `targeted-xml/`,
`full-jvm.log`, `full-jvm-xml/`, `results.json`, `source.json` (six new source/test files).
Source inventory SHA256: `0E722DD8998BC0884FFC354215D348A615B418CB97B4E8191BB77794BA235834`.
The initial attempt failed compilation of the new model alias test (missing generic
argument in MutableList cast); fixed locally to MutableList<*> and rerun successfully.
`initial-compile-failure.log` preserves that attempt. Existing SnapshotJson/backup-test
compiler and DataStore/Unsafe JVM warnings were observed and left unchanged.

Decomposition: three cohesive production files, each below300 lines; model ownership,
canonical values and CM1 encoding remain separate. Delegated workers were unavailable
(service usage limit before any file creation), so root implemented and performed the
adversarial self-review with reduced independence. No independent reviewer PASS is
claimed; the uncommitted diff awaits independent review/OWNER decision.

Remaining obligations: XML integration/extraction and actual pipeline enforcement of
value bounds, full H05 persistence/protection and near-ceiling canonical-memory/resource
measurements remain future gates. The1000-sample counting-sink test proves streaming
API behavior, not100000-sample RAM/capacity. No Android runtime result or new production
RAM/time/precision/input ceiling is claimed here. Slice3+ remain unauthorized.

### Post-review / OWNER acceptance / local commit — 2026-10-14

**CURRENT STATUS: SLICE 2 CLOSED WITHIN ITS DECLARED PURE-JVM SCOPE.**
After the uncommitted implementation and verification recorded above, independent
implementation review returned **ACCEPT**, BLOCKER/HIGH/MEDIUM **0/0/0**. OWNER then
accepted Slice2 (model/canonical values/exact decimal/canonical time/CM1) within that
scope and authorized exactly one controlled local commit of the reviewed state.
The commit-only task made no source/test/documentation/evidence changes and reran no
tests; the historical37/37 targeted and988/988 full JVM results predate the commit.

- Local commit: `ae291906b186e2c8fdda94e167af7f07e4dac620`.
- Parent: `bf36ddd481ca6a04d572635699095417f991173e`.
- Subject: `feat(gpx): implement D106 Stage 1 Slice 2`.
- Commit contains exactly the reviewed9 project paths; build/evidence was not committed.
- Push: **NOT PERFORMED**; no push authorization.
- Slice1 remains accepted/qualified, CLOSED, previously committed locally as the parent.
- Slice3: **NOT AUTHORIZED / NOT STARTED**; no further implementation is authorized.

This closure does not qualify XML extraction, XML→canonical/provider→canonical parity,
XML→CM1 integration or actual XML-pipeline enforcement of value bounds. Full H05
persistence/restore/protection/logical-ID behavior and near-ceiling canonical-memory,
resource/capacity evidence remain future gates. Restore without GPX reparsing remains
an accepted requirement, not a Slice2 restore implementation or runtime claim.
This post-commit housekeeping updates status only; no staging/commit/push is authorized.

## 27. Slice 3 execution/status — 2026-10-14

**HISTORICAL ATTEMPT 1.** Result, stop and next-action statements in this section
record the state before the separately authorized fixture-only retry. They are
preserved as evidence; §28 supersedes their CURRENT operational status. Accepted
requirements and the ATTEMPT-1 failure are not rewritten.

**RESULT: FAIL — qualification stopped after API29 test-corpus failure.**
Slice3 is NOT QUALIFIED, NOT OWNER-accepted and remains UNCOMMITTED/UNSTAGED.
This records a new fixture defect, not a demonstrated violation in the closed
Slice1/2 production implementation. API37 was deliberately NOT RUN after the failure.
Slice4/5, commit and push remain NOT AUTHORIZED. Stage1 is not complete.

### Authorization, baseline and component boundary

Separate explicit OWNER authorization covered profile interpretation, XML→canonical
integration and JVM/API29/API37 verification only. PAC was unavailable; the fresh
OWNER authorization fallback was used, without simulating a declaration.
Started CLEAN with EMPTY index on task/d106-stage0-overlay-registry at
`db03b897e3cc0e995368551c510f7c8d9a93a8a3` in the isolated task checkout.
Sections1–26 are preserved verbatim; accepted Stage1A/1B and closed Slice1/2 code
were not modified. External API/profile research used the official GPX1.1 schema
and Android XmlPullParser documentation; the accepted Bee Search subset governs.

Five new production files (under app/src/main/java/org/beesearch/app/):
- domain/gpx/GpxProfileEvent.kt — normalized facts, bounded warning/result/address types;
- domain/gpx/GpxProfileSchema.kt — supported parent/order/cardinality projection;
- domain/gpx/GpxProfileDraftBuilder.kt — tentative owned builders and immutable freeze;
- domain/gpx/GpxProfileParser.kt — pure event state machine, not another XML parser;
- data/gpx/AndroidGpxProfileReader.kt — bridge through unchanged AndroidGpxXmlReader.verify.

New tests: domain/gpx/GpxProfileParserTest.kt in src/test/java and
 data/gpx/GpxProfileDeviceTest.kt in src/androidTest/java, under org/beesearch/app.
Corpus: app/src/test/resources/gpx/slice3/, 74 XML literals and two TSV files.
No model/value/CM1 duplication, UUID allocation, storage, publication, Territory,
Room, UI or renderer integration. Pure production profile files have no data/XML
or Android dependency. Each production file is below300 lines; lexical security,
provider adaptation, profile rules, builders and canonical values stay separate.

The intended implementation preserves source order/zero-based ordinals, empty
tracks/segments and singletons; calls Slice2 exact coordinate/elevation/time helpers;
keeps ignored subtrees non-canonical; summarizes only accepted warning categories
with counts/first bounded address. The final immutable graph is returned only after
upstream EOF/security/source-equality/close and profile completion succeed. Rejection,
cancellation and late IO discard tentative state. These statements describe code
and the successful subsets below, not full Slice3 qualification.

### Contract-derived coverage and proof boundary

Basis: Stage1B §§3–8/11–15; Stage1C §§4/15/17. Mechanically selected S3 subset:

| Families | Intended assertion | Proof layer / limitation |
|---|---|---|
| A01–A09 | Structure/order/empty/singleton, metadata, values/time | J normalized facts; A real XML; A01 independent literal205-byte CM1 |
| B01–B09 | Canonical equivalence under lexical/non-canonical changes | J core where independent of XML; A encoding/token/reference integration |
| C01–C09 | Ordered/material differences and ignored C07 equality | J core + A XML→canonical/CM1 |
| D01–D14 | Zero samples, structure/value rejection and warnings | J profile cases + A actual XML/category precedence |
| E01–E04,E10 | Supported encoding envelope / XML1.1 rejection | A actual qualified input chain; not a pure-profile JVM claim |
| F08,F09 | XML duplicates and ignored/schema/PI profile behavior | F08 A; F09 J profile + A; existing S1 no-IO regressions separate |
| G02,G03,G04,G08 | Inherited profile count/text helpers and small cases | J core + A small fixtures; S4 numerical triplets/resources NOT qualified |

Total52 existing families, not a replacement92-family corpus. Exact assertion→J/A
method→expected category mapping is committed-candidate `coverage.tsv`; `manifest.tsv`
lists52 primary XML rows, with22 additional isolated variants. No new family ID.
JVM uses hand-authored normalized events, not SAX/DOM or another XML parser.
JVM evidence is not actual XML evidence. Existing Slice2 pure proof is not reopened.

This is an INTENDED coverage map, not52 runtime PASSes: the primary Android loop and
pair loop aborted at B08. Other independent methods ran, but unvisited loop rows
must not be treated as covered by that run. B05 event-budget work, F09 broader
observation/resource evidence, G02–04/G08 boundary/resource triplets and other S4
limits remain future gates. S5/coordinator/identity/publication and full H-family
persistence/restore/protection/presentation remain future; restore without GPX
reparsing remains accepted and unimplemented here.

### Frozen revision, build and JVM evidence

Local ignored evidence: `build/evidence/d106-slice3/`.
`source-pre.json` and `source-post.json` each inventory all83 new source/test/corpus
paths with byte lengths and SHA256. Both inventories have SHA256:
`B289A88DFC79C108DE454493C599FEEEE8E785A181FF01742E83804F9BDD9FDC`.
83/83 files matched after the run; no source changes after the final freeze.
Earlier revision evidence is retained in `initial-revision/`: targeted19/full1007
passed there, but androidTest compilation found an extra closing parenthesis in
new C04 expected-tree code. Only that syntax error was fixed, then a new freeze and
all final JVM/build runs were executed. No earlier result substitutes for final proof.

Existing cached Gradle9.5.0 launcher and task-local offline cache were used. Only
process-local GRADLE_USER_HOME=build/gradle-home, ANDROID_HOME=existing SDK,
ANDROID_USER_HOME=build/android-user-home and TEMP/TMP=build/tmp; no global setting
or local.properties change. Common arguments:
`--project-dir <task-checkout> --offline --no-daemon --max-workers=2 -Djava.io.tmpdir=<task-checkout>\build\tmp -Pkotlin.compiler.execution.strategy=in-process`.
Final task arguments and observed results:

| Task arguments | Result |
|---|---|
| :app:testDebugUnitTest --tests org.beesearch.app.domain.gpx.GpxProfileParserTest --rerun-tasks |19 tests/1 class;0 failures/errors/skips; BUILD SUCCESSFUL |
| :app:testDebugUnitTest --rerun-tasks |1007 tests/116 classes;0 failures/errors/skips; BUILD SUCCESSFUL |
| :app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks |BUILD SUCCESSFUL; Kotlin/app/androidTest compilation succeeded |

Logs, timing JSON and copied JUnit XML retain final command results. Existing unrelated
compiler/DataStore warnings were left unchanged. No MapLibre/native test class/activity
was selected or initialized for XML qualification; packaging the existing app still
includes its existing native libraries.

Built and installed-on-API29 APK SHA256 (aapt confirmed DEV IDs before installation):
- org.beesearch.app.dev: `24037620B9807BDDD3DA1C169A9FE02829F2BE02B64F684B0815A0B5CC17364E`.
- org.beesearch.app.dev.test: `F7DA91B3898B41472154F08D647B4DB9167260070A6DCD58801FF885341AE54B`.
`apk-inventory.json`, aapt package records and API29 installed-base-APK sha256sum
records connect the frozen source/build with the actual runtime pair. adb process
exit0 alone was NOT treated as PASS: the instrumentation result reports failures.

### API29 runtime failure and stop

AVD BeeSearch_ZIP64_API29; actual serial emulator-5554; API29/Android10/x86_64;
sys.boot_completed=1. Fingerprint:
`google/sdk_gphone_x86_64/generic_x86_64:10/QSR1.211112.011/13135432:userdebug/dev-keys`.
Observed provider `com.android.org.kxml2.io.KXmlParser;api=29`;
configuration namespaces=true;docdecl=false;relaxed=false;validation=false.
Direct serial-scoped command (no broad connected task):

```text
adb -s emulator-5554 shell am instrument -w -r -e class org.beesearch.app.data.gpx.GpxXmlCapabilityTest,org.beesearch.app.data.gpx.GpxXmlSecurityMatrixTest,org.beesearch.app.data.gpx.GpxProfileDeviceTest org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner
```

Runner: Time73.254s; **Tests run:37, Failures:2**. Status records contain35 successes
and2 failures. The existing25 Slice1 methods completed successfully; one is finite
unguarded provider characterization/context, not an independent security assertion.
New Slice3 methods:10 succeeded,2 failed:
- everySlice3FamilyHasAnIndependentFixtureAndExpectedOutcome;
- actualXmlPairsHaveIndependentCanonicalAndCm1Relationships.

Both fail at the same new B08.xml attribute literal `lat="&#000...001;"` (ellipsis
here abbreviates actual leading zeroes). Its numeric value is1, hence forbidden
XML1.0 U+0001, NOT the character digit1 (U+0031). The correct Slice1 lexical barrier
rejects it in preflight as **MALFORMED_XML:REFERENCE_SCALAR**. Thus the fixture is
not a valid canonical-equivalence vector. No closed Slice1/2 defect or guard/provider
semantic disagreement is demonstrated by this failure. Root owns the fixture error;
prior read-only review did not detect it and is not runtime proof.

Per the task's runtime-failure stop rule, B08 expectation/input was NOT altered after
this run. No fallback, relaxed guard, dependency or new ceiling was introduced.
API37 AVD exists but was NOT BOOTED/TESTED; no API37 Slice3 PASS is claimed. API29 was
serial-identified and stopped; final adb devices list EMPTY. Both original AVD
config hashes match the preflight values; no wipe/clear/uninstall/snapshot deletion,
physical-device command, SDK provisioning or AVD recreation occurred.

### Remaining decision / qualification work

Slice3 is not ready for acceptance. Minimum next action for OWNER/reviewer: review
the new B08 fixture defect, then separately allow a targeted fixture correction and
fresh frozen-current JVM/API29/API37 verification. Digit1 can be represented by a
numeric reference to codepoint49 (decimal) or31 (hex); no production security change
is indicated. That correction and rerun have NOT been performed after the stop.
No Slice4/5 implementation is authorized. Index remains EMPTY; no stage/commit/push.

## 28. Slice 3 ATTEMPT 2 PASS — current qualification status (2026-10-14)

**D106 STAGE 1 SLICE 3 QUALIFIED WITHIN DECLARED PROFILE-PARSER SCOPE.**
**ATTEMPT-2 PASS; INDEPENDENT READ-ONLY REVIEW PASS CONFIRMED.**
This is the current operational status, superseding the historical ATTEMPT-1 stop
in §27. Slice3 remains UNCOMMITTED/UNSTAGED; no commit/push has occurred. No Slice4/5
work has started or is authorized. All of D106 Stage1 is NOT complete.

### Preserved chronology and one-file correction

ATTEMPT1 remains FAIL / NOT QUALIFIED: fresh JVM19/19/full1007 passed, API29 ran37
methods with35 successes/2 failures at B08, and API37 was NOT RUN after STOP.
The new B08 fixture's long decimal reference denoted forbidden XML1.0 U+0001;
Slice1 correctly rejected MALFORMED_XML:REFERENCE_SCALAR. No closed Slice1/2
production defect was established. Historical83-file inventory digest:
`B289A88DFC79C108DE454493C599FEEEE8E785A181FF01742E83804F9BDD9FDC`.
ATTEMPT-1 evidence was preserved byte-for-byte (282 files), not relabelled PASS.

OWNER subsequently authorized only the known fixture correction and complete fresh
qualification. Only `app/src/test/resources/gpx/slice3/B08.xml` changed between the
715 inventoried project paths; exactly two bytes changed, offsets248/249 (zero-based).
The328-byte fixture and63-digit/66-character long-leading-zero decimal reference
shape were preserved. Old/new reference spellings:

```text
&#000000000000000000000000000000000000000000000000000000000000001;
&#000000000000000000000000000000000000000000000000000000000000049;
```

The scalar changed from U+0001 to U+0031, ASCII DIGIT ONE; canonical latitude is1.
Old B08 SHA256:
`B9A72D05B7F10820FF605BD8F1965FDABFBEF0C5BA5B93C85B18E6C3F0D56C84`.
Corrected/current B08 SHA256:
`E8C7C969721BF4A2CA7F97845799487BF7FE530A1F707BA02AB7CAB19B5F4AA8`.
Production Kotlin, test Kotlin, manifest/coverage/other TSV and all other fixtures
were unchanged. No implementation repair, relaxed guard, new limit or dependency.

### Frozen-current qualification evidence

ATTEMPT2 evidence is separate: `build/evidence/d106-slice3/attempt-2/`.
`source-pre.json`/`source-post.json` cover83 source/test/corpus files;83/83 MATCH.
Both have SHA256:
`96F479A3A0C86EAEC35C226BD918DFD857482FF3E2BAB0F0E6731F0ED49A3488`.
This digest belongs to ATTEMPT2 only. No source changed after freeze or between APIs.
The retry deliberately left these three status documents unchanged until this
separate documentation-only synchronization; no qualification evidence is rewritten.

| Fresh gate | ATTEMPT-2 result |
|---|---|
| GpxProfileParserTest targeted JVM |19/19 PASS;0 failures/errors/skips |
| Complete offline JVM |1007 tests/116 classes;0 failures/errors/skips |
| DEV + androidTest build |PASS; fresh assembleDebug/assembleDebugAndroidTest |
| API29 BeeSearch_ZIP64_API29, emulator-5554 |Android10/API29/x86_64;37/37 PASS,70.505s |
| API37 BeeSearch_XML_API37, emulator-5556 |Android17/API37/x86_64;37/37 PASS,90.232s |
| Complete mapped corpus, EACH API |52/52 rows:38 accepted graphs +14 expected rejections |
| B08, EACH API |ACCEPTED; latitude1; padded reference retained; B01/B08 CM1 equality PASS |
| Previously unvisited post-B08 rows |Executed on BOTH endpoints |

Actual provider on both endpoints: `com.android.org.kxml2.io.KXmlParser`;
configuration namespaces=true;docdecl=false;relaxed=false;validation=false.
Same frozen source, instrumentation set, fixture expectations and exact built/installed
APK pair were used on both endpoints; tested canonical/XML semantics agree.
DEV APK SHA256:
`24037620B9807BDDD3DA1C169A9FE02829F2BE02B64F684B0815A0B5CC17364E`.
androidTest APK SHA256:
`13057187E61AC9D0DD021AC849B033637019186DBEB92B0E9E4D14F70250CBDF`.

37 instrumentation methods and52 manifest rows are distinct counts. The completed
52-row assertion loop validates expected graphs/categories; accepted rows additionally
have per-family logs, rejected rows have less granular logging. Full runner summaries
and status records establish PASS, not INSTRUMENTATION_CODE:-1 alone. One unchanged
Slice1 method is context-only provider characterization, not independent security proof.

API29/API37 ran sequentially; both stopped, final device inventory and emulator
process checks empty. Existing AVD configurations were preserved. No physical device,
SDK provisioning, wipe/clear/uninstall, Slice4/5, stage/commit/push occurred.
Exact commands, JUnit XML, instrumentation/logcat, installed APK correspondence,
source inventories and digests remain in the separate ATTEMPT-2 evidence area.

### Independent review and present authorization boundary

OWNER supplied the subsequent independent DeepSeek READ-ONLY artifact-review result:
**PASS CONFIRMED;0 BLOCKER,0 HIGH,0 MEDIUM,1 LOW; otherwise INFO observations.**
Reviewer independently inspected repository/evidence correspondence, two-byte B08
correction (including reconstruction of old bytes), APK-contained corrected fixture,
full corpus/test results, frozen inventories, endpoint isolation/stop and Git state.
Reviewer ran no Gradle/tests/emulators/adb and modified no files. Its one LOW was
stale current status in these three documents; this synchronization addresses that
status discrepancy only. INFO items do not authorize implementation or new tests.
This records the review result supplied by OWNER, not a new runtime/review execution.

Scope remains the accepted S3 profile/JVM + actual XML→canonical/CM1 subset mapped
in §15, not a new qualification of every assertion in all92 fixture families.
Still OPEN: Slice4 boundary/resource evidence, applicable G02–G04/G08 below/exact/over
triplets, B05 event-budget work, later F09 observation/resource evidence and near-ceiling
canonical-memory/resource proof. Slice5 processor boundary/coordinator handoff and
future coordinator/persistence/logical identity/publication/protection/presentation
integration remain unimplemented/unqualified. Full H05 restore-without-GPX-reparse
implementation/proof remains future; the accepted restore requirement is unchanged.
No later gate is closed by this S3 PASS. No commit exists for Slice3; no push occurred.
This documentation task authorizes no further implementation or staging/commit/push.

## 29. Slice 4 execution/status — 2026-10-14

**D106 STAGE 1 SLICE 4 QUALIFIED WITHIN DECLARED BOUNDARY/RESOURCE SCOPE.**
Uncommitted/unstaged; independent OWNER/reviewer inspection remains next. This is
qualification evidence, not OWNER acceptance or Stage1 completion. Slice5 NOT STARTED
and NOT AUTHORIZED. No commit/push performed by this task.

### 29.1 Baseline, scope and implementation

Started CLEAN/index EMPTY on task/d106-stage0-overlay-registry at
fd8e4dbf004e0def176bbe2b53efae805bf492c1, subject
`feat(gpx): implement D106 Stage 1 Slice 3`, parent db03b897e3cc0e995368551c510f7c8d9a93a8a3.
Slice3 is CLOSED / OWNER-accepted / qualified within its declared profile-parser scope
and locally committed as fd8e4dbf004e0def176bbe2b53efae805bf492c1; push NOT PERFORMED.
The pre-commit records in §§27–28 remain historical and unchanged.
S3 frozen inventory83/83 MATCH, digest96F479A3A0C86EAEC35C226BD918DFD857482FF3E2BAB0F0E6731F0ED49A3488;
corrected B08 remains E8C7C969721BF4A2CA7F97845799487BF7FE530A1F707BA02AB7CAB19B5F4AA8.
PAC tool unavailable: fresh explicit OWNER S4 authorization used; no simulated declaration.

Before implementation, mechanically derived Stage1B §§11–14 / this contract §§15–17
scope in ignored `build/evidence/d106-slice4/scope-matrix.md`. S4 owns all input
G01–G10, E/F security/encoding regressions, cancellation/resource observations and
G21/H07 memory subset; no storage/protection G11–G20 or full aggregate integration.
All accepted enforcement already exists in S1–S3. Production code was NOT changed;
no closed-slice repair, new numerical ceiling, dependency or second parser.

New qualification files (test-only):
- `app/src/test/java/org/beesearch/app/domain/gpx/GpxProfileBoundaryTest.kt`
- `app/src/test/java/org/beesearch/app/data/gpx/GpxXmlBoundaryTest.kt`
- `app/src/androidTest/java/org/beesearch/app/data/gpx/GpxBoundaryCorpus.kt`
- `app/src/androidTest/java/org/beesearch/app/data/gpx/GpxBoundaryMeasurement.kt`
- `app/src/androidTest/java/org/beesearch/app/data/gpx/GpxBoundaryDeviceTest.kt`

J(core): hand-normalized lazy profile events, raw counting/digest and lexical guard
helpers; no JVM XML-provider claim. A(XML): actual qualified S1 chain → S3 profile →
S2 immutable canonical graph/CM1. M: transport, cancellation, heap/allocation and
bounded guard-state records, supplementing structural proof.

### 29.2 Frozen revision and executed qualification

Ignored evidence: `build/evidence/d106-slice4/attempt-3/`. Freeze2026-10-14T15:17:38.9409121Z;
all106 GPX production/JVM/Android/corpus inputs included, source-pre/source-post
106/106 MATCH, SHA25688C41139C66A1BD15DFB4BA0A3507D66177C36CB800C54407DB50EEC22080709.
Status docs were unchanged during qualification and updated only after final source
comparison; they are not parser inputs. No source/test/generator edit after freeze.

Established task-local Gradle9.5.0 cache/SDK paths; offline, no-daemon, max-workers2,
process-local temp, Kotlin in-process. Exact argument arrays/timing/logs saved in
`targeted-command.json`, `full-command.json`, `build-command.json` and adjacent logs.
Targeted `:app:testDebugUnitTest --tests org.beesearch.app.domain.gpx.GpxProfileBoundaryTest
--tests org.beesearch.app.data.gpx.GpxXmlBoundaryTest --rerun-tasks`:11/11 PASS,2 classes.
Full `:app:testDebugUnitTest --rerun-tasks`:1018/1018 PASS,118 classes,0 failures/errors/skips.
`:app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks`:PASS.

Same DEV APK24037620B9807BDDD3DA1C169A9FE02829F2BE02B64F684B0815A0B5CC17364E
and androidTest APK596CA48B07744E7A2B788292C0810F677486B0F0090326637277B3F377C57737
built once for ATTEMPT3 and installed hashes verified on both endpoints; no rebuild between them.
Direct serial-scoped `am instrument -w -r -e class` selected only
GpxXmlCapabilityTest/GpxXmlSecurityMatrixTest/GpxProfileDeviceTest/GpxBoundaryDeviceTest
under org.beesearch.app.dev.test/androidx.test.runner.AndroidJUnitRunner.
48 methods =37 existing GPX regression +11 S4, not44 generated executions or52 S3 rows.
The existing context-only unguarded provider method remains context, not independent
proof. No MapLibre/native fixture/Activity was initialized by the selected XML tests.

| Endpoint | Serial / runtime | Instrumentation | S4 generated executions |
|---|---|---|---|
| BeeSearch_ZIP64_API29 | emulator-5554 / Android10 API29 x86_64 |48/48 PASS,187.491s,0 failures/skips |44, including30 boundary cases |
| BeeSearch_XML_API37 | emulator-5556 / Android17 API37 x86_64 |48/48 PASS,243.789s,0 failures/skips |44, including30 boundary cases |

Both provider com.android.org.kxml2.io.KXmlParser, namespaces=true/docdecl=false/
relaxed=false/validation=false. Fingerprints and AVD/API/boot identity, exact commands,
installed APK hashes, method list/results and logcat saved per endpoint. Existing S3
manifest conformance52 rows (38 accepted/14 rejected) also reran successfully.
API29 stopped and launcher/QEMU processes absent before API37 boot; API37 stopped
and final emulator/QEMU count0. AVD configuration hashes unchanged. Physical device
was visible unauthorized in discovery only, never serial-targeted; no physical action.

### 29.3 Boundaries, resource and cancellation results

All following triplets executed on JVM and actual integrated Android API29/API37:
below/exact ACCEPTED; over LIMIT_EXCEEDED, no partial canonical output.

| Family | Below / exact / over | Counting owner / semantics |
|---|---|---|
| G01 |16777215 /16777216 /16777217 bytes |Raw input; Android includes UTF8 BOM + bounded comments |
| G02 |127 /128 /129 tracks |Profile; empty tracks included |
| G03 |4095 /4096 /4097 segments |Profile; across2 tracks, empty segments included |
| G04 |99999 /100000 /100001 samples |Profile; integrated across2 segments |
| G05 |31 /32 /33 depth |Guard; root-inclusive, ignored subtree |
| G06 |63 /64 /65 lexical attributes |Guard/SDK reconciliation; xmlns included |
| G07 |1999999 /2000000 /2000001 events |Guard/SDK logical normalization, not callback totals |
| G08 |1023 /1024 /1025 code points |Profile; supplementary scalars, split-surrogate JVM variants |
| G09 |8191 /8192 /8193 decoded token scalars |Guard; ignored content, no callback reset |
| G10 |127 /128 /129 decoded lexical scalars |Before trim; pure coordinate/time + actual XML coordinate integration |

B05: accepted comments-added/removed preserve canonical/CM1 material while bytes
change. Independent total-event algebra:8 core START/END +2 per ignored empty x +1
per comment, with even/odd adjustment. Comment-only9/10 cases and2million-event
one-byte chunk variant assert CM1 equality/event counts. No counter oracle generated
from production parser. G09 million-digit leading-zero reference resolves scalar49
(latitude1), accepted with bounded reference state/emission; no raw-reference limit.
F09: integrated ignored schemaLocation/stylesheet/XInclude remains canonical-equal
with UNSUPPORTED_CONTENT warning. Warmed parser-thread StrictMode signals[], loopback
connections0 on both APIs; structural no-resolver/Reader/guard argument remains primary.
Best-effort observers are not universal syscall proof (UNKNOWN).

F03 adds an explicit deterministic test-owned file canary: atomic create refuses an
existing cache file, setup I/O occurs before StrictMode, an actual file-entity DTD is
rejected as SECURITY_REJECTED:DECLARATION_BARRIER during preflight (one input open,
owned stream closed, no provider-facing second-pass input), StrictMode signals[],
canary bytes unchanged, only the created file deleted. Canary unchanged alone is
not absence-of-read proof; combine the observer with structural declaration denial.
All42 measured parse records plus F03 and F09 observer records per endpoint are preserved
in logcat; parsed42-record JSON and independent60-result cross-endpoint table in
`api29/resource-records.json`, `api37/resource-records.json`, `boundary-results.json`.
They include inherited config, input/counts, pass/read/close/EOF observations, elapsed,
Runtime heap snapshots/max, ART allocated-byte delta, relevant bounded state and outcome.
The interval includes production parsing and post-result assertions; fixture generation
precedes it. ART deltas are process-wide observations and may include framework/thread
allocation, not an isolated production allocation profile.
Rejected/cancelled guard delivery/state unavailable through final-result API is UNKNOWN.
Exact heap peak is UNKNOWN; snapshots and cumulative allocation are not peak memory.

At100000 samples:2400131 raw bytes/pass,2 passes,200008 logical events; canonical
1 track/2 segments/100000 samples accepted on both. API29 measured parse+validation interval4.567197800s,
heap66858976→31791720 bytes, allocated744196528 bytes; API37 measured interval5.625145348s,
heap43275376→39265392 bytes, allocated687497216 bytes. These are observations,
not new capacity guarantees or RAM/time thresholds. Guard maxPiece80 UTF16 units,
maxPending1, maxToken33, maxDepth4 on this corpus. No OOM/bound corruption observed.
Structural review: append-only owned lists, one reference-list freeze per completed
node, O(N) canonical graph; no repeated full-tree copying or retained original XML.
Pass1 creates no canonical graph. Test input allocation precedes parse measurements.

Near100000 cancellation in pass1/pass2 at2162704 bytes, and after verified EOF/close,
all terminated at the requesting checkpoint, closed owned streams and returned no
partial graph. JVM separately exercises cancellation before and after canonical freeze.
Late second-pass EOF/read and close failure return INPUT_IO_ERROR, no Accepted result.
Tentative parser state clears on abort/failure/finish; counter arithmetic is checked
or bounded before increment/allocation. Structural review saved separately.

### 29.4 Scope and remaining gates

Stage1C §§1–26 and historical §§27–28 preserved byte-for-byte as the original prefix.
Slice1–3 production/tests/corpus bytes unchanged (including committed manifest terminal
TAB empty-warnings representation). Only5 new test/generator files +3 status docs;
index EMPTY, no commit/push. No device provisioning, wipe, uninstall or pm clear.

Still OPEN: S5 parser boundary handoff/typed result/coordinator integration; actual
persistence, managed-original storage, logical identity allocation/addressing,
publication/protection, full H05 restore without GPX reparsing, presentation and future
storage/protection/aggregate capacity domains. G11–G20 and full G21/H07 integration
are not qualified here. Stage1/GPX import as a whole is NOT complete. Slice5 remains
NOT AUTHORIZED / NOT STARTED. Next: independent review of the unstaged S4 diff.
### 29.5 Attempt chronology

ATTEMPT1 is preserved under `build/evidence/d106-slice4/attempt-1/`: frozen106
inputs digest3EF692921D65037F4A9F20FA54ADF72E1D1F1C7859F621FF8548338644B5D4CF,
JVM11/1018 and both APIs47/47 PASS (191.781s/251.152s), test APK5D40C40F...2230CDC2.
Final §16 audit identified an evidence gap: the old file:///d106-unowned F03 probe
was not a test-owned canary. No production defect was established. One new S4
canary method was added; earlier green runs are historical, not final qualification.
ATTEMPT2 was frozen but NOT RUN, superseded before execution to make the canary
filename deterministic with no-overwrite ownership. Its frozen inventory is retained.
ATTEMPT3 is the final current qualification above: same106-file inventory scope,
new digest88C41139...22080709, fresh full JVM/build and both APIs48/48 PASS,
44 generated executions each (42 measured parses +F03/F09 observers),30 triplet
cases each. All attempts remain distinct; no historical evidence was overwritten.

## 30. Closed Slice1 cancellation corrective — execution/status (2026-10-14)

**CORRECTED AND REQUALIFIED — READY FOR INDEPENDENT REVIEW.** This is a separate
OWNER-authorized corrective cycle, not Slice5 qualification or OWNER acceptance.
Starting/current HEAD: `67a55452599a57d44d884d5c3dee57df7bfd5313`, branch
`task/d106-stage0-overlay-registry`; clean worktree/empty index verified before edits.
That HEAD is the OWNER-accepted local Slice4 commit, parent fd8e4dbf..., subject
`test(gpx): qualify D106 Stage 1 Slice 4`. No push occurred.

### 30.1 Discovery, contract and narrow correction

Slice1 was previously accepted. Later S5 preflight discovered a combined-failure
counterexample: an in-flight CancellationException was replaced by a source-close
IOException translated to `INPUT_IO_ERROR:source-close`. S5 stopped before production
implementation. Its ignored preflight evidence remains historical and unchanged.

Stage1B §12 and Stage1C S15/§13 require abort/discard and cancellation rethrow.
The contracts do not specify a general primary-versus-close precedence matrix.
This correction changes only cancellation preservation: the SAME cancellation object
escapes both preflight and parser-pass cleanup. A secondary bounded source-close
failure is attached as suppressed, following existing project cleanup conventions;
it is not made the cause or a new public result category. Without in-flight
cancellation, existing close behavior/precedence remains unchanged, including
standalone `INPUT_IO_ERROR:source-close`. No new general error policy was introduced.

Production changes are limited to:
- `app/src/main/java/org/beesearch/app/data/gpx/GpxInput.kt`: cohesive internal
  cancellation-aware close helper; original close and constructor behavior retained.
- `app/src/main/java/org/beesearch/app/data/gpx/AndroidGpxXmlReader.kt`: capture/rethrow
  cancellation and use that helper in both existing owner-finally blocks.

The constructor already preserves the primary throwable and was not changed.
Guard/raw close delegation and profile abort/rethrow were inspected and retained.
Decoder/security/provider/profile/value/model/CM1/identity/limits semantics were not
redesigned. No dependency or Slice5 processor/result file was added.

### 30.2 Frozen-current qualification

New tests:
- `app/src/test/java/org/beesearch/app/data/gpx/GpxCancellationCloseTest.kt` (6 methods).
- `app/src/androidTest/java/org/beesearch/app/data/gpx/GpxCancellationCloseDeviceTest.kt`
  (4 methods; success plus each-pass standalone close and cancellation/close variants).

ATTEMPT1 was frozen BEFORE final JVM/build/Android runs. Corrective inventory4/4
pre/post MATCH, SHA256:
`2818045720D6394EED61F2E6C3571582BBEF8C4C24602E66F669E6C66B43E6F3`.
Combined current GPX inventory108/108 pre/post MATCH, SHA256:
`365C0281EEA2228F72242B79DB5E75D09766B06A4C203C84BC9845ABF29EA18C`.
Old accepted Slice4 inventory digest
`88C41139C66A1BD15DFB4BA0A3507D66177C36CB800C54407DB50EEC22080709`
remains historical: two production files intentionally differ;104 other inputs match.
Do not describe the corrected chain as the old106/106 revision.

Targeted corrective + existing GPX JVM:97/97,10 classes, including6 corrective tests.
Full JVM:1024/1024,119 classes. Both have0 failures/errors/skips.
All seven gates PASS: compileDebugKotlin, compileBetaKotlin, compileReleaseKotlin,
compileDebugAndroidTestKotlin, assembleDebug, assembleDebugAndroidTest, lintDebug.
Offline/no-daemon/max-workers2/in-process compiler and task-local temp/cache setup
were used; exact launcher, argument arrays, logs and JUnit XML are in evidence.

One APK pair was built after freeze and used unchanged on both endpoints:
- DEV SHA256 `8FEE95D15FED8E5EE29EE1B9C36B63E7362C381D504495DA02420D17CBB9324C`.
- androidTest SHA256 `9DC589E1CD3BC9A8B1E3351BF349E3718F4F4F3B014CE6EBC61AB8C1334183D8`.

| Endpoint | Actual serial | Result | Instrumentation time |
|---|---|---|---|
| BeeSearch_ZIP64_API29, Android10/API29/x86_64 | emulator-5554 |52/52 PASS|191.346s|
| BeeSearch_XML_API37, Android17/API37/x86_64 | emulator-5556 |52/52 PASS|248.771s|

Each endpoint ran48 existing GPX regression methods plus4 corrective methods,
using actual AndroidGpxProfileReader/AndroidGpxXmlReader, not an alternate parser.
Provider: com.android.org.kxml2.io.KXmlParser; namespaces=true,docdecl=false,
relaxed=false,validation=false. Direct assertions prove same-object cancellation,
bounded suppressed close failure and no returned canonical success on BOTH passes;
standalone close failure remains exact INPUT_IO_ERROR:source-close. Successful
canonical parsing and existing Slice1–Slice4 regression remain green. The pre-existing
EOF-close/finally retry on standalone close failure was not changed into a new policy.
Success is based on OK(52 tests) and52 individual passed outcomes, not code-1 alone.

Endpoints were sequential, both stopped with no remaining device/emulator process,
and AVD configs unchanged. Explicit serial-scoped identification/install/instrumentation
commands and installed APK hash correspondence were preserved. No physical device,
uninstall/clear/wipe/provisioning, dependency change, staging, commit or push.

Evidence: `build/evidence/d106-slice1-cancellation-corrective/contract-audit.md` and
`attempt-1/`: source/combined inventories, JVM summaries/XML/logs, build-gates/APK
inventory, endpoint commands/identity/method lists/results/stop and correspondence.
Historical Slice1–Slice4 evidence and the entire prior Stage1C prefix through §29
are preserved; this section records the later discovery and corrective qualification.

### 30.3 Current boundary / next action

Corrective diff remains UNSTAGED/UNCOMMITTED for independent review and separate
OWNER acceptance. Slice5 implementation is NOT STARTED / BLOCKED UNTIL CORRECTIVE
ACCEPTANCE; a fresh OWNER continuation decision is required. No Slice5 qualification
is claimed. Actual coordinator, logical IDs/addressing, Room, managed original,
duplicate/reimport workflow, publication/protection, backup/snapshot, full H05 restore
without GPX reparsing, presentation/MapLibre/Registry and future capacity gates remain
open. Stage1 as a whole is NOT complete. Do not resume S5 or any future-owner work.