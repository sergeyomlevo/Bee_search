# Bee Search — Governance Signal Log

This is a signal log for significant governance events defined by
`.agent/evaluation-policy.yaml`. It is not an activity log or project truth.
Routine success is not recorded, and past incidents must not be reconstructed
without sufficient evidence.

## Entry template

### `<date/time or task context> — <event type>`

- Expected governance behavior:
- Actual behavior:
- Severity/impact:
- Source of classification:
- Evidence reference:
- Preliminary disposition:

#### Independent review (only if performed)

- Reviewer or review context:
- Status: `pending | completed`
- Result when completed: `confirmed | modified | rejected`
- Evidence reference:

## Events

Recorded signals follow; routine successful reviews are intentionally absent.


### 2026-10-16 — adversarial_review_found_material_issue / D106 exclusive publication

- Expected governance behavior: challenge the ownership/cleanup safety argument before qualification and commit; preserve incomplete-publication evidence.
- Actual behavior: separate read-only reviewer found that the draft cleanupStaging could discard its retained publication witness without verifying the published final and durable-publication authorization. The draft also had a constructor argument typo.
- Severity/impact: material draft defect; unsafe evidence removal was possible through the draft seam. Found before final Android qualification and commit.
- Source of classification: root preliminary classification from the separate reviewer findings; decision class persistence/filesystem safety, triggers claim_strength and evidence_scope. This records a failure in the draft, not review coverage or governance effectiveness.
- Evidence reference: current-session /root/exclusive_publication_review findings; GpxOriginalStore.cleanupStaging and GpxOriginalPublication.authorizeStagingRemoval; focused incompleteFinalCannotBeRemovedByCleanupStaging regression.
- Preliminary disposition: corrected before ATTEMPT3 freeze. Existing final now requires expected descriptor, bound witness/proof, verified final and current durable FILE_PUBLISHED authorization. Incomplete final must be compensated before staging witness removal.
- Independent review: separate read-only Luna context, completed; confirmed that the revised cleanup closes the named gap. This is neither OWNER acceptance nor runtime proof. Runtime qualification is recorded separately in ignored D106 attempt evidence.
