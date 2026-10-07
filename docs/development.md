# Development

## Requirements

- macOS 14+, Swift 6, and a macOS SDK via Command Line Tools or Xcode. Full Xcode helps with debugging/distribution.
- Java 17; Android SDK 36 and build tools 36.0.0. Set `ANDROID_HOME` or ignored `apps/android/local.properties` (`sdk.dir=<absolute path>`). Scripts recognize the default macOS SDK location.
- Python 3; Android SDK `adb` on PATH for emulator checks.

Gradle/AGP/Kotlin pins live in the build files. Android min SDK is 29; target/compile SDK is 36. Verify both protocol implementations after dependency upgrades.

## Build and check

From the repository root:

```sh
./scripts/doctor
./scripts/check macos       # Swift core checks and app build
./scripts/check android     # JVM tests, APK build, lint
./scripts/interop           # Swift/JVM socket compatibility
./scripts/check all         # native checks, interoperability, website
./scripts/build-macos       # release config, ad-hoc signing
open dist/DropDuo.app
./apps/android/gradlew -p apps/android :app:assembleDebug
python3 -m unittest discover -s scripts/tests -v
```

Use `./scripts/build-macos debug` for a debug bundle. Android APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk`.

`./scripts/check-emulator` requires a **disposable emulator**, refuses physical devices, installs test APKs, and checks the Android service/Keystore against a Swift host. It grants notification permission and uses synthetic pairing/files. Success cleans test state; failure may leave it. Camera and physical Wi-Fi discovery are not covered.

Read component `AGENTS.md`. Keep Swift/Kotlin core consistent with [the protocol](../protocol/SPEC.md) and fixtures. Never commit local SDK paths, user files, credentials, or signing keys. No release credentials are needed for development.

`version.properties` sets both app versions for local and test builds; releases get theirs from **Actions → Release**. See [releases](releases.md) and the [Git workflow](../CONTRIBUTING.md#git-workflow).

## What the checks cover

Automated: Swift/JVM crypto fixtures, replay and tamper rejection, malformed frames, path and size validation, and real loopback transfers both ways with resume, cancellation, duplicates, and empty or multi-chunk files.

Before a release, try on real devices: pairing approve/reject, forget, Share → DropDuo, menu bar Send Files…, notifications, text, app restart and login launch, Android background and screen lock, Mac sleep/wake, and Wi-Fi changes. Emulators don't prove vendor battery behavior or physical-LAN discovery.

## Development builds

For untagged builds, sign into GitHub, open a successful `develop` run in [Checks](https://github.com/rohit-yadavv/dropduo/actions/workflows/checks.yml), and download `dropduo-macos-arm64-development`, `dropduo-macos-x86_64-development`, or `dropduo-android-development`. Mac artifacts contain a ZIP holding the app; Android contains an APK. Artifacts expire after 30 days and need repository read access.

## Website and branding

```sh
./scripts/check web
python3 -m http.server 4173 -d dist/web
```

The [static website](../apps/web/README.md) has no build dependencies and reads GitHub release assets. `web.yml` deploys `master`; configure **Settings → Pages → Source → GitHub Actions** first.

After editing `branding/mark.json`, run `./scripts/generate-branding` on macOS and review the committed exports. See [branding](../branding/README.md).
