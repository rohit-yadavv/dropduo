# DropDuo

<img src="apps/web/assets/brand/dropduo-icon.png" alt="DropDuo icon" width="96" height="96">

**Share files, photos, text, and links between your Mac and Android phone.**

Pair once, then send over your local network. Free and open source, with no account, cloud uploads, ads, or analytics.

[Download](https://rohit-yadavv.github.io/dropduo/#download) · [Releases](https://github.com/rohit-yadavv/dropduo/releases)

[![DropDuo demo: a photo dropped on the Mac lands on an Android phone](apps/web/assets/video/dropduo-demo.jpg)](https://rohit-yadavv.github.io/dropduo/#demo)

## Get started

1. Install on both devices: **macOS 14+** (Apple silicon) and **Android 10+**.
2. Connect to the same local network. Scan the Mac's pairing code on Android and approve on the Mac.
3. Drop files or folders into the Mac app, right-click them in Finder and choose **Send with DropDuo**, or use **Send Files…** from its menu bar icon. On Android, use **Share → DropDuo**. You can also send text and links.

If macOS blocks the first launch, open **System Settings → Privacy & Security** and click **Open Anyway** ([more help](https://rohit-yadavv.github.io/dropduo/mac-install.html)). Keep the Mac awake to receive. On Android, received photos and videos go to your gallery, and other files to **Downloads › DropDuo**.

Transfers are encrypted between paired devices and files are integrity-checked. The protocol hasn't had an independent security audit. Please report security issues privately through GitHub rather than in a public issue.

## Development

Bug reports and pull requests are welcome; keep private files and pairing codes out of issues. Building needs macOS 14+ with Swift 6, Java 17 and the Android SDK (`ANDROID_HOME`).

```sh
./scripts/check all   # Swift and Android tests, both app builds, interop
./scripts/build-macos && open dist/DropDuo.app
```

Work happens on `develop`; `master` is what's released, through **Actions → Release**. Commits look like `fix: short description`; `git config core.hooksPath .githooks` checks them locally.

Licensed under [Apache-2.0](LICENSE).
