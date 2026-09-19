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
только после Samsung A/B PoC; z17–20 не генерируются в первый milestone.

Один versioned field MapProfile задаёт совместимость schema/profile, dataset
snapshot, style/resources, zoom contract и attribution. Новая несовместимая
версия получает новую package identity/version; старый активный package не
удаляется и не становится неинтерпретируемым молча.

Satellite остаётся optional: при разрешении provider он может работать online
независимо от vector preparation и позднее использовать отдельный offline
package с собственными bounds/zoom/profile/lifecycle. Satellite readiness не
входит в readiness основной vector map. Формат и provider satellite package не
выбраны. Contours, hillshade и DEM отложены.

Runtime style может содержать Field, Satellite и Hybrid layer groups и менять
их visibility без полной перезагрузки style. GPS,
crosshair, ObservationPoints и будущие app-generated research overlays не
зависят от выбранной base-map group.

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

Logical backup core реализует versioned архив согласно D069–D073. Пользовательский
экспорт использует Android Storage Access Framework: отдельный Data ViewModel
управляет состоянием операции, тонкий document adapter передаёт выбранный URI
существующему `BackupService`, а UI не знает формат ZIP, manifest или правила
целостности. Broad storage permissions не требуются.

Очистка observation data проходит через repository operation и одну Room
transaction в порядке FlightCycle → Bee → ObservationPoint. Territory, Observer,
DataStore settings и map packages не входят в эту транзакцию. Help является
отдельной offline presentation feature; MainActivity остаётся только app host и
маршрутизатором существующего route mechanism.

Не следует полагаться только на один телефон как единственное долговременное хранилище исследовательских данных.

---

# 65. Работа с файлами

Фото, аудио и другие вложения пока не входят в текущую модель MVP.

Если они появятся позже, бинарные файлы не следует хранить непосредственно в основных Room-таблицах.

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

---

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
