<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="apps/web/assets/brand/dropduo-wordmark-dark.png">
  <img src="apps/web/assets/brand/dropduo-wordmark-light.png" alt="DropDuo" width="360">
</picture>

### Mac to Android. Android to Mac.

Files, folders, photos, links and text, straight across your local network. Pair once. No account, no cloud.

[![Download for Mac](https://img.shields.io/badge/Download_for-Mac-2558DD?style=for-the-badge&logo=apple&logoColor=white&labelColor=555)](https://github.com/rohit-yadavv/dropduo/releases/latest)
[![Download for Android](https://img.shields.io/badge/Download_for-Android-2558DD?style=for-the-badge&logo=android&logoColor=white&labelColor=555)](https://github.com/rohit-yadavv/dropduo/releases/latest)

[![Latest release](https://img.shields.io/github/v/release/rohit-yadavv/dropduo?label=release&color=2558DD)](https://github.com/rohit-yadavv/dropduo/releases)
[![Downloads](https://img.shields.io/github/downloads/rohit-yadavv/dropduo/total?label=downloads&color=2ea44f)](https://github.com/rohit-yadavv/dropduo/releases)
![macOS 14+](https://img.shields.io/badge/macOS-14%2B-111?logo=apple&logoColor=white)
![Android 10+](https://img.shields.io/badge/Android-10%2B-111?logo=android&logoColor=3DDC84)
[![MIT license](https://img.shields.io/badge/license-MIT-555)](LICENSE)

[![DropDuo demo: a photo dropped on the Mac lands on an Android phone](apps/web/assets/video/dropduo-demo.jpg)](https://rohit-yadavv.github.io/dropduo/#demo)

</div>

## Why DropDuo

- **Original quality.** Photos and videos arrive as the original files, not compressed copies.
- **Folders stay folders.** Every subfolder and file comes across intact.
- **Picks up where it stopped.** Retry an interrupted transfer and it resumes. Files appear only after an integrity check.
- **Private by design.** Encrypted between paired devices, nothing leaves your network, and no ads or analytics.

## Download

> **Latest release:** [![Latest release](https://img.shields.io/github/v/release/rohit-yadavv/dropduo?label=&color=2558DD)](https://github.com/rohit-yadavv/dropduo/releases/latest). See the [release notes](https://github.com/rohit-yadavv/dropduo/releases/latest).

Grab both files from the [Releases page](https://github.com/rohit-yadavv/dropduo/releases/latest) (or the buttons above):

- **Mac:** unzip `dropduo-mac-v….zip` and drag **DropDuo** to Applications. After that, it checks for updates on its own.
- **Android:** open `dropduo-android-v….apk` on your phone and allow installing from your browser or Files app when asked.

First time opening it on your Mac? [Here’s how](https://rohit-yadavv.github.io/dropduo/mac-install.html). Prefer to build it yourself? See [Development](#development) below.

## Requirements

- A Mac with Apple silicon (M1 or newer) on **macOS 14** or later
- An Android phone on **Android 10** or later
- Both on the same local network
- To build from source: Swift 6, Java 17 and the Android SDK

## Get started

1. **Pair** once: choose **Pair a device** on the Mac, scan the QR code with your phone, then approve it on the Mac.
2. **Send.** On the Mac, drop files onto the app, right-click in Finder and choose **Send with DropDuo**, or use the menu bar icon. On Android, use **Share → DropDuo**.

Keep the Mac awake to receive. On Android, photos and videos go to your gallery, and other files to **Downloads › DropDuo**.

The protocol hasn't had an independent security audit. Please report security issues privately through GitHub rather than in a public issue.

## Development

Bug reports and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) to set up, run the checks and send a change.

```sh
./scripts/check all   # Swift and Android tests, both app builds, interop
./scripts/build-macos && open dist/DropDuo.app
```

## License

[MIT](LICENSE) © Rohit Yadav
