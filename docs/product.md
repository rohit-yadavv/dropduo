# Product contract: v1

## Audience and job

People who use a Mac and Android phone daily and want native sharing without an account. Install both apps, pair once, and send files or explicitly shared text in either direction.

## Scope

Native Mac window/menu bar; Android Share target and file picker; QR pairing with explicit Mac approval; remembered trusted devices; encrypted local transfers; progress, cancellation, interrupted-transfer retry, integrity checks, recent transfers, and unpairing. Files are copied, never moved. Receive history is not a backup or permanent shared library.

## Availability

Both devices must be reachable and the apps running. Mac sleep stops receiving. Android receives while its connection service is active and indicates that through a notification. No guaranteed unattended receiving after force-stop/reboot. No cloud queue. Interrupted transfers can retry once reachable and source data is still available.

## Boundaries

No automatic clipboard monitoring, folder library, browser UI, calls, notifications mirroring, remote control, internet relays, or iPhone/Windows in v1. Pairing does not expose other files. A paired peer can auto-send only when receiving is enabled; user can disable and revoke trust.

## Success

A new user pairs without coaching, sends a file both ways, understands where it went, and repeats the flow after restarting apps. Reliability measured on real devices is the release criterion.
