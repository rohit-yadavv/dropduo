# Contributing

Bug reports, code, design, docs, and device testing are welcome. Start with the [product scope](docs/product.md), [development setup](docs/development.md), and relevant `AGENTS.md`.

- Open a focused issue with expected/actual behavior, reproduction steps, OS versions, and app version. Never include private files, pairing codes, or credentials.
- Run `./scripts/doctor`, then `./scripts/check <scope>`. Protocol changes need Swift/Kotlin compatibility tests and security review. Test background receiving, sleep, and Wi-Fi changes on real devices; emulators and compilation don't prove them.
- Update affected docs. Describe implemented behavior; speed, security, and reliability claims need evidence.

## Git workflow

- `develop`: active development. Start `feature/<name>` or `fix/<name>` here.
- `staging`: pre-production QA.
- `master`: production. Publish and deploy from here only.

Changes flow feature/fix → develop → staging → master through PRs. Hotfixes start at master and merge back into develop and staging. Keep PRs focused, explain behavior, validation, and limitations, and get at least one reviewer; no self-merge, no force-push after review, and never auto-merge security-sensitive changes.

**Solo exception:** while DropDuo has a single maintainer, local milestone commits on `develop` are allowed without a PR. This doesn't cover pushing, publishing, or committing directly to staging or master.

Commit messages use `<type>: <short imperative description>` with type feat, fix, hotfix, refactor, chore, docs, or test. Enable the check with `git config core.hooksPath .githooks`. Versions use SemVer tags `vMAJOR.MINOR.PATCH`; see [releases](docs/releases.md).

Contributions use Apache-2.0; no CLA is required. See the [code of conduct](CODE_OF_CONDUCT.md) and [security policy](SECURITY.md).
