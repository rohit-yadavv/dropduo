# Security policy

Nearport is pre-release software; no independent security audit has been completed. The current development version receives fixes.

Do not report exploitable vulnerabilities with secrets in public issues. Once a GitHub remote is configured, use its private vulnerability reporting feature. Until that channel exists, do not upload sensitive reports publicly; ask the maintainer for a private channel without disclosing exploit details.

Threat model and protocol details live in `docs/security.md` and `protocol/SPEC.md`. Pair only devices you control. A compromised paired device can send files. Revoking trust prevents new sessions and closes existing sessions. Downloaded copies cannot be remotely revoked.
