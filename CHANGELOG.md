# Changelog

Each [GitHub release](https://github.com/rohit-yadavv/dropduo/releases) lists the pull requests it includes. A section here with the exact version adds highlights above that list.

## Unreleased

## 0.1.0-alpha.6

- Send while the other device is offline: files and text you share from the Mac or the phone wait, then go out automatically the next time the devices connect. Clearing recent history keeps items still waiting to send.
- Remove drag and drop onto the menu bar icon; dragging there opened the macOS desktop instead of sending. Use **Send Files…** in the menu bar menu or drop files into the window.
- The Mac app icon now uses a dark tile, so it stays legible in dark mode.

## 0.1.0-alpha.5

- Drop files on the DropDuo menu bar icon to send them to your phone. With several paired phones, choose the target under **Send To** in the menu.
- Show a Mac notification when a file or text arrives: click a file to show it in Finder, or choose **Copy** for text. Sends started from the menu bar confirm with a notification too.
- Open DropDuo at login, so a paired phone can reach the Mac after a restart. It turns on after pairing and can be switched off in Settings. Closing the window keeps DropDuo running in the menu bar.
- Clearer error messages on both apps: which device can't be reached and what to check, paused receiving, a busy receiver, folders, and lost connections.

## 0.1.0-alpha.4

- Keep the Android pairing-code scanner in portrait instead of rotating to landscape.
- Redesign the Android app: focused pairing screen, a single home with Mac status, Files and Text actions, and recent transfers, transfer details in sheets, and a grouped Settings screen. Uses the brand palette with dark mode and custom line icons.

## 0.1.0-alpha.3

- Fix "Frame too large" when sending some large files from the Mac; Foundation JSON escaping could push a chunk past the frame limit.
- Fix the Mac app crashing at launch when its bundled images could not be found (seen opening the downloaded app); images now ship in the app's Resources and missing art falls back to a system symbol.
- Resume sends automatically when the devices reconnect after a dropped connection, with an option to stop resuming.
- Simplify the README and docs; consolidate FAQ answers into the setup guide.
- Add Mac first-launch instructions, a Settings shortcut, and public-source links.

## 0.1.0-alpha.2

- Redesign Mac sharing with a device sidebar, transfer timeline, composer, drop-anywhere sending, and pairing/settings sheets.
- Add user guides and a documentation index for Mac–Android sharing.
- Add a static download website with platform-aware release links and GitHub Pages deployment.
- Reduce duplicate CI runs; skip native builds for docs/web-only changes and run CodeQL on master PRs and weekly.

## 0.1.0-alpha.1

- Add Apple Silicon and Intel Mac development downloads, repair hosted SDK/resource-bundle setup, and include download checksums.
- Add tag-validated release drafts, optional distribution signing/notarization, shared version metadata, workflow guard checks, CodeQL, dependency review, and Dependabot configuration.
- Adopt the bold overlapping double-D icon in ink and cobalt on pure white across native app launchers and headers.
- Adopt DropDuo branding throughout the native apps, package identifiers, protocol namespace, documentation, scripts, and artifacts. Earlier working-name alpha builds require fresh pairing on both devices.
- Native Mac and Android apps with persistent approved pairing and encrypted local transfer.
- Bidirectional file, photo, video, explicit text/link sharing; progress, cancellation, and manual resume/retry.
- Local recent history, receiver controls, device revocation, native Android Share target, and Mac menu bar access.
- Keychain/Keystore credential storage, bounded framing, safe filenames, integrity verification, and incomplete-file retention.
- Open-source documentation, agent contributor guides, Git standards, CI definitions, and repeatable cross-platform validation.

Development artifacts are not production-signed releases. Physical-device and distribution validation remain required.
