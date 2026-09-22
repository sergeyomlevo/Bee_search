#!/usr/bin/env python3
"""Fail-closed local release workflow for the Bee Search Beta APK."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


EXPECTED_PACKAGE = "org.beesearch.app.beta"
APK_NAME_PATTERN = re.compile(r"^bee-search-(?P<version>.+)-(?P<commit>[0-9a-f]{7,40})\.apk$")
PACKAGE_PATTERN = re.compile(
    r"package: name='(?P<package>[^']+)' versionCode='(?P<code>\d+)' versionName='(?P<name>[^']+)'"
)
BETA_VERSION_PATTERN = re.compile(r"^(?P<product>.+)-beta\.(?P<sequence>[1-9]\d*)$")


class ReleaseError(RuntimeError):
    pass


@dataclass(frozen=True)
class BetaVersion:
    product_version: str
    sequence: int
    version_code: int

    @property
    def version_name(self) -> str:
        return f"{self.product_version}-beta.{self.sequence}"


@dataclass(frozen=True)
class ReleasedApk:
    path: Path
    package_name: str
    version_name: str
    version_code: int
    commit: str | None
    signer_sha256: str


def run(command: list[str], cwd: Path | None = None) -> str:
    completed = subprocess.run(command, cwd=cwd, text=True, capture_output=True, check=False)
    if completed.returncode != 0:
        detail = (completed.stderr or completed.stdout).strip()
        raise ReleaseError(f"Command failed ({completed.returncode}): {' '.join(command)}\n{detail}")
    return completed.stdout


def exactly_one(pattern: str, text: str, label: str) -> str:
    matches = re.findall(pattern, text)
    if len(matches) != 1:
        raise ReleaseError(f"Expected exactly one {label} in app/build.gradle.kts, found {len(matches)}")
    return matches[0]


def read_beta_version(build_file: Path) -> BetaVersion:
    text = build_file.read_text(encoding="utf-8")
    product_version = exactly_one(r"\bversionName\s*=\s*\"([^\"]+)\"", text, "base versionName")
    version_code = int(exactly_one(r"\bversionCode\s*=\s*(\d+)", text, "versionCode"))
    suffix = exactly_one(r"\bversionNameSuffix\s*=\s*\"(-beta\.\d+)\"", text, "Beta versionNameSuffix")
    match = re.fullmatch(r"-beta\.([1-9]\d*)", suffix)
    if match is None:
        raise ReleaseError("Beta versionNameSuffix must be -beta.<positive sequence>")
    return BetaVersion(product_version, int(match.group(1)), version_code)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_android_tool(name: str) -> Path:
    sdk_root = os.environ.get("ANDROID_HOME")
    if not sdk_root and os.name == "nt" and os.environ.get("LOCALAPPDATA"):
        sdk_root = str(Path(os.environ["LOCALAPPDATA"]) / "Android" / "Sdk")
    if not sdk_root:
        raise ReleaseError("ANDROID_HOME is not set and the standard Android SDK location is unavailable")
    suffix = ".bat" if os.name == "nt" and name == "apksigner" else ".exe" if os.name == "nt" else ""
    candidates = sorted((Path(sdk_root) / "build-tools").glob(f"*/{name}{suffix}"), reverse=True)
    if not candidates:
        raise ReleaseError(f"Android SDK tool is unavailable: {name}")
    return candidates[0]


def inspect_apk(path: Path, aapt: Path, apksigner: Path) -> ReleasedApk:
    badging = run([str(aapt), "dump", "badging", str(path)])
    package_line = next((line for line in badging.splitlines() if line.startswith("package: ")), "")
    package_match = PACKAGE_PATTERN.search(package_line)
    if package_match is None:
        raise ReleaseError(f"Cannot read package/version metadata from {path}")
    certs = run([str(apksigner), "verify", "--print-certs", str(path)])
    signer_match = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", certs)
    if signer_match is None:
        raise ReleaseError(f"Cannot read signing certificate from {path}")
    filename_match = APK_NAME_PATTERN.fullmatch(path.name)
    return ReleasedApk(
        path=path,
        package_name=package_match.group("package"),
        version_name=package_match.group("name"),
        version_code=int(package_match.group("code")),
        commit=filename_match.group("commit") if filename_match else None,
        signer_sha256=signer_match.group(1).lower(),
    )


def validate_next_beta(current: BetaVersion, previous: list[ReleasedApk]) -> None:
    if not previous:
        raise ReleaseError("No previous Beta APK was found; owner must identify the release history before continuing")
    wrong_packages = [item for item in previous if item.package_name != EXPECTED_PACKAGE]
    if wrong_packages:
        raise ReleaseError(f"Unexpected package in Beta release archive: {wrong_packages[0].path}")
    missing_provenance = [item for item in previous if item.commit is None]
    if missing_provenance:
        raise ReleaseError(f"Beta artifact filename has no commit provenance: {missing_provenance[0].path}")
    previous_signers = {item.signer_sha256 for item in previous}
    if len(previous_signers) != 1:
        raise ReleaseError("Beta release archive contains more than one signing certificate")
    reused_name = [item.path for item in previous if item.version_name == current.version_name]
    if reused_name:
        raise ReleaseError(f"Beta versionName {current.version_name} was already released: {reused_name[0]}")
    maximum_code = max(item.version_code for item in previous)
    if current.version_code <= maximum_code:
        raise ReleaseError(
            f"Beta versionCode {current.version_code} must be greater than previous maximum {maximum_code}"
        )
    same_line_sequences = []
    for item in previous:
        match = BETA_VERSION_PATTERN.fullmatch(item.version_name)
        if match and match.group("product") == current.product_version:
            same_line_sequences.append(int(match.group("sequence")))
    if same_line_sequences and current.sequence <= max(same_line_sequences):
        raise ReleaseError(
            f"Beta sequence {current.sequence} must be greater than previous {max(same_line_sequences)} "
            f"for product {current.product_version}"
        )


def require_clean_main(repo: Path) -> str:
    branch = run(["git", "branch", "--show-current"], repo).strip()
    if branch != "main":
        raise ReleaseError(f"Beta releases must be built from main, current branch is {branch or '<detached>'}")
    status = run(["git", "status", "--porcelain"], repo).strip()
    if status:
        raise ReleaseError("Beta releases require a clean working tree")
    return run(["git", "rev-parse", "--short=7", "HEAD"], repo).strip()


def release_root_default(repo: Path) -> Path:
    return repo.parent / "Bee_search_beta_releases"


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check-only", action="store_true", help="validate the next Beta without building it")
    parser.add_argument("--release-root", type=Path, help="existing immutable Beta APK archive")
    parser.add_argument("--aapt", type=Path)
    parser.add_argument("--apksigner", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_arguments()
    repo = Path(__file__).resolve().parents[2]
    commit = require_clean_main(repo)
    current = read_beta_version(repo / "app" / "build.gradle.kts")
    release_root = (args.release_root or release_root_default(repo)).resolve()
    if not release_root.is_dir():
        raise ReleaseError(f"Beta release archive is unavailable: {release_root}")
    aapt = (args.aapt or find_android_tool("aapt")).resolve()
    apksigner = (args.apksigner or find_android_tool("apksigner")).resolve()
    previous = [inspect_apk(path, aapt, apksigner) for path in sorted(release_root.rglob("*.apk"))]
    validate_next_beta(current, previous)

    print(f"Previous maximum versionCode: {max(item.version_code for item in previous)}")
    print(f"New Beta: {current.version_name} ({current.version_code}), commit {commit}")
    if args.check_only:
        print("Beta release preflight: PASS")
        return 0

    gradle = repo / ("gradlew.bat" if os.name == "nt" else "gradlew")
    run([str(gradle), ":app:assembleBeta"], repo)
    built_apk = repo / "app" / "build" / "outputs" / "apk" / "beta" / "app-beta.apk"
    if not built_apk.is_file():
        raise ReleaseError(f"Gradle did not create the expected Beta APK: {built_apk}")
    built = inspect_apk(built_apk, aapt, apksigner)
    if (built.package_name, built.version_name, built.version_code) != (
        EXPECTED_PACKAGE,
        current.version_name,
        current.version_code,
    ):
        raise ReleaseError("Built APK metadata does not match the validated Beta version")
    previous_signers = {item.signer_sha256 for item in previous}
    if len(previous_signers) != 1 or built.signer_sha256 not in previous_signers:
        raise ReleaseError("Built APK signing certificate does not match the previous Beta")

    destination_directory = release_root / current.version_name
    destination = destination_directory / f"bee-search-{current.version_name}-{commit}.apk"
    if destination_directory.exists() or destination.exists():
        raise ReleaseError(f"Beta release destination already exists: {destination_directory}")
    destination_directory.mkdir(parents=False)
    try:
        shutil.copy2(built_apk, destination)
        copied = inspect_apk(destination, aapt, apksigner)
        if copied != ReleasedApk(
            destination,
            built.package_name,
            built.version_name,
            built.version_code,
            commit,
            built.signer_sha256,
        ):
            raise ReleaseError("Copied Beta APK metadata does not match the built artifact")
        if sha256(destination) != sha256(built_apk):
            raise ReleaseError("Copied Beta APK bytes do not match the built artifact")
    except Exception:
        if destination.exists():
            destination.unlink()
        if destination_directory.exists() and not any(destination_directory.iterdir()):
            destination_directory.rmdir()
        raise

    print(f"Beta APK: {destination}")
    print(f"SHA-256: {sha256(destination)}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ReleaseError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(2)
