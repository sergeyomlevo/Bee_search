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
Use estimated nest distance derived from bee flight observations to visualize a
circle around each point. Intersections or combined evidence from several
observations may indicate a probable nest location. Future versions may also
incorporate recorded azimuths.

**Motivation / expected value:** Help researchers combine observations from
multiple points into an evidence-based estimate of a probable nest area.

**Dependencies / prerequisites:** Stable field-observation data; an accepted and
validated method for deriving distance from FlightCycle observations; an
analysis design that treats azimuth as optional supporting evidence rather than
a direct direction to the nest.

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

**Dependencies / prerequisites:** Accepted year-filtering and explicit
no-bees-result semantics; historical point queries; Points-screen navigation;
map visualization.

**Notes:** The general ability to review saved points is already part of product
requirements. The dedicated Points navigation, year filtering, result-based
map distinction, and exact presentation remain ideas. Exact marker colors are
not decided.

## I004 — Main field UI simplification

**Status:** `idea`

**Description:** Possible redesign of the main map screen:

- remove the Bee Search title from the working map screen;
- remove Territory code and name from the primary map view;
- move Settings to bottom navigation;
- replace textual `Центр` with a compact crosshair or recenter icon;
- show compact `Точность: 3,8 м` instead of `Точность GPS: 3,8 м`;
- rename `Новая точка` to `Создать точку наблюдения`;
- remove the large `Управление территориями` action from the main map screen;
- introduce bottom navigation such as `Map/Home | Points | Settings`.

**Motivation / expected value:** Give the map more space and reduce visual noise
during frequent field work.

**Dependencies / prerequisites:** Explicit navigation and UX approval; physical
device testing; a replacement way to keep the current Territory unambiguous and
Territory management safely reachable.

**Notes:** This remains an idea until the navigation and UX are explicitly
accepted. Removing Territory information without an adequate replacement would
conflict with the current workflow requirement that the active Territory be
clear on the working screen.
