# Repository V1 foundation — production slice 1

Status: owner-approved R0-PC-D rules; Slice 1 implementation, not a complete backup product.
Complete Backup V1–V6 remains a separate portable archive mechanism with unchanged behavior.
Slice 2B adds a separate [METADATA_ONLY Snapshot V1 service](repository-snapshot-v1.md).
Historical Slice 1/2A scope exclusions below describe those slices, not the current snapshot layer.

## Scope and identity

Startup idempotently creates `Download/BeeSearch/<Variant>/Backup/{Media,Snapshots,Staging}`.
Existing contents and headers are preserved. A file/symlink at an expected directory path is a
typed failure. Skeleton creation is best-effort and never blocks primary field capture.
Directory existence does not initialize identity. No backup scheduler or UI is added.

`RepositoryFoundation.initialize()` is an explicit initialization/adoption operation. With no
header it accepts only an empty skeleton (including empty Staging); unknown/nonempty contents
are `AMBIGUOUS_REPOSITORY`. A valid existing header is never overwritten. Header creation uses
an owned staged file, sync, full verification, same-storage move, and final readback.

The bounded UTF-8 header has exactly these fields:

```json
{"repositoryFormat":"beesearch-repository","repositoryFormatVersion":1,"repositoryId":"<random UUID>","variant":"Dev"}
```

Variants are exactly `Dev`, `Beta`, `Stable`. Duplicate/unknown keys, malformed UTF-8,
invalid UUIDs and unsupported identities/versions fail closed. Header cap: 4096 bytes.
Repository UUID persists in this file. Path, URI and grant are not identity. Every ingest
requires the expected UUID from the established binding and revalidates the header before
growth and publication. Explicit adoption is not an automatic replacement-root recovery.
Slice 2A retains this UUID in a durable install-local binding; no production selection UI is implemented.

## Durable binding and reconnect (Slice 2A)

The application-facing `BoundRepository` reads durable binding before every write-capable operation.
Binding is `expectedRepositoryId` plus `rootLocator` (SAF tree/document reference), namespaced by the
build variant. It contains no inventory, snapshots, coverage or PC state. The header remains truth.
Location changes do not change identity: explicit `reconnect(locator)` accepts only the SAME UUID
and variant, then atomically updates the locator. Failure preserves the old binding and all files.
No cached BOUND probe result can authorize a later write; ingest revalidates header via foundation.
Immediately before canonical Media move, foundation rereads and validates format/version,
variant and expected UUID on its current locator, with no intervening lookup/hash/capacity query
or suspension. It also checks identity after final readback. A provider does not offer atomic
"move only if repository UUID still matches": mutation inside the provider call remains an
unsupported external-writer race, not a claimed guarantee. Future snapshot publication must
apply the same fresh boundary check; snapshots are not implemented here.

The separate Preferences DataStore file is
`files/install-state/repository_binding.preferences_pb`, already excluded from Auto Backup/device
transfer by existing rules. Exactly one instance is created by AppContainer per process. Keys are
`expected_repository_uuid_<Variant>` and `repository_root_locator_<Variant>`; absent pair is UNBOUND.
Partial/malformed pair is BINDING_INVALID, not UNBOUND. Persistence errors remain typed failures.
Compare-and-replace updates both fields atomically; no migration from path/URI or existing headers.

- `initializeNew`: explicit, unbound only, empty skeleton only, header publication/readback before
  durable bind. Existing header requires adoption; persistence failure can leave a valid unbound
  repository, which must be explicitly adopted later. No cross-filesystem/DataStore transaction claimed.
- `adoptExisting`: explicit unbound operation, validates existing header and structure without writing
  repository content. Reinstall/data loss has no remembered history; fresh adopt is required.
- `rebind`: intentional switch requires exact prior binding and explicitly selected target UUID;
  validates target before atomic binding replacement. It never rewrites either repository header.
- `clear`: exact prior binding required; removes only binding pair, not files/grants.
- Valid wrong UUID/variant: ROOT_IDENTITY_MISMATCH, no writes/rebind/delete.
- Missing root: BOUND_ROOT_UNAVAILABLE; missing header in an available/recreated root:
  BOUND_REPOSITORY_MISSING. Access/provider failures are never converted into absence.
