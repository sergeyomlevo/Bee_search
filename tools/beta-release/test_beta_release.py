from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).parent))
import beta_release as release


def apk(name: str, code: int, commit: str = "1234567") -> release.ReleasedApk:
    return release.ReleasedApk(
        path=Path(f"bee-search-{name}-{commit}.apk"),
        package_name=release.EXPECTED_PACKAGE,
        version_name=name,
        version_code=code,
        commit=commit,
        signer_sha256="a" * 64,
    )


class BetaReleaseTest(unittest.TestCase):
    def test_reads_current_gradle_beta_version(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            build_file = Path(directory) / "build.gradle.kts"
            build_file.write_text(
                'versionCode = 3\nversionName = "1.2.0"\nversionNameSuffix = "-beta.1"\n',
                encoding="utf-8",
            )

            self.assertEqual(release.BetaVersion("1.2.0", 1, 3), release.read_beta_version(build_file))

    def test_accepts_new_name_sequence_and_higher_code(self) -> None:
        release.validate_next_beta(
            release.BetaVersion("1.2.0", 2, 4),
            [apk("1.2.0-beta.1", 3)],
        )

    def test_rejects_reused_version_name(self) -> None:
        with self.assertRaisesRegex(release.ReleaseError, "already released"):
            release.validate_next_beta(
                release.BetaVersion("1.2.0", 1, 4),
                [apk("1.2.0-beta.1", 3)],
            )

    def test_rejects_non_increasing_version_code(self) -> None:
        with self.assertRaisesRegex(release.ReleaseError, "must be greater"):
            release.validate_next_beta(
                release.BetaVersion("1.2.0", 2, 3),
                [apk("1.2.0-beta.1", 3)],
            )

    def test_rejects_non_increasing_sequence_in_same_product_line(self) -> None:
        with self.assertRaisesRegex(release.ReleaseError, "sequence"):
            release.validate_next_beta(
                release.BetaVersion("1.2.0", 1, 5),
                [apk("1.2.0-beta.2", 4)],
            )

    def test_rejects_missing_release_history(self) -> None:
        with self.assertRaisesRegex(release.ReleaseError, "No previous Beta APK"):
            release.validate_next_beta(release.BetaVersion("1.3.0", 1, 4), [])

    def test_rejects_release_history_without_commit_provenance(self) -> None:
        previous = apk("1.2.0-beta.1", 3)
        previous = release.ReleasedApk(
            previous.path,
            previous.package_name,
            previous.version_name,
            previous.version_code,
            None,
            previous.signer_sha256,
        )
        with self.assertRaisesRegex(release.ReleaseError, "commit provenance"):
            release.validate_next_beta(release.BetaVersion("1.2.0", 2, 4), [previous])

    def test_rejects_mixed_signing_history(self) -> None:
        first = apk("1.2.0-beta.1", 3)
        second = release.ReleasedApk(
            Path("bee-search-1.2.0-beta.2-2345678.apk"),
            release.EXPECTED_PACKAGE,
            "1.2.0-beta.2",
            4,
            "2345678",
            "b" * 64,
        )
        with self.assertRaisesRegex(release.ReleaseError, "signing certificate"):
            release.validate_next_beta(release.BetaVersion("1.2.0", 3, 5), [first, second])


if __name__ == "__main__":
    unittest.main()
