# Pair and share

[Install both apps](downloads.md), connect to the same reachable local network, allow requested connection permissions, and keep the Mac awake. Internet access is not required.

## Pair once

1. On Mac, choose **Pair a Device** or **Show Pairing Code**.
2. Select the Mac's Wi-Fi address if several are listed. Codes expire after five minutes; use **New Code** when needed.
3. On Android, choose **Scan pairing code**, or **Enter code instead** and paste the code copied from the Mac.
4. Approve the intended phone on the Mac. Keep the code private.

Pairing survives restarts. Reopen the apps and choose **Connect** on Android if needed. Android pairs with one Mac at a time; a Mac can pair multiple phones.

## Send files, text, or links

- **Android → Mac:** select an item in another app, then **Share → DropDuo** (sometimes under **More**), or tap **Files** or **Text** inside DropDuo.
- **Mac → Android:** select your phone, drop files into the window, or use the paperclip/menu-bar **Send files…** action.
- **Text or links:** enter or paste into the sharing field and send. Mac also supports ⌘Return; Android accepts shared text from other apps. Choose **Copy** on the receiver.

Transfers copy the supplied bytes without resizing or re-encoding; originals stay on the sender. DropDuo does not monitor your clipboard, sync folders, or browse the other device's files.

## Files and history

- **Mac:** `~/Downloads/DropDuo/<pair ID>/`. Double-click a received file or use the magnifying glass to show it in Finder. The toolbar folder and menu-bar **Open received files** open the receiving folder.
- **Android:** app-specific external Downloads storage. Tap an item under **Recent**, then **Open** to view it or **Save a copy** to export. **Uninstalling deletes app-specific files; export important files first.**

Recent holds 100 local entries and is not a backup. Clearing history leaves completed received files intact. Filenames include a transfer ID to prevent overwrites. The protocol permits files up to 32 GiB; large-file endurance is not yet verified.

For interrupted files, reconnect and choose **Retry** on the sender while the original source remains available. Matching retained partials can resume; abandoned receiver partials expire after seven days. **Cancel** removes partial data. Android retains outgoing copies for failed transfers; clearing history removes those copies.

## Receiving and trusted devices

Use **DropDuo → Settings… → Accept files and text from paired devices** on Mac, or **Device → Accept files and text** on Android to pause receiving without losing pairing.

**Forget** removes local trust and closes the connection. On Mac, use the device's **⋯** toolbar menu or sidebar right-click menu. Forget on both ends to remove both stored credentials, then pair again if needed.

The Mac must stay awake with DropDuo running. Android's connection service shows a notification; force-stop, reboot, battery controls, or revoked permissions may interrupt receiving. There is no cloud relay or offline queue. See [troubleshooting](troubleshooting.md).

## Upgrades

Development Android signing keys can change, blocking in-place updates. Export files before uninstalling. Earlier working-name alpha pairings/history do not migrate: install DropDuo on both devices and pair again. Existing files remain in their original locations. See [download limitations](downloads.md#install).
