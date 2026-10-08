# Temporal Export V2 — canonical research-date carriage

Owner execution contract · 2026-10-08. Implements temporal I4; no Room, UI or import-to-Room change.
Current production writers use formatVersion **2** for each independent profile below.

| Profile | V2 payload field | V1 materialization |
|---|---|---|
| SINGLE_OBSERVATION_POINT | point.json → point.observationDate, REQUIRED string | one-time legacyObservationDate(createdAt) |
| SINGLE_PHYSICAL_OBJECT | object.json → object.fixationDate, REQUIRED string or null | fixationDate = null |
| PHYSICAL_OBJECT_COLLECTION | objects/<UUID>.json → object.fixationDate, REQUIRED string or null for every object | fixationDate = null |

V2 dates are authoritative persisted canonical values. The only string spelling is YYYY-MM-DD,
with a real ISO LocalDate. Missing keys, null observationDate, wrong JSON types, noncanonical
spellings or impossible calendar dates fail closed. No V2 path derives dates from createdAt,
reader timezone, current date or import time. Physical null means the fixation date is unknown.

Manifest formatVersion dispatch is explicit: 1 → legacy, 2 → canonical, all others → unsupported.
V1 key sets/bytes remain unchanged. Physical V2 identity key sets add only fixationDate; all
other strict key sets remain. ObservationPoint retains its historical unknown-key policy while
requiring and validating the V2 date field. Extra keys are not a date extension to V1.

## Preserved profile boundaries

ObservationPoint retains the D086 graph: exactly one point, its context, weather, Bee/FlightCycle
and owned attachments. Container remains manifest.json, point.json and attachments/<UUID>.

Physical single/collection retain [V1 boundaries](physical-object-export-v1.md): object-owned
payload/media and minimal labelling snapshots, Hollow/LogHive only. A collection contains one
concrete type in one Territory; multi-type Hollow + LogHive remains invalid. Each object's date
is independent; there is no collection date. Apiary export is not introduced.

ZIP layout, deterministic ordering, filenames, media hashes/ownership, caps, context/subtype
validation, SAF and publication mechanisms are unchanged. Readers materialize decoded graphs;
selective import or persistence into Room is not implemented.

## Legacy writers and deployment

Explicit internal V1 writer paths remain compatibility paths. They refuse corrected/nonrepresentable
observationDate or any non-null fixationDate before writing archive bytes. Refusal uses a typed
legacy representability error. Current V2 user paths do not call these guards.

Backup V7, Snapshot V2 and all three Export V2 profiles now carry canonical dates. I2/I3/I4 remain
one deployment unit. Samsung installation still requires a separate gate: Room 12→13 runtime
migration, Android V7 restore, Snapshot V2 SAF publication/read-back, V2 export integration and
preservation of existing DEV data/settings/maps/repository. I5–I7 are not implemented here.
