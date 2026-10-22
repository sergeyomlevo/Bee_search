# D106 — итоговая Samsung-квалификация «Моих точек»

Дата evidence и финализации: 2026-10-21. **Основные выполненные сценарии PASS;
полная квалификация ограничена строками NOT RUN. Ожидается рассмотрение OWNER.**
Это итог по сохранённым доказательствам, не новая сессия испытаний.

## Область и происхождение доказательств

Репозиторий `C:\App\Bee_search_stage0_overlay_registry`, ветка
`task/d106-stage0-overlay-registry`, HEAD `80f08e6bc3985e94469bb571a9dd95c4a51f96cc`.
Samsung, serial `RFCY90MBYVZ`; реальный системный fontScale **1.7** до и после.
DEV `org.beesearch.app.dev`, versionName `1.3.0`, versionCode `8`.
Сохранённый lastUpdateTime: `2026-10-21 12:02:48`;
firstInstallTime сохранён: `2026-09-16 08:13:50`.
Установленный APK идентифицирован SHA-256
`af3f27cdb4ef30a4207b2f9d9f101503409d41be3128ae15d0ea83c99b3283a2`.

Основной [каталог evidence](../build/evidence/my-points-samsung-20261021/)
содержит журнал `actions.jsonl`, PNG + UIAutomator XML/JSON, снимки Room,
файловые SHA-256 и проверки отдельных действий. Сводки проверены вместе с
baseline/final; локально повторно рассчитаны fingerprints всех таблиц из
`before/analysis/portable.sqlite` и `final/analysis/portable.sqlite`.
Они совпадают с сохранёнными `database.json`.
Скриншот подтверждает показанное состояние; результат записи/удаления
подтверждается также снимком БД и файловыми проверками.

Предшествующие отчёты: [A и общий implementation report](d106-my-points-implementation-report.md),
[B](d106-my-points-stage-b-qualification.md),
[C](d106-my-points-stage-c-qualification.md),
[D](d106-my-points-stage-d-qualification.md).
Их прежние «Samsung NOT RUN» относятся к соответствующим этапам до этой
Samsung-сессии. Результаты эмулятора не подменяют Samsung-доказательства.
В D сохранены JVM 1070/1070 и Android 115/115 + 8/8 (116 уникальных),
сборки и lint; в ходе финализации эти прогоны не повторялись.

Режим финализации: только чтение локальных evidence и запись этого отчёта с
добавлением записи в handoff. Без ADB, установки, восстановления, удаления,
новых испытаний телефона, изменения production-кода, staging, commit или push.
External research: **not applicable** — новых технических механизмов и изменений
поведения нет; задача состоит в сверке уже полученных результатов.

## Preflight, установка и технический backup

| Проверка | Статус | Сохранённое доказательство / граница |
|---|---|---|
| DEV application ID, версия и совпадение подписи | PASS | [preinstall-check.json](../build/evidence/my-points-samsung-20261021/preinstall-check.json), built/installed-signature.txt; destructive fallback отсутствует, миграция зарегистрирована |
| Стабильность baseline перед установкой | PASS | [quiescence-preinstall.json](../build/evidence/my-points-samsung-20261021/quiescence-preinstall.json): DEV остановлен предыдущей сессией, все 4066 хешей совпали |
| In-place DEV update, сохранённая первая установка | PASS | install-result.txt и [install-verification.json](../build/evidence/my-points-samsung-20261021/install-verification.json); APK SHA выше |
| Файлы сразу после установки, до запуска | PASS | [prelaunch-preservation.json](../build/evidence/my-points-samsung-20261021/prelaunch-preservation.json): все 4066 файлов идентичны |
| Room 16→17 и прежние данные после первого запуска | PASS | [migration-preservation.json](../build/evidence/my-points-samsung-20261021/migration-preservation.json): 20 прежних таблиц совпали, изменена room_master_table; 3998 критических файлов совпали |
| Технический backup доступен, SHA-256 и содержимое проверены | PASS | [backup-verification.json](../build/evidence/my-points-samsung-20261021/backup-verification.json), backup-manifest.json и critical-before.tar; подробности ниже |
| Восстановление из технического backup | NOT RUN | Архив проверен чтением; восстановление на устройстве не выполнялось |
| Stable/Beta не переустановлены | PASS | Сохранённые org.beesearch.app[.beta].before/final.txt: version/firstInstallTime/lastUpdateTime совпадают; это проверка метаданных пакетов, не полный аудит их private data |

