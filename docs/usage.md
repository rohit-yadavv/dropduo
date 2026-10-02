# Using Nearport

1. Install and open both apps. Connect the Mac and Android phone to the same network; both must be able to reach each other. Guest networks often isolate devices.
2. On Mac, choose **Devices → Pair a device**. Choose the Wi-Fi address if more than one is available. The code expires after five minutes.
3. On Android, scan the QR or paste the pairing code. Approve the request on the Mac. Only share the code with the device you intend to trust.
4. Select your phone on Mac and choose files, drop files, or send text. On Android, choose files in Nearport or select Nearport in another app's Share menu. Links are sent as explicit text.
5. Keep Mac awake with Nearport running. Android shows a connection notification while its service runs. The paired phone reconnects when the Mac becomes reachable.

## Files and history

Mac receives into `~/Downloads/Nearport/<pair ID>/`. Android receives into its app-specific external Downloads directory. Open files from Recent; use **Save copy** on Android to export them to a permanent user-selected location. Uninstalling Android deletes app-specific files.

Recent history keeps up to 100 local entries. It does not retain deleted files or act as a backup. Text appears in history and can be copied explicitly. Received filenames include a unique transfer ID to prevent overwriting existing files.

A failed file transfer retains partial receiver data. Retry from the sender while its original source remains available. Android retains a local outgoing copy for failed transfers; clearing its history removes retained outgoing copies. Interrupted partial receiver data is cleaned after seven days. Cancel removes the associated partial transfer. History clearing leaves received completed files intact.

## Trusted devices

Android v1 pairs with one Mac at a time; a Mac can pair multiple Android devices. Pause receiving to reject new inbound transfers. Forget a device to remove local trust and close its connection. Forget it on both ends to remove both stored credentials. Re-pair explicitly when needed.
