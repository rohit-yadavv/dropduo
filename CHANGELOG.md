# Changelog

## Unreleased

- Fix "Frame too large" when sending some large files from the Mac; Foundation JSON escaping could push a chunk past the frame limit.
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
