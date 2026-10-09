# Temporal data model для research dates — утверждённый design

Статус: APPROVED · 2026-10-03

Утверждён владельцем как design research dates: schema-направление, migration policy и invariants
ниже. I1–I4 реализованы в baseline `61a6726` (2026-10-09); прежние execution notes ниже
сохраняются как исторические evidence соответствующих increments. I5 реализован и проверен:
SQL/repository support независимых включительных интервалов, без UI и schema migration.
I5: NO NEW INDEX по измерению; Room остаётся v13. I6 и I7 PENDING, этим increment
не реализованы. Samsung verification для I5 не заявляется; общий I7 device gate не закрыт.
Документ остаётся нормативным design, а не заявлением о завершении всех increments.

Ревизия после owner review: внесены два owner decisions — legacy Hollow/LogHive/Apiary получают
`fixation_date = NULL` без backfill (§11.2) и исправление `observation_date` через границу года
переносит точку в новую year scope с новым номером при неизменном UUID (§13.1). Удалён proposed
invariant о запрете future-valued research date; уточнена семантика `observation_date` (§4) и
разделены canonical temporal-filter date precision и event timestamp precision (§9.1);
late-entry weather переведён в минимальный invariant и отложенный design concern (§12).

Третья корректировка (owner domain clarification): legacy ObservationPoint policy пересмотрена.
`observation_date` остаётся **REQUIRED / NOT NULL** для нормальной durable model; для существующих точек
дата восстанавливается детерминированно из persisted `created_at` как local calendar date по прежней
project convention (§11.1). Конструкция «при расхождении выведенного года с `observation_year` →
`observation_date = NULL`» и неутверждённое правило сдвига к ближайшей границе года удалены: они
опирались на теоретический timezone edge case, несовместимый с реальным field workflow Bee Search.
Честный caveat про невосстановимую historical timezone сохранён и явно отделён от domain-informed
migration assumption (не математическая гарантия). Документ утверждён 2026-10-03.

Этот документ исследует, какие минимальные temporal fields нужны durable domain/data model Bee Search,
чтобы **уже принятое** D094 можно было реализовать корректно: обычный фильтр карты по периоду для
каждого research-data type по его canonical research date — и чтобы `created_at` не приходилось
использовать как суррогат исследовательской даты.

Первоначальное утверждение этого design не меняло Room/Kotlin/UI. Текущий I1 меняет только
ObservationPoint persistence/domain и legacy materialization; UI/Help остаются без изменений.
Новых `Dxxx` здесь не создаётся.

## 1. Scope / non-scope

**Scope:** temporal data model для research dates; последствия для Room entities, numbering,
backup/restore, export, будущего import/sync, late entry, отображения и фильтрации.

**Non-scope:** existence/historical semantics (D094 явно относит её к отдельной будущей задаче);
аналитические derived values; sync protocol; late-entry UI; Inspection entity; Apiary establishment
date; любые изменения schema в этом этапе.

## 2. Accepted semantics (источник — D094, здесь только применяется)

- Три разных понятия времени: real-world history ≠ research fixation/event date ≠ technical
  `created_at` (`docs/decisions.md:3106-3129`).
- Canonical research date по типам (`docs/decisions.md:3131-3139`): ObservationPoint → дата
  наблюдения; Hollow → дата фиксации; LogHive → дата фиксации; Apiary → дата фиксации
  исследователем; Inspection → дата осмотра.
- Обычный фильтр карты отвечает на вопрос «какие research events этого типа относятся к выбранному
  периоду», а не «какие физические объекты существовали в мире» (`docs/decisions.md:3144-3160`).
- Позднее внесение/импорт сохраняют исходную research date, не заменяя её моментом записи
  (`docs/decisions.md:3162-3176`).
- `ObservationPoint.created_at` — «current-workflow equivalence», а не долгосрочный invariant
  (`docs/decisions.md:3178-3188`).
- D094 §5 прямо оставляет открытым: **какая именно research date управляет `observation_year` и
  нумерацией** (`docs/decisions.md:3186-3188`). Это одна из тем настоящего design.
- Термины уже канонизированы в глоссарии: «дата наблюдения», «дата фиксации», «дата осмотра»,
  «год основания» (`docs/glossary.md:1014-1028`).

## 3. Current-state inventory

### 3.1 Temporal columns (фактически в schema v11)

| Entity | Column | Type | Null | Смысл сегодня | Кто пишет |
|---|---|---|---|---|---|
| Territory | `created_at`, `updated_at` | Instant (epoch ms) | no | техническое время записи | `RoomTerritoryRepository.kt:34,42,58` |
| Observer | `created_at`, `updated_at` | Instant | no | техническое | `RoomObserverRepository.kt:33,42,59` |
| ObservationPoint | `created_at` | Instant | no | техническое время; в текущем workflow совпадает с датой наблюдения | `RoomObservationRepository.kt:298,318` |
| ObservationPoint | `observation_year` | Int | no (default 0) | **сохранённый локальный календарный год**, назначен при создании, не пересчитывается | `RoomObservationRepository.kt:299,309` |
| ObservationPoint | `completed_at` | Instant? | yes | завершение наблюдения | `Daos.kt:375,404` |
| ObservationPoint | `initial_group_release_at` | Instant? | yes | legacy/восстановленные данные; DAO-писатель без вызывающих | `Migrations.kt:257-275` |
| FlightCycle | `departure_time`, `return_time` | Instant / Instant? | no / yes | реальные моменты события | `RoomObservationRepository.kt:384,412` |
| Bee, FlightCycle, media, attachments | `created_at` (+ `updated_at`) | Instant | no | техническое | см. `Entities.kt:308,341-342,172,254` |
| Weather | `sample_at`, `fetched_at` | Instant? | yes | время выборки погоды | `Daos.kt:443-444` |
| PhysicalObject (Hollow/LogHive/Apiary) | `created_at` | Instant | no | техническое время создания identity-записи | `RoomPhysicalObjectRepository.kt:298` |

Все Instant-колонки хранятся как epoch milliseconds (`RoomConverters.kt:19-22`, `BeeSearchDatabase.kt:28`).
**Ни одна колонка с датой/временем не имеет индекса.**

### 3.2 Что из research dates уже есть, а что отсутствует

```text
ObservationPoint   дата наблюдения — частично: есть год (observation_year),
                   дня/месяца в исследовательском смысле нет;
                   created_at содержит момент, но это technical time
Hollow             дата фиксации — ОТСУТСТВУЕТ
LogHive            дата фиксации — ОТСУТСТВУЕТ
Apiary             дата фиксации — ОТСУТСТВУЕТ
Inspection         сущности нет
```

Это соответствует `docs/data-model.md:1912-1927` («В текущей schema отсутствуют: дата фиксации
объекта…») и `docs/data-model.md:602-611` (created_at как текущая реализация даты наблюдения).

### 3.3 Единственная существующая «календарная» конверсия

`observation_year` — единственное место, где из Instant выводится календарное значение, и делается это
один раз при создании:

```kotlin
// app/src/main/java/org/beesearch/app/data/repository/RoomObservationRepository.kt:298-299
val createdAt = clock.instant()
val observationYear = createdAt.atZone(observationZoneIdProvider()).year
```

Зона инъектируется (`RoomObservationRepository.kt:70,80`, default `ZoneId.systemDefault()`); то же
сделано в миграции 1→2 (`Migrations.kt:43-49`).

### 3.4 Текущая timezone policy (важно для §9)

- Часовой пояс не сохраняется; для отображения используется текущий часовой пояс устройства
  (`docs/glossary.md:1010-1012`).
- Локальная зона используется в UI-форматировании (`PointsScreen.kt:573-576`,
  `PhysicalObjectCards.kt:351-352`).
- **UTC** используется в именах export-файлов (`ObservationPointExportFileName.kt:9`,
  `PhysicalObjectExportFileName.kt:27,40`) и в дате запроса погоды (`OpenMeteoWeatherProvider.kt:60`).

То есть проект уже смешивает local и UTC конверсии одного и того же Instant — это зафиксировано как
факт и учитывается в §9, но в этой задаче не исправляется.

## 4. ObservationPoint analysis

**Ответы на вопросы этапа.**

