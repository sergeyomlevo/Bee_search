# D106 — Samsung: итог проверки UI-корректировок a8d51cf

2026-10-22. **Обновление и сохранность PASS; целевая квалификация имеет FAIL.
Испытания остановлены для OWNER review, production-код не изменён.**

## Сборка, устройство и baseline

- HEAD `a8d51cf6617d9ac1a69aae57f25a4bea00f474ba`, ветка `task/d106-stage0-overlay-registry`.
- Samsung SM-S938B, ADB serial `RFCY90MBYVZ`, Android 16/API36, fontScale **1.7**.
  Все адресные ADB-команды использовали этот serial; эмулятор не использовался.
- DEV до/после: `org.beesearch.app.dev`, `1.3.0`, versionCode **8**.
  Прежний APK SHA-256: `af3f27cdb4ef30a4207b2f9d9f101503409d41be3128ae15d0ea83c99b3283a2`.
  Собранный и установленный: `3c3e8459388cd2f6f880afb8579b87d84e2c2d11d20b5b6ced2bf22f9ed39e57`.
- `:app:assembleDebug --offline` PASS; новых тестовых прогонов не было.
  Подписи совпали: certificate SHA-256 `2fd4f10ac24e19eb2e8c1bae08dca48b4402e751e9c33d6a33f59025b0bfb654`.
  Схема Room 17 и migrations в этом коммите не менялись; destructive fallback отсутствует.
- Штатный `adb -s RFCY90MBYVZ install -r ...` завершился `Success`.
  firstInstallTime сохранён: `2026-09-16 08:13:50`; lastUpdateTime: `2026-10-22 08:12:10`.
  До запуска все 4067 файлов и содержимое БД совпали с актуальным baseline.
- **TEST-S25-02 отсутствовала уже ДО установки.** Текущий baseline содержит другую
  существующую точку с одной фотографией, ON. OWNER пояснил: «Точки удалял и ставил
  я в процессе тестирования». Продолжено с текущей точкой; её название, координаты
  и содержимое фотографии здесь не публикуются. Новые точки не создавались.

Evidence: [локальный каталог](../build/evidence/my-points-samsung-review-20261022/),
[проверка установки](../build/evidence/my-points-samsung-review-20261022/install-verification.json),
[preflight](../build/evidence/my-points-samsung-review-20261022/preinstall-check.json).
Предыдущие [Samsung-квалификация](d106-my-points-samsung-qualification.md) и
[эмуляторный отчёт коррекций](d106-my-points-samsung-review-corrections.md) — отдельные сессии.
Их результаты не выдаются за текущие Samsung-проверки.

## Технические копии и сохранность

Старый `my-points-samsung-20261021/critical-before.tar` доступен: SHA-256
`fb929645caefadf6664ae9e41924c70738a0da322791b6e74f18ef23c2b02738`, все 4066
файлов повторно сверены с manifest. Он исторический, не текущий baseline.

До установки DEV остановлен, отсутствие PID подтверждено. Снята актуальная
копия `build/evidence/my-points-samsung-review-20261022/current-before.tar`:
**309171712 байт**, 4067 файлов, 305834285 байт содержимого; SHA-256
`a8e3aef95c9442404ddc2154fe65efdfd874c1864871ec4782c0f8ce47f3204a`.
Все пути/размеры/хеши архива проверены; хеши устройства до/после копирования
совпали. SQLite скопирована с WAL; на компьютере получена portable SQLite через
backup API, integrity `ok`, foreign_key_check пуст, Room 17.
[Проверка актуальной копии](../build/evidence/my-points-samsung-review-20261022/current-backup-verification.json).
**Restore NOT RUN.** Наличие копии не доказывает восстановление.

[Baseline → final](../build/evidence/my-points-samsung-review-20261022/final-preservation.json):
все **23 таблицы** совпадают по count и SHA-256 нормализованных полных строк,
включая user_points, user_point_photos и Room identity. Точка целиком неизменна,
createdAt прежний, одна фотография, итоговая видимость **ON**. Семь managed media
(8060690 байт), два файла Map Package и DataStore идентичны по SHA-256.
Прежние 2 Territory, 1 Observer, 5 ObservationPoint, 19 Bee, 87 FlightCycle,
6 физических объектов и их media сохранены. GPX-imports/tracks/samples/jobs пусты
до/после, 7 gpx_deletions сохранены. Room final: integrity `ok`, FK-ошибок нет.

Нет пропавших baseline-файлов. Изменились только `files/mbgl-offline.db`,
`files/profileInstalled`, `databases/bee_search.db-wal`, `databases/bee_search.db-shm`,
`shared_prefs/android.app.ActivityThread.IDS.xml`, `no_backup/androidx.work.workdb-wal`
и `no_backup/androidx.work.workdb-shm`; это не заявление о совпадении всех private bytes.
Метаданные установки Stable/Beta неизменны; их private data отдельно не аудировались.
Архивы, APK, private SQLite и screenshots остаются локально в Git-ignored build/evidence;
в Git не добавлялись. После финальной согласованной сверки DEV оставлен остановленным.

## Samsung-сценарии

