# Bee Search — навигация по проекту

Начните с нужной смысловой области. Индексы содержат пути и назначение;
правила и статусы остаются в существующих авторитетных документах.
Навигация относится к checkout, в котором открыта: перед работой проверьте Git branch/HEAD/status.

| Что требуется | Куда перейти |
|---|---|
| Требования, архитектура, решения, контракты | [Каталог документации](docs/README.md) |
| Текущая точка продолжения | [Handoff](.agent/handoff.md) |
| Состояние D106 и открытые gates | [Существующий checklist](docs/unified-territory-map-checklist.md) |
| Правила работы агента | [AGENTS.md](AGENTS.md) |
| GPX чтение, безопасность, parser, processor | [Индекс GPX](app/src/main/java/org/beesearch/app/data/gpx/README.md) |
| Room и GPX persistence | [Индекс Room](app/src/main/java/org/beesearch/app/data/local/room/README.md) |
| Утилиты разработки и проверки | [Индекс tools](tools/README.md) |
| Вспомогательные каталоги вне checkout | [Карта рабочего пространства](docs/workspace-layout.md) |

## Основные каталоги

| Путь | Содержание |
|---|---|
| [app/src/main/java/org/beesearch/app](app/src/main/java/org/beesearch/app) | Android/Kotlin: `ui`, `domain`, `data` и wiring приложения. |
| [app/src/main/res](app/src/main/res) | Android resources. |
| [app/src/test](app/src/test) | JVM tests и их resources. |
| [app/src/androidTest](app/src/androidTest) | Android instrumentation tests. |
| [app/schemas](app/schemas) | Exported Room schemas. |
| [docs](docs/README.md) | Нормативные документы, принятые решения и история проверок. |
| [.agent](.agent) | Политики решений, предпочтения и оперативная передача контекста. |
| [.agents](.agents), [.codex](.codex) | Локальные agent skills/configuration. |
| [gradle](gradle), [app/build.gradle.kts](app/build.gradle.kts) | Gradle wrapper/catalog и конфигурация Android module. |

`build/`, `.gradle/`, `.kotlin/`, скачанные карты, caches и результаты запусков
не перечисляются пофайлово. Evidence ищите по ссылке из соответствующего
отчёта или handoff; evidence не заменяет контракт.

## Как поддерживать навигацию

Новая смысловая область получает короткий локальный `README.md`, когда это
облегчает поиск; родительский индекс ссылается на него. Не нужен README для
каждой папки. При переносе или смене назначения обновляйте соответствующую
строку и ссылки. Статусы, числовые лимиты и нормы здесь не копируются.
