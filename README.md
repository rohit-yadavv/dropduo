# DropDuo

<img src="branding/dropduo-icon.png" alt="DropDuo double-D icon" width="96" height="96">

**Share files between your Mac and Android. Pair once. Keep sharing.**

Move photos from your phone to your Mac, send a PDF back to your phone, or pass a link between them. DropDuo connects your own devices over your local network, with installed native apps and remembered pairing. No cable, account, or cloud upload required.

[Get the apps](docs/downloads.md) · [Send your first file](docs/usage.md) · [Compare alternatives](docs/comparison.md) · [FAQ](docs/faq.md) · [Contribute](CONTRIBUTING.md)

[![Checks](https://github.com/rohit-yadavv/dropduo/actions/workflows/checks.yml/badge.svg?branch=develop)](https://github.com/rohit-yadavv/dropduo/actions/workflows/checks.yml)
[![CodeQL](https://github.com/rohit-yadavv/dropduo/actions/workflows/security.yml/badge.svg?branch=develop)](https://github.com/rohit-yadavv/dropduo/actions/workflows/security.yml)

**Early development alpha.** macOS 14+ on Apple Silicon or Intel, and Android 10+. [The first public alpha](https://github.com/rohit-yadavv/dropduo/releases/tag/v0.1.0-alpha.1) is available for testers; choose your [Mac and Android downloads](docs/downloads.md). These builds use development signing. Physical-phone reliability, accessibility, production signing, and independent security review remain [release work](docs/validation.md).

## Why use DropDuo?

If you use a Mac and an Android phone every day, sending something between them should be a small task. DropDuo gives that task a dedicated place:

- **Keep your own devices connected.** Scan a code and approve the phone on your Mac once. Pairing survives app restarts; reconnect when both devices are available.
- **Share from where you are.** Choose DropDuo in Android's Share menu. On Mac, drop files into the app or use its menu-bar shortcut.
- **Keep the selected file intact.** DropDuo copies the supplied file bytes without resizing photos or re-encoding videos. It verifies integrity before marking a received file complete.
- **Transfer locally.** File transfers are authenticated and encrypted between paired devices. DropDuo doesn't upload your files to a cloud service, and sharing doesn't require internet access.
- **Know what happened.** See progress, cancel a transfer, find recent items, or manually retry an interrupted file from retained partial data.
- **Stay in control.** Pause receiving or forget a paired device. Send text and links deliberately; the app doesn't monitor your clipboard.

DropDuo is free and open source under Apache-2.0, with no ads or analytics. Its focus is everyday Mac–Android sharing.

## Everyday things you can do

| You have… | You want… | Use DropDuo to… |
| --- | --- | --- |
| Photos, a receipt, or a scan on your phone | Work with them on your Mac | Select them in the phone app that holds them, then choose **Share → DropDuo**. |
| A PDF or document on your Mac | Take it with you on your phone | Drop the file into DropDuo and send it to your paired phone. |
| A video on either device | Copy it to the other device | Choose the file and follow its transfer progress. |
| A link, address, or short note | Use it on the other device | Send it as text and select **Copy** on the receiver. |

## How it works

1. **Install and open both apps.** Connect your Mac and phone to the same local network, where devices can reach each other.
2. **Pair once.** On Mac, choose **Pair a device**. Scan the QR code with DropDuo on Android and approve the request on the Mac.
3. **Send either way.** Use Android's Share menu or file picker; use Mac drag and drop or **Choose files…**. Keep the Mac awake and the apps available during transfer.

On Mac, received files go into `~/Downloads/DropDuo/`, separated by paired device. On Android, open **Recent → Save a copy** to keep a received file in a folder you choose. Android's app storage is removed when you uninstall DropDuo.

[Full walkthrough](docs/usage.md) · [Connection troubleshooting](docs/troubleshooting.md)

## Why choose it over another sharing app?

Choose DropDuo if you want to try a focused, native Mac–Android workflow built around your paired devices. Other tools may be a better fit for other needs:

| App | Choose it when… | How DropDuo differs |
| --- | --- | --- |
| **DropDuo** | You want installed Mac and Android apps for repeated sharing between your own devices. | Remembered trust, Mac menu-bar access, Android Share integration, and a deliberately small sharing scope. Currently an early alpha. |
| [LocalSend](https://localsend.org/) | You need local sharing across Windows, macOS, Linux, Android, and iOS. | DropDuo targets only Mac and Android. LocalSend already offers encryption, local transfers, and no accounts; those are shared benefits, not DropDuo exclusives. |
| [Flitdrop](https://flitdrop.com/) | You prefer using your phone's browser without installing a phone app. | DropDuo installs on Android and exposes a system Share target. Both products offer pairing and local transfer; the phone experience is the distinction. |
| [KDE Connect](https://kdeconnect.kde.org/) | You want device integration beyond sharing, such as notifications or remote controls. | DropDuo focuses on files, text, and links. It doesn't include those additional integrations. |

We haven't established that DropDuo is faster, safer, or more reliable than these alternatives. Our reason to exist is the focused native workflow; measured comparisons and real-device testing are still ahead. See the [sourced comparison and tradeoffs](docs/comparison.md).

## What to know before trying the alpha

- **Local network only.** Both devices must be reachable. Guest Wi-Fi can block device-to-device traffic. There is no internet relay or offline delivery queue.
- **The apps must be available.** Mac sleep or quitting the app stops receiving. Android uses a foreground connection service with a notification; force-stop and battery policies can interrupt it.
- **Recent is a transfer log.** It keeps the latest 100 local entries. It isn't a backup, shared shelf, or promise that deleted files can be recovered later.
- **Text sharing is explicit.** Automatic clipboard synchronization isn't included. Neither are browser transfer, iPhone/Windows support, folder sync, or remote control.

[Downloads and installation limitations](docs/downloads.md) · [FAQ](docs/faq.md) · [Roadmap](docs/roadmap.md)

## Help make it better

Try a small file in both directions, then tell us where the experience was confusing or broke. [Report a bug](https://github.com/rohit-yadavv/dropduo/issues/new?template=bug.yml) with your OS versions, app build, and reproduction steps. Don't include private files or pairing codes.

Developers, designers, documentation writers, and real-device testers are welcome. Human and agent-assisted contributions use the same standards: start with [CONTRIBUTING.md](CONTRIBUTING.md), [AGENTS.md](AGENTS.md), and the [Git workflow](docs/git-workflow.md).

| Directory | Purpose |
| --- | --- |
| `apps/macos` | SwiftUI app, Swift protocol library, compatibility host |
| `apps/android` | Kotlin/Compose app, JVM protocol library, tests |
| `protocol` | Versioned wire specification and shared crypto fixtures |
| `docs` | User guides, product decisions, architecture, validation, releases |
| `scripts` | Repeatable checks, interoperability tests, packaging |
| `branding` | Editable icon geometry, palette, and platform exports |
| `website` | Static download website and its licensed assets |

For building from source, use [development setup](docs/development.md). For publishing downloads, use the [release guide](docs/releases.md). Find the other guides in the [documentation index](docs/README.md).

## License and security

Apache-2.0. See [LICENSE](LICENSE), [NOTICE](NOTICE), and [dependency notices](docs/dependencies.md). DropDuo is independently implemented and doesn't use LocalSend or Flitdrop code. Its documented protocol uses standard cryptographic primitives but hasn't received an independent security audit. Read the [security design](docs/security.md) and report vulnerabilities according to [SECURITY.md](SECURITY.md).
