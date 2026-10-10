# I6 owner visual corrections #2 — 2026-10-10

Historical visual-correction report. Physical temporal model/Room/wire status below is superseded by D103 and temporal design §25; the earlier no-schema-change STOP is no longer the current task boundary.

Base HEAD remains `20a7c55835f2d129908da899f743e9583414f42a`; existing dirty I6 architecture retained.
No staging, commit, push or I7. Room13/entity schema/migrations unchanged.

## Point preview

Previous marker label selected `beePresenceResult`, which explained «пчёлы найдены» even though
that result does not describe record cardinalities. Marker and compact card now share the real
ObservationPointSummary projection: `beeCount=COUNT(DISTINCT b.id)` and
`totalFlightCycleCount=COUNT(c.id)` from the same joined/grouped query and HAVING predicates
used by I6 count filters. Completed-cycle count remains separate for existing browser semantics.
No second preview query or recalculation. Exact UUID/coordinates remain unchanged.

Preview: `Точка N, X пчела/пчелы/пчёл, Y цикл/цикла/циклов`. Russian forms include 11–14 and
compound values. Zero Bee records: `Точка N, пчёлы не найдены`, independent of a stale/pending
presence-result field. Actual Samsung Point16: UUID `f7cb1543-5779-41fc-8a09-f0f6d60b2ecb`,
4 bees / 8 total cycles, matching the narrowly scoped read-only query and visible preview.

## Physical dates — STOP on canonical data correction

Exact path: PhysicalObjectEntity.createdAt/fixationDate → RoomPhysicalObjectRepository.toHollow/
toLogHive → domain createdAt/fixationDate. Full HollowCard/LogHiveCard displays a row `Создано`
from createdAt.displayDateTime (`dd.MM.yyyy HH:mm`, system zone). Map marker label uses only
fixationDate via researchDateText (`dd.MM.yyyy`). Full record does not currently show a separate
canonical fixation-date row. Mapping does not discard a saved canonical date.

Read-only on-device audit, owner-requested records only, Europe/Moscow:

| Record / UUID | Persisted created_at | Full record `Создано` | fixation_date | Final canonical preview date |
|---|---|---|---|---|
| Дупло10 / 14bf5e39-d3ca-4245-a1cb-8287174c3490 | 1791446361564 | 08.10.2026 10:59 | NULL | unknown |
| Колода1 / d170bb70-8e91-4b2a-85b9-dd6803c8f948 | 1790850344115 | 01.10.2026 13:25 | NULL | unknown |

Evidence: build/i6-visual2-evidence/owner-date-audit.txt and *-record-date.png.
Entity/subtype date fields contain no other persisted object-research date. This is a mismatch
between user-visible technical creation time and unknown canonical fixation date, not a projection
loss. Migration12→13 intentionally adds nullable fixation_date without backfill; this is consistent
with legacy records, but the exact historical migration/import provenance of these two NULLs
was not independently reconstructed. New object creation already persists automatic fixationDate.

Per explicit owner STOP rule, no hidden backfill, fallback, schema/data/migration change performed.
Bounded temporal filters still exclude these NULL records. Filling these dates requires a new
owner decision about legitimate date provenance / legacy policy. Item2 is NOT reported fixed.
When a canonical date is available, preview now uses the compact existing dd.MM.yyyy format,
for example `Дупло N, 09.10.2026`; genuine unknown keeps `дата фиксации не зафиксирована`.

Full DB/WAL export was rejected by automatic approval review as broader than this audit. The
safe alternative uses the installed AndroidJUnitRunner and SQLiteDatabase.OPEN_READONLY,
selecting only the two dates and Point16/22 counts; no full DB export or owner writes.
The audit is explicitly opt-in via ownerPhysicalDateAudit=true, and is excluded from normal suites.

## Whole type-editor action

All types use header → weighted scrollable accordion content → divider → separate bottom footer.
Exactly one `filter-done-TYPE` exists, outside the scroll/accordion subtree. Inside each section
only its local reset remains. Footer consumes layout space and never overlays the final section.
Standard ModalBottomSheet handles IME/system insets. Changes remain immediate; Done/Back returns
to the main type list and keeps the panel/filter values. This is not an Apply transaction.

Samsung fontScale1.7: Period, Bee count, Cycle count, Hollow/LogHive height and diameter checked.
Done remains separate, accessible above numeric IME; pressing it with IME returned to type list.
Screenshots: build/i6-visual2-evidence/editor-*.png.

## Evidence and limits

Focused JVM17/17 (three presentation suites); full JVM878/878; combined Android85/85, no failures,
errors or skips. Additional owner read-only audit1/1. Deterministic Room test proves preview counts
agree with exact count filters including open cycles, while completed counts remain distinct.
Presentation tests cover UUID, pluralization, zero bees, known Hollow/LogHive dates and NULL with
no created_at fallback. Footer test visits every applicable section of every type and checks one
Done with footer ancestry and absence of scroll-content ancestry. Existing reset/Back/session
regressions remain. Build/lint evidence: build/i6-visual2-quality-final.log; Android final log:
build/i6-visual2-android-final.log. Final independent review follows all final changes; final owner
response records current B/H/M, not older review results.

