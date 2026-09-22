# Bee Search Server & Sync Architecture

**Статус:** DRAFT — архитектурное предложение для обсуждения.

**Область:** граница будущих server/sync-компонентов; сервер и SyncEngine не
реализуются этим документом.

**Совместимость:** документ сохраняет D039–D041, D051, D052, D056, D061 и
принятый PMTiles baseline D063. Он не меняет Room schema v5, текущие
domain-модели или offline-first workflow.

**Терминология:** принятое предметное решение D088 определяет три конкретных
долговечных физических типа (Дупло / Колода / Пасека, то есть `Hollow` / `LogHive` /
`Apiary`) и отдельную историческую сущность `Inspection` («Осмотр»). Где ниже в этом
документе написано `Nest` или `NestInspection`, читать соответственно «физический
объект (Дупло / Колода / Пасека)» и «Осмотр»; вопрос про их domain decision, который
этот документ оставлял открытым, решён в D088.

## 1. Goals

Будущая серверная часть Bee Search должна:

- объединять исследовательские данные нескольких телефонов и наблюдателей;
- сохранять устойчивые UUID, созданные на телефоне;
- позволять телефону работать неделями без сети и безопасно повторять sync;
- давать ПК/web-интерфейсу единое представление данных через API;
- хранить исходные полевые факты и provenance их исправлений;
- строить и выдавать versioned PMTiles для выбранных Territory;
- позднее принимать фотографии, видео и другие вложения;
- не входить в критический путь полевой регистрации.

Практически подтверждённый map pipeline:

```text
OSM PBF
→ Planetiler
→ custom field profile
→ PMTiles
→ MapLibre Android
→ local asset glyphs and labels
```

является подходящей границей между server-side map generation и Android
rendering. Benchmark Territory площадью около 883.6 км² дал PMTiles около
5.01 MiB, generation около 37 секунд и приемлемый rendering на Samsung. Размер
конкретного пакета зависит от плотности OSM data и версии профиля.

## 2. Non-goals

Этот этап не определяет и не реализует:

- HTTP API, серверный код или Android SyncEngine;
- новую Room schema или поля sync в domain entities;
- account/auth UI;
- production Territory boundary persistence;
- окончательную модель исторического редактирования;
- SQL schema центральной БД;
- автоматическое определение гнезда;
- media upload pipeline;
- production deployment topology.

## 3. Architectural principles and data ownership

### 3.1. Architectural principles

1. **Offline field work is primary.** Ни auth, ни server, ни map download не
   входят в critical path регистрации полевого события.
2. **Canonical data precedes rendering.** Room/domain records создают MapLibre
   presentation, а не наоборот.
3. **Base map is replaceable infrastructure.** PMTiles не содержит
   редактируемые ObservationPoint, Nest или пользовательские линии.
4. **Map Package is acquisition-independent.** Manual import и server download
   сходятся в lifecycle D063: acquire → stage in app-controlled storage →
   validate compatibility and integrity → atomically activate → render through
   MapLibre.
5. **Raw observations retain provenance.** Server enrichment и recomputable
   analysis не перезаписывают исходные observations.
6. **Transport ordering is not domain time.** Server cursor/revision не
   подменяет `Instant` полевых событий из D051.
7. **Identity is stable before connectivity.** Client UUID остаётся identity
   после sync; display code, device и account не заменяют его.
8. **Territory boundary, map coverage and package artifact are different.** Их
   lifecycle, ownership и persistence рассматриваются отдельно.

### 3.2. Категории данных

| Данные | Категория | Будущий владелец/authority | Примечание |
|---|---|---|---|
| `Territory` record | Syncable domain data | Shared server record после первого sync; локальная копия доступна offline | Может быть создана offline с client UUID |
| `Observer` record | Syncable domain data | Shared server record после связывания | Не равен account и не равен device |
| `ObservationPoint` | Syncable domain data | Исходный автор на телефоне для unsynced change; сервер — authority принятой revision | Сервер не переписывает исходные факты молча |
| `Bee` | Syncable domain data | То же, в контексте ObservationPoint | Mark uniqueness остаётся domain invariant |
| `FlightCycle` | Syncable domain data | То же | Event timestamps сохраняются как исходные field data |
| `current_territory_id` | Local-only | Устройство | Не синхронизируется как research fact |
| `current_observer_id` | Local-only | Устройство | Выбранный рабочий контекст |
| map camera/UI settings | Local-only | Устройство | Можно переносить позже только как user preference |
| desired map coverage | Local-only в текущей модели | Устройство | Multi-fragment DataStore state по `Territory.id` |
| installed package path/status | Local-only | Устройство / фактический файл | Не является полем Territory |
| PMTiles package metadata | Server-owned infrastructure | Map package service | Телефон хранит локальный manifest установленной копии |
| PMTiles file | Large binary/file data | Server artifact storage; локальная проверенная копия на телефоне | Передача по checksum/version |
| server change log/cursor | Server-owned / derived | Server sync subsystem | Не является research domain data |
| analysis result / nest candidate | Derived/recomputable analysis data | Конкретный analysis run | Не переписывает observations; обычно можно пересчитать |
| сохранённый research analysis artifact | Derived/recomputable, иногда сохраняемый artifact | Analysis service/author | Требует input/profile/version provenance |
| подтверждённый физический объект — Дупло / Колода / Пасека (будущее) | Вероятно syncable domain data | Domain decision принято: D088 (типы, identity, обозначение, relation) | Отличать от вычисленного candidate |
| `Inspection` / Осмотр (будущее) | Вероятно syncable domain data | Field author + server revision history | D088: самостоятельная историческая сущность; schema пока не проектируется |
| custom research point/line/path/polygon | Syncable domain data, если объект предназначен для sharing | Field author + server revision history | Geometry хранится вне MapLibre style/source state |
| device-local draft/measurement preview | Local-only | Устройство | После подтверждения может породить syncable object |
| imported GPX/reference overlay | Local-only или syncable — определяется явным import intent | Устройство до публикации | Нельзя автоматически считать canonical research data |
| media metadata (будущее) | Syncable domain data | Server record с client UUID | Связь с author/entity/checksum |
| photo/video bytes (будущее) | Large binary/file data | Object/file storage | Не хранить в PostgreSQL как основной путь |

