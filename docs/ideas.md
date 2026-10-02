# Bee Search — Ideas Backlog

## Purpose

This file captures ideas and possible future directions for Bee Search.

An entry here is not an accepted requirement and must not be implemented
automatically. Authoritative product behavior, domain rules, data semantics, and
architecture remain in the corresponding project documents under `docs/`.

When an idea becomes timely, it may be discussed and promoted. Promotion to
`accepted` requires explicit user approval and a corresponding update to the
authoritative requirements or decisions. Keep entries concise and link to
authoritative documentation instead of copying it here.

## Statuses

- `idea` — captured thought that has not yet been evaluated.
- `proposed` — discussed enough to be considered for adoption.
- `accepted` — explicitly approved for future implementation and reflected in
  the relevant authoritative project documentation.
- `rejected` — deliberately not pursued.
- `done` — implemented and reflected in authoritative documentation and code.

## I001 — Point analysis / probable nest location

**Status:** `idea`

**Description:** Future analysis mode for suitable ObservationPoint records.
Use estimated nest distance derived from bee flight observations to visualize
one or more annular probable-distance zones around each point. Intersections or
combined evidence from several observations may indicate a probable nest
location. Future versions may also incorporate recorded azimuths and weather
context such as wind.

**Motivation / expected value:** Help researchers combine observations from
multiple points into an evidence-based estimate of a probable nest area.

**Dependencies / prerequisites:** Stable field-observation data; an accepted and
validated method for deriving distance from FlightCycle observations; an
analysis design that treats azimuth as optional supporting evidence rather than
a direct direction to the nest.

**Analysis principle — preserve the individual bee:** Analysis must not begin by
averaging all bees or all FlightCycle durations at an ObservationPoint. Each bee
is analysed separately across several cycles first. For one bee, the useful
distance signal is expected to be a repeatable lower flight-time level rather
than the arithmetic mean: a bee can be delayed away from the nest, making a
cycle longer, while such a delay does not make the true nest round trip shorter.
For example, durations around 5, 6, 10 and 12 minutes suggest that the repeated
short level around 5–6 minutes is more informative than the overall mean.

A single isolated shortest cycle must not automatically become the estimate. A
short-time level should be supported by at least two sufficiently consistent
cycles before it is used as the bee's distance evidence. The exact rule for
what counts as sufficiently close (absolute tolerance, relative tolerance,
clustering, or another robust method) is deliberately not decided yet and must
be validated against real observations. Every first cycle is now an ordinary
individual flight with its own departure time, so no cycle is excluded from this
analysis automatically by its sequence number.

Only after per-bee estimates exist should bees at the same ObservationPoint be
compared. If one bee has a repeatable lower flight-time level that is markedly
longer than the corresponding levels of the other bees, this is evidence that
it may belong to a different nest. Such a bee should not simply be averaged into
the main group. The method for deciding whether bees form one or several nest
groups, and the threshold for `markedly longer`, remain research questions.

The intended hierarchy is therefore `FlightCycle -> Bee -> possible nest group
at ObservationPoint -> cross-point probable nest analysis`. Raw observations
remain unchanged; these are derived analytical interpretations.

**Multiple-nest principle at one point:** If per-bee analysis indicates two or
more plausible nest groups at the same ObservationPoint, each group must remain
separate in subsequent distance analysis and map visualization. Each probable
nest group should produce its own annular distance zone. Distinct groups must
not be collapsed into one artificially wide ring merely because they were
observed from the same point.

**Temporal-compatibility principle:** Before combining ObservationPoints for a
current nest search, analysis should allow filtering by observation date/time
and should prefer points collected within a sufficiently close time window.
Different years, or otherwise widely separated observation periods, must not be
silently merged into one current-search estimate. Cross-year comparison may
still be valuable for a different question: whether repeated evidence is
consistent with long-term persistence or reuse of the same nest. The exact
maximum time gap for combining points in a current-search analysis remains
undecided and should be validated against field practice rather than fixed
arbitrarily.

**Wind as a future explanatory factor:** Wind speed and direction may affect
flight duration and possibly the interpretation of recorded azimuths, but no
wind correction is accepted yet. Wind should initially be treated as contextual
analysis data so its value can be tested later against known Nest locations.
Because wind can be retrieved retrospectively from a weather service, the
minimum provenance needed for later enrichment is the observation coordinates
and the exact observation date/time. Where possible, analysis may use the
actual FlightCycle timestamps together with the ObservationPoint coordinates,
rather than a coarse point-level date, so weather can be matched to the period
in which the flight was observed. Any future wind adjustment must be a
versioned analytical-model feature and must never modify the raw recorded
FlightCycle times.

**Distance-model principle — literature formula is a hypothesis, not a fixed
domain rule:** A published beelining relation such as `distance_m = 150 *
flight_minutes - 500` may be retained as a reference/baseline model, but must not
be hard-coded as a universally valid conversion. Field observations already
show that it can produce physically impossible results at short flight times
(e.g. 2 minutes gives -200 m). The actual time-to-distance relation may require
different coefficients, different behaviour in different distance ranges, or a
non-linear/piecewise model. The exact form is deliberately undecided until it
can be tested against ground-truth nests.

The analysis architecture should therefore treat time-to-distance conversion as
a replaceable/versioned analytical model. When a nest is actually found, the
known ObservationPoint-to-Nest distance together with the per-bee repeatable
lower flight-time evidence should form calibration/validation data. Raw
FlightCycle data must remain available so historical observations can be
re-analysed when the model changes. If derived distance estimates are persisted
or exported, they should retain enough model/version provenance to distinguish
which calculation produced them. Negative or otherwise physically impossible
outputs must not be presented as valid nest-distance estimates.

