# Changelog

## Unreleased

## 0.1.0-alpha.2

- Redesign the Mac app around a sidebar of paired devices and a per-device timeline of sent and received items, with a composer bar, drop-anywhere sending, an offline banner, a pairing sheet with an expiry countdown, and a standard Settings window.
- Rewrite the product introduction and user guides around everyday Mac–Android sharing; add a sourced competitor comparison, FAQ, and documentation index.
- Add an animated, dependency-free product website with platform-aware Mac and Android downloads from the latest published release, plus a GitHub Pages workflow.
- Reduce redundant CI runs: build/quality checks on develop and master pushes plus PRs, and CodeQL on PRs and weekly. Preserve all tests, platform coverage, and release automation.

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
