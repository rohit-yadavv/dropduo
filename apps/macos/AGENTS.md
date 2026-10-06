# macOS contributor instructions

Read root AGENTS.md. Swift package: library DropDuoCore, native app DropDuo, and headless interop executable. SwiftUI/AppKit belongs only in the app. Network and crypto stay in core. UI updates on MainActor. Store pair secrets in Keychain; never metadata JSON. Package via scripts/build-macos. Tests must not alter the user's real Keychain or app storage. The menu bar icon is an AppKit NSStatusItem (MenuBarExtra can't accept drops); notifications and the login item need a packaged app bundle. Command Line Tools lack SwiftUI's macro plugins, so avoid `@State`/`@Observable` and keep view state in `AppModel`.

UI review (debug builds only): `DROPDUO_PREVIEW=1` loads sample devices and transfers without starting the server or reading saved pairings. `DROPDUO_PREVIEW_SCENE` picks `device`, `welcome`, `pairing`, `offline` or `empty`; `DROPDUO_SNAPSHOT=<dir>` saves light and dark PNGs of each window, then quits.