Naturally present zero-bee Point22 is confirmed by read-only query. A device tap at overlapping
Point22/19 marker bounds selected topmost Point19, so zero-bee preview device evidence is NOT
claimed. No clustering/spiderfy/spacing or changed marker size/hit interaction introduced.
Physical preview/full-record date equality is NOT claimed for the two NULL legacy dates; this
remains stopped pending owner data decision. Full record creation dates were visually verified.

Samsung in-place DEV install: APK SHA256
`BAE937AD78703CCBE332C62C900B9A46BD5B44D3453ACCFBA483D184EFFAACB6`, update09:37:18.
No fake records, clear or uninstall. Display restored exactly to this turn's owner state:
OP visible Sep–Oct2026, bees4–5, cycles unrestricted; Hollow visible Sep–Oct2026, height4–6cm,
other measurements unrestricted; LogHive hidden/all criteria unrestricted. Before/after PB
snapshots verify equality. This differs from the previous turn's pre-test state because owner
had changed settings between turns; the current state was preserved.

Decomposition reviewed; kept cohesive because: aggregate SELECT/read-model mapping stays in the
existing DAO/repository path; marker formatting remains in SavedObjectMarkers; MapDataPanel owns
the cohesive type editor. No new production architecture/dependency.

Primary technology research: Android quantity resources and standard Compose insets were checked:
https://developer.android.com/guide/topics/resources/string-resource#Plurals
https://developer.android.com/develop/ui/compose/system/insets-ui
The existing pure Kotlin Russian presentation path uses a small deterministic Russian quantity
formatter, avoiding a new Android context/resource dependency in marker query assembly.


## D103 continuation verification — current dirty I6

Physical temporal correction now implemented: normative decision [D103](decisions.md#d103--physical-object-fixation-moment-and-modification-boundary), audit/model [temporal design §25](temporal-data-model-design.md#25-owner-correction-physical-object-fixation-moment), backlog pointer [I017](ideas.md#i017--physical-object-fixation-moment-and-modification-boundary). Earlier Room13/no-schema-change statements above are historical.

Current results: JVM **887/887**, 107 suites; PC Snapshot verifier **101/101**, 7 suites. Android focused data **33/33**, card UI **17/17** = **50/50**, all final runs without failures. Samsung opt-in readonly audit **1/1**. Build/compile tasks: testDebugUnitTest, assembleDebug, assembleDebugAndroidTest, compileDebugAndroidTestKotlin, compileBetaKotlin, compileReleaseKotlin and lintDebug pass. Lint: 0 errors, 28 warnings, 6 hints. git diff --check passes. Earlier failing/aborted test runs are superseded by named final logs, not counted as passes.

Logs: build/i6-physical-quality-pass.log; build/i6-physical-quality-final2.log (final JVM887); build/i6-physical-android-rebuild2.log (final test compile/lint); build/i6-physical-android-data-final.log; build/i6-physical-android-cards-pass.log; build/i6-physical-pc.log. Focused tests cover single captured creation instant, saved timezone-free calendar projection, immutable fixation/createdAt under own edits, no-op/read/filter behavior, direct media modification/rollback, no legacy migration fabrication, V8 restore/V7 date-only compatibility/loss guard, V3 wire/old readers and honest card formatting. Inspection is absent; no fake child capability was added for tests.

Samsung current DEV APK SHA256 `781CC81D77CC47FF04697586CDF27E522990B9394B664B63881F5F7229320AA8`, update 2026-10-10 10:25:05. At fontScale1.7 Hollow10/LogHive1 full cards display `Зафиксировано: момент фиксации неизвестен`; LogHive1 compact preview opens the real record and Back returns to map. Narrow SQL confirms both legacy fixation_date/fixation_at/updated_at NULL and original created_at unchanged. Original display/filter DataStore restored byte-identically; no owner records created/edited. Long LogHive preview uses the existing three-line ellipsis, while full unknown text is available in its record. Known historical fixation for those rows remains unproven; no createdAt fallback/backfill. Evidence: build/i6-physical-evidence/{owner-date-audit.txt,hollow10-record.png,loghive1-record.png,loghive1-preview.png}.

Emulator aborted one run due system GraphicBuffer.finalize timeout; final separate data/card runs passed. Owner visual acceptance remains pending; no assertion that current device testing proves unknown historical fixation. No staging/commit/push/I7. Protected verifier TOML hash unchanged. NEW final complete-diff independent review follows these final substantive doc/test/code changes; verdict belongs to the final owner report.

Decomposition reviewed: physical repository remains cohesive transaction/mapping boundary; BackupCore remains the existing tightly coupled format/graph/restore implementation. Existing map panel/editor decomposition retained; shared physical fixation formatter extracted because full card and map preview require the same independently tested presentation rule.
