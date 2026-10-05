# Releases

Publish downloads through [GitHub Releases](https://github.com/rohit-yadavv/dropduo/releases). Release CI creates a **draft**; a maintainer must review and publish it. CI artifacts are temporary development downloads.

## Prepare

1. Complete applicable [device/accessibility tests](testing.md), update [evidence](validation.md), and review security and licenses. Include required third-party license texts; the dependency inventory alone is insufficient.
2. On a preparation branch from `develop`, update `version.properties`: SemVer `versionName` (for example `0.1.0-alpha.2`) and a higher `versionCode` for each distributed update. Both apps use this file.
3. Add an exact released heading to `CHANGELOG.md`, such as `## 0.1.0-alpha.2`, below `## Unreleased`.
4. Promote through `develop` → `staging` → `master` with review and passing checks. See [Git workflow](git-workflow.md). Maintainers must establish missing branches and protections; CI does not create them.

## Tag and build

After the reviewed commit reaches master, use your **new, prepared version**:

```sh
git fetch origin --tags &&
git switch master &&
git pull --ff-only origin master &&
./scripts/check-release v0.1.0-alpha.2 development &&
git tag -a v0.1.0-alpha.2 -m "DropDuo 0.1.0-alpha.2" &&
git push origin v0.1.0-alpha.2
```

`&&` stops after failure. Never move a published tag. A `v*` tag push starts Release; a master push does not. You can also select an existing tag in **Actions → Release → Run workflow**.

Release validates that the tag exists, is reachable from master, matches app versions, and has a released changelog section. It reruns checks on the tag, verifies asset checksums/source commits, and creates a draft. It never merges, tags, overwrites releases, or publishes automatically.

## Signing modes

**Development:** default for prerelease tag pushes. No signing secrets needed; ad-hoc signed, non-notarized Mac apps and debug-signed Android APKs use `-development` filenames. For testers only. Android debug keys can change, blocking updates; export received files before uninstalling. Stable tags are refused in this mode.

**Distribution:** default for stable tags; requires credentials below. For a signed prerelease, manually run Release with `mode=distribution` before a development draft exists, or use a fresh prerelease tag. Do not relabel development builds as signed distribution.

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

Distribution verifies Android with `apksigner`; Mac uses a temporary keychain, hardened runtime, notarization, stapling, and assessment. Cleanup runs even on failure. Missing credentials or failed notarization stops the draft. **Real-credential distribution has not yet been validated.**

## Review and publish

Inspect draft notes and assets: Apple Silicon ZIP, Intel ZIP, Android APK, `SHA256SUMS`, per-asset checksums/JSON metadata, `LICENSE`, `NOTICE`, `DEPENDENCIES.md`, and required third-party license texts. Install those exact assets on clean devices; verify same-key Android upgrades for distribution builds.

Releases are never marked **pre-release**, so GitHub and the website always offer the newest published build; the `-alpha` version suffix still signals early, unsigned builds. Publish when satisfied. Published assets are public and do not expire after CI's 30-day retention; drafts require maintainer access.

## CI and repository settings

- Checks/quality: develop/master pushes and PRs into develop/staging/master. Native Checks skips docs/web-only changes; Web builds the site. Staging relies on incoming PR checks.
- CodeQL: master PRs, weekly on the default branch, or manual runs.
- Dependency review: PRs. Dependabot: weekly Actions/Gradle updates.
- Release: exact-tag build checks. Actions are SHA-pinned with scoped permissions; security changes never auto-merge.

Configure branch/tag protections, dependency graph, Dependabot alerts, and private vulnerability reporting in GitHub. Files alone do not activate them. Account for skipped workflows before making them required (skipped required workflows can remain pending). Fork PRs get read-only tokens and no release secrets.

## Troubleshooting

- **Dependency review unsupported:** enable **Dependency graph** under repository security settings, let it populate, then rerun failed jobs. This setup error is not a vulnerability finding; inspect any subsequent failure. Promotion still needs applicable checks/review.
- **No release after pushing master:** push a fresh version tag. Check **Actions → Release**; validation failure creates no draft. A successful run creates a draft that must be published.
- **Outdated/invalid tag:** changing master does not change tagged source. Prepare a new version, increase the build number, add its changelog heading, promote, and tag. Keep old published tags unchanged.
- **Existing draft blocks retry:** inspect it; a maintainer may remove an incomplete draft before retrying. Never move its published tag or overwrite reviewed assets.
- **Promotion uses old workflows:** promote the current develop commit through staging; an older staging PR uses its own source/workflows.
