# Contributing

Bug reports, code, design, docs, and device testing are welcome. Start with the [product scope](docs/product.md), [development setup](docs/development.md), and relevant `AGENTS.md`.

- Open a focused issue with expected/actual behavior, reproduction steps, OS versions, and app version. Never include private files, pairing codes, or credentials.
- Run `./scripts/doctor`, then `./scripts/check <scope>`. Protocol changes need Swift/Kotlin compatibility tests and security review. Test background receiving, sleep, and Wi-Fi changes on real devices; emulators and compilation don't prove them.
- Update affected docs. Describe implemented behavior; speed, security, and reliability claims need evidence.

## Git workflow

- `develop`: day-to-day work. Commit here directly, or use a short `feature/<name>` or `fix/<name>` branch for larger changes.
- `master`: what's released. The website deploys from it and release tags point at it.

To release, open a PR from `develop` into `master`, merge it once checks pass, then publish a release from GitHub's Releases page ([steps](docs/releases.md)). Urgent fixes can branch from `master`; merge them back into `develop` afterwards. Contributors outside the project send PRs to `develop`. Never force-push `master` or auto-merge security-sensitive changes.

Commit messages use `<type>: <short imperative description>` with type feat, fix, hotfix, refactor, chore, docs, or test. Enable the check with `git config core.hooksPath .githooks`. Versions use SemVer tags `vMAJOR.MINOR.PATCH`; see [releases](docs/releases.md).

Contributions use Apache-2.0; no CLA is required. See the [code of conduct](CODE_OF_CONDUCT.md) and [security policy](SECURITY.md).
