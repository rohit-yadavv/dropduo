"""Use disposable keys to exercise the actual Sparkle signer and CryptoKit verification on Mac."""
import importlib.util
from pathlib import Path
import platform
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SIGNER = ROOT / "apps/macos/.build/artifacts/sparkle/Sparkle/bin/sign_update"
spec = importlib.util.spec_from_file_location("signing_feed", ROOT / "scripts/update-feed.py")
feed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(feed)


@unittest.skipUnless(platform.system() == "Darwin" and SIGNER.exists(), "Resolve Sparkle on Mac to run actual signing checks")
class UpdateSigningTests(unittest.TestCase):
    def test_archive_and_feed_signatures_reject_tampering(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            source = work / "keys.swift"
            source.write_text('''import CryptoKit
import Foundation
let key = Curve25519.Signing.PrivateKey()
let root = URL(fileURLWithPath: CommandLine.arguments[1])
try key.rawRepresentation.base64EncodedString().write(to: root.appendingPathComponent("private"), atomically: true, encoding: .utf8)
try key.publicKey.rawRepresentation.base64EncodedString().write(to: root.appendingPathComponent("public"), atomically: true, encoding: .utf8)
''')
            subprocess.run(["swift", str(source), str(work)], check=True, capture_output=True)
            private_key = (work / "private").read_text()
            public_key = (work / "public").read_text()
            artifact = work / "archive.zip"
            artifact.write_bytes(b"disposable update test bytes")
            signed = subprocess.run([str(SIGNER), "--ed-key-file", "-", "-p", str(artifact)], input=private_key, text=True, capture_output=True)
            self.assertEqual(signed.returncode, 0, "Sparkle signing failed")
            signature = signed.stdout.strip()
            command = ["swift", str(ROOT / "scripts/verify-update.swift"), str(artifact), public_key, signature]
            self.assertEqual(subprocess.run(command, capture_output=True).returncode, 0)
            metadata = dict(version="0.2.0", build=100, architecture="arm64", platform="macos", mode="distribution", artifact="dropduo-mac-applesilicon-v0.2.0.zip")
            name, xml = feed.make_feed(metadata, artifact.stat().st_size, signature, "notes")
            appcast = work / name
            appcast.write_bytes(xml)
            sign_feed = subprocess.run([str(SIGNER), "--ed-key-file", "-", str(appcast)], input=private_key, text=True, capture_output=True)
            self.assertEqual(sign_feed.returncode, 0, "Feed signing failed")
            verify_feed = [str(SIGNER), "--verify", "--ed-key-file", "-", str(appcast)]
            self.assertEqual(subprocess.run(verify_feed, input=private_key, text=True, capture_output=True).returncode, 0)
            appcast.write_bytes(appcast.read_bytes().replace(b"<title>DropDuo updates</title>", b"<title>Tampered update</title>"))
            self.assertNotEqual(subprocess.run(verify_feed, input=private_key, text=True, capture_output=True).returncode, 0)
            artifact.write_bytes(b"tampered update bytes")
            self.assertNotEqual(subprocess.run(command, capture_output=True).returncode, 0)