**Uncertainty / map-visualization principle — probable distance band, not a
single circle:** A derived distance such as 700 m must not be visualized as if
the nest were known to lie exactly on a thin 700 m circumference, nor as a
filled circle implying that the whole area from 0 to 700 m is equally plausible.
The primary visualization should instead be a ring/annular probable-distance
zone with an inner and outer bound. Its width should reflect the uncertainty in
the evidence rather than an arbitrary fixed graphic width.

The bounds may initially derive from the spread of the bee's supported lower
flight-time level (for example, repeated short cycles around 5 and 6 minutes)
converted through the selected distance model. As ground-truth Nest records
accumulate, empirically measured model error may also widen or otherwise adjust
the band. Additional correction/error terms may be introduced only when they
have an explicit rationale and can be validated. Until a statistical model is
actually established, this should be described as a `probable distance range`
or `uncertainty zone`, not as a formal confidence interval.

With several ObservationPoints, overlaps/combined evidence from their annular
zones should progressively narrow the probable search area. Recorded azimuths
may later weight or constrain parts of a ring, producing annular sectors or
other probability-weighted areas rather than being treated as an exact ray to
the nest. The visualization should communicate uncertainty honestly and avoid
false precision. The exact method for calculating ring bounds and combining
multiple zones remains a research question to be validated against known nests.

**Notes:** This is a research-analysis feature, not a current MVP requirement.
It is consistent with D049 but must not be implemented as part of the field-data
collection workflow without a separate decision.

## I002 — Temporary map measurement marker

**Status:** `idea`

**Description:** On the main map, tapping a location may create a temporary
measurement marker. Show a visual direction or line from the current GPS
position to the marker and the distance between them. A subsequent tapped
location may replace the previous marker.

**Motivation / expected value:** Provide a lightweight field measurement tool
without creating an ObservationPoint or contaminating research data.

**Dependencies / prerequisites:** Main-map interaction design; clear visual
separation from saved ObservationPoint markers and from the ObservationPoint
creation crosshair.

**Notes:** The marker is transient UI state. It is not an ObservationPoint, is
not persisted to Room, and must not create research data.

## I003 — ObservationPoint history on map

**Status:** `idea`

**Description:** Provide a Points screen where saved ObservationPoint records can
be reviewed. Allow filtering by year or viewing all relevant years, display
saved points on the map, and visually distinguish points where bees were
observed from points where the observer explicitly recorded that bees were not
found.

**Motivation / expected value:** Make historical coverage and explicit negative
observations visible in the field and during later review.

**Dependencies / prerequisites:** D056 provides stored observation year and
scoped point numbering; D057 provides the explicit Bee presence result. Points
Browser v1 implements the historical summary query, dedicated navigation,
Territory/year filtering, map/table views, and read-only point history.

**Notes:** The remaining idea scope is refinement beyond v1, such as additional
filters and exact long-term marker presentation. Exact marker colors remain a
revisable UI detail rather than a domain rule.

## I004 — Main field UI simplification

**Status:** `idea`

**Description:** Possible redesign of the main map screen:

- remove the Bee Search title from the working map screen;
- remove Territory code and name from the primary map view;
- move Settings to bottom navigation;
- replace textual `Центр` with a compact crosshair or recenter icon;
- show compact `Точность: 3,8 м` instead of `Точность GPS: 3,8 м`;
- further refine the accepted general `Создать запись здесь` entry if field evidence requires it;
- remove the large `Управление территориями` action from the main map screen;
- further refine the accepted bottom actions `Объекты | Настройки` without mixing creation and browsing.

**Motivation / expected value:** Give the map more space and reduce visual noise
during frequent field work.

**Dependencies / prerequisites:** Explicit navigation and UX approval; physical
device testing; a replacement way to keep the current Territory unambiguous and
Territory management safely reachable.

**Notes:** D077 accepts only the general create chooser and the
`Объекты | Настройки` navigation shell. The broader visual simplification in
this idea remains unaccepted. Removing Territory information without an adequate
replacement would conflict with the current workflow requirement that the active
Territory be clear on the working screen.

## I005 — Pre-field compass check and HeadingProvider diagnostics

**Status:** `idea`

**Description:** Provide an optional screen outside the active observation
workflow, such as `Настройки → Компас → Проверка компаса`, where the user can
check the same live true/geographic heading that Bee Search would offer for
`FlightCycle.azimuthDeg`. The screen must reuse the production `HeadingProvider`
and its display-rotation and `GeomagneticField` correction path rather than
implementing a separate demo compass. It may show a large live heading, the
direction of the phone's upper short edge, Android sensor accuracy (`high`,
`medium`, `low`, or unavailable), and brief guidance for comparing the reading
with a known cardinal direction.

**Motivation / expected value:** A systematic heading error could distort many
FlightCycle observations and future direction or probable-nest analysis. A
calm pre-field sanity check can expose gross coordinate-transform, display-
rotation, calibration, or sensor problems before time-critical observation
begins, without creating research data or making the check mandatory for every
ObservationPoint.

**Dependencies / prerequisites:** D033 and D059; the existing lifecycle-aware
`HeadingProvider`; a future Settings/navigation location; physical-device and
daylight testing. The true-heading calculation needs a defined reference
location even when no ObservationPoint is active. That source must be selected
explicitly before implementation so the diagnostic value remains comparable to
the observation workflow. A detailed block may require the shared heading
pipeline to expose, without duplicating its math, the sensor source, magnetic
heading, declination, true heading, reference coordinates, calculation time,
and altitude or the accepted `0 m` fallback.

