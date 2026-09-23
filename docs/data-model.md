# Bee Search — Data Model

## 1. Назначение документа

Этот документ описывает логическую модель данных приложения **Bee Search**.

Он связывает предметные требования из:

* `product-requirements.md`;
* `user-workflows.md`;
* `domain-model.md`;

с будущей технической реализацией локальной базы данных.

Документ определяет:

* какие данные необходимо сохранять;
* основные поля;
* связи между сущностями;
* обязательность значений;
* ограничения целостности;
* вычисляемые значения;
* локальные настройки устройства;
* требования, важные для будущей синхронизации.

Документ пока не определяет окончательные Kotlin-классы, Room Entity, DAO, SQL-миграции или серверный API.

---

# 2. Основная структура данных

Основные сохраняемые сущности:

```text
Territory
    └── ObservationPoint
            └── Bee
                    └── FlightCycle

Observer
```

Дополнительно приложение хранит локальные настройки устройства:

```text
AppSettings
```

Отдельные сущности `ObservationSession`, `GroupRelease`, `BeeState`,
`OfflineMap` и `AzimuthMeasurement` на текущем этапе не создаются.

---

# 3. Основные принципы модели

## 3.1. Минимум сущностей

Новые сущности не должны вводиться, если соответствующее понятие можно однозначно представить через существующую структуру данных.

---

## 3.2. Координаты принадлежат точке

Координаты сохраняются только в `ObservationPoint`.

`Bee` и `FlightCycle` не имеют собственных координат.

---

## 3.3. Одно ObservationPoint — одно полевое наблюдение

`ObservationPoint` представляет конкретную полевую работу в конкретном месте и в конкретный период времени.

Если пользователь через несколько дней возвращается в то же физическое место для нового наблюдения, создаётся новая `ObservationPoint`, даже если координаты совпадают.

Совпадение координат разных `ObservationPoint` допустимо и не является дубликатом.

---

## 3.4. Первый вылет и его correction provenance

Первый вылет не хранится отдельной сущностью `GroupRelease`.

Для каждой Bee:

```text
sequence_number = 1
```

означает первый цикл этой Bee. Цикл создаётся в той же транзакции, что и сама
Bee, с индивидуальным `departure_time` её вылета. Чтобы приложение могло
безопасно предложить локальную отмену ошибочно зарегистрированного вылета, Room
хранит минимальное provenance:

- `FlightCycle.initial_group_launch` — legacy признак цикла, созданного прежним
  групповым выпуском; новые индивидуальные вылеты всегда хранят `false`;
- `FlightCycle.initial_group_launch_correction_eligible` — истинно только для
  первого цикла, пока для него ни разу не был записан `return_time`; смысл поля
  сужен до «первый вылет этой Bee ещё можно отменить».

Это не отдельная research-сущность, не сохранённый state Bee и не общий undo
history. Эти технические признаки нужны только для одной локальной correction
ошибочно зарегистрированного первого вылета.

`ObservationPoint.initial_group_release_at` сохраняется как legacy поле
восстановленных данных и больше не устанавливается новыми вылетами.

Каждый первый вылет получает собственное `departure_time` своей Bee.

---

## 3.5. Observer является отдельной сущностью

`Observer` хранится в Room как отдельная исследовательская сущность с UUID.
На одном устройстве может быть несколько Observer. Его код остаётся
человеко-читаемым и уникальным на устройстве, но технические связи строятся
только через `Observer.id`.

Текущий выбор хранится device-local как `current_observer_id`. Новая
ObservationPoint получает этот ID при создании; последующее переключение
current Observer не изменяет историческую запись точки.

---

# 4. Идентификаторы

Все основные сущности должны иметь устойчивые уникальные идентификаторы.

Предпочтительный формат:

```text
UUID
```

UUID создаётся непосредственно на мобильном устройстве в момент создания записи.

UUID необходим для:

* `Territory`;
* `Observer`;
* `ObservationPoint`;
* `Bee`;
* `FlightCycle`.

Внутренний UUID не используется как основное человекочитаемое обозначение.

---

# 5. Время

Все значимые события должны сохраняться с точностью как минимум до секунды.

Хранение времени должно быть однозначным и пригодным для последующей синхронизации между устройствами.

Логическое представление времени:

```text
Instant
```

В SQLite значения хранятся как:

```text
Unix epoch milliseconds
```

Интерфейс может отображать время в разных форматах:

```text
09:34
09:34:12
26.08.2026 09:34:12
```

Формат отображения не должен определять формат хранения.

Часовой пояс не хранится ни в `ObservationPoint`, ни в `FlightCycle`, ни в `AppSettings`.

Для отображения используется текущий часовой пояс устройства. Если часовой пояс устройства изменится, локальное представление исторической записи может измениться, но абсолютный момент останется прежним.

---

# 6. AppSettings

## 6.1. Назначение

`AppSettings` хранит локальные настройки конкретного устройства.

Это не исследовательские данные.

---

## 6.2. Минимальные значения

```text
AppSettings

current_territory_id    UUID?       optional
current_observer_id     UUID?       optional
```

---

## 6.3. `current_territory_id`

Хранит идентификатор территории, которая используется по умолчанию при обычном запуске приложения.

После выбора другой территории это значение обновляется.

Признак текущей территории не хранится внутри самой `Territory`.

То есть поле:

```text
Territory.is_current
```

не используется.

---

## 6.4. `current_observer_id`

Хранит UUID выбранного Observer. На чистой установке ключ отсутствует; не
создаются фиктивные Observer. Если сохранённый UUID не соответствует сущности
Room, selection считается invalid и startup направляет пользователя в Settings.

`current_observer_id`, как и `current_territory_id`, — настройка устройства,
а не поле исследовательской сущности. Переключение значения влияет только на
будущие ObservationPoint.

---

## 6.5. Будущие локальные настройки

В дальнейшем `AppSettings` может включать:

```text
map preferences
last map position
UI preferences
sync preferences
```

