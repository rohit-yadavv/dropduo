# Verification

## Automated

- Protocol: deterministic Swift/JVM crypto fixtures, replay rejection, malformed frames, unsupported versions, path/size validation.
- Transfers: actual loopback bidirectional transfers with hashes; cancellation, retry/resume, duplicates, zero-byte and multi-chunk files.
- Apps: platform builds and core unit tests. CI reports scope independently.

## Manual release matrix

Record date, build revision, Mac/Android model and OS, result, and evidence. Test QR pairing approve/reject, revoke, native sharing, file locations, text, no network, isolation, disk capacity, duplicate names, app restart, Android background/screen lock, Mac sleep/wake, Wi-Fi changes, and accessibility with large text/VoiceOver/TalkBack.

Emulator evidence does not prove vendor battery behavior or physical LAN discovery. Do not mark physical-device checks passed without running them.
