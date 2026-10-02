# Release checklist

Public releases originate from master after staging validation. The current alpha is a development build, not a signed public release.

1. Pass all checks and physical-device matrix; review pairing and transfer security.
2. Update version in Android config and Mac Info.plist; update changelog.
3. Use an owned Android signing key and verify upgrade/install. Never replace a public signing identity between releases.
4. Sign Mac app with Developer ID, enable hardened runtime, notarize using Apple credentials, staple, verify Gatekeeper on a clean Mac. CI development artifacts are ad-hoc signed only.
5. Build release assets from the production revision; include SHA-256 checksums and third-party notices.
6. Tag vMAJOR.MINOR.PATCH (or documented prerelease suffix), push tag, and publish release notes with known limitations.
7. Enable host branch protections and private security reports. Review dependency licenses.

The workflow file only enforces the production-branch preflight. No publishing or secret-dependent signing automation is enabled until a remote and release credentials exist.
