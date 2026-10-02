# Unified Territory Data Map — execution checklist

Операционный checklist работ по I016. Это **не** specification и **не** источник project truth: он не
заменяет и не переопределяет accepted decisions, requirements, domain или data model. Authoritative
остаётся в `docs/decisions.md`, `docs/architecture.md`, `docs/data-model.md`,
`docs/product-requirements.md`, `docs/user-workflows.md` и `docs/ideas.md`; checklist только
ссылается на них и фиксирует порядок этапов.

Продолжение работ по текущему milestone остаётся в `.agent/handoff.md`.

Авторитетные опоры:

- I016 — `docs/ideas.md` (Status: `idea`) — цель и объём Unified Territory Data Map;
- D094 — `docs/decisions.md` — временная семантика и семантика обычного фильтра по периоду;
- `docs/architecture.md` §73.3 — MapLibre/Compose split как design proposal;
- `docs/data-model.md` §22, §63, §71.1 — что из временных фактов записано в модели сейчас;
- I014 — user georeferenced rasters; I011 — GPX и пользовательские полевые данные; I001 —
  аналитические overlays.

## Done

- [x] I016 сформулирована и temporal section reconciled.
- [x] D094 ACCEPTED.
- [x] research date отделена концептуально от technical created_at.
- [x] MapLibre/Compose class-based split записан как design proposal.

## UI: Layers/Filters (mockup-first)

- [ ] Layers/Filters: inspect current UI entry points and prepare first visual mockup.
- [ ] Owner review/revision/explicit approval of mockup.
- [ ] Convert approved mockup into textual UI specification.

## Temporal data-model design

- [ ] Design minimal fixation-date data-model change for Hollow/LogHive/Apiary.
- [ ] Resolve ObservationPoint late-entry consequence:
      observation date vs created_at, observation_year, point numbering.
- [ ] Design Apiary temporal fields: fixation date separately from establishment date/year;
      exact/year-only/approximate/unknown representation remains open until this step.
- [ ] Design future Inspection entity with its own inspection date.

## Overlay architecture evidence

- [ ] Run a small DEV/device spike for runtime MapLibre overlay lifecycle across `setStyle()`.
- [ ] Based on evidence, review §73.3 overlay proposal and decide whether it can become an accepted
      decision.

## Implementation

- [ ] Implement required Room/data-model/migration changes.
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
