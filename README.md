# Nearport

Native, private sharing between your Mac and Android phone. Pair once; send files, photos, links, and text over your local network.

**Status: v1 development.** Independently implemented; no Flitdrop code is used. No account, cloud file storage, advertising, or analytics.

## Quick start

See [development](docs/development.md), [product scope](docs/product.md), and [architecture](docs/architecture.md). Build instructions and verified artifacts are updated as milestones land.

## Contributing with an agent

Start with [AGENTS.md](AGENTS.md), then the instructions inside the component you change. Use the same checks as CI. Report unavailable checks honestly.

## Project

- `apps/macos`: native SwiftUI app and Swift protocol implementation.
- `apps/android`: native Kotlin/Compose app and JVM protocol implementation.
- `protocol`: versioned wire contract and shared compatibility fixtures.
- `docs`: product decisions, development, testing, and Git workflow.
- `scripts`: repeatable setup, verification, and packaging.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