Эти значения не являются частью исследовательского набора данных, если отдельно не определено иное.

---

# 7. Territory

## 7.1. Назначение

`Territory` представляет долговременную область исследования.

Территория объединяет:

* точки наблюдений;
* человекочитаемый код;
* название.

---

## 7.2. Предлагаемые поля

```text
Territory

id                  UUID        required
code                String      required
name                String      required
region              String      required
district            String      required

created_at          Instant     required
updated_at          Instant     required
```

---

# 8. `Territory.id`

Внутренний устойчивый идентификатор территории.

Не зависит от:

* названия;
* кода;
* положения в списке;
* текущего устройства.

---

# 9. `Territory.code`

Стабильное человекочитаемое обозначение территории.

Пример:

```text
KLYAZMA-01
```

На одном устройстве нельзя создавать две территории с одинаковым кодом.

В дальнейшем при серверной синхронизации правила глобальной уникальности кодов должны быть определены отдельно.

---

# 10. `Territory.name`

Человекочитаемое название.

Пример:

```text
Клязьминская пойма
```

Название может изменяться без изменения `id`.

---

# 10.1. `Territory.region` и `Territory.district`

Обязательные человеко-читаемые поля для региона и района. Все четыре текстовых
поля Territory проходят trim и не могут стать пустыми после него.

---

# 10.2. Observer

```text
Observer

id                  UUID        required
code                String      required, UNIQUE on device
last_name           String      required
first_name          String      required
middle_name         String?     optional
contact             String?     optional
created_at          Instant     required
updated_at          Instant     required
```

`code`, `last_name` и `first_name` проходят trim и обязательны. `middle_name`
и `contact` проходят trim; пустая строка нормализуется в `null`. Contact — одно
свободное текстовое поле без жёсткой phone/email валидации. `Observer.code` не
является primary key или foreign key.

---

# 11. Состояние офлайн-карты

Состояние загрузки офлайн-карты относится к конкретному устройству, а не к исследовательской сущности `Territory`.

В Room поле `Territory.map_status` не создаётся.

Offline Ready определяется наличием полностью staged, проверенного и атомарно
активированного совместимого PMTiles Map Package. Installation/activation state
не дублируется в research database; конкретный infrastructure store и manifest
format определяются отдельно.

---

# 12. Область офлайн-карты

Выбранная область и другие технические метаданные офлайн-карты являются локальными данными устройства.

В Room поле `Territory.map_region_data` и отдельная таблица метаданных карты не создаются.

Coverage bounds и zoom остаются device-local map infrastructure. Активированный
package должен иметь достаточно identity/version metadata, чтобы отличить
установленный artifact от совместимой замены и проверить versioned MapProfile.
Конкретный manifest format и package-to-Territory association не фиксируются.

---

# 13. Картографические файлы

Сами MVT tiles, style resources и другие картографические данные не должны храниться внутри основной базы наблюдений.

Логическое разделение:

```text
Database
    ├── Territory
    ├── ObservationPoint
    ├── Bee
    └── FlightCycle

Map storage
    └── activated PMTiles Map Package
```

Локальная связь Territory с package, если она требуется, существует только в
device-local map infrastructure metadata. Удаление или failure package не
изменяет Territory и связанные исследовательские записи.

---

# 14. ObservationPoint

## 14.1. Назначение

`ObservationPoint` представляет одно конкретное полевое наблюдение в конкретном месте.

Это основной контейнер данных полевой работы.

---

## 14.2. Предлагаемые поля

```text
ObservationPoint

id                  UUID        required
territory_id        UUID        required
observer_id         UUID        required

observation_year    Int         required
point_number        Int         required
bee_presence_result BeePresenceResult? optional

code                String?     optional

latitude            Double      required
longitude           Double      required

gps_latitude        Double?     optional
gps_longitude       Double?     optional
gps_accuracy_m      Double?     optional

created_at          Instant     required
initial_group_release_at Instant? legacy, только восстановленные данные
completed_at        Instant?    optional
description         String?     optional plain text
```

Schema v7 также хранит 0..N `ObservationPointAttachment` и ровно одну строку
`ObservationPointWeather` на ObservationPoint. Attachment metadata находится в
Room, а bytes — в app-owned file storage. Weather row может оставаться `PENDING`
без искусственных числовых значений.

---

# 15. `territory_id`

Ссылка на `Territory.id`.

Каждая точка принадлежит ровно одной территории.

```text
ObservationPoint.territory_id
    → Territory.id
```

---

# 16. `observer_id`

Ссылка на `Observer.id`. Перед созданием точки и current Territory, и current
Observer должны быть валидными сохранёнными сущностями. `observer_id` и
`territory_id` записываются в точку один раз и не меняются при последующем
переключении current selection.

---

## 16.1. `observation_year`

Обязательный локальный календарный год наблюдения. Он вычисляется один раз при создании ObservationPoint по локальной временной зоне устройства и хранится как `INTEGER`.

`observation_year` не пересчитывается позднее из `created_at`. Отдельная история часовых поясов не хранится.

---

## 16.2. `point_number`

Обязательный положительный последовательный номер, начинающийся с 1 внутри области:

```text
territory_id + observation_year + observer_id
```

Следующий номер определяется и записывается в одной Room transaction. Локальная база дополнительно обеспечивает уникальность полного сочетания:

```text
territory_id + observation_year + observer_id + point_number
```

Номер не является идентификатором. UUID остаётся первичным ключом и не меняется при возможном будущем перенумеровании после синхронизации.

---

## 16.3. `bee_presence_result`

Nullable `TEXT` со значениями:

```text
BEES_FOUND
NO_BEES_FOUND
```

`null` означает, что результат ещё не установлен. Значение не выводится из количества Bee.

Создание первой Bee вместе с её первым FlightCycle и установка `BEES_FOUND` выполняются атомарно. При локальной отмене ошибочно зарегистрированного первого вылета, если на точке не осталось ни одной Bee, значение возвращается в `null`. Операция явного отсутствия пчёл одной транзакцией устанавливает `NO_BEES_FOUND` и `completed_at`; при этом создание Bee запрещено.

