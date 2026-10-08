# Product contract: v1

Free, native Mac–Android sharing over a reachable local network. No accounts, cloud file storage, ads, or analytics.

## Scope

- Files, photos, videos, explicit text/links. Mac: drag and drop into the window, **Send Files…** from the window or menu bar, notifications for received items, open at login. Android: Share target and file picker.
- QR pairing with Mac approval, persistent credentials, receiving controls, and device revocation.
- Authenticated encrypted transfers with progress, cancellation, integrity verification, and resume/retry. Files and text sent while the other device is unreachable wait on the sender, and sends cut off by a lost connection resume; both go out automatically when the devices reconnect. Other failures need a manual retry.
- Copy selected bytes without moving originals or re-encoding. Recent holds 100 entries, not backups.
- Mac files go to Downloads/DropDuo by pair; Android uses app storage with **Save a copy** for export. Android uninstall deletes app-specific files.
- One Mac per Android; multiple Android peers per Mac.
- In-app update notices, release notes, user-initiated downloads and installation. Update checks/downloads use GitHub over the internet; sharing works without it. Mac verifies signed updates and relaunches after active transfers finish. Android verifies the APK and uses the system installer confirmation, with installation available once transfers finish.
- Error messages say which device is affected and what to check.

## Limits

Both devices must be reachable and apps available. Mac sleep stops receiving; Android's foreground service and battery policies affect availability. No guaranteed receiving after force-stop, and no cloud delivery: waiting items send only once both apps are running on the same network again. The Mac sends waiting files from their original location, so moving or deleting them first stops the send. Retry requires retained data and an available source. Pairing never grants arbitrary file access.

Clipboard monitoring, folder sync, browser transfer clients, remote control, phone notification/call mirroring, internet relays, and extra platforms require an agreed scope change. Keep the apps simple: prefer improving existing flows over adding settings or modes.

## Release criteria

Users can pair without coaching, send both ways, find received files, and reconnect after restarting. Check this on real devices before each release; CI alone does not prove reliability. Describe only shipped behavior. Do not claim audited security, notarization the build doesn't have, always-on receiving, or an advantage over competitors without evidence. Use opt-in feedback, no hidden analytics.
