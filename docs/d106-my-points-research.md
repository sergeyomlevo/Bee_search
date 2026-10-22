# D106 — «Мои точки»: исследование повторного использования

2026-10-20. **DESIGN ONLY / NO IMPLEMENTATION.** Проверенная база:
`task/d106-stage0-overlay-registry`, `f7a848ea9928931e905884c17b0bf34e7a526118`.
Stage1/H05 остаются OPEN. Ни один из предлагаемых ниже типов/ключей ещё не реализован.

## Границы и решения OWNER

Самостоятельная пользовательская точка принадлежит Territory, не является
ObservationPoint, физическим исследовательским объектом или GPX sample.
Обязательное название, необязательная заметка, редактируемые координаты и фото.
**Уточнение OWNER в ходе исследования:** использовать существующее добавление
нескольких фотографий, не вводить ограничение «одно фото». Наблюдатель уже
создаётся при начале работы: текущий prerequisite chooser сохраняется.
Видео, категории, цвета, классификаторы и дополнительные характеристики не добавляются.

Рекомендация: расширить существующие маршруты и несколько точек подключения;
новые entity/DAO и форма отражают самостоятельные данные, а не второй map/media framework.
В требованиях/decisions пока зафиксирована disabled-заглушка
([D106 C](decisions.md#d106-user-data-navigation--owner-decision-2026-10-18));
перед реализацией синхронизировать её статус и новые согласованные свойства в
product/workflows/domain/data/architecture/glossary. В этом исследовании их не переписываем.

## Карта повторного использования — OBSERVED → PROPOSED

Все ссылки указывают на существующие файлы; MyPoint/UserPoint ниже — предлагаемые имена.

| Функция | Существующий компонент/файл | Можно использовать | Необходимое изменение |
|---|---|---|---|
| `+`, chooser | [CreateRecordTypeChooserDialog.kt](../app/src/main/java/org/beesearch/app/ui/map/CreateRecordTypeChooserDialog.kt):16; [MainViewModel.kt](../app/src/main/java/org/beesearch/app/MainViewModel.kt):756 | Сам AlertDialog и пять существующих кнопок | Два заголовка, divider, callback `Моя точка`; старые callbacks/disabled действия сохранить |
| Захват Territory/наблюдателя | [MainViewModel.kt](../app/src/main/java/org/beesearch/app/MainViewModel.kt):756–819 | Текущий prerequisite и захват контекста до выбора | Добавить цель пользовательской точки в transient navigation state; не создавать ObservationPoint в БД |
| Подтверждение координат | [BeeMap.kt](../app/src/main/java/org/beesearch/app/ui/map/BeeMap.kt):438,738–779; [MainMapScreen.kt](../app/src/main/java/org/beesearch/app/ui/map/MainMapScreen.kt):432 | Тот же map center, GPS/recenter, Back и `PhysicalObjectLocationControls` | В существующей selection state добавить create/edit пользовательской точки, новые назначения результата ОК |
| Форма | [PhysicalObjectEditor.kt](../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectEditor.kt):135; [PhysicalObjectCreationRoute.kt](../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectCreationRoute.kt):29 | Scroll, заголовок/Back, Material fields, adaptive actions, media section | Небольшая форма с названием/заметкой/координатами; не использовать Hollow validation и обязательные размеры/азимут |
| Карточка/редактирование | [PhysicalObjectCards.kt](../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectCards.kt):110,163; [PhysicalObjectDetailRoute.kt](../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectDetailRoute.kt):52 | Структура карточки, координатные actions, фото, подтверждение удаления | Узкий вариант пользовательской карточки, без designation/sequence/fixation/creator sections исследовательских объектов |
| Камера и готовые фото | [PhysicalObjectCreationRoute.kt](../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectCreationRoute.kt):44–89 | TakePicture + FileProvider; multi-photo picker и существующая лента | ImageOnly вместо ImageAndVideo для нового маршрута; не менять GPX/дупла/колоды и не добавлять лимит общего числа фото |
| Управляемые файлы | [PhysicalObjectMediaFileStore.kt](../app/src/main/java/org/beesearch/app/data/media/PhysicalObjectMediaFileStore.kt):27–210; [MediaFileWriter.kt](../app/src/main/java/org/beesearch/app/data/media/MediaFileWriter.kt):14 | Draft → activation → commit/rollback, streaming writer, SHA256, безопасные пути | Узкая адаптация существующего lifecycle к владельцу UserPoint и отдельному namespace; не копировать pipeline |
| Фото preview/viewer | [PhysicalObjectMediaPreview.kt](../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectMediaPreview.kt); [PhotoPreviewDecoder.kt](../app/src/main/java/org/beesearch/app/ui/media/PhotoPreviewDecoder.kt):8; [PhotoExternalViewer.kt](../app/src/main/java/org/beesearch/app/ui/media/PhotoExternalViewer.kt):12 | EXIF-aware preview и существующий большой внешний просмотр | Передавать новый managed file; PhotoExternalViewer extension не должна требовать ObservationPointAttachment как владельца |
| Room | [BeeSearchDatabase.kt](../app/src/main/java/org/beesearch/app/data/local/room/BeeSearchDatabase.kt):31; [Entities.kt](../app/src/main/java/org/beesearch/app/data/local/room/Entities.kt):157 | Та же БД, UUID/Instant converters, transactions | Отдельные point/photo tables, DAO/repository, добавочная migration; не дополнять PhysicalObjectType фиктивным типом |
| Вход в список | [GpxTracksPage.kt](../app/src/main/java/org/beesearch/app/ui/map/GpxTracksPage.kt):104; [MapDataPanel.kt](../app/src/main/java/org/beesearch/app/ui/map/MapDataPanel.kt) | Уже существующая строка `Мои точки`, pin и вложенные страницы sheet | Включить строку и добавить страницу списка, сохранив основной panel layout |
| Список/видимость | [GpxDisplayPage.kt](../app/src/main/java/org/beesearch/app/ui/map/GpxDisplayPage.kt):171–239; [GpxDisplayViewModel.kt](../app/src/main/java/org/beesearch/app/ui/map/GpxDisplayViewModel.kt):92; [DataStoreGpxVisibilityStore.kt](../app/src/main/java/org/beesearch/app/data/local/settings/DataStoreGpxVisibilityStore.kt):11 | LazyColumn, отступы, loading/error, persist-before-render, Territory UUID set | Новая строка: самостоятельный Switch + кликабельное название. GPX row целиком toggle, его поведение не менять |
| Registry/style/Territory | [MainMapScreen.kt](../app/src/main/java/org/beesearch/app/ui/map/MainMapScreen.kt):114–205; [MapLibreOverlaySession.kt](../app/src/main/java/org/beesearch/app/ui/map/MapLibreOverlaySession.kt); [MapOverlayRegistry.kt](../app/src/main/java/org/beesearch/app/ui/map/MapOverlayRegistry.kt):49 | Один session/Registry, scope, revisions, presentation results | Присоединить point declarations к runtimeOverlays GPX; отдельные keys и обработка результатов своего feature |
| Маркер и show-on-map | [SavedObjectMarkers.kt](../app/src/main/java/org/beesearch/app/ui/map/SavedObjectMarkers.kt):142; [MainViewModel.kt](../app/src/main/java/org/beesearch/app/MainViewModel.kt):879; [BeeMap.kt](../app/src/main/java/org/beesearch/app/ui/map/BeeMap.kt):526 | Проекция, 48dp touch/semantics, MapCenterRequest request→consume, origin/Back | Передать UserPoint ID для выделения и открытия той же карточки; текущий MapCenterRequest только двигает камеру |

## Координаты, формы и полный сценарий

**OBSERVED:** `+` включается при GPS reading, mapCenter и initialGpsCenterEstablished.
`requestCreateRecord` проверяет Territory, Observer и GPS, держит
ObservationPointCreationDraft в памяти. Это UI-подготовка: БД ObservationPoint
здесь не создаётся. Старый draft можно оставить входом chooser; для `Моя точка`
брать только захваченный Territory/контекст и не вызывать `toNewObservationPoint`.
`PhysicalObjectLocationSelection`/CreationTarget типизированы под HOLLOW/LOG_HIVE;
нужна дополнительная ветвь цели, а не новый GPS/map screen или PhysicalObjectType.

Первый GPS fix центрирует карту, ручное перемещение меняет camera target.
ОК берёт именно текущий target, не последний GPS. GPS readings не переписывают
выбранную координату сами. Recenter доступен в обычном режиме перед выбором;
в selection mode idle controls скрыты. Значит текущий GPS использовать можно:
recenter → `+` → `Моя точка` → при необходимости сдвиг → ОК.
Отдельного переключателя GPS/карта не нужно. Без GPS текущий `+` недоступен;
расширять это поведение для данного slice не предлагается.

**Создание:** `+` (BeeMap) → `Что создать?` (тот же chooser) → новая ветвь
`Моя точка` → существующий map selection → ОК → минимальная `Новая точка`
в текущей route-системе MainActivity/MainViewModel → validation → local transaction
и media activation → только после успеха закрытие формы.
Отмена selection очищает pending intent; Back формы использует тот же cancel/draft cleanup.

**Управление:** MapDataPanel → существующая `Мои точки` → новая вложенная страница
списка → название открывает новую минимальную карточку. Она показывает название,
непустую заметку, фото, координаты, `Показать на карте`, `Редактировать`, `Удалить`.
Последнее открывает подтверждение. Switch скрывает только marker. Нажатие marker
открывает эту же карточку напрямую, без промежуточной исследовательской preview-card.
Редактирование координат возвращается в тот же map selection и обратно в форму;
изменённое положение остаётся draft до общего Сохранить.

PhysicalObjectForm и ObjectCard сейчас private/доменнозависимы. Переиспользовать
малые presentational части (header/actions/media), добавив узкие point form/card.
Не добавлять десятки flags к HollowForm и не строить универсальный form framework.
Сохранить state формы/фото при переходе на карту; request ID потребляется один раз.
Реальный Back и fontScale1.7/IME потребуют проверки, а не выводятся из компиляции.

## Минимальная постоянная модель — PROPOSED

Две таблицы в существующей Room16, связь один→много фото после уточнения OWNER:

- `user_points`: `id UUID PK`, `territory_id UUID NOT NULL FK territories`,
  `title NOT NULL`, `note NULL`, `latitude/longitude NOT NULL`,
  `created_at/updated_at` (текущие Instant converters). Index по Territory.
- `user_point_photos`: `id UUID PK`, `user_point_id UUID NOT NULL FK user_points`,
  `relative_path`, nullable `original_file_name/mime_type`, `byte_size Long`,
  `sha256`, `created_at`; index по владельцу. Это тот же набор управляемых
  media metadata, **без UNIQUE(user_point_id)** и без нового ограничения числа фото.

Нет FK на ObservationPoint, Bee, FlightCycle, PhysicalObject или сезон.
Observer сохраняется prerequisite текущего UI; отдельная связь Observer для
новой сущности текущим набором свойств не требуется. Точки переживают сезоны:
в запросе списка только Territory, без research period/filter.
Название проверяется на непустоту, координаты — на конечность и диапазоны;
никаких новых уникальностей названия/координат или произвольных length limits.

Если baseline всё ещё16: явная добавочная migration16→17, регистрация entity/DAO,
экспорт17.json; старые таблицы не перестраивать, backfill не требуется.
Создание point+photo metadata — транзакция; updates сохраняют id/createdAt/Territory.
Фото имеют собственные UUID, пути формируются системно; внешние файлы не изменяются.
Предлагаемая замена — удалить/добавить фото существующими действиями; отдельного
атомарного replacePhoto API сейчас нет, и вводить его ради этой функции не требуется.
Draft activation rollback сохраняет прежние данные при ошибке активации.
Delete транзакционно удаляет свои photo rows и point, после commit очищает только
принадлежащие им managed bytes. Файловая система не часть SQL transaction:
использовать действующий rollback/cleanup lifecycle, не объявлять его crash-atomic.

FK Territory RESTRICT соответствует существующей защите удаления данных.
В [RoomTerritoryRepository.kt](../app/src/main/java/org/beesearch/app/data/repository/RoomTerritoryRepository.kt):70
добавить проверку наличия user points; она должна сработать до очистки coverage в
[TerritoryCoverageDeletion.kt](../app/src/main/java/org/beesearch/app/TerritoryCoverageDeletion.kt):11.
`clearObservationData` ([RoomObservationRepository.kt](../app/src/main/java/org/beesearch/app/data/repository/RoomObservationRepository.kt):271)
не должен удалять пользовательские точки/фото. Единый Backup/Restore остаётся позже;
текущий BackupDao не включает эти данные — не обещать их защиту, не менять сейчас кодеки.

## Фото и отображение — необходимые границы адаптации

Физические объекты уже имеют несколько фото, camera, picker, thumbnails, удаление,
draft activation. Picker берёт до20 items за одно открытие — это существующий
batch API, не новый общий лимит коллекции. Для нового маршрута исключить только
video. Подтверждён существующий Photo Picker, а не общий файловый chooser.
Чтобы обеспечить также выбор из произвольного DocumentsProvider, потребуется
узкий launcher стандартного OpenDocument(image/*) в том же действии добавления
фото, с передачей URI в существующий resolver→managed copy. Не использовать
GPX initial-folder contract и не хранить URI как единственный источник.

Общий MediaFileWriter уже стримит >16MiB, считает Long/SHA256, публикует после close.
PhotoPreviewDecoder использует ImageDecoder с target-size до декодирования и EXIF
orientation. PhysicalObjectMediaPreview применяет этот decoder; Crop у thumbnail
не равнозначен порче/обрезке оригинала. Большое фото — текущий ACTION_VIEW viewer.
Существующие [PhotoPreviewDecoderTest](../app/src/androidTest/java/org/beesearch/app/ui/media/PhotoPreviewDecoderTest.kt),
[LargeMediaIngestTest](../app/src/test/java/org/beesearch/app/data/media/LargeMediaIngestTest.kt)
покрывают8 EXIF orientations, portrait/исходные bytes и ingest >16MiB.
В этом исследовании тесты не запускались; новая feature этими тестами ещё не доказана.

Нельзя вставлять UserPoint photo в physical_object_media/observation attachments:
их FK ведут к другим владельцам. Минимальная адаптация — вынести/открыть ровно
существующие draft/activate/resolve операции с owner namespace, оставив прежние
пути и typed mapping физических объектов неизменными. Новый photo-row mapping
использует тот же writer и lifecycle; не копировать store как третий pipeline.
Для нового namespace добавить узкие entries в
[file_paths.xml](../app/src/main/res/xml/file_paths.xml), не открывать весь files-dir.

Для карты: отдельный set `user_points_visible_<territory UUID>` в том же DataStore,
по образцу GPX, но без записи в GPX key/VM. Предложение default — OFF как у GPX;
это предлагаемый пункт будущего MD, не уже реализованное поведение.
После успешной записи visibility обновлять presentation, при ошибке сохранить
старое состояние. Delete убирает ID из своего set; оставшиеся stale IDs не рендерить.

Технический прецедент для предлагаемого native варианта — GeoJSON Point + CircleLayer
по существующему singleton-примеру [GpxMapOverlay.kt](../app/src/main/java/org/beesearch/app/ui/map/GpxMapOverlay.kt):17.
Предложение: keys `user-point-<UUID>`, отдельные revisions и point/highlight layers;
соединить declarations с GPX в одном runtimeOverlays. Territory scope инвалидирует
старую подготовку, существующий session восстанавливает layers после style reload.
Не менять research фильтры, GPX keys или lifecycle.

Registry не содержит hit testing/semantics/selection. Повторно использовать
проекцию/48dp-target pattern SavedObjectMarkers для доступного hit target,
не рисуя второй marker поверх native glyph; callback несёт UserPoint ID.
Вариант `queryRenderedFeatures` тоже есть в установленном MapLibre, но сам по себе
не заменяет accessibility. Renderer пока PROPOSED: существующие research pins
Compose-owned, архитектура не объявляет SymbolLayer обязательным. У CircleLayer
нет image lifecycle; pin-глиф через native image потребует отдельной адаптации
session, которую не следует добавлять до утверждения конкретного marker reference.
Для show-on-map использовать существующий consumed MapCenterRequest плюс selected
UserPoint ID; после camera focus renderer выделяет ту же точку. SDK references в VM не хранить.

## Конкретные препятствия и минимальное устранение

| Подтверждённый факт | Влияние / минимальное действие | Blocker |
|---|---|---|
| Selection/route типизированы HOLLOW/LOG_HIVE | Дополнительная UserPoint ветвь назначения координат; старые ветви не менять | Локальная обязательная доработка |
| HollowForm требует исследовательские свойства | Узкая point form, общий presentation/media без Hollow validation | Локальная обязательная доработка |
| Media store/FK/FileProvider знают других владельцев | Свой photo FK/namespace через существующие операции; узкие provider paths | Обязательно до сохранения фото |
| Media UI использует Photo Picker, не общий OpenDocument | Для выбора именно из файлов добавить стандартный image-only launcher в тот же managed-copy path | Локальная доработка для файлового источника |
| GPX row toggle включает название и очищает switch semantics | Нельзя переиспользовать row целиком; отдельные action targets, GPX без изменений | Обязательно до списка |
| MapCenterRequest не выделяет ID, Registry не обрабатывает clicks | Identity selection + повторное использование hit-target/semantics pattern | Обязательно до карты |
| Territory delete сначала делает preflight, затем удаляет coverage | Включить UserPoint в preflight и FK, чтобы не потерять coverage при отказе | Обязательно для data safety |

Наблюдатель и лимит16MiB не являются препятствиями после уточнения OWNER и проверки кода.
Backup/H05 не превращаются в новый gate этой реализации.

## Mockup-first, этапы и оставшееся решение

Приложенная картинка находится вне checkout (`C:\СКАН\Создание пользовательской точки 20 окт. 2026 г., 09_36_32.png`),
путь существует. Это концепт, не утверждённая замена реальных экранов.
Локально есть утверждённые [GPX display PNG+MD](ui/mockups/gpx-display-v1.md)
и реальные Samsung [panel](../build/evidence/d106-gpx-display-samsung-20261019/main-panel-font17.png)/
[GPX list](../build/evidence/d106-gpx-filename-samsung-20261019/restart-list.png).
Новые screenshots chooser/location/«Новое дупло» в этом задании не снимались;
для этих трёх шагов основание — реальные UI sources, а не выдуманные локальные PNG.

1. **Уточнение UI-контракта:** компактный PNG+MD addendum chooser и новый
   user-point form/card/list reference с состояниями create/edit/empty/delete,
   несколькими фото и marker/highlight. Координатный экран — existing reference
   с указанием нового назначения callback, без нового GPS-экрана. На1.7 проверить
   scroll/IME/Back: chooser сейчас содержит обычный Column без прокрутки,
   поэтому увеличение числа строк требует проверки доступной высоты и при
   необходимости прокрутки тела. По действующему mockup-first получить OWNER approval; PNG сейчас не создавать.
2. **Данные и фото:** две таблицы, repository, добавочная migration и адаптация
   владельца в существующем media lifecycle. Tests:16→17/all-old-data preservation,
   Territory isolation/RESTRICT, research reset, add/remove photos и rollback,
   EXIF/portrait/>16MiB через существующие тестовые механизмы.
3. **Создание/карточка/список:** расширить chooser и shared coordinate selection,
   формы, карточку, переключатели; tests old three create actions unchanged,
   ОК/Cancel/Back/recreation без replay, title validation, фото persistence,
   independent switch/title actions и visibility после restart/A→B→A.
4. **Карта и qualification:** один Registry, point declarations/selection,
   show-on-map/marker→same card, style reload и stale async tests вместе с GPX;
   сборка/lint и отдельная разрешённая Samsung qualification на1.7, фото portrait,
   большие изображения, сохранность старых данных. Только после этого acceptance.

Один остающийся продуктовый выбор: **`Показать на карте` для выключенной точки
должно сохранять её switch ON или показывать временное выделение без изменения
сохранённой видимости?** Рекомендуется первый вариант: persist ON → focus+highlight,
с возможностью затем выключить switch. Ни текущий GPX, ни существующее camera-only
действие этот выбор не определяют. Остальные перечисленные OWNER свойства,
навигация, несколько фото и наличие Observer повторного решения не требуют.

## Первичные технические источники и проверка

Установлены Room2.8.4, Activity Compose1.8.0, MapLibre13.5.1 (libs.versions.toml).
Проверены стандартные API, до предложения custom решений:
[Room Migration](https://developer.android.com/reference/androidx/room/migration/Migration),
[Photo picker](https://developer.android.com/training/data-storage/shared/photo-picker),
[ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder),
[MapLibreMap source, tag android-v13.5.1](https://raw.githubusercontent.com/maplibre/maplibre-native/android-v13.5.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/maps/MapLibreMap.java).
Актуальная общая migration guide уже показывает Room3; её snippets/новый plugin
не переносятся в Room2.8.4 проекта. Новых dependencies не требуется.

Работа: read-only code/docs/call-chain inspection, три bounded read-only аудита.
APK/тесты/migrations/ADB не запускались; Samsung не менялся. Создан только этот
отчёт; прежние handoff и два Samsung reports сохранены без изменений, index пуст.
Нет реализации, новых PNG, checkout, staging, commit или push. Остановка здесь.
