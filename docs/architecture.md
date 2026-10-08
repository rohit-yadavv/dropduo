# Architecture and security

One monorepo, two native apps, one versioned protocol: Swift/SwiftUI on macOS, Kotlin/Compose on Android. No shared UI or engine, and no backend. The wire contract is [protocol/SPEC.md](../protocol/SPEC.md).

## System

The Mac advertises a Bonjour TCP listener. Android discovers it and keeps a persistent authenticated connection; either side can start a transfer over it. QR pairing bypasses discovery but not network isolation. Discovery names and addresses are reachability hints, never identity.

Files live in managed local folders. Small JSON files store devices and transfer history atomically; secrets live in the macOS Keychain and Android Keystore-backed storage. Platform integration (menu bar, notifications, share sheet, foreground service) stays outside protocol and transfer logic. The JVM core is tested on its own; the Swift core builds as a library with checks and a headless interop executable.

## Security

Updates stay outside the local sharing protocol. Mac uses Sparkle with a public Ed25519 key embedded in the app, signed feeds and signed archives verified before extraction; the release jobs verify the archive signature against that public key. Android checks the official release's SHA-256 checksum, APK package/version, OS support and signing certificate before invoking the system installer. Downloads stream to app-owned temporary storage with size limits, HTTPS and restricted redirect hosts. No update can silently uninstall the app or discard pairing/files. Installation pauses new work and is deferred until active transfers finish. Release checks never transmit pairing credentials, filenames or file contents, and system profiling is disabled.

Pairing uses a random 256-bit secret in a five-minute QR ticket plus explicit approval on the Mac. Anyone who sees an unused ticket may try to pair, so tickets expire and are used once.

Sessions use fresh random nonces, HMAC transcript authentication, HKDF-SHA256 directional keys, and AES-256-GCM with sequence numbers authenticated as additional data. Replays, invalid tags, wrong versions, unknown peers, and oversized frames are rejected. This is a small protocol built from standard primitives and has not been independently audited. There is no forward secrecy: if a pair secret leaks, captured sessions can be decrypted, so re-pair after a compromise.

File IDs, names, sizes, offsets, and hashes are validated. Data streams in bounded chunks to partial files, is verified with SHA-256, and only then is published; existing user files are never overwritten. Active transfers and free space are bounded and abandoned partials expire. Paired devices can only send; neither side can browse the other.

No telemetry. History may reveal filenames and device names locally. Authentication doesn't make a received file safe to run, so DropDuo never opens files automatically.
