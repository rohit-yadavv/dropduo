# Install and use DropDuo

DropDuo needs **macOS 14+** (Apple Silicon or Intel) and **Android 10+** on the same local network. Internet access isn't required.

## Install

Download the [latest release](https://github.com/rohit-yadavv/dropduo/releases/latest) from **Assets** (not **Source code**):

- Apple Silicon Mac (M1 or newer): `-macos-arm64` ZIP
- Intel Mac: `-macos-x86_64` ZIP
- Android: `-android` APK

**Mac:** unzip and move `DropDuo.app` into **Applications**, then open it. If macOS says it can't verify the developer, dismiss the alert, open **System Settings → Privacy & Security**, and click **Open Anyway** next to DropDuo. macOS remembers this. If you see a "damaged" or "will damage your computer" alert, stop and [report it](https://github.com/rohit-yadavv/dropduo/issues). See [Apple's guide](https://support.apple.com/en-us/102445).

**Android:** open the APK and allow installs from your browser or file manager when asked. Open DropDuo and allow its connection and notification permissions.

To verify a download, get its `.sha256` file and run `shasum -a 256 -c <asset>.sha256` in the same folder.

## Pair once

1. On the Mac, choose **Pair a Device** (or **Show Pairing Code**). If several addresses are listed, pick your Wi-Fi one.
2. On Android, choose **Scan pairing code**, or paste the code copied from the Mac. Codes expire after five minutes.
3. Approve the phone on the Mac. Keep the code private.

Pairing survives restarts. A Mac can pair several phones; a phone pairs with one Mac.

## Send

- **Mac → Android:** drop files on the DropDuo menu bar icon or into the window, or use **Send Files…**. With several phones paired, pick the target under **Send To** in the menu bar menu.
- **Android → Mac:** in any app, **Share → DropDuo**, or use **Files** inside DropDuo.
- **Text and links:** type or paste into the text field and send (⌘Return on Mac). Android also accepts text shared from other apps. Choose **Copy** on the receiving side.

Files are copied as-is, with no resizing or re-encoding. DropDuo never reads your clipboard, syncs folders, or browses the other device.

## Received files

- **Mac:** saved to `~/Downloads/DropDuo/`, in a folder for each phone. A notification appears when something arrives: click a file to show it in Finder, or choose **Copy** for text.
- **Android:** kept in DropDuo's storage. Use **Open**, or **Save a copy** to keep it elsewhere. **Uninstalling DropDuo deletes these files**, so save important ones first.

Recent activity keeps the last 100 transfers. Clearing it doesn't delete files.

## Staying connected

DropDuo opens at login on the Mac after you pair, and keeps running in the menu bar when you close its window. Turn this off under **DropDuo → Settings…**. The Mac can't receive while it sleeps.

If a connection drops mid-file, the transfer resumes when the devices reconnect. For other failures, choose **Retry** on the sender while the original file still exists. **Cancel** removes partial data.

To pause receiving without unpairing, use **Accept files and text from paired devices** in Mac Settings, or **Accept files and text** in Android Settings. **Forget** removes the pairing on that device; forget it on both to pair again from scratch.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Phone or Mac "not reachable" | Both on the same Wi-Fi; Mac awake with DropDuo running; DropDuo open on the phone. Allow DropDuo through the Mac firewall and local-network permission. |
| Pairing doesn't connect | Code under five minutes old; the QR uses the Mac's current Wi-Fi address; approve on the Mac. |
| Works at home, not on guest or office Wi-Fi | Some networks block devices from talking to each other. Use a network that allows it, such as your phone's hotspot. |
| Android stops receiving | Reopen DropDuo. Force-stop, battery savers, or revoked permissions can stop it in the background. |
| Mac doesn't open DropDuo at login | Allow it in **System Settings → General → Login Items**, and run it from **Applications**. |
| No notifications on the Mac | Allow DropDuo in **System Settings → Notifications**. |
| A file won't open | Install an app for that file type, or use **Save a copy** on Android. |

When reporting a bug, include OS versions and the app version, and never include pairing codes or private files.
