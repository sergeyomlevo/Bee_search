# Bee Search — Architecture

## 1. Назначение документа

Этот документ описывает техническую архитектуру мобильного приложения **Bee Search**.

Он определяет:

* основные технологические решения;
* структуру приложения;
* ответственность компонентов;
* хранение данных;
* работу с картой;
* работу с GPS и компасом;
* offline-first подход;
* подготовку к будущей синхронизации;
* границы MVP.

Документ опирается на:

* `product-requirements.md`;
* `user-workflows.md`;
* `domain-model.md`;
* `data-model.md`.

Архитектура должна поддерживать реальную полевую работу и при этом не усложнять первую версию функциями, которые понадобятся только в будущем.

---

# 2. Основные архитектурные принципы

## 2.1. Offline-first

Мобильное приложение должно быть полностью работоспособно без подключения к интернету.

Локальное устройство является первичным местом записи данных во время полевой работы.

Все пользовательские действия сначала фиксируются локально.

Будущая серверная синхронизация является дополнительным уровнем и не должна становиться необходимой для выполнения наблюдений.

---

## 2.2. Локальная база является источником текущего состояния

Во время работы приложение определяет состояние наблюдения из локальной базы данных.

Например:

```text
Room / SQLite
    ↓
ObservationPoint
    ↓
Bee
    ↓
FlightCycle
    ↓
UI
```

Интерфейс не должен быть единственным местом хранения текущего состояния.

Если приложение будет закрыто или Android уничтожит процесс, состояние должно восстанавливаться из базы.

---

## 2.3. Минимум дублирования данных

Если значение можно надёжно вычислить из существующих данных, оно не должно храниться отдельно без необходимости.

Например:

```text
FlightCycle.duration
```

не хранится, а вычисляется:

```text
return_time - departure_time
```

Состояние Bee также определяется из её FlightCycle, а не дублируется отдельным полем состояния.

Пригодность первого FlightCycle для аналитических расчётов продолжительности тоже является производным доменным правилом: завершённый первый цикл короче 60 секунд исключается из таких расчётов без изменения или удаления исходной записи. Отдельный persisted-флаг для этого не вводится.

---

## 2.4. Разделение предметной и технической логики

Предметные правила не должны зависеть напрямую от Android UI, MapLibre, Room или конкретного сетевого API.

Например, правило:

> одна Bee не может иметь два одновременно открытых FlightCycle

должно быть представлено в бизнес-логике приложения, а не только блокировкой кнопки в интерфейсе.

---

## 2.5. Карта является компонентом приложения, а не основой модели данных

Карта используется для:

* выбора территории;
* загрузки офлайн-покрытия;
* создания ObservationPoint;
* ручной корректировки GPS;
* просмотра точек.

Предметные данные должны оставаться независимыми от конкретного картографического SDK.

Это позволит при необходимости заменить картографическую библиотеку без переработки всей модели приложения.

---

# 3. Целевая платформа

Первая версия разрабатывается для:

```text
Android
```

Основной язык:

```text
Kotlin
```

Минимальная версия Android проекта:

```text
Android 10
API 29
```

Пользовательский интерфейс:

```text
Jetpack Compose
```

---

# 4. Основной технологический стек

Предварительный стек MVP:

```text
Language
    Kotlin

UI
    Jetpack Compose
    Material 3

Architecture
    ViewModel
    StateFlow / Flow
    Repository
    Use cases where justified

Local database
    Room
    SQLite

Preferences
    DataStore

Map
    MapLibre Native for Android

Location
    Android Location APIs
    preferably Fused Location Provider where appropriate

Orientation / compass
    Android Sensor APIs

Concurrency
    Kotlin Coroutines

Navigation
    Navigation Compose

Dependency injection
    initially optional
```

Точный набор библиотек должен оставаться минимальным.

Новая зависимость добавляется только при наличии конкретной задачи.

---

# 5. Общая архитектура мобильного приложения

Предлагаемая структура:

```text
┌───────────────────────────────┐
│              UI               │
│ Jetpack Compose               │
│ Screens / components          │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│         Presentation          │
│ ViewModel                     │
│ UI state                      │
│ UI events                     │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│        Domain / Logic         │
│ Application rules             │
│ Operations                    │
│ Validation                    │
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│             Data              │
│ Repository                    │
│ Room                          │
│ DataStore                     │
│ map storage                   │
└───────────────────────────────┘
```

Не требуется создавать чрезмерно сложную Clean Architecture с большим количеством пустых интерфейсов и слоёв.

Цель разделения — сделать код понятным и тестируемым, а не увеличить количество файлов.

Bee Search остаётся одним Gradle-модулем `:app`, пока размер проекта и его build graph не обосновывают отдельные модульные границы. Внутри него файлы и пакеты организуются вокруг связных feature- и layer-responsibilities; количество строк запускает review, но не является ограничением. UI не должен поглощать persistence, filesystem, network, archive или platform infrastructure concerns, domain остаётся независимым от Android, а новые пакеты или Gradle-модули вводятся только при наличии устойчивой dependency- или testability-границы.

---

# 6. Предлагаемая структура Android-кода

Предварительно:

```text
app/src/main/java/org/beesearch/app/

    MainActivity.kt

    ui/
        navigation/
        screens/
            territory/
            map/
            point/
            observation/
            history/
            settings/
        components/
        theme/

    domain/
        model/
        usecase/

    data/
        local/
            database/
            entity/
            dao/
        repository/
        preferences/

    map/
        MapController

    location/
        LocationProvider

    sensors/
        HeadingProvider

    util/
```

Эта структура является ориентиром и может уточняться по мере реализации.

Не следует заранее создавать пустые каталоги и классы только потому, что они присутствуют в архитектурной схеме.

---

# 7. UI layer

UI реализуется на Jetpack Compose.

Основная задача UI:

* показывать текущее состояние;
* принимать пользовательские действия;
* передавать события во ViewModel;
* не содержать критическую предметную логику.

Например:

```text
кнопка "Вернулась"
        ↓
ViewModel
        ↓
domain operation
        ↓
repository
        ↓
Room transaction
        ↓
Flow
        ↓
обновлённый UI
```

---

# 8. Presentation layer

Для основных экранов используются ViewModel.

ViewModel отвечает за:

* загрузку данных;
* объединение нескольких потоков данных;
* создание UI state;
* обработку пользовательских событий;
* вызов предметных операций;
* отображение ошибок.

ViewModel не должна напрямую выполнять SQL или работать с Android SensorManager.

---

# 9. UI state

Состояние экрана должно представляться отдельной моделью.

Например, рабочий экран точки может иметь:

```text
ObservationUiState

point
activeBees
completedBees
currentTime
isLoading
error
```

Каждая карточка Bee может получать уже подготовленное состояние:

```text
BeeUiState

beeId
displayMark
state
departureTime
elapsedTime
action
azimuth
```

UI не должен самостоятельно реконструировать бизнес-состояние из набора сырых SQL-полей.

---

# 10. Domain layer

Domain layer содержит правила приложения, которые не должны зависеть от конкретного UI.

Примеры операций:

```text
CreateTerritory
CreateObservationPoint
StartFirstFlight
RegisterBeeReturn
StartNextFlight
SetFlightAzimuth
RemoveFlightAzimuth
CompleteObservationPoint
RecordNoBeesFound
```

Не обязательно создавать отдельный класс UseCase для каждой простой операции.

Use case вводится там, где существует реальное предметное правило, транзакция или сложная последовательность действий.

---

# 11. Критические предметные операции

## 11.1. Создание ObservationPoint и результат присутствия пчёл

Создание исследовательской точки происходит при подтверждении координат и
является транзакционной операцией. Внутри одной Room transaction
приложение проверяет ссылки на Territory и Observer, определяет локальный
`observation_year`, получает следующий `point_number` в области `Territory +
observation_year + observer_id` и вставляет ObservationPoint. Составной UNIQUE
index остаётся окончательной защитой от совпадения номера. Подтверждённые
координаты до этого существуют только в transient draft.

`created_at` сохраняется как абсолютный Instant; локальная временная зона используется только для однократного назначения `observation_year`.

Подтверждение подготовленной точки сохраняет `ObservationPoint` с
`bee_presence_result = null` одной транзакцией и открывает наблюдение. Bee при
этом не создаются. Явный результат `NO_BEES_FOUND` создаёт ObservationPoint и
завершает её одной транзакцией.

## 11.2. Регистрация первого вылета

Это транзакционная операция.

Алгоритм:

```text
проверить, что точка активна и результат присутствия не NO_BEES_FOUND
        ↓
проверить, что реальных Bee меньше 10
        ↓
проверить, что выбранная метка ещё не используется
        ↓
получить текущее индивидуальное время вылета
        ↓
создать Bee с выбранной меткой
        ↓
создать её FlightCycle sequence_number = 1
        ↓
установить BEES_FOUND, если он ещё не установлен
        ↓
записать все изменения одной Room transaction
```