**Notes:** The simple view may show `269°`, an orientation cue, sensor accuracy,
and instructions to point the phone's upper edge toward a roughly known north,
east, south, or west direction. A collapsible diagnostic section may show data
such as `Rotation Vector`, magnetic heading, declination, true heading,
coordinates, current timestamp, and altitude fallback. Low or unreliable
accuracy should prompt the user to move away from metal or magnets and try the
phone's normal physical calibration procedure, without claiming that a
particular movement guarantees correction. Bee Search may identify suspicious
readings but should not write sensor calibration coefficients, store an
arbitrary manual angular offset, block field work, or add diagnostic details to
the compact observation cards. Any future persisted manual offset would change
research semantics and requires a separate durable decision.

## I006 — Settings information architecture and autosave

**Status:** `idea`

**Description:** Redesign Settings as a small navigation section rather than a
single technical form. The top-level Settings screen should use a standard back
arrow and contain entries for `Настройки пользователя`, `Помощь`, and
`О программе`. `Настройки пользователя` should open a second screen with its
own back arrow and visually separate Territory data from observer data:

- Territory: territory code and territory name;
- Observer: observer code and observer name.

Replace the explicit `Сохранить` button with automatic persistence as fields
are validly edited. Returning with the app back arrow, Android back gesture, or
system Back should therefore require no separate confirmation action. Invalid
required values must not be silently accepted.

**Motivation / expected value:** Make Settings simpler on mobile, remove an
unnecessary explicit save action, establish a scalable Settings hierarchy for
future sections, and present Territory and observer information in a clearer
form.

**Dependencies / prerequisites:** Settings/navigation redesign; validation and
persistence behavior for editable fields; an explicit product/data-model
decision before adding the observer-name field, because observer name is new
stored information rather than a purely visual UI change.

**Notes:** The UI may group Territory and Observer information on the same
screen, but they must remain separate concepts in the data model. Territory
code/name continue to belong to `Territory`; observer information must not be
merged into the Territory entity. Prefer the user-facing label `Имя
наблюдателя` over `ФИО наблюдателя` so the field does not assume a particular
name structure. This idea complements I004 and leaves room for future Settings
entries such as the compass diagnostic proposed in I005.

## I007 — Compact field UI for the active observation workflow

**Status:** `idea`

**Description:** Keep the working field screen compact and low-noise for fast
repeated use. Reduce headings and section labels to approximately the text size
used for primary action labels such as `Сохранить`. Avoid explanatory or status
text that does not change what the observer does next, and avoid blocks that
push the working controls out of the viewport.

Keep an explicit action for the negative research result; use red text to make
that result clearly distinguishable while keeping the button/background visually
neutral rather than presenting it as an error.

Mark choice is a derived set of available variants, not a list of Bees created
in advance. Any future presentation change to it should keep the mark color
recognizable by appearance and keep the position variant readable without a text
label, because these variants cannot be communicated by color alone. The current
implementation of that requirement is the parametric `BeeMarkIcon` (D078), where
the mark color is painted on the thorax or on the abdomen.

**Motivation / expected value:** Reduce vertical space, reading load, and visual
noise during time-sensitive field work, while keeping the most frequent
selection recognizable by appearance.

**Dependencies / prerequisites:** The active observation workflow and its label
semantics; physical-device/daylight testing; accessible selection indication
must not rely only on subtle color differences. Exact sizing and spacing remain
a UI implementation detail.

**Notes:** This is a presentation idea only. The earlier bee-preparation screen,
its prepared-bee list and the group-release action no longer exist: D075 replaced
them with individually registered first flights and derived mark choices, so this
idea now concerns the active observation screen. The negative-result action
records an explicit observation result, not an application error. This idea
complements the broader field UI simplification in I004.

## I008 — Found nest documentation

**Status:** `partially implemented; remaining idea`

**Partially superseded by accepted D088 (`docs/decisions.md`):** the model proposed below — "a
general nest (`Nest`) rather than specifically as a hollow (`Hollow`)" — is **not** the accepted
model. Accepted are three concrete durable physical types (Дупло / Колода / Пасека, i.e.
`Hollow` / `LogHive` / `Apiary`), each with its own identity and human-readable designation, and
`Nest` («гнездо») keeps its separate meaning of an *estimated* location. The useful parts of this
idea remain valid as idea material: the Obsidian nest form as a possible source for future field
design, media captured with the record, and provenance linking a found object to the observations
that led to it.

**Description:** Extend Bee Search beyond flight tracking to document a wild-bee
nest after it has been located. Treat this as the natural final stage of the
same research workflow: field search → ObservationPoint evidence → probable
location → found nest → structured documentation.

Do not invent a new nest-description scheme from scratch. Use the existing
Obsidian nest-recording workflow as the source for the future Bee Search form
and data model, adapting its established fields and terminology to a mobile
field UI. The demonstrated Obsidian form includes nest type and physical nest
parameters, tree information, entrance orientation, photo/video, track, and
additional information. Context already known to Bee Search, such as location,
date, Territory, and observer, should be filled automatically where possible
rather than entered again.

Model the research object as a general nest (`Nest`) rather than specifically as
a hollow (`Hollow`). A tree hollow can be one nest type, allowing the same model
to represent other encountered nest-site types later without changing the core
entity.

Allow a documented nest to carry media such as photographs and video and, where
appropriate, a track/reference to supporting field material. Media should be
capturable or attachable from the nest record while remaining usable offline.
The database should store the structured record and media references rather
than embedding large media content directly in Room.

Where possible, preserve provenance by linking a found nest to the
ObservationPoint records or other Bee Search observations that contributed to
finding it. This relationship should support later validation of probable-nest
analysis against actually located nests.