A. Где дата наблюдения фактически берётся из `created_at`?
В двух местах: (1) `observation_year` выводится из `created_at` при создании
(`RoomObservationRepository.kt:298-299`) и в миграции 1→2 (`Migrations.kt:43-49`); (2) UI показывает
дату точки форматированием `created_at` (`PointsScreen.kt:576`). Отдельного поля даты наблюдения нет.

B. Где year вычисляется/хранится отдельно?
Хранится как `observation_points.observation_year` Int NOT NULL (`Entities.kt:221`), вычисляется один
раз из `created_at` в локальной зоне и никогда не пересчитывается: все UPDATE его не трогают
(`Daos.kt:295-296,368-408`). Уникальность и нумерация используют именно его.

C. Когда присваивается номер точки?
В той же транзакции создания: `COALESCE(MAX(point_number),0)+1` по области
`territory_id + observation_year + observer_id` (`Daos.kt:350-363`,
`RoomObservationRepository.kt:262-304`). **High-water mark для точек не существует**: `MAX` берётся по
живым строкам, а точки можно удалять (`Daos.kt:325-348`).

D. Зависит ли uniqueness/numbering от календарного года?
Да, прямо: уникальный индекс
`(territory_id, observation_year, observer_id, point_number)` (`Entities.kt:211-214`,
`Migrations.kt:187-192`). Год входит и в область нумерации, и в ограничение (D056,
`docs/decisions.md:1139-1156`).

E. Что сломается, если 15.10.2026 ввести наблюдение, реально выполненное 20.08.2026?
Сегодня — ничего не сломается технически, но и записать это корректно нельзя: дата наблюдения
представлена только `created_at` (+ производный год), поэтому запись получит год 2026 и попадёт в
область 2026, а её настоящая исследовательская дата нигде не сохранится. Фильтр по периоду не сможет
отнести её к августу 2026: месяц/день берутся только из `created_at`, то есть из октября.
Если бы поздний ввод был реализован через правку `created_at`, это подменило бы техническую метку
исследовательской датой — прямое нарушение D094 §1.

F. Что произойдёт, если в 2027 импортируется observation, реально сделанный в 2025?
То же: `created_at`/`observation_year` будут 2027, а исходная дата наблюдения негде сохраниться.
D094 §4 требует обратного (`docs/decisions.md:3162-3176`).

G. Можно ли добавить explicit observation date, не меняя сейчас numbering semantics?
Да — при одном условии: если `observation_date` и `observation_year` всегда записываются вместе и
согласованно в одной транзакции, то для всех новых записей `observation_year == year(observation_date)`
по построению, и текущая логика нумерации (`MAX+1` по области с годом) продолжает работать без
изменений. Никакой конфликт с D056 не возникает: `observation_year` остаётся самостоятельным
сохранённым атрибутом (`docs/decisions.md:1139`), меняется только источник его значения.

H. Или numbering/year обязательно должны перейти на research date одновременно?
Обязательно — иначе появляется состояние, где `observation_date = 2025`, а `observation_year = 2026`,
и система не знает, что считать истиной. Именно поэтому recommendation (§19) вводит инвариант
согласованности в момент записи, а не «когда-нибудь потом».

**Что такое `observation_date` и чем она не является.**

`observation_date` — canonical research date точки, на которой строятся: temporal map filtering,
derivation of `observation_year` и membership точки в year-based numbering scope. Она отвечает на
вопрос «в какой локальный календарный день происходило наблюдение?».

Она **не** отвечает на вопрос «в какое точное время происходило наблюдение?» и **не заменяет**
существующие event timestamps внутри ObservationPoint: `FlightCycle.departureTime`,
`FlightCycle.returnTime`, `createdAt`, `completedAt` и любые другие временные факты реальных событий
остаются отдельными и продолжают существовать как раньше. Календарная дата — новое измерение
canonical research date, а не замена времени событий.

Из этого не следует, что «для ObservationPoint время суток не имеет исследовательского значения».
Точная формулировка: для canonical map date, года наблюдения и нумерации нужна календарная дата;
event-time semantics внутри точки остаются отдельными, существующими и этим design не затрагиваются.
Практическое следствие см. в §9.1 (разделение A/B) и §12 (погода и поздний ввод).

**Дополнительно:**

- `observation_year` уже участвует в фильтрации: `Daos.kt:309` (в запросе summaries) и `Daos.kt:355`;
  UI-фильтр года работает в памяти по сохранённому году (`PointsViewModel.kt:252-263`).
- Существующий тест уже фиксирует нужную семантику:
  `observationYearUsesLocalCalendarWithoutChangingCreatedAtInstant`
  (`app/src/androidTest/.../RoomPersistenceTest.kt:119`).
- Export точки выгружает `observationYear` и `createdAt` и не выводит ничего сам
  (`ObservationPointExportCodec.kt:175,178,235`).
- Backup сериализует `createdAt`, `initialGroupReleaseAt`, `completedAt` как epoch ms и не знает
  никакой research date (`BackupCore.kt:768-769`, `:722-723`).

## 5. Hollow analysis

- Entity: `physical_objects` (общая identity-строка, `Entities.kt:70-79`) + subtype `hollows`
  (`Entities.kt:118-127`); subtype не содержит временных полей.
- Создание: `RoomPhysicalObjectRepository.createHollow` (`:49-…`), `created_at = clock.instant()`
  (`:298`) в одной транзакции вместе с allocation номера.
- Edit: `Daos.kt:152` обновляет только координаты и характеристики; `hollows.name` добавлен в v11.
  `created_at` никогда не обновляется.
- Detail/cards: `created_at` только отображается (`PhysicalObjectCards.kt:121,141`).
- Numbering: `(territory_id, object_type, sequence_number)` (`Entities.kt:64-68`), high-water mark
  `physical_object_sequences` по `(territory_id, object_type)` (`Entities.kt:90-106`,
  `RoomPhysicalObjectRepository.kt:315-326`). **Номер не зависит от даты** (§13).
- Media: `physical_object_media.created_at` — время медиа, сортировка по нему (`Daos.kt:140,143`).
- Export single: `object.json` содержит `object.createdAt`; читатель строгий, неизвестный ключ —
  ошибка (`PhysicalObjectExportCodec.kt:504-507`).
- Collection export: `objects/<id>.json` того же вида (`PhysicalObjectExportCodec.kt:180-199`).
- Backup: `physical-objects` (`createdAt`), `hollows` (без дат) (`BackupCore.kt:762-763`).
- Tests: `PhysicalObjectNumberingAndDeletionTest.kt`, `PhysicalObjectDeletionBlockerTest.kt`,
  `PhysicalObjectReferenceRestrictTest.kt`, `PhysicalObjectExportCodecTest.kt`.

**Минимальное поле:** дата фиксации объекта. Ответы на вопросы этапа:

- **calendar date, не timestamp.** Ни одно существующее поведение объекта не использует время суток:
  ни numbering, ни сортировки (сортировка — по `sequence_number`, `Daos.kt:122`), ни export, ни
  отображение. Исследовательская семантика D094 — даты.
- **Nullable** (§11): для legacy-записей честное «неизвестно» предпочтительнее выдуманной даты.
- **Заполнение существующих записей** — решение владельца: legacy Hollow/LogHive/Apiary получают
  `fixation_date = NULL`; backfill из `created_at` не делается (§11.2).
- **Исправление даты фиксации** — да, пользователь должен уметь исправить (§12).
- **Export/import:** поле должно попадать в object snapshot; это меняет строгий key set и требует
  новой версии формата (§14).
- **Влияние на номер:** нет, подтверждено evidence (§13) — вопреки возможному предположению.

## 6. LogHive analysis

Отдельная проверка (не предполагать симметрию):

| Аспект | Hollow | LogHive | Идентично? |
|---|---|---|---|
| Identity/entity | `physical_objects` + `hollows` | `physical_objects` + `log_hives` | структура та же |
| Создание и `created_at` | `createHollow` | `createLogHive` | да: тот же `newIdentity` и `:298` |
| Numbering scope | `territory_id + object_type` | то же | да, тип входит в scope |
| Date в numbering | нет | нет | да |
| Export single | `object.json` (+`properties`) | тот же кодек, другой `properties` | да |
| Collection export | один и тот же archive layout | то же | да |
| Backup | `physical-objects` + `hollows` | `physical-objects` + `log_hives` | да |
| Subtype-поля | `tree`, диаметры… | `tree`, материал, высоты… | отличаются только характеристики |

