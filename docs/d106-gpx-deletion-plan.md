# D106 — READY deletion audit and minimal plan, 2026-10-19

**APPROVED BY OWNER, 2026-10-19; implementation completed.** The approved
[PNG+MD](ui/mockups/gpx-display-delete-v1.md) and this plan define the deletion
contract. Production deletion uses the existing GPX boundaries and a feature-local
Room journal/tombstone; it does not introduce a generic deletion framework.

## Observed existing boundaries before this slice

- `GpxEntities`: RESTRICT parent→track→segment→sample and job→import links;
  unique original_object_id. Identical-byte logical imports have distinct UUIDs
  and original objects, not content-addressed shared payloads.
- `GpxDao`: child delete queries exist for compensation; parent delete accepts
  PENDING only. There is no READY delete transaction.
- `GpxPersistenceSchema`: import journal permits CONTINUE/ABORT and existing
  import phases only. It is not a generic deletion journal.
- `GpxImportCoordinator`: process-wide serialized writers; READY recovery never
  compensates READY. completedResult cleans staging and forgets READY job.
- `GpxOriginalStore.cleanupAbortedOriginal`: requires current no-READY/abort and
  publication ownership proof. Reusing it by pretending READY was an aborted
  import would violate its contract. Payload path is owned original UUID plus
  original.gpx; no deletion of an arbitrary external picker path is acceptable.
- `GpxRuntime`: reconciliation gate and canonical reads/catalog; selection is in
  separate Settings DataStore; overlays are separate immutable prepared snapshots.

## Why direct removal is insufficient

Filesystem and Room have no common transaction. File-first failure destroys the
original while READY remains. Room-first crash loses the durable ownership/cleanup
work needed to remove the managed copy. The existing import journal cannot keep
that work after deleting its RESTRICT-linked import and cannot record deletion
intent. A stale captured request/callback also needs a durable deleted-result
boundary so it cannot republish the removed import.

## Approved coherent extension and implementation contract

1. Add a feature-local durable deletion journal/tombstone to Room with explicit
   migration and integrity checks; no universal deletion framework. Key by exact
   import UUID; retain Territory UUID, completed request UUID, owned original UUID,
   expected original identity and cleanup phase. Tombstone must survive removal
   of its parent (no FK to the removed import). Keep enough completion identity to
   reject replay of the old captured import request, without preventing a fresh
   explicitly requested reimport of identical bytes.
2. Under existing runtime/coordinator serialization, revalidate Territory + UUID,
   READY status, current job and original ownership. One Room transaction records
   durable intent and removes samples→segments→tracks→import only after import
   reconciliation has settled any staging job. A residual READY job is a safety
   failure; deletion never removes the staging job as a substitute for recovery.
   Respect RESTRICT; no broad delete and no hash-based deletion.
3. Once durable deletion is committed, remove that exact ID from selection and
   invalidate its prepared/native overlay with current epoch/revision guards.
   Ignore later preparation results for that tombstone. Other scopes unaffected.
4. New narrowly authorized Original Store operation verifies persisted ownership
   and that no import still refers to that original object. Unlink only proven
   owned payload/sidecars with directory durability, idempotently. Missing original
   is already clean; ownership mismatch fails closed. Never external source files.
5. Retry settings/files cleanup from the durable journal before normal runtime
   access and after restart. Files and settings have independent completion flags;
   one cleanup debt does not block other GPX. Report durable removal vs pending
   cleanup distinctly; never resurrect a deleted READY row or report a precommit
   failure as success.
6. Fault-inject before/after transaction, each unlink/fsync, settings save and
   callback; use isolated Room/original roots. Cover hidden/visible, identical bytes,
   other research/GPX, A→B, repeated confirmation/replay and restart. No destructive
   qualification on Samsung owner records.

The implementation uses the Room migration and lifecycle described below. A
filesystem-only tombstone is not used; it would need equivalent durable writer,
recovery and ownership semantics.

## Implemented lifecycle

Room migration 15→16 creates `gpx_deletions`, a permanent feature-local
tombstone without a foreign key to the removed import. The transaction is bound
to exact Territory/import/request/original UUIDs and deletes dependent canonical
rows in RESTRICT-safe order. The managed original store persists and verifies
directory/device/inode ownership proof before unlinking; external picker files,
including `Downloads/BeeSearch/Dev/Exchange/GPX`, are never touched. A durable
deletion event invalidates the existing Registry overlay and catalog row. Settings
and managed-file cleanup retry independently after restart. A pending file cleanup
never claims that all bytes are gone, while the removed READY import remains
hidden and cannot be restored by reconciliation.

## Mockup check

Three static frames: context action, explicit named confirmation, precommit error.
HTML/PNG uses 412×892 logical frames and text enlarged1.7, readable stacked actions.
The approved production UI retains the composition, accessibility action, Back and
fontScale semantics. Runtime qualification remains recorded by the implementation
checklist and test report. Stage1/H05 OPEN; no GPX-specific backup gate.
