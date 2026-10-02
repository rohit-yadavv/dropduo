# macOS contributor instructions

Read root AGENTS.md. Swift package: library DropDuoCore, native app DropDuo, and headless interop executable. SwiftUI/AppKit belongs only in the app. Network and crypto stay in core. UI updates on MainActor. Store pair secrets in Keychain; never metadata JSON. Package via scripts/build-macos. Tests must not alter the user's real Keychain or app storage.
