"""Gather only expected platform assets from this release run; no publishing."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
out = root / "dist/release"
tag, mode = os.environ["TAG"], os.environ["MODE"]
metadata = [json.loads(path.read_text()) for path in sorted(out.glob("*.json"))]
expected = {("macos", "arm64"), ("macos", "x86_64"), ("android", "android")}
if len(metadata) != 3 or {(m['platform'], m['architecture']) for m in metadata} != expected:
    raise SystemExit("Expected exactly Apple Silicon, Intel and Android assets.")
checksums = []
source_commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
for item in metadata:
    if 'v' + item['version'] != tag or item['mode'] != mode:
        raise SystemExit("Artifact version/mode differs from the release.")
    if item.get('dirty') is not False or item['commit'] != source_commit:
        raise SystemExit("Artifacts must come from the clean tagged source commit.")
    path = out / item['artifact']
    if path.name != item['artifact'] or not path.is_file():
        raise SystemExit("Invalid artifact name.")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != item['sha256']:
        raise SystemExit("Artifact checksum differs from its build metadata.")
    checksums.append(f"{digest}  {path.name}\n")
if len({m['commit'] for m in metadata}) != 1:
    raise SystemExit("Platform artifacts were built from different commits.")
(out / "SHA256SUMS").write_text(''.join(checksums))
for name in ("LICENSE", "NOTICE"):
    shutil.copy2(root / name, out / name)
shutil.copy2(root / "docs/dependencies.md", out / "DEPENDENCIES.md")
# An optional CHANGELOG.md section for this version leads the notes; GitHub appends the merged pull requests.
changelog = (root / "CHANGELOG.md").read_text()
heading = "## " + tag[1:] + "\n"
section = changelog.split(heading, 1)[1].split("\n## ", 1)[0].strip() + "\n\n" if heading in changelog else ""
status = ("Development prerelease: Mac apps are ad-hoc signed and not notarized. Android APK is debug-signed. "
          "Android debug certificates can change between runs; export received files before uninstalling a previous build. "
          "Do not present these downloads as production-signed software.") if mode == "development" else (
          "Mac apps use Developer ID signing and Apple notarization. Android APK uses the maintainer's distribution key.")
notes = f"{section}{status}\n\nChoose the arm64 ZIP for Apple Silicon Macs or x86_64 ZIP for Intel Macs. "
notes += "Unzip and move DropDuo.app to Applications. Install the Android APK on Android 10+. Both devices need a reachable local network.\n\n"
notes += "Verify downloads with SHA256SUMS. Source commit: `" + metadata[0]['commit'] + "`.\n\n"
notes += "Setup and troubleshooting: [install and use DropDuo](https://github.com/rohit-yadavv/dropduo/blob/" + tag + "/docs/usage.md).\n"
(root / "dist/release-notes.md").write_text(notes)
