# Bee Search — Handoff

## Current milestone

The offline-map architecture research has been promoted into authoritative
project documentation. This is a documentation/decision milestone only; Android
production code, Room schema and dependencies were not changed.

## Accepted offline-map architecture

- Bee Search has one MapLibre map. Network resources and explicitly prepared
  offline coverage complement each other; there is no separate user-facing
  `online map / offline map` mode.
- Territory can be created and used without an offline package. Missing coverage
  does not block GPS, ObservationPoint creation or local research operations.
- `Подготовить для офлайн` creates a user-adjusted rectangular MapLibre
  `OfflineRegion`. Ambient cache or an inactive download is not Ready;
  `OfflineRegionStatus.isComplete == true` for a compatible definition/profile
  is authoritative.
- The first delivery mechanism is MapLibre OfflineRegion over a Bee Search
  controlled OSM-derived vector source delivered as stable versioned HTTPS
  style/TileJSON/MVT/glyph/sprite resources. PMTiles/MBTiles are not used in the
  first milestone.
- The field source reuses standard OpenMapTiles-compatible layers and preserves
  at least cutlines, power lines/minor lines, tracktype and field-relevant
  surface detail.
- Baseline is source/offline maxzoom z15 and UI maxzoom z20 through vector
  overscaling. Actual source z16 remains a bounded Samsung A/B PoC; z17–20 are
  not generated in the first milestone.
- Offline package state is device-local infrastructure outside Room research
  data. Minimal mapping/version data may live in opaque OfflineRegion metadata;
  definition and status values are not duplicated.
- Existing Ready coverage is never deleted before a compatible replacement is
  fully Ready and covers the required old area. Multiple additive regions are
  allowed; no polygon-union manager is required initially.
- Download is foreground-only in the first implementation. After restart the
  app reads persisted region status and may resume an incomplete region in the
  foreground.
- Satellite is optional, may work online independently and may later use a
  separate smaller OfflineRegion. It does not define vector readiness. Provider
  selection and licensing remain open. Topography is deferred.

## Documentation synchronized

- `docs/decisions.md`: D005 amended; D007, D008 and D052 finalized; O001–O003
  marked resolved.
- `docs/product-requirements.md`: unified map, optional preparation, coverage,
  readiness and failure-isolation requirements.
- `docs/user-workflows.md`: Territory creation separated from optional rectangle
  preparation; progress, recovery and safe extension workflow.
- `docs/architecture.md`: OfflineRegion delivery, controlled field source,
  MapProfile/versioning, metadata boundary, zoom, satellite and foreground scope.
- `docs/data-model.md`, `docs/domain-model.md`, `docs/glossary.md`: device-local
  boundary and canonical offline terminology aligned without adding Room fields.
- `docs/ideas.md` was reviewed and did not require changes.

## Verification

- No stale mandatory-offline Territory wording or unresolved O001–O003 remains.
- D005/D007/D008/D052 and their references are consistent; no duplicate or
  missing decision ID was found.
- Data/domain docs still contain no OfflineMap entity or Territory map fields.
- No repository documentation linter was found; `git diff --check` passes with
  only Git's informational LF/CRLF warnings.

No Android build is required because production code and dependencies did not
change.

## Next implementation milestone

Implement the smallest foreground-only vector OfflineRegion workflow from the
authoritative docs. Before broad UI work, use a small development endpoint and
Samsung SM-S938B PoC to verify:

1. identical canonical URLs serve online and prepared coverage;
2. cold airplane-mode start after force-stop/reboot includes style, tiles,
   glyphs, sprites and attribution;
3. interrupted download remains incomplete and resumable without harming an
   existing Ready region;
4. actual z15 versus z16 field value, size and download time;
5. selected rectangle and Ready/Incomplete/Failed outlines remain understandable.

Satellite provider/licensing, background download, topography, automatic map
updates and a provider catalog are not part of that first implementation.