Категория `Server-owned / derived` используется для server operation state
(job, cursor, acknowledgement, package manifest). Категория
`Derived/recomputable analysis data` выделена отдельно: такие результаты могут
быть вычислены на Android, server или PC из одинаковых canonical inputs.

### 3.3. Три разных смысла source of truth

Чтобы избежать ложного выбора «телефон или сервер», нужно разделять:

1. **Field authorship.** Телефон и Observer являются источником исходного
   наблюдения и его unsynced corrections.
2. **Replication authority.** Сервер определяет, какие revisions приняты,
   присваивает им монотонный порядок и распространяет их устройствам.
3. **Materialized local state.** Room остаётся источником текущего UI и
   полевой работы на конкретном телефоне, включая работу без сети.

Сервер может валидировать структуру, выявлять противоречия и создавать
обогащения или аналитические результаты. Он не должен молча заменять
координаты, времена, presence result, marks или azimuth исходной записи.
Исправление должно иметь отдельную revision/provenance: actor, time, reason и
ссылку на предыдущую revision. Derived data хранится отдельно от raw field
facts.

Телефон может менять уже синхронизированную запись только через разрешённый
domain edit workflow. Такое изменение отправляется как новая mutation с
ожидаемой server revision, а не как безусловная перезапись.

## 4. Identity model

### 4.1. Canonical domain IDs

Текущее правило подходит будущему серверу:

```text
UUID создаётся на телефоне
→ тот же UUID сохраняется в Room
→ тот же UUID используется как domain ID на сервере
```

Сервер не выдаёт заменяющий numeric ID при sync. Внутренний database sequence
допустим для server change log, но не становится identity `Territory`,
`Observer`, `ObservationPoint`, `Bee` или `FlightCycle`.

Преимущества текущего UUID v4-подхода:

- создание полного графа данных без сети;
- idempotent повторная отправка;
- ссылки parent/child известны до sync;
- merge не зависит от локального порядка строк;
- вероятность случайной коллизии практически пренебрежима.

Сервер всё равно должен проверять формат UUID, authorization scope и ситуацию,
когда уже существующий UUID прислан с другим типом или несовместимым parent.

### 4.2. Разные identity

| Identity | Назначение | Чего не означает |
|---|---|---|
| `user/account_id` | Authentication, permissions, membership | Не является автоматически Observer |
| `observer_id` | Автор полевого наблюдения как domain entity | Не идентифицирует телефон или login session |
| `device_id` | Установка приложения, sync cursor, credentials и provenance | Не входит в research hierarchy и point numbering |
| `territory_id` | Устойчивая identity Territory | Не равен `Territory.code` |
| entity UUID | Identity конкретной domain record | Не заменяется server ID |
| `mutation_id` | Idempotency одной sync operation | Не является entity ID |
| server cursor | Позиция клиента в server change log | Не является временем устройства |

`observer_code` и `Territory.code` остаются человекочитаемыми. Их нельзя
использовать как primary key. После появления shared data уникальность code
нужно определить в явной server scope (например, research project), а не
считать глобальной по всей системе.

### 4.3. Offline Observer

Observer может существовать до account и без сети. При первом sync сервер
принимает его UUID как отдельную domain identity. Позднее account может
получить право действовать от имени этого Observer или связать существующего
Observer с account. Такое связывание не меняет `observer_id` исторических
ObservationPoint.

### 4.4. Multiple-device scenarios

| Сценарий | Ожидаемое поведение |
|---|---|
| Один Observer, один телефон | Device и Observer связаны в provenance, но остаются разными identity |
| Один Observer, новый телефон | После authentication новый device получает разрешённые server records и выбирает существующего Observer; его UUID не меняется |
| Несколько Observers в одной Territory | Membership даёт доступ к общей Territory; каждая новая ObservationPoint навсегда сохраняет фактический `observer_id` |
| Несколько телефонов параллельно | Каждый имеет свой outbox/cursor/device ID; UUID предотвращают merge по локальным row IDs, conflicts решаются revisions |
| Один телефон передан другому Observer | Пользователь явно переключает current Observer; прежние points сохраняют прежний `observer_id` |
| Телефон сменил владельца/account | Требуются sign-out, отзыв старой device registration и новая registration; локальные unsynced данные нельзя молча приписывать новому account |

### 4.5. Authentication boundary

Серверу концептуально нужны:

- `account/user` для login и authorization;
- `Observer` как domain author наблюдений;
- зарегистрированный `device_id` для конкретной установки приложения;
- access/session token, который можно отозвать;
- membership/role в research project или shared Territory.

Observer может быть создан полностью offline без account. При первом sync
аутентифицированный user предлагает связать/опубликовать этот Observer; сервер
проверяет права и возможное существующее соответствие. Ни совпадение ФИО, ни
`observer_code` не должны автоматически merge две identity.

## 5. Offline-first principles

Нормальный lifecycle:

```text
user action
→ domain validation
→ Room transaction
→ UI continues immediately
→ local change is queued for sync
→ network appears later
→ upload/pull can be retried
```

