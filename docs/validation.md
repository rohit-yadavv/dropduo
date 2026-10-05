# Alpha validation evidence

Evidence recorded **2026-10-02**; these results do not certify physical-device reliability or production signing.

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

## Rename and hosted checks

The same local checks and emulator integration passed again on 2026-10-02 with DropDuo IDs/domain separators and regenerated crypto fixtures. Earlier working-name pairings are incompatible; install both apps and pair again.

- [Checks 36987826448](https://github.com/rohit-yadavv/dropduo/actions/runs/36987826448), commit `4facb44`: Android tests/build/lint, both Mac architectures/packaging/signature checks, and interoperability passed. Uploaded development assets/reports. Fixed obsolete Android SDK setup and Mac resource-bundle layout from the earlier failed run.
- [Release 37008988771](https://github.com/rohit-yadavv/dropduo/actions/runs/37008988771), master `90dc7e7`: all nine jobs passed. Published [0.1.0-alpha.1](https://github.com/rohit-yadavv/dropduo/releases/tag/v0.1.0-alpha.1) as a development prerelease. Download checksums, Mac ad-hoc signatures/architectures, Android signature/version, and public HTTP downloads were verified.

Physical-device checks and real-credential distribution signing/notarization remain outstanding.
