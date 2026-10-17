# Unified Territory Data Map — execution checklist

> Current D106 Stage0 verification (2026-10-12): **STAGE0_VERIFICATION_PASS within the declared
> emulator + Samsung scope.** Samsung ONLINE and AreaEditor recreation methods PASS; emulator
> retains its known pre-Stage0 blocker. See Samsung physical closure for scope and remaining UNKNOWNs.

> Previous emulator-only D106 Stage0 closure (2026-10-12):
> **EMULATOR_VERIFICATION_PASS_WITH_KNOWN_PREEXISTING_NATIVE_BLOCKER / SAMSUNG_VERIFICATION_GO.**
> ONLINE and the known AreaEditor recreation method remain BLOCKED, not PASS; physical verification
> is NOT PERFORMED. Earlier NO-GO attempts are historical. See the final independent-gates section
> of lifecycle evidence for current results and the explicit unverified assertions.

Операционный checklist работ по I016. Это **не** specification и **не** источник project truth: он не
заменяет и не переопределяет accepted decisions, requirements, domain или data model. Authoritative
остаётся в `docs/decisions.md`, `docs/architecture.md`, `docs/data-model.md`,
`docs/product-requirements.md` и `docs/user-workflows.md`; `docs/ideas.md` — non-authoritative backlog
и контекст объёма. Checklist только ссылается на них и фиксирует порядок этапов.

Продолжение работ по текущему milestone остаётся в `.agent/handoff.md`.

Авторитетные опоры:

- I016 — `docs/ideas.md` (Status: `idea`) — цель и объём Unified Territory Data Map;
- D094 — `docs/decisions.md` — временная семантика и семантика обычного фильтра по периоду;
- `docs/architecture.md` §73.3 / D102 — ACCEPTED MapLibre/Compose split и lifecycle safety;
- `docs/data-model.md` §22, §63, §71.1 — что из временных фактов записано в модели сейчас;
- I014 — user georeferenced rasters; I011 — GPX и пользовательские полевые данные; I001 —
  аналитические overlays.

## OWNER development order — 2026-10-16