Серверная недоступность не блокирует создание Territory, ObservationPoint,
Bee или FlightCycle и не блокирует полевые изменения активной точки.

Для sync v1 рекомендуется отдельный infrastructure outbox, а не вычисление
pending по `updated_at` и не поле `sync_status` в каждой domain table. Одна
локальная domain transaction в будущем должна атомарно изменить materialized
Room row и добавить outbox mutation. Текущая schema не меняется до начала
реализации SyncEngine.

Состояния outbox — технические, например `pending`, `in_flight`, `acked`,
`conflict`; они не являются состояниями ObservationPoint или Bee. После
неопределённого сетевого результата mutation возвращается в retry, а не
считается потерянной.

## 6. Sync protocol

### 6.1. Timestamp-based approach

Вариант: клиент запрашивает все records с `updated_at > last_sync_time` и
отправляет собственные изменения тем же способом.

Плюсы:

- простая первая реализация;
- мало server infrastructure.

Минусы для Bee Search:

- часы телефонов могут быть неверными;
- одинаковые timestamp и пограничные сравнения могут пропустить change;
- server/client clock domains смешиваются с D051 domain timestamps;
- deletes требуют отдельной логики;
- трудно доказать полную и повторяемую доставку;
- timestamp не даёт безопасного conflict precondition.

Вывод: timestamp может оставаться domain/audit field, но не должен быть
основным sync cursor.

### 6.2. Monotonic server cursor/change log

Вариант: каждая принятая server mutation получает монотонный `change_seq` в
границах выбранной sync scope. Клиент хранит opaque cursor последнего полностью
применённого server batch.

Пример концептуального запроса:

```text
SyncRequest
- device_id
- last_server_cursor?
- mutations[]
    - mutation_id
    - entity_type
    - entity_id
    - operation
    - base_server_revision?
    - payload
```

Ответ:

```text
SyncResponse
- acknowledgements[]
    - mutation_id
    - entity_id
    - accepted_server_revision?
- conflicts/rejections[]
- remote_changes[]
- next_server_cursor
- has_more
```

Свойства:

- `mutation_id` уникален и обеспечивает idempotency retries;
- повтор одного request возвращает тот же acknowledgement;
- cursor продвигается только после локального применения всего полученного
  batch;
- server revision используется как optimistic concurrency precondition;
- `change_seq` задаёт transport order, а не меняет domain event timestamps;
- parent records отправляются раньше children, но acknowledgement остаётся
  per mutation, чтобы одна ошибка не заставляла повторять принятые changes.

### 6.3. Recommendation

Для Bee Search рекомендуется monotonic server cursor/change log. Это немного
сложнее timestamp polling, но прямо решает offline periods, retries, duplicate
batches, неправильные часы и tombstones. Cursor должен быть opaque для
клиента; сервер сможет изменить внутреннее представление без migration domain
records.

Порядок первого sync:

1. устройство регистрируется и аутентифицируется;
2. outbox mutations сортируются parent-first;
3. server idempotently принимает допустимые mutations;
4. client отмечает конкретные `mutation_id` как acknowledged;
5. client получает remote changes после своего cursor;
6. changes применяются одной локальной transaction на batch;
7. cursor сохраняется только после успешного commit;
8. незавершённый цикл повторяется с теми же mutation IDs.

## 7. Conflict policy

Универсальный last-write-wins не подходит исходным исследовательским данным.
Он может молча потерять исправление или заменить достоверное время значением с
телефона с неверными часами.

Рекомендуемая общая модель:

- создавать данные append-oriented, где это естественно;
- хранить на сервере materialized current revision и audit history;
- для update/delete требовать `base_server_revision`;
- автоматически принимать update, если base revision актуальна;
- при concurrent change не выбирать победителя по client timestamp;
- одинаковую mutation повторно acknowledge;
- конфликт содержательных полей передавать на explicit review.

| Entity | Вероятность concurrent edit | Policy v1 |
|---|---|---|
| `Territory` | Реальна после sharing | Optimistic revision; non-overlapping fields могут merge позднее, same-field/boundary conflict — manual review. LWW опасен |
| `Observer` | Низкая, но реальна на новом телефоне | Optimistic revision; identity merge только явно, code не служит merge key |
| `ObservationPoint` | Низкая при одном field author, выше при later review | Создание append-oriented; completion/correction как revision. Concurrent factual correction — manual conflict |
| `Bee` | Обычно редактирует origin phone до release | До первого sync unsynced removal может быть локальным; после sync или появления FlightCycle — revision/tombstone, не silent hard delete |
| `FlightCycle` | Низкая во время поля, возможна при review | Departure creates record; return/azimuth/correction are guarded changes. Concurrent timestamp/azimuth edit — manual conflict, не LWW |
| подтверждённый `Nest` | Реальна при совместном исследовании | Создание append-oriented; изменение location/status требует optimistic revision и provenance; merge по близости координат запрещён |
| `NestInspection` | Обычно один автор одной inspection | Новая inspection — отдельная record; correction как revision, а не перезапись другой inspection |
| custom point | Реальна после sharing | Optimistic revision; same-field location conflict требует review |
| custom line/path/polygon | Высокая для одновременного geometry edit | В v1 один editor lease/ownership или whole-object optimistic revision; автоматический vertex-level merge отложить |

Не нужно превращать Android в event-sourced систему. Room может хранить текущую
materialized row, а outbox — точную domain mutation. Server audit log сохраняет
accepted revisions и provenance.

После завершения ObservationPoint обычные field controls должны считать её
стабильной. Исправления истории выполняются отдельным явным workflow с actor и
reason; точная разрешённость полей остаётся открытым O006.

