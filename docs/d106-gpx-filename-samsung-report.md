# D106 — Samsung: исправление имени GPX, 2026-10-19

**Результат: PASS** — разрешённое обновление DEV и целевая проверка имени.
TalkBack — NOT RUN, не включался. Stage1/H05 остаются OPEN.
Production-код не менялся. Commit и push в этой qualification не выполнялись.

## Устройство и установленный артефакт

- Samsung S25 Ultra SM-S938B, serial `RFCY90MBYVZ`, Android API36.
- Реальный системный `font_scale=1.7`, не изменялся.
- Ветка `task/d106-stage0-overlay-registry`, HEAD
  `f7a848ea9928931e905884c17b0bf34e7a526118`.
- `:app:assembleDebug --offline` — BUILD SUCCESSFUL из этого HEAD.
- Package `org.beesearch.app.dev`, versionName `1.3.0`, versionCode `8`.
- APK SHA256 `ecb5151c902c20cf9b49cdc69786beb7f913e698a2037cbf72a3a21f0f4b7e70`.
  Установленный base.apk прочитан и совпал с этой сборкой после обновления
  и в конце qualification.
- Новый и прежний APK успешно проверены `apksigner`; один и тот же certificate
  SHA256 `2fd4f10ac24e19eb2e8c1bae08dca48b4402e751e9c33d6a33f59025b0bfb654`.
- Установка: только `adb -s RFCY90MBYVZ install -r`, результат `Success`.
  Нет uninstall, pm clear, downgrade, пересоздания данных или connected tests.
  Другие подключённые устройства не использовались.
- DEV lastUpdateTime: `2026-10-19 16:08:40` → `2026-10-19 21:50:54`
  по времени устройства; firstInstallTime `2026-09-16 08:13:50` сохранён.
  ceDataInode/deDataInode `438163/424656` сохранены.

## Новый тестовый импорт

Внешнее имя, 112 символов:

`Проверка имени GPX — длинный русский маршрут через северную поляну и лесную дорогу — Samsung 19 октября 2026.gpx`

Файл GPX1.1, 398 bytes, создан специально для этой проверки в
`Download/BeeSearch/Dev/Exchange/GPX`. Перед записью проверено отсутствие файла
с этим именем; его имя и SHA256 не совпадают с прежними импортами.
Внутренний `<trk><name>` намеренно другой: `Внутреннее имя трека, не имя файла`.

- Import UUID: `9826b98b-ea16-49ba-8b74-37058e95c8f9`.
- Original UUID: `41221e83-7dc1-46ed-b541-3dc724f6a890`.
- SHA256 `0171c1e9f9d9b55c05948b90d494a2e9b79c59a1187b30b683990457c7fefa5c`.
- READY, 1 SourceTrack, 1 segment, 3 samples.
- `display_name` в Room побуквенно совпадает с полным внешним именем.
  Managed original хранится по UUID как `original.gpx`, не по имени пользователя.
- Новый импорт остаётся OFF; настройки видимости не переключались.
- **Импорт и внешний тестовый файл оставлены на устройстве. Удаление не подтверждалось.**

## Результаты на физическом устройстве

| Проверка | Результат | Evidence |
|---|---|---|
| DEV update и точный установленный APK | PASS | Package/signature/SHA256 checks |
| Сохранность до первого запуска | PASS | Все4078 приватных файлов побайтно равны baseline |
| Реальный SAF picker | PASS | Открыт из Данные на карте → Треки → Импорт GPX |
| Начальная папка Exchange/GPX | PASS | Без ручной навигации, breadcrumbs BeeSearch/Dev/Exchange/GPX; новый файл из подготовленной Download-папки |
| Импорт нового GPX | PASS | Success UI, отдельный UUID, READY graph, exact original SHA256 |
| Имя в Room | PASS | `display_name` равно полному filename, не trk/name и не original.gpx |
| Имя в списке | PASS | Полное имя, переносы, без ellipsis; switch нового импорта OFF |
| Long press → Удалить → подтверждение | PASS | То же полное название в тексте подтверждения |
| Длинное сообщение, fontScale1.7 | PASS | Тело реально прокручено; начало/конец текста и кнопки проверены визуально |
| Отмена | PASS | Закрывает подтверждение, строка остаётся; новый tombstone отсутствует |
| Перезапуск | PASS | Force-stop → новый запуск; то же имя в списке, Room целиком равен snapshot после Отмена |
| Прежние4 GPX и исследовательские записи | PASS | Все прежние строки и originals сохранены |
| Visibility/DataStore/offline packages | PASS | DataStore byte-identical;5 файлов package/PMTiles byte-identical |
| Stable/Beta | PASS | Package path/version/install-update times и data inode неизменны |
| TalkBack | NOT RUN | accessibility_enabled=0, services=null до и после, не включался |
| Подтверждённое удаление нового импорта | NOT RUN | По текущему заданию тестовый GPX сохраняется |

