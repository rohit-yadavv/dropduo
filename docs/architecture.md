# Architecture

One monorepo, two native apps, one versioned protocol. Swift/SwiftUI on macOS; Kotlin/Compose on Android. No shared UI/engine or external backend in v1. Native system integration and independently tested protocol implementations are the v1 design choice.

Mac advertises a Bonjour TCP listener. Android discovers and opens a persistent authenticated connection; both sides can initiate transfers over it. QR pairing bypasses discovery but does not bypass network isolation. Discovery is a reachability hint, not authentication.

A high-entropy per-pair secret is exchanged through a short-lived QR ticket. Mac explicitly approves pairing. Native secure storage protects secrets. Authenticated fresh session nonces derive separate directional encryption keys. Framed encrypted JSON carries bounded file chunks and control messages. See `protocol/SPEC.md`.

Files live in managed local directories. Small JSON metadata stores devices and transfer records atomically. Secret material stays in macOS Keychain / encrypted Android storage backed by Keystore. JSON metadata is sufficient for bounded v1 history; introduce a database only when warranted.

Platform integration stays outside protocol/transfer logic. The JVM core is independently testable. The Swift core builds as a library with tests and a headless interop executable.
