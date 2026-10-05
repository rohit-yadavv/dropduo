# Product contract: v1

## Product promise

Share files between your Mac and Android. Pair once. Keep sharing.

DropDuo gives people who use that device combination a dedicated native workflow for copying selected files, photos, videos, text, and links over a local network. The apps are free and open source, with no account, cloud file store, ads, or analytics.

This document defines scope and acceptance criteria. The [README](../README.md) introduces the product to users; [usage](usage.md), [FAQ](faq.md), and the [comparison](comparison.md) explain the experience and tradeoffs.

## Audience and everyday jobs

| Situation | Job to complete |
| --- | --- |
| A phone photo, receipt, or scan needs editing or filing on the Mac | Share selected items from Android to Mac. |
| A document on the laptop is needed while away from the desk | Send it to Android and save a copy in a chosen folder. |
| A video or other file needs copying between personal devices | Transfer it without DropDuo transforming the supplied file bytes. |
| A link, address, or note is on the wrong device | Send it explicitly and copy it on the receiver. |

## Product choices

- Focus on a Mac and Android used repeatedly together, with explicit pairing approval and remembered credentials.
- Build around native controls: Mac drag and drop, file selection, and menu-bar access; Android file selection and system Share integration.
- Make transfer state visible: progress, cancellation, completion, and explicit retry.
- Keep trust manageable: receiving controls and device revocation.
- Keep the primary flow about sharing selected data. Don't expand it into a general device-control dashboard without an agreed scope change.

Local transfer, encryption, pairing, and open source aren't assumed to be unique to DropDuo. Describe the native workflow and scope as choices. Claims of faster transfers, better security, or superior reliability require evidence against named alternatives. See the [sourced comparison](comparison.md).

## Scope

Native Mac window/menu bar; Android Share target and file picker; QR pairing with explicit Mac approval; remembered trusted devices; authenticated encrypted local transfers; progress, cancellation, interrupted-transfer retry, integrity checks, recent transfers, and unpairing.

Files are copied, never moved. Recent holds up to 100 local entries; it isn't a backup or permanent shared library. Mac files go into Downloads/DropDuo, separated by pairing. Android receives in app-specific storage and exposes **Save a copy** for permanent export. Uninstalling Android removes its app storage. Android v1 pairs with one Mac at a time; a Mac can pair multiple Android devices.

## Availability

Both devices must be reachable and the apps running. Mac sleep stops receiving. Android receives while its connection service is active and indicates that through a notification. No guaranteed unattended receiving after force-stop/reboot. No cloud queue. Interrupted transfers can retry once reachable and source data is still available.

## Boundaries

No automatic clipboard monitoring, folder sync or library, browser transfer UI, calls, notification mirroring, remote control, internet relays, or iPhone/Windows/Linux apps in v1. Pairing doesn't expose other files. A paired peer can auto-send only when receiving is enabled; users can disable receiving and revoke trust.

## Release honesty

Implemented features and passing CI are distinct from physical-device reliability. Keep [validation evidence](validation.md) current. Don't market alpha artifacts as notarized distribution, an audited security product, a backup, or an always-available receiving service. Separate planned work from shipped behavior.

## Success

A new user understands the purpose without reading build instructions, pairs without coaching, sends a file both ways, knows where it went, and repeats the flow after restarting apps. Real-device transfer success and task completion are release criteria. Use opt-in testing and reported feedback; no hidden usage analytics.