Особый известный конфликт: два телефона одного Observer могут создать
одинаковый `point_number` в одной Territory/year scope. UUID не конфликтуют.
Server может назначить/вычислить общий display ordering или предложить
renumbering без изменения UUID и полевых фактов, как уже допускает D056.

## 8. Deletion and versioning

### 8.1. Deletes

Рекомендуемое правило:

- **до sync:** разрешённое domain deletion объекта без истории может физически
  удалить локальную row и отменить ещё не принятую create mutation;
- **после sync:** delete передаётся как tombstone с entity UUID и base server
  revision;
- **на других устройствах:** tombstone удаляет объект из обычного UI, но не
  должен теряться из-за старого cursor;
- **на сервере:** физическая очистка возможна только после retention period,
  backups и понимания, что поддерживаемые clients получили tombstone;
- records с зависимой research history не удаляются каскадом только ради sync.

Это согласуется с текущими RESTRICT foreign keys и защищёнными удалениями
Territory/Observer/Bee. Конкретное `deleted_at` в Room сейчас не добавляется.

### 8.2. Domain timestamps vs sync metadata

Domain timestamps сохраняют смысл D051:

- `departure_time`, `return_time`, `created_at`, `completed_at` — абсолютные
  моменты field/domain events в epoch milliseconds;
- они не являются server cursor и не определяют победителя конфликта.

Минимальная sync metadata нужна позднее в infrastructure boundary:

- client `mutation_id`;
- origin `device_id`;
- entity type/UUID;
- operation and payload schema version;
- `base_server_revision` для updates/deletes;
- server revision и monotonic change cursor;
- server `received_at` для audit и clock anomaly detection.

Не обязательно добавлять `sync_status`, `server_id` и `server_revision` во все
domain tables. Их можно хранить в outbox/replica metadata tables keyed by
entity UUID. Это решение принимается вместе с реальным SyncEngine schema.

## 9. Territory sharing

### 9.1. Creation and promotion to shared state

Territory продолжает создаваться offline с client UUID. До sync она локальная.
После успешной загрузки сервер создаёт shared Territory с тем же UUID и
назначает membership/permissions account или research project.

Другие пользователи получают Territory через server change log. Их локальный
current Territory не переключается автоматически.

### 9.2. Human-readable code collisions

Текущая Room v5 требует уникальный Territory code на устройстве. Сервер не
должен объединять Territory только по одинаковому code. До определения
production sharing scope безопасная политика:

- одинаковый UUID означает одну Territory;
- одинаковый code с разными UUID означает потенциальный display conflict;
- импорт на устройство с collision требует явного alias/rename или conflict
  resolution;
- серверная уникальность, если нужна, задаётся в пределах research project,
  не глобально.

Немедленная Android migration для этого не нужна, пока shared Territory не
загружаются.

### 9.3. Territory boundary and map coverage

Эти понятия нельзя объединять автоматически:

- **Territory boundary** — будущая одна основная геометрия общей Territory;
- **map coverage** — один или несколько device-local fragments желаемого или
  установленного offline coverage.

Текущий BBOX-selection PoC доказал удобство viewport-based выбора boundary,
но production persistence ещё не существует. Текущий `MapCoverageSelection`
остаётся отдельной device-local DataStore infrastructure.

Если server-side PMTiles строится до принятия Territory boundary model,
`MapPackageRequest` должен содержать явный immutable geometry snapshot/bbox и
не выдавать device-local map coverage за синхронизируемое поле Territory.

## 10. Map package service

### 10.1. Lifecycle

```text
Territory or explicit geometry snapshot
→ map package request
→ durable server job
→ Planetiler + versioned field profile
→ immutable PMTiles artifact
→ checksum/metadata publication
→ resumable phone download
→ checksum verification
→ atomic local activation
```

Минимальная metadata:

| Field | Meaning |
|---|---|
| `package_id` | Server UUID конкретного immutable artifact |
| `territory_id` | Связь с shared Territory |
| `geometry` / `bbox` | Точный snapshot построенного покрытия |
| `territory_revision` | Revision boundary/request, если она существует |
| `profile_version` | Версия field profile и vector schema |
| `style_version` | Совместимая версия Android style contract |
| `osm_data_version` | Дата/идентификатор OSM extract |
| `minzoom`, `maxzoom` | Source zoom range |
| `byte_size` | Ожидаемый размер download |
| `sha256` | Проверка целостности и content identity |
| `status` | `queued`, `building`, `ready`, `failed`, `superseded` |
| `created_at`, `ready_at?` | Server lifecycle timestamps |
| `failure_code?` | Безопасный диагностический результат job |

PMTiles metadata и artifact server-owned. На телефоне authoritative для
наличия является фактически существующий файл с подходящим checksum; server
`ready` не означает, что пакет скачан на конкретное устройство.

### 10.2. Duplicate requests and regeneration

Request должен иметь idempotency key или deterministic request fingerprint из
territory/geometry revision, profile, OSM snapshot и zoom range. Повтор не
создаёт параллельный одинаковый job.

Новая генерация той же Territory создаёт новый `package_id`. Готовый старый
artifact не переписывается in place. Это позволяет телефону закончить download
и продолжать работать со старой картой.

### 10.3. Update policy

Пакет считается не текущим, если существует совместимый ready artifact для
более новой geometry/territory revision, profile/style contract или выбранного
OSM snapshot. Это не делает старый файл неработоспособным.

Server сообщает available update через manifest/API. В v1 пользователь явно
начинает update. Android:

1. оставляет текущий package активным;
2. скачивает новый во временный файл с HTTP Range/ETag support;
3. проверяет size и SHA-256;
4. атомарно активирует новую версию;
5. удаляет старую только отдельной безопасной storage policy.

