# Contributing

Bug reports, code, design, docs, and device testing are welcome. Start with the [product scope](docs/product.md), [development setup](docs/development.md), and relevant `AGENTS.md`.

- Open a focused issue with expected/actual behavior, reproduction steps, OS versions, and build revision. Never include private files, pairing codes, or credentials.
- Follow the [Git workflow](docs/git-workflow.md): one change per PR, include validation and limitations, at least one reviewer, no self-merge. The local solo-v1 exception applies only to `develop`.
- Run `./scripts/doctor`, then `./scripts/check <scope>`. Protocol changes need Swift/Kotlin compatibility tests and security review. Record real-device results separately from emulator checks.
- Update affected docs. Describe implemented behavior; speed, security, and reliability claims need evidence.

Contributions use Apache-2.0; no CLA is required. See the [code of conduct](CODE_OF_CONDUCT.md) and [security policy](SECURITY.md).
