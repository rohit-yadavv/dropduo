"""Check this run's platform assets and write SHA256SUMS and release notes; no publishing."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
out = root / "dist/release"
tag, mode = os.environ["TAG"], os.environ["MODE"]
metadata = [json.loads(path.read_text()) for path in sorted(out.glob("*.json"))]
if sorted(m['platform'] for m in metadata) != ["android", "macos"]:
    raise SystemExit("Expected exactly one Mac and one Android asset.")
checksums = []
source_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
for item in metadata:
    if 'v' + item['version'] != tag or item['mode'] != mode:
        raise SystemExit("Artifact version/mode differs from the release.")
    if item.get('dirty') is not False or item['commit'] != source_commit:
        raise SystemExit("Artifacts must come from the clean tagged source commit.")
    # The notes and the website name these files, so each must have its platform's exact name for this version.
    expected = {"macos": f"dropduo-mac-{tag}.zip", "android": f"dropduo-android-{tag}.apk"}[item['platform']]
    path = out / item['artifact']
    if item['artifact'] != expected or not path.is_file():
        raise SystemExit(f"Expected {expected}, found {item['artifact']}.")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != item['sha256']:
        raise SystemExit("Artifact checksum differs from its build metadata.")
    checksums.append(f"{digest}  {path.name}\n")
if len({m['commit'] for m in metadata}) != 1:
    raise SystemExit("Platform artifacts were built from different commits.")
if len({m.get('build') for m in metadata}) != 1:
    raise SystemExit("Platform artifacts have different build numbers.")
if {path.name for path in out.glob('appcast-*.xml')} != {'appcast-mac.xml'}:
    raise SystemExit("Releases require the signed Mac update feed.")
for item in metadata:
    if item['platform'] != 'macos':
        continue
    feed = ET.parse(out / 'appcast-mac.xml').getroot().find('channel/item')
    sparkle = '{http://www.andymatuschak.org/xml-namespaces/sparkle}'
    if feed is None or feed.find(sparkle + 'version').text != str(item['build']) or feed.find(sparkle + 'shortVersionString').text != item['version']:
        raise SystemExit("Mac update feed version differs from the archive.")
    enclosure = feed.find('enclosure')
    expected_url = f"https://github.com/rohit-yadavv/dropduo/releases/download/{tag}/{item['artifact']}"
    if enclosure is None or enclosure.get('url') != expected_url or enclosure.get('length') != str((out / item['artifact']).stat().st_size) or not enclosure.get(sparkle + 'edSignature'):
        raise SystemExit("Mac update feed does not match its archive.")
(out / "SHA256SUMS").write_text(''.join(checksums))
status = ("Development release: the Mac app is signed with DropDuo's own certificate and not notarized by Apple. Android APK is debug-signed. "
          "Android debug certificates can change between runs; export received files before uninstalling a previous build. "
          "Do not present these downloads as production-signed software.") if mode == "development" else (
          "Mac apps use Developer ID signing and Apple notarization. Android APK uses the maintainer's distribution key.")
version = tag[1:]
# GitHub appends the merged pull requests when the release is published.
notes = "**Download DropDuo: [rohit-yadavv.github.io/dropduo](https://rohit-yadavv.github.io/dropduo/#download)**\n\n"
notes += f"{status}\n\n"
notes += f"Or pick a file below: `dropduo-mac-v{version}.zip` for Macs with Apple silicon (M1 or newer) "
notes += f"and `dropduo-android-v{version}.apk` for Android 10+. "
notes += "Unzip and move DropDuo.app to Applications. Both devices need a reachable local network. "
notes += "The license is inside each download.\n\n"
notes += "Verify downloads with SHA256SUMS. Source commit: `" + metadata[0]['commit'] + "`.\n\n"
(root / "dist/release-notes.md").write_text(notes)
