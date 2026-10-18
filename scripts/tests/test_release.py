import importlib.machinery
import importlib.util
from pathlib import Path
import unittest

loader = importlib.machinery.SourceFileLoader("release_check", str(Path(__file__).parents[1] / "check-release"))
spec = importlib.util.spec_from_loader(loader.name, loader)
release = importlib.util.module_from_spec(spec)
loader.exec_module(release)


class ReleaseVersionTests(unittest.TestCase):
    def test_next_version_follows_newest_tag(self):
        tags = ["v0.1.7", "v0.1.10", "v0.1.9", "not-a-version"]
        self.assertEqual(release.next_version(tags), "0.1.11")

    def test_prerelease_is_followed_by_its_release(self):
        self.assertEqual(release.next_version(["v0.1.8", "v0.2.0-beta.1"]), "0.2.0")
        self.assertEqual(release.next_version(["v0.1.0-alpha.6", "v0.1.7", "v0.1.8"]), "0.1.9")

    def test_first_release(self):
        self.assertEqual(release.next_version([]), "0.1.0")

    def test_requested_version_must_be_valid_and_newer(self):
        tags = ["v0.1.0-alpha.5"]
        self.assertEqual(release.next_version(tags, "0.1.0"), "0.1.0")
        self.assertEqual(release.next_version(tags, "0.1.0-beta.1"), "0.1.0-beta.1")
        self.assertEqual(release.next_version(tags, ""), "0.1.0")
        for requested in ("0.1.0-alpha.5", "0.1.0-alpha.4", "01.0.0", "1.0.0-alpha.0", "v0.1.0-alpha.6", "1.0"):
            with self.assertRaises(ValueError):
                release.next_version(tags, requested)

    def test_builds_are_unsigned_until_signing_is_configured(self):
        self.assertEqual(release.mode_for(False), "development")
        self.assertEqual(release.mode_for(True), "distribution")

    def test_bad_build_numbers_fail(self):
        for build in (0, -1, 2100000001):
            with self.assertRaises(ValueError):
                release.validate_build(build)
        self.assertEqual(release.validate_build(89), 89)


if __name__ == "__main__":
    unittest.main()
