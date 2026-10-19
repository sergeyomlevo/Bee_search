# D106 — READY deletion audit and minimal plan, 2026-10-19

**STOP B implementation; proposal only.** OWNER approved the interaction sequence,
but the new [PNG+MD](ui/mockups/gpx-display-delete-v1.md) still needs explicit visual
approval under [mockup-first](ui/Создание%20интерфейсов.md). No production deletion
API/UI or schema changes were made. A remains independent.

## Observed existing boundaries

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

## Smallest proposed coherent extension (requires separate implementation approval)

1. Add a feature-local durable deletion journal/tombstone to Room with explicit
   migration and integrity checks; no universal deletion framework. Key by exact
   import UUID; retain Territory UUID, completed request UUID, owned original UUID,
   expected original identity and cleanup phase. Tombstone must survive removal
   of its parent (no FK to the removed import). Keep enough completion identity to
   reject replay of the old captured import request, without preventing a fresh
   explicitly requested reimport of identical bytes.
2. Under existing runtime/coordinator serialization, revalidate Territory + UUID,
   READY status, current job and original ownership. One Room transaction records
   durable intent, resolves/removes only its READY job, removes samples→segments→
   tracks→import. Respect RESTRICT; no broad delete and no hash-based deletion.
3. Once durable deletion is committed, remove that exact ID from selection and
   invalidate its prepared/native overlay with current epoch/revision guards.
   Ignore later preparation results for that tombstone. Other scopes unaffected.
4. New narrowly authorized Original Store operation verifies persisted ownership
   and that no import still refers to that original object. Unlink only proven
   owned payload/sidecars with directory durability, idempotently. Missing original
   is already clean; ownership mismatch fails closed. Never external source files.
5. Retry settings/files cleanup from the durable journal before normal runtime
   access and after restart. Report durable removal vs pending cleanup distinctly;
   never resurrect a deleted READY row or report a precommit failure as success.
6. Fault-inject before/after transaction, each unlink/fsync, settings save and
   callback; use isolated Room/original roots. Cover hidden/visible, identical bytes,
   other research/GPX, A→B, repeated confirmation/replay and restart. No destructive
   qualification on Samsung owner records.

Exact DDL/phases and migration are deliberately not implemented here; this is the
minimal plan requested when safe deletion requires new durable state/schema.
A filesystem tombstone alternative would need equivalent durable writer/recovery
and ownership semantics; it is not a safe shortcut around this review.

## Mockup check

Three static frames: context action, explicit named confirmation, precommit error.
HTML/PNG uses 412×892 logical frames and text enlarged1.7, readable stacked actions.
This is a layout study, not actual Samsung fontScale/Compose/accessibility proof.
Long-message scrolling, custom accessibility action and Back require qualification
when the approved production UI exists. Stage1/H05 OPEN; no GPX-specific backup gate.