Если операция завершается ошибкой, не должна сохраниться ни Bee без первого
цикла, ни первый цикл без Bee.

Локальная отмена ошибочно зарегистрированного первого вылета также является
одной транзакцией: удаляются первый цикл и сама Bee, а `bee_presence_result`
возвращается к `null`, если на точке не осталось ни одной Bee.

---

## 11.3. Возвращение Bee

Операция:

```text
найти текущий открытый FlightCycle
        ↓
проверить, что он существует
        ↓
получить текущее время
        ↓
записать return_time
```

---

## 11.4. Повторный вылет

Перед созданием нового цикла необходимо проверить:

* Bee существует;
* предыдущий цикл завершён;
* ObservationPoint ещё активна.

После этого:

```text
next sequence_number
departure_time = current time
```

---

# 12. Local database

Для основной локальной базы предлагается:

```text
Room
```

Room предоставляет:

* SQLite;
* compile-time проверку запросов;
* DAO;
* транзакции;
* миграции;
* Flow;
* удобную интеграцию с Kotlin.

---

# 13. Основные таблицы

На основании `data-model.md`:

```text
TerritoryEntity
ObserverEntity
ObservationPointEntity
BeeEntity
FlightCycleEntity
```

`ObserverEntity` хранится отдельно; `ObservationPointEntity` ссылается на неё
по `observer_id`.

---

# 14. Room relations

Связи:

```text
Territory
1
│
N
ObservationPoint
1
│
N
Bee
1
│
N
FlightCycle
```

Foreign key должны использовать UUID.

---

# 15. UUID

UUID создаётся на устройстве до записи данных.

Не следует использовать серверный ID или SQLite autoincrement как основной идентификатор предметных объектов.

В Kotlin используется:

```text
UUID
```

В первой Room-схеме UUID хранится как каноническая строка `UUID.toString()` в колонке SQLite `TEXT` и преобразуется общим Room `TypeConverter`.

---

# 16. Room transactions

Room transaction обязательна как минимум для:

* подтверждения точки наблюдения;
* регистрации первого вылета (Bee + FlightCycle 1 + `BEES_FOUND`);
* локальной отмены ошибочно зарегистрированного первого вылета;
* потенциально сложных удалений;
* операций, изменяющих несколько связанных записей.

Простая регистрация одного return_time может быть одной атомарной SQL-операцией.

В Room schema v5 внешние ключи Territory/Observer, составные уникальности метки
Bee, номера FlightCycle и номера ObservationPoint обеспечиваются ограничениями
 SQLite. Правила «одна активная ObservationPoint» и «один открытый FlightCycle на
 Bee» проверяются внутри транзакционных методов Repository; DAO остаются
 внутренней деталью слоя хранения. Создание точки с первым результатом,
 изменение результата присутствия пчёл и связанные записи Bee выполняются
 транзакционно.

---

# 17. Reactive data flow

DAO должны по возможности предоставлять:

```text
Flow<T>
```

Например:

```text
observeActivePoint()
observeBees(pointId)
observeFlightCycles(beeId)
```

Изменение базы автоматически обновляет ViewModel и Compose UI.

---

# 18. DataStore

Для небольших локальных настроек используется:

```text
Preferences DataStore
```

Минимально:

```text
current_territory_id?
current_observer_id?
```

На чистой установке оба current UUID отсутствуют. DataStore не создаёт
фиктивных значений. Сохранённые IDs, которые больше не соответствуют entity,
считаются invalid; карта остаётся доступной, а действия, требующие контекста,
предлагают открыть Settings (D062).

UUID selection записывается в DataStore только после выбора существующей
сущности. DataStore и Room не объединяются в искусственную общую транзакцию:
ошибка DataStore запрещает создание точки, а успешная запись DataStore не
откатывается при последующей ошибке Room.

Позднее могут появиться:

```text
last_map_position
map_ui_settings
sync_settings
```

DataStore не должен использоваться вместо Room для исследовательских данных.

---

# 19. Карта

Предпочтительный картографический движок:

```text
MapLibre Native for Android
```

Причины:

* открытый исходный код;
* возможность встроить карту непосредственно в собственное приложение;
* контроль интерфейса;
* поддержка векторных карт;
* работа с собственными источниками;
* возможность построить offline-first архитектуру;
* отсутствие зависимости предметной модели от готового навигационного приложения.

---

# 20. Абстракция карты

Код приложения не должен быть полностью связан с MapLibre API.

Желательно иметь небольшой внутренний слой:

```text
MapController
```

или аналогичный компонент.

Он отвечает за операции вроде:

```text
showCurrentPosition()
showObservationPoints()
showTemporaryPoint()
moveTemporaryPoint()
showOfflineMapPackage()
```

Не требуется строить сложный универсальный GIS-framework.

Цель — локализовать зависимость от MapLibre.

---

# 21. Офлайн-карты

Bee Search использует MapLibre для online-карты и для активированного локального
PMTiles Map Package. Offline package не является обязательным условием
существования Territory или полевой работы.

Архитектура должна поддерживать:

```text
Territory.id
        ↓
device-local offline coverage metadata
        ↓
validated active PMTiles Map Package in app-controlled storage
        ↓
MapLibre local rendering without network fallback
```

Ambient cache, внешний URI и partially staged file не являются доказательством
readiness. Package считается Ready только после validation совместимости и
целостности и атомарной активации.

Сами картографические данные и metadata не хранятся в Room research schema.
`Territory` не содержит `map_status`, `map_region_data` или bounds.
У разных Territory может быть разный coverage; rectangles остаются map
infrastructure и не становятся Room entities.
Желаемое coverage хранится device-local в Preferences DataStore по ключу
`Territory.id` в versioned формате: `v1` — прежний безымянный набор участков, `v2` —
именованный Ареал (D081). Это не downloaded resources и не Room research data.
Редактирование использует working copy и заменяет участки только после Done, а смена
Territory загружает только её собственный Ареал. Создание Ареала, переименование,
изменение участков и удаление идут через явные действия пользователя и один
store-контракт, а UI-состояние редактора в хранилище не попадает (D082).
При успешном удалении неиспользуемой Territory её coverage key очищается; если
удаление заблокировано существующим ObservationPoint, coverage не изменяется.

Сохранённый Ареал имеет отдельный переносимый файл в папке обмена (D083). Граница
ответственности здесь делится на три части: canonical Ареал остаётся в DataStore;
зеркало файла (`AreaExchangeMirror`) только записывает, проверяет и удаляет один
известный managed-файл и прикреплено к store, поэтому зеркалирование нельзя забыть в
экране; транспорт (`AreaTransport`) означает «передать актуальный файл Ареала» и не
знает ни получателя, ни способа доставки. Текущий транспорт — стандартный Android
Share Sheet с временным `content://` read grant, будущий может быть серверной
отправкой без изменения Area JSON и canonical-модели. Папка обмена остаётся
пользовательским местом обмена, а не хранилищем: ошибка записи зеркала не откатывает
canonical сохранение, а содержимое файла всегда формируется из canonical Ареала.

---

# 22. Граница offline-map infrastructure

Небольшая техническая граница Map Package должна отвечать за:

* выбор области карты;
* acquisition через отдельный adapter;
* staging в app-controlled storage;
* validation совместимости и целостности;
* атомарную активацию;
* безопасную замену или удаление картографического пакета;
* предоставление device-local package/coverage state для UI.

Предметная модель не должна знать формат файлов карты.

Первый implementation использует небольшой device-local Map Package store:
стабильный ключ active package хранится в Preferences DataStore по
`Territory.id`, а staged и immutable active artifacts — в app-controlled private
storage. Это не generic provider framework и не Room-модель. Ручной Android
Files picker является acquisition adapter текущего milestone; future downloader
должен подать тот же manifest/PMTiles contract на границу staging/validation.

Каждый активированный package имеет достаточно identity/version metadata, чтобы
отличить установленный artifact от совместимой замены и проверить MapProfile.
D065 фиксирует переносимый Map Package contract v1: immutable PMTiles artifact
и versioned sidecar JSON manifest рядом с ним. Device-local installation and
activation state по-прежнему не является частью manifest и не хранится в Room.

Coverage отображается app-generated GeoJSON overlay поверх обычной карты. Для
первого milestone достаточно границ Ready, Downloading/Incomplete, Failed и
currently selected rectangles. Несколько regions можно показывать отдельно без
сложного polygon union manager.

Map/network/offline failures изолированы от Room research workflow. Они не
удаляют Territory или связанные наблюдения, не отменяют ObservationPoint и не
блокируют локальную запись GPS/time/azimuth events.

---

# 23. Формат офлайн-карт

Offline vector Map Package использует PMTiles согласно D063. MapLibre Android
читает активированный package напрямую из app-controlled private storage без
локального HTTP-сервера.