**Motivation / expected value:** Complete the field-research workflow inside Bee
Search instead of stopping at the moment a nest is located. Reuse an already
worked-out Obsidian recording practice, reduce duplicate field entry, preserve
photos/video together with structured nest data, and create ground-truth records
that can later be compared with the point-analysis/probable-location methods in
I001.

**Dependencies / prerequisites:** The existing Obsidian nest form and resulting
note structure must be reviewed as the authoritative starting point before the
exact Bee Search fields are specified. A separate durable data-model decision is
required before implementation. Media storage/export, nest-to-observation
relationships, offline lifecycle, and any automatic heading/location capture
must also be designed explicitly. Existing Territory and observer semantics
must be reused rather than duplicated.

**Notes:** Objects V1 (D089) now implements the concrete Hollow/LogHive portion
of this idea, including stable physical characteristics, creator provenance and
object-owned photo/video creation media. The generic Nest model, track/GPX and
Inspection remain outside this implementation and require their own decisions.

This idea does not turn Bee Search into a general-purpose field
notebook. Its intended product boundary is the search for and documentation of
wild honey-bee nests. Obsidian can remain a long-term viewing, note-taking, and
analysis environment, while Bee Search provides structured field capture. A
future export should preserve enough structure to remain compatible with, or be
straightforward to transform into, the established Obsidian nest records.

## I009 — Long-term nest inspections

**Status:** `idea`

**Partially superseded by accepted D088 (`docs/decisions.md`):** the entity the visits belong to
is a concrete durable physical object (Дупло / Колода / Пасека), not a generic `Nest`, and the
accepted name for one visit is `Inspection` («Осмотр»), not `NestInspection`. The core of this
idea is **confirmed** by D088 and remains its design source: a long-lived object collecting any
number of dated visits, each visit a separate historical record that never rewrites the previous
one, stable characteristics on the object versus time-varying observations on the visit, media
belonging to the visit that produced it, and the Obsidian inspection form as the starting point
for the future field design. D088 explicitly defers the Inspection schema, its media and weather
handling.

**Description:** Treat a found `Nest` as a long-lived research object that can
receive any number of dated follow-up inspections over the lifetime of the
project. Represent each visit as a separate `NestInspection` rather than
rewriting the current state on the `Nest` record. Use the existing Obsidian
nest-inspection form as the starting point for the future Bee Search inspection
form and exact field specification.

Keep stable nest characteristics on `Nest` and time-varying observations on
`NestInspection`. The demonstrated Obsidian workflow includes observations such
as bee activity, pollen carrying, nest status, photo/video, planned follow-up,
a follow-up date, and additional notes. The exact vocabulary and scales should
be reviewed from the existing Obsidian form before implementation rather than
recreated from memory.

Weather associated with an inspection should use a deliberately mixed capture
strategy based on field experience with the existing Obsidian workflow:

- temperature — retrieve automatically from a weather service using the nest
  coordinates and inspection date/time;
- atmospheric pressure — retrieve automatically;
- wind speed — retrieve automatically;
- wind direction — retrieve automatically;
- cloud cover — enter manually at the inspection location;
- precipitation — enter manually at the inspection location.

Cloud cover and precipitation must not be overwritten by later weather-service
retrieval. If internet access is unavailable in the field, the inspection must
still be saved immediately. Automatically retrievable weather fields may remain
pending and be populated later from historical weather for the recorded
coordinates and timestamp.

Allow inspection-specific photographs and video to be associated with the
inspection that produced them, rather than treating all later media as timeless
properties of the nest. A planned follow-up and its date may later support a
simple nest-review workflow, while remaining subordinate to the research record
rather than turning Bee Search into a general task manager.

**Motivation / expected value:** Support multi-year monitoring of located wild
honey-bee nests and preserve their history as a sequence of observations. This
allows the database to distinguish permanent nest characteristics from changing
colony condition and provides structured longitudinal data for later analysis.
It also avoids repeatedly entering weather variables that have proven suitable
for automatic historical retrieval while preserving direct field observation
for locally unreliable variables such as cloud cover and precipitation.

**Dependencies / prerequisites:** I008 and an accepted `Nest` data model; review
of the existing Obsidian inspection form and its scales/terminology; an accepted
`NestInspection` schema; offline-safe media handling; a weather retrieval and
retry mechanism capable of historical lookup by coordinates and timestamp.
Weather source/provenance and pending/loaded/error state semantics require a
separate implementation decision.

**Notes:** Conceptually, Bee Search would have two related long-lived branches:
`Territory → ObservationPoint → Bee → FlightCycle` for search evidence and
`Territory → Nest → NestInspection` for located nests and subsequent monitoring.
A nest may be linked to the ObservationPoint evidence that led to its discovery.
This remains within the product boundary of searching for, documenting, and
monitoring wild honey-bee nests rather than becoming a general-purpose field
notebook.

## I010 — Reproducible dev state and offline-map restoration

**Status:** `idea`

**Description:** Make the Bee Search `dev` application reproducibly restorable
across destructive installs, data clears, test resets, and other development
operations that would otherwise remove its working state. A restored dev build
should return to the same practical state as before the destructive operation:
Room data, DataStore/user settings, current Territory selection, observer data,
offline-map packages, and the metadata required to activate those maps again.

Treat an offline map as a self-describing package rather than as a bare
`.pmtiles` file. Its persisted metadata must include the exact original
coverage geometry/bounds and the identifiers/integrity data needed to restore
or validate it. Restoration of an existing map must never require the user to
manually redraw the original area or reproduce an exactly matching selection.