Отдельная таблица результата и generic-поле `status` не создаются.

---

# 17. Код точки

`code` — необязательное человекочитаемое обозначение.

Например:

```text
P-001
P-017
```

Код не используется как первичный идентификатор.

Правила автоматического формирования кодов пока не определены.

---

# 18. Итоговые координаты

```text
latitude
longitude
```

— итоговое положение точки, подтверждённое пользователем.

Именно эти координаты используются:

* при отображении точки;
* для последующего анализа;
* как координаты всех Bee данной точки;
* как географическая основа всех FlightCycle данной точки.

---

# 19. Исходное GPS-положение

Если точка первоначально создаётся по GPS, желательно сохранять:

```text
gps_latitude
gps_longitude
gps_accuracy_m
```

Эти значения отражают исходное измерение устройства.

---

# 20. Ручная корректировка

После ручной корректировки:

```text
latitude
longitude
```

могут отличаться от:

```text
gps_latitude
gps_longitude
```

Это ожидаемое состояние данных.

---

# 21. Координаты разных ObservationPoint

Две или более точки могут иметь одинаковые координаты.

Например:

```text
ObservationPoint A
26.08.2026
56.1959786, 42.7477116

ObservationPoint B
29.08.2026
56.1959786, 42.7477116
```

Это означает повторное наблюдение в том же месте и не является ошибкой.

Уникальное ограничение по координатам запрещено.

---

# 22. `created_at`

Фиксирует начало конкретной полевой работы.

Дата является важной частью смысла ObservationPoint.

---

# 23. `completed_at`

Пока:

```text
completed_at = null
```

точка считается активной.

После завершения наблюдения записывается время завершения.

Отдельное поле `status` на данном этапе не требуется.

Обычное завершение запрещено при `bee_presence_result = null`. Отрицательное наблюдение завершается отдельной атомарной операцией, устанавливающей `NO_BEES_FOUND` и `completed_at`.

---

# 24. Повторное наблюдение через несколько дней

Завершённая ObservationPoint не должна использоваться для нового полноценного наблюдения через несколько дней.

Если работа повторяется:

```text
в том же месте
+ в другой день
```

создаётся новая ObservationPoint.

Это сохраняет независимость:

* даты;
* набора пчёл;
* циклов;
* условий наблюдения;
* результатов.

---

# 25. Редактирование завершённой точки

Завершение ObservationPoint не обязательно означает абсолютную блокировку любых исправлений.

В будущем может потребоваться исправление ошибочно введённых данных.

Однако продолжение новой полевой работы через несколько дней не считается редактированием старой точки.

Для этого создаётся новая ObservationPoint.

---

# 26. Bee

## 26.1. Назначение

`Bee` представляет конкретную меченую пчелу внутри одной ObservationPoint.

---

## 26.2. Предлагаемые поля

```text
Bee

id                      UUID        required
observation_point_id    UUID        required

mark_color              String      required
mark_position           Enum        required

created_at              Instant     required
```

---

# 27. `observation_point_id`

Ссылка на ObservationPoint.

```text
Bee.observation_point_id
    → ObservationPoint.id
```

Bee не существует вне контекста конкретной точки.

---

# 28. Координаты Bee

Bee не имеет полей:

```text
latitude
longitude
```

При необходимости координаты Bee определяются через:

```text
Bee
→ ObservationPoint
→ latitude / longitude
```

---

# 29. Цвет метки

`mark_color` хранит цвет метки.

Примеры:

```text
WHITE
YELLOW
BLUE
RED
GREEN
```

Это текущий каталог MVP, но модель хранения не ограничена только этими цветами.

Добавление нового цвета не должно требовать изменения структуры базы.

Отдельный числовой лимит Bee не хранится. Доступная ёмкость пользовательского потока определяется уникальными комбинациями текущего каталога цветов и положений.

---

# 30. Положение метки

`mark_position` хранит положение метки на теле Bee:

```text
THORAX
ABDOMEN
```

Пользовательское представление положения не является видимой подписью: положение
передаётся графикой `BeeMarkIcon`, где цвет метки нанесён на грудь или на брюшко.
Смысл метки для accessibility services описывается как `Белая метка, грудь` и
`Белая метка, брюшко`.

Значения хранения не должны зависеть от языка интерфейса.

## 30.1. Совместимость сохранённых токенов

Колонка остаётся TEXT, Room version не повышается и destructive migration не
выполняется. Сохранённые токены прежних версий читаются через tolerant
преобразование:

```text
THORAX      → THORAX
NONE        → THORAX    (подтверждено реальными полевыми данными)
ABDOMEN     → ABDOMEN
RIGHT_WING  → ABDOMEN   (подтверждено реальными полевыми данными)
LEFT_WING   → LEFT_WING (смысл не подтверждён, ложная трактовка запрещена)
```

Новые записи используют `THORAX` и `ABDOMEN`. `LEFT_WING` остаётся только
внутренним legacy-значением совместимости: оно не предлагается для новых Bee и не
участвует в генерации десяти доступных вариантов метки.

Поскольку одно реальное положение может быть сохранено под двумя токенами,
проверка повторной метки выполняется по всем сохранённым токенам положения, а не
по одному строковому значению.

Backup format version не повышается. Архив прежней версии с `NONE`, `RIGHT_WING`
или `LEFT_WING` остаётся читаемым; новый архив записывает `THORAX` и `ABDOMEN`, а
встреченный `LEFT_WING` сохраняет без переименования.

---

# 31. Уникальность метки

В пределах одной ObservationPoint комбинация:

```text
observation_point_id
+ mark_color
+ mark_position
```

должна быть уникальной.

Например, нельзя одновременно создать:

```text
Белая, грудь
Белая, грудь
```

в одной точке.

Это ограничение следует обеспечить и на уровне базы данных.

---

# 32. Состояние Bee

Отдельное поле:

