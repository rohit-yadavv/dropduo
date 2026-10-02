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

Not yet verified: production Mac pairing approval/rejection with a physical phone, camera QR scanning, physical LAN multicast discovery, long screen lock, vendor battery policies, sleep/wake, network switching, large-file endurance, VoiceOver/TalkBack, signed/notarized distribution, independent security review. No hosted CI run has been claimed.

Run the repeatable commands in [development](development.md), then record physical-device results against [testing](testing.md). Resume tests seeded partial data; do not describe them as verified recovery from every possible network interruption.
