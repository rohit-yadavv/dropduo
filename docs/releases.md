# Releases

Publish downloads through [GitHub Releases](https://github.com/rohit-yadavv/dropduo/releases). One manual workflow picks the version, builds both apps and publishes the release. CI artifacts are temporary development downloads.

## Release

1. Merge `develop` into `master` through a PR once checks pass. See the [Git workflow](../CONTRIBUTING.md#git-workflow).
2. On GitHub, open **Actions → Release → Run workflow** and click **Run workflow**. Only the repository owner can release; runs started by anyone else, including re-runs, stop before building.

Leave the version empty for the next one after the newest tag (`v0.1.7` → `v0.1.8`), or type a newer version without the `v`, such as `0.2.0`. The build number is the commit count on master, so each release has a higher Android version code than the last. It builds the Mac and Android apps from master, then tags that commit and publishes the release with its downloads. Release notes list the pull requests merged since the previous release, led by a `## <version>` section from `CHANGELOG.md` if one exists. No version edits in code; `version.properties` only sets the version for local and test builds. The checks already ran when the commit reached master, so they aren't repeated.

Test the published downloads on your Mac and phone. If something is wrong, fix it on `develop` and release again; never move a published tag or replace its downloads.

## Signing modes

**Development:** used until the signing secrets below exist. Ad-hoc signed, non-notarized Mac apps and debug-signed Android APKs; the release notes say so. Android debug keys can change, blocking updates; export received files before uninstalling.

**Distribution:** used automatically once `MAC_CERTIFICATE_BASE64` and `ANDROID_KEYSTORE_BASE64` are set, along with the other credentials below. Do not relabel development builds as signed distribution.

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

Distribution verifies Android with `apksigner`; Mac uses a temporary keychain, hardened runtime, notarization, stapling, and assessment. Cleanup runs even on failure. Missing credentials or failed notarization publishes nothing. **Real-credential distribution has not yet been validated.**

## In-app updates

Android reads the latest published GitHub release and its `SHA256SUMS`, then verifies the downloaded APK's package, version, minimum OS and signing certificate. Keep the Android distribution key stable: an incompatible development certificate is rejected, rather than uninstalling and losing app data.

Mac uses Sparkle 2.10.0. Before the first distribution release with the updater, configure a persistent Sparkle signing pair:

1. Resolve the dependency with `swift package --package-path apps/macos resolve`.
2. Run `apps/macos/.build/artifacts/sparkle/Sparkle/bin/generate_keys --account dropduo` on the maintainer's Mac. This creates or reuses a private key in the login Keychain and prints the public key.
3. Set repository **Actions variable** `SPARKLE_PUBLIC_ED_KEY` to the printed public key. The build embeds it into the app. For a local configured build, use `DROPDUO_UPDATE_PUBLIC_KEY`.
4. Export the private key with that tool's `--account dropduo -x /secure/path/dropduo-sparkle-key` option. Add the file's contents directly as repository **Actions secret** `SPARKLE_PRIVATE_ED_KEY`, then securely back up/remove the exported file. Never commit or paste the private key in chat or issues.

Each distribution Mac job signs its ZIP, verifies the signature against the embedded public key, generates its architecture-specific feed and signs the feed. The publish job requires both feeds and matching archive/build metadata. Feeds point to immutable versioned release assets; clients fetch them through `/releases/latest/download/appcast-mac-applesilicon.xml` or `appcast-mac-intel.xml`. No separate server or website deployment is needed. Missing/mismatched Sparkle keys fail distribution releases before publishing. Development releases have no Mac feeds; their updater is disabled unless a public key is explicitly supplied for testing.

Don't rotate the Sparkle key without a planned migration for installed clients. Releases preceding the updater require one manual installation. Test a real signed/notarized old→new Mac upgrade and a same-key Android installation before claiming production update reliability.

## Asset names

Distribution releases also include `appcast-mac-applesilicon.xml` and `appcast-mac-intel.xml` for in-app Mac updates. Mac downloads contain Sparkle's full license at `Contents/Resources/SPARKLE-LICENSE.txt`.

Each release has `dropduo-mac-applesilicon-v<version>.zip`, `dropduo-mac-intel-v<version>.zip`, `dropduo-android-v<version>.apk`, and `SHA256SUMS`. `LICENSE`, `NOTICE` and `DEPENDENCIES.md` ship inside the Mac app (`Contents/Resources`) and the APK (`assets`). The website and [install guide](usage.md#install) depend on these names; the website also recognizes the older `DropDuo-v<version>-macos-arm64` style used up to `v0.1.0-alpha.6`.

## Review

Check the published files: Apple silicon ZIP, Intel ZIP, Android APK and `SHA256SUMS`, with the license and notices inside each app. Per-file checksums and build metadata stay in the run's artifacts for 30 days. Install them on clean devices; verify same-key Android upgrades for distribution builds.

Don't mark releases as **pre-release**, so GitHub and the website always offer the newest build. Published assets are public and do not expire after CI's 30-day retention.

## CI and repository settings

- Checks/quality: develop/master pushes and PRs into develop/master. Native Checks skips docs/web-only changes; Web builds the site.
- CodeQL: master PRs that change code (docs/web-only PRs skip it), weekly on the default branch, or manual runs. It is not a merge requirement, so skipped PRs can still merge.
- Dependency review: PRs. Dependabot: weekly Actions/Gradle updates.
- Release: manual **Run workflow** only; builds master and publishes the next version. Actions are SHA-pinned with scoped permissions; security changes never auto-merge.

Configure branch/tag protections, dependency graph, Dependabot alerts, and private vulnerability reporting in GitHub. Files alone do not activate them. Account for skipped workflows before making them required (skipped required workflows can remain pending). Fork PRs get read-only tokens and no release secrets.

## Troubleshooting

- **Dependency review unsupported:** enable **Dependency graph** under repository security settings, let it populate, then rerun failed jobs. This setup error is not a vulnerability finding; inspect any subsequent failure. Promotion still needs applicable checks/review.
- **Release workflow failed:** open **Actions → Release** to see why. The tag and release are created only in the last step, so a failed build publishes nothing; fix it on `develop`, merge, and run the workflow again.
- **Version already exists:** the workflow never reuses a tag. Leave the version empty, or type a newer one.
