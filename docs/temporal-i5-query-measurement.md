# Temporal I5 — query contract и измерение индексов

Дата: 2026-10-09. Baseline: `61a6726109421e28b7860b3d7a9de7f02113b4e8`.

Решение: **NO NEW INDEX**. Room остаётся v13; entities, schema exports и migrations не изменены.

## Production paths и контракт

- `PointsViewModel` → `ObservationRepository.observeObservationPointSummaries` → `RoomObservationRepository` → `ObservationPointDao.observeSummaries` / `observeSummariesInDateInterval`. Territory обязателен, year optional; LEFT JOIN bees/flight_cycles, counts, GROUP BY id; `Flow`; ORDER BY `created_at DESC, id`.
- `PhysicalObjectsBrowserRoute` и `PhysicalObjectCollectionExportService` → `PhysicalObjectRepository.listForTerritory` → `RoomPhysicalObjectRepository` → `PhysicalObjectDao.getForTerritory`. All-time SQL сохраняет ORDER BY `object_type, sequence_number, id`.
- При независимых Hollow/LogHive intervals репозиторий выполняет `getForTerritoryByType` или `getForTerritoryByTypeInDateInterval` для соответствующего типа, затем прежнюю subtype/media hydration в transaction. Внутри типа ORDER BY `sequence_number, id`. Apiary остаётся unbounded; нового temporal capability для него нет. Общая карта данных/period UI ещё не подключены.
- `ResearchDateInterval(LocalDate, LocalDate)` валидируется существующим canonical parser; reversed bounds → `IllegalArgumentException`; равные границы допустимы. Null interval → all time. В SQL используются включительные `>=` / `<=` по `observation_date` или `fixation_date`, без Instant/timezone/created_at fallback. NULL fixation исключается только из bounded типа. Kotlin-фильтрации дат нет.

## Методика и ограничения

OBSERVED: host Python 3.11.15, SQLite 3.53.1, Windows-10-10.0.26200-SP0.

Измеряются пять точных @Query, извлечённых из текущего Daos.kt, а не упрощённые SELECT COUNT или копии фильтров в Kotlin. Схема и все существующие индексы создаются из экспортированного Room 13.json в новых temporary DB. Application/user DB не читается. Candidate — отдельная копия synthetic DB, не production migration.

Три synthetic fixtures: 250/2 500/50 000 точек и столько же объектов, 10 Territories; hot Territory владеет 40% (100/1 000/20 000), остальные делят остаток. По 2 Bee/точку, по 3 завершённых FlightCycle/Bee; stress: 100 000 bees, 300 000 cycles. Детерминированные UUID, valid enum values и unique numbering; seed 1309. Даты распределены по 3 653 календарным дням от 2017-01-01; created_at задаётся отдельно и не определяет research date. В hot Territory типы объектов распределены примерно поровну; APIARY dates всегда NULL, для Hollow/LogHive около 20% NULL. Эти synthetic масштабы — assumptions для измерения, не измеренный owner corpus. FK violations=0, observation_year mismatches=0.

Day=2022-06-15, month=June 2022, year=2022, wide=2017-01-01..2026-12-31. Отдельно unbounded year predicate=2022. Некоторые узкие окна small fixture пусты; stress day/month/year имеют реальные результаты. 3 warmups + 15 повторов с полным fetch, median и nearest-rank p95; captured plans до и после ANALYZE; timing после ANALYZE. Baseline/candidate warm-cache runs последовательные; холодный IO, Android/Room object mapping, repository hydration и end-to-end UI latency не измерены. Это host SQL evidence, не Samsung/I7 evidence. План Android SQLite может отличаться.

Write cost: в fresh clones одинаковый transaction вставляет 1 000 валидных точек + 1 000 Hollow identities; 3 warmups + 15 samples, включает commit/FK/index writes, исключает clone time. Это parent-table microbenchmark, не полный create workflow. Storage delta и index build измерены до ANALYZE.

## Schema/index audit

Нет индексов по canonical date. ObservationPoint: territory_id, observer_id, UNIQUE(territory_id, observation_year, observer_id, point_number). PhysicalObject: territory_id, creator_observer_id, UNIQUE(territory_id, object_type, sequence_number). Existing joins используют bees(observation_point_id), flight_cycles(bee_id, sequence_number).

Измеренные кандидаты (только synthetic DB):

```sql
CREATE INDEX i5_observation_territory_date ON observation_points (territory_id, observation_date);
CREATE INDEX i5_physical_territory_type_fixation ON physical_objects (territory_id, object_type, fixation_date);
```

## Timings — baseline → оба candidate indexes

### Hot Territory: 100 rows каждого parent type

