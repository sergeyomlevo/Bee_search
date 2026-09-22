# Bee Search Beta release

This is the canonical local workflow for publishing the next Bee Search Beta.
Do not publish `app-beta.apk` directly and do not use a Git hash as a substitute
for Android or user-facing versioning.

The one version source is `app/build.gradle.kts`:

- `versionName` is the product version, for example `1.2.0`;
- Beta `versionNameSuffix` is the release sequence, for example `-beta.2`;
- `versionCode` is the monotonically increasing Android package version;
- the clean `main` short commit hash is artifact provenance only.

Before the next Beta, the owner must decide whether work remains in the current
product line or starts a new product version. Then update all three applicable
values in `app/build.gradle.kts` in a dedicated version commit. Never infer a
new product version only from elapsed time or commit count.

The release archive defaults to the sibling directory
`C:\App\Bee_search_beta_releases`. It is immutable evidence of previously
published APK metadata. Preserve old artifacts and make it available before a
release. The workflow fails closed when the archive is absent or contains no
previous Beta.

Run preflight first:

```powershell
python tools\beta-release\beta_release.py --check-only
```

Preflight reads actual APK manifests and certificates with Android SDK `aapt`
and `apksigner`. It requires:

- clean `main`;
- a versionName not already present in the archive;
- a Beta sequence greater than earlier releases in the same product line;
- a versionCode greater than every archived installable Beta.

After reviewing the reported previous and new versions, publish with:

```powershell
python tools\beta-release\beta_release.py
```

Only after preflight passes does the script run `:app:assembleBeta`. It verifies
the built package, version and signing certificate, then copies it to a new
`<versionName>` directory as
`bee-search-<versionName>-<shortCommit>.apk`. Existing release directories and
artifacts are never overwritten.

If product version ownership, previous artifacts or signing identity cannot be
established, stop and ask the owner. Do not bypass the gate by assembling and
renaming an APK manually.
