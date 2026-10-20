# Мои точки — макет v1

**Статус: APPROVED BY OWNER, 2026-10-20.** PNG+MD пакет утверждён OWNER для
производственной реализации самостоятельных пользовательских точек. Утверждение
не является доказательством runtime-поведения: APK, Room, navigation и Samsung
в этом пакете не проверялись.

Проверенная база: `task/d106-stage0-overlay-registry`, `f7a848e`.
Производственный код и runtime-поведение новой функции в этой задаче не
проверялись на Samsung. PNG-файлы перечислены ниже как companion-артефакты
текущего design-пакета; они являются статическими mockup renderings и не
доказывают runtime fontScale, touch или persistence.

Связанные материалы: [mockup-first workflow](../Создание интерфейсов.md),
[D106 research](../../d106-my-points-research.md),
[D106 decision](../../decisions.md#d106-user-data-navigation--owner-decision-2026-10-18).

## 1. Область и OWNER-решения

Моя точка — самостоятельная пользовательская точка текущей Territory. Она не
является ObservationPoint, PhysicalObject, Bee, FlightCycle или GPX sample.

Обязательные свойства:

- название;
- координаты, подтверждённые текущим map/GPS selection;
- текущий Observer prerequisite сохраняется без нового выбора наблюдателя.

Необязательные свойства:

- заметка;
- несколько фотографий.

Фото используют существующий multi-photo flow без нового лимита количества.
Видео, категории, цвета и дополнительные классификаторы не входят в этот
макет. Дополнительная кнопка создания в списке не добавляется: создание входит
через существующий `+` и chooser.

При создании после успешной локальной Room-записи visibility сохраняется `ON`;
сразу после подтверждённого save пользователь видит marker и сохранённую карточку.
Это правило относится к MyPoint и не меняет GPX: новый или импортированный GPX
по-прежнему имеет visibility `OFF` до явного включения.

OWNER-approved behavior: если точка выключена и пользователь выбирает
`Показать на карте`, сначала сохраняется visibility `ON`, затем камера
фокусируется и точка выделяется. Временный режим показа без изменения
сохранённой visibility не используется.

## 2. Визуальный пакет

[Обзор PNG](my-points-v1-overview.png) · [галерея с полноразмерными файлами](my-points-v1-review.html) ·
[воспроизводимый HTML-источник](my-points-v1.html) · [измерения композиции](my-points-v1-qa.json).

PNG подготовлены для целевого portrait-окна Samsung 1080×2340, 360×780dp,
со статической типографической моделью fontScale1.7. PNG задаёт композицию; этот MD задаёт смысл,
состояния и действия.

Companion-файлы (ссылки открывают PNG):

- [my-points-v1-a-chooser-top.png](my-points-v1-a-chooser-top.png)
- [my-points-v1-a-chooser-bottom.png](my-points-v1-a-chooser-bottom.png)
- [my-points-v1-b-location.png](my-points-v1-b-location.png)
- [my-points-v1-c-create-empty.png](my-points-v1-c-create-empty.png)
- [my-points-v1-c-create-empty-bottom.png](my-points-v1-c-create-empty-bottom.png)
- [my-points-v1-c-create-photos-top.png](my-points-v1-c-create-photos-top.png)
- [my-points-v1-c-create-photos-bottom.png](my-points-v1-c-create-photos-bottom.png)
- [my-points-v1-d-list.png](my-points-v1-d-list.png)
- [my-points-v1-d-list-bottom.png](my-points-v1-d-list-bottom.png)
- [my-points-v1-d-empty.png](my-points-v1-d-empty.png)
- [my-points-v1-e-card-top.png](my-points-v1-e-card-top.png)
- [my-points-v1-e-card-bottom.png](my-points-v1-e-card-bottom.png)
- [my-points-v1-f-edit-top.png](my-points-v1-f-edit-top.png)
- [my-points-v1-f-edit-bottom.png](my-points-v1-f-edit-bottom.png)
- [my-points-v1-f-delete.png](my-points-v1-f-delete.png)
- [my-points-v1-f-delete-scrolled.png](my-points-v1-f-delete-scrolled.png)
- [my-points-v1-f-delete-error.png](my-points-v1-f-delete-error.png)
- [my-points-v1-save-error.png](my-points-v1-save-error.png)
- [my-points-v1-validation-error.png](my-points-v1-validation-error.png)
- [my-points-v1-visibility-error.png](my-points-v1-visibility-error.png)

### Состояние B: координаты

`my-points-v1-b-location.png` реконструирует существующий Samsung map/location
reference и аннотирует новое назначение результата. Это не новый GPS screen.
Состояние использует карту, центральную красную мишень, ручное перемещение
и существующую кнопку `OK` (латинская надпись в коде). `OK` → форма C.
GPS/recenter доступен ДО входа в selection; в selection idle controls скрыты.
Картографическая подложка иллюстративна: это реконструкция из существующего
снимка карты, а не новый device screenshot и не новый экран.
Необходимое изменение здесь — только назначение результата на draft MyPoint.

## 3. A — chooser

Вход: существующая кнопка `+` на карте при выполненном текущем GPS/selection
prerequisite. Открывается существующий chooser `Что создать?` в том же sheet или
route-контуре.

Композиция: `ИССЛЕДОВАТЕЛЬСКИЕ ОБЪЕКТЫ` → `Точка наблюдения`, `Дупло`,
`Колода`, disabled `Ловушка — пока недоступно`, disabled `Пасека — пока недоступно`
→ разделитель → `ПОЛЬЗОВАТЕЛЬСКИЕ ОБЪЕКТЫ` → `Моя точка`.
Прокручивается тело AlertDialog; заголовок и `Отмена` остаются доступны отдельно.
Существующие действия и disabled-состояния сохраняются. Новое действие передаёт
захваченные Territory, Observer и координатный контекст в MyPoint flow.

Не добавляются отдельное меню, новая кнопка `Создать` в списке, новый GPS-экран
или новый Observer chooser.

Обязательные состояния:

- chooser открыт;
- chooser закрыт через Back/Cancel без создания;
- выбранная `Моя точка` переходит в состояние B.

## 4. B — подтверждение координат

Сохраняется существующий map selection flow:

1. карта показывает текущий target;
2. `ОК` подтверждает именно текущий target;
3. ручное перемещение меняет target;
4. recenter можно выполнить до входа в selection; отдельной кнопки внутри нет;
5. GPS сам не перезаписывает уже выбранные координаты;
6. системный Back отменяет создание и возвращает на обычную карту без записи.
   При редактировании координат Back возвращает в форму с прежней draft-координатой.
   Отдельная кнопка Cancel на карте не добавляется.

После `ОК` открывается форма C с черновыми координатами. До общего Save
пользовательская точка и фото в Room не считаются созданными.

Общий Save создаёт `user_points` и принадлежащие фото атомарно. Только после
успешного локального commit visibility устанавливается `ON`, marker добавляется
в текущий map overlay, и открывается сохранённая карточка. При ошибке записи
форма остаётся открытой, а marker и карточка сохранённой точки не появляются.

## 5. C — создание

### C1. Empty

Заголовок: `Новая точка`.

Форма прокручивается одним вертикальным контуром и содержит:

- обязательное поле `Название`;
- необязательное поле `Заметка`;
- отображение выбранных координат и `Изменить место` → B → обратно в эту форму;
- actions `Сделать фото` и `Выбрать из галереи`;
- full-width `Сохранить` ближе к низу формы;
- full-width `Отмена` рядом с Save в том же вертикальном action stack.

При font scale 1.7 actions остаются вертикальными и достижимыми после обычного
scroll. Нажатие Сохранить при пустом названии показывает «Введите название»
у поля и возвращает его в viewport; validation-error и persistence-error — разные состояния.

Кнопки используют точные labels существующего media UI: `Сделать фото` и
`Выбрать из галереи`. Новые синонимичные labels `Камера` и `Из галереи` не
добавляются.

### C2. Photos

После добавления фото появляется горизонтальная лента thumbnail-карточек.
Ориентир существующего PhysicalObject UI — 112dp thumbnail и отдельная touch
область удаления 48dp. Карточка использует preview и `×` для удаления, как существующая лента;
имя фото используется в accessibility description, отдельной видимой подписи нет. Лента допускает несколько фотографий и не вводит новый
лимит.

Камера использует `TakePicture` в app-owned staging file. Галерея использует
Photo Picker (`PickMultipleVisualMedia`/image-only для MyPoint), затем поток
копируется в managed storage; URI не является единственным источником данных.

Preview использует EXIF-aware `ImageDecoder` и downsampling. Нажатие thumbnail
открывает внешний `ACTION_VIEW` через `FileProvider`; это не обещает отдельный
встроенный fullscreen viewer.

Ошибки добавления фото показываются в текущем контексте формы; Save остаётся
доступным для исправления, если остальные поля валидны.

## 6. D — список

Вход: `Карта → Визуализация → Данные на карте → Мои точки`. Используется существующая вложенная sheet/page
структура без нового меню.

Каждая строка содержит:

- самостоятельный visibility switch слева;
- полное кликабельное название справа с естественным переносом;
- доступный touch target не менее 48dp.

Нажатие названия открывает E. Переключатель меняет только visibility и не
открывает карточку. Нажатие маркера на карте открывает ту же карточку E.

Состояния:

- loading — progress без ложного empty;
- populated — список пользовательских точек текущей Territory;
- all OFF — непустой список с выключенными switches;
- read error — `Не удалось загрузить Мои точки` и `Повторить`;
- visibility write error — прежнее значение switch восстанавливается и рядом
  показывается `Не удалось сохранить видимость`/`Повторить`.

Новая точка не появляется в списке до успешной локальной записи.
Несколько ON-точек отображаются одновременно. Выбор сохраняется по UUID
территории и точки, переживает перезапуск и переключение территории A → B → A.
Switch не изменяет точку, фотографии, GPX или фильтры исследовательских объектов.
При ошибке записи остаётся предыдущее подтверждённое значение; ложный ON не показывается.

## 7. D-empty — пустой список

Показывается `Нет сохранённых точек` и обычный Back. В empty state нет новой
кнопки создания: создание остаётся на карте через `+` и chooser.

Пустой список не смешивается с ошибкой чтения и не показывает фиктивные rows,
switches или placeholder markers.

## 8. E — карточка

Карточка показывает:

- полное название;
- непустую заметку, если она сохранена;
- координаты;
- photo hero/preview примерно 1.8 aspect ratio при наличии фото;
- горизонтальную ленту остальных thumbnails при нескольких фото;
- `Показать на карте`;
- `Редактировать`;
- отдельную команду `Удалить точку`, без дополнительного меню.

OWNER-approved `Показать на карте` для OFF-точки сначала сохраняет ON, затем
фокусирует камеру и выделяет тот же UserPoint. Ошибка visibility оставляет карточку открытой, прежнее значение OFF и показывает
`Не удалось сохранить видимость` с `Повторить`; камера не двигается и маркер
не выделяется. При повторе сначала снова выполняется сохранение ON.
Для уже ON-точки команда перемещает карту и выделяет её маркер.
Для обычного switch используется та же ошибка без перемещения карты.

Фото открываются тем же внешним `FileProvider`/`ACTION_VIEW` viewer. Удаление
карточки открывает отдельное подтверждение F; menus не добавляются.

## 9. F — edit/delete

### F1. Edit

Заголовок: `Редактирование`.

Форма повторяет C, с заполненными названием, заметкой, координатами и текущими
фото. Координаты снова проходят B и возвращаются в edit draft. Фото можно
добавлять и удалять в той же multi-photo ленте. Замена = удалить старое и добавить
новое существующими действиями, без новой кнопки или replace API.
Удаление сохранённого фото до Save меняет только draft; Cancel сохраняет оригинал.

Save и Cancel — full-width vertical actions ближе к низу прокручиваемой формы.
Cancel отбрасывает draft changes и не меняет сохранённую точку/visibility.
Save пишет локально и только после успеха закрывает форму.

### F2. Delete confirmation

Подтверждение явно называет точку по названию и предупреждает, что её заметка,
координаты и managed photos будут удалены. Actions: `Удалить` и `Отмена`.

Текст показанного состояния: `«{название}» будет удалена из Bee Search вместе с
фотографиями. Восстановить её нельзя. Исходные файлы в другой папке телефона
не затрагиваются.` При отсутствии фото убрать слова `вместе с фотографиями`.
Внутри команда привязана к UUID/территории, название не идентификатор операции.

При Cancel точка остаётся без изменений. При успешном Delete карточка закрывается,
строка исчезает из списка, marker и принадлежащие managed photo bytes очищаются
по существующему owner-aware lifecycle.

### F3. Delete error

PNG F3 показывает ошибку ДО успешного удаления записи: `Не удалось удалить точку`,
карточка и данные сохраняются; `Повторить` снова открывает подтверждение.
Во время удаления повторный submit блокируется; закрытие диалога не изображает успех.
Если запись уже удалена, а очистка managed bytes отложена/не удалась, нельзя
возвращать карточку как существующую или повторять SQL-delete: показывается
отдельное сообщение `Точка удалена. Не удалось удалить часть её фотографий`
и повтор только cleanup по существующему lifecycle. Это поведенческое состояние,
не отдельный новый экран. Внешние исходники не затрагиваются; файловая очистка
не является SQL-транзакцией. Не добавлять GPX-журнал или новую систему recovery
в рамках этого design-пакета.

## 10. Save/error and accessibility contract

Save/progress блокирует повторный submit, но не скрывает текущий draft. Ошибка
валидации называет поле (`Название` обязательно; координаты должны быть
конечными и допустимыми). Ошибка persistence предлагает повторить действие и
сохраняет исправимый draft, если это безопасно.

Все основные actions, Back, switches, thumbnails и delete controls имеют минимум
48dp touch target и понятные content descriptions. Long title/note
переносятся без ellipsis, clipping или горизонтального scroll. Один screen-level
vertical scroll сохраняется при font scale 1.7 и IME.

One-shot route/navigation requests имеют lifecycle request → handle → consume;
возврат на карту после завершения не открывает форму повторно.

## 11. Data and implementation boundary

Принятая минимальная модель — отдельные `user_points` и
`user_point_photos`, связь один-to-many, без `UNIQUE(user_point_id)` и без нового
лимита фото. `user_points` принадлежит `Territory` по UUID; current Observer
prerequisite для входа в поток сохраняется без изменения и не превращает MyPoint
в ObservationPoint. Visibility — device-local persisted attribute, а не research
domain status.

Media implementation reuses the existing streaming writer, managed-copy
validation, EXIF preview, external viewer, and owner namespace pattern. It does
not add a third general media framework.

Эти persistence/architecture details синхронизируются с authoritative
product/domain/data/architecture docs в рамках принятого D106 дополнения.

## 12. Acceptance boundary

Этот UI contract не утверждает runtime visual equivalence, runtime font scale, IME,
touch semantics, camera/gallery availability, Photo Picker behavior, external
viewer availability, Room migration, file cleanup, MapLibre hit testing или
Samsung field behavior. PNG render is a static design artifact only.

## 13. Существующие компоненты и минимальная UI-дельта

| Основа в production | Что сохраняется | Минимальное дополнение после approval |
|---|---|---|
| [CreateRecordTypeChooserDialog.kt](../../../app/src/main/java/org/beesearch/app/ui/map/CreateRecordTypeChooserDialog.kt) | AlertDialog, старые callbacks и недоступные действия | Заголовки секций, divider, Моя точка; scroll body при закреплённой Отмена |
| [BeeMap.kt](../../../app/src/main/java/org/beesearch/app/ui/map/BeeMap.kt), [MainMapScreen.kt](../../../app/src/main/java/org/beesearch/app/ui/map/MainMapScreen.kt) | Та же мишень, camera target, OK и system Back | Новый адресат существующего результата координат; без GPS-экрана/новой карты |
| [PhysicalObjectEditor.kt](../../../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectEditor.kt) | Заголовок/Back, OutlinedTextField, vertical scroll, AdaptiveActions, 112dp media strip | Узкая форма пользовательской точки без дерева/размеров/азимута; несколько фото, без дополнительного лимита |
| [PhysicalObjectCreationRoute.kt](../../../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectCreationRoute.kt), [PhotoPreviewDecoder.kt](../../../app/src/main/java/org/beesearch/app/ui/media/PhotoPreviewDecoder.kt) | Camera/picker → managed copy, preview/EXIF, внешнее открытие | Image-only owner mapping; никаких новых media framework/редакторов изображений |
| [PhysicalObjectCards.kt](../../../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectCards.kt), [PhysicalObjectDetailRoute.kt](../../../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectDetailRoute.kt) | Hero/лента фото, координатный блок, card actions | UserPoint card без research attributes/export; форма редактирования сохраняет draft до Save, а не немедленно применяет координаты |
| [GpxDisplayPage.kt](../../../app/src/main/java/org/beesearch/app/ui/map/GpxDisplayPage.kt), [GpxTracksPage.kt](../../../app/src/main/java/org/beesearch/app/ui/map/GpxTracksPage.kt) | Sheet/header/list spacing, имеющийся вход Мои точки | Собственная строка с двумя действиями Switch/название; GPX row целиком toggle и не меняется ради сходства |
| [GpxDisplayPage.kt](../../../app/src/main/java/org/beesearch/app/ui/map/GpxDisplayPage.kt): DeleteGpxDialog | AlertDialog, scrollable text, вертикальные кнопки1.7 | Текст/UUID пользовательской точки, ошибка до commit и повторное подтверждение |

Это план повторного использования, не утверждение о существующей реализации MyPoint.
Для `PhysicalObjectForm`/card приватность и типы требуют небольших точек
извлечения presentation; не превращать их в универсальный form framework.

## 14. Источники изображений и статическая QA

20 PNG состояний имеют размер1080×2340. Обзор1080×1710 — уменьшенная контактная
страница, не замена полноразмерным эталонам. Верх/низ формы или диалога — одна
и та же поверхность при разных scroll offsets, а не дополнительные экраны.

HTML использует layout360×780 с коэффициентом3 px/dp, body16sp×1.7=27.2px,
button14sp×1.7=23.8px, line-height34/30px. Title30px/38px line-height откалиброван
по реальному увеличенному GPX header; Android14+ применяет нелинейное масштабирование
к разным размерам шрифта. Это модель композиции, не применение Android system
fontScale в работающем приложении; fallback CSS font также не pixel-perfect Roboto.
В реализации использовать Material typography и реальный system fontScale,
не hardcode этих CSS-размеров и не уменьшать системный шрифт.

Проверено браузерным renderer и визуальным просмотром:

- Нет horizontal text overflow в controls/fields/headings во всех20 состояниях.
- Нет button targets меньше48×48dp, включая Back, switches и удаление фото.
- Wheel scroll действительно изменил offset: chooser0→99, create photos0→279,
  list0→66, card0→213, edit0→258; dialog text0→26. Последние пункты и кнопки
  доступны в нижних companion PNG. Отмена chooser не перемещается со scroll body.
- Длинное русское название переносится словами; нет letter-per-line layout.
  Частичный следующий item у края scroll viewport означает продолжение списка;
  он полностью достижим прокруткой и не является потерей содержимого.
- Горизонтальная media strip прокручивается независимо, как существующий LazyRow.
  Полный warning диалога показан парой delete/delete-scrolled; actions вне scroll text.
- Validation и persistence error разделены; visibility error сохраняет OFF.

Полные измерения: `my-points-v1-qa.json`. На Samsung новые экраны не запускались;
IME, TalkBack, camera/gallery, save/delete и native navigation требуют отдельной
проверки после разрешённой реализации. Статическая проверка не объявляет их PASS.

Происхождение visual assets:

- `my-points-v1-assets/native-map.png` — неизменённый прежний Samsung `dev-home.png`
  из filename qualification2026-10-19; карта используется как иллюстративная подложка.
- `my-points-v1-assets/native-list.png` — неизменённый прежний `restart-list.png`;
  из него CSS показывает только системные полосы. Время21:56 — время reference,
  не новый screenshot и не техническая подпись в продукте.
- Фотографии — CSS-crops из уже утверждённого `objects-ui-approved-v1.png`,
  без его встроенных кнопок. Они иллюстративны, не принадлежат новой реальной точке.
  В production используются фото пользователя, previews не переписывают originals.

Ни один прежний approved mockup или исследовательский отчёт не переписан.
Production-код/Room/Registry/media pipeline сейчас не изменялись. APK не собирался,
Samsung не менялся, staging/commit/push не выполнялись. Stage1/H05 OPEN.
PNG+MD approval завершён OWNER 2026-10-20. Следующий этап — отдельная
production implementation и её runtime/device verification.

Технические справки для границы статической проверки:
[Android14 nonlinear font scaling](https://developer.android.com/about/versions/14/features#non-linear-font-scaling),
[Playwright screenshot export](https://playwright.dev/docs/screenshots).
Отдельный read-only визуальный review финальных B/edit/delete-scrolled PNG
не обнаружил оставшихся конкретных расхождений; runtime проверки им не выполнялись.

После approval нужны отдельные checks для create/edit/empty/photos/delete/error,
visibility persistence, Back/recreation one-shot behavior, old chooser actions,
coordinates, EXIF portrait, large photos, and Samsung 1.7 visual/device gates.


## 15. Back, отмена, отсутствие данных и доступность

- Back/`Отмена` в chooser закрывают его без записи; прежние исследовательские
  callbacks и проверка Observer не меняются.
- Back/`Отмена` формы до сохранения отбрасывают draft и только принадлежащие ему
  временные фото. Создание возвращает на карту; редактирование — в исходную карточку.
  Нового диалога несохранённых изменений не добавляется. Во время локального
  сохранения/удаления действуют существующие ограничения повторных действий.
- `Изменить место` сохраняет весь draft (название, заметка, фото), показывает
  существующий selection с текущей координатой; `OK` заменяет только draft-координату.
  Общий `Сохранить` применяет изменения. Текстовый ввод широты/долготы не добавляется.
- После успешного создания открывается сохранённая карточка; после редактирования
  возвращается та же карточка с обновлёнными данными. После удаления — источник
  открытия (список либо карта), без уже удалённой строки/маркера.
- Back карточки возвращает в список или на карту в зависимости от входа;
  Back вложенного списка возвращает к `Данные на карте`. Закрытие sheet сохраняет
  visibility. Возврат без нового действия не повторяет координатный запрос.
- Нет заметки/фото: соответствующие блоки карточки отсутствуют, без заглушек
  и пустого hero. В форме без фото остаются две существующие media-команды.
- Ошибка добавления/просмотра фото остаётся в текущем контексте; остальные поля
  и сохранённые фото сохраняются. Отмена системной камеры/picker ничего не добавляет.
- Пустое/пробельное название → `Введите название`, фокус на поле. Невалидные
  координаты блокируют Save и предлагают `Изменить место`. Loading/read error
  списка не трактуется как отсутствие точек. `Повторить` повторяет чтение.
- TalkBack: отдельные targets для switch и названия, состояние ON/OFF,
  описания `Назад`, `Удалить фото …`, `Открыть фотографию …`; порядок чтения
  соответствует визуальному. Ошибка объявляется без потери draft/focus.
- IME уменьшает доступное окно, форма прокручивается к полю/ошибке и действиям;
  system bars/IME не перекрывают controls. Физическая IME/TalkBack проверка pending.

## 16. Реальные компоненты и минимальные изменения

| Основа (существует) | Необходимое расширение |
|---|---|
| [CreateRecordTypeChooserDialog](../../../app/src/main/java/org/beesearch/app/ui/map/CreateRecordTypeChooserDialog.kt) / AlertDialog, OutlinedButton, TextButton | Два заголовка, divider, `Моя точка`; прокрутка тела с доступной `Отмена` |
| [BeeMap](../../../app/src/main/java/org/beesearch/app/ui/map/BeeMap.kt), [PhysicalObjectLocationControls](../../../app/src/main/java/org/beesearch/app/ui/map/MainMapScreen.kt) | Новое назначение результата существующего selection, без нового экрана |
| [PhysicalObjectEditor](../../../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectEditor.kt) / OutlinedTextField, AdaptiveActions, scroll, media thumbnails | Узкая форма названия/заметки/координат/фото, без полей дупла/колоды |
| [PhysicalObjectCreationRoute](../../../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectCreationRoute.kt), [PhysicalObjectMediaPreview](../../../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectMediaPreview.kt), [PhotoExternalViewer](../../../app/src/main/java/org/beesearch/app/ui/media/PhotoExternalViewer.kt) | Та же камера, image-only Photo Picker, managed copy, внешний просмотр; новый владелец фото |
| [GpxDisplayPage](../../../app/src/main/java/org/beesearch/app/ui/map/GpxDisplayPage.kt), [MapDataPanel](../../../app/src/main/java/org/beesearch/app/ui/map/MapDataPanel.kt) | Включить существующий вход, добавить список точек. Отдельные switch/title actions; не менять GPX row-toggle |
| [PhysicalObjectCards](../../../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectCards.kt), [PhysicalObjectDetailRoute](../../../app/src/main/java/org/beesearch/app/ui/physicalobjects/PhysicalObjectDetailRoute.kt) | Узкая карточка с фото, координатами, показом/редактированием; то же подтверждение удаления и inline error |
| [MapOverlayRegistry](../../../app/src/main/java/org/beesearch/app/ui/map/MapOverlayRegistry.kt) / существующая карта | Маркер точки → та же карточка, persist visibility → focus/highlight; без второго Registry/map lifecycle |

**Конкретные расхождения:** form/card/media helpers сейчас private и зависят от
PhysicalObject. Их нельзя подключить к точке без узкой адаптации владельца и
presentational частей. Не расширять HollowForm десятками флагов и не создавать
универсальный UI/media framework. GPX switch сейчас переключает всю строку;
для точки нужны два самостоятельных target. Существующий camera request только
центрирует — выделение нового маркера требует отдельного feature подключения.
Карточка без фото не создаёт новую систему просмотра. Для новой MyPoint visibility
после успешного создания сохраняется ON; GPX visibility остаётся OFF по его
отдельному принятому контракту.
Маркер/selected treatment используют имеющуюся систему карты; новая пользовательская
настройка значков/цветов не вводится. Его точная форма не переутверждается этим пакетом.

Исследование сохранено без переписывания: его открытый вопрос о скрытой точке
закрыт текущим OWNER-решением persist ON → focus/highlight в этом контракте.
Историческая документация ещё описывает disabled-заглушку `Мои точки`; она
заменяется текущим D106 owner-решением, история не удаляется.
Stage1/H05 остаются OPEN. Две таблицы — предварительная модель, migration не выполнялась.
Backup/Restore — позднее. Никаких новых framework, APK или device действий здесь нет.

## 17. Как проверена статическая композиция

Источник рендера: [my-points-v1.html](my-points-v1.html), 360×780 условных dp,
экспорт 1080×2340px, 3px/dp. System bars и map/list reference — сохранённые снимки
Samsung в `my-points-v1-assets`; фотографии — иллюстративные crops существующего
[objects-ui-approved-v1.png](objects-ui-approved-v1.png), не пользовательские данные.
Текст моделируется увеличенными CSS-размерами (body 16×1.7 = 27.2px), без искусственного
уменьшения системного шрифта. Это консервативная линейная design-модель: Android 14+
использует [нелинейное масштабирование](https://developer.android.com/about/versions/14/features#non-linear-font-scaling),
поэтому точное равенство Compose/Samsung glyph metrics **не заявляется**.
[Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
задают минимум 48dp для интерактивных targets.

Проверяются верх/низ chooser, форм, карточки и списка: scroll достигает нижних
команд, `Отмена` chooser закреплена вне прокрутки, длинное русское название
переносится по словам. Горизонтальный scroll допустим только у фотоленты.
Частично видимая строка/поле у края scroll viewport — продолжение содержимого,
а не обрезанная недоступная кнопка; нижние PNG показывают достижимость.

Статические геометрические измерения: [my-points-v1-qa.json](my-points-v1-qa.json).
Они проверяют CSS scroll, overflow и targets; runtime navigation, IME, TalkBack,
persist-before-render и физический Samsung не проверялись. После OWNER approval
они остаются отдельными критериями реализации.

PNG+MD approval завершён OWNER 2026-10-20. Runtime navigation, IME, TalkBack,
persist-before-render, Room migration и физический Samsung остаются отдельными
критериями production implementation и здесь не объявляются проверенными.


Примечание к утверждению: PNG-композиции сохранены без перерисовки. Видимые OFF-строки
показывают ранее скрытые точки; начальное состояние новой точки утверждено ON.
Надпись DRAFT на историческом обзорном PNG относится к моменту экспорта; текущий
статус пакета определён решением OWNER от 2026-10-20 и этим MD.