| Проверка | Статус | Фактический результат |
|---|---|---|
| Уменьшенные заголовки chooser | PASS | Оба заголовка меньше object buttons, второстепенный контрастный цвет, полные переносы без clipping; все пункты, «Моя точка» и «Отмена» доступны при 1.7 |
| Вертикальный scroll chooser при overflow | NOT RUN | Жест выполнен, но всё содержимое помещается без scroll; положительное смещение/overflow этим не доказаны |
| Графитовый треугольник, светлая обводка, отличие от GPS | PASS | Один существующий треугольник и отдельная синяя GPS-точка видны одновременно на online и установленной vector |
| Янтарное выделение и focus | PASS | «Показать на карте» центрирует правильную точку, янтарный triangular halo виден на online |
| Нажатие треугольника | PASS | И online, и vector открывают правильную карточку с прежними датой/фото/координатами |
| Online-подложка / установленная offline-vector | PASS | Обе реально отрисовали tiles и треугольник; ничего не скачивалось/заменялось. Сеть не отключалась, airplane-mode readiness не испытывалась |
| Сохранение vector при карточка → «Показать на карте» | FAIL | Возврат переключает источник на «Онлайн карта»; подробности ниже |
| Выделенный треугольник именно на vector после возврата | NOT RUN | Полученный после возврата selected screenshot фактически online; не выдан за offline PASS |
| OFF → скрытие → ON → восстановление | PASS | Маркер исчез и вернулся; затем БД подтвердила неизменную точку/фото и итоговый ON |
| Несколько пользовательских точек одновременно | NOT RUN | В baseline одна точка; дополнительные ради проверки не создавались |
| Обычный перезапуск и восстановление маркеров/видимости | NOT RUN | Остановлено после функционального дефекта; финальная остановка для сверки данных не является restart-тестом |
| Дата в карточке | PASS | `Создана: 21.10.2026` соответствует createdAt `1792599663690` в Europe/Moscow, не текущему 22.10.2026; читается компактной строкой; повторно не меняется |
| Фото, координаты, действия карточки | PASS | Фото реально отрисовано; координаты и «Показать на карте» доступны; редактор открыт и отменён без изменения полей |
| Дата отсутствует в списке/редакторе | PASS | Список и верх/низ редактора проверены; createdAt не редактировался |
| Дата отсутствует в форме создания | NOT RUN | В осмотренном первом viewport даты нет; полный scroll формы не проверялся. Пустой draft отменён, ничего не сохранено |
| GPS / исследовательские объекты / GPX | PASS / NOT RUN | GPS визуально прежний синий индикатор; research и GPX data сохранены. Визуальная регрессия research-маркеров NOT RUN (исходные фильтры скрывают их), populated GPX NOT RUN (0 imports) |

## Функциональный дефект и STOP

1. На карте выбрать «Векторная карта» — реальные tiles и треугольник видны.
2. Нажать треугольник текущей точки — открывается правильная карточка.
3. Нажать «Показать на карте».
4. Фактически: источник стал **«Онлайн карта»** вместо выбранной vector;
   точка центрирована и выделена. Ожидается возврат к выбранной подложке.

Доказательства: `offline-normal-triangle-gps` → `offline-card-from-triangle` →
`offline-selected-centered`. Последние имена исторические имена capture-команды:
их XML/PNG показывают ONLINE после возврата, это не offline-selected evidence.
Статически `BeeMap.kt` хранит developerBasemap через `remember`, начальное ONLINE;
та же строка есть в parent `80f08e6`. Это указывает на существующий lifecycle-путь,
но физическое поведение старой сборки в этой сессии не перепроверялось:
**новая регрессия коммита a8d51cf не доказана**. Код не исправлялся.

## Настоящие Samsung screenshots — локальные оригиналы

- [Chooser при 1.7](../build/evidence/my-points-samsung-review-20261022/chooser-top.png).
- [Online: треугольник и GPS](../build/evidence/my-points-samsung-review-20261022/online-normal-triangle-gps.png).
- [Online: янтарное выделение](../build/evidence/my-points-samsung-review-20261022/online-selected-triangle.png).
- [Установленная vector: треугольник, светлая обводка и GPS](../build/evidence/my-points-samsung-review-20261022/offline-normal-triangle-gps.png).
- [Карточка с датой и фото — private local evidence](../build/evidence/my-points-samsung-review-20261022/card-created-date.png).
- [Дефект: после vector-карточки возврат в ONLINE](../build/evidence/my-points-samsung-review-20261022/offline-selected-centered.png).

Фото и личный текст не копируются в публичный отчёт; ссылки требуют локального
игнорируемого evidence-каталога. Оригиналы PNG не редактировались.

## Git и оставшиеся ограничения

До и после: index пуст. Modified `.agent/handoff.md`; untracked прежние
`docs/d106-gpx-deletion-samsung-report.md`, `docs/d106-gpx-filename-samsung-report.md`,
`docs/d106-my-points-research.md`, `docs/d106-my-points-samsung-qualification.md`,
`docs/ui/evidence/my-points-stage-b-20261020/`, плюс этот новый отчёт.
Все 31 прежних dirty-файла проверены по SHA-256; handoff дополнен с сохранением
старых байтов. Production diff отсутствует. Commit/push/staging не выполнялись.

TalkBack, аварийные process-kill/ENOSPC/provider tests, полная A–D/территориальная
матрица и sensor accuracy не повторялись. См. отдельные NOT RUN выше.
**Stage1/H05 OPEN. STOP для OWNER review; дальнейшие испытания и исправление
переключения подложки требуют отдельной задачи.**

Технологическая сверка: [официальный ADB — install -r сохраняет данные](https://developer.android.com/tools/adb),
[AOSP checkDowngrade](https://android.googlesource.com/platform/frameworks/base/+/3a66bd2809838723252d0b81dfd8d3d2b3da6b23/services/core/java/com/android/server/pm/PackageManagerServiceUtils.java).
Равный versionCode сам по себе не запрещает замену; совпадение подписи и фактический
успех same-version update отдельно подтверждены на этом Samsung.