Keep the canonical development snapshot outside the Android app sandbox so it
survives `adb uninstall`, `pm clear`, package replacement, and application-data
loss. A PC-side snapshot associated with the Bee Search development workflow is
an acceptable primary mechanism. Support both restoration of the last captured
working state and, separately, restoration of a known-good baseline state.

**Motivation / expected value:** Repeated loss of settings and locally installed
offline maps interrupts development and makes device testing dependent on Codex
or manual reconstruction of map coverage. Reproducible restoration turns the
dev package into a persistent test environment rather than a disposable clean
install and removes the impossible requirement to redraw an identical map area.

**Dependencies / prerequisites:** Define the snapshot contents and versioning;
define export/import or adb-safe procedures for Room and DataStore; preserve
map-package manifests/descriptors and map files; handle schema migrations and
incompatible snapshots safely; distinguish `org.beesearch.app.dev` from the
stable package; define a known-good baseline policy.

**Notes:** Ordinary APK updates should preserve app data and use a non-destructive
update path. Destructive reset remains useful for explicit clean-install tests,
but it must be intentional and must not be the default development workflow.
Operational guardrails for Codex/agents belong in project agent instructions or
development documentation and can be adopted before I010 is fully implemented.

## I011 — Пользовательский полевой слой дорог и троп

**Status:** `idea`

**Description:** Поверх независимой базовой карты пользователь сможет
импортировать GPX-трек, записанный Bee Search или сторонним приложением.
Импорт сначала остаётся фактическим треком, а не становится автоматически
картографическим объектом. Пользователь сможет подтвердить весь трек или его
участок как собственную дорогу либо тропу; подтверждённая линия отображается
поверх базовой карты.

**Motivation / expected value:** В полевой работе встречаются используемые
дороги и тропы, которых нет на базовой карте, но которые пользователь уже
прошёл или проехал и зафиксировал GPX-треком.

**Dependencies / prerequisites:** Для будущей реализации потребуются отдельные
решения о проверке/выборе трека или участка, отображении слоя и метаданных:
тип (`дорога` / `тропа`), проходимость (`автомобиль` / `пешком` /
`неизвестно`), источник (`GPX` / собственная запись / ручное создание) и дата
последнего подтверждения на местности. Запись GPX непосредственно Bee Search —
отдельное будущее развитие; для первой реализации достаточно импорта
существующего GPX.

**Notes:** Собственные объекты Bee Search не меняют базовые OSM-данные или
тайлы. В перспективе слой должен поддерживать offline-использование,
экспорт/импорт и резервное копирование. Сейчас это только идея: не определяет
Room schema, UI, конкретный формат хранения или implementation milestone.

## I012 — Compact Bee / FlightCycle matrix

**Status:** `done`

**Description:** Make the Bee-by-cycle history matrix more compact on a phone without reducing the readability of recorded times. Remove the redundant word `Пчела` from every row and represent the first column by the Bee mark icon plus a short numeric identifier (`1`, `2`, `3`, ...). The column should be only wide enough for that compact identity.

The Bee identity column should scroll horizontally together with the cycle columns rather than remain frozen. On a narrow phone screen it is more useful to expose an additional cycle column than to keep the Bee identifier permanently visible; with the small number of simultaneously observed Bees, row order remains understandable after horizontal scrolling. Reduce unnecessary row/column padding where practical, but do not shrink the flight-time text merely to fit more columns.

**Motivation / expected value:** The current fixed, text-heavy first column consumes a large share of the phone width, so only about two cycle columns may remain visible. A compact moving identity column should make three or more cycle columns visible and improve rapid comparison of repeated flights in the field and during point review.

**Dependencies / prerequisites:** The existing Bee/FlightCycle matrix and `BeeMarkIcon`; physical-device testing on the field phone; accessibility must not depend on color alone, so the mark-position variant and numeric row identity must remain distinguishable.

**Notes:** This is a presentation change only. It must not change Bee identity, FlightCycle ordering, stored data, or analysis semantics. Exact dimensions and whether the header is omitted or reduced to a compact marker such as `№` remain UI details. Implemented in `f437e19`; current behavior is reflected in authoritative project documentation.

## I013 — Thermal-drone assisted search inside a probable nest area

**Status:** `idea`

**Description:** Investigate using a drone with a thermal camera as a second-stage search tool after Bee Search has narrowed the probable nest location to a relatively small area (for example, roughly a hectare). The drone would survey the area for localized thermal anomalies that may correspond to an occupied tree hollow or its entrance; suspicious locations would then be checked on the ground and, if confirmed, recorded as `Nest` objects.

The first research workflow should not require Bee Search to control the aircraft. Bee Search may export a survey polygon or other simple area representation to suitable mission software, while candidate thermal locations may later be imported or recorded back in Bee Search. A practical flight pattern may combine an autonomous grid/lawnmower route with operator supervision and manual interruption. Survey geometry should consider oblique views from several directions rather than relying only on a nadir view from above the canopy, because foliage and trunk visibility may be the dominant limitation.

**Motivation / expected value:** An occupied hollow can create a detectable thermal contrast at a small entrance, particularly when the surrounding tree surface has cooled. If probable-distance analysis has already reduced the search area, thermal surveying may reduce the amount of difficult ground inspection needed to find the actual nest.

**Dependencies / prerequisites:** Treat this as an experimental method, not an accepted detection technique. First validate it against one or more already known occupied nests, comparing viewing angle, distance, altitude and time of day. Cloudy conditions, after sunset, night or pre-dawn may provide useful thermal contrast, but the effective window must be established experimentally. Flight safety, local aviation rules, obstacle avoidance, thermal-camera capability and radio performance in forest must be handled by the drone system/operator rather than assumed by Bee Search.