Evidence: `Entities.kt:64-68,118-149`; `PhysicalObjectExportCodec.kt:202-220`;
`PhysicalObjectCollectionExportCodec.kt:10-19`; `BackupCore.kt:762-764`.

**Вывод:** temporal design для Hollow и LogHive может быть одинаковым, и более того — он должен быть
одним **общим столбцом** в `physical_objects`, потому что у обоих типов одна identity-таблица и один
и тот же смысл даты («дата фиксации», D094 §2). Отдельные столбцы по типам не нужны.

## 7. Apiary analysis

Фактическое состояние (проверено по коду):

- Данные существуют: таблица `apiaries` (`Entities.kt:176-187`), domain `Apiary`
  (`PhysicalObjects.kt:124`), backup (`BackupCore.kt:766`), часть repository API
  (`RoomPhysicalObjectRepository.kt:67-77`).
- **Пользовательского жизненного цикла нет:** создание не реализовано
  (`MainViewModel.kt:141`, `PhysicalObjectCreationViewModel.kt:158`,
  `PhysicalObjectCreationRoute.kt:120` → `error("Apiary creation is not part of this flow")`);
  список не открывается (`MainActivity.kt:227-228` — только `onOpenHollows`/`onOpenLogHives`);
  карточки нет; экспорт отказывает (`PhysicalObjectExportValidator.kt:60`).

Разграничение трёх дат (D094 §6, `docs/decisions.md:3190-3200`) остаётся в силе:

```text
дата фиксации пасеки исследователем  ≠  дата/год основания пасеки  ≠  created_at
```

**Что нужно уже сейчас:** только дата фиксации — она обслуживается тем же общим столбцом
`physical_objects`, что и Hollow/LogHive, и появится в API панели «Данные на карте» вместе с
жизненным циклом Apiary. Отдельного кода сейчас не требуется.

Lifecycle поля по типам:

```text
NEW Hollow / LogHive    fixation_date заполняется при создании записи
LEGACY Hollow / LogHive fixation_date = NULL («дата фиксации не известна»)
Apiary                  столбец существует на общем identity-уровне,
                        но полноценного user-facing lifecycle пока нет;
                        никакое creation/UI/API behaviour для Apiary этим документом
                        не проектируется сверх текущего scope
```

Отдельные fixation-поля в subtype-таблицах `hollows` / `log_hives` / `apiaries` не добавляются:
subtype-таблицы характеристик остаются без временных полей (§6).

**Дата/год основания — не проектируется на этом этапе** (necessity review): D094 §8 фиксирует это
как понятие, но не как поле (`docs/decisions.md:3209-3218`), а текущий accepted scope её не использует
(панель Apiary не показывает, фильтра по Apiary нет, entity не создаётся). Если она появится, её
точность («точно / только год / приблизительно / неизвестно») должна решаться вместе с жизненным
циклом Apiary, а не здесь.

## 8. Future Inspection — только temporal invariant

Фактическое состояние: `Inspection` в `app/src/main/java` отсутствует полностью. D088 откладывает
схему Осмотра (`docs/ideas.md:413-421`), I009 описывает посещения как отдельные dated-записи, каждая
из которых никогда не перезаписывает предыдущую (`docs/ideas.md:423-435`).

**Достаточно ли зафиксировать invariant, без резервирования структуры?** Да. Минимальное требование к
будущей модели:

```text
каждый Inspection несёт собственную дату осмотра, независимую от
(а) времени создания записи Inspection,
(б) даты фиксации родительского объекта,
(в) дат других осмотров того же объекта
```

Создавать таблицу, поля или константы сейчас не нужно: никакой accepted документ этого не требует, а
преждевременная структура была бы догадкой о ещё не определённой модели (necessity review).

## 9. Semantic precision vs storage representation

### 9.1 Требуемая семантическая точность

Точность нужно разделять на два независимых измерения.

**A. Canonical temporal-filter date precision** — то, чем оперирует обычный фильтр карты по периоду и
canonical research date из D094 §2:

| Type | Canonical date | Почему именно так |
|---|---|---|
| ObservationPoint | календарная дата (день) | D094 говорит о дате наблюдения; UI-фильтр работает на уровнях Год/Месяц/День (approved spec); год наблюдения и нумерация строятся на календарном годе |
| Hollow | календарная дата (день) | ни numbering, ни сортировка, ни отображение объекта не используют время суток |
| LogHive | календарная дата (день) | то же |
| Apiary | календарная дата (день) | то же; establishment date — отдельный future concern |
| Inspection | календарная дата (день) | D094 §7 — «дата осмотра»; этим документом фиксируется только требование собственной даты осмотра, без решений о будущей event-time модели Осмотра |

**B. Event timestamp precision** — времена реальных событий; это отдельное измерение, и оно не
заменяется canonical date:

| Type | Event timestamps | Что с ними делает этот design |
|---|---|---|
| ObservationPoint | `FlightCycle.departureTime`, `FlightCycle.returnTime`, `createdAt`, `completedAt`, времена weather snapshot | ничего: остаются отдельными и существующими; `observation_date` не является их заменой и не отменяет потребность в точном времени |
| Hollow / LogHive / Apiary | `physical_objects.created_at` (техническое), `physical_object_media.created_at` (время медиа) | ничего: остаются техническими/metadata; в исследовательском смысле объекта event-времён в текущем scope нет |
| Inspection | не определены | этим документом не проектируются |

Важно: из «canonical date — календарная дата» **не следует**, что время суток лишено
исследовательского значения. Следует только то, что canonical map date, год наблюдения и нумерация
строятся на календарной дате; event-time semantics остаются своими.

### 9.2 Representation

Выбор представления не должен определяться только тем, что Room уже использует epoch ms
(`RoomConverters.kt:19-22`). Проблема epoch-представления для календарной даты:

> `created_at`-подобный Instant — абсолютный момент. Календарная дата из него получается только через
> зону, а зона в проекте **не сохраняется** (`docs/glossary.md:1012`): при смене пояса устройства или
> при просмотре данных в другом поясе одна и та же фиксация 2026-08-31 может отобразиться как
> 2026-09-01. Пример в проекте уже есть: одна и та же величина конвертируется в local zone для года
> (`RoomObservationRepository.kt:299`), в UTC для имени файла (`PhysicalObjectExportFileName.kt:27`) и
> в UTC для запроса погоды (`OpenMeteoWeatherProvider.kt:60`).

Поэтому предлагается хранить research date как **zone-free значение календарной даты**, а не как
Instant.

Варианты представления:

| Вариант | Плюсы | Минусы |
|---|---|---|
| **ISO `YYYY-MM-DD` в TEXT** (утверждено) | zone-free; лексикографический порядок = хронологический, интервальные фильтры — обычные сравнения; читаемо в DB dump и в JSON export (где Instant и так пишется ISO-строкой, `ObservationPointExportCodec.kt:361`); не путается с epoch-ms колонками | строка вместо числа; нужен converter `LocalDate ↔ String` |
| `INTEGER` epoch-day | компактно, числовое семейство | новая единица рядом с epoch-**ms** колонками — источник ошибок; менее читаемо |

Конвертер для `LocalDate` в проекте появится впервые; `RoomConverters` пока умеет только
Instant ↔ epoch ms (`RoomConverters.kt:19-22`) — это implementation detail, а не часть решения.

### 9.3 Правило получения значения (важнее представления)

Для новых и явно исправленных записей значение research date фиксируется **один раз в момент ввода**
как локальная календарная дата устройства и далее является zone-free: оно не пересчитывается из
Instant при чтении, при отображении, при export и при backup. Это ровно тот приём, который уже принят
для `observation_year` (`docs/decisions.md:1139`: назначен при создании, позднее не пересчитывается).

Для legacy-записей, где дата никогда не вводилась пользователем, действует отдельное правило
миграции: `observation_date` восстанавливается один раз из persisted `created_at` как локальная
календарная дата по прежней project convention (legacy migration rule, §11.1). Это восстановление
explicit field из прежнего implicit representation, а не research fact, введённый пользователем.

Следствие: «фиксация была 2026-08-31, а стала 2026-09-01» становится невозможной по построению —
дата не выводится из момента, а хранится.

### 9.4 Совместимость с D051