Автообновление в поле не требуется. Отсутствие сети не запрещает работу со
старым совместимым PMTiles.

### 10.4. Acquisition-independent installed Map Package

`Map Package` — локально установленный, проверенный и совместимый artifact, а
не download job и не Android picker result. MapLibre boundary должна получать
только local URI/path активного package из private app storage.

```text
manual file import ─┐
                    ├→ acquire → stage in app-controlled storage
server download ────┘
                       → validate compatibility and integrity
                       → atomically activate
                       → render through MapLibre
```

Общий installation pipeline в будущем отвечает за:

- streaming input во временный app-private файл;
- безопасное имя файла, не зависящее от имени внешнего документа;
- проверку PMTiles header/version/tile type;
- проверку required metadata/profile compatibility;
- проверку bounds/zoom;
- вычисление byte size и SHA-256;
- отказ без изменения текущей карты при ошибке;
- атомарную активацию после полной проверки;
- сохранение старой рабочей версии до безопасной cleanup policy.

Отдельный copy step не является архитектурным инвариантом. Конкретный adapter
может stream/copy входные bytes прямо в staging, но не меняет общий lifecycle.
Каждый активированный package должен иметь достаточно identity/version metadata,
чтобы отличить installed artifact от совместимой замены; конкретный manifest
format остаётся открытым.

MapLibre не знает, был ли source Telegram attachment, USB file или Bee Search
API. Android renderer зависит от установленного package descriptor, а не от
transport.

### 10.5. Manual PMTiles import before server

Принятое промежуточное направление доставки:

```text
externally prepared *.pmtiles
→ Telegram / email / cloud / USB / Downloads
→ Android Storage Access Framework document picker
→ Bee Search reads granted content URI
→ common Map Package installation pipeline
→ private app storage
```

SAF позволяет пользователю выбрать файл без `MANAGE_EXTERNAL_STORAGE`, ADB,
`run-as` и знания private `filesDir`. Bee Search должна читать `content://` как
stream и копировать bytes внутрь приложения; внешний URI не является надёжным
долговременным runtime location.

Индивидуальные Territory packages не встраиваются в APK. Один APK обслуживает
разные Territory, users и packages. Import не запускает Planetiler и не требует
сервера.

Минимум metadata, необходимый уже для безопасного manual import:

- locally assigned/imported `package_id` или content-derived identity;
- PMTiles format/version и vector tile type;
- exact bounds;
- min/max source zoom;
- `profile_version`/style compatibility identifier;
- byte size и locally computed SHA-256;
- optional `territory_id`, если manifest достоверно связывает package;
- import/installation timestamp и local active/superseded state.

Нельзя доверять одному filename или введённому пользователем Territory code.
Если внешний PMTiles не несёт достаточного подписанного/встроенного manifest,
приложение показывает обнаруженные bounds/profile и требует явного выбора
Territory; это association metadata, а не доказательство происхождения.

До server delivery не обязательны:

- server job status;
- server revision/cursor;
- OSM snapshot identity, если её нет в package metadata;
- remote URL/ETag;
- server-created checksum signature.

Import UX и storage schema не реализуются этим документом. Перед реализацией
нужно отдельно утвердить supported PMTiles/profile compatibility contract и
поведение при package без Bee Search metadata.

### 10.6. Future server map delivery

Server path использует тот же installation pipeline:

```text
Territory + immutable geometry/profile request
→ server job
→ Planetiler
→ ready artifact manifest
→ resumable HTTPS download to staging
→ common validation/checksum
→ atomic activation in private storage
```

Отличаются только acquisition adapter и доверие к authenticated manifest.
Installed package descriptor и MapLibre rendering path остаются одинаковыми.
Это позволяет реализовать manual import независимо сейчас и заменить канал
доставки позже без переписывания map renderer.

## 11. User and domain geodata

### 11.1. Base map is not user data

PMTiles — immutable картографическая подложка. В неё не встраиваются:

- ObservationPoint;
- confirmed Nest/NestInspection;
- пользовательские ориентиры;
- собственные points, roads, tracks, cutlines, routes;
- editable lines/polygons/boundaries;
- текущие measurements и analysis overlays.

Перегенерация или замена base PMTiles не должна удалять, изменять или
перенумеровывать пользовательские объекты.

### 11.2. Canonical storage and rendering boundary

Правильное направление:

```text
Room/domain object
→ repository/domain Flow
→ map rendering adapter
→ GeoJSON/source/layer representation
→ MapLibre
```

MapLibre marker, annotation, source или style layer не является source of
truth. Process death восстанавливает отображение из canonical local records.
Edit сначала проходит domain validation/local transaction, затем Flow обновляет
map representation.

Будущие geo entities должны иметь client UUID, geometry в WGS84 или явно
versioned CRS contract, author/territory links и domain timestamps только по
реальной необходимости. Тип geometry сам по себе не доказывает, что нужна
отдельная domain entity: временный target, measurement preview и selection
frame остаются UI state.

### 11.3. Offline editing and publication

Создание/редактирование confirmed user geo objects должно работать offline.
Server позже добавляет sharing и aggregation. Полезно различать:

- local draft/transient measurement;
- confirmed local domain object pending sync;
- acknowledged shared object;
- conflict/review state в sync infrastructure.

Не каждый imported track обязан становиться shared canonical data. При import
пользователь/конкретный workflow выбирает: reference-only local overlay или
публикуемый research object.

Для первой shared geometry v1 предпочтительна whole-object optimistic revision.
CRDT и автоматический vertex-level merge преждевременны. Для сложной линии при
конфликте сохраняются обе candidate revisions и предлагается review.

## 12. Analysis data

### 12.1. Primary observations vs derived results

