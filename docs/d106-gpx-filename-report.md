# D106 — сохранение имени импортированного GPX

2026-10-19. Targeted bug fix. Base `b29af07`,
`task/d106-stage0-overlay-registry`. Stage1/H05 остаются OPEN.

## Причина и цепочка данных

1. `OpenExchangeDocument` использует Android `OpenDocument`; выбранный URI
   поступает в callback `MainMapScreen`.
2. `ContentResolver.query(OpenableColumns.DISPLAY_NAME)` уже получал настоящее
   имя; это согласуется с [Android SAF metadata API](https://developer.android.com/training/secure-file-sharing/retrieve-info).
3. **Первое место потери — `GpxImportViewModel.onFilePicked`:** имя записывалось
   только в UI `fileName`. `GpxCapturedRequest` создавался до picker через
   `capture()` с `displayName=null`, а `execute` получал именно этот запрос.
   Поэтому успешное сообщение показывало имя, а persistence получал null.
4. `GpxImportWorkflow` → `GpxImportCoordinator.prepare` → `pendingGpxGraph`
   → `GpxImportEntity.displayName`/`gpx_imports.display_name` передавали null
   без дополнительной потери. READY-каталог читает сохранённое поле.
5. `GpxDisplayViewModel.names` корректно выбирал fallback по времени импорта;
   `GpxDisplayPage` использовал то же имя строки в подтверждении удаления.
   Room и отображение не были причиной дефекта.

## Исправление

- `execute` принимает имя, полученное после picker, и привязывает его к копии
  того же запроса; UUID, территория, наблюдатель, время, issuer, pending lifecycle
  и revision selection не пересоздаются. Retry готового импорта не переименовывает его.
- ViewModel передаёт настоящее nullable имя отдельно от UI-label `GPX-файл`;
  подтверждённый повторный импорт тоже сохраняет имя.
- SAF helper ищет колонку по имени, проверяет пустые/отсутствующие значения
  и не подставляет непрозрачный URI вместо настоящего имени.
- Имя сохраняется без trim пользовательского значения; blank означает отсутствие.
  Оно не участвует в managed path, UUID, SHA256 или canonical geometry.
- Parser, schema/migrations, delete journal/tombstone, удаление и композиция UI
  не изменены. `<trk><name>` не используется как имя файла.
- Decomposition reviewed; kept cohesive because: `MainMapScreen.kt` (537 lines)
  изменён только в существующем picker callback и связанном SAF metadata helper.
  Новых независимых обязанностей не добавлено.

## Старые импорты

Read-only проверен уже сохранённый snapshot Samsung `after-restart/bee_search.db`
из qualification удаления: 4 импорта, у всех `display_name` отсутствует.
Схема import/journal не сохраняет SAF filename или исходный URI отдельно.
`source_name` — метаданные внутри GPX, не достоверное имя внешнего файла.
Managed original именуется независимо от исходного имени.
Достоверного источника для backfill нет; старые строки не переписываются.
Утверждённый fallback остаётся. Samsung и внешние оригиналы не изменялись.

## Проверки

- `:app:testDebugUnitTest` — 1062 tests, 0 failures/errors (Gradle UP-TO-DATE,
  валидный результат для текущих входов; production fix не менялся после него).
- `:app:assembleDebug :app:assembleDebugAndroidTest --offline` — PASS.
- `:app:lintDebug --offline` — PASS, 0 errors, 36 warnings, 6 hints.
- API29 `emulator-5556`, system fontScale1.7: **69/69 PASS** в финальном
  `am instrument` прогоне. Охват: SAF provider; import ViewModel; Workflow;
  GPX1.0/1.1; nullable имя; Room/readReady/READY catalog; close/reopen DB;
  одинаковые имена с разными UUID; fallback/disambiguation; список/подтверждение;
  удаление и его migration/data-preservation регрессии.
- Ещё **2/2 PASS** в двух отдельных instrumentation-процессах с явным
  `am force-stop org.beesearch.app.dev` между ними: имя, переданное после
  `capture()`, читается после restart из Room и READY-каталога без source URI.
  Использован уникальный `gpx-ready-filename-*` sandbox, удалённый тестом.
- Длинное имя с пробелами и кириллицей: UI при1.7 показывает то же полное
  имя в строке и диалоге; тест подтвердил увеличение scroll offset, скриншоты
  начала/конца текста визуально проверены. Отмена доступна после прокрутки.
- `git diff --check` — PASS; отдельный read-only review Luna — без findings.

Первый connected-прогон НЕ PASS: ошибочное ожидание capture имени до picker,
затем crash тестового provider в отдельном процессе без Kotlin runtime.
Исправлены только новые тесты: ожидание null первого capture, Java provider
с собственным процессом и детерминированным ответом по URI. Финальный69/69
выполнен после пересборки/переустановки test APK. Результаты не смешиваются.

Финальный DEV APK `org.beesearch.app.dev1.3.0(8)`, SHA256
`ecb5151c902c20cf9b49cdc69786beb7f913e698a2037cbf72a3a21f0f4b7e70`.
Установлен только на эмулятор; Samsung не обновлялся. Connected run был
изолирован через `ANDROID_SERIAL=emulator-5556`, прямые ADB-команды через `-s`.

Evidence (ignored): `build/evidence/d106-gpx-filename/`, включая финальный
и первый failed logs, два restart logs и исходные snapshots документов.

- [Список, fontScale1.7](../build/evidence/d106-gpx-filename/filename-list-font17.png)
- [Диалог до прокрутки](../build/evidence/d106-gpx-filename/filename-dialog-top-font17.png)
- [Диалог после прокрутки](../build/evidence/d106-gpx-filename/filename-dialog-scrolled-font17.png)

Не выполнены: повторный реальный SAF import/UI walk и TalkBack на Samsung;
установка новой сборки требует отдельного разрешения OWNER. Результаты
эмулятора не заменяют физическую qualification. Пользовательские GPX и
исследовательские данные Samsung этим исправлением не изменялись.

## Git / границы

Исходные незакоммиченные handoff и Samsung report сохранены отдельно и не входят
в commit этого исправления. Их побайтная сохранность проверяется перед commit.
Samsung не обновляется; новая qualification и TalkBack требуют отдельного OWNER scope.
Нет push, «Моих точек», GPX Backup/Restore или продолжения Stage1/H05.
