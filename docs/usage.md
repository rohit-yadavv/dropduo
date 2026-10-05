# Your first DropDuo transfer

DropDuo copies files, photos, videos, text, and links between your Mac and Android phone. Pair the devices once, then share in either direction. It doesn't upload them to a cloud service or require an account.

## Before you start

- [Install DropDuo](downloads.md) on both devices: macOS 14+ and Android 10+.
- Connect both devices to the same reachable local network. Internet access isn't required, but guest Wi-Fi may block communication between devices.
- Open both apps and keep your Mac awake. Complete the connection permissions requested by your OS.

The current builds are an early alpha. For questions about availability, storage, or alternatives, see the [FAQ](faq.md) and [comparison](comparison.md).

## 1. Pair your phone and Mac

1. On Mac, choose **Pair a Device** at the bottom of the sidebar, or **Show Pairing Code** on first run.
2. If the Mac has several network addresses, select its Wi-Fi address under the QR code. The code expires after five minutes; choose **New Code** if it does.
3. On Android, choose **Scan pairing code** and scan the Mac's QR. Alternatively, use **Paste a pairing code** with the code copied from the Mac.
4. Approve the request on the Mac for the phone you intended to connect.

The apps remember the pairing across restarts. Keep the code private: it grants an opportunity to request trust. When an already-paired phone is offline, reopen the apps and choose **Connect** on Android if needed. Pairing doesn't require a new QR each time.

## 2. Send from Android to Mac

For a photo, scan, document, or other item already open in another Android app:

1. Select the item and choose **Share**.
2. Select **DropDuo** in Android's Share menu. Some apps may put it under **More**.
3. If your paired Mac is offline, reconnect before completing the transfer. Watch the progress in DropDuo.

You can also open DropDuo's **Share** tab and choose **Choose files**.

On Mac, received items appear in that phone's timeline. Double-click a file to open it, or use its magnifying-glass button to show it in Finder. The folder button in the window toolbar and the menu-bar item **Open received files** open the receiving folder.

## 3. Send from Mac to Android

1. Open DropDuo and select your connected phone in the sidebar.
2. Drop files anywhere in the window or use the paperclip button. The menu-bar item **Send files…** is another way to open the picker.
3. Follow transfer progress. In Android's **Recent** tab, choose **Open** to view a received file or **Save a copy** to export it to a chosen folder.

**Save a copy is important:** Android's default received files belong to DropDuo's app storage. Export files you want to keep independently of the app.

## Send a link or text

On either device, enter or paste a link or text into the sharing field and send it. On Mac, use the send button or ⌘Return. On Android, you can also share supported text from another app's Share menu. On the receiver, find the item in Recent (on Mac, the phone's timeline) and select **Copy**.

Text is shared deliberately. DropDuo doesn't monitor or automatically synchronize your clipboard.

## Files and history

Mac receives into `~/Downloads/DropDuo/<pair ID>/`. Android receives into its app-specific external Downloads directory. Uninstalling Android deletes app-specific files; exported copies remain in their chosen location.

Recent history keeps up to 100 local entries. It doesn't retain deleted files or act as a backup. Received filenames include a unique transfer ID to prevent overwriting existing files. Sharing copies selected data; the sender's original isn't moved or deleted.

A failed file transfer retains partial receiver data. Reconnect, then choose **Retry** on the sender while the original source is still available. Android retains a local outgoing copy for failed transfers; clearing its history removes retained outgoing copies. Interrupted partial receiver data is cleaned after seven days. **Cancel** removes the associated partial transfer. Clearing history leaves completed received files intact.

## Receiving and trusted devices

On Mac, **DropDuo → Settings… → Accept files and text from paired devices** controls receiving. On Android, use **Device → Accept files and text**. Disabling receiving keeps pairing intact but rejects new inbound transfers.

Android v1 pairs with one Mac at a time; a Mac can pair multiple Android devices. **Forget** removes local trust and closes the connection. On Mac, it's in the device's **⋯** toolbar menu or its right-click menu in the sidebar. Forget the device on both ends to remove both stored credentials; pair explicitly again when needed.

Keep Mac awake with DropDuo running. Android shows a connection notification while its foreground service runs. Mac sleep, Android force-stop, revoked permissions, or battery controls can interrupt availability. See [troubleshooting](troubleshooting.md) if a device stays offline.

## Updating from an earlier alpha

DropDuo has new application identifiers and pairing credentials. Install it on both devices and pair again. Earlier working-name alpha history and trust aren't automatically migrated. Existing received files remain in their original storage locations; export important Android files before uninstalling an earlier build. Also review the [development upgrade limitations](downloads.md#install).
