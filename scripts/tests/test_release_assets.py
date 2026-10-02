import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class ReleaseAssetTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        (self.root / "scripts").mkdir()
        shutil.copy2(Path(__file__).parents[1] / "release-notes.py", self.root / "scripts/release-notes.py")
        (self.root / "docs").mkdir()
        for name in ("LICENSE", "NOTICE", "docs/dependencies.md"):
            (self.root / name).write_text("Test notice\n")
        (self.root / "CHANGELOG.md").write_text("## 0.1.0-alpha.1\n\n- Test release\n")
        (self.root / ".gitignore").write_text("dist/\n")
        for args in [("init", "-q"), ("add", "."), ("-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-qm", "test fixture")]:
            subprocess.run(["git", *args], cwd=self.root, check=True, capture_output=True)
        self.commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=self.root, text=True).strip()
        self.out = self.root / "dist/release"
        self.out.mkdir(parents=True)
        for name, target, arch in [("arm.zip", "macos", "arm64"), ("intel.zip", "macos", "x86_64"), ("android.apk", "android", "android")]:
            data = (target + arch).encode()
            (self.out / name).write_bytes(data)
            (self.out / (name + ".json")).write_text(json.dumps({"artifact": name, "version": "0.1.0-alpha.1", "mode": "development", "platform": target, "architecture": arch, "commit": self.commit, "dirty": False, "sha256": hashlib.sha256(data).hexdigest()}))

    def assemble(self):
        return subprocess.run(["python3", "scripts/release-notes.py"], cwd=self.root, env={**os.environ, "TAG": "v0.1.0-alpha.1", "MODE": "development"}, capture_output=True, text=True)

    def test_complete_assets_produce_checksums_and_explicit_development_notes(self):
        result = self.assemble()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len((self.out / "SHA256SUMS").read_text().splitlines()), 3)
        self.assertIn("not notarized", (self.root / "dist/release-notes.md").read_text())

    def test_tampered_download_is_rejected(self):
        (self.out / "android.apk").write_bytes(b"modified")
        self.assertNotEqual(self.assemble().returncode, 0)

    def test_incomplete_platform_set_is_rejected(self):
        (self.out / "intel.zip.json").unlink()
        self.assertNotEqual(self.assemble().returncode, 0)

    def test_wrong_source_or_dirty_build_is_rejected(self):
        path = self.out / "arm.zip.json"
        original = json.loads(path.read_text())
        for key, value in [("commit", "0" * 40), ("dirty", True), ("version", "0.2.0"), ("mode", "distribution")]:
            path.write_text(json.dumps({**original, key: value}))
            self.assertNotEqual(self.assemble().returncode, 0)
