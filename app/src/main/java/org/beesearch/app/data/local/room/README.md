# Навигация по Room persistence

GPX persistence foundation живёт в общей Room database. На этой ветке schema
version — DB15; миграция `14 → 15` добавляет GPX persistence tables. Этот индекс
описывает точки входа и не объявляет READY writer/coordinator.

| Путь | Назначение |
|---|---|
| [GpxEntities.kt](GpxEntities.kt) | Пять GPX entity-моделей: imports, source tracks, segments, samples и import jobs. |
| [GpxDao.kt](GpxDao.kt) | GPX queries, graph rows, READY/PENDING visibility и derived counts. |
| [GpxPersistenceSchema.kt](GpxPersistenceSchema.kt) | Таблицы, индексы, structural triggers и schema verification. |
| [GpxPersistenceStore.kt](GpxPersistenceStore.kt) | Ограниченный Room store для graph persistence/read/count/PENDING cleanup. |
| [GpxStoredGraph.kt](GpxStoredGraph.kt) | Stored graph mapping и integrity checks канонического графа. |
| [BeeSearchDatabase.kt](BeeSearchDatabase.kt) | Общая Room database, DB15 и подключение GPX DAO/schema callback. |
| [Migrations.kt](Migrations.kt) | Explicit `MIGRATION_14_15`, делегирующая создание GPX schema. |
| [Room GPX tests](../../../../../../../../test/java/org/beesearch/app/data/local/room) | Stored graph, converters и persistence regression tests. |

Нормативные ограничения и staged ownership: [Stage1D](../../../../../../../../../../docs/d106-stage1d-gpx-persistence-contract.md).
Связанные требования: [Stage1A](../../../../../../../../../../docs/d106-stage1-gpx-spec.md),
[Stage1B](../../../../../../../../../../docs/d106-stage1b-gpx-parser-identity-contract.md),
[Stage1C](../../../../../../../../../../docs/d106-stage1c-gpx-implementation-contract.md).

Документация проекта: [README.md](../../../../../../../../../../README.md).
