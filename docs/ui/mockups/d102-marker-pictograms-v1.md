# D102 marker pictograms — OWNER APPROVED · 2026-10-09

Owner reference: [approved image](d102-marker-pictograms-v1.png). Explicit owner instructions
govern the schematic shapes; ObservationPoint reuses the existing Bee Search wingless bee geometry
instead of tracing the illustration into a second bee implementation. The image's size examples and
its "24 px recommended" caption are the pre-device recommendation; the current production size is the
owner-approved value below, and the image is not the size contract.

| Presentation type | Pictogram | Family | Capability boundary |
|---|---|---|---|
| ObservationPoint | existing bee: head, thorax, abdomen; no wings | yellow | existing points |
| Hollow | simple tree | green | existing hollows |
| LogHive | short standing cut trunk, rings/opening | brown | existing log hives |
| Trap | box/crate, not house | blue | vocabulary only; no lifecycle added |
| Apiary | house/hive-house | purple | reserved; no product capability added |

Compact pin, contrasting border; selected state keeps the pictogram and adds a light outline/halo.
Shape is the primary distinction; color is reinforcement. Operational GPS/centre/measurement symbols
are unchanged. Components do not depend on Room and do not create data.

Owner Samsung S25 Ultra approval · 2026-10-09: **32 dp is the production normal marker size**.
24 dp is readable but less convenient for field work; 20 dp must not be the normal size.
All five existing pictograms are device-approved; their drawing geometry and colors are unchanged.
The size describes the icon canvas including outline/halo space; the pin geometry occupies about
81% of its height. Interactive targets remain at least 48 dp regardless of visible pin size.
The existing DEBUG preview starts at 32 dp. Its 20/24/32 dp and legacy physical-px comparisons are
diagnostic only; they do not change the production normal size.

## Visual gate

DEBUG only: main map → `DEV: маркеры`. All five pictograms, normal/selected state, size controls and
crowded samples are viewport preview specimens over the actual map, not geographic research records.
Pan/zoom the map and use the existing source selector for available vector, Sentinel and Hybrid.
Raster/topographic is checked only if an actual relevant source is available; no basemap is invented.
Close the preview to restore the ordinary map. Release/beta variants have a no-op preview boundary.

Samsung SM-S938B / RFCY90MBYVZ owner approval of **size and pictograms is complete**.
The remaining visual gate is **PENDING**: selected state and nearby readability at 32 dp,
contrast on available vector / Sentinel raster / Hybrid backgrounds and operational-symbol distinction.
An independent non-Sentinel raster/topographic source is checked only when actually available.
Host/emulator checks and successful install do not close the remaining owner gate.

Production integration · 2026-10-09: the existing `SavedObjectMarkersOverlay` (map of the «Точки»
browser) now draws the approved ObservationPoint pin through `ResearchObjectMarker` at 32 dp, with the
approved selected halo on the marker the host has selected; the generic circle marker is gone. The
record → marker mapping lives beside the overlay and the appearance comes from this catalogue only.
Hollow and LogHive still have no map screen, and Trap/Apiary have no product capability, so they stay
reserved vocabulary — no Trap/Apiary entry, filter or UI. The DEBUG preview stays as the visual
regression surface. Production marker pins anchor by their tip, so the recorded position stays under
the tip. Temporal I6/I7 are unchanged. No commit/push in this task.

Nearby markers · owner decision 2026-10-09: при малом масштабе близкие точки могут визуально
перекрываться, и их 48 dp touch targets пересекаются; при увеличении масштаба точки разделяются и
выбираются нормально. Поведение принято владельцем для текущей версии; normal 32 dp не уменьшается,
clustering/spiderfy/автоматическое раздвижение в этот контракт не входят.
Owner production map verification · 2026-10-09: **PASS** на реальных ObservationPoint (форма, 32 dp,
selected state, смена фона).

Implementation uses Android native [variant source sets](https://developer.android.com/build/build-variants),
[Compose drawing](https://developer.android.com/develop/ui/compose/graphics/draw/overview) and
[semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics); no new dependency.


Owner correction 2026-10-10: temporary `DEV: маркеры` / MarkerVisualPreview removed after real D102 integration. Production catalogue, pictograms, 32 dp normal size and selected halo remain. Preview evidence above is historical.
