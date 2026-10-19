# D106 — безопасное удаление сохранённых GPX, 2026-10-19

## Scope и baseline

Checkout `C:/App/Bee_search_stage0_overlay_registry`, branch
`task/d106-stage0-overlay-registry`, исходный HEAD `97d4d67`.
До работы index был пуст, единственный прежний diff — `.agent/handoff.md`
(134 добавления / 1 удаление). Его исходные байты сохранены в локальном evidence.
GPX1.0 и Exchange/GPX уже находились в этом checkout и не перерабатывались.

OWNER утвердил [PNG+MD](ui/mockups/gpx-display-delete-v1.md) и
[durable deletion plan](d106-gpx-deletion-plan.md), APPROVED BY OWNER, 2026-10-19.
Нового согласования и новой версии макета нет. Stage1/H05 остаются OPEN.

## Реализовано

- Long press имени → `Удалить` → именованное подтверждение. Обычный tap меняет
  видимость. Accessibility custom action открывает то же подтверждение.
- Отмена/Back не удаляют данные; повторные команды блокируются. До durable commit
  строка остаётся. Precommit error оставляет строку и `Повторить`, которое снова
  открывает подтверждение того же UUID. Длинные названия переносятся, тело диалога
  прокручивается, кнопки расположены вертикально и имеют область не меньше48dp.
- Room migration15→16 добавляет `gpx_deletions`: постоянный tombstone без FK к
  удалённому parent, request UUID unique, отдельные files/settings cleanup flags.
  Существующие таблицы, original/canonical identity и исследовательские данные
  не изменяются миграцией. Схема16 экспортирована в обычный Room schema каталог.
- Под общим Coordinator writer lock выполняется проверка runtime reconciliation,
  exact Territory/import UUID, READY envelope и ownership. Остаточный READY job
  блокирует удаление до завершения его штатного recovery; он не удаляется этим API.
- Одна транзакция сохраняет tombstone и удаляет samples→segments→tracks→import
  в RESTRICT-safe порядке. Старые request/import/original UUID нельзя вернуть
  через INSERT/UPDATE/reconciliation. Новый явный импорт тех же байтов разрешён;
  прежнее подтверждение повторного импорта сохраняет свою семантику.
- После commit синхронная runtime-подписка инвалидирует только выбранный UUID.
  ViewModel отбрасывает позднюю геометрию и snapshot каталога; native resources
  освобождает существующий Registry/session. Камера не перемещается.
- Managed copy очищается только по сохранённому request/identity/directory/payload
  inode witness, с fsync директорий. Уже отсутствующий payload/каталог допускает
  идемпотентное продолжение; чужая ownership до commit сохраняет READY.
- File/DataStore cleanup подтверждаются независимо. При ошибке durable journal
  остаётся и повторяется при runtime access/старте. Один cleanup debt не закрывает
  доступ к другим READY GPX. Stale visibility UUID не делает удалённый GPX видимым.
  Исчезновение строки означает durable логическое удаление, а не заявление о
  завершении всех файловых операций при `cleanupPending=true`.
- Внешний файл, включая Exchange/GPX, не передаётся в deletion API и не удаляется.
  Импорт, Registry, MapLibre, Backup/Restore и Android Auto Backup не перестраивались.

Decomposition performed: filesystem deletion protocol выделен в
`GpxOriginalDeletion`, Room transaction — в `GpxDeletionStore`, orchestration —
в `GpxDeletion`. Существующие крупные entrypoint-файлы получили только подключения;
`GpxDisplayPage` сохраняет одну UI-ответственность со связанными private helpers.

## Проверки

| Проверка | Результат |
|---|---|
| JVM `testDebugUnitTest` | 1062/1062, без ошибок и пропусков |
| DEV + Android test APK assemblies | PASS |
| API29 deletion/backend/migration/ViewModel/UI | 27/27 PASS |
| API29 целевые GPX/import/catalog/geometry/visibility/SAF/migration regressions | 64/64 PASS |
| API29 native MapLibre opt-in lifecycle | 4/4 PASS, без пропусков |
| API37 deletion/backend/migration + GPX1.0 | 13/13 PASS |
| Финальный UI screenshot/Back recheck после error-цвета | 1/1 PASS |
| Итоговый lint | PASS: 0 errors,36 warnings,6 hints |
| Diff/links/PNG checks | PASS: cached/worktree diff check,15 новых local links,PNG decode |

API29 — `emulator-5556`, фактический system fontScale1.7; API37 —
`emulator-5554`, backend qualification. Установка выполнялась только на эти
явные serial, пакетами `org.beesearch.app.dev` / `.dev.test`, через update `-r`.
Никаких uninstall/clear, broad connected tasks или изменения Samsung не было.

Fault injection проверяет BEFORE_ROOM, BEFORE_COMMIT с полным rollback,
AFTER_COMMIT, каждый unlink sidecar/payload, удаление каталога, его fsync,
BEFORE_FILES_ACK, ошибку visibility save и BEFORE_SETTINGS_ACK. Проверено
закрытие/повторное открытие отдельной дисковой БД и cleanup через новый runtime.
Тесты используют отдельные Room базы и private roots с тестовыми GPX.
Проверены visible/hidden UUID, одинаковые байты разных импортов, request replay,
повторное удаление, новая загрузка, другая Territory, stale callbacks/catalog,
сохранность ObservationPoint/Bee/FlightCycle и других GPX, ownership mismatch,
missing original, FK integrity, внешний sentinel. Native lifecycle отдельно
проверяет удаление GPX declarations и сохранность исследовательских ресурсов.

