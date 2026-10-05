# Download and install

**[0.1.0-alpha.1](https://github.com/rohit-yadavv/dropduo/releases/tag/v0.1.0-alpha.1)** is a free public development alpha. Requires **macOS 14+** and **Android 10+**. After installing both apps, [pair and share](usage.md) over a reachable local network.

| Device | Download |
| --- | --- |
| Apple Silicon (M1 or newer) | [Mac ZIP](https://github.com/rohit-yadavv/dropduo/releases/download/v0.1.0-alpha.1/DropDuo-v0.1.0-alpha.1-macos-arm64-development.zip) |
| Intel Mac | [Mac ZIP](https://github.com/rohit-yadavv/dropduo/releases/download/v0.1.0-alpha.1/DropDuo-v0.1.0-alpha.1-macos-x86_64-development.zip) |
| Android | [APK](https://github.com/rohit-yadavv/dropduo/releases/download/v0.1.0-alpha.1/DropDuo-v0.1.0-alpha.1-android-development.apk) |

No GitHub account is needed for [published release assets](https://github.com/rohit-yadavv/dropduo/releases). Download from **Assets**, not **Source code** (which contains no installed apps). Mac filenames use `-macos-arm64` or `-macos-x86_64`; Android uses `-android`. Development builds add `-development` before the extension.

## Install

### Mac: first launch

The current Mac alpha is ad-hoc signed, without Apple Developer ID signing or notarization. macOS may block its first launch.

1. Extract the ZIP and move `DropDuo.app` into **Applications**.
2. Try opening it once. If macOS says the developer cannot be verified or Apple cannot check it for malicious software, dismiss the alert.
3. Open **System Settings → Privacy & Security**, scroll to **Security**, and look for DropDuo.
4. If you trust the download, click **Open Anyway** when available, authenticate if asked, then click **Open**. macOS remembers the exception.

The website's **Mac won't open it? First-launch steps** link opens a dedicated help page. Its **Open Privacy & Security on Mac** button opens settings; it cannot approve the app. Your browser may ask permission. If it fails, follow the manual path above. **Open Anyway** requires a blocked launch and may be unavailable on a managed Mac. For a “will damage your computer” or “damaged” alert, stop and [report it](https://github.com/rohit-yadavv/dropduo/issues). [Apple's guide](https://support.apple.com/en-us/102445).

### Android

Open the APK and allow installation from that browser/file manager when prompted. This is a direct download, not a Play Store app. Open DropDuo and allow its connection permissions.

Development signing keys may change and prevent updates. **Save a copy** of important received files before uninstalling; uninstall removes app storage and pairing. Distribution updates must keep the same signing key and increase `versionCode`.

## Public code and checksums

Anyone can [inspect the source](https://github.com/rohit-yadavv/dropduo), [build it](development.md), or [contribute](../CONTRIBUTING.md). DropDuo uses Apache-2.0, with no accounts, cloud uploads, ads, or analytics. Open source does not replace Apple notarization or an independent security audit; this early alpha has not had one. See [validation](validation.md).

Releases include checksums, build metadata, and license/dependency notices. To verify a single asset, download its `.sha256` file and run `shasum -a 256 -c <asset>.sha256` in the same folder. With all three assets present, use `shasum -a 256 -c SHA256SUMS`. Checksums confirm a match to the published asset.

## Development downloads available now

For newer untagged builds, sign into GitHub, open a successful `develop` run in [Checks](https://github.com/rohit-yadavv/dropduo/actions/workflows/checks.yml), and confirm its commit/date. Download an artifact:

- `dropduo-macos-arm64-development`
- `dropduo-macos-x86_64-development`
- `dropduo-android-development`

Extract the artifact ZIP; Mac artifacts contain another ZIP holding the app, Android an APK. Verify its checksum. Artifacts expire after 30 days and require repository read access; published releases are the normal download route.
