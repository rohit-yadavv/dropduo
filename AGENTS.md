# Agent contributor guide

## Product

DropDuo v1 shares files, photos, links, and text between macOS and Android on a reachable local network. Native apps, persistent trusted pairing, no accounts or cloud. Do not add clipboard monitoring, remote control, browser clients, or extra platforms without an agreed scope change.

## Map and authority

- `docs/product.md`: behavior and scope.
- `docs/architecture.md`: system boundaries and security design.
- `protocol/SPEC.md`: authoritative cross-platform contract.
- `CONTRIBUTING.md`: Git workflow (`develop` → `master`) and commit format.
- Component `AGENTS.md` files add focused instructions.

## Work loop

Read relevant code and instructions; make a bounded change; run `./scripts/check <scope>`; update docs when behavior changes; commit a coherent milestone. Fetch current official dependency/API documentation using Context7 when available. Never invent test results.

## Invariants

Authenticate paired peers before accepting data. Never trust discovery names or IPs as identity. Never log pairing secrets or file contents. Keep secrets, signing keys, local SDK paths, builds, and user files out of Git. Stream files with bounded memory. Treat remote filenames and lengths as untrusted. Publish completed files only after integrity verification. Keep protocol versions and shared fixtures consistent.

## Verification

Run `./scripts/doctor` first. Mac checks require macOS and a Swift SDK. Android checks require Java 17 and Android SDK. Protocol tests must cover cross-language compatibility. Background receiving, screen locking, and Wi-Fi changes require device evidence; compilation is not proof. See `docs/development.md`.

## Collaboration

No vendor-specific tool is required to contribute. Do not create PRs, push, publish releases, or configure hosted protections unless that action is in task scope. Current authorization: local milestone commits on develop. Do not auto-merge security-sensitive changes. Do not spawn agents unless explicitly authorized by the task.