D051 (`docs/decisions.md`, ACCEPTED) остаётся в силе: этим design он не переписывается и не
ослабляется. D051 требует, чтобы все сохраняемые **моменты времени** представляли абсолютный момент
события — `Instant` в Kotlin, Unix epoch milliseconds в SQLite.

Новые атрибуты этого design — `observation_date`, `fixation_date` и будущая `inspection_date` — **не
являются «моментами времени»**. Это zone-free calendar research-date attributes (§9.1A, §9.3), у
которых нет времени суток и зоны. Разделение по категориям значения:

```text
моменты времени / события                 calendar research dates
  FlightCycle.departureTime                 observation_date
  FlightCycle.returnTime                    fixation_date
  createdAt / completedAt                   future inspection_date
  physical_object_media.createdAt
  → Instant в Kotlin                        → zone-free calendar date
  → Unix epoch milliseconds в SQLite        → ISO YYYY-MM-DD
```

Хранение календарной даты как ISO `YYYY-MM-DD` **не заменяет и не нарушает** Instant/epoch-ms policy:
это другая категория значения, а не другой формат того же значения. Event timestamps продолжают
оставаться `Instant`/epoch ms (T5), `created_at` продолжает быть technical database creation time
(T1), и D051 продолжает применяться к ним без изменений.

Это editorial compatibility clarification, а не новое архитектурное решение: `docs/decisions.md` в
рамках этого этапа не изменяется, D051 остаётся ACCEPTED.

## 10. Timezone analysis

Ответы на вопросы этапа:

- timestamps сегодня — epoch ms (`RoomConverters.kt:19-22`), в дату преобразуются в двух местах:
  UI-форматирование в локальной зоне (`PointsScreen.kt:576`, `PhysicalObjectCards.kt:351`) и
  `observation_year` при создании/миграции (`RoomObservationRepository.kt:299`, `Migrations.kt:47-49`);
- используется **system timezone** по умолчанию (`ZoneId.systemDefault()`), с инъекцией для тестов
  (`RoomObservationRepository.kt:70`);
- explicit timezone/offset нигде не сохраняется (`docs/glossary.md:1012`);
- для research date timezone **не нужна**, если дата хранится как календарная величина (§9.3): зона
  нужна ровно в один момент — чтобы определить, какой сегодня день на устройстве в момент ввода;
- дата наблюдения/фиксации — это локальная календарная дата места исследования, какой её видел
  исследователь в момент события;
- при просмотре/экспорте в другом поясе хранимая дата не меняется (это и есть цель решения).

Остаётся честно назвать то, что решением не закрывается: **historical timezone не сохранялась и точно
не восстанавливается** — это уже признано проектом при миграции v1→v2 (`docs/data-model.md:1716`:
«Историческая зона v1 неизвестна и отдельно не восстанавливается»). Этот caveat не скрывается, но и
не превращается в обязательную сложность модели: для legacy ObservationPoint действует
domain-informed migration assumption о дневном полевом workflow (§11.1).

## 11. Legacy / migration analysis

### 11.1 ObservationPoint

Evidence, что backfill допустим: `docs/data-model.md:602-611` прямо утверждает, что в текущем workflow
запись создаётся в поле в момент наблюдения, поэтому `created_at` **используется как текущая
реализация даты наблюдения**. Плюс уже существующий прецедент: миграция 1→2 вывела `observation_year`
из `created_at` (`Migrations.kt:43-49`, `docs/data-model.md:1716-1718`).

**Это legacy migration rule, а не новая domain semantics.** Правило действует один раз, при
наполнении уже существующих строк, и не описывает, как дата ведёт себя дальше. После миграции:

```text
observation_date = source of truth для research calendar date точки
created_at       = database creation time (техническое время, как и было)
```

Owner-ratified execution clarification · 2026-10-08: то же единое legacy reconstruction rule применяется
при materialization Complete Backup V1–V6 и Snapshot V1, где ObservationPoint observationDate
исторически отсутствовала: LocalDate один раз выводится из persisted createdAt по прежней local
calendar convention. Для ObservationPoint результат всегда non-null; после materialization это
canonical date. Это не general fallback для будущих formats, обязанных переносить explicit date.

Accepted legacy archive limitation: архив без observationDate реконструирует дату в legacy
local-calendar convention timezone при materialization. При чтении в другом timezone около
границы суток результат теоретически может отличаться. Это compatibility limitation старого
format, а не новая canonical semantics. V6/Snapshot V1 writers fail closed, если canonical date
не равна legacy derivation createdAt в текущем timezone; guard не устраняет timezone limitation
последующего чтения архива.

I3 acceptance criterion для **versioned future format**: writer переносит explicit canonical
observationDate; reader использует это required поле, не пересчитывая его из createdAt;
malformed/missing required field → fail closed. Legacy derivation разрешена только для historical
formats без поля и только при one-time materialization. Это не правило «extra key wins» для
legacy format: Snapshot V1 остаётся closed schema без несанкционированного расширения.


Ни `created_at`, ни момент миграции не становятся исследовательской датой сами по себе: миграция лишь
переносит в новое поле то, что текущий workflow уже использовал как дату наблюдения. Тот же rationale
**не распространяется** на Hollow / LogHive / Apiary — там эквивалентного evidence нет (§11.2).

**Согласованность года и legacy backfill.** Точный исторический путь, которым у существующих точек
появился `observation_year`. Существуют ровно два пути, и оба берут **один и тот же** сохранённый
вход — `created_at` в epoch millis:

```text
путь 1 (создание точки, schema v2+)
    RoomObservationRepository.kt:298-299
    createdAt = clock.instant()
    observationYear = createdAt.atZone(observationZoneProvider).year
    zone = ZoneId.systemDefault() на момент создания

путь 2 (миграция v1 → v2, legacy строки)
    Migrations.kt:43-49
    zoneId = ZoneId.systemDefault() на момент миграции
    observationYear = Instant.ofEpochMilli(createdAt).atZone(zoneId).year
```

Никакой третий путь `observation_year` не пишет: все UPDATE его не трогают (`Daos.kt:295-408`), а
`defaultValue = 0` (`Entities.kt:221`) не может остаться у реальной строки, потому что и создание, и
миграция всегда записывают значение, а backup считает `0` некорректным (`BackupCore.kt:687`).

**Правило миграции (детерминированное, без NULL-ветки):**

```text
derived = DATE(created_at, локальная зона на момент миграции)
observation_date  = derived        для каждой legacy-точки
observation_year  : не переписывается
point_number      : не переписывается
```

`derived` вычисляется **той же самой project convention**, которая исторически определяла календарную
принадлежность точки: локальная календарная дата из persisted `created_at` (путь 1/путь 2 выше). Ничего
не выбирается произвольно и никакая дата не подгоняется: день берётся из того же входа, из которого
всегда бралась календарная семантика точки.

После миграции:

```text
observation_date  → canonical research calendar date точки
created_at        → database creation timestamp, и больше не source of truth
                     для календарной семантики точки
```

`created_at` используется как historical source **один раз** и не становится research date навсегда:
это восстановление explicit field из прежнего implicit representation, а не перенос технического
времени в исследовательскую дату. Именно поэтому та же операция **не** распространяется на
Hollow / LogHive / Apiary: у объектов прежнего implicit representation фиксации не существовало
(§11.2).

**Честный caveat.** Historical timezone действительно не сохранялась
(`docs/glossary.md:1010-1012`) и точно не восстанавливается (`docs/data-model.md:1716`). Поэтому
равенство `observation_year == year(observation_date)` после миграции не является *математической*
гарантией: она зависела бы от того, что зона устройства не менялась между двумя операциями.

**Domain-informed migration assumption (сознательное допущение, не математическая гарантия).**
ObservationPoint в существующем field workflow создаётся **во время реального наблюдения за летающими
пчёлами**, то есть днём и в сезон лёта. Точка около полуночи, около смены календарного дня и тем более
около Нового года не является реалистичным field scenario Bee Search; пример `2025-12-31` / `2026-01-01`
как основание для усложнения legacy migration не используется, потому что в это время пчёлы в данном
workflow не летают и ObservationPoint такого происхождения не возникает. Соответственно
timezone-day-boundary ambiguity **не считается practically relevant migration case**, и специальная
обработка для неё не вводится. Математически возможные, но несовместимые с field workflow timezone
counterexamples не превращаются в обязательную сложность domain model.

