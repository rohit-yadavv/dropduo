# Download DropDuo

Install DropDuo on both your Mac and Android phone to share selected files, photos, videos, text, and links directly over your local network. Pair once; use Mac drag and drop or Android **Share → DropDuo** for later transfers. No account or cloud upload is required.

**Available now: development alpha builds for testers.** No public release has been published yet. Start with [development downloads](#development-downloads-available-now), then follow the [first-transfer walkthrough](usage.md). Read the [comparison](comparison.md) if you're deciding whether DropDuo fits your devices.

| Requirement | Supported |
| --- | --- |
| Mac | macOS 14+, Apple Silicon or Intel |
| Phone | Android 10+ |
| Connection | Same reachable local network; internet access is not required for sharing |

## Published versions

Use [GitHub Releases](https://github.com/rohit-yadavv/dropduo/releases). Published releases have downloadable Mac ZIPs and an Android APK under **Assets**. GitHub's **Source code** ZIP is for developers; it does not contain installed apps. No published version exists yet; use development downloads until the first release is published.

| Device | File |
| --- | --- |
| Apple Silicon Mac (M1 or newer) | `DropDuo-vVERSION-macos-arm64.zip` |
| Intel Mac | `DropDuo-vVERSION-macos-x86_64.zip` |
| Android 10+ | `DropDuo-vVERSION-android.apk` |

Development prereleases add `-development` before the extension. Release pages include `SHA256SUMS`, per-file checksums, build metadata, license and dependency notices. On a Mac, run `shasum -a 256 -c SHA256SUMS` in the folder containing all three downloads. To verify one download alone, use its `.sha256` file instead.

## Development downloads available now

1. Sign into GitHub and open [Checks](https://github.com/rohit-yadavv/dropduo/actions/workflows/checks.yml).
2. Choose a successful run for `develop`; check its commit and date.
3. Under **Artifacts**, select `dropduo-macos-arm64-development`, `dropduo-macos-x86_64-development`, or `dropduo-android-development`.
4. Extract GitHub's artifact ZIP. The Mac artifact contains another ZIP holding `DropDuo.app`; the Android artifact contains an APK. Verify the included checksum.

These artifacts expire after 30 days. Downloading workflow artifacts requires a signed-in account with repository read access ([GitHub documentation](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts)). Public release assets are the intended download route for everyday users.

## Install

**Mac:** requires macOS 14+. Extract the platform ZIP, move `DropDuo.app` into Applications, then open it. Current development builds use ad-hoc signing and have not been notarized. For a development build you trust and have verified, macOS may offer **System Settings → Privacy & Security → Open Anyway** after the first launch attempt. See [Apple's instructions](https://support.apple.com/en-us/102445). Signed distribution releases use Developer ID and notarization.

**Android:** download the APK onto your phone and open it. Android may ask you to allow installations from the browser or file manager you used. This is a direct APK download, not a Play Store installation. Open DropDuo, allow the requested device-connection permissions, and pair with your Mac on the same reachable local network. See [usage](usage.md).

**Development upgrades:** CI debug signing certificates can change between runs. Android may reject an update signed with a different certificate. Export received files with **Save a copy** before uninstalling; uninstall removes DropDuo's app storage and pairing. Public distribution releases must retain the same Android signing identity and increment `versionCode`. [Android signing documentation](https://developer.android.com/studio/publish/app-signing).
