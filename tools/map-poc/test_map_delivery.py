import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest
from urllib.parse import quote


TOOL_DIR = Path(__file__).resolve().parent
BUILD_SCRIPT = TOOL_DIR / "build-map-package.ps1"
PUSH_SCRIPT = TOOL_DIR / "push-map-package-for-import.ps1"


class MapPackageDeliveryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.pwsh = shutil.which("pwsh")
        if cls.pwsh is None:
            raise unittest.SkipTest("PowerShell 7 is required for the PC delivery tests")

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.adb_log = self.root / "adb-log.jsonl"
        self.python_log = self.root / "python-log.jsonl"
        self.fake_adb = self.root / "fake-adb.cmd"
        self.fake_python = self.root / "fake-python.cmd"
        self.fake_java = self.root / "fake-java.cmd"
        (self.root / "fake-adb.py").write_text(
            textwrap.dedent(
                """
                import json
                import os
                from pathlib import Path
                import sys

                args = sys.argv[1:]
                with Path(os.environ["BEE_TEST_ADB_LOG"]).open("a", encoding="utf-8") as log:
                    log.write(json.dumps({"args": args}, ensure_ascii=False) + "\\n")
                if "get-state" in args:
                    print("device")
                elif "ro.product.model" in args:
                    print("Fake Samsung")
                elif any(argument.startswith("sha256sum ") for argument in args):
                    print(f'{os.environ["BEE_TEST_EXPECTED_SHA"]}  artifact.pmtiles')
                """
            ).strip()
            + "\n",
            encoding="utf-8",
        )
        (self.root / "fake-python.py").write_text(
            textwrap.dedent(
                """
                import json
                import os
                from pathlib import Path
                import sys

                args = sys.argv[1:]
                with Path(os.environ["BEE_TEST_PYTHON_LOG"]).open("a", encoding="utf-8") as log:
                    log.write(json.dumps({"args": args}, ensure_ascii=False) + "\\n")
                command = args[1] if len(args) > 1 else None
                if command == "validate-package-id":
                    print(json.dumps({"packageId": args[-1]}, ensure_ascii=False))
                elif command == "source-info":
                    print(json.dumps({
                        "selectedBounds": {"west": 42.0, "south": 55.0, "east": 43.0, "north": 56.0},
                        "selectedMetrics": {"widthKm": 64.0, "heightKm": 111.0, "areaKm2": 7104.0},
                        "source": {"path": "fixture.osm.pbf"},
                        "sourceHeaderContainsSelectedBounds": True,
                    }))
                """
            ).strip()
            + "\n",
            encoding="utf-8",
        )
        self.fake_adb.write_text(
            f'@"{sys.executable}" "%~dp0fake-adb.py" %*\n', encoding="utf-8"
        )
        self.fake_python.write_text(
            f'@"{sys.executable}" "%~dp0fake-python.py" %*\n', encoding="utf-8"
        )
        self.fake_java.write_text('@exit /b 0\n', encoding="ascii")

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def run_delivery(self, variant: str, package_id: str) -> subprocess.CompletedProcess[str]:
        package_directory = self.root / f"package-{variant}"
        package_directory.mkdir()
        artifact = package_directory / f"{package_id}.pmtiles"
        artifact.write_bytes(f"fixture-{variant}".encode())
        (package_directory / f"{package_id}.pmtiles.manifest.json").write_text(
            "{}\n", encoding="utf-8"
        )
        env = os.environ.copy()
        env["BEE_TEST_ADB_LOG"] = str(self.adb_log)
        env["BEE_TEST_PYTHON_LOG"] = str(self.python_log)
        env["BEE_TEST_EXPECTED_SHA"] = hashlib.sha256(artifact.read_bytes()).hexdigest()
        return subprocess.run(
            [
                self.pwsh,
                "-NoProfile",
                "-File",
                str(PUSH_SCRIPT),
                "-PackageDirectory",
                str(package_directory),
                "-PackageId",
                package_id,
                "-Serial",
                "TEST-SERIAL",
                "-Variant",
                variant,
                "-Adb",
                str(self.fake_adb),
                "-Python",
                str(self.fake_python),
            ],
            cwd=TOOL_DIR.parent.parent,
            env=env,
            text=True,
            encoding="utf-8",
            capture_output=True,
            check=False,
        )

    def read_adb_calls(self) -> list[list[str]]:
        return [
            json.loads(line)["args"]
            for line in self.adb_log.read_text(encoding="utf-8-sig").splitlines()
        ]

    def read_python_calls(self) -> list[list[str]]:
        return [
            json.loads(line)["args"]
            for line in self.python_log.read_text(encoding="utf-8-sig").splitlines()
        ]

    def test_build_plan_preserves_unicode_space_package_id_as_one_argument(self) -> None:
        if not (TOOL_DIR / "work" / "planetiler-0.10.0.jar").is_file():
            self.skipTest("The pinned local Planetiler jar is required by the existing build preflight")
        source = self.root / "fixture.osm.pbf"
        source.write_bytes(b"fixture")
        package_id = "Beta Test Territory--9cc6cc3a--map-v1"
        env = os.environ.copy()
        env["BEE_TEST_PYTHON_LOG"] = str(self.python_log)
        result = subprocess.run(
            [
                self.pwsh,
                "-NoProfile",
                "-File",
                str(BUILD_SCRIPT),
                "-West",
                "42",
                "-South",
                "55",
                "-East",
                "43",
                "-North",
                "56",
                "-PackageId",
                package_id,
                "-SourcePbf",
                str(source),
                "-Java",
                str(self.fake_java),
                "-Python",
                str(self.fake_python),
                "-PlanOnly",
            ],
            cwd=TOOL_DIR.parent.parent,
            env=env,
            text=True,
            encoding="utf-8",
            capture_output=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        validation_calls = [call for call in self.read_python_calls() if "validate-package-id" in call]
        self.assertEqual(1, len(validation_calls))
        call = validation_calls[0]
        self.assertEqual(package_id, call[call.index("--package-id") + 1])

    def test_variants_use_one_canonical_offline_maps_directory(self) -> None:
        cases = (
            ("Stable", "territory-benchmark-v1"),
            ("Beta", "Beta Test Territory--9cc6cc3a--map-v1"),
            ("Dev", "Лух--7e82a310--map-v1"),
        )
        for variant, package_id in cases:
            with self.subTest(variant=variant, package_id=package_id):
                self.adb_log.unlink(missing_ok=True)
                result = self.run_delivery(variant, package_id)
                self.assertEqual(0, result.returncode, result.stderr + result.stdout)
                destination = f"/sdcard/Download/BeeSearch/{variant}/Exchange/OfflineMaps"
                calls = self.read_adb_calls()
                push_calls = [call for call in calls if "push" in call]
                self.assertEqual(2, len(push_calls))
                for call in push_calls:
                    push_index = call.index("push")
                    self.assertEqual(f"{destination}/", call[push_index + 2])
                    self.assertNotIn(package_id, destination)
                pushed_names = {Path(call[call.index("push") + 1]).name for call in push_calls}
                self.assertEqual(
                    {f"{package_id}.pmtiles", f"{package_id}.pmtiles.manifest.json"},
                    pushed_names,
                )
                hash_calls = [
                    call for call in calls if any(str(arg).startswith("sha256sum ") for arg in call)
                ]
                self.assertEqual(1, len(hash_calls))
                self.assertIn(f"'{destination}/{package_id}.pmtiles'", hash_calls[0][-1])
                media_uris = [
                    call[call.index("-d") + 1]
                    for call in calls
                    if "android.intent.action.MEDIA_SCANNER_SCAN_FILE" in call
                ]
                self.assertEqual(2, len(media_uris))
                expected_uris = {
                    quote(f"file://{destination}/{name}", safe="/:") for name in pushed_names
                }
                self.assertEqual(expected_uris, set(media_uris))

    def test_unknown_variant_fails_before_adb(self) -> None:
        result = self.run_delivery("Unknown", "territory-benchmark-v1")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.adb_log.exists())


if __name__ == "__main__":
    unittest.main()
