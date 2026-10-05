# Alpha validation evidence

Recorded 2026-10-02 during local development. Check the Git log for the associated implementation and validation commits; this document is not a public release certification.

| Check | Result and scope |
| --- | --- |
| Swift core assertions | Passed: frame crypto, tampering/replay rejection, pairing tickets, safe names, integrity, resumed/empty/cancelled/idempotent storage. |
| Kotlin core JUnit tests | Passed, including shared Swift-generated crypto fixture compatibility and storage edge cases. |
| Mac app compile and packaging | Passed on Apple Silicon with Swift 6.4 and macOS SDK 27; Command Line Tools emitted missing optional developer-framework search-path warnings. |
| Android debug build and lint | Passed with Java 17 and SDK 36; lint reports non-fatal version, storage, and platform recommendations, with no errors. |
| Swift/JVM socket test | Passed: authenticated bidirectional multi-chunk files, text, checksums, and resume from seeded partial files; incorrect pairing credential rejected. |
| Android emulator integration | Passed on API 37: actual foreground service, Android Keystore pairing, authenticated Swift connection, file/text exchange, receiving toggle. |
| Native UI inspection | Android first-run screenshot inspected; Mac first-run accessibility tree inspected. Mac screenshot blocked by host screen-recording permission. |

Not yet verified: production Mac pairing approval/rejection with a physical phone, camera QR scanning, physical LAN multicast discovery, long screen lock, vendor battery policies, sleep/wake, network switching, large-file endurance, VoiceOver/TalkBack, signed/notarized distribution, independent security review.

Run the repeatable commands in [development](development.md), then record physical-device results against [testing](testing.md). Resume tests seeded partial data; do not describe them as verified recovery from every possible network interruption.

## DropDuo rename verification

On 2026-10-02 the core checks, native builds, Android lint, Swift/JVM interoperability, and Android emulator foreground-service/Keystore integration passed again using DropDuo application IDs and protocol namespaces. The shared cryptographic fixture was regenerated for the new domain separators. Earlier working-name alpha pairings are incompatible; both devices must install DropDuo and pair again.

## Hosted CI

On 2026-10-02, [Checks run 36987826448](https://github.com/rohit-yadavv/dropduo/actions/runs/36987826448) at commit `4facb44` passed Android tests/build/lint, Apple Silicon Mac tests/packaging, Intel Mac tests/packaging, signature validation, and Swift/JVM interoperability. It uploaded all three development downloads and Android reports. The initial run at `8b6ff02` had failed because the Android setup action requested obsolete SDK `tools` and the Mac resource bundle layout differed between Swift toolchains; both were corrected. This hosted result does not verify real-phone behavior or production signing.

On 2026-10-02, [Release run 37008988771](https://github.com/rohit-yadavv/dropduo/actions/runs/37008988771) passed all nine jobs at master commit `90dc7e7`. [0.1.0-alpha.1](https://github.com/rohit-yadavv/dropduo/releases/tag/v0.1.0-alpha.1) was published as a development prerelease. All three downloaded installer checksums, Mac ad-hoc signatures and architectures, and Android APK signature/version were verified; public download URLs returned HTTP 200. Physical-device checks remain outstanding. No real-credential distribution signing/notarization run has been performed.
