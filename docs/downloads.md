# Download DropDuo

Install DropDuo on both your Mac and Android phone to share selected files, photos, videos, text, and links directly over your local network. Pair once; use Mac drag and drop or Android **Share → DropDuo** for later transfers. No account or cloud upload is required.

**Available now: [0.1.0-alpha.1](https://github.com/rohit-yadavv/dropduo/releases/tag/v0.1.0-alpha.1), a public development prerelease for testers.** Download your Mac ZIP and Android APK below, then follow the [first-transfer walkthrough](usage.md). Read the [comparison](comparison.md) if you're deciding whether DropDuo fits your devices.

| Requirement | Supported |
| --- | --- |
| Mac | macOS 14+, Apple Silicon or Intel |
| Phone | Android 10+ |
| Connection | Same reachable local network; internet access is not required for sharing |

## Published versions

Use [GitHub Releases](https://github.com/rohit-yadavv/dropduo/releases). Published releases have downloadable Mac ZIPs and an Android APK under **Assets**. GitHub's **Source code** ZIP is for developers; it does not contain installed apps. The current public version is **0.1.0-alpha.1**, with development signing and outstanding physical-device validation. No GitHub account is needed to download its assets.

| Device | File |
| --- | --- |
| Apple Silicon Mac (M1 or newer) | [Download arm64 ZIP](https://github.com/rohit-yadavv/dropduo/releases/download/v0.1.0-alpha.1/DropDuo-v0.1.0-alpha.1-macos-arm64-development.zip) |
| Intel Mac | [Download Intel ZIP](https://github.com/rohit-yadavv/dropduo/releases/download/v0.1.0-alpha.1/DropDuo-v0.1.0-alpha.1-macos-x86_64-development.zip) |
| Android 10+ | [Download Android APK](https://github.com/rohit-yadavv/dropduo/releases/download/v0.1.0-alpha.1/DropDuo-v0.1.0-alpha.1-android-development.apk) |

These prerelease filenames include `-development` before the extension. Release pages include `SHA256SUMS`, per-file checksums, build metadata, license and dependency notices. On a Mac, run `shasum -a 256 -c SHA256SUMS` in the folder containing all three downloads. To verify one download alone, use its `.sha256` file instead.

## Development downloads available now

For newer untagged development builds, use CI artifacts instead of the public alpha:

1. Sign into GitHub and open [Checks](https://github.com/rohit-yadavv/dropduo/actions/workflows/checks.yml).
2. Choose a successful run for `develop`; check its commit and date.
3. Under **Artifacts**, select `dropduo-macos-arm64-development`, `dropduo-macos-x86_64-development`, or `dropduo-android-development`.
4. Extract GitHub's artifact ZIP. The Mac artifact contains another ZIP holding `DropDuo.app`; the Android artifact contains an APK. Verify the included checksum.

These artifacts expire after 30 days. Downloading workflow artifacts requires a signed-in account with repository read access ([GitHub documentation](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts)). Public release assets are the intended download route for everyday users.

## Install

### Mac: first launch

DropDuo is free to download. Requires macOS 14+. Current Mac alpha builds use ad-hoc signing: they are not signed with an Apple Developer ID and have not been notarized by Apple. macOS may block the first launch.

1. Download your platform ZIP from our GitHub Releases, extract it, and move `DropDuo.app` into **Applications**.
2. Try opening DropDuo once. If macOS says the developer cannot be verified or Apple cannot check it for malicious software, dismiss the alert.
3. Open **System Settings → Privacy & Security**, scroll to **Security**, and look for the message about DropDuo.
4. If you trust the download, choose **Open Anyway** when available, authenticate if asked, and click **Open** in the next prompt. macOS remembers this exception for later launches.

The download website includes an **Open Privacy & Security on Mac** button. Your browser may ask to open System Settings. If the button doesn't open the right pane, use the manual steps above. It only opens settings; you still decide whether to allow the app. **Open Anyway** requires a blocked launch attempt and may be unavailable on a managed Mac. If you see a “will damage your computer” or “damaged” alert, stop and [report the issue](https://github.com/rohit-yadavv/dropduo/issues) instead of using these steps. See [Apple's instructions](https://support.apple.com/en-us/102445).

### Public code and verifiable downloads

DropDuo is [open source](https://github.com/rohit-yadavv/dropduo) under Apache-2.0. Anyone can inspect the code, [build it themselves](development.md), report issues, or [propose improvements](../CONTRIBUTING.md). Transfers stay on your local network, with no account, cloud uploads, ads, or analytics. Download from our GitHub Releases and compare the included checksums using the commands above to check that the file matches the published asset.

Open source and checksums do not replace Apple notarization or an independent security review. This is an early alpha and has not had an independent security audit. Signed distribution releases use Developer ID and notarization.

**Android:** download the APK onto your phone and open it. Android may ask you to allow installations from the browser or file manager you used. This is a direct APK download, not a Play Store installation. Open DropDuo, allow the requested device-connection permissions, and pair with your Mac on the same reachable local network. See [usage](usage.md).

**Development upgrades:** CI debug signing certificates can change between runs. Android may reject an update signed with a different certificate. Export received files with **Save a copy** before uninstalling; uninstall removes DropDuo's app storage and pairing. Public distribution releases must retain the same Android signing identity and increment `versionCode`. [Android signing documentation](https://developer.android.com/studio/publish/app-signing).
