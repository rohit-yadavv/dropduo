import base64
import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("update_feed", Path(__file__).parents[1] / "update-feed.py")
feed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(feed)


class UpdateFeedTests(unittest.TestCase):
    def metadata(self, architecture="arm64", chip="applesilicon"):
        return dict(version="0.2.0", build=100, architecture=architecture, platform="macos", mode="distribution", artifact=f"dropduo-mac-{chip}-v0.2.0.zip")

    def test_each_architecture_gets_its_own_signed_archive_and_build(self):
        signature = base64.b64encode(bytes(64)).decode()
        for arch, chip in [("arm64", "applesilicon"), ("x86_64", "intel")]:
            name, xml = feed.make_feed(self.metadata(arch, chip), 123, signature, "Fix <unsafe> & text")
            self.assertEqual(name, f"appcast-mac-{chip}.xml")
            item = ET.fromstring(xml).find("channel/item")
            self.assertEqual(item.find(f"{{{feed.SPARKLE}}}version").text, "100")
            self.assertEqual(item.find("enclosure").get("url"), f"{feed.REPOSITORY}/releases/download/v0.2.0/dropduo-mac-{chip}-v0.2.0.zip")
            self.assertEqual(item.find("enclosure").get(f"{{{feed.SPARKLE}}}edSignature"), signature)
            self.assertIn("&lt;unsafe&gt; &amp;", item.find("description").text)

    def test_development_builds_bad_names_numbers_and_signatures_are_rejected(self):
        original = self.metadata()
        for change in [dict(mode="development"), dict(platform="android"), dict(artifact="../evil.zip"), dict(build=0), dict(build=True), dict(build=2_100_000_001)]:
            with self.assertRaises(ValueError):
                feed.make_feed({**original, **change}, 123, base64.b64encode(bytes(64)).decode(), "notes")
        for signature in ["bad", base64.b64encode(bytes(32)).decode()]:
            with self.assertRaises(ValueError):
                feed.make_feed(original, 123, signature, "notes")
