import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile


spec = importlib.util.spec_from_file_location("release_snapshot", Path(__file__).parents[1] / "release_snapshot.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


def varint(value):
    result = bytearray()
    while value > 127:
        result.append((value & 127) | 128)
        value >>= 7
    return bytes(result + bytes([value]))


def message(number, value):
    if isinstance(value, str):
        value = value.encode()
    return varint((number << 3) | 2) + varint(len(value)) + value


def bundle(path, version="1.1.6", code=16, package="io.github.nicholasarda.keysnap", signed=True):
    attributes = b"".join(message(4, message(2, key) + message(3, str(value))) for key, value in {
        "package": package, "versionName": version, "versionCode": code,
    }.items())
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("base/manifest/AndroidManifest.xml", message(1, message(3, "manifest") + attributes))
        if signed:
            archive.writestr("META-INF/TEST.RSA", "fixture marker, not a real signature")


def save_commit(repo):
    release.git(repo, "add", ".")
    release.git(repo, "-c", "user.name=Release fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "fixture")
    return release.commit(repo, "HEAD")


class ReleaseSnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="keysnap-release-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.pro = self.root / "pro"
        for repo in (self.root, self.pro):
            repo.mkdir(exist_ok=True)
            subprocess.run(["git", "init", "-qb", "master", str(repo)], check=True, capture_output=True)
            (repo / "app/src/main").mkdir(parents=True)
            (repo / "app/src/main/state.txt").write_text("released source")
        (self.root / ".gitignore").write_text("/pro/\n/KeySnap/\n*.aab\n")
        self.config = self.root / "app/build.gradle.kts"
        self.config.write_text('versionCode = 16\nversionName = "1.1.6"\n')
        self.pro_sha = save_commit(self.pro)
        self.core_sha = save_commit(self.root)
        self.aab = self.root / "signed.aab"
        bundle(self.aab)
        self.output = self.root / "KeySnap/Yayinlar"

    def snapshot(self, **kwargs):
        return release.snapshot(self.root, self.aab, self.output, **kwargs)

    def test_historical_freeze_and_live_refs_leave_development_edits_untouched(self):
        for repo in (self.root, self.pro):
            (repo / "app/src/main/state.txt").write_text("unfinished next version")
        directory = self.snapshot(core_ref=self.core_sha, pro_ref=self.pro_sha, historical=True)
        release.mark_live(self.root, directory, True)
        for repo, sha in ((self.root, self.core_sha), (self.pro, self.pro_sha)):
            self.assertEqual(sha, release.commit(repo, "stable"))
            self.assertEqual("master", release.git(repo, "branch", "--show-current"))
            self.assertEqual("unfinished next version", (repo / "app/src/main/state.txt").read_text())
        with zipfile.ZipFile(directory / "pro.zip") as archive:
            self.assertEqual(b"released source", archive.read("app/src/main/state.txt"))
        with zipfile.ZipFile(directory / "core.zip") as archive:
            self.assertFalse(any(name.startswith("pro/") for name in archive.namelist()))

    def test_normal_freeze_rejects_dirty_shipping_source(self):
        (self.pro / "app/src/main/state.txt").write_text("uncommitted")
        with self.assertRaisesRegex(ValueError, "Commit shipping-source"):
            self.snapshot()
        self.assertFalse(self.output.exists())

    def test_aab_version_must_match_source(self):
        bundle(self.aab, "1.1.7", 17)
        with self.assertRaisesRegex(ValueError, "version differs"):
            self.snapshot()
        self.assertFalse(self.output.exists())

    def test_untracked_release_source_is_not_missed(self):
        (self.root / "app/src/release").mkdir()
        (self.root / "app/src/release/new.txt").write_text("uncommitted release behavior")
        with self.assertRaisesRegex(ValueError, "Commit shipping-source"):
            self.snapshot()

    def test_untracked_phone_test_helper_does_not_block_shipping_snapshot(self):
        (self.root / "app/src/androidTest").mkdir()
        (self.root / "app/src/androidTest/helper.txt").write_text("not shipped in the AAB")
        self.assertTrue((self.snapshot() / "manifest.json").is_file())

    def test_unsigned_and_dev_bundles_are_rejected(self):
        for kwargs in ({"signed": False}, {"package": "io.github.nicholasarda.keysnap.dev"}):
            bundle(self.aab, **kwargs)
            with self.assertRaisesRegex(ValueError, "Play-package"):
                self.snapshot()

    def test_archived_bundle_and_refs_cannot_be_silently_overwritten(self):
        directory = self.snapshot()
        original = (directory / "manifest.json").read_bytes()
        with self.assertRaises(ValueError):
            self.snapshot()
        self.assertEqual(original, (directory / "manifest.json").read_bytes())

    def test_mark_live_requires_publication_confirmation_and_intact_archive(self):
        directory = self.snapshot()
        with self.assertRaisesRegex(ValueError, "confirms publication"):
            release.mark_live(self.root, directory, False)
        (directory / "app.aab").write_bytes(b"changed after freeze")
        with self.assertRaisesRegex(ValueError, "integrity"):
            release.mark_live(self.root, directory, True)
        self.assertEqual("", release.git(self.root, "branch", "--list", "stable"))

    def test_mark_live_refuses_downgrade(self):
        old = self.snapshot()
        self.config.write_text('versionCode = 17\nversionName = "1.1.7"\n')
        new_sha = save_commit(self.root)
        bundle(self.aab, "1.1.7", 17)
        new = self.snapshot()
        release.mark_live(self.root, new, True)
        with self.assertRaisesRegex(ValueError, "backwards"):
            release.mark_live(self.root, old, True)
        self.assertEqual(new_sha, release.commit(self.root, "stable"))

    def test_checked_out_stable_branch_is_never_moved(self):
        directory = self.snapshot()
        release.git(self.root, "checkout", "-qb", "stable")
        with self.assertRaisesRegex(ValueError, "checked-out"):
            release.mark_live(self.root, directory, True)
        self.assertEqual("", release.git(self.pro, "branch", "--list", "stable"))

    def test_truncated_manifest_is_rejected(self):
        with self.assertRaises(ValueError):
            list(release.fields(b"\x0a\x05a"))


if __name__ == "__main__":
    unittest.main()