Независимый Luna review критического lifecycle выполнен; первоначальные проблемы
runtime gate/остаточного READY job исправлены, повторный review блокеров не нашёл.
Начальный Android-прогон27 имел4 ошибки тестов (SQL qualification и test API на
UI thread); они исправлены, повторный набор27 прошёл полностью.

## Screenshots и evidence

Фактические Compose bottom sheet screenshots API29/fontScale1.7:

- [Меню удаления](../build/evidence/d106-gpx-delete-20261019/screenshots-final/sheet-delete-menu-font17.png).
- [Подтверждение](../build/evidence/d106-gpx-delete-20261019/screenshots-final/sheet-delete-dialog-font17.png).
- [Ошибка и повтор](../build/evidence/d106-gpx-delete-20261019/screenshots-final/sheet-delete-error-font17.png).

Это тестовые UI fixtures существующего bottom sheet, не скриншоты Samsung или
настоящих пользовательских записей. Палитра определяется текущей темой устройства.
Финальный DEV APK: `org.beesearch.app.dev`,1.3.0(8); SHA256
`e8f449b3b2b88084378de09f5adb8e427efdb3c9d01015e38b4bf0f916fb6549`.
На Samsung этот APK не устанавливался.

Локальные логи: `build/evidence/d106-gpx-delete-20261019/` (ignored).

## Непроверенное и следующий шаг

- Samsung не обновлялся. Реальное удаление специально созданного тестового GPX,
  Samsung Room migration, TalkBack и визуальная приёмка на S25 Ultra требуют
  отдельного разрешения OWNER на новое DEV-обновление.
- Fault injection/close-reopen не являются испытанием физического отключения
  питания, kernel I/O hang, заполнения реального накопителя или предельного размера.
- При стойкой ошибке recovery остаточного READY job удаление консервативно
  недоступно; при чужом ownership не удаляется ни чужой файл, ни READY parent.
- No push. My points, export, trash/restore и единый Backup/Restore не начаты.

## Основания Android API

Использованы существующие platform/Room/Compose механизмы, без новых зависимостей:
[Room migration](https://developer.android.com/training/data-storage/room/migrating-db-versions),
[Compose custom accessibility actions](https://developer.android.com/develop/ui/compose/accessibility/semantics#custom-actions).

## Изменённые файлы

- `.agent/handoff.md` — только новая запись; прежний diff не входит в commit.
- `app/schemas/org.beesearch.app.data.local.room.BeeSearchDatabase/16.json`
- `app/src/androidTest/java/org/beesearch/app/data/gpx/GpxDeletionDeviceTest.kt`
- `app/src/androidTest/java/org/beesearch/app/data/local/room/GpxDeletionMigrationTest.kt`
- `app/src/androidTest/java/org/beesearch/app/data/local/room/GpxPersistenceMigrationTest.kt`
- `app/src/androidTest/java/org/beesearch/app/ui/map/GpxDeletionViewModelTest.kt`
- `app/src/androidTest/java/org/beesearch/app/ui/map/GpxDisplayPageTest.kt`
- `app/src/main/java/org/beesearch/app/AppContainer.kt`
- `app/src/main/java/org/beesearch/app/data/gpx/GpxDeletion.kt`
- `app/src/main/java/org/beesearch/app/data/gpx/GpxImportCoordinator.kt`
- `app/src/main/java/org/beesearch/app/data/gpx/GpxOriginalDeletion.kt`
- `app/src/main/java/org/beesearch/app/data/gpx/GpxOriginalStore.kt`
- `app/src/main/java/org/beesearch/app/data/gpx/GpxRuntime.kt`
- `app/src/main/java/org/beesearch/app/data/local/room/BeeSearchDatabase.kt`
- `app/src/main/java/org/beesearch/app/data/local/room/GpxDao.kt`
- `app/src/main/java/org/beesearch/app/data/local/room/GpxDeletionEntity.kt`
- `app/src/main/java/org/beesearch/app/data/local/room/GpxDeletionSchema.kt`
- `app/src/main/java/org/beesearch/app/data/local/room/GpxDeletionStore.kt`
- `app/src/main/java/org/beesearch/app/data/local/room/GpxPersistenceSchema.kt`
- `app/src/main/java/org/beesearch/app/ui/map/GpxDisplayPage.kt`
- `app/src/main/java/org/beesearch/app/ui/map/GpxDisplayViewModel.kt`
- `app/src/main/java/org/beesearch/app/ui/map/MainMapScreen.kt`
- `app/src/main/java/org/beesearch/app/ui/map/MapDataPanel.kt`
- `docs/architecture.md`
- `docs/d106-gpx-deletion-plan.md`
- `docs/d106-gpx-deletion-report.md`
- `docs/data-model.md`
- `docs/decisions.md`
- `docs/product-requirements.md`
- `docs/ui/mockups/gpx-display-delete-v1.html`
- `docs/ui/mockups/gpx-display-delete-v1.md`
- `docs/ui/mockups/gpx-display-delete-v1.png`
- `docs/unified-territory-map-checklist.md`
- `docs/user-workflows.md`


## Git и handoff

В task commit входят34 файла из списка выше, включая только новую15-строчную
запись handoff. Прежние134 добавления /1 удаление остаются в worktree и не
включены в commit. Побайтная проверка после удаления новой записи совпала с
исходным snapshot SHA256
`e04dc8b81d405bd299a3d7229aa902771ae15b36f3c890ee08afa77dab1313d2`.
После commit ожидается только прежний dirty handoff; итоговый status проверяется
отдельно. Push не выполняется.