```text
ObservationPoint + FlightCycle + azimuth
→ versioned analysis algorithm
→ direction lines / intersections / candidate areas / statistics
→ rendering adapter
→ MapLibre or PC/Web view
```

Primary observations остаются canonical. Direction lines, intersections и
обычные statistics, которые дёшево и детерминированно пересчитываются, не
нужно синхронизировать как независимые field facts. Они могут быть локальным
cache с `input_revision_set + algorithm_version`.

Результат стоит сохранять как research artifact, если он:

- был явно подтверждён/интерпретирован исследователем;
- дорог или недетерминирован для повторного расчёта;
- использован в отчёте/публикации;
- должен воспроизводиться с конкретными inputs/parameters/software version.

Такой artifact хранит provenance: UUID, input references/revisions, algorithm
and profile version, parameters, creator (user/server/tool), created time и
optional checksum. Он не заменяет source observations.

### 12.2. Execution location

Архитектура допускает три execution adapters:

- Android — быстрый offline preview/field aid;
- server — объединённые data нескольких devices и durable jobs;
- PC/Web — interactive research exploration или reproducible export.

Одинаковый versioned algorithm/input contract должен давать сопоставимый
результат. Решение «весь analysis только на сервере» сейчас не требуется.

MapLibre отвечает за presentation, hit testing и interaction, но не становится
canonical storage analysis result.

## 13. Server architecture

### 13.1. Practical v1

```text
Android / Web
      │ HTTPS
      ▼
nginx / reverse proxy
      │
      ▼
Bee Search API
      ├── PostgreSQL + PostGIS
      ├── durable DB-backed job queue
      └── artifact file storage
              ▲
              │
      background worker + Planetiler
```

Нужно сразу:

- HTTPS reverse proxy;
- authenticated Bee Search API;
- PostgreSQL с PostGIS;
- transactional server change log и idempotency records;
- background worker для Planetiler;
- durable PMTiles storage с checksum;
- automated backups и restore test;
- structured logs/metrics минимального уровня.

Можно отложить:

- **Redis/message broker:** PostgreSQL job table с row locking достаточно для
  первого worker и небольшого потока задач;
- **S3-compatible storage:** локальный versioned filesystem/volume с backup
  достаточен для одного сервера и ранних PMTiles; object storage понадобится
  при media, нескольких app nodes или росте artifacts;
- **Kubernetes:** не нужен v1;
- **microservices:** API и worker могут быть одним deployable codebase с
  разными process entrypoints;
- **CDN:** не нужен до подтверждённой нагрузки.

Docker Compose полезен для повторяемых PostgreSQL/PostGIS, API и worker, но
Docker не должен быть условием понимания domain model и sync protocol.
Конкретный server framework/язык следует выбрать отдельным implementation
решением после фиксации API contract.

## 14. PostgreSQL/PostGIS role

PostgreSQL + PostGIS — рекомендуемый кандидат центральной БД, потому что Bee
Search объединяет транзакционные research records и spatial data.

Практическая польза:

- foreign keys, constraints и transactions для domain graph;
- JSONB только для versioned envelopes/неосновной metadata, не вместо schema;
- геометрия Territory/boundary и ObservationPoint;
- поиск points внутри Territory или bbox;
- distance/intersection queries;
- spatial indexes;
- совместная карта нескольких observers;
- будущий анализ azimuth rays и nest candidates;
- связь confirmed nests/inspections с картой;
- server change sequence и idempotency table рядом с transactional apply.

PostGIS не означает, что GPS coordinates нужно немедленно переносить из
текущих `latitude`/`longitude` Room fields или менять Android schema.

## 15. PC/Web role

ПК не должен получать отдельную неуправляемую «главную копию базы». Web/PC UI
работает через Bee Search API и server permissions.

Основные роли:

- review и conflict resolution;
- карта объединённых ObservationPoint;
- просмотр Territory, Observer, Bee и FlightCycle;
- provenance и audit history;
- analytics и derived results;
- запуск/просмотр map package jobs;
- export в CSV/GeoJSON и другие research formats.

Обычный UI не подключается напрямую к PostgreSQL. Прямой DB access допустим
только для ограниченного администрирования/аналитики с read-only credentials и
не является application contract. Export создаётся API/job и не заменяет sync.

## 16. Failure handling

| Failure | Правило поведения |
|---|---|
| Интернет пропал во время sync | Room state остаётся рабочим; не acknowledged mutations повторяются с теми же IDs |
| Batch отправлен дважды | Server idempotency по `mutation_id`; второй request возвращает прежний результат |
| Server недоступен несколько дней | Outbox растёт локально; field workflow не блокируется; UI показывает ненавязчивый sync health |
| Phone lost/replaced | Принятые server data восстанавливаются после login; unsynced local data без backup утрачены — нужен отдельный pre-server backup plan |
| PMTiles download оборвался | Временный файл + Range/ETag resume; активный старый package не удаляется |
| Server regenerated same Territory | Новый immutable package/version; старый остаётся usable до verified activation |
| App переустановлено с unsynced records | Android uninstall удаляет app data; server не может восстановить то, чего не получил. Нужны предупреждение и backup strategy |
| Телефонные часы неверны | Domain timestamp сохраняется с provenance; sync ordering использует server cursor; server flags anomaly, но не переписывает field time молча |
| Remote parent ещё не принят | Child mutation получает retryable dependency rejection; UUID и mutation ID сохраняются |
| Conflict после offline edits | Сервер сохраняет обе candidate revisions/diagnostics; current value не выбирается по client timestamp |
| Cursor response применён частично | Local transaction rollback; cursor не продвигается; batch повторяется |

## 17. Security and backup baseline

