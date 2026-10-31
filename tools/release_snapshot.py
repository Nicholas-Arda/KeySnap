"""Local Play release archive. Never builds, signs, commits, pushes, or publishes.

Each snapshot pins both Git repositories and the exact signed AAB. Source identity is
recorded, not proof that an arbitrary supplied AAB was compiled from those commits.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile


def git(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo), *args], stderr=subprocess.PIPE).decode("utf-8").strip()


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def fields(data):
    """Read protobuf wire fields; AAPT XmlNode/XmlElement/XmlAttribute schema:
    android.googlesource.com/platform/frameworks/base/+/main/tools/aapt2/Resources.proto
    """
    pos = 0

    def varint():
        nonlocal pos
        value = 0
        for shift in range(0, 70, 7):
            if pos >= len(data):
                raise ValueError("Truncated protobuf")
            byte = data[pos]
            pos += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value
        raise ValueError("Invalid protobuf varint")

    while pos < len(data):
        key = varint()
        wire = key & 7
        if wire == 0:
            value = varint()
        elif wire in (1, 2, 5):
            size = varint() if wire == 2 else (8 if wire == 1 else 4)
            if pos + size > len(data):
                raise ValueError("Truncated protobuf field")
            value = data[pos:pos + size]
            pos += size
        else:
            raise ValueError("Unsupported protobuf wire type")
        yield key >> 3, value


def aab_identity(path):
    with zipfile.ZipFile(path) as archive:
        element = dict(fields(archive.read("base/manifest/AndroidManifest.xml")))[1]
        attrs = {}
        for number, value in fields(element):
            if number == 4:
                attribute = dict(fields(value))
                attrs[attribute[2].decode()] = attribute.get(3, b"").decode()
        signed = any(re.fullmatch(r"META-INF/[^/]+\.(RSA|DSA|EC)", name, re.I) for name in archive.namelist())
    if attrs.get("package") != "io.github.nicholasarda.keysnap" or not signed:
        raise ValueError("A signed Play-package AAB is required; .dev and unsigned bundles are rejected")
    version = attrs.get("versionName", "")
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:\.\d+)?", version):
        raise ValueError("Unexpected release version name")
    return version, int(attrs["versionCode"])


def commit(repo, ref):
    if ref.startswith("-"):
        raise ValueError("Invalid Git ref")
    return git(repo, "rev-parse", "--verify", "--end-of-options", ref + "^{commit}")


def ensure_clean(repo, private=False):
    paths = ["app/src", "bridge/src"] if private else [
        "app/src", "app/build.gradle.kts", "app/proguard-rules.pro",
        "bridge/src", "bridge/build.gradle.kts", "gradle",
        "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    ]
    paths += [":(exclude)app/src/test", ":(exclude)app/src/androidTest", ":(exclude)bridge/src/test"]
    changes = git(repo, "status", "--porcelain=v1", "--untracked-files=all", "--", *paths).splitlines()
    changes = [line for line in changes if line != "?? gradle/wrapper/gradle-wrapper.jar"]
    if changes:
        raise ValueError("Commit shipping-source changes before freezing a release: " + str(repo))


def paired_refs(root, name, core_sha, pro_sha, replace=False):
    """Compare-and-swap both local refs, rolling the first back if the second fails."""
    repos = (root, root / "pro")
    old = []
    for repo in repos:
        try:
            old.append(git(repo, "rev-parse", "--verify", name))
        except subprocess.CalledProcessError:
            old.append(None)
        if old[-1] and not replace:
            raise ValueError("Snapshot ref already exists: " + name)
        if name.startswith("refs/heads/") and ("branch " + name) in git(repo, "worktree", "list", "--porcelain").splitlines():
            raise ValueError("Cannot move a checked-out maintenance branch")
    git(repos[0], "update-ref", name, core_sha, old[0] or "0" * 40)
    try:
        git(repos[1], "update-ref", name, pro_sha, old[1] or "0" * 40)
    except Exception:
        if old[0]:
            git(repos[0], "update-ref", name, old[0], core_sha)
        else:
            git(repos[0], "update-ref", "-d", name, core_sha)
        raise


def snapshot(root, aab, output, core_ref="HEAD", pro_ref="HEAD", historical=False):
    root, aab, output = root.resolve(), aab.resolve(), output.resolve()
    if output.is_relative_to(root) and not git(root, "check-ignore", "--", str(output)):
        raise ValueError("Release archives must stay outside public Git tracking")
    core_sha, pro_sha = commit(root, core_ref), commit(root / "pro", pro_ref)
    if not historical:
        if core_sha != commit(root, "HEAD") or pro_sha != commit(root / "pro", "HEAD"):
            raise ValueError("Freeze the checked-out commits, or explicitly mark a historical reconstruction")
        ensure_clean(root)
        ensure_clean(root / "pro", private=True)
    version, code = aab_identity(aab)
    config = git(root, "show", core_sha + ":app/build.gradle.kts")
    if int(re.search(r"versionCode\s*=\s*(\d+)", config)[1]) != code or re.search(r'versionName\s*=\s*"([^"]+)"', config)[1] != version:
        raise ValueError("AAB version differs from the selected source commit")
    previous = [json.loads(p.read_text(encoding="utf-8")) for p in output.glob("*/manifest.json")]
    if previous and code <= max(p["versionCode"] for p in previous):
        raise ValueError("Use a new versionCode greater than every archived bundle")
    key = f"{version}-code{code}"
    destination = output / key
    if destination.exists():
        raise ValueError("Snapshot already exists; archives are immutable")
    tag = "refs/tags/play/" + key
    for repo in (root, root / "pro"):
        if tag in git(repo, "for-each-ref", "--format=%(refname)", tag).splitlines():
            raise ValueError("Snapshot tag already exists")
    destination.mkdir(parents=True)
    for repo, sha, filename in ((root, core_sha, "core.zip"), (root / "pro", pro_sha, "pro.zip")):
        git(repo, "archive", "--format=zip", "--output=" + str(destination / filename), sha)
    # The wrapper is intentionally untracked in this workspace. Preserve it, never local.properties/keys.
    with zipfile.ZipFile(destination / "core.zip", "a") as archive:
        for relative in ("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar"):
            if (root / relative).is_file() and relative not in archive.namelist():
                archive.write(root / relative, relative)
    shutil.copyfile(aab, destination / "app.aab")
    manifest = {
        "schema": 1, "versionName": version, "versionCode": code,
        "coreCommit": core_sha, "proCommit": pro_sha,
        "sourceAssociation": "historical-reconstruction" if historical else "clean-checkout-at-freeze",
        "createdUtc": datetime.now(timezone.utc).isoformat(),
        "files": {name: digest(destination / name) for name in ("app.aab", "core.zip", "pro.zip")},
    }
    paired_refs(root, tag, core_sha, pro_sha)
    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return destination


def mark_live(root, directory, published):
    if not published:
        raise ValueError("Only mark live after the user confirms publication in Play")
    root, directory = root.resolve(), directory.resolve()
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    for name, expected in manifest["files"].items():
        if name not in ("app.aab", "core.zip", "pro.zip") or digest(directory / name) != expected:
            raise ValueError("Archive integrity check failed")
    if aab_identity(directory / "app.aab") != (manifest["versionName"], manifest["versionCode"]):
        raise ValueError("Archived AAB identity mismatch")
    active_file = directory.parent / "live.json"
    if active_file.exists():
        active = json.loads(active_file.read_text(encoding="utf-8"))
        if manifest["versionCode"] < active["versionCode"]:
            raise ValueError("Cannot move live backwards")
    core_sha = commit(root, manifest["coreCommit"])
    pro_sha = commit(root / "pro", manifest["proCommit"])
    paired_refs(root, "refs/heads/stable", core_sha, pro_sha, replace=True)
    active_file.write_text(json.dumps({**manifest, "snapshot": directory.name}, indent=2) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    commands = parser.add_subparsers(dest="command", required=True)
    save = commands.add_parser("snapshot")
    save.add_argument("--aab", type=Path, required=True)
    save.add_argument("--core-ref", default="HEAD")
    save.add_argument("--pro-ref", default="HEAD")
    save.add_argument("--historical", action="store_true")
    save.add_argument("--output", type=Path)
    live = commands.add_parser("mark-live")
    live.add_argument("--snapshot", type=Path, required=True)
    live.add_argument("--published", action="store_true")
    args = parser.parse_args()
    if args.command == "snapshot":
        print(snapshot(args.root, args.aab, args.output or args.root / "KeySnap/Yayinlar", args.core_ref, args.pro_ref, args.historical))
    else:
        mark_live(args.root, args.snapshot, args.published)
        print("Local stable refs updated. Nothing was uploaded or published.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as error:
        sys.exit(str(error))
