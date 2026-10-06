# Security policy

DropDuo has not had an independent security audit. The latest release receives fixes.

Report vulnerabilities through GitHub private vulnerability reporting **when enabled**. Otherwise ask the maintainer for a private channel without sharing exploit details. Never post secrets or sensitive reports in public issues.

Pair only devices you control. A compromised paired device can send files. Forgetting a device closes sessions and revokes local trust, but cannot revoke received copies. See [architecture and security](docs/architecture.md#security) and [protocol](protocol/SPEC.md).
