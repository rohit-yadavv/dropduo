# DropDuo

<img src="branding/dropduo-icon.png" alt="DropDuo double-D icon" width="96" height="96">

Native, private sharing between your Mac and Android phone. Pair once; send files, photos, videos, links, and text over your local network.

**Status: working development alpha, not a signed public release.** Independently implemented; no Flitdrop or LocalSend code is used. No account, cloud file storage, advertising, or analytics. macOS 14+ and Android 10+.

## Try it

Prerequisites: macOS with Swift 6 and a macOS SDK, Java 17, Android SDK platform 36. See [development](docs/development.md) for setup.

```sh
./scripts/doctor
./scripts/check all
./scripts/build-macos
open dist/DropDuo.app
./apps/android/gradlew -p apps/android :app:assembleDebug
```

Install `apps/android/app/build/outputs/apk/debug/app-debug.apk` on your Android phone. Put both devices on the same reachable Wi-Fi, open DropDuo on the Mac, choose **Pair a device**, scan its QR code on Android, and approve the connection on the Mac. See [usage](docs/usage.md) and [troubleshooting](docs/troubleshooting.md).

## What works

- Persistent pairing, native file selection, Android system Share target, and Mac drag and drop.
- Authenticated, encrypted transfers in both directions; explicit text and link sharing.
- Transfer progress, cancellation, retained partial files, and manual file retry with resume.
- Recent transfers, receiving pause, device revocation, and Android connection notification.
- Mac menu bar access and local-network discovery to find a paired Mac after its address changes.

This is a transfer tool. History is local metadata, not a backup or a permanent shelf. Android files must be exported with **Save copy** to survive uninstall. Mac receiving requires the app running and the computer awake; Android uses a foreground service. Receiving does not bypass OS restrictions.

## Contribute, with or without an agent

Start with [AGENTS.md](AGENTS.md), [CONTRIBUTING.md](CONTRIBUTING.md), and [Git standards](docs/git-workflow.md). Component guides explain ownership and checks. Use the same commands as CI and report unavailable checks honestly.

| Directory | Purpose |
| --- | --- |
| `apps/macos` | SwiftUI app, Swift protocol library, compatibility host |
| `apps/android` | Kotlin/Compose app, JVM protocol library, tests |
| `protocol` | Versioned wire specification and shared crypto fixtures |
| `docs` | Product scope, architecture, decisions, usage, verification |
| `scripts` | Repeatable checks, interoperability tests, packaging |
| `branding` | Editable icon geometry, palette, SVG and platform export guide |

Read [validation evidence](docs/validation.md) before treating the alpha as release-ready. Physical-phone LAN, camera, sleep, accessibility, and battery behavior still need the [manual release matrix](docs/testing.md). The custom protocol has not received an independent security audit.

## License

Apache-2.0. See [LICENSE](LICENSE), [NOTICE](NOTICE), and [dependency notices](docs/dependencies.md). Report security issues according to [SECURITY.md](SECURITY.md).
