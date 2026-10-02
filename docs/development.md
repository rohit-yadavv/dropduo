# Development

Run `./scripts/doctor`. macOS requires Swift 6 and macOS SDK (Command Line Tools can build the Swift package); full Xcode is useful but not required for this build. Android requires Java 17 and an Android SDK; set ANDROID_HOME or put `sdk.dir` in ignored local.properties.

Build/check commands are implemented with the apps. No release credentials are needed for development. Distribution builds must use a maintainer-owned signing identity; development signing is not production signing.

Read component AGENTS.md before editing. Keep dependencies pinned and wrapper checksums verified. Never commit local SDK paths.
