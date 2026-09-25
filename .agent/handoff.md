# Bee Search handoff

## Current milestone

Objects V1 extends the accepted D088 foundation with creation, storage,
viewing and correction of Hollow and LogHive records. Creation starts from the
main-map `+` chooser, reuses the map-centre location confirmation and production
`HeadingProvider`, and persists only after a valid form is confirmed. Room v9
adds immutable creator provenance, subtype tables and normalized 1:N object
media; Complete Backup v4 covers the new data while v1-v3 remain readable.

## Verification status

The full JVM unit suite, debug build and debug/androidTest assembly pass. A
preserving Samsung SM-S938B run reports `OK (55 tests)` across backup,
migration, repository, map/create navigation, object forms and Objects browser.
The DEV package remained installed and its data was not cleared; Stable and
Beta were not touched. Manual Samsung checks opened the Objects browser and
confirmed it survives app restart. A live create flow, physical compass
measurement and camera/photo-picker interaction remain unverified because the
device had no GPS fix; system location was restored to its original disabled
state after the check.

## Next task

Owner review and real-field acceptance with a GPS fix: create/edit both object
types, exercise the physical compass, camera and multi-item picker, then confirm
persistence after restart. No push, Beta release or version change was made.

## Previous milestone

Initial Setup V1 adds a non-blocking, four-step readiness checklist for the
current Observer, Territory, Ареал and coverage-compatible offline map. The
first incomplete launch opens it after local state loads unless an active
ObservationPoint needs recovery. Settings provides repeat access. One
device-local `initial_setup_offer_handled` flag suppresses repeat automatic
offers; no readiness or Area-sent flags are persisted. The Ареал screen now
keeps the send/load guidance visible until a suitable map is Ready.

## Verification status

Focused JVM tests, debug/androidTest Kotlin compilation, lint and debug build
passed. Preserving Samsung SM-S938B instrumentation passed for checklist UI,
DataStore settings, Settings navigation, Ареал guidance and map loading. The
DEV package was updated in place without clearing data; Beta was not touched.
Independent luna-verifier was launched but hit a usage limit before a verdict;
root performed a separate self-review.

## Next task

Owner review of the local Initial Setup commit and manual in-app acceptance on
Samsung; no push or Beta release was performed for this milestone.

## Previous milestone

D088 physical object data foundation is implemented. Room schema v8 adds an
internal `physical_objects` identity table, an `apiaries` subtype table, and
nullable `bees.source_object_id`. Concrete domain types are Hollow, LogHive and
Apiary. Sequence numbers are allocated transactionally within Territory and
type; records are retained and no object deletion API exists. Linked deletion
is restricted. Complete backup v3 preserves both new collections and the Bee
link; v1/v2 readers remain supported. D086 single-point export v1 is unchanged.

## Verification status

JVM unit tests, lint and debug/androidTest compilation passed. The preserving
Samsung SM-S938B workflow reported `OK (34 tests)` for migration, backup and
repository checks before a small Apiary name pass-through adjustment, then
`OK (25 tests)` for backup and repository after that adjustment. The DEV
package remained installed; Beta was not touched.

## Next task

The next implementation stage is Objects V1 UI, after owner review of this
foundation. Inspection and research tooling remain separate future work.

## Previous milestone

ObservationPoint creation now treats description and photos as properties of
the unsaved preparation draft. Both ordinary creation and `NO_BEES_FOUND`
creation commit them together with the point; cancelling preparation removes
the app-owned draft photo directory and persists nothing.

Draft photos live under
`files/observation-attachments-staging/<draftSessionId>/<attachmentId>`. On
successful creation they move to the existing permanent
`files/observation-attachments/<pointId>/<attachmentId>` layout before Room
atomically inserts the point, pending weather row and attachment metadata. A
failed Room operation rolls the files back to the draft.

Room remains v7 and backup remains v2. Existing attachment metadata,
FileProvider paths, Open-Meteo/WorkManager behavior, Area DataStore and PMTiles
storage contracts are unchanged.

## Verification status

Offline debug Kotlin compilation, the full unit suite, androidTest compilation,
lint, debug APK assembly and debug androidTest APK assembly pass. The focused
preserving Samsung SM-S938B run reports `OK (37 tests)` for preparation UI,
properties Room behavior, historical Properties, point/file deletion and
backup v2.

Manual Samsung checks at `font_scale=1.7` covered picker and camera cancellation,
draft cleanup on creation cancellation, ordinary creation with description and
photo, `NO_BEES_FOUND` creation with photo, historical/active Properties reopen
and photo deletion. DataStore, the installed PMTiles file and its manifest kept
their pre-test SHA-256 values. Area still reports two sections / 4.8 km² and the
offline map as loaded.

## Next task

No follow-up is required for this iteration. Continue new functional work from
the updated `main`; do not revive the pre-integration source worktrees as a
development baseline.