| Query | Result rows | Median baseline ms | Median candidate ms | p95 baseline ms | p95 candidate ms |
|---|---:|---:|---:|---:|---:|
| getForTerritory | 100 | 0.158 | 0.157 | 0.185 | 0.204 |
| getForTerritoryByType | 34 | 0.068 | 0.066 | 0.121 | 0.073 |
| getForTerritoryByTypeInDateInterval_day | 0 | 0.024 | 0.020 | 0.029 | 0.022 |
| getForTerritoryByTypeInDateInterval_month | 1 | 0.026 | 0.023 | 0.037 | 0.079 |
| getForTerritoryByTypeInDateInterval_wide | 27 | 0.059 | 0.063 | 0.070 | 0.063 |
| getForTerritoryByTypeInDateInterval_year | 4 | 0.030 | 0.028 | 0.030 | 0.058 |
| observeSummaries | 100 | 0.666 | 0.583 | 0.773 | 0.716 |
| observeSummariesInDateInterval_day | 0 | 0.031 | 0.022 | 0.042 | 0.059 |
| observeSummariesInDateInterval_month | 0 | 0.030 | 0.022 | 0.033 | 0.030 |
| observeSummariesInDateInterval_wide | 100 | 0.682 | 0.704 | 0.819 | 0.983 |
| observeSummariesInDateInterval_year | 10 | 0.094 | 0.084 | 0.114 | 0.123 |
| observeSummaries_year | 10 | 0.093 | 0.090 | 0.201 | 0.101 |

Index build 4.552 ms; storage delta 40,960 bytes. Write median 12.421 → 13.971 ms; p95 12.692 → 15.129 ms.

### Hot Territory: 1000 rows каждого parent type

| Query | Result rows | Median baseline ms | Median candidate ms | p95 baseline ms | p95 candidate ms |
|---|---:|---:|---:|---:|---:|
| getForTerritory | 1000 | 1.522 | 1.525 | 1.809 | 1.585 |
| getForTerritoryByType | 334 | 0.516 | 0.508 | 0.559 | 0.528 |
| getForTerritoryByTypeInDateInterval_day | 0 | 0.070 | 0.020 | 0.075 | 0.022 |
| getForTerritoryByTypeInDateInterval_month | 3 | 0.073 | 0.026 | 0.081 | 0.028 |
| getForTerritoryByTypeInDateInterval_wide | 267 | 0.432 | 0.468 | 0.965 | 0.488 |
| getForTerritoryByTypeInDateInterval_year | 27 | 0.104 | 0.065 | 0.110 | 0.069 |
| observeSummaries | 1000 | 7.170 | 6.513 | 7.802 | 7.007 |
| observeSummariesInDateInterval_day | 1 | 0.108 | 0.030 | 0.288 | 0.033 |
| observeSummariesInDateInterval_month | 8 | 0.150 | 0.074 | 0.165 | 0.079 |
| observeSummariesInDateInterval_wide | 1000 | 7.222 | 8.160 | 7.927 | 8.749 |
| observeSummariesInDateInterval_year | 99 | 0.747 | 0.730 | 0.782 | 1.029 |
| observeSummaries_year | 99 | 0.732 | 0.714 | 0.860 | 0.824 |

Index build 6.675 ms; storage delta 294,912 bytes. Write median 12.355 → 14.443 ms; p95 13.247 → 15.302 ms.

### Hot Territory: 20000 rows каждого parent type

| Query | Result rows | Median baseline ms | Median candidate ms | p95 baseline ms | p95 candidate ms |
|---|---:|---:|---:|---:|---:|
| getForTerritory | 20000 | 38.438 | 38.120 | 40.283 | 39.422 |
| getForTerritoryByType | 6667 | 12.887 | 12.864 | 13.531 | 13.331 |
| getForTerritoryByTypeInDateInterval_day | 1 | 3.122 | 0.023 | 3.343 | 0.032 |
| getForTerritoryByTypeInDateInterval_month | 46 | 3.062 | 0.097 | 3.418 | 0.102 |
| getForTerritoryByTypeInDateInterval_wide | 5331 | 11.376 | 13.108 | 12.538 | 14.511 |
| getForTerritoryByTypeInDateInterval_year | 533 | 3.931 | 0.963 | 4.202 | 1.089 |
| observeSummaries | 20000 | 206.366 | 189.571 | 210.362 | 193.491 |
| observeSummariesInDateInterval_day | 6 | 4.046 | 0.068 | 4.251 | 0.083 |
| observeSummariesInDateInterval_month | 165 | 7.195 | 4.083 | 8.182 | 4.832 |
| observeSummariesInDateInterval_wide | 19994 | 207.444 | 537.743 | 214.208 | 544.696 |
| observeSummariesInDateInterval_year | 2004 | 27.476 | 53.808 | 28.061 | 58.785 |
| observeSummaries_year | 2004 | 26.511 | 26.114 | 31.206 | 27.297 |