```text
Bee.status
```

на текущем этапе не хранится.

Состояние выводится из истории `FlightCycle`.

---

# 33. Bee ещё не зарегистрирована

Bee создаётся только вместе со своим первым FlightCycle, поэтому состояния
«Bee без циклов» в актуальном workflow не возникает.

До первого вылета доступные метки показаны как варианты, а не как Bee: они не
хранятся и не являются наблюдаемыми пчёлами.

---

# 34. Bee в полёте

Если последний FlightCycle имеет:

```text
return_time = null
```

и ObservationPoint активна, Bee находится в полёте.

---

# 35. Bee вернулась

Если последний FlightCycle имеет:

```text
return_time != null
```

Bee считается вернувшейся и потенциально готовой к следующему вылету.

---

# 36. Bee не вернулась

Если ObservationPoint завершена, а последний FlightCycle имеет:

```text
return_time = null
```

это означает:

> пчела не вернулась в течение периода наблюдения.

Дополнительное фиктивное событие или время не создаётся.

---

# 37. FlightCycle

## 37.1. Назначение

`FlightCycle` представляет один цикл:

```text
вылет → возможное возвращение
```

конкретной Bee.

---

## 37.2. Предлагаемые поля

```text
FlightCycle

id                  UUID        required
bee_id              UUID        required

sequence_number     Integer     required

departure_time      Instant     required
return_time         Instant?    optional

azimuth_deg         Double?     optional
azimuth_capture_consumed Boolean required
initial_group_launch Boolean required
initial_group_launch_correction_eligible Boolean required

created_at          Instant     required
updated_at          Instant     required
```

---

# 38. `bee_id`

Ссылка на:

```text
Bee.id
```

Каждый FlightCycle принадлежит ровно одной Bee.

---

# 39. `sequence_number`

Порядковый номер цикла конкретной Bee:

```text
1
2
3
...
```

Комбинация:

```text
bee_id + sequence_number
```

должна быть уникальной.

---

# 40. Первый цикл

`sequence_number = 1` означает первый FlightCycle конкретной Bee. Он создаётся
одновременно с самой Bee при регистрации её первого вылета и всегда содержит
индивидуальное `departure_time` этой Bee.

Пригодность первого цикла для расчётов продолжительности не хранится отдельным
полем и не имеет автоматического исключения по номеру цикла: первый цикл
проходит тот же путь оценки, что и последующие. Такой FlightCycle остаётся
полноценной строкой исходных данных, его timestamps не изменяются.

---

# 41. Регистрация первого вылета

При действии:

```text
УЛЕТЕЛА
```

для выбранной метки одной транзакцией создаётся реальная Bee и её первый цикл:

```text
Bee
    mark_color, mark_position

FlightCycle
sequence_number = 1
departure_time = фактическое время этой Bee
initial_group_launch = false
initial_group_launch_correction_eligible = true
```

В этой же транзакции у ObservationPoint устанавливается `BEES_FOUND`, если он
ещё не установлен.

Например:

```text
УЛЕТЕЛА Белая, грудь    → Белая, грудь    cycle 1, 09:34:12
УЛЕТЕЛА Белая, брюшко   → Белая, брюшко   cycle 1, 09:36:40
УЛЕТЕЛА Синяя, грудь    → Синяя, грудь    cycle 1, 09:41:05
```

Одна ObservationPoint содержит не более 10 реальных Bee. Варианты метки,
показанные до вылета, не являются Bee и этот лимит не расходуют.

---

# 42. Атомарность регистрации первого вылета

Создание Bee и её первого FlightCycle должно сохраняться одной транзакцией.

Недопустима ситуация, когда из-за ошибки Bee создана без первого FlightCycle
или первый FlightCycle создан без Bee.

Операция должна завершаться:

```text
либо полностью успешно
либо без создания ни Bee, ни первого цикла
```

---

# 43. `departure_time`

Время вылета обязательно.

FlightCycle не существует без времени вылета.

---

# 44. `return_time`

Время возвращения необязательно.

```text
return_time = null
```

может означать:

* пчела ещё находится в полёте;
* пчела не вернулась до завершения ObservationPoint.

---

# 45. Ограничение времени

Если `return_time` существует:

```text
return_time >= departure_time
```

Нарушение считается ошибкой данных.

---

# 46. Продолжительность

Продолжительность является вычисляемым значением.

Для завершённого цикла:

```text
duration =
return_time - departure_time
```

Для активного:

```text
current_duration =
current_time - departure_time
```

Отдельное поле:

```text
duration
```

не сохраняется.

---

# 47. Повторный вылет

После зарегистрированного возвращения создаётся новый FlightCycle.

Например:

```text
cycle 1
09:34:12 → 09:39:27

cycle 2
09:41:08 → 09:46:31

cycle 3
09:49:02 → ...
```

Предыдущий цикл не переиспользуется.

---

# 48. Одновременно открытый FlightCycle

У Bee не должно существовать более одного незавершённого цикла.

Недопустимо:

```text
cycle 2
departure_time = 09:41
return_time = null

cycle 3
departure_time = 09:45
return_time = null
```

Новый цикл можно создать только после возвращения предыдущего.

---

# 49. Азимут

`azimuth_deg` является необязательным.

Сохранённое значение является истинным / географическим азимутом. Магнитное sensor heading перед
сохранением корректируется через `GeomagneticField`; магнитное значение отдельно не хранится.

`azimuth_capture_consumed` хранит другой факт: использована ли уже однократная возможность field
capture для этого FlightCycle. Он не означает наличие `azimuth_deg`.

Допустимый диапазон:

```text
0 <= azimuth_deg < 360
```

Примеры:

```text
0
127
127.4
359.8
```

---

# 50. Отсутствующий азимут

```text
azimuth_deg = null
```

вместе с `azimuth_capture_consumed = false` означает, что полевая фиксация ещё не выполнялась.

Вместе с `azimuth_capture_consumed = true` означает, что зафиксированное направление было удалено;
повторная полевая фиксация этого FlightCycle запрещена.

