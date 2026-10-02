# Physical Object Export V1 — individual и collection profiles

Документ фиксирует переносимый формат пакета одного Physical Object (решение D093). Это формат
**одного объекта**, а не complete backup и не ObservationPoint package.

Пакет описывает один долговечный объект — Дупло или Колоду — и его собственные данные. Внешние
record-bearing сущности (Bee, FlightCycle, ObservationPoint, weather и attachments ObservationPoint)
в пакет не входят никогда, даже если они ссылаются на этот объект.

---

## 1. Контейнер

ZIP-архив со строго фиксированным составом записей:

```text
manifest.json
object.json
media/<media-id>
```

- Записи пишутся в этом порядке; `ZipEntry.time = 0`, поэтому одинаковые входные данные дают
  байт-идентичный архив.
- Media-записи упорядочены по `createdAt`, затем по `id` — порядок канонический и не зависит от
  порядка строк в базе.
- Разрешены только перечисленные записи. Лишняя запись, отсутствующая запись, дубликат имени,
  каталог, абсолютный путь, `..`, `\`, `:` или пустой сегмент — ошибка чтения.

Пределы:

```text
MAX_ENTRIES      = 64
MAX_ENTRY_BYTES  = 16 МиБ   (совпадает с пределом одного media item в приложении)
MAX_TOTAL_BYTES  = 64 МиБ
```

## 2. `manifest.json`

Описывает профиль и бинарные записи, чтобы пакет можно было проверить без разбора домена.

```json
{
  "profile": "SINGLE_PHYSICAL_OBJECT",
  "formatVersion": 1,
  "physicalObjectId": "<uuid>",
  "physicalObjectType": "HOLLOW | LOG_HIVE",
  "objectEntry": "object.json",
  "objectByteLength": 1234,
  "objectSha256": "<64 hex>",
  "media": [
    {
      "id": "<uuid>",
      "entry": "media/<uuid>",
      "mediaType": "IMAGE | VIDEO",
      "byteLength": 456,
      "sha256": "<64 hex>",
      "mimeType": "image/jpeg | null"
    }
  ]
}
```

`physicalObjectType` объявлен и в manifest, и в `object.json`; несовпадение — ошибка. Чтение
отклоняет любой другой `profile`, любой другой `formatVersion` и неподдерживаемый тип объекта.

## 3. `object.json`

Содержит собственные данные объекта и минимальный labelling/provenance snapshot.

```json
{
  "object": {
    "id": "<uuid>",
    "territoryId": "<uuid>",
    "type": "HOLLOW | LOG_HIVE",
    "sequenceNumber": 7,
    "latitude": 56.1961784,
    "longitude": 42.7480444,
    "createdAt": "<ISO-8601 instant>",
    "creatorObserverId": "<uuid> | null",
    "name": "Старое дупло | null"
  },
  "properties": { ... } | null,
  "media": [ ... ],
  "territory": { "id": "<uuid>", "code": "DEV-BENCH2", "name": "Тестовая территория" },
  "observer": { "id": "<uuid>", "code": "DEV-OBS1", "lastName": "…", "firstName": "…", "middleName": "… | null" } | null
}
```

Все перечисленные поля присутствуют всегда: отсутствующее необязательное значение пишется как
`null`, а не пропускается. Любое поле, не объявленное в этой схеме, делает пакет невалидным: схема
является полной, чтобы пакет не мог пронести чужую сущность.

### `properties`

Subtype-свойства конкретного типа. `null` означает, что subtype-строка не заполнена (возможное
состояние foundation rows после миграций): пакет экспортирует фактическое persisted state и не
выдумывает значения. Отсутствие свойств не является причиной отказа в экспорте.

`type = HOLLOW`:

```json
{
  "tree": "дуб",
  "entranceHeightCm": 180.0,
  "entranceAzimuthDeg": 127,
  "outerDiameterCm": 40.0,
  "internalDiameterCm": 25.0,
  "notes": "рядом с тропой | null"
}
```

`type = LOG_HIVE`:

```json
{
  "tree": "сосна",
  "entranceHeightCm": 250.0,
  "entranceAzimuthDeg": 90,
  "outerDiameterCm": 45.0,
  "material": "сосна",
  "internalDiameterCm": 30.0,
  "internalHeightCm": 120.0,
  "notes": null
}
```

Набор полей `properties` обязан соответствовать `type`: свойства другого типа — ошибка. Требования к
значениям совпадают с доменными (`tree` непустое и обрезанное, положительные размеры, азимут 0–359°).

### `media`

Только media этого объекта.

```json
{
  "id": "<uuid>",
  "physicalObjectId": "<uuid>",
  "type": "IMAGE | VIDEO",
  "relativePath": "physical-object-media/<objectId>/<mediaId>",
  "originalFileName": "дупло.jpg | null",
  "mimeType": "image/jpeg | null",
  "byteSize": 456,
  "sha256": "<64 hex>",
  "createdAt": "<ISO-8601 instant>",
  "packageEntry": "media/<uuid>"
}
```

Проверяется: `physicalObjectId` совпадает с объектом пакета, `relativePath` является
детерминированным app-owned путём этого объекта, `packageEntry` совпадает с `media/<id>`, `byteSize`
положителен и не превышает `MAX_ENTRY_BYTES`, `sha256` соответствует формату, а байты записи
совпадают с `byteSize` и `sha256`.

### `territory` и `observer` — labelling/provenance snapshot

Это **не** экспорт Territory или Observer как самостоятельных сущностей и **не** object-owned entity
graph. Snapshot существует только для того, чтобы объект можно было опознать человеком вне исходной
базы: обозначение `Дупло N` уникально лишь внутри одной Territory, а creator и `createdAt` — это
provenance.

- Territory snapshot ограничен `id`, `code`, `name`. Область, район, timestamps и настройки Territory
  в формат не входят.
- Observer snapshot ограничен `id`, `code`, `lastName`, `firstName`, `middleName`, потому что именно
  эти поля существуют в модели Observer. Если `creatorObserverId = null`, snapshot отсутствует
  (`observer: null`), и пакет остаётся валидным.
- Если `creatorObserverId` задан, snapshot обязан существовать и иметь тот же `id`; иначе пакет
  невалиден.
- `territory.id` обязан совпадать с `object.territoryId`.
- Другие объекты Territory, её observation points, её настройки, другие Observer records и любые
  связанные observation data в пакет не входят.

## 4. Что пакет не содержит

Пакет не содержит и не должен содержать:

- Bee и их метки;
- FlightCycle;
- ObservationPoint;
- weather ObservationPoint;
- attachments ObservationPoint и их bytes;
- другие Physical Objects;
- настройки приложения, Ареал, офлайн-карты, device-local state;
- список внешних ссылок на объект только потому, что они существуют в базе.

Наличие `Bee.sourceObjectId` или иной внешней ссылки на объект **не препятствует** экспорту и не
влияет на состав пакета.

## 5. Проверки чтения

Чтение пакета отклоняет:

- неподдерживаемый `profile` и неподдерживаемый `formatVersion`;
- неподдерживаемый тип объекта (в V1 — `APIARY`);
- malformed JSON, отсутствующие и лишние записи, дубликаты записей, небезопасные имена записей;
- необъявленные поля JSON;
- несовпадение `byteLength` и `sha256` для `object.json` и для любой media-записи;
- media-запись, принадлежащую другому объекту, либо путь, не являющийся детерминированным путём
  этого объекта;
- несовпадение identity/типа между manifest и `object.json`;
- несовпадение набора media-записей и набора media-записей в `object.json`;
- превышение пределов entry и archive.

## 6. Имя файла и запись

Предлагаемое имя файла — convenience metadata для человека, а не источник истины (источник истины —
manifest и `object.json`):

```text
<territoryCode>--hollow-<N>--<YYYY-MM-DD>--<shortId>.zip
<territoryCode>--log-hive-<N>--<YYYY-MM-DD>--<shortId>.zip
```

`territoryCode` проходит существующую sanitization convention. Запись выполняется через SAF
`CreateDocument` с MIME `application/zip` и начальным каталогом `Exchange/Data`; пользователь
подтверждает имя и место.

Пакет собирается и проверяется полностью в app cache, и только целый архив копируется в выбранный
destination. Гарантия — «partial или повреждённый пакет не записывается»: ошибка отсутствующего,
изменившегося по размеру или по SHA-256 media происходит до открытия destination. SAF создаёт
destination document в момент подтверждения имени, поэтому при отказе до копирования там может
остаться пустой документ; приложение его не удаляет, потому что это выбранный пользователем файл.
Отмена системного выбора ничего не меняет и не считается ошибкой.

## 7. Совместимость

- Экспорт read-only: он не изменяет объект, его media, Bee, sequence state и другие записи.
- Формат не зависит от графа наблюдательных данных: у него собственный профиль и собственный
  `formatVersion`, и он не является частью backup-формата.
- Обратный импорт пакета в Room этой версией не реализуется.

---

# Physical Object Collection Export V1 — `PHYSICAL_OBJECT_COLLECTION`

Collection profile переносит все Дупла либо все Колоды текущей Territory одним ZIP. Это отдельный
контракт с `profile = "PHYSICAL_OBJECT_COLLECTION"` и `formatVersion = 1`; individual profile
`SINGLE_PHYSICAL_OBJECT` и его entries не переопределяются.

## 8. Collection container и порядок entries

```text
manifest.json
territory.json
observers.json
objects/<object-id>.json
media/<object-id>/<media-id>
```

Вложенных ZIP и повторного `object.json` нет. `objects/` содержит ровно один JSON для каждого
объекта, а каждый media entry находится под UUID своего owner. Filename не является identity:
object UUID также присутствует в payload и manifest.

Encoder создаёт детерминированные bytes: JSON canonical, ZIP entry time фиксирован, objects
упорядочены по `sequenceNumber`, затем UUID; observers — по UUID; media каждого объекта — по
`createdAt`, затем UUID. Порядок ZIP entries: manifest, Territory, observers, canonical objects и
их canonical media. Одинаковый graph с одинаковыми media bytes даёт одинаковый архив.

## 9. Collection manifest

`manifest.json` фиксирует:

- `profile`, `formatVersion`;
- `territoryId`;
- один `physicalObjectType`: `HOLLOW` или `LOG_HIVE`;
- `objectCount`;
- descriptors `territory.json` и `observers.json`;
- descriptor каждого object entry: object UUID, entry, byte length и SHA-256;
- descriptor каждого media entry: media UUID, owner object UUID, concrete media type, MIME type,
  entry, byte length и SHA-256.

Manifest и фактические entries должны совпадать взаимно однозначно. Необъявленная, пропущенная,
повторная или небезопасная запись делает package невалидным.

## 10. Territory и Observer snapshots

`territory.json` хранится один раз и содержит минимальный read-only snapshot `id`, `code`, `name`.
Его `id` равен `manifest.territoryId`; каждый object имеет тот же `territoryId`.

`observers.json` содержит дедуплицированный canonical массив только тех creator Observer, на которых
ссылаются objects: `id`, `code`, `lastName`, `firstName`, `middleName`. В object payload хранится
только `creatorObserverId`. `null` допустим и не создаёт искусственный Observer. Каждый ненулевой
creator UUID имеет ровно один совпадающий snapshot; неиспользуемые и дублированные snapshots
запрещены.

## 11. Object payload и media ownership

Каждый `objects/<object-id>.json` содержит persisted state:

- `id`, `territoryId`, concrete `type`, `sequenceNumber`;
- `latitude`, `longitude`, `createdAt`, `creatorObserverId`, `name`;
- subtype-specific `properties` либо фактический `null`;
- metadata принадлежащих объекту media.

Все objects имеют один тип, равный `manifest.physicalObjectType`. Media metadata сохраняет UUID,
owner UUID, image/video type, original name, MIME type, byte size, SHA-256, createdAt и
детерминированный entry path. Media descriptor, object metadata и фактические bytes обязаны
совпадать; media другого owner или entry вне `media/<object-id>/` запрещено.

## 12. Strict validation и all-or-nothing

Reader отклоняет wrong profile/version, malformed или неканоничный graph, смешанные type/Territory,
duplicate object/observer/media ID, несовпадающий snapshot, лишний/пропущенный/небезопасный entry,
size/hash mismatch и превышение caps. Encoder проверяет те же graph invariants до записи.

Service получает все objects выбранного типа текущей Territory, загружает каждое owned media,
собирает временный ZIP в app cache и полностью декодирует/проверяет его. Только после этого архив
копируется в единственный SAF destination. Ошибка одного объекта или media отменяет весь export:
нет пропуска повреждённого объекта, результата «N из M» или partial collection package. После
подтверждения имени SAF мог уже создать пустой destination document; приложение не обещает удалить
выбранный пользователем документ.

Пустая коллекция возвращает typed empty result до запуска SAF UI и ZIP не создаёт. Отмена SAF
нейтральна и не даёт success/error feedback.

## 13. Collection boundary и filenames

Collection включает только Hollow либо только LogHive одной Territory и их object-owned media.
Она не включает Bee, FlightCycle, ObservationPoint, weather, ObservationPoint attachments, другой
Physical Object type/Territory, app settings, Area/map packages, backup data или external-reference
list. Внешние ссылки, включая `Bee.sourceObjectId`, export не блокируют.

Предлагаемые имена:

```text
<territoryCode>--hollows--<YYYY-MM-DD>.zip
<territoryCode>--log-hives--<YYYY-MM-DD>.zip
```

Territory code проходит ту же sanitization, что individual export. В V1 нет Apiary collection,
multi-type package или bulk delete. Обратный импорт collection package не реализован.
