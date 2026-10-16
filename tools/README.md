# Навигация по `tools`

Репозиторные утилиты и их входные точки. Сгенерированные каталоги `work/`,
`out/`, `build/` и другие результаты запусков в этот индекс не входят.

| Путь | Назначение |
|---|---|
| [analysis-evidence-explorer](analysis-evidence-explorer/README.md) | Read-only разбор и валидация COMPLETE_BACKUP. Вход: `explorer.py`. |
| [beta-release](beta-release/README.md) | Проверка и публикация Beta-артефакта. Вход: `beta_release.py`. |
| [luna-delegation-audit](luna-delegation-audit/README.md) | Наблюдение hook-событий делегации. Входы: `run-fixtures.js`, `report-audit.js`. |
| [map-poc](map-poc/README.md) | Воспроизводимый pipeline векторного map PoC. Входы: `prepare-map.ps1`, `build-map-package.ps1`. |
| [pc/snapshot-verifier](pc/snapshot-verifier/README.md) | Независимая проверка Snapshot V1–V3. Входы: `verify-snapshot.ps1`, `verify-repository.ps1`. |
| [personal-core-audit-pilot](personal-core-audit-pilot/README.md) | Audit-only наблюдение tool lifecycle и покрытия comparator. Входы: `run-*-fixtures.js`, `report-audit.js`. |
| [repository-binding-audit](repository-binding-audit/README.md) | Изолированный Android audit binding/reinstall state. Вход: Gradle project этого каталога. |

Дополнительно: [temporal-query-measurement/measure.py](temporal-query-measurement/measure.py) — host-side SQLite measurements;
[dev-bootstrap.ps1](dev-bootstrap.ps1) — подготовка DEV fixtures.

Документация проекта: [README.md](../README.md).
