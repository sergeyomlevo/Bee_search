# Independent Snapshot V1/V2 verifier

Standalone Kotlin/JVM boundary; no dependency on Android code or root Android build.
JDK 17, cached Kotlin 2.2.10, Gson 2.11.0 and JUnit 4.13.2. No network required.

From the repository root, the only build/test command for this slice is:

```powershell
.\gradlew.bat -p tools\pc\snapshot-verifier test --offline
```

Then, on JDK 17:

```powershell
.\tools\pc\snapshot-verifier\verify-snapshot.ps1 'C:\path\snapshot-<uuid>-<sha>.zip'
```

The launcher is the `verify-snapshot` command: one file argument, no interactive UI.
Human summary goes to stderr; one machine-readable JSON report goes to stdout.
Exit 0 means all supported checks passed; nonzero includes malformed/unsupported/
unreadable and contract-verification failure. Never modifies input. Reports contain
paths/counts/hashes/issues, never research content. Owned temporary verification
spools are separate from the input and removed; generated corpus stays outside Git.

## Evidence profiles and exit codes

Snapshot V1 and V2 accept exactly two evidence tuples (wire schema §7.1):
`METADATA_ONLY`/`NO_MEDIA_EVIDENCE`/`COMPLETE` and `FULL`/`LOCAL_VERIFIED`/`COMPLETE`.
The archive structure is the same for both: metadata and references only, no payload
bytes and no `Media/*` entry.

A metadata-only snapshot can be judged on its own, so standalone verification ends in
`PASS`. A FULL snapshot additionally claims that the required blobs of the same capture
were verified in the bound local repository, which a ZIP alone cannot establish, so
standalone verification reports `REPOSITORY_EVIDENCE_REQUIRED` instead of a final PASS.
That is not a failure and not an acceptance: run the repository-aware mode for the local
evidence decision. An unsupported tuple or token fails with
`MANIFEST_UNSUPPORTED_PROFILE`.

Exit codes: `0` = PASS, `1` = FAIL/invalid, `2` = invalid arguments or unreadable input,
`3` = REPOSITORY_EVIDENCE_REQUIRED (structure valid, declared local repository evidence
needs a repository root). The JSON report always states the declared `snapshotProfile`,
`evidencePolicy`, `creationResult` and `repositoryEvidenceRequired`.

## Repository-aware mode

A second, additive mode answers a question the standalone mode cannot: does the repository
actually hold the bytes the selected snapshot requires?

```powershell
.\tools\pc\snapshot-verifier\verify-repository.ps1 'C:\path\repository-root' 'C:\path\snapshot-<uuid>-<sha>.zip'
```

It is read-only and never modifies the repository or the snapshot. It checks:

- `repository.json`: bounded JSON with exactly the four header keys, `beesearch-repository`
  format version 1, canonical repository UUID and variant;
- the snapshot itself, through the unchanged standalone verification (for a FULL snapshot that
  means its structure is valid and its local evidence is decided here, not there);
- context agreement: snapshot `repositoryId` and `variant` against the header;
- every blob in the snapshot's `references/media-blobs.jsonl`: the canonical path
  `Media/<sha256>.<canonicalExtension>` must exist as a regular file with the exact declared
  size and a SHA-256 recomputed from its actual bytes;
- extras: additional valid blobs are allowed, because other snapshots may require them. A blob
  present under a second canonical name, or a `Media` entry that is not a canonical blob name, is
  a repository-integrity failure rather than an allowed extra.

Exit 0 means the repository context, the snapshot and every required blob passed. The JSON report
lists the repository identity, snapshot identity, per-blob outcomes and any issues.

The mode reads the snapshot archive twice: once through the unchanged standalone verification and once
to read its references. Both reads of a static file are the same bytes; the snapshot file is not
re-hashed between them, so verify a snapshot that is not being rewritten while the check runs.

Both modes are implemented from the documented text only; no Android production code is imported
or copied.

## Independence and limitations

Implementation workers began with empty context and were explicitly forbidden to
read `app/` source/tests. Root integrated against text contracts only during this
slice; root had worked on Android in a previous slice, which is not erased history.
No Android parser/codec imports, copies, reflection or calls are used here.
The normative field-level source is `docs/snapshot-v1-wire-schema.md`, sections
1–8, alongside the approved envelope/preflight and canonical vectors. CF1–CF3
are now resolved; the original blocked report remains historical evidence.
See [ALIGNMENT-REPORT.md](ALIGNMENT-REPORT.md) for current acceptance and mapping.
All 13 collections, named-target FKs/subtypes/lifecycle, settings, exact v1/v2
geometry and recomputed eligible media references are verified. No media bytes
are required for METADATA_ONLY / NO_MEDIA_EVIDENCE / COMPLETE.

The manifest `snapshotFormatVersion` selects the closed domain schema. V1 keeps
the frozen field inventories. V2 additionally requires `observationDate` on
observation points and nullable `fixationDate` on physical objects; both are
validated as exact, real `YYYY-MM-DD` calendar dates. Dates are read as stored
values and are never derived from timestamps.

V1 objects are closed; UUID/SHA/enums are exact lexical identities. Domain member
order is irrelevant, JSONL record order is not. Optional/nullable media identity
is preserved, but only positive safe-size known identities produce references.
No trim/case-folding/Unicode normalization is performed. Typed schema/graph/media
failures are distinct from file-access/container/integrity failures.

No real application ZIP path was supplied for alignment: that acceptance remains
NOT_RUN. Generated nonempty corpus acceptance is not Android writer acceptance.

## Offline technology research

SOURCE-OBSERVED: installed Temurin 17.0.18 `lib/src.zip`, official OpenJDK
`java.base/java/util/zip/{ZipFile,ZipInputStream}.java`: CEN versus local-stream
views, CRC handling and malformed central-directory rejection. Both APIs are used;
central entry CRC must additionally be checked from actual bytes.
SOURCE-OBSERVED: cached official Gson 2.11.0 sources, `stream/JsonReader.java`:
default is LEGACY_STRICT; explicitly select Strictness.STRICT. Standard streaming
reader is reused, with bounded duplicate-aware schema handling rather than a new
general-purpose JSON grammar. Custom canonical emission implements the already
approved restricted profile and is tested against the preserved 9 external vectors.

No new survey or network research: accepted RFC8785 profile and local upstream
sources supply the primary evidence. Offline JVM plugin marker was not cached;
the installed official Kotlin Gradle plugin classpath is applied directly instead.
Production docs/contracts are deliberately unchanged.
