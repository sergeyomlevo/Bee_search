# Unified Territory Data Map — execution checklist

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
I2 implemented in worktree, owner review pending. I3–I7 не начаты; I2 NOT DEPLOYABLE until I3 + I4 carriage.

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

- [ ] Implement required Room/data-model/migration changes.
      I1 committed (f4f5105): Room v12, canonical ObservationPoint date и atomic correction без user caller.
      I2 implemented in worktree: Room v13 nullable fixationDate, legacy NULL, new Hollow/LogHive dates,
      legacy writer fail-closed guards. Owner review и isolated Android verification остаются pending.
      **NOT DEPLOYABLE until I3 + I4 carriage**; общий temporal stage не закрыт. I3 + I4 также обязательны
      до I6 или любого user-reachable explicit/corrected research-date path.
- [ ] Implement Layers/Filters from the approved UI spec.
- [ ] Implement MapLibre runtime overlay registry/restoration where required.
- [ ] Connect Compose research markers to common layer/date filtering.

## Separate increments

- [ ] Integrate I014 user raster layers as a separate increment.
- [ ] Integrate I011 GPX/user field geometry as a separate increment.
- [ ] Integrate future Inspection markers when Inspection exists.
- [ ] Integrate I001 analytical layers only after the base layer/filter system is stable.

## Device verification

- [ ] Samsung/device verification: base-map switching, runtime overlay restoration, layer visibility,
      date ranges, marker selection, accessibility, offline operation.

## Documentation follow-up after implementation

- [ ] update `product-requirements.md`;
- [ ] update `user-workflows.md`;
- [ ] update Help if user-visible behaviour changed;
- [ ] reconcile I016 status.

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
