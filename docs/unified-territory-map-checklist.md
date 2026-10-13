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
- [ ] Separate explicit OWNER authorization for Slice1; implementation NOT started, XML/security runtime qualification pending.
- Documentation acceptance only; no parser/fixture/code/dependency/build/device work or runtime PASS.