Current planning decision: [unified local Complete Backup/Restore](decisions.md#unified-local-complete-backuprestore--owner-decision-2026-10-16).
Continue D106, then the necessary user entities, media and user raster maps;
return to the unified local Backup/Restore after the main stores stabilize.
New stores must expose clear ownership/storage/enumeration/read/restore rules
and join the common mechanism rather than add a separate backup per data type.
Before full use with irreplaceable user data, Restore must be implemented and
tested on a real device. Android Auto Backup remains unchanged and outside the
current work plan unless a concrete need arises.

This is a development sequence, not gate closure or runtime authorization.
Stage1, H05 and the existing protection/restore gates remain OPEN; no user GPX
import is activated. Earlier stage tables retain their capability/exit obligations.
No speculative risk creates mandatory work without demonstrated impact.

## Done

- [x] I016 сформулирована и temporal section reconciled.
- [x] D094 ACCEPTED.
- [x] research date отделена концептуально от technical created_at.
- [x] MapLibre/Compose class-based split принят владельцем как D102 (2026-10-08).

## UI: Layers/Filters (mockup-first)

- [x] Layers/Filters: inspect current UI entry points and prepare visual mockups
      (iterations V1 → V2 → V2.1; V1/V2 сохранены как history и не являются production specification).
- [x] Owner review/revision/explicit approval of mockup: **V2.1 APPROVED UI DIRECTION** (2026-10-02).
      Утверждённые артефакты: `docs/ui/mockups/unified-territory-layers-v2-1.png` (кадры A–D),
      `docs/ui/mockups/unified-territory-layers-v2-1-calendar.png` (кадры E–H) и
      `docs/ui/mockups/unified-territory-layers-v2-1.md` (семантический контракт направления).
      Owner correction: `Apiary` / `Inspection` не показываются в панели, пока нет доступной
      пользовательской функциональности. D094 §3 и I016 `Temporal filtering` приведены в
      соответствие; D094 остаётся ACCEPTED, I016 — `idea`.
- [x] Convert approved mockup into textual UI specification: **APPROVED · 2026-10-02**.
      `docs/ui/unified-territory-map-display-spec.md` — утверждённая спецификация поведения; owner
      decisions при approval: display state per-Territory и persistent, default второго фильтра
      сознательно отложен. Реализация не начата.

## Temporal data-model design

- [x] Design minimal fixation-date data-model change for Hollow/LogHive/Apiary.
- [x] Resolve ObservationPoint late-entry consequence:
      observation date vs created_at, observation_year, point numbering.
- [x] Design Apiary temporal fields: fixation date separately from establishment date/year;
      exact/year-only/approximate/unknown representation remains open until this step.
- [x] Design future Inspection entity with its own inspection date.

Design APPROVED · 2026-10-03: `docs/temporal-data-model-design.md`. Закрыт design: schema-направление,
migration policy и invariants. За пределами утверждённого minimal scope остаются future concerns —
establishment-date precision, схема Inspection, late-entry/weather design. I1 committed (f4f5105);
I2 committed locally (bd977b3), isolated Room/device verification pending. I3 committed locally (ac6c7d1); I4 implemented in worktree, owner review required. I5–I7 pending.
I2 + I3 + I4 — одна deployment unit; отдельный Android/Room/device gate блокирует Samsung.

## Overlay architecture evidence

- [x] Run a small DEV/device spike for runtime MapLibre overlay lifecycle across `setStyle()`.
- [x] Based on evidence, review §73.3 overlay proposal and decide whether it can become an accepted
      decision.

Closed · 2026-10-08: initial spike + targeted lifecycle closure + review; owner accepted §73.3 /
[D102](decisions.md#d102--map-overlay-rendering-split-и-lifecycle-safety).
[Durable evidence summary](map-overlay-lifecycle-evidence.md). Implementation и её device verification
остаются OPEN. Следующий отдельный production stage — temporal data model по утверждённому
`docs/temporal-data-model-design.md`; текущий I1 slice указан ниже.

## Implementation

- [x] D102 marker semantic pictograms OWNER APPROVED · 2026-10-09:
      wingless existing bee / tree / cut trunk / box / reserved house.
      [Contract and owner image](ui/mockups/d102-marker-pictograms-v1.md).
- [x] Reusable presentation components promoted to the main unified map during I6.
      Temporary DEBUG actual-map marker preview and `DEV: маркеры` were removed by owner decision.
- [x] Samsung S25 Ultra owner approval: all five pictograms and **32 dp production normal size**.
      24 dp is readable but less convenient in the field; 20 dp is not the normal size.
- [x] Remaining Samsung owner visual approval at 32 dp: selected state, contrast on actual
      vector / Sentinel raster / Hybrid backgrounds, nearby readability and operational-symbol
      distinction — OWNER PASS · 2026-10-09.
- [x] Production integration: `SavedObjectMarkersOverlay` on the existing main map draws real
      ObservationPoint/Hollow/LogHive through `ResearchObjectMarker` at 32 dp with the approved
      selected treatment and tip anchoring. Points is table/records only. Trap/Apiary stay reserved.
- [x] Owner production-map verification: real saved points appear in the correct places, types and
      selected state on the production map — OWNER PASS · 2026-10-09. Nearby markers may overlap at low
      zoom (interacting touch targets); the owner accepted this, 32 dp stays unchanged and
      clustering/spiderfy are out of D102 scope ([D102](decisions.md)).
      A type without a real record in the owner's database cannot be verified and must be reported as
      an evidence limitation, not seeded with fake data.

- [ ] Implement required Room/data-model/migration changes.
      I1 committed (f4f5105): Room v12, canonical ObservationPoint date и atomic correction без user caller.
      I2 committed: Room v13 nullable fixationDate, legacy NULL, new Hollow/LogHive dates.
      I3 committed (ac6c7d1): Backup V7 / Snapshot V2 explicit dates, legacy readers retained.
      I4 committed (9a079d7): три Export V2 profiles, legacy V1 readers/guards retained.
      I5 committed (3c2c4c3): `ResearchDateInterval`, ObservationPoint bounded query по `observation_date`,
      независимые Hollow/LogHive bounded queries по `fixation_date`, bounded исключает NULL.
      Room остаётся v13; schema, migration и canonical date columns этим stage не менялись.
      Отдельный deployment/owner-device migration gate по-прежнему не закрыт этим пунктом.
- [x] Implement Layers/Filters from the approved UI spec.
      Реализовано как утверждённая поверхность: третье действие `Данные на карте` в нижней панели
      карты, панель с независимой видимостью и периодом каждого доступного типа, экран фильтров типа
      с accordion `Период` и уровнями год/месяц/день, per-Territory persistent состояние в
      существующем settings DataStore. Owner Samsung UI verification — отдельный следующий шаг.
- [ ] Implement MapLibre runtime overlay registry/restoration where required.
      I6 не вводил runtime MapLibre Source/Layer: research-маркеры остаются Compose-маркерами (§73.3),
      поэтому registry не потребовался. Пункт остаётся открытым для стадий, которым действительно
      нужны MapLibre layers (I014 rasters, I011 geometry, I001 analytical overlays).
- [x] Connect Compose research markers to common layer/date filtering.
      Основная карта показывает research-объекты текущей Territory: ObservationPoint, Hollow и
      LogHive различаются утверждёнными D102-маркерами 32 dp и фильтруются FilterSet своего типа через
      I5 query layer (никакого post-filter в Compose). Trap/Apiary в панели и на карте не появляются.
      Marker tap → selected halo + preview → существующая полная запись реализован для всех трёх типов.
      Открыт обязательный перед rollout общей карты Compose marker scale/performance benchmark
      (`docs/architecture.md` §73.3).

## Separate increments

- [ ] Integrate I014 user raster layers as a separate increment.
- [ ] Integrate I011 GPX/user field geometry as a separate increment.
- [ ] Integrate future Inspection markers when Inspection exists.
- [ ] Integrate I001 analytical layers only after the base layer/filter system is stable.

## Device verification

- [ ] Samsung/device verification: base-map switching, runtime overlay restoration, layer visibility,
      date ranges, marker selection, accessibility, offline operation.
      I6 gate already performed on the owner device (SM-S938B / RFCY90MBYVZ, in-place `install -r`, no
      uninstall/clear, DB/DataStore hashes unchanged, real records only): the panel and type screen,
      independent per-type visibility and periods, per-Territory persistence across a restart, the
      nullable fixation-date wording, the seven-column weekday calendar with 48 dp cells at 360 dp,
      the panel at the owner's system font scale, «Сбросить фильтры» and the state indicator. Owner
      visual acceptance of the surface is still open, as are base-map switching with an active filter,
      offline operation and marker selection on this map.
      Emulator gate: independent per-type periods, visibility, accordion, back priority, panel reopen
      and the store are covered by focused instrumented tests on API29 — `MapDataPanelTest` 31/31,
      `MapResearchObjectsQueryTest` 2/2, `DataStoreMapDataDisplayStoreTest` 5/5,
      `MapDataViewModelSessionTest` 6/6, plus `CleanStartupIntegrationTest`'s display-state route gate
      (which fails with the owner-reported defect reproduced) and the pure
      `InitialSetupLoadingRuleTest` 3/3 in JVM.

### Resolution notes recorded during I6

- The approved seven-column weekday calendar and the mandatory 48 dp touch target cannot both hold at
  the panel's 16 dp text inset on a 360 dp phone. The calendars therefore keep the approved
  nearly-full-bleed insets (4–6 dp) and a 2 dp day gap, so the approved layout still fits at 360 dp
  with 48 dp cells; below that width the grid reflows to fewer columns and drops the weekday header
  rather than shrinking a target (pinned by `MapPeriodGridTest`).
- Owner extension 2026-10-10: marker tap selects the real object (D102 selected treatment) and
  shows a compact preview; «Открыть запись» opens its existing full record by UUID. Back restores
  the map viewport and per-type filters; no separate map-specific domain/detail UI.
- The panel is one settings session: a visibility switch, a period tap, a reset and a type's `Готово`
  all keep it open, and only `Готово`/`✕`/system Back from the type list/swipe/scrim close it. This is
  enforced by driving the real `ModalBottomSheet` in `MapDataPanelTest` (owner sections A–J), because
  the defect that motivated them lived in the session's lifetime rather than in the panel itself.

## Documentation follow-up after implementation

- [x] update `product-requirements.md` (§17.1 «Отображение исследовательских данных на карте»);
- [x] update `user-workflows.md` (§48 «Данные на карте»);
- [ ] update Help if user-visible behaviour changed (`docs/ui/help` never mentions «Данные на карте»);
- [ ] reconcile I016 status (`docs/ideas.md` still says `idea`).

## Rules

- Checklist не переопределяет accepted decisions и authoritative project documents; при расхождении
  действуют соответствующие authoritative sources, перечисленные выше.
- Значимый UI идёт через mockup-first (`docs/ui/Создание интерфейсов.md`); утверждённые артефакты —
  `docs/ui/mockups/<name>-vN.png` вместе с `<name>-vN.md`.
- Каждая крупная стадия — отдельная задача и отдельный commit, если нет конкретной причины
  объединять.
- Не реализовывать будущие стадии только потому, что они ниже в списке.
- Отмечать пункт `[x]` только тогда, когда существует evidence или owner approval, требующиеся
  именно для этого пункта.
- Обновлять этот checklist в том же commit, который завершает стадию, когда это уместно.

## D106 Stage 0 — Overlay Registry (2026-10-12)

- [x] Production runtime registry and one guarded style session in BeeMap, both FIELD/AREA_VIEW.
- [x] Opaque scope/revision, retained ownership journal, collision and unsafe-attachment handling.
- [x] Permanent opt-in native synthetic fixture; research/GPS/Area remain Compose.
- [x] JVM/build/lint and opt-in emulator fixture gates; see `map-overlay-lifecycle-evidence.md` for actual counts.
- [ ] Deliberately delayed SDK request, profile-specific visual/restore budget and field regression verification.
- [ ] Owner-authorized physical Samsung verification. Not performed in this task.
- Stage 1 GPX is not started; no empty GIS controls or new persistent data were added.

### Targeted emulator closure attempt (2026-10-12)

- [x] Test-only constructor compatibility fix: all four MapViewLifecycleControllerTest methods PASS.
- [x] Current JVM 927/927, Debug/Beta/Release and androidTest compilation/assembly, lint PASS.
- [ ] Existing BeeMap regression closure: AreaEditorEntryUiTest ABORTED by native SIGSEGV in
      MapRenderer::render during return/recreation; XML 2 completed, 1 pass, 1 failure, 4 uncompleted.
      Remaining targeted classes NOT RUN. Cause UNKNOWN; no production change or new commit.
- [ ] Four real production profile factories: opt-in tests added and compile; native NOT RUN.
- [ ] Enhanced actual BeeMap double-startup/recreation fixture: compiles; current native NOT RUN.
- SAMSUNG VERIFICATION: NO-GO until the unexplained native crash and remaining gates are resolved.
  Evidence, exact commands, baseline XML and lint clarifications: `map-overlay-lifecycle-evidence.md`.
  No Stage 1 work or physical-device operation performed.

### Bounded crash attribution (2026-10-12)

- [x] Exact pre-Stage0 baseline comparison: one isolated execution per state, matching SIGSEGV /
      RenderThread / MapRenderer::render+151 in both. PREEXISTING_OR_ENVIRONMENTAL_SUPPORTED.
- [ ] Native closure gates remain incomplete; comparison does not count crashes as PASS or waive
      the four production profiles/remaining regression tests. SAMSUNG_REMAINS_NO_GO.
- No production fix supported by comparison; no new commit. See bounded diagnosis evidence.

### Remaining emulator gates attempt (2026-10-12)

- [x] Stage0 DEV/test APK restored; JVM927/927, required compilation/assembly/lint remain PASS.
- [ ] ONLINE production profile: STAGE0_VERIFICATION_FAIL at exact native source-set parity;
      actual adds org.maplibre.annotations. Native load/metadata passed preceding assertions,
      subsequent assertions unverified. Production defect not established; no correction attempted.
- [ ] Remaining profiles/BeeMap/startup/non-blocked AreaEditor methods NOT_RUN_WITH_REASON:
      required stop after JUnit failure. Known AreaEditor blocker excluded, not rerun.
- EMULATOR_VERIFICATION_FAIL / SAMSUNG_VERIFICATION_NO_GO. No new native crash or commit.
  Full evidence/provenance: latest section in `map-overlay-lifecycle-evidence.md`.

### Test-only native parity contract (2026-10-12)

- [x] SDK ownership proven from MapLibre13.5.1 AnnotationManager; exact SDK source/point-layer
      separation preserves exact application parity. No broad namespace exception.
- [x] Seven negative/positive contract tests PASS; JVM927/927 and required build/lint gates PASS.
- [ ] ONLINE native full verification: BLOCKED_BY_PREEXISTING_NATIVE_FAILURE on one run,
      exact recorded baseline family, no JUnit assertion. Individual assertions remain unverified.
- [ ] Other profiles and remaining gates NOT_RUN_WITH_REASON: latest owner Section7 requires
      ONLINE FULL PASS first. No retry/known AreaEditor rerun or production change.
- Emulator closure incomplete / SAMSUNG_VERIFICATION_NO_GO / no new commit. See lifecycle evidence.

### Historical emulator-only closure — replacement owner rule (2026-10-12)

Snapshot before Samsung verification. The two-method NOT VERIFIED / physical NOT PERFORMED
items below are superseded for those two methods by the Samsung closure section; emulator crash
results remain blocked, and broader visual/performance/field obligations remain separate.

- [x] ACTIVE_VECTOR/SENTINEL/HYBRID real-profile gates PASS with corrected strict parity contract.
- [x] MapDataWiring5/5, MapResearchObjectsQuery3/3, MapCenterTarget2/2, CoverageSelection12/12 PASS.
- [x] Five non-blocked AreaEditor methods and enhanced FIELD/startup/disposal/AREA_VIEW fixture PASS.
- [x] Remaining independent gates13/13, tests31/31, no failures/skips/aborts/new native signature.
- [x] Owner-rule classification: EMULATOR_VERIFICATION_PASS_WITH_KNOWN_PREEXISTING_NATIVE_BLOCKER.
- [ ] Frozen ONLINE and known AreaEditor recreation assertions remain NOT VERIFIED; blocked, not PASS.
- [ ] Physical Samsung/visual/performance/field verification. NOT PERFORMED here.
- SAMSUNG_VERIFICATION_GO under updated owner rule; earlier prerequisite/NO-GO entries are historical.
  Known blocker classification unchanged, root cause UNKNOWN. No production fix or Stage1 work.
  See final independent-gates section of lifecycle evidence for exact commands and blocked assertions.

### Samsung closure of emulator-blocked assertions (2026-10-12)

- [x] ONLINE full production-profile method1/1 PASS on SamsungSM-S938B/Android16/API36.
- [x] Known AreaEditor recreation method1/1 PASS, all four recreations and handledRequests==1.
- [x] No native/process crash/assertion failure/skip; DEV/test update-in-place, data directories retained.
- [x] SAMSUNG_STAGE0_BLOCKED_ASSERTIONS_PASS; combined STAGE0_VERIFICATION_PASS within declared scope.
- Emulator status retained separately; known pre-Stage0 emulator native blocker root cause UNKNOWN,
  not fixed and not relabeled PASS. Current physical results supersede earlier physical-NOT-PERFORMED
  entries only for these two methods. No broader physical suite or Stage1 work.
- [ ] Visual readiness/performance/GPS/heading/field ergonomics beyond the two test assertions.
  See Samsung physical closure section of lifecycle evidence for provenance, bounded screenshots
  and exact commands. No general MapLibre stability or performance acceptance is claimed.

## D106 Stage 1A — accepted specification (2026-10-12)

- [x] OWNER accepted Stage 1A with resource-budget and hidden-residency amendments.
      Single normative detail: [Stage 1 GPX specification](d106-stage1-gpx-spec.md).
- [x] Stage 0 prerequisite accepted within declared emulator + Samsung scope;
      known pre-Stage0 emulator blocker stays separate, root cause UNKNOWN.
- [x] Stage 1B parser/profile/security and identity design prepared and OWNER accepted.
- [ ] Separately authorize implementation; no GPX code/migration/UI/build/device action
      is completed by recording the specification.
- [ ] Measure four budget domains using aggregate multi-Territory corpus and relevant
      physical evidence; provisional ceilings are not final capacity guarantees.

## D106 Stage 1B — accepted technical contract (2026-10-13)

- [x] Accepted technical contract: [parser/profile/security and identity contract](d106-stage1b-gpx-parser-identity-contract.md).
- [x] OWNER accepted with LOW-1…LOW-6; unknown decisions.md NO-GO hunk excluded from commit.
- [ ] Separate parser/fixture implementation contract and OWNER authorization.
- No parser/test/fixture/migration/UI/build/device work completed by this design task.

## D106 Stage 1C — ACCEPTED design contract (2026-10-13)

- [x] Repository-grounded [processing implementation design](d106-stage1c-gpx-implementation-contract.md) prepared; all92 fixture families mapped.
- [x] M1–M3 documentation revision prepared: bounded references, explicit attribute checks/normalization; no new input ceiling or runtime PASS.
- [x] Final R1/R3/R4 documentation amendments prepared; compact R5 audit map added; M1–M3 mechanisms preserved.
- [x] Final control review ACCEPT (BLOCKER/HIGH/MEDIUM0/LOW1); R1/R3/R4 CLOSED, M1–M3 regression NONE; OWNER accepted design only on2026-10-13.
- [x] Separate explicit OWNER authorization for Slice1 granted on2026-10-13; bounded XML/security capability slice implemented.
- [x] Slice1 evidence closure: fresh frozen-current FULL25/25 on API29/API37; explicit actual serials and source/build/installed APK correspondence, pre/post source hashes equal. Earlier23+3 revisions remain history in [Stage1C §25](d106-stage1c-gpx-implementation-contract.md#25-slice-1-execution-evidencestatus--2026-10-13). Final independent re-review ACCEPT; OWNER accepted scoped Slice1, committed locally as bf36ddd.
- [x] Slice2 CLOSED within its declared pure-JVM scope (2026-10-14): immutable model, exact decimal/time values, streamed CM1 + independent A01 oracle. Historical targeted37/37 and full offline JVM988/988 PASS, zero skips;22 core row subsets +H05 material only. Subsequent independent implementation review ACCEPT (BLOCKER/HIGH/MEDIUM0/0/0), OWNER acceptance, then exactly one authorized local commit: `ae291906b186e2c8fdda94e167af7f07e4dac620` (parent `bf36ddd481ca6a04d572635699095417f991173e`, subject `feat(gpx): implement D106 Stage 1 Slice 2`), exactly9 reviewed paths; build/evidence excluded. Push NOT PERFORMED. [Chronology/evidence and current status](d106-stage1c-gpx-implementation-contract.md#26-slice-2-executionstatus--2026-10-14).
- Slice1 remains ACCEPTED/QUALIFIED and CLOSED, previously committed locally as bf36ddd. Slice2 closure does not qualify XML extraction, XML→canonical/provider parity, XML→CM1 or XML enforcement of value bounds, full H05 persistence/restore/protection/logical IDs, or near-ceiling canonical-memory/resource/capacity behavior. Those were future gates at Slice2 closure; Slice3 has since been separately authorized and attempted as recorded below. At that historical Slice2 point Slice4/5 remained unauthorized; current S4 authorization/result is recorded below. No staging/commit/push authorization.

- [x] Slice3 QUALIFIED WITHIN DECLARED PROFILE-PARSER SCOPE (ATTEMPT2,2026-10-14); independent DeepSeek READ-ONLY review PASS CONFIRMED (0 BLOCKER/HIGH/MEDIUM,1 LOW stale-status finding addressed here). OWNER-authorized B08-only ...001→...049 correction preserved the long-leading-zero reference (U+0001→U+0031, latitude1). New frozen83-file digest `96F479A3A0C86EAEC35C226BD918DFD857482FF3E2BAB0F0E6731F0ED49A3488`,83/83 pre/post MATCH. Fresh JVM19/19 +full1007/116 classes,0 failures/errors/skips; build PASS; sequential API29 and API37 each37/37 methods and52/52 mapped rows (38 accepted,14 rejected), post-B08 rows executed, B01/B08 CM1 equality PASS, identical APK pair/source/test set. [Current status/evidence](d106-stage1c-gpx-implementation-contract.md#28-slice-3-attempt-2-pass--current-qualification-status-2026-10-14). Subsequently OWNER accepted Slice3 and locally committed it as `fd8e4dbf004e0def176bbe2b53efae805bf492c1` (parent db03b897..., subject `feat(gpx): implement D106 Stage 1 Slice 3`). Push NOT PERFORMED. Pre-commit evidence remains historical; current S4 qualification follows below. Stage1 and later persistence/restore/protection/presentation gates remain open.
- Historical ATTEMPT1: FAIL / NOT QUALIFIED; new B08 reference encoded forbidden U+0001, correctly rejected as MALFORMED_XML:REFERENCE_SCALAR. API29 ran37 methods,35 pass/2 fail; API37 NOT RUN after STOP. Historical frozen digest `B289A88DFC79C108DE454493C599FEEEE8E785A181FF01742E83804F9BDD9FDC`; [unchanged attempt evidence/history](d106-stage1c-gpx-implementation-contract.md#27-slice-3-executionstatus--2026-10-14). ATTEMPT2 subsequently superseded this operational status; the independent reviewer inspected artifacts only and reran no tests/devices.


- [x] Historical pre-commit Slice4 record: QUALIFIED WITHIN DECLARED BOUNDARY/RESOURCE SCOPE (2026-10-14), awaiting independent review, not OWNER acceptance. Production unchanged; deterministic JVM/Android probes prove G01–G10 triplets, B05 normalized event/CM1 oracle, F03 test-owned canary/F09 no-I/O observations, near100000 canonical memory and cancellation/late EOF/close. Frozen106 inputs pre/post MATCH (88C41139C66A1BD15DFB4BA0A3507D66177C36CB800C54407DB50EEC22080709); targeted11/11, full1018/118 classes with0 failures/errors/skips; build PASS; sequential API29/API37 each48/48 methods (37 regression+11 S4),44 generated S4 executions each, same APK pair, both stopped. [Slice4 record](d106-stage1c-gpx-implementation-contract.md#29-slice-4-executionstatus--2026-10-14). Unstaged/uncommitted; no push; no physical-device use. Exact peak memory UNKNOWN, no new numerical RAM/time/capacity guarantee.
- Slice4 was subsequently OWNER-accepted and locally committed as `67a55452599a57d44d884d5c3dee57df7bfd5313` (`test(gpx): qualify D106 Stage 1 Slice 4`); push NOT PERFORMED. Its pre-commit evidence above remains historical.
- [x] Historical pre-commit closed Slice1 cancellation corrective QUALIFIED / READY FOR INDEPENDENT REVIEW (2026-10-14), not OWNER acceptance: S5 preflight discovered cancellation masked by source-close IOException; separate corrective cycle preserves the SAME cancellation with bounded suppressed close failure in both passes. Standalone close and non-cancellation precedence unchanged. Frozen corrective4/4 and combined108/108 pre/post MATCH (digests `2818045720D6394EED61F2E6C3571582BBEF8C4C24602E66F669E6C66B43E6F3` / `365C0281EEA2228F72242B79DB5E75D09766B06A4C203C84BC9845ABF29EA18C`). Targeted GPX JVM97/97 including6 corrective, full1024/119 classes with0 failures/errors/skips; all7 build/lint gates PASS; sequential API29/API37 each52/52 (48 regression+4 corrective), identical APKs/source, both stopped, no physical device. Historical S1–S4 evidence preserved; old S4 inventory has2 intentional production differences and104 unchanged inputs. [Corrective record](d106-stage1c-gpx-implementation-contract.md#30-closed-slice1-cancellation-corrective--executionstatus-2026-10-14). Unstaged/uncommitted; no push.
- [x] Slice5 CLOSED / OWNER-ACCEPTED; QUALIFIED WITHIN DECLARED PROCESSOR-HANDOFF SCOPE (2026-10-15). Independent DeepSeek review ACCEPT (0 BLOCKER/HIGH/MEDIUM/LOW); four INFO accepted as non-blocking. OWNER accepted Slice5 and locally committed it as `cafb12d4be0fa250898be633b6c309a21fd5e642` (`feat(gpx): implement D106 Stage 1 Slice 5`). Accepted corrective commit `e5cabea2823aadd24ba5c76fe02ab5887fb83cf0` follows accepted Slice4 commit `67a55452599a57d44d884d5c3dee57df7bfd5313`. Internal typed result/processor compose the unchanged closed chain; exact original identity, canonical/CM1 handoff, warnings, source-change, same cancellation plus suppressed close failure and no partial Accepted proven. Targeted16/16 (10 S5+6 corrective), full1034/120 classes with0 failures/errors/skips; all7 build/lint gates PASS. API29/API37 each64/64 =52 closed (48 pre-corrective+4 corrective)+12 S5, identical frozen APKs/methods, both stopped, no physical device. Closed108 unchanged (`365C0281...29EA18C`); new S5 superset112/112 MATCH (`65585099B560BE247E5E32B7F604E911D6D16648E61EEE8FB9DB1D8CFD359005`). New processor two-pass measurement and future-owner integration contract recorded in [Stage1C §31](d106-stage1c-gpx-implementation-contract.md#31-slice5-processor-handoff--executionstatus-2026-10-15); historical S5 preflight preserved. Section31 remains the historical pre-commit qualification record. Push NOT PERFORMED.
- [ ] Future-owner gates remain OPEN: actual coordinator/captured ownership request, persistent logical IDs/Room/managed original, duplicate/reimport confirmation, publication/protection, Complete Backup/Snapshot, full G11–G21 and H01–H07 integration/capacity including H05 restore WITHOUT GPX reparsing, presentation/UI/MapLibre/Registry. No future-owner implementation started. Stage1 as a whole is NOT complete.

- [x] [Stage1D Persistence Implementation Contract](d106-stage1d-gpx-persistence-contract.md): OWNER-ACCEPTED after DeepSeek ACCEPT WITH CONDITIONS, C1–C3 incorporation and explicit OWNER agreement; D1/D2 recorded in decisions.md. Contract acceptance only; persistence implementation, recovery and protection/restore gates remain open. Stage1 remains OPEN.

- [x] Stage1D Implementation Slice1 — Persistence Foundation OWNER-ACCEPTED / PASS. Independent DeepSeek review: 0 BLOCKER/HIGH/MEDIUM, 6 non-blocking INFO; no INFO remediation. Room15/five GPX tables, explicit14→15 migration, shared structural triggers/fail-closed gate, exact canonical mapping, PENDING transactions and deletion guards. Frozen564 inputs pre/post MATCH (`0DB264A569618C6B4D364568D972C4D710D8251FB83A81D5F88AA3413D7AFECF`); closed GPX112 unchanged. Targeted9/9, full1043/121 classes,0 failures/errors/skips; all7 build/lint gates PASS; sequential API29/API37 each151/151 (64 GPX+74 Room/repository+13 new methods), same APKs/methods, both stopped, no physical-device commands. Evidence: `build/evidence/d106-stage1d-slice1/attempt-1/`. Baseline HEAD `f753f9ef339f94fffe2f58d74689fd400d0c2f73`; OWNER authorized one local acceptance commit, no push. No user import/READY activation; coordinator, managed originals, recovery, admission, Backup/Snapshot/Restore and UI remain future stages. Stage1 and H05 remain OPEN.

- Historical initial attempt: [Managed Original Store implementation](D106_Managed_Original_Store_implementation_contract_2026-10-16.md) — historical initial BLOCKED / NOT QUALIFIED (2026-10-16). Baseline `a31ce3abc24c7f16b79348e6590d0c4d44d93a34`; implementation/tests remain unstaged, no commit/push. Targeted JVM6/6, full1049/122 classes and all7 build/lint gates PASS. API29 accepted regression155/155 PASS in ATTEMPT1; new Store qualification failed. After correcting the new JUnit method return type, focused ATTEMPT2 API29/API37 each13/21 PASS,8 FAIL with identical APKs/methods: `Os.link` returns EACCES and SELinux denies `{ link }`. Required hard-link publication/inode-proof cannot qualify; no unsafe fallback. Frozen diagnostic571/571 pre/post MATCH; closed GPX112 unchanged. Evidence: `build/evidence/d106-managed-original-store/` (ignored). OWNER must authorize a minimal publication-contract correction before continuation. No process-kill/publication-cost/real-ENOSPC PASS claim; Stage1 and H05 remain OPEN. Room, Coordinator, READY activation, Android backup rules and Backup/Restore unchanged.

- Historical ATTEMPTS3-5: Managed Original Store exclusive-publication continuation — BLOCKED / NOT QUALIFIED. OWNER authorized O_CREAT|O_EXCL correction; deterministic O directory, bound actual-file proof/witness and completion marker replace hard links, with no overwrite fallback. Targeted JVM6/6, full1049/122 classes and all7 build/lint gates PASS. ATTEMPT3 API29 body181/181 PASS, but real crash probe exposed a one-block fixture; corrected before a new freeze. ATTEMPT4 and unchanged ATTEMPT5 API29 each173/181 PASS,8 ART OOM failures (16MiB growth limit; cause UNKNOWN). ATTEMPT5 API37 terminated after6/181 methods with startup ANR/Process crashed. Required endpoint/crash gates remain OPEN; no commit/push. Frozen572/572 MATCH (`1740C1111B3CB531015AF454DACE4F25C0771E9C8A29DD78922A51613318285F`); closed GPX112 unchanged; same frozen APK pair. Ignored evidence preserves all attempts. Next: diagnose endpoint failures, then rerun qualification without weakening tests. Stage1/H05 OPEN; Coordinator, READY, Room, media and backup rules unchanged. Physical device untouched; real ENOSPC NOT TESTED, power-loss UNKNOWN.

- [x] Managed Original Store OWNER-ACCEPTED / PASS within agreed file scope (2026-10-16). Targeted diagnosis localized API29 old16MiB heap to zygote starting before emulator heapsize-property delivery; effective controlled runtime512MiB, eight formerly failing methods8/8 PASS together. API37 prior startup ANR exact cause UNKNOWN (empty retained trace); ordinary launch and isolated tests PASS after boot settling. ATTEMPT6 same frozen APKs/method set: API29 181/181 PASS, API37 181/181 PASS, each plus separate FINAL_COPY and DIRECTORY_SYNC process-stop/recovery PASS. No source/test or AVD configuration changes. Frozen572/572 and diagnostic qualification573/573 pre/post MATCH; closed GPX112/112 unchanged. Historical failed attempts preserved. Prior JVM1049/1049 and7 build/lint PASS reused for unchanged inputs, not rerun. Ignored evidence: `build/evidence/d106-managed-original-store/diagnosis-1/` and `attempt-6/`. No push; physical device untouched. Real ENOSPC NOT TESTED; power-loss UNKNOWN; Stage1/H05 OPEN. OWNER explicitly accepted the qualified Slice and these non-blocking limits; one local implementation/contract/status commit authorized, no push. No Coordinator/READY/Room/media/backup changes.


- [x] Coordinator / Journal / Recovery QUALIFIED within OWNER-selected FILE_PUBLISHED boundary (2026-10-16), baseline `bd28079fca38154cc74a280d480cbf38d6c38ce0`. Complete graph remains PENDING/invisible; Prepared is not successful import. Checked journal transitions, stable IDs, cancellation/ABORT compensation, retry and startup reconciliation; no provider reopen/new IDs on recovery. Targeted JVM2/2, full1051/123 classes,0 failures/errors/skips; all7 build/lint gates PASS. Same frozen APK pair and exact202-method set on sequential API29/API37: each202/202 PASS (181 existing+21 new), plus7/7 actual process-stop/recovery probes per API. Frozen579/579 MATCH, digest `E691434EE72D5962D9271FD3668FDEBFDC013FA26848EE5EDB30188EE003C760`; closed GPX112/112 unchanged. Evidence: ignored `build/evidence/d106-coordinator/attempt-1/`. No Room schema/parser/Original Store/media/backup/UI changes; no user-import or startup wiring. Real ENOSPC NOT TESTED; power-loss UNKNOWN; physical device NOT RUN; predecessor API37 ANR exact cause UNKNOWN. One local implementation commit authorized after qualification, no push. Stage1/H05 OPEN. READY activation, G11/G12 admission, completed-request correlation, protection/restore and user UI/Registry integration remain future gates; no automatic continuation.


- [x] Internal READY Transition / Admission / Request Correlation QUALIFIED (2026-10-17), baseline `b14552762363f5adbff522a2b2888b2221571ebb`; implementation OWNER acceptance remains pending. Captured-Territory G11 READY+candidate<=1024 and G12 actual READY samples+candidate<=200000, no other PENDING reservations; current-DB duplicate SHA/length confirmation, stable IDs, atomic READY+correlation+journal, durable lookup before compensation and READY-preserving cleanup. Non-repeat recovery may complete; unconfirmed repeat recovery rejects/compensates. OWNER rules are in decisions.md.
  ATTEMPT3: targeted policy JVM4/4; full1055/124classes,0 failures/errors/skips; all7 build/lint PASS. Sequential API29/API37 each228/228 PASS (202 regression+24 READY+2 crash), identical APK pair and exact method set; each plus5/5 actual force-stop/recovery probes before READY, inside both transaction update boundaries, after commit and after staging removal before journal cleanup. Frozen587/587 MATCH, digest `101775DDC403C452EDD8A9E6E10AE7AA036EAD4F59785AC92C9A31A3FB82BD12`; closed GPX112/112 unchanged. Ignored evidence `build/evidence/d106-ready/attempt-3/` preserves commands, installed APK hashes, individual outcomes and prior attempts. No schema/parser/Original Store/media/backup/UI changes, no runtime import/startup wiring. Real ENOSPC NOT TESTED; power-loss UNKNOWN; physical NOT RUN; historical API37 ANR exact cause UNKNOWN. One qualified local implementation commit authorized; no push.
- [ ] Stage1/H05 remain OPEN: separate implementation OWNER review; runtime reconciliation/READY access integration, saved captured-request and duplicate-confirmation user workflow, visibility/UI/Registry, protection/Complete Backup/Repository Snapshot/Restore including full H05, remaining aggregate/capacity and Stage1 exit gates. This internal READY result does not activate user import or close full H/G integration.


- [x] OWNER accepted preceding READY Slice as PASS within scope, commit `081a41cad7bcc36dc4734664c0df5d15932f287b` (2026-10-17). This supersedes the historical pending-review status without rewriting qualification evidence.
- [x] Runtime Reconciliation / READY Access QUALIFIED (2026-10-17), baseline `081a41ca`. Application.onCreate starts existing Coordinator recovery on application-owned IO. Startup/first reads serialize and gate access, failed passes retry safely; committed READY and originals are preserved. Internal `AppContainer.gpxRuntime.readReady(territoryId, importId)` reuses the existing graph and excludes PENDING/other Territory; no map visibility or live-import route. Future import entry must honor this gate and Coordinator serialization.
  Targeted JVM3/3; full1058/125classes,0 failures/errors/skips; all7 build/lint PASS. Sequential API29/API37 each239/239 PASS (228 existing+9 runtime+2 runtime crash methods), identical APKs/exact method set, each plus5/5 actual process-stop/recovery probes through runtime. Frozen593/593 MATCH, digest `2CEE06C64C5B05C079BA38AD462271D51856A7A820145BBCE26F693F9F40459F`; closed GPX112/112 unchanged. Ignored evidence: `build/evidence/d106-runtime/attempt-2/`; incomplete ATTEMPT1 preserved. No schema/parser/Coordinator/Original Store/media/backup/UI changes. Real ENOSPC NOT TESTED; power-loss UNKNOWN; physical NOT RUN; historical API37 ANR exact cause UNKNOWN. Local implementation commit authorized, no push.
- [ ] Current remaining boundary: runtime implementation OWNER review; Stage1/H05 OPEN. Captured-request/duplicate-confirmation user workflow and activation gates, visibility/UI/Registry, protection/Complete Backup/Repository Snapshot/Restore including H05, remaining aggregate/capacity and Stage1 exit gates remain open. No automatic continuation.


- [x] OWNER accepted Runtime Reconciliation / READY Access as PASS within scope, commit `9f2b0605461dff110ea9933f8bfc9052fe92ba4b` (2026-10-17); preceding pending-review entry is historical.
- [x] Captured Request / Internal Import Workflow QUALIFIED (2026-10-17), baseline `9f2b0605`. AppContainer exposes capture/execute/lookup with immutable captured Territory/Observer/R, single-use request authorization, durable READY retry and explicit duplicate confirmation. Runtime excludes concurrent reconciliation; shared transient settings guard invalidates A-B-A and orders selection writes with READY. Post-Prepared cancellation/settings failure delegates safe ABORT to Coordinator; committed READY always wins. No new state machine, IDs on retry, URI persistence or Room fields.
  Targeted JVM3/3; full1061/126 classes,0 failures/errors/skips; all7 build/lint PASS. Sequential API29/API37 each264/264 PASS (239 prior+6 existing settings+19 new methods), same APK pair/exact method set, plus5/5 actual host process-stop/recovery probes each through workflow lookup. Frozen601/601 MATCH, digest `C8DB9F7F551F67069E5BB358332503D42A16520A5BE562C93AFFE666CC60F2E9`; closed GPX112/112 unchanged. Ignored evidence `build/evidence/d106-workflow/attempt-2/`; failed ATTEMPT1 preserved. Internal workflow only: userImportAvailable=false; no picker/UI/map/Registry, protection/Restore/Backup/Auto Backup, parser/Store/Coordinator/admission/schema changes. Real ENOSPC NOT TESTED; power-loss UNKNOWN; physical NOT RUN; historical API37 ANR exact cause UNKNOWN. Local commit authorized, no push.
- [ ] Current remaining boundary: workflow implementation OWNER review; Stage1/H05 OPEN. Actual user request/picker/permission lifecycle, approved UI/visibility/Registry, activation/protection/Complete Backup/Repository Snapshot/Restore including H05 and remaining aggregate/capacity/Stage1 exit gates remain open. No automatic next Slice or user activation.
