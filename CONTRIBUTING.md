# Contributing to DropDuo

Thanks for your interest in DropDuo. Bug reports, ideas, docs and code are all welcome.

## Ground rules

- Be respectful and keep discussions about the work.
- Keep changes focused: one topic per pull request.
- DropDuo is deliberately simple. It polishes a few everyday flows rather than adding new surfaces, so open an issue before proposing a big feature or a new dependency.
- Keep private files and pairing codes out of issues, screenshots and logs.

## Getting set up

You'll need a Mac on macOS 14+ with Swift 6, Java 17 and the Android SDK. `ANDROID_HOME` defaults to `~/Library/Android/sdk`.

```sh
git clone https://github.com/rohit-yadavv/dropduo.git
cd dropduo
git config core.hooksPath .githooks   # checks commit messages locally
./scripts/check all                   # Swift and Android tests, both app builds, interop, website
./scripts/build-macos && open dist/DropDuo.app
```

| Folder | What's inside |
|---|---|
| `apps/macos` | Mac app (Swift package) |
| `apps/android` | Android app and the shared protocol code (`core`) |
| `apps/web` | Website |
| `scripts` | Build, check and release scripts |

Run one part at a time with `./scripts/check macos`, `android`, `protocol` or `web`.

## Making a change

1. **Open an issue first** for anything non-trivial, so we can agree on the approach.
2. Branch from `develop`. `master` is what's released.
3. Match the surrounding code: naming, spacing and comment density. Small, readable code beats clever code.
4. If you touch the transfer protocol, change both apps and run `./scripts/check protocol`, so the Mac and Android stay compatible.
5. Make sure `./scripts/check all` passes.
6. Write commits like `fix: short description`, using `feat`, `fix`, `hotfix`, `refactor`, `chore`, `docs` or `test`.

## Pull requests

- Open them against `develop`, describe the change and link the issue.
- Include before/after screenshots or a short clip for UI changes.
- Keep the diff as small as the change allows.

## Reporting bugs and requesting features

Use the [issue templates](https://github.com/rohit-yadavv/dropduo/issues/new/choose). For bugs, include the app, macOS and Android versions, your device models and steps to reproduce.

For security issues, **don't** open a public issue. Report them privately through [GitHub security advisories](https://github.com/rohit-yadavv/dropduo/security/advisories/new).

By contributing, you agree that your work is licensed under the [MIT License](LICENSE).