Проверено: конкретного repo-supported сценария, совместимого с field workflow, в котором равенство
`observation_year == year(observation_date)` невозможно сохранить без выдумывания даты, **не
обнаружено**, поэтому STOP-условие не наступило.

**Удалено:** (а) правило «при mismatch сдвинуть `observation_date` к ближайшей границе сохранённого
`observation_year`» (например `2026-01-01` → `2025-12-31`) — оно фабриковало конкретный день;
(б) NULL-ветка «если `year(derived) != observation_year` → `observation_date = NULL`» — она вводила
специальное unknown-состояние ради теоретического timezone mismatch. Ни одна из них ничем не заменена:
выдуманная дата не используется, а nullable-состояние для legacy-точек не создаётся.

**Не вводятся** ради theoretical timezone ambiguity: nullable `observation_date` для legacy, special
unknown state для ObservationPoint, repair workflow, sentinel, approximate dates, precision enum,
отдельная migration phase, timezone-history storage.

### 11.2 Hollow / LogHive / Apiary

Evidence, что backfill **не** доказан: `docs/data-model.md:1912-1927` описывает `created_at` как
техническую метку identity-записи и перечисляет дату фиксации среди **отсутствующих** данных. Ни один
authoritative документ не утверждает, что `created_at` объекта равен моменту фиксации (в отличие от
строки про ObservationPoint).

Поэтому:

- **решение владельца: backfill не делается.** `fixation_date` nullable; для всех существующих строк
  остаётся `NULL`, что означает «дата фиксации не известна» (legacy-запись). Никакой выдуманной
  research date не появляется;
- причина решения: repo не доказывает, что `created_at` равен реальной дате фиксации объекта, —
  техническое время создания записи нельзя превращать в исследовательскую дату задним числом;
- отклонены: backfill из `created_at`; `NOT NULL` с sentinel-значением «неизвестная дата»;
  precision enum или любая другая partial-date machinery. Дополнительно к этому документу не
  добавляются ни special legacy date, ни отдельный флаг «дата неизвестна сверх `NULL`»;
- отличие от ObservationPoint намеренное: там backfill опирается на документированное evidence
  (§11.1), здесь эквивалентного evidence нет.

### 11.3 Что даёт `NULL` на практике

Для ObservationPoint `NULL` не используется вообще: после миграции `observation_date` заполнена у
каждой точки (§11.1). Единственный `NULL` — legacy fixation date физического объекта:

```text
legacy physical object с fixation_date = NULL

«Всё время»            → запись видна
конкретный интервал    → запись НЕ попадает в результат, пока research fixation date неизвестна
```

Это не ошибка и не потеря данных: запись существует, отображается на карте при отсутствии ограничения
по периоду и остаётся доступной в списках. Это честное следствие того, что research fixation date
неизвестна. Такое поведение видимо пользователю и должно быть отражено в UI-спецификации на этапе
интеграции (в approved spec сейчас такого случая нет, потому что поля не существовало).

## 12. Late entry consequences

Инвариант (T7): **время создания записи ≠ дата research event; research date переносится вместе с
записью и никогда не подменяется моментом ввода, импорта или синхронизации.**

Последствия по типам:

| Type | Последствие |
|---|---|
| ObservationPoint | появляется возможность указать реальную дату наблюдения; год и номер выводятся из неё (§4G); `created_at` остаётся техническим |
| Hollow / LogHive | то же для даты фиксации; на номер не влияет |
| Apiary | то же, когда появится жизненный цикл |
| Inspection | то же по построению (собственная дата осмотра) |

**Минимальный invariant про время при позднем вводе (T8).** `created_at` и момент позднего ввода
**не могут автоматически считаться** ни временем реального наблюдения, ни временем погодных условий
наблюдения. Поздний ввод не должен создавать ложное утверждение «погода в момент ввода = погода в
момент наблюдения»: если temporal information недостаточно, чтобы получить корректный weather
snapshot, такой snapshot **не фабрикуется** из времени ввода.

Это ограничение, а не спроектированное решение. Конкретика — late-entry UI для времени, отдельное
`observation_time` field, восстановление старой погоды, API/retry strategy, snapshot status model,
выбор используемого timestamp, редактирование времени — **отложена** как отдельный future
late-entry/weather design concern и не является blocker’ом текущей temporal schema. См. §24 и §23
(отдельная будущая задача, не спрятанная внутрь increments этого design).

## 13. Numbering consequences

### 13.1 ObservationPoint

```text
research date
   → observation_year (материализованный атрибут, D056)
   → область нумерации territory + year + observer
   → point_number (MAX+1 по живым строкам)
   → UNIQUE (territory, year, observer, number)
```

Изменение: источником значения `observation_year` становится выбранная дата наблюдения, а не момент
создания записи. Структура области, ограничение и алгоритм `MAX+1` не меняются. D056 при этом **не
переоткрывается**: он говорит, что `observation_year` — самостоятельный сохранённый атрибут, который
не пересчитывается позднее из `created_at` (`docs/decisions.md:1139`); предложение лишь уточняет, из
чего он берётся в момент назначения — что D094 §5 прямо оставляет открытым.

**Решение владельца: исправление даты через границу года.** Если `observation_date` исправляется так,
что календарный год меняется:

```text
observation_year   := год новой observation_date
membership         := точка переходит в соответствующую year-based numbering scope
point_number       := новый номер, назначенный по правилам этой новой scope
UUID               := не меняется
```

Состояние `observation_date = 2025`, `observation_year = 2026` не является нормальным и не
сохраняется. Варианты «заморозить старый год и номер» и «запретить исправление из-за перехода через
год» отклонены.

Детали, которые из этого следуют:

- **Atomicity requirement.** Изменение даты, пересчёт `observation_year`, назначение нового
  `point_number` и сохранение строки выполняются в **одной транзакции**. Промежуточное состояние
  (например год уже новый, а номер ещё из старой scope) не должно ни сохраняться, ни наблюдаться
  снаружи. Строка не удаляется и не пересоздаётся — это обновление существующей строки.
- **Uniqueness consequence.** Уникальность обеспечивается парой (год, номер) в рамках
  `(territory_id, observation_year, observer_id, point_number)` (`Entities.kt:211-214`), поэтому
  прежний `point_number` в новой scope может быть занят: сохранять его нельзя. Номер выдаётся тем же
  правилом, что и при создании — `COALESCE(MAX(point_number),0)+1` по новой scope
  (`Daos.kt:350-363`) — в той же транзакции. Прежний номер в старой scope освобождается; повторное
  использование освободившихся номеров — уже существующее свойство текущей политики (для точек нет
  high-water mark, удаление строки освобождает номер, `Daos.kt:325-348`), и этот design его не
  меняет.
- **UUID stability.** UUID остаётся идентичностью точки и не меняется: номер не является
  идентификатором и может быть перенумерован без изменения исследовательских данных (D056,
  `docs/decisions.md:1158-1160`). Никакая новая строка не создаётся, ссылки Bee/FlightCycle/медиа на
  точку остаются валидными.
- **Что происходит с `point_number`.** Он заменяется на следующий свободный номер в новой scope.
  Прежний номер нигде не сохраняется как исторический факт: это отображаемый последовательный номер, а
  не исследовательские данные. Если год не изменился (исправление внутри года), номер не меняется
  вообще.
- **Numbering policy не переоткрывается.** Логическая форма scope остаётся прежней
  (`territory + year + observer`); меняется только membership точки в scope, потому что изменился её
  canonical observation year. Allocator не проектируется этим документом.

Годовая фильтрация уже покрыта индексом: уникальный индекс начинается с
`(territory_id, observation_year, …)` (`Entities.kt:211-214`), а запросы фильтруют по
`observation_year` (`Daos.kt:309,355`).

### 13.2 Hollow / LogHive / Apiary

Номер не зависит от даты: область — `territory_id + object_type` (`Entities.kt:64-68,100`), allocator
использует только эти два значения (`RoomPhysicalObjectRepository.kt:315-326`), high-water mark
`physical_object_sequences` не содержит даты (`Entities.kt:90-106`), designation — префикс + номер
(`PhysicalObjects.kt:12-15`). Дата фиксации на нумерацию не влияет и менять numbering policy не
требуется.

## 14. Export consequences

