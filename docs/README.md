# Документация Bee Search

[Наверх — карта проекта](../README.md).
Выбирайте область; связанные документы читаются по необходимости, а не все подряд.

## Основные источники

| Документ | Содержание |
|---|---|
| [product-requirements.md](product-requirements.md) | Требования и границы продукта. |
| [user-workflows.md](user-workflows.md) | Пользовательские последовательности действий. |
| [domain-model.md](domain-model.md) | Сущности, ownership и доменные инварианты. |
| [data-model.md](data-model.md) | Постоянные данные, связи и ограничения. |
| [architecture.md](architecture.md) | Компоненты и технические границы. |
| [decisions.md](decisions.md) | Решения с их действующими статусами. |
| [glossary.md](glossary.md) | Терминология. |
| [ideas.md](ideas.md) | Идеи; сами по себе не требования. |
| [workspace-layout.md](workspace-layout.md) | Внешние вспомогательные каталоги и правило их размещения. |

## D106 / GPX / единая карта

| Документ | Содержание |
|---|---|
| [Checklist](unified-territory-map-checklist.md) | Основной источник состояния D106 и открытых gates. |
| [Stage1A](d106-stage1-gpx-spec.md) | Спецификация GPX и интеграционные обязательства. |
| [Stage1B](d106-stage1b-gpx-parser-identity-contract.md) | Parser, identities и семейства проверок. |
| [Stage1C](d106-stage1c-gpx-implementation-contract.md) | Контракт реализации и исторические execution sections. |
| [Stage1D](d106-stage1d-gpx-persistence-contract.md) | Persistence Foundation contract. |
| [Overlay lifecycle evidence](map-overlay-lifecycle-evidence.md) | История проверок map overlays. |
| [Map display spec](ui/unified-territory-map-display-spec.md) | Семантика отображения единой карты. |
| [GPX display mockup v1 — APPROVED](ui/mockups/gpx-display-v1.md) | Список READY GPX, выбор отображения и targeted Registry integration check; APPROVED BY OWNER, 2026-10-19. |
| [GPX display implementation](d106-gpx-display-implementation-report.md) | Реализация списка READY, сохранения выбора и отображения через существующий Registry; проверки и ограничения. |
| [My points C qualification](d106-my-points-stage-c-qualification.md) | Список, Room-видимость, маркеры и карточка; проверки и ограничения этапа C. |
| [My points D qualification](d106-my-points-stage-d-qualification.md) | Редактирование, подтверждённое удаление и очистка фотографий; результаты и ограничения этапа D. |
| [Test vectors](test-vectors) | Контрактные тестовые материалы. |

## Backup / Snapshot / экспорт

| Документ | Содержание |
|---|---|
| [Backup formats](backup-format-v1.md) | Версионированные logical backup formats; версия в имени файла не определяет текущую версию. |
| [Backup operation design](backup-operation-design.md) | Жизненный цикл backup operations. |
| [Repository foundation](repository-v1-foundation.md) | Основа repository. |
| [Repository Snapshot](repository-snapshot-v1.md) | Контракт Snapshot и его развитие. |
| [Wire V1](snapshot-v1-wire-schema.md), [Wire V2/V3](snapshot-v2-wire-schema.md) | Wire schema snapshots. |
| [2B preflight](snapshot-2b-contract-preflight.md), [reader alignment](snapshot-v1-reader-alignment-status.md) | Контрактная сверка и состояние alignment. |
| [Slice2B report](repository-snapshot-v1-slice2b-report.md), [final acceptance](repository-snapshot-v1-final-acceptance.md) | Исторические отчёты qualification/acceptance. |
| [Physical-object export](physical-object-export-v1.md) | Экспорт physical objects. |

## Temporal data / UI / дальнейшие направления

| Документ | Содержание |
|---|---|
| [Temporal design](temporal-data-model-design.md) | Временная модель данных. |
| [Temporal export](temporal-export-v2.md) | Контракт temporal export. |
| [I5 measurements](temporal-i5-query-measurement.md) | Измерения temporal queries. |
| [I6 corrections](temporal-i6-owner-corrections-report.md), [visual corrections](temporal-i6-owner-visual-corrections-2.md) | Исторические owner corrections. |
| [UI workflow](ui/Создание%20интерфейсов.md) | Правила подготовки и принятия интерфейсов. |
| [Mockups](ui/mockups) | Изображения и семантические контракты макетов. |
| [Help](ui/help/README.md) | Справочные UI материалы. |
| [Server-sync architecture](server-sync-architecture.md) | Документ отдельного направления; действующий scope сверять с решениями. |

Этот каталог не вводит новые статусы. При конфликте следуйте источникам и
порядку приоритетов из [AGENTS.md](../AGENTS.md), а не краткому описанию здесь.

GPX follow-up2026-10-19: [GPX1.0 qualification](d106-gpx10-report.md); [deletion audit/plan](d106-gpx-deletion-plan.md), [deletion PNG+MD draft](ui/mockups/gpx-display-delete-v1.md).

[GPX Exchange folder and Samsung deployment](d106-gpx-picker-folder-report.md) — OWNER follow-up2026-10-19.