Это нормальное состояние.

---

# 51. Нулевой азимут

```text
azimuth_deg = 0
```

означает реальное направление на истинный / географический север.

Поэтому значение `0` не используется как признак отсутствия данных.

---

# 52. Удаление азимута

Удаление сомнительного измерения означает:

```text
azimuth_deg = null
azimuth_capture_consumed = true
```

Остальные значения FlightCycle не изменяются.

---

# 53. Азимут и направление на гнездо

На уровне модели данных азимут является только зарегистрированным направлением ухода пчелы.

Он не должен интерпретироваться как гарантированное направление на гнездо.

Алгоритмы анализа должны учитывать это отдельно.

---

# 54. Автоматическое сохранение

Каждое значимое событие должно сохраняться сразу.

Примеры:

```text
создание территории
→ сохранить

подтверждение координат
→ transient draft, не сохранять

`Добавить`
→ одной транзакцией сохранить точку с `bee_presence_result = null`

`Пчёлы отсутствуют`
→ одной транзакцией сохранить точку + NO_BEES_FOUND + completed_at

первый вылет (`УЛЕТЕЛА`)
→ одной транзакцией сохранить Bee + FlightCycle 1 + BEES_FOUND

возвращение
→ сохранить return_time

повторный вылет
→ создать новый FlightCycle

добавление азимута
→ сохранить azimuth_deg

удаление азимута
→ сохранить null

завершение точки
→ сохранить completed_at
```

Не должно требоваться отдельное общее сохранение всей полевой работы в конце.

---

# 55. Восстановление после перезапуска

Для восстановления активного наблюдения приложение ищет ObservationPoint, у которой:

```text
completed_at = null
```

и загружает:

```text
ObservationPoint
    ↓
Bee
    ↓
FlightCycle
```

Отдельная таблица сессий для этого не нужна.

---

# 56. Одна активная точка

Для MVP предполагается, что на одном устройстве одновременно ведётся не более одной активной ObservationPoint.

Это упрощает:

* восстановление после закрытия;
* рабочий интерфейс;
* предотвращение случайного смешения наблюдений.

Если позже появится реальная необходимость в нескольких одновременно активных точках, модель может быть расширена.

---

# 57. Удаление Territory

Удаление территории может затрагивать всю связанную историю:

```text
Territory
    ↓
ObservationPoint
    ↓
Bee
    ↓
FlightCycle
```

Поэтому обычное каскадное удаление из интерфейса опасно.

Для MVP предпочтительно запрещать удаление Territory, если в ней уже существуют ObservationPoint.

---

# 58. Удаление ObservationPoint

Удаление ObservationPoint потенциально уничтожает:

* всех Bee;
* все FlightCycle.

Поэтому физическое удаление должно быть защищено от случайного действия.

Текущая выборочная операция разрешена только для завершённой ObservationPoint
и требует явного подтверждения. Repository повторно проверяет `completed_at`
внутри одной Room transaction, затем из-за `ON DELETE RESTRICT` удаляет только
связанные `FlightCycle`, затем `Bee`, затем саму `ObservationPoint`. Активная
точка (`completed_at = null`) отклоняется без изменения данных.

В дальнейшем может быть рассмотрено архивирование или мягкое удаление.

---

# 59. Удаление Bee

Bee создаётся только вместе со своим первым FlightCycle, поэтому отдельной
стадии «подготовленная Bee» не существует.

Локальная отмена ошибочно зарегистрированного первого вылета удаляет и Bee, и её
первый цикл одной транзакцией, пока для этого цикла ни разу не был записан
`return_time`. Это восстанавливает возможность снова использовать эту метку.

После зарегистрированного прилёта удаление Bee затрагивает историю наблюдений и
должно выполняться только как явное исправление данных.

---

# 60. Исправление ошибочных событий

Пользователь должен иметь возможность исправлять ошибочно зарегистрированные данные.

Однако модель должна различать:

```text
исправление ошибки
```

и:

```text
новое полевое наблюдение
```

Во время active observation допускается только локальная correction последнего
обратимого действия конкретной Bee: очистить `return_time` последнего цикла,
очистить его `azimuth_deg` без сброса `azimuth_capture_consumed`, удалить
только открытый последний FlightCycle с `sequence_number > 1` или удалить
ошибочно зарегистрированный первый вылет вместе с его Bee, пока для этого цикла
не записан `return_time`. Эти операции выполняются транзакционно и не требуют
отдельной persisted undo-history. Более ранние циклы этим workflow не удаляются.

Повторная работа через несколько дней в том же месте создаёт новую ObservationPoint.

---

# 61. Подготовка к будущей синхронизации

Хотя серверная синхронизация не входит в MVP, модель должна позволять добавить её позднее.

Для этого:

* UUID создаются локально;
* данные не зависят от автоинкрементных серверных ID;
* связи строятся по UUID;
* пользовательские коды не используются как внешние ключи;
* время хранится как абсолютный `Instant` / Unix epoch milliseconds;
* ObservationPoint сохраняет UUID выбранных Territory и Observer.

---

# 62. Метаданные синхронизации

Поля вроде:

```text
sync_status
server_id
device_id
sync_version
deleted_on_server
```

не следует добавлять до проектирования реального механизма синхронизации.

Это позволит не усложнять локальную модель преждевременно.

---

# 63. `created_at` и `updated_at`

Для сущностей, которые могут изменяться, полезно иметь технические временные метки.

Минимально:

```text
Territory
created_at
updated_at

ObservationPoint
created_at
completed_at

Bee
created_at

FlightCycle
created_at
updated_at
```

Позднее набор может быть уточнён.

---

# 64. Локальные и исследовательские данные

## Исследовательские данные

Потенциально синхронизируются:

```text
Territory
ObservationPoint
Bee
FlightCycle
Observer
```