FAIL в разрешённом целевом сценарии не обнаружено.
Это qualification исправления имени, не полная квалификация Stage1/H05.

## Контроль сохранности

Сняты согласованные read-only snapshots после остановки только DEV; DB/WAL/SHM
и DataStore скопированы наружу. SQL анализировался на локальных копиях через
SQLite backup; исходные данные на телефоне не открывались для записи вручную.

- Room16, `integrity_check=ok`, `foreign_key_check=[]`.
- Все прежние строки каждого GPX-table — точное подмножество финальных строк;
  additions только: imports+1, source_tracks+1, segments+1, samples+3.
- Все остальные17 таблиц побуквенно равны baseline, включая research tables,
  delete tombstones и import journal. В том числе сохранены19 Bee,87 FlightCycle,
  5 ObservationPoint,6 physical objects и6 media records.
- Все12 файлов четырёх прежних GPX originals совпали по SHA256.
- DataStore совпал побайтно: это включает прежний visibility set и offline
  package settings. Новый UUID в visibility не добавлялся.
- Все5 файлов map-packages/PMTiles сохранены побайтно; офлайн-карты не удалялись
  и не скачивались заново. MapLibre tile-cache DB изменился при работе онлайн-карты;
  он сравнивался отдельно от неизменных импортированных PMTiles.
- Ни одного прежнего файла не удалено. Добавлены только original.gpx/owner/verified
  нового original UUID. Изменённые прежние файлы ограничены Room DB/WAL/SHM,
  MapLibre cache, WorkManager DB/WAL/SHM, ActivityThread IDs и profileInstalled.
- Room после Отмена и после restart совпал целиком. Внешний тестовый GPX
  сохранился с исходным SHA256. Существующие внешние оригиналы не изменялись.
- Stable/Beta не запускались и не обновлялись.

Для финального coherent snapshot DEV остановлен; следующий запуск обычный.

## Скриншоты Samsung

Local ignored evidence (not part of Git; may be absent from another checkout):
`build/evidence/d106-gpx-filename-samsung-20261019/` contains
`import-picker.png`, `import-result.png`, `list-with-name.png`, `test-menu.png`,
`named-confirmation.png`, `named-confirmation-scrolled.png`,
`cancelled-list.png`, `restart-list.png`.

Локальная ignored evidence-папка содержит приватные snapshots; не публиковать:
`build/evidence/d106-gpx-filename-samsung-20261019/`.
Основные проверки: installed-apk.json, apk-check.json, preservation-comparison.json,
package-comparison.json, final-device-check.json, test-import.json и UI XML/JSON.

Отдельный read-only аудит Luna подтвердил согласованность snapshots, хэшей,
имени в Room/UI и package metadata; расхождений не найдено. Повторных действий
на устройстве этот аудит не выполнял.

## Git / точка остановки

HEAD остаётся `f7a848e`, index пуст. Изменены только новая запись handoff и этот
новый отчёт; прежний handoff diff и `d106-gpx-deletion-samsung-report.md`
сохранены побайтно. Production diff пуст; commit/push не выполнялись.
Stage1/H05 OPEN. «Мои точки» и следующие стадии не начинались.