Технический архив находится локально в
`build/evidence/my-points-samsung-20261021/critical-before.tar`:
305859072 байта, **4066 файлов**, суммарно 302523877 байт содержимого.
SHA-256: `fb929645caefadf6664ae9e41924c70738a0da322791b6e74f18ef23c2b02738`.
При финализации SHA-256 пересчитан; каждый файл внутри tar прочитан без
извлечения, размер и SHA-256 совпали с manifest, набор путей и хеши совпали с
`before/files.json`. Baseline Room v16: integrity `ok`, FK-нарушений нет.
Это техническая копия с личными данными, **не испытанный restore и не продуктовый
Backup/Restore**. Каталог `build/evidence/` игнорируется Git; архив и private
снимки не отслеживаются, не добавлены в index и не включаются в Git.
Ссылки на эти локальные материалы требуют сохранённого evidence-каталога.

## Матрица выполненных пользовательских сценариев

PASS означает только явно указанный выполненный сценарий. NOT RUN означает
отсутствие достаточного сохранённого подтверждения. Непроверенные ветви не
получают PASS из результатов соседнего сценария. Подтверждённых итоговых FAIL
в этой матрице нет; это не утверждение об отсутствии дефектов во всех условиях.

| № | Сценарий | Статус | Доказательства и предел результата |
|---|---|---|---|
| S01 | Создание через `+ → Моя точка → OK`, название/заметка, сохранение | PASS | actions.jsonl, create-title/create-ime, saved-first-card; TEST-S25-01 с длинным русским названием, затем TEST-S25-02; two-saved содержит 2 точки и 4 фото |
| S02 | Реальная камера Samsung и возврат снимка в форму | PASS | real-camera-open/review, camera-thumb-form; реальный снимок вместе с двумя gallery-фото в create-three-photos, затем сохранён |
| S03 | Выбор нескольких gallery-фото, отмена picker | PASS | picker-two-selected, create-three-photos, picker-cancel-return; журнал содержит выбор и Back; камера и gallery здесь реальные, не fake callback эмулятора |
| S04 | Миниатюры, внешний просмотр draft-фото, portrait и large EXIF6 | PASS | draft-portrait-viewer, draft-large-exif-viewer и возврат draft-return; визуальное подтверждение этих двух fixture, не всех EXIF/форматов |
| S05 | Новые точки сразу ON, независимый список и маркеры | PASS | two-saved: обе is_visible=1; my-points-list, two-markers; у первой 3 фото, у второй 1 |
| S06 | OFF из списка, открытие карточки по названию скрытой точки | PASS | list-first-off, card-from-list-off; нажатие названия отдельно от switch отражено в журнале |
| S07 | «Показать на карте» для OFF-точки, затем карточка по маркеру | PASS | off-card-actions, off-show-now-on, card-from-marker; Samsung подтверждает успешный путь; отказ записи отдельно NOT RUN |
| S08 | Сохранение OFF после перезапуска и последующий ON | PASS | visibility-off snapshot: TEST-S25-02 is_visible=0; remaining-off-restart без её marker; remaining-on-map и final: is_visible=1 |
| S09 | Редактирование текста/места, удаление старого и добавление нового фото → Отмена | PASS | edit-cancel-*; edit-cancel-verification.json: все fingerprints БД и saved media hashes прежние, staging пуст; after-edit-cancel-restart |
| S10 | Удаление фото из draft до Save не удаляет saved bytes | PASS | edit-removed-large-before-save; edit-draft-deletion-safety.json: все 4 saved media hashes прежние |
| S11 | Редактирование текста/места/фото → Save | PASS | edit-save-three-photos, edited-card/edited-marker; edit-save-verification.json: текст/координаты изменены, вторая точка идентична, 3 retained фото сохранили rows/hash, удалённое фото убрано после Save |
| S12 | Сохранённые изменения и два маркера после перезапуска | PASS | restart-edited-points, test-records-after-edit.json; в текстовом вводе EDIT оказался внутри названия — оценивается сохранение фактического текста, не предполагаемый суффикс |
| S13 | Отмена нового создания с выбранным фото | PASS | cancel-create-with-photo/map; create-cancel-verification.json: все таблицы совпали, 10 сохранённых фото прежние, staging пуст |
| S14 | Диалог удаления тестовой точки → Отмена | PASS | delete-confirmation/delete-cancelled, delete-cancel-verification.json: все таблицы прежние |
| S15 | Подтверждённое удаление TEST-S25-01 из карточки на карте | PASS | delete-map-origin/card, delete-final-confirm; delete-verification.json и after-delete: удалены только целевые rows, вторая точка/её фото и external source hashes сохранены |
| S16 | Удалённая точка не возвращается после перезапуска | PASS | deleted-restart-map, post-delete-list, final: только TEST-S25-02 и 1 фото; staging пуст |
| S17 | Смена online/vector и возврат overlay | PASS | online-map-user-markers, vector-map-user-markers, final-offline-map/final-restored-map; это локальный smoke, не вся матрица стилей/сбоев или доказательство airplane mode |
| S18 | Прежние объекты и фото доступны | PASS | legacy-hollows-list/card, legacy-photo-viewer/return, regression-back-map плюс baseline/final checks ниже |
| S19 | Реальный fontScale 1.7, scroll, длинное название и IME | PASS | font-before.txt, final-preservation.json; my-points-list, create-ime/title-ime, create-three-photos, edit-save-three-photos; подтверждены показанные состояния и достижимость действий, не полный accessibility audit |
| S20 | TalkBack: focus order, announcements, управление без зрения | NOT RUN | Наличие semantic labels в XML не доказывает работу TalkBack |
| S21 | Process kill/сбой питания в Save/Delete/cleanup; ENOSPC, provider/Room/file failures | NOT RUN | Перезапуск после завершённого действия не является аварийным испытанием |
| S22 | Ошибки сохранения видимости, повтор/retry, отказ persist-before-focus на Samsung | NOT RUN | В этом evidence есть только успешные действия; автоматические проверки прошлых этапов имеют отдельный scope |
| S23 | Смена Territory A→B→A, overlapping targets, полная map lifecycle matrix на Samsung | NOT RUN | Смена подложки S17 не подменяет эти сценарии |
| S24 | Сохранность существующих populated GPX при Samsung update | NOT RUN | В Samsung baseline 0 GPX-imports; пустые таблицы совпали, но populated GPX здесь нет |
| S25 | GPS/компас: точность, полевые условия; все форматы/очень большие наборы фото | NOT RUN | Показ GPS и выбранных fixture не доказывает точность датчиков или capacity |
| S26 | Удаление из списка, Back каждого диалога, rotation в каждой форме на Samsung | NOT RUN | Эти отдельные ветви не выводятся из S09/S14/S15; результаты API29 см. D |