Owner I4 execution · 2026-10-08: все три portable export profiles используют versioned **V2**.
SINGLE_OBSERVATION_POINT требует explicit canonical observationDate; SINGLE_PHYSICAL_OBJECT и
PHYSICAL_OBJECT_COLLECTION требуют nullable fixationDate key для каждого объекта. Новые readers
не выводят dates из createdAt; missing/malformed/wrong-type dates отвергаются. V1 readers сохраняют
legacy semantics (point reconstruction / physical NULL), V1 writer paths сохраняют loss-prevention
guards. Это заменяет рекомендацию optional-key evolution для ObservationPoint ниже; code audit
ниже описывает pre-I4 V1. [Export V2 contract](temporal-export-v2.md).

### 14.1 Single physical object (`SINGLE_PHYSICAL_OBJECT` v1)

- `object.json` содержит `object.createdAt`; читатель **строгий**: полный key set, любой новый ключ —
  ошибка (`PhysicalObjectExportCodec.kt:504-507`, тест `PhysicalObjectExportCodecTest.kt:152-158`).
- `formatVersion` проверяется на точное равенство; более новая версия отвергается
  (`PhysicalObjectExportCodec.kt:108-110`).

Следствие: добавление `fixationDate` в `object.json` **является breaking change** для сегодняшних
читателей, даже если значение null. Поэтому в increment экспорта потребуется: новая версия формата,
явное решение о чтении старых пакетов (v1 → поле отсутствует → «неизвестно») и обновление
`docs/physical-object-export-v1.md`. Сам формат в этой задаче не меняется.

Отдельно: имя файла использует `created_at` в UTC (`PhysicalObjectExportFileName.kt:27`). Это не
research date, и подменять её датой фиксации в имени файла не следует — иначе изменится уже
зафиксированная конвенция имён и её тесты (`PhysicalObjectExportFileNameTest.kt:19-38`).

### 14.2 Collection (`PHYSICAL_OBJECT_COLLECTION` v1)

Тот же строгий key set для манифеста, `territory.json`, `observers.json`, payload объекта и его
`properties` (`PhysicalObjectCollectionExportCodec.kt:385-387`). Следствие то же: новая версия формата
и решение о чтении старых архивов. Никакие временные поля в манифест и описатели добавлять не нужно —
там дат нет вовсе.

### 14.3 ObservationPoint (`SINGLE_OBSERVATION_POINT` v1)

Читатель **терпим к неизвестным JSON-ключам** (нет `requireKeys`, только извлечение нужных ключей —
`ObservationPointExportCodec.kt:219-232,352`). Следствие: добавление `observationDate` в `point.json`
не ломает сегодняшних читателей; новая версия формата не обязательна. Но новое чтение старых пакетов
должно считать ключ **опциональным**, потому что в них его нет. Строгим остаётся набор ZIP-entries
(`:126-131`), и он не меняется.

## 15. Backup / restore consequences

Owner I3 execution · 2026-10-08 supersedes the optional-key evolution recommendation below:
Complete Backup **V7** / archive schema **7** and Snapshot **V2** carry REQUIRED canonical
observationDate and REQUIRED nullable fixationDate as ISO YYYY-MM-DD. New-version readers use
only those explicit fields; missing/malformed/wrong-type fields fail closed, never legacy fallback.
V1–V6 Complete Backup and V1 Snapshot keep one-time legacy ObservationPoint reconstruction and
NULL physical fixationDate. See [Complete Backup contract](backup-format-v1.md) and
[Snapshot V2](snapshot-v2-wire-schema.md). The code audit below describes the pre-I3 baseline.

Backup — другая система, чем export: полный архив research-базы с манифестом, 15 обязательными
коллекциями и SHA-256 каждого payload (`BackupCore.kt:390-397`).

- Даты в backup кодируются **только** как epoch ms (`BackupCore.kt:782,743-744`), ISO там нет.
- Версии: `backupFormatVersion = 6`, `archiveSchemaVersion = 6`; читатель принимает `format in 1..6`
  (`BackupCore.kt:403-404`); `collectionSchemaVersion` должен быть `1` для известных коллекций
  (`BackupCore.kt:453`).
- Отсутствующий ключ в записи — фатально (`BackupCore.kt:733,743-744`); **лишний ключ в записи
  игнорируется** (нет правила, отвергающего неизвестные поля записи); набор ZIP-entries строгий
  (`BackupCore.kt:467`).
- Invariants порядка дат уже проверяются (`BackupCore.kt:608-609,689,703,708`).
- Restore отказывается работать в непустую research-базу (`BackupCore.kt:134-136`).

Следствия для нового поля:

- legacy physical-object fixation date без ключа восстанавливается с `NULL`/«неизвестно».
  ObservationPoint — отдельное REQUIRED правило: старые representations без observationDate
  materialize с legacy reconstruction из persisted createdAt (§11.1), не с NULL. В I1 writers
  остаются прежними; explicit date carriage и compatibility будущих formats относятся к I3.
  Возможность добавлять optional keys в Complete Backup не распространяется на frozen closed
  Snapshot V1: его wire evolution требует отдельного I3 решения;
- поднимать `collectionSchemaVersion` **не требуется** (и это важно: значение ≠ 1 сейчас фатально,
  `BackupCore.kt:453`); поднимать `backupFormatVersion` тоже не требуется, поскольку запись
  становится шире, а не другой;
- если владелец предпочтёт явное версионирование, альтернатива — `backupFormatVersion = 7` и приём
  `1..7`; это отдельное решение, а не необходимость;
- существующие migration-тесты (`BeeSearchMigrationTest.kt`) и backup-тесты должны быть расширены
  проверкой нового поля, включая восстановление архива без него.

## 16. Future import / sync consequences

I015 — идея, не реализация: stable UUID как identity; будущие revisions/change tracking; никаких
sync-specific полей сейчас (`docs/ideas.md:690-722`).

Предлагаемая модель совместима с ней:

- research date — обычный атрибут записи, поэтому «research date travels with the record»
  выполняется автоматически; import/sync не может подменить её временем приёма (T7);
- никаких `syncStatus`, `lastExportedAt`, `serverId`, tombstones не добавляется — прямой запрет I015
  соблюдается;
- stable UUID и существующие FK не меняются, поэтому будущая модель изменений может строиться поверх
  без переделки temporal полей.

## 17. Query / index consequences

Будущий запрос Unified Territory Data Map: **Territory + тип + интервал research date**.

- Сегодня ни одна колонка с датой не индексирована (§3.1).
- Для ObservationPoint годовой уровень фильтра уже покрыт префиксом уникального индекса
  `(territory_id, observation_year, …)` (`Entities.kt:211-214`); месячный и дневной уровни — нет.
- Для физических объектов индекс по дате отсутствует; выборка идёт по `territory_id + object_type`
  (индексы `Entities.kt:65,67`).

Рекомендация: **добавить колонку сейчас, индекс — не сейчас.** Составной индекс
`(territory_id, fixation_date)` / `(territory_id, observation_date)` добавляется в том increment,
который реально вводит интервальный запрос, и только если простое измерение на реальном объёме
покажет необходимость. Основание: объёмы полевых данных на Territory таковы, что сканирование
недорого, а индекс по дате без запроса — преждевременная оптимизация. Триггер для пересмотра
зафиксирован здесь явно, чтобы решение не потерялось.

### I5 execution — 2026-10-09

`ResearchDateInterval(fromDate: LocalDate, toDate: LocalDate)` валидирует обе границы через
существующий `parseResearchDate` и отвергает `fromDate > toDate` с `IllegalArgumentException`.
`null` interval означает отсутствие temporal restriction; single-day interval допустим.
Calendar dates сравниваются в SQL как сохранённые ISO YYYY-MM-DD TEXT, без Instant/timezone.

- `ObservationRepository.observeObservationPointSummaries(territoryId, observationYear,
  dateInterval)` сохраняет Territory/year predicates, JOIN/aggregate counts, Flow invalidation
  и ordering `created_at DESC, id`. Бounded ветвь DAO использует только `observation_date >=
  fromDate AND observation_date <= toDate`; unbounded ветвь сохраняет прежний SQL.
