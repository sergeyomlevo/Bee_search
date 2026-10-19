# D106 — GPX exchange picker initial folder, 2026-10-19

Separate small storage-navigation slice; no import pipeline or external file
move/delete. Existing `BeeSearchExchangeStorage`, BuildConfig.EXCHANGE_VARIANT,
`OpenExchangeDocument` and Android OpenDocument are reused exactly as offline maps.

The folder is `Download/BeeSearch/Dev/Exchange/GPX` on primary storage (the system
Downloads collection). Other variants use their own Exchange branch. It is only a
user exchange folder, never managed canonical/original application storage.

The existing idempotent exchange-directory preparation includes GPX. Preparation
uses ordinary platform-enforced app filesystem access under public Downloads,
without broad storage permission or bypass; a refusal is allowed. Before launch,
preparation errors do not block import; cancellation still propagates normally.
No external file is adopted, moved or removed by directory preparation.

Existing `OpenExchangeDocument` supplies a document URI for the initial folder,
not a raw filesystem path or a tree grant. `EXTRA_INITIAL_URI` is a navigation hint:
no permission is granted by it; provider/system may ignore it or fail to locate a
missing folder, using its ordinary initial location instead. OpenDocument remains
available for manual selection. No SAF permission or stored choice is reset.
Android11 restrictions on choosing Downloads root with OpenDocumentTree are not
bypassed: this flow selects one file via OpenDocument, not a root tree grant.

