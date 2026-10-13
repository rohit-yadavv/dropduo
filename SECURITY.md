# Security policy

DropDuo has not had an independent security audit. The latest release receives fixes.

Report vulnerabilities through GitHub private vulnerability reporting **when enabled**. Otherwise ask the maintainer for a private channel without sharing exploit details. Never post secrets or sensitive reports in public issues.

Pair only devices you control. A compromised paired device can send files. Forgetting a device closes sessions and revokes local trust, but cannot revoke received copies. See the [protocol](protocol/SPEC.md).

Pairing uses a single-use QR ticket that expires after five minutes, plus approval on the Mac. Sessions use HMAC-authenticated handshakes, HKDF-SHA256 keys and AES-256-GCM. There is no forward secrecy: if a pair secret leaks, re-pair. Files are verified with SHA-256 before they appear, existing files are never overwritten, and DropDuo never opens received files automatically.
