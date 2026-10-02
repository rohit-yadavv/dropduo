import importlib.machinery
import importlib.util
from pathlib import Path
import tempfile
import unittest

loader = importlib.machinery.SourceFileLoader("release_check", str(Path(__file__).parents[1] / "check-release"))
spec = importlib.util.spec_from_loader(loader.name, loader)
release = importlib.util.module_from_spec(spec)
loader.exec_module(release)


class ReleaseGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def version(self, name, code="1", heading=None):
        (self.root / "version.properties").write_text(f"versionName={name}\nversionCode={code}\n")
        (self.root / "CHANGELOG.md").write_text(heading if heading is not None else f"# Changelog\n\n## {name}\n")

    def test_alpha_allows_development_or_distribution(self):
        self.version("0.1.0-alpha.1")
        for mode in ("development", "distribution"):
            self.assertTrue(release.validate("v0.1.0-alpha.1", mode, self.root))

    def test_stable_cannot_publish_development_builds(self):
        self.version("1.0.0")
        with self.assertRaisesRegex(ValueError, "distribution signing"):
            release.validate("v1.0.0", "development", self.root)
        self.assertFalse(release.validate("v1.0.0", "distribution", self.root))

    def test_tag_mismatch_and_invalid_versions_fail(self):
        for name, tag in [("0.1.0", "v0.2.0"), ("01.0.0", "v01.0.0"), ("1.0.0-alpha.0", "v1.0.0-alpha.0")]:
            self.version(name)
            with self.assertRaises(ValueError):
                release.validate(tag, "distribution", self.root)

    def test_unreleased_heading_is_not_a_release(self):
        self.version("0.1.0-alpha.1", heading="# Changelog\n\n## Unreleased — 0.1.0-alpha.1\n")
        with self.assertRaisesRegex(ValueError, "changelog"):
            release.validate("v0.1.0-alpha.1", "development", self.root)

    def test_bad_build_numbers_fail(self):
        for code in ("0", "-1", "abc", "2100000001"):
            self.version("1.0.0", code)
            with self.assertRaises(ValueError):
                release.validate("v1.0.0", "distribution", self.root)


if __name__ == "__main__":
    unittest.main()
