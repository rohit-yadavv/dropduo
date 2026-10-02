# Releases

Users download published apps from [GitHub Releases](https://github.com/rohit-yadavv/dropduo/releases), following [installation instructions](downloads.md). CI builds on the Actions page are temporary development downloads. The Release workflow always creates a **draft**, which remains invisible to ordinary users until a maintainer publishes it.

## What is automated

`checks.yml` tests and packages both Mac architectures, builds/lints Android, and runs Swift/JVM interoperability on pushes, PRs, or manual runs. `quality.yml` validates workflow syntax, shell syntax and release guards. `security.yml` runs CodeQL for Swift and Java/Kotlin on pushes, PRs and weekly. Dependency review checks PRs; Dependabot opens weekly update PRs for GitHub Actions and Gradle dependencies. Actions are pinned to full commit SHAs and workflows grant permissions per job. No dependency or security change auto-merges.

`release.yml` runs when a `v*` tag is pushed, or when a maintainer selects an existing tag using **Actions → Release → Run workflow**. It verifies the tag is an actual tag, is reachable from `master`, matches the app version, and has a released changelog section. It reruns checks on that exact tag, builds three platform assets, verifies checksums/consistent source commits, and creates a draft with notices and notes. Existing releases are never overwritten. It does not create tags, merge branches, publish to app stores, or publish the draft automatically.

## Prepare a version

1. Complete the applicable [physical-device and accessibility checks](testing.md), review [validation limits](validation.md), and review security and third-party licenses. CI does not establish real-phone reliability or constitute a security audit. Include required third-party license texts before a public release; `DEPENDENCIES.md` is an inventory, not a substitute for those texts.
2. On a feature/release preparation branch from `develop`, edit `version.properties`: set `versionName` to `0.1.0-alpha.1`, `0.1.0-beta.1`, `0.1.0-rc.1`, or a stable SemVer such as `1.0.0`. Increase `versionCode` for every distributed update. This file drives both app versions; do not edit platform version values separately.
3. Add an exact changelog heading such as `## 0.1.0-alpha.1` and move that version's changes beneath it. Leave `## Unreleased` above it for future work. The workflow rejects an Unreleased-only section.
4. Merge the preparation PR into `develop`, validate through `staging`, then merge the release PR from `staging` into `master`. Wait for all required checks on the production commit. Follow [Git standards](git-workflow.md).

The repository currently started with `develop`. If `staging` and `master` do not exist, maintainers must establish their reviewed initial baselines using GitHub's **Branches → New branch** before adopting the promotion flow. Future changes go through PRs; CI files do not create or protect branches.

## Tag and build the draft

Run after the reviewed release commit is on `master`:

```sh
git fetch origin --tags
git switch master
git pull --ff-only origin master
./scripts/check-release v0.1.0-alpha.1 development
git tag -a v0.1.0-alpha.1 -m "DropDuo 0.1.0-alpha.1"
git push origin v0.1.0-alpha.1
```

Use the version you prepared, not an already-used tag. Never move a published tag. Pushing a prerelease tag chooses `development` by default; pushing a stable tag chooses `distribution`. For a signed prerelease, run the workflow manually against the existing tag with `mode=distribution`, before any development draft for that tag has been created. Alternatively, use a new prerelease tag. A rerun with an existing draft will fail at creation to avoid overwriting reviewed assets; after inspecting a failed/incomplete draft, a maintainer may remove that draft before retrying. Removing a draft does not require moving its tag.

## Development prerelease mode

No signing secrets are required. It produces an ad-hoc signed, non-notarized Mac app and a debug-signed Android APK, with `-development` in filenames and explicit limitations in the draft. These are for testers. Android debug keys on hosted runners can change, preventing in-place updates; users must export received files before uninstalling an earlier build.

Stable tags are refused in this mode. Do not relabel a development build as a signed release.

## Signed distribution mode

Add the following **Actions repository secrets** through GitHub **Settings → Secrets and variables → Actions**. Keep master/tag creation restricted to trusted maintainers; for a team, require reviewed production changes and protect version tags before storing signing credentials. PR/CI jobs never import these signing credentials; only the tag-validated Release jobs use them.

| Secret | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of your backed-up distribution `.jks` |
| `ANDROID_STORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Signing key alias |
| `ANDROID_KEY_PASSWORD` | Key password |
| `MAC_CERTIFICATE_BASE64` | Base64 of exported Developer ID Application `.p12`, including private key |
| `MAC_CERTIFICATE_PASSWORD` | Password protecting that `.p12` |
| `MAC_SIGN_IDENTITY` | Full `Developer ID Application: … (TEAMID)` identity |
| `APPLE_ID` | Apple account authorized for notarization |
| `APPLE_APP_PASSWORD` | Apple app-specific password for notarization |
| `APPLE_TEAM_ID` | Apple Developer team ID |

Create the Android key using Android Studio **Build → Generate Signed Bundle/APK → Create new**; retain the same distribution key for all direct APK updates and keep an offline backup. See [Android signing](https://developer.android.com/studio/publish/app-signing). Obtain a Developer ID Application certificate through your Apple Developer account and export it with its private key. See [Apple notarization](https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution).

For a single-line Base64 value on macOS, use `base64 -i /path/to/file | tr -d '\n'` and paste the result directly into the corresponding secret field. Do not paste certificates, keystores or passwords into issues, PRs, chat or Git. Repository Actions secrets are sufficient for the current workflow; if you adopt an approval-protected `release` environment, update the signing jobs to use that environment.

Distribution jobs build the Android release APK with that key and verify it with `apksigner`. Mac jobs import the certificate into an ephemeral runner keychain, sign with hardened runtime, submit to Apple notarization, staple the ticket, and assess the app. Temporary credentials are removed in `always()` cleanup steps. Missing credentials or failed notarization stops the draft. Distribution mode has not been executed with real maintainer credentials yet.

## Review and publish

Open **Releases**, edit the generated draft, and inspect its notes and Assets. There must be one Apple Silicon ZIP, one Intel ZIP, one Android APK, `SHA256SUMS`, per-asset checksums and JSON metadata, `LICENSE`, `NOTICE`, and `DEPENDENCIES.md`. Download/install the exact draft assets on clean devices, verify same-key Android upgrades for signed builds, and review the known limitations. Attach any required third-party license texts.

Keep alpha/beta/rc versions marked **pre-release**. Click **Publish release** when satisfied. Users can then download those Assets directly from the public release page. Draft releases require maintainer access; published release assets do not use CI's 30-day retention. [GitHub release management](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).

## Repository settings to enable

Protect `develop`, `staging`, and `master` using the [Git workflow](git-workflow.md), with the scoped solo-v1 exception on `develop`. Require relevant successful build, quality and CodeQL jobs; enable dependency graph, Dependabot alerts and private vulnerability reporting. Consider version-tag protection and immutable releases. These settings require maintainer configuration in GitHub; workflow files alone do not activate them. Fork PRs have read-only tokens and no release secrets.