## Компактный обзор настоящих Samsung screenshots

Оригинальные PNG оставлены на месте без изменений, копирования и перерисовки.
Таблица служит обзором; рядом с каждым PNG сохранены XML/JSON.

| Что смотреть | Оригинальные снимки |
|---|---|
| Камера и создание с 3 фото | [Камера](../build/evidence/my-points-samsung-20261021/real-camera-review.png) · [Форма, 3 фото](../build/evidence/my-points-samsung-20261021/create-three-photos.png) · [IME](../build/evidence/my-points-samsung-20261021/create-ime.png) |
| Draft preview | [Portrait](../build/evidence/my-points-samsung-20261021/draft-portrait-viewer.png) · [Large EXIF6](../build/evidence/my-points-samsung-20261021/draft-large-exif-viewer.png) |
| Список и карта | [Две точки ON](../build/evidence/my-points-samsung-20261021/my-points-list.png) · [Карточка OFF-точки](../build/evidence/my-points-samsung-20261021/card-from-list-off.png) · [Карточка по маркеру](../build/evidence/my-points-samsung-20261021/card-from-marker.png) |
| Редактирование | [3 фото перед Save](../build/evidence/my-points-samsung-20261021/edit-save-three-photos.png) · [Результат](../build/evidence/my-points-samsung-20261021/edited-card.png) · [После restart](../build/evidence/my-points-samsung-20261021/restart-edited-points.png) |
| Отмена | [Новый draft с фото](../build/evidence/my-points-samsung-20261021/cancel-create-with-photo.png) · [Отмена edit, restart](../build/evidence/my-points-samsung-20261021/after-edit-cancel-restart.png) |
| Удаление | [Финальное подтверждение](../build/evidence/my-points-samsung-20261021/delete-final-confirm.png) · [Список после удаления](../build/evidence/my-points-samsung-20261021/post-delete-list.png) · [Карта после restart](../build/evidence/my-points-samsung-20261021/deleted-restart-map.png) |
| Остаток TEST-S25-02 и видимость | [Карточка](../build/evidence/my-points-samsung-20261021/remaining-card.png) · [OFF после restart](../build/evidence/my-points-samsung-20261021/remaining-off-restart.png) · [ON](../build/evidence/my-points-samsung-20261021/remaining-on-map.png) |
| Карта и прежнее фото | [Векторная карта](../build/evidence/my-points-samsung-20261021/final-offline-map.png) · [Прежнее фото](../build/evidence/my-points-samsung-20261021/legacy-photo-viewer.png) |