Index build 79.815 ms; storage delta 5,754,880 bytes. Write median 14.301 → 16.460 ms; p95 17.232 → 17.386 ms.

## EXPLAIN QUERY PLAN — stress, после ANALYZE

Exact node IDs/cost estimates зависят от SQLite version; ниже сохранены raw планы текущего запуска. Pre-ANALYZE планы и raw timing samples находятся в ignored JSON reports. SQL boundaries остаются inclusive, даже если EQP описывает range условными >/<.

### observeSummaries

Baseline:

```text
12 | 0 | 145 | SEARCH p USING INDEX index_observation_points_territory_id (territory_id=?)
22 | 0 | 47 | SEARCH b USING INDEX index_bees_observation_point_id (observation_point_id=?) LEFT-JOIN
29 | 0 | 51 | SEARCH c USING INDEX index_flight_cycles_bee_id_sequence_number (bee_id=?) LEFT-JOIN
37 | 0 | 0 | USE TEMP B-TREE FOR GROUP BY
126 | 0 | 0 | USE TEMP B-TREE FOR count(DISTINCT)
129 | 0 | 0 | USE TEMP B-TREE FOR ORDER BY
```

Candidate:

```text
12 | 0 | 145 | SEARCH p USING INDEX index_observation_points_territory_id (territory_id=?)
22 | 0 | 47 | SEARCH b USING INDEX index_bees_observation_point_id (observation_point_id=?) LEFT-JOIN
29 | 0 | 51 | SEARCH c USING INDEX index_flight_cycles_bee_id_sequence_number (bee_id=?) LEFT-JOIN
37 | 0 | 0 | USE TEMP B-TREE FOR GROUP BY
126 | 0 | 0 | USE TEMP B-TREE FOR count(DISTINCT)
129 | 0 | 0 | USE TEMP B-TREE FOR ORDER BY
```

### observeSummariesInDateInterval_month

Baseline:

```text
12 | 0 | 145 | SEARCH p USING INDEX index_observation_points_territory_id (territory_id=?)
26 | 0 | 47 | SEARCH b USING INDEX index_bees_observation_point_id (observation_point_id=?) LEFT-JOIN
33 | 0 | 51 | SEARCH c USING INDEX index_flight_cycles_bee_id_sequence_number (bee_id=?) LEFT-JOIN
41 | 0 | 0 | USE TEMP B-TREE FOR GROUP BY
130 | 0 | 0 | USE TEMP B-TREE FOR count(DISTINCT)
133 | 0 | 0 | USE TEMP B-TREE FOR ORDER BY
```

Candidate:

```text
12 | 0 | 87 | SEARCH p USING INDEX i5_observation_territory_date (territory_id=? AND observation_date>? AND observation_date<?)
27 | 0 | 47 | SEARCH b USING INDEX index_bees_observation_point_id (observation_point_id=?) LEFT-JOIN
34 | 0 | 51 | SEARCH c USING INDEX index_flight_cycles_bee_id_sequence_number (bee_id=?) LEFT-JOIN
42 | 0 | 0 | USE TEMP B-TREE FOR GROUP BY
131 | 0 | 0 | USE TEMP B-TREE FOR count(DISTINCT)
134 | 0 | 0 | USE TEMP B-TREE FOR ORDER BY
```

### getForTerritory

Baseline:

```text
4 | 0 | 147 | SEARCH physical_objects USING INDEX index_physical_objects_territory_id_object_type_sequence_number (territory_id=?)
```

Candidate:

```text
4 | 0 | 147 | SEARCH physical_objects USING INDEX index_physical_objects_territory_id_object_type_sequence_number (territory_id=?)
```

### getForTerritoryByType

Baseline:

```text
4 | 0 | 145 | SEARCH physical_objects USING INDEX index_physical_objects_territory_id_object_type_sequence_number (territory_id=? AND object_type=?)
```

Candidate:

```text
4 | 0 | 145 | SEARCH physical_objects USING INDEX index_physical_objects_territory_id_object_type_sequence_number (territory_id=? AND object_type=?)
```

### getForTerritoryByTypeInDateInterval_month

Baseline:

```text
4 | 0 | 145 | SEARCH physical_objects USING INDEX index_physical_objects_territory_id_object_type_sequence_number (territory_id=? AND object_type=?)
```

Candidate:

```text
4 | 0 | 86 | SEARCH physical_objects USING INDEX i5_physical_territory_type_fixation (territory_id=? AND object_type=? AND fixation_date>? AND fixation_date<?)
32 | 0 | 0 | USE TEMP B-TREE FOR ORDER BY
```

## Ordering diagnostic