Принятая, но ещё не реализованная ветвь долговечных физических объектов относится к
исследовательским данным и подчиняется тем же правилам (D088): объект хранит UUID, тип, номер,
координаты и subtype-данные, а `Bee → объект` является фактической исследовательской связью.
См. раздел 71.1.

## Локальные данные устройства

По умолчанию не являются исследовательскими:

```text
current_territory_id
current_observer_id
локальные пути офлайн-карт
map download state
Map Package installation/activation metadata
UI preferences
последняя позиция карты
```

Локальные данные офлайн-карт не моделируются в Room research schema.
Authoritative readiness задаётся полностью проверенным и атомарно активированным
PMTiles Map Package; минимальная package identity/version и возможная
package-to-Territory/profile связь относятся к device-local infrastructure.

`current_territory_id` и `current_observer_id` сами не являются
исследовательскими данными; историческую принадлежность определяют foreign keys
в ObservationPoint.

Желаемый offline coverage хранится отдельно от Room в device-local Preferences
DataStore. Запись keyed by `Territory.id` и имеет version marker:

```text
v1|north,east,south,west|...                       прежний безымянный набор участков
v2|{"areaId":…,"name":…,"bounds":[…]}              именованный Ареал
```

Запись `v2` описывает Ареал: стабильный `areaId` (UUID), изменяемое человекочитаемое
`name` и **1..N** участков `bounds`, каждый из которых содержит `north`, `east`,
`south`, `west` как `Double`. У Territory не более одного Ареала, поэтому запись
хранит один Ареал, а не коллекцию, и не содержит указателя выбранного: принадлежность
выражается ключом по `Territory.id`. Ареал с нулём участков не существует.

Непустой `v1` мигрирует в `v2` при первом чтении одной транзакцией DataStore: один раз,
с новым устойчивым UUID и начальным именем из названия Territory. Геометрия переносится
без изменений координат, порядка, перекрытий и без повторной нормализации. Пустой `v1`
означает отсутствие Ареала.

Чтение различает три состояния: Ареала нет, Ареал прочитан, сохранённое значение
повреждено. Повреждённое значение не считается отсутствием Ареала, не заменяется пустым
и не перезаписывается сохранением или миграцией.

Запись значения подчиняется явным правилам. Новый Ареал создаётся только действием
пользователя и только вместе с непустым `name` и хотя бы одним участком; `create`
отказывается писать поверх существующего, повреждённого или непереведённого значения.
`updateBounds` и `rename` отказываются работать при отсутствующем Ареале и при
повреждённом значении, а `rename` меняет только `name`, сохраняя `areaId` и участки.
Пустой набор участков не записывается: сохранение отклоняется, поэтому существующий
Ареал нельзя превратить в отсутствие Ареала обычным редактированием геометрии. Удаление
Ареала убирает запись по `Territory.id`, а не пишет пустое значение; новых записей `v1`
приложение не создаёт, `v1` остаётся только читаемым legacy-форматом. UI-состояние
редактора в записи не хранится.

Это только сохранённая геометрия намерения пользователя, не downloaded resources.
При успешном удалении неиспользуемой Territory соответствующая device-local
запись удаляется по `Territory.id`; удаление Territory, используемой
ObservationPoint, блокируется и не изменяет coverage.

Ареал переносится основным backup в существующей settings-секции `map-coverage` как
строка `encoded` для своего `territoryId`; формат и версии архива при этом не меняются.
Указатель активного Map Package остаётся device-local и в backup не входит.

Single ObservationPoint export не меняет Room schema и не является выборкой полного
backup. Package format v1 содержит ровно одну persisted ObservationPoint, её weather,
attachment metadata и bytes, Bee/FlightCycle graph, а Territory и Observer — только
как context snapshot. `return_time = null`, nullable description/weather fields и
необязательный azimuth сохраняются без подстановки значений. Package identity хранится
в manifest; человекочитаемое имя файла не является ключом данных.

Дополнительно Ареал имеет переносимый **managed-файл** в
`Download/BeeSearch/<вариант>/Exchange/Areas/<имя>--<short-id>.json` (D083). Файл
содержит весь Ареал — `formatVersion`, полный `areaId`, `name` и 1..N участков — и
является зеркалом, а не хранилищем: canonical значением остаётся `v2` запись,
отсутствие файла не означает отсутствие Ареала, а ошибка записи файла не откатывает
сохранение. Identity файла — `areaId` внутри него, а не имя файла. Общая площадь Ареала
в DataStore и в файле не хранится: это производная величина, площадь объединения
участков, где перекрытие считается один раз, вложенный участок не добавляет площади, а
промежутки между отдельными участками не входят.

---

# 65. Предлагаемая схема связей

```text
Territory
┌─────────────────────────┐
│ id PK                   │
│ code UNIQUE             │
│ name                    │
│ region                  │
│ district                │
│ created_at              │
│ updated_at              │
└────────────┬────────────┘
             │
             ▼
Observer
┌─────────────────────────┐
│ id PK                   │
│ code UNIQUE             │
│ last_name               │
│ first_name              │
│ middle_name?            │
│ contact?                │
└────────────┬────────────┘
             │
             ▼
ObservationPoint
┌─────────────────────────┐
│ id PK                   │
│ territory_id FK         │
│ observer_id FK          │
│ observation_year        │
│ point_number            │
│ bee_presence_result     │
│ code                    │
│ latitude                │
│ longitude               │
│ gps_latitude            │
│ gps_longitude           │
│ gps_accuracy_m          │
│ created_at              │
│ completed_at            │
└────────────┬────────────┘
             │
             ▼
Bee
┌─────────────────────────┐
│ id PK                   │
│ observation_point_id FK │
│ mark_color              │
│ mark_position           │
│ created_at              │
└────────────┬────────────┘
             │
             ▼
FlightCycle
┌─────────────────────────┐
│ id PK                   │
│ bee_id FK               │
│ sequence_number         │
│ departure_time          │
│ return_time             │
│ azimuth_deg             │
│ azimuth_capture_consumed│
│ created_at              │
│ updated_at              │
└─────────────────────────┘
```

