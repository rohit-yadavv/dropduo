"""Generate and sign the architecture-specific Sparkle feed after packaging. Never publish or log keys."""
import base64
import hashlib
import html
import json
import os
import re
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

SPARKLE = "http://www.andymatuschak.org/xml-namespaces/sparkle"
REPOSITORY = "https://github.com/rohit-yadavv/dropduo"
ET.register_namespace("sparkle", SPARKLE)


def make_feed(metadata, size, signature, notes):
    chip = {"arm64": "applesilicon", "x86_64": "intel"}[metadata["architecture"]]
    version, build = metadata["version"], metadata["build"]
    if not re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(alpha|beta|rc)\.([1-9]\d*))?", version):
        raise ValueError("Invalid update version.")
    name = f"dropduo-mac-{chip}-v{version}.zip"
    if metadata["artifact"] != name or metadata["platform"] != "macos" or metadata["mode"] != "distribution":
        raise ValueError("Only versioned distribution Mac downloads can enter the update feed.")
    if not isinstance(build, int) or isinstance(build, bool) or not 1 <= build <= 2_100_000_000 or size <= 0:
        raise ValueError("Invalid update build or length.")
    if len(base64.b64decode(signature, validate=True)) != 64:
        raise ValueError("Invalid update signature.")
    root = ET.Element("rss", version="2.0")
    channel = ET.SubElement(root, "channel")
    ET.SubElement(channel, "title").text = "DropDuo updates"
    item = ET.SubElement(channel, "item")
    ET.SubElement(item, "title").text = "DropDuo " + version
    ET.SubElement(item, "link").text = f"{REPOSITORY}/releases/tag/v{version}"
    ET.SubElement(item, f"{{{SPARKLE}}}version").text = str(build)
    ET.SubElement(item, f"{{{SPARKLE}}}shortVersionString").text = version
    ET.SubElement(item, f"{{{SPARKLE}}}minimumSystemVersion").text = "14.0"
    ET.SubElement(item, "description").text = "<p>" + html.escape(notes).replace("\n", "<br>") + "</p>"
    ET.SubElement(item, "enclosure", {"url": f"{REPOSITORY}/releases/download/v{version}/{name}",
        "length": str(size), "type": "application/octet-stream", f"{{{SPARKLE}}}edSignature": signature})
    return f"appcast-mac-{chip}.xml", ET.tostring(root, encoding="utf-8", xml_declaration=True)


def main():
    root = Path(__file__).resolve().parent.parent
    out = root / "dist/release"
    metadata_files = sorted(out.glob("*.zip.json"))
    if len(metadata_files) != 1:
        raise SystemExit("Generate each Mac feed on its own architecture runner.")
    metadata = json.loads(metadata_files[0].read_text())
    artifact = out / metadata["artifact"]
    if artifact.name != metadata["artifact"] or hashlib.sha256(artifact.read_bytes()).hexdigest() != metadata["sha256"]:
        raise SystemExit("Invalid update archive.")
    private_key = os.environ.get("SPARKLE_PRIVATE_ED_KEY", "").strip()
    public_key = os.environ.get("DROPDUO_UPDATE_PUBLIC_KEY", "").strip()
    if not private_key or not public_key:
        raise SystemExit("Distribution updates require Sparkle signing keys.")
    signer = root / "apps/macos/.build/artifacts/sparkle/Sparkle/bin/sign_update"
    result = subprocess.run([str(signer), "--ed-key-file", "-", "-p", str(artifact)], input=private_key + "\n", text=True, capture_output=True)
    if result.returncode:
        raise SystemExit("Sparkle archive signing failed.")
    signature = result.stdout.strip()
    subprocess.run(["swift", str(root / "scripts/verify-update.swift"), str(artifact), public_key, signature], check=True)
    heading = "## " + metadata["version"] + "\n"
    changelog = (root / "CHANGELOG.md").read_text()
    notes = changelog.split(heading, 1)[1].split("\n## ", 1)[0].strip() if heading in changelog else "A new version of DropDuo is available."
    name, contents = make_feed(metadata, artifact.stat().st_size, signature, notes)
    path = out / name
    path.write_bytes(contents)
    result = subprocess.run([str(signer), "--ed-key-file", "-", str(path)], input=private_key + "\n", text=True, capture_output=True)
    if result.returncode:
        raise SystemExit("Sparkle feed signing failed.")
    print("Generated signed " + name)


if __name__ == "__main__":
    main()