- Startup creates skeleton and probes binding read-only, with no initialization/adoption/rebind.
  Backup failure does not block primary capture.

Stored UUID/locator are device-local safety context, not proof of current media availability.
Updates/restarts preserve them; clean uninstall/app-data loss may remove them. Existing repositories
can be adopted without copying Media back. Dev/Beta/Stable package/data and key namespaces remain isolated.
No snapshots/restore/handoff/offload/UI have been added by Slice 2A.
Two physical copies with the same UUID may explicitly reconnect/adopt. Until the snapshot layer
exists, binding cannot distinguish a stale copy by research contents. Future reconnect UX may
show the latest valid snapshot time/UUID/whole-file SHA; no copy identity or UI is introduced now.
Android persistence uses one DataStore per process/file and atomic edit of the pair. Host tests
use supported OkioStorage for Windows fault/concurrency checks; no production backend change.
Crash exactly mid-persistence-write has not been physically characterized.

DataStore atomic update/single-instance requirements are DOCUMENTED in the
[official DataStore documentation](https://developer.android.com/topic/libraries/architecture/datastore)
(project dependency 1.2.1). No new dependency or custom persistence engine is introduced.

## Media and publication

SHA-256 of actual whole-file bytes is binary identity. Layout is flat:
`Media/<lowercase-sha256>.<ext>`. V1 recognizes only `image/jpeg → jpg`, `video/mp4 → mp4`;
unknown/generic hints use `bin`. No sniffing. Conflicting recognized hints for a new SHA fail.
An existing strongly verified blob retains its extension, including an existing `bin`.
Multiple paths for the same declared SHA or an invalid canonical placement block ingest.
Existing wrong-sized/corrupt/unreadable canonical blobs are never overwritten.

The independent `data/backuprepository` maintenance service serializes operations; AppContainer
shares one maintenance mutex across bindings. There are no snapshot operations in this slice.
External concurrent mutation of the selected repository is unsupported.

```text
private source → owned Staging/<operation UUID>/candidate.part → writing → closed
→ synced → staged size/SHA verified → rename within Staging → same-storage move
→ exact canonical Media path reopened → final size/SHA verified → COMMITTED
```

Canonical Media is never a streaming target. The source is hashed before transfer, streamed
with exact byte-count/hash validation, checked for disappearance/size/timestamp changes,
and hashed again after transfer to detect same-size mutations. Sources must be app-private;
no source-delete operation exists. All large reads are bounded streaming reads.
The trusted app-private root may use Android's system path alias. Its canonical mapping is
pinned for the source instance; changed mapping and symlinks below that root are rejected.
Coroutine cancellation propagates; explicit cancellation returns `CANCELLED`. Owned stage
cleanup runs on exit. Failed cleanup leaves non-committed evidence, not false publication.

The SAF adapter uses `wt`, reopen `rw` plus `FileDescriptor.sync()`, then provider rename/move.
No copy/delete publication fallback. Provider response failure is not success: the exact final
path must still be reopened and fully verified. This can recover a move whose response failed.
Cleanup checks the original owned Staging parent before deleting; a URI still valid after move
must never cause deletion of the committed final file. Unknown staging entries are preserved.
If repository identity is missing/changed/unreadable, ingest cleanup leaves Staging untouched;
ownership evidence cannot authorize deletion in a replacement repository.

Atomic rename and power-loss persistence are not claimed. After restart, partial Staging is
non-committed; final files require strong validation. Verified orphan Media is allowed before
future snapshot publication. No automatic GC, recovery journal, commit marker or staging sweep.

## Capacity

Internally configurable fixed reserve default: **20 GiB = 21,474,836,480 bytes**.
For artifact size `N`:

```text
slack = max(16 MiB, N / 100 + (N % 100 != 0 ? 1 : 0))
additional = checkedAdd(N, slack)
allow only when available >= checkedAdd(additional, reserve)
```

Checked integer arithmetic only. During streaming (each 4 MiB), refresh capacity against
remaining bytes + original slack + reserve. Before publication check slack + reserve again.
UNKNOWN/negative inputs or overflow fail closed. Already committed bytes remain intact.

The Android adapter requires the exact external-storage tree ID to map to the supplied public
path, and `StorageManager.getUuidForPath(private filesDir)` to equal the public-root volume UUID.
Fresh `StatFs.availableBytes` supplies capacity. Optional provider capacity and
`getAllocatableBytes()` are not correctness inputs. This guards Bee Search's own growth, not
space consumed by other apps/camera/system; it is not an exclusive storage reservation.

Same-provider move capability must be confirmed; unknown mapping/provider behavior is unsupported.
The allowance is conservative policy, not measured exact overhead or a universal SAF guarantee.

## Typed failures and boundaries

Typed failures include absence, permission loss, provider failure, UUID/variant mismatch,
ambiguous root, invalid/unsupported header, directory conflict, unknown/insufficient capacity,
write/sync/verify/publish failure, unsupported publication path, inconsistent hints/placement,
source change, cancellation, and owned-stage delete failure. Successful child enumeration alone
proves absence; arbitrary provider exceptions must not be treated as deletion.

No snapshots, restore, PC ingest, handoff/report import, external coverage, offload or user-facing
backup UI is implemented. `Snapshots/` is an empty structural boundary only.
Current blob publication must not be presented as a completed research backup.

## Slice 1 verification

- JVM: 511 tests, zero failures/errors, including 42 foundation tests.
- DEV `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: passed. Lint has no errors;
  existing warnings/hints remain. No Complete Backup implementation was changed.
- Samsung SM-S938B/API36, serial RFCY90MBYVZ: opt-in synthetic preparation, publication,
  and restart/conflict checks passed. Actual DEV skeleton was materialized; explicit header
  initialization/blob tests used only disposable `_poc/ProductionSlice1` storage.
- Run `605340a6-3a60-44d2-86ed-e64d0410c72f`, repository UUID
  `ba879c42-77ad-46f8-a46a-70fee456798b` survived process stop/relaunch.
- JPEG: 1146 bytes, SHA `7b46dd8420eba075b690697f5ecbaee37ee126c832663f94174b27e66f9770c4`.
- MP4: 1549 bytes, SHA `730120c1b4c245b5501e45643e0d085e686e70014863e2c99e5f0ab92234a630`.
  Independent device shell SHA checks matched private source and public final bytes.
- Repeated ingest created no duplicate; same-size corruption of a third owned synthetic target
  returned `VERIFY_FAILED` without overwrite. Test evidence and private fixtures were retained.
- DEV-only permission setup lives in `src/debug`, excluded from Beta/Stable. It saves only an
  owner-selected exact disposable tree grant. No production selection UI was introduced.
- Setup failures were explicit: unsupported tiny AVC frame, test-APK grant forwarding, private
  root alias rejection, and raw public-file assertions. These were corrected and successful
  checks rerun; none is represented as an earlier successful test.
- This does not prove power-loss durability, other providers/API levels, large-media performance,
  snapshot completeness or offload safety. Those are outside this slice.

Public `Download` is outside app Auto Backup's app-specific file/external domains. Current
Room/DataStore rules and map exclusions remain unchanged. No claim is made about an OEM's
independent whole-shared-storage migration.

## Slice 2A verification

- 2026-10-06: 525 JVM tests, zero failures/errors; DEV assemble, test APK assemble and lint PASS.
- Persistence tests cover durable reopen/update-style reopen, atomic compare-and-replace,
  variant keys, malformed/partial state, and explicit I/O failure. Windows host tests use the
  existing official OkioStorage backend: Android FileStorage's File.renameTo replacement
  failed on Windows. Production Android backend remains unchanged and was tested on device.
- Samsung SM-S938B/API36: three opt-in instrumentation invocations PASS; isolated smoke
  binding file and new disposable run `879198d5-f933-4f30-b565-331cfa660731` only.
  UUID `8880d149-db43-41f6-98fb-da8ef361f699` survived DEV force-stop/relaunch;
  repository `b062eb8d-cd45-4b2e-94cc-6b161748d7bd` was rejected without binding/header changes.
- Newly created EMPTY RepositoryA was deleted/recreated under its same name. The old grant
  resolved the replacement, but binding returned BOUND_REPOSITORY_MISSING; no new header
  or automatic UUID adoption occurred. Independent shell listing confirmed A empty and B intact.
- No real media, previous evidence, field/Beta package or actual app binding was changed.
  Same-signature update was simulated by JVM reopen; clean reinstall was subsequently tested
  on a separate disposable audit package as documented below. Selection/adoption UI is outside scope.

### Final acceptance audit (2026-10-06)

- Full JVM regression: 531 tests, zero failures/errors. DEV/test assemble and lint PASS;
  standalone audit APK assemble/lint PASS; git diff --check PASS. Complete Backup remains unchanged.
- Publication check now runs immediately before the canonical move, after the last existing-blob
  lookup. Injected UUID replacement during that lookup returns ROOT_IDENTITY_MISMATCH, leaves
  Media empty and does not clean staging against the foreign UUID. No provider atomic conditional
  move or absolute protection against mutation inside a provider call is claimed.
- Corrupt persisted protobuf bytes, malformed/incomplete pair and injected read/write failures
  fail closed. CAS tests cover two initial binds, clear versus rebind, and explicit adoption
  paused before CAS versus competing initialization; no mixed UUID/locator pair is accepted.
- Permission/provider failures retain the durable binding and never turn into UNBOUND/missing.
- Physical Samsung audit uses standalone `tools/repository-binding-audit`, not the production
  Gradle graph. Application ID `org.beesearch.bindingaudit`, Auto Backup disabled, current
  production repository sources compiled by a generated-source build task. No real DEV data linked.
- Run `a55ab34b-f43f-4f93-be89-7aeaef8b4930`, public root under `_poc/Slice2AAudit`:
  explicit init, tiny synthetic JPEG publication and duplicate ingest PASS; force-stop/relaunch
  preserved UUID `b5a04173-d5aa-4e48-863f-0fc65ff2fd24`. Uninstall/reinstall ONLY this disposable
  package yielded UNBOUND without automatic adoption. Explicit owner-selected adopt restored
  the same UUID without returning any Media to the phone.
- Public header and JPEG SHA remained identical through uninstall/reinstall/adopt. JPEG:
  759 bytes, SHA `270c0ca16088b645bbc07143f8bece6c799e101204828f2f29b75ffe34a036fb`;
  header SHA `b94648381eeec755aa2afad7baa1b1c3fc0d73379f79f83d98c7db290adf5941`.
- Initial standalone harness launch failed before binding because its Android Main coroutine
  dispatcher was absent. Added matching coroutines-android 1.9.0 to that test project only;
  preserving reinstall and corrected run passed. Production dependencies/backend unchanged.
- Crash exactly mid-DataStore write and stale-copy freshness without snapshots remain unproven.
  Same UUID at a different valid location remains an allowed explicit reconnect/adopt.

## Technology evidence

Existing platform primitives and project dependencies suffice; no new library is added.
The four-field parser is bounded, not a general-purpose JSON or canonical digest implementation.

- DOCUMENTED: [StatFs available bytes](https://developer.android.com/reference/android/os/StatFs#getAvailableBytes()).
- DOCUMENTED: [StorageManager path volume UUID](https://developer.android.com/reference/android/os/storage/StorageManager#getUuidForPath(java.io.File)).
- DOCUMENTED: [SAF rename/move return values and flags](https://developer.android.com/reference/android/provider/DocumentsContract).
- DOCUMENTED: [ContentResolver provider-dependent write modes](https://developer.android.com/reference/android/content/ContentResolver#openOutputStream(android.net.Uri,%20java.lang.String)).
- DOCUMENTED: [FileDescriptor sync](https://developer.android.com/reference/java/io/FileDescriptor#sync()).
- DOCUMENTED: [Auto Backup domains](https://developer.android.com/identity/data/autobackup).
- DEVICE-OBSERVED in accepted R0 C2: Samsung SM-S938B/API36 external-storage provider, local
  volume mapping, sync and same-storage move without a second full blob. This is not proof for
  arbitrary SAF providers or crash atomicity.