Минимум для первой реальной серверной версии:

- HTTPS для API и downloads;
- account authentication и короткоживущий access token/возобновляемая session;
- device registration и возможность отозвать потерянное устройство;
- authorization по research project/Territory membership;
- server-side input validation и audit actor/device;
- секреты вне APK и repository;
- регулярный PostgreSQL backup плюс проверенный restore;
- backup artifact storage и media metadata;
- SHA-256 и byte size для PMTiles/media;
- upload size/type limits и безопасные object names;
- retention policy для tombstones/audit/backups.

Account, Observer и device нельзя объединять в одну identity. Передача телефона
другому человеку требует явного выбора Observer, а при смене владельца — sign
out/re-registration; historical `observer_id` не меняется.

## 18. Android migration impact

### 18.1. MUST NOW

**Ничего.** Текущая Room v5 уже имеет необходимые foundational свойства:

- client-generated UUID как canonical IDs;
- UUID foreign keys;
- absolute `Instant`/epoch milliseconds;
- `created_at`/`updated_at` там, где они нужны текущему domain workflow;
- локальные transactions и constraints;
- отдельные Room research data, DataStore settings и map infrastructure.

Преждевременное добавление `server_id`, `device_id`, `sync_status` или
`sync_version` сейчас нарушило бы D041 и не решило бы ещё не реализованный
protocol.

### 18.2. PREPARE / SHOULD

- создавать новые syncable entities с UUID до persistence;
- не использовать display codes или list positions как identity;
- проводить все domain mutations через Repository/transaction boundary;
- не давать UI обходить domain operations прямыми DAO updates;
- сохранять raw field facts отдельно от derived analysis;
- не использовать `updated_at` как distributed ordering;
- версионировать будущие API payloads и map profile contracts;
- не смешивать Territory boundary с device-local map coverage;
- проектировать future media metadata отдельно от binary bytes.

### 18.3. DEFER

- Room outbox/replica metadata tables и non-destructive migration;
- generated `device_id` и device registration;
- account tokens/secure credential storage;
- server cursor persistence;
- mutation envelope serialization;
- tombstones и conflict UI;
- remote change application;
- background scheduling/network constraints;
- PMTiles manifest/download manager;
- media upload queue.

Перед реализацией sync нужен schema/API spike на копии реальной Room v5 базы и
миграционные тесты. После v5 никаких destructive reset для этой задачи.

## 19. Independent Android work before the server

Ближайшую Android/field разработку можно продолжать независимо от server v1,
если сохранять следующие границы:

| Функция | Можно до сервера? | Ограничение для будущего sync |
|---|---|---|
| PMTiles import через SAF | Да | Использовать общий acquisition-independent lifecycle D063; stage в app-controlled storage, validate и атомарно activate; renderer получает только active package |
| Territory boundary UX | Да, после отдельного domain/persistence decision | Один canonical boundary с UUID/revision-ready representation; не подменять `MapCoverageSelection` |
| Map coverage | Да | Оставлять device-local multi-fragment infrastructure, пока явно не принято sharing |
| Field observations | Да, приоритетно | Сохранять UUID, Repository transactions, raw facts и offline-first behavior |
| Custom points/ориентиры | Да, после определения domain meaning | Canonical local entity с UUID; MapLibre layer только projection |
| Roads/tracks/cutlines/routes | Да, после узкого domain decision | Geometry не в PMTiles; whole-object revisions; local draft отдельно от confirmed object |
| Polygons/boundaries | Да, после определения ownership | Явный geometry type/CRS/territory/author; без vertex CRDT v1 |
| физические объекты и `Inspection` | Domain decision принято (D088); server design ещё не начат | Отличать confirmed object, inspection и derived candidate; UUID/provenance |
| Local analysis | Да | Recomputable result маркируется input/algorithm version и не переписывает observations |
| Map overlays | Да | Строятся из canonical local state; MapLibre state не является persistence |

Практически более срочные field workflows не должны ждать server design.
Главное — не писать future sync assumptions внутрь каждой domain entity и не
создавать геообъекты только как MapLibre annotations.

## 20. Open questions and draft decisions

D063 уже принимает PMTiles как offline vector Map Package, общий
acquisition-independent lifecycle, разделение base map и user/domain geodata и
различие Territory boundary/map coverage. Следующие положения остаются
кандидатами, а не новыми `ACCEPTED` decisions:

1. **Canonical IDs:** client-generated UUID остаётся canonical server ID;
   отдельный `server_id` domain entities не вводится.
2. **Sync ordering:** opaque monotonic server cursor/change log вместо
   timestamp polling.
3. **Mutation delivery:** отдельный transactional local outbox с idempotent
   `mutation_id`.
4. **Conflicts:** optimistic server revisions + explicit conflict для
   содержательных concurrent edits; universal LWW запрещён.
5. **Central DB:** PostgreSQL + PostGIS.
6. **Map service:** future server-side Planetiler generation создаёт
   D063-compatible immutable versioned PMTiles; job/API/storage mechanics ещё
   не приняты.
7. **Auth identities:** account/user, Observer и device — разные identity.
8. **Deletes:** synced deletes распространяются tombstones; физическая очистка
   выполняется позже по retention policy.
9. **Provenance:** server enrichment/analysis не переписывает raw field data.
10. **Offline geo editing:** confirmed user geo objects имеют canonical local
    representation и позже синхронизируются; MapLibre остаётся renderer.
11. **Analysis separation:** recomputable analysis отделён от primary
    observations; durable result хранится только с input/algorithm provenance.

Перед implementation нужно отдельно решить:

