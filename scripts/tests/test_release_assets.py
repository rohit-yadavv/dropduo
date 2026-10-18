import hashlib
import base64
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("release_feed", Path(__file__).parents[1] / "update-feed.py")
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)


class ReleaseAssetTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        (self.root / "scripts").mkdir()
        shutil.copy2(Path(__file__).parents[1] / "release-notes.py", self.root / "scripts/release-notes.py")
        for name in ("LICENSE",):
            (self.root / name).write_text("Test notice\n")
        (self.root / ".gitignore").write_text("dist/\n")
        for args in [("init", "-q"), ("add", "."), ("-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-qm", "test fixture")]:
            subprocess.run(["git", *args], cwd=self.root, check=True, capture_output=True)
        self.commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=self.root, text=True).strip()
        self.out = self.root / "dist/release"
        self.out.mkdir(parents=True)
        for name, target in [("dropduo-mac-v0.1.0-alpha.1.zip", "macos"), ("dropduo-android-v0.1.0-alpha.1.apk", "android")]:
            data = target.encode()
            (self.out / name).write_bytes(data)
            item = {"artifact": name, "version": "0.1.0-alpha.1", "build": 100, "mode": "development", "platform": target, "commit": self.commit, "dirty": False, "sha256": hashlib.sha256(data).hexdigest()}
            (self.out / (name + ".json")).write_text(json.dumps(item))
        mac = dict(version="0.1.0-alpha.1", build=100, platform="macos", artifact="dropduo-mac-v0.1.0-alpha.1.zip")
        feed, xml = generator.make_feed(mac, len(b"macos"), base64.b64encode(bytes(64)).decode())
        (self.out / feed).write_bytes(xml)

    def assemble(self, mode="development"):
        return subprocess.run(["python3", "scripts/release-notes.py"], cwd=self.root, env={**os.environ, "TAG": "v0.1.0-alpha.1", "MODE": mode}, capture_output=True, text=True)

    def test_complete_assets_produce_checksums_and_explicit_development_notes(self):
        result = self.assemble()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len((self.out / "SHA256SUMS").read_text().splitlines()), 2)
        notes = (self.root / "dist/release-notes.md").read_text()
        self.assertIn("not notarized", notes)
        self.assertTrue(notes.startswith("**Download DropDuo: [rohit-yadavv.github.io/dropduo]"))
        # License files ship inside the downloads, not as separate release files.
        self.assertFalse((self.out / "LICENSE").exists())

    def test_tampered_download_is_rejected(self):
        (self.out / "dropduo-android-v0.1.0-alpha.1.apk").write_bytes(b"modified")
        self.assertNotEqual(self.assemble().returncode, 0)

    def test_download_with_another_name_is_rejected(self):
        path = self.out / "dropduo-android-v0.1.0-alpha.1.apk.json"
        item = json.loads(path.read_text())
        (self.out / item['artifact']).rename(self.out / "android.apk")
        path.write_text(json.dumps({**item, "artifact": "android.apk"}))
        self.assertNotEqual(self.assemble().returncode, 0)

    def test_incomplete_platform_set_is_rejected(self):
        (self.out / "dropduo-mac-v0.1.0-alpha.1.zip.json").unlink()
        self.assertNotEqual(self.assemble().returncode, 0)

    def test_wrong_source_or_dirty_build_is_rejected(self):
        path = self.out / "dropduo-mac-v0.1.0-alpha.1.zip.json"
        original = json.loads(path.read_text())
        for key, value in [("commit", "0" * 40), ("dirty", True), ("version", "0.2.0"), ("mode", "distribution")]:
            path.write_text(json.dumps({**original, key: value}))
            self.assertNotEqual(self.assemble().returncode, 0)

    def test_update_feed_must_match_the_mac_download(self):
        feed = self.out / "appcast-mac.xml"
        xml = feed.read_bytes()
        feed.unlink()
        self.assertNotEqual(self.assemble().returncode, 0)
        feed.write_bytes(xml.replace(b'>100<', b'>101<'))
        self.assertNotEqual(self.assemble().returncode, 0)

    def test_mismatched_build_numbers_are_rejected(self):
        path = self.out / 'dropduo-android-v0.1.0-alpha.1.apk.json'
        item = json.loads(path.read_text())
        item['build'] = 123
        path.write_text(json.dumps(item))
        self.assertNotEqual(self.assemble().returncode, 0)