- `PhysicalObjectRepository.listForTerritory(territoryId, hollowDateInterval,
  logHiveDateInterval)` принимает независимые интервалы. Если оба null, сохраняется прежний
  общий запрос. Иначе identities отбираются SQL отдельно по Territory/type, с прежним порядком
  `sequence_number, id`, до subtype/media hydration в существующей transaction.
  Бounded Hollow/LogHive используют `fixation_date`; NULL исключается сравнением SQL.
  Unbounded тип сохраняет legacy NULL. Apiary остаётся на прежнем unbounded path, без новой
  fixation-date capability; Inspection не создаётся. Kotlin `.filter` используется только для
  прежней сборки subtype lists, не для temporal filtering.

Новые intervals не подключены к UI, map overlays, сохранённому filter state или global period.
Existing callers по умолчанию остаются unbounded. I1–I4 dates/numbering/wire semantics не меняются.
Измерение и решение по индексу: [I5 query measurement](temporal-i5-query-measurement.md).

## 18. Design options

### 18.1 ObservationPoint

| Option | Schema concept | Semantic correctness | Migration | Late entry | Export/backup | Complexity | Future |
|---|---|---|---|---|---|---|---|
| **A** | добавить `observation_date`, year/numbering продолжают считаться от `created_at` | ломается при позднем вводе: появляется `observation_date ≠ year` без правила истины | backfill | поддержан формально, но год/номер врут | как в §14/§15 | низкая | требует переделки позже |
| **B (выбран владельцем)** | `observation_date` — canonical; `observation_year` материализуется из неё при создании и при исправлении | корректна: одна истина для research date; год и номер согласованы с датой в любом пути записи | детерминированный backfill даты из `created_at` по прежней local-calendar convention (§11.1) | поддержан полностью | как в §14/§15 | средняя (согласованная запись + правило исправления через границу года, §13.1) | не блокирует sync/existence |
| **C** | отказаться от `observation_year`, выводить область из `observation_date` | требует переоткрытия D056 и замены ключа уникальности на производный | тяжёлая | поддержан | меняет export (`observationYear` уже экспортируется) | высокая | не оправдана сейчас |

### 18.2 Physical objects

| Option | Schema concept | Semantic correctness | Migration | Late entry | Export/backup | Complexity |
|---|---|---|---|---|---|---|
| **A** | `fixation_date` NOT NULL, backfill из `created_at` | утверждает факт, который ни один документ не подтверждает | backfill | поддержан | §14/§15 | низкая |
| **B (выбран владельцем)** | `fixation_date` NULL для legacy, без backfill | честно: «неизвестно» не превращается в дату; техническое время записи не становится research date | без backfill, все legacy-строки остаются NULL (§11.2) | поддержан | §14/§15 | низкая + один новый видимый случай (запись не попадает в ограниченный период, §11.3) |
| **C** | NOT NULL + sentinel «неизвестная дата» | точность внутри значения | backfill/sentinel | поддержан | сложнее | выше — не оправдана |

### 18.3 Representation

ISO `YYYY-MM-DD` TEXT (утверждено) против epoch-day INTEGER (§9.2).

## 19. Approved simplest sufficient design

```text
observation_points
    observation_date   календарная дата (день), NOT NULL, ISO YYYY-MM-DD,
                       обязательна для каждой точки — и новой, и восстановленной миграцией;
                       для legacy-точек восстанавливается детерминированно из created_at
                       по прежней local-calendar convention (§11.1);
                       записывается вместе с observation_year в одной транзакции создания;
                       при исправлении даты через границу года год, membership в year scope
                       и point_number обновляются атомарно (§13.1)
    observation_year   остаётся как есть; значение выводится из выбранной даты наблюдения
                       (D056 сохраняется: атрибут не пересчитывается позднее из created_at)

physical_objects
    fixation_date      календарная дата (день), NULL,
                       заполняется при создании новых Hollow/LogHive;
                       legacy Hollow/LogHive/Apiary остаются NULL = «дата фиксации не известна»,
                       backfill из created_at не делается (§11.2);
                       обслуживает также Apiary, когда у неё появится жизненный цикл

Inspection             полей не появляется; фиксируется только invariant (§8)

created_at             нигде не переопределяется и остаётся техническим временем записи
```

Почему это минимально достаточно:

- закрывает все пять типов из D094 §2 (четыре полем, пятый — инвариантом);
- даёт Unified Territory Data Map корректную основу для фильтра Год/Месяц/День по каждому типу;
- не требует ни одной новой сущности, таблицы, enum или precision-machinery;
- не меняет numbering policy, identity, FK и delete rules;
- явно различает canonical calendar date и event timestamps: `observation_date` ничего не заменяет в
  event-time semantics точки (§4, §9.1B);
- **не меняет поведение сегодня**: без UI позднего ввода дата наблюдения всегда равна сегодняшней
  локальной дате, то есть ровно тому году, который и так назначается сегодня. Schema-инкремент
  можно выпустить раньше UI без изменения наблюдаемого поведения.

Проверка рабочей гипотезы из задания: ObservationPoint получает explicit observation date (да), Hollow
и LogHive — fixation date (да), Apiary — fixation date (да, тем же столбцом и без кода сейчас),
Inspection — только invariant (да). Отличий от гипотезы нет, кроме одного уточнения: **гипотеза
требует одновременно определить relationship даты с year/numbering, иначе она несовместима с D094 §4**
(§4G–H, §13.1).

## 20. Proposed temporal invariants

Помечено происхождение: `[D094]` — уже accepted, здесь только применяется; `[owner decision]` —
решение, принятое владельцем при review этого документа; `[proposed]` — предложение, ожидающее
финального approval вместе с документом.

```text
T1 [D094]  created_at фиксирует время появления записи в БД и не является canonical research date.
T2 [D094]  canonical research date по типам: ObservationPoint — дата наблюдения;
           Hollow / LogHive — дата фиксации; Apiary — дата фиксации исследователем;
           Inspection — дата осмотра.
T3 [proposed] research date — zone-free календарная дата (день), зафиксированная один раз в момент
           события в локальном календаре места исследования; она не пересчитывается из Instant при
           чтении, отображении, export, backup или import. Для legacy-записей, где дата не
           сохранялась, explicit field восстанавливается один раз при миграции из persisted
           created_at по прежней local-calendar convention (§11.1).
T4 [owner decision] для ObservationPoint observation_year == year(observation_date):
           оба значения пишутся согласованно при создании и обновляются согласованно при исправлении
           даты; created_at никогда не участвует в пересчёте observation_year. Инвариант действует
           для каждой точки, включая восстановленные миграцией: observation_date обязательна и
           заполнена у каждой legacy-строки (§11.1).
T5 [owner decision] observation_date не заменяет event timestamps точки: FlightCycle.departureTime,
           FlightCycle.returnTime и прочие временные факты реальных событий остаются отдельными
           и не выводятся из календарной даты.
T6 [owner decision] fixation_date физического объекта — nullable календарная дата; NULL означает
           «research fixation date не известна» (legacy) и никогда не фабрикуется из created_at;
           fixation_date не участвует в identity, нумерации и delete rules объекта.
T7 [proposed] ни запись, ни поздний ввод, ни импорт, ни синхронизация не подменяют research date
           моментом создания, ввода или приёма данных.
T8 [proposed] при позднем вводе created_at и момент ввода не считаются автоматически ни временем
           реального наблюдения, ни временем погодных условий наблюдения; weather snapshot
           не фабрикуется из времени ввода при недостатке temporal information.
T9 [proposed] будущий Inspection несёт собственную дату осмотра, независимую от времени создания
           записи Inspection, от даты фиксации родительского объекта и от дат других осмотров.
```

Запрет future-valued research date (T9 предыдущей ревизии) **удалён и не заменён другим глобальным
запретом**: из D094 он не следует, неправильные часы устройства возможны, сценарии импорта и
исправления существуют, а UI validation и domain invariant — разные уровни. Если ограничение на
будущие даты когда-нибудь понадобится, оно должно решаться отдельно и на уровне, где для него есть
основание.

Все инварианты проверяемы без UI: T3–T6 — на уровне domain/migration/Room-тестов; T7, T8 — тестами
round-trip и late-entry сценариями; T9 — формулировка для будущей модели Inspection.

## 21. Test impact (future matrix)