- account ↔ Observer cardinality и workflow связывания offline Observer;
- research project/organization scope и роли;
- кто имеет право редактировать completed ObservationPoint;
- production Territory boundary geometry и revision model;
- server uniqueness scope для human-readable codes;
- granularity mutation payloads: whole-record revision или domain operation;
- conflict review UX и права reviewers;
- pre-sync local backup до появления полноценного сервера;
- PMTiles request source: Territory boundary, explicit coverage snapshot или
  оба варианта;
- retention и quota для PMTiles/media;
- server implementation language/framework and hosting.

## 21. Recommended implementation phases

Server не обязан быть следующим coding milestone. Порядок должен сохранять
приоритет реальной field usability.

### Phase A — Complete local field workflows

- продолжать ObservationPoint/Bee/FlightCycle UX и field verification;
- защищать Room v5 data non-destructive migrations;
- добавить pre-server backup/export safety, когда начнутся длительные реальные
  исследования;
- не блокировать field work ожиданием auth/server.

### Phase B — Local geodata and bounded analysis

- отдельно решить Territory boundary model;
- реализовывать только подтверждённые custom geo entities;
- хранить canonical geometry в local domain storage;
- строить MapLibre overlays через adapters;
- использовать versioned recomputable local analysis без изменения raw facts.

### Phase C — Manual Map Package import

- утвердить Map Package compatibility/metadata contract;
- SAF system picker;
- общий staging/validation/checksum/activation pipeline;
- private app storage и recovery/error tests;
- проверить import и полностью offline rendering на Samsung.

Этот этап можно выполнить до сервера, если практическая доставка карт другим
пользователям становится ближайшей потребностью.

### Phase D — Server decisions and foundation

- утвердить identity/ownership/conflict decisions из раздела 20;
- определить account, project membership и Territory sharing;
- описать versioned sync envelope/error taxonomy;
- PostgreSQL/PostGIS schema с UUID domain IDs;
- API auth/device registration;
- idempotency/change-log foundation и backups.

### Phase E — Controlled multi-device sync pilot

- non-destructive Room migration для outbox/replica metadata;
- ручное действие Sync для контролируемого пилота;
- upload одного dependency-complete graph;
- cursor pull и transactional apply;
- interruption, duplicate batch, conflict и wrong-clock tests;
- recovery на новом телефоне без сложного auto-merge.

### Phase F — Server Map Package delivery

- explicit package request с immutable geometry snapshot;
- PostgreSQL-backed worker + Planetiler;
- versioned artifact manifest/checksum;
- resumable Android download через общий installer;
- update/supersession/storage UX.

### Phase G — Shared PC/Web, media and durable analysis

- shared Territory/research project UI;
- web map/review/conflict/provenance/export;
- attachment metadata + object storage;
- resumable media uploads/checksums;
- confirmed Nest/NestInspection decisions;
- durable versioned analysis artifacts.

Первый sync pilot должен проверяться на реальном сценарии:
телефон неделю offline создаёт и исправляет связанные records, затем дважды
отправляет один batch, теряет сеть посередине pull и в итоге приходит к тому же
server/local state без дублей и потери исходных данных.

## 22. Direct answers

1. **Совместима ли текущая Android data model с multi-device sync?** Да.
   UUID, foreign keys, `Instant`, Repository transactions и разделение
   Room/DataStore/map infrastructure дают правильную основу.
2. **Нужно ли менять Room schema прямо сейчас?** Нет. Sync fields без API и
   SyncEngine были бы преждевременными и противоречили бы D041.
3. **Что заложить сейчас?** Не новые колонки, а правила: stable UUID, все
   mutations через transaction/repository, raw facts отдельно от derived data,
   MapLibre только как presentation, boundary отдельно от coverage.
4. **Может ли UUID остаться canonical?** Да. Client UUID становится тем же
   server entity ID; server numeric sequence нужен только для change log.
5. **Какой sync protocol подходит?** Idempotent mutation outbox + optimistic
   server revisions + opaque monotonic change-log cursor.
6. **Где возникают conflicts?** Shared Territory/boundary, historical factual
   corrections и editable shared geometry. Initial append-oriented field
   records обычно имеют одного origin author и конфликтуют редко.
7. **Как соотносятся spatial concepts?** Territory — domain context;
   Territory boundary — будущая canonical shared geometry; map coverage —
   device-local multi-fragment intent/readiness; PMTiles — immutable installed
   base-map artifact для конкретного geometry/profile snapshot.
8. **Как связаны manual import и server download?** Это два acquisition
   adapters одного staging/validation/checksum/activation pipeline. MapLibre
   получает один и тот же private local package.
9. **Как хранить user points/lines/roads?** Как canonical local domain records
   с UUID, geometry, author/Territory и необходимыми timestamps; MapLibre
   sources/layers пересоздаются из них. Не в base PMTiles.
10. **Что делать с derived analysis?** Дешёвое детерминированное — recompute/cache
    по input+algorithm version. Подтверждённое, дорогое или опубликованное —
    сохранять как отдельный artifact с provenance.
11. **Какой минимальный server v1?** Одна VPS допустима: HTTPS reverse proxy,
    Bee Search API, PostgreSQL/PostGIS, DB-backed worker queue, Planetiler,
    versioned file storage и backups. Без Kubernetes/Redis на старте.
12. **Что можно продолжать в Android?** Field workflows, manual PMTiles import,
    map coverage, boundary UX после решения модели, local analysis и узко
    определённые custom geo entities — все без server dependency.
13. **Что premature?** Per-row sync flags сейчас, server-issued replacement
    IDs, timestamp-only sync, universal LWW, event sourcing rewrite, geometry
    CRDT, Redis/Kubernetes/microservices, обязательный S3, embedding user data
    into PMTiles и server-only field/analysis workflow.
