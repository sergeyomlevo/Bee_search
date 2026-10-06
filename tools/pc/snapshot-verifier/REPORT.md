# Independent Snapshot V1 Verifier Report

Historical initial slice report below. Current normative-schema alignment result
is recorded separately in [ALIGNMENT-REPORT.md](ALIGNMENT-REPORT.md); CF1–CF3 text
gaps are resolved. The original execution evidence and deviation remain preserved.

1. Baseline/final HEAD = origin/main = `4c62cbbef0cc2d15d2c4c43f0b00ae735d4924a9`.
2. Added autonomous build, CLI launcher, five main Kotlin files, four test files,
   preserved canonical vectors, README and CONTRACT-FINDINGS; only this tool subtree.
3. Independence boundary respected: YES for source inspection in this slice.
   Workers started without Android context; root's earlier Android work is disclosed.
   No application implementation read/import/copy/call in this slice.
4. Read-only CLI: stderr summary, stdout JSON, zero/nonzero exit; input unchanged test.
5. Canonical corpus: 9/9; separate existing Node reference invocation also 9/9.
6. Independently generated empty-graph valid subset: PASS, 17 entries.
   This does not establish full nonempty Snapshot V1 acceptance.
7. Negative corpus covers stale/raw digest corruption, filename identity/hash,
   unsupported enums, JSON duplicates/nonfinite values, ZIP path/entry hazards,
   truncation, container mutation, record ordering/duplicates and settings references.
8. ZipFile/ZipInputStream local/CEN name disagreement: ZIP_VIEW_MISMATCH asserted.
   Renamed hash after container comment is valid; unchanged filename rejects mutation.
9. Parser equality/+1 boundaries: depth, UTF-16 strings/keys, members, arrays.
   Byte equality/+1 uses the same production Budget for ZIP/total/entry/record caps;
   it does not claim semantically valid full snapshots at every exact byte boundary.
   Compressed expansion over actual uncompressed budget rejects; advertised sizes
   are not the budget authority. Exhaustive nonempty semantic corpus remains blocked.
10. REAL_APPLICATION_SNAPSHOT = NOT_RUN: owner supplied no PC file path.
11. CONTRACT FINDINGS: CF1 full domain record schemas/references missing;
    CF2 map geometry wire schema missing; CF3 exact media source reachability schema
    missing. Nonempty affected snapshots fail closed rather than receive partial PASS.
    Details, source sections and interpretations: CONTRACT-FINDINGS.md.
12. DISCREPANCIES: none established; application snapshot not tested.
13. Allowed test command: `.\gradlew.bat -p tools\pc\snapshot-verifier test --offline`.
    Final run BUILD SUCCESSFUL, 26 tests, 0 failures/errors; diff check clean.
    Independent read-only critic completed; weak FAIL assertions strengthened.
14. Process deviation: worker attempted unauthorized `compileKotlin --offline`.
    Wrapper attempted a Gradle distribution download; sandbox denied network.
    No compilation/download succeeded in that attempt. Later permitted test runs
    used installed cache. No Android builds/device actions/network research.
15. Commit: NOT_CREATED; blocking findings do not satisfy conditional commit gate.
    Status: `?? tools/pc/`; no tracked production changes. Build/corpus outputs ignored.
16. Verdict: CONTRACT_BLOCKED. Owner review needed for complete textual wire schemas;
    do not infer them from Android code or amend the contract automatically.

STOP: no push, R2, UI, device operations or production changes.
