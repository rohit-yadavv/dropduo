# Releases

Publish downloads through [GitHub Releases](https://github.com/rohit-yadavv/dropduo/releases). You publish a release on GitHub; Release CI builds and attaches its downloads. CI artifacts are temporary development downloads.

## Release

1. On `develop`, set the new `versionName` (for example `0.1.0-alpha.7`) and a higher `versionCode` in `version.properties`; both apps use it. Move the **Unreleased** items in `CHANGELOG.md` under a heading with the exact version, such as `## 0.1.0-alpha.7`.
2. Open a PR from `develop` into `master` and merge it once checks pass. See the [Git workflow](../CONTRIBUTING.md#git-workflow).
3. On GitHub, open **Releases → Draft a new release**. Type the new tag (`v0.1.0-alpha.7`), choose target **master**, leave the description empty, and click **Publish release**.

Publishing starts **Actions → Release**. It checks that the tag matches `version.properties` and the changelog and points at master, builds the Mac and Android apps, attaches them to the release, and fills the empty description from the changelog. It doesn't repeat the checks that ran when the commit reached master. The website lists the release only once its downloads are attached, about 15 minutes later.

Test the attached downloads on your Mac and phone. If something is wrong, fix it on `develop` and release a new version; never move a published tag or replace its downloads.

## Signing modes

**Development:** default for prerelease tags such as `v0.1.0-alpha.7`. No signing secrets needed; ad-hoc signed, non-notarized Mac apps and debug-signed Android APKs use `-development` filenames. For testers only. Android debug keys can change, blocking updates; export received files before uninstalling. Stable tags are refused in this mode.

**Distribution:** default for stable tags; requires credentials below. For a signed prerelease, cancel the automatic run in **Actions → Release**, then use **Run workflow** with the tag and `mode=distribution`. Do not relabel development builds as signed distribution.

Add these repository **Actions secrets** in **Settings → Secrets and variables → Actions**:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 distribution `.jks` |
| `ANDROID_STORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |
| `MAC_CERTIFICATE_BASE64` | Base64 Developer ID Application `.p12` with private key |
| `MAC_CERTIFICATE_PASSWORD` | `.p12` password |
| `MAC_SIGN_IDENTITY` | Full Developer ID Application identity with team ID |
| `APPLE_ID` | Account authorized for notarization |
| `APPLE_APP_PASSWORD` | App-specific password |
| `APPLE_TEAM_ID` | Developer team ID |

Create/back up the Android key using Android Studio's **Generate Signed Bundle/APK**; retain the same key for direct APK updates. Obtain/export Apple's Developer ID Application certificate with its private key. [Android signing](https://developer.android.com/studio/publish/app-signing) · [Apple notarization](https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution).

For single-line Base64 on macOS: `base64 -i /path/to/file | tr -d '\n'`. Paste directly into the secret field, never issues, PRs, chat, or Git. Restrict master/tag creation to trusted maintainers before storing credentials. PR/CI jobs do not import them; only tag-validated Release jobs do. If adopting an approval-protected environment, update signing jobs to use it.

Distribution verifies Android with `apksigner`; Mac uses a temporary keychain, hardened runtime, notarization, stapling, and assessment. Cleanup runs even on failure. Missing credentials or failed notarization attaches nothing. **Real-credential distribution has not yet been validated.**

## Asset names

Release assets are `DropDuo-v<version>-macos-arm64.zip`, `DropDuo-v<version>-macos-x86_64.zip`, and `DropDuo-v<version>-android.apk`, each with a `.sha256` file, plus `SHA256SUMS`. Development builds add `-development` before the extension. The website and [install guide](usage.md#install) depend on these patterns.

## Review

Check the attached files: Apple Silicon ZIP, Intel ZIP, Android APK, `SHA256SUMS`, per-asset checksums/JSON metadata, `LICENSE`, `NOTICE`, `DEPENDENCIES.md`, and required third-party license texts. Install them on clean devices; verify same-key Android upgrades for distribution builds.

Don't mark releases as **pre-release**, so GitHub and the website always offer the newest build. Published assets are public and do not expire after CI's 30-day retention.

## CI and repository settings

- Checks/quality: develop/master pushes and PRs into develop/master. Native Checks skips docs/web-only changes; Web builds the site.
- CodeQL: master PRs that change code (docs/web-only PRs skip it), weekly on the default branch, or manual runs. It is not a merge requirement, so skipped PRs can still merge.
- Dependency review: PRs. Dependabot: weekly Actions/Gradle updates.
- Release: runs when a release is published; validates the tag and builds downloads. Actions are SHA-pinned with scoped permissions; security changes never auto-merge.

Configure branch/tag protections, dependency graph, Dependabot alerts, and private vulnerability reporting in GitHub. Files alone do not activate them. Account for skipped workflows before making them required (skipped required workflows can remain pending). Fork PRs get read-only tokens and no release secrets.

## Troubleshooting

- **Dependency review unsupported:** enable **Dependency graph** under repository security settings, let it populate, then rerun failed jobs. This setup error is not a vulnerability finding; inspect any subsequent failure. Promotion still needs applicable checks/review.
- **Release workflow failed:** open **Actions → Release** to see why. A tag that doesn't match `version.properties` or the changelog, or isn't on master, attaches nothing: delete that release and its tag on GitHub, fix `develop`, merge, and publish again. For a build failure, fix it, then rerun the failed jobs, or use **Run workflow** with the tag.
- **Outdated/invalid tag:** changing master does not change tagged source. Prepare a new version, increase the build number, add its changelog heading, merge, and publish a new release. Keep old published tags unchanged.