---

# 66. Основные ограничения целостности

## Territory

```text
id              UNIQUE
code            UNIQUE
name, region, district required after trim

## Observer

```text
id              UNIQUE
code            UNIQUE
last_name       required after trim
first_name      required after trim
middle_name     nullable
contact         nullable
```
```

## ObservationPoint

```text
territory_id    MUST EXIST
observer_id     MUST EXIST
observation_year required
point_number    required, >= 1
latitude        required
longitude       required
```

Координаты не уникальны.

Уникально:

```text
territory_id
+ observation_year
+ observer_id
+ point_number
```

## ObservationPointAttachment

```text
id                   UNIQUE UUID
observation_point_id MUST EXIST
type                 PHOTO
relative_path        app-owned relative path
byte_size            >= 0
sha256               lowercase SHA-256
created_at           required
```

## ObservationPointWeather

```text
observation_point_id UNIQUE, MUST EXIST
status               PENDING | LOADED | UNAVAILABLE
temperature_c        numeric only when LOADED
wind_speed_mps       numeric m/s, >= 0 only when LOADED
wind_direction_deg   numeric [0, 360) only when LOADED
sample_at            required when LOADED
fetched_at           required when LOADED
source               required when LOADED
```

Числовое направление ветра является primary data; сторона света вычисляется
только presentation layer.

## Bee

```text
observation_point_id MUST EXIST
```

Уникально:

```text
observation_point_id
+ mark_color
+ mark_position
```

## FlightCycle

```text
bee_id          MUST EXIST
departure_time  required
sequence_number >= 1
```

Уникально:

```text
bee_id
+ sequence_number
```

Дополнительно:

```text
0 <= azimuth_deg < 360
return_time >= departure_time
не более одного незавершённого FlightCycle на Bee
```

---

# 67. Room schema v2 и миграция из v1

Schema v2 добавляет в `observation_points`:

```text
observation_year INTEGER NOT NULL
point_number INTEGER NOT NULL
bee_presence_result TEXT NULL
```

Миграция сохраняет все существующие строки и UUID. Для прототипных v1-точек `observation_year` вычисляется из `created_at` в текущей локальной временной зоне устройства во время миграции. Историческая зона v1 неизвестна и отдельно не восстанавливается.

В каждой области `territory_id + observation_year + observer_code` номера назначаются с 1 в порядке `created_at`, затем `id` как детерминированный tie-breaker. После backfill создаётся UNIQUE index по четырём полям.

`bee_presence_result` получает `BEES_FOUND`, если для точки существует хотя бы одна строка Bee. Для точек без Bee остаётся `null`; `NO_BEES_FOUND` никогда не выводится миграцией.

---

# 68. Room schema v3/v4 и миграции

Schema v3 добавляет в `flight_cycles`:

```text
azimuth_capture_consumed INTEGER NOT NULL DEFAULT 0
```

Migration 2 → 3 сохраняет все строки, UUID, timestamps и значения азимута. Для старых строк с
`azimuth_deg IS NOT NULL` поле получает `true`; для строк с `azimuth_deg IS NULL` — `false`.
Ранее удалённую фиксацию восстановить невозможно, поскольку до v3 этот факт отдельно не хранился.

Новый FlightCycle создаётся с `azimuth_deg = null` и `azimuth_capture_consumed = false`. Field
capture одной атомарной операцией записывает значение и устанавливает consumed-state. Undo очищает
только значение.

Schema v4 не изменяет пользовательские таблицы. Compatibility migration 3 → 4 принимает финальную
v3-структуру, установленную промежуточной debug-сборкой с прежним Room identity hash. После
стандартной schema validation Room записывает актуальный hash; строки, UUID, timestamps,
`azimuth_deg` и `azimuth_capture_consumed` не преобразуются. Проверяются пути 3 → 4, повторное
открытие v4 и 1 → 2 → 3 → 4.

---

# 69. Room schema v5 и controlled development reset

Schema v5 добавляет required `Territory.region` и `Territory.district`,
отдельную таблицу `Observer`, `ObservationPoint.observer_id`, внешние ключи и
индекс нумерации по `territory_id + observation_year + observer_id`.

Переход 4 → 5 является единственным явно согласованным controlled
development-stage reset: он очищает прежние тестовые Territory,
ObservationPoint, Bee и FlightCycle, потому что невозможно честно заполнить
новые обязательные поля и Observer без выдумывания данных. Старый
`observer_code` DataStore больше не является целевой настройкой и игнорируется.
Это не является общей политикой: после v5 будущие миграции по умолчанию должны
быть non-destructive и сохранять реальные исследовательские данные.

---

# 69.1. Room schema v6/v7/v8

Schema v6 сохраняет Points Browser/read-side изменения предыдущей итерации.
Schema v7 выполняет non-destructive migration 6 → 7: добавляет nullable
`observation_points.description`, таблицы `observation_point_attachments` и
`observation_point_weather`. Для каждой существующей точки создаётся weather row
со статусом `PENDING` и всеми значениями snapshot `null`; фиктивная погода не
подставляется. Existing UUID, Bee и FlightCycle сохраняются.

Schema v8 выполняет non-destructive migration 7 → 8 для физических объектов
и nullable явной связи Bee, описанной в разделе 71.1. Существующие Bee получают
`source_object_id = null`; исходные FlightCycle не меняются.

Новая ObservationPoint создаётся вместе с `PENDING` weather row в одной Room
transaction. Успешно загруженный `LOADED` snapshot не перезаписывается обычным
повторным worker run.

Creation draft не является Room entity. Его description остаётся transient, а
фото до создания точки находятся в app-owned
`files/observation-attachments-staging/<draftSessionId>/`. При создании UUID точки
уже известен: файлы активируются в deterministic final paths, а ObservationPoint,
weather row и attachment metadata записываются одной Room transaction. Отмена
удаляет draft directory. Это не требует schema version выше 7.

---

# 70. Что намеренно не хранится