## Сохранность baseline → final

Источник: [final-preservation.json](../build/evidence/my-points-samsung-20261021/final-preservation.json),
`before/database.json`, `final/database.json`, `before/files.json`, `final/files.json`.
Локальная повторная проверка portable.sqlite: версии 16/17, integrity `ok`,
foreign_key_check пуст в обеих копиях. Все **20 прежних таблиц** имеют прежние
row counts и SHA-256 нормализованных полных строк, кроме отдельно исключённой
служебной `room_master_table` с новой Room identity.

Сохранены, в частности: 2 Territory, 1 Observer, 5 ObservationPoint,
19 Bee, 87 FlightCycle, 6 physical objects (5 Hollow, 1 LogHive),
6 physical-object-media rows, 2 observation attachments и 5 weather rows.
GPX-imports/source tracks/segments/samples/jobs были и остались пустыми;
7 прежних gpx_deletions сохранены. Ранее созданных My Points в baseline нет.

Шесть прежних managed photo files (5111915 байт) и два map-package файла
(21108421 байт, включая PMTiles и manifest) сохранили SHA-256.
DataStore `settings.pb` двоично идентичен baseline, изменённых ключей нет.
Два внешних тестовых исходника сохранены по recorded SHA-256.

Из 4012 baseline critical paths сохранённая сводка отмечает 6 изменений:
`files/mbgl-offline.db`, `files/profileInstalled`,
`shared_prefs/android.app.ActivityThread.IDS.xml`,
`no_backup/androidx.work.workdb`, его `-wal` и `-shm`.
Это служебные MapLibre/profile/platform/WorkManager файлы; их изменение
не означает совпадения всех private bytes. Их внутреннее семантическое
содержимое здесь отдельно не квалифицировано. Сохранность исследовательских
данных подтверждена таблицами, managed media, PMTiles и настройками выше.

Финал: ровно **TEST-S25-02**, UUID
`61e1a366-ba99-41cc-be7e-9490aa4d9600`, **1 фото**, `is_visible=1`.
TEST-S25-01 удалена в предыдущей испытательной сессии; отменённый новый draft
не сохранён. Staging пуст. **TEST-S25-02 не удалять**: оставлена для OWNER review.
Внешние тестовые исходники также остаются; очистка не входит в эту задачу.

## Оставшиеся ограничения и точка остановки

- OWNER ещё не подтвердил финальную визуальную/полевую приёмку. PASS отдельных
  сценариев не закрывает весь UI checklist, TalkBack и строки NOT RUN.
- Restore не испытывался; наличие технического архива и верных SHA-256 не
  доказывает восстановление приложения или продуктовый Backup/Restore.
- Аварийные ветви не испытывались. Из Stage D известно: cleanup receipts
  живут в текущем процессе; process death после неудачной очистки может оставить
  внутренние orphan bytes. Успешный обычный delete не доказывает durable retry.
- Существующие GPX отсутствовали в Samsung baseline; нельзя заявлять
  сохранность populated GPX на этом устройстве по пустым таблицам.
- Карта проверена ограниченным online/vector smoke; Territory/ошибки/overlap,
  длительная работа без сети и полевые GPS/компас не квалифицированы здесь.
- Все ссылки на Samsung PNG и private verification evidence локальны и зависят
  от сохранённого игнорируемого каталога build; технический архив не публикуется.

Прежние незакоммиченные материалы сверены с original-dirty-manifest.json перед
правкой; handoff обновлён добавлением новой записи с сохранением старых байтов.
Production-код и изображения не изменялись. Stage1/H05 остаются OPEN.
**STOP для рассмотрения OWNER. Новую разработку, установку, очистку и испытания
без отдельной задачи не продолжать.**