```text
acquire
→ stage in app-controlled storage
→ validate compatibility and integrity
→ atomically activate
→ render through MapLibre
```

Отдельный copy step не является архитектурным инвариантом. Manual import и
future server download различаются acquisition adapter, но используют один
installation/validation/activation boundary. Renderer получает только локальный
активный package, а не внешний document URI или partially staged artifact.

Пользователь формирует device-local offline coverage из одного или нескольких
rectangle fragments. Каждый фрагмент может первоначально соответствовать
viewport и затем добавляться после pan/zoom карты. Составное coverage является
объединением фрагментов; оно не превращается автоматически в общий bounding
rectangle. Фрагменты могут перекрываться и должны быть видимы поверх карты до
начала acquisition. Bounds остаются device-local map coverage data, не
становятся Territory data и не копируются в Room.

Replacement не изменяет активный package in place. Старая версия сохраняется,
пока новый artifact не прошёл validation и не был атомарно активирован. Ошибка
acquisition/staging/validation не удаляет существующую Ready-карту. Renderer
получает только active validated package текущей Territory; он не читает PoC
fixture, внешний document URI или staging directory. Конкретные download,
resume, quota и cleanup остаются отдельными решениями; static manifest
validation определена D065.

## 23.1. Map Package contract v1

Contract v1 хранит metadata в sidecar JSON manifest рядом с immutable PMTiles,
а не во внутреннем metadata block архива. Manifest называет PMTiles только
переносимым basename; он не содержит workstation path, document URI или
app-private installation path. Его обязательные поля перечислены в D065:
schema/package identity, profile/style compatibility, declared coverage
fragments, zoom range, filename, byte length и SHA-256.

`coverageFragments` описывает фактическое заявленное package coverage, а не
`Territory boundary` и не заменяет device-local desired coverage. Общий bbox в
PMTiles header может подтвердить лишь внешний envelope. Поэтому v1 проверяет
каждый desired rectangle консервативно: он должен целиком входить хотя бы в один
declared fragment; частичное покрытие или покрытие только объединением
нескольких fragments не даёт Ready для нового desired coverage. Generator
должен выпускать fragments из того же coverage plan, что использован для
создания artifact.

При изменении desired coverage активный package не удаляется. Приложение заново
сравнивает её с manifest: если все desired fragments всё ещё покрыты, package
остаётся Ready; иначе он остаётся доступной картой предыдущего coverage, но не
доказывает Offline Ready для нового desired coverage до validated replacement.

---

# 24. Источник карты

Картографический движок и источник карт — разные вещи.

MapLibre не предоставляет готовые карты автоматически.

Основной источник контролируется Bee Search и строится как:

```text
Raw OSM
→ Planetiler / OpenMapTiles-compatible generation
→ minimal Bee_search field profile
→ versioned MVT inside PMTiles Map Package
→ acquisition-independent delivery
→ app-controlled local storage
→ MapLibre
```

Stateful tile server и конкретный cloud/CDN vendor не являются обязательными.
Package и MapProfile обеспечивают совместимость MVT schema, style/resources,
glyphs, attribution/license и zoom contract. Online source может использовать
отдельный совместимый transport.

Стандартные OpenMapTiles-compatible layers используются для roads,
tracks/paths, waterways, landcover, buildings, settlements, railway и bridges.
Field extension сохраняет `man_made=cutline`, `power=line/minor_line`,
`tracktype` и surface detail beyond generic paved/unpaved. Окончательная
нормализация surface categories уточняется implementation PoC.

Vector source и offline maxzoom равны `15`. UI может увеличивать карту до
`20`, используя vector overscaling на z16–20. Actual source z16 допускается
технически только после изменения pinned Planetiler/profile: текущий Planetiler
0.10.0 и все feature definitions `field-profile.yml` ограничены z15. Owner
decision D092 сохраняет этот pipeline без изменений. Будущий high-resolution
raster сначала проверяется с существующим z15 vector overzoom; настоящий vector
z16–18 рассматривается только при доказанном недостатке detail.

Один versioned field MapProfile задаёт совместимость schema/profile, dataset
snapshot, style/resources, zoom contract и attribution. Новая несовместимая
версия получает новую package identity/version; старый активный package не
удаляется и не становится неинтерпретируемым молча.

Satellite остаётся optional: при разрешении provider он может работать online
независимо от vector preparation и позднее использовать отдельный offline
package с собственными bounds/zoom/profile/lifecycle. Satellite readiness не
входит в readiness основной vector map. Формат и provider satellite package не
выбраны. Contours, hillshade и DEM отложены.

DEV PoC D092 подтвердил на Samsung независимую композицию Sentinel raster,
выбранных vector lines/symbols и Bee Search overlays. Принятый research package
имеет реальные raster levels z10-z13; Sentinel и этот Sentinel-based Hybrid
ограничены UI z13. Это reference implementation, а не production package
lifecycle. Raster и vector остаются отдельными sources: будущая imagery не
должна запекать дороги и labels в PNG.

Следующий отдельный architecture task — source-neutral preparation/package
workflow для georeferenced raster imagery с validation, reprojection/mosaic,
пирамидой реальных уровней согласно resolution, raster PMTiles и проверяемым
metadata/manifest. Production import/acquisition, package management и final
compatibility contract пока не определены. GPS, crosshair, ObservationPoints и
app-generated research overlays не зависят от выбранной base-map composition; механизм отображения
overlays и layers принят в разделе 73.3 (D102).

---

# 25. GPS

Работа с местоположением должна быть вынесена в компонент:

```text
LocationProvider
```

Он отвечает за:

* получение текущей позиции;
* получение accuracy;
* обновление позиции;
* обработку отсутствия разрешения;
* обработку отсутствия GPS.

UI получает уже структурированные данные положения.

На текущем MapLibre/GPS milestone Android-реализация использует системный
`LocationManager` за этой границей. Обновления запрашиваются только пока
рабочий экран карты открыт; background location не используется.
При внутренней навигации внутри запущенного приложения последнее состояние `Available`
сохраняется в activity-scoped
ViewModel, поэтому возврат на карту не изображает уже полученный fix как потерянный, пока
возобновлённая foreground-подписка ожидает следующий callback. Сама подписка вне карты
по-прежнему остановлена; уход приложения из foreground переводит UI в обычное ожидание нового
fix.

---

# 26. Location data

Минимальная модель результата:

```text
LocationReading

latitude
longitude
accuracyMeters
timestamp
```

При создании точки исходное значение может быть сохранено в ObservationPoint как GPS measurement.

---

# 27. Создание точки

Предполагаемый поток:

```text
GPS position → Map screen
        ↓
пользователь корректирует карту под красным прицелом
        ↓
крестик фиксирует текущий map center
        ↓
chooser типа записи
        ↓
`Точка наблюдения`
        ↓
current Territory и current Observer валидны?
        │
       нет → chooser закрывается; карта показывает сообщение и предлагает Settings
        ↓
transient preparation draft с зафиксированными coordinates,
original GPS, immutable territory_id + observer_id
        ↓
`Добавить` → ObservationPoint с nullable result
        │
        └── `Пчёлы отсутствуют` → ObservationPoint + NO_BEES_FOUND + completed_at
```

Открытие или отмена chooser не записывает данные. После выбора типа дальнейшие
GPS updates не меняют зафиксированные coordinates. Close или system Back из
preparation отбрасывают draft без записи в Room. Persistence и атомарность
дальнейшего Observation workflow определяются D075.

Если сохранение кода не удалось, поток не доходит до Room. Если транзакция
первого результата не удалась после успешной записи кода, код остаётся в
DataStore для повторной попытки.

GPS-позиция и итоговый marker должны оставаться различимыми.

---

# 28. Разрешения Android

Приложению потребуется разрешение на местоположение.

Необходимо запрашивать только реально необходимые permissions.

Для MVP не следует автоматически добавлять:

* background location;
* постоянный доступ к местоположению;
* лишние файловые permissions.

Если работа с GPS выполняется только при открытом приложении, foreground location достаточно.

---

# 29. Компас и ориентация устройства

Азимут реализуется через отдельный компонент:

```text
HeadingProvider
```

Он отвечает за работу с Android Sensor APIs.

---

# 30. HeadingProvider

Предполагаемый интерфейс предоставляет:

```text
currentHeading
sensorAccuracy
timestamp
```

UI не должен напрямую обращаться к SensorManager.

Текущая реализация создаёт один lifecycle-aware Flow для активного observation screen. Listener
регистрируется только пока Flow собирается и снимается при уходе экрана из composition или остановке
lifecycle. Все незаписанные карточки используют одно общее текущее направление.

---

# 31. Расчёт азимута

При реализации необходимо использовать рекомендованный Android способ вычисления ориентации устройства на основе доступных датчиков.