На текущем этапе не создаются отдельные поля или таблицы:

```text
ObservationSession
GroupRelease
is_initial_group
Bee.status
FlightCycle.duration
AzimuthMeasurement
OfflineMap
CurrentTerritory entity
PhysicalPlace
ObservationSeason
Territory.map_status
Territory.map_region_data
time_zone_id
```

`PhysicalPlace` в этом списке остаётся непринятой сущностью места наблюдений (раздел 71) и не
тождественен долговечным физическим объектам Дупло/Колода/Пасека, принятым решением D088:
физический объект — это наблюдаемая вещь с собственными координатами и историей осмотров, а не
вывод «здесь было несколько наблюдений». Принятая, но ещё не реализованная schema boundary
физических объектов описана в разделе 71.1.

---

# 71. Почему нет PhysicalPlace

Разные ObservationPoint могут иметь одинаковые координаты.

Это не требует отдельной сущности физического места.

Например:

```text
ObservationPoint 1
26 августа
координаты X,Y

ObservationPoint 2
29 августа
координаты X,Y
```

Для текущей задачи достаточно координат.

Если в будущем появится необходимость анализировать серии наблюдений именно как повторения одного постоянного места, возможность отдельной сущности `PhysicalPlace` может быть рассмотрена позднее.

---

# 71.1. Долговечные физические объекты — Room schema v8

Принято решением D088. Room schema v8 реализует эту границу таблицами
`physical_objects` и `apiaries`, а также nullable FK `bees.source_object_id`.

## Общая внутренняя identity-запись

```text
id               UUID      identity и адресат FK
territory_id     UUID      MUST EXIST; задаёт область нумерации
object_type      enum      concrete physical type (Дупло / Колода / Пасека)
sequence_number  Int       NOT NULL, >= 1, выделяется в scope Territory + object_type
latitude         Double    фактические координаты объекта
longitude        Double    фактические координаты объекта
created_at       Instant
```

```text
UNIQUE(territory_id, object_type, sequence_number)
```

Сохранённого `display_key` нет: человекочитаемое обозначение `Дупло N` / `Колода N` /
`Пасека N` полностью выводится из `object_type + sequence_number`, как это уже сделано для
точки (`Точка N`) и для меток Bee. Второго источника правды для обозначения не создаётся,
поэтому отдельное ограничение уникальности по строке обозначения не требуется.

Эта запись является internal persistence mechanism: она даёт единый UUID для FK и единый
scope нумерации. Она не является пользовательской или domain-сущностью, не выводится в UI как
единый тип, и конкретные типы остаются конкретными (D088, раздел 7).

## Subtype-таблица Пасеки

```text
object_id   UUID   PK, FK → identity.id (RESTRICT)
name        String nullable
```

`name` — осмысленное название Пасеки: не identity, не FK и **не unique**; одинаковые названия
у разных Пасек допустимы. Первичный ключ, равный FK, допускает не более одной subtype-строки на
объект.

## Отложено до появления реальных свойств

Subtype-таблицы Дупла и Колоды не создаются, пока у этих типов нет ни одного реального
subtype-свойства. Они добавляются аддитивной миграцией вместе со своим первым свойством
(таблица со ссылкой на ту же identity-запись плюс заполнение строк для существующих объектов
этого типа) без изменения UUID, обозначения, координат и существующих связей.

## Связь Bee → объект

```text
bees.source_object_id   UUID   nullable, FK → identity.id (RESTRICT)
```

Связь задаётся только явно, не выводится из расстояния, ближайшего объекта или координат, и
ссылается на identity физического объекта, а не на subtype-строку. Будущий Осмотр ссылается на
ту же identity (`1 → N`).

## Непереиспользование обозначения

Обозначение и `sequence_number` **никогда не выдаются повторно** другому физическому объекту в
том же scope (D088, раздел 4). Это отдельный инвариант, а не следствие способа выделения
номера. В v8 строки физических объектов сохраняются исторически: операция их удаления
не предоставляется, удаление Territory с объектами блокируется, а следующий номер
выделяется как `MAX(sequence_number) + 1` внутри одной Room-транзакции. Любая будущая
возможность физического удаления обязана отдельно сохранить непереиспользование номера.

---

# 72. Минимальная модель MVP

Ядро исследовательской базы:

```text
Territory
- id
- code
- name
- region
- district
- created_at
- updated_at

Observer
- id
- code
- last_name
- first_name
- middle_name?
- contact?
- created_at
- updated_at

ObservationPoint
- id
- territory_id
- observer_id
- observation_year
- point_number
- bee_presence_result?
- code?
- latitude
- longitude
- gps_latitude?
- gps_longitude?
- gps_accuracy_m?
- created_at
- completed_at?

Bee
- id
- observation_point_id
- mark_color
- mark_position
- created_at

FlightCycle
- id
- bee_id
- sequence_number
- departure_time
- return_time?
- azimuth_deg?
- azimuth_capture_consumed
- created_at
- updated_at
```

Локальные настройки:

```text
AppSettings
- current_territory_id?
- current_observer_id?
```

Картографические файлы хранятся отдельно от исследовательской базы.

---

# 73. Итоговая логика данных

```text
AppSettings
    ├── current_observer_id?
    └── current_territory_id?

Territory
    │
Observer
    │
    └── ObservationPoint
            ├── territory_id + observer_id
            ├── observation_year + point_number
            ├── bee_presence_result?
            ├── coordinates
            ├── original GPS data
            │
            └── Bee
                    ├── mark
                    │
                    └── FlightCycle 1
                    │       ├── common first departure time
                    │       ├── return time?
                    │       ├── azimuth?
                    │       └── azimuth capture consumed
                    │
                    ├── FlightCycle 2
                    │       ├── departure time
                    │       ├── return time?
                    │       ├── azimuth?
                    │       └── azimuth capture consumed
                    │
                    └── ...
```

Эта модель должна использоваться как исходная при проектировании Room/SQLite, если дальнейшее обсуждение не выявит необходимости её изменить.