| Query | Baseline full / without ORDER BY median ms | Candidate full / without ORDER BY median ms |
|---|---:|---:|
| getForTerritory | 38.438 / 32.821 | 38.120 / 32.111 |
| getForTerritoryByType | 12.887 / 13.117 | 12.864 / 14.941 |
| getForTerritoryByTypeInDateInterval_wide | 11.376 / 11.242 | 13.108 / 11.824 |
| getForTerritoryByTypeInDateInterval_year | 3.931 / 3.880 | 0.963 / 0.863 |
| observeSummaries | 206.366 / 195.150 | 189.571 / 192.026 |
| observeSummariesInDateInterval_wide | 207.444 / 195.143 | 537.743 / 528.184 |
| observeSummariesInDateInterval_year | 27.476 / 26.701 | 53.808 / 53.870 |
| observeSummaries_year | 26.511 / 26.381 | 26.114 / 25.882 |

OBSERVED: candidate сохраняет GROUP BY/count(DISTINCT)/ORDER BY temporary B-trees для point summaries и добавляет ORDER BY sort для физических объектов вместо existing ordering index. Удаление финального ORDER BY не устраняет wide observation regression: причиной не является только этот sort. Точная причинная доля random access, JOIN и GROUP BY не установлена; замеры не являются profiler evidence.

## Решение и критерий пересмотра

DERIVED: NO NEW INDEX для I5. На 1 000 records/Territory date SELECT уже меньше 1 ms для day/month/year; абсолютный выигрыш кандидата — доли миллисекунды. На stress 20 000 records узкий фильтр действительно выигрывает несколько ms, но year/wide observation заметно ухудшаются; full-result cost не устраняется date index. Physical-object gains также не доказывают практическую необходимость отдельной migration: baseline bounded day/month/year несколько ms, wide хуже с candidate, плюс storage/write overhead. Существующие callers остаются unbounded, I6 interaction frequency ещё не известна. Индексы не оправданы только большим относительным ускорением tiny SELECT.

Это не утверждение «индексы никогда не нужны». Пересмотр: после I6 измерить реальные independent period queries и mapper/hydration на representative Android data; при повторяемом query-related latency выше локального бюджета (например 50 ms) и material end-to-end gain без широких regressions рассмотреть минимальный type/date index отдельно. 50 ms — engineering trigger для нового measurement, не owner product SLA. Alternative ordering/covering/partial indexes сейчас не реализованы и не объявлены доказанно бесполезными. Schema ownership нового решения оценивается перед migration.

## Verification и воспроизведение

Fresh independent read-only review полного I5 diff: BLOCKER=0, HIGH=0, MEDIUM=0.
Decomposition reviewed; kept cohesive because: Daos.kt остаётся общей DAO/schema boundary,
RoomPhysicalObjectRepository.kt — transaction/hydration boundary, measurement script —
единый воспроизводимый fixture/query-plan/timing/report pipeline. No source changes after review.

829/829 full JVM PASS (4 новых interval tests); assembleDebug, compileDebugAndroidTestKotlin, assembleDebugAndroidTest PASS. Isolated API29 emulator: 86/86 Room/temporal/repository tests PASS, включая 15 migration tests и 4 новых I5 tests. Final artifact: app/build/reports/temporal-i5/emulator-room-tests-final.txt. Ранний emulator-room-tests.txt с неверным ordering assertion superseded final PASS. Samsung не использовался; I6/I7 не завершались.

```powershell
python tools/temporal-query-measurement/measure.py
python tools/temporal-query-measurement/measure.py --points 2500 --objects 2500 --output app/build/reports/temporal-i5/query-measurement-territory1000.json --markdown app/build/reports/temporal-i5/query-measurement-territory1000.md
python tools/temporal-query-measurement/measure.py --points 250 --objects 250 --output app/build/reports/temporal-i5/query-measurement-territory100.json --markdown app/build/reports/temporal-i5/query-measurement-territory100.md
```

Raw reports остаются ignored в app/build/reports/temporal-i5/. Это durable summary всех финальных timings/plans/решения; устаревшие preliminary числа заменены финальными fixture runs.

Source SHA-256 на момент финального измерения:

- `app\schemas\org.beesearch.app.data.local.room.BeeSearchDatabase\13.json`: `9adc42b056af724abc3f544aa225aa84b1facdd95d2356142aeeee5b14d5e19b`
- `app\src\main\java\org\beesearch\app\data\local\room\Daos.kt`: `be86c01c449fdacffad57d9fff34146aff6172030ccc00e3d1e5732bbe9a6dca`
- `tools\temporal-query-measurement\measure.py`: `83680f4e19215b782c607829ac7141625108d6003ebb1d5fd688f4e5492f5717`

Primary sources до реализации: [Room @Query](https://developer.android.com/reference/androidx/room/Query), [SQLite EXPLAIN QUERY PLAN](https://sqlite.org/eqp.html), [SQLite query planner](https://sqlite.org/queryplanner.html). Room 2.8.4 в проекте проверен KSP/build и реальным исполнением Room SQL; external docs не подменяют project measurements.
