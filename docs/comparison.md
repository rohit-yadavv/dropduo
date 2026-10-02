# Choosing a sharing app

DropDuo is for people who use a Mac and an Android phone every day and want a dedicated native sharing workflow. Install both apps, approve a pairing once, and send files, text, or links between those trusted devices on your local network.

The practical reason to try it is its combination of remembered pairing, Mac menu-bar access, Android Share integration, and a small feature set. These are product choices, not proof of superior performance or security.

## Which tool fits your situation?

| Your main need | Consider | Why |
| --- | --- | --- |
| Repeated sharing between your own Mac and Android, with installed native apps | DropDuo | The entire v1 scope serves that device combination. You can try the alpha and help shape the experience. |
| Sharing across several operating systems | [LocalSend](https://localsend.org/) | Its official platform list includes Windows, macOS, Linux, Android, and iOS. |
| Sharing without installing a phone app | [Flitdrop](https://flitdrop.com/) | Its documented phone workflow uses a browser, paired with a desktop app. |
| Files plus broader phone/computer integrations | [KDE Connect](https://kdeconnect.kde.org/) | It offers sharing alongside features such as notifications, remote commands, and media controls; availability varies by platform. |
| Files accessible later while the original device is offline | A cloud storage or backup service | DropDuo requires both devices to be reachable and doesn't store a server copy. |

## DropDuo and LocalSend

LocalSend already provides encrypted local sharing without an account. Privacy, offline local transfer, and open source are therefore common ground. It also covers more platforms than DropDuo. [Official features](https://localsend.org/).

DropDuo narrows the product to your Mac and Android phone, with an explicit QR pairing/approval flow and remembered credentials. Its interfaces are built separately with SwiftUI and Jetpack Compose. That implementation choice lets us develop around each platform's controls; it doesn't establish that another app's interface is worse. LocalSend's repository documents its Flutter-based app. [LocalSend source and build information](https://github.com/localsend/localsend).

Choose DropDuo to explore this focused workflow. Choose LocalSend when its broader platform support is what you need. We haven't measured a head-to-head speed, reliability, or battery advantage.

## DropDuo and Flitdrop

Flitdrop documents QR pairing, local encrypted transfer, interrupted-transfer resume, and a phone browser that needs no installation. Pairing and resume aren't DropDuo exclusives. [Official workflow and features](https://flitdrop.com/).

DropDuo's difference is its installed Android app: share from another app through **Share → DropDuo**, use the native file picker, and find received items in **Recent**. There is an installation and permission setup step on the phone. Browser convenience and an installed app's integration are different tradeoffs; neither guarantees better reliability under every OS restriction.

## DropDuo and KDE Connect

KDE Connect offers a broad set of device integrations beyond file/link sharing, including notifications and remote-control features. [Official feature overview](https://kdeconnect.kde.org/).

DropDuo's v1 scope stops at files, explicitly shared text/links, trusted devices, and transfer controls. Choose it if that small scope suits your task. If you need notification mirroring, a remote trackpad, media control, or commands, explore a tool that implements those features.

## What we can claim today

- Native Mac and Android apps are implemented, with an Android Share target and Mac menu-bar access.
- Pairing requires Mac approval and is remembered across restarts.
- Transfers use authenticated encryption; completed files are integrity-checked.
- An interrupted file can be manually retried using matching retained partial data while its source remains available.
- No account, cloud file storage, ads, or analytics are included.

DropDuo is an early alpha. Automated build/core/interoperability checks pass, but physical-phone LAN behavior, camera scanning, extended background receiving, accessibility, and distribution signing still need validation. See [current evidence](validation.md).

We don't claim fastest transfer speed, universal background reliability, stronger security than competitors, or feature parity across every operating system. There is no independent protocol audit yet, and the v1 protocol has no forward secrecy. See [security design](security.md).

## Keeping this comparison accurate

Competitor descriptions were checked against the primary sources linked above on **2026-10-02**. They describe documented product positioning, not hands-on benchmark results. Update the sources when products change; confirm a capability before describing it as exclusive.

[Get DropDuo](downloads.md) · [Send your first file](usage.md) · [FAQ](faq.md)