**Notes:** GNSS navigation and the controller-to-drone radio link do not inherently require mobile internet, so a preloaded survey mission can in principle operate offline. Forest attenuation and obstacles mean open-field range specifications must not be treated as guaranteed working range. Initial trials should favour safe above-canopy flight with oblique thermal observation; low flight among trees should not be assumed safe merely because GNSS is available. No machine-learning or automatic hotspot classifier is required for the first PoC.

## I014 — User georeferenced raster layers from old maps and imagery

**Status:** `idea`

**Description:** Allow Bee Search to display user-supplied georeferenced raster material as an optional offline overlay over or alongside the modern base map. Typical sources include scanned or photographed old detailed paper maps, historical aerial photographs, satellite imagery, or other raster material useful for field navigation and interpretation.

Complex georeferencing should be prepared outside Bee Search, preferably on a PC with GIS software. The user identifies multiple stable control points visible both on the source image and in modern coordinates (for example durable road junctions), rectifies/warps the source, and exports a ready georeferenced raster package. Bee Search then displays that prepared layer; it should not initially implement its own control-point fitting or reprojection workflow.

Use more than four corner points when the source permits it: well-distributed control points can account for rotation, scale, skew and deformation of old paper or photographs. Some independently known points should be reserved for checking registration error rather than used to fit the transform. The source coordinate reference system may be unknown if control-point registration still produces a validated modern-coordinate raster.

**Motivation / expected value:** Old maps can contain roads, clearings, boundaries and other field detail missing from current maps, while recent imagery can reveal river meanders, new clearings and landscape changes. A georeferenced overlay lets these sources be compared with current GPS position, ObservationPoints, probable nest zones and future Nest records without changing the canonical base map.

**Dependencies / prerequisites:** Define one or more supported offline raster package formats, coverage metadata, import/activation lifecycle, storage/backup rules and map-layer ordering. The UI should support at least layer on/off and useful opacity/transparency control. Registration quality/error should be retained as metadata where available.

**Raster / imagery PoC checkpoint (2026-09-28):** These are accepted research
results, not an accepted production raster architecture or a decision to ship
Sentinel-2 in a production package.

**OBSERVED / proven by PoC:**

- On Samsung SM-S938B, Bee Search DEV rendered local raster XYZ offline and
  raster PMTiles v3 through source `url: pmtiles://file://...`. The diagnostic
  templated tiles URL `pmtiles://.../{z}/{x}/{y}.png` did not work. The existing
  vector PMTiles map still rendered after the experiments, and Bee Search
  markers/overlays retained their geographic alignment. Raster PMTiles is the
  preferred future transport candidate; local XYZ remains useful for diagnostics
  and PoC work. This preference does not yet define a production package contract.
- MapLibre on the target Samsung can request a physical source tile level one
  level deeper than the visible badge zoom. If that source level is absent, the
  renderer overzooms the last available tile and visibly degrades the image.
  A future raster pipeline must therefore supply the physical source levels the
  renderer needs instead of relying on stretching the final level. The exact
  observed 2x render/density behaviour is not a current blocker and is not being
  investigated further in this cycle.
- A public USGS NAIP sample established the technical high-resolution reference:
  real 0.30 m/px source information can provide useful detail at z18. The main
  cause of the earlier blur was a missing finer source level and resulting
  overzoom, with resampling also contributing; JPEG q90 was not the primary
  cause. Artificial upscale, sharpening and AI enhancement were not needed.
  NAIP was only a public technical sample and is not a source for Russian areas.
- The real Sentinel experiment used product
  `S2B_T38VLH_20260718T082107_L2A`, SAFE
  `S2B_MSIL2A_20260718T081609_N0512_R121_T38VLH_20260718T121847.SAFE`,
  acquired `2026-07-18T08:24:24.15Z`, Sentinel-2 L2A processing baseline
  `05.12`, tile cloud cover `0.000374%`, and the 10 m bands B04/B03/B02 from
  Earth Search `sentinel-2-c1-l2a` / Sentinel-2 Collection 1 L2A COG. The fixed
  review centre was `56.1914 N, 42.7423 E`; WGS84 bbox was west
  `42.5390625`, south `55.9983809554`, east `42.9345703125`, north
  `56.3652501369`.
- Sentinel display processing was exactly
  `reflectance = DN * 0.0001 - 0.1`, RGB `B04/B03/B02`, then
  `uint8(round(255 * clip(2.5 * reflectance, 0, 1)))`; reprojection was
  `EPSG:32638 -> EPSG:3857` with bilinear resampling and lossless PNG. No
  sharpening, AI or super-resolution was used. The physical XYZ pyramid held
  135 tiles at z13 and 375 tiles at each of z14-z18 (2010 total); z18 existed
  only so renderer requests for the z17 review would not cause accidental
  overzoom. The z13-z17 sequence rendered offline on the Samsung.
- The final whole-area package accepted by the owner uses the same 2026-07-18
  source material and a source-direct linear display:
  `uint8(round(255 * clip(7.5 * reflectance, 0, 1)))`, where `7.5 = 2.5 * 3.0`.
  It contains real lossless-PNG levels z10-z13 in raster PMTiles v3. The
  accepted external artifact is `sentinel-area-z10-13-linear-b.pmtiles`,
  SHA-256 `135246b8f2f5e99e50b2361fb63a0539dff1c071074a075982d125ee2c09daff`.
  The owner manually accepted this rendering on Samsung SM-S938B.
- The DEV Hybrid PoC rendered that raster, selected independent vector line
  and label layers, and Bee Search overlays in one MapLibre view. Roads,
  tracks/paths, rivers/streams, road labels and water/waterway labels improved
  orientation compared with the bare Sentinel raster. Offline glyphs and
  Cyrillic labels worked; sparse place labels reflect the current vector
  package content rather than a glyph/rendering failure.

