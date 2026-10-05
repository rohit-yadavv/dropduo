# Security policy

DropDuo is pre-release software without an independent security audit. The current development version receives fixes.

Report vulnerabilities through GitHub private vulnerability reporting **when enabled**. Otherwise ask the maintainer for a private channel without sharing exploit details. Never post secrets or sensitive reports in public issues.

Pair only devices you control. A compromised paired device can send files. Forgetting a device closes sessions and revokes local trust, but cannot revoke received copies. See [security design](docs/security.md) and [protocol](protocol/SPEC.md).