Primary references checked before implementation:
- [DocumentsContract.EXTRA_INITIAL_URI](https://developer.android.com/reference/android/provider/DocumentsContract#EXTRA_INITIAL_URI)
- [SAF shared-document access](https://developer.android.com/training/data-storage/shared/documents-files)

## Verification

- JVM1062/1062 PASS; DEV/test assemblies PASS.
- API29 final target19/19 PASS: GPX intent hint + import panel8 + import ViewModel10.
- API37 direct isolated instrumentation10/10 PASS: GPX1.0 class4 (including actual
  271-point original) + complete Exchange device class6. Exact DEV/test package
  IDs checked before emulator installs; no physical instrumentation.
- Final lint PASS:0 errors,35 warnings,6 hints (final-qualification.log,5m26s).
- Initial API29 run24 tests/4 failures:3 existing Samsung directory-write assumptions
  fail because public Downloads mkdir is refused even for Areas/Data; this is the
  platform-limited best-effort branch, no broad permission added. The fourth was
  a new test's incorrect CATEGORY_OPENABLE assumption, fixed in the test only.
  Existing positive directory tests remain unchanged and pass on API37.
- Full manual API29 fallback-navigation smoke was not performed; URI/intent and
  existing import UI/state tests are distinguished from that physical interaction.

### Samsung actual result — PASS for automatic initial folder

After the later explicit OWNER connection/install permission, updated only
`org.beesearch.app.dev` on SM-S938B, Android API36, actual fontScale1.7.
Version1.3.0(8), lastUpdateTime2026-10-19 13:17:58, installed APK SHA256
`66ba1e813a6278feff7e8c1c26b3feae009d2c319d6ce756e68b640ad59f8eb1`.
New/installed signing certificate matched; equal versionCode, no downgrade.
`adb install -r -t`; no uninstall/clear, no Stable/Beta mutation.

Before the first updated launch all4078 private files matched exactly by path and
SHA256; first-install time and data inodes unchanged. Stable/Beta package metadata
unchanged. Maps/canonical/originals/DataStore were not reset. A pre-existing system
picker task was cancelled without selection before the fresh DEV launch.

Folder was absent before launch and existed after the app's normal startup
preparation; no ADB mkdir or shell file delivery was used. Fresh `Импорт GPX`
opened directly in **BeeSearch → Dev → Exchange → GPX**, confirmed by screenshot
and UIAutomator breadcrumb. No file selected; native Back navigation returned to
Tracks. No new import or deletion was performed on user records. The external
Topokarty original retained the exact OWNER SHA256 after this smoke.

[Actual Samsung picker screenshot](../build/evidence/d106-gpx-picker-samsung-20261019/picker-initial-gpx-font17.png).
Raw evidence: `build/evidence/d106-gpx-picker-samsung-20261019/verification.json`,
plus before/after file manifests and installed APK. Full phone import of1.0 was not
repeated; actual1.0 bytes are qualified through Android parsers on API29 and37.
No destructive phone tests. Stage1/H05 remain OPEN.

### Review

SELF_REVIEW fallback: independent Luna context was unavailable due usage limit;
independence is reduced. Review corrected the stale1.0 rejection row, fixed the
control fixture's Windows byte-preservation attribute and rejected an unsupported
all-platform directory-creation claim. No additional A/C code blocker identified;
B remains a proposed durable-state extension and an unapproved visual addendum.

Decomposition reviewed; kept cohesive because: MainMapScreen gets only picker
contract/folder preparation wiring; storage layout remains owned by the existing
exchange component. No second picker system, new permissions or import workflow.

## Changed files by slice


A2412661:

- [.gitattributes](../.gitattributes)
- [app/src/androidTest/assets/gpx/v10/topokarty-expected.tsv](../app/src/androidTest/assets/gpx/v10/topokarty-expected.tsv)
- [app/src/androidTest/assets/gpx/v10/topokarty-original.gpx](../app/src/androidTest/assets/gpx/v10/topokarty-original.gpx)
- [app/src/androidTest/assets/gpx/v10/tracks.gpx](../app/src/androidTest/assets/gpx/v10/tracks.gpx)
- [app/src/androidTest/java/org/beesearch/app/data/gpx/Gpx10DeviceTest.kt](../app/src/androidTest/java/org/beesearch/app/data/gpx/Gpx10DeviceTest.kt)
- [app/src/androidTest/java/org/beesearch/app/data/gpx/GpxProfileDeviceTest.kt](../app/src/androidTest/java/org/beesearch/app/data/gpx/GpxProfileDeviceTest.kt)
- [app/src/main/java/org/beesearch/app/data/gpx/AndroidGpxXmlReader.kt](../app/src/main/java/org/beesearch/app/data/gpx/AndroidGpxXmlReader.kt)
- [app/src/main/java/org/beesearch/app/data/gpx/README.md](../app/src/main/java/org/beesearch/app/data/gpx/README.md)
- [app/src/main/java/org/beesearch/app/data/local/room/GpxStoredGraph.kt](../app/src/main/java/org/beesearch/app/data/local/room/GpxStoredGraph.kt)
- [app/src/main/java/org/beesearch/app/domain/gpx/GpxCanonicalModel.kt](../app/src/main/java/org/beesearch/app/domain/gpx/GpxCanonicalModel.kt)
- [app/src/main/java/org/beesearch/app/domain/gpx/GpxProfileDraftBuilder.kt](../app/src/main/java/org/beesearch/app/domain/gpx/GpxProfileDraftBuilder.kt)
- [app/src/main/java/org/beesearch/app/domain/gpx/GpxProfileParser.kt](../app/src/main/java/org/beesearch/app/domain/gpx/GpxProfileParser.kt)
- [app/src/main/java/org/beesearch/app/domain/gpx/GpxProfileSchema.kt](../app/src/main/java/org/beesearch/app/domain/gpx/GpxProfileSchema.kt)
- [app/src/test/java/org/beesearch/app/data/gpx/GpxPendingGraphTest.kt](../app/src/test/java/org/beesearch/app/data/gpx/GpxPendingGraphTest.kt)
- [docs/d106-gpx10-report.md](../docs/d106-gpx10-report.md)
- [docs/d106-stage1-gpx-spec.md](../docs/d106-stage1-gpx-spec.md)
- [docs/d106-stage1b-gpx-parser-identity-contract.md](../docs/d106-stage1b-gpx-parser-identity-contract.md)
- [docs/decisions.md](../docs/decisions.md)

B documentation eb0859e:

- [docs/README.md](../docs/README.md)
- [docs/d106-gpx-deletion-plan.md](../docs/d106-gpx-deletion-plan.md)
- [docs/d106-stage1-gpx-spec.md](../docs/d106-stage1-gpx-spec.md)
- [docs/decisions.md](../docs/decisions.md)
- [docs/ui/mockups/gpx-display-delete-v1.html](../docs/ui/mockups/gpx-display-delete-v1.html)
- [docs/ui/mockups/gpx-display-delete-v1.md](../docs/ui/mockups/gpx-display-delete-v1.md)
- [docs/ui/mockups/gpx-display-delete-v1.png](../docs/ui/mockups/gpx-display-delete-v1.png)

Folder and session record (this commit):

- [app/src/androidTest/java/org/beesearch/app/data/exchange/BeeSearchExchangeStorageDeviceTest.kt](../app/src/androidTest/java/org/beesearch/app/data/exchange/BeeSearchExchangeStorageDeviceTest.kt)
- [app/src/main/java/org/beesearch/app/data/exchange/BeeSearchExchangeStorage.kt](../app/src/main/java/org/beesearch/app/data/exchange/BeeSearchExchangeStorage.kt)
- [app/src/main/java/org/beesearch/app/ui/map/MainMapScreen.kt](../app/src/main/java/org/beesearch/app/ui/map/MainMapScreen.kt)
- [app/src/test/java/org/beesearch/app/data/exchange/BeeSearchExchangeStorageTest.kt](../app/src/test/java/org/beesearch/app/data/exchange/BeeSearchExchangeStorageTest.kt)
- [docs/README.md](../docs/README.md)
- [docs/architecture.md](../docs/architecture.md)
- [docs/d106-gpx10-report.md](../docs/d106-gpx10-report.md)
- [docs/d106-gpx-picker-folder-report.md](../docs/d106-gpx-picker-folder-report.md)
- [docs/product-requirements.md](../docs/product-requirements.md)
- [docs/unified-territory-map-checklist.md](../docs/unified-territory-map-checklist.md)
- [docs/user-workflows.md](../docs/user-workflows.md)
- [.agent/handoff.md](../.agent/handoff.md)
