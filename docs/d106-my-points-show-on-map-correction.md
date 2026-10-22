# D106 — «Показать на карте», basemap и единый маркер: причина и исправление

**Статус: причина установлена, минимальное исправление реализовано, целевые
проверки PASS; STOP для OWNER review.** Репозиторий
`C:\App\Bee_search_stage0_overlay_registry`, ветка
`task/d106-stage0-overlay-registry`, baseline/expected HEAD
`a8d51cf6617d9ac1a69aae57f25a4bea00f474ba`.

Это уточнение после физической проверки 2026-10-22. Оно отменяет только
прежнее требование об янтарном selected-ореоле/увеличении; остальные решения
D106 сохраняются. Исторические PNG, отчёты и §18 mockup не переписаны.

External research: **not applicable** — the fix uses the existing Compose
`ViewModel`/`StateFlow` and MapLibre SymbolLayer/style-image lifecycle already in
the project; no new API, dependency or lifecycle was introduced.

## 1. Подтверждённая причина VECTOR → ONLINE

Фактический путь:

```text
UserPointDetailRoute «Показать на карте»
  -> UserPointDisplayViewModel.showOnMap(id)
       (при OFF сначала persistVisibility ON; при ошибке возврат без перехода)
  -> onShowOnMap(point)
  -> MainViewModel.showUserPointOnMap(point)
       MapCenterRequest(point.lat, point.lon)
       manualRoute = AppRoute.CurrentTerritory
  -> MainActivity route switch
       UserPointDetailRoute удаляется, CurrentTerritoryScreen/BeeMap создаётся заново
  -> BeeMap: var developerBasemap by remember { mutableStateOf(DeveloperBasemap.ONLINE) }
  -> style request = ONLINE
```

`BeeMap` владел выбором подложки только в локальном `remember`. Переход в
карточку заменяет экран карты, поэтому composition и это состояние
уничтожаются. При возврате новый `BeeMap` инициализируется `ONLINE`, даже если
до карточки была выбрана установленная `VECTOR`. Затем `MapCenterRequest`
центрирует точку; selected-состояние остаётся в Activity-scoped
`UserPointDisplayViewModel`, поэтому маркер выглядел выбранным уже на online.

Дополнительно проверено:

- **Back из карточки** идёт по тому же route-return и также теряет локальное
  состояние; это не специфично для «Показать на карте».
- Происхождение открытия карточки (список, маркер, `origin`) не влияет на
  basemap: все пути заменяют map composition.
- Строка `remember { DeveloperBasemap.ONLINE }` присутствует и в parent
  `80f08e6`, и в `a8d51cf`. Поэтому **нельзя утверждать, что это новая
  регрессия `a8d51cf`**; дефект имеет более ранний lifecycle-путь.
- Существующая ветка «если выбран vector/hybrid, а активный offline package не
  Ready — вернуть ONLINE» остаётся отдельным fail-safe. На Samsung vector package
  был Ready и реально отрисовывался; эта ветка не является объяснением
  наблюдённого сброса. Новая автоматическая логика для отсутствующего пакета не
  изобреталась.

Samsung evidence: the pre-existing, still-untracked local report
`docs/d106-my-points-samsung-review-final.md`, with local originals in
`build/evidence/my-points-samsung-review-20261022/`
(`offline-normal-triangle-gps` → `offline-card-from-triangle` →
`offline-selected-centered`). This is Samsung evidence of the defect; the new
build was not installed on Samsung.

## 2. Исправление basemap

- [MapBasemap.kt](../app/src/main/java/org/beesearch/app/ui/map/MapBasemap.kt):
  `DeveloperBasemap` и один `MapBasemapViewModel` с Activity-scoped
  `StateFlow`/`select`.
- [MainMapScreen.kt](../app/src/main/java/org/beesearch/app/ui/map/MainMapScreen.kt):
  `viewModel()` создаётся на уровне карты; при замене карты карточкой тот же
  Activity store возвращает тот же ViewModel. Значение и callback передаются в
  `BeeMap`.
- [BeeMap.kt](../app/src/main/java/org/beesearch/app/ui/map/BeeMap.kt): локальный
  `remember` удалён; `developerBasemap` — параметр, изменения идут через
  `onDeveloperBasemapChange`. Package fail-safe теперь также вызывает callback, а
  не пишет локальную копию.

Это **одно** состояние, а не второе независимое; пользовательские точки его не
владеют. `MapOverlayRegistry`, MapLibre style-image seam, Style lifecycle и
существующий `MapCenterRequest` не менялись. Выбор подложки не связан с
перемещением камеры: style request зависит от значения и путей пакета, а не от
камеры.