| Категория | Что проверять | Уровень |
|---|---|---|
| new record | дата и год пишутся согласованно в одной транзакции; `created_at` — отдельный Instant | unit + Room |
| late entry | запись получает выбранную дату, а не дату ввода; год/номер — из неё; время ввода не становится временем наблюдения (T8) | unit + Room |
| legacy migration (ObservationPoint) | `observation_date` восстановлена из historical `created_at` по прежней local-calendar convention; колонка остаётся NOT NULL — ни одна legacy-точка не получает пустую дату; `observation_year` не переписан; `observation_year == year(observation_date)` выполняется для каждой мигрированной строки | Room migration (instrumented, по образцу `BeeSearchMigrationTest.kt`) |
| legacy migration (ObservationPoint): identity | существующий `point_number` не меняется миграцией; UUID не меняется; существующие связи Bee / FlightCycle / медиа на точку не меняются | Room migration (instrumented) |
| legacy migration (physical objects) | после миграции `fixation_date IS NULL` для всех существующих Hollow/LogHive/Apiary; ничего не backfill-ится из `created_at` | Room migration (instrumented) |
| legacy object filtering | объект с `fixation_date = NULL` виден при «Всё время» и не попадает в ограниченный календарный интервал | Room |
| correction within same year | правка даты внутри года не меняет ни `observation_year`, ни `point_number` | unit + Room |
| correction across year | `observation_year` становится годом новой даты; точка получает номер в новой year scope; правка и назначение номера — в одной транзакции; промежуточное состояние не сохраняется | Room |
| correction across year: number | прежний номер в старой scope освобождается, строка не пересоздаётся, дублей в новой scope не возникает | Room |
| correction across year: UUID | UUID не меняется; ссылки Bee / FlightCycle / медиа на точку остаются валидными | Room |
| observation_year consistency | `observation_year == year(observation_date)` после создания и после любой правки даты | Room |
| date boundary | 31.08 → 01.09 не «переезжает» при смене пояса устройства | unit (fixed zone) |
| timezone boundary | normal behaviour: сохранённая календарная дата не меняется при смене timezone отображения | unit + device |
| export | новый формат: строгий key set, версия, чтение старого пакета | unit (codec) |
| collection export | то же для коллекции | unit (codec) |
| backup/restore | архив без нового ключа восстанавливается; с ключом — значение сохраняется | instrumented |
| filter query | территория + интервал даты отбирает ровно нужные записи; запись без даты не попадает | Room |
| event timestamps untouched | `departureTime` / `returnTime` / `createdAt` / `completedAt` не изменяются и не выводятся из `observation_date` | unit + Room |

Разделение: unit — семантика и форматы; Room/migration и backup — instrumented, потому что требуют
реальной SQLite-схемы; device — сценарий смены часового пояса и офлайн-проверка.

## 22. Unresolved owner decisions

**No blocking owner decisions remain for the proposed temporal data-model.**

Два прежних вопроса закрыты решением владельца:

1. ~~legacy-политика для физических объектов~~ → `fixation_date = NULL`, без backfill (§11.2);
2. ~~исправление даты наблюдения через границу года~~ → переход в новую year scope с новым номером
   при неизменном UUID (§13.1).

Вопрос про погоду при позднем вводе больше не является вопросом к владельцу в рамках этого этапа: он
сведён к минимальному invariant (T8, §12) и вынесен в **отложенный future design concern**
(§23, §24).

Не являются вопросами (уже выведено и зафиксировано выше): нужность даты фиксации для
Hollow/LogHive/Apiary (следует из D094); отказ от establishment date сейчас (§7); отказ от Inspection
entity сейчас (§8); представление значения — ISO `YYYY-MM-DD` TEXT утверждено в составе design;
индекс (§17 — отложен по измерению); legacy backfill для ObservationPoint (§11.1 — выведен из
документированного evidence и прежней project convention; специальной обработки theoretical timezone
ambiguity не вводится, поэтому отдельного вопроса нет).

Новых вопросов ради сохранения раздела не добавляется. Документ утверждён 2026-10-03 (APPROVED).

Historical I2 execution gate (current V7/V2 writers now carry dates; device gate remains) · 2026-10-08: NEW Hollow/LogHive получают fixationDate при создании;
LEGACY Hollow/LogHive/Apiary и технический createApiary сохраняют NULL. Legacy V6 Backup,
Snapshot V1 и single/collection physical-object Export V1 отказывают до успешной публикации при
любом serialized object с non-null fixationDate; это временная loss-prevention защита без wire
carriage. **I2 APK нельзя устанавливать на рабочий Samsung до I3 + I4**: guard делает текущие
backup/export paths недоступными для graphs с новыми dated objects. I3 должен переносить explicit
canonical dates в versioned Backup/Snapshot; I4 — в versioned export, сохраняя legacy reader semantics.

## 23. Implementation sequence (design only)

Последовательность выведена из зависимостей, а не из удобства:

```text
I1  ObservationPoint research date
    schema + migration + domain + repository + creation transaction
    + NOT NULL observation_date
    + deterministic legacy backfill из persisted created_at (§11.1)
    + derivation of observation_year из observation_date
    + правило исправления через границу года (год, membership, новый номер — одна транзакция)
    (T3, T4, T5, T7)                                   ← независим
I2  Physical object fixation date (Hollow/LogHive)
    nullable column + migration (legacy → NULL, без backfill)
    + заполнение при создании новых Hollow/LogHive
    (T3, T6, T7)                                        ← независим от I1
I3  Versioned Backup V7 / Snapshot V2: required canonical dates + legacy readers ← зависит от I1, I2
I4  Export V2: required canonical dates + legacy V1 readers      ← зависит от I1, I2
I5  Query/filter support: интервальный запрос по research date
    + запись без research date (legacy fixation_date IS NULL) не попадает в ограниченный период
    + решение по индексу по измерению                   ← зависит от I1, I2
I6  UI integration: период в «Данные на карте» + ввод и исправление даты
    (по утверждённой UI specification)                  ← зависит от I5
I7  Device verification: Samsung, офлайн, смена пояса, границы месяца/года ← последний
```

Owner execution clarification · 2026-10-08: I1 остаётся самостоятельным increment; correction API
реализуется и тестируется без user-facing caller. Normal creation не принимает explicit date.
**I3 Backup/Snapshot carriage + I4 Export carriage MUST complete before I6 или любым другим
user-reachable explicit/corrected research-date path**, способным создать дату, отличающуюся от
legacy local date(createdAt). До этого запрещены UI/ViewModel/use-case/import/deep-link callers
correction и explicit late-entry creation. I1 legacy reader adaptation лишь materialize REQUIRED
entity; не реализует I3/I4 wire carriage. Physical-object legacy NULL policy не меняется.

Weather и late-entry-время в increments **не входят**: это отдельный future
late-entry/weather design concern (§12, §24), который должен планироваться собственной задачей и не
должен попадать в I1–I7 скрытно.

Каждый increment проверяем отдельно: I1/I2 — Room-тестами и тестами domain без UI; I3/I4 — тестами
codec; I5 — Room-тестами фильтра; I6 — сравнением с approved mockup; I7 — на устройстве. Гигантский
all-in-one commit не предполагается.

## 24. Что этот документ намеренно не определяет

Конкретные имена миграций и их номера; финальный SQL; точный API DAO; UI позднего ввода и исправления
даты; establishment date и precision «только год»; Inspection entity; sync protocol и revisions;
индекс без измерения; изменение существующих numbering правил; любые изменения Help и product/workflow
документов.

**Отложенный design concern (не blocker этой schema):** late-entry-время и погода — late-entry UI для
времени, отдельное `observation_time` field, восстановление погоды за прошедший период, API и retry
strategy, snapshot status model, выбор используемого timestamp, редактирование времени. Этот документ
фиксирует только T8 (§12) и не проектирует перечисленное.

## Опора на источники

Ключевые evidence-точки: `docs/decisions.md` D056/D088/D094; `docs/data-model.md` §16.1, §22, §25,
§63, §67, §71.1; `docs/glossary.md` §57, §57.1; `docs/ideas.md` I009, I015, I016;
`Entities.kt`, `Daos.kt`, `Migrations.kt`, `RoomObservationRepository.kt`,
`RoomPhysicalObjectRepository.kt`, `PhysicalObjectExportCodec.kt`,
`PhysicalObjectCollectionExportCodec.kt`, `ObservationPointExportCodec.kt`, `BackupCore.kt`,
`PhysicalObjectExportFileName.kt`, `OpenMeteoWeatherProvider.kt`.