**OWNER JUDGMENT:**

- Sentinel-2 RGB at native 10 m/px is useful in Bee Search only as a
  small-scale overview. The accepted package provides z10-z13 and both the
  Sentinel and Sentinel-based Hybrid DEV modes stop at UI z13. Blur is already
  beginning there; z14 and above must not be treated as working Sentinel
  basemap levels because further zoom adds no useful spatial information.
- Hybrid is more informative for field orientation than the bare Sentinel
  raster. Raster and vector remain independent sources; roads or labels are not
  baked into PNG tiles. Existing cutline and power-line vector data may be
  evaluated later, but is not part of the accepted first Hybrid overlay.
- The real 0.30 m/px reference is sufficiently detailed at z18 for the intended
  detailed map use. z19 brought no practical benefit and is not required.

**OPEN / next architecture task:** Design one source-neutral offline raster
preparation/package workflow for Sentinel, higher-resolution satellite data,
orthophotos and other properly georeferenced imagery:

```text
georeferenced source imagery
→ validation
→ reprojection/mosaic as needed
→ real zoom pyramid appropriate to source resolution
→ raster PMTiles
→ metadata/manifest
→ package verification
→ independent raster basemap beneath a vector Hybrid overlay
```

Its production package lifecycle, import/acquisition UI and compatibility
contract are not implemented or accepted yet. A legal high-resolution source
for real Russian territories, licensing/access and intermediate-scale coverage
also remain open. A future high-resolution raster must first be tested with the
existing true-z15 vector package overzoomed above z15. True vector z16-z18 is
revisited only if that practical test shows insufficient vector detail; the
current Planetiler/profile pipeline remains unchanged.

**Notes:** Generalize this as a `user georeferenced raster layer` capability rather than a special-case old-paper-map feature. The original source/year and, when known, original CRS should remain provenance metadata. Bee Search research objects continue to use modern geographic coordinates independently of the raster source.

## I015 — Incremental Territory Sync

**Status:** `idea`

**Description:** В будущем Bee Search должен поддерживать основной механизм передачи полевых
исследовательских данных с телефона на сервер и далее в настольные аналитические приложения. Это
отдельная перспектива, а не текущий `PHYSICAL_OBJECT_COLLECTION` export и не часть его реализации.

Нужно различать три сценария:

1. `SINGLE_PHYSICAL_OBJECT` и другие `SINGLE_*` форматы предназначены прежде всего для обмена
   отдельными записями или интересными случаями и потенциального будущего импорта.
2. Collection export — ручная выгрузка набора данных, например всех Hollow или всех LogHive текущей
   Territory.
3. Incremental Territory Sync — будущий основной механизм регулярной передачи полной
   исследовательской базы Territory на сервер.

Первая синхронизация может передавать полный snapshot Territory. Последующие синхронизации должны
передавать изменения после последнего подтверждённого sync checkpoint, а не повторять всю базу.
Учитываются новые, изменённые и удалённые записи. В перспективе механизм охватывает связанные данные
Territory: ObservationPoint, Bee, FlightCycle, PhysicalObject, object/observation media, будущие
inspections и другие относящиеся к исследовательской базе сущности.

Stable UUID используются как identity. Простой persisted flag вида `exported = true` не должен быть
основой архитектуры синхронизации. Будущая модель должна предусматривать revisions/change tracking и
способ представления удалений (например tombstone или change log), но конкретная схема сейчас не
принимается. Sync state продвигается только после подтверждённого приёма сервером; повторная передача
после сбоя должна проектироваться как безопасная и idempotent.

Ручной file export и server sync остаются разными механизмами. Текущие `SINGLE_*` и collection
package formats не объявляются будущим network sync protocol. Нынешняя реализация export не должна
преждевременно добавлять `syncStatus`, `lastExportedAt`, tombstones, server IDs или другую
sync-specific persistence.

**Motivation / expected value:** В реальной длительной работе повторная выгрузка всей Territory после
каждого полевого изменения будет избыточной. Сервер должен постепенно собирать долговременную
исследовательскую базу, а настольные приложения — использовать её для более серьёзного анализа,
объединения и обработки данных.

**Dependencies / prerequisites:** Стабильная модель исследовательских сущностей; серверная
архитектура; решение о revision/change tracking; semantics удаления и конфликтов; media
synchronization; authentication/source provenance; protocol/versioning; отдельное архитектурное
решение перед реализацией.

**Notes:** Это IDEA, а не accepted requirement. Sync protocol не проектируется и не реализуется в
текущей задаче Physical Object Collection Export. Текущая задача лишь не создаёт искусственных
препятствий будущей синхронизации.

## I016 — Unified Territory Data Map and map presentation modes

**Status:** `idea`

**Description:** В перспективе карта должна стать единой картой исследовательских данных Territory
(Territory Data Map), а не набором почти одинаковых браузеров для каждого типа объекта. На одной
карте пользователь независимо включает и выключает тематические слои: `ObservationPoint`, Дупло
(`Hollow`), Колода (`LogHive`), Пасека (`Apiary`), будущие `Inspection` и будущие аналитические
результаты. Отдельный map browser для каждого типа объекта оправдан только при доказанной
необходимости: общая карта Territory должна решать задачу лучше.