Верхнеуровневый `Decomposition reviewed; kept cohesive because: MainMapScreen и
BeeMap остаются единой map-shell композицией; из BeeMap вынесено только владение
состоянием подложки, новых ответственностей не добавлено.`

## 3. Удаление янтарной подсветки

- [UserPointMapOverlay.kt](../app/src/main/java/org/beesearch/app/ui/userpoints/UserPointMapOverlay.kt):
  удалён отдельный `selection` SymbolLayer и фильтр selected. Остаётся один
  `markers` SymbolLayer с `USER_POINT_TRIANGLE_IMAGE`; GeoJSON по-прежнему хранит
  `selected` для внутренней навигации/hit testing.
- [UserPointMarkerImages.kt](../app/src/main/java/org/beesearch/app/ui/userpoints/UserPointMarkerImages.kt):
  удалены selected image, янтарный `#FFB91F` halo и увеличенный dark pad.
  Генерируется одно изображение: графитовый `#2B2B2B` треугольник вверх с
  обводкой `#F2F2F2` ~1.4 dp. Все точки одного размера.
- 48 dp `UserPointMarkerTargets`, semantics, open-card, ON/OFF, GPS, красный
  центр, research, GPX, фото, дата и формы не изменены. Отдельной кнопки снятия
  выделения нет.

## 4. Проверки

| Проверка | Статус |
|---|---|
| Full JVM | **1073/1073 PASS**, 130 классов, 0 failures/errors/skips |
| Basemap state regression | `MapBasemapViewModelTest` PASS: online default; select vector; same Activity `ViewModelStore` returns same owner with vector |
| API29 targeted Android | **28/28 PASS** (`UserPointCreationUiTest`, `UserPointCardMapActionTest`, `UserPointListUiTest`, `UserPointMapOverlayNativeTest`, `UserPointStageDUiTest`, `UserPointRepositoryTest`) |
| Native marker | markers layer is `SymbolLayer`; selected legacy layer is absent; only triangle image present; style reload retains it |
| Build/lint gates | all **7/7 PASS** (`assembleDebug/Beta/Release`, `assembleDebugAndroidTest`, `lintDebug/Beta/Release`) |
| Runtime online | Show on map centered selected point under red pointer; no amber halo; normal triangle remains compact |
| Actual VECTOR round trip | **NOT RUN** on emulator: no ready offline vector package; selecting vector opens the existing offline-maps action (Area) rather than pretending vector is active |
| Samsung build | **NOT INSTALLED**; TEST-S25-02 and private data untouched |
| Full Android suite / TalkBack / process death / fault paths / full basemap matrix | **NOT RUN** |
| `git diff --check` | PASS |

### Emulator evidence

API29 AVD `emulator-5556`, 1080×2280, `fontScale=1.7`, DEV `1.3.0` (8).
Локальный debug APK SHA-256:
`90D7F6282AB96A6A93C7B6FD6D7ED62E8F8E724F27CF024E97B7D55C11EEC594`;
установленный на эмулятор APK совпал по SHA. Снимок сделан с этой сборки.

- [Карта после «Показать на карте»: выбранная C в центре под красным указателем;
  обычный B20-треугольник; ни у одного нет янтарного
  ореола](ui/evidence/my-points-show-on-map-20261022/map-show-on-map-centered-17.png)

Selected C находится ровно под красным указателем и поэтому закрыт им — это
целевое совмещение. Обычный B20 виден компактным графитовым треугольником. В
этой сессии эмулятор не имел готового vector package, поэтому скриншот
показывает online-подложку, а не VECTOR→VECTOR. Состояние VECTOR отдельно
проверено JVM-тестом Activity-scoped store; фактическая офлайн-отрисовка остаётся
NOT RUN.

## 5. Ограничения и Git

- Samsung не обновлялся; новые Samsung screenshots/физический PASS не заявлены.
- Полный Android suite не перезапускался: выполнены адресные 28.
- Полная матрица ONLINE/VECTOR/SENTINEL/HYBRID, офлайн-тайлы, TalkBack,
  process death, ENOSPC/provider failure и sensor accuracy — NOT RUN.
- Room schema/migrations не менялись.
- Коммит: один локальный implementation commit после успешных проверок; push не
  выполнялся. Точный hash — в handoff/финальном ответе. Прежние dirty/untracked
  Samsung/GPX/research/evidence файлы сохранены и не входят в этот commit
  (кроме нового отчёта/скриншота и документации этой задачи).
- Stage1/H05 остаются **OPEN**; Samsung обновлять только по отдельному разрешению.
