# D106 A — GPX1.0 profile, 2026-10-19

Base ad180bc; independent parser slice, no UI/delete/schema/Backup changes.
Existing profile state machine now selects exact1.0/1.1 namespace+version pairs.
GPX1.0 root name/desc/scalar author map to metadata; same track/segment/sample,
elevation/time canonical projection. Unsupported speed/root time/rte/wpt are
preserved in immutable original with existing summary, not promoted to canonical.
Existing managed-copy/identity/READY/recovery/render boundaries are reused.
Original1.1 CM1 encoding unchanged;1.0 occupies existing version fields, no UUID
or original rewriting. No dependencies/security/resource/cancel limits changed.

## Control file provenance

Requested original `2026-10-14_164011[1].gpx` (33339 bytes,
SHA256 dc390810332489769fcea36cc1703e33deac12a8f795648226316f62b9e175d8)
was found on the reconnected Samsung at
`/sdcard/Download/Топокарты России/2026-10-14 164011.gpx`. Exact bytes pulled
read-only; SHA256/length/version/271-point structure all match OWNER.
`topokarty-original.gpx` is that unchanged source, not a generated replacement.
The TSV oracle was independently transcribed using Python ElementTree from its
lat/lon/elevation/time fields in XML order. The test compares every one of271
samples, track/segment counts, elevation/time, speed warning and exact copy bytes.

Earlier supplied Windows path `C:/СКАН/topokarty_2026-10-14_164011_gpx11.gpx`
is a different converted GPX1.1 (35743 bytes,SHA256
834da6ec66774c75f03744f553c4b1ba5a906e651f9ffe59f1da6af5d27238ef).
Neither external source was changed, moved or deleted.

## Regression evidence

Independent fixture: app/src/androidTest/assets/gpx/v10/tracks.gpx (not an owner
original): root metadata, multiple tracks/segments, elevation/time, unsupported
speed/rte/wpt. Gpx10DeviceTest checks exact sample order/coordinates and copy bytes,
version/namespace mismatch, malformed XML, invalid coordinates, zero track points,
DTD refusal, depth limit and cancellation. GpxPendingGraphTest adds1.0 version-field
roundtrip and mismatch rejection. Existing1.1/CM1/security/runtime tests retained.
Two old expectations for frozen valid1.0 empty-track D06 were updated to
NO_IMPORTABLE_TRACK after initial qualification reported them; frozen XML and
historical manifest/results remain unchanged.

Results: JVM1062/1062 PASS; API29 main matrix52/52 PASS plus final GPX1.0
class4/4 PASS including owner original (53 unique Android tests across those runs).
DEV/test APK assemblies PASS. Final A lint PASS:0 errors,35 warnings,6 hints;
no disabled rules. Logs: qualification.log and owner-qualification.log. Initial
52-test run had2 old D06 expectations; corrected as described above, rerun passed.
Raw evidence is local, ignored: build/evidence/d106-gpx10-20261019/. No Samsung install during A qualification.
Later API37 GPX1.0 tests and combined DEV deployment are recorded in the
[folder/deployment report](d106-gpx-picker-folder-report.md); no destructive phone test.

Decomposition reviewed; kept cohesive because: the361-line Android XML reader
receives only a root-namespace acceptance change; XML security/provider ownership
stays together. Version rules stay in the existing profile schema/parser; canonical
version fields remain in the existing model. No unrelated refactor.

Primary sources checked before implementation:
- [TopoGrafix GPX1.0 manual](https://www.topografix.com/gpx_manual.asp)
- [GPX1.1 schema](https://www.topografix.com/GPX/1/1/)
- [QName matching guidance](https://www.topografix.com/gpx_for_developers.asp)

Stage1/H05 OPEN. Part B is separately gated; A is not rolled back.
