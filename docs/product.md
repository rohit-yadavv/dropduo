# Product contract: v1

Free, native Mac–Android sharing over a reachable local network. No accounts, cloud file storage, ads, or analytics.

## Scope

- Files, photos, videos, explicit text/links; Mac drag and drop/menu bar and Android Share/file picker.
- QR pairing with Mac approval, persistent credentials, receiving controls, and device revocation.
- Authenticated encrypted transfers with progress, cancellation, integrity verification, and manual retry/resume.
- Copy selected bytes without moving originals or re-encoding. Recent holds 100 entries, not backups.
- Mac files go to Downloads/DropDuo by pair; Android uses app storage with **Save a copy** for export. Android uninstall deletes app-specific files.
- One Mac per Android; multiple Android peers per Mac.

## Limits

Both devices must be reachable and apps available. Mac sleep stops receiving; Android's foreground service and battery policies affect availability. No guaranteed receiving after force-stop/reboot or cloud delivery queue. Retry requires retained data and an available source. Pairing never grants arbitrary file access.

Clipboard monitoring, folder sync, browser transfer clients, remote control, notifications/calls, internet relays, and extra platforms require an agreed scope change.

## Release criteria

Users can pair without coaching, send both ways, find received files, and reconnect after restarting. Record real-device results in [validation](validation.md); CI alone does not prove reliability. Keep shipped behavior separate from plans. Do not claim audited security, notarized alpha builds, always-on receiving, or an advantage over competitors without evidence. Use opt-in feedback, no hidden analytics.
