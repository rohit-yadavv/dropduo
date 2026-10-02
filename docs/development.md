# Development

## Prerequisites

- macOS 14+, Swift 6, and a macOS SDK. Install Apple's Command Line Tools or Xcode. The executable checks work with Command Line Tools; full Xcode is useful for debugging and distribution.
- Java 17. Android SDK platform 36 and build tools 36.0.0. Install with Android Studio's SDK Manager. Set `ANDROID_HOME`, or create ignored `apps/android/local.properties` with `sdk.dir=<absolute SDK path>`. The check script recognizes the default macOS SDK location.
- Python 3 for the disposable emulator ticket rewrite. Android SDK `adb` on PATH for emulator checks.

Pinned build components: Gradle 8.13 (checksum-verified wrapper), Android Gradle Plugin 8.12.0, Kotlin/Compose compiler 2.2.10. Android min SDK 29, target/compile SDK 36. See Gradle files for dependency pins. Do not upgrade them without verifying both protocol implementations.

## Commands

Run from the repository root:

```sh
./scripts/doctor                 # report prerequisites
./scripts/check macos            # Swift core assertions and app compile
./scripts/check android          # JVM tests, APK build, Android lint
./scripts/interop                # real Swift/JVM socket interoperability
./scripts/check all              # all the above
./scripts/build-macos            # release configuration; development ad-hoc signing
open dist/DropDuo.app
./apps/android/gradlew -p apps/android :app:assembleDebug
```

Use `./scripts/build-macos debug` for a debug app bundle. Install the Android debug APK from `apps/android/app/build/outputs/apk/debug/app-debug.apk` through your normal development workflow.

To verify the real Android service and Keystore, start a **disposable Android emulator**, then run `./scripts/check-emulator`. This installs debug and test APKs, grants notification permission, uses a synthetic pairing ticket, and transfers test files against a Swift host. It refuses physical devices. The test removes its pairing and received test files on success; a failed run may leave synthetic state. It does not test the camera or physical Wi-Fi discovery.

## Boundaries

Read component AGENTS.md before editing. Swift core and Kotlin core must agree with `protocol/SPEC.md` and shared fixtures. Never commit local SDK paths, received files, pairing credentials, or signing keys. Build output belongs in ignored directories.

No release credentials are needed for development. Distribution requires maintainer-owned signing and notarization, described in [releases](releases.md). GitHub workflows are included; verify their hosted results before release. Branch protection must be configured in GitHub; the workflow files alone do not enable it.

## Icon assets

The approved double-D icon uses ink, cobalt, and pure white. See [branding](../branding/README.md) for editable source and exports. Run `./scripts/generate-branding` on macOS to regenerate SVGs, Android vectors, Mac PNG resources, and ICNS after changing `branding/mark.json`. Exported assets are committed, so normal app builds need no graphics tooling.