Не следует предполагать, что простое чтение одного магнитометра автоматически даёт надёжный азимут.

Расчёт должен учитывать:

* магнитометр;
* акселерометр / rotation vector при наличии;
* ориентацию устройства;
* качество датчиков.

`TYPE_ROTATION_VECTOR` является основным источником; при его отсутствии может использоваться
`TYPE_GEOMAGNETIC_ROTATION_VECTOR`. Матрица переориентируется согласно display rotation так, чтобы
направлением всегда оставалась верхняя короткая сторона экрана.

Полученный магнитный heading корректируется через `GeomagneticField` по подтверждённым координатам
ObservationPoint, текущему системному timestamp каждого вычисления и высоте `0 м`. После прибавления
declination результат нормализуется в `[0, 360)` и округляется для компактного полевого UI.

---

# 32. Направление телефона

До реализации необходимо точно определить физическое соглашение:

> какая сторона телефона считается направлением измерения.

Например:

```text
верхняя короткая сторона телефона
```

Это должно быть одинаково:

* в коде;
* в интерфейсе;
* в инструкции пользователю.

---

# 33. Азимут не должен блокировать вылет

Регистрация времени вылета имеет более высокий приоритет.

Архитектура должна позволять:

```text
создать FlightCycle
        ↓
позже установить azimuth_deg
```

или оставить его `null`.

---

# 34. Качество компаса

Если Android сообщает низкую точность датчика, HeadingProvider может передавать это в UI.

Например:

```text
HIGH
MEDIUM
LOW
UNRELIABLE
```

Это может использоваться для предупреждения пользователя.

Азимут всё равно остаётся необязательным.

---

# 35. Текущее время

Для предметных событий должен существовать единый источник времени приложения.

Все сохраняемые моменты представлены как абсолютный `Instant`, а в SQLite хранятся как Unix epoch milliseconds.

Часовой пояс отдельно не сохраняется. UI преобразует `Instant` для отображения с использованием текущего часового пояса Android-устройства.

Желательно не размазывать вызовы системных часов по UI-компонентам.

Можно использовать небольшой компонент:

```text
Clock
```

или инъецируемый источник времени.

Persistence-слой использует инъецируемый `Clock`. При создании изменяемой записи `created_at` и `updated_at` получают один `Instant`; при изменении `FlightCycle` его `updated_at` получает время соответствующей сохранённой операции. При изменении `Territory` её `updated_at` получает текущее значение `Clock`.

Это упростит тестирование:

```text
StartFirstFlight
RegisterBeeReturn
StartNextFlight
```

---

# 36. Таймеры на экране

Для отображения:

```text
в полёте 06:42
```

не нужно постоянно обновлять базу.

UI периодически вычисляет:

```text
current time - departure_time
```

Исходным значением остаётся сохранённый `departure_time`.

---

# 37. Навигация

Предварительные основные экраны:

```text
Startup
TerritoryManagement
TerritoryCreate
OfflineMapDownload
Map
PointPreparation
Observation
PointSummary
Settings
```

Не все экраны обязательно должны быть отдельными route.

UX будет уточняться при прототипировании.

---

# 38. Старт приложения

Логика запуска:

```text
Есть активная ObservationPoint?
        │
       да
        ↓
предложить продолжить
        │
       нет
        ↓
Есть valid current_territory_id и current_observer_id?
        │
       да
        ↓
открыть её карту
        │
       нет
        ↓
настройка территории
```

---

# 39. Рабочий экран Observation

Это критически важный экран.

Архитектура должна позволять ему получать одним агрегированным потоком:

```text
ObservationPoint
+ Bees
+ latest FlightCycle per Bee
+ history if required
```

Следует избегать десятков независимых запросов Room на каждую карточку.

---

# 40. Сортировка Bee

Сортировка является Presentation-логикой.

Например, можно отображать:

```text
сначала Bee в полёте
затем вернувшиеся
```

Однако сортировка UI не должна изменять идентификаторы или историю данных.

---

# 41. Цветовые метки

Фактическое значение цвета хранится независимо от визуального оформления Compose.

Например:

```text
mark_color = "WHITE"
```

UI уже решает, как визуально показать этот цвет.

Нельзя хранить Android Color integer как предметное значение метки.

---

# 42. Автоматическое сохранение

Приложение не должно иметь модель:

```text
заполнить всё
        ↓
нажать Сохранить
```

Каждое событие записывается сразу.

Это означает, что ViewModel после предметной операции ожидает успешное сохранение в Repository.

---

# 43. Ошибки записи

Если критическое событие не удалось сохранить в Room, UI должен явно сообщить об этом.

Нельзя показывать пользователю состояние:

```text
Вернулась
```

если return_time фактически не записан в базу.

---

# 44. Работа процесса Android

Приложение не должно рассчитывать, что Activity или ViewModel будут существовать непрерывно.

Android может уничтожить процесс.

Поэтому:

* критические данные хранятся в Room;
* текущая территория хранится в DataStore;
* таймеры вычисляются из timestamps;
* UI восстанавливается из persistent state.

---

# 45. Background services

В MVP постоянный background service не требуется.

Нет необходимости поддерживать работающий секундомер в фоне.

Если Bee улетела в 09:34 и приложение было закрыто, после открытия в 09:45 продолжительность определяется из timestamps.

---

# 46. Repository layer

Repository скрывает детали хранения от Presentation / Domain.

Например:

```text
TerritoryRepository
ObservationRepository
SettingsRepository
```

Не обязательно создавать отдельный Repository на каждую SQL-таблицу.

Границы Repository следует определять по реальным операциям приложения.

---

# 47. DAO layer

DAO отвечает только за работу с Room.

Например:

```text
TerritoryDao
ObservationPointDao
BeeDao
FlightCycleDao
```

DAO не должен содержать пользовательские сценарии или UI-логику.

---

# 48. DTO

В локальном MVP отдельные DTO между каждым внутренним слоем могут быть избыточны.

Не следует создавать:

```text
BeeEntity
BeeDto
BeeDomain
BeeUi
```

без реальной необходимости.

Разделение моделей следует добавлять там, где оно действительно предотвращает зависимость или упрощает код.

---

# 49. Dependency Injection

На раннем этапе можно использовать простую явную сборку зависимостей.

Например:

```text
AppContainer
```

Если количество компонентов существенно вырастет, можно рассмотреть:

```text
Hilt
```

Hilt не является обязательным требованием MVP.

Не следует подключать DI-framework только ради формального соответствия архитектурному шаблону.

---

# 50. Тестирование

Архитектура должна позволять тестировать предметные правила без запуска Android UI.

Особенно важны тесты:

* индивидуальная регистрация первого вылета;
* индивидуальное departure_time для первого цикла каждой Bee;
* невозможность второго открытого цикла;
* расчёт sequence_number;
* лимит 10 реальных Bee на точку;
* регистрация return_time;
* удаление азимута;
* допустимость null azimuth;
* завершение точки с невозвратившейся Bee.

---

# 51. Room tests

Необходимы тесты ограничений базы:

```text
Bee mark uniqueness
foreign keys
transactions
queries
migration
```

Особенно важно протестировать транзакцию регистрации первого вылета: Bee и её
первый FlightCycle должны появляться вместе.

---

# 52. UI tests

UI-тесты следует использовать для ключевых сценариев, а не пытаться покрыть ими каждую кнопку.

Главный поток:

```text
создать точку
→ УЛЕТЕЛА для выбранной метки
→ вернуть произвольную Bee
→ повторный выпуск
→ завершить наблюдение
```

---

# 53. Реальный телефон

Некоторые функции невозможно полноценно проверить только unit-тестами и эмулятором.

Реальный телефон необходим для проверки:

* GPS;
* accuracy;
* MapLibre;
* офлайн-карт;
* компаса;
* ориентации устройства;
* работы на солнце;
* удобства интерфейса;
* скорости регистрации событий.

---

# 54. Логирование

Во время разработки следует использовать структурированное техническое логирование.

Логи могут включать:

```text
создание точки
создание FlightCycle
GPS accuracy
sensor accuracy
ошибки карты
ошибки Room
```

Не следует логировать больше персональных или исследовательских данных, чем необходимо для диагностики.

---

# 55. Экспорт

Экспорт не является центральным компонентом архитектуры MVP.

Позднее может появиться:

```text
ExportService
```

который получает данные из Repository и формирует:

```text
CSV
GeoJSON
GPX
```

Экспорт не должен читать внутренние SQLite-файлы напрямую.

---

# 56. Будущая серверная архитектура

Сервер не входит в MVP.

Подробное, пока не принятое архитектурное предложение по ownership данных,
SyncEngine, conflict policy, map-package service и минимальному server stack
находится в `server-sync-architecture.md`. Оно не изменяет статусы решений в
`decisions.md` до отдельного review и утверждения.

Предполагаемая будущая схема:

```text
Android app
     │
     │ sync API
     ▼
Server
     │
     ▼
PostgreSQL
     │
     ├── mobile synchronization
     └── PC / Web interface
```

---

# 57. Сервер не является источником истины во время полевой работы

Даже после появления сервера мобильное приложение должно продолжать работать offline-first.

Схема:

```text
User action
    ↓
local Room write
    ↓
user continues working
    ↓
sync later
```

Не:

```text
User action
    ↓
wait for server
    ↓
save
```

---

# 58. Sync layer

В будущем между Repository и сервером появится:

```text
SyncEngine
```

Он должен отвечать за:

* отправку локальных изменений;
* получение удалённых изменений;
* конфликты;
* повторные попытки;
* состояние синхронизации.

Но этот слой не реализуется до появления конкретных требований к серверу.

---

# 59. Подготовка локальной модели к синхронизации

Уже в MVP необходимо:

* использовать UUID;
* хранить timestamps как абсолютный `Instant` / Unix epoch milliseconds;
* не полагаться на autoincrement ID;
* хранить immutable Territory/Observer UUID-связи в точке;
* хранить UUID как identity независимо от изменяемого `point_number`;
* не связывать данные с локальным порядком строк.

Локальные совпадения `point_number` между двумя будущими устройствами одного наблюдателя допустимы: синхронизация сможет перенумеровать отображаемую последовательность, не меняя UUID и исследовательские данные. Распределённый генератор номеров в MVP не создаётся.

Не требуется пока добавлять:

```text
server_id
sync_status
sync_version
```

---

# 60. Future PC interface

ПК-интерфейс не является Android-компонентом.

В будущем предпочтительно отдельное web-приложение, использующее серверные данные.

Его задачи:

* просмотр территорий;
* просмотр карты;
* сравнение ObservationPoint;
* просмотр FlightCycle;
* аналитика;
* визуализация азимутов;
* поиск вероятного гнезда.

Мобильный UI не должен пытаться одновременно быть полноценным настольным аналитическим интерфейсом.

---

# 61. Безопасность данных

Для MVP основная задача — избежать случайной потери наблюдений.

Необходимо:

* записывать события сразу;
* использовать транзакции;
* не хранить единственную копию текущих данных только в памяти;
* предусмотреть возможность будущего резервного копирования.

Полная система авторизации и серверной безопасности проектируется позже.

---

# 62. Миграции базы

После начала реального использования приложения изменение структуры Room должно выполняться через миграции.

Не следует использовать destructive migration для рабочей базы с полевыми наблюдениями.

На самом раннем этапе разработки до появления реальных данных допустим только
явно документированный controlled reset конкретной migration.

Момент перехода к обязательным миграциям должен быть явно зафиксирован перед первым реальным полевым использованием.

Room schema v2 вводится явной миграцией из v1. Для прототипных ObservationPoint миграция сохраняет UUID и все строки, вычисляет `observation_year` из `created_at` в текущей локальной временной зоне устройства и назначает `point_number` по порядку `created_at`, затем UUID внутри каждой области. Историческая временная зона v1 не восстанавливается и не изобретается.

Точки v1 с Bee получают `BEES_FOUND`; точки без Bee получают `null`. `NO_BEES_FOUND` миграция не назначает автоматически.

Room schema v3 вводится явной migration 2 → 3. Она добавляет persisted
`flight_cycles.azimuth_capture_consumed`, устанавливает его для старых строк по наличию
`azimuth_deg` и сохраняет все существующие исследования. Полный путь 1 → 2 → 3 проверяется migration
test.

Room schema v4 использует неструктурную compatibility migration 3 → 4. Она предназначена для
финальной v3-структуры, которую промежуточная debug-сборка успела открыть с прежним Room identity
hash: пользовательские таблицы не пересоздаются и не изменяются, а после стандартной Room schema
validation записывается актуальный identity hash. Проверяются отдельный путь 3 → 4, повторное
открытие v4 и полный путь 1 → 2 → 3 → 4.

Room schema v5 вводит Observer, required region/district Territory и
`ObservationPoint.observer_id`. Migration 4 → 5 очищает только согласованные
тестовые Territory, ObservationPoint, Bee и FlightCycle: старые записи нельзя
честно преобразовать без фиктивных персональных и географических данных. Это
не fallback policy; после v5 migrations для реальных данных non-destructive по
умолчанию.

---

# 63. Версионирование схемы

Room database должна иметь явную версию схемы.

Изменения модели данных после начала эксплуатации должны:

1. изменять версию;
2. иметь migration;
3. иметь тест migration;
4. не уничтожать существующие наблюдения.

---

# 64. Резервное копирование

