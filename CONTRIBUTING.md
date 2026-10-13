# Contributing

Bug reports and pull requests are welcome. Please keep private files and pairing codes out of issues.

## Building

You need macOS 14+ with Swift 6, Java 17, and the Android SDK (set `ANDROID_HOME`).

```sh
./scripts/check macos     # Swift checks and Mac app build
./scripts/check android   # JVM tests, APK build, lint
./scripts/check all       # everything, including Swift/Kotlin interop
./scripts/build-macos && open dist/DropDuo.app
```

Background receiving, sleep, and Wi-Fi changes need testing on real devices.

## Git workflow

Work happens on `develop`; `master` is what's released. To release, merge `develop` into `master` and run **Actions → Release** on GitHub.

Commit messages look like `fix: short description` (feat, fix, hotfix, refactor, chore, docs, or test). Run `git config core.hooksPath .githooks` to check them locally.