Практический исследовательский сценарий — видеть одновременно, например,
`ObservationPoint + Hollow + Apiary`, и визуально исследовать взаимное расположение: находится ли
найденное дупло рядом с известной пасекой, попадают ли точки наблюдений в возможную зону лёта от
пасек, как расположены найденные объекты относительно наблюдений. В будущем к этому добавляются
вспомогательные spatial tools: заданный радиус вокруг `Apiary`, расстояния
`ObservationPoint ↔ Apiary`, `Hollow ↔ Apiary`, `ObservationPoint ↔ Hollow` и другие
пространственные отношения. Круг радиуса или близость к пасеке — инструмент визуального и
пространственного анализа, а **не** доказательство происхождения пчёл от конкретной пасеки.

**Temporal filtering:** у каждого типа данных собственный календарный фильтр, а не один общий
диапазон: visibility и filters типов независимы, и период одного типа не влияет на другой. Период
типа — один непрерывный интервал, выбранный с явной точностью `год` / `месяц` / `день` (например
`2024–2026`, `авг–сен 2026` или `10.08.2026 — 01.09.2026`); отсутствие периода означает отсутствие
временного ограничения для этого типа, а несколько независимых интервалов (disjoint periods) в
модель не входят. Semantics зафиксирована решением D094 и здесь только применяется: каждый тип
фильтруется по своей canonical research date — `ObservationPoint` по дате наблюдения, `Hollow` /
`LogHive` / `Apiary` по дате фиксации, будущий `Inspection` по дате осмотра. Это не вопрос о
существовании реальных объектов в периоде: такой historical/analytical вопрос — отдельная будущая
задача. Технический `created_at` исследовательскую дату не заменяет.

**Technical sources ≠ presentation modes:** нужно различать внутренние технические
картографические источники (ONLINE raster, offline vector package, raster imagery, будущие
пользовательские raster layers) и пользовательские режимы представления карты. Появление нового
технического источника не должно автоматически создавать новый верхнеуровневый пункт выбора карты.
Bare satellite raster сам по себе мало информативен для полевой работы: на нём отсутствуют или
плохо читаются дороги, тропы, ручьи и подписи населённых пунктов. Практически полезный Hybrid
(raster imagery + независимые vector roads/tracks/waterways/labels) полезнее bare raster, и именно
raster + vector navigation overlay может стать основным полевым представлением, если появится
действительно подробная imagery. При достаточно хорошей imagery отдельный пользовательский режим
`VECTOR` может перестать быть нужен, но vector source остаётся нужным системе как overlay
infrastructure для дорог, троп, водотоков и подписей.

**Conceptual layering (не навязан коду):** base/presentation mode → пользовательские
georeferenced rasters (I014) → пользовательские полевые данные (I011 и его продолжение: стоянки,
места для многодневного лагеря, труднопроходимые участки, переправы, удобные подходы, парковки,
другие POI, линии, возможно полигоны) → исследовательские данные (`ObservationPoint`, `Hollow`,
`LogHive`, `Apiary`, будущие `Inspection`) → аналитические overlays (I001 и последующие derived
results). Пользовательские полевые данные и raster layers не запекаются в raster/vector basemap и
остаются видимыми независимо от выбранной базовой карты. Механизм отображения этих классов данных —
design proposal в `architecture.md` (раздел 73.3).

**Наблюдаемое состояние (OBSERVED, 2026):** на экране карты один селектор подложек со списком
`Онлайн карта` / `Векторная карта` (в DEV дополнительно `Спутник Sentinel` и `Гибрид`); выбор —
transient UI state, он не сохраняется и сбрасывается при пересоздании экрана. Смена режима заменяет
весь MapLibre style (`setStyle`), а собственные маркеры Bee Search рисуются Compose-overlay поверх
карты. Общий механизм маркеров уже существует (`MapObjectType` / `MapObjectMarker` /
`SavedObjectMarkersOverlay`), и сегодня в нём ровно один тип — `OBSERVATION_POINT`; `Hollow`,
`LogHive` и `Apiary` добавляются как новые типы этого списка. Маркеры показываются только в
просмотре `Точки`; на основном полевом экране исследовательские маркеры не рисуются. Фильтр точек —
`Territory + year`, произвольного диапазона нет; у объектов Territory есть только полный список без
временного фильтра.

**Motivation / expected value:** пространственные вопросы («это дупло рядом с пасекой?», «эти точки
в возможной зоне лёта?») требуют сейчас переключения между экранами и не видны как одна картина.
Единая карта с независимыми слоями даёт прямое сравнение расположения наблюдений, найденных
объектов и пасек, а произвольный период позволяет смотреть конкретный выезд, сезон или всю историю.

**Открытые вопросы (не решены этой идеей):** окончательные названия и количество map modes; состав
и порядок кнопок; обязателен ли UI вида `КАРТА | ГИБРИД` (это пример, а не решение); судьба
`ONLINE`, `VECTOR` и `SENTINEL`; Room schema пользовательских полевых объектов; конкретная модель
`Inspection`; архитектура аналитических overlays и конкретные радиусы; формат пользовательских raster
packages сверх уже принятых решений.

**Dependencies / prerequisites:** mockup-first проектирование панели слоёв и фильтров до реализации
(`docs/ui/Создание интерфейсов.md`); observable reads объектов Territory (сейчас список объектов
читается одноразово, а точки — потоком).
Пользовательские georeferenced rasters остаются темой I014, GPX и собственные дороги/тропы — темой
I011, метод анализа вероятного гнезда — темой I001; здесь фиксируется только их место в общей
архитектуре карты, без дублирования их содержания.

**Notes:** Это IDEA, а не accepted requirement: она не меняет текущий UI, не выбирает названия
режимов, не определяет Room schema и не отменяет существующие источники карты. Независимое
включение тематических слоёв — принцип: рост числа слоёв не должен превращаться в рост числа
верхнеуровневых экранов.