Repository V1 foundation (`data/backuprepository`) отделён от Complete Backup:
startup materializes variant-isolated Backup skeleton; explicit SAF maintenance initializes
UUID identity and strongly verifies one immutable flat SHA blob through owned Staging and
same-storage move. Capacity guard uses a configurable 20 GiB reserve. Slice 2B adds an explicit
METADATA_ONLY immutable snapshot service (transactional Room capture, settings consistency,
raw-byte digests, bounded ZIP reader, fresh publication identity and final readback).
Snapshot UI/restore/FULL/offload remain unimplemented. Slice 2A adds an install-local DataStore binding gate: all application-facing repository
writes require durable expected UUID + actual header match; startup probe never auto-adopts or initializes.
Контракт и ограничения: [Repository V1 foundation](repository-v1-foundation.md), D095/D096.
Snapshot contract: [Repository Snapshot V1](repository-snapshot-v1.md), D097.
Temporal I3 adds [Snapshot V2](snapshot-v2-wire-schema.md): current writer uses V2,
readers/discovery validate V1 and V2 by manifest version. Complete Backup current writer is V7
([format contract](backup-format-v1.md#temporal-i3--complete-backup-v7-2026-10-08)); V1–V6 readers remain.
New formats carry explicit canonical research dates without createdAt derivation; Room stays v13.

Доступ пользователя к резервным копиям (D098) отделён от хранения: `BackupLocation` — единственный
источник фиксированного пути `Download/BeeSearch/<variant>/Backup` и его SAF document id,
`BackupDirectoryBootstrap` выводит из него skeleton, `AndroidBackupTreeAccess` переводит результат
системного picker в locator и сохраняет persistable grant, а Android-независимый
`BackupAccessCoordinator` решает только одно: принять ли выбор и какую identity-операцию выполнить
(`reconnect` того же UUID, `adoptExisting` существующего `repository.json` или `initializeNew` в
пустом skeleton). Выбор, не совпадающий с фиксированной папкой, отклоняется до любого обращения к
репозиторию. Типизированные ошибки хранения группируются в понятные пользователю проблемы на
границе UI; экран `Настройки → Резервное копирование` не знает ни форматов, ни правил identity.
Настройка доступа не входит в полевой путь: startup по-прежнему только создаёт skeleton и
read-only проверяет binding.

Ручное создание копии (D099) добавлено поверх того же экрана без нового слоя хранения:
`BackupSnapshotOperations` — узкая граница над принятым `RepositorySnapshotService` (который сам
идёт через `BoundRepository`), поэтому UI не может ни вызвать Repository V1 напрямую, ни собрать
граф, ни написать архив. Android-независимый `BackupOperationCoordinator` держит два правила:
одновременно выполняется ровно одно создание (повторный запрос возвращает `AlreadyRunning` и ничего
не публикует) и репозиторий остаётся источником истины (каждое чтение — `discover()`, успешное
создание сразу перечитывается; отдельной метки времени в Room/DataStore нет). Состояние экрана —
одна модель на доступ, содержимое репозитория и текущую операцию; непроверяемые кандидаты
показываются предупреждением, а не скрываются, и успех сообщается только после committed и verified
результата сервиса. Фонового выполнения, планировщика и автоматических копий нет.

Защита медиа (D100) добавляет только backend. `RequiredMediaSet` — единственное определение того,
какие блобы требует текущее состояние исследований; через него работают и ссылки Snapshot V1, и
защита, поэтому второго определения элигибильности/дедупликации/расширения не существует.
`MediaStateCapture` берёт медиа-записи одним Room-транзакционным чтением (как snapshot),
`MediaProtectionService` разрешает приватные источники через те же managed-хранилища и публикует
каждый блоб существующим `BoundRepository.ingest()`, а результат остаётся per-blob: успешно
защищённые блобы не откатываются из-за другого блоба, и остановить запуск может только проблема
уровня репозитория. Сервис не удаляет и не перемещает приватные оригиналы, не пишет Room/DataStore и
не создаёт snapshot; пользовательского входа и автоматического вызова нет. Независимая проверка на
ПК расширена вторым режимом поверх неизменного одиночного: заголовок репозитория, сам snapshot,
согласованность UUID/варианта и фактический SHA каждого требуемого блоба в `Media`.

Профили доказательств Snapshot V1 (S6B, контракт §7.1). Поддерживаются ровно два набора
`snapshotProfile`/`evidencePolicy`/`creationResult`: `METADATA_ONLY`/`NO_MEDIA_EVIDENCE`/`COMPLETE`
(как было) и `FULL`/`LOCAL_VERIFIED`/`COMPLETE` (новый). Структурная оболочка V1 не меняется: те же 17
записей, тот же набор полей манифеста, те же правила дескрипторов, канонического JSON, дайджестов и
лимитов; версия формата остаётся 1, потому что читатель, знающий только первый набор, отказывается от
неизвестного набора, а не толкует его иначе. Полный набор означает ровно одно: требуемый медиа-набор
взят из ТОГО ЖЕ неизменяемого `SnapshotDomainEntries`, который сериализуется, и каждый его блоб
проверен в этом же связанном репозитории непосредственно перед публикацией —
`RepositoryMediaEvidence` читает канонический `Media/<sha256>.<canonicalExtension>` и считает
фактический размер и SHA-256 по байтам репозитория, не веря ни имени файла, ни размеру от провайдера,
ни SHA из метаданных. Проверка идёт только на чтение: ingest не вызывается, при отсутствии, другом
размере, других байтах, другом каноническом расширении или неоднозначном SHA создание падает
типизированной ошибкой (`MEDIA_EVIDENCE_MISSING`/`MEDIA_EVIDENCE_MISMATCH`/
`MEDIA_EVIDENCE_INCONSISTENT`), ничего не публикует и НЕ откатывается к профилю метаданных. Внутри ZIP
остаются только метаданные и ссылки: байтов JPEG/MP4 и записей `Media/*` там нет. Обнаружение
snapshot'ов для полного набора дополнительно требует, чтобы требуемые блобы были подтверждены сейчас:
иначе кандидат не считается пригодным локальным результатом, файл не удаляется и не переписывается, и
старый корректный `METADATA_ONLY` может остаться самым новым пригодным. `LOCAL_VERIFIED` — это
локальное доказательство на этом устройстве и в этом хранилище: оно ничего не говорит о копии на ПК,
в облаке, на другом носителе или о защите от потери телефона. Пользовательского входа у полного
профиля нет: экран резервного копирования по-прежнему создаёт `METADATA_ONLY`.

Logical backup core реализует versioned архив согласно D069–D073. Пользовательский
экспорт использует Android Storage Access Framework: отдельный Data ViewModel
управляет состоянием операции, тонкий document adapter передаёт выбранный URI
существующему `BackupService`, а UI не знает формат ZIP, manifest или правила
целостности. Broad storage permissions не требуются.

M2A media I/O boundary: Complete Backup и три portable export profiles используют
`ArchivePayload` для file-backed media и `StagedZipArchive` для чтения ZIP. Payload
копируется bounded buffer с Long byte count и incremental SHA-256; source size/hash
повторно проверяются при записи. Readers извлекают entries в isolated cache workspace,
валидируют полный manifest/domain/media graph и только затем допускают restore activation.
Known bounded JSON остаётся memory-resident. Decoded export result является Closeable:
caller обязан закрыть его после работы с graph/media; validation failure закрывает workspace.
Collection temp-ZIP read-back использует тот же staged reader и закрывает result перед SAF copy.
M2B разделяет archive policy: metadata ограничена 16 MiB на entry и 64 MiB суммарно
(collection — 128 MiB); entry limits остаются 64 / 1024 и collection максимум 256 objects.
Media не имеет фиксированного application cap: bounded metadata/full domain validation
авторизует точные paths, ownership, Long byte_size и SHA; actual streamed size должен точно
совпасть, overflow и hash mismatch отклоняются. Reader сначала spool-ит compressed ZIP на
диск, ограничивает central index до открытия ZipFile и читает bounded metadata; только затем
извлекает media. Проверяются central и local records, duplicates, CRC и inventory. ZIP без
полного central directory теперь malformed; валидные wire versions/paths/order сохранены.
Compressed spool ограничен доступным storage; после inventory выполняется advisory free-space
check для extracted media. Restore дополнительно требует место для managed staging copy.
Provider capacity неизвестна заранее: streaming write failure прерывает операцию и очищает
workspace. Source, temp ZIP и SAF destination могут сосуществовать; raw restore/crash recovery
не добавлены. Ingest фотографий точек и фото/видео физических объектов не имеет фиксированного
application size cap: общий bounded-stream writer считает actual Long byte_size и SHA-256,
проверяет overflow и cancellation, закрывает потоки перед публикацией через rename и очищает
свои partial/new files при ошибке. Storage/provider I/O errors остаются реальным пределом;
free-space preflight не гарантирует запись. Metadata/ZIP guards M2B не изменены.
>=1000 MB end-to-end runtime support ещё не доказана.
Direct-SAF Point export и возможность partial
destination при SAF copy failure остаются прежними; filesystem/Room crash recovery не добавлен.

Single ObservationPoint export отделён от logical backup пакетом
`data/pointexport`. Транзакционный `getObservationPointDetail(pointId)` является
единственным source read: он возвращает выбранную Point, Territory/Observer context,
weather, Bee/FlightCycle и attachment metadata, после чего exporter сверяет app-owned
photo bytes с size/SHA metadata. Pure codec пишет format v2 и читает v1/v2 ZIP с
`manifest.json`, `point.json` и `attachments/<attachmentId>`. Он не читает DataStore,
Area, карты или сеть и не имеет restore/import side effects. UI только запускает
существующий `CreateExchangeDocument` с начальным `Exchange/Data` и передаёт выбранный
URI document adapter-у.

Очистка observation data проходит через repository operation и одну Room
transaction в порядке FlightCycle → Bee → ObservationPoint. Territory, Observer,
DataStore settings и map packages не входят в эту транзакцию. Help является
отдельной offline presentation feature; MainActivity остаётся только app host и
маршрутизатором существующего route mechanism.

Single Physical Object export отделён от logical backup и от ObservationPoint export пакетом
`data/objectexport` с собственным профилем `SINGLE_PHYSICAL_OBJECT` v2 (V1 foundation — D093). Профиль
реализован изолированно: ZIP/hash/JSON механика повторена локально, потому что ObservationPoint
export уже проверен и общий export framework в это решение не входит. Source read возвращает
объект, его subtype properties и его media из `PhysicalObjectRepository`, у которого нет пути чтения
Bee, ObservationPoint и их attachments, поэтому «только object-owned данные» является свойством
формы чтения, а не фильтром, который можно забыть. Territory и creator Observer читаются отдельно и
попадают в пакет только как минимальный read-only labelling/provenance snapshot. Apiary
отклоняется fail-closed, так как у него нет пользовательского жизненного цикла. Пакет собирается и
проверяется в app cache, и в выбранный SAF destination копируется только целый архив. Field-level
контракты — `docs/physical-object-export-v1.md` и `docs/temporal-export-v2.md`.

Тот же изолированный feature boundary содержит отдельный collection-профиль
`PHYSICAL_OBJECT_COLLECTION` v2 для всех Hollow либо всех LogHive текущей Territory. Collection
source использует только `PhysicalObjectRepository.listForTerritory`, Territory/Observer
repositories и object-owned `PhysicalObjectMediaFileStore`: Territory snapshot записывается один
раз, используемые Observer snapshots дедуплицируются, а paths media включают object UUID. Codec
детерминированно упорядочивает objects и media, строго проверяет manifest/entries/hash/size и
отклоняет смешанный type/Territory. Service сначала собирает и декодирует временный package в app
cache и лишь после полной проверки копирует его в единственный SAF destination. Ошибка любого
объекта или media отменяет всю операцию; пустой список отсекается до SAF. ObservationPoint export,
backup contract и Room schema от этого профиля не зависят.

Заблокированное удаление объекта возвращает структурированный результат: kinds блокирующих ссылок и
их количества. Repository собирает их внутри транзакции удаления, UI показывает их в dedicated
dialog, а `RESTRICT` FK остаётся второй, fail-safe линией защиты.

ObservationPoint properties v1 введены в Room schema v7; текущая schema v8
добавляет физические объекты и nullable явную связь Bee с ними (D088).
`description` остаётся полем ObservationPoint; attachment metadata и one-to-one weather snapshot имеют
отдельные таблицы. Photo bytes копируются в
`files/observation-attachments/<pointId>/<attachmentId>` и никогда не зависят от
долговечности исходного content URI. Selective/full deletion сначала безопасно
перемещает файлы в staging, затем удаляет metadata транзакцией и завершает удаление;
при ошибке транзакции files возвращаются.

До создания ObservationPoint photo draft хранится отдельно в
`files/observation-attachments-staging/<draftSessionId>/<attachmentId>`. Picker и
camera bytes валидируются и копируются туда без Room row. При подтверждении draft
files перемещаются в final point directory, после чего ObservationPoint, weather и
attachment metadata фиксируются одной Room transaction; при ошибке transaction
files возвращаются в draft. Cancel удаляет session directory. Exchange/Areas и
map-package storage в этом lifecycle не участвуют.

UI зависит от provider-neutral `WeatherProvider`. WorkManager с network constraint
обрабатывает только persisted `PENDING` requests; координаты и время берутся из
ObservationPoint, поэтому delayed retry не подменяет условия погодой reconnect.
Open-Meteo adapter использует hourly `temperature_2m`, `wind_speed_10m` в `ms` и
`wind_direction_10m`; ближайший sample выбирается к `created_at`, при tie — более
ранний. Forecast endpoint применяется для поддерживаемого recent диапазона, archive
endpoint — для более старых точек. UI/domain не знают endpoint DTO.

Во внешний weather request передаются только latitude, longitude и требуемая дата/
время. Observer, Territory, UUID точки, Bee, description и photos не отправляются.
Open-Meteo является текущим non-commercial adapter, но domain boundary не содержит
предположения о бесплатности или неизменности provider.

Не следует полагаться только на один телефон как единственное долговременное хранилище исследовательских данных.

---

# 65. Работа с файлами

Фотографии ObservationPoint входят в текущую модель; аудио и другие типы вложений
пока не входят. Бинарные файлы не хранятся непосредственно в Room-таблицах.

Предпочтительно:

```text
file storage
+
database metadata
```

## 65.1. Каталог обмена Bee Search

Пользовательский обмен файлами отделён от хранилища приложения отдельным контрактом
`BeeSearchExchangeStorage`. Контракт определяет корень варианта и три каталога:
`Areas`, `OfflineMaps`, `Data`. UI, импорт и экспорт не собирают пути самостоятельно —
они получают каталог и начальное расположение для picker из этого контракта.

Физический корень — общедоступный `Download`, поэтому фактический путь варианта:

```text
Download/BeeSearch/Stable/Exchange/
Download/BeeSearch/Beta/Exchange/
Download/BeeSearch/Dev/Exchange/
```

Вариант берётся из сгенерированного `BuildConfig.EXCHANGE_VARIANT`, то есть из build
configuration, а не из разбора package name. Каталоги создаются идемпотентно при первом
обращении: существующие каталоги переиспользуются, ничего не удаляется, не
переименовывается и не дублируется.

Каталог обмена не является canonical storage приложения. Room DB, DataStore, кэш,
установленная копия PMTiles и map-package runtime state остаются в app-owned storage.
Импорт по-прежнему копирует пакет в app-owned storage, поэтому удаление или перемещение
файла из каталога обмена не влияет на уже импортированную карту и не затрагивает данные
наблюдений.

Scoped Storage не позволяет приложению создать `BeeSearch` в корне общего хранилища, а
широкие разрешения на файловую систему (`MANAGE_EXTERNAL_STORAGE`) не запрашиваются.
`Download/BeeSearch/...` — ближайший стандартный пользовательский путь, согласованный с
уже существующей доставкой map package на устройство. На проверенном целевом устройстве
(API 36, `targetSdk 37`) приложение создаёт в нём каталоги и файлы без разрешений на
хранилище; `ensure()` возвращает `Unavailable`, если платформа отказала, и вызывающий код
деградирует к picker без начального расположения, а не к ошибке операции.

Системный picker нельзя нацелить на произвольный каталог без ранее выданного разрешения,
поэтому каталог обмена передаётся как `DocumentsContract.EXTRA_INITIAL_URI` — начальное
расположение, а сам файл по-прежнему выбирает пользователь. Если система игнорирует
начальное расположение, picker открывается в ближайшем доступном месте, и приложение
дополнительно показывает точный путь текстом на экране.

---

# 66. Build system

Используется:

```text
Gradle
Kotlin DSL
```

Канонический локальный выпуск Beta выполняется только через
`tools/beta-release/beta_release.py`; правила product version, Beta sequence,
монотонного Android versionCode, commit provenance и fail-closed проверки архива
описаны рядом в `tools/beta-release/README.md`. Прямая публикация результата
`assembleBeta` минует обязательную проверку и не является release workflow.

Проект уже создан с:

```text
build.gradle.kts
settings.gradle.kts
```

Следует сохранять Kotlin DSL.

---

# 67. Version control

Проект хранится в Git.

В репозитории должны находиться:

* исходный код;
* Gradle Wrapper;
* документация;
* миграции;
* тесты;
* настройки проекта, пригодные для совместной разработки.

Не должны попадать:

* build output;
* `.gradle`;
* `.kotlin`;
* локальный `local.properties`;
* пользовательские IDE cache.

---

# 68. Документация как часть архитектуры

Проектная документация является частью репозитория.

Основные документы:

```text
docs/
    product-requirements.md
    user-workflows.md
    domain-model.md
    data-model.md
    architecture.md
    decisions.md
    glossary.md
```

Изменение поведения приложения должно сопровождаться обновлением соответствующей документации.

---

# 69. Architecture Decision Records

Существенные технические решения следует фиксировать в:

```text
docs/decisions.md
```

или позднее разбить на отдельные ADR-файлы, если их станет много.

Примеры решений:

```text
использование MapLibre
выбор Room
формат офлайн-карт
источник базовой карты
подход к времени
выбор DI
архитектура синхронизации
```

---

# 70. Что не следует делать в MVP

На первом этапе не требуется:

* сервер;
* авторизация;
* облачная база;
* web-интерфейс;
* автоматическая синхронизация;
* сложный GIS-анализ;
* алгоритм определения гнезда;
* отдельная сущность Observer;
* отдельная сущность ObservationSession;
* отдельная сущность GroupRelease;
* background GPS tracking;
* сложная DI-инфраструктура;
* микросервисы;
* собственный картографический сервер без необходимости.

---

# 71. Этапы реализации

Рекомендуемая последовательность:

## Этап 1 — фундамент

```text
Room
DataStore
основные модели
Repository
Navigation
```

## Этап 2 — Territory

```text
создание Territory
выбор текущей Territory
сохранение между запусками
```

Territory создаётся и выбирается независимо от offline package. Отсутствие
подготовленного coverage не блокирует переход к рабочей карте или
исследовательские операции; оно означает только отсутствие гарантированной
карты при потере сети.

## Этап 3 — карта

```text
MapLibre
GPS
current position
current Territory + current Observer перед первой ObservationPoint
создание ObservationPoint
ручная коррекция
```

## Этап 4 — открытие наблюдения

```text
подтверждение точки
Bee не создаются заранее
15 производных вариантов метки
```

## Этап 5 — первый вылет

```text
Room transaction
индивидуальное время вылета
Bee + sequence_number = 1 + BEES_FOUND
локальная отмена ошибочного вылета
```

## Этап 6 — рабочий экран

```text
все Bee с уникальными сочетаниями текущего каталога меток
В ПОЛЁТЕ
ВЕРНУЛАСЬ
таймеры
```

## Этап 7 — повторные циклы

```text
Register return
Start next flight
Flight history
```

## Этап 8 — азимут

```text
HeadingProvider
sensor accuracy
set azimuth
remove azimuth
```

## Этап 9 — offline maps

```text
Bee_search OSM field source
PMTiles Map Package acquisition
staging + compatibility/integrity validation
atomic activation + strict Ready status
coverage overlay
safe package replacement
airplane-mode Samsung validation
```

## Этап 10 — полевой прототип

Реальная проверка полного рабочего сценария.

Сервер начинается только после стабилизации локального процесса.

---

# 72. MVP architecture overview

Итоговая схема первой версии:

```text
                  Android phone
┌────────────────────────────────────────────┐
│                                            │
│              Jetpack Compose               │
│                                            │
│ Territory  Map  Observation  History       │
│     │       │        │          │          │
│     └───────┴────────┴──────────┘          │
│                  │                         │
│              ViewModel                     │
│                  │                         │
│            Domain logic                    │
│                  │                         │
│             Repository                     │
│          ┌───────┴──────────┐              │
│          │                  │              │
│        Room              DataStore         │
│          │                                  │
│ Territory → Point → Bee → FlightCycle      │
│                                            │
│ MapLibre ← active local PMTiles package    │
│                                            │
│ LocationProvider ← GPS                     │
│ HeadingProvider  ← Sensors                 │
│                                            │
└────────────────────────────────────────────┘
```

---

# 73. Будущее расширение

После стабилизации MVP:

```text
Android
   │
   │
   ▼
SyncEngine
   │
   ▼
API
   │
   ▼
PostgreSQL
   │
   ├── Web UI
   ├── Data export
   └── Analysis
```

Локальная предметная модель при этом должна сохраниться максимально неизменной.

## 73.1. Граница приложения и исследования

Исследование алгоритмов является частью проекта Bee Search, но **не частью Android-приложения**
(D088). Граница трёх уровней:

```text
Уровень 1 — Bee Search Android
    factual/raw фиксация: ObservationPoint, Bee, FlightCycle,
    физические объекты (Дупло / Колода / Пасека), координаты,
    явные фактические связи, позднее Осмотр

Уровень 2 — research tooling внутри проекта, вне Android-приложения
    actual geodesic distance, калибровка, сравнение алгоритмов,
    validation, оценка ошибок, вероятностные модели

Уровень 3 — принятый и версионированный production algorithm
    реализуется в Android только после отдельного решения
```

Android не подбирает и не обучает алгоритмы: в приложении не вводятся calibration entities,
validation flags, model fitting, training, research dataset manager и probability/calibration
state. Actual distance выводится из координат и в Room не хранится. Research tooling получает
фактические данные через versioned export/API, а не неформальным чтением внутренних Room-таблиц.

Физические объекты — долговечная ветвь предметной модели, отдельная от событий наблюдения
(D073, D088); Осмотр (Inspection) является их отдельной исторической сущностью и не входит в
дистанционную модель анализа: для actual distance достаточно координат ObservationPoint,
FlightCycle конкретной Bee и координат физического объекта.

---

## 73.2. Objects V1: создание и хранение

Objects V1 сохраняет D088 boundary: `Hollow`, `LogHive` и `Apiary` остаются конкретными
domain types, а общая `physical_objects` identity используется только как internal
persistence/FK boundary. Основной create path начинается на карте (`+`), переиспользует
map crosshair для выбора координат, затем записывает subtype properties транзакционно после
успешной валидации формы. Номер выделяется в той же транзакции, поэтому отмена/invalid create
не создаёт row и не расходует designation.

Creator хранится стабильным Observer UUID с `RESTRICT`; старые foundation rows допускают
nullable creator. Creation media хранятся нормализованно в `physical_object_media` (1:N) с
app-owned files, типом image/video, размером и hash. Это отдельная граница от будущей
Inspection media.

Room schema v11 хранит optional user `name` прямо в subtype-строках Hollow/LogHive; migration
из v10 добавляет только nullable columns. Complete Backup v6 переносит эти имена и сохраняет
чтение v1–v5. Identity, sequence state и media architecture не меняются. Track/GPX и
Inspection остаются deferred.

Удаление и экспорт одного объекта реализованы для Дупло и Колоды (D093). `Apiary` остаётся
persisted типом без пользовательского жизненного цикла: creation UI, категория, карточка, медиа,
удаление и экспорт для него не реализуются, а экспорт отказывает для этого типа fail-closed. Это не
создаёт отдельной архитектуры для Пасеки: решение потребуется вместе с её UI.

## 73.3. Map overlays и layers: принятое разделение

**Статус:** ACCEPTED · owner approval 2026-10-08 · [D102](decisions.md#d102--map-overlay-rendering-split-и-lifecycle-safety).
Основание — два Samsung DEV/device spike и последующий review:
[durable evidence summary](map-overlay-lifecycle-evidence.md). Принято архитектурное разделение и
lifecycle/safety contract; production runtime overlay registry и общая карта данных не реализованы.
Причина разделения — разные свойства механизмов: runtime MapLibre Source/Layer принадлежит текущему
Style, который заменяется при смене basemap; Compose overlays рисуются поверх карты и не требуют
MapLibre style restoration. Координатные markers продолжают использовать projection текущей карты.

```text
MAPLIBRE SOURCES / LAYERS
    basemap / presentation composition
    raster и user georeferenced raster layers (I014)
    GPX и другая bulk geometry (I011)
    user field lines / polygons / areas
    статическая analytical geometry: distance bands, probable nest zones, apiary-radius aids
    другие overlays, которым нужны MapLibre ordering и взаимодействие с basemap/style layers

COMPOSE OVERLAYS
    маркеры ObservationPoint, Hollow, LogHive, Apiary, будущий Inspection
    selection и selected state
    GPS marker, map-centre target, direction guide
    temporary measurement marker
    интерактивные analytical handles и элементы
```

Причины: Compose даёт interaction, accessibility semantics, 48 dp touch targets, уже проверенный
marker path и независимость от смены style; MapLibre даёт raster, bulk geometry, ordering
относительно basemap, labels и геометрию, масштабируемость на большое число features.

Существующие research markers не переносятся в MapLibre только ради технологической унификации;
вопрос масштаба Compose markers решается по реальным device measurements, конкретные performance
limits сейчас не устанавливаются.

### Runtime MapLibre lifecycle и safety invariants

После `setStyle()` прежние runtime Source/Layer отсутствуют, старый Style invalid. Restore создаёт
новые SDK Source/Layer в новом current fully-loaded Style; registry/lifecycle owner живёт вне
конкретного Style. Минимальный контракт будущей реализации:

```text
requested basemap/profile identity
→ increment/store request generation BEFORE setStyle()
→ setStyle()
→ callback / deferred work
→ reject stale generation/request identity
→ require current fully-loaded Style
→ validate owned IDs / collision policy
→ pre-add existence checks
→ add Sources
→ add dependent Layers in deterministic order
→ complete for current request
```

Generation и immutable request/profile identity записываются **до** `setStyle()`: callback может
быть synchronous. Current Style сам по себе не доказывает request identity: controlled pending A,
superseded B, дал deferred/getStyle callback с current B Style и stale контекстом A. Stale production
callback в сегодняшних четырёх локальных JSON basemap profiles не обнаружен; это защитный invariant
для deferred work, будущих медленных paths и изменения SDK/lifecycle, а не заявление о текущем
пользовательском race bug.

Requests и mutations сериализуются на main thread. Request validation → existence checks → SDK add
выполняются одним непрерывным main-thread блоком без suspension и вложенного style switch. Любая
deferred work перед mutation повторно валидирует generation/request identity, current Style и его
loaded state. Superseded setter callback может не прийти: нельзя ждать completion каждого request.

Runtime Source/Layer IDs имеют собственный явно app-owned namespace; ownership определяется по
конструкции. Presence ID не доказывает совместимость Source/Layer с ожидаемым owned overlay.
Неожиданная collision с чужим/style-defined ID вызывает явный typed/fail-closed отказ соответствующей
overlay operation, а не `skip и продолжить`. Duplicate prevention выполняется **до** SDK add call.
`try add → catch duplicate → continue` запрещён: после duplicate SDK exceptions в первом spike
наблюдался asynchronous native SIGSEGV; Java exception не является безопасной recovery boundary.
Точная native причинность не установлена, crash-class safety requirement принят владельцем.

### OPEN implementation/device verification (не блокирует принятие)

- Compose scale/performance: перед rollout общей карты Samsung benchmark синтетического набора
  ориентировочно 200–1000 markers; frame time, gesture smoothness, projection update cost.
  Performance limit не устанавливается до измерения.
- Style failure/cancellation: final visible basemap и active overlay state должны быть согласованы.
- Activity/Map lifecycle: recreation, return-to-map, registry lifecycle owner и stale/deferred work
  после lifecycle transition.
- Frame budget: continuous main-thread mutation block не разрешает неограниченную работу в одном
  frame; restore нескольких Sources/Layers должен укладываться в измеренный budget.
- Layer ordering: Sources before dependent Layers; deterministic ordering; конкретные anchors/order
  относительно каждого basemap проверяются при реализации.
- Visual transition/flicker: окно style replacement → restore остаётся UX/device verification;
  masking/transition mechanism сейчас не выбран.
- MapLibre upgrade: существенное обновление требует релевантного lifecycle regression test.

### Research marker visual follow-up

ObservationPoint, Hollow и LogHive различаются прежде всего формой/пиктограммой, не только цветом.
Selected object сохраняет type pictogram; selection добавляет отдельный halo/ring/highlight/scale,
не заменяет тип общей selected-иконкой. Type symbol используется последовательно там, где уместно.
Перед production markers обязателен отдельный небольшой Samsung visual pass: различимость формы и
контраст на vector, Sentinel/raster и Hybrid. Точные icons/colors/sizes и selected treatment не
утверждены; assets этим решением не создаются.

Territory Data Map, независимое включение слоёв и Layers/Filters UI остаются идеей I016
(`docs/ideas.md`).

# 74. Критерий правильности архитектуры

Архитектура считается подходящей, если она позволяет:

1. провести полное наблюдение без интернета;
2. не потерять данные при закрытии приложения;
3. работать одновременно примерно с 10 Bee;
4. быстро регистрировать события;
5. использовать карту и вручную корректировать GPS;
6. регистрировать необязательный азимут;
7. повторно открывать историю точки;
8. добавлять новые функции без переделки предметной модели;
9. позднее добавить серверную синхронизацию без превращения мобильного приложения в полностью зависимый от сервера клиент;
10. сохранять код достаточно простым для разработки и сопровождения небольшим проектом.
